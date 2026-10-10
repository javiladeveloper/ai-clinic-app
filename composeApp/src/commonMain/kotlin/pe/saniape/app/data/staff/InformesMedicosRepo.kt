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
 * Lecturas de los documentos médicos (lib/informes-medicos-acciones.ts de la
 * web): directo a la base con el Bearer del staff; la RLS acota (el profesional
 * ve lo suyo, recepción todo lo de la clínica). Emitir y anular van por
 * AtencionRepo.emitirInforme / anularInforme (endpoints del servidor).
 */
object InformesMedicosRepo {
    private val json = Json { ignoreUnknownKeys = true }

    private fun JsonObject.s(k: String): String? =
        (this[k] as? JsonPrimitive)?.content?.takeIf { it != "null" && it.isNotBlank() }

    /**
     * Documentos del paciente, el más nuevo primero (incluye anulados: son
     * historial). Con respaldo local por paciente. null = no se pudo y no hay respaldo.
     */
    suspend fun listar(pacienteId: String): List<InformeMedicoApp>? {
        val clave = "informes-medicos:$pacienteId"
        val filas: List<JsonObject> = try {
            Supabase.client.postgrest["informes_medicos"]
                .select(Columns.raw(SELECT_INFORMES_STAFF)) {
                    filter { eq("paciente_id", pacienteId) }
                    order("created_at", Order.DESCENDING)
                    limit(200)
                }
                .decodeList<JsonObject>()
                .also { CacheLectura.guardar(clave, Json.encodeToString(JsonArray.serializer(), JsonArray(it))) }
        } catch (e: kotlin.coroutines.cancellation.CancellationException) {
            throw e
        } catch (e: Exception) {
            val crudo = CacheLectura.leer(clave) ?: return null
            runCatching { json.parseToJsonElement(crudo).jsonArray.map { it.jsonObject } }.getOrNull() ?: return null
        }
        return filas.mapNotNull { aInformeMedico(it) }
    }

    /** Exámenes que la clínica ya pidió (los aprende el trigger), más usados primero. Vacío si falla. */
    suspend fun examenesAprendidos(): List<ExamenAprendido> = runCatching {
        Supabase.client.postgrest["examenes_frecuentes"]
            .select(Columns.list("nombre", "usos")) {
                order("usos", Order.DESCENDING)
                limit(300)
            }
            .decodeList<JsonObject>()
            .mapNotNull { o -> o.s("nombre")?.let { ExamenAprendido(it, o.s("usos")?.toDoubleOrNull()?.toInt() ?: 0) } }
    }.getOrDefault(emptyList())

    /**
     * El equipo con colegiatura y los rubros y nombres de sus especialidades
     * (para el selector de quién firma: firmantesDocumento). null si falla: la
     * pantalla lo avisa (un fallo de red no es "nadie puede firmar").
     */
    suspend fun equipoFirmantes(): List<FirmanteDoc>? = runCatching {
        Supabase.client.postgrest["terapeutas"]
            .select(Columns.raw(
                "id, nombre, cmp, estado, " +
                    "especialidades:terapeuta_especialidades(especialidad:especialidades(id, rubro, nombre))"
            )) { order("nombre", Order.ASCENDING) }
            .decodeList<JsonObject>()
            .mapNotNull { o ->
                val id = o.s("id") ?: return@mapNotNull null
                val esps = (o["especialidades"] as? JsonArray).orEmpty()
                    .mapNotNull { (it as? JsonObject)?.get("especialidad") as? JsonObject }
                FirmanteDoc(
                    id = id,
                    nombre = o.s("nombre").orEmpty(),
                    cmp = o.s("cmp"),
                    estado = o.s("estado").orEmpty(),
                    rubros = esps.map { it.s("rubro") },
                    nombresEspecialidades = esps.mapNotNull { it.s("nombre") },
                )
            }
    }.getOrNull()

    /** Especialidades de la clínica con nombre y estado (¿la atención es de psiquiatría?). Vacío si falla. */
    suspend fun especialidades(): List<EspecialidadNombre> = runCatching {
        Supabase.client.postgrest["especialidades"]
            .select(Columns.list("id", "nombre", "estado")) { limit(200) }
            .decodeList<JsonObject>()
            .mapNotNull { o -> o.s("id")?.let { EspecialidadNombre(it, o.s("nombre").orEmpty(), o.s("estado")) } }
    }.getOrDefault(emptyList())
}
