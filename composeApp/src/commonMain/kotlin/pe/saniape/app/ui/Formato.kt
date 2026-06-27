package pe.saniape.app.ui

/**
 * Formatea una hora guardada en 24h ("HH:mm" o "HH:mm:ss") a 12h con AM/PM
 * (ej. "10:25" → "10:25 a. m.", "14:00" → "2:00 p. m.").
 * La BD siempre guarda 24h; esto es solo presentación.
 */
fun hora12(hora24: String?): String {
    if (hora24.isNullOrBlank()) return "—"
    val partes = hora24.split(":")
    val h = partes.getOrNull(0)?.toIntOrNull() ?: return hora24.take(5)
    val m = partes.getOrNull(1)?.toIntOrNull() ?: 0
    val sufijo = if (h < 12) "a. m." else "p. m."
    val h12 = when {
        h == 0 -> 12
        h > 12 -> h - 12
        else -> h
    }
    return "$h12:${m.toString().padStart(2, '0')} $sufijo"
}