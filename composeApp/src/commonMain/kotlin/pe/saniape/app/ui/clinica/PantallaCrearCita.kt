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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import pe.saniape.app.data.staff.AgendaRepo
import pe.saniape.app.data.staff.ContextoStaff
import pe.saniape.app.data.staff.EspecialidadRef
import pe.saniape.app.data.staff.EstadoProfesional
import pe.saniape.app.data.staff.RefNombre
import pe.saniape.app.data.staff.TerapeutaRef
import pe.saniape.app.data.staff.TratamientoRef
import pe.saniape.app.ui.ManejarAtras
import pe.saniape.app.ui.theme.Sania

private val TIPOS = listOf("Consulta", "Evaluación", "Sesión")

/** Info visual de cada tipo de cita (icono + descripción), como las tarjetas de la web. */
private data class TipoInfo(val valor: String, val icono: String, val desc: String)
private val TIPOS_INFO = listOf(
    TipoInfo("Consulta", "💬", "El paciente explica su caso"),
    TipoInfo("Evaluación", "🔍", "Se evalúa y diagnostica"),
    TipoInfo("Sesión", "🏃", "Sesión de tratamiento"),
)

/**
 * Pre-llenado del formulario (para "→ Evaluación"): tipo, paciente, fecha/hora y
 * profesional ya seleccionados. [citaOrigenId] = consulta a completar al guardar
 * (flujo Consulta → Evaluación, igual que la web).
 */
data class PrefillCita(
    val tipo: String,
    val pacienteId: String?,
    val pacienteNombre: String?,
    val fecha: String,
    val hora: String,
    val terapeutaId: String?,
    val citaOrigenId: String? = null,
)

/**
 * Formulario de crear cita (igual que CitaForm de la web): tipo, paciente,
 * tratamiento (si Sesión), fecha, hora (pickers nativos), profesional, costo, notas.
 * Respeta el scope: si es profesional vinculado, el profesional queda fijado a él.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PantallaCrearCita(
    ctx: ContextoStaff,
    fechaInicial: String,
    onListo: () -> Unit,
    onCancelar: () -> Unit,
    prefill: PrefillCita? = null,
) {
    val c = Sania.colors
    val scope = rememberCoroutineScope()

    // El botón/gesto "Atrás" del sistema cierra el formulario (app nativa).
    ManejarAtras(activo = true, onAtras = onCancelar)

    var pacientes by remember { mutableStateOf<List<RefNombre>>(emptyList()) }
    var terapeutas by remember { mutableStateOf<List<TerapeutaRef>>(emptyList()) }
    var especialidadesClinica by remember { mutableStateOf<List<EspecialidadRef>>(emptyList()) }
    var tratamientos by remember { mutableStateOf<List<TratamientoRef>>(emptyList()) }
    var precioConsulta by remember { mutableStateOf(0.0) }
    var precioEvaluacion by remember { mutableStateOf(40.0) }

    var tipo by remember { mutableStateOf(prefill?.tipo ?: "Consulta") }
    var paciente by remember { mutableStateOf<RefNombre?>(null) }
    var tratamiento by remember { mutableStateOf<TratamientoRef?>(null) }
    var terapeuta by remember { mutableStateOf<TerapeutaRef?>(null) }
    var especialidad by remember { mutableStateOf<EspecialidadRef?>(null) }
    var fecha by remember { mutableStateOf(prefill?.fecha ?: fechaInicial) }
    var hora by remember { mutableStateOf(prefill?.hora ?: "09:00") }
    var costo by remember { mutableStateOf("0") }
    var diagnostico by remember { mutableStateOf("") }
    var notas by remember { mutableStateOf("") }
    var esRegularizacion by remember { mutableStateOf(false) }   // "la cita ya ocurrió"

    var mostrarFecha by remember { mutableStateOf(false) }
    var mostrarHora by remember { mutableStateOf(false) }
    var mostrarHorarios by remember { mutableStateOf(false) }    // modal "ver horarios" de todos
    var guardando by remember { mutableStateOf(false) }
    var mensaje by remember { mutableStateOf<String?>(null) }

    // Clínica multi-especialidad → mostrar selector que filtra los profesionales.
    val multiEspecialidad = especialidadesClinica.size > 1
    // Profesionales filtrados por la especialidad elegida (o todos si no se eligió).
    val terapeutasFiltrados = especialidad?.let { e ->
        terapeutas.filter { e.id in it.especialidadIds }
    } ?: terapeutas

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
            especialidadesClinica = runCatching { AgendaRepo.especialidades() }.getOrDefault(emptyList())
            val (pc, pe) = AgendaRepo.precios()
            precioConsulta = pc; precioEvaluacion = pe
            costo = if (prefill?.tipo == "Evaluación") pe.toString() else pc.toString()
            // Resolver referencias del pre-llenado (→ Evaluación).
            prefill?.let { pf ->
                paciente = pf.pacienteId?.let { id ->
                    pacientes.find { it.id == id } ?: pf.pacienteNombre?.let { RefNombre(id, it) }
                }
                val ter = pf.terapeutaId?.let { id -> terapeutas.find { it.id == id } }
                terapeuta = ter
                // Auto-rellenar la especialidad del profesional prefijado (si tiene una sola).
                ter?.especialidadIds?.singleOrNull()?.let { espId ->
                    especialidad = especialidadesClinica.find { it.id == espId }
                }
            }
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
            is24Hour = false,   // selector en formato 12h (AM/PM); se guarda igual en 24h
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

    // Modal "ver horarios": disponibilidad de todos los profesionales para fecha/hora.
    if (mostrarHorarios) {
        ModalVerHorarios(
            fecha = fecha, hora = hora,
            duracion = if (tipo == "Consulta") 15 else 60,
            soloTerapeutaIds = especialidad?.let { e -> terapeutas.filter { e.id in it.especialidadIds }.map { it.id } },
            onElegir = { id -> terapeuta = terapeutas.find { it.id == id }; mostrarHorarios = false },
            onCerrar = { mostrarHorarios = false },
        )
    }

    Surface(color = c.fondo, modifier = Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            // Toolbar nativa: flecha ← circular + título (en vez del texto "Volver" suelto).
            Row(
                Modifier.fillMaxWidth().background(c.navyDark)
                    .padding(horizontal = Sania.dim.lg, vertical = Sania.dim.lg),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    Modifier.size(38.dp).clip(CircleShape)
                        .background(c.sobreNavy.copy(alpha = 0.15f))
                        .clickable { onCancelar() },
                    contentAlignment = Alignment.Center,
                ) { Text("←", color = c.sobreNavy, fontSize = 20.sp, fontWeight = FontWeight.Bold) }
                Spacer(Modifier.width(Sania.dim.md))
                Text(if (prefill != null) "Nueva evaluación" else "Nueva cita",
                    color = c.sobreNavy, fontSize = Sania.txt.subtitulo, fontWeight = FontWeight.Bold)
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

                // Tipo de cita — tarjetas con icono + descripción (más llamativas)
                Etiqueta("Tipo de cita")
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TIPOS_INFO.forEach { ti ->
                        TarjetaTipo(
                            info = ti, activo = tipo == ti.valor,
                            modifier = Modifier.weight(1f),
                        ) { tipo = ti.valor }
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

                // Especialidad (solo si la clínica tiene más de una). Filtra los profesionales.
                if (multiEspecialidad && ctx.miTerapeutaId == null) {
                    Spacer(Modifier.height(Sania.dim.md))
                    Etiqueta("Especialidad")
                    SelectorLista(
                        items = especialidadesClinica, elegido = especialidad, etiqueta = { it.nombre },
                        onElegir = { esp ->
                            especialidad = esp
                            // Si el profesional elegido ya no pertenece a la especialidad, lo quitamos.
                            terapeuta?.let { t -> if (esp.id !in t.especialidadIds) terapeuta = null }
                        },
                        placeholder = "Todas las especialidades",
                    )
                }

                Spacer(Modifier.height(Sania.dim.md))
                // Profesional (fijado si es profesional vinculado)
                Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween, Alignment.CenterVertically) {
                    Etiqueta("Profesional")
                    if (ctx.miTerapeutaId == null) {
                        Text("Ver horarios", color = c.navy, fontSize = 11.sp, fontWeight = FontWeight.Bold,
                            modifier = Modifier.clickable { mostrarHorarios = true })
                    }
                }
                if (ctx.miTerapeutaId != null) {
                    val miNombre = terapeutas.find { it.id == ctx.miTerapeutaId }?.nombre ?: "Tú"
                    SelectorBoton("$miNombre (tú)", bloqueado = true) {}
                } else {
                    SelectorLista(
                        items = terapeutasFiltrados, elegido = terapeuta, etiqueta = { it.nombre },
                        onElegir = { t ->
                            terapeuta = t
                            // Al elegir profesional, auto-rellenar su especialidad si tiene UNA sola
                            // (igual que la web). Así no queda en "Todas" cuando ya hay profesional.
                            if (especialidad == null) {
                                t.especialidadIds.singleOrNull()?.let { espId ->
                                    especialidad = especialidadesClinica.find { it.id == espId }
                                }
                            }
                        },
                        placeholder = "Sin asignar",
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
                            // Flujo → Evaluación: completar primero la consulta origen
                            // (igual que handleEvalSave de la web), luego crear la cita.
                            prefill?.citaOrigenId?.let { origenId ->
                                runCatching { AgendaRepo.completar(origenId) }
                            }
                            val terId = if (ctx.miTerapeutaId != null) ctx.miTerapeutaId else terapeuta?.id
                            // Especialidad: la elegida, o la del profesional si solo tiene una.
                            val espId = especialidad?.id
                                ?: terapeuta?.especialidadIds?.singleOrNull()
                            val ok = AgendaRepo.crearCita(
                                pacienteId = p.id, tipo = tipo, fecha = fecha, hora = hora,
                                terapeutaId = terId, tratamientoId = tratamiento?.id,
                                costo = costo.toDoubleOrNull() ?: 0.0,
                                duracion = if (tipo == "Consulta") 15 else 60,
                                notas = notas.ifBlank { null },
                                especialidadId = espId,
                                diagnostico = if (tipo == "Evaluación") diagnostico.ifBlank { null } else null,
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
private fun TarjetaTipo(info: TipoInfo, activo: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val c = Sania.colors
    val acento = when (info.valor) {
        "Evaluación" -> c.info
        "Sesión" -> c.ok
        else -> c.navy
    }
    Column(
        modifier.clip(RoundedCornerShape(Sania.shape.md.dp))
            .background(if (activo) acento.copy(alpha = 0.12f) else c.superficie)
            .border(if (activo) 2.dp else 1.dp, if (activo) acento else c.borde, RoundedCornerShape(Sania.shape.md.dp))
            .clickable { onClick() }.padding(vertical = 12.dp, horizontal = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(info.icono, fontSize = 22.sp)
        Spacer(Modifier.height(4.dp))
        Text(info.valor, color = if (activo) acento else c.texto, fontSize = 13.sp, fontWeight = FontWeight.Bold)
        Text(info.desc, color = c.textoSuave, fontSize = 9.sp,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            modifier = Modifier.padding(top = 2.dp))
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

/**
 * Modal que muestra la disponibilidad de TODOS los profesionales para una fecha/hora.
 * Reusa la misma regla de la web (DisponibilidadRepo). Tocar uno lo selecciona.
 */
@Composable
private fun ModalVerHorarios(
    fecha: String, hora: String, duracion: Int,
    soloTerapeutaIds: List<String>?,
    onElegir: (String) -> Unit, onCerrar: () -> Unit,
) {
    val c = Sania.colors
    var estado by remember { mutableStateOf<List<EstadoProfesional>?>(null) }
    LaunchedEffect(fecha, hora, duracion) {
        estado = runCatching {
            AgendaRepo.disponibilidadProfesionales(fecha, hora, duracion, soloTerapeutaIds)
        }.getOrDefault(emptyList())
    }

    // Diálogo real (ventana propia) — así sí queda por encima del formulario.
    Dialog(onDismissRequest = onCerrar) {
        Column(
            Modifier.fillMaxWidth().heightIn(max = 520.dp).clip(RoundedCornerShape(Sania.shape.lg.dp))
                .background(c.superficie).verticalScroll(rememberScrollState()).padding(Sania.dim.xl),
        ) {
            Text("Disponibilidad — ${hora.take(5)}", color = c.texto,
                fontSize = Sania.txt.subtitulo, fontWeight = FontWeight.Bold)
            Text(fecha, color = c.textoSuave, fontSize = Sania.txt.pequeno,
                modifier = Modifier.padding(bottom = Sania.dim.md))

            when {
                estado == null -> Box(Modifier.fillMaxWidth().padding(Sania.dim.lg), Alignment.Center) {
                    CircularProgressIndicator(color = c.navy, strokeWidth = 2.dp)
                }
                estado!!.isEmpty() -> Text("No hay profesionales para mostrar.",
                    color = c.textoSuave, fontSize = Sania.txt.cuerpo)
                else -> Column {
                    estado!!.forEach { p ->
                        Row(
                            Modifier.fillMaxWidth().clip(RoundedCornerShape(Sania.shape.sm.dp))
                                .clickable { onElegir(p.terapeutaId) }
                                .padding(vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(if (p.libre) "🟢" else "🟡", fontSize = 14.sp)
                            Spacer(Modifier.width(8.dp))
                            Column(Modifier.weight(1f)) {
                                Text(p.nombre, color = c.texto, fontSize = Sania.txt.cuerpo, fontWeight = FontWeight.Bold)
                                Text(p.etiqueta, color = c.textoSuave, fontSize = 12.sp)
                            }
                            Text("Elegir →", color = c.navy, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }

            Spacer(Modifier.height(Sania.dim.md))
            Text("Cerrar", color = c.textoSuave, fontSize = Sania.txt.cuerpo, fontWeight = FontWeight.Bold,
                modifier = Modifier.fillMaxWidth().clickable { onCerrar() }.padding(vertical = 8.dp),
                textAlign = androidx.compose.ui.text.style.TextAlign.Center)
        }
    }
}