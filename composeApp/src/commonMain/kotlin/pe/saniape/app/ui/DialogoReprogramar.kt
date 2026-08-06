package pe.saniape.app.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.DatePicker
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import pe.saniape.app.ui.theme.Sania

/**
 * Elegir nueva fecha y hora para la cita.
 *
 * Primero el día, después la hora — el mismo orden del formulario de reserva,
 * para que el paciente reconozca el paso.
 *
 * El servidor valida lo que aquí no se puede saber (que la clínica atienda ese
 * día, que falten más de 3 horas) y devuelve el motivo en español.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DialogoReprogramar(
    onCerrar: () -> Unit,
    onElegir: (fecha: String, hora: String) -> Unit,
) {
    val c = Sania.colors
    var fecha by remember { mutableStateOf<String?>(null) }

    if (fecha == null) {
        // Solo desde hoy: dejar tocar el lunes pasado y rechazarlo después es
        // hacerle perder el tiempo al paciente. El servidor igual lo valida.
        val estado = rememberDatePickerState(
            selectableDates = object : SelectableDates {
                override fun isSelectableDate(utcTimeMillis: Long): Boolean {
                    val hoy = Clock.System.now().toEpochMilliseconds()
                    // Margen de un día: el picker trabaja en UTC y Perú va -5.
                    return utcTimeMillis >= hoy - 86_400_000L
                }
                override fun isSelectableYear(year: Int): Boolean {
                    val actual = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).year
                    return year >= actual
                }
            },
        )
        DatePickerDialog(
            onDismissRequest = onCerrar,
            confirmButton = {
                TextButton(onClick = {
                    estado.selectedDateMillis?.let { fecha = millisAIso(it) }
                }) { Text("Siguiente", color = c.navy) }
            },
            dismissButton = { TextButton(onClick = onCerrar) { Text("Cancelar", color = c.textoSuave) } },
        ) { DatePicker(state = estado) }
    } else {
        val estado = rememberTimePickerState(initialHour = 10, initialMinute = 0, is24Hour = false)   // 12h (AM/PM): nadie en Perú dice "15:00". Se guarda igual en 24h.
        DatePickerDialog(   // mismo contenedor que en reservas, para el TimePicker
            onDismissRequest = onCerrar,
            confirmButton = {
                TextButton(onClick = {
                    onElegir(fecha!!, "${estado.hour.dosDigitos()}:${estado.minute.dosDigitos()}")
                }) { Text("Reprogramar", color = c.navy) }
            },
            dismissButton = { TextButton(onClick = onCerrar) { Text("Cancelar", color = c.textoSuave) } },
        ) {
            Box(Modifier.fillMaxWidth().padding(Sania.dim.lg), Alignment.Center) {
                TimePicker(state = estado)
            }
        }
    }
}

private fun Int.dosDigitos(): String = toString().padStart(2, '0')

/** Millis UTC del DatePicker → yyyy-MM-dd (entrega medianoche UTC). */
private fun millisAIso(millis: Long): String {
    val d = Instant.fromEpochMilliseconds(millis).toLocalDateTime(TimeZone.UTC).date
    return "${d.year}-${d.monthNumber.dosDigitos()}-${d.dayOfMonth.dosDigitos()}"
}
