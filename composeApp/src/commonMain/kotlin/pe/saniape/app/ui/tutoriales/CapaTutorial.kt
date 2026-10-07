package pe.saniape.app.ui.tutoriales

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import pe.saniape.app.tutoriales.EstadoTour
import pe.saniape.app.tutoriales.Fase
import pe.saniape.app.tutoriales.LocalCapaTutorial
import pe.saniape.app.tutoriales.MotorTutoriales
import pe.saniape.app.tutoriales.Movimiento
import pe.saniape.app.tutoriales.PasoApp
import pe.saniape.app.tutoriales.ReglasTutorial
import pe.saniape.app.tutoriales.TutorialApp
import pe.saniape.app.ui.theme.Sania
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * Capa visual de un tutorial en curso (gemela de CapaTutorial.tsx): foco con
 * pulso sobre el elemento REAL, mano animada, tarjeta con progreso, pausa y
 * celebración.
 *
 * NO bloquea nada: el foco y la mano son dibujo sin `pointerInput` (los toques
 * pasan al elemento de abajo); solo la tarjeta tiene botones.
 *
 * Hay un host por ventana: el principal (ClinicaConTabs) y uno dentro de cada
 * diálogo (ver [CapaTutorialDialogo]). El foco lo dibuja el host de la ventana
 * del ancla; la tarjeta, el host de más arriba (un diálogo abierto tapa a la
 * ventana principal).
 */
@Composable
fun HostTutorial(capa: Int, principal: Boolean = false) {
    DisposableEffect(capa) {
        MotorTutoriales.registrarHost(capa)
        onDispose { MotorTutoriales.quitarHost(capa) }
    }
    val estado = MotorTutoriales.estado
    if (estado.fase == Fase.INACTIVO) return
    val tut = MotorTutoriales.tutorial ?: return
    var origen by remember { mutableStateOf(Offset.Zero) }
    var altoPx by remember { mutableStateOf(0f) }
    Box(
        Modifier.fillMaxSize().onGloballyPositioned {
            val b = it.boundsInWindow()
            origen = b.topLeft
            altoPx = b.height
        },
    ) {
        val obj = MotorTutoriales.objetivo
        val r = MotorTutoriales.rectObjetivo
        val paso = MotorTutoriales.pasoActual
        val local = if (estado.fase == Fase.ACTIVO && obj != null && obj.capa == capa && r != null && r.width > 0f && r.height > 0f)
            r.translate(-origen.x, -origen.y) else null
        if (local != null && paso != null) Foco(local, mano = paso.espera.tipo != "manual", altoPx = altoPx)

        if (MotorTutoriales.hostSuperior == capa) {
            when (estado.fase) {
                Fase.ACTIVO -> if (paso != null) {
                    // La tarjeta va del lado opuesto al elemento señalado.
                    val arriba = local != null && altoPx > 0f && (local.top + local.height / 2f) > altoPx * 0.5f
                    Tarjeta(tut, paso, estado, arriba = arriba, principal = principal)
                }
                Fase.PAUSADO -> Pausa(tut, estado, principal)
                Fase.COMPLETADO -> Celebracion(tut, principal)
                Fase.INACTIVO -> Unit
            }
        }
    }
}

/**
 * Envuelve el contenido de un diálogo para que el tutorial se vea DENTRO de
 * él (un Dialog es otra ventana: la capa principal queda debajo de su velo).
 */
@Composable
fun CapaTutorialDialogo(contenido: @Composable BoxScope.() -> Unit) {
    val capa = remember { MotorTutoriales.nuevaCapa() }
    CompositionLocalProvider(LocalCapaTutorial provides capa) {
        Box(Modifier.fillMaxSize()) {
            contenido()
            HostTutorial(capa)
        }
    }
}

// ── Foco: velo suave + anillo con pulso + mano ─────────────────────────────

@Composable
private fun Foco(caja: Rect, mano: Boolean, altoPx: Float) {
    val c = Sania.colors
    val reducir = Movimiento.reducido
    val d = LocalDensity.current
    val pad = with(d) { 6.dp.toPx() }
    val radio = with(d) { 12.dp.toPx() }
    val trazo = with(d) { 3.dp.toPx() }
    val inf = rememberInfiniteTransition(label = "foco")
    val pulso by inf.animateFloat(0f, 1f, infiniteRepeatable(tween(1400, easing = LinearEasing)), label = "pulso")
    val rebote by inf.animateFloat(0f, 1f, infiniteRepeatable(tween(550), RepeatMode.Reverse), label = "rebote")
    val aparicion = remember { Animatable(0f) }
    LaunchedEffect(Unit) { aparicion.animateTo(1f, tween(if (reducir) 0 else 250)) }
    val anillo = c.lav
    Canvas(Modifier.fillMaxSize().alpha(aparicion.value)) {
        val hueco = RoundRect(
            caja.left - pad, caja.top - pad, caja.right + pad, caja.bottom + pad, CornerRadius(radio, radio),
        )
        val velo = Path().apply {
            fillType = PathFillType.EvenOdd
            addRect(Rect(Offset.Zero, size))
            addRoundRect(hueco)
        }
        drawPath(velo, Color(0x2E0F1437))
        drawRoundRect(anillo, hueco.topLeft(), hueco.tamano(), CornerRadius(radio, radio), style = Stroke(trazo))
        if (!reducir) {
            val e = 1f + 0.12f * pulso
            val w = hueco.width * e
            val h = hueco.height * e
            drawRoundRect(
                anillo.copy(alpha = 0.8f * (1f - pulso)),
                Offset(hueco.left + (hueco.width - w) / 2f, hueco.top + (hueco.height - h) / 2f),
                Size(w, h), CornerRadius(radio * e, radio * e), style = Stroke(trazo),
            )
        }
    }
    if (mano) {
        val manoPx = with(d) { 40.dp.toPx() }
        val abajo = caja.bottom + manoPx + pad < altoPx
        val salto = if (reducir) 0f else with(d) { 7.dp.toPx() } * rebote * (if (abajo) -1f else 1f)
        val x = caja.left + minOf(caja.width / 2f, with(d) { 60.dp.toPx() }) - with(d) { 14.dp.toPx() }
        val y = (if (abajo) caja.bottom + pad else caja.top - pad - manoPx) + salto
        Text(
            if (abajo) "👆" else "👇", fontSize = 30.sp,
            modifier = Modifier.offset { IntOffset(x.roundToInt(), y.roundToInt()) }.alpha(aparicion.value),
        )
    }
}

private fun RoundRect.topLeft() = Offset(left, top)
private fun RoundRect.tamano() = Size(width, height)

// ── Tarjeta del paso ───────────────────────────────────────────────────────

@Composable
private fun BoxScope.Tarjeta(tut: TutorialApp, paso: PasoApp, estado: EstadoTour, arriba: Boolean, principal: Boolean) {
    val c = Sania.colors
    val reducir = Movimiento.reducido
    val total = estado.total.coerceAtLeast(1)
    val n = estado.paso + 1
    Box(
        Modifier.align(if (arriba) Alignment.TopCenter else Alignment.BottomCenter)
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .padding(start = 12.dp, end = 12.dp, top = if (arriba) 8.dp else 0.dp, bottom = if (arriba) 0.dp else if (principal) 84.dp else 12.dp)
            .widthIn(max = 440.dp).fillMaxWidth(),
    ) {
        AnimatedContent(
            targetState = paso.id,
            transitionSpec = {
                if (reducir) fadeIn(tween(150)) togetherWith fadeOut(tween(100))
                else (fadeIn(tween(220)) + slideInVertically(spring(dampingRatio = 0.8f, stiffness = 420f)) { it / 6 }) togetherWith fadeOut(tween(120))
            },
            label = "paso",
        ) { _ ->
            Column(
                Modifier.fillMaxWidth().shadow(14.dp, RoundedCornerShape(18.dp))
                    .clip(RoundedCornerShape(18.dp)).background(c.superficie)
                    .border(1.dp, c.borde, RoundedCornerShape(18.dp)),
            ) {
                // Barra de progreso
                val avance by animateFloatAsState(n.toFloat() / total, tween(if (reducir) 0 else 500), label = "avance")
                Box(Modifier.fillMaxWidth().height(5.dp).background(c.chipBg)) {
                    Box(
                        Modifier.fillMaxWidth(avance).height(5.dp)
                            .background(Brush.horizontalGradient(listOf(c.navy, c.lav))),
                    )
                }
                Column(Modifier.padding(14.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(tut.icono, fontSize = 14.sp)
                        Spacer(Modifier.width(6.dp))
                        Text(
                            tut.titulo.uppercase(), color = c.textoSuave, fontSize = 10.sp, fontWeight = FontWeight.Bold,
                            maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f),
                        )
                        Box(Modifier.clip(RoundedCornerShape(50)).background(c.chipBg).padding(horizontal = 8.dp, vertical = 2.dp)) {
                            Text("$n de $total", color = c.navy, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                    Spacer(Modifier.height(6.dp))
                    Text(paso.titulo, color = c.texto, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                    if (paso.texto.isNotBlank()) {
                        Spacer(Modifier.height(2.dp))
                        Text(paso.texto, color = c.textoSuave, fontSize = 13.sp, lineHeight = 18.sp)
                    }
                    ReglasTutorial.textoEspera(paso.espera.tipo)?.let { esperando ->
                        Spacer(Modifier.height(8.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            PuntoLatiendo(reducir)
                            Spacer(Modifier.width(8.dp))
                            Text(esperando, color = c.navy, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                    AvisoNoVisible(paso)
                    Spacer(Modifier.height(12.dp))
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (paso.espera.tipo == "manual") {
                            BotonPrimario(paso.boton ?: "Entendido →") { MotorTutoriales.manual() }
                        }
                        val destino = paso.espera.takeIf { it.tipo == "pantalla" }?.opciones?.firstOrNull()
                        if (destino != null && destino in MotorTutoriales.navegables && destino != MotorTutoriales.pantallaActual) {
                            BotonPrimario("Llévame →") { MotorTutoriales.llevar() }
                        }
                        Box(
                            Modifier.clip(RoundedCornerShape(12.dp)).border(1.dp, c.borde, RoundedCornerShape(12.dp))
                                .clickable { MotorTutoriales.posponer() }.padding(horizontal = 12.dp, vertical = 9.dp),
                        ) { Text("Lo hago después", color = c.texto, fontSize = 12.sp, fontWeight = FontWeight.Bold) }
                        Spacer(Modifier.weight(1f))
                        Text(
                            "Saltar", color = c.textoSuave, fontSize = 12.sp, fontWeight = FontWeight.Bold,
                            modifier = Modifier.clip(RoundedCornerShape(8.dp)).clickable { MotorTutoriales.saltar() }.padding(8.dp),
                        )
                    }
                }
            }
        }
    }
}

/** "¿No lo ves?": solo si el elemento sigue sin aparecer después de un rato. */
@Composable
private fun AvisoNoVisible(paso: PasoApp) {
    val c = Sania.colors
    val sinAncla = paso.anclasEfectivas.isNotEmpty() && MotorTutoriales.objetivo == null
    var vencio by remember(paso.id) { mutableStateOf(false) }
    LaunchedEffect(paso.id, sinAncla) {
        vencio = false
        if (sinAncla) { delay(2500); vencio = true }
    }
    if (!(sinAncla && vencio) || paso.espera.tipo == "pantalla") return
    val fuera = paso.pantallas.isNotEmpty() && MotorTutoriales.pantallaActual !in paso.pantallas
    Spacer(Modifier.height(6.dp))
    Text(
        if (fuera) "Vuelve a la pantalla de este paso para seguir." else "¿No lo ves en pantalla? Desplázate o revisa que haya uno disponible.",
        color = c.pend, fontSize = 12.sp,
    )
}

@Composable
private fun PuntoLatiendo(reducir: Boolean) {
    val c = Sania.colors
    val inf = rememberInfiniteTransition(label = "punto")
    val p by inf.animateFloat(0f, 1f, infiniteRepeatable(tween(1000)), label = "p")
    Box(Modifier.size(10.dp), contentAlignment = Alignment.Center) {
        if (!reducir) Box(Modifier.size(10.dp).scale(1f + p).alpha(0.7f * (1f - p)).clip(CircleShape).background(c.lav))
        Box(Modifier.size(10.dp).clip(CircleShape).background(c.lav))
    }
}

@Composable
private fun BotonPrimario(texto: String, onClick: () -> Unit) {
    val c = Sania.colors
    Box(
        Modifier.clip(RoundedCornerShape(12.dp))
            .background(Brush.linearGradient(listOf(c.navy, c.navyLight)))
            .clickable(onClick = onClick).padding(horizontal = 14.dp, vertical = 9.dp),
    ) { Text(texto, color = c.sobreNavy, fontSize = 12.sp, fontWeight = FontWeight.Bold) }
}

// ── Pausa ──────────────────────────────────────────────────────────────────

@Composable
private fun BoxScope.Pausa(tut: TutorialApp, estado: EstadoTour, principal: Boolean) {
    val c = Sania.colors
    Row(
        Modifier.align(Alignment.BottomCenter).windowInsetsPadding(WindowInsets.safeDrawing)
            .padding(bottom = if (principal) 84.dp else 16.dp, start = 16.dp, end = 16.dp)
            .shadow(10.dp, RoundedCornerShape(50)).clip(RoundedCornerShape(50))
            .background(c.superficie).border(1.dp, c.borde, RoundedCornerShape(50))
            .padding(start = 12.dp, end = 6.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("⏸", fontSize = 13.sp)
        Spacer(Modifier.width(6.dp))
        Text(
            "${tut.icono} ${tut.titulo}", color = c.texto, fontSize = 12.sp, fontWeight = FontWeight.Bold,
            maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.widthIn(max = 170.dp),
        )
        Spacer(Modifier.width(6.dp))
        Text("${estado.paso + 1}/${estado.total}", color = c.textoSuave, fontSize = 11.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.width(8.dp))
        Box(
            Modifier.clip(RoundedCornerShape(50)).background(c.navy).clickable { MotorTutoriales.retomar() }
                .padding(horizontal = 12.dp, vertical = 7.dp),
        ) { Text("Retomar", color = c.sobreNavy, fontSize = 12.sp, fontWeight = FontWeight.Bold) }
        Text(
            "✕", color = c.textoSuave, fontSize = 14.sp,
            modifier = Modifier.clip(CircleShape).clickable { MotorTutoriales.posponer() }.padding(horizontal = 10.dp, vertical = 6.dp),
        )
    }
}

// ── Celebración ────────────────────────────────────────────────────────────

private val CONFETI = listOf(
    Color(0xFF2C3E7A), Color(0xFF8892C8), Color(0xFF16A34A), Color(0xFFD97706),
    Color(0xFF0F766E), Color(0xFF7C3AED), Color(0xFFF472B6),
)

@Composable
private fun BoxScope.Celebracion(tut: TutorialApp, principal: Boolean) {
    val c = Sania.colors
    val reducir = Movimiento.reducido
    // Se cierra sola a los 6 s.
    LaunchedEffect(tut.id) { delay(6000); MotorTutoriales.cerrarCelebracion() }
    val entrada = remember { Animatable(if (reducir) 1f else 0f) }
    val confeti = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        if (!reducir) entrada.animateTo(1f, spring(dampingRatio = 0.55f, stiffness = 380f))
    }
    LaunchedEffect(Unit) { if (!reducir) confeti.animateTo(1f, tween(1500, easing = LinearEasing)) }
    val d = LocalDensity.current
    Box(
        Modifier.align(Alignment.BottomCenter).windowInsetsPadding(WindowInsets.safeDrawing)
            .padding(bottom = if (principal) 90.dp else 20.dp, start = 20.dp, end = 20.dp).widthIn(max = 360.dp).fillMaxWidth(),
        contentAlignment = Alignment.TopCenter,
    ) {
        Column(
            Modifier.fillMaxWidth().scale(0.9f + 0.1f * entrada.value).alpha(entrada.value.coerceIn(0f, 1f))
                .shadow(14.dp, RoundedCornerShape(18.dp)).clip(RoundedCornerShape(18.dp))
                .background(c.superficie).border(1.dp, c.borde, RoundedCornerShape(18.dp)).padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(
                Modifier.size(56.dp).scale(entrada.value).clip(CircleShape).background(c.ok),
                contentAlignment = Alignment.Center,
            ) { Text("✓", color = Color.White, fontSize = 26.sp, fontWeight = FontWeight.Bold) }
            Spacer(Modifier.height(10.dp))
            Text("¡Lo hiciste!", color = c.texto, fontSize = 17.sp, fontWeight = FontWeight.Bold)
            Text("${tut.icono} ${tut.titulo}", color = c.textoSuave, fontSize = 13.sp)
            Spacer(Modifier.height(14.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                BotonPrimario("Otro tutorial") { MotorTutoriales.otroTutorial() }
                Box(
                    Modifier.clip(RoundedCornerShape(12.dp)).border(1.dp, c.borde, RoundedCornerShape(12.dp))
                        .clickable { MotorTutoriales.cerrarCelebracion() }.padding(horizontal = 14.dp, vertical = 9.dp),
                ) { Text("Cerrar", color = c.texto, fontSize = 12.sp, fontWeight = FontWeight.Bold) }
            }
        }
        if (!reducir && confeti.value < 1f) {
            // 30 piezas con trayectoria fija por índice (sin librería), como la web.
            Canvas(Modifier.size(1.dp).offset(y = 24.dp)) {
                val t = confeti.value
                for (i in 0 until 30) {
                    val ang = (i / 30.0) * PI * 2
                    val fuerza = with(d) { (90 + (i * 37) % 70).dp.toPx() }
                    val x = (cos(ang) * fuerza).toFloat() * t
                    val subida = (sin(ang) * fuerza * 0.7).toFloat() - with(d) { 60.dp.toPx() }
                    val y = if (t < 0.45f) subida * (t / 0.45f) else subida + with(d) { 140.dp.toPx() } * ((t - 0.45f) / 0.55f)
                    val alfa = if (t < 0.45f) 1f else 1f - (t - 0.45f) / 0.55f
                    val color = CONFETI[i % CONFETI.size].copy(alpha = alfa.coerceIn(0f, 1f))
                    if (i % 3 == 0) drawCircle(color, with(d) { 4.dp.toPx() }, Offset(x, y))
                    else drawRect(color, Offset(x, y), Size(with(d) { 6.dp.toPx() }, with(d) { 12.dp.toPx() }))
                }
            }
        }
    }
}
