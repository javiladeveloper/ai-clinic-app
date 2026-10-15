package pe.saniape.app.data.staff

import io.github.jan.supabase.auth.auth
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
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put
import pe.saniape.app.data.Supabase
import pe.saniape.app.data.crearHttpClient
import pe.saniape.app.data.offline.RechazoServidor
import pe.saniape.app.data.offline.ResultadoEscritura

/** Lectura de planes: [items] o, si no se pudo, [error] para mostrar. */
data class LecturaPlanes(val items: List<PlanAlimentacion>? = null, val error: String? = null)

/** Mensaje cuando el servidor todavía no tiene el endpoint (backend viejo → 404 sin JSON). */
const val PLAN_NO_DISPONIBLE = "El plan alimentario aún no está disponible en el servidor. Intenta más tarde."

/**
 * Planes alimentarios (Nutrición) por /api/staff/plan-alimentacion y sus
 * plantillas, con el Bearer del staff. El servidor valida permiso ('pacientes'
 * o 'sesiones'), el candado de solo lectura y los datos (`validarPlan`, el
 * mismo que aquí).
 *
 * DIRECTO, sin cola offline (como [MedicionesRepo]): la pestaña necesita la
 * fila guardada para mostrarla.
 */
object PlanAlimentacionRepo {

    private const val RUTA = "/api/staff/plan-alimentacion"
    private const val RUTA_PLANTILLAS = "/api/staff/plan-alimentacion/plantillas"
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
            return ResultadoEscritura(registrada = false, rechazo = RechazoServidor(PLAN_NO_DISPONIBLE, "NO_DISPONIBLE", 404))
        }
        return r
    }

    /** Los del paciente, el más reciente primero. */
    suspend fun listar(pacienteId: String): LecturaPlanes {
        val tk = token() ?: return LecturaPlanes(error = "Tu sesión expiró. Vuelve a entrar.")
        return try {
            val resp = http.get("${Supabase.SITE_URL}$RUTA") {
                header("Authorization", "Bearer $tk")
                parameter("pacienteId", pacienteId)
            }
            val texto = runCatching { resp.bodyAsText() }.getOrNull()
            val o = texto?.let { runCatching { json.parseToJsonElement(it).jsonObject }.getOrNull() }
            when {
                resp.status == HttpStatusCode.OK && o != null ->
                    LecturaPlanes(items = ordenarPlanes((o["items"] as? JsonArray).orEmpty().mapNotNull { (it as? JsonObject)?.let(::planDeJson) }))
                resp.status == HttpStatusCode.NotFound && o == null -> LecturaPlanes(error = PLAN_NO_DISPONIBLE)
                else -> LecturaPlanes(error = (o?.get("error") as? kotlinx.serialization.json.JsonPrimitive)?.content
                    ?: "No se pudieron cargar los planes")
            }
        } catch (e: CancellationException) { throw e } catch (_: Exception) {
            LecturaPlanes(error = "Sin conexión. Revisa tu internet.")
        }
    }

    /** POST (nuevo, sin [id]) o PATCH (editar, con [id]). Borrador YA validado. En `cuerpo["plan"]` vuelve la fila. */
    suspend fun guardar(b: BorradorPlan, id: String? = null): ResultadoEscritura {
        val tk = token() ?: return SIN_SESION
        val cuerpo = cuerpoPlan(b, id).toString()
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

    suspend fun borrar(id: String): ResultadoEscritura = borrarEn(RUTA, id)

    // ── Plantillas ──

    /** Las de la clínica, por nombre. Si no se pudo leer (o el servidor no las tiene), lista vacía. */
    suspend fun plantillas(): List<PlantillaPlan> {
        val tk = token() ?: return emptyList()
        return try {
            val resp = http.get("${Supabase.SITE_URL}$RUTA_PLANTILLAS") { header("Authorization", "Bearer $tk") }
            if (resp.status != HttpStatusCode.OK) return emptyList()
            val o = json.parseToJsonElement(resp.bodyAsText()).jsonObject
            (o["items"] as? JsonArray).orEmpty().mapNotNull { (it as? JsonObject)?.let(::plantillaDeJson) }
        } catch (e: CancellationException) { throw e } catch (_: Exception) { emptyList() }
    }

    /** Guarda (o reemplaza, si ya hay una con ese nombre) la plantilla. `cuerpo["actualizada"]` dice cuál. */
    suspend fun guardarPlantilla(nombre: String, contenido: ContenidoPlanFuente): ResultadoEscritura {
        val tk = token() ?: return SIN_SESION
        return try {
            pe.saniape.app.ui.conIndicador {
                val resp = http.post("${Supabase.SITE_URL}$RUTA_PLANTILLAS") {
                    header("Authorization", "Bearer $tk")
                    contentType(ContentType.Application.Json)
                    setBody(cuerpoPlantilla(nombre, contenido).toString())
                }
                resultado(resp)
            }
        } catch (e: CancellationException) { throw e } catch (_: Exception) { SIN_RED }
    }

    suspend fun borrarPlantilla(id: String): ResultadoEscritura = borrarEn(RUTA_PLANTILLAS, id)

    private suspend fun borrarEn(ruta: String, id: String): ResultadoEscritura {
        val tk = token() ?: return SIN_SESION
        return try {
            pe.saniape.app.ui.conIndicador {
                val resp = http.delete("${Supabase.SITE_URL}$ruta") {
                    header("Authorization", "Bearer $tk")
                    contentType(ContentType.Application.Json)
                    setBody(buildJsonObject { put("id", id) }.toString())
                }
                resultado(resp)
            }
        } catch (e: CancellationException) { throw e } catch (_: Exception) { SIN_RED }
    }
}
