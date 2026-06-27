package pe.saniape.app.ui.clinica

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import pe.saniape.app.data.staff.AgendaRepo
import pe.saniape.app.data.staff.ContextoStaff
import pe.saniape.app.data.staff.RefNombre
import pe.saniape.app.data.staff.TratamientoRef
import pe.saniape.app.ui.theme.Sania

private val TIPOS = listOf("Consulta", "Evaluación", "Sesión")

/**
 * Formulario de crear cita (igual que CitaForm de la web): tipo, paciente,
 * tratamiento (si Sesión), fecha, hora (pickers nativos), profesional, costo, notas.
 * Respeta el scope: si es profesional vinculado, el profesional queda fijado a él.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PantallaCrearCita(ctx: ContextoStaff, fechaInicial: String, onListo: () -> Unit, onCancelar: () -> Unit) {
    val c = Sania.colors
    val scope = rememberCoroutineScope()

    var pacientes by remember { mutableStateOf<List<RefNombre>>(emptyList()) }
    var terapeutas by remember { mutableStateOf<List<RefNombre>>(emptyList()) }
    var tratamientos by remember { mutableStateOf<List<TratamientoRef>>(emptyList()) }
    var precioConsulta by remember { mutableStateOf(0.0) }
    var precioEvaluacion by remember { mutableStateOf(40.0) }

    var tipo by remember { mutableStateOf("Consulta") }
    var paciente by remember { mutableStateOf<RefNombre?>(null) }
    var tratamiento by remember { mutableStateOf<TratamientoRef?>(null) }
    var terapeuta by remember { mutableStateOf<RefNombre?>(null) }
    var fecha by remember { mutableStateOf(fechaInicial) }
    var hora by remember { mutableStateOf("09:00") }
    var costo by remember { mutableStateOf("0") }
    var diagnostico by remember { mutableStateOf("") }
    var notas by remember { mutableStateOf("") }
    var esRegularizacion by remember { mutableStateOf(false) }   // "la cita ya ocurrió"

    var mostrarFecha by remember { mutableStateOf(false) }
    var mostrarHora by remember { mutableStateOf(false) }
    var guardando by remember { mutableStateOf(false) }
    var mensaje by remember { mutableStateOf<String?>(null) }

    // Disponibilidad en vivo (igual que la web): bloquea si no disponible (futura),
    // advierte si hay solapamiento. Se recalcula al cambiar profesional/fecha/hora.
    var disponibilidad by remember { mutableStateOf<pe.saniape.app.data.staff.Disponibilidad?>(null) }
    LaunchedEffect(terapeuta?.id, fecha, hora, tipo) {
        val terId = terapeuta?.id ?: ctx.miTerapeutaId
        disponibilidad = if (terId != null) {
            runCatching {
                pe.saniape.app.data.staff.DisponibilidadRepo.verificar(
                    terId, fecha, hora, if (tipo == "Consulta") 15 else 60, pacienteId = paciente?.id,
                )
            }.getOrNull()
        } else null
    }

    LaunchedEffect(Unit) {
        try {
            pacientes = AgendaRepo.pacientesParaSelector()
            terapeutas = AgendaRepo.terapeutasActivos()
            val (pc, pe) = AgendaRepo.precios()
            precioConsulta = pc; precioEvaluacion = pe
            costo = pc.toString()
        } catch (_: Exception) {}
    }

    // Costo por defecto según tipo
    LaunchedEffect(tipo) {
        costo = when (tipo) {
            "Evaluación" -> precioEvaluacion.toString()
            "Consulta" -> precioConsulta.toString()
            else -> "0"
        }
    }
    // Tratamientos del paciente (para tipo Sesión)
    LaunchedEffect(paciente?.id) {
        val p = paciente
        tratamientos = if (p != null) try { AgendaRepo.tratamientosActivos(p.id) } catch (_: Exception) { emptyList() } else emptyList()
    }

    // Pickers nativos
    if (mostrarFecha) {
        val estado = rememberDatePickerState()
        DatePickerDialog(
            onDismissRequest = { mostrarFecha = false },
            confirmButton = {
                TextButton(onClick = {
                    estado.selectedDateMillis?.let { fecha = millisISO(it) }
                    mostrarFecha = false
                }) { Text("Aceptar", color = c.navy) }
            },
            dismissButton = { TextButton(onClick = { mostrarFecha = false }) { Text("Cancelar", color = c.textoSuave) } },
        ) { DatePicker(state = estado) }
    }
    if (mostrarHora) {
        val partes = hora.split(":")
        val estado = rememberTimePickerState(
            initialHour = partes.getOrNull(0)?.toIntOrNull() ?: 9,
            initialMinute = partes.getOrNull(1)?.toIntOrNull() ?: 0,
            is24Hour = true,
        )
        DatePickerDialog(
            onDismissRequest = { mostrarHora = false },
            confirmButton = {
                TextButton(onClick = {
                    hora = "${estado.hour.toString().padStart(2, '0')}:${estado.minute.toString().padStart(2, '0')}"
                    mostrarHora = false
                }) { Text("Aceptar", color = c.navy) }
            },
            dismissButton = { TextButton(onClick = { mostrarHora = false }) { Text("Cancelar", color = c.textoSuave) } },
        ) { Box(Modifier.fillMaxWidth().padding(Sania.dim.lg), Alignment.Center) { TimePicker(state = estado) } }
    }

    Surface(color = c.fondo, modifier = Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            Column(Modifier.fillMaxWidth().background(c.navyDark)
                .padding(horizontal = Sania.dim.xl, vertical = Sania.dim.lg)) {
                Text("← Volver", color = c.sobreNavy, fontSize = Sania.txt.pequeno,
                    fontWeight = FontWeight.Bold, modifier = Modifier.clickable { onCancelar() })
                Spacer(Modifier.height(Sania.dim.sm))
                Text("Nueva cita", color = c.sobreNavy, fontSize = Sania.txt.subtitulo, fontWeight = FontWeight.Bold)
            }

            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(Sania.dim.xl)) {
                mensaje?.let {
                    Text("⚠ $it", color = c.error, fontSize = Sania.txt.pequeno,
                        modifier = Modifier.fillMaxWidth().padding(bottom = Sania.dim.md))
                }

                // Aviso de disponibilidad (bloqueante en rojo / advertencia en ámbar).
                disponibilidad?.takeIf { it.motivo != null }?.let { d ->
                    val color = if (!d.disponible) c.error else c.pend
                    val bg = if (!d.disponible) c.errorBg else c.pendBg
                    Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(Sania.shape.sm.dp))
                        .background(bg).padding(Sania.dim.md).padding(bottom = 0.dp)) {
                        Text("${if (!d.disponible) "⛔" else "⚠"} ${d.motivo}", color = color, fontSize = 12.sp)
                    }
                    Spacer(Modifier.height(Sania.dim.sm))
                }

                // Tipo
                Etiqueta("Tipo de cita")
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TIPOS.forEach { t ->
                        ChipSel(t, tipo == t, Modifier.weight(1f)) { tipo = t }
                    }
                }
                Spacer(Modifier.height(Sania.dim.md))

                // Paciente
                Etiqueta("Paciente")
                SelectorLista(
                    items = pacientes, elegido = paciente, etiqueta = { it.nombre },
                    onElegir = { paciente = it; terapeuta = null; tratamiento = null },
                    placeholder = "Elegir paciente",
                )

                // Tratamiento (solo Sesión)
                if (tipo == "Sesión" && tratamientos.isNotEmpty()) {
                    Spacer(Modifier.height(Sania.dim.md))
                    Etiqueta("Tratamiento")
                    SelectorLista(
                        items = tratamientos, elegido = tratamiento,
                        etiqueta = { "${it.procedimiento} — ${it.modalidad}" },
                        onElegir = { tr -> tratamiento = tr; tr.terapeutaId?.let { tid -> terapeuta = terapeutas.find { it.id == tid } } },
                        placeholder = "Elegir tratamiento",
                    )
                }

                Spacer(Modifier.height(Sania.dim.md))
                // Fecha y hora (pickers)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Column(Modifier.weight(1f)) {
                        Etiqueta("Fecha")
                        SelectorBoton(fecha) { mostrarFecha = true }
                    }
                    Column(Modifier.weight(1f)) {
                        Etiqueta("Hora")
                        SelectorBoton(hora) { mostrarHora = true }
                    }
                }
                // Regularización: "esta cita ya ocurrió" (salta validación de disponibilidad)
                Row(verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(top = Sania.dim.sm).clickable { esRegularizacion = !esRegularizacion }) {
                    Text(if (esRegularizacion) "☑" else "☐", fontSize = 18.sp, color = c.navy)
                    Spacer(Modifier.width(6.dp))
                    Text("Esta cita ya ocurrió (la registro después)", color = c.textoSuave, fontSize = 12.sp)
                }

                Spacer(Modifier.height(Sania.dim.md))
                // Profesional (fijado si es profesional vinculado)
                Etiqueta("Profesional")
                if (ctx.miTerapeutaId != null) {
                    val miNombre = terapeutas.find { it.id == ctx.miTerapeutaId }?.nombre ?: "Tú"
                    SelectorBoton("$miNombre (tú)", bloqueado = true) {}
                } else {
                    SelectorLista(
                        items = terapeutas, elegido = terapeuta, etiqueta = { it.nombre },
                        onElegir = { terapeuta = it }, placeholder = "Sin asignar",
                    )
                }

                // Costo (si tiene permiso pagos)
                // Diagnóstico (Evaluación) — opcional al agendar.
                if (tipo == "Evaluación") {
                    Spacer(Modifier.height(Sania.dim.md))
                    Etiqueta("Diagnóstico / Motivo (opcional)")
                    OutlinedTextField(
                        value = diagnostico, onValueChange = { diagnostico = it },
                        placeholder = { Text("Se puede completar luego", color = c.textoSuave) },
                        modifier = Modifier.fillMaxWidth(), minLines = 2,
                    )
                }

                if (ctx.puede("pagos") && tipo != "Sesión") {
                    val precioBase = if (tipo == "Evaluación") precioEvaluacion else precioConsulta
                    Spacer(Modifier.height(Sania.dim.md))
                    Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween, Alignment.CenterVertically) {
                        Etiqueta("Costo (S/)")
                        if ((costo.toDoubleOrNull() ?: 0.0) != precioBase) {
                            Text("Restablecer", color = c.navy, fontSize = 11.sp, fontWeight = FontWeight.Bold,
                                modifier = Modifier.clickable { costo = precioBase.toString() })
                        }
                    }
                    OutlinedTextField(
                        value = costo, onValueChange = { costo = it.filter { ch -> ch.isDigit() || ch == '.' } },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }

                Spacer(Modifier.height(Sania.dim.md))
                Etiqueta("Observaciones (opcional)")
                OutlinedTextField(
                    value = notas, onValueChange = { notas = it },
                    placeholder = { Text("Notas…", color = c.textoSuave) },
                    modifier = Modifier.fillMaxWidth(), minLines = 2,
                )

                Spacer(Modifier.height(Sania.dim.lg))
                Button(
                    onClick = {
                        if (guardando) return@Button
                        mensaje = null
                        val p = paciente ?: run { mensaje = "Elige un paciente"; return@Button }
                        if (tipo == "Sesión" && tratamiento == null && tratamientos.isNotEmpty()) {
                            mensaje = "Elige el tratamiento"; return@Button
                        }
                        // Disponibilidad bloquea solo si NO es regularización (igual que la web).
                        val d = disponibilidad
                        if (d != null && !d.disponible && !esRegularizacion) {
                            mensaje = d.motivo ?: "El horario no está disponible"; return@Button
                        }
                        guardando = true
                        scope.launch {
                            val terId = if (ctx.miTerapeutaId != null) ctx.miTerapeutaId else terapeuta?.id
                            val ok = AgendaRepo.crearCita(
                                pacienteId = p.id, tipo = tipo, fecha = fecha, hora = hora,
                                terapeutaId = terId, tratamientoId = tratamiento?.id,
                                costo = costo.toDoubleOrNull() ?: 0.0,
                                duracion = if (tipo == "Consulta") 15 else 60,
                                notas = notas.ifBlank { null },
                            )
                            guardando = false
                            if (ok) onListo() else mensaje = "No se pudo agendar. Intenta de nuevo."
                        }
                    },
                    enabled = !guardando,
                    shape = RoundedCornerShape(Sania.shape.md.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = c.navy, contentColor = c.sobreNavy),
                    modifier = Modifier.fillMaxWidth().height(Sania.dim.boton),
                ) {
                    if (guardando) CircularProgressIndicator(color = c.sobreNavy, strokeWidth = 2.dp,
                        modifier = Modifier.height(20.dp).padding(end = 8.dp))
                    Text("Guardar cita", fontWeight = FontWeight.Bold)
                }
                Spacer(Modifier.height(Sania.dim.xxl))
            }
        }
    }
}

@Composable
private fun Etiqueta(t: String) {
    Text(t, color = Sania.colors.textoSuave, fontSize = Sania.txt.mini, fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(bottom = 4.dp))
}

@Composable
private fun ChipSel(texto: String, activo: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val c = Sania.colors
    Box(
        modifier.clip(RoundedCornerShape(Sania.shape.sm.dp))
            .background(if (activo) c.navy else c.superficie)
            .border(1.dp, if (activo) c.navy else c.borde, RoundedCornerShape(Sania.shape.sm.dp))
            .clickable { onClick() }.padding(vertical = 10.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(texto, color = if (activo) c.sobreNavy else c.texto, fontSize = 13.sp,
            fontWeight = if (activo) FontWeight.Bold else FontWeight.Normal)
    }
}

@Composable
private fun SelectorBoton(valor: String, bloqueado: Boolean = false, onClick: () -> Unit) {
    val c = Sania.colors
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(Sania.shape.sm.dp))
            .background(if (bloqueado) c.chipBg else c.superficie)
            .border(1.dp, c.borde, RoundedCornerShape(Sania.shape.sm.dp))
            .clickable(enabled = !bloqueado) { onClick() }
            .padding(horizontal = 14.dp, vertical = 14.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(valor, color = c.texto, fontSize = Sania.txt.cuerpo)
        if (!bloqueado) Text("▾", color = c.navy)
        else Text("🔒", color = c.textoSuave, fontSize = 12.sp)
    }
}

@Composable
private fun <T> SelectorLista(
    items: List<T>, elegido: T?, etiqueta: (T) -> String, onElegir: (T) -> Unit, placeholder: String,
) {
    val c = Sania.colors
    var abierto by remember { mutableStateOf(false) }
    Column {
        SelectorBoton(elegido?.let(etiqueta) ?: placeholder) { abierto = !abierto }
        if (abierto) {
            Column(Modifier.fillMaxWidth().padding(top = 4.dp)
                .clip(RoundedCornerShape(Sania.shape.sm.dp)).background(c.superficie)
                .border(1.dp, c.borde, RoundedCornerShape(Sania.shape.sm.dp))) {
                items.take(50).forEach { item ->
                    Text(etiqueta(item), color = c.texto, fontSize = Sania.txt.cuerpo,
                        modifier = Modifier.fillMaxWidth().clickable { onElegir(item); abierto = false }
                            .padding(horizontal = 14.dp, vertical = 12.dp))
                }
            }
        }
    }
}

private fun millisISO(millis: Long): String {
    val d = Instant.fromEpochMilliseconds(millis).toLocalDateTime(TimeZone.UTC).date
    return "${d.year}-${d.monthNumber.toString().padStart(2, '0')}-${d.dayOfMonth.toString().padStart(2, '0')}"
}