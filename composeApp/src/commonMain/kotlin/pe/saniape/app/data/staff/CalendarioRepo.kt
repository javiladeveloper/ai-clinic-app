package pe.saniape.app.data.staff

import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.Columns
import io.github.jan.supabase.postgrest.query.Order
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.request.header
import io.ktor.client.request.request
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpMethod
import io.ktor.http.contentType
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.put
import pe.saniape.app.data.Supabase
import pe.saniape.app.data.crearHttpClient
import pe.saniape.app.data.offline.RechazoServidor
import pe.saniape.app.data.offline.ResultadoEscritura

/**
 * Traer la agenda de Google Calendar: los MISMOS endpoints que la web
 * (/api/staff/calendario/…). Solo Admin; el servidor lo valida (SOLO_ADMIN) y
 * pone el candado de solo lectura en cada escritura.
 *
 * Cliente propio con esperas largas: leer un calendario de años y armar la vista
 * previa puede tardar más de un minuto, y la importación hasta ~4 min (el
 * cliente común corta a los 10 s sin respuesta).
 */
object CalendarioRepo {
    private val http = crearHttpClient().config {
        install(HttpTimeout) {
            requestTimeoutMillis = 300_000
            socketTimeoutMillis = 300_000
            connectTimeoutMillis = 20_000
        }
    }

    private const val BASE = "/api/staff/calendario"

    private suspend fun token(): String? = Supabase.client.auth.currentSessionOrNull()?.accessToken

    private suspend fun pedir(metodo: HttpMethod, ruta: String, cuerpo: JsonObject? = null): ResultadoEscritura {
        val tk = token() ?: return ResultadoEscritura(
            registrada = false, rechazo = RechazoServidor("Tu sesión expiró. Vuelve a entrar.", "NO_AUTENTICADO", 401),
        )
        return try {
            val resp = http.request("${Supabase.SITE_URL}$ruta") {
                method = metodo
                header("Authorization", "Bearer $tk")
                if (cuerpo != null) {
                    contentType(ContentType.Application.Json)
                    setBody(cuerpo.toString())
                }
            }
            AtencionRepo.resultadoDeRespuesta(resp.status.value, runCatching { resp.bodyAsText() }.getOrNull())
        } catch (e: kotlin.coroutines.cancellation.CancellationException) {
            throw e
        } catch (e: Exception) {
            ResultadoEscritura(registrada = false, rechazo = RechazoServidor("Sin conexión o el servidor tardó demasiado. Inténtalo otra vez.", "SIN_RED"))
        }
    }

    suspend fun estado(): ResultadoEscritura = pedir(HttpMethod.Get, BASE)

    suspend fun conectar(): ResultadoEscritura = pedir(HttpMethod.Post, "$BASE/conectar", JsonObject(emptyMap()))

    suspend fun desconectar(conexionId: String): ResultadoEscritura =
        pedir(HttpMethod.Delete, "$BASE?conexionId=${pe.saniape.app.ui.urlEncode(conexionId)}")

    suspend fun calendarios(conexionId: String): ResultadoEscritura =
        pedir(HttpMethod.Get, "$BASE/calendarios?conexionId=${pe.saniape.app.ui.urlEncode(conexionId)}")

    /** "Traer este": el calendario y de qué profesional son sus citas. */
    suspend fun elegir(conexionId: String, cal: CalendarioGoogle, terapeutaId: String?): ResultadoEscritura =
        pedir(HttpMethod.Post, "$BASE/fuente", buildJsonObject {
            put("conexionId", conexionId); put("calendarioId", cal.id); put("nombre", cal.nombre); put("terapeutaId", terapeutaId)
        })

    suspend fun cambiarProfesional(fuenteId: String, terapeutaId: String?): ResultadoEscritura =
        pedir(HttpMethod.Patch, "$BASE/fuente", buildJsonObject { put("id", fuenteId); put("terapeutaId", terapeutaId) })

    suspend fun recordatorios(fuenteId: String, valor: Boolean): ResultadoEscritura =
        pedir(HttpMethod.Patch, "$BASE/fuente", buildJsonObject { put("id", fuenteId); put("recordatoriosPacientes", valor) })

    suspend fun quitar(fuenteId: String): ResultadoEscritura =
        pedir(HttpMethod.Delete, "$BASE/fuente?id=${pe.saniape.app.ui.urlEncode(fuenteId)}")

    suspend fun sincronizar(fuenteId: String?): ResultadoEscritura =
        pedir(HttpMethod.Post, "$BASE/sincronizar", buildJsonObject { fuenteId?.let { put("fuenteId", it) } })

    suspend fun vistaPrevia(fuenteId: String, config: ConfigFuenteCal, decisiones: DecisionesCal): ResultadoEscritura =
        pedir(HttpMethod.Post, "$BASE/vista-previa", cuerpoGoogle(fuenteId, config, decisiones))

    suspend fun importar(fuenteId: String, config: ConfigFuenteCal, decisiones: DecisionesCal): ResultadoEscritura =
        pedir(HttpMethod.Post, "$BASE/importar", cuerpoGoogle(fuenteId, config, decisiones))

    /** Servicios activos (para "Servicio de las citas"). */
    suspend fun procedimientosActivos(): List<Pair<String, String>> = runCatching {
        Supabase.client.postgrest["procedimientos"]
            .select(Columns.list("id, nombre")) {
                filter { eq("estado", "Activo") }
                order("nombre", Order.ASCENDING)
            }
            .decodeList<JsonObject>()
            .mapNotNull { o -> o.texto("id")?.let { it to (o.texto("nombre") ?: "Servicio") } }
    }.getOrDefault(emptyList())

    /** Celular y dirección de una ficha que ya existe (se muestran sin editar). */
    suspend fun datosFicha(id: String): Pair<String?, String?>? = runCatching {
        Supabase.client.postgrest["pacientes"]
            .select(Columns.list("telefono, direccion")) { filter { eq("id", id) } }
            .decodeList<JsonObject>().firstOrNull()?.let { it.texto("telefono") to it.texto("direccion") }
    }.getOrNull()

    private fun JsonObject.texto(k: String): String? =
        (this[k] as? JsonPrimitive)?.takeIf { it !is JsonNull }?.contentOrNull?.takeIf { it.isNotBlank() }
}
