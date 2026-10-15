package pe.saniape.app.ui.clinica.mapas

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import pe.saniape.app.data.staff.ComandoTrazo
import pe.saniape.app.data.staff.FormaMapa

/**
 * Dibujo de las formas de los mapas (pie, rostro) en un Canvas escalado desde
 * el viewBox de la web, y la capa de ACCESIBILIDAD: un nodo invisible por zona
 * con su nombre (TalkBack lo lee y lo activa) que no intercepta el dedo — el
 * toque lo resuelve el contenedor con la geometría de la web.
 */

internal fun DrawScope.dibujarForma(
    f: FormaMapa, escala: Float, relleno: Color?, borde: Color?, grosor: Float = 1f, punteado: Boolean = false,
) {
    val trazo = Stroke(
        width = grosor * escala,
        pathEffect = if (punteado) PathEffect.dashPathEffect(floatArrayOf(3f * escala, 2f * escala)) else null,
    )
    when (f) {
        is FormaMapa.Elipse -> {
            val tl = Offset((f.cx - f.rx) * escala, (f.cy - f.ry) * escala)
            val sz = Size(f.rx * 2 * escala, f.ry * 2 * escala)
            rotate(f.rot, pivot = Offset(f.cx * escala, f.cy * escala)) {
                relleno?.let { drawOval(it, tl, sz) }
                borde?.let { drawOval(it, tl, sz, style = trazo) }
            }
        }
        is FormaMapa.Rect -> {
            val tl = Offset(f.x * escala, f.y * escala)
            val sz = Size(f.w * escala, f.h * escala)
            val cr = CornerRadius(f.r * escala, f.r * escala)
            relleno?.let { drawRoundRect(it, tl, sz, cr) }
            borde?.let { drawRoundRect(it, tl, sz, cr, style = trazo) }
        }
    }
}

/** Un trazo SVG (absoluto) como Path escalado. */
internal fun pathDe(trazos: List<ComandoTrazo>, escala: Float): Path = Path().apply {
    for (t in trazos) when (t) {
        is ComandoTrazo.Mover -> moveTo(t.x * escala, t.y * escala)
        is ComandoTrazo.Linea -> lineTo(t.x * escala, t.y * escala)
        is ComandoTrazo.Curva -> cubicTo(t.x1 * escala, t.y1 * escala, t.x2 * escala, t.y2 * escala, t.x * escala, t.y * escala)
        ComandoTrazo.Cerrar -> close()
    }
}

internal fun DrawScope.dibujarTrazo(trazos: List<ComandoTrazo>, escala: Float, tinta: Color, relleno: Color? = null, grosor: Float = 1.3f) {
    val p = pathDe(trazos, escala)
    relleno?.let { drawPath(p, it) }
    drawPath(p, tinta, style = Stroke(width = grosor * escala, cap = StrokeCap.Round, join = StrokeJoin.Round))
}

/** Rectángulo que contiene las formas (en el viewBox). */
internal fun limites(formas: List<FormaMapa>): FloatArray {
    var x0 = Float.MAX_VALUE; var y0 = Float.MAX_VALUE; var x1 = -Float.MAX_VALUE; var y1 = -Float.MAX_VALUE
    for (f in formas) when (f) {
        is FormaMapa.Elipse -> { val r = maxOf(f.rx, f.ry).takeIf { f.rot != 0f }
            x0 = minOf(x0, f.cx - (r ?: f.rx)); x1 = maxOf(x1, f.cx + (r ?: f.rx))
            y0 = minOf(y0, f.cy - (r ?: f.ry)); y1 = maxOf(y1, f.cy + (r ?: f.ry)) }
        is FormaMapa.Rect -> { x0 = minOf(x0, f.x); x1 = maxOf(x1, f.x + f.w); y0 = minOf(y0, f.y); y1 = maxOf(y1, f.y + f.h) }
    }
    return floatArrayOf(x0, y0, x1, y1)
}

/**
 * Nodo de accesibilidad de una zona: invisible, del tamaño de la zona (mínimo
 * [minimo]), con su nombre y la acción de tocar. No tiene `pointerInput`: el
 * dedo lo atraviesa y lo resuelve el contenedor.
 */
@Composable
internal fun NodoZonaAccesible(
    formas: List<FormaMapa>, escalaDp: Float, descripcion: String, seleccionada: Boolean,
    onActivar: (() -> Unit)?, minimo: Dp = 32.dp,
) {
    val (x0, y0, x1, y1) = limites(formas).let { listOf(it[0], it[1], it[2], it[3]) }
    val w = ((x1 - x0) * escalaDp).coerceAtLeast(minimo.value)
    val h = ((y1 - y0) * escalaDp).coerceAtLeast(minimo.value)
    val cx = (x0 + x1) / 2f * escalaDp
    val cy = (y0 + y1) / 2f * escalaDp
    Box(
        Modifier.offset((cx - w / 2f).dp, (cy - h / 2f).dp).size(w.dp, h.dp).semantics {
            contentDescription = descripcion
            selected = seleccionada
            if (onActivar != null) {
                role = Role.Button
                onClick(label = descripcion) { onActivar(); true }
            }
        },
    )
}
