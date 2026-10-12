package pe.saniape.app.data.staff

import io.github.jan.supabase.auth.auth
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.client.statement.readRawBytes
import io.ktor.http.encodeURLPathPart
import pe.saniape.app.data.Supabase
import pe.saniape.app.data.crearHttpClient

/**
 * Recibo de pago en PDF (recibo INTERNO, no boleta SUNAT) — el mismo que la web:
 * GET /api/staff/comprobante/{pago|cita}/[id]?formato=a4|ticket (permiso 'pagos').
 */
object ReciboRepo {
    private val http = crearHttpClient()
    private suspend fun token(): String? = Supabase.client.auth.currentSessionOrNull()?.accessToken

    /** Bytes del PDF, o null si no se pudo (sin red, sin permiso, aún no cobrado). */
    suspend fun pdf(tipo: String, id: String, formato: String = "a4"): ByteArray? {
        val tk = token() ?: return null
        return try {
            val resp = http.get("${Supabase.SITE_URL}/api/staff/comprobante/${tipo.encodeURLPathPart()}/${id.encodeURLPathPart()}") {
                header("Authorization", "Bearer $tk")
                parameter("formato", if (formato == "ticket") "ticket" else "a4")
            }
            if (resp.status.value in 200..299) resp.readRawBytes() else null
        } catch (e: kotlin.coroutines.cancellation.CancellationException) {
            throw e
        } catch (e: Exception) {
            null
        }
    }
}
