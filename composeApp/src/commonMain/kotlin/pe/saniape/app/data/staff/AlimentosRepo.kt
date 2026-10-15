package pe.saniape.app.data.staff

import io.github.jan.supabase.auth.auth
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
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

/** El catálogo listo para buscar (se arma UNA vez por sesión). */
class CatalogoAlimentos(val version: String, val alimentos: List<Alimento>)

/** Cómo terminó la lectura del catálogo. */
sealed interface LecturaCatalogo {
    data class Listo(val catalogo: CatalogoAlimentos) : LecturaCatalogo
    /** Backend viejo (404 sin JSON): sin buscador; el texto libre sigue. */
    data object NoDisponible : LecturaCatalogo
    data class Fallo(val error: String) : LecturaCatalogo
}

/**
 * BASE DE ALIMENTOS por la API de la web (contrato §13), con el Bearer del staff:
 *  · `GET /api/staff/alimentos?v=` — el catálogo global (~1000 filas): se baja
 *    UNA vez por sesión y se busca en memoria ([buscarAlimentos]); cero viajes
 *    por tecla. Si la `version` del servidor cambia, se usa la nueva.
 *  · `/api/staff/alimentos/clinica` — los propios de la clínica (GET/POST/PATCH/DELETE).
 * La pestaña en lectura no lo pide: los totales salen de la foto `por100` de cada ítem.
 */
object AlimentosRepo {

    private const val RUTA = "/api/staff/alimentos"
    private const val RUTA_CLINICA = "/api/staff/alimentos/clinica"
    private val http = crearHttpClient()
    private val json = Json { ignoreUnknownKeys = true }
    private val candado = Mutex()

    /** Caché de la sesión (el catálogo es global: no depende de la clínica). */
    private var cache: CatalogoAlimentos? = null

    private fun token(): String? = Supabase.client.auth.currentSessionOrNull()?.accessToken

    private val SIN_SESION = ResultadoEscritura(
        registrada = false, rechazo = RechazoServidor("Tu sesión expiró. Vuelve a entrar.", "NO_AUTENTICADO", 401),
    )
    private val SIN_RED = ResultadoEscritura(
        registrada = false, rechazo = RechazoServidor("Sin conexión. Revisa tu internet.", "SIN_RED"),
    )

    /** El catálogo (de la caché de la sesión o del servidor). Dos editores a la vez piden UNA vez. */
    suspend fun catalogo(): LecturaCatalogo = candado.withLock {
        cache?.let { return@withLock LecturaCatalogo.Listo(it) }
        val tk = token() ?: return@withLock LecturaCatalogo.Fallo("Tu sesión expiró. Vuelve a entrar.")
        try {
            val resp = http.get("${Supabase.SITE_URL}$RUTA") {
                header("Authorization", "Bearer $tk")
                parameter("v", VERSION_CATALOGO)
            }
            val texto = runCatching { resp.bodyAsText() }.getOrNull()
            // Armar ~1000 alimentos fuera del hilo de la UI.
            val o = withContext(Dispatchers.Default) { texto?.let { runCatching { json.parseToJsonElement(it).jsonObject }.getOrNull() } }
            when {
                resp.status == HttpStatusCode.OK && o != null -> {
                    val cat = withContext(Dispatchers.Default) { catalogoDeJson(o) }
                    cache = cat
                    LecturaCatalogo.Listo(cat)
                }
                resp.status == HttpStatusCode.NotFound && o == null -> LecturaCatalogo.NoDisponible
                else -> LecturaCatalogo.Fallo(
                    (o?.get("error") as? JsonPrimitive)?.content ?: "No se pudo cargar la base de alimentos",
                )
            }
        } catch (e: CancellationException) { throw e } catch (_: Exception) {
            LecturaCatalogo.Fallo("Sin conexión: la base de alimentos no se pudo cargar.")
        }
    }

    /** Los propios de la clínica. null = no se pudieron leer (o el servidor no tiene la ruta). */
    suspend fun propios(): List<FilaAlimentoClinica>? {
        val tk = token() ?: return null
        return try {
            val resp = http.get("${Supabase.SITE_URL}$RUTA_CLINICA") { header("Authorization", "Bearer $tk") }
            if (resp.status != HttpStatusCode.OK) return null
            val o = json.parseToJsonElement(resp.bodyAsText()).jsonObject
            (o["items"] as? JsonArray).orEmpty().mapNotNull { (it as? JsonObject)?.let(::filaClinicaDeJson) }
        } catch (e: CancellationException) { throw e } catch (_: Exception) { null }
    }

    /**
     * POST (nuevo, sin [id]) o PATCH (con [id]). Borrador YA validado. La fila
     * guardada vuelve en `cuerpo["alimento"]` ([filaGuardada]). Un nombre que ya
     * existe en la clínica → rechazo `DUPLICADO` (409) con el mensaje del servidor.
     */
    suspend fun guardarPropio(b: BorradorAlimentoClinica, id: String? = null): ResultadoEscritura {
        val tk = token() ?: return SIN_SESION
        val cuerpo = cuerpoAlimentoClinica(b, id).toString()
        return try {
            pe.saniape.app.ui.conIndicador {
                val resp = if (id == null) {
                    http.post("${Supabase.SITE_URL}$RUTA_CLINICA") {
                        header("Authorization", "Bearer $tk"); contentType(ContentType.Application.Json); setBody(cuerpo)
                    }
                } else {
                    http.patch("${Supabase.SITE_URL}$RUTA_CLINICA") {
                        header("Authorization", "Bearer $tk"); contentType(ContentType.Application.Json); setBody(cuerpo)
                    }
                }
                resultado(resp.status, runCatching { resp.bodyAsText() }.getOrNull())
            }
        } catch (e: CancellationException) { throw e } catch (_: Exception) { SIN_RED }
    }

    suspend fun borrarPropio(id: String): ResultadoEscritura {
        val tk = token() ?: return SIN_SESION
        return try {
            pe.saniape.app.ui.conIndicador {
                val resp = http.delete("${Supabase.SITE_URL}$RUTA_CLINICA") {
                    header("Authorization", "Bearer $tk")
                    contentType(ContentType.Application.Json)
                    setBody(buildJsonObject { put("id", id) }.toString())
                }
                resultado(resp.status, runCatching { resp.bodyAsText() }.getOrNull())
            }
        } catch (e: CancellationException) { throw e } catch (_: Exception) { SIN_RED }
    }

    private fun resultado(status: HttpStatusCode, cuerpo: String?): ResultadoEscritura {
        val r = AtencionRepo.resultadoDeRespuesta(status.value, cuerpo)
        if (status == HttpStatusCode.NotFound && r.rechazo?.codigo == null) {
            return ResultadoEscritura(
                registrada = false,
                rechazo = RechazoServidor("Los alimentos de la clínica aún no están disponibles en el servidor.", "NO_DISPONIBLE", 404),
            )
        }
        return r
    }
}

/** La fila que devolvió POST/PATCH de un alimento propio. */
fun filaGuardada(r: ResultadoEscritura): FilaAlimentoClinica? =
    (r.cuerpo?.get("alimento") as? JsonObject)?.let(::filaClinicaDeJson)

/** Respuesta de GET /api/staff/alimentos → catálogo (las filas sin id o nombre se saltan). */
fun catalogoDeJson(o: JsonObject): CatalogoAlimentos = CatalogoAlimentos(
    version = (o["version"] as? JsonPrimitive)?.content ?: VERSION_CATALOGO,
    alimentos = (o["items"] as? JsonArray).orEmpty().mapNotNull { (it as? JsonObject)?.let(::alimentoDeCatalogo) },
)
