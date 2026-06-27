package pe.saniape.app.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Isotipo de Sania — EL MISMO que la web (components/ui/Logo.tsx): una "S" cuyo
 * trazo central es una línea de pulso/latido (ECG), con gradiente azul→morado.
 * El path está en viewBox 0 0 48 48, así que lo escalamos al tamaño dado.
 */
private const val SANIA_MARK_PATH =
    "M34 13 C29 9 16.5 8.6 14 15 C12.2 19.8 17 23.4 22 24 L24.6 24 L27 18.6 L29.4 29 L31.4 24 " +
        "C35.4 24.6 37.4 27.6 36.3 31.3 C34.5 37.8 21.5 39.5 15.8 34.8"

private val AZUL = Color(0xFF3B82F6)
private val MORADO = Color(0xFF8B5CF6)

@Composable
fun LogoSania(size: Dp = 32.dp, modifier: Modifier = Modifier) {
    val path = remember { PathParser().parsePathString(SANIA_MARK_PATH).toPath() }
    Canvas(modifier = modifier.size(size)) {
        val escala = this.size.width / 48f
        val gradiente = Brush.linearGradient(
            colors = listOf(AZUL, MORADO),
            start = Offset(10f * escala, 8f * escala),
            end = Offset(38f * escala, 40f * escala),
        )
        scale(escala, escala, pivot = Offset.Zero) {
            drawPath(
                path = path,
                brush = gradiente,
                style = Stroke(width = 4.8f, cap = StrokeCap.Round, join = StrokeJoin.Round),
            )
        }
    }
}