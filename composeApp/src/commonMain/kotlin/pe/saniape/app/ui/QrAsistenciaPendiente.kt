package pe.saniape.app.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * La llave del QR de asistencia de un enlace `https://www.saniape.com/asistencia?qr=<llave>`,
 * o null si el enlace no es ese. Puro. La llave son 32 hexadecimales (la genera la web).
 */
fun qrDeEnlaceAsistencia(url: String?): String? {
    val u = url?.trim().orEmpty()
    val sinEsquema = u.removePrefix("https://").removePrefix("http://")
    val host = sinEsquema.substringBefore('/').substringBefore('?').lowercase()
    if (host != "www.saniape.com" && host != "saniape.com") return null
    val resto = sinEsquema.substringAfter('/', "")
    val ruta = resto.substringBefore('?').substringBefore('#').trimEnd('/')
    if (ruta != "asistencia") return null
    val consulta = resto.substringAfter('?', "").substringBefore('#')
    val qr = consulta.split('&').firstOrNull { it.startsWith("qr=") }?.removePrefix("qr=") ?: return null
    return qr.takeIf { Regex("^[0-9a-fA-F]{32}$").matches(it) }
}

/**
 * 🕘 El QR de la recepción escaneado con la cámara del teléfono (26/11/2026):
 * Android abre la app con ese enlace y aquí queda la llave hasta que la
 * pantalla de Asistencia marque con ella. Mismo mecanismo que
 * [CitaPendienteDeAbrir] (un aviso que abre la app).
 */
object QrAsistenciaPendiente {
    var actual by mutableStateOf<String?>(null)
        private set

    /** Lo llama la actividad con el enlace que abrió la app (cualquier otro se ignora). */
    fun pedir(url: String?) {
        qrDeEnlaceAsistencia(url)?.let { actual = it }
    }

    /** Se usa una sola vez: volver a la pantalla no vuelve a marcar. */
    fun consumir(): String? = actual.also { actual = null }
}
