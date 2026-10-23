package pe.saniape.app.data.staff

// BÚSQUEDA EN LOS SELECTORES DE SERVICIO (pedido del dueño, 24/10/2026: con
// decenas de servicios —Centro OMER ~38— el selector ya no se recorre a ojo).
// Gemelo de `lib/buscar-opciones.ts` (web); acá vive la regla, pura y con tests,
// y el componente (SelectorBuscable) solo la pinta.
//
// La regla:
//   · Sin tildes ni mayúsculas ("TERAPIA FISICA" = "terapia física"; ñ = n).
//   · Por palabras y en cualquier orden: TODAS las palabras escritas tienen que
//     aparecer en el nombre ("dolor rodilla" → "manejo regenerativo del dolor de rodillas").
//   · Orden: primero lo que EMPIEZA por lo escrito, luego lo que tiene una palabra
//     que empieza así, y al final lo que solo lo contiene. Dentro de cada grupo,
//     el orden original (el del catálogo).
//   · Consulta vacía = todas, en su orden.

/** Un tramo del nombre, para pintar en negrita lo que coincide. */
data class TramoResaltado(val texto: String, val coincide: Boolean)

/**
 * Un carácter en forma de búsqueda: sin tilde y en minúscula; lo que no es
 * letra ni número se vuelve espacio (separa palabras).
 */
private fun normalizarCaracter(c: Char): String {
    val s = plegarTildes(c.toString()).lowercase()
    return if (s.isNotEmpty() && s.all { it in 'a'..'z' || it in '0'..'9' }) s else " "
}

/** Texto en forma de búsqueda: "Manejo del DOLOR (rodillas)" → "manejo del dolor  rodillas ". */
fun normalizarParaBuscar(texto: String?): String =
    buildString { texto.orEmpty().forEach { append(normalizarCaracter(it)) } }

/** Las palabras de lo que se escribió, normalizadas y sin repetir. */
fun palabrasDeConsulta(consulta: String?): List<String> =
    normalizarParaBuscar(consulta).split(' ').filter { it.isNotEmpty() }.distinct()

/** 0 = empieza por lo escrito · 1 = cada palabra empieza una palabra · 2 = solo contiene. */
private fun puntaje(norm: String, palabras: List<String>): Int {
    val limpio = norm.replace(Regex(" +"), " ").trim()
    if (limpio.startsWith(palabras.joinToString(" "))) return 0
    val palabrasNombre = limpio.split(' ')
    if (palabras.all { p -> palabrasNombre.any { it.startsWith(p) } }) return 1
    return 2
}

/** ¿El nombre contiene todas las palabras escritas? */
fun coincideConsulta(etiqueta: String, consulta: String?): Boolean {
    val palabras = palabrasDeConsulta(consulta)
    if (palabras.isEmpty()) return true
    val norm = normalizarParaBuscar(etiqueta)
    return palabras.all { norm.contains(it) }
}

/** Las opciones que coinciden, ordenadas (ver la regla arriba). [etiqueta] = el nombre que se busca. */
fun <T> filtrarOpciones(opciones: List<T>, consulta: String?, etiqueta: (T) -> String): List<T> {
    val palabras = palabrasDeConsulta(consulta)
    if (palabras.isEmpty()) return opciones.toList()
    return opciones.mapIndexedNotNull { i, o ->
        val norm = normalizarParaBuscar(etiqueta(o))
        if (palabras.all { norm.contains(it) }) Triple(o, puntaje(norm, palabras), i) else null
    }.sortedWith(compareBy({ it.second }, { it.third })).map { it.first }
}

/**
 * El nombre partido en tramos, marcando cada aparición de las palabras escritas
 * (todas; los tramos que se tocan se juntan). Sin consulta, un solo tramo sin
 * marcar. Unir los `texto` devuelve el nombre intacto (con tildes y emojis).
 */
fun resaltarCoincidencias(etiqueta: String, consulta: String?): List<TramoResaltado> {
    if (etiqueta.isEmpty()) return emptyList()
    val palabras = palabrasDeConsulta(consulta)
    if (palabras.isEmpty()) return listOf(TramoResaltado(etiqueta, false))
    // Forma normalizada + a qué carácter ORIGINAL corresponde cada carácter normalizado.
    val norm = StringBuilder()
    val origen = ArrayList<Int>()
    etiqueta.forEachIndexed { i, ch ->
        val n = normalizarCaracter(ch)
        repeat(n.length) { origen.add(i) }
        norm.append(n)
    }
    val marcado = BooleanArray(etiqueta.length)
    val normTxt = norm.toString()
    for (p in palabras) {
        var desde = normTxt.indexOf(p)
        while (desde >= 0) {
            for (k in origen[desde]..origen[desde + p.length - 1]) marcado[k] = true
            desde = normTxt.indexOf(p, desde + 1)
        }
    }
    val tramos = mutableListOf<TramoResaltado>()
    val actual = StringBuilder()
    var marca = marcado[0]
    etiqueta.forEachIndexed { k, ch ->
        if (marcado[k] != marca) {
            tramos += TramoResaltado(actual.toString(), marca)
            actual.clear()
            marca = marcado[k]
        }
        actual.append(ch)
    }
    tramos += TramoResaltado(actual.toString(), marca)
    return tramos
}
