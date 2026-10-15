package pe.saniape.app.ui

/**
 * Percent-encoding UTF-8 para querystrings (mensajes de WhatsApp, links, Maps).
 * Solo letras/dígitos ASCII y "-_.~" pasan tal cual. Antes se miraba el byte
 * como Char con signo: 0xF0 → '￰', y los de 0x80–0xBF caían en katakana
 * de ancho medio, que `isLetterOrDigit()` da por letras: los emojis y la "¿"
 * llegaban a WhatsApp como "�ﾟﾑﾋ" / "ￂ�" (Centro OMER, 2026-10-14).
 */
fun urlEncode(s: String): String = buildString {
    for (b in s.encodeToByteArray()) {
        val c = b.toInt() and 0xFF
        val ch = c.toChar()
        if (ch in 'A'..'Z' || ch in 'a'..'z' || ch in '0'..'9' || ch in "-_.~") append(ch)
        else append('%').append(c.toString(16).uppercase().padStart(2, '0'))
    }
}
