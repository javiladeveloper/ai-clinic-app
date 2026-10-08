package pe.saniape.app.ui.clinica.pacientes

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import pe.saniape.app.data.staff.hoyClinicaIso
import pe.saniape.app.data.staff.isoAMillisUtc
import pe.saniape.app.ui.fechaDMA
import pe.saniape.app.ui.theme.Sania

/**
 * FECHA DE INGRESO DEL PACIENTE (DALU, 2026-10-07): nace con el día de hoy y se
 * puede corregir para quien llegó a la clínica antes de usar Sania. Mismas
 * reglas que la web (lib/fecha-ingreso.ts): no futura, no antes del 2000.
 */
const val FECHA_INGRESO_MINIMA = "2000-01-01"

fun fechaIngresoValida(iso: String?, hoy: String = hoyClinicaIso()): Boolean {
    if (iso == null || !Regex("""^\d{4}-\d{2}-\d{2}$""").matches(iso)) return false
    if (runCatching { kotlinx.datetime.LocalDate.parse(iso) }.isFailure) return false
    return iso >= FECHA_INGRESO_MINIMA && iso <= hoy
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CampoFechaIngreso(fecha: String, onCambio: (String) -> Unit, habilitado: Boolean = true) {
    val c = Sania.colors
    var abierto by remember { mutableStateOf(false) }
    val hoy = remember { hoyClinicaIso() }

    if (abierto) {
        val estado = rememberDatePickerState(
            initialSelectedDateMillis = isoAMillisUtc(fecha) ?: isoAMillisUtc(hoy),
            selectableDates = object : SelectableDates {
                override fun isSelectableDate(utcTimeMillis: Long): Boolean =
                    fechaIngresoValida(millisAIsoIngreso(utcTimeMillis), hoy)
                override fun isSelectableYear(year: Int): Boolean = year >= 2000
            },
        )
        DatePickerDialog(
            onDismissRequest = { abierto = false },
            confirmButton = {
                TextButton(onClick = {
                    estado.selectedDateMillis?.let { onCambio(millisAIsoIngreso(it)) }
                    abierto = false
                }) { Text("Aceptar", color = c.navy) }
            },
            dismissButton = { TextButton(onClick = { abierto = false }) { Text("Cancelar", color = c.textoSuave) } },
        ) { DatePicker(state = estado) }
    }

    Column(Modifier.fillMaxWidth()) {
        Text("Fecha de ingreso", color = c.textoSuave, fontSize = 12.sp, fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(bottom = 4.dp))
        Row(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(Sania.shape.sm.dp))
                .background(c.superficie).border(1.dp, c.borde, RoundedCornerShape(Sania.shape.sm.dp))
                .clickable(enabled = habilitado) { abierto = true }
                .padding(horizontal = 12.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text("📅 ${if (fecha.isBlank()) "Sin fecha" else fechaDMA(fecha)}", color = c.texto, fontSize = Sania.txt.cuerpo)
            Text("▾", color = c.navy)
        }
        Text(
            "Cuándo llegó a la clínica. Cámbiala si vino antes de usar Sania.",
            color = c.textoSuave, fontSize = 11.sp, lineHeight = 14.sp, modifier = Modifier.padding(top = 4.dp),
        )
    }
}

private fun millisAIsoIngreso(millis: Long): String {
    val d = Instant.fromEpochMilliseconds(millis).toLocalDateTime(TimeZone.UTC).date
    return "${d.year}-${d.monthNumber.toString().padStart(2, '0')}-${d.dayOfMonth.toString().padStart(2, '0')}"
}
