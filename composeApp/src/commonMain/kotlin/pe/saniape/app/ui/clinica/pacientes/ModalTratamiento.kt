package pe.saniape.app.ui.clinica.pacientes

import pe.saniape.app.data.staff.LocalTerminologiaPaciente
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
import kotlinx.coroutines.launch
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
import pe.saniape.app.tutoriales.tourAncla
import pe.saniape.app.data.staff.simboloMoneda
import pe.saniape.app.data.staff.monedaDeSede
import pe.saniape.app.data.staff.zonaDeSede

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
    // Campaña aplicada, motivo del descuento y fecha de inicio (paridad web 2026-09-02).
    val campaniaId: String? = null,
    val motivoPrecio: String? = null,
    val fechaInicio: String? = null,
    // Primera sesión en el mismo paso (opcional, 28/09/2026). null = no se agenda.
    val primeraFecha: String? = null,
    val primeraHora: String? = null,
    /**
     * Diagnóstico a APRENDER (sugerencias por especialidad) y su especialidad: solo
     * si es nuevo o cambió respecto del precargado. Se registra DESPUÉS de crear
     * ([aprenderDiagnosticoDe]); null = nada que aprender.
     */
    val diagnosticoAprender: String? = null,
    val especialidadDiagnostico: String? = null,
    /** Tipo del tratamiento (decide qué cita ofrecer agendar tras crearlo). */
    val tipo: pe.saniape.app.data.staff.TipoTratamientoNuevo = pe.saniape.app.data.staff.TipoTratamientoNuevo.SESIONES,
)

/** Tras crear el tratamiento con éxito: aprende su diagnóstico (en segundo plano, una vez). */
fun aprenderDiagnosticoDe(nuevo: TratamientoNuevo, nombrePaciente: String?) {
    nuevo.diagnosticoAprender?.let {
        pe.saniape.app.data.staff.DiagnosticosRepo.registrar(it, nuevo.especialidadDiagnostico, nombrePaciente)
    }
}

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
    /**
     * Fisioterapia (M3) · "📦 Nuevo paquete": el tratamiento que se acaba. Prellena
     * el MISMO servicio, profesional, modalidad y tamaño del paquete ORIGINAL (sin
     * ampliaciones), con sus precios; todo se puede cambiar antes de crear.
     */
    renovacion: pe.saniape.app.data.staff.TratamientoPaciente? = null,
    /**
     * Creación rápida DESDE LA AGENDA (gemelo de /citas web): la cita que evaluó es
     * el origen del plan y su profesional quien lo lleva. Vienen ya puestos.
     */
    citaOrigenId: String? = null,
    terapeutaInicialId: String? = null,
    /**
     * Evaluación psicológica · "Crear tratamiento con este plan": servicio,
     * profesional, Paquete, sesiones y precio que propuso el plan (el
     * `planPrefill` que arma el servidor). Todo editable; se crea por el camino
     * de siempre. El diagnóstico llega por [diagnosticoPrevio].
     */
    prefillPlan: pe.saniape.app.data.staff.PrefillPlanPsico? = null,
) {
    val moneda = pe.saniape.app.ui.monedaUI()
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
    // Se aplica AL INSTANTE (también si su servicio ya estaba elegido) sobre los
    // valores frescos del servicio: lo que no trae queda como lo pone el servicio.
    var plantillas by remember { mutableStateOf<List<PlantillaRef>>(emptyList()) }
    var plantilla by remember { mutableStateOf<PlantillaRef?>(null) }
    // Lo que había ANTES de la primera plantilla: "Sin plantilla" (o una que no
    // trae diagnóstico/profesional) vuelve a esto.
    var dxSinPlantilla by remember { mutableStateOf(diagnosticoPrevio ?: "") }
    var terSinPlantilla by remember { mutableStateOf<TerapeutaConEsp?>(null) }
    // Indicaciones → medicación y "control en X días" → próximo control (de la plantilla).
    var medicacion by remember { mutableStateOf("") }
    var proximoControl by remember { mutableStateOf<String?>(null) }
    var mostrarFechaControl by remember { mutableStateOf(false) }
    // Aviso si los precios de la plantilla no se copiaron (moneda / precio de la sede).
    var avisoPlantilla by remember { mutableStateOf<String?>(null) }
    // Servicio cuyos precios ya se pusieron: el autollenado no vuelve a pisarlos
    // (la plantilla los pone ella misma; ver elegirPlantilla).
    var autollenadoDe by remember { mutableStateOf<String?>(null) }
    // Multisede: precio propio / servicio apagado en la sede donde se venderá.
    var serviciosSede by remember { mutableStateOf<Map<String, Pair<Double?, Boolean>>>(emptyMap()) }
    // Igual que la plantilla: la renovación se aplica DESPUÉS del prefill del servicio.
    var renovPend by remember { mutableStateOf(renovacion) }
    // El plan de la evaluación psicológica, igual: después del prefill del servicio.
    var planPend by remember { mutableStateOf(prefillPlan) }
    // medicación y próximo control: no se piden al crear (se llenan al editar tras atender).
    // Campañas de descuento vigentes (⚡ promos): se ofrecen al elegir el servicio.
    var campanias by remember { mutableStateOf<List<pe.saniape.app.data.staff.CampaniaApp>>(emptyList()) }
    var campaniaAplicada by remember { mutableStateOf<pe.saniape.app.data.staff.CampaniaApp?>(null) }
    // Auditoría de descuentos: si el acordado queda por DEBAJO de la referencia, se pide el porqué.
    var motivoPrecio by remember { mutableStateOf("") }
    // Cuándo empieza (pasada si ya venía atendiéndose, futura si está programado). Default hoy.
    var fechaInicio by remember { mutableStateOf(pe.saniape.app.ui.clinica.agenda.hoyIso()) }
    var mostrarFechaInicio by remember { mutableStateOf(false) }
    // Primera sesión en el mismo paso: arranca DESMARCADA (decisión del dueño,
    // 2026-10-09): nunca se crea una cita sin que alguien elija fecha y hora. Al
    // crear el tratamiento se OFRECE "📅 Agendar la primera sesión" (DialogoAgendarPrimera).
    var conPrimera by remember { mutableStateOf(false) }
    // Propuesta: la próxima hora en punto de hoy si cabe en el horario de atención;
    // si no (ya cerró), el próximo día que atiende a su apertura. Nunca una hora
    // pasada. Arranca con el horario por defecto y se ajusta al de la sede al cargar.
    val propuestaInicial = remember {
        val a = pe.saniape.app.data.staff.ahoraEn(pe.saniape.app.data.staff.zonaActivaId())
        pe.saniape.app.data.staff.primeraSesionPropuesta(a.fecha, a.minutos, a.diaIdx, pe.saniape.app.data.staff.HORARIO_ATENCION_DEFAULT)
    }
    var fechaPrimera by remember { mutableStateOf(propuestaInicial.first) }
    var horaPrimera by remember { mutableStateOf(propuestaInicial.second) }
    // Si el usuario ya eligió fecha u hora, la propuesta del horario no la pisa.
    var primeraTocada by remember { mutableStateOf(false) }
    var mostrarFechaPrimera by remember { mutableStateOf(false) }
    var mostrarHoraPrimera by remember { mutableStateOf(false) }

    // ── Multipaís / multisede: dónde se venderá y en qué moneda ──
    // La sede del tratamiento la pone el servidor (cita de origen → sede activa del
    // usuario → la del profesional → la principal); acá se estima con la activa
    // (o la principal). Las plantillas son de la clínica: sus montos están en la
    // moneda de la clínica.
    val ctxStaff = pe.saniape.app.data.staff.StaffContextoRepo.actual
    val multiSede = ctxStaff?.multiSede == true
    val sedeActivaId = remember { pe.saniape.app.data.staff.SedeActiva.estado.value.sedeId.ifBlank { null } }
    // Mismo orden que el servidor: cita de origen → sede activa → principal.
    var sedeDestino by remember {
        mutableStateOf(pe.saniape.app.data.staff.sedeDestinoTratamiento(multiSede, null, sedeActivaId, ctxStaff?.sedePrincipalId))
    }
    // La cita de origen con la que se calculó la sede (para recalcular si cambia).
    var origenDeSede by remember { mutableStateOf<String?>(null) }
    var sedeIniciada by remember { mutableStateOf(false) }
    suspend fun resolverSede(citaOrigen: String?) {
        origenDeSede = citaOrigen
        val nueva = pe.saniape.app.data.staff.sedeDestinoTratamiento(
            multiSede, citaOrigen?.let { PacientesRepo.sedeDeCita(it) }, sedeActivaId, ctxStaff?.sedePrincipalId,
        )
        if (nueva != sedeDestino || !sedeIniciada) {
            sedeDestino = nueva
            serviciosSede = nueva?.let { PacientesRepo.serviciosDeSede(it) } ?: emptyMap()
        }
        sedeIniciada = true
    }
    val monedaClinica = ctxStaff?.moneda ?: pe.saniape.app.data.staff.MONEDA_POR_DEFECTO
    val monedaSede = ctxStaff?.monedaDeSede(sedeDestino) ?: moneda
    val monedasDestino = setOf(moneda, monedaSede)
    // ¿Se cobra en la moneda de la clínica? Si no, los precios del servicio, sus
    // paquetes y las plantillas (escritos en la moneda de la clínica) no sirven.
    val mismaMonedaSede = pe.saniape.app.data.staff.mismaMonedaQueLaClinica(monedaClinica, monedasDestino)
    fun hoySede(): String = ctxStaff?.let { pe.saniape.app.data.staff.hoyEnIso(it.zonaDeSede(sedeDestino)) }
        ?: pe.saniape.app.data.staff.hoyClinicaIso()
    fun precioSedeDe(procId: String?): Double? = procId?.let { serviciosSede[it]?.first }
    // Lo que el servicio pone en el formulario; en una sede de otra moneda, solo su precio propio.
    fun camposServicio(p: ProcedimientoRef, modalidadActual: String = "Paquete"): pe.saniape.app.data.staff.CamposComerciales {
        val ps = precioSedeDe(p.id)
        val c = pe.saniape.app.data.staff.camposDeServicio(p, ps, modalidadActual)
        return if (mismaMonedaSede) c else pe.saniape.app.data.staff.camposEnOtraMoneda(c, pe.saniape.app.data.staff.tipoTratamientoDe(p, ps), ps)
    }

    LaunchedEffect(pacienteId) {
        // Antes que nada (la renovación elige servicio acá abajo y su prefill lo usa).
        if (multiSede) resolverSede(citaOrigenId ?: renovacion?.citaOrigenId) else sedeIniciada = true
        // Horario de atención de la sede (o de la clínica) → propuesta de la primera sesión.
        launch {
            val horario = pe.saniape.app.data.staff.parsearHorariosAtencion(PacientesRepo.horarioAtencionCrudo(sedeDestino))
            val a = pe.saniape.app.data.staff.ahoraEn(ctxStaff?.zonaDeSede(sedeDestino))
            val (f, h) = pe.saniape.app.data.staff.primeraSesionPropuesta(a.fecha, a.minutos, a.diaIdx, horario)
            if (!primeraTocada) { fechaPrimera = f; horaPrimera = h }
        }
        campanias = runCatching { pe.saniape.app.data.staff.CatalogosCobroRepo.campaniasVigentes() }.getOrDefault(emptyList())
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
        // Desde la agenda: la cita de origen (si es una Evaluación completada, se
        // elige en la lista y trae su especialidad) y el profesional que atendió.
        if (miTerapeutaId == null) terapeutaInicialId?.let { tId -> terapeuta = ters.find { it.id == tId } ?: terapeuta }
        citaOrigenId?.let { cId ->
            evaluaciones.find { it.id == cId }?.let { ev ->
                evaluacion = ev
                ev.especialidadId?.let { eId -> especialidad = esps.find { it.id == eId } ?: especialidad }
                if (miTerapeutaId == null) ev.terapeutaId?.let { tId -> terapeuta = ters.find { it.id == tId } ?: terapeuta }
            }
        }
        // Nuevo paquete: mismo servicio/profesional/evaluación de origen que el que se acaba.
        renovacion?.let { r ->
            if (miTerapeutaId == null) r.terapeutaId?.let { tId -> terapeuta = ters.find { it.id == tId } ?: terapeuta }
            r.citaOrigenId?.let { cId -> evaluacion = evaluaciones.find { it.id == cId } }
            val pr = procedimientos.find { it.id == r.procedimientoId }
            if (pr != null) {
                pr.especialidadId?.let { eId -> especialidad = esps.find { it.id == eId } ?: especialidad }
                proc = pr   // dispara el prefill del servicio; luego se aplica renovPend
            } else renovPend = null
        }
        // Plan de la evaluación psicológica: su servicio y quien evaluó (suele seguir la terapia).
        prefillPlan?.let { pp ->
            if (miTerapeutaId == null) pp.terapeutaId?.let { tId -> terapeuta = ters.find { it.id == tId } ?: terapeuta }
            procedimientos.find { it.id == pp.procedimientoId }?.let { pr ->
                pr.especialidadId?.let { eId -> especialidad = esps.find { it.id == eId } ?: especialidad }
                proc = pr   // dispara el prefill del servicio; luego se aplica planPend
            }
            // Sin servicio en el plan: las sesiones y el precio se aplican al elegir uno.
        }
    }

    // Otra evaluación de origen (elegida a mano): su cita decide la sede, como en el servidor.
    LaunchedEffect(evaluacion?.id, sedeIniciada) {
        if (!multiSede || !sedeIniciada) return@LaunchedEffect
        val origen = evaluacion?.id ?: citaOrigenId
        if (origen != origenDeSede) resolverSede(origen)
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

    // Pone los campos comerciales de una vez (prefill del servicio / plantilla).
    fun ponerCampos(f: pe.saniape.app.data.staff.CamposComerciales) {
        modalidad = f.modalidad; totalSesiones = f.totalSesiones
        precioPaquete = f.precioPaquete; precioPorSesion = f.precioPorSesion
        cantidadUnidades = f.cantidadUnidades; precioUnitario = f.precioUnitario
        precioAcordado = f.precioAcordado
    }

    // Al elegir servicio: autocompletar precios + tarifario (si hay). Una vez por
    // servicio elegido: si ya se pusieron (p. ej. los puso la plantilla), no se pisan.
    LaunchedEffect(proc?.id) {
        val p = proc
        if (p != null && autollenadoDe != p.id) {
            autollenadoDe = p.id
            campaniaAplicada = null   // otra promo puede aplicar al nuevo servicio
            // Servicio único: el acordado NO se prellena (igual que la web): el precio base se
            // muestra aparte y como ayuda en el campo; vacío = se cobra el base. Cambiar de
            // servicio conserva la cantidad de unidades y la elección Paquete/Suelta.
            var f = camposServicio(p, modalidad).copy(cantidadUnidades = cantidadUnidades)
            // "Nuevo paquete": SUS valores mandan sobre el prefill del servicio.
            renovPend?.let { r ->
                r.modalidad?.takeIf { it == "Paquete" || it == "Sesión suelta" }?.let { f = f.copy(modalidad = it) }
                (r.sesionesBase ?: r.totalSesiones).takeIf { it > 0 }?.let { f = f.copy(totalSesiones = it.toString()) }
                r.precioPaquete?.let { f = f.copy(precioPaquete = it.toString()) }
                r.precioPorSesion?.let { f = f.copy(precioPorSesion = it.toString()) }
                renovPend = null
            }
            planPend?.let { pp ->
                f = f.copy(modalidad = "Paquete")
                pp.totalSesiones?.takeIf { it > 0 }?.let { f = f.copy(totalSesiones = it.toString()) }
                pp.precioPaquete?.let { f = f.copy(precioPaquete = formatoNum(it)) }
                planPend = null
            }
            ponerCampos(f)
        }
    }

    /**
     * Elegir plantilla (o "Sin plantilla" = null). Se aplica YA, aunque su servicio
     * sea el que ya estaba elegido (antes quedaba pendiente y caía sobre el SIGUIENTE
     * servicio). Parte de los valores frescos del servicio: lo que la plantilla no
     * trae vuelve a lo del servicio, no a lo de la plantilla anterior.
     */
    fun elegirPlantilla(pl: PlantillaRef?) {
        if (pl == null && plantilla == null) return
        if (plantilla == null) { dxSinPlantilla = diagnostico; terSinPlantilla = terapeuta }
        plantilla = pl
        // Servicio: el de la plantilla (las que se ofrecen siempre tienen uno activo);
        // "Sin plantilla" conserva el que estaba elegido.
        val pr = pl?.procedimientoId?.let { id -> procedimientos.find { it.id == id } } ?: proc
        if (pl != null) pr?.especialidadId?.let { eId -> especialidad = especialidades.find { it.id == eId } ?: especialidad }
        val avisos = mutableListOf<String>()
        if (miTerapeutaId == null) {
            // El de la plantilla si sigue activo; si no, el que había antes de las plantillas.
            val sugerido = pl?.terapeutaId?.let { tId -> terapeutas.find { it.id == tId } }
            if (pl?.terapeutaId != null && sugerido == null) avisos += "El profesional sugerido por la plantilla ya no está activo."
            terapeuta = sugerido ?: terSinPlantilla
            // Quien no atiende ese servicio no queda puesto (la misma regla que filtra los servicios).
            val t = terapeuta
            if (pl != null && pr != null && t != null &&
                !pe.saniape.app.data.staff.profesionalAtiende(pr.especialidadId, t.especialidadIds)
            ) {
                avisos += "${t.nombre} no atiende \"${pr.nombre}\": elige quién lo atenderá."
                terapeuta = null
            }
        }
        diagnostico = pl?.diagnostico?.takeIf { it.isNotBlank() } ?: dxSinPlantilla
        medicacion = pe.saniape.app.data.staff.medicacionDePlantilla(pl?.indicaciones).orEmpty()
        proximoControl = pe.saniape.app.data.staff.proximoControlDePlantilla(
            pl?.controlDias, hoySede(), servicioConProtocolo = pr?.controlesDias?.isNotEmpty() == true,
        )
        motivoPrecio = ""
        if (pr != null) {
            val ps = precioSedeDe(pr.id)
            val base = camposServicio(pr)
            val aplicada = if (pl == null) pe.saniape.app.data.staff.PlantillaAplicada(base, preciosOmitidos = false)
            else pe.saniape.app.data.staff.aplicarPlantilla(
                base, pl, pe.saniape.app.data.staff.tipoTratamientoDe(pr, ps), pr.tarifarios,
                mismaMoneda = mismaMonedaSede,
                precioSede = ps,
            )
            ponerCampos(aplicada.campos)
            pe.saniape.app.data.staff.avisoPreciosPlantilla(
                aplicada.preciosOmitidos, monedaClinica,
                monedasDestino.firstOrNull {
                    pe.saniape.app.data.staff.normalizarMoneda(it) != pe.saniape.app.data.staff.normalizarMoneda(monedaClinica)
                } ?: monedaSede,
                ps,
            )?.let { avisos += it }
            campaniaAplicada = null
            autollenadoDe = pr.id   // el autollenado del servicio ya no la pisa
            proc = pr
        }
        avisoPlantilla = avisos.joinToString("\n").ifBlank { null }
    }

    // TIPO del tratamiento a crear (mismo criterio que la web / TipoTratamiento):
    //  - UNIDADES:       servicio con modo_cobro 'unidades' (injerto, botox × cantidad)
    //  - SERVICIO ÚNICO: modo_cobro 'simple' con precio > 0 (blanqueamiento, profilaxis)
    //  - CONSULTA:       modo 'simple' sin precio (medicina/nutrición)
    //  - SESIONES:       el resto (fisio, ortodoncia…)
    //  (Modo de cobro EFECTIVO del servicio: el del procedimiento o el heredado de
    //  su especialidad — pe.saniape.app.data.staff.tipoTratamientoDe.)
    val precioSede = precioSedeDe(proc?.id)
    // Precio base del servicio EN LA SEDE donde se vende (el propio de la sede si tiene).
    // En una sede de OTRA moneda sin precio propio, el del servicio (moneda de la
    // clínica) no es respaldo: null = hay que escribirlo.
    val precioBaseONull: Double? = precioSede ?: proc?.precio?.takeIf { mismaMonedaSede }
    val precioBase = precioBaseONull ?: 0.0
    val tipoTrat = proc?.let { pe.saniape.app.data.staff.tipoTratamientoDe(it, precioSede) }
    val esUnidades = tipoTrat == pe.saniape.app.data.staff.TipoTratamientoNuevo.UNIDADES
    val esServUnico = tipoTrat == pe.saniape.app.data.staff.TipoTratamientoNuevo.SERVICIO_UNICO
    val esConsulta = tipoTrat == pe.saniape.app.data.staff.TipoTratamientoNuevo.CONSULTA
    val usaSesiones = tipoTrat == pe.saniape.app.data.staff.TipoTratamientoNuevo.SESIONES
    // Unidades: exige cantidad y precio por unidad (> 0) para poder crear.
    // Paquete SIN precio: nacía en S/ 0 y la ficha decía "nada que cobrar" (demo
    // dental, 28/09/2026: el servicio no tenía precio de paquete y nadie lo
    // escribió). Gratis se puede, escribiendo 0. Gemelo de TratamientoForm (web).
    val faltaPrecioPaquete = usaSesiones && modalidad == "Paquete" &&
        precioPaquete.isBlank() && precioAcordado.isBlank()
    // Pista: N sesiones × precio por sesión (no se rellena solo: el paquete suele llevar descuento).
    val referenciaPaquete = if (usaSesiones) (totalSesiones.toIntOrNull() ?: 10) * precioBase else 0.0
    // Sesión suelta / servicio único sin precio en la moneda de la sede: no se crea.
    val faltaPrecioMoneda = pe.saniape.app.data.staff.faltaPrecioEnMonedaSede(
        sinPrecioEnMoneda = proc != null && precioBaseONull == null && !mismaMonedaSede,
        tipo = tipoTrat, modalidad = modalidad, precioPorSesion = precioPorSesion, precioAcordado = precioAcordado,
    )
    val avisoMoneda = "Esta sede cobra en ${pe.saniape.app.data.staff.normalizarMoneda(monedaSede)} y el servicio " +
        "no tiene precio propio aquí: escribe el precio para poder crearlo."
    val puedeCrear = proc != null && !faltaPrecioPaquete && !faltaPrecioMoneda && (!esUnidades ||
        ((cantidadUnidades.toIntOrNull() ?: 0) > 0 && (precioUnitario.toDoubleOrNull() ?: 0.0) > 0.0))

    if (mostrarFechaInicio) DialogoFecha(onElegir = { fechaInicio = it }, onCerrar = { mostrarFechaInicio = false })
    if (mostrarFechaPrimera) DialogoFecha(onElegir = { fechaPrimera = it; primeraTocada = true }, onCerrar = { mostrarFechaPrimera = false }, inicial = fechaPrimera)
    if (mostrarHoraPrimera) DialogoHora(horaPrimera, onElegir = { horaPrimera = it; primeraTocada = true }, onCerrar = { mostrarHoraPrimera = false })
    if (mostrarFechaControl) DialogoFecha(onElegir = { proximoControl = it }, onCerrar = { mostrarFechaControl = false }, inicial = proximoControl ?: hoySede())

    // Crear: la misma acción desde el pie y desde la cabecera (con teclado).
    var enviado by remember { mutableStateOf(false) }
    fun crear() {
                            val p = proc ?: return
                            // Un solo envío: un doble toque no crea (ni aprende) dos veces.
                            if (enviado) return
                            enviado = true
                            // Diagnóstico a aprender solo si es nuevo o cambió (el precargado de la
                            // evaluación ya se aprendió ahí). Se registra tras crear, no ahora.
                            val dxNuevo = diagnostico.trim()
                            val dxAprender = dxNuevo.takeIf {
                                it.isNotEmpty() && pe.saniape.app.data.staff.claveChip(it) !=
                                    pe.saniape.app.data.staff.claveChip(diagnosticoPrevio.orEmpty())
                            }
                            // Unidades: si no se negoció un acordado, el total = cantidad × precio.
                            val totalUnidades = (cantidadUnidades.toIntOrNull() ?: 0) * (precioUnitario.toDoubleOrNull() ?: 0.0)
                            val tipoFinal = tipoTrat ?: pe.saniape.app.data.staff.tipoTratamientoDe(p, precioSede)
                            // Nunca una modalidad que contradiga el modo de cobro del servicio.
                            val modalidadFinal = pe.saniape.app.data.staff.modalidadAGuardar(tipoFinal, modalidad)
                            onGuardar(
                                TratamientoNuevo(
                                    procedimientoId = p.id,
                                    terapeutaId = if (miTerapeutaId != null) miTerapeutaId else terapeuta?.id,
                                    modalidad = modalidadFinal,
                                    totalSesiones = if (usaSesiones && modalidadFinal == "Paquete") totalSesiones.toIntOrNull() ?: 10
                                        else if (usaSesiones) 1 else null,
                                    precioPaquete = if (usaSesiones && modalidadFinal == "Paquete") precioPaquete.toDoubleOrNull() else null,
                                    // Suelta vacía = el precio del servicio en la sede (como la web:
                                    // precioPorSesionAGuardar); antes valía 0.
                                    precioPorSesion = if (usaSesiones && modalidadFinal == "Sesión suelta")
                                        precioPorSesion.toDoubleOrNull() ?: precioBaseONull else null,
                                    // Vacío = precio de lista (igual que la web): unidades → cantidad ×
                                    // precio; servicio único → el precio base del servicio.
                                    precioAcordado = precioAcordado.toDoubleOrNull()
                                        ?: if (esUnidades && totalUnidades > 0) totalUnidades
                                        else if (esServUnico) precioBaseONull else null,
                                    diagnostico = diagnostico.trim().ifBlank { null },
                                    // La cita de la agenda cuenta aunque no esté en la lista
                                    // (una Consulta que evalúa, en flujos sin Evaluación).
                                    citaOrigenId = evaluacion?.id ?: citaOrigenId,
                                    // Medicación y próximo control: solo los que trae la plantilla
                                    // (indicaciones / "control en X días"), en todos los tipos. Sin
                                    // plantilla se llenan al editar, tras atender.
                                    medicacion = medicacion.trim().ifBlank { null },
                                    // Con protocolo de controles, el próximo lo programa el protocolo.
                                    proximoControl = proximoControl.takeIf { p.controlesDias.isEmpty() },
                                    cantidadUnidades = if (esUnidades) cantidadUnidades.toIntOrNull() else null,
                                    precioUnitario = if (esUnidades) precioUnitario.toDoubleOrNull() else null,
                                    tecnicasSugeridas = plantilla?.tecnicasSesion?.takeIf { it.isNotBlank() },
                                    plantillaId = plantilla?.id,
                                    campaniaId = campaniaAplicada?.id,
                                    motivoPrecio = motivoPrecio.trim().ifBlank { null },
                                    primeraFecha = if (usaSesiones && conPrimera) fechaPrimera else null,
                                    primeraHora = if (usaSesiones && conPrimera) horaPrimera else null,
                                    tipo = tipoFinal,
                                    diagnosticoAprender = dxAprender,
                                    especialidadDiagnostico = p.especialidadId,
                                    fechaInicio = if (esUnidades) null else fechaInicio,
                                )
                            )
    }

    // Con el teclado abierto el cuerpo se achica y el botón Crear queda a la
    // vista: ver DialogoConTeclado (29/09/2026).
    pe.saniape.app.ui.DialogoConTeclado(onCancelar) {
        Column(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp).heightIn(max = 720.dp).tourAncla("tratamiento_form")
                .clip(RoundedCornerShape(Sania.shape.lg.dp)).background(c.fondo),
        ) {
            // ── Header navy (el "negro" de la marca) ──────────────────────
            Row(Modifier.fillMaxWidth().background(c.navyDark).padding(horizontal = 18.dp, vertical = 16.dp),
                verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(if (renovacion != null) "📦 Nuevo paquete" else if (prefillPlan != null) "🎯 Tratamiento del plan" else "Nuevo tratamiento", color = c.sobreNavy, fontSize = 19.sp, fontWeight = FontWeight.Bold)
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
            // Con el teclado abierto el pie puede quedar tapado: el botón sube acá.
            if (pe.saniape.app.ui.LocalTecladoEnDialogo.current) {
                Box(
                    Modifier.clip(RoundedCornerShape(Sania.shape.md.dp))
                        .background(if (puedeCrear) c.sobreNavy else c.sobreNavy.copy(alpha = 0.35f))
                        .clickable(enabled = puedeCrear) { crear() }
                        .padding(horizontal = 14.dp, vertical = 9.dp),
                ) { Text(if (esConsulta) "Crear consulta" else "Crear", color = c.navyDark, fontWeight = FontWeight.Bold, fontSize = 13.sp) }
            }
            }

            // ── Cuerpo scroll ─────────────────────────────────────────────
            Column(
                Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 14.dp),
            ) {
                // Bloque 1 · ATENCIÓN ─────────────────────────────────────
                Tarjeta(titulo = "Atención", icono = "🩺") {
                    // ⚡ Plantilla ("combo" de la clínica): autocompleta servicio + comercial + clínico.
                    // Solo las que tienen un servicio ACTIVO (las copiadas de la biblioteca nacen
                    // sin servicio hasta que la clínica le asigna uno) y no apagado en la sede.
                    val ofrecibles = pe.saniape.app.data.staff.plantillasOfrecibles(
                        plantillas, procedimientos,
                        apagadosEnSede = serviciosSede.filterValues { !it.second }.keys,
                    )
                    if (ofrecibles.isNotEmpty()) {
                        Etq("⚡ Usar plantilla (opcional)")
                        SelectorLista(listOf<PlantillaRef?>(null) + ofrecibles, plantilla,
                            { it?.nombre ?: "Sin plantilla — armar manualmente" }, "Armar manualmente…") { pl ->
                            elegirPlantilla(pl)
                        }
                        avisoPlantilla?.let { av ->
                            Text("⚠ $av", color = c.error, fontSize = 11.sp, fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(top = 4.dp))
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
                    // Typeahead + chips como la web (TratamientoForm): las patologías de la
                    // especialidad del servicio (las personalizadas mandan) y lo aprendido
                    // en esa especialidad + su rubro. Sin servicio elegido, sin chips.
                    val espDx = proc?.especialidadId?.let { id -> especialidades.find { it.id == id } }
                    val chipsDx = remember(espDx) {
                        espDx?.let {
                            pe.saniape.app.ui.clinica.chipsDeEspecialidad(
                                pe.saniape.app.ui.clinica.EspecialidadChips(it.nombre, chipsTipos = it.chipsTipos),
                            ).tipos
                        }.orEmpty()
                    }
                    // Sin servicio elegido: ni chips ni viaje (maxExtras = 0 no pide nada).
                    pe.saniape.app.ui.clinica.agenda.componentes.DiagnosticoInput(
                        value = diagnostico, onChange = { diagnostico = it }, opciones = chipsDx,
                        placeholder = "Diagnóstico que motiva este tratamiento",
                        especialidadId = proc?.especialidadId,
                        activo = proc != null,
                    )
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
                    Column(Modifier.tourAncla("tratamiento_form.servicio", valor = proc?.nombre ?: "")) {
                        SelectorLista(procsVisibles, proc, { it.nombre },
                            if (especialidad == null && terId == null) "Elige especialidad o profesional" else "Seleccionar…") { proc = it }
                    }
                }

                // ⚡ Promoción vigente (campañas): aplicar con un toque, como la web.
                // La campaña es una capa de precio — pisa los campos, nunca el precio base.
                val campsDelServicio = proc?.let { p ->
                    pe.saniape.app.data.staff.CatalogosCobroRepo.paraProcedimiento(campanias, p.id)
                } ?: emptyList()
                if (campsDelServicio.isNotEmpty() || campaniaAplicada != null) {
                    Spacer(Modifier.height(12.dp))
                    Tarjeta(titulo = "Promoción vigente", icono = "⚡") {
                        campaniaAplicada?.let { ca ->
                            Row(
                                Modifier.fillMaxWidth().clip(RoundedCornerShape(Sania.shape.sm.dp))
                                    .background(c.chipBg).padding(10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text("⚡ ${ca.nombre}: ${ca.etiqueta()}", color = c.navy, fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                                Text("✕ Quitar", color = c.error, fontSize = 12.sp, fontWeight = FontWeight.Bold,
                                    modifier = Modifier.clickable {
                                        // Quitar = volver a los precios del servicio (mismo prefill).
                                        campaniaAplicada = null
                                        proc?.let { p ->
                                            ponerCampos(camposServicio(p, modalidad).copy(cantidadUnidades = cantidadUnidades))
                                        }
                                    }.padding(4.dp))
                            }
                        }
                        if (campaniaAplicada == null) campsDelServicio.forEach { camp ->
                            Row(
                                Modifier.fillMaxWidth().padding(vertical = 3.dp)
                                    .clip(RoundedCornerShape(Sania.shape.sm.dp)).background(c.fondo)
                                    .border(1.dp, c.borde, RoundedCornerShape(Sania.shape.sm.dp))
                                    .padding(horizontal = 12.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Column(Modifier.weight(1f)) {
                                    Text(camp.nombre, color = c.texto, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                                    Text(camp.etiqueta(), color = c.textoSuave, fontSize = 11.sp)
                                }
                                Box(
                                    Modifier.clip(RoundedCornerShape(Sania.shape.pill.dp)).background(c.navy)
                                        .clickable {
                                            // Port de aplicarCampania (web): a qué campo va el precio promo.
                                            val p = proc ?: return@clickable
                                            when (camp.tipo) {
                                                "paquete_fijo" -> {
                                                    modalidad = "Paquete"
                                                    camp.cantidad?.let { totalSesiones = it.toString() }
                                                    camp.precio?.let { precioPaquete = it.toString() }
                                                    precioAcordado = ""
                                                }
                                                "precio_fijo" -> when {
                                                    esUnidades -> { precioUnitario = (camp.precio ?: 0.0).toString(); precioAcordado = "" }
                                                    usaSesiones && modalidad == "Paquete" -> { precioPaquete = (camp.precio ?: 0.0).toString(); precioAcordado = "" }
                                                    usaSesiones -> { precioPorSesion = (camp.precio ?: 0.0).toString(); precioAcordado = "" }
                                                    else -> precioAcordado = (camp.precio ?: 0.0).toString()
                                                }
                                                else -> when {   // porcentaje / monto_fijo: descuentan el base
                                                    esUnidades -> { precioUnitario = camp.precioCon(precioUnitario.toDoubleOrNull() ?: precioBase).toString(); precioAcordado = "" }
                                                    usaSesiones && modalidad == "Paquete" -> { precioPaquete = camp.precioCon(precioPaquete.toDoubleOrNull() ?: 0.0).toString(); precioAcordado = "" }
                                                    usaSesiones -> { precioPorSesion = camp.precioCon(precioPorSesion.toDoubleOrNull() ?: precioBase).toString(); precioAcordado = "" }
                                                    else -> precioAcordado = camp.precioCon(precioBase).toString()
                                                }
                                            }
                                            campaniaAplicada = camp
                                        }.padding(horizontal = 14.dp, vertical = 7.dp),
                                ) { Text("Aplicar", color = c.sobreNavy, fontSize = 12.sp, fontWeight = FontWeight.Bold) }
                            }
                        }
                    }
                }

                // Bloque 2 · adaptado al tipo de servicio ─────────────────
                if (esUnidades) {
                    Spacer(Modifier.height(12.dp))
                    val etiquetaU = proc?.unidadLabel ?: "unidades"
                    Tarjeta(titulo = "Cobro por $etiquetaU", icono = "🔢") {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Column(Modifier.weight(1f)) { Etq("Cantidad de $etiquetaU"); CampoNum(cantidadUnidades) { cantidadUnidades = it } }
                            Column(Modifier.weight(1f)) { Etq("Precio por unidad (${simboloMoneda(moneda)})"); CampoNum(precioUnitario) { precioUnitario = it } }
                        }
                        // Total en vivo (cantidad × precio unitario) — es el acordado por defecto.
                        val total = (cantidadUnidades.toIntOrNull() ?: 0) * (precioUnitario.toDoubleOrNull() ?: 0.0)
                        if (total > 0) {
                            Spacer(Modifier.height(8.dp))
                            Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(Sania.shape.sm.dp))
                                .background(c.chipBg).padding(10.dp)) {
                                Text("Total: ${simboloMoneda(moneda)} ${if (total % 1.0 == 0.0) total.toInt() else total}",
                                    color = c.texto, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                        Spacer(Modifier.height(10.dp))
                        Etq("Precio acordado (${simboloMoneda(moneda)}) — opcional")
                        CampoNum(precioAcordado, ayuda = if (total > 0) "Vacío = total ${simboloMoneda(moneda)} ${formatoNum(total)}" else "Vacío = cantidad × precio") { precioAcordado = it }
                        Text("Solo si se negoció distinto al total (cantidad × precio).",
                            color = c.textoSuave, fontSize = 10.sp)
                    }
                } else if (esServUnico) {
                    Spacer(Modifier.height(12.dp))
                    Tarjeta(titulo = "Servicio único", icono = "✨") {
                        Etq("Precio base del servicio")
                        SelectorBox(precioBaseONull?.let { "${simboloMoneda(moneda)} $it" } ?: "— (sin precio en esta sede)", bloqueado = true) {}
                        Spacer(Modifier.height(10.dp))
                        Etq(if (precioBaseONull == null) "Precio acordado (${simboloMoneda(moneda)})" else "Precio acordado (${simboloMoneda(moneda)}) — opcional")
                        CampoNum(precioAcordado, ayuda = precioBaseONull?.let { "${simboloMoneda(moneda)} ${formatoNum(it)} (precio de lista)" }) { precioAcordado = it }
                        if (faltaPrecioMoneda) {
                            Text(avisoMoneda, color = c.error, fontSize = 11.sp, fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(top = 4.dp))
                        }
                        Text("Vacío = se cobra el precio de lista; escríbelo solo si se negoció otro. " +
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
                                    { "${it.cantidadSesiones} sesiones — ${simboloMoneda(moneda)} ${it.precioTotal}" },
                                    "Personalizado o manual…") { t -> totalSesiones = t.cantidadSesiones.toString(); precioPaquete = t.precioTotal.toString() }
                                Spacer(Modifier.height(8.dp))
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Column(Modifier.weight(1f)) { Etq("N° sesiones"); CampoNum(totalSesiones) { totalSesiones = it } }
                                Column(Modifier.weight(1f)) {
                                    Etq("Precio paquete")
                                    CampoNum(precioPaquete, ayuda = if (referenciaPaquete > 0) "Ej. ${formatoNum(referenciaPaquete)}" else null) { precioPaquete = it }
                                }
                            }
                            if (faltaPrecioPaquete) {
                                Text("Pon el precio del paquete para poder crearlo. Si es gratis, escribe 0.",
                                    color = c.error, fontSize = 11.sp, fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(top = 4.dp))
                            }
                            Text("Puedes ajustar sesiones y precio; el tarifario solo los pre-llena.",
                                color = c.textoSuave, fontSize = 10.sp, modifier = Modifier.padding(top = 2.dp))
                        } else {
                            Etq("Precio por sesión")
                            CampoNum(precioPorSesion) { precioPorSesion = it }
                            if (faltaPrecioMoneda) {
                                Text(avisoMoneda, color = c.error, fontSize = 11.sp, fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(top = 4.dp))
                            }
                            Text("Se cobra por cada sesión realizada.", color = c.textoSuave, fontSize = 10.sp)
                        }
                        Spacer(Modifier.height(10.dp))
                        Etq("Precio acordado (${simboloMoneda(moneda)}) — opcional")
                        CampoNum(precioAcordado, ayuda = "Vacío = precio base") { precioAcordado = it }
                        Text("Solo si se negoció un precio distinto al base.", color = c.textoSuave, fontSize = 10.sp)
                    }
                } else if (esConsulta) {
                    Spacer(Modifier.height(12.dp))
                    Tarjeta(titulo = "Consulta", icono = "📋") {
                        // La medicación/receta NO se pide al crear: el médico aún no atendió.
                        // Se registra al EDITAR el tratamiento, después de la atención.
                        Etq("Costo de la consulta (${simboloMoneda(moneda)}) — opcional")
                        CampoNum(precioAcordado, ayuda = "Ej. 80 — vacío si es gratis") { precioAcordado = it }
                        Text("Déjalo vacío si es gratis. La medicación y el próximo control se " +
                            "registran al editar, después de atender.", color = c.textoSuave, fontSize = 10.sp)
                    }
                }

                // ¿Por qué este precio? — SOLO cuando el acordado va a la BAJA respecto
                // de la referencia (auditoría de descuentos, igual que la web).
                val referencia = when {
                    esUnidades -> (cantidadUnidades.toIntOrNull() ?: 0) * (precioUnitario.toDoubleOrNull() ?: 0.0)
                    usaSesiones && modalidad == "Paquete" -> precioPaquete.toDoubleOrNull() ?: 0.0
                    else -> precioBase
                }
                val acordadoNum = precioAcordado.toDoubleOrNull() ?: 0.0
                val hayDescuento = referencia > 0 && acordadoNum > 0 && (referencia - acordadoNum) > 0.005
                if (hayDescuento) {
                    Spacer(Modifier.height(12.dp))
                    Tarjeta(titulo = "¿Por qué este precio?", icono = "💬") {
                        OutlinedTextField(colors = coloresCampoForm(), value = motivoPrecio,
                            onValueChange = { motivoPrecio = it.take(200) },
                            placeholder = { Text("Ej. Promoción acordada, ${LocalTerminologiaPaciente.current.paciente} frecuente…", color = c.textoSuave) },
                            singleLine = true, modifier = Modifier.fillMaxWidth())
                        Text("Queda registrado: ${simboloMoneda(moneda)} ${formatoNum(referencia)} → ${simboloMoneda(moneda)} ${formatoNum(acordadoNum)}",
                            color = c.textoSuave, fontSize = 10.sp, modifier = Modifier.padding(top = 2.dp))
                    }
                }

                // Indicaciones y control que trajo la plantilla (editables; se guardan al crear
                // como medicación y próximo control, en cualquier tipo de tratamiento).
                if (proc != null && (medicacion.isNotBlank() || proximoControl != null)) {
                    Spacer(Modifier.height(12.dp))
                    Tarjeta(titulo = "Indicaciones de la plantilla", icono = "💊") {
                        Etq("Medicación / cuidados")
                        OutlinedTextField(colors = coloresCampoForm(), value = medicacion,
                            onValueChange = { medicacion = it.take(2000) }, minLines = 2,
                            modifier = Modifier.fillMaxWidth())
                        if (proc?.controlesDias?.isNotEmpty() == true) {
                            Text("Los controles los programa el protocolo del servicio.",
                                color = c.textoSuave, fontSize = 10.sp, modifier = Modifier.padding(top = 6.dp))
                        } else {
                        Spacer(Modifier.height(10.dp))
                        Etq("Próximo control")
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(Modifier.weight(1f)) {
                                CajaSelectorForm(proximoControl ?: "Sin fecha") { mostrarFechaControl = true }
                            }
                            if (proximoControl != null) {
                                Text("✕", color = c.textoSuave, fontSize = 16.sp, fontWeight = FontWeight.Bold,
                                    modifier = Modifier.clickable { proximoControl = null }.padding(horizontal = 10.dp, vertical = 6.dp))
                            }
                        }
                        }
                    }
                }

                // Fecha de inicio (no aplica a unidades — igual que la web, donde el
                // protocolo de controles la gobierna).
                if (proc != null && !esUnidades) {
                    Spacer(Modifier.height(12.dp))
                    Tarjeta(titulo = "Fecha de inicio", icono = "📅") {
                        CajaSelectorForm(fechaInicio) { mostrarFechaInicio = true }
                        Text("Anterior si ya empezó, futura si está programado.",
                            color = c.textoSuave, fontSize = 10.sp, modifier = Modifier.padding(top = 4.dp))
                    }
                }

                // PRIMERA SESIÓN en el mismo paso: sin esto había que crear el
                // tratamiento y después buscarlo para agendar (28/09/2026).
                if (usaSesiones) {
                    Spacer(Modifier.height(12.dp))
                    Tarjeta(titulo = "Primera sesión", icono = "📅") {
                        Row(Modifier.fillMaxWidth().clickable { conPrimera = !conPrimera },
                            verticalAlignment = Alignment.CenterVertically) {
                            Text(if (conPrimera) "☑" else "☐", fontSize = 20.sp,
                                color = if (conPrimera) c.navy else c.textoSuave)
                            Spacer(Modifier.width(8.dp))
                            Text("Agendarla ahora", color = c.texto, fontWeight = FontWeight.SemiBold)
                        }
                        if (!conPrimera) {
                            Text("Sin fecha todavía: al crear te ofreceremos agendarla.",
                                color = c.textoSuave, fontSize = 10.sp, modifier = Modifier.padding(top = 4.dp))
                        }
                        if (conPrimera) {
                            Spacer(Modifier.height(8.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Column(Modifier.weight(1f)) { Etq("Fecha"); CajaSelectorForm(fechaPrimera) { mostrarFechaPrimera = true } }
                                Column(Modifier.weight(1f)) { Etq("Hora"); CajaSelectorForm(horaPrimera) { mostrarHoraPrimera = true } }
                            }
                            Text("Se crea la sesión #1 con su cita. Las siguientes se agendan como siempre.",
                                color = c.textoSuave, fontSize = 10.sp, modifier = Modifier.padding(top = 4.dp))
                        }
                    }
                }
            }

            // ── Footer fijo: Cancelar + Crear a ancho completo ────────────
            // Con el teclado abierto "Crear" ya está en la cabecera (no duplicar).
            if (!pe.saniape.app.ui.LocalTecladoEnDialogo.current) {
                Box(Modifier.fillMaxWidth().height(1.dp).background(c.borde))
                Row(
                    Modifier.fillMaxWidth().background(c.superficie)
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    TextButton(onClick = onCancelar) { Text("Cancelar", color = c.textoSuave, fontWeight = FontWeight.Bold) }
                    Box(
                        Modifier.weight(1f).tourAncla("tratamiento_form.guardar").clip(RoundedCornerShape(Sania.shape.md.dp))
                            .background(if (puedeCrear) c.navy else c.borde)
                            .clickable(enabled = puedeCrear) { crear() }.padding(vertical = 13.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(if (esConsulta) "Crear consulta" else "Crear tratamiento",
                            color = if (puedeCrear) c.sobreNavy else c.textoSuave, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                    }
                }
            }
        }
    }
}

/** El cuerpo de `accion: crear` para un [TratamientoNuevo] (una sola fuente). */
private fun cuerpoDe(pacienteId: String, nuevo: TratamientoNuevo) = PacientesRepo.cuerpoCrearTratamiento(
    pacienteId = pacienteId, procedimientoId = nuevo.procedimientoId,
    terapeutaId = nuevo.terapeutaId, modalidad = nuevo.modalidad,
    totalSesiones = nuevo.totalSesiones, precioPaquete = nuevo.precioPaquete,
    precioPorSesion = nuevo.precioPorSesion, precioAcordado = nuevo.precioAcordado,
    diagnostico = nuevo.diagnostico, citaOrigenId = nuevo.citaOrigenId,
    medicacion = nuevo.medicacion, proximoControl = nuevo.proximoControl,
    cantidadUnidades = nuevo.cantidadUnidades, precioUnitario = nuevo.precioUnitario,
    tecnicasSugeridas = nuevo.tecnicasSugeridas,
    campaniaId = nuevo.campaniaId, motivoPrecio = nuevo.motivoPrecio,
    fechaInicio = nuevo.fechaInicio,
    primeraFecha = nuevo.primeraFecha, primeraHora = nuevo.primeraHora,
)

/**
 * Tras crear un tratamiento SIN su primera sesión (se desmarcó "Agendarla ahora",
 * o el tipo no la tiene: unidades / servicio único / consulta): agendar su
 * primera cita, prellenada con el profesional, la especialidad, el tratamiento
 * (servicio y "Sesión #N") y su sede. Requisito del dueño: siempre se ofrece.
 */
data class OfertaPrimeraCita(
    val tratamientoId: String,
    val pacienteId: String,
    val pacienteNombre: String?,
    val tipo: pe.saniape.app.data.staff.TipoTratamientoNuevo,
    val terapeutaId: String?,
    val especialidadId: String?,
    /** La sede que el servidor le puso al tratamiento (multisede); null = la de siempre. */
    val sedeId: String?,
    /** Fecha y hora propuestas (nunca pasadas: si ya cerró, el próximo día que atiende). */
    val fecha: String = pe.saniape.app.data.staff.hoyClinicaIso(),
    val hora: String = pe.saniape.app.ui.proximaHoraEnPunto(),
) {
    val titulo: String get() = pe.saniape.app.data.staff.textoAgendarPrimera(tipo)

    /** El formulario nativo de crear cita, prellenado. */
    fun prefill(): pe.saniape.app.ui.clinica.PrefillCita {
        return pe.saniape.app.ui.clinica.PrefillCita(
            tipo = pe.saniape.app.data.staff.tipoCitaPrimera(tipo),
            pacienteId = pacienteId, pacienteNombre = pacienteNombre,
            fecha = fecha, hora = hora,
            terapeutaId = terapeutaId, especialidadId = especialidadId,
            tratamientoId = tratamientoId, sedeId = sedeId,
        )
    }
}

/** Lo que dejó crear un tratamiento: si quedó creado y, si corresponde, la oferta de agendar. */
data class ResultadoCrearTratamiento(val creado: Boolean, val oferta: OfertaPrimeraCita? = null)

/** El id del tratamiento creado: de la respuesta, o del rechazo PARCIAL (se creó, falló lo demás). */
private fun idCreado(r: pe.saniape.app.data.offline.ResultadoEscritura): String? =
    if (r.registrada) pe.saniape.app.data.staff.idDeRespuesta(r.cuerpo)
    else r.rechazo?.datos?.let { d ->
        (d["tratamientoId"] as? kotlinx.serialization.json.JsonPrimitive)?.content?.takeIf { it.isNotBlank() && it != "null" }
            ?: pe.saniape.app.data.staff.idDeRespuesta(d)
    }

/** Después de crear (con id): contar la plantilla, aprender el diagnóstico y armar la oferta. */
private suspend fun trasCrear(
    pacienteId: String, nuevo: TratamientoNuevo, nombrePaciente: String?, id: String?, primeraAgendada: Boolean,
    respuesta: kotlinx.serialization.json.JsonObject? = null,
): OfertaPrimeraCita? {
    // El contador de la plantilla cuenta SOLO tratamientos guardados.
    nuevo.plantillaId?.let { runCatching { PacientesRepo.contarUsoPlantilla(it) } }
    aprenderDiagnosticoDe(nuevo, nombrePaciente)
    if (!pe.saniape.app.data.staff.ofrecerAgendarTrasCrear(id, primeraAgendada) || id == null) return null
    return ofertaPrimeraCitaDe(
        tratamientoId = id, pacienteId = pacienteId, pacienteNombre = nombrePaciente, tipo = nuevo.tipo,
        terapeutaId = nuevo.terapeutaId, especialidadId = nuevo.especialidadDiagnostico,
        respuesta = respuesta,
    )
}

/** La oferta de agendar para un tratamiento recién creado (con multisede, lee su sede). */
suspend fun ofertaPrimeraCitaDe(
    tratamientoId: String, pacienteId: String, pacienteNombre: String?,
    tipo: pe.saniape.app.data.staff.TipoTratamientoNuevo, terapeutaId: String?, especialidadId: String?,
    /** La respuesta del crear: el servidor nuevo devuelve `sedeId` (sin él, se lee aparte). */
    respuesta: kotlinx.serialization.json.JsonObject? = null,
): OfertaPrimeraCita {
    val multiSede = pe.saniape.app.data.staff.StaffContextoRepo.actual?.multiSede == true
    val sede = when {
        !multiSede -> null
        respuesta?.containsKey("sedeId") == true ->
            (respuesta["sedeId"] as? kotlinx.serialization.json.JsonPrimitive)?.content?.takeIf { it.isNotBlank() && it != "null" }
        else -> PacientesRepo.sedeDeTratamiento(tratamientoId)
    }
    // Propuesta según el horario de atención de su sede (o de la clínica), en su zona.
    val horario = pe.saniape.app.data.staff.parsearHorariosAtencion(PacientesRepo.horarioAtencionCrudo(sede))
    val a = pe.saniape.app.data.staff.ahoraEn(
        pe.saniape.app.data.staff.StaffContextoRepo.actual?.zonaDeSede(sede) ?: pe.saniape.app.data.staff.zonaActivaId(),
    )
    val (fecha, hora) = pe.saniape.app.data.staff.primeraSesionPropuesta(a.fecha, a.minutos, a.diaIdx, horario)
    return OfertaPrimeraCita(
        tratamientoId = tratamientoId, pacienteId = pacienteId, pacienteNombre = pacienteNombre, tipo = tipo,
        terapeutaId = terapeutaId, especialidadId = especialidadId, sedeId = sede, fecha = fecha, hora = hora,
    )
}

/**
 * Guarda el tratamiento del form (mismo endpoint y campos en TODOS los lugares
 * que lo crean: ficha, agenda, nuevo paquete). Avisa con su toast, cuenta el uso
 * de la plantilla y devuelve la oferta de agendar la primera cita si no se agendó.
 */
suspend fun guardarTratamientoNuevo(pacienteId: String, nuevo: TratamientoNuevo, nombrePaciente: String? = null): ResultadoCrearTratamiento {
    val r = PacientesRepo.crearTratamientoDetalle(cuerpoDe(pacienteId, nuevo))
    val id = idCreado(r)
    if (!r.registrada && id == null) {
        pe.saniape.app.ui.Toaster.error(r.rechazo?.error ?: "No se pudo crear el tratamiento")
        return ResultadoCrearTratamiento(creado = false)
    }
    when {
        // Parcial: el tratamiento existe, falló su primera sesión (o sus regalos): el texto del servidor lo dice.
        !r.registrada -> pe.saniape.app.ui.Toaster.error(r.rechazo?.error ?: "El tratamiento se creó, pero no todo se guardó")
        r.encolada -> Unit   // la cola ya avisó ("se registrará al volver la señal")
        nuevo.primeraFecha != null -> pe.saniape.app.ui.Toaster.exito("Tratamiento creado con su primera sesión")
        else -> pe.saniape.app.ui.Toaster.exito("Tratamiento creado")
    }
    // En la cola offline aún no hay id: no hay qué agendar todavía.
    val oferta = trasCrear(pacienteId, nuevo, nombrePaciente, if (r.encolada) null else id,
        primeraAgendada = r.registrada && nuevo.primeraFecha != null, respuesta = r.cuerpo ?: r.rechazo?.datos)
    return ResultadoCrearTratamiento(creado = true, oferta = oferta)
}

/**
 * "Crear tratamiento con este plan" (evaluación psicológica): se crea por el
 * camino de SIEMPRE (`/api/staff/tratamiento/accion`, `crear`) pero directo,
 * para leer el `id` y atarlo al plan (`/plan-tratamiento`: "✓ Tratamiento
 * creado" y los objetivos a objetivos_tratamiento). Sin señal no se crea (no
 * habría id que atar).
 */
suspend fun crearTratamientoDelPlan(
    pacienteId: String, nuevo: TratamientoNuevo, evaluacionId: String, nombrePaciente: String? = null,
): ResultadoCrearTratamiento {
    val cuerpo = cuerpoDe(pacienteId, nuevo)
    val r = pe.saniape.app.ui.conIndicador { pe.saniape.app.data.staff.EvaluacionPsicoRepo.crearTratamientoConId(cuerpo) }
    if (!r.registrada) {
        pe.saniape.app.ui.Toaster.error(r.rechazo?.error ?: "No se pudo crear el tratamiento")
        return ResultadoCrearTratamiento(creado = false)
    }
    val id = pe.saniape.app.data.staff.idDeRespuesta(r.cuerpo)
    val atado = id != null && pe.saniape.app.data.staff.EvaluacionPsicoRepo.vincularPlan(evaluacionId, id).registrada
    if (atado) pe.saniape.app.ui.Toaster.exito("Tratamiento creado con el plan de la evaluación")
    else pe.saniape.app.ui.Toaster.error("Se creó el tratamiento, pero no se pudo marcar en el plan de la evaluación")
    val oferta = trasCrear(pacienteId, nuevo, nombrePaciente, id, primeraAgendada = nuevo.primeraFecha != null, respuesta = r.cuerpo)
    return ResultadoCrearTratamiento(creado = true, oferta = oferta)
}

/**
 * "📅 Agendar la primera sesión / la cita": se ofrece al terminar de crear un
 * tratamiento sin su primera cita. "Agendar" abre el formulario nativo de crear
 * cita, prellenado ([OfertaPrimeraCita.prefill]).
 */
@Composable
fun DialogoAgendarPrimera(
    oferta: OfertaPrimeraCita,
    onAgendar: (pe.saniape.app.ui.clinica.PrefillCita) -> Unit,
    onCerrar: () -> Unit,
) {
    val c = Sania.colors
    val sesiones = oferta.tipo == pe.saniape.app.data.staff.TipoTratamientoNuevo.SESIONES
    AlertDialog(
        onDismissRequest = onCerrar,
        title = { Text(oferta.titulo, fontWeight = FontWeight.Bold) },
        text = {
            Text(
                if (sesiones) "El tratamiento quedó creado sin sesiones agendadas. ¿Agendamos la primera ahora? " +
                    "Se abre la cita con el profesional y el tratamiento ya puestos; eliges fecha y hora."
                else "El tratamiento quedó creado. ¿Agendamos su cita ahora? Se abre con el profesional y el " +
                    "tratamiento ya puestos; eliges fecha y hora.",
                color = c.textoSuave, fontSize = 13.sp,
            )
        },
        confirmButton = {
            TextButton(onClick = { onAgendar(oferta.prefill()) }) {
                Text("Agendar", color = c.navy, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = { TextButton(onClick = onCerrar) { Text("Ahora no", color = c.textoSuave) } },
        containerColor = c.superficie,
    )
}

/** "80" o "79.50" — para mostrar montos sin colas de decimales. */
private fun formatoNum(n: Double): String =
    if (n % 1.0 == 0.0) n.toInt().toString() else {
        val cent = kotlin.math.round(n * 100).toLong()
        "${cent / 100}.${(cent % 100).toString().padStart(2, '0')}"
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
    val moneda = pe.saniape.app.ui.monedaUI()
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
            EtqForm("Monto adicional (${simboloMoneda(moneda)}) — opcional"); CampoNum(monto) { monto = it }
            Spacer(Modifier.height(10.dp))
            EtqForm("Motivo / acuerdo — opcional")
            OutlinedTextField(colors = coloresCampoForm(), value = nota, onValueChange = { nota = it }, minLines = 2,
                modifier = Modifier.fillMaxWidth())
        }
    }
}

/** Lo que manda la corrección del Admin (además de sesiones/precios del editar de siempre). */
data class CorreccionTratamiento(
    val totalSesiones: Int?, val precioPaquete: Double?, val precioPorSesion: Double?, val precioAcordado: Double?,
    val cantidadUnidades: Int?, val precioUnitario: Double?,
    val diagnostico: String, val terapeutaId: String?,
)

/**
 * Editar tratamiento: N° sesiones + precios (según modalidad).
 * Con [onCorregir] (solo el ADMIN sobre un tratamiento cerrado o de una ficha de
 * baja) muestra el aviso "Estás corrigiendo un registro cerrado" y suma diagnóstico
 * y profesional; sin él, es exactamente el modal de siempre.
 */
@Composable
fun ModalEditarTratamiento(
    t: pe.saniape.app.data.staff.TratamientoPaciente,
    onCancelar: () -> Unit,
    onGuardar: (totalSesiones: Int?, precioPaquete: Double?, precioPorSesion: Double?, precioAcordado: Double?,
                diagnostico: String?, medicacion: String?, proximoControl: String?,
                cantidadUnidades: Int?, precioUnitario: Double?) -> Unit,
    onCorregir: ((CorreccionTratamiento) -> Unit)? = null,
) {
    val moneda = pe.saniape.app.ui.monedaUI()
    val c = Sania.colors
    val correccion = onCorregir != null
    var diagnosticoC by remember { mutableStateOf(t.diagnostico ?: "") }
    var terapeutaC by remember { mutableStateOf(t.terapeutaId) }
    var terapeutasC by remember { mutableStateOf<List<pe.saniape.app.data.staff.RefNombre>?>(null) }
    if (correccion) LaunchedEffect(Unit) {
        terapeutasC = runCatching { pe.saniape.app.data.staff.PacientesRepo.terapeutasActivos() }.getOrDefault(emptyList())
    }
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
                if (correccion) {
                    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(Sania.shape.sm.dp)).background(c.pendBg)
                        .padding(Sania.dim.md)) {
                        Text("⚠ ${pe.saniape.app.data.staff.AVISO_CORRECCION}", color = c.pend, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                        Text(pe.saniape.app.data.staff.DETALLE_CORRECCION, color = c.pend, fontSize = 11.sp)
                    }
                    Spacer(Modifier.height(12.dp))
                    Tarjeta(titulo = "Datos clínicos", icono = "🩺") {
                        Etq("Diagnóstico")
                        OutlinedTextField(colors = coloresCampoForm(), value = diagnosticoC, onValueChange = { diagnosticoC = it },
                            minLines = 2, modifier = Modifier.fillMaxWidth())
                        Spacer(Modifier.height(10.dp))
                        Etq("Profesional")
                        val lista = terapeutasC
                        if (lista == null) Text("Cargando…", color = c.textoSuave, fontSize = 12.sp)
                        else lista.forEach { ter ->
                            val elegido = ter.id == terapeutaC
                            Box(
                                Modifier.fillMaxWidth().padding(vertical = 3.dp).clip(RoundedCornerShape(Sania.shape.sm.dp))
                                    .background(if (elegido) c.navy.copy(alpha = 0.12f) else c.fondo)
                                    .border(1.dp, if (elegido) c.navy else c.borde, RoundedCornerShape(Sania.shape.sm.dp))
                                    .clickable { terapeutaC = ter.id }.padding(horizontal = 12.dp, vertical = 10.dp),
                            ) { Text((if (elegido) "✓ " else "") + ter.nombre, color = c.texto, fontSize = 13.sp) }
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                }
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
                            Column(Modifier.weight(1f)) { Etq("Precio por unidad (${simboloMoneda(moneda)})"); CampoNum(precioUnitario) { precioUnitario = it } }
                        }
                        Spacer(Modifier.height(10.dp))
                        Etq("Precio acordado (${simboloMoneda(moneda)})"); CampoNum(precioAcordado) { precioAcordado = it }
                        Text("El acordado manda sobre cantidad × precio si se negoció distinto.",
                            color = c.textoSuave, fontSize = 10.sp)
                    }
                } else if (t.esServicioUnico) {
                    Tarjeta(titulo = "Servicio único", icono = "✨") {
                        Etq("Precio acordado (${simboloMoneda(moneda)})"); CampoNum(precioAcordado) { precioAcordado = it }
                        Text("Precio base del servicio: ${simboloMoneda(moneda)} ${t.precioBase ?: 0.0}.",
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
                        Etq("Precio acordado (${simboloMoneda(moneda)}) — opcional"); CampoNum(precioAcordado) { precioAcordado = it }
                        Text("Solo si se negoció un precio distinto al base.", color = c.textoSuave, fontSize = 10.sp)
                    }
                } else {
                    // EDITAR (ajuste administrativo) = solo el costo. Lo clínico (diagnóstico/
                    // medicación/próximo control) se registra en "Registrar atención" (paso Control).
                    Tarjeta(titulo = "Ajuste de la consulta", icono = "💲") {
                        Etq("Costo de la consulta (${simboloMoneda(moneda)})"); CampoNum(precioAcordado) { precioAcordado = it }
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
                            if (onCorregir != null) {
                                onCorregir(CorreccionTratamiento(
                                    totalSesiones = if (esSesiones) totalSesiones.toIntOrNull() else null,
                                    precioPaquete = if (esSesiones && esPaquete) precioPaquete.toDoubleOrNull() else null,
                                    precioPorSesion = if (esSesiones && !esPaquete) precioPorSesion.toDoubleOrNull() else null,
                                    precioAcordado = precioAcordado.toDoubleOrNull(),
                                    cantidadUnidades = if (esUnidades) cantidadUnidades.toIntOrNull() else null,
                                    precioUnitario = if (esUnidades) precioUnitario.toDoubleOrNull() else null,
                                    diagnostico = diagnosticoC.trim(),
                                    terapeutaId = terapeutaC,
                                ))
                                return@clickable
                            }
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
    val moneda = pe.saniape.app.ui.monedaUI()
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
    var enviadoConsulta by remember { mutableStateOf(false) }

    fun msISO(ms: Long): String {
        val d = kotlinx.datetime.Instant.fromEpochMilliseconds(ms).toLocalDateTime(kotlinx.datetime.TimeZone.UTC).date
        return "${d.year}-${d.monthNumber.toString().padStart(2, '0')}-${d.dayOfMonth.toString().padStart(2, '0')}"
    }

    if (mostrarFechaCita || mostrarProxControl) {
        val esCita = mostrarFechaCita
        // Abre en la fecha que ya tiene el campo (la de la cita o el próximo control).
        val estadoP = androidx.compose.material3.rememberDatePickerState(
            initialSelectedDateMillis = pe.saniape.app.data.staff.isoAMillisUtc(if (esCita) fecha else proximoControl))
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
                    OutlinedTextField(colors = coloresCampoForm(), value = diagnostico, onValueChange = { diagnostico = it },
                        placeholder = { Text("Hallazgos / diagnóstico", color = c.textoSuave) },
                        minLines = 2, modifier = Modifier.fillMaxWidth())
                    Spacer(Modifier.height(10.dp))
                    Etq("Medicación / receta")
                    OutlinedTextField(colors = coloresCampoForm(), value = medicacion, onValueChange = { medicacion = it },
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
                        Etq("Costo de la consulta (${simboloMoneda(moneda)}) — opcional")
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
                            // Un solo envío: un doble toque no guarda (ni aprende) dos veces.
                            if (enviadoConsulta) return@clickable
                            enviadoConsulta = true
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
private fun CampoNum(value: String, ayuda: String? = null, onChange: (String) -> Unit) {
    OutlinedTextField(colors = coloresCampoForm(),
        value = value, onValueChange = { onChange(it.filter { ch -> ch.isDigit() || ch == '.' }) },
        // Ayuda gris (placeholder): se ve mientras el campo está vacío y NUNCA se guarda.
        placeholder = ayuda?.let { a -> { Text(a, color = Sania.colors.textoSuave) } },
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
