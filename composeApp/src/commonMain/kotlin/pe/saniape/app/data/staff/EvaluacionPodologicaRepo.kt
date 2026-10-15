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
data class LecturaEvaluacionesPodo(val items: List<EvaluacionPodologica>? = null, val error: String? = null)

/**
 * 🦶 Evaluación del pie por /api/staff/evaluacion-podologica (+ /sugerencias),
 * con el Bearer del staff. Mismo molde que [IndicacionesRepo]: el servidor
 * valida permiso ('pacientes' o 'sesiones' para escribir), el candado de solo
 * lectura y los datos, y CALCULA el riesgo IWGDF. DIRECTO, sin cola offline:
 * la pestaña necesita la fila guardada (con su riesgo) para mostrarla e imprimirla.
 */
object EvaluacionPodologicaRepo {

    private const val RUTA = "/api/staff/evaluacion-podologica"
    private const val RUTA_SUGERENCIAS = "/api/staff/evaluacion-podologica/sugerencias"
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
            return ResultadoEscritura(registrada = false, rechazo = RechazoServidor(EVALUACION_PIE_NO_DISPONIBLE, "NO_DISPONIBLE", 404))
        }
        return r
    }

    /** Las del paciente, la más reciente primero. */
    suspend fun listar(pacienteId: String): LecturaEvaluacionesPodo {
        val tk = token() ?: return LecturaEvaluacionesPodo(error = "Tu sesión expiró. Vuelve a entrar.")
        return try {
            val resp = http.get("${Supabase.SITE_URL}$RUTA") {
                header("Authorization", "Bearer $tk")
                parameter("pacienteId", pacienteId)
            }
            val texto = runCatching { resp.bodyAsText() }.getOrNull()
            val o = texto?.let { runCatching { json.parseToJsonElement(it).jsonObject }.getOrNull() }
            when {
                resp.status == HttpStatusCode.OK && o != null ->
                    LecturaEvaluacionesPodo(items = ordenarEvaluacionesPodo((o["items"] as? JsonArray).orEmpty()
                        .mapNotNull { (it as? JsonObject)?.let(::evaluacionPodoDeJson) }))
                resp.status == HttpStatusCode.NotFound && o?.get("codigo") == null -> LecturaEvaluacionesPodo(error = EVALUACION_PIE_NO_DISPONIBLE)
                else -> LecturaEvaluacionesPodo(error = (o?.get("error") as? JsonPrimitive)?.content
                    ?: "No se pudieron cargar las evaluaciones")
            }
        } catch (e: CancellationException) { throw e } catch (_: Exception) {
            LecturaEvaluacionesPodo(error = "Sin conexión. Revisa tu internet.")
        }
    }

    /** POST (nueva, sin [id]) o PATCH (editar, con [id]). Borrador YA validado. En `cuerpo["evaluacion"]` vuelve la fila. */
    suspend fun guardar(b: BorradorEvaluacionPodo, id: String? = null): ResultadoEscritura {
        val tk = token() ?: return SIN_SESION
        val cuerpo = cuerpoEvaluacionPodo(b, id).toString()
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

    /** Caché de la sesión por clínica: UN viaje al abrir el editor. */
    private val cacheSugerencias = mutableMapOf<String, SugerenciasPodo>()

    /** Lo que la clínica ya escribió (calzado y observaciones). Si falla o no existe la ruta: vacío (chips base). */
    suspend fun sugerencias(clinicaId: String?): SugerenciasPodo {
        val clinica = clinicaId?.takeIf { it.isNotBlank() }
        if (clinica != null) cacheSugerencias[clinica]?.let { return it }
        val tk = token() ?: return SugerenciasPodo()
        return try {
            val resp = http.get("${Supabase.SITE_URL}$RUTA_SUGERENCIAS") { header("Authorization", "Bearer $tk") }
            if (resp.status != HttpStatusCode.OK) return SugerenciasPodo()
            val o = runCatching { json.parseToJsonElement(resp.bodyAsText()).jsonObject }.getOrNull() ?: return SugerenciasPodo()
            sugerenciasPodoDeJson(o).also { if (clinica != null) cacheSugerencias[clinica] = it }
        } catch (e: CancellationException) { throw e } catch (_: Exception) { SugerenciasPodo() }
    }

    /** Tras guardar: la próxima vez se vuelve a pedir (ya aprendió lo nuevo). */
    fun olvidarSugerencias() { cacheSugerencias.clear() }

    /**
     * Clínica MIXTA con podología: ¿el paciente ya tiene evaluaciones del pie?
     * (así no pierde la pestaña si cambió la especialidad de sus citas). Una
     * lectura chica por el índice del paciente; sin la tabla (backend viejo) o
     * sin red → false.
     */
    suspend fun tieneEvaluaciones(pacienteId: String): Boolean = try {
        Supabase.client.postgrest["evaluaciones_podologicas"]
            .select(Columns.list("id")) { filter { eq("paciente_id", pacienteId) }; limit(1) }
            .decodeList<JsonObject>().isNotEmpty()
    } catch (e: CancellationException) { throw e } catch (_: Exception) { false }
}
