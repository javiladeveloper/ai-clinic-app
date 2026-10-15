package pe.saniape.app.data.staff

import io.github.jan.supabase.auth.auth
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.contentType
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.datetime.Clock
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put
import pe.saniape.app.data.Supabase
import pe.saniape.app.data.crearHttpClient
import pe.saniape.app.data.offline.RechazoServidor
import pe.saniape.app.data.offline.ResultadoEscritura
import pe.saniape.app.data.offline.enviarOEncolarDetalle

/** Resultado de pedir una lista de rutinas. */
sealed class CargaRutinas {
    data class Ok(val rutinas: List<RutinaEjercicios>) : CargaRutinas()
    /** La web aún no tiene la ruta (404 sin JSON) o la clínica no hace fisioterapia: se oculta. */
    data object NoDisponible : CargaRutinas()
    data class Error(val mensaje: String) : CargaRutinas()
}

/**
 * Rutinas de ejercicios (staff): las de la clínica y las base de Sania. Gemelo de
 * `components/fisio/RutinasEjerciciosModal.tsx` (lecturas y escrituras).
 *
 * Lecturas con el Bearer del staff contra la web (no a Supabase directo): las base
 * viven en el código de la web y la app no las duplica. Las base se guardan 1 h en
 * memoria (`Cache-Control: max-age=3600`); las de la clínica se piden cada vez que
 * se abre el buscador (cambian cuando un fisio guarda una).
 *
 * Usar una rutina va por la cola offline (como agregar/copiar: no duplica).
 * Guardar y borrar van DIRECTO: necesitan la respuesta (`actualizada`).
 * `clinica_id` NUNCA se manda.
 */
object RutinasEjerciciosRepo {

    private const val RUTA_PLANTILLAS = "/api/staff/ejercicios/plantillas"
    private const val RUTA_BASE = "/api/staff/ejercicios/rutinas-base"
    private const val RUTA_PLAN = "/api/staff/ejercicios/plan"
    private const val MSJ_SIN_RED = "Sin conexión. Revisa tu internet."

    private val http = crearHttpClient()

    /**
     * false cuando el servidor dijo que la función no existe (web vieja) o está
     * apagada para la clínica: los botones de rutina se ocultan hasta reiniciar la app.
     */
    var disponible: Boolean = true
        private set

    private var baseEnMemoria: Triple<String, Long, List<RutinaEjercicios>>? = null
    private const val VIGENCIA_BASE_MS = 60L * 60 * 1000

    private fun token(): String? = Supabase.client.auth.currentSessionOrNull()?.accessToken
    private fun usuario(): String = Supabase.client.auth.currentUserOrNull()?.id.orEmpty()

    private fun obj(texto: String?): JsonObject? = texto?.let { runCatching { Json.parseToJsonElement(it).jsonObject }.getOrNull() }

    private suspend fun leer(ruta: String, parsear: (String) -> List<RutinaEjercicios>?): CargaRutinas {
        val tk = token() ?: return CargaRutinas.Error("Tu sesión expiró. Vuelve a entrar.")
        val resp = try {
            withTimeoutOrNull(15_000) { http.get("${Supabase.SITE_URL}$ruta") { header("Authorization", "Bearer $tk") } }
        } catch (e: CancellationException) { throw e } catch (_: Exception) { null } ?: return CargaRutinas.Error(MSJ_SIN_RED)
        val texto = runCatching { resp.bodyAsText() }.getOrNull()
        val status = resp.status.value
        if (status in 200..299) {
            return parsear(texto.orEmpty())?.let { CargaRutinas.Ok(it) } ?: CargaRutinas.Error("No se pudieron leer las rutinas.")
        }
        val o = obj(texto)
        val codigo = (o?.get("codigo") as? JsonPrimitive)?.contentOrNull
        if (funcionNoDisponible(status, codigo, o?.containsKey("error") == true)) {
            disponible = false
            return CargaRutinas.NoDisponible
        }
        return CargaRutinas.Error((o?.get("error") as? JsonPrimitive)?.contentOrNull ?: "No se pudieron cargar las rutinas.")
    }

    /** Las rutinas de la clínica, por nombre. */
    suspend fun deClinica(): CargaRutinas = leer(RUTA_PLANTILLAS, ::parsearRutinasClinica)

    /** Las rutinas base de Sania, resueltas contra la biblioteca (1 h en memoria por usuario). */
    suspend fun base(): CargaRutinas {
        val ahora = Clock.System.now().toEpochMilliseconds()
        baseEnMemoria?.takeIf { it.first == usuario() && ahora - it.second < VIGENCIA_BASE_MS }?.let { return CargaRutinas.Ok(it.third) }
        val r = leer(RUTA_BASE, ::parsearRutinasBase)
        if (r is CargaRutinas.Ok && r.rutinas.isNotEmpty()) baseEnMemoria = Triple(usuario(), ahora, r.rutinas)
        return r
    }

    /** "📋 Usar": suma al plan del destino los ejercicios de la rutina con SU dosis (no duplica). */
    suspend fun usar(pacienteId: String, r: RutinaEjercicios, destino: DestinoEjercicios): ResultadoEscritura =
        enviarOEncolarDetalle("ejercicios:plan:rutina", RUTA_PLAN, cuerpoUsarRutina(pacienteId, r, destino))

    private suspend fun directo(bloque: suspend (String) -> Pair<Int, String?>): ResultadoEscritura {
        val tk = token()
            ?: return ResultadoEscritura(registrada = false, rechazo = RechazoServidor("Tu sesión expiró. Vuelve a entrar.", "NO_AUTENTICADO", 401))
        return try {
            pe.saniape.app.ui.conIndicador {
                val (status, texto) = bloque(tk)
                AtencionRepo.resultadoDeRespuesta(status, texto)
            }
        } catch (e: CancellationException) { throw e } catch (_: Exception) {
            ResultadoEscritura(registrada = false, rechazo = RechazoServidor(MSJ_SIN_RED, "SIN_RED"))
        }
    }

    /** "💾 Guardar como rutina" desde un plan. Mismo nombre = la reemplaza (`actualizada: true`). */
    suspend fun guardar(
        nombre: String, descripcion: String, desdePlanId: String, indicacionesGenerales: String?, precauciones: String?,
    ): ResultadoEscritura = directo { tk ->
        val resp = http.post("${Supabase.SITE_URL}$RUTA_PLANTILLAS") {
            header("Authorization", "Bearer $tk")
            contentType(ContentType.Application.Json)
            setBody(cuerpoGuardarRutina(nombre, descripcion, desdePlanId, indicacionesGenerales, precauciones).toString())
        }
        resp.status.value to runCatching { resp.bodyAsText() }.getOrNull()
    }

    /** Borra una rutina de la clínica. Los planes que ya la usaron no cambian. */
    suspend fun borrar(id: String): ResultadoEscritura = directo { tk ->
        val resp = http.delete("${Supabase.SITE_URL}$RUTA_PLANTILLAS") {
            header("Authorization", "Bearer $tk")
            contentType(ContentType.Application.Json)
            setBody(buildJsonObject { put("id", id) }.toString())
        }
        resp.status.value to runCatching { resp.bodyAsText() }.getOrNull()
    }
}
