package pe.saniape.app.ui.clinica.especialidades

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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import pe.saniape.app.data.staff.ContextoStaff
import pe.saniape.app.data.staff.EspecialidadApp
import pe.saniape.app.data.staff.EspecialidadesRepo
import pe.saniape.app.ui.theme.Sania

/** "#2c3e7a" → Color; cualquier otra cosa → null (se usa el navy de la marca). */
private fun colorDeHex(hex: String?): Color? {
    val h = hex?.trim()?.removePrefix("#") ?: return null
    if (h.length != 6) return null
    val v = h.toLongOrNull(16) ?: return null
    return Color(0xFF000000 or v)
}

/**
 * 🩺 Especialidades (Más → Administración): lista de la clínica, alta de una nueva
 * y el asistente de carga inicial (servicios, tipos de imagen, procedimientos
 * típicos). Editar el resto de la especialidad (flujo, etiquetas…) sigue en la web.
 * Solo se abre con permiso "equipo" (lo decide el padre, igual que los endpoints).
 */
@Composable
fun PantallaEspecialidades(ctx: ContextoStaff, onSalir: () -> Unit) {
    val c = Sania.colors
    var lista by remember { mutableStateOf<List<EspecialidadApp>?>(null) }
    var fallo by remember { mutableStateOf(false) }
    // Sube para volver a pedir la lista (reintentar, tras crear o tras cargar servicios).
    var recarga by remember { mutableIntStateOf(0) }
    var creando by remember { mutableStateOf(false) }
    var asistente by remember { mutableStateOf<EspecialidadApp?>(null) }

    LaunchedEffect(ctx.clinicaId, recarga) {
        // Recarga silenciosa si ya había lista (sin parpadeo del spinner).
        try {
            lista = EspecialidadesRepo.listar()
            fallo = false
        } catch (e: kotlin.coroutines.cancellation.CancellationException) {
            throw e
        } catch (e: Exception) {
            if (lista == null) fallo = true
        }
    }

    if (creando) {
        DialogoNuevaEspecialidad(
            onCancelar = { creando = false },
            onCreada = { nueva ->
                // TODO: refrescar el contexto del staff (usaSesiones) cuando exista un callback; hoy no hay.
                creando = false
                lista = (lista.orEmpty() + nueva).sortedBy { it.nombre.lowercase() }
                recarga++
                // Recién creada: de una vez el asistente para cargar sus servicios.
                asistente = nueva
            },
        )
    }
    asistente?.let { esp ->
        AsistenteEspecialidad(
            especialidad = esp,
            onCerrar = { asistente = null },
            onCargado = { recarga++ },
        )
    }

    Surface(color = c.fondo, modifier = Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            Row(
                Modifier.fillMaxWidth().background(c.navyDark)
                    .padding(horizontal = Sania.dim.xl, vertical = Sania.dim.lg),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        "← Más", color = c.sobreNavy, fontSize = Sania.txt.pequeno,
                        modifier = Modifier.clip(RoundedCornerShape(Sania.shape.sm.dp))
                            .clickable { onSalir() }.padding(vertical = 2.dp),
                    )
                    Spacer(Modifier.height(2.dp))
                    Text("Especialidades", color = c.sobreNavy, fontSize = Sania.txt.subtitulo, fontWeight = FontWeight.Bold)
                }
                pe.saniape.app.ui.tutoriales.BotonAyuda("Especialidades")
                Spacer(Modifier.width(8.dp))
                Box(
                    Modifier.clip(RoundedCornerShape(Sania.shape.pill.dp)).background(c.navy)
                        .clickable { creando = true }
                        .padding(horizontal = 14.dp, vertical = 8.dp),
                ) { Text("+ Nueva", color = c.sobreNavy, fontSize = 13.sp, fontWeight = FontWeight.Bold) }
            }

            val actual = lista
            when {
                actual == null && fallo -> Box(Modifier.fillMaxSize().padding(24.dp), Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            "No se pudieron cargar las especialidades. Revisa tu conexión.",
                            color = c.textoSuave, fontSize = 13.sp, textAlign = TextAlign.Center,
                        )
                        Spacer(Modifier.height(Sania.dim.md))
                        Box(
                            Modifier.clip(RoundedCornerShape(Sania.shape.md.dp)).background(c.navy)
                                .clickable { fallo = false; recarga++ }
                                .padding(horizontal = 20.dp, vertical = 10.dp),
                        ) { Text("Reintentar", color = c.sobreNavy, fontWeight = FontWeight.Bold) }
                    }
                }
                actual == null -> Box(Modifier.fillMaxSize(), Alignment.Center) {
                    CircularProgressIndicator(color = c.navy, strokeWidth = 2.dp)
                }
                actual.isEmpty() -> Box(Modifier.fillMaxSize().padding(24.dp), Alignment.Center) {
                    Text(
                        "Aún no hay especialidades. Crea la primera con “+ Nueva”.",
                        color = c.textoSuave, fontSize = 13.sp, textAlign = TextAlign.Center,
                    )
                }
                else -> LazyColumn(
                    Modifier.fillMaxSize().padding(horizontal = Sania.dim.lg),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    item { Spacer(Modifier.height(4.dp)) }
                    items(actual, key = { it.id }) { esp ->
                        FilaEspecialidad(esp, onSugerencias = { asistente = esp })
                    }
                    item {
                        Text(
                            "Para editar el flujo, las etiquetas o desactivar una especialidad, entra a la web.",
                            color = c.textoSuave, fontSize = Sania.txt.mini, textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth().padding(vertical = Sania.dim.lg),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun FilaEspecialidad(esp: EspecialidadApp, onSugerencias: () -> Unit) {
    val c = Sania.colors
    val activa = esp.estado.equals("Activa", ignoreCase = true)
    val forma = RoundedCornerShape(Sania.shape.md.dp)
    Row(
        Modifier.fillMaxWidth().clip(forma).background(c.superficie).border(1.dp, c.borde, forma)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.size(38.dp).alpha(if (activa) 1f else 0.5f).clip(CircleShape)
                .background((colorDeHex(esp.color) ?: c.navy).copy(alpha = 0.15f)),
            contentAlignment = Alignment.Center,
        ) { Text(esp.icono?.takeIf { it.isNotBlank() } ?: "🏥", fontSize = 18.sp) }
        Spacer(Modifier.width(Sania.dim.md))
        Column(Modifier.weight(1f)) {
            Text(
                esp.nombre, color = if (activa) c.texto else c.textoSuave,
                fontSize = Sania.txt.cuerpo, fontWeight = FontWeight.Bold,
            )
            Row(Modifier.padding(top = 4.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                esp.rubro?.let { r -> NOMBRE_RUBRO[r] ?: r }?.let { Pastilla(it, c.purple, c.purpleBg) }
                if (!activa) Pastilla(esp.estado.ifBlank { "Inactiva" }, c.textoSuave, c.fondo)
            }
        }
        Spacer(Modifier.width(Sania.dim.sm))
        Box(
            Modifier.clip(RoundedCornerShape(Sania.shape.pill.dp)).background(c.chipBg)
                .border(1.dp, c.borde, RoundedCornerShape(Sania.shape.pill.dp))
                .clickable { onSugerencias() }
                .padding(horizontal = 10.dp, vertical = 6.dp),
        ) { Text("✨ Sugerencias", color = c.navy, fontSize = 12.sp, fontWeight = FontWeight.Bold) }
    }
}

@Composable
private fun Pastilla(texto: String, fg: Color, bg: Color) {
    Box(
        Modifier.clip(RoundedCornerShape(Sania.shape.pill.dp)).background(bg)
            .padding(horizontal = 8.dp, vertical = 2.dp),
    ) { Text(texto, color = fg, fontSize = Sania.txt.mini, fontWeight = FontWeight.Bold) }
}
