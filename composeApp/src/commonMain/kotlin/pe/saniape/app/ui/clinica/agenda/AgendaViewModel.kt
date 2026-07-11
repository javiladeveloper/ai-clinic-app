package pe.saniape.app.ui.clinica.agenda

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.launch
import kotlinx.datetime.Clock
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.plus
import kotlinx.datetime.toLocalDateTime
import pe.saniape.app.data.staff.AgendaBanners
import pe.saniape.app.data.staff.AgendaRepo
import pe.saniape.app.data.staff.BannersAgenda
import pe.saniape.app.data.staff.CitaStaff
import pe.saniape.app.data.staff.ContextoStaff
import pe.saniape.app.data.staff.EspecialidadRef
import pe.saniape.app.data.staff.TerapeutaRef

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
    var cargando by mutableStateOf(true); private set
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
            //  2) Entre las demás (completadas incluidas): por HORA ascendente.
            //  3) Desempate a misma hora Y MISMO paciente: la Evaluación arriba de la Consulta
            //     (el paciente pasa consulta → evaluación; se prioriza la evaluación).
            Comparator { a, b ->
                val aCanc = a.estado == "Cancelada"
                val bCanc = b.estado == "Cancelada"
                if (aCanc != bCanc) return@Comparator if (aCanc) 1 else -1
                val ha = a.hora.take(5)
                val hb = b.hora.take(5)
                if (ha != hb) return@Comparator ha.compareTo(hb)
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

    fun cambiarBusqueda(v: String) { busqueda = v }
    fun cambiarFiltroEstado(v: String?) { filtroEstado = v }
    fun cambiarFiltroTipo(v: String?) { filtroTipo = v }
    fun cambiarFiltroTerapeuta(v: String?) { filtroTerapeuta = v }
    fun cambiarFiltroEspecialidad(v: String?) { filtroEspecialidad = v }

    init {
        cargarDia(fechaSel)
        cargarAuxiliares()
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
            cargando = true
            citas = runCatching { AgendaRepo.citasDelDia(fecha, ctx.miTerapeutaId) }.getOrDefault(emptyList())
            cargando = false
        }
    }

    private fun cargarLista() {
        viewModelScope.launch {
            cargando = true
            val r = runCatching {
                AgendaRepo.citasPaginadas(verHistorial, pagina, ctx.miTerapeutaId, hoy)
            }.getOrDefault(emptyList())
            citas = r
            hayMasPaginas = r.size >= AgendaRepo.PAGE_SIZE
            cargando = false
        }
    }

    private fun cargarAuxiliares() {
        viewModelScope.launch {
            especialidades = runCatching { AgendaRepo.especialidades() }.getOrDefault(emptyList())
            if (puedeFiltrarPorPersonal) {
                terapeutas = runCatching { AgendaRepo.terapeutasActivos() }.getOrDefault(emptyList())
            }
            recargarBanners()
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

    /** Acción genérica sobre una cita. Refresca citas + banners al terminar. */
    fun ejecutar(
        accion: AccionCita, cita: CitaStaff,
        observaciones: String? = null, diagnostico: String? = null, derivarEspId: String? = null,
    ) {
        if (accionando) return
        viewModelScope.launch {
            accionando = true; mensaje = null
            val ok = when (accion) {
                AccionCita.Confirmar -> AgendaRepo.confirmar(cita.id)
                AccionCita.Completar -> AgendaRepo.completar(cita.id, observaciones, diagnostico, derivarEspId)
                AccionCita.Revertir -> AgendaRepo.revertir(cita.id)
                AccionCita.Cancelar -> AgendaRepo.cancelar(cita.id)
            }
            if (ok) {
                val txt = when (accion) {
                    AccionCita.Confirmar -> "Cita confirmada"
                    AccionCita.Completar -> "Cita completada"
                    AccionCita.Revertir -> "Cita revertida"
                    AccionCita.Cancelar -> "Cita cancelada"
                }
                pe.saniape.app.ui.Toaster.exito(txt)
            } else pe.saniape.app.ui.Toaster.error("No se pudo, intenta de nuevo")
            recargarCitas()
            recargarBanners()
            accionando = false
        }
    }

    /** Reprogramar (cambia fecha/hora). Escritura simple. */
    fun reprogramar(cita: CitaStaff, fecha: String, hora: String, onFin: (Boolean) -> Unit) {
        if (accionando) return
        viewModelScope.launch {
            accionando = true
            // Pasa tipo/tratamiento/fecha previa para sincronizar la sesión vinculada.
            val ok = AgendaRepo.reprogramar(
                cita.id, fecha, hora,
                tipo = cita.tipo, tratamientoId = cita.tratamientoId, fechaAntes = cita.fecha,
            )
            recargarCitas()
            recargarBanners()
            if (ok) pe.saniape.app.ui.Toaster.exito("Cita reprogramada")
            else pe.saniape.app.ui.Toaster.error("No se pudo reprogramar")
            accionando = false
            onFin(ok)
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
fun hoyIso(): String {
    val d = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).date
    return d.iso()
}

fun mananaIso(hoy: String): String {
    val p = hoy.split("-")
    return LocalDate(p[0].toInt(), p[1].toInt(), p[2].toInt()).plus(DatePeriod(days = 1)).iso()
}

internal fun LocalDate.iso(): String =
    "$year-${monthNumber.toString().padStart(2, '0')}-${dayOfMonth.toString().padStart(2, '0')}"