package pe.saniape.app.ui.clinica.finanzas

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import pe.saniape.app.data.staff.soles
import pe.saniape.app.ui.theme.Sania

/** Piezas chicas que comparten las pestañas de Finanzas y caja. */

@Composable
internal fun TarjetaFin(modifier: Modifier = Modifier, borde: Color? = null, contenido: @Composable ColumnScope.() -> Unit) {
    val c = Sania.colors
    Column(
        modifier.fillMaxWidth().clip(RoundedCornerShape(Sania.shape.md.dp)).background(c.superficie)
            .border(1.dp, borde ?: c.borde, RoundedCornerShape(Sania.shape.md.dp)).padding(14.dp),
        content = contenido,
    )
}

@Composable
internal fun RotuloFin(texto: String, color: Color = Sania.colors.textoSuave, modifier: Modifier = Modifier) {
    Text(texto.uppercase(), color = color, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.5.sp, modifier = modifier)
}

/** Tarjeta de cifra: rótulo arriba, monto grande abajo. */
@Composable
internal fun CifraFin(titulo: String, monto: Double, color: Color, modifier: Modifier = Modifier) {
    val c = Sania.colors
    Column(
        modifier.clip(RoundedCornerShape(Sania.shape.md.dp)).background(c.superficie)
            .border(1.dp, c.borde, RoundedCornerShape(Sania.shape.md.dp)).padding(vertical = 12.dp, horizontal = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(titulo.uppercase(), color = c.textoSuave, fontSize = 9.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.5.sp, textAlign = TextAlign.Center)
        Spacer(Modifier.height(3.dp))
        Text(soles(monto), color = color, fontSize = 15.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
    }
}

@Composable
internal fun ChipFin(texto: String, activo: Boolean, habilitado: Boolean = true, onClick: () -> Unit) {
    val c = Sania.colors
    Box(
        Modifier.clip(RoundedCornerShape(Sania.shape.sm.dp))
            .background(if (activo) c.navy else c.superficie)
            .border(1.dp, if (activo) c.navy else c.borde, RoundedCornerShape(Sania.shape.sm.dp))
            .clickable(enabled = habilitado, onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 7.dp),
    ) {
        Text(texto, color = if (activo) c.sobreNavy else if (habilitado) c.texto else c.textoSuave, fontSize = 12.sp,
            fontWeight = if (activo) FontWeight.Bold else FontWeight.Normal)
    }
}

@Composable
internal fun BotonFin(texto: String, modifier: Modifier = Modifier, color: Color = Sania.colors.navy, habilitado: Boolean = true, onClick: () -> Unit) {
    val c = Sania.colors
    Box(
        modifier.clip(RoundedCornerShape(Sania.shape.pill.dp)).background(if (habilitado) color else c.borde)
            .clickable(enabled = habilitado, onClick = onClick).padding(horizontal = 14.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center,
    ) { Text(texto, color = if (habilitado) c.sobreNavy else c.textoSuave, fontSize = 13.sp, fontWeight = FontWeight.Bold) }
}

/** Aviso de color (descuadre, tope alcanzado, caja cerrada…). */
@Composable
internal fun AvisoFin(texto: String, fg: Color, bg: Color, modifier: Modifier = Modifier) {
    Text(texto, color = fg, fontSize = 12.sp, fontWeight = FontWeight.SemiBold,
        modifier = modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(bg).padding(10.dp))
}

/** Confirmación (borrar algo). Con el botón en un diálogo que respeta el teclado. */
@Composable
internal fun ConfirmarFin(titulo: String, detalle: String, textoAccion: String, onCancelar: () -> Unit, onConfirmar: () -> Unit) {
    val c = Sania.colors
    pe.saniape.app.ui.AlertaConTeclado(
        onDismissRequest = onCancelar,
        title = { Text(titulo, color = c.texto, fontSize = 17.sp, fontWeight = FontWeight.Bold) },
        text = { Text(detalle, color = c.textoSuave, fontSize = 13.sp) },
        confirmButton = { TextButton(onClick = onConfirmar) { Text(textoAccion, color = c.error, fontWeight = FontWeight.Bold) } },
        dismissButton = { TextButton(onClick = onCancelar) { Text("Cancelar", color = c.textoSuave) } },
        containerColor = c.superficie,
    )
}

private val MESES = listOf("ene", "feb", "mar", "abr", "may", "jun", "jul", "ago", "sep", "oct", "nov", "dic")

/** "2026-10-08" → "8 oct". */
internal fun fechaCortaFin(iso: String?): String {
    val p = iso?.take(10)?.split("-") ?: return ""
    val m = p.getOrNull(1)?.toIntOrNull() ?: return iso
    val d = p.getOrNull(2)?.toIntOrNull() ?: return iso
    return "$d ${MESES.getOrElse(m - 1) { "" }}"
}

internal fun iconoMetodoFin(m: String?): String = when (m) {
    "Efectivo" -> "💵"; "Yape" -> "📱"; "Plin" -> "📲"; "BCP" -> "🏦"; "Transferencia" -> "💳"
    "Sin especificar" -> "❔"; else -> "💰"
}
