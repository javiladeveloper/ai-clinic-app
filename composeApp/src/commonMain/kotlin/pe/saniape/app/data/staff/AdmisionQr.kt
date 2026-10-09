package pe.saniape.app.data.staff

import io.github.jan.supabase.auth.auth
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.statement.bodyAsText
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import pe.saniape.app.data.Supabase
import pe.saniape.app.data.crearHttpClient

/**
 * QR de ADMISIÓN — gemelo de `components/pacientes/QrAdmision.tsx` (web).
 * Recepción lo genera EN EL MOMENTO (POST /api/staff/admision-qr, permiso
 * 'pacientes'): vale 10 minutos y UN registro. El paciente nuevo lo escanea y
 * llena su propia ficha desde su celular (página pública /admision/[token]).
 * Efímero a propósito: un QR permanente fotografiado sería una puerta abierta.
 */
data class AdmisionQr(val url: String, val ttlMin: Int)

/** Lo que devuelve el endpoint: el enlace, o el mensaje de error del servidor. */
data class ResultadoAdmisionQr(val qr: AdmisionQr?, val error: String?)

private const val TTL_POR_DEFECTO = 10

/** Respuesta de POST /api/staff/admision-qr → enlace + vigencia (o error). Puro. */
fun interpretarAdmisionQr(status: Int, cuerpo: String): ResultadoAdmisionQr {
    val o = runCatching { Json.parseToJsonElement(cuerpo) as? JsonObject }.getOrNull()
    fun txt(k: String) = (o?.get(k) as? JsonPrimitive)?.content?.takeIf { it != "null" && it.isNotBlank() }
    val url = txt("url")
    if (status !in 200..299 || url == null || !(url.startsWith("https://") || url.startsWith("http://"))) {
        return ResultadoAdmisionQr(null, txt("error") ?: "No se pudo generar el código")
    }
    val ttl = txt("ttlMin")?.toDoubleOrNull()?.toInt()?.takeIf { it > 0 } ?: TTL_POR_DEFECTO
    return ResultadoAdmisionQr(AdmisionQr(url, ttl), null)
}

/** Cuenta atrás "mm:ss" (nunca negativa). */
fun cuentaAtras(segundos: Int): String {
    val s = segundos.coerceAtLeast(0)
    return "${(s / 60).toString().padStart(2, '0')}:${(s % 60).toString().padStart(2, '0')}"
}

/** Mensaje para mandar el enlace por WhatsApp. */
fun textoWhatsAppAdmision(url: String, ttlMin: Int, clinica: String? = null): String {
    val de = clinica?.trim()?.takeIf { it.isNotEmpty() }?.let { " en $it" } ?: ""
    return "Hola 👋 Para registrarte$de, llena tus datos desde este enlace (vale $ttlMin minutos): $url"
}

object AdmisionQrRepo {
    private val http = crearHttpClient()

    suspend fun generar(): ResultadoAdmisionQr = try {
        val tk = Supabase.client.auth.currentSessionOrNull()?.accessToken
            ?: return ResultadoAdmisionQr(null, "Sesión vencida. Vuelve a entrar.")
        val resp = http.post("${Supabase.SITE_URL}/api/staff/admision-qr") { header("Authorization", "Bearer $tk") }
        interpretarAdmisionQr(resp.status.value, resp.bodyAsText())
    } catch (_: Exception) {
        ResultadoAdmisionQr(null, "No se pudo generar el código. Revisa tu conexión.")
    }
}
