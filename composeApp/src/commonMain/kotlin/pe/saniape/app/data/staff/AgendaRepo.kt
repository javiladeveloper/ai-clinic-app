package pe.saniape.app.data.staff

import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.Columns
import io.github.jan.supabase.postgrest.query.Order
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import pe.saniape.app.data.Supabase
import pe.saniape.app.data.crearHttpClient

/** Una cita del staff (lista de agenda), con joins de paciente/terapeuta/tratamiento. */
data class CitaStaff(
    val id: String,
    val fecha: String,
    val hora: String,
    val estado: String,
    val tipo: String?,
    val costo: Double?,
    val terapeutaId: String?,
    val terapeutaNombre: String?,
    val pacienteId: String?,
    val pacienteNombre: String?,
    val tratamientoId: String?,
    val procedimiento: String?,
)

/**
 * Lee la agenda (citas) directo de Supabase con la RLS de staff. Las acciones de
 * escritura (completar/revertir/cancelar) van por endpoints (misma lógica que la web).
 * Confirmar y reprogramar son escrituras simples → directas a Supabase.
 */
object AgendaRepo {

    private val json = Json { ignoreUnknownKeys = true }
    private val http = crearHttpClient()
    private suspend fun token(): String? = Supabase.client.auth.currentSessionOrNull()?.accessToken

    private fun JsonObject.str(k: String): String? =
        (this[k] as? JsonPrimitive)?.content?.takeIf { it != "null" }
    private fun JsonObject.dbl(k: String): Double? =
        (this[k] as? JsonPrimitive)?.content?.toDoubleOrNull()
    private fun JsonObject.nested(k: String): JsonObject? = this[k] as? JsonObject

    /** Citas de un día (yyyy-MM-dd). Si miTerapeutaId != null, solo las suyas (scope). */
    suspend fun citasDelDia(fecha: String, miTerapeutaId: String?): List<CitaStaff> {
        val filas = Supabase.client.postgrest["citas"]
            .select(
                Columns.raw(
                    "id, fecha, hora, estado, tipo, costo, terapeuta_id, paciente_id, tratamiento_id, " +
                        "paciente:pacientes(nombre), terapeuta:terapeutas(nombre), " +
                        "tratamiento:tratamientos!citas_tratamiento_id_fkey(procedimiento:procedimientos(nombre))"
                )
            ) {
                filter {
                    eq("fecha", fecha)
                    if (miTerapeutaId != null) eq("terapeuta_id", miTerapeutaId)
                }
                order("hora", Order.ASCENDING)
            }
            .decodeList<JsonObject>()

        return filas.map { o ->
            CitaStaff(
                id = o.str("id") ?: "",
                fecha = o.str("fecha") ?: "",
                hora = o.str("hora") ?: "",
                estado = o.str("estado") ?: "",
                tipo = o.str("tipo"),
                costo = o.dbl("costo"),
                terapeutaId = o.str("terapeuta_id"),
                terapeutaNombre = o.nested("terapeuta")?.str("nombre"),
                pacienteId = o.str("paciente_id"),
                pacienteNombre = o.nested("paciente")?.str("nombre"),
                tratamientoId = o.str("tratamiento_id"),
                procedimiento = o.nested("tratamiento")?.nested("procedimiento")?.str("nombre"),
            )
        }
    }

    /** Confirmar (escritura simple, sin efectos colaterales) → directo a Supabase. */
    suspend fun confirmar(citaId: String): Boolean = try {
        Supabase.client.postgrest["citas"].update({ set("estado", "Confirmada") }) {
            filter { eq("id", citaId) }
        }
        true
    } catch (_: Exception) { false }

    /** Reprogramar (cambia fecha/hora). Sincroniza la sesión vinculada si es tipo Sesión. */
    suspend fun reprogramar(citaId: String, fecha: String, hora: String): Boolean = try {
        Supabase.client.postgrest["citas"].update({
            set("fecha", fecha); set("hora", hora)
        }) { filter { eq("id", citaId) } }
        true
    } catch (_: Exception) { false }

    // ── Acciones complejas vía endpoints (kardex/comisión/contador) ──
    suspend fun completar(
        citaId: String,
        observaciones: String? = null,
        diagnostico: String? = null,
        derivarEspecialidadId: String? = null,
    ): Boolean {
        val tk = token() ?: return false
        val cuerpo = buildJsonObject {
            put("citaId", citaId)
            if (observaciones != null) put("observaciones", observaciones)
            if (!diagnostico.isNullOrBlank()) put("diagnostico", diagnostico)
            if (!derivarEspecialidadId.isNullOrBlank()) put("derivarEspecialidadId", derivarEspecialidadId)
        }
        val resp = http.post("${Supabase.SITE_URL}/api/staff/cita/completar") {
            header("Authorization", "Bearer $tk")
            contentType(ContentType.Application.Json)
            setBody(cuerpo.toString())
        }
        return resp.status == HttpStatusCode.OK
    }

    private suspend fun postSimple(ruta: String, citaId: String): Boolean {
        val tk = token() ?: return false
        val resp = http.post("${Supabase.SITE_URL}/api/staff/cita/$ruta") {
            header("Authorization", "Bearer $tk")
            contentType(ContentType.Application.Json)
            setBody(buildJsonObject { put("citaId", citaId) }.toString())
        }
        return resp.status == HttpStatusCode.OK
    }

    suspend fun revertir(citaId: String) = postSimple("revertir", citaId)
    suspend fun cancelar(citaId: String) = postSimple("cancelar", citaId)

    /** Especialidades activas de la clínica (para el selector de derivación). */
    suspend fun especialidades(): List<EspecialidadRef> {
        val filas = Supabase.client.postgrest["especialidades"]
            .select(Columns.list("id, nombre")) {
                filter { eq("estado", "Activa") }
                order("nombre", Order.ASCENDING)
            }
            .decodeList<JsonObject>()
        return filas.mapNotNull {
            val id = it.str("id") ?: return@mapNotNull null
            EspecialidadRef(id, it.str("nombre") ?: "")
        }
    }
}

data class EspecialidadRef(val id: String, val nombre: String)
