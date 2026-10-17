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
import pe.saniape.app.data.staff.TEXTO_YA_PAGO
import pe.saniape.app.data.staff.TEXTO_YA_PAGO_CORTO
import pe.saniape.app.ui.theme.Sania

/**
 * ¿El paciente ya pagó? Badge para quien atiende (agenda, consulta guiada,
 * completar sesión). El texto lo arma el servidor (`etiqueta`): solo el estado y
 * la deuda, nunca lo ya pagado. Verde = pagado, ámbar = debe/parcial, gris =
 * gratis. sin_dato o cita cancelada = no se pinta nada. Nunca bloquea atender.
 * Pagado = "✓ Ya pagó" ([EtiquetaYaPago]), no la etiqueta del servidor.
 */
@Composable
fun BadgeEstadoPago(estado: EstadoPagoCita?, estadoCita: String? = null, modifier: Modifier = Modifier) {
    if (estado == null || !estado.mostrable || estadoCita == "Cancelada") return
    // Pagado: "✓ Ya pagó" en TEXTO (gemelo de <EtiquetaPagado> web, 2026-10-17: el
    // 💰 verde solo no se distinguía). Misma regla: estado del servidor == "pagado".
    if (estado.estado == "pagado") { EtiquetaYaPago(modifier = modifier); return }
    val (fg, bg) = coloresEstadoPago(estado.estado) ?: return
    Box(modifier.clip(RoundedCornerShape(Sania.shape.pill.dp)).background(bg)
        .padding(horizontal = 8.dp, vertical = 3.dp)) {
        Text(estado.etiqueta, color = fg, fontSize = 10.sp, fontWeight = FontWeight.Bold)
    }
}

/**
 * "✓ Ya pagó" (o "✓ Pagó" en las vistas apretadas, nunca solo ícono): pastilla
 * verde del tema. Quien llama ya decidió que pagó (citaYaPago / yaPagoConRespaldo).
 * El 💰 no se quita: sigue siendo el botón para cobrar / ver.
 */
@Composable
fun EtiquetaYaPago(compacta: Boolean = false, modifier: Modifier = Modifier) {
    val c = Sania.colors
    Box(modifier.clip(RoundedCornerShape(Sania.shape.pill.dp)).background(c.okBg)
        .padding(horizontal = if (compacta) 6.dp else 8.dp, vertical = 3.dp)) {
        Text(if (compacta) TEXTO_YA_PAGO_CORTO else TEXTO_YA_PAGO, color = c.ok,
            fontSize = 10.sp, fontWeight = FontWeight.Bold, maxLines = 1, softWrap = false)
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
