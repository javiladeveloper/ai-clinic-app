package pe.saniape.app.ui.clinica.agenda.modales

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import pe.saniape.app.data.staff.CitaStaff
import pe.saniape.app.data.staff.EspecialidadRef
import pe.saniape.app.ui.clinica.agenda.AccionCita
import pe.saniape.app.ui.theme.Sania

/**
 * Modal de completar Evaluación/Sesión (como la web):
 *  - Evaluación: diagnóstico + opción de derivar a especialidad.
 *  - Sesión: observaciones (procedimientos realizados).
 */
@Composable
fun ModalCompletar(
    cita: CitaStaff,
    especialidades: List<EspecialidadRef>,
    onCancelar: () -> Unit,
    onConfirmar: (observaciones: String?, diagnostico: String?, derivarEspId: String?) -> Unit,
) {
    val c = Sania.colors
    val esEvaluacion = cita.tipo == "Evaluación"
    var texto by remember { mutableStateOf("") }
    var derivar by remember { mutableStateOf(false) }
    var espElegida by remember { mutableStateOf<EspecialidadRef?>(null) }

    AlertDialog(
        onDismissRequest = onCancelar,
        title = { Text(if (esEvaluacion) "✓ Completar evaluación" else "✓ Completar sesión") },
        text = {
            Column {
                if (esEvaluacion) {
                    OutlinedTextField(
                        value = texto, onValueChange = { texto = it },
                        label = { Text("Diagnóstico") },
                        placeholder = { Text("Ej. Lumbalgia mecánica…", color = c.textoSuave) },
                        modifier = Modifier.fillMaxWidth(), minLines = 2,
                    )
                } else {
                    // Sesión: autocomplete de procedimientos/técnicas (como la web).
                    Text("Procedimientos realizados", color = c.textoSuave, fontSize = Sania.txt.mini,
                        fontWeight = FontWeight.Bold, modifier = Modifier.padding(bottom = 4.dp))
                    pe.saniape.app.ui.clinica.agenda.componentes.TecnicasInput(
                        value = texto, onChange = { texto = it },
                    )
                }
                if (esEvaluacion && especialidades.size > 1) {
                    Spacer(Modifier.height(Sania.dim.md))
                    Row(verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.clickable { derivar = !derivar }) {
                        Text(if (derivar) "☑" else "☐", fontSize = 18.sp, color = c.navy)
                        Spacer(Modifier.width(6.dp))
                        Text("↗ Derivar a otra especialidad", color = c.texto, fontSize = Sania.txt.cuerpo)
                    }
                    if (derivar) {
                        Spacer(Modifier.height(Sania.dim.sm))
                        especialidades.forEach { esp ->
                            val activa = espElegida?.id == esp.id
                            Row(
                                Modifier.fillMaxWidth().padding(vertical = 2.dp)
                                    .clip(RoundedCornerShape(Sania.shape.sm.dp))
                                    .background(if (activa) c.chipBg else c.superficie)
                                    .border(1.dp, if (activa) c.navy else c.borde, RoundedCornerShape(Sania.shape.sm.dp))
                                    .clickable { espElegida = if (activa) null else esp }
                                    .padding(horizontal = 12.dp, vertical = 10.dp),
                            ) {
                                Text(esp.nombre, color = if (activa) c.navy else c.texto, fontSize = Sania.txt.pequeno,
                                    fontWeight = if (activa) FontWeight.Bold else FontWeight.Normal)
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                onConfirmar(
                    texto.trim().ifBlank { null },
                    if (esEvaluacion) texto.trim().ifBlank { null } else null,
                    if (esEvaluacion && derivar) espElegida?.id else null,
                )
            }) { Text("Guardar y completar", color = c.navy, fontWeight = FontWeight.Bold) }
        },
        dismissButton = { TextButton(onClick = onCancelar) { Text("Cancelar", color = c.textoSuave) } },
        containerColor = c.superficie,
    )
}

/** Confirmación antes de cancelar/revertir (evita miss-clicks, como la web). */
@Composable
fun ConfirmacionAccion(cita: CitaStaff, accion: AccionCita, onCancelar: () -> Unit, onConfirmar: () -> Unit) {
    val c = Sania.colors
    val esCancelar = accion == AccionCita.Cancelar
    AlertDialog(
        onDismissRequest = onCancelar,
        title = { Text(if (esCancelar) "¿Cancelar esta cita?" else "¿Revertir esta cita?") },
        text = {
            Text(
                if (esCancelar) {
                    if (cita.tipo == "Sesión") "Se eliminará la sesión vinculada." else "La cita quedará como cancelada."
                } else "Volverá a confirmada y se deshará el cobro/registro asociado.",
                color = c.texto, fontSize = Sania.txt.cuerpo,
            )
        },
        confirmButton = {
            TextButton(onClick = onConfirmar) {
                Text(if (esCancelar) "Sí, cancelar" else "Sí, revertir",
                    color = if (esCancelar) c.error else c.pend, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = { TextButton(onClick = onCancelar) { Text("No", color = c.textoSuave) } },
        containerColor = c.superficie,
    )
}

/** Editar/Reprogramar cita: fecha y hora con pickers nativos. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ModalEditarCita(cita: CitaStaff, onCancelar: () -> Unit, onGuardar: (fecha: String, hora: String) -> Unit) {
    val c = Sania.colors
    var fecha by remember { mutableStateOf(cita.fecha) }
    var hora by remember { mutableStateOf(cita.hora.take(5)) }
    var mostrarFecha by remember { mutableStateOf(false) }
    var mostrarHora by remember { mutableStateOf(false) }

    if (mostrarFecha) {
        val estado = rememberDatePickerState()
        DatePickerDialog(
            onDismissRequest = { mostrarFecha = false },
            confirmButton = {
                TextButton(onClick = {
                    estado.selectedDateMillis?.let { fecha = millisISO(it) }; mostrarFecha = false
                }) { Text("Aceptar", color = c.navy) }
            },
            dismissButton = { TextButton(onClick = { mostrarFecha = false }) { Text("Cancelar", color = c.textoSuave) } },
        ) { DatePicker(state = estado) }
    }
    if (mostrarHora) {
        val p = hora.split(":")
        val estado = rememberTimePickerState(p.getOrNull(0)?.toIntOrNull() ?: 9, p.getOrNull(1)?.toIntOrNull() ?: 0, true)
        DatePickerDialog(
            onDismissRequest = { mostrarHora = false },
            confirmButton = {
                TextButton(onClick = {
                    hora = "${estado.hour.toString().padStart(2, '0')}:${estado.minute.toString().padStart(2, '0')}"; mostrarHora = false
                }) { Text("Aceptar", color = c.navy) }
            },
            dismissButton = { TextButton(onClick = { mostrarHora = false }) { Text("Cancelar", color = c.textoSuave) } },
        ) { Box(Modifier.fillMaxWidth().padding(Sania.dim.lg), Alignment.Center) { TimePicker(state = estado) } }
    }

    AlertDialog(
        onDismissRequest = onCancelar,
        title = { Text("✏ Editar cita") },
        text = {
            Column {
                Text("Cita de ${cita.pacienteNombre ?: "paciente"} · ${cita.tipo ?: ""}",
                    color = c.textoSuave, fontSize = Sania.txt.pequeno)
                if (cita.tipo == "Sesión") {
                    Text("También se actualizará la sesión vinculada.", color = c.textoSuave, fontSize = 11.sp)
                }
                Spacer(Modifier.height(Sania.dim.md))
                SelectorBotonModal("Fecha", fecha) { mostrarFecha = true }
                Spacer(Modifier.height(Sania.dim.sm))
                SelectorBotonModal("Hora", hora) { mostrarHora = true }
            }
        },
        confirmButton = {
            TextButton(onClick = { onGuardar(fecha, hora) }) {
                Text("Guardar cambios", color = c.navy, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = { TextButton(onClick = onCancelar) { Text("Cancelar", color = c.textoSuave) } },
        containerColor = c.superficie,
    )
}

/** "Pasar a Evaluación": elegir mismo horario o nueva hora (como la web). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ModalPasarEvaluacion(cita: CitaStaff, onCancelar: () -> Unit, onElegir: (fecha: String, hora: String) -> Unit) {
    val c = Sania.colors
    var eligiendoNueva by remember { mutableStateOf(false) }
    var fecha by remember { mutableStateOf(cita.fecha) }
    var hora by remember { mutableStateOf(cita.hora.take(5)) }
    var mostrarFecha by remember { mutableStateOf(false) }
    var mostrarHora by remember { mutableStateOf(false) }

    if (mostrarFecha) {
        val estado = rememberDatePickerState()
        DatePickerDialog(
            onDismissRequest = { mostrarFecha = false },
            confirmButton = { TextButton(onClick = { estado.selectedDateMillis?.let { fecha = millisISO(it) }; mostrarFecha = false }) { Text("Aceptar", color = c.navy) } },
            dismissButton = { TextButton(onClick = { mostrarFecha = false }) { Text("Cancelar", color = c.textoSuave) } },
        ) { DatePicker(state = estado) }
    }
    if (mostrarHora) {
        val p = hora.split(":")
        val estado = rememberTimePickerState(p.getOrNull(0)?.toIntOrNull() ?: 9, p.getOrNull(1)?.toIntOrNull() ?: 0, true)
        DatePickerDialog(
            onDismissRequest = { mostrarHora = false },
            confirmButton = { TextButton(onClick = { hora = "${estado.hour.toString().padStart(2, '0')}:${estado.minute.toString().padStart(2, '0')}"; mostrarHora = false }) { Text("Aceptar", color = c.navy) } },
            dismissButton = { TextButton(onClick = { mostrarHora = false }) { Text("Cancelar", color = c.textoSuave) } },
        ) { Box(Modifier.fillMaxWidth().padding(Sania.dim.lg), Alignment.Center) { TimePicker(state = estado) } }
    }

    AlertDialog(
        onDismissRequest = onCancelar,
        title = { Text("🔍 Pasar a Evaluación") },
        text = {
            Column {
                Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(Sania.shape.sm.dp))
                    .background(c.errorBg).padding(Sania.dim.md)) {
                    Text("⚠ El horario ${cita.fecha} ${cita.hora.take(5)} ya está separado para la consulta de ${cita.pacienteNombre ?: "el paciente"}.",
                        color = c.error, fontSize = 12.sp)
                }
                Spacer(Modifier.height(Sania.dim.md))
                Text("¿La evaluación será en el mismo horario o en una nueva hora?",
                    color = c.texto, fontSize = Sania.txt.cuerpo)
                if (eligiendoNueva) {
                    Spacer(Modifier.height(Sania.dim.md))
                    SelectorBotonModal("Fecha", fecha) { mostrarFecha = true }
                    Spacer(Modifier.height(Sania.dim.sm))
                    SelectorBotonModal("Hora", hora) { mostrarHora = true }
                }
            }
        },
        confirmButton = {
            if (eligiendoNueva) {
                TextButton(onClick = { onElegir(fecha, hora) }) {
                    Text("Agendar", color = c.navy, fontWeight = FontWeight.Bold)
                }
            } else {
                TextButton(onClick = { onElegir(cita.fecha, cita.hora.take(5)) }) {
                    Text("✓ Mismo horario", color = c.ok, fontWeight = FontWeight.Bold)
                }
            }
        },
        dismissButton = {
            if (!eligiendoNueva) {
                TextButton(onClick = { eligiendoNueva = true }) { Text("📅 Nueva hora", color = c.navy) }
            } else {
                TextButton(onClick = onCancelar) { Text("Cancelar", color = c.textoSuave) }
            }
        },
        containerColor = c.superficie,
    )
}

@Composable
private fun SelectorBotonModal(label: String, valor: String, onClick: () -> Unit) {
    val c = Sania.colors
    Column {
        Text(label, color = c.textoSuave, fontSize = 11.sp, fontWeight = FontWeight.Bold)
        Row(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(Sania.shape.sm.dp))
                .background(c.superficie).border(1.dp, c.borde, RoundedCornerShape(Sania.shape.sm.dp))
                .clickable { onClick() }.padding(horizontal = 12.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(valor, color = c.texto, fontSize = Sania.txt.cuerpo)
            Text("▾", color = c.navy)
        }
    }
}

private fun millisISO(millis: Long): String {
    val d = Instant.fromEpochMilliseconds(millis).toLocalDateTime(TimeZone.UTC).date
    return "${d.year}-${d.monthNumber.toString().padStart(2, '0')}-${d.dayOfMonth.toString().padStart(2, '0')}"
}