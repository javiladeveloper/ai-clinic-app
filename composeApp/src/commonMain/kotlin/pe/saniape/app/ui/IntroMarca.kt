package pe.saniape.app.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.Canvas
import androidx.compose.ui.geometry.Offset

/**
 * Intro animada de marca al abrir la app. Fondo navy, dibuja un latido (ECG)
 * bajo el nombre "Sania" y luego se desvanece. Llama [onFin] al terminar.
 *
 * Inspirada en el SplashIntro de la web (trazo ECG + marca).
 */
@Composable
fun IntroMarca(onFin: () -> Unit) {
    val textoAlpha = remember { Animatable(0f) }
    val textoScale = remember { Animatable(0.8f) }
    val trazo = remember { Animatable(0f) }
    val salida = remember { Animatable(1f) }

    LaunchedEffect(Unit) {
        // 1) aparece el nombre
        textoScale.animateTo(1f, tween(600, easing = FastOutSlowInEasing))
    }
    LaunchedEffect(Unit) {
        textoAlpha.animateTo(1f, tween(500, easing = LinearEasing))
        // 2) se dibuja el latido
        trazo.animateTo(1f, tween(900, easing = FastOutSlowInEasing))
        // 3) pausa breve y fade-out
        salida.animateTo(0f, tween(450, delayMillis = 350))
        onFin()
    }

    Surface(color = NavyDark, modifier = Modifier.fillMaxSize().alpha(salida.value)) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Box(contentAlignment = Alignment.Center) {
                Text(
                    "Sania",
                    color = Color.White,
                    fontSize = 46.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier
                        .alpha(textoAlpha.value)
                        .scale(textoScale.value),
                )
            }
            // Latido ECG debajo del nombre
            Canvas(
                modifier = Modifier
                    .size(width = 220.dp, height = 60.dp)
                    .alpha(textoAlpha.value)
            ) {
                val w = size.width
                val h = size.height
                val midY = h * 0.62f
                // Puntos de un latido simple, normalizados en X 0..1
                val pts = listOf(
                    0.0f to midY,
                    0.30f to midY,
                    0.40f to midY - h * 0.10f,
                    0.48f to midY + h * 0.38f,
                    0.56f to midY - h * 0.50f,
                    0.64f to midY + h * 0.12f,
                    0.74f to midY,
                    1.0f to midY,
                )
                val total = pts.size - 1
                val avance = (trazo.value * total)
                var i = 0
                while (i < total && i < avance) {
                    val frac = (avance - i).coerceIn(0f, 1f)
                    val a = Offset(pts[i].first * w, pts[i].second)
                    val b = Offset(pts[i + 1].first * w, pts[i + 1].second)
                    val end = Offset(a.x + (b.x - a.x) * frac, a.y + (b.y - a.y) * frac)
                    drawLine(
                        color = Lav,
                        start = a,
                        end = end,
                        strokeWidth = 5f,
                        cap = StrokeCap.Round,
                    )
                    i++
                }
            }
        }
    }
}