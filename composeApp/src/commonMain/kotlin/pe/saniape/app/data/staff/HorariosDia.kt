package pe.saniape.app.data.staff

import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.Columns
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.client.statement.bodyAsText
import io.ktor.http.isSuccess
import kotlinx.datetime.LocalDate
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.jsonObject
import pe.saniape.app.data.Supabase
import pe.saniape.app.data.crearHttpClient

// «🕘 Horarios» de la agenda (20/11/2026, gemelo de lib/horarios-dia.ts y
// components/citas/HorariosDelDia.tsx de la web). Jonathan: «imagínate que está
// sentada y le preguntan por horario…» y «no se entiende bien qué hora está
// ocupada… una mancha roja, no se entiende si es media hora, 45 min». El día de
// cada profesional en tramos legibles: ocupado, libre, permiso, ausente.

/** Un tramo del día en minutos (fin exclusivo). */
data class Tramo(val ini: Int, val fin: Int)

/** Las citas (inicio + duración) unidas cuando se tocan o se cruzan, en orden. */
fun ocupadosUnidos(citas: List<Pair<Int, Int>>): List<Tramo> {
    val r = mutableListOf<Tramo>()
    for ((ini, dur) in citas.sortedBy { it.first }) {
        val fin = ini + maxOf(1, dur)
        val u = r.lastOrNull()
        if (u != null && ini <= u.fin) r[r.lastIndex] = u.copy(fin = maxOf(u.fin, fin)) else r += Tramo(ini, fin)
    }
    return r
}

/** Los tramos LIBRES: su turno (o el rango, a demanda) menos lo ocupado y los permisos (≥ [minimo] min). */
fun libresDelDia(turno: List<Tramo>, ocupados: List<Tramo>, flexible: Boolean, rango: Tramo, bloqueados: List<Tramo> = emptyList(), minimo: Int = 10): List<Tramo> {
    val base = if (flexible) listOf(rango) else turno.sortedBy { it.ini }
    val quitar = ocupadosUnidos((ocupados + bloqueados).map { it.ini to (it.fin - it.ini) })
    val libres = mutableListOf<Tramo>()
    for (f in base) {
        var desde = f.ini
        for (q in quitar) {
            if (q.fin <= desde || q.ini >= f.fin) continue
            if (q.ini > desde) libres += Tramo(desde, minOf(q.ini, f.fin))
            desde = maxOf(desde, q.fin)
        }
        if (desde < f.fin) libres += Tramo(desde, f.fin)
    }
    return libres.filter { it.fin - it.ini >= minimo }
}

/** El tramo que se dibuja: horas enteras, cubriendo turnos y citas, al menos 7–20. */
fun rangoDelDia(tramos: List<Tramo>): Tramo {
    val ini = minOf(7 * 60, tramos.minOfOrNull { it.ini } ?: 7 * 60)
    val fin = maxOf(20 * 60, tramos.maxOfOrNull { it.fin } ?: 20 * 60)
    return Tramo(ini / 60 * 60, (fin + 59) / 60 * 60)
}

/** 990 → «4:30 p. m.»; con [corta], «4:30». */
fun horaDeMin(min: Int, corta: Boolean = false): String {
    val h24 = (min / 60) % 24
    val m = min % 60
    val h12 = if (h24 % 12 == 0) 12 else h24 % 12
    val base = "$h12:${m.toString().padStart(2, '0')}"
    return if (corta) base else "$base ${if (h24 < 12) "a. m." else "p. m."}"
}

/** «4:00–4:45 p. m.» (el sufijo una vez si los dos son de la misma mitad del día). */
fun tramoTexto(t: Tramo): String =
    if ((t.ini < 720) == (t.fin <= 720)) "${horaDeMin(t.ini, true)}–${horaDeMin(t.fin)}" else "${horaDeMin(t.ini)}–${horaDeMin(t.fin)}"

/** «45 min», «1 h», «1 h 30 min». */
fun duracionTexto(min: Int): String {
    val h = min / 60; val m = min % 60
    return if (h > 0) (if (m > 0) "$h h $m min" else "$h h") else "$m min"
}

private fun aMin(h: String?): Int? {
    val p = h?.split(":") ?: return null
    return (p.getOrNull(0)?.toIntOrNull() ?: return null) * 60 + (p.getOrNull(1)?.toIntOrNull() ?: 0)
}

private val ICONO_AUSENCIA = mapOf("Vacaciones" to "🌴", "Licencia" to "📋", "Descanso médico" to "🩺", "Capacitación" to "🎓", "Permiso" to "🕑", "Otro" to "🚫")
private val BADGE_AUSENCIA = mapOf("Vacaciones" to "De vacaciones", "Licencia" to "De licencia", "Descanso médico" to "Con descanso médico",
    "Capacitación" to "En capacitación", "Permiso" to "Con permiso", "Otro" to "No atiende")

/** El día de UN profesional, listo para pintar. */
data class FilaHorario(
    val terapeutaId: String,
    val nombre: String,
    val flexible: Boolean,
    val turno: List<Tramo>,
    val ocupados: List<Tramo>,
    val bloqueados: List<Tramo>,
    val libres: List<Tramo>,
    /** «🌴 De vacaciones» si está ausente todo el día; null si atiende. */
    val ausente: String?,
    /** 🚪 Dónde atiende ese día: «Consultorio 2» o «Consultorio 2 · 9:00 a. m.–1:00 p. m.» (web: chip de Horarios, 21/11/2026). */
    val consultorios: List<String> = emptyList(),
) {
    val atiende: Boolean get() = ausente == null && (flexible || turno.isNotEmpty())
}

data class DiaHorarios(val rango: Tramo, val filas: List<FilaHorario>)

/** Lo que se lee de la base, crudo (para [armarDiaHorarios], que es puro). */
data class DatosHorarios(
    val profesionales: List<Triple<String, String, Boolean>>,           // id, nombre, a demanda
    val turnos: List<Triple<String, String, String>>,                   // terapeuta, inicio, fin
    val citas: List<Triple<String, String, Int>>,                       // terapeuta, hora, duración
    val ausencias: List<List<String?>>,                                 // terapeuta, motivo, hora_desde, hora_hasta
)

/** Puro: arma las filas del día (atienden primero, luego alfabético). */
fun armarDiaHorarios(d: DatosHorarios): DiaHorarios {
    val base = d.profesionales.map { (id, nombre, flexible) ->
        val turno = d.turnos.filter { it.first == id }.mapNotNull { (_, i, f) -> val a = aMin(i); val b = aMin(f); if (a != null && b != null) Tramo(a, b) else null }
        val ocupados = ocupadosUnidos(d.citas.filter { it.first == id }.mapNotNull { (_, h, dur) -> aMin(h)?.let { it to dur } })
        val suyas = d.ausencias.filter { it.getOrNull(0) == id }
        val diaEntero = suyas.firstOrNull { it.getOrNull(2) == null || it.getOrNull(3) == null }
        val bloqueados = suyas.filter { it.getOrNull(2) != null && it.getOrNull(3) != null }
            .mapNotNull { val a = aMin(it[2]); val b = aMin(it[3]); if (a != null && b != null) Tramo(a, b) else null }
        val ausente = diaEntero?.let { "${ICONO_AUSENCIA[it.getOrNull(1)] ?: "🚫"} ${BADGE_AUSENCIA[it.getOrNull(1)] ?: "No atiende"}" }
        FilaHorario(id, nombre, flexible, turno, ocupados, bloqueados, emptyList(), ausente)
    }
    val rango = rangoDelDia(base.flatMap { it.turno + it.ocupados })
    val filas = base.map { f -> if (f.atiende) f.copy(libres = libresDelDia(f.turno, f.ocupados, f.flexible, rango, f.bloqueados)) else f }
        .sortedWith(compareBy<FilaHorario>({ !it.atiende }, { it.nombre.lowercase() }))
    return DiaHorarios(rango, filas)
}

/**
 * Puro: los consultorios de cada profesional, de la respuesta de GET /api/staff/consultorios
 * (`porProfesional: [{ terapeutaId, tramos: [{ consultorio, desde, hasta, puntual }] }]`).
 * `desde`/`hasta` null = todo el día. Un cambio puntual de ese día se marca «(solo hoy)».
 * Respuesta sin consultorios, de un backend viejo o de un plan sin la función: mapa vacío.
 */
fun consultoriosPorProfesional(o: JsonObject?): Map<String, List<String>> {
    fun JsonObject.s(k: String): String? = (this[k] as? JsonPrimitive)?.content?.takeIf { it != "null" && it.isNotBlank() }
    val lista = o?.get("porProfesional") as? JsonArray ?: return emptyMap()
    val out = LinkedHashMap<String, List<String>>()
    for (e in lista) {
        val p = e as? JsonObject ?: continue
        val id = p.s("terapeutaId") ?: continue
        val textos = (p["tramos"] as? JsonArray).orEmpty().mapNotNull { t ->
            val x = t as? JsonObject ?: return@mapNotNull null
            val nombre = x.s("consultorio") ?: return@mapNotNull null
            val a = aMin(x.s("desde")); val b = aMin(x.s("hasta"))
            val horas = if (a != null && b != null) " · ${tramoTexto(Tramo(a, b))}" else ""
            val puntual = if ((x["puntual"] as? JsonPrimitive)?.booleanOrNull == true) " (solo hoy)" else ""
            "$nombre$horas$puntual"
        }.distinct()
        if (textos.isNotEmpty()) out[id] = textos
    }
    return out
}

object HorariosDiaRepo {
    private val http = crearHttpClient()

    /** Consultorios del día (servidor: plan desde Premium). Nunca rompe la hoja: ante cualquier fallo, sin consultorios. */
    private suspend fun consultorios(fecha: String): Map<String, List<String>> = runCatching {
        val tk = Supabase.client.auth.currentSessionOrNull()?.accessToken ?: return emptyMap()
        val r = http.get("${Supabase.SITE_URL}/api/staff/consultorios") { header("Authorization", "Bearer $tk"); parameter("fecha", fecha) }
        if (!r.status.isSuccess()) return emptyMap()
        consultoriosPorProfesional(Json.parseToJsonElement(r.bodyAsText()).jsonObject)
    }.getOrDefault(emptyMap())

    private val DIA = listOf("Lun", "Mar", "Mié", "Jue", "Vie", "Sáb", "Dom")
    private fun JsonObject.s(k: String): String? = (this[k] as? JsonPrimitive)?.content?.takeIf { it != "null" }

    /** Lee el día (RLS de la clínica): profesionales activos, sus turnos, citas y ausencias. */
    suspend fun cargar(fecha: String): DiaHorarios {
        val dia = DIA[LocalDate.parse(fecha.take(10)).dayOfWeek.ordinal]
        val pg = Supabase.client.postgrest
        val profs = pg["terapeutas"].select(Columns.list("id, nombre, horario_flexible, estado")) { filter { eq("estado", "Activo") } }
            .decodeList<JsonObject>().mapNotNull { o -> o.s("id")?.let { Triple(it, o.s("nombre") ?: "Profesional", o.s("horario_flexible") == "true") } }
        val turnos = pg["horarios_terapeuta"].select(Columns.list("terapeuta_id, hora_inicio, hora_fin")) { filter { eq("activo", true); eq("dia", dia) } }
            .decodeList<JsonObject>().mapNotNull { o -> val t = o.s("terapeuta_id"); val i = o.s("hora_inicio"); val f = o.s("hora_fin"); if (t != null && i != null && f != null) Triple(t, i, f) else null }
        val citas = pg["citas"].select(Columns.list("terapeuta_id, hora, duracion")) { filter { eq("fecha", fecha); neq("estado", "Cancelada") } }
            .decodeList<JsonObject>().mapNotNull { o -> val t = o.s("terapeuta_id"); val h = o.s("hora"); if (t != null && h != null) Triple(t, h, o.s("duracion")?.toIntOrNull() ?: 45) else null }
        val ausencias = runCatching {
            pg["ausencias_profesional"].select(Columns.list("terapeuta_id, motivo, hora_desde, hora_hasta, desde, hasta")) {
                filter { lte("desde", fecha); gte("hasta", fecha) }
            }.decodeList<JsonObject>().map { o -> listOf(o.s("terapeuta_id"), o.s("motivo"), o.s("hora_desde")?.take(5), o.s("hora_hasta")?.take(5)) }
        }.getOrDefault(emptyList())
        val dondeAtiende = consultorios(fecha)
        val d = armarDiaHorarios(DatosHorarios(profs, turnos, citas, ausencias))
        return if (dondeAtiende.isEmpty()) d else d.copy(filas = d.filas.map { f -> dondeAtiende[f.terapeutaId]?.let { f.copy(consultorios = it) } ?: f })
    }
}

/** Una cita de hoy con adelanto que todavía debe (para «Cobros de hoy»). */
data class DeudaAdelanto(val citaId: String, val paciente: String, val hora: String, val costo: Double, val abonado: Double)

/** Las citas de hoy con adelanto y sin terminar de pagar, por hora (gemela de deudasConAdelanto). */
fun deudasConAdelanto(citas: List<CitaStaff>, hoy: String): List<DeudaAdelanto> =
    citas.filter { it.fecha.take(10) == hoy && it.estado != "Cancelada" && it.pagadaAt == null && it.abonado > 0 && (it.costo ?: 0.0) > it.abonado }
        .map { DeudaAdelanto(it.id, it.pacienteNombre ?: "Paciente", it.hora.take(5), it.costo ?: 0.0, it.abonado) }
        .sortedBy { it.hora }
