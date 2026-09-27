package pe.saniape.app.data.staff

import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.Columns
import io.github.jan.supabase.postgrest.query.Order
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import pe.saniape.app.data.Supabase
import pe.saniape.app.data.offline.CacheLectura

/**
 * Lo que el formulario de cita necesita para ofrecer los profesionales de UNA
 * sede (gemelo de useFranjasSemana de la web): las franjas de la semana con su
 * sede y la sede base de cada profesional. Se piden SOLO con multisede — sin
 * ella nunca se nombra la columna sede_id (DALU no paga ni un viaje) — y una
 * sola vez por clínica: son tablas chicas. Con respaldo local para agendar sin señal.
 */
object SedesAgendaRepo {

    data class DatosSede(val franjas: List<FranjaSede>, val base: Map<String, SedeBaseTerapeuta>)

    private var cache: Pair<String, DatosSede>? = null

    private fun JsonObject.str(k: String): String? =
        (this[k] as? JsonPrimitive)?.content?.takeIf { it != "null" }

    fun limpiarCache() { cache = null }

    suspend fun datos(clinicaId: String): DatosSede {
        cache?.let { (id, d) -> if (id == clinicaId) return d }
        val franjas = conRespaldo("sedes:franjas:$clinicaId") {
            Supabase.client.postgrest["horarios_terapeuta"]
                .select(Columns.list("terapeuta_id", "dia", "sede_id")) {
                    filter { eq("activo", true) }
                }
                .decodeList<JsonObject>()
        }.mapNotNull { o ->
            FranjaSede(
                terapeutaId = o.str("terapeuta_id") ?: return@mapNotNull null,
                dia = o.str("dia") ?: return@mapNotNull null,
                sedeId = o.str("sede_id"),
            )
        }
        val base = conRespaldo("sedes:terapeutas:$clinicaId") {
            Supabase.client.postgrest["terapeutas"]
                .select(Columns.list("id", "sede_id", "horario_flexible")) {
                    filter { eq("estado", "Activo") }
                    order("id", Order.ASCENDING)
                }
                .decodeList<JsonObject>()
        }.mapNotNull { o ->
            val id = o.str("id") ?: return@mapNotNull null
            id to SedeBaseTerapeuta(o.str("sede_id"), o.str("horario_flexible") == "true")
        }.toMap()
        return DatosSede(franjas, base).also { cache = clinicaId to it }
    }

    /**
     * Sede de la última cita viva con sede de un tratamiento: la sede "de origen"
     * de una sesión que se agenda desde la ficha. Gemelo de `sedeUltimaCitaTratamiento`.
     * Solo se llama con multisede.
     */
    suspend fun sedeUltimaCitaTratamiento(tratamientoId: String): String? = runCatching {
        Supabase.client.postgrest["citas"]
            .select(Columns.list("sede_id")) {
                filter {
                    eq("tratamiento_id", tratamientoId)
                    neq("estado", "Cancelada")
                    filterNot("sede_id", io.github.jan.supabase.postgrest.query.filter.FilterOperator.IS, "null")
                }
                order("fecha", Order.DESCENDING)
                limit(1)
            }
            .decodeList<JsonObject>().firstOrNull()?.str("sede_id")
    }.getOrNull()

    private suspend fun conRespaldo(nombre: String, traer: suspend () -> List<JsonObject>): List<JsonObject> {
        val clave = CacheLectura.claveCatalogo(nombre)
        return try {
            traer().also { CacheLectura.guardar(clave, Json.encodeToString(JsonArray.serializer(), JsonArray(it))) }
        } catch (e: Exception) {
            val crudo = CacheLectura.leer(clave) ?: throw e
            Json.parseToJsonElement(crudo).jsonArray.map { it.jsonObject }
        }
    }
}
