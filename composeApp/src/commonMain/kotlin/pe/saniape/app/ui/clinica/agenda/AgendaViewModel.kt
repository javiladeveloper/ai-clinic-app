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

    val miTerapeutaId: String? get() = ctx.miTerapeutaId
    val esGestor: Boolean get() = ctx.esGestor

    /** Citas tras aplicar los filtros (lo que la pantalla pinta). */
    val citasFiltradas: List<CitaStaff>
        get() = citas.filter { c ->
            (busqueda.isBlank() ||
                (c.pacienteNombre?.contains(busqueda, ignoreCase = true) == true) ||
                (c.procedimiento?.contains(busqueda, ignoreCase = true) == true)) &&
                (filtroEstado == null || c.estado == filtroEstado) &&
                (filtroTipo == null || c.tipo == filtroTipo)
        }

    fun cambiarBusqueda(v: String) { busqueda = v }
    fun cambiarFiltroEstado(v: String?) { filtroEstado = v }
    fun cambiarFiltroTipo(v: String?) { filtroTipo = v }

    init {
        cargarDia(fechaSel)
        cargarAuxiliares()
    }

    // ── Intents ──
    fun seleccionarDia(iso: String) {
        fechaSel = iso
        cargarDia(iso)
    }

    fun limpiarMensaje() { mensaje = null }

    private fun cargarDia(fecha: String) {
        viewModelScope.launch {
            cargando = true
            citas = runCatching { AgendaRepo.citasDelDia(fecha, ctx.miTerapeutaId) }.getOrDefault(emptyList())
            cargando = false
        }
    }

    private fun cargarAuxiliares() {
        viewModelScope.launch {
            especialidades = runCatching { AgendaRepo.especialidades() }.getOrDefault(emptyList())
            recargarBanners()
        }
    }

    private suspend fun recargarBanners() {
        banners = runCatching {
            AgendaBanners.cargar(hoy, mananaIso(hoy), ctx.miTerapeutaId, ctx.esGestor)
        }.getOrNull()
    }

    /** Acción genérica sobre una cita. Refresca día + banners al terminar. */
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
            mensaje = if (ok) "✓ Listo" else "⚠ No se pudo, intenta de nuevo"
            citas = runCatching { AgendaRepo.citasDelDia(fechaSel, ctx.miTerapeutaId) }.getOrDefault(citas)
            recargarBanners()
            accionando = false
        }
    }

    /** Reprogramar (cambia fecha/hora). Escritura simple. */
    fun reprogramar(cita: CitaStaff, fecha: String, hora: String, onFin: (Boolean) -> Unit) {
        if (accionando) return
        viewModelScope.launch {
            accionando = true
            val ok = AgendaRepo.reprogramar(cita.id, fecha, hora)
            citas = runCatching { AgendaRepo.citasDelDia(fechaSel, ctx.miTerapeutaId) }.getOrDefault(citas)
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
            citas = runCatching { AgendaRepo.citasDelDia(fechaSel, ctx.miTerapeutaId) }.getOrDefault(citas)
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

    /** Recarga el día actual y los banners (tras crear cita). */
    fun refrescar() {
        cargarDia(fechaSel)
        viewModelScope.launch { recargarBanners() }
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