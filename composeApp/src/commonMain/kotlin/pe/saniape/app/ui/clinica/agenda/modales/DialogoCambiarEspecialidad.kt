package pe.saniape.app.ui.clinica.agenda.modales

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import pe.saniape.app.data.staff.CitaStaff
import pe.saniape.app.data.staff.EspecialidadRef
import pe.saniape.app.ui.theme.Sania

/**
 * «🏷️ Cambiar especialidad» de una consulta/evaluación por atender (gemelo del
 * modal de /citas web; Centro OMER). Al atenderla, la historia clínica y lo demás
 * salen de la especialidad elegida. No cambia fecha, hora, profesional ni precio.
 * [especialidadesDelProfesional] vacío = no se sabe (no se avisa nada).
 */
@Composable
fun DialogoCambiarEspecialidad(
    cita: CitaStaff,
    especialidades: List<EspecialidadRef>,
    actualId: String?,
    especialidadesDelProfesional: List<String>,
    guardando: Boolean,
    onCancelar: () -> Unit,
    onGuardar: (String) -> Unit,
) {
    val c = Sania.colors
    var elegida by remember(cita.id) { mutableStateOf(actualId?.takeIf { id -> especialidades.any { it.id == id } }) }
    val profNoLaAtiende = elegida != null && especialidadesDelProfesional.isNotEmpty() && elegida !in especialidadesDelProfesional
    AlertDialog(
        onDismissRequest = onCancelar,
        title = {
            Column {
                Text("🏷️ Cambiar especialidad", fontWeight = FontWeight.Bold)
                Text(
                    listOfNotNull(cita.tipo, cita.pacienteNombre).joinToString(" de "),
                    color = c.textoSuave, fontSize = 13.sp, modifier = Modifier.padding(top = 2.dp),
                )
            }
        },
        text = {
            Column {
                Text(
                    "Al atenderla, la historia clínica y lo demás saldrán de la especialidad que elijas. La fecha, la hora, el profesional y el precio no cambian.",
                    color = c.textoSuave, fontSize = 13.sp,
                )
                Column(Modifier.padding(top = 8.dp).heightIn(max = 320.dp).verticalScroll(rememberScrollState())) {
                    especialidades.forEach { e ->
                        Row(
                            Modifier.fillMaxWidth().clickable(enabled = !guardando) { elegida = e.id }.padding(vertical = 2.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            RadioButton(
                                selected = elegida == e.id, onClick = { elegida = e.id }, enabled = !guardando,
                                colors = RadioButtonDefaults.colors(selectedColor = c.navy),
                            )
                            Text(e.nombre, color = c.texto, fontSize = 15.sp)
                        }
                    }
                }
                if (profNoLaAtiende) {
                    Text(
                        "El profesional de esta cita no tiene esa especialidad. Si la atenderá otra persona, cámbiala con «✏ Editar o reprogramar».",
                        color = c.pend, fontSize = 12.sp, modifier = Modifier.padding(top = 8.dp),
                    )
                }
            }
        },
        confirmButton = {
            TextButton(enabled = !guardando && elegida != null && elegida != actualId, onClick = { elegida?.let(onGuardar) }) {
                Text(if (guardando) "Guardando…" else "Guardar", color = c.navy, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(enabled = !guardando, onClick = onCancelar) { Text("Cancelar", color = c.textoSuave) }
        },
        containerColor = c.superficie,
    )
}
