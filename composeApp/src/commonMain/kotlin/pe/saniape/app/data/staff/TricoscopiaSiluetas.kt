package pe.saniape.app.data.staff

/**
 * Siluetas de la clasificación capilar (gemelo de `lib/tricoscopia-siluetas.ts`):
 * el cuero cabelludo visto DESDE ARRIBA (frente arriba), con las zonas sin pelo
 * (opacidad 1) o ralas (< 1) de cada grado. Esquemas simples hechos en código,
 * no reproducciones de las láminas de Norwood, Ludwig o Sinclair.
 *
 * Coordenadas: caja 100 × 120; la cabeza es la elipse (50, 62) 38×50 → la
 * frente está en y = 12, la nuca en y = 112. Todo se recorta a la cabeza.
 * La dibujan `ui/clinica/capilar/SiluetaCapilar.kt` (Canvas) y [svgSilueta]
 * (el informe impreso).
 */
sealed class FormaSilueta {
    abstract val o: Double
    data class Elipse(val cx: Double, val cy: Double, val rx: Double, val ry: Double, override val o: Double) : FormaSilueta()
    data class Rect(val x: Double, val y: Double, val w: Double, val h: Double, override val o: Double) : FormaSilueta()
}

object CabezaSilueta {
    const val CX = 50.0
    const val CY = 62.0
    const val RX = 38.0
    const val RY = 50.0
}

// Las entradas: en las esquinas fronto-temporales, más hondas cuanto mayor el grado.
private fun entradas(r: Double): List<FormaSilueta> = listOf(
    FormaSilueta.Elipse(32.0, 18.0, r * 0.75, r * 1.15, 1.0),
    FormaSilueta.Elipse(68.0, 18.0, r * 0.75, r * 1.15, 1.0),
)
private fun frente(h: Double): FormaSilueta = FormaSilueta.Elipse(50.0, 8.0, 34.0, h, 1.0)
private fun coronilla(r: Double): FormaSilueta = FormaSilueta.Elipse(50.0, 76.0, r, r * 1.1, 1.0)
private fun rala(rx: Double, ry: Double, o: Double, cy: Double = 62.0): FormaSilueta = FormaSilueta.Elipse(50.0, cy, rx, ry, o)
private fun raya(w: Double): FormaSilueta = FormaSilueta.Rect(50 - w / 2, 20.0, w, 66.0, 0.95)

private val NORWOOD: Map<String, List<FormaSilueta>> = mapOf(
    "I" to entradas(5.0),
    "II" to entradas(9.0) + frente(6.0),
    "IIa" to entradas(9.0) + frente(14.0),
    "III" to entradas(15.0) + frente(8.0),
    "IIIa" to entradas(15.0) + frente(22.0),
    "III vértex" to entradas(15.0) + frente(8.0) + coronilla(8.0),
    "IV" to entradas(17.0) + frente(16.0) + coronilla(12.0),
    "IVa" to entradas(18.0) + frente(32.0),
    "V" to entradas(19.0) + frente(22.0) + coronilla(16.0),
    "Va" to entradas(20.0) + frente(46.0),
    "VI" to entradas(20.0) + frente(26.0) + coronilla(18.0) + FormaSilueta.Elipse(50.0, 52.0, 20.0, 24.0, 1.0),
    "VII" to listOf(FormaSilueta.Elipse(50.0, 54.0, 31.0, 47.0, 1.0)),
)

// Ludwig: la coronilla se aclara; la línea frontal se conserva.
private val LUDWIG: Map<String, List<FormaSilueta>> = mapOf(
    "I" to listOf(rala(13.0, 20.0, 0.45)),
    "II" to listOf(rala(21.0, 30.0, 0.65)),
    "III" to listOf(rala(28.0, 40.0, 0.9)),
)

// Sinclair: la raya central se ensancha y la rarefacción se extiende.
private val SINCLAIR: Map<String, List<FormaSilueta>> = mapOf(
    "1" to listOf(raya(1.5)),
    "2" to listOf(raya(3.5)),
    "3" to listOf(raya(5.0), rala(11.0, 24.0, 0.35)),
    "4" to listOf(raya(7.0), rala(18.0, 32.0, 0.55)),
    "5" to listOf(raya(9.0), rala(26.0, 40.0, 0.8)),
)

private val DIFUSO: Map<String, List<FormaSilueta>> = mapOf(
    "Leve" to listOf(rala(34.0, 46.0, 0.25)),
    "Moderado" to listOf(rala(34.0, 46.0, 0.45)),
    "Severo" to listOf(rala(34.0, 46.0, 0.65)),
)

private val POR_ESCALA: Map<String, Map<String, List<FormaSilueta>>> = mapOf(
    "hamilton_norwood" to NORWOOD, "ludwig" to LUDWIG, "sinclair" to SINCLAIR, "difuso" to DIFUSO,
)

/** Las formas de un grado; null si la escala no tiene dibujo ("otro") o el grado no existe. */
fun formasDeGrado(escala: String?, grado: String?): List<FormaSilueta>? {
    if (escala.isNullOrEmpty() || grado.isNullOrEmpty()) return null
    return POR_ESCALA[escala]?.get(grado.trim())
}

/** Colores FIJOS de la ilustración (se ve igual en tema oscuro, impresa y en el portal). */
object ColoresSilueta {
    const val PELO = "#3f3a52"
    const val PIEL = "#f1d3bf"
    const val CONTORNO = "#8892c8"
}

/**
 * La silueta como SVG en texto (para el informe impreso). [idClip] debe ser
 * único en el documento. Solo números y colores fijos: nada que escapar.
 */
fun svgSilueta(escala: String?, grado: String?, tam: Int, idClip: String): String {
    val formas = formasDeGrado(escala, grado)
    val c = CabezaSilueta
    val id = idClip.filter { it.isLetterOrDigit() || it == '-' }
    val n = ::numTrico
    val zonas = formas.orEmpty().joinToString("") { f ->
        when (f) {
            is FormaSilueta.Elipse -> "<ellipse cx=\"${n(f.cx)}\" cy=\"${n(f.cy)}\" rx=\"${n(redondearTrico(f.rx, 2))}\" ry=\"${n(redondearTrico(f.ry, 2))}\" fill=\"${ColoresSilueta.PIEL}\" fill-opacity=\"${n(f.o)}\"/>"
            is FormaSilueta.Rect -> "<rect x=\"${n(f.x)}\" y=\"${n(f.y)}\" width=\"${n(f.w)}\" height=\"${n(f.h)}\" rx=\"${n(minOf(f.w / 2, 3.0))}\" fill=\"${ColoresSilueta.PIEL}\" fill-opacity=\"${n(f.o)}\"/>"
        }
    }
    val pregunta = if (formas == null)
        "<text x=\"${n(c.CX)}\" y=\"${n(c.CY + 9)}\" text-anchor=\"middle\" font-size=\"28\" font-weight=\"700\" fill=\"#ffffff\" fill-opacity=\"0.85\">?</text>" else ""
    return "<svg viewBox=\"0 0 100 120\" width=\"$tam\" height=\"${(tam * 1.2).toInt()}\" aria-hidden=\"true\">" +
        "<defs><clipPath id=\"$id\"><ellipse cx=\"${n(c.CX)}\" cy=\"${n(c.CY)}\" rx=\"${n(c.RX)}\" ry=\"${n(c.RY)}\"/></clipPath></defs>" +
        "<path d=\"M45 13 L50 3 L55 13 Z\" fill=\"${ColoresSilueta.PIEL}\" stroke=\"${ColoresSilueta.CONTORNO}\" stroke-width=\"1.2\"/>" +
        "<ellipse cx=\"${n(c.CX - c.RX)}\" cy=\"66\" rx=\"4\" ry=\"9\" fill=\"${ColoresSilueta.PIEL}\" stroke=\"${ColoresSilueta.CONTORNO}\" stroke-width=\"1.2\"/>" +
        "<ellipse cx=\"${n(c.CX + c.RX)}\" cy=\"66\" rx=\"4\" ry=\"9\" fill=\"${ColoresSilueta.PIEL}\" stroke=\"${ColoresSilueta.CONTORNO}\" stroke-width=\"1.2\"/>" +
        "<ellipse cx=\"${n(c.CX)}\" cy=\"${n(c.CY)}\" rx=\"${n(c.RX)}\" ry=\"${n(c.RY)}\" fill=\"${ColoresSilueta.PELO}\"/>" +
        "<g clip-path=\"url(#$id)\">$zonas</g>" +
        "<ellipse cx=\"${n(c.CX)}\" cy=\"${n(c.CY)}\" rx=\"${n(c.RX)}\" ry=\"${n(c.RY)}\" fill=\"none\" stroke=\"${ColoresSilueta.CONTORNO}\" stroke-width=\"1.5\"/>" +
        pregunta + "</svg>"
}
