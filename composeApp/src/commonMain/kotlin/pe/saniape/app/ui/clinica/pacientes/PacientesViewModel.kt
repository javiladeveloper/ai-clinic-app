package pe.saniape.app.ui.clinica.pacientes

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.launch
import pe.saniape.app.data.staff.ContextoStaff
import pe.saniape.app.data.staff.PacienteStaff
import pe.saniape.app.data.staff.PacientesRepo

/**
 * ViewModel de la lista de Pacientes (staff). Carga, búsqueda y filtro de estado.
 * Respeta el scope del profesional vinculado (solo sus pacientes) y el modoClinico
 * (sin contacto). La fuente de las reglas es el ContextoStaff resuelto en el server.
 */
class PacientesViewModel(private val ctx: ContextoStaff) : ViewModel() {

    var cargando by mutableStateOf(true); private set    // spinner completo (solo 1ª carga)
    var recargando by mutableStateOf(false); private set   // aviso sutil (refresh/volver de ficha)
    // Distingue "falló la carga" (red/permiso) de "no hay pacientes" — así la UI ofrece
    // reintentar en vez de mentir con "no hay pacientes".
    var cargaFallo by mutableStateOf(false); private set
    var pacientes by mutableStateOf<List<PacienteStaff>>(emptyList()); private set
    var busqueda by mutableStateOf(""); private set
    var filtroEstado by mutableStateOf<String?>(null); private set

    /** Admin/recepción ve contacto (DNI/teléfono); en modoClinico se oculta. */
    val verContacto: Boolean get() = ctx.esGestor && !ctx.modoClinico

    init { cargar() }

    fun cambiarBusqueda(v: String) { busqueda = v }
    fun cambiarFiltroEstado(v: String?) { filtroEstado = v }

    fun cargar() {
        viewModelScope.launch {
            // Spinner completo solo la 1ª vez; si ya hay lista (volver de ficha / refresh),
            // se mantiene visible con aviso sutil "Actualizando…" (no parpadea a vacío).
            if (pacientes.isEmpty()) cargando = true else recargando = true
            cargaFallo = false
            // Scope: si es profesional vinculado, solo sus pacientes.
            runCatching { PacientesRepo.listar(ctx.miTerapeutaId) }
                .onSuccess { pacientes = it }
                .onFailure { cargaFallo = true }
            cargando = false; recargando = false
        }
    }

    /** Lista tras búsqueda + filtro de estado (default: oculta Inactivos). */
    val filtrados: List<PacienteStaff>
        get() = pacientes.filter { p ->
            (busqueda.isBlank() ||
                p.nombre.contains(busqueda, ignoreCase = true) ||
                (verContacto && p.dni?.contains(busqueda, ignoreCase = true) == true) ||
                (p.diagnostico?.contains(busqueda, ignoreCase = true) == true)) &&
                when (filtroEstado) {
                    null -> p.estado != "Inactivo"   // por defecto, sin inactivos
                    "todos" -> true
                    else -> p.estado == filtroEstado
                }
        }
}