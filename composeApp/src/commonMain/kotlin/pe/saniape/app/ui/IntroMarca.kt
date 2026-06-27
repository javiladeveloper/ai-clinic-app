package pe.saniape.app.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import pe.saniape.app.ui.theme.Paleta

/**
 * Intro animada de marca al abrir la app. Fondo navy con un halo radial; aparece
 * "Sania" con fade+scale, se dibuja un latido (ECG) con glow, y un destello final.
 * Reproduce el "shimmer" de marca. Llama [onFin] al terminar.
 */
@Composable
fun IntroMarca(onFin: () -> Unit) {
    val textoAlpha = remember { Animatable(0f) }
    val textoScale = remember { Animatable(0.78f) }
    val trazo = remember { Animatable(0f) }
    val halo = remember { Animatable(0f) }
    val destello = remember { Animatable(0f) }
    val salida = remember { Animatable(1f) }

    val sonar = recordarSonidoIntro()

    LaunchedEffect(Unit) {
        sonar() // shimmer al arrancar
    }
    LaunchedEffect(Unit) {
        halo.animateTo(1f, tween(700, easing = FastOutSlowInEasing))
    }
    LaunchedEffect(Unit) {
        textoScale.animateTo(1f, tween(650, easing = FastOutSlowInEasing))
    }
    LaunchedEffect(Unit) {
        textoAlpha.animateTo(1f, tween(550, easing = LinearEasing))
        trazo.animateTo(1f, tween(950, easing = FastOutSlowInEasing))
        // Destello rápido al completar el latido
        destello.animateTo(1f, tween(160, easing = LinearEasing))
        destello.animateTo(0f, tween(260, easing = LinearEasing))
        // Fade-out final
        salida.animateTo(0f, tween(420, delayMillis = 220))
        onFin()
    }

    Surface(color = Paleta.Navy700, modifier = Modifier.fillMaxSize().alpha(salida.value)) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            // Halo radial suave detrás de la marca
            Canvas(Modifier.fillMaxSize()) {
                val centro = Offset(size.width / 2, size.height * 0.46f)
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            Paleta.Navy500.copy(alpha = 0.55f * halo.value),
                            Paleta.Navy700.copy(alpha = 0f),
                        ),
                        center = centro,
                        radius = size.width * 0.7f,
                    ),
                    radius = size.width * 0.7f,
                    center = centro,
                )
            }

            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    "Sania",
                    color = Color.White,
                    fontSize = 48.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.alpha(textoAlpha.value).scale(textoScale.value),
                )
                Spacer(Modifier.height(14.dp))
                // Latido ECG con glow
                Canvas(
                    Modifier.size(width = 230.dp, height = 64.dp).alpha(textoAlpha.value),
                ) {
                    val w = size.width
                    val h = size.height
                    val midY = h * 0.62f
                    val pts = listOf(
                        0.0f to midY,
                        0.28f to midY,
                        0.38f to midY - h * 0.10f,
                        0.46f to midY + h * 0.40f,
                        0.54f to midY - h * 0.52f,
                        0.62f to midY + h * 0.14f,
                        0.72f to midY,
                        1.0f to midY,
                    )
                    val total = pts.size - 1
                    val avance = trazo.value * total
                    var i = 0
                    while (i < total && i < avance) {
                        val frac = (avance - i).coerceIn(0f, 1f)
                        val a = Offset(pts[i].first * w, pts[i].second)
                        val b = Offset(pts[i + 1].first * w, pts[i + 1].second)
                        val end = Offset(a.x + (b.x - a.x) * frac, a.y + (b.y - a.y) * frac)
                        // Glow (trazo grueso translúcido) + línea nítida encima
                        drawLine(Paleta.Lav300.copy(alpha = 0.35f), a, end, strokeWidth = 12f, cap = StrokeCap.Round)
                        drawLine(Color.White, a, end, strokeWidth = 4.5f, cap = StrokeCap.Round)
                        i++
                    }
                    // Punto brillante en la punta del trazo
                    if (avance in 0.01f..total.toFloat()) {
                        val idx = avance.toInt().coerceIn(0, total - 1)
                        val frac = (avance - idx).coerceIn(0f, 1f)
                        val a = Offset(pts[idx].first * w, pts[idx].second)
                        val b = Offset(pts[idx + 1].first * w, pts[idx + 1].second)
                        val p = Offset(a.x + (b.x - a.x) * frac, a.y + (b.y - a.y) * frac)
                        drawCircle(Color.White, radius = 6f, center = p)
                        drawCircle(Paleta.Lav300.copy(alpha = 0.5f), radius = 12f, center = p)
                    }
                }
            }

            // Destello blanco al terminar el latido
            if (destello.value > 0f) {
                Box(Modifier.fillMaxSize().background(Color.White.copy(alpha = 0.16f * destello.value)))
            }
        }
    }
}