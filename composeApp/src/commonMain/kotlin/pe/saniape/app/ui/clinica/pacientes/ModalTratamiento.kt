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
import pe.saniape.app.ui.hora12
import pe.saniape.app.data.staff.EspecialidadClinica
import pe.saniape.app.data.staff.EvaluacionRef
import pe.saniape.app.data.staff.PacientesRepo
import pe.saniape.app.data.staff.PlantillaRef
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
    // Modalidad Unidades (injerto capilar, botox…): cantidad × precio unitario.
    val cantidadUnidades: Int? = null,
    val precioUnitario: Double? = null,
    // Plantilla usada (si se eligió): técnicas por sesión + contador de usos.
    val tecnicasSugeridas: String? = null,
    val plantillaId: String? = null,
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
    var cantidadUnidades by remember { mutableStateOf("") }   // modo unidades
    var precioUnitario by remember { mutableStateOf("") }     // modo unidades
    var diagnostico by remember { mutableStateOf(diagnosticoPrevio ?: "") }
    // Plantillas ("combos" de la clínica): elegir una autocompleta servicio + comercial + clínico.
    var plantillas by remember { mutableStateOf<List<PlantillaRef>>(emptyList()) }
    var plantilla by remember { mutableStateOf<PlantillaRef?>(null) }
    // La plantilla se aplica DESPUÉS del prefill del servicio (LaunchedEffect) para no ser pisada.
    var plantillaPend by remember { mutableStateOf<PlantillaRef?>(null) }
    // medicación y próximo control: no se piden al crear (se llenan al editar tras atender).

    LaunchedEffect(pacienteId) {
        procedimientos = runCatching { PacientesRepo.procedimientos() }.getOrDefault(emptyList())
        terapeutas = runCatching { PacientesRepo.terapeutasConEspecialidad() }.getOrDefault(emptyList())
        val esps = runCatching { PacientesRepo.especialidadesClinica() }.getOrDefault(emptyList())
        val ters = runCatching { PacientesRepo.terapeutasConEspecialidad() }.getOrDefault(terapeutas)
        especialidades = esps
        terapeutas = ters
        evaluaciones = runCatching { PacientesRepo.evaluacionesDe(pacienteId) }.getOrDefault(emptyList())
        plantillas = runCatching { PacientesRepo.plantillas() }.getOrDefault(emptyList())
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
            // Modo unidades: prellenar el precio por unidad sugerido.
            precioUnitario = (p.precioUnitarioSugerido ?: p.precio).toString()
            // Servicio único (simple + precio > 0): el acordado arranca en el precio base.
            if (p.modoCobro == "simple" && p.precio > 0) precioAcordado = p.precio.toString()
            // Plantilla elegida: SUS valores comerciales mandan sobre el prefill del servicio.
            plantillaPend?.let { pl ->
                pl.modalidad?.takeIf { it == "Paquete" || it == "Sesión suelta" }?.let { modalidad = it }
                pl.totalSesiones?.let { totalSesiones = it.toString() }
                pl.precioPaquete?.let { precioPaquete = it.toString() }
                pl.precioPorSesion?.let { precioPorSesion = it.toString() }
                pl.cantidadUnidades?.let { cantidadUnidades = it.toString() }
                pl.precioUnitario?.let { precioUnitario = it.toString() }
                plantillaPend = null
            }
        }
    }

    // TIPO del tratamiento a crear (mismo criterio que la web / TipoTratamiento):
    //  - UNIDADES:       servicio con modo_cobro 'unidades' (injerto, botox × cantidad)
    //  - SERVICIO ÚNICO: modo_cobro 'simple' con precio > 0 (blanqueamiento, profilaxis)
    //  - CONSULTA:       especialidad sin sesiones (medicina/nutrición)
    //  - SESIONES:       el resto (fisio, ortodoncia…)
    val esUnidades = proc?.modoCobro == "unidades"
    val esServUnico = !esUnidades && proc?.modoCobro == "simple" && (proc?.precio ?: 0.0) > 0.0
    val esConsulta = proc != null && !esUnidades && !esServUnico && proc?.usaSesiones == false
    val usaSesiones = proc != null && !esUnidades && !esServUnico && !esConsulta
    // Unidades: exige cantidad y precio por unidad (> 0) para poder crear.
    val puedeCrear = proc != null && (!esUnidades ||
        ((cantidadUnidades.toIntOrNull() ?: 0) > 0 && (precioUnitario.toDoubleOrNull() ?: 0.0) > 0.0))

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
                        esUnidades -> "Por unidades · ${proc?.unidadLabel ?: "unidades"} × precio"
                        esServUnico -> "Servicio único · un solo acto"
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
                    // ⚡ Plantilla ("combo" de la clínica): autocompleta servicio + comercial + clínico.
                    if (plantillas.isNotEmpty()) {
                        Etq("⚡ Usar plantilla (opcional)")
                        SelectorLista(plantillas, plantilla, { it.nombre }, "Armar manualmente…") { pl ->
                            plantilla = pl
                            plantillaPend = pl
                            // Servicio de la plantilla → dispara el prefill y luego se aplican sus valores.
                            pl.procedimientoId?.let { pid ->
                                procedimientos.find { it.id == pid }?.let { pr ->
                                    proc = pr
                                    pr.especialidadId?.let { eId -> especialidad = especialidades.find { it.id == eId } ?: especialidad }
                                }
                            }
                            if (miTerapeutaId == null) pl.terapeutaId?.let { tId ->
                                terapeuta = terapeutas.find { it.id == tId } ?: terapeuta
                            }
                            pl.diagnostico?.takeIf { it.isNotBlank() }?.let { diagnostico = it }
                        }
                        Spacer(Modifier.height(10.dp))
                    }

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
                if (esUnidades) {
                    Spacer(Modifier.height(12.dp))
                    val etiquetaU = proc?.unidadLabel ?: "unidades"
                    Tarjeta(titulo = "Cobro por $etiquetaU", icono = "🔢") {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Column(Modifier.weight(1f)) { Etq("Cantidad de $etiquetaU"); CampoNum(cantidadUnidades) { cantidadUnidades = it } }
                            Column(Modifier.weight(1f)) { Etq("Precio por unidad (S/)"); CampoNum(precioUnitario) { precioUnitario = it } }
                        }
                        // Total en vivo (cantidad × precio unitario) — es el acordado por defecto.
                        val total = (cantidadUnidades.toIntOrNull() ?: 0) * (precioUnitario.toDoubleOrNull() ?: 0.0)
                        if (total > 0) {
                            Spacer(Modifier.height(8.dp))
                            Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(Sania.shape.sm.dp))
                                .background(c.chipBg).padding(10.dp)) {
                                Text("Total: S/ ${if (total % 1.0 == 0.0) total.toInt() else total}",
                                    color = c.texto, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                        Spacer(Modifier.height(10.dp))
                        Etq("Precio acordado (S/) — opcional")
                        CampoNum(precioAcordado) { precioAcordado = it }
                        Text("Solo si se negoció distinto al total (cantidad × precio).",
                            color = c.textoSuave, fontSize = 10.sp)
                    }
                } else if (esServUnico) {
                    Spacer(Modifier.height(12.dp))
                    Tarjeta(titulo = "Servicio único", icono = "✨") {
                        Etq("Precio base del servicio")
                        SelectorBox("S/ ${proc?.precio ?: 0.0}", bloqueado = true) {}
                        Spacer(Modifier.height(10.dp))
                        Etq("Precio acordado (S/)")
                        CampoNum(precioAcordado) { precioAcordado = it }
                        Text("Prellenado con el precio base; ajústalo si se negoció otro. " +
                            "El servicio se registra al realizarse (paso “Por hacer”).",
                            color = c.textoSuave, fontSize = 10.sp)
                    }
                } else if (usaSesiones) {
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
                            // Unidades: si no se negoció un acordado, el total = cantidad × precio.
                            val totalUnidades = (cantidadUnidades.toIntOrNull() ?: 0) * (precioUnitario.toDoubleOrNull() ?: 0.0)
                            onGuardar(
                                TratamientoNuevo(
                                    procedimientoId = p.id,
                                    terapeutaId = if (miTerapeutaId != null) miTerapeutaId else terapeuta?.id,
                                    modalidad = when {
                                        esUnidades -> "Unidades"
                                        esServUnico || esConsulta -> "Consulta"
                                        else -> modalidad
                                    },
                                    totalSesiones = if (usaSesiones && modalidad == "Paquete") totalSesiones.toIntOrNull() ?: 10
                                        else if (usaSesiones) 1 else null,
                                    precioPaquete = if (usaSesiones && modalidad == "Paquete") precioPaquete.toDoubleOrNull() else null,
                                    precioPorSesion = if (usaSesiones && modalidad == "Sesión suelta") precioPorSesion.toDoubleOrNull() else null,
                                    precioAcordado = precioAcordado.toDoubleOrNull()
                                        ?: if (esUnidades && totalUnidades > 0) totalUnidades else null,
                                    diagnostico = diagnostico.trim().ifBlank { null },
                                    citaOrigenId = evaluacion?.id,
                                    // Medicación y próximo control NO se piden al crear (se llenan al editar tras atender).
                                    medicacion = null,
                                    proximoControl = null,
                                    cantidadUnidades = if (esUnidades) cantidadUnidades.toIntOrNull() else null,
                                    precioUnitario = if (esUnidades) precioUnitario.toDoubleOrNull() else null,
                                    tecnicasSugeridas = plantilla?.tecnicasSesion?.takeIf { it.isNotBlank() },
                                    plantillaId = plantilla?.id,
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
                diagnostico: String?, medicacion: String?, proximoControl: String?,
                cantidadUnidades: Int?, precioUnitario: Double?) -> Unit,
) {
    val c = Sania.colors
    var totalSesiones by remember { mutableStateOf(t.totalSesiones.toString()) }
    var precioPaquete by remember { mutableStateOf(t.precioPaquete?.toString() ?: "") }
    var precioPorSesion by remember { mutableStateOf(t.precioPorSesion?.toString() ?: "") }
    var precioAcordado by remember { mutableStateOf(t.precioAcordado?.toString() ?: "") }
    var cantidadUnidades by remember { mutableStateOf(t.cantidadUnidades?.toString() ?: "") }
    var precioUnitario by remember { mutableStateOf(t.precioUnitario?.toString() ?: "") }
    val esPaquete = t.modalidad == "Paquete"
    val esUnidades = t.tipo == pe.saniape.app.data.staff.TipoTratamiento.UNIDADES
    // No se puede bajar el N° de sesiones por debajo de las ya completadas.
    val nuevoTotal = totalSesiones.toIntOrNull() ?: 0
    val errorSesiones = !t.esConsulta && !esUnidades && !t.esServicioUnico && nuevoTotal < t.sesionesCompletadas

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

                if (esUnidades) {
                    val etiquetaU = t.unidadLabel ?: "unidades"
                    Tarjeta(titulo = "Cobro por $etiquetaU", icono = "🔢") {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Column(Modifier.weight(1f)) { Etq("Cantidad de $etiquetaU"); CampoNum(cantidadUnidades) { cantidadUnidades = it } }
                            Column(Modifier.weight(1f)) { Etq("Precio por unidad (S/)"); CampoNum(precioUnitario) { precioUnitario = it } }
                        }
                        Spacer(Modifier.height(10.dp))
                        Etq("Precio acordado (S/)"); CampoNum(precioAcordado) { precioAcordado = it }
                        Text("El acordado manda sobre cantidad × precio si se negoció distinto.",
                            color = c.textoSuave, fontSize = 10.sp)
                    }
                } else if (t.esServicioUnico) {
                    Tarjeta(titulo = "Servicio único", icono = "✨") {
                        Etq("Precio acordado (S/)"); CampoNum(precioAcordado) { precioAcordado = it }
                        Text("Precio base del servicio: S/ ${t.precioBase ?: 0.0}.",
                            color = c.textoSuave, fontSize = 10.sp)
                    }
                } else if (!t.esConsulta) {
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
                            val esSesiones = !t.esConsulta && !esUnidades && !t.esServicioUnico
                            onGuardar(
                                if (esSesiones) totalSesiones.toIntOrNull() else null,
                                if (esSesiones && esPaquete) precioPaquete.toDoubleOrNull() else null,
                                if (esSesiones && !esPaquete) precioPorSesion.toDoubleOrNull() else null,
                                precioAcordado.toDoubleOrNull(),
                                // Editar = solo costo; lo clínico va en "Registrar atención".
                                null, null, null,
                                if (esUnidades) cantidadUnidades.toIntOrNull() else null,
                                if (esUnidades) precioUnitario.toDoubleOrNull() else null,
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

/** Lo que se guarda al editar una consulta (cita + clínico + costo en un solo modal). */
data class EdicionConsulta(
    val fecha: String, val hora: String,
    val diagnostico: String, val medicacion: String, val proximoControl: String, val costo: Double?,
)

/**
 * Editar una consulta/control TODO en un solo modal (sin confundir "editar cita" vs
 * "registrar atención"): la cita (fecha/hora), lo clínico (diagnóstico/medicación/próximo
 * control) y el costo. Llega tanto desde el ✏ de la bolita como desde "Registrar atención".
 */
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun ModalEditarConsulta(
    t: pe.saniape.app.data.staff.TratamientoPaciente,
    cita: pe.saniape.app.data.staff.CitaHito?,   // la cita de la consulta (fecha/hora), si existe
    esGestor: Boolean,
    puedePagos: Boolean,
    onCancelar: () -> Unit,
    onGuardar: (EdicionConsulta) -> Unit,
) {
    val c = Sania.colors
    var fecha by remember { mutableStateOf(cita?.fecha ?: "") }
    var hora by remember { mutableStateOf(cita?.hora ?: "09:00") }
    var diagnostico by remember { mutableStateOf(t.diagnostico ?: "") }
    var medicacion by remember { mutableStateOf(t.medicacion ?: "") }
    var proximoControl by remember { mutableStateOf(t.proximoControl ?: "") }
    var costo by remember { mutableStateOf(t.precioAcordado?.toString() ?: "") }
    var mostrarFechaCita by remember { mutableStateOf(false) }
    var mostrarHoraCita by remember { mutableStateOf(false) }
    var mostrarProxControl by remember { mutableStateOf(false) }

    fun msISO(ms: Long): String {
        val d = kotlinx.datetime.Instant.fromEpochMilliseconds(ms).toLocalDateTime(kotlinx.datetime.TimeZone.UTC).date
        return "${d.year}-${d.monthNumber.toString().padStart(2, '0')}-${d.dayOfMonth.toString().padStart(2, '0')}"
    }

    if (mostrarFechaCita || mostrarProxControl) {
        val esCita = mostrarFechaCita
        val estadoP = androidx.compose.material3.rememberDatePickerState()
        androidx.compose.material3.DatePickerDialog(
            onDismissRequest = { mostrarFechaCita = false; mostrarProxControl = false },
            confirmButton = {
                TextButton(onClick = {
                    estadoP.selectedDateMillis?.let { ms -> if (esCita) fecha = msISO(ms) else proximoControl = msISO(ms) }
                    mostrarFechaCita = false; mostrarProxControl = false
                }) { Text("Aceptar", color = c.navy) }
            },
            dismissButton = { TextButton(onClick = { mostrarFechaCita = false; mostrarProxControl = false }) { Text("Cancelar", color = c.textoSuave) } },
        ) { androidx.compose.material3.DatePicker(state = estadoP) }
    }
    if (mostrarHoraCita) {
        val p = hora.split(":")
        val estadoP = androidx.compose.material3.rememberTimePickerState(
            initialHour = p.getOrNull(0)?.toIntOrNull() ?: 9, initialMinute = p.getOrNull(1)?.toIntOrNull() ?: 0, is24Hour = false)
        androidx.compose.material3.DatePickerDialog(
            onDismissRequest = { mostrarHoraCita = false },
            confirmButton = {
                TextButton(onClick = {
                    hora = "${estadoP.hour.toString().padStart(2, '0')}:${estadoP.minute.toString().padStart(2, '0')}"
                    mostrarHoraCita = false
                }) { Text("Aceptar", color = c.navy) }
            },
            dismissButton = { TextButton(onClick = { mostrarHoraCita = false }) { Text("Cancelar", color = c.textoSuave) } },
        ) { Box(Modifier.fillMaxWidth().padding(20.dp), Alignment.Center) { androidx.compose.material3.TimePicker(state = estadoP) } }
    }

    Dialog(onDismissRequest = onCancelar, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Column(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp).heightIn(max = 720.dp)
                .clip(RoundedCornerShape(Sania.shape.lg.dp)).background(c.fondo),
        ) {
            Column(Modifier.fillMaxWidth().background(c.navyDark).padding(horizontal = 18.dp, vertical = 16.dp)) {
                Text("Editar consulta", color = c.sobreNavy, fontSize = 19.sp, fontWeight = FontWeight.Bold)
                Text(t.procedimiento ?: "Consulta", color = c.sobreNavy.copy(alpha = 0.7f),
                    fontSize = 12.sp, modifier = Modifier.padding(top = 2.dp))
            }
            Column(
                Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 14.dp),
            ) {
                // Cita: fecha/hora (solo si la consulta tiene una cita).
                if (cita != null) {
                    Tarjeta(titulo = "Cita", icono = "📅") {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Column(Modifier.weight(1f)) {
                                Etq("Fecha")
                                SelectorCajita(fecha.ifBlank { "Elegir…" }) { mostrarFechaCita = true }
                            }
                            Column(Modifier.weight(1f)) {
                                Etq("Hora")
                                SelectorCajita(pe.saniape.app.ui.hora12(hora)) { mostrarHoraCita = true }
                            }
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                }

                Tarjeta(titulo = "Atención", icono = "📝") {
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
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.weight(1f)) { SelectorCajita(proximoControl.ifBlank { "📅 Elegir fecha…" }, vacio = proximoControl.isBlank()) { mostrarProxControl = true } }
                        if (proximoControl.isNotBlank()) {
                            Spacer(Modifier.width(8.dp))
                            Text("✕", color = c.textoSuave, fontSize = 14.sp, modifier = Modifier.clickable { proximoControl = "" })
                        }
                    }
                    Text(
                        if (esGestor) "Fecha tentativa — luego confirma horario/disponibilidad al agendar."
                        else "Referencial (ej. “vuelve en ~1 mes”). No agenda la cita.",
                        color = c.textoSuave, fontSize = 10.sp, modifier = Modifier.padding(top = 4.dp),
                    )
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
                        .clickable {
                            onGuardar(EdicionConsulta(
                                fecha = fecha.trim(), hora = hora.trim(),
                                diagnostico = diagnostico.trim(), medicacion = medicacion.trim(),
                                proximoControl = proximoControl.trim(), costo = costo.toDoubleOrNull(),
                            ))
                        }.padding(vertical = 13.dp),
                    contentAlignment = Alignment.Center,
                ) { Text("Guardar", color = c.sobreNavy, fontWeight = FontWeight.Bold, fontSize = 15.sp) }
            }
        }
    }
}

/** Cajita seleccionable (abre un picker). */
@Composable
private fun SelectorCajita(valor: String, vacio: Boolean = false, onClick: () -> Unit) {
    val c = Sania.colors
    Box(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(Sania.shape.sm.dp)).background(c.superficie)
            .border(1.dp, c.borde, RoundedCornerShape(Sania.shape.sm.dp))
            .clickable { onClick() }.padding(horizontal = 12.dp, vertical = 11.dp),
    ) { Text(valor, color = if (vacio) c.textoSuave else c.texto, fontSize = Sania.txt.cuerpo) }
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
