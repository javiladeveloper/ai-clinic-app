package pe.saniape.app.ui.clinica.agenda.modales

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import pe.saniape.app.data.staff.CitaStaff
import pe.saniape.app.data.staff.monedaDeFila
import pe.saniape.app.data.staff.simboloMoneda
import pe.saniape.app.ui.AlertaConTeclado
import pe.saniape.app.ui.clinica.pacientes.coloresCampoForm
import pe.saniape.app.ui.theme.Sania

/** El precio escrito (acepta "40", "40.5" o "40,5"); null si no es un número > 0. */
internal fun leerPrecioCita(texto: String): Double? =
    texto.trim().replace(',', '.').toDoubleOrNull()?.takeIf { it.isFinite() && it > 0 }

/**
 * "💲 Poner precio" a una cita SIN costo (gemelo del modal "Corregir cobro" de
 * /citas web cuando la cita no tiene precio): las importadas de Google Calendar
 * entran sin servicio ni costo, y sin precio no hay 💰. Con teclado: el botón
 * de guardar también sale en la cabecera (AlertaConTeclado, Android 16).
 */
@Composable
fun DialogoPonerPrecio(
    cita: CitaStaff,
    guardando: Boolean,
    onCancelar: () -> Unit,
    onGuardar: (Double) -> Unit,
) {
    val c = Sania.colors
    var texto by remember(cita.id) { mutableStateOf("") }
    var error by remember(cita.id) { mutableStateOf<String?>(null) }
    val simbolo = simboloMoneda(monedaDeFila(cita.sedeId))
    fun guardar() {
        val monto = leerPrecioCita(texto)
        if (monto == null) { error = "Escribe el precio de la cita"; return }
        error = null
        onGuardar(monto)
    }
    AlertaConTeclado(
        onDismissRequest = onCancelar,
        title = {
            Column {
                Text("💲 Poner precio", fontWeight = FontWeight.Bold)
                cita.pacienteNombre?.let { Text(it, color = c.textoSuave, fontSize = 13.sp, modifier = Modifier.padding(top = 2.dp)) }
            }
        },
        text = {
            Column {
                Text(
                    "Esta cita no tiene precio (p. ej. vino de Google Calendar). Con el precio ya puedes cobrarla con 💰.",
                    color = c.textoSuave, fontSize = 13.sp,
                )
                OutlinedTextField(
                    value = texto,
                    onValueChange = { v -> texto = v.filter { it.isDigit() || it == '.' || it == ',' }.take(10); error = null },
                    label = { Text("Precio de la cita ($simbolo)") },
                    singleLine = true,
                    isError = error != null,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    colors = coloresCampoForm(),
                    shape = RoundedCornerShape(Sania.shape.sm.dp),
                    modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
                )
                error?.let { Text(it, color = c.error, fontSize = 12.sp, modifier = Modifier.padding(top = 4.dp)) }
            }
        },
        confirmButton = {
            TextButton(enabled = !guardando, onClick = ::guardar) {
                Text(if (guardando) "Guardando…" else "Guardar precio", color = c.navy, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(enabled = !guardando, onClick = onCancelar) { Text("Cancelar", color = c.textoSuave) }
        },
        containerColor = c.superficie,
    )
}
