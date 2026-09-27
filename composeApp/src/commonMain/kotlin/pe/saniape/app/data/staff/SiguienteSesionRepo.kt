package pe.saniape.app.data.staff

import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.Columns
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import pe.saniape.app.data.Supabase

/** Lo que hace falta para ofrecer "📅 Agendar siguiente" tras completar una sesión. */
data class OfertaSiguienteSesion(
    val tratamientoId: String,
    /** Fecha sugerida (intervalo del servicio o +7 días desde hoy en Lima). */
    val fecha: String,
    val intervaloDias: Int,
    /** Sesiones que faltan (ya descontada la recién completada). */
    val quedan: Int,
    val terapeutaTratamientoId: String?,
    val especialidadId: String?,
)

/**
 * Decide si se ofrece "Agendar siguiente" leyendo el tratamiento YA sincronizado
 * (después de completar) y si hay alguna sesión/cita futura. Tres lecturas
 * chicas en paralelo, acotadas a un tratamiento. Gemelo del toast de la ficha
 * web (quedanSesiones + fechaSugeridaSiguienteSesion), más el chequeo de
 * "ya tiene la siguiente agendada" que evita duplicados.
 */
object SiguienteSesionRepo {
    private fun JsonObject.str(k: String): String? =
        (this[k] as? JsonPrimitive)?.content?.takeIf { it != "null" }

    /**
     * null = no ofrecer (terminó, ya tiene la siguiente, o no se pudo leer: sin
     * señal mejor no ofrecer que ofrecer mal). [excluirCitaId]/[excluirSesionId]:
     * la cita/sesión recién atendida.
     */
    suspend fun evaluar(
        tratamientoId: String, hoy: String = hoyClinicaIso(),
        excluirCitaId: String? = null, excluirSesionId: String? = null,
    ): OfertaSiguienteSesion? = runCatching {
        coroutineScope {
            val tratD = async {
                Supabase.client.postgrest["tratamientos"]
                    .select(Columns.raw(
                        "id, estado, total_sesiones, sesiones_completadas, terapeuta_id, " +
                            "procedimiento:procedimientos(especialidad_id, sesiones_intervalo_dias)"
                    )) { filter { eq("id", tratamientoId) }; limit(1) }
                    .decodeList<JsonObject>().firstOrNull()
            }
            val citasD = async {
                Supabase.client.postgrest["citas"]
                    .select(Columns.list("id")) {
                        filter {
                            eq("tratamiento_id", tratamientoId)
                            eq("tipo", "Sesión")
                            gte("fecha", hoy)
                            isIn("estado", listOf("Pendiente", "Confirmada"))
                            if (excluirCitaId != null) neq("id", excluirCitaId)
                        }
                        limit(1)
                    }
                    .decodeList<JsonObject>()
            }
            val sesD = async {
                Supabase.client.postgrest["sesiones"]
                    .select(Columns.list("id")) {
                        filter {
                            eq("tratamiento_id", tratamientoId)
                            gte("fecha", hoy)
                            isIn("estado", listOf("Planificada", "Reprogramada", "En progreso"))
                            if (excluirSesionId != null) neq("id", excluirSesionId)
                        }
                        limit(1)
                    }
                    .decodeList<JsonObject>()
            }
            val t = tratD.await() ?: return@coroutineScope null
            val hayFutura = citasD.await().isNotEmpty() || sesD.await().isNotEmpty()
            val total = t.str("total_sesiones")?.toIntOrNull() ?: 0
            val comp = t.str("sesiones_completadas")?.toIntOrNull() ?: 0
            if (!debeOfrecerSiguienteSesion(t.str("estado"), total, comp, hayFutura)) return@coroutineScope null
            val proc = t["procedimiento"] as? JsonObject
            val intervalo = proc?.str("sesiones_intervalo_dias")?.toIntOrNull()?.takeIf { it >= 1 } ?: 7
            OfertaSiguienteSesion(
                tratamientoId = tratamientoId,
                fecha = fechaSugeridaSiguienteSesion(hoy, intervalo),
                intervaloDias = intervalo,
                quedan = total - comp,
                terapeutaTratamientoId = t.str("terapeuta_id"),
                especialidadId = proc?.str("especialidad_id"),
            )
        }
    }.getOrNull()
}
