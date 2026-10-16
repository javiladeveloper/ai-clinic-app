package pe.saniape.app.data.staff

import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.Columns
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.client.request.post
import io.ktor.client.request.put
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.contentType
import kotlinx.coroutines.CancellationException
import kotlinx.datetime.Clock
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import pe.saniape.app.data.Supabase
import pe.saniape.app.data.crearHttpClient
import pe.saniape.app.data.offline.RechazoServidor
import pe.saniape.app.data.offline.ResultadoEscritura

/**
 * 🧠 Certificado de salud mental contra el servidor (contrato §8 de
 * docs/psiquiatria-documentos-medicos.md en la web): los MISMOS endpoints que
 * la web (`/api/staff/certificado/…`), con el Bearer del staff. Ahí, en el
 * trigger y en la RLS se valida quién firma; la app no reimplementa nada de eso.
 *
 * DIRECTO, sin cola offline: emitir lleva `claveCliente` (un reintento devuelve
 * el ya emitido, `repetido: true`) y lo demás se repite a mano sin daño.
 *
 * TOLERANCIA: estos endpoints salen en el mismo deploy que esta versión. Un
 * servidor que todavía no los tiene responde el 404 de Next (sin `codigo`):
 * [disponible] da false y la app no ofrece el certificado en ningún lado.
 */
object CertificadoRepo {

    private const val BASE = "/api/staff/certificado"
    private const val MSJ_SIN_RED = "Sin conexión. Revisa tu internet."
    private val http = crearHttpClient()

    private fun token(): String? = Supabase.client.auth.currentSessionOrNull()?.accessToken

    private val sinSesion = ResultadoEscritura(
        registrada = false, rechazo = RechazoServidor("Tu sesión expiró. Vuelve a entrar.", "NO_AUTENTICADO", 401),
    )
    private val sinRed = ResultadoEscritura(registrada = false, rechazo = RechazoServidor(MSJ_SIN_RED, "SIN_RED"))

    /** ¿La respuesta dice "este servidor no tiene la ruta"? (404/405 de Next, sin `codigo` nuestro). */
    internal fun esRutaInexistente(status: Int, codigo: String?): Boolean = (status == 404 || status == 405) && codigo == null

    // ── ¿El servidor ya tiene el certificado? ────────────────────────────────

    // @Volatile: la ficha y la consulta pueden preguntar a la vez desde corrutinas distintas.
    @kotlin.concurrent.Volatile private var disponibleCache: Boolean? = null
    @kotlin.concurrent.Volatile private var disponibleEn: Long = 0
    /** De quién es la respuesta guardada (otro usuario puede tener otro permiso). */
    @kotlin.concurrent.Volatile private var disponibleDe: String? = null
    /** Un "no" se vuelve a preguntar pasado este tiempo (el deploy puede llegar con la app abierta). */
    private const val REINTENTO_NO_MS = 10 * 60 * 1000L

    /**
     * ¿El servidor tiene `/api/staff/certificado/…` y quien mira puede usarlo?
     * `GET plantilla` (liviano, sin parámetros): 2xx → sí; 404 de Next o 403 →
     * no; sin red / sesión vencida → null (no se sabe: no se ofrece y se
     * vuelve a preguntar en la próxima pantalla).
     */
    suspend fun disponible(): Boolean? {
        val ahora = Clock.System.now().toEpochMilliseconds()
        val usuario = Supabase.client.auth.currentUserOrNull()?.id
        if (usuario == disponibleDe) disponibleCache?.let { if (it || ahora - disponibleEn < REINTENTO_NO_MS) return it }
        val tk = token() ?: return null
        val r = try {
            val resp = http.get("${Supabase.SITE_URL}$BASE/plantilla") { header("Authorization", "Bearer $tk") }
            val res = AtencionRepo.resultadoDeRespuesta(resp.status.value, runCatching { resp.bodyAsText() }.getOrNull())
            when {
                res.registrada -> true
                esRutaInexistente(resp.status.value, res.codigo) || resp.status.value == 403 -> false
                else -> null
            }
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            null
        }
        if (r != null) { disponibleCache = r; disponibleEn = ahora; disponibleDe = usuario }
        return r
    }

    // ── Lecturas ─────────────────────────────────────────────────────────────

    sealed class Lista {
        data class Ok(val items: List<CertificadoApp>) : Lista()
        /** El servidor aún no tiene el certificado: la tarjeta no se muestra. */
        data object NoDisponible : Lista()
        data class Fallo(val mensaje: String) : Lista()
    }

    /** Los del paciente, el más nuevo primero (incluye anulados). Recepción, sin el detalle. */
    suspend fun listar(pacienteId: String): Lista {
        val tk = token() ?: return Lista.Fallo("Tu sesión expiró. Vuelve a entrar.")
        return try {
            val resp = http.get("${Supabase.SITE_URL}$BASE") {
                header("Authorization", "Bearer $tk")
                parameter("pacienteId", pacienteId)
            }
            aLista(resp.status.value, runCatching { resp.bodyAsText() }.getOrNull())
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            Lista.Fallo(MSJ_SIN_RED)
        }
    }

    internal fun aLista(status: Int, cuerpo: String?): Lista {
        val r = AtencionRepo.resultadoDeRespuesta(status, cuerpo)
        if (!r.registrada) {
            return if (esRutaInexistente(status, r.codigo)) Lista.NoDisponible
            else Lista.Fallo(r.rechazo?.error ?: "No se pudieron cargar los certificados.")
        }
        val items = (r.cuerpo?.get("items") as? JsonArray) ?: return Lista.Fallo("Respuesta inesperada del servidor.")
        return Lista.Ok(items.mapNotNull { (it as? JsonObject)?.let(::certificadoDeJson) }.distinctBy { it.id })
    }

    sealed class Prefill {
        data class Ok(val datos: PrefillCertificado) : Prefill()
        data object NoDisponible : Prefill()
        data class Error(val mensaje: String) : Prefill()
    }

    /** Lo que el formulario necesita (paciente, hoy y lugar en la zona de la sede, firmantes, plantillas). */
    suspend fun prefill(pacienteId: String, citaId: String?): Prefill {
        val tk = token() ?: return Prefill.Error("Tu sesión expiró. Vuelve a entrar.")
        return try {
            val resp = http.get("${Supabase.SITE_URL}$BASE/prefill") {
                header("Authorization", "Bearer $tk")
                parameter("pacienteId", pacienteId)
                if (!citaId.isNullOrBlank()) parameter("citaId", citaId)
            }
            aPrefill(resp.status.value, runCatching { resp.bodyAsText() }.getOrNull())
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            Prefill.Error("Sin conexión: no se pudo preparar el certificado")
        }
    }

    internal fun aPrefill(status: Int, cuerpo: String?): Prefill {
        val r = AtencionRepo.resultadoDeRespuesta(status, cuerpo)
        if (!r.registrada) {
            return if (esRutaInexistente(status, r.codigo)) Prefill.NoDisponible
            else Prefill.Error(r.rechazo?.error ?: "No se pudo preparar el certificado")
        }
        return r.cuerpo?.let(::prefillCertificadoDeJson)?.let { Prefill.Ok(it) }
            ?: Prefill.Error("No se pudo preparar el certificado")
    }

    /**
     * Clínica MIXTA: ¿el paciente ya tiene certificados? Lectura chica por la
     * RLS; sin la tabla (base vieja) o sin red → false.
     */
    suspend fun tieneCertificados(pacienteId: String): Boolean = try {
        Supabase.client.postgrest["certificados_medicos"]
            .select(Columns.list("id")) { filter { eq("paciente_id", pacienteId) }; limit(1) }
            .decodeList<JsonObject>().isNotEmpty()
    } catch (e: CancellationException) { throw e } catch (_: Exception) { false }

    // ── Escrituras ───────────────────────────────────────────────────────────

    /** Emite ([cuerpo] = cuerpoEmitirCertificado). `cuerpo.certificado` = la fila; `cuerpo.repetido`. */
    suspend fun emitir(cuerpo: JsonObject): ResultadoEscritura = enviar("$BASE/emitir", cuerpo)

    /** Anula (queda en el historial con el motivo). Solo quien lo emitió o el Admin. */
    suspend fun anular(id: String, motivo: String): ResultadoEscritura =
        enviar("$BASE/anular", buildJsonObject { put("id", id); put("motivo", motivo.trim()) })

    /** ¿El paciente lo ve en su app? (409 ANULADO / 403 PROFESIONAL_AJENO si no). */
    suspend fun visibilidad(id: String, visible: Boolean): ResultadoEscritura =
        enviar("$BASE/visibilidad", buildJsonObject { put("id", id); put("visible", visible) })

    /** "Mi texto predeterminado". [terapeutaId] null = el de la clínica (solo Admin). */
    suspend fun guardarPlantilla(terapeutaId: String?, texto: String): ResultadoEscritura =
        enviar("$BASE/plantilla", buildJsonObject {
            put("terapeutaId", terapeutaId?.let { JsonPrimitive(it) } ?: JsonNull)
            put("texto", texto.trim())
        }, metodo = "PUT")

    /** Vuelve al texto de la clínica (o al de fábrica). */
    suspend fun restablecerPlantilla(terapeutaId: String?): ResultadoEscritura =
        enviar("$BASE/plantilla", buildJsonObject {
            put("terapeutaId", terapeutaId?.let { JsonPrimitive(it) } ?: JsonNull)
        }, metodo = "DELETE")

    private suspend fun enviar(endpoint: String, cuerpo: JsonObject, metodo: String = "POST"): ResultadoEscritura {
        val tk = token() ?: return sinSesion
        return try {
            val url = "${Supabase.SITE_URL}$endpoint"
            val resp: HttpResponse = when (metodo) {
                "PUT" -> http.put(url) { header("Authorization", "Bearer $tk"); contentType(ContentType.Application.Json); setBody(cuerpo.toString()) }
                "DELETE" -> http.delete(url) { header("Authorization", "Bearer $tk"); contentType(ContentType.Application.Json); setBody(cuerpo.toString()) }
                else -> http.post(url) { header("Authorization", "Bearer $tk"); contentType(ContentType.Application.Json); setBody(cuerpo.toString()) }
            }
            val r = AtencionRepo.resultadoDeRespuesta(resp.status.value, runCatching { resp.bodyAsText() }.getOrNull())
            if (esRutaInexistente(resp.status.value, r.codigo)) {
                ResultadoEscritura(
                    registrada = false,
                    rechazo = RechazoServidor("El certificado de salud mental todavía no está disponible. Actualiza más tarde.", "NO_DISPONIBLE", 404),
                )
            } else r
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            sinRed
        }
    }
}
