package pe.saniape.app.data.staff

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.round
import kotlin.math.sin

/**
 * Geometría PURA de los mapas tocables de la ficha (🦶 pie de podología y 💉
 * rostro de estética): las MISMAS formas del SVG de la web, en coordenadas de
 * su viewBox. El Canvas de la app las dibuja escaladas, el toque se prueba
 * contra ellas y las hojas imprimibles las vuelven a escribir como SVG.
 *
 * Solo elipses (con rotación opcional, en grados, sobre su centro, como el
 * `rotate(r cx cy)` de la web) y rectángulos redondeados; las siluetas son
 * trazos SVG absolutos (M, L, C, Z) que [trazosSvg] lee.
 */
sealed class FormaMapa {
    data class Elipse(val cx: Float, val cy: Float, val rx: Float, val ry: Float, val rot: Float = 0f) : FormaMapa()
    data class Rect(val x: Float, val y: Float, val w: Float, val h: Float, val r: Float = 3f) : FormaMapa()

    /** ¿El punto (en el viewBox) cae dentro? La elipse rotada se prueba en su propio eje. */
    fun contiene(px: Float, py: Float): Boolean = when (this) {
        is Elipse -> {
            val (lx, ly) = aEjeLocal(px, py)
            val dx = lx / rx
            val dy = ly / ry
            dx * dx + dy * dy <= 1f
        }
        is Rect -> px in x..(x + w) && py in y..(y + h)
    }

    /** Centro de la forma (para elegir la más cercana cuando dos áreas de toque se pisan). */
    val centro: Pair<Float, Float> get() = when (this) {
        is Elipse -> cx to cy
        is Rect -> (x + w / 2f) to (y + h / 2f)
    }

    /** Área aproximada (para ordenar: las chicas van encima de las grandes). */
    val area: Float get() = when (this) {
        is Elipse -> rx * ry
        is Rect -> w * h / 4f
    }

    /** Reflejo horizontal dentro de un viewBox de [ancho] (el pie izquierdo, el perfil izquierdo). */
    fun espejo(ancho: Float): FormaMapa = when (this) {
        is Elipse -> copy(cx = ancho - cx, rot = -rot)
        is Rect -> copy(x = ancho - x - w)
    }

    private fun Elipse.aEjeLocal(px: Float, py: Float): Pair<Float, Float> {
        val dx = px - cx
        val dy = py - cy
        if (rot == 0f) return dx to dy
        val a = -rot * PI.toFloat() / 180f
        return (dx * cos(a) - dy * sin(a)) to (dx * sin(a) + dy * cos(a))
    }
}

/** Un tramo de un trazo SVG absoluto. */
sealed class ComandoTrazo {
    data class Mover(val x: Float, val y: Float) : ComandoTrazo()
    data class Linea(val x: Float, val y: Float) : ComandoTrazo()
    data class Curva(val x1: Float, val y1: Float, val x2: Float, val y2: Float, val x: Float, val y: Float) : ComandoTrazo()
    data object Cerrar : ComandoTrazo()
}

/**
 * Lee un `d` de SVG con comandos ABSOLUTOS M, L, C y Z (los únicos que usan las
 * siluetas de la web). Coordenadas repetidas tras un comando continúan con el
 * mismo (tras M, como L). Lo que no entiende se descarta.
 */
fun trazosSvg(d: String): List<ComandoTrazo> {
    val tokens = Regex("""[MLCZmlcz]|-?\d*\.?\d+(?:e-?\d+)?""").findAll(d).map { it.value }.toList()
    val out = mutableListOf<ComandoTrazo>()
    var i = 0
    var cmd = ' '
    fun num(): Float? = tokens.getOrNull(i)?.toFloatOrNull()?.also { i++ }
    while (i < tokens.size) {
        val t = tokens[i]
        if (t.length == 1 && t[0].isLetter()) {
            cmd = t[0].uppercaseChar()
            i++
            if (cmd == 'Z') { out += ComandoTrazo.Cerrar; continue }
        }
        when (cmd) {
            'M' -> {
                val x = num() ?: break
                val y = num() ?: break
                out += ComandoTrazo.Mover(x, y)
                cmd = 'L'
            }
            'L' -> {
                val x = num() ?: break
                val y = num() ?: break
                out += ComandoTrazo.Linea(x, y)
            }
            'C' -> {
                val v = List(6) { num() }
                if (v.any { it == null }) break
                out += ComandoTrazo.Curva(v[0]!!, v[1]!!, v[2]!!, v[3]!!, v[4]!!, v[5]!!)
            }
            else -> i++
        }
    }
    return out
}

/** Un trazo reflejado horizontalmente en un viewBox de [ancho]. */
fun espejoTrazos(t: List<ComandoTrazo>, ancho: Float): List<ComandoTrazo> = t.map {
    when (it) {
        is ComandoTrazo.Mover -> it.copy(x = ancho - it.x)
        is ComandoTrazo.Linea -> it.copy(x = ancho - it.x)
        is ComandoTrazo.Curva -> it.copy(x1 = ancho - it.x1, x2 = ancho - it.x2, x = ancho - it.x)
        ComandoTrazo.Cerrar -> it
    }
}

/** Una zona tocable: su id, lo que se dibuja y (opcional) un área de toque más grande, invisible. */
data class ZonaDibujo(val id: String, val formas: List<FormaMapa>, val toque: List<FormaMapa> = emptyList())

/**
 * La zona en (x, y) del viewBox, como el SVG de la web: gana la de ARRIBA en el
 * orden de dibujo (la última), contando su área de toque invisible.
 */
fun zonaTocadaEnOrden(zonas: List<ZonaDibujo>, x: Float, y: Float): String? =
    zonas.lastOrNull { z -> z.formas.any { it.contiene(x, y) } || z.toque.any { it.contiene(x, y) } }?.id

/**
 * Para los mapas con áreas de toque AGRANDADAS (radio mínimo [radioMin] en el
 * viewBox): primero gana la zona VISIBLE de arriba; si el dedo cayó fuera de
 * todas, el área de toque cuya forma esté más cerca (distancia normalizada al
 * centro). Así un toque cerca de una zona chica la encuentra sin tapar a la vecina.
 */
fun zonaTocadaConRadio(zonas: List<ZonaDibujo>, x: Float, y: Float, radioMin: Float): String? {
    zonas.lastOrNull { z -> z.formas.any { it.contiene(x, y) } }?.let { return it.id }
    var mejor: String? = null
    var mejorD = Float.MAX_VALUE
    for (z in zonas) for (f in z.formas) {
        val ampliada = ampliarForma(f, radioMin)
        if (!ampliada.contiene(x, y)) continue
        val (cx, cy) = f.centro
        val d = (x - cx) * (x - cx) + (y - cy) * (y - cy)
        if (d < mejorD) { mejorD = d; mejor = z.id }
    }
    return mejor
}

/** La forma con su radio mínimo de toque (las grandes quedan igual). */
fun ampliarForma(f: FormaMapa, radioMin: Float): FormaMapa = when (f) {
    is FormaMapa.Elipse -> f.copy(rx = max(f.rx, radioMin), ry = max(f.ry, radioMin))
    is FormaMapa.Rect -> {
        val w = max(f.w, radioMin * 2)
        val h = max(f.h, radioMin * 2)
        FormaMapa.Rect(f.x - (w - f.w) / 2, f.y - (h - f.h) / 2, w, h, f.r)
    }
}

// ─── SVG para las hojas imprimibles ──────────────────────────────────────────

/** Número para SVG: sin decimales de más ("52", "37.5"). */
internal fun numSvg(v: Float): String {
    val r = round(v * 100f) / 100f
    return if (r == r.toLong().toFloat()) r.toLong().toString() else r.toString()
}

/** Una forma como elemento SVG (atributos extra ya escapados). */
fun svgDeForma(f: FormaMapa, atributos: String): String = when (f) {
    is FormaMapa.Elipse -> "<ellipse cx=\"${numSvg(f.cx)}\" cy=\"${numSvg(f.cy)}\" rx=\"${numSvg(f.rx)}\" ry=\"${numSvg(f.ry)}\"" +
        (if (f.rot != 0f) " transform=\"rotate(${numSvg(f.rot)} ${numSvg(f.cx)} ${numSvg(f.cy)})\"" else "") + " $atributos/>"
    is FormaMapa.Rect -> "<rect x=\"${numSvg(f.x)}\" y=\"${numSvg(f.y)}\" width=\"${numSvg(f.w)}\" height=\"${numSvg(f.h)}\" rx=\"${numSvg(f.r)}\" $atributos/>"
}

/** "#rrggbb" → ARGB opaco (para el Canvas). Basura → el navy de Sania. */
fun argbDeHex(hex: String): Long {
    val h = hex.trim().removePrefix("#")
    val v = if (h.length == 6) h.toLongOrNull(16) else null
    return 0xFF000000 or (v ?: 0x2c3e7a)
}
