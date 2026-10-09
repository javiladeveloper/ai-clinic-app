package pe.saniape.app.ui.clinica.calendario

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import pe.saniape.app.data.staff.CalendarioFeedRepo
import pe.saniape.app.data.staff.EstadoCalendario
import pe.saniape.app.data.staff.FeedCalendario
import pe.saniape.app.ui.AlertaConTeclado
import pe.saniape.app.ui.CargandoLista
import pe.saniape.app.ui.Toaster
import pe.saniape.app.ui.recordarAcciones
import pe.saniape.app.ui.theme.Sania

private enum class Confirmar { ROTAR, REVOCAR }

/**
 * 📅 Mis citas en mi calendario (Más). Gemelo de la tarjeta de Mi cuenta en la web:
 * enlace ICS de SOLO LECTURA (Sania → Google/Apple/Outlook). Nunca lleva diagnóstico,
 * motivo ni teléfono; por defecto solo iniciales. Crear, renovar, desactivar y la
 * privacidad los decide el servidor (/api/staff/calendario/feed).
 */
@Composable
fun PantallaMiCalendario(onSalir: () -> Unit) {
    val c = Sania.colors
    val acciones = recordarAcciones()
    val scope = rememberCoroutineScope()
    var estado by remember { mutableStateOf<EstadoCalendario?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var recarga by remember { mutableIntStateOf(0) }
    var trabajando by remember { mutableStateOf(false) }
    var confirmar by remember { mutableStateOf<Confirmar?>(null) }

    LaunchedEffect(recarga) {
        val (e, msg) = CalendarioFeedRepo.cargar()
        if (e != null) { estado = e; error = null } else if (estado == null) error = msg
    }

    fun correr(accion: String, privacidad: String? = null, ok: String) {
        if (trabajando) return
        trabajando = true
        scope.launch {
            val (feed, msg) = CalendarioFeedRepo.accion(accion, privacidad)
            trabajando = false
            if (msg != null) { Toaster.error(msg); return@launch }
            estado = EstadoCalendario(true, feed)
            Toaster.exito(ok)
        }
    }

    Surface(color = c.fondo, modifier = Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            Row(
                Modifier.fillMaxWidth().background(c.navyDark).padding(horizontal = Sania.dim.xl, vertical = Sania.dim.lg),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        "← Más", color = c.sobreNavy, fontSize = Sania.txt.pequeno,
                        modifier = Modifier.clip(RoundedCornerShape(Sania.shape.sm.dp)).clickable { onSalir() }.padding(vertical = 2.dp),
                    )
                    Spacer(Modifier.height(2.dp))
                    Text("Mis citas en mi calendario", color = c.sobreNavy, fontSize = Sania.txt.subtitulo, fontWeight = FontWeight.Bold)
                    Text("Google Calendar, Apple u Outlook", color = c.sobreNavy.copy(alpha = 0.75f), fontSize = Sania.txt.mini)
                }
            }

            val e = estado
            when {
                e == null && error != null -> Box(Modifier.fillMaxSize().padding(24.dp), Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(error.orEmpty(), color = c.textoSuave, fontSize = 13.sp, textAlign = TextAlign.Center)
                        Spacer(Modifier.height(Sania.dim.md))
                        Box(
                            Modifier.clip(RoundedCornerShape(Sania.shape.md.dp)).background(c.navy)
                                .clickable { error = null; recarga++ }.padding(horizontal = 20.dp, vertical = 10.dp),
                        ) { Text("Reintentar", color = c.sobreNavy, fontWeight = FontWeight.Bold) }
                    }
                }
                e == null -> CargandoLista(filas = 3, conAvatar = false)
                !e.tieneAgenda -> Box(Modifier.fillMaxSize().padding(24.dp), Alignment.Center) {
                    Text(
                        "Tu usuario no tiene una agenda de profesional vinculada, así que no hay citas que mostrar.",
                        color = c.textoSuave, fontSize = 13.sp, textAlign = TextAlign.Center,
                    )
                }
                else -> Column(
                    Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(Sania.dim.lg),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Text(
                        "Tus citas aparecen en tu calendario y se actualizan solas. Es de una sola vía: lo que cambies en tu calendario no cambia la agenda de Sania.",
                        color = c.textoSuave, fontSize = 13.sp,
                    )
                    val feed = e.feed
                    if (feed == null) {
                        Boton("Crear mi enlace de calendario", activo = !trabajando) { correr("crear", ok = "Enlace creado") }
                    } else {
                        ContenidoFeed(
                            feed = feed, trabajando = trabajando,
                            onGoogle = { acciones.abrirUrl(feed.google) },
                            onWebcal = { acciones.abrirUrl(feed.webcal) },
                            onCompartir = { acciones.compartirTexto(feed.url, "Mi calendario de Sania") },
                            onCopiar = { acciones.copiarTexto(feed.url, "Enlace de calendario"); Toaster.exito("Enlace copiado") },
                            onPrivacidad = { correr("privacidad", it, "Guardado") },
                            onRotar = { confirmar = Confirmar.ROTAR },
                            onRevocar = { confirmar = Confirmar.REVOCAR },
                        )
                    }
                }
            }
        }
    }

    confirmar?.let { cual ->
        val rotar = cual == Confirmar.ROTAR
        AlertaConTeclado(
            onDismissRequest = { confirmar = null },
            title = { Text(if (rotar) "Renovar enlace" else "Desactivar enlace", fontWeight = FontWeight.Bold) },
            text = {
                Text(
                    if (rotar) "Se creará un enlace nuevo y el actual dejará de funcionar. Tendrás que suscribirte de nuevo."
                    else "El enlace dejará de funcionar y las citas desaparecerán de tu calendario.",
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    confirmar = null
                    if (rotar) correr("rotar", ok = "Enlace renovado: vuelve a suscribirte con el nuevo")
                    else correr("revocar", ok = "Enlace desactivado")
                }) { Text(if (rotar) "Renovar" else "Desactivar", color = if (rotar) c.navy else c.error, fontWeight = FontWeight.Bold) }
            },
            dismissButton = { TextButton(onClick = { confirmar = null }) { Text("Cancelar", color = c.textoSuave) } },
            containerColor = c.superficie,
        )
    }
}

@Composable
private fun ContenidoFeed(
    feed: FeedCalendario,
    trabajando: Boolean,
    onGoogle: () -> Unit,
    onWebcal: () -> Unit,
    onCompartir: () -> Unit,
    onCopiar: () -> Unit,
    onPrivacidad: (String) -> Unit,
    onRotar: () -> Unit,
    onRevocar: () -> Unit,
) {
    val c = Sania.colors
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(Sania.shape.sm.dp)).background(c.superficie)
            .border(1.dp, c.borde, RoundedCornerShape(Sania.shape.sm.dp)).padding(Sania.dim.lg),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text("Tu enlace", color = c.textoSuave, fontSize = Sania.txt.mini, fontWeight = FontWeight.Bold)
        Text(feed.url, color = c.texto, fontSize = 12.sp)
        Boton("Abrir en Google Calendar", activo = !trabajando, onClick = onGoogle)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            BotonSuave("Compartir enlace", Modifier.weight(1f), onCompartir)
            BotonSuave("Copiar", Modifier.weight(1f), onCopiar)
        }
        BotonSuave("Apple / Outlook (webcal)", Modifier.fillMaxWidth(), onWebcal)
    }

    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(Sania.shape.sm.dp)).background(c.superficie)
            .border(1.dp, c.borde, RoundedCornerShape(Sania.shape.sm.dp)).padding(Sania.dim.lg),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text("QUÉ SE VE DEL PACIENTE", color = c.textoSuave, fontSize = Sania.txt.mini, fontWeight = FontWeight.Bold)
        Opcion("Solo iniciales (J.P. · Sesión) — recomendado", feed.privacidad == "iniciales", !trabajando) { onPrivacidad("iniciales") }
        Opcion("Nombre completo", feed.privacidad == "completo", !trabajando) { onPrivacidad("completo") }
        Text("Nunca se envía diagnóstico, motivo ni teléfono.", color = c.textoSuave, fontSize = 11.5.sp)
    }

    Text(
        "Google refresca los calendarios suscritos cada 8 a 24 horas, así que un cambio de cita puede tardar en verse.",
        color = c.textoSuave, fontSize = 12.sp,
    )

    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        TextButton(onClick = onRotar, enabled = !trabajando) { Text("Renovar enlace", color = c.textoSuave) }
        TextButton(onClick = onRevocar, enabled = !trabajando) { Text("Desactivar", color = c.error, fontWeight = FontWeight.Bold) }
    }
}

@Composable
private fun Opcion(texto: String, elegida: Boolean, activo: Boolean, onClick: () -> Unit) {
    val c = Sania.colors
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp))
            .border(1.5.dp, if (elegida) c.navy else c.borde, RoundedCornerShape(10.dp))
            .background(if (elegida) c.navy.copy(alpha = 0.08f) else c.superficie)
            .clickable(enabled = activo && !elegida, onClick = onClick).padding(horizontal = 12.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(if (elegida) "●  $texto" else "○  $texto", color = c.texto, fontSize = 13.sp, fontWeight = if (elegida) FontWeight.Bold else FontWeight.Normal)
    }
}

@Composable
private fun Boton(texto: String, activo: Boolean, onClick: () -> Unit) {
    val c = Sania.colors
    Box(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(Sania.shape.md.dp)).background(if (activo) c.navy else c.borde)
            .clickable(enabled = activo, onClick = onClick).padding(vertical = 12.dp),
        contentAlignment = Alignment.Center,
    ) { Text(texto, color = c.sobreNavy, fontWeight = FontWeight.Bold, fontSize = 14.sp) }
}

@Composable
private fun BotonSuave(texto: String, modifier: Modifier, onClick: () -> Unit) {
    val c = Sania.colors
    Box(
        modifier.clip(RoundedCornerShape(Sania.shape.md.dp)).border(1.dp, c.borde, RoundedCornerShape(Sania.shape.md.dp))
            .clickable(onClick = onClick).padding(vertical = 10.dp),
        contentAlignment = Alignment.Center,
    ) { Text(texto, color = c.texto, fontWeight = FontWeight.SemiBold, fontSize = 13.sp) }
}
