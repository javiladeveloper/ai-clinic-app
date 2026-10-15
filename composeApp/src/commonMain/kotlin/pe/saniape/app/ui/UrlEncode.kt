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

/**
 * URL de WhatsApp con texto prearmado (gemela de urlWhatsApp de la web). Con
 * texto va DIRECTO a api.whatsapp.com/send: wa.me redirige ahí pero en la
 * redirección cambia cada emoji por "�" (Centro OMER, 2026-10-14). Sin texto,
 * wa.me de siempre. [numero] con código de país; vacío = WhatsApp elige el contacto.
 */
fun urlWhatsApp(numero: String?, texto: String? = null): String {
    val n = numero.orEmpty().filter { it.isDigit() }
    val t = texto?.trim().orEmpty()
    if (t.isEmpty()) return "https://wa.me/$n"
    return "https://api.whatsapp.com/send?" + (if (n.isNotEmpty()) "phone=$n&" else "") + "text=${urlEncode(t)}"
}
