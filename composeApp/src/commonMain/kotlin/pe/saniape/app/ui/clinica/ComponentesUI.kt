package pe.saniape.app.ui.clinica

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import pe.saniape.app.ui.theme.Sania

/**
 * Estado vacío CONSISTENTE en toda la app (antes cada pantalla ponía un texto gris plano).
 * Emoji grande + título + subtítulo, dentro de una tarjeta. [accion] opcional pinta un botón
 * teal de llamada a la acción (ej. "Registrar el primero", "Agendar").
 */
@Composable
fun EstadoVacio(
    emoji: String,
    titulo: String,
    subtitulo: String? = null,
    textoAccion: String? = null,
    onAccion: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val c = Sania.colors
    Column(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(Sania.shape.md.dp))
            .background(c.superficie)
            .border(1.dp, c.borde, RoundedCornerShape(Sania.shape.md.dp))
            .padding(Sania.dim.xxl),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(emoji, fontSize = 40.sp)
        Spacer(Modifier.height(Sania.dim.sm))
        Text(
            titulo, color = c.texto, fontSize = Sania.txt.seccion,
            fontWeight = FontWeight.Bold, textAlign = TextAlign.Center,
        )
        if (subtitulo != null) {
            Spacer(Modifier.height(Sania.dim.xs))
            Text(
                subtitulo, color = c.textoSuave, fontSize = Sania.txt.pequeno,
                textAlign = TextAlign.Center,
            )
        }
        if (textoAccion != null && onAccion != null) {
            Spacer(Modifier.height(Sania.dim.lg))
            Box(
                Modifier
                    .clip(RoundedCornerShape(Sania.shape.pill.dp))
                    .background(c.teal)
                    .clickable { onAccion() }
                    .padding(horizontal = Sania.dim.xl, vertical = Sania.dim.md),
            ) {
                Text(textoAccion, color = c.sobreNavy, fontSize = Sania.txt.pequeno, fontWeight = FontWeight.Bold)
            }
        }
    }
}

/**
 * Chevron (▾) que rota suave al expandir/colapsar. Uniforma el gesto en toda la app
 * (antes cada tarjeta alternaba ▾/▴ sin animación).
 */
@Composable
fun ChevronExpandible(expandido: Boolean, modifier: Modifier = Modifier, tam: Int = 12) {
    val giro by animateFloatAsState(if (expandido) 180f else 0f)
    Text(
        "▾",
        color = Sania.colors.textoSuave,
        fontSize = tam.sp,
        modifier = modifier.rotate(giro),
    )
}

/**
 * Barra de progreso CONSISTENTE (antes había 3 alturas distintas: 5/6dp). Usa el token
 * `Sania.dim.barraProgreso`. Color parametrizable (verde=pago/completado, navy=sesiones).
 */
@Composable
fun BarraProgreso(
    fraccion: Float,
    modifier: Modifier = Modifier,
    color: androidx.compose.ui.graphics.Color = Sania.colors.ok,
) {
    val c = Sania.colors
    Box(
        modifier
            .fillMaxWidth()
            .height(Sania.dim.barraProgreso)
            .clip(RoundedCornerShape(Sania.shape.pill.dp))
            .background(c.chipBg),
    ) {
        Box(
            Modifier
                .fillMaxWidth(fraccion.coerceIn(0f, 1f))
                .height(Sania.dim.barraProgreso)
                .clip(RoundedCornerShape(Sania.shape.pill.dp))
                .background(color),
        )
    }
}
