package pe.saniape.app.ui.clinica.equipo

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import pe.saniape.app.data.CodigoQr
import pe.saniape.app.ui.theme.Sania

/**
 * QR dibujado con Canvas (sin librerías). Siempre negro sobre blanco y con margen
 * blanco de 4 módulos: en tema oscuro un QR invertido no lo leen todas las cámaras.
 */
@Composable
fun DibujoQr(texto: String, modifier: Modifier = Modifier) {
    val matriz = remember(texto) { runCatching { CodigoQr.generar(texto) }.getOrNull() } ?: return
    Canvas(modifier.aspectRatio(1f).background(Color.White)) {
        val margen = 4
        val total = matriz.tamano + margen * 2
        val lado = size.minDimension / total
        for (f in 0 until matriz.tamano) for (col in 0 until matriz.tamano) {
            if (matriz.oscuro(f, col)) {
                drawRect(
                    color = Color.Black,
                    topLeft = Offset((col + margen) * lado, (f + margen) * lado),
                    // +0.5px: sin costuras blancas entre módulos al redondear.
                    size = Size(lado + 0.5f, lado + 0.5f),
                )
            }
        }
    }
}

/** Pastilla de color (badges de rol, "Vinculado", sedes…). */
@Composable
internal fun Pastilla(texto: String, fg: Color, bg: Color, modifier: Modifier = Modifier) {
    Box(
        modifier.clip(RoundedCornerShape(Sania.shape.pill.dp)).background(bg)
            .padding(horizontal = 8.dp, vertical = 2.dp),
    ) { Text(texto, color = fg, fontSize = Sania.txt.mini, fontWeight = FontWeight.Bold, maxLines = 1) }
}

/** Inicial en círculo navy (avatar de la web). */
@Composable
internal fun Inicial(nombre: String, tam: Int = 40) {
    val c = Sania.colors
    Box(Modifier.size(tam.dp).clip(CircleShape).background(c.navy), contentAlignment = Alignment.Center) {
        Text(nombre.trim().take(1).uppercase().ifBlank { "?" }, color = c.sobreNavy, fontWeight = FontWeight.Bold, fontSize = (tam * 0.42f).sp)
    }
}

/** Opción elegible tipo radio (rol, registro de personal…). */
@Composable
internal fun OpcionElegible(texto: String, detalle: String? = null, elegida: Boolean, onClick: () -> Unit) {
    val c = Sania.colors
    val forma = RoundedCornerShape(Sania.shape.sm.dp)
    Row(
        Modifier.fillMaxWidth().padding(bottom = 6.dp).clip(forma)
            .background(if (elegida) c.chipBg else c.superficie)
            .border(if (elegida) 1.5.dp else 1.dp, if (elegida) c.navy else c.borde, forma)
            .clickable(onClick = onClick).padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.size(18.dp).clip(CircleShape).border(2.dp, if (elegida) c.navy else c.borde, CircleShape),
            contentAlignment = Alignment.Center,
        ) { if (elegida) Box(Modifier.size(9.dp).clip(CircleShape).background(c.navy)) }
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(texto, color = c.texto, fontSize = 14.sp, fontWeight = if (elegida) FontWeight.Bold else FontWeight.Normal)
            detalle?.let { Text(it, color = c.textoSuave, fontSize = Sania.txt.mini) }
        }
    }
}

/** Botón de acción a ancho completo (fila de la ficha del miembro). */
@Composable
internal fun FilaAccion(texto: String, peligro: Boolean = false, habilitada: Boolean = true, onClick: () -> Unit) {
    val c = Sania.colors
    val forma = RoundedCornerShape(Sania.shape.sm.dp)
    Row(
        Modifier.fillMaxWidth().padding(bottom = 8.dp).clip(forma)
            .background(if (peligro) c.errorBg else c.superficie)
            .border(1.dp, if (peligro) c.error.copy(alpha = 0.35f) else c.borde, forma)
            .clickable(enabled = habilitada, onClick = onClick).padding(horizontal = 14.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            texto, modifier = Modifier.weight(1f),
            color = when { !habilitada -> c.textoSuave; peligro -> c.error; else -> c.texto },
            fontSize = Sania.txt.cuerpo, fontWeight = FontWeight.SemiBold,
        )
        Text("→", color = if (peligro) c.error else c.textoSuave, fontSize = Sania.txt.cuerpo)
    }
}
