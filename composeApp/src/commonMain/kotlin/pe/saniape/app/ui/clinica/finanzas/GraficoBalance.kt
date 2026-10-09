package pe.saniape.app.ui.clinica.finanzas

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import pe.saniape.app.data.staff.Finanzas
import pe.saniape.app.data.staff.formatearDinero
import pe.saniape.app.data.staff.monedaActiva
import pe.saniape.app.ui.theme.Sania

/**
 * Barras de ingresos (verde) y egresos (rojo) por día o por mes — el gráfico de
 * Balance de /finanzas, dibujado con Canvas (sin librerías de gráficos).
 */
@Composable
fun GraficoBalance(barras: List<Finanzas.Barra>, modifier: Modifier = Modifier, moneda: String = monedaActiva()) {
    val c = Sania.colors
    val medidor = rememberTextMeasurer()
    val maximo = barras.maxOfOrNull { maxOf(it.ingresos, it.egresos) } ?: 0.0
    val techo = Finanzas.techoBonito(maximo)
    val estilo = TextStyle(color = c.textoSuave, fontSize = 9.sp)
    val verde = c.ok
    val rojo = c.error
    val rejilla = c.borde
    val descripcion = "Gráfico de balance: ${barras.size} barras, ingresos totales ${formatearDinero(barras.sumOf { it.ingresos }, moneda)}, egresos ${formatearDinero(barras.sumOf { it.egresos }, moneda)}"

    Column(modifier) {
        Canvas(
            Modifier.fillMaxWidth().height(170.dp).semantics { contentDescription = descripcion },
        ) {
            val ejeIzq = 34.dp.toPx()
            val abajo = 16.dp.toPx()
            val alto = size.height - abajo
            val ancho = size.width - ejeIzq
            // Rejilla: 0, mitad y techo (números legibles gracias a techoBonito).
            listOf(0.0, techo / 2, techo).forEach { v ->
                val y = alto - (v / techo * alto).toFloat()
                drawLine(rejilla, Offset(ejeIzq, y), Offset(size.width, y), strokeWidth = 1f,
                    pathEffect = if (v == 0.0) null else PathEffect.dashPathEffect(floatArrayOf(6f, 6f)))
                val etq = medidor.measure(abreviar(v), estilo)
                drawText(etq, topLeft = Offset((ejeIzq - etq.size.width - 4f).coerceAtLeast(0f), (y - etq.size.height / 2f).coerceIn(0f, alto)))
            }
            if (barras.isEmpty()) return@Canvas
            val paso = ancho / barras.size
            val anchoBarra = (paso * 0.36f).coerceIn(1.5f, 18.dp.toPx())
            // Etiquetas del eje X sin amontonarse: como mucho ~8.
            val cadaN = ((barras.size + 7) / 8).coerceAtLeast(1)
            barras.forEachIndexed { i, b ->
                val x0 = ejeIzq + paso * i + paso / 2f
                val hIn = (b.ingresos / techo * alto).toFloat()
                val hEg = (b.egresos / techo * alto).toFloat()
                if (hIn > 0f) drawRoundRect(verde, Offset(x0 - anchoBarra, alto - hIn), Size(anchoBarra, hIn), CornerRadius(2f, 2f))
                if (hEg > 0f) drawRoundRect(rojo, Offset(x0, alto - hEg), Size(anchoBarra, hEg), CornerRadius(2f, 2f))
                if (i % cadaN == 0 || i == barras.lastIndex) {
                    val etq = medidor.measure(b.etiqueta, estilo)
                    val x = (x0 - etq.size.width / 2f).coerceIn(ejeIzq, (size.width - etq.size.width).coerceAtLeast(ejeIzq))
                    drawText(etq, topLeft = Offset(x, alto + 3f))
                }
            }
        }
        Spacer(Modifier.height(6.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Leyenda("Ingresos", verde)
            Spacer(Modifier.width(14.dp))
            Leyenda("Egresos", rojo)
        }
    }
}

@Composable
private fun Leyenda(texto: String, color: Color) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(8.dp).clip(CircleShape).background(color))
        Spacer(Modifier.width(5.dp))
        Text(texto, color = Sania.colors.textoSuave, fontSize = 11.sp, modifier = Modifier.padding(end = 2.dp))
    }
}

/** 1500 → "1.5k" para el eje (los montos exactos están en las tarjetas). */
private fun abreviar(v: Double): String = when {
    v >= 1000 -> {
        val k = v / 1000
        if (k % 1.0 == 0.0) "${k.toInt()}k" else "${(k * 10).toInt() / 10.0}k"
    }
    else -> v.toInt().toString()
}
