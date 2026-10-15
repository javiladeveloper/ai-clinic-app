package pe.saniape.app.data.staff

import io.github.jan.supabase.auth.auth
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put
import pe.saniape.app.data.Supabase
import pe.saniape.app.data.crearHttpClient
import pe.saniape.app.data.offline.RechazoServidor
import pe.saniape.app.data.offline.ResultadoEscritura

/**
 * Mediciones corporales (Nutrición) por /api/staff/medicion-corporal, con el
 * Bearer del staff. El servidor valida permiso ('pacientes' o 'sesiones'), el
 * candado de solo lectura y los rangos (`validarMedicion`, el mismo que aquí).
 *
 * DIRECTO, sin cola offline: la pestaña necesita la fila guardada para mostrarla
 * y, sin señal, tampoco pudo leer el historial.
 */
object MedicionesRepo {

    private const val RUTA = "/api/staff/medicion-corporal"
    private val http = crearHttpClient()
    private val json = Json { ignoreUnknownKeys = true }

    private fun token(): String? = Supabase.client.auth.currentSessionOrNull()?.accessToken

    private val SIN_SESION = ResultadoEscritura(
        registrada = false, rechazo = RechazoServidor("Tu sesión expiró. Vuelve a entrar.", "NO_AUTENTICADO", 401),
    )
    private val SIN_RED = ResultadoEscritura(
        registrada = false, rechazo = RechazoServidor("Sin conexión. Revisa tu internet.", "SIN_RED"),
    )

    /** Las del paciente, cronológicas. null = no se pudo leer (no es "sin mediciones"). */
    suspend fun listar(pacienteId: String): List<MedicionCorporal>? {
        val tk = token() ?: return null
        return try {
            val resp = http.get("${Supabase.SITE_URL}$RUTA") {
                header("Authorization", "Bearer $tk")
                parameter("pacienteId", pacienteId)
            }
            if (resp.status != HttpStatusCode.OK) return null
            val o = json.parseToJsonElement(resp.bodyAsText()).jsonObject
            ordenarMediciones((o["items"] as? JsonArray).orEmpty().mapNotNull { (it as? JsonObject)?.let(::medicionDeJson) })
        } catch (e: CancellationException) { throw e } catch (_: Exception) { null }
    }

    /** Registra una medición (ya validada con `validarMedicion`). En `cuerpo["medicion"]` vuelve la fila. */
    suspend fun registrar(b: BorradorMedicion, citaId: String? = null, tratamientoId: String? = null): ResultadoEscritura {
        val tk = token() ?: return SIN_SESION
        return try {
            pe.saniape.app.ui.conIndicador {
                val resp = http.post("${Supabase.SITE_URL}$RUTA") {
                    header("Authorization", "Bearer $tk")
                    contentType(ContentType.Application.Json)
                    setBody(cuerpoMedicion(b, citaId, tratamientoId).toString())
                }
                AtencionRepo.resultadoDeRespuesta(resp.status.value, runCatching { resp.bodyAsText() }.getOrNull())
            }
        } catch (e: CancellationException) { throw e } catch (_: Exception) { SIN_RED }
    }

    suspend fun borrar(id: String): ResultadoEscritura {
        val tk = token() ?: return SIN_SESION
        return try {
            pe.saniape.app.ui.conIndicador {
                val resp = http.delete("${Supabase.SITE_URL}$RUTA") {
                    header("Authorization", "Bearer $tk")
                    contentType(ContentType.Application.Json)
                    setBody(buildJsonObject { put("id", id) }.toString())
                }
                AtencionRepo.resultadoDeRespuesta(resp.status.value, runCatching { resp.bodyAsText() }.getOrNull())
            }
        } catch (e: CancellationException) { throw e } catch (_: Exception) { SIN_RED }
    }
}
