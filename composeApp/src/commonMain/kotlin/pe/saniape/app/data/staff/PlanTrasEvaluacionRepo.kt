package pe.saniape.app.data.staff

import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.Columns
import io.github.jan.supabase.postgrest.query.Order
import io.github.jan.supabase.postgrest.query.filter.FilterOperator
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import pe.saniape.app.data.Supabase

/**
 * Lecturas del paso «Plan de tratamiento» tras completar la evaluación (T3,
 * gemelo de components/citas/PlanTrasEvaluacion de la web). Directas a la
 * base con la RLS del usuario; las escrituras van por los endpoints de siempre.
 */
object PlanTrasEvaluacionRepo {

    /** ¿Ya tiene un plan abierto? null = no se pudo leer (no se concluye que no tenga). */
    suspend fun tieneTratamientoActivo(pacienteId: String): Boolean? = runCatching {
        Supabase.client.postgrest["tratamientos"].select(Columns.raw("id")) {
            filter { eq("paciente_id", pacienteId); eq("estado", "Activo") }
            limit(1)
        }.decodeList<JsonObject>().isNotEmpty()
    }.getOrNull()

    /** Qué servicios se eligen tras una evaluación en esta clínica (los últimos 300 planes). */
    suspend fun usosTrasEvaluacion(): Map<String, Int> = runCatching {
        val filas = Supabase.client.postgrest["tratamientos"].select(Columns.raw("procedimiento_id")) {
            filter {
                filterNot("cita_origen_id", FilterOperator.IS, "null")
                neq("estado", "Eliminado")
            }
            order("created_at", Order.DESCENDING)
            limit(300)
        }.decodeList<JsonObject>()
        usosPorServicio(filas.map { (it["procedimiento_id"] as? JsonPrimitive)?.content?.takeIf { v -> v != "null" } })
    }.getOrDefault(emptyMap())

    /** Lo pagado de un tratamiento (null = no se pudo leer). */
    suspend fun pagadoDe(tratamientoId: String): Double? = runCatching {
        Supabase.client.postgrest["pagos_tratamiento"].select(Columns.raw("monto")) {
            filter { eq("tratamiento_id", tratamientoId) }
        }.decodeList<JsonObject>().sumOf { (it["monto"] as? JsonPrimitive)?.content?.toDoubleOrNull() ?: 0.0 }
    }.getOrNull()
}
