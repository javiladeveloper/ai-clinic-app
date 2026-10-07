package pe.saniape.app.ui.tutoriales

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import pe.saniape.app.data.Supabase
import pe.saniape.app.tutoriales.ExcluidoApp
import pe.saniape.app.tutoriales.MotorTutoriales
import pe.saniape.app.tutoriales.Movimiento
import pe.saniape.app.tutoriales.ReglasTutorial
import pe.saniape.app.tutoriales.TutorialApp
import pe.saniape.app.ui.ManejarAtras
import pe.saniape.app.ui.recordarAcciones
import pe.saniape.app.ui.theme.Sania

private const val WHATSAPP_SOPORTE = "https://wa.me/51916884168"

/**
 * Centro de ayuda (gemelo de PanelAyuda.tsx): buscador sin tildes, "Hazlo
 * conmigo en esta pantalla", la guía de la pantalla, todos los tutoriales con
 * ✓ de los hechos (progreso del servidor) y su duración. Hoja desde abajo.
 */
@Composable
fun CentroAyuda() {
    val abierta = MotorTutoriales.ayudaAbierta
    val reducir = Movimiento.reducido
    // Se compone SOLO abierto: así su "Atrás" se registra después que el de la
    // pantalla de abajo (Profesionales, Horario, Especialidades…) y gana él.
    if (abierta) ManejarAtras(activo = true) { MotorTutoriales.cerrarAyuda() }
    AnimatedVisibility(abierta, enter = fadeIn(tween(if (reducir) 0 else 200)), exit = fadeOut(tween(if (reducir) 0 else 200))) {
        Box(
            Modifier.fillMaxSize().background(Color(0x660F1437))
                .clickable(remember { MutableInteractionSource() }, null) { MotorTutoriales.cerrarAyuda() },
        )
    }
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.BottomCenter) {
        AnimatedVisibility(
            abierta,
            enter = if (reducir) fadeIn(tween(150)) else slideInVertically(tween(280)) { it / 3 } + fadeIn(tween(200)),
            exit = if (reducir) fadeOut(tween(150)) else slideOutVertically(tween(200)) { it / 3 } + fadeOut(tween(150)),
        ) {
            Panel()
        }
    }
}

@Composable
private fun Panel() {
    val c = Sania.colors
    val cat = MotorTutoriales.catalogo
    val progreso = MotorTutoriales.progreso
    val guia = cat?.guia(MotorTutoriales.pantallaAyuda)
    var q by remember { mutableStateOf("") }
    var verTodos by remember { mutableStateOf(false) }
    val acciones = recordarAcciones()
    Column(
        Modifier.fillMaxWidth().fillMaxHeight(0.88f)
            .clip(RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp))
            .background(c.fondo)
            .clickable(remember { MutableInteractionSource() }, null) {} // no cerrar al tocar dentro
            .imePadding(),
    ) {
        // Cabecera
        Column(
            Modifier.fillMaxWidth()
                .background(Brush.linearGradient(listOf(Color(0xFF1A2550), Color(0xFF2C3E7A), Color(0xFF4A5FA8))))
                .padding(horizontal = 20.dp, vertical = 18.dp),
        ) {
            Row(verticalAlignment = Alignment.Top) {
                Column(Modifier.weight(1f)) {
                    Text("CENTRO DE AYUDA", color = Color.White.copy(alpha = 0.75f), fontSize = 10.sp, fontWeight = FontWeight.Bold)
                    Text(guia?.titulo ?: "¿Qué quieres hacer?", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                    if (cat != null && cat.tutoriales.isNotEmpty()) {
                        val hechos = cat.tutoriales.count { ReglasTutorial.hecho(it.id, progreso) }
                        Text("$hechos de ${cat.tutoriales.size} tutoriales hechos", color = Color.White.copy(alpha = 0.8f), fontSize = 12.sp)
                    }
                }
                Box(
                    Modifier.size(32.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.12f))
                        .clickable { MotorTutoriales.cerrarAyuda() },
                    contentAlignment = Alignment.Center,
                ) { Text("×", color = Color.White, fontSize = 20.sp) }
            }
            Spacer(Modifier.height(12.dp))
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(Color.White.copy(alpha = 0.15f))
                    .border(1.dp, Color.White.copy(alpha = 0.15f), RoundedCornerShape(12.dp))
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("🔍", fontSize = 14.sp)
                Spacer(Modifier.width(8.dp))
                Box(Modifier.weight(1f)) {
                    if (q.isEmpty()) Text("Busca: cobrar, horario, receta…", color = Color.White.copy(alpha = 0.6f), fontSize = 14.sp)
                    BasicTextField(
                        q, { q = it }, singleLine = true,
                        textStyle = TextStyle(color = Color.White, fontSize = 14.sp),
                        cursorBrush = SolidColor(Color.White), modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }

        if (cat == null) {
            Box(Modifier.fillMaxWidth().weight(1f).padding(24.dp), contentAlignment = Alignment.Center) {
                val err = MotorTutoriales.errorCatalogo
                if (err != null && !MotorTutoriales.cargandoCatalogo) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(err, color = c.error, fontSize = 13.sp)
                        Spacer(Modifier.height(10.dp))
                        Text(
                            "Reintentar", color = c.navy, fontWeight = FontWeight.Bold, fontSize = 13.sp,
                            modifier = Modifier.clip(RoundedCornerShape(8.dp)).clickable { MotorTutoriales.cargarCatalogo() }.padding(8.dp),
                        )
                    }
                } else CircularProgressIndicator(color = c.navy)
            }
        } else {
            val todos = cat.tutoriales
            val sugeridos = ReglasTutorial.sugeridos(guia, cat, progreso)
            val mostrarTodos = verTodos || sugeridos.isEmpty()
            LazyColumn(
                Modifier.fillMaxWidth().weight(1f),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                if (q.isNotBlank()) {
                    val res = ReglasTutorial.buscar(q, todos)
                    item { Seccion(if (res.isEmpty()) "Sin resultados" else "Resultados (${res.size})") }
                    if (res.isEmpty()) item { Text("Prueba con otra palabra o escríbenos por WhatsApp.", color = c.textoSuave, fontSize = 13.sp) }
                    res.forEach { t -> item(key = "r-${t.id}") { Fila(t, ReglasTutorial.hecho(t.id, progreso)) } }
                } else {
                    if (sugeridos.isNotEmpty()) {
                        item { Seccion("Hazlo conmigo en esta pantalla") }
                        sugeridos.forEachIndexed { i, t ->
                            item(key = "s-${t.id}") { Fila(t, ReglasTutorial.hecho(t.id, progreso), destacada = i == 0 && !ReglasTutorial.hecho(t.id, progreso)) }
                        }
                    }
                    if (guia != null && guia.guia.isNotEmpty()) {
                        item { Spacer(Modifier.height(6.dp)); Seccion("Cómo funciona: ${guia.titulo}") }
                        guia.guia.forEach { g ->
                            item {
                                Row(
                                    Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(c.superficie)
                                        .border(1.dp, c.borde, RoundedCornerShape(16.dp)).padding(12.dp),
                                ) {
                                    Box(Modifier.size(36.dp).clip(RoundedCornerShape(10.dp)).background(c.chipBg), contentAlignment = Alignment.Center) {
                                        Text(g.icono, fontSize = 17.sp)
                                    }
                                    Spacer(Modifier.width(12.dp))
                                    Column {
                                        Text(g.titulo, color = c.texto, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                                        Text(g.texto, color = c.textoSuave, fontSize = 12.sp, lineHeight = 17.sp)
                                    }
                                }
                            }
                        }
                    }
                    item { Spacer(Modifier.height(6.dp)); Seccion("Todos los tutoriales") }
                    if (!mostrarTodos) {
                        item {
                            Box(
                                Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).border(1.dp, c.borde, RoundedCornerShape(12.dp))
                                    .clickable { verTodos = true }.padding(12.dp),
                                contentAlignment = Alignment.Center,
                            ) { Text("Ver todos (${todos.size + cat.excluidos.size})", color = c.navy, fontSize = 13.sp, fontWeight = FontWeight.Bold) }
                        }
                    } else {
                        ReglasTutorial.CATEGORIAS.forEach { (id, titulo) ->
                            val lista = todos.filter { it.categoria == id }
                            if (lista.isNotEmpty()) {
                                item { Text(titulo.uppercase(), color = c.textoSuave, fontSize = 10.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 4.dp)) }
                                lista.forEach { t -> item(key = "t-${t.id}") { Fila(t, ReglasTutorial.hecho(t.id, progreso)) } }
                            }
                        }
                        // Los que en la app abren la web (hoy: invitar al equipo).
                        cat.excluidos.filter { it.rutaWeb != null }.forEach { x ->
                            item(key = "x-${x.id}") { FilaWeb(x) { acciones.abrirUrl("${Supabase.SITE_URL}${x.rutaWeb}") } }
                        }
                    }
                }
            }
        }

        // Pie
        Column(
            Modifier.fillMaxWidth().background(c.superficie).padding(horizontal = 16.dp, vertical = 10.dp)
                .windowInsetsPadding(WindowInsets.navigationBars),
        ) {
            if (MotorTutoriales.primerosPasosActivos) {
                val apagadas = progreso[ReglasTutorial.CLAVE_PILDORAS_OFF] != null
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "Sugerirme tutoriales la primera vez que entro a una pantalla",
                        color = c.textoSuave, fontSize = 12.sp, modifier = Modifier.weight(1f),
                    )
                    Switch(
                        checked = !apagadas, enabled = !apagadas,
                        onCheckedChange = { if (!it) MotorTutoriales.marcarVisto(ReglasTutorial.CLAVE_PILDORAS_OFF) },
                        colors = SwitchDefaults.colors(checkedTrackColor = c.navy),
                    )
                }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                Text("¿Más dudas? ", color = c.textoSuave, fontSize = 12.sp)
                Text(
                    "Escríbenos por WhatsApp", color = c.ok, fontSize = 12.sp, fontWeight = FontWeight.Bold,
                    modifier = Modifier.clickable { acciones.abrirUrl(WHATSAPP_SOPORTE) },
                )
            }
        }
    }
}

@Composable
private fun Seccion(t: String) {
    Text(t.uppercase(), color = Sania.colors.textoSuave, fontSize = 11.sp, fontWeight = FontWeight.Bold)
}

@Composable
private fun Fila(t: TutorialApp, ok: Boolean, destacada: Boolean = false) {
    val c = Sania.colors
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(c.superficie)
            .border(if (destacada) 2.dp else 1.dp, if (destacada) c.navy else c.borde, RoundedCornerShape(16.dp))
            .clickable { MotorTutoriales.lanzarDesdeAyuda(t.id) }.padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(contentAlignment = Alignment.BottomEnd) {
            Box(
                Modifier.size(40.dp).clip(RoundedCornerShape(12.dp)).background(if (ok) c.okBg else c.chipBg),
                contentAlignment = Alignment.Center,
            ) { Text(t.icono, fontSize = 19.sp) }
            if (ok) Box(Modifier.size(18.dp).clip(CircleShape).background(c.ok), contentAlignment = Alignment.Center) {
                Text("✓", color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold)
            }
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(t.titulo, color = c.texto, fontSize = 13.sp, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
            if (t.descripcion.isNotBlank()) Text(t.descripcion, color = c.textoSuave, fontSize = 12.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
        Spacer(Modifier.width(8.dp))
        Column(horizontalAlignment = Alignment.End) {
            Box(Modifier.clip(RoundedCornerShape(50)).background(c.chipBg).padding(horizontal = 8.dp, vertical = 2.dp)) {
                Text("▶ ${t.duracion}", color = c.navy, fontSize = 10.sp, fontWeight = FontWeight.Bold)
            }
            if (ok) Text("Hecho", color = c.ok, fontSize = 10.sp, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun FilaWeb(x: ExcluidoApp, onAbrir: () -> Unit) {
    val c = Sania.colors
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(c.superficie)
            .border(1.dp, c.borde, RoundedCornerShape(16.dp)).clickable(onClick = onAbrir).padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(x.titulo, color = c.texto, fontSize = 13.sp, fontWeight = FontWeight.Bold)
            if (x.razon.isNotBlank()) Text(x.razon, color = c.textoSuave, fontSize = 12.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
        Spacer(Modifier.width(8.dp))
        Text("Abrir en la web ↗", color = c.navy, fontSize = 11.sp, fontWeight = FontWeight.Bold)
    }
}

/**
 * Botón "?" discreto para la barra superior de una pantalla con guía. Se
 * oculta si la web todavía no tiene la ayuda (404).
 */
@Composable
fun BotonAyuda(pantalla: String, sobreOscuro: Boolean = true, modifier: Modifier = Modifier) {
    if (MotorTutoriales.noDisponible) return
    val c = Sania.colors
    Box(
        modifier.size(30.dp).clip(CircleShape)
            .background(if (sobreOscuro) Color.White.copy(alpha = 0.14f) else c.chipBg)
            .clickable { MotorTutoriales.abrirAyuda(pantalla) },
        contentAlignment = Alignment.Center,
    ) { Text("?", color = if (sobreOscuro) Color.White else c.navy, fontSize = 15.sp, fontWeight = FontWeight.Bold) }
}
