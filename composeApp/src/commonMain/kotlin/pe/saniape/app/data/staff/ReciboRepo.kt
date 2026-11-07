package pe.saniape.app.data.staff

import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.Columns
import io.github.jan.supabase.postgrest.query.Order
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.client.statement.bodyAsText
import io.ktor.client.statement.readRawBytes
import io.ktor.http.encodeURLPathPart
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import pe.saniape.app.data.Supabase
import pe.saniape.app.data.crearHttpClient

/** Resultado de pedir un recibo: el PDF, o el mensaje humano de por qué no. */
sealed class ResultadoRecibo {
    class Ok(val pdf: ByteArray) : ResultadoRecibo()
    data class Error(val mensaje: String) : ResultadoRecibo()
}

private val jsonRecibo = Json { ignoreUnknownKeys = true }

/**
 * El mensaje del servidor (`{ "error": "…" }`: 404 anulado/de otra clínica, 409
 * "todavía no se cobró", 403 sin permiso) o uno genérico si no vino. Puro: se prueba sin red.
 */
internal fun mensajeErrorRecibo(cuerpo: String?): String =
    runCatching {
        ((jsonRecibo.parseToJsonElement(cuerpo ?: "") as? JsonObject)?.get("error") as? JsonPrimitive)
            ?.content?.takeIf { it.isNotBlank() && it != "null" }
    }.getOrNull() ?: "No se pudo generar el recibo"

/**
 * Recibo de pago en PDF (recibo INTERNO, no boleta SUNAT) — el mismo que la web:
 * GET /api/staff/comprobante/{pago|cita|atencion|venta}/[id]?formato=a4|ticket
 * (permiso 'pagos'). `reimpresion=1` = "🧾 Comprobante" de un pago YA hecho: mismo
 * recibo (número, monto, medio, fecha), solo agrega la marca "Reimpresión · <hoy>".
 */
object ReciboRepo {
    private val http = crearHttpClient()
    private suspend fun token(): String? = Supabase.client.auth.currentSessionOrNull()?.accessToken

    /** El PDF o el porqué (mensaje del servidor en 404/409; sin red, uno de conexión). */
    suspend fun descargar(tipo: String, id: String, formato: String = "a4", reimpresion: Boolean = false): ResultadoRecibo {
        val tk = token() ?: return ResultadoRecibo.Error("Tu sesión venció: vuelve a entrar.")
        return try {
            val resp = http.get("${Supabase.SITE_URL}/api/staff/comprobante/${tipo.encodeURLPathPart()}/${id.encodeURLPathPart()}") {
                header("Authorization", "Bearer $tk")
                parameter("formato", if (formato == "ticket") "ticket" else "a4")
                if (reimpresion) parameter("reimpresion", "1")
            }
            if (resp.status.value in 200..299) ResultadoRecibo.Ok(resp.readRawBytes())
            else ResultadoRecibo.Error(mensajeErrorRecibo(runCatching { resp.bodyAsText() }.getOrNull()))
        } catch (e: kotlin.coroutines.cancellation.CancellationException) {
            throw e
        } catch (e: Exception) {
            ResultadoRecibo.Error("Sin conexión: no se pudo generar el recibo.")
        }
    }

    /**
     * 🧾 de una SESIÓN pagada (gemelo de `elegirReciboDeSesion` de la web): el pago
     * VINCULADO a la sesión (el más reciente); sin él (un paquete pagado aparte),
     * el último del tratamiento. Success(null) = no hay ninguno; Failure = no se
     * pudo leer (sin red). Una lectura al tocar: la RLS la acota a la clínica.
     */
    suspend fun pagoDeSesion(sesionId: String?, tratamientoId: String?): Result<String?> = try {
        suspend fun ultimo(columna: String, id: String): String? =
            Supabase.client.postgrest["pagos_tratamiento"]
                .select(Columns.raw("id")) {
                    filter { eq(columna, id) }
                    order("created_at", Order.DESCENDING)
                    limit(1)
                }
                .decodeList<JsonObject>()
                .firstOrNull()?.let { (it["id"] as? JsonPrimitive)?.contentOrNull?.takeIf { s -> s.isNotBlank() } }
        val porSesion = sesionId?.takeIf { it.isNotBlank() }?.let { ultimo("sesion_id", it) }
        Result.success(porSesion ?: tratamientoId?.takeIf { it.isNotBlank() }?.let { ultimo("tratamiento_id", it) })
    } catch (e: kotlin.coroutines.cancellation.CancellationException) {
        throw e
    } catch (e: Exception) {
        Result.failure(e)
    }

    /** Bytes del PDF, o null si no se pudo (sin red, sin permiso, aún no cobrado). */
    suspend fun pdf(tipo: String, id: String, formato: String = "a4", reimpresion: Boolean = false): ByteArray? =
        (descargar(tipo, id, formato, reimpresion) as? ResultadoRecibo.Ok)?.pdf
}
