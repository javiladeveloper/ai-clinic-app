package pe.saniape.app.ui.clinica.pacientes

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlinx.datetime.toLocalDateTime
import pe.saniape.app.data.staff.EspecialidadClinica
import pe.saniape.app.data.staff.EvaluacionRef
import pe.saniape.app.data.staff.PacientesRepo
import pe.saniape.app.data.staff.ProcedimientoRef
import pe.saniape.app.data.staff.TarifarioRef
import pe.saniape.app.data.staff.TerapeutaConEsp
import pe.saniape.app.ui.theme.Sania

/** Resultado del form de tratamiento (lo que se envía al endpoint crear). */
data class TratamientoNuevo(
    val procedimientoId: String,
    val terapeutaId: String?,
    val modalidad: String,
    val totalSesiones: Int?,
    val precioPaquete: Double?,
    val precioPorSesion: Double?,
    val precioAcordado: Double?,
    val diagnostico: String?,
    val citaOrigenId: String?,
    val medicacion: String?,
    val proximoControl: String?,
)

/**
 * Modal de crear tratamiento (igual que TratamientoForm web): servicio → especialidad
 * decide si usa sesiones. Si usa sesiones: modalidad Paquete (N+precio) o Sesión suelta
 * (precio/sesión). Si es Consulta (especialidad sin sesiones): solo costo de la consulta.
 * El profesional puede venir fijado (profesional vinculado).
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ModalCrearTratamiento(
    pacienteId: String,
    miTerapeutaId: String?,
    diagnosticoPrevio: String?,   // del paciente/evaluación, para precargar
    onCancelar: () -> Unit,
    onGuardar: (TratamientoNuevo) -> Unit,
) {
    val c = Sania.colors
    var procedimientos by remember { mutableStateOf<List<ProcedimientoRef>>(emptyList()) }
    var terapeutas by remember { mutableStateOf<List<TerapeutaConEsp>>(emptyList()) }
    var especialidades by remember { mutableStateOf<List<EspecialidadClinica>>(emptyList()) }
    var evaluaciones by remember { mutableStateOf<List<EvaluacionRef>>(emptyList()) }
    var especialidad by remember { mutableStateOf<EspecialidadClinica?>(null) }
    var proc by remember { mutableStateOf<ProcedimientoRef?>(null) }
    var terapeuta by remember { mutableStateOf<TerapeutaConEsp?>(null) }
    var evaluacion by remember { mutableStateOf<EvaluacionRef?>(null) }
    var modalidad by remember { mutableStateOf("Paquete") }
    var totalSesiones by remember { mutableStateOf("10") }
    var precioPaquete by remember { mutableStateOf("") }
    var precioPorSesion by remember { mutableStateOf("") }
    var precioAcordado by remember { mutableStateOf("") }
    var diagnostico by remember { mutableStateOf(diagnosticoPrevio ?: "") }
    // medicación y próximo control: no se piden al crear (se llenan al editar tras atender).

    LaunchedEffect(pacienteId) {
        procedimientos = runCatching { PacientesRepo.procedimientos() }.getOrDefault(emptyList())
        terapeutas = runCatching { PacientesRepo.terapeutasConEspecialidad() }.getOrDefault(emptyList())
        val esps = runCatching { PacientesRepo.especialidadesClinica() }.getOrDefault(emptyList())
        val ters = runCatching { PacientesRepo.terapeutasConEspecialidad() }.getOrDefault(terapeutas)
        especialidades = esps
        terapeutas = ters
        evaluaciones = runCatching { PacientesRepo.evaluacionesDe(pacienteId) }.getOrDefault(emptyList())
        // Si la clínica tiene 1 sola especialidad, se autoselecciona.
        if (especialidad == null && esps.size == 1) especialidad = esps.first()
        // Profesional vinculado: fijar su(s) especialidad(es) si tiene una sola.
        if (miTerapeutaId != null) {
            val miTer = ters.find { it.id == miTerapeutaId }
            terapeuta = miTer
            miTer?.especialidadIds?.singleOrNull()?.let { espId ->
                especialidad = esps.find { it.id == espId } ?: especialidad
            }
        }
    }

    // Profesionales de la especialidad elegida (o todos si no hay especialidad).
    val terapeutasFiltrados = especialidad?.let { e ->
        terapeutas.filter { e.id in it.especialidadIds }
    } ?: terapeutas
    // Servicios de la especialidad elegida (o del profesional, o todos).
    val espId = especialidad?.id
    val terId = if (miTerapeutaId != null) miTerapeutaId else terapeuta?.id
    val espsDelProf = terapeutas.find { it.id == terId }?.especialidadIds ?: emptyList()
    val procsVisibles = procedimientos.filter { p ->
        when {
            espId != null -> p.especialidadId == null || p.especialidadId == espId
            terId != null -> p.especialidadId == null || p.especialidadId in espsDelProf
            else -> true
        }
    }

    // Al elegir servicio: autocompletar precios + tarifario (si hay).
    LaunchedEffect(proc?.id) {
        proc?.let { p ->
            precioPorSesion = p.precio.toString()
            val tar10 = p.tarifarios.firstOrNull { it.cantidadSesiones == 10 } ?: p.tarifarios.firstOrNull()
            if (tar10 != null) {
                totalSesiones = tar10.cantidadSesiones.toString()
                precioPaquete = tar10.precioTotal.toString()
            } else {
                precioPaquete = p.precioPaquete?.toString() ?: ""
                totalSesiones = "10"
            }
        }
    }

    val esConsulta = proc?.usaSesiones == false
    val usaSesiones = proc != null && !esConsulta
    val puedeCrear = proc != null

    Dialog(onDismissRequest = onCancelar, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Column(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp).heightIn(max = 720.dp)
                .clip(RoundedCornerShape(Sania.shape.lg.dp)).background(c.fondo),
        ) {
            // ── Header navy (el "negro" de la marca) ──────────────────────
            Column(Modifier.fillMaxWidth().background(c.navyDark).padding(horizontal = 18.dp, vertical = 16.dp)) {
                Text("Nuevo tratamiento", color = c.sobreNavy, fontSize = 19.sp, fontWeight = FontWeight.Bold)
                Text(
                    when {
                        esConsulta -> "Consulta médica · sin sesiones"
                        usaSesiones -> "Plan por sesiones"
                        else -> "Elige el servicio para empezar"
                    },
                    color = c.sobreNavy.copy(alpha = 0.7f), fontSize = 12.sp, modifier = Modifier.padding(top = 2.dp),
                )
            }

            // ── Cuerpo scroll ─────────────────────────────────────────────
            Column(
                Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 14.dp),
            ) {
                // Bloque 1 · ATENCIÓN ─────────────────────────────────────
                Tarjeta(titulo = "Atención", icono = "🩺") {
                    // ¿Nació de una evaluación? (primero — autocompleta especialidad y médico)
                    if (evaluaciones.isNotEmpty()) {
                        Etq("¿Nació de una evaluación? (opcional)")
                        SelectorLista(evaluaciones, evaluacion,
                            { "Evaluación ${it.fecha}" + (it.terapeutaNombre?.let { n -> " · $n" } ?: "") },
                            "Sin evaluación previa") { ev ->
                            evaluacion = ev
                            ev.especialidadId?.let { espId -> especialidad = especialidades.find { it.id == espId } ?: especialidad }
                            ev.terapeutaId?.let { tId -> terapeuta = terapeutas.find { it.id == tId } ?: terapeuta }
                        }
                        Spacer(Modifier.height(10.dp))
                    }

                    Etq("Diagnóstico")
                    OutlinedTextField(value = diagnostico, onValueChange = { diagnostico = it },
                        placeholder = { Text("Diagnóstico que motiva este tratamiento", color = c.textoSuave) },
                        minLines = 2, modifier = Modifier.fillMaxWidth())
                    if (!diagnosticoPrevio.isNullOrBlank() || evaluacion != null) {
                        Text("🔍 Tomado de la evaluación — puedes ajustarlo", color = c.textoSuave, fontSize = 10.sp,
                            modifier = Modifier.padding(top = 2.dp))
                    }
                    Spacer(Modifier.height(10.dp))

                    if (especialidades.size > 1 && miTerapeutaId == null) {
                        Etq("Especialidad")
                        SelectorLista(especialidades, especialidad, { it.nombre }, "Elegir especialidad") { e ->
                            especialidad = e
                            terapeuta?.let { t -> if (e.id !in t.especialidadIds) terapeuta = null }
                            proc?.let { p -> if (p.especialidadId != null && p.especialidadId != e.id) proc = null }
                        }
                        Spacer(Modifier.height(10.dp))
                    }

                    Etq("Profesional que atenderá")
                    if (miTerapeutaId != null) {
                        SelectorBox("${terapeuta?.nombre ?: "Tú"} (tú)", bloqueado = true) {}
                    } else {
                        SelectorLista(terapeutasFiltrados, terapeuta, { it.nombre }, "Sin asignar") { t ->
                            terapeuta = t
                            proc?.let { p -> if (p.especialidadId != null && p.especialidadId !in t.especialidadIds) proc = null }
                        }
                    }
                    Spacer(Modifier.height(10.dp))

                    Etq("Servicio")
                    SelectorLista(procsVisibles, proc, { it.nombre },
                        if (especialidad == null && terId == null) "Elige especialidad o profesional" else "Seleccionar…") { proc = it }
                }

                // Bloque 2 · adaptado al tipo de servicio ─────────────────
                if (usaSesiones) {
                    Spacer(Modifier.height(12.dp))
                    Tarjeta(titulo = "Pago del plan", icono = "💳") {
                        Etq("Modalidad de pago")
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            ChipMod("📦", "Paquete", "Precio fijo por N sesiones", modalidad == "Paquete", Modifier.weight(1f)) { modalidad = "Paquete" }
                            ChipMod("🎫", "Suelta", "Se cobra por sesión", modalidad == "Sesión suelta", Modifier.weight(1f)) { modalidad = "Sesión suelta" }
                        }
                        Spacer(Modifier.height(10.dp))
                        if (modalidad == "Paquete") {
                            val tarifs = proc?.tarifarios ?: emptyList()
                            if (tarifs.isNotEmpty()) {
                                Etq("Elegir paquete del tarifario")
                                SelectorLista(tarifs, null as TarifarioRef?,
                                    { "${it.cantidadSesiones} sesiones — S/ ${it.precioTotal}" },
                                    "Personalizado o manual…") { t -> totalSesiones = t.cantidadSesiones.toString(); precioPaquete = t.precioTotal.toString() }
                                Spacer(Modifier.height(8.dp))
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Column(Modifier.weight(1f)) { Etq("N° sesiones"); CampoNum(totalSesiones) { totalSesiones = it } }
                                Column(Modifier.weight(1f)) { Etq("Precio paquete"); CampoNum(precioPaquete) { precioPaquete = it } }
                            }
                            Text("Puedes ajustar sesiones y precio; el tarifario solo los pre-llena.",
                                color = c.textoSuave, fontSize = 10.sp, modifier = Modifier.padding(top = 2.dp))
                        } else {
                            Etq("Precio por sesión")
                            CampoNum(precioPorSesion) { precioPorSesion = it }
                            Text("Se cobra por cada sesión realizada.", color = c.textoSuave, fontSize = 10.sp)
                        }
                        Spacer(Modifier.height(10.dp))
                        Etq("Precio acordado (S/) — opcional")
                        CampoNum(precioAcordado) { precioAcordado = it }
                        Text("Solo si se negoció un precio distinto al base.", color = c.textoSuave, fontSize = 10.sp)
                    }
                } else if (esConsulta) {
                    Spacer(Modifier.height(12.dp))
                    Tarjeta(titulo = "Consulta", icono = "📋") {
                        // La medicación/receta NO se pide al crear: el médico aún no atendió.
                        // Se registra al EDITAR el tratamiento, después de la atención.
                        Etq("Costo de la consulta (S/) — opcional")
                        CampoNum(precioAcordado) { precioAcordado = it }
                        Text("Déjalo vacío si es gratis. La medicación y el próximo control se " +
                            "registran al editar, después de atender.", color = c.textoSuave, fontSize = 10.sp)
                    }
                }
            }

            // ── Footer fijo: Cancelar + Crear a ancho completo ────────────
            Box(Modifier.fillMaxWidth().height(1.dp).background(c.borde))
            Row(
                Modifier.fillMaxWidth().background(c.superficie)
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                TextButton(onClick = onCancelar) { Text("Cancelar", color = c.textoSuave, fontWeight = FontWeight.Bold) }
                Box(
                    Modifier.weight(1f).clip(RoundedCornerShape(Sania.shape.md.dp))
                        .background(if (puedeCrear) c.navy else c.borde)
                        .clickable(enabled = puedeCrear) {
                            val p = proc ?: return@clickable
                            onGuardar(
                                TratamientoNuevo(
                                    procedimientoId = p.id,
                                    terapeutaId = if (miTerapeutaId != null) miTerapeutaId else terapeuta?.id,
                                    modalidad = if (esConsulta) "Consulta" else modalidad,
                                    totalSesiones = if (usaSesiones && modalidad == "Paquete") totalSesiones.toIntOrNull() ?: 10
                                        else if (usaSesiones) 1 else null,
                                    precioPaquete = if (usaSesiones && modalidad == "Paquete") precioPaquete.toDoubleOrNull() else null,
                                    precioPorSesion = if (usaSesiones && modalidad == "Sesión suelta") precioPorSesion.toDoubleOrNull() else null,
                                    precioAcordado = precioAcordado.toDoubleOrNull(),
                                    diagnostico = diagnostico.trim().ifBlank { null },
                                    citaOrigenId = evaluacion?.id,
                                    // Medicación y próximo control NO se piden al crear (se llenan al editar tras atender).
                                    medicacion = null,
                                    proximoControl = null,
                                )
                            )
                        }.padding(vertical = 13.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(if (esConsulta) "Crear consulta" else "Crear tratamiento",
                        color = if (puedeCrear) c.sobreNavy else c.textoSuave, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                }
            }
        }
    }
}

/** Tarjeta de sección con título e ícono — agrupa campos relacionados. */
@Composable
private fun Tarjeta(titulo: String, icono: String, contenido: @Composable () -> Unit) {
    val c = Sania.colors
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(Sania.shape.md.dp))
            .background(c.superficie).border(1.dp, c.borde, RoundedCornerShape(Sania.shape.md.dp))
            .padding(14.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(bottom = 12.dp)) {
            Text(icono, fontSize = 15.sp)
            Spacer(Modifier.width(7.dp))
            Text(titulo, color = c.texto, fontSize = 14.sp, fontWeight = FontWeight.Bold)
        }
        contenido()
    }
}

/** Ampliar tratamiento: +sesiones, +monto opcional, nota. */
@Composable
fun ModalAmpliarTratamiento(
    t: pe.saniape.app.data.staff.TratamientoPaciente,
    onCancelar: () -> Unit,
    onConfirmar: (sesionesExtra: Int, montoExtra: Double, nota: String?) -> Unit,
) {
    val c = Sania.colors
    var sesiones by remember { mutableStateOf("") }
    var monto by remember { mutableStateOf("") }
    var nota by remember { mutableStateOf("") }
    val valido = (sesiones.toIntOrNull() ?: 0) > 0
    DialogoForm(
        titulo = "Ampliar tratamiento",
        subtitulo = t.procedimiento ?: "Tratamiento",
        textoAccion = "Ampliar",
        accionHabilitada = valido,
        onCancelar = onCancelar,
        onAccion = {
            val n = sesiones.toIntOrNull() ?: 0
            if (n > 0) onConfirmar(n, monto.toDoubleOrNull() ?: 0.0, nota.trim().ifBlank { null })
        },
    ) {
        TarjetaForm(titulo = "Sesiones adicionales", icono = "➕") {
            Text("Quedará en ${t.totalSesiones} + las que agregues.", color = c.textoSuave, fontSize = 11.sp,
                modifier = Modifier.padding(bottom = 10.dp))
            EtqForm("Sesiones adicionales"); CampoNum(sesiones) { sesiones = it }
            Spacer(Modifier.height(10.dp))
            EtqForm("Monto adicional (S/) — opcional"); CampoNum(monto) { monto = it }
            Spacer(Modifier.height(10.dp))
            EtqForm("Motivo / acuerdo — opcional")
            OutlinedTextField(value = nota, onValueChange = { nota = it }, minLines = 2,
                modifier = Modifier.fillMaxWidth())
        }
    }
}

/** Editar tratamiento: N° sesiones + precios (según modalidad). */
@Composable
fun ModalEditarTratamiento(
    t: pe.saniape.app.data.staff.TratamientoPaciente,
    onCancelar: () -> Unit,
    onGuardar: (totalSesiones: Int?, precioPaquete: Double?, precioPorSesion: Double?, precioAcordado: Double?,
                diagnostico: String?, medicacion: String?, proximoControl: String?) -> Unit,
) {
    val c = Sania.colors
    var totalSesiones by remember { mutableStateOf(t.totalSesiones.toString()) }
    var precioPaquete by remember { mutableStateOf(t.precioPaquete?.toString() ?: "") }
    var precioPorSesion by remember { mutableStateOf(t.precioPorSesion?.toString() ?: "") }
    var precioAcordado by remember { mutableStateOf(t.precioAcordado?.toString() ?: "") }
    val esPaquete = t.modalidad == "Paquete"
    // No se puede bajar el N° de sesiones por debajo de las ya completadas.
    val nuevoTotal = totalSesiones.toIntOrNull() ?: 0
    val errorSesiones = !t.esConsulta && nuevoTotal < t.sesionesCompletadas

    Dialog(onDismissRequest = onCancelar, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Column(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp).heightIn(max = 720.dp)
                .clip(RoundedCornerShape(Sania.shape.lg.dp)).background(c.fondo),
        ) {
            // Header navy (mismo formato que crear)
            Column(Modifier.fillMaxWidth().background(c.navyDark).padding(horizontal = 18.dp, vertical = 16.dp)) {
                Text("Editar tratamiento", color = c.sobreNavy, fontSize = 19.sp, fontWeight = FontWeight.Bold)
                Text(t.procedimiento ?: "Tratamiento", color = c.sobreNavy.copy(alpha = 0.7f),
                    fontSize = 12.sp, modifier = Modifier.padding(top = 2.dp))
            }

            Column(
                Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 14.dp),
            ) {
                // Estado actual (lo realizado no se pierde)
                if (t.sesionesCompletadas > 0) {
                    Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(Sania.shape.sm.dp)).background(c.chipBg)
                        .padding(Sania.dim.md)) {
                        Text("Ya realizadas: ${t.sesionesCompletadas} sesión(es). El total no puede ser menor.",
                            color = c.texto, fontSize = 11.sp)
                    }
                    Spacer(Modifier.height(12.dp))
                }

                if (!t.esConsulta) {
                    Tarjeta(titulo = "Plan por sesiones", icono = if (esPaquete) "📦" else "🎫") {
                        Etq("N° de sesiones"); CampoNum(totalSesiones) { totalSesiones = it }
                        if (errorSesiones) Text("⚠ No puede ser menor a ${t.sesionesCompletadas} (ya completadas).",
                            color = c.error, fontSize = 10.sp, modifier = Modifier.padding(top = 2.dp))
                        Spacer(Modifier.height(10.dp))
                        if (esPaquete) { Etq("Precio del paquete"); CampoNum(precioPaquete) { precioPaquete = it } }
                        else { Etq("Precio por sesión"); CampoNum(precioPorSesion) { precioPorSesion = it } }
                        Spacer(Modifier.height(10.dp))
                        Etq("Precio acordado (S/) — opcional"); CampoNum(precioAcordado) { precioAcordado = it }
                        Text("Solo si se negoció un precio distinto al base.", color = c.textoSuave, fontSize = 10.sp)
                    }
                } else {
                    // EDITAR (ajuste administrativo) = solo el costo. Lo clínico (diagnóstico/
                    // medicación/próximo control) se registra en "Registrar atención" (paso Control).
                    Tarjeta(titulo = "Ajuste de la consulta", icono = "💲") {
                        Etq("Costo de la consulta (S/)"); CampoNum(precioAcordado) { precioAcordado = it }
                        Text("El diagnóstico, medicación y próximo control se registran al atender " +
                            "(paso “Control” del recorrido).", color = c.textoSuave, fontSize = 10.sp)
                    }
                }
            }

            Box(Modifier.fillMaxWidth().height(1.dp).background(c.borde))
            Row(
                Modifier.fillMaxWidth().background(c.superficie).padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                TextButton(onClick = onCancelar) { Text("Cancelar", color = c.textoSuave, fontWeight = FontWeight.Bold) }
                Box(
                    Modifier.weight(1f).clip(RoundedCornerShape(Sania.shape.md.dp))
                        .background(if (errorSesiones) c.borde else c.navy)
                        .clickable(enabled = !errorSesiones) {
                            onGuardar(
                                if (t.esConsulta) null else totalSesiones.toIntOrNull(),
                                if (esPaquete) precioPaquete.toDoubleOrNull() else null,
                                if (!esPaquete && !t.esConsulta) precioPorSesion.toDoubleOrNull() else null,
                                precioAcordado.toDoubleOrNull(),
                                // Editar = solo costo; lo clínico va en "Registrar atención".
                                null, null, null,
                            )
                        }.padding(vertical = 13.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text("Guardar cambios", color = if (errorSesiones) c.textoSuave else c.sobreNavy,
                        fontWeight = FontWeight.Bold, fontSize = 15.sp)
                }
            }
        }
    }
}

/**
 * Registrar atención de una consulta/control (acto clínico, NO administrativo): diagnóstico,
 * medicación/receta y próximo control. Es lo que el médico llena tras atender (paso Control).
 * El costo se ajusta aparte en "Editar tratamiento".
 */
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun ModalRegistrarAtencion(
    t: pe.saniape.app.data.staff.TratamientoPaciente,
    esGestor: Boolean,   // recepción/admin → "fecha tentativa"; médico solo → "referencial"
    puedePagos: Boolean, // mostrar el costo solo con permiso de pagos
    onCancelar: () -> Unit,
    onGuardar: (diagnostico: String, medicacion: String, proximoControl: String, costo: Double?) -> Unit,
) {
    val c = Sania.colors
    var diagnostico by remember { mutableStateOf(t.diagnostico ?: "") }
    var medicacion by remember { mutableStateOf(t.medicacion ?: "") }
    var proximoControl by remember { mutableStateOf(t.proximoControl ?: "") }
    var costo by remember { mutableStateOf(t.precioAcordado?.toString() ?: "") }
    var mostrarFecha by remember { mutableStateOf(false) }

    if (mostrarFecha) {
        val estadoP = androidx.compose.material3.rememberDatePickerState()
        androidx.compose.material3.DatePickerDialog(
            onDismissRequest = { mostrarFecha = false },
            confirmButton = {
                TextButton(onClick = {
                    estadoP.selectedDateMillis?.let { ms ->
                        val d = kotlinx.datetime.Instant.fromEpochMilliseconds(ms)
                            .toLocalDateTime(kotlinx.datetime.TimeZone.UTC).date
                        proximoControl = "${d.year}-${d.monthNumber.toString().padStart(2, '0')}-${d.dayOfMonth.toString().padStart(2, '0')}"
                    }
                    mostrarFecha = false
                }) { Text("Aceptar", color = c.navy) }
            },
            dismissButton = { TextButton(onClick = { mostrarFecha = false }) { Text("Cancelar", color = c.textoSuave) } },
        ) { androidx.compose.material3.DatePicker(state = estadoP) }
    }

    Dialog(onDismissRequest = onCancelar, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Column(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp).heightIn(max = 720.dp)
                .clip(RoundedCornerShape(Sania.shape.lg.dp)).background(c.fondo),
        ) {
            Column(Modifier.fillMaxWidth().background(c.navyDark).padding(horizontal = 18.dp, vertical = 16.dp)) {
                Text("Registrar atención", color = c.sobreNavy, fontSize = 19.sp, fontWeight = FontWeight.Bold)
                Text(t.procedimiento ?: "Consulta", color = c.sobreNavy.copy(alpha = 0.7f),
                    fontSize = 12.sp, modifier = Modifier.padding(top = 2.dp))
            }
            Column(
                Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 14.dp),
            ) {
                Tarjeta(titulo = "Lo que se hizo en la atención", icono = "📝") {
                    Etq("Diagnóstico")
                    OutlinedTextField(value = diagnostico, onValueChange = { diagnostico = it },
                        placeholder = { Text("Hallazgos / diagnóstico", color = c.textoSuave) },
                        minLines = 2, modifier = Modifier.fillMaxWidth())
                    Spacer(Modifier.height(10.dp))
                    Etq("Medicación / receta")
                    OutlinedTextField(value = medicacion, onValueChange = { medicacion = it },
                        placeholder = { Text("Indicaciones, receta…", color = c.textoSuave) },
                        minLines = 2, modifier = Modifier.fillMaxWidth())
                    Spacer(Modifier.height(10.dp))
                    Etq("Próximo control (opcional)")
                    // Selector de fecha (calendario). Es REFERENCIAL: no agenda la cita.
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            Modifier.weight(1f).clip(RoundedCornerShape(Sania.shape.sm.dp))
                                .background(c.superficie).border(1.dp, c.borde, RoundedCornerShape(Sania.shape.sm.dp))
                                .clickable { mostrarFecha = true }.padding(horizontal = 12.dp, vertical = 11.dp),
                        ) {
                            Text(proximoControl.ifBlank { "📅 Elegir fecha…" },
                                color = if (proximoControl.isBlank()) c.textoSuave else c.texto, fontSize = Sania.txt.cuerpo)
                        }
                        if (proximoControl.isNotBlank()) {
                            Spacer(Modifier.width(8.dp))
                            Text("✕", color = c.textoSuave, fontSize = 14.sp,
                                modifier = Modifier.clickable { proximoControl = "" })
                        }
                    }
                    Text(
                        if (esGestor) "Fecha tentativa — luego confirma horario/disponibilidad del profesional al agendar."
                        else "Referencial (ej. “vuelve en ~1 mes”). No agenda la cita; recepción coordinará el horario.",
                        color = c.textoSuave, fontSize = 10.sp, modifier = Modifier.padding(top = 4.dp),
                    )
                    // Costo de la consulta (solo con permiso de pagos). Algunos controles no se cobran.
                    if (puedePagos) {
                        Spacer(Modifier.height(10.dp))
                        Etq("Costo de la consulta (S/) — opcional")
                        CampoNum(costo) { costo = it }
                        Text("Déjalo vacío si es gratis (p. ej. un control sin cobro).",
                            color = c.textoSuave, fontSize = 10.sp)
                    }
                }
            }
            Box(Modifier.fillMaxWidth().height(1.dp).background(c.borde))
            Row(
                Modifier.fillMaxWidth().background(c.superficie).padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                TextButton(onClick = onCancelar) { Text("Cancelar", color = c.textoSuave, fontWeight = FontWeight.Bold) }
                Box(
                    Modifier.weight(1f).clip(RoundedCornerShape(Sania.shape.md.dp)).background(c.navy)
                        .clickable { onGuardar(diagnostico.trim(), medicacion.trim(), proximoControl.trim(), costo.toDoubleOrNull()) }
                        .padding(vertical = 13.dp),
                    contentAlignment = Alignment.Center,
                ) { Text("Guardar atención", color = c.sobreNavy, fontWeight = FontWeight.Bold, fontSize = 15.sp) }
            }
        }
    }
}

@Composable
private fun Etq(t: String) {
    Text(t.uppercase(), color = Sania.colors.textoSuave, fontSize = 10.sp, fontWeight = FontWeight.Bold,
        letterSpacing = 0.5.sp, modifier = Modifier.padding(bottom = 5.dp))
}

@Composable
private fun CampoNum(value: String, onChange: (String) -> Unit) {
    OutlinedTextField(
        value = value, onValueChange = { onChange(it.filter { ch -> ch.isDigit() || ch == '.' }) },
        singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun ChipMod(icono: String, titulo: String, desc: String, activo: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val c = Sania.colors
    Column(
        modifier.clip(RoundedCornerShape(Sania.shape.md.dp))
            .background(if (activo) c.navy.copy(alpha = 0.10f) else c.superficie)
            .border(if (activo) 2.dp else 1.dp, if (activo) c.navy else c.borde, RoundedCornerShape(Sania.shape.md.dp))
            .clickable { onClick() }.padding(vertical = 12.dp, horizontal = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(icono, fontSize = 20.sp)
        Spacer(Modifier.height(3.dp))
        Text(titulo, color = if (activo) c.navy else c.texto, fontSize = 13.sp, fontWeight = FontWeight.Bold)
        Text(desc, color = c.textoSuave, fontSize = 9.sp,
            textAlign = TextAlign.Center, modifier = Modifier.padding(top = 1.dp))
    }
}

@Composable
private fun SelectorBox(valor: String, bloqueado: Boolean = false, onClick: () -> Unit) {
    val c = Sania.colors
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(Sania.shape.sm.dp))
            .background(if (bloqueado) c.chipBg else c.superficie)
            .border(1.dp, c.borde, RoundedCornerShape(Sania.shape.sm.dp))
            .clickable(enabled = !bloqueado) { onClick() }.padding(horizontal = 12.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(valor, color = c.texto, fontSize = Sania.txt.cuerpo)
        Text(if (bloqueado) "🔒" else "▾", color = c.navy, fontSize = 12.sp)
    }
}

@Composable
private fun <T> SelectorLista(items: List<T>, elegido: T?, etiqueta: (T) -> String, placeholder: String, onElegir: (T) -> Unit) {
    val c = Sania.colors
    var abierto by remember { mutableStateOf(false) }
    Column {
        SelectorBox(elegido?.let(etiqueta) ?: placeholder) { abierto = !abierto }
        if (abierto) {
            // Column normal (no LazyColumn): un LazyColumn dentro de un Column con
            // verticalScroll colapsa a altura 0 y "no se despliega". Las listas son cortas.
            Column(
                Modifier.fillMaxWidth().padding(top = 4.dp)
                    .clip(RoundedCornerShape(Sania.shape.sm.dp)).background(c.superficie)
                    .border(1.dp, c.borde, RoundedCornerShape(Sania.shape.sm.dp)),
            ) {
                if (items.isEmpty()) {
                    Text("(Sin opciones)", color = c.textoSuave, fontSize = Sania.txt.pequeno,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 11.dp))
                }
                items.forEach { item ->
                    Text(etiqueta(item), color = c.texto, fontSize = Sania.txt.cuerpo,
                        modifier = Modifier.fillMaxWidth().clickable { onElegir(item); abierto = false }
                            .padding(horizontal = 12.dp, vertical = 11.dp))
                }
            }
        }
    }
}
