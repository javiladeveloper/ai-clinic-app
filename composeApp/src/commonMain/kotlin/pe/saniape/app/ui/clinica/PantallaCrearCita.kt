package pe.saniape.app.ui.clinica

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.imePadding
import androidx.compose.ui.platform.LocalDensity
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
import pe.saniape.app.data.staff.CampaniaApp
import pe.saniape.app.data.staff.CatalogosCobroRepo
import androidx.compose.runtime.collectAsState
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
import pe.saniape.app.data.staff.EvaluacionPrevia
import pe.saniape.app.data.staff.SugerenciaProfesional
import pe.saniape.app.data.staff.profesionalSugerido
import pe.saniape.app.data.staff.horaInicialNuevaCita
import pe.saniape.app.data.staff.hoyClinicaIso
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
    val especialidadId: String? = null,   // pre-seleccionar especialidad (p.ej. derivación destino)
    val tratamientoId: String? = null,    // enlazar la cita al tratamiento que la origina (control)
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
    /**
     * Se llama con la FECHA de la cita recién guardada, antes de [onListo]: la
     * agenda la usa para ofrecer "Ver día" cuando se agendó en otro día.
     */
    onGuardada: (fecha: String) -> Unit = {},
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
    // Campañas vigentes: el costo por defecto sale con la MEJOR promo aplicada (como la web).
    // (nombres cortos vía import: `pe` es también el destructure de precios más abajo)
    var campanias by remember { mutableStateOf<List<CampaniaApp>>(emptyList()) }
    var promoAplicada by remember { mutableStateOf<CampaniaApp?>(null) }

    var tipo by remember { mutableStateOf(prefill?.tipo ?: "Consulta") }
    // Duración EDITABLE (antes era fija 15/60 y una sesión de 30 no se podía agendar).
    // Default por tipo (igual que la web: Consulta 15, resto 60); si el usuario la
    // tocó, cambiar de tipo ya no se la pisa.
    var duracion by remember { mutableStateOf(if ((prefill?.tipo ?: "Consulta") == "Consulta") 15 else 60) }
    var duracionTocada by remember { mutableStateOf(false) }
    LaunchedEffect(tipo) { if (!duracionTocada) duracion = if (tipo == "Consulta") 15 else 60 }
    var paciente by remember { mutableStateOf<RefNombre?>(null) }
    var tratamiento by remember { mutableStateOf<TratamientoRef?>(null) }
    var terapeuta by remember { mutableStateOf<TerapeutaRef?>(null) }
    // ¿Lo eligió quien agenda (o vino prefijado)? Entonces ninguna sugerencia lo pisa.
    var terapeutaAMano by remember { mutableStateOf(prefill?.terapeutaId != null) }
    // El precargado por tratamiento/diagnóstico (para la ayuda bajo el selector).
    var precargado by remember { mutableStateOf<SugerenciaProfesional?>(null) }
    var evaluaciones by remember { mutableStateOf<List<EvaluacionPrevia>>(emptyList()) }
    var especialidad by remember { mutableStateOf<EspecialidadRef?>(null) }
    var fecha by remember { mutableStateOf(prefill?.fecha ?: fechaInicial) }
    // Un día futuro arranca 09:00 (como la web); hoy, la próxima hora en punto.
    var hora by remember {
        mutableStateOf(prefill?.hora ?: horaInicialNuevaCita(fechaInicial, hoyClinicaIso(), pe.saniape.app.ui.proximaHoraEnPunto()))
    }
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

    // El flujo que rige ESTA cita: el de la especialidad elegida si tiene uno
    // propio, si no el de la clínica. Es lo que permite que en una misma
    // clínica fisioterapia entre por Consulta y odontología por Diagnóstico.
    val flujoEfectivo = remember(ctx.flujo, especialidad) {
        ctx.flujo.paraEspecialidad(especialidad?.flujoPreset)
    }
    // Los tipos que se ofrecen de verdad. Antes se mostraban los tres SIEMPRE:
    // a RENOVA, que no hace consultas, se le ofrecía "Consulta"; y con los
    // nombres internos, no los suyos.
    val tiposVisibles = remember(flujoEfectivo, ctx.usaSesiones) {
        TIPOS_INFO.filter { flujoEfectivo.usaTipo(it.valor) }
            .filter { it.valor != "Sesión" || ctx.usaSesiones }
    }
    // Si el tipo elegido deja de ofrecerse (al cambiar de especialidad, o al
    // abrir la pantalla en una clínica que no usa Consulta), se cae al primero
    // que sí exista. Sin esto se guardaba una cita de un tipo que la clínica
    // declaró no tener.
    LaunchedEffect(tiposVisibles) {
        if (tiposVisibles.isNotEmpty() && tiposVisibles.none { it.valor == tipo }) {
            tipo = tiposVisibles.first().valor
        }
    }
    // ── Multisede (gemelo de CitaForm web) ──
    // La cita va a la sede ACTIVA; si el Admin mira "todas las sedes", elige
    // aquí (por defecto la principal). Solo se ofrecen las sedes del usuario.
    // Sin multisede: nada de esto se pinta ni se consulta, y no se manda sede.
    val sedeEstado by pe.saniape.app.data.staff.SedeActiva.estado.collectAsState()
    val multiSede = sedeEstado.multiSede
    // La sede "normal" de la cita: la activa; en "todas", la principal (o la primera).
    fun sedePorDefecto(): String? =
        if (!sedeEstado.multiSede) null
        else sedeEstado.sedeId.ifEmpty { null }
            ?: sedeEstado.principalId?.takeIf { p -> sedeEstado.sedes.any { it.id == p } }
            ?: sedeEstado.sedes.firstOrNull()?.id
    var sedeId by remember { mutableStateOf(sedePorDefecto()) }
    // Pacientes por sede: la cita ES del paciente, así que va en SU sede. Si se
    // agendara en otra, el personal de la sede del paciente no la vería y el de la
    // otra tampoco (no ve al paciente). Se fuerza y se dice; el selector queda
    // bloqueado. Gemelo de `sedeForzada` en CitaForm.tsx (web).
    val sedeForzada = pe.saniape.app.data.staff.sedeForzadaCita(
        multiSede = multiSede,
        pacientesPorSede = sedeEstado.pacientesPorSede,
        sedePaciente = paciente?.sedeId,
        sedesElegibles = sedeEstado.sedes.map { it.id },
    )
    var habiaForzada by remember { mutableStateOf(false) }
    LaunchedEffect(sedeForzada) {
        if (sedeForzada != null) {
            sedeId = sedeForzada; habiaForzada = true
        } else if (habiaForzada) {
            // Cambió a un paciente sin sede: vuelve a la sede de siempre.
            sedeId = sedePorDefecto(); habiaForzada = false
        }
    }
    var datosSede by remember { mutableStateOf<pe.saniape.app.data.staff.SedesAgendaRepo.DatosSede?>(null) }
    LaunchedEffect(multiSede) {
        if (multiSede) datosSede = runCatching { pe.saniape.app.data.staff.SedesAgendaRepo.datos(ctx.clinicaId) }.getOrNull()
    }
    val filtroSedeForm = if (multiSede) sedeId?.let { pe.saniape.app.data.staff.FiltroSede(it, sedeEstado.principalId) } else null

    // Profesionales filtrados por la especialidad elegida (o todos si no se eligió)
    // y, con multisede, por los que atienden en ESA sede ese día (los sin horario
    // o a demanda, por su sede base). El ya elegido no desaparece de la lista.
    val terapeutasFiltrados = (especialidad?.let { e ->
        terapeutas.filter { e.id in it.especialidadIds }
    } ?: terapeutas).let { lista ->
        val d = datosSede
        if (filtroSedeForm == null || d == null) lista
        else pe.saniape.app.data.staff.profesionalesEnSede(
            terapeutas = lista, idDe = { it.id }, base = d.base, franjas = d.franjas,
            dia = pe.saniape.app.data.staff.diaCorto(fecha), f = filtroSedeForm, mantener = terapeuta?.id,
        )
    }
    // ¿El elegido no atiende en esta sede ese día? (aviso, no bloqueo; como la web)
    val elegidoFueraDeSede = run {
        val d = datosSede; val t = terapeuta
        filtroSedeForm != null && d != null && t != null && pe.saniape.app.data.staff.profesionalesEnSede(
            terapeutas = listOf(t), idDe = { it.id }, base = d.base, franjas = d.franjas,
            dia = pe.saniape.app.data.staff.diaCorto(fecha), f = filtroSedeForm,
        ).isEmpty()
    }

    // Disponibilidad en vivo (igual que la web): bloquea si no disponible (futura),
    // advierte si hay solapamiento. Se recalcula al cambiar profesional/fecha/hora.
    var disponibilidad by remember { mutableStateOf<pe.saniape.app.data.staff.Disponibilidad?>(null) }
    // Depende también de paciente?.id: si no, el aviso de "el paciente ya tiene otra cita"
    // no se recalcula al cambiar de paciente (quedaba obsoleto).
    LaunchedEffect(terapeuta?.id, fecha, hora, tipo, paciente?.id, duracion) {
        val terId = terapeuta?.id ?: ctx.miTerapeutaId
        disponibilidad = if (terId != null) {
            runCatching {
                pe.saniape.app.data.staff.DisponibilidadRepo.verificar(
                    terId, fecha, hora, duracion, pacienteId = paciente?.id,
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
            campanias = runCatching { CatalogosCobroRepo.campaniasVigentes() }.getOrDefault(emptyList())
            val tipoIni = prefill?.tipo ?: "Consulta"
            val baseIni = if (tipoIni == "Evaluación") pe else pc
            val (precioIni, promoIni) = CatalogosCobroRepo.precioCitaConCampania(campanias, tipoIni, baseIni)
            promoAplicada = promoIni
            costo = precioIni.toString()
            // Resolver referencias del pre-llenado (→ Evaluación).
            prefill?.let { pf ->
                paciente = pf.pacienteId?.let { id ->
                    // Fuera de la lista precargada: su sede se pide aparte (pacientes por sede).
                    pacientes.find { it.id == id }
                        ?: pf.pacienteNombre?.let { RefNombre(id, it, sedeId = AgendaRepo.sedeDePaciente(id)) }
                }
                val ter = pf.terapeutaId?.let { id -> terapeutas.find { it.id == id } }
                terapeuta = ter
                // Especialidad: la explícita del prefill (p.ej. derivación destino) tiene prioridad;
                // si no, la del profesional prefijado (si tiene una sola). El profesional queda
                // OPCIONAL: en áreas como rayos X puede no haber profesional propio (lo hace el
                // mismo médico) → no se fuerza, recepción/el médico lo asigna si aplica.
                especialidad = pf.especialidadId?.let { espId -> especialidadesClinica.find { it.id == espId } }
                    ?: ter?.especialidadIds?.singleOrNull()?.let { espId -> especialidadesClinica.find { it.id == espId } }
            }
        } catch (_: Exception) {}
    }

    // Costo por defecto según tipo, con la MEJOR campaña vigente ya descontada.
    LaunchedEffect(tipo) {
        if (tipo == "Sesión") { costo = "0"; promoAplicada = null; return@LaunchedEffect }
        val base = if (tipo == "Evaluación") precioEvaluacion else precioConsulta
        val (precio, promo) = CatalogosCobroRepo.precioCitaConCampania(campanias, tipo, base)
        promoAplicada = promo
        costo = precio.toString()
    }
    // Tratamientos del paciente (para tipo Sesión / control vinculado)
    LaunchedEffect(paciente?.id) {
        val p = paciente
        tratamientos = if (p != null) try { AgendaRepo.tratamientosActivos(p.id) } catch (_: Exception) { emptyList() } else emptyList()
        // Si el prefill trae un tratamiento (p.ej. control que nace de un tratamiento), enlazarlo.
        prefill?.tratamientoId?.let { tid -> tratamiento = tratamientos.find { it.id == tid } }
    }

    // Profesional por defecto de una cita de tratamiento (Sesión): el del
    // tratamiento → el que hizo su evaluación/diagnóstico → lo de siempre.
    // Gemelo de la web (ProfesionalSugerido.kt). El profesional vinculado queda
    // fijado a sí mismo, así que ahí no se consulta nada.
    val quiereSugerencia = tipo == "Sesión" && ctx.miTerapeutaId == null
    LaunchedEffect(paciente?.id, quiereSugerencia) {
        val p = paciente
        evaluaciones = if (p != null && quiereSugerencia) {
            runCatching {
                AgendaRepo.evaluacionesPaciente(p.id, ctx.flujo.usaConsulta && !ctx.flujo.usaEvaluacion)
            }.getOrDefault(emptyList())
        } else emptyList()
    }
    val sugerencia = remember(quiereSugerencia, paciente?.id, tratamiento, evaluaciones, terapeutas, especialidad) {
        if (!quiereSugerencia || paciente == null) null
        else profesionalSugerido(
            tratamientoTerapeutaId = tratamiento?.terapeutaId,
            evaluaciones = evaluaciones,
            especialidadId = tratamiento?.especialidadId ?: especialidad?.id,
            activos = terapeutas.associate { it.id to it.especialidadIds },
        )
    }
    LaunchedEffect(sugerencia) {
        val s = sugerencia ?: return@LaunchedEffect
        if (terapeutaAMano) return@LaunchedEffect
        terapeutas.find { it.id == s.id }?.let { terapeuta = it; precargado = s }
    }

    // Pickers nativos
    if (mostrarFecha) {
        // Abre en la fecha que ya tiene el campo (no en hoy): al ajustar una
        // fecha sugerida (+7 días, próximo control) no hay que volver a buscarla.
        val estado = rememberDatePickerState(initialSelectedDateMillis = pe.saniape.app.data.staff.isoAMillisUtc(fecha))
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
            duracion = duracion,
            // Multisede: solo los de la sede de la cita (ya filtrados arriba).
            soloTerapeutaIds = if (filtroSedeForm != null && datosSede != null) terapeutasFiltrados.map { it.id }
                else especialidad?.let { e -> terapeutas.filter { e.id in it.especialidadIds }.map { it.id } },
            onElegir = { id ->
                terapeuta = terapeutas.find { it.id == id }; terapeutaAMano = true; precargado = null
                mostrarHorarios = false
            },
            onCerrar = { mostrarHorarios = false },
        )
    }

    // Guardar: la misma acción desde el botón del final y desde la cabecera
    // (esta última solo con el teclado abierto, que tapa el final).
    fun guardar() {
        if (guardando) return
        mensaje = null
        val p = paciente ?: run { mensaje = "Elige un paciente"; return }
        if (tipo == "Sesión" && tratamiento == null && tratamientos.isNotEmpty()) {
            mensaje = "Elige el tratamiento"; return
        }
        // Clínica que mezcla odontología con otra especialidad: la
        // cita tiene que decir de cuál es, porque de eso depende que
        // al completarla se abra el odontograma (citaEsDental). Igual
        // que la web. El profesional agenda lo suyo (su especialidad
        // sale de él) y la sesión la toma de su tratamiento.
        val mixtaDental = ctx.mapaDental.ids.isNotEmpty() && !ctx.mapaDental.solo
        if (mixtaDental && multiEspecialidad && ctx.miTerapeutaId == null && tipo != "Sesión" &&
            especialidad == null && terapeuta?.especialidadIds?.singleOrNull() == null
        ) {
            mensaje = "Elige la especialidad: la clínica atiende odontología y otras, y cada una se atiende distinto."
            return
        }
        if (multiSede && sedeId == null) {
            mensaje = "Elige la sede de la cita"; return
        }
        // Disponibilidad bloquea solo si NO es regularización (igual que la web).
        val d = disponibilidad
        if (d != null && !d.disponible && !esRegularizacion) {
            mensaje = d.motivo ?: "El horario no está disponible"; return
        }
        guardando = true
        scope.launch {
            // Flujo → Evaluación: completar primero la consulta origen
            // (igual que handleEvalSave de la web), luego crear la cita.
            // Si completar falla, NO seguimos: dejaría la consulta origen a
            // medias y la evaluación creada suelta. Avisamos y abortamos.
            val origenId = prefill?.citaOrigenId
            if (origenId != null) {
                val okOrigen = runCatching { AgendaRepo.completar(origenId) }.getOrDefault(false)
                if (!okOrigen) {
                    guardando = false
                    mensaje = "No se pudo cerrar la consulta previa. Intenta de nuevo."
                    return@launch
                }
            }
            val terId = if (ctx.miTerapeutaId != null) ctx.miTerapeutaId else terapeuta?.id
            // Especialidad: la elegida, o la del profesional si solo tiene una.
            val espId = especialidad?.id
                ?: terapeuta?.especialidadIds?.singleOrNull()
            val r = AgendaRepo.crearCitaDetalle(
                pacienteId = p.id, tipo = tipo, fecha = fecha, hora = hora,
                terapeutaId = terId, tratamientoId = tratamiento?.id,
                costo = costo.toDoubleOrNull() ?: 0.0,
                duracion = duracion,
                notas = notas.ifBlank { null },
                especialidadId = espId,
                diagnostico = if (tipo == "Evaluación") diagnostico.ifBlank { null } else null,
                campaniaId = if (tipo != "Sesión") promoAplicada?.id else null,
                // Multisede: la sede viaja en el cuerpo (también en la cola offline).
                sedeId = if (multiSede) sedeId else null,
            )
            guardando = false
            if (r.registrada) {
                if (!r.encolada) pe.saniape.app.ui.Toaster.exito("Cita agendada")
                onGuardada(fecha)
                onListo()
            } else {
                // El motivo real del servidor (cupo lleno, paciente de baja,
                // sin permiso...); el genérico solo si ni se pudo intentar.
                mensaje = r.rechazo?.error ?: "No se pudo agendar. Intenta de nuevo."
            }
        }
    }

    // imePadding: la pantalla deja lugar al teclado (edge-to-edge no achica la
    // ventana), así se puede desplazar hasta el final con el teclado abierto.
    val tecladoAbierto = WindowInsets.ime.getBottom(LocalDensity.current) > 0
    Surface(color = c.fondo, modifier = Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().imePadding()) {
            // Sin flecha "←": en táctil el gesto/botón ATRÁS del sistema ya cancela
            // (ManejarAtras arriba). Dibujarla era redundante.
            Row(
                Modifier.fillMaxWidth().background(c.navyDark)
                    .padding(horizontal = Sania.dim.lg, vertical = Sania.dim.lg),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // Con prefill nombra lo que se agenda ("Agendar sesión"); antes decía
                // siempre "Nueva evaluación", también al agendar una sesión o un control.
                Text(pe.saniape.app.data.staff.tituloFormularioCita(prefill != null, flujoEfectivo.nombreTipo(tipo)),
                    color = c.sobreNavy, fontSize = Sania.txt.subtitulo, fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f))
                // Con el teclado abierto "Guardar cita" queda debajo: el botón sube
                // acá (mismo patrón que los diálogos, reporte 29/09/2026).
                if (tecladoAbierto) {
                    Box(
                        Modifier.clip(RoundedCornerShape(Sania.shape.md.dp))
                            .background(if (!guardando) c.sobreNavy else c.sobreNavy.copy(alpha = 0.35f))
                            .clickable(enabled = !guardando) { guardar() }
                            .padding(horizontal = 14.dp, vertical = 9.dp),
                    ) { Text("Guardar", color = c.navyDark, fontWeight = FontWeight.Bold, fontSize = 13.sp) }
                }
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

                // La especialidad va ANTES del tipo de cita: de ella dependen
                // qué etapas existen. En una clínica con fisioterapia y
                // odontología, fisioterapia entra por Consulta y odontología
                // directo al Diagnóstico; preguntar el tipo primero ofrecería
                // etapas que esa especialidad no tiene.
                // Sede (multisede): la activa, o a elegir si el Admin está en "todas".
                if (multiSede && sedeEstado.sedes.isNotEmpty()) {
                    Spacer(Modifier.height(Sania.dim.md))
                    Etiqueta("Sede")
                    if (sedeForzada != null || sedeEstado.sedeId.isNotEmpty() || sedeEstado.sedes.size == 1) {
                        SelectorBoton("🏢 " + (sedeEstado.sedes.find { it.id == sedeId }?.nombre ?: "Sede"), bloqueado = true) {}
                        if (sedeForzada != null) {
                            Text(
                                "Es la sede del paciente: sus citas se agendan ahí. Para atenderlo en otra, cambia su sede desde la ficha.",
                                color = c.textoSuave, fontSize = 11.sp,
                                modifier = Modifier.padding(top = 4.dp),
                            )
                        }
                    } else {
                        SelectorLista(
                            items = sedeEstado.sedes, elegido = sedeEstado.sedes.find { it.id == sedeId },
                            etiqueta = { "🏢 " + it.nombre },
                            onElegir = { sedeId = it.id },
                            placeholder = "Elige la sede",
                        )
                    }
                }

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

                // Tipo de cita — solo los que esta clínica (o la especialidad
                // elegida) ofrece de verdad, y con SU nombre: RENOVA no hace
                // "consultas" y llama "Evaluación" a su primera cita.
                Etiqueta("Tipo de cita")
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    tiposVisibles.forEach { ti ->
                        TarjetaTipo(
                            info = ti, activo = tipo == ti.valor,
                            etiqueta = flujoEfectivo.nombreTipo(ti.valor),
                            modifier = Modifier.weight(1f),
                        ) { tipo = ti.valor }
                    }
                }
                Spacer(Modifier.height(Sania.dim.md))

                // Paciente — con BUSCADOR (escribir nombre filtra), no scroll uno por uno.
                Etiqueta("Paciente")
                SelectorPacienteBuscable(
                    items = pacientes, elegido = paciente,
                    onElegir = { paciente = it; terapeuta = null; tratamiento = null; terapeutaAMano = false; precargado = null },
                )

                // Tratamiento (solo Sesión)
                if (tipo == "Sesión" && tratamientos.isNotEmpty()) {
                    Spacer(Modifier.height(Sania.dim.md))
                    Etiqueta("Tratamiento")
                    SelectorLista(
                        items = tratamientos, elegido = tratamiento,
                        // Servicio · diagnóstico · avance · desde cuándo: con dos del mismo
                        // servicio, el nombre solo no alcanzaba para saber cuál es cuál.
                        etiqueta = { it.etiqueta() },
                        // El profesional lo pone la sugerencia (tratamiento → diagnóstico),
                        // salvo que quien agenda ya haya elegido uno a mano.
                        onElegir = { tr -> tratamiento = tr },
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
                Spacer(Modifier.height(Sania.dim.md))
                Etiqueta("Duración")
                pe.saniape.app.ui.clinica.pacientes.ChipsDuracion(duracion, onChange = {
                    duracion = it; duracionTocada = true
                })
                // Regularización: "esta cita ya ocurrió" (salta validación de disponibilidad)
                Row(verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(top = Sania.dim.sm).clickable { esRegularizacion = !esRegularizacion }) {
                    Text(if (esRegularizacion) "☑" else "☐", fontSize = 18.sp, color = c.navy)
                    Spacer(Modifier.width(6.dp))
                    Text("Esta cita ya ocurrió (la registro después)", color = c.textoSuave, fontSize = 12.sp)
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
                            terapeutaAMano = true; precargado = null
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
                    if (elegidoFueraDeSede) {
                        Text("⚠ No atiende en esta sede ese día", color = c.pend, fontSize = 11.sp,
                            modifier = Modifier.padding(top = 4.dp))
                    }
                    // De dónde salió el profesional precargado (discreto; se cambia arriba).
                    precargado?.takeIf { it.id == terapeuta?.id }?.let { s ->
                        Text("↺ ${s.texto} · puedes cambiarlo", color = c.textoSuave, fontSize = 11.sp,
                            modifier = Modifier.padding(top = 4.dp))
                    }
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
                    // Con promo, "Restablecer" vuelve al precio PROMOCIONAL (es el vigente).
                    val precioDefault = promoAplicada?.precioCon(precioBase) ?: precioBase
                    Spacer(Modifier.height(Sania.dim.md))
                    Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween, Alignment.CenterVertically) {
                        Etiqueta("Costo (S/)")
                        if ((costo.toDoubleOrNull() ?: 0.0) != precioDefault) {
                            Text("Restablecer", color = c.navy, fontSize = 11.sp, fontWeight = FontWeight.Bold,
                                modifier = Modifier.clickable { costo = precioDefault.toString() })
                        }
                    }
                    OutlinedTextField(
                        value = costo, onValueChange = { costo = it.filter { ch -> ch.isDigit() || ch == '.' } },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    promoAplicada?.let { promo ->
                        Text(
                            "🎉 ${promo.nombre} — ${promo.etiqueta()} · antes S/ ${if (precioBase % 1.0 == 0.0) precioBase.toInt() else precioBase}",
                            color = c.ok, fontSize = 11.sp, fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(top = 4.dp),
                        )
                    }
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
                    onClick = { guardar() },
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
private fun TarjetaTipo(info: TipoInfo, activo: Boolean, etiqueta: String = info.valor, modifier: Modifier = Modifier, onClick: () -> Unit) {
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
        Text(etiqueta, color = if (activo) acento else c.texto, fontSize = 13.sp, fontWeight = FontWeight.Bold)
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

/**
 * Selector de paciente con BUSCADOR: al abrir muestra un campo de texto; escribir
 * filtra por nombre o DNI. Misma regla que la lista de pacientes
 * (`coincideBusqueda`: sin tildes, cada palabra en cualquier orden). Filtra al
 * instante lo que ya está en el teléfono y, desde 2 letras, pregunta también al
 * servidor (con pausa): así aparece quien no entró en los 500 precargados
 * (DALU tiene más). Sin señal se queda con lo local.
 */
@Composable
private fun SelectorPacienteBuscable(
    items: List<RefNombre>, elegido: RefNombre?, onElegir: (RefNombre) -> Unit,
) {
    val c = Sania.colors
    var abierto by remember { mutableStateOf(false) }
    var query by remember { mutableStateOf("") }

    Column {
        SelectorBoton(elegido?.nombre ?: "Buscar paciente por nombre o DNI…") {
            abierto = !abierto
            if (abierto) query = ""
        }
        if (abierto) {
            Column(Modifier.fillMaxWidth().padding(top = 4.dp)
                .clip(RoundedCornerShape(Sania.shape.sm.dp)).background(c.superficie)
                .border(1.dp, c.borde, RoundedCornerShape(Sania.shape.sm.dp))
                .padding(8.dp)) {
                OutlinedTextField(
                    value = query, onValueChange = { query = it },
                    placeholder = { Text("Nombre o DNI…", color = c.textoSuave) },
                    singleLine = true, modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(6.dp))
                // Resultados del servidor para la búsqueda actual (null = aún no / sin señal).
                var remotos by remember { mutableStateOf<Pair<String, List<RefNombre>>?>(null) }
                var buscandoRemoto by remember { mutableStateOf(false) }
                LaunchedEffect(query) {
                    val q = query.trim()
                    if (q.length < 2) { remotos = null; buscandoRemoto = false; return@LaunchedEffect }
                    kotlinx.coroutines.delay(300)   // pausa: no una consulta por tecla
                    buscandoRemoto = true
                    val r = runCatching { AgendaRepo.buscarPacientes(q) }.getOrNull()
                    buscandoRemoto = false
                    if (r != null) remotos = q to r
                }
                val filtrados = remember(query, items, remotos) {
                    val q = query.trim()
                    if (q.isBlank()) items.take(30)
                    else {
                        val delServidor = remotos?.takeIf { it.first == q }?.second.orEmpty()
                        (items + delServidor).distinctBy { it.id }
                            .filter { pe.saniape.app.ui.clinica.pacientes.coincideBusqueda(it.nombre, it.dni, null, q) }
                            .take(40)
                    }
                }
                Column(Modifier.fillMaxWidth().heightIn(max = 260.dp).verticalScroll(rememberScrollState())) {
                    if (filtrados.isEmpty()) {
                        Text(
                            when {
                                query.isBlank() -> "Escribe para buscar."
                                buscandoRemoto -> "Buscando…"
                                else -> "Sin coincidencias."
                            },
                            color = c.textoSuave, fontSize = Sania.txt.pequeno,
                            modifier = Modifier.padding(vertical = 10.dp, horizontal = 6.dp),
                        )
                    } else filtrados.forEach { item ->
                        Column(
                            Modifier.fillMaxWidth()
                                .clickable { onElegir(item); abierto = false; query = "" }
                                .padding(horizontal = 10.dp, vertical = 10.dp),
                        ) {
                            Text(item.nombre, color = c.texto, fontSize = Sania.txt.cuerpo)
                            item.dni?.takeIf { it.isNotBlank() }?.let {
                                Text("DNI $it", color = c.textoSuave, fontSize = 11.sp)
                            }
                        }
                    }
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