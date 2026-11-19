package pe.saniape.app.ui.clinica.atencion

// POSOLOGÍA ESTANDARIZADA de la receta — gemelo de lib/receta-posologia.ts (web,
// 19/11/2026: «si era un líquido no tiene cantidad y salía error… pongo el 1 y el
// contador no lo reconoce… estandariza»). La dosis, la frecuencia y la duración se
// arman con piezas y se guarda el MISMO texto de siempre («1 tableta», «cada 8
// horas», «7 días»). Lo que no encaja va en «Otra…» (texto libre, como antes).

internal data class UnidadDosis(val clave: String, val singular: String, val plural: String)

internal val UNIDADES_DOSIS = listOf(
    UnidadDosis("tableta", "tableta", "tabletas"),
    UnidadDosis("capsula", "cápsula", "cápsulas"),
    UnidadDosis("gota", "gota", "gotas"),
    UnidadDosis("ml", "ml", "ml"),
    UnidadDosis("sobre", "sobre", "sobres"),
    UnidadDosis("ampolla", "ampolla", "ampollas"),
    UnidadDosis("inhalacion", "inhalación", "inhalaciones"),
    UnidadDosis("aplicacion", "aplicación", "aplicaciones"),
    UnidadDosis("supositorio", "supositorio", "supositorios"),
    UnidadDosis("ovulo", "óvulo", "óvulos"),
    UnidadDosis("parche", "parche", "parches"),
    UnidadDosis("puff", "pulverización", "pulverizaciones"),
)

internal val CANTIDADES_DOSIS = listOf("¼", "½", "1", "1½", "2", "3", "4", "5", "10", "15", "20")

internal fun unidadDosisDeForma(forma: String?): String {
    val f = sinTildes(forma.orEmpty())
    if (f.isEmpty()) return "tableta"
    return when {
        "tableta" in f || "comprimido" in f || "gragea" in f -> "tableta"
        "capsula" in f -> "capsula"
        Regex("gotas|oftalm|otica").containsMatchIn(f) -> "gota"
        Regex("jarabe|suspension|liquido|solucion oral|enjuague").containsMatchIn(f) -> "ml"
        "polvo" in f || "sobre" in f -> "sobre"
        "inyect" in f -> "ampolla"
        "inhal" in f || "nebuli" in f -> "inhalacion"
        "spray" in f || "nasal" in f -> "puff"
        "supositorio" in f -> "supositorio"
        "ovulo" in f -> "ovulo"
        "parche" in f -> "parche"
        Regex("crema|unguento|gel|locion|topica").containsMatchIn(f) -> "aplicacion"
        else -> "tableta"
    }
}

internal fun numeroDosis(t: String): Double {
    val s = t.trim().replace(',', '.')
    Regex("""^(\d+)?\s*([¼½¾])$""").find(s)?.let { m ->
        val base = m.groupValues[1].toDoubleOrNull() ?: 0.0
        return base + when (m.groupValues[2]) { "¼" -> 0.25; "½" -> 0.5; else -> 0.75 }
    }
    return s.toDoubleOrNull() ?: Double.NaN
}

internal fun textoDosis(cantidad: String, unidad: String): String {
    val u = UNIDADES_DOSIS.firstOrNull { it.clave == unidad } ?: UNIDADES_DOSIS.first()
    val n = numeroDosis(cantidad)
    return "${cantidad.trim()} ${if (!n.isNaN() && n > 1) u.plural else u.singular}".trim()
}

/** «1 tableta» → (1, tableta); «1» → (1, ""). null = no se entiende (va a «Otra»). */
internal fun leerDosis(texto: String?): Pair<String, String>? {
    val t = texto.orEmpty().trim()
    if (t.isEmpty()) return null
    val m = Regex("""^(\d+\s*/\s*\d+|\d*\s*[¼½¾]|\d+(?:[.,]\d+)?)\s*(.*)$""").find(t) ?: return null
    var cantidad = m.groupValues[1].replace(Regex("""\s+"""), "")
    if (cantidad == "1/2") cantidad = "½"
    if (cantidad == "1/4") cantidad = "¼"
    val resto = sinTildes(m.groupValues[2]).trimEnd('.').trim()
    if (resto.isEmpty()) return cantidad to ""
    val u = UNIDADES_DOSIS.firstOrNull { x ->
        val s = sinTildes(x.singular); val p = sinTildes(x.plural)
        resto == s || resto == p || resto.startsWith("$s ") || resto.startsWith("$p ") ||
            (x.clave == "tableta" && Regex("^(tab|tabs|tb|comp|comprimidos?)$").matches(resto)) ||
            (x.clave == "capsula" && Regex("^(cap|caps)$").matches(resto))
    } ?: return null
    return cantidad to u.clave
}

internal val FRECUENCIAS_RECETA = listOf(
    "cada 4 horas", "cada 6 horas", "cada 8 horas", "cada 12 horas", "cada 24 horas",
    "1 vez al día", "2 veces al día", "3 veces al día",
    "cada mañana", "cada noche", "antes de dormir", "1 vez a la semana",
    "dosis única", "si hay dolor", "si hay fiebre",
)

private val FRECUENCIAS_ANTES = mapOf(
    "una vez al dia" to "1 vez al día", "dos veces al dia" to "2 veces al día", "tres veces al dia" to "3 veces al día",
    "condicional a dolor" to "si hay dolor", "una vez a la semana" to "1 vez a la semana",
)

/** ¿El texto es una de la lista? («8» y «c/8h» = cada 8 horas). null = va a «Otra». */
internal fun frecuenciaDeLista(texto: String?): String? {
    val t = sinTildes(texto.orEmpty().trim())
    if (t.isEmpty()) return null
    Regex("""^(?:c\s*/\s*)?(\d{1,2})\s*(?:h|hs|hrs?|horas?)?$""").find(t)?.let { m ->
        val h = m.groupValues[1].toInt()
        if (h in listOf(4, 6, 8, 12, 24)) return "cada $h horas"
    }
    FRECUENCIAS_ANTES[t]?.let { return it }
    return FRECUENCIAS_RECETA.firstOrNull { sinTildes(it) == t }
}

internal val UNIDADES_DURACION = listOf("días", "semanas", "meses")
internal const val USO_CONTINUO = "uso continuo"

internal fun textoDuracion(numero: String, unidad: String): String {
    val n = numero.trim().replace(',', '.').toDoubleOrNull() ?: return ""
    if (n <= 0) return ""
    val u = when (unidad) {
        "semanas" -> if (n == 1.0) "semana" else "semanas"
        "meses" -> if (n == 1.0) "mes" else "meses"
        else -> if (n == 1.0) "día" else "días"
    }
    return "${numero.trim()} $u"
}

/** «7 días» → (7, días, false); «uso continuo» → continuo. null = va a «Otra». */
internal data class DuracionLeida(val numero: String, val unidad: String, val continuo: Boolean)

internal fun leerDuracion(texto: String?): DuracionLeida? {
    val t = sinTildes(texto.orEmpty().trim()).removePrefix("por ").trim()
    if (t.isEmpty()) return null
    if (Regex("continu|indefinid|permanente").containsMatchIn(t)) return DuracionLeida("", "días", true)
    val m = Regex("""^(\d+)\s*(dias?|d|semanas?|sem|mes(?:es)?)?$""").find(t) ?: return null
    val g = m.groupValues[2]
    val u = if (g.isEmpty() || g.startsWith("d")) "días" else if (g.startsWith("sem")) "semanas" else "meses"
    return DuracionLeida(m.groupValues[1], u, false)
}
