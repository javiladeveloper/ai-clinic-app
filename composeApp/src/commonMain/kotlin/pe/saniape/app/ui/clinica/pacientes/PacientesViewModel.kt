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
            // MOSTRAR Y REFRESCAR: si no hay nada en pantalla, se pinta al
            // instante lo último que se vio (caché local) y se refresca por
            // detrás. Sin esto la pantalla arranca en blanco y el fisio espera
            // a la red, que en una clínica es lo peor que se siente.
            if (pacientes.isEmpty()) {
                val deCache = PacientesRepo.listarDeCache(ctx.scopePacientes)
                if (deCache.isNotEmpty()) { pacientes = deCache; recargando = true }
                else cargando = true
            } else recargando = true
            cargaFallo = false
            // Scope: gestor (permiso pacientes) ve toda la clínica; en modo clínico,
            // solo los suyos. Ver ContextoStaff.scopePacientes.
            runCatching { PacientesRepo.listar(ctx.scopePacientes) }
                .onSuccess { pacientes = it }
                .onFailure { cargaFallo = true }
            cargando = false; recargando = false
        }
    }

    /** Lista tras búsqueda + filtro de estado (default: oculta Inactivos). */
    val filtrados: List<PacienteStaff>
        get() = pacientes.filter { p ->
            coincideBusqueda(p.nombre, p.dni.takeIf { verContacto }, p.diagnostico, busqueda) &&
                when (filtroEstado) {
                    null -> p.estado != "Inactivo"   // por defecto, sin inactivos
                    "todos" -> true
                    else -> p.estado == filtroEstado
                }
        }
}

/** Minúsculas y SIN TILDES, para comparar nombres escritos a las apuradas. */
internal fun normalizarBusqueda(s: String): String = buildString {
    for (c in s.lowercase().trim()) {
        append(
            when (c) {
                'á', 'à', 'ä', 'â' -> 'a'
                'é', 'è', 'ë', 'ê' -> 'e'
                'í', 'ì', 'ï', 'î' -> 'i'
                'ó', 'ò', 'ö', 'ô' -> 'o'
                'ú', 'ù', 'ü', 'û' -> 'u'
                'ñ' -> 'n'
                else -> c
            }
        )
    }
}

/**
 * ¿Este paciente coincide con lo que se escribió en el buscador?
 *
 * Cada palabra por separado y en cualquier orden, sin exigir que estén pegadas.
 * Antes era `nombre.contains(busqueda)` —la frase literal— y eso rompía el caso
 * más común: buscar por nombre y apellido.
 *
 * "jorge oli" no encontraba a "JORGE YOCELYN OLIVERA GAMERO", porque entre
 * "jorge" y "oli" está el segundo nombre. En producción 399 de 873 pacientes
 * (46%) tienen cuatro o más palabras en el nombre, y 137 llevan alguna tilde
 * que nadie teclea al buscar.
 *
 * El documento y el diagnóstico se buscan con el texto COMPLETO: partirlos en
 * palabras daría falsos positivos.
 *
 * Mismo criterio que la web (components/ui/BuscadorPaciente.tsx y
 * hooks/usePacientes.ts): un paciente se busca igual desde donde sea.
 */
internal fun coincideBusqueda(
    nombre: String,
    dni: String?,
    diagnostico: String?,
    busqueda: String,
): Boolean {
    val q = normalizarBusqueda(busqueda)
    if (q.isBlank()) return true
    val sinEspacios = q.filterNot { it.isWhitespace() }
    if (!dni.isNullOrBlank() && dni.lowercase().contains(sinEspacios)) return true
    if (!diagnostico.isNullOrBlank() && normalizarBusqueda(diagnostico).contains(q)) return true
    val nombreNorm = normalizarBusqueda(nombre)
    return q.split(Regex("\\s+")).filter { it.isNotBlank() }.all { nombreNorm.contains(it) }
}