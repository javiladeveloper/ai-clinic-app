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
import pe.saniape.app.ui.clinica.pacientes.ChipsMetodoPago
import pe.saniape.app.ui.clinica.pacientes.coloresCampoForm
import pe.saniape.app.ui.clinica.pacientes.rememberMetodoPagoInicial
import pe.saniape.app.ui.theme.Sania

/**
 * 💰 del saldo del PAQUETE en una sesión ya atendida (gemelo del modal de cobro
 * de /citas web en modo paquete). Propone lo que se debe del paquete y nunca deja
 * pasar de eso ([pe.saniape.app.data.staff.montoAbonoPaquete]). Entra a caja como
 * «Registrar pago» de la ficha: abono al tratamiento, sin ligarlo a la sesión.
 * Con teclado: el botón también sale en la cabecera (AlertaConTeclado, Android 16).
 */
@Composable
fun DialogoAbonarPaquete(
    cita: CitaStaff,
    /** Lo que se debe del paquete (servidor). */
    deuda: Double,
    guardando: Boolean,
    onCancelar: () -> Unit,
    onConfirmar: (monto: Double, metodo: String) -> Unit,
) {
    val c = Sania.colors
    val moneda = monedaDeFila(cita.sedeId)
    var texto by remember(cita.id) { mutableStateOf(pe.saniape.app.data.staff.textoMonto(deuda)) }
    var error by remember(cita.id) { mutableStateOf<String?>(null) }
    var metodo by rememberMetodoPagoInicial(cita.pacienteId)
    fun confirmar() {
        val pedido = texto.trim().replace(',', '.').toDoubleOrNull()
        val monto = pedido?.let { pe.saniape.app.data.staff.montoAbonoPaquete(it, deuda) }
        if (monto == null) {
            error = "Monto inválido: hasta lo que se debe del paquete (${textoSoles(deuda, moneda)})."
            return
        }
        error = null
        onConfirmar(monto, metodo)
    }
    AlertaConTeclado(
        onDismissRequest = { if (!guardando) onCancelar() },
        title = {
            Column {
                Text("💰 Cobrar el paquete", fontWeight = FontWeight.Bold)
                cita.pacienteNombre?.let { Text(it, color = c.textoSuave, fontSize = 13.sp, modifier = Modifier.padding(top = 2.dp)) }
            }
        },
        text = {
            Column {
                Text(
                    "Se abona al paquete de esta sesión" + (cita.procedimiento?.let { " ($it)" } ?: "") +
                        ": se descuenta de lo que debe (${textoSoles(deuda, moneda)}).",
                    color = c.texto, fontSize = 13.sp,
                )
                OutlinedTextField(
                    value = texto,
                    onValueChange = { v -> texto = v.filter { it.isDigit() || it == '.' || it == ',' }.take(10); error = null },
                    label = { Text("Monto (${simboloMoneda(moneda)})") },
                    singleLine = true,
                    isError = error != null,
                    enabled = !guardando,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    colors = coloresCampoForm(),
                    shape = RoundedCornerShape(Sania.shape.sm.dp),
                    modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
                )
                error?.let { Text(it, color = c.error, fontSize = 12.sp, modifier = Modifier.padding(top = 4.dp)) }
                Text("Método", color = c.textoSuave, fontSize = 11.sp, fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(top = 12.dp, bottom = 6.dp))
                ChipsMetodoPago(metodo) { metodo = it }
                Text(
                    "Entra a caja como pago del paquete, igual que «Registrar pago» en la ficha del paciente.",
                    color = c.textoSuave, fontSize = 11.sp, modifier = Modifier.padding(top = 10.dp),
                )
            }
        },
        confirmButton = {
            TextButton(enabled = !guardando, onClick = ::confirmar) {
                Text(if (guardando) "Guardando…" else "Registrar pago", color = c.ok, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(enabled = !guardando, onClick = onCancelar) { Text("Cancelar", color = c.textoSuave) }
        },
        containerColor = c.superficie,
    )
}
