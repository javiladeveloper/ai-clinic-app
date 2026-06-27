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

/**
 * Verifica disponibilidad de un profesional (REGLA idéntica a hooks/useHorarios.ts):
 * valida horario fijo (salvo flexible) + solapamientos (advertencia, no bloquea).
 * Lectura directa con RLS de staff.
 */
object DisponibilidadRepo {

    private val DIA = mapOf(
        DayOfWeek.MONDAY to "Lun", DayOfWeek.TUESDAY to "Mar", DayOfWeek.WEDNESDAY to "Mié",
        DayOfWeek.THURSDAY to "Jue", DayOfWeek.FRIDAY to "Vie", DayOfWeek.SATURDAY to "Sáb",
        DayOfWeek.SUNDAY to "Dom",
    )

    private fun JsonObject.s(k: String): String? = (this[k] as? JsonPrimitive)?.content?.takeIf { it != "null" }
    private fun min(t: String): Int { val p = t.split(":"); return (p[0].toIntOrNull() ?: 0) * 60 + (p.getOrNull(1)?.toIntOrNull() ?: 0) }
    private fun diaDe(fecha: String): String {
        val p = fecha.split("-")
        return DIA[LocalDate(p[0].toInt(), p[1].toInt(), p[2].toInt()).dayOfWeek] ?: "Lun"
    }

    suspend fun verificar(
        terapeutaId: String, fecha: String, hora: String, duracion: Int,
        excludeCitaId: String? = null, pacienteId: String? = null,
    ): Disponibilidad {
        val esFlexible = esFlexible(terapeutaId)
        val newStart = min(hora); val newEnd = newStart + duracion
        var overflow: String? = null

        if (!esFlexible) {
            val horarios = horariosDelDia(terapeutaId, diaDe(fecha))
            if (horarios.isEmpty()) return Disponibilidad(false, "El profesional no trabaja los ${diaDe(fecha)}", false)
            var startsWithin = false; var fullyWithin = false
            for (h in horarios) {
                val ini = min(h.first); val fin = min(h.second)
                if (newStart in ini until fin) { startsWithin = true; if (newEnd <= fin) { fullyWithin = true; break } }
            }
            if (!startsWithin) {
                val rangos = horarios.joinToString(", ") { "${it.first}-${it.second}" }
                return Disponibilidad(false, "Fuera de horario. Disponible: $rangos", false)
            }
            if (!fullyWithin) overflow = "Ojo: la cita termina después del turno del profesional ($duracion min)."
        }

        // Solapamiento del profesional (advertencia, no bloquea).
        val solapeProf = primerSolape(
            citasDe("terapeuta_id", terapeutaId, fecha, excludeCitaId), newStart, newEnd
        )?.let { "El profesional ya atiende a otra persona a las $it (se permite en paralelo)" }

        // Solapamiento del paciente (advertencia).
        val solapePac = if (pacienteId != null) primerSolape(
            citasDe("paciente_id", pacienteId, fecha, excludeCitaId), newStart, newEnd
        )?.let { "El paciente ya tiene otra cita a las $it ese día" } else null

        val aviso = solapeProf ?: solapePac ?: overflow
        return Disponibilidad(true, aviso, aviso != null)
    }

    private suspend fun esFlexible(terapeutaId: String): Boolean {
        val o = Supabase.client.postgrest["terapeutas"]
            .select(Columns.list("horario_flexible")) { filter { eq("id", terapeutaId) } }
            .decodeList<JsonObject>().firstOrNull()
        return o?.s("horario_flexible") == "true"
    }

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

    private fun primerSolape(citas: List<Pair<String, Int>>, newStart: Int, newEnd: Int): String? {
        for ((hora, dur) in citas) {
            val s = min(hora); val e = s + dur
            if (newStart < e && newEnd > s) return hora.take(5)
        }
        return null
    }
}
