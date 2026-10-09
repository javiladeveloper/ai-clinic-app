package pe.saniape.app.ui.clinica.historia

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import pe.saniape.app.data.staff.PuntoSerieHc
import pe.saniape.app.data.staff.numeroHc
import pe.saniape.app.ui.theme.Sania

/**
 * Línea simple de una serie numérica en el tiempo (peso, IMC, perímetro…), sin
 * librerías: Canvas con el mínimo y el máximo como rejilla, un punto por toma y,
 * debajo, la lista en texto (el gráfico nunca es lo único que dice algo).
 * Gemelo en espíritu de `psico/GraficoPerfilPsico.kt` y `fisio/CurvaDolor`.
 */
@Composable
internal fun GraficoLineaHc(
    titulo: String,
    unidad: String,
    puntos: List<PuntoSerieHc>,
    color: Color = Sania.colors.teal,
) {
    if (puntos.isEmpty()) return
    val c = Sania.colors
    val rejilla = c.borde
    val fondo = c.superficie
    val min = remember(puntos) { puntos.minOf { it.valor } }
    val max = remember(puntos) { puntos.maxOf { it.valor } }
    val rango = (max - min).takeIf { it > 0.0001 } ?: 1.0
    val descripcion = "$titulo: " + puntos.joinToString("; ") { "${it.fecha} ${numeroHc(it.valor)} $unidad" }

    Column(Modifier.fillMaxWidth().semantics { contentDescription = descripcion }) {
        Text(titulo, color = c.texto, fontSize = 12.sp, modifier = Modifier.padding(bottom = 4.dp))
        Row(Modifier.fillMaxWidth()) {
            Column(Modifier.height(96.dp).width(40.dp), verticalArrangement = Arrangement.SpaceBetween) {
                Text(numeroHc(max), color = c.textoSuave, fontSize = 8.sp)
                Text(numeroHc(min), color = c.textoSuave, fontSize = 8.sp)
            }
            Canvas(Modifier.weight(1f).height(96.dp)) {
                val padY = 6.dp.toPx()
                val padX = 8.dp.toPx()
                val iw = size.width - padX * 2
                val ih = size.height - padY * 2
                fun x(i: Int) = padX + if (puntos.size == 1) iw / 2 else i * iw / (puntos.size - 1)
                fun y(v: Double) = padY + ih - (((v - min) / rango).toFloat()) * ih
                drawLine(rejilla, Offset(0f, y(max)), Offset(size.width, y(max)), strokeWidth = 1.dp.toPx())
                drawLine(rejilla, Offset(0f, y(min)), Offset(size.width, y(min)), strokeWidth = 1.dp.toPx())
                if (puntos.size > 1) {
                    val p = Path()
                    puntos.forEachIndexed { i, pt -> if (i == 0) p.moveTo(x(i), y(pt.valor)) else p.lineTo(x(i), y(pt.valor)) }
                    drawPath(p, color, style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
                }
                puntos.forEachIndexed { i, pt ->
                    drawCircle(fondo, 4.dp.toPx(), Offset(x(i), y(pt.valor)))
                    drawCircle(color, 4.dp.toPx(), Offset(x(i), y(pt.valor)), style = Stroke(2.dp.toPx()))
                }
            }
        }
        Text(
            puntos.joinToString(" · ") { "${fechaHc(it.fecha)}: ${numeroHc(it.valor)} $unidad".trim() },
            color = c.textoSuave, fontSize = 11.sp, modifier = Modifier.padding(top = 4.dp),
        )
    }
}

/** "2026-10-08" → "08/10/2026"; otra cosa se deja como vino. */
internal fun fechaHc(iso: String?): String {
    if (iso.isNullOrBlank()) return "—"
    val f = iso.take(10)
    val p = f.split("-")
    return if (p.size == 3 && p[0].length == 4) "${p[2]}/${p[1]}/${p[0]}" else iso
}
