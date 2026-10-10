package pe.saniape.app.ui.clinica

import pe.saniape.app.data.staff.LocalTerminologiaPaciente
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.plus
import kotlinx.datetime.toLocalDateTime
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import pe.saniape.app.data.staff.CitaStaff
import pe.saniape.app.data.staff.ContextoStaff
import pe.saniape.app.ui.hora12
import pe.saniape.app.ui.clinica.agenda.AccionCita
import pe.saniape.app.ui.clinica.agenda.AgendaViewModel
import pe.saniape.app.ui.clinica.agenda.componentes.AccionTarjeta
import pe.saniape.app.ui.clinica.agenda.componentes.FiltrosAgenda
import pe.saniape.app.ui.clinica.agenda.componentes.TarjetaCita
import pe.saniape.app.ui.clinica.agenda.componentes.TiraDias
import pe.saniape.app.ui.clinica.agenda.modales.ConfirmacionAccion
import pe.saniape.app.ui.clinica.agenda.modales.ModalCobrarCita
import pe.saniape.app.ui.clinica.agenda.modales.ModalCompletar
import pe.saniape.app.ui.clinica.agenda.modales.ModalEditarCita
import pe.saniape.app.ui.clinica.agenda.modales.ModalPasarEvaluacion
import pe.saniape.app.ui.clinica.agenda.modales.ResumenClinicoSheet
import pe.saniape.app.ui.clinica.pacientes.PantallaFichaPaciente
import pe.saniape.app.data.staff.PacienteStaff
import pe.saniape.app.data.staff.PacientesRepo
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import pe.saniape.app.ui.CargandoLista
import pe.saniape.app.ui.theme.Sania
import pe.saniape.app.data.staff.FlujoClinica
import pe.saniape.app.tutoriales.tourAncla

/**
 * Una apertura de "▶ Atender". [apertura] hace única cada vez que se abre la
 * misma cita: es parte de la key del AtencionViewModel, así reabrir trae un VM
 * NUEVO (borrador, paso y datos frescos; y un `terminada` en false — con el VM
 * viejo, `terminada == true` sacaba de la pantalla apenas entraba). Los VMs de
 * aperturas anteriores quedan en el store de la Activity hasta que ésta muere:
 * son chicos y pocos por jornada, aceptable.
 */
private data class Atendiendo(
    val citaId: String,
    val apertura: Long = kotlinx.datetime.Clock.System.now().toEpochMilliseconds(),
)

/**
 * Agenda del staff. Pantalla DELGADA: solo observa el [AgendaViewModel] y dispara
 * intents. La lógica vive en el ViewModel; los componentes (TarjetaCita, TiraDias,
 * banners, modales) están en archivos propios. Escalable y fácil de mantener.
 */
@Composable
fun PantallaAgenda(
    ctx: ContextoStaff,
    /**
     * Día (ISO) en el que abrir, cuando se llegó tocando el aviso de una cita.
     * null = comportamiento normal (hoy, o el día que ya estuviera elegido).
     */
    fechaInicial: String? = null,
    /** Se llama al posicionarse: así no vuelve a esa fecha en cada recomposición. */
    onFechaConsumida: () -> Unit = {},
    /**
     * Avisa si hay un flujo a pantalla completa abierto (crear cita, "▶ Atender"):
     * el contenedor de tabs oculta la barra inferior mientras tanto. Si no, tocar
     * un tab destruía el flujo a medias sin pasar por su "¿salir sin guardar?".
     */
    onPantallaCompleta: (Boolean) -> Unit = {},
) {
    val c = Sania.colors
    val vm: AgendaViewModel = viewModel(key = ctx.clinicaId) { AgendaViewModel(ctx) }

    // El profesional tocó "Nueva cita agendada": la agenda abre en el día de ESA
    // cita, no en hoy. Sin esto, una cita de la semana que viene no se ve.
    LaunchedEffect(fechaInicial) {
        if (fechaInicial != null) {
            vm.seleccionarDia(fechaInicial)
            onFechaConsumida()
        }
    }

    // Sub-pantalla: crear cita (con o sin pre-llenado de → Evaluación)
    var creandoCita by remember { mutableStateOf(false) }
    // Sub-pantalla: la consulta guiada ("▶ Atender") de esta cita, nativa.
    var atendiendo by remember { mutableStateOf<Atendiendo?>(null) }
    var prefillEval by remember { mutableStateOf<PrefillCita?>(null) }
    // 🧠 Evaluación psicológica: (cita, apertura) del espacio abierto y el plan a crear.
    var evalPsico by remember { mutableStateOf<Pair<CitaStaff, Long>?>(null) }
    var planPsico by remember { mutableStateOf<Triple<CitaStaff, String, pe.saniape.app.data.staff.PrefillPlanPsico>?>(null) }
    // Servicios de evaluación psicológica (consulta aparte; vacío = la agenda no cambia).
    var procsEvalPsico by remember { mutableStateOf<Set<String>>(emptySet()) }
    LaunchedEffect(Unit) { procsEvalPsico = pe.saniape.app.data.staff.EvaluacionPsicoRepo.procedimientosEvaluacion() }
    // Crear cita y Atender tapan la agenda entera: sin barra de tabs encima. Al
    // salir de la agenda por otro camino (onDispose) la barra vuelve sí o sí.
    val pantallaCompleta = creandoCita || prefillEval != null || atendiendo != null || evalPsico != null
    DisposableEffect(pantallaCompleta) {
        onPantallaCompleta(pantallaCompleta)
        onDispose { onPantallaCompleta(false) }
    }
    // Modales (la cita objetivo, o null)
    var completar by remember { mutableStateOf<CitaStaff?>(null) }
    // Odontología: la cita cuyo odontograma ya se revisó pero que NO se pudo
    // completar en un paso (varios odontólogos, o sin diagnóstico que redactar):
    // sigue la ventana de completar de siempre, con lo que salió de la revisión.
    var revisada by remember { mutableStateOf<RevisionHecha?>(null) }
    // Evaluación dental completándose en un paso (crea tratamiento + completa).
    var completandoEval by remember { mutableStateOf(false) }
    var odontogramaCita by remember { mutableStateOf<CitaStaff?>(null) }
    var confirmar by remember { mutableStateOf<Pair<CitaStaff, AccionCita>?>(null) }
    var editar by remember { mutableStateOf<CitaStaff?>(null) }
    var pasarEval by remember { mutableStateOf<CitaStaff?>(null) }
    // "💰 Cobrar" de una Consulta/Evaluación (método + fecha del pago).
    var cobrar by remember { mutableStateOf<CitaStaff?>(null) }
    // "↺ Anular cobro" (cobro dividido, solo Admin): se confirma antes de borrar de caja.
    var anularCobroCita by remember { mutableStateOf<CitaStaff?>(null) }
    // "✗ No vino" de una vencida: confirmación con motivo + "📅 Reponer".
    var noVino by remember { mutableStateOf<CitaStaff?>(null) }
    // Derivación cuya evaluación se está agendando: se marca procesada SOLO si la
    // cita se guarda (antes se marcaba al abrir el form, aunque se cancelara).
    var derivacionPendiente by remember { mutableStateOf<String?>(null) }
    // Resumen clínico (popup al tocar el nombre) + ficha completa que abre desde ahí.
    var resumenPacienteId by remember { mutableStateOf<String?>(null) }
    // Detalle de la cita (tocar la tarjeta): observaciones + lo clínico para quien atiende.
    var detalleCita by remember { mutableStateOf<pe.saniape.app.data.staff.CitaStaff?>(null) }
    var fichaPaciente by remember { mutableStateOf<PacienteStaff?>(null) }
    // La ficha se abre en la pestaña 🏠 Ejercicios (se cerró una sesión de fisio con "dejarle ejercicios").
    var fichaEjercicios by remember { mutableStateOf<pe.saniape.app.ui.clinica.fisio.IndicarEjercicios?>(null) }
    var cargandoFicha by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val acciones = pe.saniape.app.ui.recordarAcciones()
    // "📝 Dar…" de una tarjeta consultando si ya tiene receta (un doble toque no abre dos).
    var buscandoReceta by remember { mutableStateOf(false) }
    // Se emitió una receta (desde la barra o el menú): el indicador de las tarjetas se actualiza.
    val recetasEmitidas = pe.saniape.app.ui.clinica.recetas.RecetaTrasAtencion.emitidas
    LaunchedEffect(recetasEmitidas) { if (recetasEmitidas > 0) vm.cargarRecetasVinculadas(forzar = true) }
    /**
     * Cierre con "dejarle ejercicios de apoyo" (fisio): a la ficha del paciente, pestaña
     * 🏠, con ESA sesión elegida (gemelo de `irAEjercicios` en /citas web). La sesión
     * puede nacer al completar: se lee del vínculo que deja la cita; si aún no se conoce,
     * va el tratamiento y el destino cae en la última atendida.
     */
    fun abrirFichaConEjercicios(cita: CitaStaff) {
        val pid = cita.pacienteId ?: return
        cargandoFicha = true
        scope.launch {
            val (sesionId, pac) = coroutineScope {
                val sesionD = async { pe.saniape.app.data.staff.AgendaRepo.sesionDeCita(cita.id)?.first }
                val pacD = async { runCatching { PacientesRepo.porId(pid) }.getOrNull() }
                sesionD.await() to pacD.await()
            }
            cargandoFicha = false
            if (pac == null) {
                // Sin señal (la sesión quedó en la cola) o la ficha no cargó: no se pierde nada.
                pe.saniape.app.ui.Toaster.error("No se pudo abrir la ficha. Déjale los ejercicios desde su ficha (pestaña 🏠) cuando tengas conexión.")
                return@launch
            }
            fichaEjercicios = pe.saniape.app.ui.clinica.fisio.IndicarEjercicios(sesionId, cita.tratamientoId)
            fichaPaciente = pac
        }
    }
    // Sala de espera: la cita a la que se le toma el triaje, y un reloj para el
    // "· 12 min" de quien espera (solo con sala de espera; DALU no lo arranca).
    var triajeCita by remember { mutableStateOf<CitaStaff?>(null) }
    var ahora by remember { mutableStateOf(kotlinx.datetime.Clock.System.now()) }
    LaunchedEffect(vm.salaEsperaOn) {
        while (vm.salaEsperaOn) {
            kotlinx.coroutines.delay(30_000)
            ahora = kotlinx.datetime.Clock.System.now()
        }
    }

    // ── Crear el tratamiento desde la cita (gemelo de /citas web) ──
    // Desde "🩺 Crear tratamiento" o al completar una Evaluación. Si el paciente YA
    // tiene tratamientos abiertos se muestran primero (crear otro es posible, pero
    // no lo primero: así nacen los duplicados); sin ninguno, el form directo.
    var tratCita by remember { mutableStateOf<CitaStaff?>(null) }
    var tratDiag by remember { mutableStateOf<String?>(null) }
    var tratPac by remember { mutableStateOf<PacienteStaff?>(null) }
    var tratForm by remember { mutableStateOf(false) }
    // Tras crear un tratamiento sin su primera cita: ofrecer agendarla.
    var ofertaPrimera by remember { mutableStateOf<pe.saniape.app.ui.clinica.pacientes.OfertaPrimeraCita?>(null) }
    // Evaluación dental que no se pudo completar sola (falta quién atendió o el
    // diagnóstico): la oferta espera a que se cierre la ventana de completar.
    var ofertaTrasCompletar by remember { mutableStateOf<pe.saniape.app.ui.clinica.pacientes.OfertaPrimeraCita?>(null) }
    LaunchedEffect(completar) {
        if (completar == null) ofertaTrasCompletar?.let { ofertaPrimera = it; ofertaTrasCompletar = null }
    }
    fun cerrarTratamiento() { tratCita = null; tratDiag = null; tratPac = null; tratForm = false }
    fun abrirTratamientoDeCita(cita: CitaStaff, diagnostico: String?) {
        val pid = cita.pacienteId ?: return
        if (!ctx.puede("sesiones")) return
        tratCita = cita; tratDiag = diagnostico; tratPac = null; tratForm = false
        cargandoFicha = true
        scope.launch {
            val pac = runCatching { PacientesRepo.porId(pid) }.getOrNull()
            cargandoFicha = false
            tratPac = pac
            if (pac?.tratamientos.orEmpty().none { it.estado == "Activo" }) tratForm = true
        }
    }
    // ── Evaluación dental en UN paso (29/09/2026, gemelo de la web del 28/09) ──
    // Quién atendió, sin preguntar si se puede saber: el de la cita → el del
    // usuario → el único odontólogo activo. null = ambiguo (se preguntará).
    fun profesionalEvaluacion(cita: CitaStaff): String? =
        pe.saniape.app.data.staff.profesionalDeEvaluacionDental(
            cita.terapeutaId, ctx.miTerapeutaId, vm.terapeutas, ctx.mapaDental,
        )
    fun nombreProfesional(cita: CitaStaff, id: String?): String? = when {
        id == null -> null
        id == cita.terapeutaId -> cita.terapeutaNombre ?: vm.terapeutas.find { it.id == id }?.nombre ?: "Profesional de la cita"
        id == ctx.miTerapeutaId -> vm.terapeutas.find { it.id == id }?.nombre ?: ctx.nombre ?: "Tú"
        else -> vm.terapeutas.find { it.id == id }?.nombre ?: "Profesional"
    }
    /**
     * "✓ Completar evaluación" de la revisión dental (desde Completar o desde
     * 🦷 Odontograma de la tarjeta):
     *  1. el tratamiento, ANTES de completar: si no se puede crear, la cita
     *     queda sin completar y la revisión abierta — nada a medias. Idempotente
     *     por `cita_origen_id` (si ya existe, no crea otro);
     *  2. la cita, con el MISMO camino que la ventana de completar
     *     (vm.ejecutar → /api/staff/cita/completar), el diagnóstico redactado de
     *     los hallazgos y quien atendió ya resuelto.
     * Si falta quién atendió (varios odontólogos) o no hay diagnóstico que
     * redactar, sigue la ventana de completar de siempre, que los pide.
     */
    fun completarEvaluacionDental(
        cita: CitaStaff,
        diagnostico: String,
        registro: pe.saniape.app.ui.clinica.odontologia.RegistroPresupuesto,
        cerrar: () -> Unit,
    ) {
        val pac = cita.pacienteId ?: return
        if (completandoEval) return
        completandoEval = true
        scope.launch {
            val res = try {
                pe.saniape.app.ui.clinica.odontologia.asegurarTratamientoDeEvaluacion(pac, cita.id, registro)
            } catch (e: pe.saniape.app.ui.clinica.odontologia.ErrorEvaluacionDental) {
                pe.saniape.app.ui.Toaster.error("No se completó la evaluación: ${e.message}")
                completandoEval = false
                return@launch
            } catch (e: kotlinx.coroutines.CancellationException) {
                completandoEval = false
                throw e
            } catch (e: Exception) {
                // Lo inesperado tampoco deja la revisión colgada en "Guardando…".
                pe.saniape.app.ui.Toaster.error("No se completó la evaluación: ${e.message ?: "error inesperado"}")
                completandoEval = false
                return@launch
            }
            val detalle = pe.saniape.app.ui.clinica.odontologia.textoTratamientoEvaluacion(res)
            val creados = res is pe.saniape.app.ui.clinica.odontologia.ResultadoTratamientoEvaluacion.Creado
            val terId = profesionalEvaluacion(cita)
            val diag = diagnostico.trim()
            if (terId == null || diag.isBlank()) {
                // No se puede completar solo: la ventana de siempre, con el
                // diagnóstico que haya. El tratamiento ya quedó resuelto (no
                // se vuelve a ofrecer el form de crear otro).
                completandoEval = false
                cerrar()
                revisada = RevisionHecha(cita.id, diag, tratamientoResuelto = true)
                // El tratamiento ya existe: su primera cita se ofrece igual, al cerrar esta ventana.
                (res as? pe.saniape.app.ui.clinica.odontologia.ResultadoTratamientoEvaluacion.Creado)
                    ?.oferta?.let { of -> ofertaTrasCompletar = of.copy(pacienteNombre = cita.pacienteNombre) }
                completar = cita
                val falta = if (terId == null) "indica quién atendió" else "escribe el diagnóstico"
                pe.saniape.app.ui.Toaster.info(
                    if (creados) "$detalle. Para completar, $falta." else "Para completar la evaluación, $falta.",
                )
                return@launch
            }
            vm.ejecutar(
                AccionCita.Completar, cita, diagnostico = diag,
                // Foto fija del odontograma del día (la primera es la inicial).
                congelarOdontograma = true,
                // Solo si la cita no tiene: el servidor asigna quién atendió.
                terapeutaId = terId.takeIf { cita.terapeutaId.isNullOrBlank() },
                textoExito = listOf("Evaluación completada", detalle).filter { it.isNotBlank() }.joinToString(" · "),
                ofrecerPlan = false,
                alTerminar = { ok ->
                    completandoEval = false
                    if (ok) {
                        cerrar()
                        // Evaluación dental con tratamiento(s) creado(s): agendar su primera cita.
                        (res as? pe.saniape.app.ui.clinica.odontologia.ResultadoTratamientoEvaluacion.Creado)
                            ?.oferta?.let { of -> ofertaPrimera = of.copy(pacienteNombre = cita.pacienteNombre) }
                    }
                    // El error del servidor ya se mostró (vm.ejecutar); aquí,
                    // que el tratamiento SÍ quedó y no se duplicará al reintentar.
                    else if (creados) pe.saniape.app.ui.Toaster.info(
                        "El tratamiento ya quedó creado: al volver a completar no se duplica.",
                    )
                },
            )
        }
    }

    // Evaluación recién completada → se ofrece el plan (como la web).
    LaunchedEffect(vm.ofrecerTratamiento) {
        vm.ofrecerTratamiento?.let { o ->
            vm.cerrarOfertaTratamiento()
            abrirTratamientoDeCita(o.cita, o.diagnostico)
        }
    }

    if (creandoCita || prefillEval != null) {
        PantallaCrearCita(
            // El día que se está mirando en la tira (buscando un espacio); si ya
            // pasó, hoy. Gemelo de fechaParaNuevaCita de la web.
            ctx = ctx, fechaInicial = pe.saniape.app.data.staff.fechaParaNuevaCita(vm.fechaSel, pe.saniape.app.data.staff.hoyClinicaIso()),
            prefill = prefillEval,
            onGuardada = { fecha ->
                derivacionPendiente?.let { vm.marcarDerivacion(it) }
                derivacionPendiente = null
                vm.citaGuardadaEn(fecha)
            },
            onListo = { creandoCita = false; prefillEval = null; vm.refrescar() },
            onCancelar = { creandoCita = false; prefillEval = null; derivacionPendiente = null },
        )
        return
    }

    Surface(color = c.fondo, modifier = Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            // Barra con marca de la clínica (logo white-label) + "Agenda" + "+ Nueva"
            Row(
                Modifier.fillMaxWidth().background(c.navyDark)
                    .padding(horizontal = Sania.dim.xl, vertical = Sania.dim.lg),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                    LogoMarcaChica(ctx)
                    Spacer(Modifier.width(10.dp))
                    Column {
                        Text("Agenda", color = c.sobreNavy, fontSize = Sania.txt.subtitulo, fontWeight = FontWeight.Bold)
                        // Multisede: la agenda es la de esta sede. Sin multisede no pinta nada.
                        ChipSede(Modifier.padding(top = 2.dp))
                    }
                }
                // Agendar (crear cita) solo con permiso 'agendar' (recepción/admin). El profesional
                // que solo atiende ve su agenda pero no agenda.
                pe.saniape.app.ui.tutoriales.BotonAyuda("Agenda")
                if (ctx.puede("agendar")) {
                    Spacer(Modifier.width(8.dp))
                    Box(
                        Modifier.tourAncla("agenda.nueva_cita").clip(RoundedCornerShape(Sania.shape.pill.dp))
                            .background(c.sobreNavy.copy(alpha = 0.15f))
                            .clickable { creandoCita = true }
                            .padding(horizontal = 14.dp, vertical = 7.dp),
                    ) { Text("+ Nueva", color = c.sobreNavy, fontSize = 13.sp, fontWeight = FontWeight.Bold) }
                }
            }

            // La tira de días solo aplica en modo "día" (no en la lista/historial).
            if (!vm.modoLista) {
                TiraDias(hoy = vm.hoy, seleccionado = vm.fechaSel, onSeleccionar = { vm.seleccionarDia(it) })
            }

            FiltrosAgenda(
                flujo = ctx.flujo,
                busqueda = vm.busqueda, onBusqueda = { vm.cambiarBusqueda(it) },
                filtroEstado = vm.filtroEstado, onEstado = { vm.cambiarFiltroEstado(it) },
                filtroTipo = vm.filtroTipo, onTipo = { vm.cambiarFiltroTipo(it) },
                especialidades = vm.especialidades,
                muestraEspecialidad = vm.muestraFiltroEspecialidad,
                seleccionEspecialidades = vm.seleccionEspecialidades,
                onEspecialidades = { vm.cambiarSeleccionEspecialidades(it) },
                verHistorial = vm.verHistorial, onVerHistorial = { vm.alternarHistorial() },
            )

            vm.mensaje?.let {
                Text(it, color = c.navy, fontSize = Sania.txt.pequeno,
                    modifier = Modifier.padding(horizontal = Sania.dim.lg, vertical = 4.dp))
            }

            // Se agendó una cita en OTRO día del que se mira: atajo para ir a verla.
            vm.citaAgendadaEn?.let { f ->
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = Sania.dim.lg, vertical = 4.dp)
                        .clip(RoundedCornerShape(Sania.shape.sm.dp)).background(c.okBg)
                        .padding(start = 12.dp, top = 4.dp, bottom = 4.dp, end = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("✓ Agendada para el ${pe.saniape.app.data.staff.fechaLegibleCorta(f)}",
                        color = c.ok, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                    Text("Ver día →", color = c.navy, fontSize = 12.sp, fontWeight = FontWeight.Bold,
                        modifier = Modifier.clip(RoundedCornerShape(Sania.shape.pill.dp))
                            .clickable { vm.seleccionarDia(f) }.padding(horizontal = 10.dp, vertical = 8.dp))
                    Text("✕", color = c.textoSuave, fontSize = 13.sp,
                        modifier = Modifier.clickable { vm.cerrarAvisoCitaAgendada() }.padding(8.dp))
                }
            }

            LazyColumn(
                Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = Sania.dim.xl),
            ) {
                // Banners (mañana / vencidas / derivaciones)
                // Con el filtro de especialidad aplicado (sin filtro: los de siempre).
                vm.bannersVisibles?.let { b ->
                    item {
                        BannersAgendaUI(
                            banners = b,
                            onVerCitaManana = { vm.seleccionarDia(it.fecha) },
                            onCerrarVencida = { cita, vino ->
                                if (vino) {
                                    if (cita.tipo == "Evaluación" || cita.tipo == "Sesión") completar = cita
                                    else vm.ejecutar(AccionCita.Completar, cita)
                                } else noVino = cita   // confirma y registra "No asistió" (no cancela de un toque)
                            },
                            onReagendarVencida = { editar = it },   // abre el modal de fecha/hora
                            // Agendar la evaluación de la derivación: form pre-llenado con el
                            // paciente + tipo Evaluación + especialidad de destino. El profesional
                            // queda opcional (en rayos X puede no haber uno propio). Se marca
                            // procesada al GUARDAR la cita (si se cancela, sigue pendiente).
                            onAgendarDerivacion = { d ->
                                derivacionPendiente = d.id
                                prefillEval = PrefillCita(
                                    tipo = "Evaluación",
                                    pacienteId = d.pacienteId,
                                    pacienteNombre = d.pacienteNombre,
                                    // Como "+ Nueva": el día mirado si es futuro; si no, hoy.
                                    fecha = pe.saniape.app.data.staff.fechaParaNuevaCita(vm.fechaSel, pe.saniape.app.data.staff.hoyClinicaIso()),
                                    hora = pe.saniape.app.data.staff.horaInicialNuevaCita(
                                        pe.saniape.app.data.staff.fechaParaNuevaCita(vm.fechaSel, pe.saniape.app.data.staff.hoyClinicaIso()),
                                        pe.saniape.app.data.staff.hoyClinicaIso(), pe.saniape.app.ui.proximaHoraEnPunto()),
                                    terapeutaId = null,
                                    especialidadId = d.especialidadDestinoId,
                                )
                            },
                            onMarcarDerivacion = { vm.marcarDerivacion(it.id) },
                        )
                    }
                }

                // Aviso de citas sin profesional asignado (origen web sin terapeuta).
                if (vm.citasSinProfesional.isNotEmpty()) {
                    item {
                        BannerSinProfesional(
                            citas = vm.citasSinProfesional,
                            flujoDe = vm::flujoDe,
                            onAsignar = { editar = it },
                        )
                    }
                }

                // Aviso sutil al cambiar de día / refrescar: mantiene la lista visible
                // (no parpadea a spinner completo).
                if (vm.recargando) {
                    item {
                        Row(
                            Modifier.fillMaxWidth().padding(horizontal = Sania.dim.lg, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            CircularProgressIndicator(color = c.navy, strokeWidth = 2.dp,
                                modifier = Modifier.size(14.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("Actualizando…", color = c.textoSuave, fontSize = Sania.txt.mini)
                        }
                    }
                }

                // Sala de espera (triaje o flujo médico): filtro rápido del día de hoy.
                // Sin sala de espera (DALU, RENOVA) no aparece.
                if (vm.mirandoHoy && !vm.cargando) {
                    item { ChipSalaEspera(vm.enEspera.size, vm.soloEspera) { vm.alternarSoloEspera() } }
                }

                when {
                    // Andamio con la forma de las tarjetas de cita: dice qué viene
                    // y evita el salto al llegar los datos.
                    vm.cargando -> item { CargandoLista(filas = 5) }
                    vm.citasVisibles.isEmpty() -> item {
                        Box(Modifier.fillMaxWidth().padding(Sania.dim.lg)) {
                            when {
                                vm.mirandoHoy && vm.soloEspera -> pe.saniape.app.ui.clinica.EstadoVacio(
                                    emoji = "🪑", titulo = "Nadie en sala de espera ahora",
                                    subtitulo = "Marca \"🔔 Llegó\" cuando el ${LocalTerminologiaPaciente.current.paciente} llegue.",
                                )
                                vm.citas.isEmpty() && vm.verHistorial -> pe.saniape.app.ui.clinica.EstadoVacio(
                                    emoji = "🗂", titulo = "Historial vacío",
                                    subtitulo = "Aún no hay citas registradas.",
                                )
                                vm.citas.isEmpty() -> pe.saniape.app.ui.clinica.EstadoVacio(
                                    emoji = "🌤", titulo = "Sin citas este día",
                                    subtitulo = if (ctx.puede("agendar")) "Agenda la primera cita del día." else "No hay nada programado.",
                                    textoAccion = if (ctx.puede("agendar")) "+ Nueva cita" else null,
                                    onAccion = { creandoCita = true },
                                )
                                else -> pe.saniape.app.ui.clinica.EstadoVacio(
                                    emoji = "🔍", titulo = "Sin resultados",
                                    subtitulo = "No hay citas con esos filtros.",
                                )
                            }
                        }
                    }
                    else -> items(vm.citasVisibles, key = { it.id }) { cita ->
                        // Separador: de acá para abajo, lo ya atendido del día.
                        if (cita.id == vm.primeraAtendidaId) {
                            val n = vm.citasVisibles.count { vm.citaYaAtendida(it) }
                            Text("✓ Ya atendidas ($n)", color = Sania.colors.textoSuave, fontSize = 12.sp, fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(start = Sania.dim.lg + 4.dp, end = Sania.dim.lg, top = Sania.dim.lg, bottom = Sania.dim.xs))
                        }
                        Box(Modifier.padding(horizontal = Sania.dim.lg, vertical = Sania.dim.sm / 2)) {
                            TarjetaCita(
                                cita = cita,
                                puedeVerCosto = ctx.puede("pagos"),
                                accionando = vm.accionando,
                                onAccion = { accion ->
                                    when (accion) {
                                        AccionTarjeta.Confirmar -> vm.ejecutar(AccionCita.Confirmar, cita)
                                        AccionTarjeta.Completar ->
                                            // La cita que EVALÚA pide el diagnóstico del profesional
                                            // (también la Consulta de medicina general, que no tiene
                                            // Evaluación aparte); antes solo "Evaluación" y la consulta
                                            // médica se cerraba sin diagnóstico.
                                            if (vm.flujoDe(cita).esCitaQueEvalua(cita.tipo) || cita.tipo == "Sesión") completar = cita
                                            else vm.ejecutar(AccionCita.Completar, cita)
                                        AccionTarjeta.Cancelar -> confirmar = cita to AccionCita.Cancelar
                                        AccionTarjeta.Revertir -> confirmar = cita to AccionCita.Revertir
                                        AccionTarjeta.Editar -> editar = cita
                                        AccionTarjeta.PasarEvaluacion -> pasarEval = cita
                                        AccionTarjeta.Repetir -> prefillEval = repetirDesde(cita)
                                        AccionTarjeta.Odontograma -> odontogramaCita = cita
                                        AccionTarjeta.CrearTratamiento -> abrirTratamientoDeCita(cita, null)
                                        AccionTarjeta.Llego -> vm.marcarLlegada(cita)
                                        AccionTarjeta.Triaje -> triajeCita = cita
                                        AccionTarjeta.Cobrar -> cobrar = cita
                                        // La consulta guiada, nativa (antes se abría en la web).
                                        AccionTarjeta.Atender -> atendiendo = Atendiendo(cita.id)
                                        AccionTarjeta.EvaluacionPsico -> evalPsico = cita to kotlinx.datetime.Clock.System.now().toEpochMilliseconds()
                                        AccionTarjeta.AnularCobro -> anularCobroCita = cita
                                        // 📝 Ya tiene receta → verla/imprimirla; si no → darla, prellenada.
                                        // Primero se confirma si ya tiene (aunque el indicador no haya
                                        // cargado): si tiene, el aviso 🖨 Ver / reimprimir · Emitir otra.
                                        AccionTarjeta.Receta -> vm.prefillReceta(cita)?.let { pf ->
                                            if (!buscandoReceta) {
                                                buscandoReceta = true
                                                scope.launch {
                                                    try {
                                                        pe.saniape.app.ui.clinica.recetas.RecetaTrasAtencion.abrir(pf, yaTiene = vm.recetaVinculadaDe(cita))
                                                    } finally { buscandoReceta = false }
                                                }
                                            }
                                        }
                                    }
                                },
                                onVerResumen = { resumenPacienteId = it },
                                onAbrirDetalle = { detalleCita = cita },
                                conteoFranja = vm.conteosFranja[cita.id] ?: 1,
                                odontologia = vm.esDental(cita),
                                // Crear el plan desde la cita que EVALÚA (esCitaQueEvalua de la
                                // web), solo con permiso 'sesiones' — no 'agendar'.
                                crearTratamiento = ctx.puede("sesiones") && vm.flujoDe(cita).esCitaQueEvalua(cita.tipo),
                                flujo = vm.flujoDe(cita),
                                puedeCobrar = ctx.puede("pagos"),
                                estadoPago = vm.estadosPago[cita.id],
                                mediosPago = vm.mediosPago[cita.id],
                                anularCobro = vm.puedeAnularCobro(cita),
                                // 📝 Receta / indicaciones de la cita atendida (con el módulo y el permiso).
                                textoReceta = if (cita.estado == "Completada" && vm.recetaAplica(cita))
                                    pe.saniape.app.data.staff.textoDarReceta(ctx.modulosClinicos) else null,
                                recetaVinculada = vm.recetasPorCita[cita.id]?.let {
                                    pe.saniape.app.data.staff.etiquetaRecetaVinculada(it)
                                },
                                // Gemelo de evalPsicoDe (/citas web): servicio de evaluación + Admin o
                                // quien atiende la cita. La base decide al abrir.
                                evaluacionPsico = cita.procedimientoId != null && cita.procedimientoId in procsEvalPsico &&
                                    pe.saniape.app.data.staff.puedeVerEvaluacionPsico(ctx.rol, ctx.miTerapeutaId, null, listOf(cita.terapeutaId)),
                                sala = vm.etapaDe(cita)?.let { etapa ->
                                    val a = vm.atencionDe(cita)
                                    pe.saniape.app.ui.clinica.agenda.componentes.SalaTarjeta(
                                        etapa = etapa, atencion = a, triajeOn = vm.triajeOn,
                                        medica = vm.esMedicaGuiada(cita),
                                        marcando = vm.marcandoLlegada == cita.id,
                                        minutos = if (pe.saniape.app.data.staff.enSalaDeEspera(etapa))
                                            pe.saniape.app.data.staff.minutosEsperando(a?.llegadaAt ?: a?.triajeAt, ahora) else null,
                                    )
                                },
                            )
                        }
                    }
                }

                // Paginación (solo en modo lista/historial).
                if (vm.modoLista && !vm.cargando && (vm.pagina > 0 || vm.hayMasPaginas)) {
                    item {
                        Row(
                            Modifier.fillMaxWidth().padding(Sania.dim.lg),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            BotonPagina("← Anterior", habilitado = vm.pagina > 0) { vm.paginaAnterior() }
                            Text("Página ${vm.pagina + 1}", color = c.textoSuave, fontSize = Sania.txt.pequeno)
                            BotonPagina("Siguiente →", habilitado = vm.hayMasPaginas) { vm.paginaSiguiente() }
                        }
                    }
                }
            }
        }
    }

    // "▶ Atender": la consulta guiada a pantalla completa, ENCIMA de la agenda (no
    // con return como crear cita): así el resumen/ficha del paciente y el
    // odontograma que abre desde adentro se dibujan sobre ella con los mismos
    // modales de la agenda.
    atendiendo?.let { a ->
        pe.saniape.app.tutoriales.PantallaTutorial("Consulta") { pe.saniape.app.ui.clinica.atencion.PantallaAtencion(
            ctx = ctx, citaId = a.citaId, apertura = a.apertura, acciones = acciones,
            onSalir = { atendiendo = null; vm.refrescar() },
            onVerFicha = { resumenPacienteId = it },
            onOdontograma = { id -> vm.citas.firstOrNull { it.id == id }?.let { odontogramaCita = it } },
        ) }
    }

    // 🧠 Evaluación psicológica de la cita (su tratamiento), a pantalla completa.
    evalPsico?.let { (cita, apertura) ->
        // Por la CITA (GET ?citaId=): quien la atiende puede no ver el tratamiento.
        run {
            pe.saniape.app.tutoriales.PantallaTutorial("EvaluacionPsico") { pe.saniape.app.ui.clinica.psico.PantallaEvaluacionPsico(
                ctx = ctx, tratamientoId = null, citaId = cita.id, apertura = apertura, acciones = acciones,
                onSalir = { evalPsico = null; vm.refrescar() },
                onCrearTratamiento = if (ctx.puede("sesiones") && cita.pacienteId != null) { evId, prefill ->
                    evalPsico = null
                    planPsico = Triple(cita, evId, prefill)
                } else null,
            ) }
        }
    }
    planPsico?.let { (cita, evId, prefill) ->
        val pid = cita.pacienteId
        if (pid != null) {
            pe.saniape.app.ui.clinica.pacientes.ModalCrearTratamiento(
                pacienteId = pid,
                miTerapeutaId = ctx.miTerapeutaId,
                diagnosticoPrevio = prefill.diagnostico,
                prefillPlan = prefill,
                onCancelar = { planPsico = null },
                onGuardar = { nuevo ->
                    planPsico = null
                    scope.launch {
                        ofertaPrimera = pe.saniape.app.ui.clinica.pacientes.crearTratamientoDelPlan(pid, nuevo, evId, cita.pacienteNombre).oferta
                        vm.refrescar()
                    }
                },
            )
        }
    }

    // ── Modales ──
    // "🩺 Triaje": solo las mediciones que toma la clínica. Se cierra al quedar
    // registrado (servidor o cola); el "no" del servidor se muestra adentro.
    triajeCita?.let { cita ->
        pe.saniape.app.ui.clinica.agenda.modales.ModalTriaje(
            cita = cita,
            campos = vm.camposTriaje,
            flujoMedico = vm.esMedicaGuiada(cita),
            guardando = vm.guardandoTriaje,
            onCancelar = { triajeCita = null },
            onGuardar = { valores, motivo, onRechazo ->
                vm.guardarTriaje(cita, valores, motivo) { error ->
                    if (error == null) triajeCita = null else onRechazo(error)
                }
            },
        )
    }
    completar?.let { cita ->
        // Misma regla que ModalCompletar: la cita que evalúa pide diagnóstico.
        val flujoCita = vm.flujoDe(cita)
        val evalua = flujoCita.esCitaQueEvalua(cita.tipo)
        val pac = cita.pacienteId
        // SOLO la cita dental que evalúa: primero el odontograma. Se decide por
        // CITA: en una clínica con fisio y odontología, la evaluación de fisio
        // va directo al modal de siempre.
        if (vm.esDental(cita) && evalua && pac != null && revisada?.citaId != cita.id) {
            // Un solo paso: la revisión completa la evaluación (ver
            // completarEvaluacionDental). La ventana de abajo solo si falta algo.
            pe.saniape.app.ui.clinica.odontologia.RevisionPrevia(
                pacienteId = pac,
                pacienteNombre = cita.pacienteNombre,
                citaId = cita.id,
                mapaDental = ctx.mapaDental,
                atendio = nombreProfesional(cita, profesionalEvaluacion(cita)),
                guardando = completandoEval,
                onCompletar = { diag, registro ->
                    completarEvaluacionDental(cita, diag, registro) { completar = null; revisada = null }
                },
                onCancelar = { if (!completandoEval) { completar = null; revisada = null } },
            )
        } else {
            // La revisión dental ya resolvió el tratamiento: no ofrecer otro plan.
            val planResuelto = revisada?.takeIf { it.citaId == cita.id }?.tratamientoResuelto == true
            // Fisioterapia (M5): la evaluación estructurada de ESTA cita. Holder sin
            // estado: escribir en él no recompone la agenda. Nunca en una cita dental.
            val evalFisio = remember(cita.id) { pe.saniape.app.ui.clinica.fisio.RefBorradorFisio() }
            val conEvalFisio = evalua && vm.esFisio(cita) && !vm.esDental(cita) && cita.pacienteId != null
            // Cobro en el mismo cierre de una SESIÓN (como la ficha), solo con permiso de pagos.
            val metodoCobro = pe.saniape.app.ui.clinica.pacientes.rememberMetodoPagoInicial(cita.pacienteId)
            val cobro = remember(cita.id) {
                pe.saniape.app.ui.clinica.agenda.modales.CobroCierreAgenda(
                    montoInicial = cita.costo?.takeIf { it > 0 }?.let { v -> if (v % 1.0 == 0.0) v.toInt().toString() else v.toString() } ?: "",
                    metodo = metodoCobro,
                )
            }
            // Sin saldo (paquete pagado completo, o esta sesión suelta ya pagada: el
            // "¿Ya pagó?" del servidor dice "pagado") no se pregunta "¿pagó esta sesión?".
            val conCobro = !evalua && cita.tipo == "Sesión" && cita.tratamientoId != null && ctx.puede("pagos") &&
                vm.estadosPago[cita.id]?.estado != "pagado"
            // 📷 Fotos de la sesión: Sesión de un tratamiento + plan con fotos + clínica sin apagarlas.
            val fotosSesion = remember(cita.id) { pe.saniape.app.ui.clinica.pacientes.FotosSesionPendientes() }
            val conFotos = !evalua && cita.tipo == "Sesión" && cita.tratamientoId != null && cita.pacienteId != null &&
                pe.saniape.app.ui.clinica.pacientes.recordarFotosActivas(ctx.can("fotosEvolutivas")) == true
            // Tras completar: subir las fotos ligadas a la sesión que quedó vinculada a la cita.
            // Sin señal (encolada) o sin completar: se avisa en vez de perderlas.
            fun alCompletarConFotos(): ((Boolean, Boolean) -> Unit)? {
                val fotos = if (conFotos) fotosSesion.copia() else emptyList()
                val pacId = cita.pacienteId
                val tratId = cita.tratamientoId
                if (fotos.isEmpty() || pacId == null || tratId == null) return null
                val visibles = fotosSesion.visiblePaciente
                return { ok, encolada ->
                    pe.saniape.app.ui.clinica.pacientes.fotosTrasCompletar(ok, encolada, pacId, tratId, fotos, visibles,
                        citaIdOffline = cita.id) {
                        pe.saniape.app.data.staff.AgendaRepo.sesionDeCita(cita.id)?.first
                    }
                }
            }
            // 🏠 "¿Le dejas ejercicios de apoyo?" (como la web y la ficha): solo en una sesión
            // de fisio, con permiso de sesiones y con paciente al que abrirle la ficha.
            val dejarEjercicios = remember(cita.id) { mutableStateOf(false) }
            val ofreceEjercicios = vm.esFisio(cita) && ctx.puede("sesiones") && cita.pacienteId != null
            ModalCompletar(
                fotosSesion = if (conFotos) fotosSesion else null,
                dejarEjercicios = if (ofreceEjercicios) dejarEjercicios else null,
                cita = cita, especialidades = vm.especialidades, flujo = flujoCita,
                especialidadId = vm.especialidadDeCita(cita),
                esDental = vm.esDental(cita),
                esFisio = vm.esFisio(cita),
                bloqueEvaluacionFisio = if (conEvalFisio) { diag ->
                    pe.saniape.app.ui.clinica.fisio.EvaluacionFisioForm(
                        citaId = cita.id,
                        textoRegion = "$diag ${cita.procedimiento ?: ""}",
                        onCambio = { evalFisio.valor = it },
                    )
                } else null,
                cobro = if (conCobro) cobro else null,
                estadoPago = vm.estadosPago[cita.id],
                onConfirmarFisio = { obs, piezas, mejorias, eva ->
                    completar = null
                    revisada = null
                    // Con "dejarle ejercicios": si se completó, a la ficha (pestaña 🏠) y sin el
                    // "¿Agendar la siguiente?", que se abriría encima. Si falla, no se navega.
                    val conEjercicios = ofreceEjercicios && dejarEjercicios.value
                    vm.ejecutar(AccionCita.Completar, cita, obs, piezas = piezas, mejorias = mejorias, eva = eva,
                        pago = if (conCobro) cobro.pago() else null,
                        ofrecerSiguienteSesion = !conEjercicios,
                        alTerminar = if (conEjercicios) ({ ok -> if (ok) abrirFichaConEjercicios(cita) }) else null,
                        alCompletarSesion = alCompletarConFotos())
                },
                diagnosticoInicial = revisada?.takeIf { it.citaId == cita.id }?.diagnostico ?: "",
                onCancelar = { completar = null; revisada = null },
                onConfirmar = { obs, diag, espId, piezas ->
                    completar = null
                    revisada = null
                    vm.ejecutar(
                        AccionCita.Completar, cita, obs, diag, espId, piezas = piezas,
                        // Evaluación dental: foto fija del odontograma del día (como la web).
                        congelarOdontograma = vm.esDental(cita) && evalua,
                        evaluacionFisio = if (conEvalFisio) evalFisio.valor else null,
                        pago = if (conCobro) cobro.pago() else null,
                        ofrecerPlan = !planResuelto,
                        alCompletarSesion = alCompletarConFotos(),
                    )
                },
                // Recepción completando una evaluación SIN profesional: se pide quién
                // atendió ANTES de enviar (el servidor la rechaza con SIN_PROFESIONAL).
                profesionales = if (evalua && pe.saniape.app.data.staff.pideProfesionalAlCompletar(
                        cita.tipo, cita.terapeutaId, ctx.miTerapeutaId)) vm.terapeutas.takeIf { it.isNotEmpty() } else null,
                onConfirmarConProfesional = { obs, diag, espId, piezas, terId ->
                    completar = null
                    revisada = null
                    vm.ejecutar(
                        AccionCita.Completar, cita, obs, diag, espId, piezas = piezas,
                        congelarOdontograma = vm.esDental(cita) && evalua,
                        terapeutaId = terId,
                        evaluacionFisio = if (conEvalFisio) evalFisio.valor else null,
                        // Lo mismo que onConfirmar: si algún día este camino cierra
                        // una sesión, el cobro y las fotos no se pueden perder
                        // (bug PodoBlack 2026-10-08, ver rutaCompletar).
                        pago = if (conCobro) cobro.pago() else null,
                        ofrecerPlan = !planResuelto,
                        alCompletarSesion = alCompletarConFotos(),
                    )
                },
            )
        }
    }
    // Odontograma abierto desde una cita dental: lo que se marque
    // queda atado a esa atención.
    odontogramaCita?.takeIf { vm.esDental(it) }?.let { cita ->
        val pac = cita.pacienteId ?: return@let
        // La cita que EVALÚA y sigue abierta: desde aquí mismo se completa la
        // evaluación en un paso (29/09/2026, como la web). Solo a quien puede
        // completar citas (el permiso del endpoint y de esta agenda).
        val completable = vm.flujoDe(cita).esCitaQueEvalua(cita.tipo) &&
            (cita.estado == "Pendiente" || cita.estado == "Confirmada") && ctx.puede("citas")
        if (completable) {
            pe.saniape.app.ui.clinica.odontologia.RevisionPrevia(
                pacienteId = pac,
                pacienteNombre = cita.pacienteNombre,
                citaId = cita.id,
                mapaDental = ctx.mapaDental,
                atendio = nombreProfesional(cita, profesionalEvaluacion(cita)),
                guardando = completandoEval,
                onCompletar = { diag, registro ->
                    completarEvaluacionDental(cita, diag, registro) { odontogramaCita = null }
                },
                // "Cerrar" solo cierra: lo marcado ya quedó guardado.
                onCancelar = { if (!completandoEval) odontogramaCita = null },
                titulo = "🦷 Odontograma",
                textoCancelar = "Cerrar",
            )
        } else run {
            pe.saniape.app.ui.clinica.pacientes.DialogoForm(
                titulo = "🦷 Odontograma",
                subtitulo = cita.pacienteNombre,
                textoAccion = "Listo",
                onCancelar = { odontogramaCita = null },
                onAccion = { odontogramaCita = null },
                textoCancelar = "Cerrar",
            ) {
                pe.saniape.app.ui.clinica.odontologia.OdontogramaVista(
                    pacienteId = pac, citaId = cita.id, mapaDental = ctx.mapaDental,
                    // Tras "Crear tratamiento(s)": agendar la primera cita (cierra el odontograma).
                    onOfrecerAgendar = { of -> odontogramaCita = null; ofertaPrimera = of.copy(pacienteNombre = cita.pacienteNombre) },
                )
            }
        }
    }
    confirmar?.let { (cita, accion) ->
        ConfirmacionAccion(
            cita = cita, accion = accion,
            onCancelar = { confirmar = null },
            onConfirmar = { confirmar = null; vm.ejecutar(accion, cita) },
        )
    }
    editar?.let { cita ->
        ModalEditarCita(
            cita = cita,
            flujo = vm.flujoDe(cita),
            // Reasignar profesional: solo quien gestiona la agenda de todos (igual que el filtro).
            profesionales = if (vm.puedeFiltrarPorPersonal && ctx.puede("citas")) vm.terapeutas else null,
            guardando = vm.accionando,
            onCancelar = { editar = null },
            // Solo se cierra si quedó registrado: "sin cupo" deja elegir otra hora ahí mismo.
            onGuardar = { fecha, hora, terId, motivo ->
                vm.reprogramar(cita, fecha, hora, terapeutaId = terId, motivo = motivo) { ok -> if (ok) editar = null }
            },
        )
    }
    // "✗ No vino": confirmar la falta (con motivo) o reponerla en otra fecha.
    noVino?.let { cita ->
        pe.saniape.app.ui.clinica.agenda.modales.ModalNoAsistio(
            cita = cita, flujo = vm.flujoDe(cita), guardando = vm.accionando,
            onCancelar = { noVino = null },
            onMarcar = { motivo -> vm.noAsistio(cita, motivo) { ok -> if (ok) noVino = null } },
            onReponer = { noVino = null; editar = cita },
        )
    }
    // Cobro que no entró tras completar la sesión: reintentar SOLO el cobro.
    vm.cobroFallido?.let { cf ->
        pe.saniape.app.ui.clinica.pacientes.DialogoCobroFallido(
            numeroSesion = cf.numero, monto = cf.monto, metodo = cf.metodo, motivo = cf.motivo,
            reintentando = vm.reintentandoCobro,
            onReintentar = { vm.reintentarCobro() },
            onCerrar = { vm.cerrarCobroFallido() },
        )
    }
    // "📅 Agendar siguiente" tras completar una sesión (después del cobro fallido, si lo hubo).
    vm.ofrecerSiguiente?.takeIf { vm.cobroFallido == null }?.let { os ->
        val horaSig = os.cita.hora.take(5).takeIf { it.length == 5 } ?: pe.saniape.app.ui.proximaHoraEnPunto()
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { vm.cerrarOfertaSiguiente() },
            title = { Text("📅 ¿Agendar la siguiente sesión?", fontWeight = FontWeight.Bold) },
            text = {
                Text(
                    "${os.cita.pacienteNombre ?: "El ${LocalTerminologiaPaciente.current.paciente}"}: quedan ${os.oferta.quedan} sesión(es). Se propone el " +
                        "${pe.saniape.app.data.staff.fechaLegibleCorta(os.oferta.fecha)} a las ${hora12(horaSig)}" +
                        (os.cita.terapeutaNombre?.let { " con $it" } ?: "") + "; puedes ajustarlo antes de guardar.",
                    color = c.textoSuave, fontSize = 13.sp,
                )
            },
            confirmButton = {
                androidx.compose.material3.TextButton(onClick = {
                    vm.cerrarOfertaSiguiente()
                    prefillEval = PrefillCita(
                        tipo = "Sesión",
                        pacienteId = os.cita.pacienteId, pacienteNombre = os.cita.pacienteNombre,
                        fecha = os.oferta.fecha, hora = horaSig,
                        terapeutaId = os.cita.terapeutaId ?: os.oferta.terapeutaTratamientoId,
                        especialidadId = os.cita.especialidadId ?: os.oferta.especialidadId,
                        tratamientoId = os.oferta.tratamientoId,
                    )
                }) { Text("Agendar (+${os.oferta.intervaloDias} días)", color = c.navy, fontWeight = FontWeight.Bold) }
            },
            dismissButton = {
                androidx.compose.material3.TextButton(onClick = { vm.cerrarOfertaSiguiente() }) { Text("Ahora no", color = c.textoSuave) }
            },
            containerColor = c.superficie,
        )
    }
    // El servidor pidió quién atendió (SIN_PROFESIONAL): selector y reintento, sin cola.
    vm.pedirProfesional?.let { pedido ->
        pe.saniape.app.ui.clinica.agenda.modales.ModalElegirProfesional(
            cita = pedido.cita,
            profesionales = vm.terapeutas,
            onCancelar = { vm.cerrarPedidoProfesional() },
            onElegir = { vm.completarConProfesional(it) },
        )
    }
    anularCobroCita?.let { cita ->
        val medios = vm.mediosPago[cita.id]
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { if (!vm.accionando) anularCobroCita = null },
            title = { Text("↺ ¿Anular este cobro?", fontWeight = FontWeight.Bold) },
            text = {
                Text(
                    "${cita.pacienteNombre ?: "El ${LocalTerminologiaPaciente.current.paciente}"} pagó ${pe.saniape.app.ui.clinica.agenda.modales.textoSoles(cita.costo ?: 0.0, pe.saniape.app.data.staff.monedaDeFila(cita.sedeId))}" +
                        (medios?.let { " con $it" } ?: "") + ". Se borrarán de caja TODAS sus partes y la cita volverá a " +
                        "\"por cobrar\" para cobrarla de nuevo. La atención, el diagnóstico y el historial no cambian.",
                    color = c.textoSuave, fontSize = 13.sp,
                )
            },
            confirmButton = {
                androidx.compose.material3.TextButton(
                    enabled = !vm.accionando,
                    onClick = { vm.anularCobro(cita) { anularCobroCita = null } },
                ) { Text(if (vm.accionando) "Anulando…" else "Anular cobro", color = c.error, fontWeight = FontWeight.Bold) }
            },
            dismissButton = {
                androidx.compose.material3.TextButton(enabled = !vm.accionando, onClick = { anularCobroCita = null }) {
                    Text("No, dejarlo", color = c.textoSuave)
                }
            },
            containerColor = c.superficie,
        )
    }
    cobrar?.let { cita ->
        ModalCobrarCita(
            cita = cita,
            onCancelar = { cobrar = null },
            onConfirmar = { metodo, modo, fecha, pagos ->
                vm.cobrar(cita, metodo, modo, fecha, pagos) { ok -> if (ok) cobrar = null }
            },
            guardando = vm.accionando,
            nombreTipo = vm.flujoDe(cita).nombreTipo(cita.tipo),
        )
    }
    pasarEval?.let { cita ->
        ModalPasarEvaluacion(
            cita = cita,
            onCancelar = { pasarEval = null },
            onElegir = { fecha, hora ->
                // Igual que la web: abre el formulario de Evaluación pre-llenado.
                // Al guardar se completa la consulta origen (citaOrigenId) y se crea la cita.
                prefillEval = PrefillCita(
                    tipo = "Evaluación",
                    pacienteId = cita.pacienteId,
                    pacienteNombre = cita.pacienteNombre,
                    fecha = fecha, hora = hora,
                    terapeutaId = cita.terapeutaId,
                    citaOrigenId = cita.id,
                )
                pasarEval = null
            },
        )
    }

    // Detalle de la cita (tocar la tarjeta): las observaciones (dirección del
    // domicilio con Maps) y, para quien atiende, diagnóstico y notas de la sesión.
    detalleCita?.let { cita ->
        pe.saniape.app.ui.clinica.agenda.modales.DetalleCitaSheet(
            cita = cita,
            flujo = vm.flujoDe(cita),
            puedeVerCosto = ctx.puede("pagos"),
            verClinico = pe.saniape.app.data.staff.veClinicoEnDetalleCita(ctx.puede("sesiones")),
            onCerrar = { detalleCita = null },
            onVerResumen = { pid -> detalleCita = null; resumenPacienteId = pid },
            // Como la web: permiso `citas`. La app no recibe el flag de solo
            // lectura (soporte): el servidor lo corta con 403 SOLO_LECTURA.
            puedeEditarNotas = pe.saniape.app.data.staff.puedeEditarObservaciones(ctx.puede("citas")),
            // Tras guardar, la agenda se recarga (tarjetas y banners al día).
            onNotasGuardadas = { vm.refrescar() },
        )
    }

    // Popup de resumen clínico (tocar el nombre en una tarjeta). "Ver ficha" carga el
    // paciente por id y abre la ficha completa como overlay (misma pantalla que la web).
    resumenPacienteId?.let { pid ->
        ResumenClinicoSheet(
            pacienteId = pid,
            onCerrar = { resumenPacienteId = null },
            onVerFicha = {
                resumenPacienteId = null
                cargandoFicha = true
                scope.launch {
                    fichaPaciente = PacientesRepo.porId(pid)
                    cargandoFicha = false
                }
            },
        )
    }
    if (cargandoFicha) {
        Box(Modifier.fillMaxSize().background(c.fondo.copy(alpha = 0.6f)), Alignment.Center) {
            CircularProgressIndicator(color = c.navy)
        }
    }
    // Ya tiene tratamientos abiertos: se listan antes de crear otro.
    val pacTrat = tratPac
    if (tratCita != null && !tratForm && pacTrat != null) {
        val activos = pacTrat.tratamientos.filter { it.estado == "Activo" }
        if (activos.isNotEmpty()) {
            androidx.compose.material3.AlertDialog(
                onDismissRequest = { cerrarTratamiento() },
                title = { Text("Tratamiento del ${LocalTerminologiaPaciente.current.paciente}", fontWeight = FontWeight.Bold) },
                text = {
                    Column {
                        Text(
                            pacTrat.nombre + " ya tiene " +
                                (if (activos.size == 1) "un tratamiento abierto" else "${activos.size} tratamientos abiertos") + ":",
                            color = c.textoSuave, fontSize = 13.sp,
                        )
                        Spacer(Modifier.size(8.dp))
                        activos.forEach { t ->
                            Column(
                                Modifier.fillMaxWidth().padding(vertical = 3.dp)
                                    .clip(RoundedCornerShape(Sania.shape.sm.dp)).background(c.chipBg)
                                    .padding(horizontal = 12.dp, vertical = 9.dp),
                            ) {
                                Text(t.procedimiento ?: "Tratamiento", color = c.navy, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                                if (t.totalSesiones > 0 && !t.esConsulta) {
                                    Text("${t.sesionesCompletadas} de ${t.totalSesiones} sesiones", color = c.textoSuave, fontSize = 11.sp)
                                }
                            }
                        }
                    }
                },
                confirmButton = {
                    androidx.compose.material3.TextButton(onClick = { tratForm = true }) {
                        Text("+ Crear otro", color = c.navy, fontWeight = FontWeight.Bold)
                    }
                },
                dismissButton = {
                    androidx.compose.material3.TextButton(onClick = { fichaPaciente = pacTrat; cerrarTratamiento() }) {
                        Text("Ver ficha", color = c.textoSuave)
                    }
                },
                containerColor = c.superficie,
            )
        }
    }
    // El MISMO form de la ficha, con la cita como origen y su profesional puesto.
    val citaTrat = tratCita
    val pidTrat = citaTrat?.pacienteId
    if (citaTrat != null && pidTrat != null && tratForm) {
        pe.saniape.app.ui.clinica.pacientes.ModalCrearTratamiento(
            pacienteId = pidTrat,
            miTerapeutaId = ctx.miTerapeutaId,
            diagnosticoPrevio = tratDiag ?: tratPac?.diagnostico,
            citaOrigenId = citaTrat.id,
            terapeutaInicialId = citaTrat.terapeutaId,
            onCancelar = { cerrarTratamiento() },
            onGuardar = { nuevo ->
                cerrarTratamiento()
                scope.launch {
                    // Toast, plantilla y diagnóstico van adentro; sin primera cita, se ofrece agendarla.
                    ofertaPrimera = pe.saniape.app.ui.clinica.pacientes.guardarTratamientoNuevo(pidTrat, nuevo, citaTrat.pacienteNombre).oferta
                    vm.refrescar()
                }
            },
        )
    }
    // Tratamiento creado sin su primera cita: "📅 Agendar la primera sesión" → el
    // formulario nativo de crear cita, prellenado.
    ofertaPrimera?.let { of ->
        pe.saniape.app.ui.clinica.pacientes.DialogoAgendarPrimera(
            oferta = of,
            onAgendar = { pf -> ofertaPrimera = null; prefillEval = pf },
            onCerrar = { ofertaPrimera = null },
        )
    }

    fichaPaciente?.let { pac ->
        Box(Modifier.fillMaxSize().background(c.fondo)) {
            pe.saniape.app.tutoriales.PantallaTutorial("Ficha") { PantallaFichaPaciente(
                ctx = ctx, pacienteInicial = pac,
                onCerrar = { fichaPaciente = null; fichaEjercicios = null },
                ejerciciosAlAbrir = fichaEjercicios,
            ) }
        }
    }
}

/**
 * "🪑 En sala de espera (N)" (gemelo del filtro de /citas): con el filtro,
 * solo quienes llegaron, por orden de llegada.
 */
@Composable
private fun ChipSalaEspera(n: Int, activo: Boolean, onClick: () -> Unit) {
    val c = Sania.colors
    Row(
        Modifier.fillMaxWidth().padding(horizontal = Sania.dim.lg, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.clip(RoundedCornerShape(Sania.shape.pill.dp))
                .background(if (activo) c.pendBg else c.superficie)
                .bordePill(if (activo) c.pend else c.borde)
                .clickable { onClick() }
                .padding(horizontal = 12.dp, vertical = 7.dp),
        ) {
            Text("🪑 En sala de espera ($n)", color = if (activo) c.pend else c.texto,
                fontSize = 12.sp, fontWeight = FontWeight.Bold)
        }
        if (activo) {
            Spacer(Modifier.width(8.dp))
            Text("Por orden de llegada · ver todas", color = c.textoSuave, fontSize = 11.sp,
                modifier = Modifier.clickable { onClick() })
        }
    }
}

private fun Modifier.bordePill(color: androidx.compose.ui.graphics.Color): Modifier =
    this.then(Modifier.border(1.5.dp, color, RoundedCornerShape(Sania.shape.pill.dp)))

/** Aviso de citas sin profesional asignado (origen web). Tocar una abre el editor para asignar. */
@Composable
private fun BannerSinProfesional(citas: List<CitaStaff>, flujoDe: (CitaStaff) -> FlujoClinica, onAsignar: (CitaStaff) -> Unit) {
    val c = Sania.colors
    Column(
        Modifier.fillMaxWidth().padding(horizontal = Sania.dim.lg, vertical = Sania.dim.sm)
            .clip(RoundedCornerShape(Sania.shape.md.dp)).background(c.pendBg)
            .padding(Sania.dim.md),
    ) {
        Text("⚠ ${citas.size} cita${if (citas.size == 1) "" else "s"} sin profesional asignado",
            color = c.pend, fontSize = Sania.txt.pequeno, fontWeight = FontWeight.Bold)
        Text("No aparecen en ninguna agenda. Asígnales un profesional.",
            color = c.pend, fontSize = 11.sp, modifier = Modifier.padding(bottom = 4.dp))
        citas.forEach { cita ->
            Row(
                Modifier.fillMaxWidth().clickable { onAsignar(cita) }.padding(vertical = 6.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("${hora12(cita.hora)} · ${cita.pacienteNombre ?: "${LocalTerminologiaPaciente.current.Paciente}"} · ${flujoDe(cita).nombreTipo(cita.tipo)}",
                    color = c.texto, fontSize = 12.sp, modifier = Modifier.weight(1f))
                Box(
                    Modifier.clip(RoundedCornerShape(Sania.shape.pill.dp)).background(c.pend)
                        .padding(horizontal = 10.dp, vertical = 4.dp),
                ) { Text("Asignar", color = c.sobreNavy, fontSize = 11.sp, fontWeight = FontWeight.Bold) }
            }
        }
    }
}

@Composable
private fun BotonPagina(texto: String, habilitado: Boolean, onClick: () -> Unit) {
    val c = Sania.colors
    Box(
        Modifier.clip(RoundedCornerShape(Sania.shape.sm.dp))
            .background(if (habilitado) c.navy else c.chipBg)
            .then(if (habilitado) Modifier.clickable { onClick() } else Modifier)
            .padding(horizontal = 14.dp, vertical = 8.dp),
    ) {
        Text(texto, color = if (habilitado) c.sobreNavy else c.textoSuave,
            fontSize = 12.sp, fontWeight = FontWeight.Bold)
    }
}
/**
 * Construye el prefill para REPETIR una cita: mismo paciente, profesional, tipo,
 * tratamiento y especialidad, con la fecha propuesta a +7 días de la original (o de
 * hoy si la original ya pasó). El staff solo confirma/ajusta y guarda: 1 toque para
 * citar la próxima sesión/control.
 */
private fun repetirDesde(cita: pe.saniape.app.data.staff.CitaStaff): pe.saniape.app.ui.clinica.PrefillCita {
    // "Hoy" de la sede activa, no del teléfono.
    val hoy = kotlinx.datetime.Clock.System.now()
        .toLocalDateTime(pe.saniape.app.data.staff.ZONA_CLINICA).date
    val base = runCatching { kotlinx.datetime.LocalDate.parse(cita.fecha) }.getOrDefault(hoy)
    val desde = if (base < hoy) hoy else base
    val proxima = desde.plus(7, kotlinx.datetime.DateTimeUnit.DAY)
    return pe.saniape.app.ui.clinica.PrefillCita(
        tipo = cita.tipo ?: "Sesión",
        pacienteId = cita.pacienteId,
        pacienteNombre = cita.pacienteNombre,
        fecha = proxima.toString(),
        hora = cita.hora.takeIf { it.isNotBlank() } ?: "09:00",
        terapeutaId = cita.terapeutaId,
        especialidadId = cita.especialidadId,
        tratamientoId = cita.tratamientoId,
    )
}

/**
 * Revisión dental que no pudo completar sola (29/09/2026): pasa a la ventana de
 * completar con el diagnóstico redactado. [tratamientoResuelto] = la revisión
 * ya creó (o encontró) el tratamiento: no se ofrece el form de crear otro.
 */
private data class RevisionHecha(
    val citaId: String,
    val diagnostico: String,
    val tratamientoResuelto: Boolean,
)
