package pe.saniape.app.data.staff

import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.Columns
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.client.request.patch
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

/** Lectura de evaluaciones: [items] o, si no se pudo, [error] para mostrar. */
data class LecturaEvaluacionesReuma(val items: List<EvaluacionReumatologica>? = null, val error: String? = null)

/**
 * 🦴 Evaluación articular por /api/staff/evaluacion-reumatologica, con el Bearer
 * del staff. Mismo molde que [EvaluacionPodologicaRepo]: el servidor valida
 * permiso, el candado de solo lectura y los datos, y CALCULA conteos e índices.
 * DIRECTO, sin cola offline: la pestaña necesita la fila guardada con su índice.
 */
object EvaluacionReumatologicaRepo {

    private const val RUTA = "/api/staff/evaluacion-reumatologica"
    private val http = crearHttpClient()
    private val json = Json { ignoreUnknownKeys = true }

    private fun token(): String? = Supabase.client.auth.currentSessionOrNull()?.accessToken

    private val SIN_SESION = ResultadoEscritura(
        registrada = false, rechazo = RechazoServidor("Tu sesión expiró. Vuelve a entrar.", "NO_AUTENTICADO", 401),
    )
    private val SIN_RED = ResultadoEscritura(
        registrada = false, rechazo = RechazoServidor("Sin conexión. Revisa tu internet.", "SIN_RED"),
    )

    /** Respuesta → resultado; un 404 SIN el JSON del API es un servidor que aún no tiene la ruta. */
    private suspend fun resultado(resp: HttpResponse): ResultadoEscritura {
        val cuerpo = runCatching { resp.bodyAsText() }.getOrNull()
        val r = AtencionRepo.resultadoDeRespuesta(resp.status.value, cuerpo)
        if (resp.status == HttpStatusCode.NotFound && r.rechazo?.codigo == null) {
            return ResultadoEscritura(registrada = false, rechazo = RechazoServidor(EVALUACION_REUMA_NO_DISPONIBLE, "NO_DISPONIBLE", 404))
        }
        return r
    }

    /** Las del paciente, la más reciente primero. */
    suspend fun listar(pacienteId: String): LecturaEvaluacionesReuma {
        val tk = token() ?: return LecturaEvaluacionesReuma(error = "Tu sesión expiró. Vuelve a entrar.")
        return try {
            val resp = http.get("${Supabase.SITE_URL}$RUTA") {
                header("Authorization", "Bearer $tk")
                parameter("pacienteId", pacienteId)
            }
            val texto = runCatching { resp.bodyAsText() }.getOrNull()
            val o = texto?.let { runCatching { json.parseToJsonElement(it).jsonObject }.getOrNull() }
            when {
                resp.status == HttpStatusCode.OK && o != null ->
                    LecturaEvaluacionesReuma(items = ordenarEvaluacionesReuma((o["items"] as? JsonArray).orEmpty()
                        .mapNotNull { (it as? JsonObject)?.let(::evaluacionReumaDeJson) }))
                resp.status == HttpStatusCode.NotFound && o?.get("codigo") == null -> LecturaEvaluacionesReuma(error = EVALUACION_REUMA_NO_DISPONIBLE)
                else -> LecturaEvaluacionesReuma(error = (o?.get("error") as? JsonPrimitive)?.content
                    ?: "No se pudieron cargar las evaluaciones")
            }
        } catch (e: CancellationException) { throw e } catch (_: Exception) {
            LecturaEvaluacionesReuma(error = "Sin conexión. Revisa tu internet.")
        }
    }

    /** POST (nueva, sin [id]) o PATCH (editar, con [id]). Borrador YA validado. En `cuerpo["evaluacion"]` vuelve la fila. */
    suspend fun guardar(b: BorradorEvaluacionReuma, id: String? = null): ResultadoEscritura {
        val tk = token() ?: return SIN_SESION
        val cuerpo = cuerpoEvaluacionReuma(b, id).toString()
        return try {
            pe.saniape.app.ui.conIndicador {
                val resp = if (id == null) {
                    http.post("${Supabase.SITE_URL}$RUTA") {
                        header("Authorization", "Bearer $tk"); contentType(ContentType.Application.Json); setBody(cuerpo)
                    }
                } else {
                    http.patch("${Supabase.SITE_URL}$RUTA") {
                        header("Authorization", "Bearer $tk"); contentType(ContentType.Application.Json); setBody(cuerpo)
                    }
                }
                resultado(resp)
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
                resultado(resp)
            }
        } catch (e: CancellationException) { throw e } catch (_: Exception) { SIN_RED }
    }

    /**
     * Clínica MIXTA con reumatología: ¿el paciente ya tiene evaluaciones articulares?
     * (así no pierde la pestaña si cambió la especialidad de sus citas). Una
     * lectura chica por el índice del paciente; sin la tabla (backend viejo) o
     * sin red → false.
     */
    suspend fun tieneEvaluaciones(pacienteId: String): Boolean = try {
        Supabase.client.postgrest["evaluaciones_reumatologicas"]
            .select(Columns.list("id")) { filter { eq("paciente_id", pacienteId) }; limit(1) }
            .decodeList<JsonObject>().isNotEmpty()
    } catch (e: CancellationException) { throw e } catch (_: Exception) { false }
}
