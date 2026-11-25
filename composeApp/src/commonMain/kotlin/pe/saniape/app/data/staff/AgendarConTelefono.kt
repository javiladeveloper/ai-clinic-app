package pe.saniape.app.data.staff

/*
 * 📞 AGENDAR CON CUALQUIERA DE LOS TRES: TELÉFONO, NOMBRE O DNI (24/11/2026).
 * Gemelo de separarTelefono / coincidePorTelefono / nombreProvisional de
 * lib/paciente-busqueda.ts (web). Una clínica con teleoperadores agenda con el nombre
 * y el número de quien llama, o solo con el número. Lógica pura.
 */

data class TextoYTelefono(val texto: String, val telefono: String?)

private val RE_TELEFONO = Regex(
    """(?<![\d.])(?:\+?51[\s-]?)?(9\d{2})[\s-]?(\d{3})[\s-]?(\d{3})(?![\d.\-kK])|(?<![\d.])\+(\d[\d\s-]{8,15}\d)(?![\d.])""",
)

/**
 * Saca el teléfono de lo escrito: «Ana Quispe 987 654 321» → texto «Ana Quispe» y
 * teléfono «987654321»; «987654321» → solo el teléfono. Un DNI (8 dígitos) o un RUT
 * con puntos y guion no son teléfono.
 */
fun separarTelefono(escrito: String?): TextoYTelefono {
    val s = escrito.orEmpty()
    val m = RE_TELEFONO.find(s) ?: return TextoYTelefono(s, null)
    val g = m.groupValues
    val telefono = if (g[1].isNotEmpty()) g[1] + g[2] + g[3] else "+" + g[4].filter { it.isDigit() }
    val texto = (s.substring(0, m.range.first) + " " + s.substring(m.range.last + 1)).replace(Regex("\\s+"), " ").trim()
    return TextoYTelefono(texto, telefono)
}

private fun cola9(t: String?): String = t.orEmpty().filter { it.isDigit() }.takeLast(9)

/** ¿El teléfono guardado es el que se escribió? (sin importar espacios, guiones ni el código de país). */
fun coincidePorTelefono(guardado: String?, telefono: String?): Boolean {
    val a = cola9(guardado); val b = cola9(telefono)
    return a.length >= 7 && b.length >= 7 && a == b
}

/** «987 654 321»: legible en la agenda. */
fun telefonoLegible(telefono: String): String {
    val d = telefono.filter { it.isDigit() }
    return if (!telefono.startsWith("+") && d.length == 9) "${d.substring(0, 3)} ${d.substring(3, 6)} ${d.substring(6)}" else telefono
}

/** El nombre con que nace una ficha registrada SOLO con el teléfono (lo pone también el servidor). */
fun nombreProvisional(telefono: String): String = "Sin nombre · ${telefonoLegible(telefono)}"
