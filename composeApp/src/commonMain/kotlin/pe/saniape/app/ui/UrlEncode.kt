package pe.saniape.app.ui

/** Percent-encoding mínimo para querystrings (mensajes de WhatsApp, links). */
fun urlEncode(s: String): String = buildString {
    for (b in s.encodeToByteArray()) {
        val ch = b.toInt().toChar()
        if (ch.isLetterOrDigit() || ch in "-_.~") append(ch)
        else append('%').append(((b.toInt() and 0xFF).toString(16)).uppercase().padStart(2, '0'))
    }
}
