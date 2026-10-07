package pe.saniape.app.ui.tutoriales

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import pe.saniape.app.tutoriales.Fase
import pe.saniape.app.tutoriales.MotorTutoriales
import pe.saniape.app.tutoriales.Movimiento
import pe.saniape.app.tutoriales.ReglasTutorial
import pe.saniape.app.tutoriales.TutorialApp
import pe.saniape.app.ui.theme.Sania

private const val DURACION_MS = 8000L

/** Píldoras ya ofrecidas en esta sesión (por si el POST de "visto" no llegó). */
private val ofrecidas = mutableSetOf<String>()

/**
 * "¿Primera vez aquí? Míralo en 30 s" (gemela de PildoraPrimeraVez.tsx).
 * Discreta: no bloquea, se va sola a los 8 s, una vez por pantalla y por
 * usuario (MISMA clave que la web: si la vio en la computadora no reaparece).
 * SOLO en clínicas con Primeros pasos activos: DALU nunca la ve.
 */
@Composable
fun PildoraPrimeraVez() {
    val pantalla = MotorTutoriales.pantallaActual
    val activos = MotorTutoriales.primerosPasosActivos
    val cat = MotorTutoriales.catalogo
    var oferta by remember { mutableStateOf<TutorialApp?>(null) }
    val ocupado = MotorTutoriales.estado.fase != Fase.INACTIVO || MotorTutoriales.ayudaAbierta

    LaunchedEffect(pantalla, activos, cat != null, ocupado) {
        oferta = null
        if (!activos || cat == null || ocupado || pantalla == null) return@LaunchedEffect
        delay(1500)
        if (MotorTutoriales.hayDialogo || MotorTutoriales.pantallaActual != pantalla) return@LaunchedEffect
        val guia = cat.guia(pantalla) ?: return@LaunchedEffect
        val clave = guia.clavePildora ?: return@LaunchedEffect
        if (clave in ofrecidas) return@LaunchedEffect
        val tut = ReglasTutorial.pildora(guia, cat, MotorTutoriales.progreso, activos) ?: return@LaunchedEffect
        ofrecidas += clave
        MotorTutoriales.marcarVisto(clave)
        oferta = tut
        delay(DURACION_MS)
        oferta = null
    }

    val reducir = Movimiento.reducido
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.BottomCenter) {
        AnimatedVisibility(
            visible = oferta != null && !ocupado,
            enter = if (reducir) fadeIn(tween(150)) else slideInVertically(spring(dampingRatio = 0.7f, stiffness = 420f)) { it } + fadeIn() + scaleIn(initialScale = 0.9f),
            exit = if (reducir) fadeOut(tween(150)) else slideOutVertically(tween(200)) { it / 2 } + fadeOut(tween(200)),
        ) {
            val t = remember(oferta) { oferta } ?: return@AnimatedVisibility
            Contenido(t, reducir, onVer = { oferta = null; MotorTutoriales.iniciar(t.id) },
                onNoMostrar = { oferta = null; MotorTutoriales.marcarVisto(ReglasTutorial.CLAVE_PILDORAS_OFF) },
                onCerrar = { oferta = null })
        }
    }
}

@Composable
private fun Contenido(t: TutorialApp, reducir: Boolean, onVer: () -> Unit, onNoMostrar: () -> Unit, onCerrar: () -> Unit) {
    val c = Sania.colors
    Column(
        Modifier.padding(bottom = 92.dp, start = 12.dp, end = 12.dp).widthIn(max = 460.dp)
            .shadow(10.dp, RoundedCornerShape(50)).clip(RoundedCornerShape(50))
            .background(c.superficie).border(1.dp, c.borde, RoundedCornerShape(50)),
    ) {
        Row(Modifier.padding(5.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.weight(1f, fill = false).clip(RoundedCornerShape(50))
                    .background(Brush.linearGradient(listOf(c.navy, c.navyLight)))
                    .clickable(onClick = onVer).padding(horizontal = 12.dp, vertical = 8.dp),
            ) {
                // Brillo que cruza el botón (sin él, si se pide reducir movimiento).
                if (!reducir) {
                    val inf = rememberInfiniteTransition(label = "brillo")
                    val x by inf.animateFloat(-0.3f, 1.3f, infiniteRepeatable(tween(3000, easing = LinearEasing), RepeatMode.Restart), label = "x")
                    Box(Modifier.matchParentSize().background(Brush.horizontalGradient(
                        0f to androidx.compose.ui.graphics.Color.Transparent,
                        (x - 0.1f).coerceIn(0f, 1f) to androidx.compose.ui.graphics.Color.Transparent,
                        x.coerceIn(0f, 1f) to androidx.compose.ui.graphics.Color.White.copy(alpha = 0.22f),
                        (x + 0.1f).coerceIn(0f, 1f) to androidx.compose.ui.graphics.Color.Transparent,
                        1f to androidx.compose.ui.graphics.Color.Transparent,
                    )))
                }
                Text(
                    "${t.icono}  ¿Primera vez aquí? Míralo en ${t.duracion}", color = c.sobreNavy, fontSize = 13.sp,
                    fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
            }
            Text(
                "No mostrar más", color = c.textoSuave, fontSize = 11.sp, fontWeight = FontWeight.Bold,
                modifier = Modifier.clip(RoundedCornerShape(8.dp)).clickable(onClick = onNoMostrar).padding(horizontal = 6.dp, vertical = 6.dp),
            )
            Text(
                "✕", color = c.textoSuave, fontSize = 13.sp,
                modifier = Modifier.clip(CircleShape).clickable(onClick = onCerrar).padding(horizontal = 8.dp, vertical = 6.dp),
            )
        }
        // Cuenta regresiva: una línea que se vacía en 8 s.
        if (!reducir) {
            val inicio = remember { androidx.compose.animation.core.Animatable(1f) }
            LaunchedEffect(t.id) { inicio.animateTo(0f, tween(DURACION_MS.toInt(), easing = LinearEasing)) }
            Box(Modifier.fillMaxWidth().padding(horizontal = 18.dp).height(2.dp)) {
                Box(Modifier.fillMaxWidth(inicio.value).height(2.dp).clip(RoundedCornerShape(50)).background(c.lav))
            }
        }
    }
}
