package pe.saniape.app.data.staff

/**
 * DINERO EN VARIAS MONEDAS (multipaís, Etapa 1). Gemelo de lib/dinero.ts de la web.
 *
 * Cada sede cobra en su moneda (la resuelve la web en /api/staff/contexto; ver
 * [monedaDeSede]). Acá solo se FORMATEA y se SUMA dinero de una moneda dada.
 *
 *  · Para PEN, `formatearDinero(n, "PEN")` da EXACTAMENTE lo mismo que [soles]
 *    ("S/ 1,234.50", "-S/ 553.00"). Hay test que lo cuida.
 *  · Mismo molde y misma puntuación para toda moneda: [signo][símbolo] [número]
 *    con miles "," y decimales "." ("Bs 3,100.00"). CLP/COP sin decimales.
 *    Los pesos llevan prefijo de país ("CLP$", "COL$", "MX$", "AR$").
 *  · Montos de monedas distintas NUNCA se suman: [totalesPorMoneda] y un total
 *    por moneda ([formatearTotalesPorMoneda]).
 */

const val MONEDA_POR_DEFECTO = "PEN"

private class FichaMoneda(val simbolo: String, val decimales: Int)

private val FICHAS: Map<String, FichaMoneda> = mapOf(
    "PEN" to FichaMoneda("S/", 2),
    "BOB" to FichaMoneda("Bs", 2),
    "USD" to FichaMoneda("US$", 2),
    "CLP" to FichaMoneda("CLP$", 0),
    "COP" to FichaMoneda("COL$", 0),
    "MXN" to FichaMoneda("MX$", 2),
    "ARS" to FichaMoneda("AR$", 2),
    "EUR" to FichaMoneda("€", 2),
)

/** Código ISO 4217 en mayúsculas; vacío o inválido → PEN. */
fun normalizarMoneda(moneda: String?): String {
    val m = moneda.orEmpty().trim().uppercase()
    return if (m.length == 3 && m.all { it in 'A'..'Z' }) m else MONEDA_POR_DEFECTO
}

/** Decimales que se muestran (CLP/COP: 0; una moneda sin ficha: 2). */
fun decimalesMoneda(moneda: String?): Int = FICHAS[normalizarMoneda(moneda)]?.decimales ?: 2

/** "S/", "Bs", "US$", "CLP$"… null/vacío → "S/". Una moneda sin ficha: su código. */
fun simboloMoneda(moneda: String?): String {
    val c = normalizarMoneda(moneda)
    return FICHAS[c]?.simbolo ?: c
}

private fun potencia10(n: Int): Long { var p = 1L; repeat(n) { p *= 10 }; return p }

/**
 * Monto con el símbolo de su moneda:
 *   formatearDinero(1234.5, "PEN")     → "S/ 1,234.50"   (igual que soles())
 *   formatearDinero(1234.5, "BOB")     → "Bs 1,234.50"
 *   formatearDinero(-553.0, "PEN")     → "-S/ 553.00"    (el signo ANTES del símbolo)
 *   formatearDinero(1500000.0, "CLP")  → "CLP$ 1,500,000"
 * [decimales] los fuerza (p. ej. 0 en un gráfico).
 */
fun formatearDinero(monto: Double?, moneda: String?, decimales: Int? = null): String {
    val dec = (decimales ?: decimalesMoneda(moneda)).coerceIn(0, 4)
    val factor = potencia10(dec)
    val v = monto?.takeIf { it.isFinite() } ?: 0.0
    // Mismo redondeo que soles()/aCentimos (kotlin.math.round).
    val unidades = kotlin.math.round(v * factor).toLong()
    val abs = kotlin.math.abs(unidades)
    val enteros = (abs / factor).toString().reversed().chunked(3).joinToString(",").reversed()
    val numero = if (dec > 0) "$enteros.${(abs % factor).toString().padStart(dec, '0')}" else enteros
    return (if (unidades < 0) "-" else "") + simboloMoneda(moneda) + " " + numero
}

/** Orden de presentación: PEN primero, el resto alfabético. */
fun ordenarMonedas(monedas: Iterable<String>): List<String> =
    monedas.map(::normalizarMoneda).distinct()
        .sortedWith(compareBy<String> { it != MONEDA_POR_DEFECTO }.thenBy { it })

/**
 * Suma agrupada por moneda (nunca mezcla), en centavos. Filas = (monto, moneda);
 * sin moneda → PEN. Resultado (moneda, total) con PEN primero.
 */
fun totalesPorMoneda(filas: Iterable<Pair<Double?, String?>>): List<Pair<String, Double>> {
    val centavos = linkedMapOf<String, Long>()
    for ((monto, moneda) in filas) {
        val m = normalizarMoneda(moneda)
        centavos[m] = (centavos[m] ?: 0L) + kotlin.math.round((monto?.takeIf { it.isFinite() } ?: 0.0) * 100).toLong()
    }
    return ordenarMonedas(centavos.keys).map { it to centavos.getValue(it) / 100.0 }
}

/**
 * "S/ 12,400.00 · Bs 3,100.00" a partir de (moneda, total). Uno solo → igual
 * que formatearDinero. Ninguno → "S/ 0.00".
 */
fun formatearTotalesPorMoneda(totales: List<Pair<String, Double>>): String {
    if (totales.isEmpty()) return formatearDinero(0.0, MONEDA_POR_DEFECTO)
    val porMoneda = linkedMapOf<String, Double>()
    for ((m, t) in totales) normalizarMoneda(m).let { porMoneda[it] = (porMoneda[it] ?: 0.0) + t }
    return ordenarMonedas(porMoneda.keys).joinToString(" · ") { formatearDinero(porMoneda[it], it) }
}
