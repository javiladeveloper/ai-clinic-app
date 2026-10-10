package pe.saniape.app.ui.clinica.agenda

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.map
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
import io.github.jan.supabase.auth.auth

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
    /**
     * Especialidades elegidas en el filtro (varias; vacío = Todas). Cruda: lo
     * guardado en el teléfono, que se valida contra las especialidades al usarse
     * ([seleccionEspecialidades]) porque pueden no haber llegado todavía.
     */
    private var seleccionEspGuardada by mutableStateOf<List<String>>(emptyList())

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

    /**
     * Filtro por especialidad: solo si la clínica tiene 2+ especialidades y quien
     * mira ve la agenda de todos. Al profesional vinculado (su agenda ya viene
     * acotada a él) no se le muestra ni se le aplica: ve lo mismo que antes.
     */
    val muestraFiltroEspecialidad: Boolean get() = puedeFiltrarPorPersonal && especialidades.size > 1

    /** La selección válida hoy (vacía = Todas, o si el filtro no se muestra). */
    val seleccionEspecialidades: List<String>
        get() = if (!muestraFiltroEspecialidad) emptyList()
                else pe.saniape.app.data.staff.normalizarSeleccion(seleccionEspGuardada, especialidades.map { it.id })

    /** ¿La cita pasa el filtro de especialidad? Sin filtro puesto, siempre sí. */
    private fun pasaEsp(cita: CitaStaff, sel: List<String>, ofrecidos: Set<String>): Boolean =
        pe.saniape.app.data.staff.citaPasaFiltroEspecialidad(cita, sel, espsPorTerapeuta, ofrecidos)

    /** Clave de la preferencia: clínica + usuario (null sin sesión: no se guarda). */
    private val claveFiltroEsp: String? by lazy {
        runCatching { pe.saniape.app.data.Supabase.client.auth.currentUserOrNull()?.id }.getOrNull()
            ?.let { "${ctx.clinicaId}:$it" }
    }

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

    /**
     * La especialidad de la cita para los chips (sugerir y aprender técnicas y
     * diagnósticos): la de la cita → la del servicio → la del profesional si tiene
     * UNA. La misma cadena que el filtro de la agenda ([especialidadIdDeCita]).
     */
    fun especialidadDeCita(cita: CitaStaff): String? = pe.saniape.app.data.staff.especialidadIdDeCita(
        cita.especialidadId, cita.especialidadServicioId,
        cita.terapeutaId?.let { espsPorTerapeuta[it] },
    )

    /** ¿Esta cita es de fisioterapia? (EVA, mejorías, dictado al completar). Gemelo de `citaEsFisio`. */
    fun esFisio(cita: CitaStaff): Boolean = pe.saniape.app.data.staff.citaEsFisio(
        ctx.mapaFisio, cita.especialidadId, cita.especialidadServicioId,
        cita.terapeutaId?.let { espsPorTerapeuta[it] },
    )

    /** Ya se resolvió: completada o no asistió (baja al final del día). */
    fun citaYaAtendida(c: CitaStaff): Boolean = c.estado == "Completada" || c.estado == "No asistió"

    /** Primera cita ya atendida de la lista del día (encima va el separador "Ya atendidas"). */
    val primeraAtendidaId: String?
        get() = if (modoLista) null else citasVisibles.firstOrNull { citaYaAtendida(it) }
            ?.takeIf { citasVisibles.any { c -> !citaYaAtendida(c) && c.estado != "Cancelada" } }?.id

    /** Citas tras aplicar los filtros (lo que la pantalla pinta). */
    val citasFiltradas: List<CitaStaff>
        get() {
            val sel = seleccionEspecialidades
            val ofrecidos = especialidades.map { it.id }.toSet()
            return citas.filter { c ->
            (busqueda.isBlank() ||
                (c.pacienteNombre?.contains(busqueda, ignoreCase = true) == true) ||
                (c.procedimiento?.contains(busqueda, ignoreCase = true) == true)) &&
                (filtroEstado == null || c.estado == filtroEstado) &&
                (filtroTipo == null || c.tipo == filtroTipo) &&
                (filtroTerapeuta == null || c.terapeutaId == filtroTerapeuta) &&
                pasaEsp(c, sel, ofrecidos)
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
                // En la agenda de UN día, las ya atendidas bajan (antes de las
                // canceladas): arriba queda a quién falta atender (pedido
                // 29/09/2026, "dando prioridad a los que aún están por atenderse").
                if (!modoLista) {
                    val aHecha = citaYaAtendida(a)
                    val bHecha = citaYaAtendida(b)
                    if (aHecha != bHecha) return@Comparator if (aHecha) 1 else -1
                }
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
        }

    /**
     * Banners con el filtro de especialidad aplicado: mañana y vencidas por la
     * especialidad de la cita; derivaciones por la de DESTINO (la que atenderá).
     * Sin filtro puesto, los mismos de siempre.
     */
    val bannersVisibles: BannersAgenda?
        get() {
            val b = banners ?: return null
            val sel = seleccionEspecialidades
            if (sel.isEmpty()) return b
            val ofrecidos = especialidades.map { it.id }.toSet()
            return b.copy(
                manana = b.manana.filter { pasaEsp(it.cita, sel, ofrecidos) },
                vencidas = b.vencidas.filter { pasaEsp(it, sel, ofrecidos) },
                derivaciones = b.derivaciones.filter {
                    pe.saniape.app.data.staff.pasaFiltroEspecialidad(
                        pe.saniape.app.data.staff.opcionDeEspecialidad(it.especialidadDestinoId, ofrecidos), sel,
                    )
                },
            )
        }

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

    // ── Sala de espera (triaje o flujo médico; solo HOY) ─────────────────────
    // Gemelo de /citas web: cada cita de HOY lleva su etapa de llegada, deducida
    // de su atención (etapaLlegada). Con el triaje, todas las citas de la
    // clínica; solo con el flujo médico, las de atención médica. Sin interruptores
    // (DALU, RENOVA) todo esto queda en null/vacío y NO se consulta nada más.
    private val modulos get() = ctx.modulosClinicos
    val triajeOn: Boolean get() = modulos.triaje
    val salaEsperaOn: Boolean get() = modulos.salaEspera
    /** Campos que mide la clínica en su triaje. */
    val camposTriaje: List<String> get() = modulos.camposTriaje

    /** Lo que dijo el servidor de las atenciones de hoy (por cita). */
    private var llegadasServidor by mutableStateOf<Map<String, pe.saniape.app.data.staff.AtencionLlegada>>(emptyMap())
    /**
     * Lo registrado EN ESTE TELÉFONO que el servidor todavía no confirma (sin
     * señal va a la cola): se pinta encima hasta que la relectura lo traiga.
     */
    private var llegadasLocales by mutableStateOf<Map<String, pe.saniape.app.data.staff.AtencionLlegada>>(emptyMap())

    /** Atención de hoy de una cita (servidor + lo registrado aquí). */
    fun atencionDe(cita: CitaStaff): pe.saniape.app.data.staff.AtencionLlegada? =
        pe.saniape.app.data.staff.fusionarLlegada(llegadasServidor[cita.id], llegadasLocales[cita.id])

    /**
     * ¿La cita va por la consulta guiada? Por CITA (gemela de `esMedicaGuiada`):
     * especialidad médica y no dental. La consulta guiada se abre en la web.
     */
    fun esMedicaGuiada(cita: CitaStaff): Boolean = pe.saniape.app.data.staff.citaEsMedicaGuiada(
        modulos.mapaMedico, esDental(cita), cita.especialidadId, cita.especialidadServicioId,
        cita.terapeutaId?.let { espsPorTerapeuta[it] },
    )

    /** Etapa de llegada de la cita (null = sin sala de espera: la tarjeta de siempre). */
    fun etapaDe(cita: CitaStaff): pe.saniape.app.data.staff.EtapaLlegada? {
        if (!salaEsperaOn || cita.fecha.take(10) != hoy) return null
        if (!triajeOn && !esMedicaGuiada(cita)) return null
        return pe.saniape.app.data.staff.etapaLlegada(cita.estado, false, atencionDe(cita))
    }

    /** Filtro rápido "🪑 En sala de espera (N)" (solo mirando hoy, sin búsqueda). */
    var soloEspera by mutableStateOf(false); private set
    val mirandoHoy: Boolean get() = salaEsperaOn && busqueda.isBlank() && !verHistorial && fechaSel == hoy
    val enEspera: List<CitaStaff>
        get() = if (!mirandoHoy) emptyList()
                else citasFiltradas.filter { pe.saniape.app.data.staff.enSalaDeEspera(etapaDe(it)) }
    /** Lo que la lista pinta: con el filtro, quien llegó primero arriba (es a quien le toca). */
    val citasVisibles: List<CitaStaff>
        get() = if (mirandoHoy && soloEspera) enEspera.sortedBy { c -> atencionDe(c)?.let { it.llegadaAt ?: it.triajeAt }.orEmpty() }
                else citasFiltradas
    fun alternarSoloEspera() { soloEspera = !soloEspera }

    /** Cita a la que se le está marcando la llegada (para el "…" del botón). */
    var marcandoLlegada by mutableStateOf<String?>(null); private set
    var guardandoTriaje by mutableStateOf(false); private set

    /** Relee las atenciones de HOY (solo con sala de espera). Conserva lo que había si falla. */
    private suspend fun recargarLlegadas() {
        if (!salaEsperaOn) return
        val m = runCatching { pe.saniape.app.data.staff.AtencionMedicaRepo.llegadasDelDia(hoy) }.getOrNull() ?: return
        llegadasServidor = m
        // Lo local que el servidor ya tiene, sobra.
        llegadasLocales = llegadasLocales.filterNot { (id, local) -> pe.saniape.app.data.staff.servidorAlDia(m[id], local) }
    }

    /**
     * "🔔 Llegó": el paciente pasa a la sala de espera. Se pinta AL INSTANTE (con
     * o sin señal); si el servidor la rechaza, se deshace y se avisa.
     */
    fun marcarLlegada(cita: CitaStaff) {
        if (marcandoLlegada != null) return
        viewModelScope.launch {
            marcandoLlegada = cita.id
            val previa = atencionDe(cita)
            val ahora = kotlinx.datetime.Clock.System.now().toString()
            val base = previa ?: pe.saniape.app.data.staff.AtencionLlegada(id = null, citaId = cita.id)
            llegadasLocales = llegadasLocales + (cita.id to base.copy(llegadaAt = previa?.llegadaAt ?: ahora))
            val r = pe.saniape.app.data.staff.AtencionMedicaRepo.marcarLlegada(cita.id)
            marcandoLlegada = null
            if (!r.registrada) {
                llegadasLocales = llegadasLocales - cita.id
                pe.saniape.app.ui.Toaster.error(r.rechazo?.error ?: "No se pudo marcar la llegada")
                return@launch
            }
            if (!r.encolada) {
                val quien = cita.pacienteNombre ?: ctx.terminologiaPaciente.Paciente
                pe.saniape.app.ui.Toaster.exito(if (triajeOn) "$quien en sala de espera — falta el triaje" else "$quien en sala de espera")
                recargarLlegadas()
            }
        }
    }

    /**
     * Guarda el triaje de la cita. [onFin] recibe null si quedó registrado (en el
     * servidor o en la cola) o el motivo del rechazo, para mostrarlo en el
     * formulario sin cerrarlo.
     */
    fun guardarTriaje(cita: CitaStaff, valores: Map<String, String>, motivo: String, onFin: (String?) -> Unit) {
        if (guardandoTriaje) return
        viewModelScope.launch {
            guardandoTriaje = true
            val vitales = pe.saniape.app.data.staff.cuerpoVitalesTriaje(valores, camposTriaje)
            val r = pe.saniape.app.data.staff.AtencionMedicaRepo.guardarTriaje(cita.id, vitales, motivo)
            guardandoTriaje = false
            if (!r.registrada) {
                onFin(r.rechazo?.error ?: "No se pudo guardar el triaje")
                return@launch
            }
            val ahora = kotlinx.datetime.Clock.System.now().toString()
            val local = pe.saniape.app.data.staff.atencionLocalDeTriaje(cita.id, valores, camposTriaje, ahora, atencionDe(cita))
            llegadasLocales = llegadasLocales + (cita.id to local)
            if (!r.encolada) {
                pe.saniape.app.ui.Toaster.exito(
                    if (esMedicaGuiada(cita)) "Triaje registrado: el médico lo verá en la consulta" else "Triaje registrado"
                )
                recargarLlegadas()
            }
            onFin(null)
        }
    }

    fun cambiarBusqueda(v: String) { busqueda = v }
    fun cambiarFiltroEstado(v: String?) { filtroEstado = v }
    fun cambiarFiltroTipo(v: String?) { filtroTipo = v }
    fun cambiarFiltroTerapeuta(v: String?) { filtroTerapeuta = v }
    /** Nueva selección del filtro de especialidad (vacía = Todas). Se recuerda en el teléfono. */
    fun cambiarSeleccionEspecialidades(v: List<String>) {
        seleccionEspGuardada = v
        claveFiltroEsp?.let { clave ->
            runCatching {
                pe.saniape.app.data.Preferencias.setFiltroEspecialidadAgenda(clave, pe.saniape.app.data.staff.codificarSeleccion(v))
            }
        }
    }

    // Suscripción Realtime a la tabla `citas`: mantiene la agenda al día sin recargar.
    private var realtimeJob: kotlinx.coroutines.Job? = null

    init {
        // Lo que eligió la última vez en este teléfono (se valida al llegar las especialidades).
        seleccionEspGuardada = claveFiltroEsp?.let { clave ->
            runCatching {
                pe.saniape.app.data.staff.decodificarSeleccion(pe.saniape.app.data.Preferencias.filtroEspecialidadAgenda(clave))
            }.getOrNull()
        }.orEmpty()
        cargarDia(fechaSel)
        cargarAuxiliares()
        // Auto-refresco en vivo: si desde la web se agenda/cambia/cancela una cita, la
        // agenda visible se recarga sola. Best-effort — si no conecta, no pasa nada.
        // Multisede: al cambiar de sede se recargan UNA vez las citas visibles y
        // los avisos (catálogos y profesionales no dependen de la sede). drop(1):
        // el valor actual ya se usó en la carga de arriba. Sin multisede el filtro
        // es siempre null y esto nunca dispara.
        viewModelScope.launch {
            pe.saniape.app.data.staff.SedeActiva.estado
                .map { it.filtro }
                .distinctUntilChanged()
                .drop(1)
                .collect {
                    pagina = 0
                    if (verHistorial) cargarLista() else cargarDia(fechaSel)
                    recargarBanners()
                }
        }
        // Sala de espera: la web refresca las llegadas cada 45 s (recepción marca,
        // el médico lo ve). Lo mismo aquí, y SOLO con triaje/flujo médico.
        if (salaEsperaOn) {
            viewModelScope.launch {
                while (true) {
                    kotlinx.coroutines.delay(45_000)
                    // Mirando otro día no hay nada de hoy que pintar: no se consulta.
                    if (fechaSel == hoy || verHistorial) recargarLlegadas()
                }
            }
        }
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
        citaAgendadaEn = null
        cargarDia(iso)
    }

    /**
     * Fecha de la cita recién agendada cuando NO es el día que se está mirando:
     * la agenda muestra "✓ Agendada para … · Ver día". null = nada que ofrecer.
     */
    var citaAgendadaEn by mutableStateOf<String?>(null); private set

    /** El formulario guardó una cita para [fecha]: si es otro día, ofrecer ir a verlo. */
    fun citaGuardadaEn(fecha: String) {
        citaAgendadaEn = if (verHistorial || fecha == fechaSel) null else fecha
    }
    fun cerrarAvisoCitaAgendada() { citaAgendadaEn = null }

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
            coroutineScope {
                // Las llegadas de hoy van EN PARALELO con la agenda (sin cascada).
                val lleg = async { recargarLlegadas() }
                ponerCitas(runCatching { AgendaRepo.citasDelDia(fecha, ctx.miTerapeutaId) }.getOrDefault(emptyList()))
                lleg.await()
            }
            cargando = false; recargando = false
        }
    }

    private fun cargarLista() {
        viewModelScope.launch {
            if (citas.isEmpty()) cargando = true else recargando = true
            val r = runCatching {
                AgendaRepo.citasPaginadas(verHistorial, pagina, ctx.miTerapeutaId, hoy)
            }.getOrDefault(emptyList())
            ponerCitas(r)
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
                    if (puedeFiltrarPorPersonal || clinicaMixtaDental ||
                        (ctx.modulosClinicos.mapaMedico.activo && !ctx.modulosClinicos.mapaMedico.solo))
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
        recargarLlegadas()
        ponerCitas(runCatching {
            if (verHistorial) AgendaRepo.citasPaginadas(verHistorial, pagina, ctx.miTerapeutaId, hoy)
            else AgendaRepo.citasDelDia(fechaSel, ctx.miTerapeutaId)
        }.getOrDefault(citas))
    }

    // ── 📝 Receta / indicaciones de las citas atendidas ──
    /**
     * ¿La cita (atendida) puede llevar receta / indicaciones? Módulo de recetas +
     * permiso 'sesiones' + especialidad de la cita en el mapa de recetas (DALU:
     * todas). Una clínica sin el módulo (RENOVA) nunca ve nada.
     */
    fun recetaAplica(cita: CitaStaff, terapeutaId: String? = null): Boolean =
        cita.pacienteId != null && pe.saniape.app.data.staff.recetaAplicaAtencion(
            ctx.modulosClinicos, ctx.puede("sesiones"),
            especialidadCitaId = cita.especialidadId,
            especialidadServicioId = cita.especialidadServicioId,
            especialidadesProfesional = (terapeutaId ?: cita.terapeutaId)?.let { espsPorTerapeuta[it] },
            especialidadesDeQuienMira = ctx.miTerapeutaId?.let { espsPorTerapeuta[it] },
        )

    /** Prellenado de la receta de esta cita (quien atendió, su diagnóstico si se escribió). */
    fun prefillReceta(cita: CitaStaff, terapeutaId: String? = null, diagnostico: String? = null) =
        pe.saniape.app.data.staff.prefillRecetaDeCita(cita, terapeutaId, diagnostico, flujoDe(cita).nombreTipo(cita.tipo))

    /** Receta ya vinculada a cada cita completada del día (por cita o por su sesión). */
    var recetasPorCita by mutableStateOf<Map<String, pe.saniape.app.data.staff.RecetaVinculada>>(emptyMap()); private set
    private var recetasJob: kotlinx.coroutines.Job? = null
    /** Citas (ids) de la última lectura buena: la recarga en vivo de la agenda no repite la consulta. */
    private var recetasLeidasDe: Set<String>? = null

    /**
     * El indicador "💊 Receta N°…" / "📋 Indicaciones N°…" de las citas completadas: UNA lectura por
     * lista (por paciente, con índice), en segundo plano y solo con el módulo de
     * recetas encendido. Un fallo deja lo que había (sin indicador): nunca bloquea.
     */
    fun cargarRecetasVinculadas(lista: List<CitaStaff> = citas, forzar: Boolean = false) {
        val completadas = lista.filter { it.estado == "Completada" && recetaAplica(it) }
        val ids = completadas.map { it.id }.toSet()
        // Mismas citas completadas que la última lectura (recarga en vivo, cobro…): nada que leer.
        if (!forzar && ids == recetasLeidasDe) return
        recetasJob?.cancel()
        if (completadas.isEmpty()) { recetasPorCita = emptyMap(); recetasLeidasDe = ids; return }
        recetasJob = viewModelScope.launch {
            val r = pe.saniape.app.data.staff.RecetaAtencionRepo
                .vinculadasDe(completadas.mapNotNull { it.pacienteId }) ?: return@launch
            recetasPorCita = completadas.mapNotNull { c ->
                // La dada desde la agenda lleva su cita_id; la de la ficha, su sesion_id.
                pe.saniape.app.data.staff.recetaDeAtencion(r, c.id, c.sesionId)?.let { c.id to it }
            }.toMap()
            recetasLeidasDe = ids
        }
    }

    /**
     * "¿Ya pagó?" por cita (badge de la tarjeta), lo arma el servidor. UNA
     * petición por lista cargada (no por tarjeta). Vacío = no se muestra nada.
     */
    var estadosPago by mutableStateOf<Map<String, pe.saniape.app.data.staff.EstadoPagoCita>>(emptyMap()); private set
    private var estadosPagoJob: kotlinx.coroutines.Job? = null

    /**
     * ÚNICO lugar donde cambia la lista de citas: la pone y pide, en segundo
     * plano, el estado de pago de las visibles (sin canceladas). Se repite en cada
     * recarga (cambio de día, realtime, tras cobrar/completar). Mientras llega se
     * conserva lo ya sabido de esas mismas citas (sin parpadeo); un fallo deja el
     * mapa vacío (sin badge, la tarjeta de siempre): nunca bloquea la agenda.
     */
    private fun ponerCitas(nuevas: List<CitaStaff>) {
        citas = nuevas
        val ids = nuevas.filter { it.estado != "Cancelada" }.map { it.id }
        estadosPagoJob?.cancel()
        if (ids.isEmpty()) { estadosPago = emptyMap(); return }
        // Las de otro día no aplican: se descartan ya, sin esperar la respuesta.
        estadosPago = estadosPago.filterKeys { it in ids }
        estadosPagoJob = viewModelScope.launch {
            val r = pe.saniape.app.data.staff.CobroRepo.estadosPago(ids)
            estadosPago = r
        }
        cargarMediosPago(nuevas)
        cargarRecetasVinculadas(nuevas)
    }

    /**
     * Con qué medios se pagó cada cita COBRADA ("Efectivo + Yape"), como la
     * moneda de /citas web. Solo para quien ve la caja (permiso 'pagos'): al
     * resto no se le pide ni se le muestra. UNA lectura por lista cargada, en
     * paralelo con el estado de pago; un fallo deja el mapa vacío ("Pagado" a secas).
     */
    var mediosPago by mutableStateOf<Map<String, String>>(emptyMap()); private set
    private var mediosPagoJob: kotlinx.coroutines.Job? = null

    private fun cargarMediosPago(nuevas: List<CitaStaff>) {
        mediosPagoJob?.cancel()
        val pagadas = if (ctx.puede("pagos")) nuevas.filter { it.pagadaAt != null && it.estado != "Cancelada" }.map { it.id } else emptyList()
        if (pagadas.isEmpty()) { mediosPago = emptyMap(); return }
        mediosPago = mediosPago.filterKeys { it in pagadas }
        mediosPagoJob = viewModelScope.launch {
            mediosPago = pe.saniape.app.data.staff.CobroRepo.metodosPagoDeCitas(pagadas)
        }
    }

    /** ¿Ofrecer "Anular cobro" en esta cita? (Admin + pagos + cobro con varios medios). */
    fun puedeAnularCobro(cita: CitaStaff): Boolean = pe.saniape.app.data.staff.puedeAnularCobro(
        ctx.rol, ctx.puede("pagos"), cita.tipo, cita.costo, cita.estado, cita.pagadaAt, mediosPago[cita.id],
    )

    /**
     * Anula el cobro dividido de la cita (solo Admin; el servidor lo valida igual):
     * borra todas sus partes de caja y la cita vuelve a "por cobrar". La atención
     * no se toca. Tras el sí del servidor se recarga la agenda (pagada_at = null).
     */
    fun anularCobro(cita: CitaStaff, onFin: (Boolean) -> Unit = {}) {
        if (accionando) return
        viewModelScope.launch {
            accionando = true
            val error = pe.saniape.app.ui.conIndicador { pe.saniape.app.data.staff.CobroRepo.anularCobro(cita.id) }
            if (error == null) {
                pe.saniape.app.ui.Toaster.exito("Cobro anulado: la cita quedó por cobrar")
                mediosPago = mediosPago - cita.id
                recargarCitas()
                recargarBanners()
            } else {
                pe.saniape.app.ui.Toaster.error(error)
            }
            accionando = false
            onFin(error == null)
        }
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
        /** Fotos de la sesión pendientes de subir: el reintento con profesional no las pierde. */
        val alCompletarSesion: ((ok: Boolean, encolada: Boolean) -> Unit)? = null,
    )
    var pedirProfesional by mutableStateOf<PedidoProfesional?>(null); private set

    fun cerrarPedidoProfesional() {
        // Cerrar sin elegir = la cita no se completó: las fotos no se guardan (se avisa).
        pedirProfesional?.alCompletarSesion?.invoke(false, false)
        pedirProfesional = null
    }

    /**
     * Tras completar una EVALUACIÓN: ofrecer crear el tratamiento sin salir de la
     * agenda (gemelo de /citas web: `abrirTratamientoDeCita` tras completar la
     * evaluación). Solo a quien gestiona tratamientos ('sesiones'), como la web.
     */
    data class OfertaTratamiento(val cita: CitaStaff, val diagnostico: String?)
    var ofrecerTratamiento by mutableStateOf<OfertaTratamiento?>(null); private set
    fun cerrarOfertaTratamiento() { ofrecerTratamiento = null }

    /**
     * Tras completar una SESIÓN: "📅 Agendar siguiente" con la fecha sugerida
     * (intervalo del servicio o +7 días), la MISMA hora y el MISMO profesional de
     * la sesión atendida. Solo si quedan sesiones y no hay ya una futura.
     */
    data class OfertaSiguiente(val cita: CitaStaff, val oferta: pe.saniape.app.data.staff.OfertaSiguienteSesion)
    var ofrecerSiguiente by mutableStateOf<OfertaSiguiente?>(null); private set
    fun cerrarOfertaSiguiente() { ofrecerSiguiente = null }

    /** Cobro que no entró tras completar la sesión desde la agenda (para reintentar SOLO el cobro). */
    data class CobroFallidoAgenda(
        val cita: CitaStaff, val tratamientoId: String, val numero: Int,
        val monto: Double, val metodo: String, val motivo: String?,
    )
    var cobroFallido by mutableStateOf<CobroFallidoAgenda?>(null); private set
    var reintentandoCobro by mutableStateOf(false); private set
    fun cerrarCobroFallido() { cobroFallido = null }

    /**
     * Cobra la sesión recién completada desde la agenda (mismo endpoint que la
     * ficha: pago vinculado a la sesión + caja + recálculo). La sesión puede
     * haber nacido al completar: se lee del vínculo que deja la cita. Si la
     * completación quedó en la cola (sin señal), el pago entra también a la cola
     * como abono del tratamiento con la fecha en la nota (como "Nueva sesión").
     * Devuelve null si entró, o el motivo del fallo.
     */
    private suspend fun cobrarSesionDeCita(
        cita: CitaStaff, tratamientoId: String, monto: Double, metodo: String, encolada: Boolean,
    ): Pair<Int, String?> {
        if (encolada) {
            val ok = pe.saniape.app.data.staff.PacientesRepo.registrarPago(
                tratamientoId, monto, metodo, "Pago de la sesión del ${cita.fecha}")
            if (ok) pe.saniape.app.data.staff.MetodoPagoPreferido.recordar(cita.pacienteId, metodo)
            return (cita.numeroSesion ?: 0) to (if (ok) null else "no se pudo guardar el pago")
        }
        val vinculo = AgendaRepo.sesionDeCita(cita.id)
            ?: return (cita.numeroSesion ?: 0) to "no se encontró la sesión de esta cita"
        val r = pe.saniape.app.data.staff.PacientesRepo.cobrarSesionDetalle(tratamientoId, vinculo.first, monto, metodo, null)
        val numero = vinculo.second ?: cita.numeroSesion ?: 0
        if (r.registrada) {
            pe.saniape.app.data.staff.MetodoPagoPreferido.recordar(cita.pacienteId, metodo)
            if (!r.encolada) pe.saniape.app.ui.Toaster.exito("Pago registrado ($metodo)")
            return numero to null
        }
        return numero to (r.rechazo?.error ?: "no se pudo registrar")
    }

    /** Reintenta SOLO el cobro (la sesión ya quedó completada). */
    fun reintentarCobro() {
        val cf = cobroFallido ?: return
        if (reintentandoCobro) return
        viewModelScope.launch {
            reintentandoCobro = true
            val (_, motivo) = cobrarSesionDeCita(cf.cita, cf.tratamientoId, cf.monto, cf.metodo, encolada = false)
            reintentandoCobro = false
            cobroFallido = if (motivo == null) null else cf.copy(motivo = motivo)
            // Entró el pago: el badge "¿ya pagó?" de esa cita cambia.
            if (motivo == null) ponerCitas(citas)
        }
    }

    /**
     * "✗ No vino": registra la falta ("No asistió") en vez de cancelar. En una
     * Sesión la sesión queda "No asistió" (no se borra). [onFin] = ¿quedó registrado?
     */
    fun noAsistio(cita: CitaStaff, motivo: String?, onFin: (Boolean) -> Unit) {
        if (accionando) return
        viewModelScope.launch {
            accionando = true
            val r = AgendaRepo.noAsistio(cita.id, motivo)
            if (r.registrada) {
                if (!r.encolada) pe.saniape.app.ui.Toaster.exito("Falta registrada (No asistió)")
            } else pe.saniape.app.ui.Toaster.error(r.rechazo?.error ?: "No se pudo registrar la falta")
            recargarCitas()
            recargarBanners()
            accionando = false
            onFin(r.registrada)
        }
    }

    /** Reintenta el completar con el profesional elegido en el selector. */
    fun completarConProfesional(terapeutaId: String) {
        val p = pedirProfesional ?: return
        pedirProfesional = null
        ejecutar(
            AccionCita.Completar, p.cita, p.observaciones, p.diagnostico, p.derivarEspId,
            piezas = p.piezas, congelarOdontograma = p.congelarOdontograma, terapeutaId = terapeutaId,
            evaluacionFisio = p.evaluacionFisio,
            alCompletarSesion = p.alCompletarSesion,
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
        /** Sesión: (monto, método) si el paciente pagó en el mismo cierre (null = no se cobra). */
        pago: Pair<Double, String>? = null,
        /**
         * Evaluación dental en UN paso (29/09/2026): el toast de éxito propio
         * ("Evaluación completada · …") en vez del genérico "Cita completada".
         */
        textoExito: String? = null,
        /**
         * false = no ofrecer el form de crear tratamiento al terminar. La
         * evaluación dental ya resolvió su tratamiento desde el presupuesto:
         * ofrecerlo aquí sería un segundo plan por lo mismo (como la web).
         */
        ofrecerPlan: Boolean = true,
        /**
         * false = no ofrecer "📅 Agendar siguiente" al terminar la sesión. Con
         * "dejarle ejercicios de apoyo" se va a la ficha (pestaña 🏠) y ese
         * diálogo se abriría encima; como la web, ahí se omite.
         */
        ofrecerSiguienteSesion: Boolean = true,
        /**
         * Resultado para quien necesita saberlo (la revisión dental: solo se
         * cierra si quedó completada, y si no, avisa que el tratamiento ya se
         * creó; la sesión de fisio con "dejarle ejercicios": solo entonces se
         * abre la ficha). Se llama tras recargar la agenda.
         */
        alTerminar: ((Boolean) -> Unit)? = null,
        /**
         * Completar: resultado detallado (completó, quedó en la cola sin señal) para
         * las fotos de la sesión. Se llama SIEMPRE que se pasó (también si no se
         * completó), para que las fotos nunca se pierdan en silencio.
         */
        alCompletarSesion: ((ok: Boolean, encolada: Boolean) -> Unit)? = null,
    ) {
        if (accionando) {
            // Callado dejaba la revisión dental en "Guardando…" para siempre.
            if (alTerminar != null || alCompletarSesion != null) {
                pe.saniape.app.ui.Toaster.info("Espera a que termine la acción en curso")
                alTerminar?.invoke(false)
                alCompletarSesion?.invoke(false, false)
            }
            return
        }
        viewModelScope.launch {
            accionando = true; mensaje = null
            var completadaEncolada = false
            val ok = when (accion) {
                AccionCita.Confirmar -> AgendaRepo.confirmar(cita.id)
                AccionCita.Completar -> {
                    val r = AgendaRepo.completarDetalle(
                        cita.id, observaciones, diagnostico, derivarEspId,
                        piezas = piezas, congelarOdontograma = congelarOdontograma,
                        terapeutaId = terapeutaId,
                        mejorias = mejorias, eva = eva,
                    )
                    completadaEncolada = r.encolada
                    when {
                        r.registrada -> true
                        // Error de NEGOCIO, no de red: no se encola ni se reintenta solo.
                        // Falta quién atendió → se ofrece el selector en vez de un toast mudo.
                        r.codigo == "SIN_PROFESIONAL" -> {
                            pedirProfesional = PedidoProfesional(
                                cita, observaciones, diagnostico, derivarEspId, piezas, congelarOdontograma,
                                evaluacionFisio, alCompletarSesion,
                            )
                            accionando = false
                            alTerminar?.invoke(false)
                            return@launch
                        }
                        else -> {
                            r.rechazo?.let { pe.saniape.app.ui.Toaster.error(it.error) }
                            // Sin rechazo = ni se pudo encolar: el toast genérico de abajo.
                            if (r.rechazo != null) {
                                recargarCitas(); accionando = false
                                alTerminar?.invoke(false)
                                alCompletarSesion?.invoke(false, false)
                                return@launch
                            }
                            false
                        }
                    }
                }
                AccionCita.Revertir -> AgendaRepo.revertir(cita.id)
                AccionCita.Cancelar -> AgendaRepo.cancelar(cita.id)
            }
            if (ok) {
                // Aprende lo escrito para sugerirlo luego (RPC registrar_chips con la
                // especialidad de la cita). En segundo plano: nunca demora ni hace fallar
                // el cierre. Sin señal (quedó en la cola) se descarta: no se encola.
                if (accion == AccionCita.Completar && !completadaEncolada) {
                    val espChips = especialidadDeCita(cita)
                    if (!diagnostico.isNullOrBlank()) {
                        pe.saniape.app.data.staff.DiagnosticosRepo.registrar(diagnostico, espChips, cita.pacienteNombre)
                    } else if (!observaciones.isNullOrBlank()) {
                        // Fuera de la evaluación, lo escrito sale del campo de técnicas
                        // ("Procedimientos realizados"): antes el cierre desde la agenda no
                        // las aprendía (solo la ficha y Sesiones).
                        pe.saniape.app.data.staff.TecnicasRepo.registrar(observaciones, espChips, cita.pacienteNombre)
                    }
                }
                // 📝 "¿Le dejas indicaciones?": la barra sale DESPUÉS del éxito (completar
                // sigue siendo un viaje). Sin señal (quedó en la cola) no: la receta
                // necesita red y la cita aún no está completada en el servidor.
                if (accion == AccionCita.Completar && !completadaEncolada && recetaAplica(cita, terapeutaId)) {
                    prefillReceta(cita.copy(estado = "Completada"), terapeutaId, diagnostico)
                        ?.let { pe.saniape.app.ui.clinica.recetas.RecetaTrasAtencion.ofrecer(it) }
                }
                val txt = when (accion) {
                    AccionCita.Confirmar -> "Cita confirmada"
                    AccionCita.Completar -> "Cita completada"
                    AccionCita.Revertir -> "Cita revertida"
                    AccionCita.Cancelar -> "Cita cancelada"
                }
                pe.saniape.app.ui.Toaster.exito(textoExito ?: txt)
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
                if (accion == AccionCita.Completar && cita.tipo == "Evaluación" && ofrecerPlan &&
                    ctx.puede("sesiones") && cita.pacienteId != null) {
                    ofrecerTratamiento = OfertaTratamiento(
                        cita.copy(terapeutaId = terapeutaId ?: cita.terapeutaId, estado = "Completada"),
                        diagnostico?.trim()?.ifBlank { null },
                    )
                }
                val tratSesion = cita.tratamientoId
                if (accion == AccionCita.Completar && cita.tipo == "Sesión" && tratSesion != null) {
                    // Cobro en el mismo cierre (solo con permiso de pagos; el endpoint
                    // también lo valida). Si falla, se ofrece reintentar SOLO el cobro.
                    if (pago != null && ctx.puede("pagos")) {
                        val (numero, motivo) = cobrarSesionDeCita(cita, tratSesion, pago.first, pago.second, completadaEncolada)
                        if (motivo != null) {
                            cobroFallido = CobroFallidoAgenda(cita, tratSesion, numero, pago.first, pago.second, motivo)
                        }
                    }
                    // "📅 Agendar siguiente": lee el tratamiento YA sincronizado. Sin
                    // señal (encolada) no se ofrece: no se sabe si ya tiene la siguiente.
                    if (ofrecerSiguienteSesion && !completadaEncolada && ctx.puede("sesiones") && cita.pacienteId != null) {
                        val atendida = cita.copy(terapeutaId = terapeutaId ?: cita.terapeutaId)
                        viewModelScope.launch {
                            pe.saniape.app.data.staff.SiguienteSesionRepo.evaluar(tratSesion, hoy, excluirCitaId = cita.id)
                                ?.let { ofrecerSiguiente = OfertaSiguiente(atendida, it) }
                        }
                    }
                }
            } else pe.saniape.app.ui.Toaster.error("No se pudo, intenta de nuevo")
            recargarCitas()
            recargarBanners()
            accionando = false
            alTerminar?.invoke(ok)
            if (accion == AccionCita.Completar) alCompletarSesion?.invoke(ok, completadaEncolada)
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

    /**
     * Cobrar una Consulta/Evaluación (gemelo del "💵 Registrar cobro" de /citas web):
     * [modo] "cobrar" | "abonar" | "gratis"; [fecha] (yyyy-MM-dd) = día en que el
     * paciente pagó (por defecto HOY, Lima: la caja del día en que se cobra); [pagos] = cobro dividido en varios
     * medios (null = un solo [metodo]). [onFin] recibe true si quedó registrado (en
     * el servidor o en la cola): solo entonces se cierra el modal, así un rechazo
     * (p. ej. sin tratamiento al cual abonar, o un cobro incierto que se repite con
     * el mismo reparto) deja el formulario tal cual.
     */
    fun cobrar(
        cita: CitaStaff, metodo: String, modo: String, fecha: String,
        pagos: List<pe.saniape.app.data.staff.PartePago>? = null, onFin: (Boolean) -> Unit = {},
    ) {
        if (accionando) return
        viewModelScope.launch {
            accionando = true
            val r = AgendaRepo.cobrarCita(cita.id, metodo, modo, fecha, pagos)
            if (r.registrada) {
                // Encolada: enviarOEncolar ya avisó "se registrará al volver la señal".
                if (!r.encolada) {
                    pe.saniape.app.ui.Toaster.exito(
                        pe.saniape.app.ui.clinica.atencion.textoCobrado(
                            flujoDe(cita).nombreTipo(cita.tipo),
                            pe.saniape.app.ui.clinica.agenda.modales.textoSoles(cita.costo ?: 0.0, pe.saniape.app.data.staff.monedaDeFila(cita.sedeId)),
                            // Bugs caja 2026-10-08: "fechado el…" solo si no es HOY (de la sede activa).
                            modo, fecha, hoyIso(), pagos, r.yaEstaba,
                        )
                    )
                }
                recargarCitas()
                recargarBanners()
            } else {
                // Sin rechazo = ni se pudo encolar (o doble toque, que ya avisó).
                r.rechazo?.let {
                    pe.saniape.app.ui.Toaster.error(pe.saniape.app.data.staff.mensajeRechazoCobro(it, pagos != null))
                }
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