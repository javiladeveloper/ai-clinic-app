package pe.saniape.app.ui.clinica

import pe.saniape.app.data.staff.LocalTerminologiaPaciente
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import pe.saniape.app.data.Supabase
import pe.saniape.app.data.staff.ContextoStaff
import pe.saniape.app.data.staff.EstadoPrimerosPasos
import pe.saniape.app.data.staff.OnboardingRepo
import pe.saniape.app.data.staff.ResumenEjemplo
import pe.saniape.app.data.staff.textoConfirmacionEjemplo
import pe.saniape.app.data.staff.urlPaginaClinica
import pe.saniape.app.tutoriales.MotorTutoriales
import pe.saniape.app.tutoriales.Movimiento
import pe.saniape.app.ui.Reanudacion
import pe.saniape.app.ui.Toaster
import pe.saniape.app.ui.recordarAcciones
import pe.saniape.app.ui.theme.Sania

private const val SOPORTE_WA = "51916884168"

/** Lo último que se vio de Primeros pasos (para no parpadear al volver al Inicio). */
private object MemoriaPrimerosPasos {
    var clinica: String? = null
    var datos by mutableStateOf<EstadoPrimerosPasos?>(null)
    var estado by mutableStateOf<String?>(null)
    var hechas: Set<String>? = null
}

/**
 * "Primeros pasos" (onboarding v2, gemela de GuiaOnboarding.tsx): la tarjeta
 * viva "3 de 7" de una clínica nueva. Las tareas se marcan SOLAS en el servidor
 * (leyendo la base); la app solo vuelve a pedir el estado al entrar al Inicio y
 * al volver al frente. Solo el Admin, y solo si la clínica tiene Primeros
 * pasos: DALU y las de antes no piden ni pintan nada.
 *
 * [onIr] navega a una pantalla de la app ("Profesionales", "Pacientes", "Agenda").
 */
@Composable
fun TarjetaPrimerosPasos(ctx: ContextoStaff, onIr: (String) -> Unit) {
    if (!ctx.esAdmin || ctx.primerosPasos == null) return
    val m = MemoriaPrimerosPasos
    if (m.clinica != ctx.clinicaId) { m.clinica = ctx.clinicaId; m.datos = null; m.estado = ctx.primerosPasos; m.hechas = null }
    val estado = m.estado
    if (estado != "visible" && estado != "minimizado") return

    val c = Sania.colors
    val scope = rememberCoroutineScope()
    val acciones = recordarAcciones()
    var recien by remember { mutableStateOf<Set<String>>(emptySet()) }
    var confirmarDescarte by remember { mutableStateOf(false) }
    var ejemplo by remember { mutableStateOf<ResumenEjemplo?>(null) }
    var borrando by remember { mutableStateOf(false) }

    suspend fun cargar() {
        when (val r = OnboardingRepo.estado()) {
            is OnboardingRepo.R.Ok -> {
                val d = r.dato
                val ahora = d.tareas.filter { it.hecho }.map { it.clave }.toSet()
                val antes = m.hechas
                if (antes != null) {
                    val nuevas = ahora - antes
                    if (nuevas.isNotEmpty()) {
                        recien = nuevas
                        d.tareas.firstOrNull { it.clave == nuevas.first() }?.let { Toaster.exito("¡Listo! ${it.titulo} · ${d.hechas} de ${d.total}") }
                    }
                }
                m.hechas = ahora
                m.datos = d
                d.estado?.let { m.estado = it }
            }
            is OnboardingRepo.R.Error -> Unit // la tarjeta simplemente no aparece / queda la última
        }
    }
    LaunchedEffect(Reanudacion.contador) { cargar() }
    LaunchedEffect(recien) { if (recien.isNotEmpty()) { delay(2400); recien = emptySet() } }

    fun cambiar(nuevo: String) {
        val anterior = m.estado
        m.estado = nuevo
        MotorTutoriales.actualizarPrimerosPasos(nuevo == "visible" || nuevo == "minimizado")
        scope.launch {
            val r = OnboardingRepo.cambiarEstado(nuevo)
            if (r is OnboardingRepo.R.Error) {
                m.estado = anterior
                MotorTutoriales.actualizarPrimerosPasos(anterior == "visible" || anterior == "minimizado")
                Toaster.error(r.mensaje)
            }
        }
    }

    val d = m.datos ?: return
    if (!d.mostrar) return
    val pct = if (d.total > 0) d.hechas.toFloat() / d.total else 0f
    val reducir = Movimiento.reducido

    if (confirmarDescarte) {
        AlertDialog(
            onDismissRequest = { confirmarDescarte = false },
            title = { Text("¿Ocultar \"Primeros pasos\" para siempre?") },
            text = { Text("Puedes seguir usando todo normalmente; solo dejarás de ver esta guía.") },
            confirmButton = { TextButton({ confirmarDescarte = false; cambiar("descartado") }) { Text("Ocultar", color = c.error) } },
            dismissButton = { TextButton({ confirmarDescarte = false }) { Text("Cancelar", color = c.textoSuave) } },
        )
    }
    ejemplo?.let { r ->
        AlertDialog(
            onDismissRequest = { ejemplo = null },
            title = { Text("¿Borrar los datos de ejemplo?") },
            text = { Text(textoConfirmacionEjemplo(r)) },
            confirmButton = {
                TextButton({
                    ejemplo = null; borrando = true
                    scope.launch {
                        val res = OnboardingRepo.borrarEjemplo()
                        borrando = false
                        if (res is OnboardingRepo.R.Error) Toaster.error(res.mensaje)
                        else { Toaster.exito("Datos de ejemplo borrados"); cargar() }
                    }
                }) { Text("Borrar", color = c.error) }
            },
            dismissButton = { TextButton({ ejemplo = null }) { Text("Cancelar", color = c.textoSuave) } },
        )
    }

    AnimatedContent(
        targetState = m.estado == "minimizado",
        transitionSpec = { fadeIn(tween(if (reducir) 0 else 200)) togetherWith fadeOut(tween(if (reducir) 0 else 150)) },
        label = "pp",
    ) { minimizada ->
        if (minimizada) {
            // Minimizada: una fila con el anillo de progreso.
            Row(
                Modifier.fillMaxWidth().padding(bottom = Sania.dim.md).clip(RoundedCornerShape(50)).background(c.navy)
                    .clickable { cambiar("visible") }.padding(horizontal = 10.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Anillo(pct, "${d.hechas}/${d.total}", reducir)
                Spacer(Modifier.width(10.dp))
                Text("🚀 Primeros pasos", color = c.sobreNavy, fontSize = 14.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                Text("Abrir ▴", color = c.sobreNavy.copy(alpha = 0.8f), fontSize = 12.sp, fontWeight = FontWeight.Bold)
            }
        } else {
            Column(
                Modifier.fillMaxWidth().padding(bottom = Sania.dim.lg).clip(RoundedCornerShape(Sania.shape.md.dp))
                    .background(c.superficie).border(1.dp, c.borde, RoundedCornerShape(Sania.shape.md.dp)).animateContentSize(),
            ) {
                Column(
                    Modifier.fillMaxWidth()
                        .background(Brush.linearGradient(listOf(c.navyDark, Color(0xFF2C3E7A), Color(0xFF4A5FA8))))
                        .padding(16.dp),
                ) {
                    Row(verticalAlignment = Alignment.Top) {
                        Column(Modifier.weight(1f)) {
                            val nombre = ctx.nombre?.trim()?.split(" ")?.firstOrNull()?.takeIf { it.isNotBlank() }
                            Text(
                                if (d.completas) "¡Tu clínica está lista! 🎉" else "¡Bienvenido${nombre?.let { ", $it" } ?: ""}! 🚀",
                                color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold,
                            )
                            Text(
                                if (d.completas) "Completaste todos los primeros pasos. Ya puedes ocultar esta guía."
                                else "Tus primeros pasos se marcan solos a medida que avanzas.",
                                color = Color.White.copy(alpha = 0.85f), fontSize = 12.sp,
                            )
                        }
                        BotonRedondo("▾") { cambiar("minimizado") }
                        Spacer(Modifier.width(6.dp))
                        BotonRedondo("✕") { confirmarDescarte = true }
                    }
                    Spacer(Modifier.height(12.dp))
                    Row {
                        Text("${d.hechas} de ${d.total}", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                        Text("${(pct * 100).toInt()}%", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                    Spacer(Modifier.height(6.dp))
                    val ancho by animateFloatAsState(pct, tween(if (reducir) 0 else 700), label = "pp-bar")
                    Box(Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(50)).background(Color.White.copy(alpha = 0.2f))) {
                        Box(Modifier.fillMaxWidth(ancho).height(8.dp).clip(RoundedCornerShape(50)).background(Color.White))
                    }
                }
                val siguiente = d.tareas.firstOrNull { !it.hecho }?.clave
                Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    d.tareas.forEach { t ->
                        val esSig = t.clave == siguiente
                        val pulso = remember(t.clave) { Animatable(1f) }
                        LaunchedEffect(t.clave in recien) {
                            if (t.clave in recien && !reducir) { pulso.animateTo(1.04f, tween(220)); pulso.animateTo(1f, spring()) }
                        }
                        Row(
                            Modifier.fillMaxWidth().scale(pulso.value).clip(RoundedCornerShape(12.dp))
                                .background(if (t.hecho) c.fondo else c.superficie)
                                .border(if (esSig) 2.dp else 1.dp, if (esSig) c.navy else c.borde, RoundedCornerShape(12.dp))
                                .padding(10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Box(
                                Modifier.size(32.dp).clip(CircleShape).background(if (t.hecho) c.ok else c.borde),
                                contentAlignment = Alignment.Center,
                            ) {
                                AnimatedContent(t.hecho, transitionSpec = { scaleIn(spring(dampingRatio = 0.5f)) togetherWith fadeOut() }, label = "ok") { ok ->
                                    if (ok) Text("✓", color = Color.White, fontWeight = FontWeight.Bold) else Text(t.icono, fontSize = 15.sp)
                                }
                            }
                            Spacer(Modifier.width(10.dp))
                            Column(Modifier.weight(1f)) {
                                Text(
                                    t.titulo, color = if (t.hecho) c.textoSuave else c.texto, fontSize = 13.sp, fontWeight = FontWeight.Bold,
                                    textDecoration = if (t.hecho) TextDecoration.LineThrough else null,
                                )
                                if (!t.hecho && t.desc.isNotBlank()) Text(t.desc, color = c.textoSuave, fontSize = 11.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                            }
                            if (!t.hecho) {
                                val tutId = MotorTutoriales.catalogo?.tutorialDeTarea?.get(t.clave)
                                    ?.takeIf { MotorTutoriales.catalogo?.tutorial(it) != null }
                                if (tutId != null) {
                                    Spacer(Modifier.width(6.dp))
                                    Text(
                                        "▶", color = c.navy, fontSize = 13.sp, fontWeight = FontWeight.Bold,
                                        modifier = Modifier.clip(RoundedCornerShape(8.dp)).background(c.chipBg)
                                            .clickable { MotorTutoriales.iniciar(tutId) }.padding(horizontal = 10.dp, vertical = 8.dp),
                                    )
                                }
                                Spacer(Modifier.width(6.dp))
                                Text(
                                    accionDe(t.clave, d.slug != null), color = if (esSig) c.sobreNavy else c.navy, fontSize = 11.sp, fontWeight = FontWeight.Bold,
                                    modifier = Modifier.clip(RoundedCornerShape(8.dp))
                                        .background(if (esSig) c.navy else c.superficie)
                                        .border(1.dp, if (esSig) c.navy else c.borde, RoundedCornerShape(8.dp))
                                        .clickable {
                                            when (t.clave) {
                                                "profesional" -> onIr("Profesionales")
                                                "paciente" -> onIr("Pacientes")
                                                "cita", "cobro" -> onIr("Agenda")
                                                "equipo" -> acciones.abrirUrl("${Supabase.SITE_URL}/equipo")
                                                "whatsapp" -> acciones.abrirUrl("${Supabase.SITE_URL}/configuracion?tab=canales")
                                                "pagina" -> {
                                                    val slug = d.slug
                                                    if (slug != null) {
                                                        acciones.abrirUrl(MotorTutoriales.catalogo?.pagina?.url ?: urlPaginaClinica(slug))
                                                        paginaCompartida(scope)
                                                        scope.launch { delay(800); cargar() }
                                                    } else {
                                                        val txt = "Hola, quiero publicar la página de mi clínica \"${ctx.clinicaNombre}\" en Sania."
                                                        acciones.abrirUrl("https://wa.me/$SOPORTE_WA?text=${pe.saniape.app.ui.urlEncode(txt)}")
                                                    }
                                                }
                                            }
                                        }.padding(horizontal = 10.dp, vertical = 8.dp),
                                )
                            }
                        }
                    }
                    if (d.tieneEjemplo) {
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 4.dp)) {
                            Text("🧪 Tienes un ${LocalTerminologiaPaciente.current.paciente} de ejemplo para probar.", color = c.textoSuave, fontSize = 12.sp, modifier = Modifier.weight(1f))
                            Text(
                                if (borrando) "Borrando…" else "Borrar datos de ejemplo", color = c.error, fontSize = 12.sp, fontWeight = FontWeight.Bold,
                                modifier = Modifier.clip(RoundedCornerShape(6.dp)).clickable(enabled = !borrando) {
                                    scope.launch {
                                        when (val pre = OnboardingRepo.simularEjemplo()) {
                                            is OnboardingRepo.R.Error -> Toaster.error(pre.mensaje)
                                            is OnboardingRepo.R.Ok -> if (pre.dato.n == 0) { Toaster.info("No hay datos de ejemplo para borrar."); cargar() } else ejemplo = pre.dato
                                        }
                                    }
                                }.padding(6.dp),
                            )
                        }
                    }
                }
            }
        }
    }
}

private fun accionDe(clave: String, conSlug: Boolean): String = when (clave) {
    "profesional" -> "Ver"
    "paciente" -> "Nuevo"
    "cita" -> "Agendar"
    "cobro" -> "Agenda"
    "equipo" -> "Invitar ↗"
    "whatsapp" -> "Conectar ↗"
    "pagina" -> if (conSlug) "Ver ↗" else "Pedir"
    else -> "Ir"
}

@Composable
private fun BotonRedondo(t: String, onClick: () -> Unit) {
    Box(
        Modifier.size(30.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.15f)).clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { Text(t, color = Color.White, fontSize = 13.sp) }
}

@Composable
private fun Anillo(pct: Float, texto: String, reducir: Boolean) {
    val v by animateFloatAsState(pct, tween(if (reducir) 0 else 600), label = "anillo")
    Box(Modifier.size(36.dp), contentAlignment = Alignment.Center) {
        Canvas(Modifier.size(36.dp)) {
            val grosor = 4.dp.toPx()
            val tam = Size(size.width - grosor, size.height - grosor)
            val o = Offset(grosor / 2, grosor / 2)
            drawArc(Color.White.copy(alpha = 0.2f), 0f, 360f, false, o, tam, style = Stroke(grosor))
            drawArc(Color.White, -90f, 360f * v, false, o, tam, style = Stroke(grosor, cap = StrokeCap.Round))
        }
        Text(texto, color = Color.White, fontSize = 9.sp, fontWeight = FontWeight.Bold)
    }
}

/**
 * Abrió, copió o compartió la página pública: tarea del tutorial y, para el
 * Admin, la tarea de Primeros pasos ("Mira y comparte tu página").
 */
fun paginaCompartida(scope: kotlinx.coroutines.CoroutineScope) {
    MotorTutoriales.tarea("pagina_compartida")
    if (MotorTutoriales.esAdmin) scope.launch { OnboardingRepo.avisarPaginaVista() }
}

/** Más → "🌐 Mi página": abrir, copiar o compartir la página pública de la clínica. */
@Composable
fun DialogoMiPagina(url: String, onCerrar: () -> Unit) {
    val c = Sania.colors
    val acciones = recordarAcciones()
    val scope = rememberCoroutineScope()
    AlertDialog(
        onDismissRequest = onCerrar,
        title = { Text("🌐 Mi página") },
        text = {
            Column {
                Text("Tu web con tus servicios y reservas. Compártela con tus ${LocalTerminologiaPaciente.current.pacientes}.", color = c.textoSuave, fontSize = 13.sp)
                Spacer(Modifier.height(10.dp))
                Text(
                    url, color = c.navy, fontSize = 13.sp, fontWeight = FontWeight.Bold,
                    modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(c.chipBg).padding(10.dp),
                )
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("Abrir ↗" to 0, "Copiar" to 1, "Compartir" to 2).forEach { (etq, i) ->
                        Box(
                            Modifier.weight(1f).clip(RoundedCornerShape(10.dp)).background(if (i == 2) c.navy else c.superficie)
                                .border(1.dp, if (i == 2) c.navy else c.borde, RoundedCornerShape(10.dp))
                                .clickable {
                                    when (i) {
                                        0 -> acciones.abrirUrl(url)
                                        1 -> { acciones.copiarTexto(url, "Mi página"); Toaster.exito("Enlace copiado") }
                                        else -> acciones.compartirTexto(url, "Compartir mi página")
                                    }
                                    paginaCompartida(scope)
                                }.padding(vertical = 10.dp),
                            contentAlignment = Alignment.Center,
                        ) { Text(etq, color = if (i == 2) c.sobreNavy else c.navy, fontSize = 12.sp, fontWeight = FontWeight.Bold) }
                    }
                }
            }
        },
        confirmButton = { TextButton(onCerrar) { Text("Cerrar", color = c.textoSuave) } },
    )
}
