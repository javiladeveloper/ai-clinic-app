package pe.saniape.app.ui.clinica.agenda.componentes

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import pe.saniape.app.data.staff.EstadoPagoCita
import pe.saniape.app.ui.theme.Sania

/**
 * ¿El paciente ya pagó? Badge para quien atiende (agenda, consulta guiada,
 * completar sesión). El texto lo arma el servidor (`etiqueta`): solo el estado y
 * la deuda, nunca lo ya pagado. Verde = pagado, ámbar = debe/parcial, gris =
 * gratis. sin_dato o cita cancelada = no se pinta nada. Nunca bloquea atender.
 */
@Composable
fun BadgeEstadoPago(estado: EstadoPagoCita?, estadoCita: String? = null, modifier: Modifier = Modifier) {
    if (estado == null || !estado.mostrable || estadoCita == "Cancelada") return
    val (fg, bg) = coloresEstadoPago(estado.estado) ?: return
    Box(modifier.clip(RoundedCornerShape(Sania.shape.pill.dp)).background(bg)
        .padding(horizontal = 8.dp, vertical = 3.dp)) {
        Text(estado.etiqueta, color = fg, fontSize = 10.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun coloresEstadoPago(estado: String): Pair<Color, Color>? {
    val c = Sania.colors
    return when (estado) {
        "pagado" -> c.ok to c.okBg
        "debe", "parcial" -> c.pend to c.pendBg
        "gratis" -> c.textoSuave to c.chipBg
        else -> null
    }
}
