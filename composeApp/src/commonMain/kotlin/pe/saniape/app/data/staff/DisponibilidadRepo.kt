package pe.saniape.app.data.staff

import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.Columns
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import pe.saniape.app.data.Supabase

/** Resultado de verificar disponibilidad (igual que la web). */
data class Disponibilidad(val disponible: Boolean, val motivo: String?, val esAdvertencia: Boolean)

/** Estado de un profesional para el modal "ver horarios". */
data class EstadoProfesional(
    val terapeutaId: String,
    val nombre: String,
    val libre: Boolean,
    val etiqueta: String,         // "Libre a las 10:00" / "Fuera de su horario" / etc.
    val rangos: String,           // "09:00-12:00 · 14:00-17:00"
    val citasDia: String,         // "09:00, 10:30"
)

private fun minutosDe(t: String): Int {
    val p = t.split(":")
    return (p[0].toIntOrNull() ?: 0) * 60 + (p.getOrNull(1)?.toIntOrNull() ?: 0)
}

/** Primera cita que se cruza con [inicio, fin) (hora "HH:MM"), o null. */
private fun primerSolape(citas: List<Pair<String, Int>>, inicio: Int, fin: Int): String? {
    for ((hora, dur) in citas) {
        val s = minutosDe(hora); val e = s + dur
        if (inicio < e && fin > s) return hora.take(5)
    }
    return null
}

/**
 * La regla de disponibilidad, PURA (gemelo de estadoProfesional de CitaForm web,
 * 19/11/2026). Nunca bloquea: todo es aviso.
 *  - La cita ENTERA tiene que caber en una franja del profesional («llega 9:40 y
 *    dura 20 → hasta las 10»); antes bastaba con que empezara dentro.
 *  - Consulta y Evaluación no se cruzan con nada (regla de solapamiento); una
 *    sesión sí se puede en paralelo.
 * [horarios] = franjas (inicio, fin) del día; [citasProf]/[citasPac] = (hora, duración).
 */
fun evaluarDisponibilidad(
    flexible: Boolean, diaNombre: String, horarios: List<Pair<String, String>>,
    citasProf: List<Pair<String, Int>>, citasPac: List<Pair<String, Int>>,
    hora: String, duracion: Int, tipo: String?,
    /**
     * Tiene horario cargado OTROS días y ninguno este (22/11/2026: un doctor con
     * L–S marcado «a demanda» y la cita en domingo no avisaba nada). Manda sobre
     * «a demanda»: sale en plomo con «Sin horarios». Gemelo de `diaSinHorario` (web).
     */
    sinHorarioEseDia: Boolean = false,
): Disponibilidad {
    val inicio = minutosDe(hora); val fin = inicio + duracion
    var avisoHorario: String? = null
    if (sinHorarioEseDia) {
        avisoHorario = "Sin horarios los $diaNombre"
    } else if (!flexible) {
        if (horarios.isEmpty()) {
            avisoHorario = "No atiende los $diaNombre"
        } else {
            // La base guarda "08:00:00": al usuario, "08:00 a 13:00" (sin segundos).
            val rangos = horarios.joinToString(", ") { "${it.first.take(5)} a ${it.second.take(5)}" }
            val empieza = horarios.any { inicio >= minutosDe(it.first) && inicio < minutosDe(it.second) }
            val cabe = horarios.any { inicio >= minutosDe(it.first) && fin <= minutosDe(it.second) }
            avisoHorario = when {
                !empieza -> "Fuera de su horario. Atiende: $rangos"
                !cabe -> "La cita ($duracion min) termina después de su turno. Atiende: $rangos"
                else -> null
            }
        }
    }
    val exclusiva = tipo == "Consulta" || tipo == "Evaluación"
    val solapeProf = primerSolape(citasProf, inicio, fin)?.let {
        if (exclusiva) "Ocupado a las $it" else "El profesional ya atiende a otra persona a las $it (se permite en paralelo)"
    }
    val solapePac = primerSolape(citasPac, inicio, fin)?.let { "El paciente ya tiene otra cita a las $it ese día" }
    // Prioridad del aviso: horario > solape del profesional > del paciente.
    val aviso = avisoHorario ?: solapeProf ?: solapePac
    return Disponibilidad(true, aviso, aviso != null)
}

/** ¿Tiene horario otros días y ninguno este? Sin horario cargado en absoluto NO es «día libre». */
fun diaSinHorario(diasConHorario: Collection<String>, dia: String): Boolean =
    diasConHorario.isNotEmpty() && dia !in diasConHorario

/**
 * Por qué NO conviene este profesional a esa hora (para pintarlo en gris en el
 * selector, 19/11/2026: «si el fisio no tiene disponibilidad debe aparecer en
 * plomo»). null = disponible, también «en paralelo» en una sesión.
 */
fun motivoNoDisponible(d: Disponibilidad?): String? =
    d?.motivo?.takeIf { d.esAdvertencia && !it.contains("paralelo") && !it.startsWith("El paciente") }

/**
 * Verifica disponibilidad de un profesional (REGLA idéntica a la web, ver
 * [evaluarDisponibilidad]). Lectura directa con RLS de staff.
 */
object DisponibilidadRepo {

    private val DIA = mapOf(
        DayOfWeek.MONDAY to "Lun", DayOfWeek.TUESDAY to "Mar", DayOfWeek.WEDNESDAY to "Mié",
        DayOfWeek.THURSDAY to "Jue", DayOfWeek.FRIDAY to "Vie", DayOfWeek.SATURDAY to "Sáb",
        DayOfWeek.SUNDAY to "Dom",
    )

    private fun JsonObject.s(k: String): String? = (this[k] as? JsonPrimitive)?.content?.takeIf { it != "null" }
    private fun diaDe(fecha: String): String {
        val p = fecha.split("-")
        return DIA[LocalDate(p[0].toInt(), p[1].toInt(), p[2].toInt()).dayOfWeek] ?: "Lun"
    }

    suspend fun verificar(
        terapeutaId: String, fecha: String, hora: String, duracion: Int,
        excludeCitaId: String? = null, pacienteId: String? = null,
        /** Tipo de la cita que se arma: Consulta/Evaluación no se cruzan con nada. */
        tipo: String? = null,
    ): Disponibilidad {
        val flexible = esFlexible(terapeutaId)
        val dia = diaDe(fecha)
        val semana = horariosSemana(listOf(terapeutaId))[terapeutaId].orEmpty()
        return evaluarDisponibilidad(
            flexible = flexible, diaNombre = dia,
            horarios = if (flexible) emptyList() else semana.filter { it.first == dia }.map { it.second },
            sinHorarioEseDia = diaSinHorario(semana.map { it.first }, dia),
            citasProf = citasDe("terapeuta_id", terapeutaId, fecha, excludeCitaId),
            citasPac = if (pacienteId != null) citasDe("paciente_id", pacienteId, fecha, excludeCitaId) else emptyList(),
            hora = hora, duracion = duracion, tipo = tipo,
        )
    }

    /**
     * La disponibilidad de VARIOS profesionales a la vez (selector de la nueva cita
     * y «Ver horarios»): tres lecturas en total, no tres por profesional.
     */
    suspend fun deVarios(
        ids: List<String>, fecha: String, hora: String, duracion: Int, tipo: String?,
        excludeCitaId: String? = null,
    ): Map<String, Disponibilidad> {
        if (ids.isEmpty() || hora.isBlank() || fecha.length < 10) return emptyMap()
        val dia = diaDe(fecha)
        val flexibles = Supabase.client.postgrest["terapeutas"]
            .select(Columns.list("id, horario_flexible")) { filter { isIn("id", ids) } }
            .decodeList<JsonObject>()
            .filter { it.s("horario_flexible") == "true" }.mapNotNull { it.s("id") }.toSet()
        // Toda la semana (no solo este día): hace falta saber si hoy es su día sin horario.
        val semana = horariosSemana(ids)
        val horarios = semana.mapValues { (_, l) -> l.filter { it.first == dia }.map { it.second } }
        val citas = Supabase.client.postgrest["citas"]
            .select(Columns.list("id, terapeuta_id, hora, duracion")) {
                filter {
                    isIn("terapeuta_id", ids); eq("fecha", fecha); neq("estado", "Cancelada")
                    if (excludeCitaId != null) neq("id", excludeCitaId)
                }
            }
            .decodeList<JsonObject>()
            .mapNotNull { o ->
                val t = o.s("terapeuta_id"); val h = o.s("hora")
                if (t != null && h != null) t to (h to (o.s("duracion")?.toIntOrNull() ?: 45)) else null
            }
            .groupBy({ it.first }, { it.second })
        return ids.associateWith { id ->
            evaluarDisponibilidad(
                flexible = id in flexibles, diaNombre = dia, horarios = horarios[id].orEmpty(),
                sinHorarioEseDia = diaSinHorario(semana[id].orEmpty().map { it.first }, dia),
                citasProf = citas[id].orEmpty(), citasPac = emptyList(),
                hora = hora, duracion = duracion, tipo = tipo,
            )
        }
    }

    private suspend fun esFlexible(terapeutaId: String): Boolean {
        val o = Supabase.client.postgrest["terapeutas"]
            .select(Columns.list("horario_flexible")) { filter { eq("id", terapeutaId) } }
            .decodeList<JsonObject>().firstOrNull()
        return o?.s("horario_flexible") == "true"
    }

    /** Las franjas activas de TODA la semana por profesional: (día, (inicio, fin)). */
    private suspend fun horariosSemana(ids: List<String>): Map<String, List<Pair<String, Pair<String, String>>>> =
        Supabase.client.postgrest["horarios_terapeuta"]
            .select(Columns.list("terapeuta_id, dia, hora_inicio, hora_fin")) {
                filter { isIn("terapeuta_id", ids); eq("activo", true) }
            }
            .decodeList<JsonObject>()
            .mapNotNull { o ->
                val t = o.s("terapeuta_id"); val d = o.s("dia"); val i = o.s("hora_inicio"); val f = o.s("hora_fin")
                if (t != null && d != null && i != null && f != null) t to (d to (i to f)) else null
            }
            .groupBy({ it.first }, { it.second })

    @Suppress("unused")
    private suspend fun horariosDelDia(terapeutaId: String, dia: String): List<Pair<String, String>> {
        return Supabase.client.postgrest["horarios_terapeuta"]
            .select(Columns.list("dia, hora_inicio, hora_fin, activo")) {
                filter { eq("terapeuta_id", terapeutaId); eq("activo", true); eq("dia", dia) }
            }
            .decodeList<JsonObject>()
            .mapNotNull { val i = it.s("hora_inicio"); val f = it.s("hora_fin"); if (i != null && f != null) i to f else null }
    }

    private suspend fun citasDe(col: String, id: String, fecha: String, excludeId: String?): List<Pair<String, Int>> {
        return Supabase.client.postgrest["citas"]
            .select(Columns.list("id, hora, duracion, estado")) {
                filter {
                    eq(col, id); eq("fecha", fecha); neq("estado", "Cancelada")
                    if (excludeId != null) neq("id", excludeId)
                }
            }
            .decodeList<JsonObject>()
            .mapNotNull { val h = it.s("hora"); if (h != null) h to (it.s("duracion")?.toIntOrNull() ?: 45) else null }
    }
}
