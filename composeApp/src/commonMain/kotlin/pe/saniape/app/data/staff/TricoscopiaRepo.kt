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
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put
import pe.saniape.app.data.Supabase
import pe.saniape.app.data.crearHttpClient
import pe.saniape.app.data.offline.RechazoServidor
import pe.saniape.app.data.offline.ResultadoEscritura

/** Lectura de evaluaciones: [items] (cronológicas) o, si no se pudo, [error] para mostrar. */
data class LecturaTricoscopia(val items: List<EvaluacionTricoscopica>? = null, val error: String? = null, val sinAcceso: Boolean = false)

/** El texto del 403 de lectura (solo 'citas': la RLS escondería todo). */
const val TRICOSCOPIA_SIN_ACCESO = "No tienes acceso a las tricoscopías de este paciente"

/**
 * 💇 Tricoscopía por /api/staff/tricoscopia, con el Bearer del staff. Mismo
 * molde que [IndicacionesRepo]: el servidor valida permiso ('pacientes' o
 * 'sesiones'), el candado de solo lectura y los datos (`validarTricoscopia`,
 * el mismo que aquí). DIRECTO, sin cola offline: la pestaña necesita la fila
 * guardada para mostrar la evolución y el informe.
 */
object TricoscopiaRepo {

    private const val RUTA = "/api/staff/tricoscopia"
    private val http = crearHttpClient()
    private val json = Json { ignoreUnknownKeys = true }

    private fun token(): String? = Supabase.client.auth.currentSessionOrNull()?.accessToken

    private val SIN_SESION = ResultadoEscritura(
        registrada = false, rechazo = RechazoServidor("Tu sesión expiró. Vuelve a entrar.", "NO_AUTENTICADO", 401),
    )
    private val SIN_RED = ResultadoEscritura(
        registrada = false, rechazo = RechazoServidor("Sin conexión. Revisa tu internet.", "SIN_RED"),
    )

    /** Las del paciente, cronológicas (la más antigua primero). */
    suspend fun listar(pacienteId: String): LecturaTricoscopia {
        val tk = token() ?: return LecturaTricoscopia(error = "Tu sesión expiró. Vuelve a entrar.")
        return try {
            val resp = http.get("${Supabase.SITE_URL}$RUTA") {
                header("Authorization", "Bearer $tk")
                parameter("pacienteId", pacienteId)
            }
            lecturaDeRespuesta(resp.status.value, runCatching { resp.bodyAsText() }.getOrNull())
        } catch (e: CancellationException) { throw e } catch (_: Exception) {
            LecturaTricoscopia(error = "Sin conexión. Revisa tu internet.")
        }
    }

    /** Respuesta del GET → lectura (pura, testeable). */
    internal fun lecturaDeRespuesta(status: Int, texto: String?): LecturaTricoscopia {
        val o = texto?.let { runCatching { json.parseToJsonElement(it).jsonObject }.getOrNull() }
        val err = (o?.get("error") as? JsonPrimitive)?.takeIf { it !is JsonNull }?.content
        return when {
            status == HttpStatusCode.OK.value && o != null -> LecturaTricoscopia(items = ordenarEvaluaciones(
                (o["items"] as? JsonArray).orEmpty().mapNotNull { (it as? JsonObject)?.let(::evaluacionDeJson) },
            ))
            status == HttpStatusCode.Forbidden.value -> LecturaTricoscopia(error = err ?: TRICOSCOPIA_SIN_ACCESO, sinAcceso = true)
            status == HttpStatusCode.NotFound.value && o?.get("codigo") == null ->
                LecturaTricoscopia(error = "Tu servidor aún no tiene la tricoscopía. Actualiza la web de la clínica.")
            else -> LecturaTricoscopia(error = err ?: "No se pudieron cargar las evaluaciones")
        }
    }

    /** POST (nueva, sin [id]) o PATCH (editar, con [id]). Borrador YA validado. En `cuerpo["evaluacion"]` vuelve la fila. */
    suspend fun guardar(b: BorradorTricoscopia, id: String? = null): ResultadoEscritura {
        val tk = token() ?: return SIN_SESION
        val cuerpo = cuerpoTricoscopia(b, id).toString()
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

    /**
     * Solo en una clínica MIXTA (GLAM): ¿ya tiene evaluaciones? y ¿de qué
     * especialidades es quien mira? Dos lecturas livianas en paralelo (la RLS
     * acota). Si falla, (false, vacío): la pestaña sale por sus atenciones.
     */
    suspend fun datosMixta(pacienteId: String, miTerapeutaId: String?): Pair<Boolean, List<String>> = coroutineScope {
        val tiene = async {
            runCatching {
                Supabase.client.postgrest["evaluaciones_tricoscopicas"]
                    .select(Columns.list("id")) { filter { eq("paciente_id", pacienteId) }; limit(1) }
                    .decodeList<JsonObject>().isNotEmpty()
            }.getOrDefault(false)
        }
        val mirando = async {
            if (miTerapeutaId == null) emptyList() else runCatching {
                Supabase.client.postgrest["terapeuta_especialidades"]
                    .select(Columns.list("especialidad_id")) { filter { eq("terapeuta_id", miTerapeutaId) } }
                    .decodeList<JsonObject>()
                    .mapNotNull { (it["especialidad_id"] as? JsonPrimitive)?.takeIf { p -> p !is JsonNull }?.content }
            }.getOrDefault(emptyList())
        }
        tiene.await() to mirando.await()
    }
}
