package pe.saniape.app.ui.clinica

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import pe.saniape.app.data.staff.SedeActiva
import pe.saniape.app.ui.theme.Sania

// Multisede en la app: el chip "🏢 Sede X" de las barras y el diálogo "¿En qué
// sede trabajas hoy?". Gemelos de components/layout/BotonSede.tsx y
// SelectorSede.tsx de la web. Sin multisede NO pintan nada (DALU igual que hoy).

/**
 * Chip de la sede activa para las barras navy. Tocable solo si hay más de una
 * opción (quien está limitado a una sede la ve fija).
 */
@Composable
fun ChipSede(modifier: Modifier = Modifier) {
    val e by SedeActiva.estado.collectAsState()
    if (!e.multiSede) return
    val c = Sania.colors
    val base = modifier.clip(RoundedCornerShape(Sania.shape.pill.dp))
        .background(c.sobreNavy.copy(alpha = 0.15f))
    Row(
        (if (e.puedeCambiar) base.clickable { SedeActiva.abrirCambio() } else base)
            .padding(horizontal = 10.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("🏢 ${e.etiqueta}", color = c.sobreNavy, fontSize = 12.sp, fontWeight = FontWeight.Bold,
            maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
        if (e.puedeCambiar) {
            Spacer(Modifier.width(4.dp))
            Text("▾", color = c.sobreNavy.copy(alpha = 0.8f), fontSize = 11.sp)
        }
    }
}

/**
 * "¿En qué sede trabajas hoy?". Obligatorio al entrar si todavía no eligió
 * (no se cierra sin elegir); a pedido desde el chip. Se monta UNA vez en
 * ClinicaConTabs. "Todas las sedes" solo para el Admin.
 */
@Composable
fun DialogoSede() {
    val e by SedeActiva.estado.collectAsState()
    if (!e.pedirSede) return
    val c = Sania.colors
    val cerrable = !e.obligatorio && !e.sinSedes
    Dialog(
        onDismissRequest = { if (cerrable) SedeActiva.cerrarCambio() },
        properties = DialogProperties(dismissOnBackPress = cerrable, dismissOnClickOutside = cerrable),
    ) {
        Column(
            Modifier.fillMaxWidth().heightIn(max = 560.dp)
                .clip(RoundedCornerShape(Sania.shape.lg.dp)).background(c.superficie)
                .verticalScroll(rememberScrollState())
                .padding(Sania.dim.xl),
        ) {
            Text("🏢 ¿En qué sede trabajas hoy?", color = c.texto,
                fontSize = Sania.txt.subtitulo, fontWeight = FontWeight.Bold)
            Text(
                if (e.sinSedes) "Las sedes que tienes asignadas están desactivadas. Pídele al administrador que te asigne una sede activa."
                else "La agenda, el inicio y la caja se mostrarán de esa sede. Puedes cambiarla cuando quieras desde el chip de arriba.",
                color = c.textoSuave, fontSize = 12.sp,
                modifier = Modifier.padding(top = 4.dp, bottom = Sania.dim.md),
            )
            e.sedes.forEach { s ->
                OpcionSede(
                    titulo = s.nombre + if (s.esPrincipal) " · principal" else "",
                    detalle = listOfNotNull(s.direccion, s.distrito).filter { it.isNotBlank() }.joinToString(" · ").ifBlank { null },
                    elegida = !e.obligatorio && e.sedeId == s.id,
                ) { SedeActiva.elegir(s.id) }
                Spacer(Modifier.height(8.dp))
            }
            if (e.puedeConsolidado && e.sedes.isNotEmpty()) {
                OpcionSede(
                    titulo = "Todas las sedes",
                    detalle = "Vista consolidada (solo administrador)",
                    elegida = !e.obligatorio && e.sedeId.isEmpty(),
                ) { SedeActiva.elegir("") }
            }
            if (cerrable) {
                Text("Cancelar", color = c.textoSuave, fontSize = Sania.txt.cuerpo, fontWeight = FontWeight.Bold,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().padding(top = Sania.dim.md)
                        .clickable { SedeActiva.cerrarCambio() }.padding(vertical = 8.dp))
            }
        }
    }
}

@Composable
private fun OpcionSede(titulo: String, detalle: String?, elegida: Boolean, onClick: () -> Unit) {
    val c = Sania.colors
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(Sania.shape.md.dp))
            .background(if (elegida) c.chipBg else c.fondo)
            .border(if (elegida) 2.dp else 1.dp, if (elegida) c.navy else c.borde, RoundedCornerShape(Sania.shape.md.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Column(Modifier.weight(1f)) {
            Text(titulo, color = c.texto, fontSize = Sania.txt.cuerpo, fontWeight = FontWeight.Bold)
            detalle?.let { Text(it, color = c.textoSuave, fontSize = 12.sp) }
        }
        if (elegida) Text("✓", color = c.navy, fontSize = 16.sp, fontWeight = FontWeight.Bold)
    }
}
