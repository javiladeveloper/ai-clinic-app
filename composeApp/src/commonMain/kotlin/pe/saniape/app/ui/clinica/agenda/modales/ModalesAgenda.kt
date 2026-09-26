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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import pe.saniape.app.data.staff.FlujoClinica

/**
 * Modal de completar Evaluación/Sesión (como la web):
 *  - Evaluación: diagnóstico + opción de derivar a especialidad.
 *  - Sesión: observaciones (procedimientos realizados).
 */
@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
fun ModalCompletar(
    cita: CitaStaff,
    especialidades: List<EspecialidadRef>,
    onCancelar: () -> Unit,
    /** piezas = ids de hallazgos hechos hoy (solo sesión dental; null = no tocar el odontograma). */
    onConfirmar: (observaciones: String?, diagnostico: String?, derivarEspId: String?, piezas: List<String>?) -> Unit,
    flujo: FlujoClinica = FlujoClinica(),
    /** La cita es dental (citaEsDental): la sesión muestra "¿Qué se le hizo hoy?". */
    esDental: Boolean = false,
    /**
     * Odontología: el diagnóstico ya redactado a partir del odontograma que se
     * marcó en el paso previo. El odontólogo lo corrige o lo acepta tal cual.
     */
    diagnosticoInicial: String = "",
    /**
     * Profesionales para "¿Quién atendió?": no-null SOLO cuando hace falta
     * (cita que evalúa, sin profesional, y quien completa no es uno — ver
     * pideProfesionalAlCompletar). El servidor la rechaza sin él (SIN_PROFESIONAL).
     */
    profesionales: List<pe.saniape.app.data.staff.TerapeutaRef>? = null,
    /** Variante de [onConfirmar] con quién atendió (id), para cuando se pidió [profesionales]. */
    onConfirmarConProfesional: ((observaciones: String?, diagnostico: String?, derivarEspId: String?, piezas: List<String>?, terapeutaId: String?) -> Unit)? = null,
    /**
     * Sesión de FISIOTERAPIA (`citaEsFisio`): EVA al entrar/salir, mejorías con los
     * chips de fisio (desde la sesión #2) y dictado 🎤. En false, como siempre.
     */
    esFisio: Boolean = false,
    /** Cierre de la sesión de fisio: observaciones, piezas, mejorías (null = no tocar) y EVA. */
    onConfirmarFisio: ((observaciones: String?, piezas: List<String>?, mejorias: String?, eva: Pair<Int?, Int?>) -> Unit)? = null,
) {
    val c = Sania.colors
    var terapeutaElegido by remember { mutableStateOf<String?>(null) }
    // Aviso de UNA vez al completar la evaluación sin diagnóstico (como la web):
    // el diagnóstico es opcional, pero olvidarlo deja la ficha sin motivo clínico.
    var avisoSinDiagnostico by remember { mutableStateOf(false) }
    // La cita que EVALÚA pide diagnóstico: la Evaluación siempre, y la Consulta
    // cuando el flujo no tiene Evaluación aparte (estética: su "Evaluación" es
    // una Consulta con otro nombre). Antes García la cerraba como sesión sin
    // poder poner diagnóstico (reporte de Jonathan, demo 2026-09-05).
    val esEvaluacion = cita.tipo == "Evaluación" || (cita.tipo == "Consulta" && !flujo.usaEvaluacion)
    var texto by remember { mutableStateOf(diagnosticoInicial) }
    var derivar by remember { mutableStateOf(false) }
    var espElegida by remember { mutableStateOf<EspecialidadRef?>(null) }
    var piezas by remember { mutableStateOf<List<String>>(emptyList()) }
    // true = la lista de piezas cargó; solo entonces se mandan (ver PiezasTratadas).
    var piezasListas by remember { mutableStateOf(false) }
    val conPiezas = esDental && !esEvaluacion && cita.tipo == "Sesión" && cita.pacienteId != null
    // Fisioterapia: solo en citas de Sesión (no en la evaluación) y con su callback.
    val fisioSesion = esFisio && !esEvaluacion && cita.tipo == "Sesión" && onConfirmarFisio != null
    val pideMejorias = fisioSesion && (cita.numeroSesion ?: 0) > 1
    var dolorInicio by remember { mutableStateOf<Int?>(null) }
    var dolorFin by remember { mutableStateOf<Int?>(null) }
    var mejorias by remember { mutableStateOf("") }
    val dictado = if (fisioSesion) pe.saniape.app.ui.clinica.fisio.recordarDictadoCampos { campo, dicho ->
        if (campo == "tecnicas") texto = pe.saniape.app.data.staff.sumarTecnicasDictadas(texto, dicho)
        else mejorias = pe.saniape.app.data.staff.unirDictado(mejorias, dicho)
    } else null

    AlertDialog(
        onDismissRequest = onCancelar,
        title = {
            Column {
                Text(if (esEvaluacion) "🔍 Completar ${flujo.nombreTipo(cita.tipo).lowercase()}" else "🏃 Completar sesión",
                    fontWeight = FontWeight.Bold)
                cita.pacienteNombre?.let {
                    Text(it, color = c.textoSuave, fontSize = Sania.txt.pequeno,
                        modifier = Modifier.padding(top = 2.dp))
                }
            }
        },
        text = {
            // Con EVA y mejorías (fisio) el contenido crece: se puede desplazar.
            Column(if (fisioSesion) Modifier.verticalScroll(rememberScrollState()) else Modifier) {
                if (fisioSesion) {
                    pe.saniape.app.ui.clinica.fisio.BloqueEva(
                        dolorInicio, dolorFin, onInicio = { dolorInicio = it }, onFin = { dolorFin = it },
                    )
                    Spacer(Modifier.height(Sania.dim.lg))
                }
                if (esEvaluacion) {
                    Text("Diagnóstico / Motivo", color = c.textoSuave, fontSize = Sania.txt.mini,
                        fontWeight = FontWeight.Bold, modifier = Modifier.padding(bottom = 6.dp))
                    // Campo con TYPEAHEAD + chips, igual que la web: al escribir sugiere las
                    // patologías de la especialidad que coinciden; tocar una la completa.
                    val espNombre = especialidades.find { it.id == cita.especialidadId }?.nombre
                        ?: especialidades.singleOrNull()?.nombre
                    val chips = pe.saniape.app.ui.clinica.chipsDeEspecialidad(espNombre).tipos
                    pe.saniape.app.ui.clinica.agenda.componentes.DiagnosticoInput(
                        value = texto, onChange = { texto = it }, opciones = chips,
                    )
                    Text("Se guardará en la ficha del paciente.", color = c.textoSuave,
                        fontSize = 11.sp, modifier = Modifier.padding(top = 4.dp))
                } else {
                    // Odontología: de todo lo pendiente del paciente, qué se le hizo
                    // hoy. Lo marcado suma su procedimiento a lo realizado.
                    if (conPiezas) {
                        pe.saniape.app.ui.clinica.odontologia.PiezasTratadas(
                            pacienteId = cita.pacienteId!!, tratamientoId = cita.tratamientoId, sesionId = null,
                            seleccion = piezas, onSeleccion = { piezas = it },
                            onCargado = { piezasListas = it },
                            onProcedimiento = { nombre -> texto = pe.saniape.app.data.staff.sumarTecnica(texto, nombre) },
                        )
                        Spacer(Modifier.height(Sania.dim.lg))
                    }
                    Text("Procedimientos realizados", color = c.textoSuave, fontSize = Sania.txt.mini,
                        fontWeight = FontWeight.Bold, modifier = Modifier.padding(bottom = 6.dp))
                    pe.saniape.app.ui.clinica.agenda.componentes.TecnicasInput(
                        value = texto, onChange = { texto = it },
                    )
                    if (dictado != null && dictado.disponible) {
                        Spacer(Modifier.height(6.dp))
                        pe.saniape.app.ui.clinica.fisio.BotonDictar(dictado, "tecnicas")
                    }
                    if (pideMejorias) {
                        Spacer(Modifier.height(Sania.dim.lg))
                        Text("Mejorías / evolución", color = c.textoSuave, fontSize = Sania.txt.mini,
                            fontWeight = FontWeight.Bold, modifier = Modifier.padding(bottom = 6.dp))
                        OutlinedTextField(
                            colors = pe.saniape.app.ui.clinica.pacientes.coloresCampoForm(),
                            value = mejorias, onValueChange = { mejorias = it },
                            placeholder = { Text("Ej: Menos dolor al caminar…", color = c.textoSuave) },
                            modifier = Modifier.fillMaxWidth(), minLines = 2,
                        )
                        Spacer(Modifier.height(8.dp))
                        pe.saniape.app.ui.clinica.fisio.ChipsMejoriaFisio(mejorias) { mejorias = it }
                        if (dictado != null && dictado.disponible) {
                            Spacer(Modifier.height(8.dp))
                            pe.saniape.app.ui.clinica.fisio.BotonDictar(dictado, "mejorias")
                        }
                    }
                }
                if (esEvaluacion && profesionales != null) {
                    Spacer(Modifier.height(Sania.dim.lg))
                    Text("¿Quién atendió? *", color = c.textoSuave, fontSize = Sania.txt.mini,
                        fontWeight = FontWeight.Bold, modifier = Modifier.padding(bottom = 6.dp))
                    SelectorProfesional(profesionales, terapeutaElegido) { terapeutaElegido = it }
                }
                if (esEvaluacion && avisoSinDiagnostico && texto.isBlank()) {
                    Spacer(Modifier.height(Sania.dim.sm))
                    Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(Sania.shape.sm.dp))
                        .background(c.pendBg).padding(horizontal = 10.dp, vertical = 8.dp)) {
                        Text("⚠ Sin diagnóstico, la ficha queda sin motivo clínico. Escríbelo, o toca de nuevo para completar sin él.",
                            color = c.pend, fontSize = 11.sp)
                    }
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
            val faltaProfesional = esEvaluacion && profesionales != null && terapeutaElegido == null
            Box(
                Modifier.clip(RoundedCornerShape(Sania.shape.md.dp))
                    .background(if (faltaProfesional) c.borde else c.navy)
                    .clickable(enabled = !faltaProfesional) {
                        if (esEvaluacion && texto.isBlank() && !avisoSinDiagnostico) {
                            avisoSinDiagnostico = true
                            return@clickable
                        }
                        val obs = texto.trim().ifBlank { null }
                        val diag = if (esEvaluacion) texto.trim().ifBlank { null } else null
                        val esp = if (esEvaluacion && derivar) espElegida?.id else null
                        val pz = if (conPiezas && piezasListas) piezas else null
                        val conProf = onConfirmarConProfesional
                        val fisio = onConfirmarFisio
                        if (fisioSesion && fisio != null) {
                            fisio(obs, pz, if (pideMejorias) mejorias.trim().ifBlank { null } else null, dolorInicio to dolorFin)
                            return@clickable
                        }
                        if (conProf != null) conProf(obs, diag, esp, pz, if (esEvaluacion) terapeutaElegido else null)
                        else onConfirmar(obs, diag, esp, pz)
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

/**
 * Editar/Reprogramar cita: fecha y hora con pickers nativos, profesional (solo
 * quien gestiona la agenda de todos) y, si es una sesión, el motivo (queda en la
 * sesión, que pasa a "Reprogramada" — igual que el menú ⋯ de la ficha web).
 * El cupo lo valida el servidor: si no hay, el modal sigue abierto para elegir otra hora.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ModalEditarCita(
    cita: CitaStaff,
    flujo: FlujoClinica = FlujoClinica(),
    /** Profesionales para reasignar; null = quien mira no gestiona la agenda de otros. */
    profesionales: List<pe.saniape.app.data.staff.TerapeutaRef>? = null,
    guardando: Boolean = false,
    onCancelar: () -> Unit,
    onGuardar: (fecha: String, hora: String, terapeutaId: String?, motivo: String?) -> Unit,
) {
    val c = Sania.colors
    var fecha by remember { mutableStateOf(cita.fecha) }
    var hora by remember { mutableStateOf(cita.hora.take(5)) }
    var terapeutaId by remember { mutableStateOf(cita.terapeutaId) }
    var motivo by remember { mutableStateOf("") }
    var mostrarFecha by remember { mutableStateOf(false) }
    var mostrarHora by remember { mutableStateOf(false) }
    val esSesion = cita.tipo == "Sesión"
    val cambiaHorario = fecha != cita.fecha || hora != cita.hora.take(5)
    val cambiaProfesional = terapeutaId != null && terapeutaId != cita.terapeutaId
    val hayCambios = cambiaHorario || cambiaProfesional

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
        onDismissRequest = { if (!guardando) onCancelar() },
        title = { Text(if (esSesion) "📅 Reprogramar sesión" else "✏ Editar cita") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                Text("Cita de ${cita.pacienteNombre ?: "paciente"} · ${flujo.nombreTipo(cita.tipo)}",
                    color = c.textoSuave, fontSize = Sania.txt.pequeno)
                if (esSesion) {
                    Text("La sesión vinculada también se mueve y queda como Reprogramada.",
                        color = c.textoSuave, fontSize = 11.sp)
                }
                Spacer(Modifier.height(Sania.dim.md))
                SelectorBotonModal("Fecha", fecha) { mostrarFecha = true }
                Spacer(Modifier.height(Sania.dim.sm))
                SelectorBotonModal("Hora", hora12(hora)) { mostrarHora = true }
                if (profesionales != null && profesionales.isNotEmpty()) {
                    Spacer(Modifier.height(Sania.dim.md))
                    Text("Profesional", color = c.textoSuave, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(4.dp))
                    SelectorProfesional(profesionales, terapeutaId) { terapeutaId = it }
                }
                if (esSesion && cambiaHorario) {
                    Spacer(Modifier.height(Sania.dim.md))
                    Text("Motivo (opcional)", color = c.textoSuave, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    OutlinedTextField(
                        value = motivo, onValueChange = { motivo = it },
                        placeholder = { Text("Ej. El paciente pidió cambiar la fecha…", color = c.textoSuave) },
                        modifier = Modifier.fillMaxWidth(), minLines = 2,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = hayCambios && !guardando,
                onClick = { onGuardar(fecha, hora, terapeutaId.takeIf { cambiaProfesional }, motivo.trim().ifBlank { null }) },
            ) {
                Text(if (guardando) "Guardando…" else "Guardar cambios",
                    color = if (hayCambios && !guardando) c.navy else c.textoSuave, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = { TextButton(onClick = onCancelar, enabled = !guardando) { Text("Cancelar", color = c.textoSuave) } },
        containerColor = c.superficie,
    )
}

/**
 * "¿Quién atendió?" cuando el servidor rechazó el completar con SIN_PROFESIONAL:
 * se elige y se reintenta (el servidor asigna el profesional y completa).
 */
@Composable
fun ModalElegirProfesional(
    cita: CitaStaff,
    profesionales: List<pe.saniape.app.data.staff.TerapeutaRef>,
    onCancelar: () -> Unit,
    onElegir: (String) -> Unit,
) {
    val c = Sania.colors
    var elegido by remember { mutableStateOf<String?>(null) }
    AlertDialog(
        onDismissRequest = onCancelar,
        title = { Text("👤 ¿Quién atendió?", fontWeight = FontWeight.Bold) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                Text("Esta cita de ${cita.pacienteNombre ?: "el paciente"} no tiene profesional asignado. " +
                    "Indica quién la atendió para completarla.", color = c.textoSuave, fontSize = 12.sp)
                Spacer(Modifier.height(Sania.dim.md))
                if (profesionales.isEmpty()) {
                    Text("No hay profesionales activos para elegir.", color = c.error, fontSize = 12.sp)
                } else {
                    SelectorProfesional(profesionales, elegido) { elegido = it }
                }
            }
        },
        confirmButton = {
            TextButton(enabled = elegido != null, onClick = { elegido?.let(onElegir) }) {
                Text("Completar", color = if (elegido != null) c.navy else c.textoSuave, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = { TextButton(onClick = onCancelar) { Text("Cancelar", color = c.textoSuave) } },
        containerColor = c.superficie,
    )
}

/** Lista de profesionales tocables (el elegido resaltado). */
@Composable
private fun SelectorProfesional(
    profesionales: List<pe.saniape.app.data.staff.TerapeutaRef>,
    elegido: String?,
    onElegir: (String) -> Unit,
) {
    val c = Sania.colors
    Column {
        profesionales.forEach { p ->
            val activo = p.id == elegido
            Box(
                Modifier.fillMaxWidth().padding(vertical = 3.dp)
                    .clip(RoundedCornerShape(Sania.shape.sm.dp))
                    .background(if (activo) c.navy else c.superficie)
                    .border(1.dp, if (activo) c.navy else c.borde, RoundedCornerShape(Sania.shape.sm.dp))
                    .clickable { onElegir(p.id) }
                    .padding(horizontal = 12.dp, vertical = 10.dp),
            ) {
                Text(p.nombre, color = if (activo) c.sobreNavy else c.texto, fontSize = Sania.txt.pequeno,
                    fontWeight = if (activo) FontWeight.Bold else FontWeight.Normal)
            }
        }
    }
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