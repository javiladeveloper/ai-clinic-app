package pe.saniape.app.data.staff

import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.Columns
import io.github.jan.supabase.postgrest.query.Order
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import pe.saniape.app.data.Supabase

/**
 * Lecturas del control de nutrición ([NutricionControl.kt]), directo a la base
 * con el Bearer del staff (RLS de siempre). Gemelo de `leerSeguimientoNutricion`
 * y de las lecturas de "¿ya lo atendió?" de `lib/nutricion-control.ts`.
 * Lo que falla nunca abre de más: el seguimiento devuelve null (se evalúa como
 * siempre) y "¿ya lo atendió?" cuenta como NO.
 */
object NutricionControlRepo {

    private fun JsonObject.str(k: String): String? =
        (this[k] as? JsonPrimitive)?.content?.takeIf { it.isNotBlank() && it != "null" }

    /** Una lectura que no tumba a las demás: null si falló. */
    private suspend fun <T> intento(bloque: suspend () -> T): T? = try {
        bloque()
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        null
    }

    /**
     * El seguimiento de nutrición de UN paciente: sus atenciones completadas
     * (filtradas a nutrición con [esNutricion], la misma cadena de la pantalla) y
     * sus planes alimentarios, de lo más antiguo a lo más nuevo, más el origen
     * del tratamiento ligado a la cita (si hay). Tres lecturas en paralelo.
     * null = no se pudo leer o se llegó al tope.
     */
    suspend fun seguimiento(
        pacienteId: String,
        tratamientoId: String?,
        esNutricion: (FilaCitaNutricion) -> Boolean,
    ): SeguimientoNutricion? = coroutineScope {
        val citasD = async {
            intento {
                Supabase.client.postgrest["citas"]
                    .select(Columns.list("id, paciente_id, fecha, hora, especialidad_id, terapeuta_id")) {
                        filter { eq("paciente_id", pacienteId); eq("estado", "Completada") }
                        order("fecha", Order.ASCENDING)
                        order("hora", Order.ASCENDING)
                        limit(TOPE_SEGUIMIENTO.toLong())
                    }
                    .decodeList<JsonObject>()
                    .mapNotNull { o ->
                        FilaCitaNutricion(
                            id = o.str("id") ?: return@mapNotNull null,
                            pacienteId = o.str("paciente_id") ?: pacienteId,
                            fecha = o.str("fecha") ?: return@mapNotNull null,
                            hora = o.str("hora"),
                            especialidadId = o.str("especialidad_id"),
                            terapeutaId = o.str("terapeuta_id"),
                        )
                    }
            }
        }
        val planesD = async {
            intento {
                Supabase.client.postgrest["planes_alimentacion"]
                    .select(Columns.list("paciente_id, cita_id, fecha")) {
                        filter { eq("paciente_id", pacienteId) }
                        order("fecha", Order.ASCENDING)
                        limit(TOPE_SEGUIMIENTO.toLong())
                    }
                    .decodeList<JsonObject>()
                    .mapNotNull { o ->
                        FilaPlanNutricion(
                            pacienteId = o.str("paciente_id") ?: pacienteId,
                            citaId = o.str("cita_id"),
                            fecha = o.str("fecha") ?: return@mapNotNull null,
                        )
                    }
            }
        }
        val origenD = async {
            if (tratamientoId.isNullOrBlank()) emptyMap()
            else intento {
                Supabase.client.postgrest["tratamientos"]
                    .select(Columns.list("id, cita_origen_id")) {
                        filter { eq("id", tratamientoId) }
                        limit(1)
                    }
                    .decodeList<JsonObject>()
                    .mapNotNull { o -> o.str("id")?.let { it to o.str("cita_origen_id") } }
                    .toMap()
            }
        }
        armarSeguimientoNutricion(
            listOf(pacienteId), citasD.await(), planesD.await(), origenD.await(), esNutricion,
        )?.get(pacienteId)
    }

    /**
     * ¿Este profesional ya atendió a este paciente? Existencia (limit 1) de una
     * cita completada, un tratamiento vivo o un plan alimentario suyos. Lo que no
     * se pudo leer cuenta como NO (el mismo criterio que el servidor).
     */
    suspend fun atendio(pacienteId: String, miTerapeutaId: String): Boolean = coroutineScope {
        fun filas(l: List<JsonObject>?) = l.orEmpty().map { FilaAtendio(it.str("terapeuta_id"), it.str("estado")) }
        val citas = async {
            intento {
                Supabase.client.postgrest["citas"].select(Columns.list("terapeuta_id, estado")) {
                    filter { eq("paciente_id", pacienteId); eq("terapeuta_id", miTerapeutaId); eq("estado", "Completada") }
                    limit(1)
                }.decodeList<JsonObject>()
            }
        }
        val trats = async {
            intento {
                Supabase.client.postgrest["tratamientos"].select(Columns.list("terapeuta_id, estado")) {
                    filter { eq("paciente_id", pacienteId); eq("terapeuta_id", miTerapeutaId); neq("estado", "Eliminado") }
                    limit(1)
                }.decodeList<JsonObject>()
            }
        }
        val planes = async {
            intento {
                Supabase.client.postgrest["planes_alimentacion"].select(Columns.list("terapeuta_id")) {
                    filter { eq("paciente_id", pacienteId); eq("terapeuta_id", miTerapeutaId) }
                    limit(1)
                }.decodeList<JsonObject>()
            }
        }
        atendioAlPaciente(miTerapeutaId, filas(citas.await()), filas(trats.await()), filas(planes.await()))
    }
}
