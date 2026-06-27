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
import pe.saniape.app.ui.hora12
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
        title = {
            Column {
                Text(if (esEvaluacion) "🔍 Completar evaluación" else "🏃 Completar sesión",
                    fontWeight = FontWeight.Bold)
                cita.pacienteNombre?.let {
                    Text(it, color = c.textoSuave, fontSize = Sania.txt.pequeno,
                        modifier = Modifier.padding(top = 2.dp))
                }
            }
        },
        text = {
            Column {
                if (esEvaluacion) {
                    Text("Diagnóstico / Motivo", color = c.textoSuave, fontSize = Sania.txt.mini,
                        fontWeight = FontWeight.Bold, modifier = Modifier.padding(bottom = 6.dp))
                    OutlinedTextField(
                        value = texto, onValueChange = { texto = it },
                        placeholder = { Text("Ej. Lumbalgia mecánica…", color = c.textoSuave) },
                        modifier = Modifier.fillMaxWidth(), minLines = 3,
                        shape = RoundedCornerShape(Sania.shape.sm.dp),
                    )
                    Text("Se guardará en la ficha del paciente.", color = c.textoSuave,
                        fontSize = 11.sp, modifier = Modifier.padding(top = 4.dp))
                } else {
                    Text("Procedimientos realizados", color = c.textoSuave, fontSize = Sania.txt.mini,
                        fontWeight = FontWeight.Bold, modifier = Modifier.padding(bottom = 6.dp))
                    pe.saniape.app.ui.clinica.agenda.componentes.TecnicasInput(
                        value = texto, onChange = { texto = it },
                    )
                }
                if (esEvaluacion && especialidades.size > 1) {
                    Spacer(Modifier.height(Sania.dim.lg))
                    // Tarjeta de derivación (más clara que el checkbox suelto).
                    Column(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(Sania.shape.sm.dp))
                            .background(if (derivar) c.purpleBg else c.chipBg)
                            .border(1.dp, if (derivar) c.purple else c.borde, RoundedCornerShape(Sania.shape.sm.dp))
                            .padding(Sania.dim.md),
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth().clickable { derivar = !derivar }) {
                            Text(if (derivar) "☑" else "☐", fontSize = 18.sp,
                                color = if (derivar) c.purple else c.textoSuave)
                            Spacer(Modifier.width(8.dp))
                            Column(Modifier.weight(1f)) {
                                Text("↗ Derivar a otra especialidad", color = c.texto,
                                    fontSize = Sania.txt.cuerpo, fontWeight = FontWeight.SemiBold)
                                Text("Recepción agendará con el especialista", color = c.textoSuave, fontSize = 11.sp)
                            }
                        }
                        if (derivar) {
                            Spacer(Modifier.height(Sania.dim.sm))
                            especialidades.forEach { esp ->
                                val activa = espElegida?.id == esp.id
                                Row(
                                    Modifier.fillMaxWidth().padding(vertical = 3.dp)
                                        .clip(RoundedCornerShape(Sania.shape.sm.dp))
                                        .background(if (activa) c.purple else c.superficie)
                                        .border(1.dp, if (activa) c.purple else c.borde, RoundedCornerShape(Sania.shape.sm.dp))
                                        .clickable { espElegida = if (activa) null else esp }
                                        .padding(horizontal = 12.dp, vertical = 10.dp),
                                ) {
                                    Text(esp.nombre, color = if (activa) c.sobreNavy else c.texto,
                                        fontSize = Sania.txt.pequeno,
                                        fontWeight = if (activa) FontWeight.Bold else FontWeight.Normal)
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            // Botón principal grande (full-width via padding del AlertDialog).
            Box(
                Modifier.clip(RoundedCornerShape(Sania.shape.md.dp)).background(c.navy)
                    .clickable {
                        onConfirmar(
                            texto.trim().ifBlank { null },
                            if (esEvaluacion) texto.trim().ifBlank { null } else null,
                            if (esEvaluacion && derivar) espElegida?.id else null,
                        )
                    }.padding(horizontal = 20.dp, vertical = 11.dp),
            ) { Text("✓ Guardar y completar", color = c.sobreNavy, fontWeight = FontWeight.Bold) }
        },
        dismissButton = { TextButton(onClick = onCancelar) { Text("Cancelar", color = c.textoSuave) } },
        containerColor = c.superficie,
        shape = RoundedCornerShape(Sania.shape.lg.dp),
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
        val estado = rememberTimePickerState(p.getOrNull(0)?.toIntOrNull() ?: 9, p.getOrNull(1)?.toIntOrNull() ?: 0, false)
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
                SelectorBotonModal("Hora", hora12(hora)) { mostrarHora = true }
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
        val estado = rememberTimePickerState(p.getOrNull(0)?.toIntOrNull() ?: 9, p.getOrNull(1)?.toIntOrNull() ?: 0, false)
        DatePickerDialog(
            onDismissRequest = { mostrarHora = false },
            confirmButton = { TextButton(onClick = { hora = "${estado.hour.toString().padStart(2, '0')}:${estado.minute.toString().padStart(2, '0')}"; mostrarHora = false }) { Text("Aceptar", color = c.navy) } },
            dismissButton = { TextButton(onClick = { mostrarHora = false }) { Text("Cancelar", color = c.textoSuave) } },
        ) { Box(Modifier.fillMaxWidth().padding(Sania.dim.lg), Alignment.Center) { TimePicker(state = estado) } }
    }

    AlertDialog(
        onDismissRequest = onCancelar,
        title = { Text("🔍 Pasar a Evaluación", fontWeight = FontWeight.Bold) },
        text = {
            Column {
                Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(Sania.shape.sm.dp))
                    .background(c.errorBg).padding(Sania.dim.md)) {
                    Text("⚠ El horario ${cita.fecha} ${hora12(cita.hora)} ya está separado para la consulta de ${cita.pacienteNombre ?: "el paciente"}.",
                        color = c.error, fontSize = 12.sp)
                }
                Spacer(Modifier.height(Sania.dim.md))
                Text("¿La evaluación será en el mismo horario o en una nueva hora?",
                    color = c.texto, fontSize = Sania.txt.cuerpo)

                if (eligiendoNueva) {
                    // Paso 2: elegir nueva fecha/hora.
                    Spacer(Modifier.height(Sania.dim.md))
                    SelectorBotonModal("Fecha", fecha) { mostrarFecha = true }
                    Spacer(Modifier.height(Sania.dim.sm))
                    SelectorBotonModal("Hora", hora12(hora)) { mostrarHora = true }
                    Spacer(Modifier.height(Sania.dim.lg))
                    BotonModal("✓ Agendar evaluación", c.navy, c.sobreNavy, lleno = true) { onElegir(fecha, hora) }
                    Spacer(Modifier.height(Sania.dim.sm))
                    BotonModal("Cancelar", c.textoSuave, c.textoSuave, lleno = false) { onCancelar() }
                } else {
                    // Paso 1: dos opciones grandes, apiladas (como la web).
                    Spacer(Modifier.height(Sania.dim.lg))
                    BotonModal("✓ Mismo horario (${hora12(cita.hora)})", c.ok, c.sobreNavy, lleno = true) {
                        onElegir(cita.fecha, cita.hora.take(5))
                    }
                    Spacer(Modifier.height(Sania.dim.sm))
                    BotonModal("📅 Agendar nueva hora", c.navy, c.navy, lleno = false) { eligiendoNueva = true }
                    Spacer(Modifier.height(Sania.dim.sm))
                    BotonModal("Cancelar", c.textoSuave, c.textoSuave, lleno = false) { onCancelar() }
                }
            }
        },
        confirmButton = {},   // botones dentro del cuerpo, full-width, sin amontonar
        containerColor = c.superficie,
    )
}

/** Botón de modal a ancho completo: relleno (lleno=true) o de borde (lleno=false). */
@Composable
private fun BotonModal(texto: String, color: androidx.compose.ui.graphics.Color, fg: androidx.compose.ui.graphics.Color, lleno: Boolean, onClick: () -> Unit) {
    val c = Sania.colors
    Box(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(Sania.shape.md.dp))
            .background(if (lleno) color else c.superficie)
            .let { if (!lleno) it.border(1.dp, c.borde, RoundedCornerShape(Sania.shape.md.dp)) else it }
            .clickable { onClick() }.padding(vertical = 13.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(texto, color = if (lleno) fg else fg, fontSize = 14.sp, fontWeight = FontWeight.Bold)
    }
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