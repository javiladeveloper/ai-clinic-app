package pe.saniape.app.data.staff

import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.Columns
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put
import pe.saniape.app.data.Supabase
import pe.saniape.app.data.crearHttpClient
import pe.saniape.app.data.offline.RechazoServidor
import pe.saniape.app.data.offline.ResultadoEscritura

data class LecturaInfiltraciones(val items: List<Infiltracion>? = null, val error: String? = null)

/**
 * 💉 Infiltraciones por /api/staff/infiltracion con el Bearer del staff (mismo molde que
 * [EvaluacionReumatologicaRepo]). El servidor valida permiso, plan, solo lectura y datos.
 * DIRECTO, sin cola offline: la pantalla necesita las filas guardadas.
 */
object InfiltracionesRepo {

    private const val RUTA = "/api/staff/infiltracion"
    private val http = crearHttpClient()
    private val json = Json { ignoreUnknownKeys = true }

    private fun token(): String? = Supabase.client.auth.currentSessionOrNull()?.accessToken

    private val SIN_SESION = ResultadoEscritura(registrada = false, rechazo = RechazoServidor("Tu sesión expiró. Vuelve a entrar.", "NO_AUTENTICADO", 401))
    private val SIN_RED = ResultadoEscritura(registrada = false, rechazo = RechazoServidor("Sin conexión. Revisa tu internet.", "SIN_RED"))

    private suspend fun resultado(resp: HttpResponse): ResultadoEscritura {
        val cuerpo = runCatching { resp.bodyAsText() }.getOrNull()
        val r = AtencionRepo.resultadoDeRespuesta(resp.status.value, cuerpo)
        if (resp.status == HttpStatusCode.NotFound && r.rechazo?.codigo == null) {
            return ResultadoEscritura(registrada = false, rechazo = RechazoServidor(INFILTRACIONES_NO_DISPONIBLE, "NO_DISPONIBLE", 404))
        }
        return r
    }

    /** Las del paciente, la más reciente primero. */
    suspend fun listar(pacienteId: String): LecturaInfiltraciones {
        val tk = token() ?: return LecturaInfiltraciones(error = "Tu sesión expiró. Vuelve a entrar.")
        return try {
            val resp = http.get("${Supabase.SITE_URL}$RUTA") {
                header("Authorization", "Bearer $tk")
                parameter("pacienteId", pacienteId)
            }
            val texto = runCatching { resp.bodyAsText() }.getOrNull()
            val o = texto?.let { runCatching { json.parseToJsonElement(it).jsonObject }.getOrNull() }
            when {
                resp.status == HttpStatusCode.OK && o != null ->
                    LecturaInfiltraciones(items = ordenarInfiltraciones((o["items"] as? JsonArray).orEmpty()
                        .mapNotNull { (it as? JsonObject)?.let(::infiltracionDeJson) }))
                resp.status == HttpStatusCode.NotFound && o?.get("codigo") == null -> LecturaInfiltraciones(error = INFILTRACIONES_NO_DISPONIBLE)
                else -> LecturaInfiltraciones(error = (o?.get("error") as? JsonPrimitive)?.content ?: "No se pudieron cargar las infiltraciones")
            }
        } catch (e: CancellationException) { throw e } catch (_: Exception) {
            LecturaInfiltraciones(error = "Sin conexión. Revisa tu internet.")
        }
    }

    /** POST con una o varias articulaciones (borrador YA validado). */
    suspend fun registrar(pacienteId: String, citaId: String?, fecha: String, b: BorradorInfiltracion): ResultadoEscritura {
        val tk = token() ?: return SIN_SESION
        val cuerpo = cuerpoInfiltracion(pacienteId, citaId, fecha, b).toString()
        return try {
            pe.saniape.app.ui.conIndicador {
                resultado(http.post("${Supabase.SITE_URL}$RUTA") {
                    header("Authorization", "Bearer $tk"); contentType(ContentType.Application.Json); setBody(cuerpo)
                })
            }
        } catch (e: CancellationException) { throw e } catch (_: Exception) { SIN_RED }
    }

    suspend fun borrar(id: String): ResultadoEscritura {
        val tk = token() ?: return SIN_SESION
        return try {
            pe.saniape.app.ui.conIndicador {
                resultado(http.delete("${Supabase.SITE_URL}$RUTA") {
                    header("Authorization", "Bearer $tk"); contentType(ContentType.Application.Json)
                    setBody(buildJsonObject { put("id", id) }.toString())
                })
            }
        } catch (e: CancellationException) { throw e } catch (_: Exception) { SIN_RED }
    }

    /** Clínica mixta o con la opción apagada: ¿ya tiene infiltraciones? (así la pestaña queda en lectura). */
    suspend fun tieneRegistros(pacienteId: String): Boolean = try {
        Supabase.client.postgrest["infiltraciones_articulares"]
            .select(Columns.list("id")) { filter { eq("paciente_id", pacienteId) }; limit(1) }
            .decodeList<JsonObject>().isNotEmpty()
    } catch (e: CancellationException) { throw e } catch (_: Exception) { false }
}
