package pe.saniape.app.ui.clinica

import pe.saniape.app.data.staff.LocalTerminologiaPaciente
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
import pe.saniape.app.data.staff.aChips
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
import pe.saniape.app.tutoriales.tourAncla
import pe.saniape.app.data.staff.simboloMoneda
import pe.saniape.app.data.staff.monedaDeSede
import pe.saniape.app.data.staff.zonaDeSede

private val TIPOS = listOf("Consulta", "Evaluación", "Sesión")

/** Info visual de cada tipo de cita (icono + descripción), como las tarjetas de la web. */
private data class TipoInfo(
    val valor: String, val icono: String, val desc: String,
    /** Nombre fijo (la «Atención directa»); null = el del flujo de la clínica. */
    val etiqueta: String? = null,
)

/**
 * ⚡ La atención directa de psicología (AtencionDirecta.kt): se guarda como
 * Consulta, sin tratamiento; se atiende con «▶ Atender».
 */
private val TIPO_ATENCION_DIRECTA_INFO = TipoInfo(
    pe.saniape.app.data.staff.TIPO_ATENCION_DIRECTA, "📝", "Atender y anotar evolución", etiqueta = "Atención directa",
)
private val TIPOS_INFO = listOf(
    TipoInfo("Consulta", "💬", "El @ explica su caso"),
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
    /** Multisede: la sede del tratamiento (su primera cita va ahí si el usuario puede agendar en ella). */
    val sedeId: String? = null,
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
    val moneda = pe.saniape.app.ui.monedaUI()
    val c = Sania.colors
    val scope = rememberCoroutineScope()

    // El botón/gesto "Atrás" del sistema cierra el formulario (app nativa).
    ManejarAtras(activo = true, onAtras = onCancelar)
    // Formulario a pantalla completa: la barra "📝 Dar indicaciones" no va encima.
    pe.saniape.app.ui.clinica.recetas.OcultarOfertaReceta()

    // El profesional queda fijado salvo al Admin (ContextoStaff.profesionalFijo).
    val profFijo = ctx.profesionalFijo

    var pacientes by remember { mutableStateOf<List<RefNombre>>(emptyList()) }
    var terapeutas by remember { mutableStateOf<List<TerapeutaRef>>(emptyList()) }
    var especialidadesClinica by remember { mutableStateOf<List<EspecialidadRef>>(emptyList()) }
    var tratamientos by remember { mutableStateOf<List<TratamientoRef>>(emptyList()) }
    // De qué paciente llegó BIEN la lista de tratamientos (si la consulta falla no
    // se afirma "aún no tiene tratamiento").
    var tratamientosDe by remember { mutableStateOf<String?>(null) }
    // "＋ Crear tratamiento" (paciente sin tratamiento en una Sesión, psicología).
    var crearTratamiento by remember { mutableStateOf(false) }
    // Creándolo: el botón queda desactivado (con progreso) hasta que vuelva la
    // petición y se recargue la lista. Sin esto, un doble toque creaba dos.
    var creandoTratamiento by remember { mutableStateOf(false) }
    // El tratamiento creado aquí y el costo de su primera atención (costoPrimeraSesion).
    var creadoAqui by remember { mutableStateOf<Pair<String, Double?>?>(null) }
    // «Crear tratamiento y agendar cita» en un toque (CitaUnPaso.kt): lo que se va
    // eligiendo en ese formulario (profesional, servicio, modalidad). La cita lo
    // sigue mientras está abierto, para agendarse en el mismo toque con el
    // profesional y la videollamada que tendría al volver y tocar «Guardar cita».
    var tratEnCurso by remember { mutableStateOf<TratEnCurso?>(null) }
    // La modalidad (cita virtual) del servicio que se está eligiendo ahí.
    var servicioEnCurso by remember { mutableStateOf<pe.saniape.app.data.staff.ServicioModalidad?>(null) }
    // De qué servicio terminó la consulta (bien o mal): mientras no sea el elegido,
    // «Crear tratamiento y agendar cita» espera (servicioPendiente).
    var servicioEnCursoDe by remember { mutableStateOf<String?>(null) }
    // Lo que la verificación fresca dijo al tocar el botón (se borra al cambiar la cita).
    var errorUnPaso by remember { mutableStateOf<String?>(null) }
    var precioConsulta by remember { mutableStateOf(0.0) }
    var precioEvaluacion by remember { mutableStateOf(40.0) }
    // Campañas vigentes: el costo por defecto sale con la MEJOR promo aplicada (como la web).
    // (nombres cortos vía import: `pe` es también el destructure de precios más abajo)
    var campanias by remember { mutableStateOf<List<CampaniaApp>>(emptyList()) }
    var promoAplicada by remember { mutableStateOf<CampaniaApp?>(null) }
    // Cuánto dura cada tipo de cita en esta clínica (DuracionCita.kt, como la web) y
    // los servicios de una vez (el paciente nuevo que viene directo al procedimiento).
    var duracionesCfg by remember { mutableStateOf(pe.saniape.app.data.staff.DuracionesCita()) }
    var serviciosUnaVez by remember { mutableStateOf<List<pe.saniape.app.data.staff.ServicioUnaVez>>(emptyList()) }
    LaunchedEffect(Unit) { duracionesCfg = AgendaRepo.duracionesCita() }
    LaunchedEffect(Unit) {
        if (ctx.puede("sesiones")) serviciosUnaVez = pe.saniape.app.data.staff.ServicioEnVisitaRepo.servicios()
    }

    var tipo by remember { mutableStateOf(prefill?.tipo ?: "Consulta") }
    // ¿Quien agenda tocó el tipo? Entonces la atención directa ya no se lo cambia.
    var tipoTocado by remember { mutableStateOf(false) }
    // El tipo lo puso solo el paciente (Sesión de su tratamiento vigente): se dice en una línea.
    var tipoAuto by remember { mutableStateOf(false) }
    // El tipo que tenía el formulario antes de pasar solo a Sesión (elegir otro paciente lo devuelve).
    var tipoAntesAuto by remember { mutableStateOf<String?>(null) }
    // Duración EDITABLE (antes era fija 15/60 y una sesión de 30 no se podía agendar).
    // Default por tipo (igual que la web: Consulta 15, resto 60); si el usuario la
    // tocó, cambiar de tipo ya no se la pisa.
    var duracion by remember { mutableStateOf(if ((prefill?.tipo ?: "Consulta") == "Consulta") 15 else 60) }
    var duracionTocada by remember { mutableStateOf(false) }
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
    // Cita virtual (2026-10-16): "¿Dónde se atiende?". Arranca con la modalidad del
    // servicio del tratamiento; lo que se toca a mano ya no lo pisa el servicio.
    val meetPlan = ctx.can("citasVirtualesMeet")
    var virt by remember { mutableStateOf(pe.saniape.app.data.staff.VALOR_MODALIDAD_INICIAL) }

    var mostrarFecha by remember { mutableStateOf(false) }
    var mostrarHora by remember { mutableStateOf(false) }
    var mostrarHorarios by remember { mutableStateOf(false) }    // modal "ver horarios" de todos
    var guardando by remember { mutableStateOf(false) }
    var mensaje by remember { mutableStateOf<String?>(null) }
    // Recién registrado desde el buscador: "Completar datos de…" (paciente, ¿sin DNI?).
    var completarDatos by remember { mutableStateOf<Pair<RefNombre, Boolean>?>(null) }
    // Registrado POR DOCUMENTO aquí: el diálogo ya no se abre solo (nombre, DNI y
    // teléfono quedaron en la tarjeta del alta); «✎ Completar su ficha» a un toque.
    var recienRegistrado by remember { mutableStateOf<RefNombre?>(null) }

    // Clínica multi-especialidad → mostrar selector que filtra los profesionales.
    val multiEspecialidad = especialidadesClinica.size > 1
    // Las que se ofrecen en el selector: todas, o las del profesional que agenda
    // lo suyo. Con varias (OMER: ozonoterapia y estética) también elige; con una
    // sola se deduce y no se pregunta. Sin ninguna asignada, todas (como la web).
    val misEspecialidadIds = profFijo?.let { yo -> terapeutas.find { it.id == yo }?.especialidadIds }.orEmpty()
    val especialidadesElegibles = if (misEspecialidadIds.isEmpty()) especialidadesClinica
        else especialidadesClinica.filter { it.id in misEspecialidadIds }

    // El flujo que rige ESTA cita: el de la especialidad elegida si tiene uno
    // propio, si no el de la clínica. Es lo que permite que en una misma
    // clínica fisioterapia entre por Consulta y odontología por Diagnóstico.
    val flujoEfectivo = remember(ctx.flujo, especialidad) {
        ctx.flujo.paraEspecialidad(especialidad?.flujoPreset, especialidad?.nombre)
    }
    // Los tipos que se ofrecen de verdad. Antes se mostraban los tres SIEMPRE:
    // a RENOVA, que no hace consultas, se le ofrecía "Consulta"; y con los
    // nombres internos, no los suyos.
    // ⚡ ATENCIÓN DIRECTA (psicología, sin interruptor): la especialidad elegida →
    // la del profesional → la de la clínica. Otro rubro (DALU, RENOVA): el
    // formulario de siempre. Gemelo de `atencionRapidaForm` (CitaForm web).
    val atencionDirectaForm = remember(especialidadesClinica, especialidad?.id, terapeuta?.id, terapeutas) {
        val prof = (terapeuta?.id ?: profFijo)?.let { id -> terapeutas.find { it.id == id } }
        pe.saniape.app.data.staff.atencionDirectaAplica(pe.saniape.app.data.staff.atencionEsPsicologia(
            especialidadesClinica.map { Triple(it.id, it.rubro, it.nombre) }, especialidad?.id, prof?.especialidadIds.orEmpty(),
        ))
    }
    val tiposVisibles = remember(flujoEfectivo, ctx.usaSesiones, atencionDirectaForm) {
        val base = TIPOS_INFO.filter { flujoEfectivo.usaTipo(it.valor) }
            .filter { it.valor != "Sesión" || ctx.usaSesiones }
            .map { if (atencionDirectaForm && it.valor == "Sesión") it.copy(desc = "Dentro de un tratamiento o paquete") else it }
        // Atención directa PRIMERO (aunque el flujo no tenga Consulta); lo demás detrás.
        pe.saniape.app.data.staff.tiposConAtencionDirecta(base, atencionDirectaForm, TIPO_ATENCION_DIRECTA_INFO) { it.valor }
    }
    // La duración de la configuración (la de la especialidad de la cita → la de la
    // clínica → el respaldo del tipo), como la web; antes 15 / 60 fijos. La atención
    // directa dura lo que una sesión, no lo que una consulta de entrada.
    val espDuracion = tratamiento?.especialidadId ?: especialidad?.id
    LaunchedEffect(tipo, atencionDirectaForm, duracionesCfg, espDuracion) {
        if (!duracionTocada) duracion = pe.saniape.app.data.staff.duracionSugerida(
            if (atencionDirectaForm && tipo == "Consulta") "Sesión" else tipo, duracionesCfg, espDuracion,
        )
    }
    // Psicología (sin Consulta ni Evaluación): la Sesión es la primera cita, así que
    // ahí también se ofrece el alta rápida del paciente. Con Consulta/Evaluación el
    // paciente nuevo entra por ahí (sin alta en Sesión).
    val soloSesion = pe.saniape.app.data.staff.entraSoloPorSesion(tiposVisibles.map { it.valor })
    // Una Sesión sin tratamiento (en CUALQUIER clínica): se dice, se ofrece crearlo
    // aquí (o a quién pedírselo) y no se guarda huérfana. Gemelo de la web.
    val avisoTratamiento = pe.saniape.app.data.staff.avisoSinTratamiento(
        tipo = tipo,
        pacienteElegido = paciente != null,
        tratamientosCargados = paciente != null && tratamientosDe == paciente?.id,
        hayTratamientos = tratamientos.isNotEmpty(),
        tratamientoPrefijado = prefill?.tratamientoId != null,
        puedeSesiones = ctx.puede("sesiones"),
    )
    // La otra puerta de la clínica (su Consulta/Evaluación), para sugerirla en el aviso.
    val entradaClinica = tiposVisibles.firstOrNull { it.valor != "Sesión" }?.let { it.etiqueta ?: flujoEfectivo.nombreTipo(it.valor) }
    // «＋ Ampliar tratamiento anterior» (SesionSinTratamiento.kt): el paciente no
    // tiene tratamiento vigente pero sí uno TERMINADO por sesiones. Misma regla y
    // permisos que «➕ Ampliar» de la ficha. Solo se consulta con el aviso
    // «＋ Crear tratamiento» a la vista (dos lecturas chicas de ESE paciente).
    var ampliables by remember { mutableStateOf<List<pe.saniape.app.data.staff.TratAmpliable>>(emptyList()) }
    var ampliablesDe by remember { mutableStateOf<String?>(null) }
    val pedirAmpliables = avisoTratamiento == pe.saniape.app.data.staff.AvisoSinTratamiento.OFRECER_CREAR
    LaunchedEffect(paciente?.id, pedirAmpliables) {
        val pid = paciente?.id
        if (pid == null || !pedirAmpliables) { ampliables = emptyList(); ampliablesDe = null; return@LaunchedEffect }
        if (ampliablesDe == pid) return@LaunchedEffect
        ampliables = pe.saniape.app.data.staff.tratamientosAmpliables(
            pe.saniape.app.data.staff.AtencionDirectaRepo.tratamientosTerminados(pid),
        )
        ampliablesDe = pid
    }
    val ofreceAmpliar = paciente != null && ampliablesDe == paciente?.id &&
        pe.saniape.app.data.staff.ofreceAmpliarAnterior(avisoTratamiento, pacienteInactivo = false, ampliables = ampliables.size)
    // Lo elegido (por defecto el más reciente), cuántas sesiones y el monto (vacío = la lista).
    var ampliarId by remember(paciente?.id) { mutableStateOf<String?>(null) }
    var ampliarSesiones by remember(paciente?.id) { mutableStateOf("1") }
    var ampliarMonto by remember(paciente?.id) { mutableStateOf("") }
    var ampliando by remember { mutableStateOf(false) }
    val tratAmpliar = ampliables.firstOrNull { it.id == ampliarId } ?: ampliables.firstOrNull()
    // La lista solo vale si la cita cobra en la moneda del catálogo (la de la clínica).
    val listaAmpliar = tratAmpliar?.let {
        pe.saniape.app.data.staff.precioListaAmpliacion(
            it, ampliarSesiones.trim().toIntOrNull() ?: 0, otraMoneda = moneda != ctx.monedaDeSede(null),
        )
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
    var sedeId by remember {
        mutableStateOf(prefill?.sedeId?.takeIf { s -> sedeEstado.multiSede && sedeEstado.sedes.any { it.id == s } } ?: sedePorDefecto())
    }
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
        val terId = terapeuta?.id ?: profFijo
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
            // Admin que también atiende: él mismo precargado,
            // pero lo puede cambiar. Como la web.
            if (profFijo == null && prefill?.terapeutaId == null) {
                ctx.miTerapeutaId?.let { yo -> terapeutas.find { it.id == yo } }?.let { terapeuta = it }
            }
        } catch (_: Exception) {}
    }

    // ⚡ Atención directa: el tipo lo decide el PACIENTE. Con tratamiento vigente
    // por sesiones → Sesión; sin él (o sin paciente) → atención directa. Nunca pisa
    // un tipo que quien agenda tocó ni uno prefijado (derivación, «Repetir»…).
    // `tipoInicialAtencionRapida` de la web.
    LaunchedEffect(atencionDirectaForm, paciente?.id, tratamientosDe, tratamientos) {
        if (!atencionDirectaForm || tipoTocado || prefill != null) return@LaunchedEffect
        val vigentes = if (paciente != null && tratamientosDe == paciente?.id)
            tratamientos.filter { it.estado == "Activo" }.map { it.modalidad } else emptyList()
        val deseado = pe.saniape.app.data.staff.tipoInicialAtencionDirecta(tiposVisibles.map { it.valor }, vigentes)
        tipoAuto = deseado == "Sesión"
        if (deseado != tipo) tipo = deseado
    }
    // Sin precio de Consulta: la atención directa sugiere el de la sesión de
    // psicoterapia (editable). Solo se piden los servicios en psicología.
    var precioSugeridoDirecta by remember { mutableStateOf<Double?>(null) }
    LaunchedEffect(atencionDirectaForm, especialidad?.id) {
        precioSugeridoDirecta = if (!atencionDirectaForm) null else pe.saniape.app.data.staff.precioSugeridoAtencionDirecta(
            pe.saniape.app.data.staff.AtencionDirectaRepo.serviciosPrecio(), especialidad?.id,
        )
    }
    val precioConsultaEf = if (atencionDirectaForm && precioConsulta <= 0) precioSugeridoDirecta ?: precioConsulta else precioConsulta

    // Costo por defecto según tipo, con la MEJOR campaña vigente ya descontada.
    // (La clave del precio solo cuenta en psicología: en otro rubro, como siempre, por tipo.)
    LaunchedEffect(tipo, if (atencionDirectaForm) precioConsultaEf else null) {
        if (tipo == "Sesión") { costo = "0"; promoAplicada = null; return@LaunchedEffect }
        val base = if (tipo == "Evaluación") precioEvaluacion else precioConsultaEf
        val (precio, promo) = CatalogosCobroRepo.precioCitaConCampania(campanias, tipo, base)
        promoAplicada = promo
        costo = precio.toString()
    }
    // Tratamientos del paciente (para tipo Sesión / control vinculado)
    LaunchedEffect(paciente?.id) {
        val p = paciente
        tratamientosDe = null
        tratamientos = if (p != null) try {
            AgendaRepo.tratamientosAgendables(p.id).also { tratamientosDe = p.id }
        } catch (e: kotlin.coroutines.cancellation.CancellationException) {
            throw e
        } catch (_: Exception) { emptyList() } else emptyList()
        // Si el prefill trae un tratamiento (p.ej. control que nace de un tratamiento), enlazarlo.
        prefill?.tratamientoId?.let { tid -> tratamiento = tratamientos.find { it.id == tid } }
    }

    // Tipo y tratamiento según el paciente (PrefillCita.kt, gemelo de
    // lib/prefill-cita.ts): con un plan activo por sesiones pasa SOLO a Sesión, y con
    // un único tratamiento activo, ese queda elegido. Nunca pisa un tipo que quien
    // agenda tocó ni uno prefijado; en psicología lo decide la atención directa.
    LaunchedEffect(paciente?.id, tratamientosDe, especialidad?.id, tiposVisibles) {
        if (atencionDirectaForm) return@LaunchedEffect
        val p = paciente
        if (p == null) {
            // Se quitó el paciente: si el tipo lo había puesto la regla, se deshace.
            if (tipoAuto) { tipoAntesAuto?.let { tipo = it }; tipoAuto = false; tipoAntesAuto = null }
            return@LaunchedEffect
        }
        if (tratamientosDe != p.id) return@LaunchedEffect
        val deseado = pe.saniape.app.data.staff.tipoSegunPaciente(
            pe.saniape.app.data.staff.tratamientosQueDecidenTipo(tratamientos, especialidad?.id).map { it.modalidad },
            tipoInicial = tipoAntesAuto ?: tipo,
            tiposVisibles = tiposVisibles.map { it.valor },
            tocadoAMano = tipoTocado, prefijado = prefill != null,
        ) ?: return@LaunchedEffect
        if (deseado == "Sesión") {
            if (tipo != "Sesión") tipoAntesAuto = tipo
            tipoAuto = true
        } else {
            tipoAuto = false; tipoAntesAuto = null
        }
        tipo = deseado
    }
    LaunchedEffect(tipo, tratamientosDe, tratamientos) {
        if (tipo != "Sesión" || tratamiento != null) return@LaunchedEffect
        val p = paciente ?: return@LaunchedEffect
        if (tratamientosDe != p.id) return@LaunchedEffect
        val id = pe.saniape.app.data.staff.tratamientoInicial(
            prefill?.tratamientoId, tratamientos.filter { it.estado == "Activo" }.map { it.id },
        )
        tratamientos.firstOrNull { it.id == id }?.let { tratamiento = it }
    }

    // «＋ Crear tratamiento» abierto: el tratamiento que se está armando ahí.
    val enCurso = if (crearTratamiento) tratEnCurso else null
    LaunchedEffect(enCurso?.procedimientoId) {
        val procId = enCurso?.procedimientoId ?: run { servicioEnCurso = null; servicioEnCursoDe = null; return@LaunchedEffect }
        try {
            servicioEnCurso = AgendaRepo.modalidadDeServicio(procId)
        } catch (e: kotlin.coroutines.cancellation.CancellationException) {
            throw e
        } catch (_: Exception) {
            // Sin red: no se toca servicioEnCurso, así la cita conserva su modalidad
            // (lo que ya muestra el selector) y el botón deja de esperar.
        }
        servicioEnCursoDe = procId
    }
    // El servicio de la cita (el de su tratamiento, o el del que se está creando)
    // decide cómo arranca el selector.
    val servicioCita = if (enCurso != null) servicioEnCurso else tratamiento?.servicio
    LaunchedEffect(tratamiento?.id, servicioCita, meetPlan) {
        if (!virt.tocado) virt = pe.saniape.app.data.staff.valorModalidadDeServicio(servicioCita, meetPlan)
    }

    // Profesional por defecto de una cita de tratamiento (Sesión): el del
    // tratamiento → el que hizo su evaluación/diagnóstico → lo de siempre.
    // Gemelo de la web (ProfesionalSugerido.kt). El profesional vinculado queda
    // fijado a sí mismo, así que ahí no se consulta nada.
    val quiereSugerencia = tipo == "Sesión" && profFijo == null
    LaunchedEffect(paciente?.id, quiereSugerencia) {
        val p = paciente
        evaluaciones = if (p != null && quiereSugerencia) {
            runCatching {
                AgendaRepo.evaluacionesPaciente(p.id, ctx.flujo.usaConsulta && !ctx.flujo.usaEvaluacion)
            }.getOrDefault(emptyList())
        } else emptyList()
    }
    val sugerencia = remember(quiereSugerencia, paciente?.id, tratamiento, evaluaciones, terapeutas, especialidad, enCurso?.terapeutaId) {
        if (!quiereSugerencia || paciente == null) null
        else profesionalSugerido(
            // Con «＋ Crear tratamiento» abierto, el profesional elegido ahí.
            tratamientoTerapeutaId = if (enCurso != null) enCurso.terapeutaId else tratamiento?.terapeutaId,
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

    // "＋ Crear tratamiento": el MISMO form de la ficha, prellenado con lo que ya se
    // eligió en la cita, solo con servicios por sesiones de su especialidad. Sin
    // "Agendarla ahora": la sesión #1 es esta cita.
    completarDatos?.let { (nuevo, sinDni) ->
        DialogoCompletarDatosPaciente(
            pacienteId = nuevo.id, nombreInicial = nuevo.nombre, sinDni = sinDni,
            puedeAgregarDni = ctx.puede("datos_personales"),
            pais = pe.saniape.app.data.staff.paisDeFila(sedeId),
            chipsTipos = remember(especialidadesClinica) { chipsDeClinica(especialidadesClinica.map { it.aChips() }).tipos },
            chipsSintomas = remember(especialidadesClinica) { chipsDeClinica(especialidadesClinica.map { it.aChips() }).sintomas },
            onListo = { completarDatos = null },
            telefonoInicial = nuevo.telefono,
            onDniGuardado = { dni, nombre ->
                // El DNI (y quizá el nombre del padrón) a la lista y al elegido.
                val act = nuevo.copy(nombre = nombre, dni = dni)
                pacientes = pacientes.map { if (it.id == nuevo.id) act else it }
                if (paciente?.id == nuevo.id) paciente = act
            },
        )
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

    // «＋ Ampliar tratamiento anterior»: la MISMA acción que «➕ Ampliar» de la ficha
    // (suma sesiones y monto, lo deja Activo). Queda elegido: esta cita es su
    // siguiente sesión, con la fecha, hora y profesional ya puestos.
    fun ampliarAnterior() {
        val t = tratAmpliar ?: return
        val pac = paciente ?: return
        if (ampliando) return
        val pedido = pe.saniape.app.data.staff.pedidoAmpliacion(ampliarSesiones, ampliarMonto, listaAmpliar, moneda)
        if (pedido is pe.saniape.app.data.staff.PedidoAmpliacion.Error) { mensaje = pedido.error; return }
        val ok = pedido as pe.saniape.app.data.staff.PedidoAmpliacion.Ok
        ampliando = true
        mensaje = null
        scope.launch {
            try {
                val r = pe.saniape.app.data.staff.PacientesRepo.ampliarTratamientoDetalle(t.id, ok.sesiones, ok.monto, "desde Nueva cita")
                if (paciente?.id != pac.id) return@launch
                when {
                    r.rechazo != null -> mensaje = "No se pudo ampliar el tratamiento: ${r.rechazo!!.error}"
                    !r.registrada -> mensaje = "No se pudo ampliar el tratamiento. Intenta de nuevo."
                    // En la cola (sin señal): la cita no se ata a un tratamiento aún terminado.
                    r.encolada -> mensaje = "El tratamiento se ampliará al recuperar la señal; agenda la sesión después."
                    else -> {
                        val lista = runCatching { AgendaRepo.tratamientosAgendables(pac.id) }.getOrNull()
                            ?.takeIf { l -> l.any { it.id == t.id } }
                            // Aún no se ve (réplica, caché): se agrega el ampliado tal cual.
                            ?: (tratamientos + TratamientoRef(
                                id = t.id, procedimiento = t.servicio ?: "Tratamiento", modalidad = t.modalidad.orEmpty(),
                                terapeutaId = t.terapeutaId, especialidadId = t.especialidadId, diagnostico = t.diagnostico,
                                fechaInicio = t.fechaInicio, totalSesiones = (t.totalSesiones ?: 0) + ok.sesiones,
                                estado = "Activo",
                            )).distinctBy { it.id }
                        tratamientos = lista
                        tratamientosDe = pac.id
                        tratamiento = lista.firstOrNull { it.id == t.id }
                        pe.saniape.app.ui.Toaster.exito(
                            "Tratamiento ampliado: +${ok.sesiones} ${if (ok.sesiones == 1) "sesión" else "sesiones"}" +
                                (if (ok.monto > 0) " por ${pe.saniape.app.ui.clinica.agenda.modales.textoSoles(ok.monto, moneda)}" else " sin costo extra") +
                                ". Esta cita queda como su siguiente sesión.",
                        )
                    }
                }
            } finally {
                ampliando = false
            }
        }
    }

    /**
     * Guarda la cita. Devuelve null si quedó guardada, o el error (que también
     * queda a la vista arriba del formulario). La misma acción desde el botón del
     * final, desde la cabecera (con el teclado abierto) y desde «Crear tratamiento
     * y agendar cita».
     *
     * [ov]: el tratamiento recién creado con «Crear tratamiento y agendar cita»
     * (CitaUnPaso.kt). La cita es su primera atención, igual que al volver aquí y
     * tocar «Guardar cita»; viaja aparte porque la pantalla todavía no lo tiene
     * elegido. Su horario ya se verificó con datos frescos antes de crearlo.
     */
    suspend fun guardarCita(ov: TratCreadoEnCita? = null, textoExito: String = "Cita agendada"): String? {
        fun fallar(m: String): String { mensaje = m; return m }
        if (guardando) return "La cita ya se está guardando."
        mensaje = null
        val p = paciente ?: return fallar("Elige un ${ctx.terminologiaPaciente.paciente}")
        if (ov != null && tipo != "Sesión") return fallar("La cita ya no es una sesión: revisa el tipo de cita.")
        if (ov == null) {
            if (tipo == "Sesión" && tratamiento == null && tratamientos.isNotEmpty()) return fallar("Elige el tratamiento")
            if (creandoTratamiento) return fallar("Espera: se está creando el tratamiento.")
        }
        if (ampliando) return fallar("Espera: se está ampliando el tratamiento.")
        if (ov == null) {
            pe.saniape.app.data.staff.mensajeFaltaTratamiento(avisoTratamiento, ctx.terminologiaPaciente.paciente)?.let {
                return fallar(if (ofreceAmpliar) "Amplía su tratamiento anterior o crea uno nuevo para agendar la sesión." else it)
            }
        }
        // Clínica que mezcla odontología con otra especialidad: la
        // cita tiene que decir de cuál es, porque de eso depende que
        // al completarla se abra el odontograma (citaEsDental). Igual
        // que la web. El profesional agenda lo suyo (su especialidad
        // sale de él) y la sesión la toma de su tratamiento.
        val mixtaDental = ctx.mapaDental.ids.isNotEmpty() && !ctx.mapaDental.solo
        if (mixtaDental && multiEspecialidad && profFijo == null && tipo != "Sesión" &&
            especialidad == null && terapeuta?.especialidadIds?.singleOrNull() == null
        ) {
            return fallar("Elige la especialidad: la clínica atiende odontología y otras, y cada una se atiende distinto.")
        }
        if (multiSede && sedeId == null) return fallar("Elige la sede de la cita")
        // Disponibilidad bloquea solo si NO es regularización (igual que la web).
        // Con [ov] ya se verificó fresca antes de crear el tratamiento.
        val d = disponibilidad
        if (ov == null && d != null && !d.disponible && !esRegularizacion) {
            return fallar(d.motivo ?: "El horario no está disponible")
        }
        // Videollamada: un enlace escrito tiene que ser válido (vacío = se pega después).
        val camposVirt = when (val rv = pe.saniape.app.data.staff.camposModalidadCita(virt, servicioCita, meetPlan)) {
            is pe.saniape.app.data.staff.ResultadoCampos.Error -> return fallar("Videollamada: ${rv.error}")
            is pe.saniape.app.data.staff.ResultadoCampos.Ok -> rv.campos
        }
        val virtualCrear = pe.saniape.app.data.staff.virtualParaCrear(virt, servicioCita, camposVirt)
        guardando = true
        try {
            // Flujo → Evaluación: completar primero la consulta origen
            // (igual que handleEvalSave de la web), luego crear la cita.
            // Si completar falla, NO seguimos: dejaría la consulta origen a
            // medias y la evaluación creada suelta. Avisamos y abortamos.
            val origenId = prefill?.citaOrigenId
            if (origenId != null) {
                val okOrigen = runCatching { AgendaRepo.completar(origenId) }.getOrDefault(false)
                if (!okOrigen) return fallar("No se pudo cerrar la consulta previa. Intenta de nuevo.")
            }
            val terId = profFijo ?: terapeuta?.id
            // Especialidad: la elegida, o la del profesional si solo tiene una.
            val espId = especialidad?.id
                ?: terapeuta?.especialidadIds?.singleOrNull()
                ?: misEspecialidadIds.singleOrNull()
            val r = AgendaRepo.crearCitaDetalle(
                pacienteId = p.id, tipo = tipo, fecha = fecha, hora = hora,
                terapeutaId = terId, tratamientoId = ov?.id ?: tratamiento?.id,
                // Primera atención de un tratamiento creado aquí: su costo como lo pondría
                // el tratamiento (paquete/unidades → 0; suelta → precio por sesión; pago
                // único → precio acordado). Otra atención de un pago único: sin costo.
                // Sesión de un tratamiento que ya existía: como las que agenda la web
                // desde el plan (suelta → el precio de una sesión; paquete → 0). Antes
                // iba siempre en 0 y el cierre proponía cobrar S/ 0.
                costo = if (ov != null) ov.costo ?: 0.0
                    else creadoAqui?.takeIf { tipo == "Sesión" && it.first == tratamiento?.id }
                        ?.let { it.second ?: 0.0 }
                        ?: tratamiento?.takeIf { tipo == "Sesión" }?.let { t ->
                            pe.saniape.app.data.staff.costoCitaDeSesion(t.modalidad, t.precioPorSesion, t.precioAcordado, t.totalSesiones)
                        }
                        ?: costo.toDoubleOrNull() ?: 0.0,
                duracion = duracion,
                notas = notas.ifBlank { null },
                especialidadId = espId,
                diagnostico = if (tipo == "Evaluación") diagnostico.ifBlank { null } else null,
                campaniaId = if (tipo != "Sesión") promoAplicada?.id else null,
                // Multisede: la sede viaja en el cuerpo (también en la cola offline).
                sedeId = if (multiSede) sedeId else null,
                citaOrigenId = if (tipo == "Evaluación") origenId else null,
                virtual = virtualCrear,
            )
            if (!r.registrada) {
                // El motivo real del servidor (cupo lleno, paciente de baja,
                // sin permiso...); el genérico solo si ni se pudo intentar.
                return fallar(r.rechazo?.error ?: "No se pudo agendar. Intenta de nuevo.")
            }
            if (!r.encolada) pe.saniape.app.ui.Toaster.exito(textoExito)
            // Google Meet: la cita YA está guardada; si Google falla, la agenda
            // ofrece "Reintentar Meet" o pegar el enlace (gemelo de crearMeetDeCita).
            if (!r.encolada) {
                val meet = pe.saniape.app.data.staff.parsearResultadoMeet(r.cuerpo)
                (meet?.aviso ?: camposVirt.aviso)?.let { pe.saniape.app.ui.Toaster.info(it) }
                pe.saniape.app.data.staff.avisoTrasMeet(meet)?.let { a ->
                    when (a.tipo) {
                        pe.saniape.app.data.staff.TipoAvisoMeet.EXITO -> pe.saniape.app.ui.Toaster.exito(a.texto)
                        pe.saniape.app.data.staff.TipoAvisoMeet.INFO -> pe.saniape.app.ui.Toaster.info(a.texto)
                        pe.saniape.app.data.staff.TipoAvisoMeet.ERROR -> pe.saniape.app.ui.Toaster.error(a.texto)
                    }
                }
            }
            onGuardada(fecha)
            onListo()
            return null
        } finally {
            guardando = false
        }
    }

    fun guardar() {
        if (guardando) return
        scope.launch { guardarCita() }
    }

    /** El profesional elegido a mano (aquí o en el bloque de la cita del tratamiento). */
    fun elegirProfesional(t: TerapeutaRef) {
        terapeuta = t
        terapeutaAMano = true; precargado = null
        // Al elegir profesional, auto-rellenar su especialidad si tiene UNA sola
        // (igual que la web). Así no queda en "Todas" cuando ya hay profesional.
        if (especialidad == null) {
            t.especialidadIds.singleOrNull()?.let { espId ->
                especialidad = especialidadesClinica.find { it.id == espId }
            }
        }
    }

    // ── «Crear tratamiento y agendar cita» en un toque (CitaUnPaso.kt) ──
    fun conflictoDe(d: pe.saniape.app.data.staff.Disponibilidad?): String? =
        d?.takeIf { !it.disponible }?.let { it.motivo ?: "El horario no está disponible" }
    /** Lo que a la cita le falta y NO se elige en el bloque del tratamiento. */
    fun fueraDelBloqueCita(): String? = when {
        paciente == null -> "Falta elegir el ${ctx.terminologiaPaciente.paciente}"
        tipo != "Sesión" -> "La cita no es una sesión"
        multiSede && sedeId == null -> "Falta la sede de la cita"
        else -> (pe.saniape.app.data.staff.camposModalidadCita(virt, servicioCita, meetPlan)
            as? pe.saniape.app.data.staff.ResultadoCampos.Error)?.let { "Videollamada: ${it.error}" }
    }
    fun cargandoServicioCita(): Boolean =
        pe.saniape.app.data.staff.servicioPendiente(tratEnCurso?.procedimientoId, servicioEnCursoDe)
    fun horaPasadaAhora(): Boolean {
        val a = pe.saniape.app.data.staff.ahoraEn(ctx.zonaDeSede(sedeId))
        return pe.saniape.app.data.staff.horaDeCitaYaPaso(fecha, hora, a.fecha, a.minutos)
    }
    val horaPasada = horaPasadaAhora()
    val estadoUnPaso = pe.saniape.app.data.staff.estadoCitaUnPaso(
        fecha = fecha, hora = hora, horaPasada = horaPasada, regularizacion = esRegularizacion,
        conflicto = conflictoDe(disponibilidad), fueraDelBloque = if (crearTratamiento) fueraDelBloqueCita() else null,
        cargandoServicio = crearTratamiento && cargandoServicioCita(),
    )
    // Lo que dijo la verificación fresca deja de valer al cambiar la cita.
    LaunchedEffect(fecha, hora, terapeuta?.id, duracion, esRegularizacion) { errorUnPaso = null }
    LaunchedEffect(crearTratamiento) { if (!crearTratamiento) { tratEnCurso = null; errorUnPaso = null } }

    /**
     * Antes de crear el tratamiento: ¿la cita se puede agendar? Con una
     * verificación FRESCA (la de pantalla corre después de cada cambio): si no se
     * puede, no se crea nada.
     */
    suspend fun verificarCitaUnPaso(): String? {
        val pasada = horaPasadaAhora()
        val e = pe.saniape.app.data.staff.estadoCitaUnPaso(
            fecha, hora, pasada, esRegularizacion, conflictoDe(disponibilidad), fueraDelBloqueCita(),
            cargandoServicio = cargandoServicioCita(),
        )
        if (e.estado != pe.saniape.app.data.staff.EstadoCitaUnPaso.Estado.LISTO) return e.motivo ?: "Revisa la cita"
        // Regularización (ya ocurrió) o sin profesional: no hay horario que verificar.
        val terId = terapeuta?.id ?: profFijo
        if ((pasada && esRegularizacion) || terId == null) return null
        // Igual que la verificación de pantalla: un fallo de red no frena.
        val r = runCatching {
            pe.saniape.app.data.staff.DisponibilidadRepo.verificar(terId, fecha, hora, duracion, pacienteId = paciente?.id)
        }.getOrNull() ?: return null
        disponibilidad = r
        return if (!r.disponible) r.motivo ?: "El horario no está disponible" else null
    }

    /**
     * El tratamiento recién creado queda elegido en la cita (su Sesión #1 / su
     * atención / su intervención), con el costo de su primera atención. Lo mismo
     * con «Solo crear tratamiento» y con el toque único.
     */
    suspend fun elegirCreado(pacTrat: RefNombre, nuevo: pe.saniape.app.ui.clinica.pacientes.TratamientoNuevo, id: String?) {
        if (paciente?.id != pacTrat.id) return
        val lista = runCatching { AgendaRepo.tratamientosAgendables(pacTrat.id) }.getOrNull()
            ?.takeIf { l -> id == null || l.any { it.id == id } }
            // No se pudo recargar (o aún no se ve): se agrega el creado tal cual.
            ?: (tratamientos + listOfNotNull(id?.let {
                TratamientoRef(
                    id = it, procedimiento = "Tratamiento nuevo", modalidad = nuevo.modalidad,
                    terapeutaId = nuevo.terapeutaId, especialidadId = nuevo.especialidadDiagnostico,
                    diagnostico = nuevo.diagnostico, totalSesiones = nuevo.totalSesiones, sesionesCompletadas = 0,
                    servicio = servicioEnCurso,
                )
            })).distinctBy { it.id }
        tratamientos = lista
        tratamientosDe = pacTrat.id
        // Queda elegido en la cita: se agenda como su Sesión #1.
        val elegido = pe.saniape.app.data.staff.tratamientoCreadoEnLista(lista, id)
        tratamiento = elegido
        // Su costo según el modo: suelta → precio por sesión; pago único → el
        // precio acordado (lo lleva esta primera atención); paquete/unidades → 0.
        creadoAqui = elegido?.let {
            it.id to pe.saniape.app.data.staff.costoPrimeraSesion(nuevo.modalidad, nuevo.precioPorSesion, nuevo.precioAcordado)
        }
        // La fecha de la cita ya siguió a la de inicio mientras se editaba
        // (onFechaInicio, CitaUnPaso.kt): aquí no se mueve.
        mensaje = null
    }

    /** «Crear tratamiento y agendar cita»: verificar → crear → guardar la cita (CitaUnPaso.kt). */
    fun crearYAgendarCita(pacTrat: RefNombre, nuevo: pe.saniape.app.ui.clinica.pacientes.TratamientoNuevo) {
        if (creandoTratamiento || guardando) return
        creandoTratamiento = true
        errorUnPaso = null
        scope.launch {
            try {
                val nombre = pe.saniape.app.data.staff.nombreCitaDelTratamiento(nuevo.modalidad)
                // Creado sin id (respuesta vieja): existe, no se ofrece crearlo otra vez.
                var creadoSinId = false
                val r = pe.saniape.app.data.staff.crearTratamientoYAgendar(
                    agendar = true,
                    verificarCita = { verificarCitaUnPaso() },
                    crearTratamiento = {
                        // Sin aviso de éxito: el único aviso es el de la cita agendada.
                        val alta = pe.saniape.app.ui.clinica.pacientes.guardarTratamientoNuevo(
                            pacTrat.id, nuevo, pacTrat.nombre, avisarExito = false,
                        )
                        creadoSinId = alta.creado && !alta.encolada && alta.tratamientoId == null
                        pe.saniape.app.data.staff.ResultadoAltaTratamiento(
                            ok = alta.creado && !alta.parcial, id = alta.tratamientoId, encolada = alta.encolada,
                            // El motivo ya lo avisó guardarTratamientoNuevo.
                            error = if (alta.parcial) "El tratamiento se creó, pero no todo se guardó." else null,
                        )
                    },
                    alCrear = { id ->
                        elegirCreado(pacTrat, nuevo, id)
                        TratCreadoEnCita(id, pe.saniape.app.data.staff.costoPrimeraSesion(nuevo.modalidad, nuevo.precioPorSesion, nuevo.precioAcordado))
                    },
                    // El MISMO guardado de «Guardar cita», con este tratamiento.
                    agendarCita = { t -> guardarCita(t, textoExito = "Tratamiento creado: la cita agendada es $nombre") },
                )
                when (r) {
                    // No se creó nada: el formulario sigue abierto y el bloque dice qué falta.
                    is pe.saniape.app.data.staff.ResultadoUnPaso.CitaInvalida -> {
                        errorUnPaso = r.error
                        pe.saniape.app.ui.Toaster.error(r.error)
                    }
                    is pe.saniape.app.data.staff.ResultadoUnPaso.TratamientoFallido -> {
                        // Sin crear: el aviso ya salió y el formulario sigue abierto para reintentar.
                        // Creado (a medias o sin id): crearlo otra vez sería un duplicado.
                        if (r.creado != null || creadoSinId) {
                            if (creadoSinId) elegirCreado(pacTrat, nuevo, null)
                            crearTratamiento = false
                            mensaje = "El tratamiento se creó, pero la cita no se agendó: revísala y toca «Guardar cita»."
                        }
                    }
                    // Sin señal: igual que «Solo crear tratamiento» (la cola avisó); la cita
                    // no se agenda a ciegas.
                    pe.saniape.app.data.staff.ResultadoUnPaso.TratamientoEnCola -> {
                        crearTratamiento = false
                        mensaje = "Sin señal: el tratamiento se creará al recuperar la señal; agenda la sesión después."
                    }
                    // El tratamiento YA existe y queda elegido: este formulario se cierra y la
                    // Nueva cita queda abierta con el motivo.
                    is pe.saniape.app.data.staff.ResultadoUnPaso.CitaFallida -> {
                        crearTratamiento = false
                        pe.saniape.app.ui.Toaster.error("Tratamiento creado, pero la cita no se guardó: ${r.error} Revísala y toca «Guardar cita».")
                    }
                    is pe.saniape.app.data.staff.ResultadoUnPaso.Ambos,
                    is pe.saniape.app.data.staff.ResultadoUnPaso.SoloTratamiento -> Unit
                }
            } finally {
                creandoTratamiento = false
            }
        }
    }

    // 📅 La cita, abajo del formulario del tratamiento (CitaUnPaso.kt): lo ya
    // elegido aquí, con fecha, hora, profesional y duración a mano para no tener
    // que volver. Son los MISMOS campos de esta cita (mismo estado, misma
    // verificación de horario): cambiarlos ahí es cambiarlos aquí.
    val bloqueCitaUnPaso: @Composable () -> Unit = {
        val forma = RoundedCornerShape(Sania.shape.md.dp)
        var verFecha by remember { mutableStateOf(false) }
        var verHora by remember { mutableStateOf(false) }
        if (verFecha) pe.saniape.app.ui.clinica.pacientes.DialogoFecha(
            onElegir = { fecha = it }, onCerrar = { verFecha = false }, inicial = fecha,
        )
        if (verHora) pe.saniape.app.ui.clinica.pacientes.DialogoHora(hora, onElegir = { hora = it }, onCerrar = { verHora = false })
        Column(
            Modifier.fillMaxWidth().clip(forma).background(c.chipBg).border(1.5.dp, c.lav, forma).padding(12.dp),
        ) {
            Text("📅 La cita", color = c.texto, fontSize = 14.sp, fontWeight = FontWeight.Bold)
            Text(
                "Queda como " + (enCurso?.procedimientoId?.let { pe.saniape.app.data.staff.nombreCitaDelTratamiento(enCurso.modalidad) }
                    ?: "su primera atención") + " del tratamiento.",
                color = c.textoSuave, fontSize = 11.sp, modifier = Modifier.padding(top = 2.dp, bottom = 8.dp),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Column(Modifier.weight(1f)) { Etiqueta("Fecha"); SelectorBoton(fecha) { verFecha = true } }
                Column(Modifier.weight(1f)) { Etiqueta("Hora"); SelectorBoton(hora) { verHora = true } }
            }
            if (profFijo == null) {
                Spacer(Modifier.height(8.dp))
                Etiqueta("Profesional")
                SelectorLista(
                    items = terapeutasFiltrados, elegido = terapeuta, etiqueta = { it.nombre },
                    onElegir = { elegirProfesional(it) }, placeholder = "Sin asignar",
                )
            }
            Spacer(Modifier.height(8.dp))
            Etiqueta("Duración")
            pe.saniape.app.ui.clinica.pacientes.ChipsDuracion(duracion, onChange = { duracion = it; duracionTocada = true },
                opciones = pe.saniape.app.data.staff.opcionesDuracion(duracion))
            if (horaPasada) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(top = 8.dp).clickable { esRegularizacion = !esRegularizacion },
                ) {
                    Text(if (esRegularizacion) "☑" else "☐", fontSize = 18.sp, color = c.pend)
                    Spacer(Modifier.width(6.dp))
                    Text("Ya pasó: esta cita ya ocurrió (la estoy registrando después)",
                        color = c.pend, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                }
            }
            val d = disponibilidad
            val aviso: Pair<String, androidx.compose.ui.graphics.Color>? = when {
                // Consultando la videollamada del servicio: espera, no es un error.
                estadoUnPaso.motivo == pe.saniape.app.data.staff.MOTIVO_CARGANDO_SERVICIO ->
                    pe.saniape.app.data.staff.MOTIVO_CARGANDO_SERVICIO to c.textoSuave
                estadoUnPaso.estado == pe.saniape.app.data.staff.EstadoCitaUnPaso.Estado.FALTA ->
                    "✗ ${estadoUnPaso.motivo}" to c.error
                estadoUnPaso.estado == pe.saniape.app.data.staff.EstadoCitaUnPaso.Estado.NO ->
                    "${estadoUnPaso.motivo}. Crea el tratamiento y termina la cita en su formulario." to c.texto
                errorUnPaso != null -> "✗ $errorUnPaso" to c.error
                d != null && !d.disponible && horaPasada && esRegularizacion ->
                    "⚠ ${d.motivo ?: "Fuera de su horario"} — como ya ocurrió, igual se guarda." to c.pend
                d != null && d.disponible && d.motivo != null -> "⚠ ${d.motivo}" to c.pend
                else -> null
            }
            aviso?.let { (t, color) ->
                Text(t, color = color, fontSize = 12.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 8.dp))
            }
        }
    }

    val pacTrat = paciente
    if (crearTratamiento && pacTrat != null) {
        pe.saniape.app.ui.clinica.pacientes.ModalCrearTratamiento(
            pacienteId = pacTrat.id,
            miTerapeutaId = profFijo,
            diagnosticoPrevio = null,
            terapeutaInicialId = terapeuta?.id,
            especialidadInicialId = especialidad?.id ?: terapeuta?.especialidadIds?.singleOrNull(),
            desdeCita = true,
            // El tratamiento empieza el día de la cita (OMER, 24/10/2026: antes caía en hoy).
            fechaInicioInicial = fecha,
            // Mientras se crea y agenda en un toque, no se cierra a medias.
            onCancelar = { if (!creandoTratamiento) crearTratamiento = false },
            // «Crear tratamiento y agendar cita» en un toque (CitaUnPaso.kt).
            citaEnUnPaso = pe.saniape.app.ui.clinica.pacientes.CitaEnUnPaso(
                bloque = bloqueCitaUnPaso,
                estado = estadoUnPaso.estado,
                ocupado = creandoTratamiento || guardando,
                onCambio = { ter, proc, mod -> tratEnCurso = TratEnCurso(ter, proc, mod) },
                // Un inicio programado para más adelante lleva la cita a ese día (la
                // hora se conserva); uno que «ya empezó» no la arrastra al pasado.
                onFechaInicio = { f ->
                    pe.saniape.app.data.staff.fechaCitaPorInicio(f, fecha, pe.saniape.app.data.staff.hoyEnIso(ctx.zonaDeSede(sedeId)))
                        ?.let { fecha = it }
                },
                onCrearYAgendar = { nuevo -> crearYAgendarCita(pacTrat, nuevo) },
            ),
            // «Solo crear tratamiento» (y el «Crear tratamiento» de siempre): vuelve a la
            // Nueva cita, que sigue abierta, con el tratamiento ya elegido.
            onGuardar = guardarTrat@{ nuevo ->
                // Un segundo toque mientras se crea no manda otra petición.
                if (creandoTratamiento) return@guardarTrat
                creandoTratamiento = true
                crearTratamiento = false
                scope.launch {
                    try {
                        // Toast, plantilla y diagnóstico van adentro (el mismo camino de la ficha).
                        val r = pe.saniape.app.ui.clinica.pacientes.guardarTratamientoNuevo(
                            pacTrat.id, nuevo, pacTrat.nombre,
                            textoExito = "Tratamiento creado: esta cita será " +
                                pe.saniape.app.data.staff.nombreCitaDelTratamiento(nuevo.modalidad),
                        )
                        if (!r.creado || paciente?.id != pacTrat.id) return@launch
                        if (r.encolada) {
                            // Sin id todavía: la cita no se puede atar a él.
                            mensaje = "El tratamiento se creará al recuperar la señal; agenda la sesión después."
                            return@launch
                        }
                        elegirCreado(pacTrat, nuevo, r.tratamientoId)
                    } finally {
                        creandoTratamiento = false
                    }
                }
            },
        )
    }

    // imePadding: la pantalla deja lugar al teclado (edge-to-edge no achica la
    // ventana), así se puede desplazar hasta el final con el teclado abierto.
    val tecladoAbierto = WindowInsets.ime.getBottom(LocalDensity.current) > 0
    Surface(color = c.fondo, modifier = Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().tourAncla("cita_form").imePadding()) {
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
                                "Es la sede del ${LocalTerminologiaPaciente.current.paciente}: sus citas se agendan ahí. Para atenderlo en otra, cambia su sede desde la ficha.",
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

                if (multiEspecialidad && especialidadesElegibles.size > 1) {
                    Spacer(Modifier.height(Sania.dim.md))
                    Etiqueta("Especialidad")
                    SelectorLista(
                        items = especialidadesElegibles, elegido = especialidad, etiqueta = { it.nombre },
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
                            etiqueta = ti.etiqueta ?: flujoEfectivo.nombreTipo(ti.valor),
                            modifier = Modifier.weight(1f),
                        ) { tipo = ti.valor; tipoTocado = true; tipoAuto = false }
                    }
                }
                // Atención directa: el paciente tiene un tratamiento vigente y por eso
                // arrancó en Sesión. Una línea: por qué, y cuál tocar si no.
                if (tipoAuto && tipo == "Sesión") {
                    val nombres = tratamientos.filter { it.estado == "Activo" && pe.saniape.app.data.staff.vaPorSesiones(it.modalidad) }
                        .map { it.procedimiento }.distinct()
                    val activos = if (nombres.isNotEmpty()) " (${nombres.joinToString(", ")})" else ""
                    Text(
                        if (atencionDirectaForm) "Tiene un tratamiento activo$activos. ¿Viene por otra cosa? Elige ${TIPO_ATENCION_DIRECTA_INFO.etiqueta}."
                        // Como la web: el tipo cambió solo al elegir al paciente; se dice por qué.
                        else "⚠ Se cambió a ${flujoEfectivo.nombreTipo("Sesión")} porque tiene un tratamiento activo$activos." +
                            (entradaClinica?.let { " Si viene por algo nuevo, elige $it." } ?: ""),
                        color = c.texto, fontSize = 12.sp,
                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp).clip(RoundedCornerShape(Sania.shape.sm.dp))
                            .background(c.pendBg).border(1.dp, c.pend.copy(alpha = 0.4f), RoundedCornerShape(Sania.shape.sm.dp))
                            .padding(horizontal = 10.dp, vertical = 8.dp),
                    )
                }
                Spacer(Modifier.height(Sania.dim.md))

                // Paciente — con BUSCADOR (escribir nombre filtra), no scroll uno por uno.
                Etiqueta(ctx.terminologiaPaciente.Paciente)
                // Tutoriales: "cita_form.paciente" mientras se busca; "…paciente_elegido" ya elegido.
                Column(Modifier.tourAncla(if (paciente != null) "cita_form.paciente_elegido" else "cita_form.paciente")) {
                    // El tipo que eligió quien agenda (no el que puso el paciente anterior).
                    val tipoBuscador = if (tipoAuto) tipoAntesAuto ?: tipo else tipo
                    // ¿Vende un servicio de una vez (uña encarnada, bótox)? Entonces la Sesión
                    // también registra al paciente nuevo que viene directo a él (solo por DNI).
                    val servicioDeUnaVez = pe.saniape.app.data.staff.hayServicioDeUnaVez(serviciosUnaVez, especialidad?.id)
                    SelectorPacienteBuscable(
                        termino = ctx.terminologiaPaciente.paciente,
                        items = pacientes, elegido = paciente,
                        onElegir = { paciente = it; terapeuta = null; tratamiento = null; terapeutaAMano = false; precargado = null },
                        // Alta al vuelo (DNI → RENIEC → teléfono → registrar), como la web: en
                        // Consulta/Evaluación, en la Sesión de psicología (la primera cita) y en
                        // la de un servicio de una vez; para quien agenda. Con el país de la sede.
                        altaRapida = pe.saniape.app.data.staff.altaRapidaPermitida(
                            tipoBuscador, ctx.puede("agendar"), soloSesion, servicioDeUnaVez, ctx.puede("sesiones"),
                        ),
                        altaSoloConDocumento = pe.saniape.app.data.staff.altaSoloConDocumento(
                            tipoBuscador, soloSesion, servicioDeUnaVez, ctx.puede("sesiones"),
                        ),
                        pais = pe.saniape.app.data.staff.paisDeFila(sedeId),
                        verDatosPersonales = ctx.puede("datos_personales"),
                        onRegistrado = { nuevo, como ->
                            // El recién creado entra a la lista local (si no, el selector
                            // tendría elegido un id que su copia desconoce).
                            pacientes = listOf(nuevo) + pacientes.filter { it.id != nuevo.id }
                            paciente = nuevo; terapeuta = null; tratamiento = null; terapeutaAMano = false; precargado = null
                            // Sin DNI: ofrecer completar su ficha (y agregar el DNI), como la web.
                            // Por documento: nombre, DNI y teléfono ya quedaron; el resto a un toque
                            // («✎ Completar su ficha»). Reactivada: nada (ya tiene sus datos).
                            when (como) {
                                AltaEnCita.SIN_DNI -> completarDatos = nuevo to true
                                AltaEnCita.POR_DOCUMENTO -> recienRegistrado = nuevo
                                AltaEnCita.REACTIVADO -> Unit
                            }
                            // Sesión: el recién registrado no tiene tratamiento y es lo único que
                            // falta → «Crear tratamiento y agendar cita» se abre solo (CitaUnPaso.kt).
                            if (como != AltaEnCita.REACTIVADO && pe.saniape.app.data.staff.abrirTratamientoTrasAlta(
                                    tipoBuscador, ctx.puede("sesiones"), prefill?.tratamientoId != null)
                            ) crearTratamiento = true
                        },
                    )
                    recienRegistrado?.takeIf { it.id == paciente?.id && completarDatos == null }?.let { nuevo ->
                        Text(
                            "✎ Completar su ficha (opcional)" + if (nuevo.telefono == null) " · sin teléfono" else "",
                            color = c.navy, fontSize = 12.sp, fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(top = 6.dp).clickable { completarDatos = nuevo to false },
                        )
                    }
                }

                // Sesión con un paciente sin tratamiento: decirlo y ofrecer crearlo (o a quién pedírselo).
                if (avisoTratamiento != pe.saniape.app.data.staff.AvisoSinTratamiento.NINGUNO) {
                    Spacer(Modifier.height(Sania.dim.sm))
                    val termino = ctx.terminologiaPaciente.paciente
                    val t = tratAmpliar
                    if (ofreceAmpliar && t != null) {
                        // Ya tuvo uno y terminó (una sesión a la vez): ampliarlo es un toque;
                        // crear otro sigue a mano para cuando viene por otro servicio.
                        AvisoAmpliarAnterior(
                            termino = termino,
                            ampliables = ampliables, elegido = t, onElegir = { ampliarId = it.id; ampliarMonto = "" },
                            sesiones = ampliarSesiones, onSesiones = { ampliarSesiones = it.filter(Char::isDigit).take(3) },
                            monto = ampliarMonto, onMonto = { ampliarMonto = it },
                            lista = listaAmpliar, moneda = moneda,
                            ampliando = ampliando, creando = creandoTratamiento,
                            onAmpliar = { ampliarAnterior() },
                            onCrear = { if (!creandoTratamiento && !ampliando) crearTratamiento = true },
                        )
                    } else {
                        AvisoSinTratamientoCita(
                            titulo = pe.saniape.app.data.staff.tituloSinTratamiento(avisoTratamiento, termino),
                            detalle = pe.saniape.app.data.staff.detalleSinTratamiento(
                                avisoTratamiento, entradaClinica, atencionDirecta = atencionDirectaForm,
                            ),
                            aviso = avisoTratamiento,
                            creando = creandoTratamiento,
                            onCrear = { if (!creandoTratamiento) crearTratamiento = true },
                        )
                    }
                }

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
                }, opciones = pe.saniape.app.data.staff.opcionesDuracion(duracion))
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
                    if (profFijo == null) {
                        Text("Ver horarios", color = c.navy, fontSize = 11.sp, fontWeight = FontWeight.Bold,
                            modifier = Modifier.clickable { mostrarHorarios = true })
                    }
                }
                if (profFijo != null) {
                    val miNombre = terapeutas.find { it.id == profFijo }?.nombre ?: "Tú"
                    SelectorBoton("$miNombre (tú)", bloqueado = true) {}
                } else {
                    SelectorLista(
                        items = terapeutasFiltrados, elegido = terapeuta, etiqueta = { it.nombre },
                        onElegir = { t -> elegirProfesional(t) },
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

                // 📍 En el local / 🎥 Videollamada (y con qué enlace).
                Spacer(Modifier.height(Sania.dim.md))
                pe.saniape.app.ui.clinica.agenda.componentes.SelectorModalidadCita(
                    valor = virt,
                    onChange = { virt = it },
                    meetPlan = meetPlan,
                    terapeutaId = profFijo ?: terapeuta?.id,
                    habilitado = !guardando,
                )

                // Costo (si tiene permiso pagos)
                // Diagnóstico (Evaluación) — opcional al agendar. Es clínico: sin el
                // permiso 'historia_clinica' no se pide (lo escribe quien evalúa).
                if (tipo == "Evaluación" && pe.saniape.app.data.staff.verHistoriaClinica(ctx)) {
                    Spacer(Modifier.height(Sania.dim.md))
                    Etiqueta("Diagnóstico / Motivo (opcional)")
                    OutlinedTextField(
                        value = diagnostico, onValueChange = { diagnostico = it },
                        placeholder = { Text("Se puede completar luego", color = c.textoSuave) },
                        modifier = Modifier.fillMaxWidth(), minLines = 2,
                    )
                    // Síntomas de la especialidad (los personalizados mandan) + unos pocos
                    // del rubro (pool), como la web. Sin especialidad concreta, ninguno:
                    // la unión llenaba una cita dental de síntomas de fisio.
                    val espMotivo = especialidad
                        ?: terapeuta?.especialidadIds?.singleOrNull()?.let { id -> especialidadesClinica.find { it.id == id } }
                        ?: especialidadesClinica.singleOrNull()
                    val baseMotivo = remember(espMotivo) {
                        espMotivo?.let { pe.saniape.app.ui.clinica.chipsDeEspecialidad(it.aChips()).sintomas }.orEmpty()
                    }
                    val chipsMotivo = pe.saniape.app.ui.clinica.rememberChipsConPool(
                        pe.saniape.app.data.staff.CampoChip.SINTOMA, espMotivo?.id, baseMotivo, activo = espMotivo != null,
                    )
                    pe.saniape.app.ui.clinica.FilaChipsSugeridos(
                        if (espMotivo != null) chipsMotivo else emptyList(),
                        puesto = { pe.saniape.app.data.staff.chipPuesto(diagnostico, it) },
                        onToca = { diagnostico = pe.saniape.app.data.staff.alternarChip(diagnostico, it) },
                    )
                }

                if (ctx.puede("pagos") && tipo != "Sesión") {
                    val precioBase = if (tipo == "Evaluación") precioEvaluacion else precioConsultaEf
                    // Con promo, "Restablecer" vuelve al precio PROMOCIONAL (es el vigente).
                    val precioDefault = promoAplicada?.precioCon(precioBase) ?: precioBase
                    Spacer(Modifier.height(Sania.dim.md))
                    Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween, Alignment.CenterVertically) {
                        Etiqueta("Costo (${simboloMoneda(moneda)})")
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
                            "🎉 ${promo.nombre} — ${promo.etiqueta()} · antes ${simboloMoneda(moneda)} ${if (precioBase % 1.0 == 0.0) precioBase.toInt() else precioBase}",
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
                    modifier = Modifier.fillMaxWidth().tourAncla("cita_form.guardar").height(Sania.dim.boton),
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

/**
 * "Este paciente aún no tiene tratamiento" bajo el paciente de una Sesión
 * (psicología). Con permiso: "＋ Crear tratamiento"; sin él, a quién pedírselo.
 */
@Composable
private fun AvisoSinTratamientoCita(
    titulo: String,
    detalle: String,
    aviso: pe.saniape.app.data.staff.AvisoSinTratamiento,
    creando: Boolean,
    onCrear: () -> Unit,
) {
    val c = Sania.colors
    val forma = RoundedCornerShape(Sania.shape.sm.dp)
    Column(
        Modifier.fillMaxWidth().clip(forma).background(c.pendBg).border(1.dp, c.pend.copy(alpha = 0.4f), forma)
            .padding(12.dp),
    ) {
        Text("📋 $titulo", color = c.texto, fontSize = 13.sp, fontWeight = FontWeight.Bold)
        Text(detalle, color = c.textoSuave, fontSize = 12.sp,
            modifier = Modifier.padding(top = 2.dp))
        if (aviso == pe.saniape.app.data.staff.AvisoSinTratamiento.OFRECER_CREAR) {
            Spacer(Modifier.height(8.dp))
            Row(
                Modifier.fillMaxWidth().heightIn(min = 44.dp).clip(RoundedCornerShape(Sania.shape.md.dp))
                    .background(if (creando) c.navy.copy(alpha = 0.5f) else c.navy)
                    .clickable(enabled = !creando, onClick = onCrear).padding(horizontal = 14.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (creando) {
                    androidx.compose.material3.CircularProgressIndicator(
                        color = c.sobreNavy, strokeWidth = 2.dp, modifier = Modifier.size(16.dp),
                    )
                    Spacer(Modifier.width(8.dp))
                }
                Text(if (creando) "Creando tratamiento…" else "＋ Crear tratamiento",
                    color = c.sobreNavy, fontSize = 14.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

/**
 * «El tratamiento anterior de este paciente ya terminó» con «＋ Ampliar tratamiento
 * anterior» (servicio + última sesión, select si hay varios, sesiones y monto) y
 * «＋ Crear tratamiento» al lado. Gemelo del bloque de CitaForm web.
 */
@Composable
private fun AvisoAmpliarAnterior(
    termino: String,
    ampliables: List<pe.saniape.app.data.staff.TratAmpliable>,
    elegido: pe.saniape.app.data.staff.TratAmpliable,
    onElegir: (pe.saniape.app.data.staff.TratAmpliable) -> Unit,
    sesiones: String, onSesiones: (String) -> Unit,
    monto: String, onMonto: (String) -> Unit,
    lista: Double?, moneda: String,
    ampliando: Boolean, creando: Boolean,
    onAmpliar: () -> Unit, onCrear: () -> Unit,
) {
    val c = Sania.colors
    val forma = RoundedCornerShape(Sania.shape.sm.dp)
    val texto = { t: pe.saniape.app.data.staff.TratAmpliable -> pe.saniape.app.data.staff.textoTratamientoAmpliable(t.servicio, t.ultimaSesion) }
    Column(
        Modifier.fillMaxWidth().clip(forma).background(c.pendBg).border(1.dp, c.pend.copy(alpha = 0.4f), forma).padding(12.dp),
    ) {
        Text("📋 El tratamiento anterior de este $termino ya terminó", color = c.texto, fontSize = 13.sp, fontWeight = FontWeight.Bold)
        Text(
            "Amplíalo para seguir: esta cita queda como su siguiente sesión, con la fecha, hora y profesional que ya elegiste. " +
                "Si viene por otro servicio, crea uno nuevo.",
            color = c.textoSuave, fontSize = 12.sp, modifier = Modifier.padding(top = 2.dp),
        )
        Spacer(Modifier.height(8.dp))
        if (ampliables.size > 1) {
            SelectorLista(items = ampliables, elegido = elegido, etiqueta = texto, onElegir = onElegir, placeholder = "Tratamiento a ampliar")
        } else {
            Text(texto(elegido), color = c.texto, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
        }
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.Top) {
            Column(Modifier.width(90.dp)) {
                Etiqueta("Sesiones")
                OutlinedTextField(
                    value = sesiones, onValueChange = onSesiones, singleLine = true, enabled = !ampliando,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            Column(Modifier.weight(1f)) {
                Etiqueta("Monto adicional (${simboloMoneda(moneda)})")
                OutlinedTextField(
                    value = monto, onValueChange = onMonto, singleLine = true, enabled = !ampliando,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    // Precio negociable: vacío = la lista de la ampliación; 0 escrito = cortesía.
                    placeholder = {
                        Text(lista?.let { pe.saniape.app.ui.clinica.agenda.modales.textoSoles(it, moneda) } ?: "0.00", color = c.textoSuave)
                    },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
        Text(
            if (lista != null) "Vacío = precio de lista (${pe.saniape.app.ui.clinica.agenda.modales.textoSoles(lista, moneda)}) · 0 = cortesía"
            else "Vacío = sin costo extra",
            color = c.textoSuave, fontSize = 11.sp, modifier = Modifier.padding(top = 4.dp),
        )
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
                Modifier.weight(1.4f).heightIn(min = 44.dp).clip(RoundedCornerShape(Sania.shape.md.dp))
                    .background(if (ampliando) c.navy.copy(alpha = 0.5f) else c.navy)
                    .clickable(enabled = !ampliando && !creando, onClick = onAmpliar).padding(horizontal = 10.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically,
            ) {
                if (ampliando) {
                    CircularProgressIndicator(color = c.sobreNavy, strokeWidth = 2.dp, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(8.dp))
                }
                Text(if (ampliando) "Ampliando…" else "＋ Ampliar tratamiento anterior", color = c.sobreNavy,
                    fontSize = 13.sp, fontWeight = FontWeight.Bold, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
            }
            Box(
                Modifier.weight(1f).heightIn(min = 44.dp).clip(RoundedCornerShape(Sania.shape.md.dp))
                    .border(1.dp, c.navy, RoundedCornerShape(Sania.shape.md.dp))
                    .clickable(enabled = !ampliando && !creando, onClick = onCrear).padding(horizontal = 10.dp, vertical = 10.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(if (creando) "Creando…" else "＋ Crear tratamiento", color = c.navy, fontSize = 13.sp, fontWeight = FontWeight.Bold,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center)
            }
        }
    }
}

@Composable
private fun TarjetaTipo(info: TipoInfo,activo: Boolean, etiqueta: String = info.valor, modifier: Modifier = Modifier, onClick: () -> Unit) {
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
        Text(info.desc.replace("@", LocalTerminologiaPaciente.current.paciente), color = c.textoSuave, fontSize = 9.sp,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            modifier = Modifier.padding(top = 2.dp))
    }
}

@Composable
internal fun SelectorBoton(valor: String, bloqueado: Boolean = false, onClick: () -> Unit) {
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

/** Lo que se va eligiendo en «＋ Crear tratamiento» abierto desde la cita (CitaUnPaso.kt). */
private data class TratEnCurso(val terapeutaId: String?, val procedimientoId: String?, val modalidad: String?)

/** El tratamiento recién creado desde la cita: su id y el costo de su primera atención. */
private data class TratCreadoEnCita(val id: String, val costo: Double?)
