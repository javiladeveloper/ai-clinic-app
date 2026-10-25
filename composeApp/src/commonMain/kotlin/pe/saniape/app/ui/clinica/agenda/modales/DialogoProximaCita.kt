package pe.saniape.app.ui.clinica.agenda.modales

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import pe.saniape.app.data.staff.PropuestaProxima
import pe.saniape.app.data.staff.detallePropuesta
import pe.saniape.app.ui.theme.Sania

/**
 * «📅 Agendar próxima: mié 4 nov · 10:00 a. m. con Lic. Ana» — UN toque, más
 * «Otra fecha» (el formulario de siempre prellenado) y «Ahora no». Gemelo del
 * chip de la web (components/citas/proxima-cita-oferta.ts): la propuesta viene
 * del servidor (ProximaCitaRepo), la app no calcula fechas. Lo usan la agenda
 * y la ficha.
 */
@Composable
fun DialogoProximaCita(
    pacienteNombre: String?,
    propuesta: PropuestaProxima,
    agendando: Boolean,
    onAgendar: () -> Unit,
    onOtraFecha: () -> Unit,
    onCerrar: () -> Unit,
) {
    val c = Sania.colors
    AlertDialog(
        onDismissRequest = { if (!agendando) onCerrar() },
        title = { Text("📅 Agendar próxima", fontWeight = FontWeight.Bold) },
        text = {
            Column {
                pacienteNombre?.let { Text(it, color = c.textoSuave, fontSize = 12.sp) }
                Spacer(Modifier.height(4.dp))
                Text(propuesta.etiqueta, color = c.texto, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(4.dp))
                Text(detallePropuesta(propuesta), color = c.textoSuave, fontSize = 12.sp)
            }
        },
        confirmButton = {
            TextButton(onClick = onAgendar, enabled = !agendando) {
                Text(if (agendando) "Agendando…" else "Agendar", color = c.navy, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            Row {
                TextButton(onClick = onOtraFecha, enabled = !agendando) { Text("Otra fecha", color = c.navy) }
                TextButton(onClick = onCerrar, enabled = !agendando) { Text("Ahora no", color = c.textoSuave) }
            }
        },
        containerColor = c.superficie,
    )
}
