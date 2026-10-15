package pe.saniape.app.ui.clinica.pediatria

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import pe.saniape.app.data.staff.IndicadorCrecimiento
import pe.saniape.app.data.staff.PuntoCurva
import pe.saniape.app.data.staff.PuntoPaciente
import pe.saniape.app.data.staff.VentanaCurva
import pe.saniape.app.data.staff.escalaCurva
import pe.saniape.app.data.staff.marcasXCurva
import pe.saniape.app.data.staff.numComa
import pe.saniape.app.ui.theme.Sania

/**
 * Una CURVA DE CRECIMIENTO OMS en Canvas (sin librerías), gemela de
 * CurvaCrecimiento.tsx: bandas −3…+3 DE (normal en verde, ±2…±3 en ámbar), las
 * líneas −3, −2, 0, +2, +3 y los puntos del niño unidos por fecha. Mismas
 * marcas de ejes y misma escala que la hoja impresa (`escalaCurva` /
 * `marcasXCurva`). Colores del tema (modo oscuro incluido). Proporción 640×340
 * como la web: cabe en un teléfono sin desbordar. Lo dice también en texto
 * (contentDescription) para lectores de pantalla.
 */
@Composable
fun GraficoCrecimiento(
    indicador: IndicadorCrecimiento,
    curva: List<PuntoCurva>,
    puntos: List<PuntoPaciente>,
    ventana: VentanaCurva,
    modifier: Modifier = Modifier,
) {
    if (curva.size < 2) return
    val c = Sania.colors
    val medidor = rememberTextMeasurer()
    val escala = remember(curva, puntos) { escalaCurva(curva, puntos) }
    val marcasX = remember(ventana) { marcasXCurva(ventana) }
    val verde = c.ok; val ambar = c.pend; val rojo = c.error
    val texto = c.textoSuave; val grilla = c.borde; val punto = c.navy; val fondo = c.superficie
    val estilo = TextStyle(fontSize = 9.sp, color = texto)
    val descripcion = "${indicador.nombre} (${indicador.unidad}), patrones OMS con líneas de −3 a +3 desviaciones estándar" +
        (if (puntos.isNotEmpty()) "; ${puntos.size} ${if (puntos.size == 1) "medición" else "mediciones"}, la última ${numComa(puntos.last().valor)} ${indicador.unidad}" else "")

    Canvas(modifier.fillMaxWidth().aspectRatio(640f / 340f).semantics { contentDescription = descripcion }) {
        val izq = 34.dp.toPx(); val der = 22.dp.toPx(); val arr = 8.dp.toPx(); val aba = 26.dp.toPx()
        val ancho = size.width - izq - der
        val alto = size.height - arr - aba
        fun px(x: Double) = izq + (((x - ventana.desde) / (ventana.hasta - ventana.desde)).toFloat() * ancho)
        fun py(y: Double) = arr + ((1 - (y - escala.yMin) / (escala.yMax - escala.yMin)).toFloat() * alto)
        fun linea(z: Int) = Path().apply { curva.forEachIndexed { i, p -> if (i == 0) moveTo(px(p.x), py(p.en(z))) else lineTo(px(p.x), py(p.en(z))) } }
        fun banda(a: Int, b: Int) = Path().apply {
            curva.forEachIndexed { i, p -> if (i == 0) moveTo(px(p.x), py(p.en(a))) else lineTo(px(p.x), py(p.en(a))) }
            curva.asReversed().forEach { p -> lineTo(px(p.x), py(p.en(b))) }
            close()
        }
        // Bandas
        drawPath(banda(-2, 2), verde.copy(alpha = 0.10f))
        drawPath(banda(2, 3), ambar.copy(alpha = 0.12f))
        drawPath(banda(-3, -2), ambar.copy(alpha = 0.12f))
        // Grilla y ejes
        escala.marcas.forEach { y ->
            val yy = py(y)
            drawLine(grilla, Offset(izq, yy), Offset(size.width - der, yy), strokeWidth = 0.6.dp.toPx())
            val t = medidor.measure(numComa(y), estilo)
            drawText(t, topLeft = Offset(izq - t.size.width - 4.dp.toPx(), yy - t.size.height / 2f))
        }
        marcasX.forEach { m ->
            val xx = px(m.x)
            drawLine(grilla, Offset(xx, arr), Offset(xx, size.height - aba), strokeWidth = 0.4.dp.toPx())
            val t = medidor.measure(m.texto, estilo)
            drawText(t, topLeft = Offset((xx - t.size.width / 2f).coerceIn(0f, size.width - t.size.width), size.height - aba + 3.dp.toPx()))
        }
        val eje = medidor.measure(if (ventana.ejeCm) "Talla (cm)" else "Edad", estilo)
        drawText(eje, topLeft = Offset(izq + (ancho - eje.size.width) / 2f, size.height - eje.size.height.toFloat()))
        // Líneas de referencia OMS con su etiqueta al final
        val ultimo = curva.last()
        listOf(3 to rojo, 2 to ambar, 0 to verde, -2 to ambar, -3 to rojo).forEach { (z, color) ->
            drawPath(
                linea(z), color,
                style = Stroke(
                    width = (if (z == 0) 1.8 else 1.1).dp.toPx(),
                    pathEffect = if (z == 3 || z == -3) PathEffect.dashPathEffect(floatArrayOf(4.dp.toPx(), 3.dp.toPx())) else null,
                ),
            )
            val t = medidor.measure(if (z > 0) "+$z" else if (z < 0) "−${-z}" else "0", TextStyle(fontSize = 9.sp, color = color, fontWeight = FontWeight.Bold))
            drawText(t, topLeft = Offset(px(ultimo.x) + 3.dp.toPx(), py(ultimo.en(z)) - t.size.height / 2f))
        }
        // El niño
        if (puntos.size > 1) {
            val p = Path().apply { puntos.forEachIndexed { i, pt -> if (i == 0) moveTo(px(pt.x), py(pt.valor)) else lineTo(px(pt.x), py(pt.valor)) } }
            drawPath(p, punto, style = Stroke(width = 1.6.dp.toPx()))
        }
        puntos.forEach { pt ->
            val o = Offset(px(pt.x), py(pt.valor))
            drawCircle(fondo, 4.6.dp.toPx(), o)
            drawCircle(punto, 3.4.dp.toPx(), o)
        }
    }
}

/** Colores del nivel (verde / ámbar / rojo / azul) con el tema. */
@Composable
internal fun coloresNivel(n: pe.saniape.app.data.staff.NivelCrecimiento?): Pair<Color, Color> {
    val c = Sania.colors
    return when (n) {
        pe.saniape.app.data.staff.NivelCrecimiento.OK -> c.ok to c.okBg
        pe.saniape.app.data.staff.NivelCrecimiento.ALERTA -> c.pend to c.pendBg
        pe.saniape.app.data.staff.NivelCrecimiento.RIESGO -> c.error to c.errorBg
        pe.saniape.app.data.staff.NivelCrecimiento.INFO -> c.info to c.infoBg
        null -> c.textoSuave to c.chipBg
    }
}
