package pe.saniape.app.ui.clinica.pacientes

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextFieldColors
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import pe.saniape.app.ui.theme.Sania

/**
 * Esquema ESTÁNDAR de los popups de la ficha (igual que Nuevo/Editar tratamiento):
 * Dialog full-width, header navy con título+subtítulo, cuerpo scrolleable, footer fijo
 * con Cancelar + botón de acción a ancho completo. Uniformiza TODOS los modales.
 */
@Composable
fun DialogoForm(
    titulo: String,
    subtitulo: String?,
    textoAccion: String,
    accionHabilitada: Boolean = true,
    onCancelar: () -> Unit,
    onAccion: () -> Unit,
    contenido: @Composable ColumnScope.() -> Unit,
) {
    val c = Sania.colors
    Dialog(onDismissRequest = onCancelar, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Column(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp).heightIn(max = 720.dp)
                .clip(RoundedCornerShape(Sania.shape.lg.dp)).background(c.fondo),
        ) {
            // Header navy
            Column(Modifier.fillMaxWidth().background(c.navyDark).padding(horizontal = 18.dp, vertical = 16.dp)) {
                Text(titulo, color = c.sobreNavy, fontSize = 19.sp, fontWeight = FontWeight.Bold)
                subtitulo?.let {
                    Text(it, color = c.sobreNavy.copy(alpha = 0.7f), fontSize = 12.sp, modifier = Modifier.padding(top = 2.dp))
                }
            }
            // Cuerpo
            Column(
                Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                content = contenido,
            )
            // Footer
            Box(Modifier.fillMaxWidth().height(1.dp).background(c.borde))
            Row(
                Modifier.fillMaxWidth().background(c.superficie).padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                TextButton(onClick = onCancelar) { Text("Cancelar", color = c.textoSuave, fontWeight = FontWeight.Bold) }
                Box(
                    Modifier.weight(1f).clip(RoundedCornerShape(Sania.shape.md.dp))
                        .background(if (accionHabilitada) c.navy else c.borde)
                        .clickable(enabled = accionHabilitada) { onAccion() }.padding(vertical = 13.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(textoAccion, color = if (accionHabilitada) c.sobreNavy else c.textoSuave,
                        fontWeight = FontWeight.Bold, fontSize = 15.sp)
                }
            }
        }
    }
}

/** Tarjeta de sección con título e ícono — agrupa campos relacionados (esquema estándar). */
@Composable
fun TarjetaForm(titulo: String, icono: String, contenido: @Composable ColumnScope.() -> Unit) {
    val c = Sania.colors
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(Sania.shape.md.dp))
            .background(c.superficie).border(1.dp, c.borde, RoundedCornerShape(Sania.shape.md.dp)).padding(14.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(bottom = 12.dp)) {
            Text(icono, fontSize = 15.sp)
            Spacer(Modifier.width(7.dp))
            Text(titulo, color = c.texto, fontSize = 14.sp, fontWeight = FontWeight.Bold)
        }
        contenido()
    }
}

/**
 * Colores estándar de los campos de texto del flujo de pacientes. Sin esto,
 * OutlinedTextField usa el colorScheme por defecto de Material3 (no el tema
 * Sania): el texto tecleado quedaba lavado/casi invisible — se veía "borroso",
 * sobre todo en tema oscuro (reporte DALU 2026-07-23, campo DNI del alta).
 */
@Composable
fun coloresCampoForm(): TextFieldColors {
    val c = Sania.colors
    return OutlinedTextFieldDefaults.colors(
        focusedTextColor = c.texto, unfocusedTextColor = c.texto,
        cursorColor = c.navy,
        focusedBorderColor = c.navy, unfocusedBorderColor = c.borde,
        focusedContainerColor = c.superficie, unfocusedContainerColor = c.superficie,
    )
}

/** Etiqueta de campo (MAYÚSCULAS, espaciado) — esquema estándar. */
@Composable
fun EtqForm(t: String) {
    Text(t.uppercase(), color = Sania.colors.textoSuave, fontSize = 10.sp, fontWeight = FontWeight.Bold,
        letterSpacing = 0.5.sp, modifier = Modifier.padding(bottom = 5.dp))
}
