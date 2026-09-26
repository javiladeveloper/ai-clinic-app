package pe.saniape.app.ui.clinica.agenda

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.plus
import pe.saniape.app.data.staff.AgendaBanners
import pe.saniape.app.data.staff.AgendaRepo
import pe.saniape.app.data.staff.RealtimeAgenda
import pe.saniape.app.data.staff.BannersAgenda
import pe.saniape.app.data.staff.CitaStaff
import pe.saniape.app.data.staff.ContextoStaff
import pe.saniape.app.data.staff.EspecialidadRef
import pe.saniape.app.data.staff.TerapeutaRef
import pe.saniape.app.data.staff.FlujoClinica
import pe.saniape.app.data.staff.citaEsDental

/**
 * ViewModel de la Agenda: ÚNICA fuente de estado y lógica. La pantalla solo
 * observa este estado y dispara intents. Así, tocar la UI no rompe la lógica y
 * viceversa (separación de responsabilidades, testeable, escalable).
 *
 * Las escrituras delegan en AgendaRepo, que a su vez usa los endpoints que reusan
 * los helpers de la web (las REGLAS DE NEGOCIO viven una sola vez, en la web).
 */
class AgendaViewModel(private val ctx: ContextoStaff) : ViewModel() {

    // ── Estado expuesto (inmutable hacia afuera) ──
    val hoy: String = hoyIso()
    var fechaSel by mutableStateOf(hoy); private set
    var cargando by mutableStateOf(true); private set   // spinner completo (solo 1ª carga)
    var recargando by mutableStateOf(false); private set  // aviso sutil (cambios de día/refresh)
    var citas by mutableStateOf<List<CitaStaff>>(emptyList()); private set   // crudas del día
    var especialidades by mutableStateOf<List<EspecialidadRef>>(emptyList()); private set
    var banners by mutableStateOf<BannersAgenda?>(null); private set
    var accionando by mutableStateOf(false); private set
    var mensaje by mutableStateOf<String?>(null); private set

    // ── Filtros ──
    var busqueda by mutableStateOf(""); private set
    var filtroEstado by mutableStateOf<String?>(null); private set
    var filtroTipo by mutableStateOf<String?>(null); private set
    var filtroTerapeuta by mutableStateOf<String?>(null); private set   // solo gestores
    var filtroEspecialidad by mutableStateOf<String?>(null); private set

    // ── Vista historial + paginación (igual que la web) ──
    var verHistorial by mutableStateOf(false); private set
    var pagina by mutableStateOf(0); private set
    var hayMasPaginas by mutableStateOf(false); private set
    /** true = vista lista paginada (historial/todas); false = un día concreto. */
    val modoLista: Boolean get() = verHistorial

    // Profesionales activos para el filtro (solo si es gestor sin scope).
    var terapeutas by mutableStateOf<List<TerapeutaRef>>(emptyList()); private set

    val miTerapeutaId: String? get() = ctx.miTerapeutaId
    val esGestor: Boolean get() = ctx.esGestor
    /** El gestor sin scope propio puede filtrar por profesional. */
    val puedeFiltrarPorPersonal: Boolean get() = ctx.miTerapeutaId == null

    /** Clínica con odontología Y otras especialidades: lo dental se decide por cita. */
    private val clinicaMixtaDental: Boolean get() = ctx.mapaDental.ids.isNotEmpty() && !ctx.mapaDental.solo
    /** Especialidades de cada profesional: el último respaldo de `citaEsDental`. */
    private var espsPorTerapeuta by mutableStateOf<Map<String, List<String>>>(emptyMap())

    /**
     * El flujo de la ESPECIALIDAD de la cita (si evalúa, cómo se llama). En una
     * clínica mixta la cita dental es "Diagnóstico" aunque la clínica llame
     * "Evaluación" a la de fisio. Sin especialidad, el de la clínica.
     */
    fun flujoDe(cita: CitaStaff): FlujoClinica {
        val espId = cita.especialidadId ?: cita.especialidadServicioId
        return ctx.flujo.paraEspecialidad(especialidades.find { it.id == espId }?.flujoPreset)
    }

    /** ¿Esta cita pasa por el odontograma? Por cita, no por clínica (gemelo de la web). */
    fun esDental(cita: CitaStaff): Boolean = citaEsDental(
        ctx.mapaDental, cita.especialidadId, cita.especialidadServicioId,
        cita.terapeutaId?.let { espsPorTerapeuta[it] },
    )

    /** ¿Esta cita es de fisioterapia? (EVA, mejorías, dictado al completar). Gemelo de `citaEsFisio`. */
    fun esFisio(cita: CitaStaff): Boolean = pe.saniape.app.data.staff.citaEsFisio(
        ctx.mapaFisio, cita.especialidadId, cita.especialidadServicioId,
        cita.terapeutaId?.let { espsPorTerapeuta[it] },
    )

    /** Citas tras aplicar los filtros (lo que la pantalla pinta). */
    val citasFiltradas: List<CitaStaff>
        get() = citas.filter { c ->
            (busqueda.isBlank() ||
                (c.pacienteNombre?.contains(busqueda, ignoreCase = true) == true) ||
                (c.procedimiento?.contains(busqueda, ignoreCase = true) == true)) &&
                (filtroEstado == null || c.estado == filtroEstado) &&
                (filtroTipo == null || c.tipo == filtroTipo) &&
                (filtroTerapeuta == null || c.terapeutaId == filtroTerapeuta) &&
                (filtroEspecialidad == null || c.especialidadId == filtroEspecialidad)
        }.sortedWith(
            // Orden de la agenda (mismo criterio que compararCitas en la web):
            //  1) Las CANCELADAS siempre al fondo del todo.
            //  2) La FECHA manda cuando la vista mezcla varios días (historial y
            //     "solo próximas"): en el historial lo más reciente arriba; en
            //     próximas, lo que toca antes. En la vista de UN día todas
            //     comparten fecha, así que esto no la altera.
            //  3) Luego la HORA, en el mismo sentido que la fecha.
            //  4) Desempate a misma hora Y MISMO paciente: la Evaluación arriba de la Consulta
            //     (el paciente pasa consulta → evaluación; se prioriza la evaluación).
            //
            // Sin el paso 2 el historial ordenaba SOLO por hora: una cita del 22
            // de julio a las 9:00 se colaba encima de una del 3 de septiembre a
            // las 18:00, y la lista parecía desordenada (reporte 2026-09-03).
            Comparator { a, b ->
                val aCanc = a.estado == "Cancelada"
                val bCanc = b.estado == "Cancelada"
                if (aCanc != bCanc) return@Comparator if (aCanc) 1 else -1
                if (modoLista && a.fecha != b.fecha) {
                    return@Comparator if (verHistorial) b.fecha.compareTo(a.fecha)
                                      else a.fecha.compareTo(b.fecha)
                }
                val ha = a.hora.take(5)
                val hb = b.hora.take(5)
                if (ha != hb) {
                    return@Comparator if (modoLista && verHistorial) hb.compareTo(ha)
                                      else ha.compareTo(hb)
                }
                if (a.pacienteId != null && a.pacienteId == b.pacienteId) {
                    val prioridad = { t: String? -> when (t) { "Evaluación" -> 0; "Consulta" -> 1; "Sesión" -> 2; else -> 3 } }
                    val pa = prioridad(a.tipo)
                    val pb = prioridad(b.tipo)
                    if (pa != pb) return@Comparator pa - pb
                }
                0
            }
        )

    /** Citas del día sin profesional asignado (aviso "⚠ Asignar"). */
    val citasSinProfesional: List<CitaStaff>
        get() = citasFiltradas.filter { it.terapeutaId == null && it.estado != "Cancelada" }

    /**
     * Conteo de SOLAPAMIENTO por franja/profesional: para cada cita, cuántas citas
     * ACTIVAS comparten su misma hora de inicio (`hora.take(5)`) y su mismo
     * profesional (`terapeutaId`). Sirve para colorear la agenda (2 = ámbar, 3+ =
     * rojo). Se calcula sobre `citas` SIN aplicar el filtro de profesional, para no
     * perder solapamientos reales cuando el gestor filtra por uno. Se excluyen las
     * canceladas (ya no ocupan la franja) y las sin profesional (no forman "franja").
     * Clave = id de la cita → conteo (1 si no solapa con nadie).
     */
    val conteosFranja: Map<String, Int>
        get() {
            val m = HashMap<String, Int>()
            citas.asSequence()
                .filter { it.estado != "Cancelada" && it.terapeutaId != null }
                .groupBy { it.terapeutaId to it.hora.take(5) }
                .forEach { (_, grupo) -> grupo.forEach { m[it.id] = grupo.size } }
            return m
        }

    fun cambiarBusqueda(v: String) { busqueda = v }
    fun cambiarFiltroEstado(v: String?) { filtroEstado = v }
    fun cambiarFiltroTipo(v: String?) { filtroTipo = v }
    fun cambiarFiltroTerapeuta(v: String?) { filtroTerapeuta = v }
    fun cambiarFiltroEspecialidad(v: String?) { filtroEspecialidad = v }

    // Suscripción Realtime a la tabla `citas`: mantiene la agenda al día sin recargar.
    private var realtimeJob: kotlinx.coroutines.Job? = null

    init {
        cargarDia(fechaSel)
        cargarAuxiliares()
        // Auto-refresco en vivo: si desde la web se agenda/cambia/cancela una cita, la
        // agenda visible se recarga sola. Best-effort — si no conecta, no pasa nada.
        realtimeJob = RealtimeAgenda.suscribir(viewModelScope) {
            // Recarga lo que esté visible ahora mismo (día concreto o lista), sin spinner.
            if (verHistorial) cargarLista() else cargarDia(fechaSel)
        }
    }

    override fun onCleared() {
        realtimeJob?.cancel()
        super.onCleared()
    }

    // ── Intents ──
    fun seleccionarDia(iso: String) {
        verHistorial = false
        fechaSel = iso
        cargarDia(iso)
    }

    /** Alterna la vista lista (historial/todas) vs el día seleccionado. */
    fun alternarHistorial() {
        verHistorial = !verHistorial
        pagina = 0
        if (verHistorial) cargarLista() else cargarDia(fechaSel)
    }

    fun paginaSiguiente() { if (hayMasPaginas) { pagina++; cargarLista() } }
    fun paginaAnterior() { if (pagina > 0) { pagina--; cargarLista() } }

    fun limpiarMensaje() { mensaje = null }

    private fun cargarDia(fecha: String) {
        viewModelScope.launch {
            // Spinner completo solo si aún no hay nada; si ya hay citas (cambio de día),
            // mantenemos la lista visible y mostramos "Actualizando…" (no parpadea a spinner).
            if (citas.isEmpty()) cargando = true else recargando = true
            citas = runCatching { AgendaRepo.citasDelDia(fecha, ctx.miTerapeutaId) }.getOrDefault(emptyList())
            cargando = false; recargando = false
        }
    }

    private fun cargarLista() {
        viewModelScope.launch {
            if (citas.isEmpty()) cargando = true else recargando = true
            val r = runCatching {
                AgendaRepo.citasPaginadas(verHistorial, pagina, ctx.miTerapeutaId, hoy)
            }.getOrDefault(emptyList())
            citas = r
            hayMasPaginas = r.size >= AgendaRepo.PAGE_SIZE
            cargando = false; recargando = false
        }
    }

    private fun cargarAuxiliares() {
        viewModelScope.launch {
            // Independientes → en paralelo (antes: especialidades → terapeutas → banners en serie).
            coroutineScope {
                val espD = async { runCatching { AgendaRepo.especialidades() }.getOrDefault(emptyList()) }
                val terD = async {
                    // En una clínica mixta hacen falta también para decidir qué
                    // cita es dental, aunque quien mira no filtre por profesional.
                    if (puedeFiltrarPorPersonal || clinicaMixtaDental)
                        runCatching { AgendaRepo.terapeutasActivos() }.getOrDefault(emptyList())
                    else null
                }
                val banD = async { recargarBanners() }
                especialidades = espD.await()
                terD.await()?.let { ts ->
                    espsPorTerapeuta = ts.associate { it.id to it.especialidadIds }
                    if (puedeFiltrarPorPersonal) terapeutas = ts
                }
                banD.await()
            }
        }
    }

    private suspend fun recargarBanners() {
        banners = runCatching {
            AgendaBanners.cargar(hoy, mananaIso(hoy), ctx.miTerapeutaId, ctx.esGestor)
        }.getOrNull()
    }

    /** Recarga las citas según el modo actual (lista paginada vs día). */
    private suspend fun recargarCitas() {
        citas = runCatching {
            if (verHistorial) AgendaRepo.citasPaginadas(verHistorial, pagina, ctx.miTerapeutaId, hoy)
            else AgendaRepo.citasDelDia(fechaSel, ctx.miTerapeutaId)
        }.getOrDefault(citas)
    }

    /**
     * Completar pendiente de elegir QUIÉN ATENDIÓ: el servidor lo rechazó con
     * SIN_PROFESIONAL (la cita no tiene profesional y quien completa no es uno).
     * La pantalla muestra el selector y reintenta con [completarConProfesional].
     */
    data class PedidoProfesional(
        val cita: CitaStaff,
        val observaciones: String?, val diagnostico: String?, val derivarEspId: String?,
        val piezas: List<String>?, val congelarOdontograma: Boolean,
        /** Fisio (M5): la evaluación estructurada llenada, para no perderla en el reintento. */
        val evaluacionFisio: pe.saniape.app.data.staff.BorradorEvaluacionFisio? = null,
    )
    var pedirProfesional by mutableStateOf<PedidoProfesional?>(null); private set

    fun cerrarPedidoProfesional() { pedirProfesional = null }

    /**
     * Tras completar una EVALUACIÓN: ofrecer crear el tratamiento sin salir de la
     * agenda (gemelo de /citas web: `abrirTratamientoDeCita` tras completar la
     * evaluación). Solo a quien gestiona tratamientos ('sesiones'), como la web.
     */
    data class OfertaTratamiento(val cita: CitaStaff, val diagnostico: String?)
    var ofrecerTratamiento by mutableStateOf<OfertaTratamiento?>(null); private set
    fun cerrarOfertaTratamiento() { ofrecerTratamiento = null }

    /** Reintenta el completar con el profesional elegido en el selector. */
    fun completarConProfesional(terapeutaId: String) {
        val p = pedirProfesional ?: return
        pedirProfesional = null
        ejecutar(
            AccionCita.Completar, p.cita, p.observaciones, p.diagnostico, p.derivarEspId,
            piezas = p.piezas, congelarOdontograma = p.congelarOdontograma, terapeutaId = terapeutaId,
            evaluacionFisio = p.evaluacionFisio,
        )
    }

    /** Acción genérica sobre una cita. Refresca citas + banners al terminar. */
    fun ejecutar(
        accion: AccionCita, cita: CitaStaff,
        observaciones: String? = null, diagnostico: String? = null, derivarEspId: String? = null,
        // Odontología (solo citas dentales; ver ModalCompletar).
        piezas: List<String>? = null, congelarOdontograma: Boolean = false,
        /** Quién atendió (solo si la cita no tiene profesional). */
        terapeutaId: String? = null,
        /** Fisioterapia (sesión): evolución (null = no tocar) y EVA (null = no es fisio). */
        mejorias: String? = null,
        eva: Pair<Int?, Int?>? = null,
        /** Fisioterapia (evaluación): lo llenado en "Evaluación estructurada" (null = nada). */
        evaluacionFisio: pe.saniape.app.data.staff.BorradorEvaluacionFisio? = null,
    ) {
        if (accionando) return
        viewModelScope.launch {
            accionando = true; mensaje = null
            val ok = when (accion) {
                AccionCita.Confirmar -> AgendaRepo.confirmar(cita.id)
                AccionCita.Completar -> {
                    val r = AgendaRepo.completarDetalle(
                        cita.id, observaciones, diagnostico, derivarEspId,
                        piezas = piezas, congelarOdontograma = congelarOdontograma,
                        terapeutaId = terapeutaId,
                        mejorias = mejorias, eva = eva,
                    )
                    when {
                        r.registrada -> true
                        // Error de NEGOCIO, no de red: no se encola ni se reintenta solo.
                        // Falta quién atendió → se ofrece el selector en vez de un toast mudo.
                        r.codigo == "SIN_PROFESIONAL" -> {
                            pedirProfesional = PedidoProfesional(
                                cita, observaciones, diagnostico, derivarEspId, piezas, congelarOdontograma,
                                evaluacionFisio,
                            )
                            accionando = false
                            return@launch
                        }
                        else -> {
                            r.rechazo?.let { pe.saniape.app.ui.Toaster.error(it.error) }
                            // Sin rechazo = ni se pudo encolar: el toast genérico de abajo.
                            if (r.rechazo != null) { recargarCitas(); accionando = false; return@launch }
                            false
                        }
                    }
                }
                AccionCita.Revertir -> AgendaRepo.revertir(cita.id)
                AccionCita.Cancelar -> AgendaRepo.cancelar(cita.id)
            }
            if (ok) {
                // Aprende el diagnóstico escrito (para sugerirlo luego, como las técnicas).
                if (accion == AccionCita.Completar && !diagnostico.isNullOrBlank()) {
                    runCatching {
                        pe.saniape.app.data.staff.DiagnosticosRepo.registrar(diagnostico, cita.especialidadId)
                    }
                }
                val txt = when (accion) {
                    AccionCita.Confirmar -> "Cita confirmada"
                    AccionCita.Completar -> "Cita completada"
                    AccionCita.Revertir -> "Cita revertida"
                    AccionCita.Cancelar -> "Cita cancelada"
                }
                pe.saniape.app.ui.Toaster.exito(txt)
                // Fisioterapia: la evaluación estructurada (opcional). Aparte, para no
                // demorar la recarga de la agenda; nunca bloquea: la cita ya quedó
                // completada y, si falla, solo se avisa (gemelo de guardarEvaluacionDeCita).
                if (accion == AccionCita.Completar && evaluacionFisio != null) {
                    viewModelScope.launch {
                        val okFisio = pe.saniape.app.data.staff.EvaluacionFisioRepo.guardarDeCita(
                            evaluacionFisio, cita.id, cita.pacienteId, cita.tratamientoId, cita.fecha,
                            terapeutaId ?: cita.terapeutaId ?: ctx.miTerapeutaId,
                        )
                        if (okFisio == false) pe.saniape.app.ui.Toaster.error(
                            "La evaluación se completó, pero no se guardó la evaluación estructurada. Cárgala desde la ficha (pestaña 📏 Evaluación).",
                        )
                    }
                }
                // Evaluación completada → el siguiente paso natural es el plan (como la web).
                if (accion == AccionCita.Completar && cita.tipo == "Evaluación" &&
                    ctx.puede("sesiones") && cita.pacienteId != null) {
                    ofrecerTratamiento = OfertaTratamiento(
                        cita.copy(terapeutaId = terapeutaId ?: cita.terapeutaId, estado = "Completada"),
                        diagnostico?.trim()?.ifBlank { null },
                    )
                }
            } else pe.saniape.app.ui.Toaster.error("No se pudo, intenta de nuevo")
            recargarCitas()
            recargarBanners()
            accionando = false
        }
    }

    /**
     * Reprogramar (fecha/hora) y/o asignar profesional, vía servidor (valida cupo,
     * marca la sesión vinculada como Reprogramada con su motivo, cola offline).
     * [onFin] recibe true si quedó registrado: el modal solo se cierra entonces, así
     * un "sin cupo" deja elegir otra hora sin volver a abrirlo.
     */
    fun reprogramar(
        cita: CitaStaff, fecha: String, hora: String,
        terapeutaId: String? = null, motivo: String? = null,
        onFin: (Boolean) -> Unit,
    ) {
        if (accionando) return
        viewModelScope.launch {
            accionando = true
            val r = AgendaRepo.reprogramar(cita.id, fecha, hora, terapeutaId = terapeutaId, motivo = motivo)
            if (r.registrada) {
                // Recarga PARCIAL: solo lo que cambia (citas + banners), no toda la agenda.
                recargarCitas()
                recargarBanners()
                if (!r.encolada) pe.saniape.app.ui.Toaster.exito(
                    if (fecha == cita.fecha && hora.take(5) == cita.hora.take(5)) "Cita actualizada" else "Cita reprogramada"
                )
            } else {
                pe.saniape.app.ui.Toaster.error(r.rechazo?.error ?: "No se pudo reprogramar")
            }
            accionando = false
            onFin(r.registrada)
        }
    }

    /** Pasar Consulta → Evaluación. */
    fun pasarAEvaluacion(cita: CitaStaff, fecha: String, hora: String, costo: Double, notas: String?, onFin: (Boolean) -> Unit) {
        if (accionando) return
        viewModelScope.launch {
            accionando = true; mensaje = null
            val ok = AgendaRepo.pasarAEvaluacion(cita, fecha, hora, costo, notas)
            mensaje = if (ok) "✓ Evaluación agendada" else "⚠ No se pudo"
            recargarCitas()
            recargarBanners()
            accionando = false
            onFin(ok)
        }
    }

    /** Marca una derivación como procesada (la quita del banner). */
    fun marcarDerivacion(id: String) {
        viewModelScope.launch {
            if (AgendaBanners.marcarDerivacion(id)) {
                banners = banners?.copy(derivaciones = banners!!.derivaciones.filter { it.id != id })
            }
        }
    }

    /** Recarga las citas (modo actual) y los banners (tras crear cita). */
    fun refrescar() {
        viewModelScope.launch { recargarCitas(); recargarBanners() }
    }
}

/** Acciones de una cita (type-safe, en vez de strings mágicos). */
enum class AccionCita { Confirmar, Completar, Revertir, Cancelar }

// ── Helpers de fecha (puros) ──
/**
 * "Hoy" en la zona de la CLÍNICA (America/Lima), no la del teléfono: gemelo de
 * getLocalToday() de la web. Un celular con la zona mal puesta proponía mañana.
 */
fun hoyIso(): String = pe.saniape.app.data.staff.hoyClinicaIso()

fun mananaIso(hoy: String): String {
    val p = hoy.split("-")
    return LocalDate(p[0].toInt(), p[1].toInt(), p[2].toInt()).plus(DatePeriod(days = 1)).iso()
}

internal fun LocalDate.iso(): String =
    "$year-${monthNumber.toString().padStart(2, '0')}-${dayOfMonth.toString().padStart(2, '0')}"