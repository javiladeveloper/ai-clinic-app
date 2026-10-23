package pe.saniape.app.ui.clinica.pacientes

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import pe.saniape.app.data.offline.EstadoRed
import pe.saniape.app.data.staff.ContextoStaff
import pe.saniape.app.data.staff.RecargaLista
import pe.saniape.app.data.staff.listaFresca
import pe.saniape.app.data.staff.medirEnDepuracion
import pe.saniape.app.data.staff.recargaAlVolverDeFicha
import pe.saniape.app.data.staff.relojMonotonoMs
import pe.saniape.app.data.staff.PacienteStaff
import pe.saniape.app.data.staff.PacientesRepo
import pe.saniape.app.data.staff.SedeActiva

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

    /**
     * Pacientes por sede: sede por la que se filtra la lista (null = todos). La
     * pantalla la usa para decir "Mostrando pacientes de <Sede>".
     */
    var sedeFiltrada by mutableStateOf(SedeActiva.estado.value.sedePacientes); private set

    private var trabajo: Job? = null

    // Frescura (DALU, 23/10/2026): cada vuelta a la pestaña bajaba la lista entera, a
    // veces dos veces seguidas. Se recuerda cuándo terminó la última carga buena (y de
    // qué sede) y qué aviso de "volvió la red" ya se atendió.
    private var ultimaCargaOkMs: Long? = null
    private var sedeUltimaCarga: String? = null
    private var tokenRedVisto: Int = EstadoRed.token.value

    /** La lista en pantalla tiene menos de un minuto y es de la sede vigente. */
    private fun listaEsFresca(): Boolean =
        sedeUltimaCarga == SedeActiva.estado.value.sedePacientes && listaFresca(ultimaCargaOkMs, relojMonotonoMs())

    init {
        cargar()
        // Al cambiar la sede activa (o la regla), la lista es OTRA: se vacía y se
        // recarga (sin pintar un instante la lista de la sede anterior).
        viewModelScope.launch {
            SedeActiva.estado.map { it.sedePacientes }.distinctUntilChanged().drop(1).collect { nueva ->
                sedeFiltrada = nueva
                pacientes = emptyList()
                cargar()
            }
        }
    }

    fun cambiarBusqueda(v: String) { busqueda = v }
    fun cambiarFiltroEstado(v: String?) { filtroEstado = v }

    /** [trasCambio] = recién se creó/registró un paciente: no sumarse a una carga en camino. */
    fun cargar(trasCambio: Boolean = false) {
        // Una carga a la vez: si cambió la sede a mitad de camino, la vieja no pisa a la nueva.
        trabajo?.cancel()
        trabajo = viewModelScope.launch {
            val sede = SedeActiva.estado.value.sedePacientes
            sedeFiltrada = sede
            // Spinner completo solo la 1ª vez; si ya hay lista (volver de ficha / refresh),
            // se mantiene visible con aviso sutil "Actualizando…" (no parpadea a vacío).
            // MOSTRAR Y REFRESCAR: si no hay nada en pantalla, se pinta al
            // instante lo último que se vio (caché local) y se refresca por
            // detrás. Sin esto la pantalla arranca en blanco y el fisio espera
            // a la red, que en una clínica es lo peor que se siente.
            if (pacientes.isEmpty()) {
                // Leer y decodificar la caché fuera del hilo de la pantalla.
                val deCache = withContext(Dispatchers.Default) { PacientesRepo.listarDeCache(ctx.scopePacientes, sede) }
                if (deCache.isNotEmpty()) { pacientes = deCache; recargando = true }
                else cargando = true
            } else recargando = true
            cargaFallo = false
            // Scope: gestor (permiso pacientes) ve toda la clínica; en modo clínico,
            // solo los suyos. Ver ContextoStaff.scopePacientes.
            try {
                // Sin teléfono ni correo si la lista no los muestra (modo clínico).
                pacientes = PacientesRepo.listar(ctx.scopePacientes, sede, conContacto = verContacto, compartir = !trasCambio)
                ultimaCargaOkMs = relojMonotonoMs()
                sedeUltimaCarga = sede
            } catch (e: CancellationException) {
                throw e   // la reemplazó otra carga: no tocar el estado
            } catch (_: Exception) {
                cargaFallo = true
            }
            cargando = false; recargando = false
        }
    }

    /**
     * Se volvió a mostrar la lista (otra pestaña → Pacientes): solo se pide si ya no es
     * fresca y no hay una carga en camino.
     */
    fun alMostrar() {
        if (trabajo?.isActive == true) return
        if (!listaEsFresca()) cargar()
    }

    /**
     * Vuelta de la ficha. Si se cambió algo, basta con releer ESE paciente (es lo único
     * que pudo cambiar); si la lista ya es vieja, entera. Antes: siempre entera.
     */
    fun alVolverDeFicha(pacienteId: String?, huboCambios: Boolean) {
        if (trabajo?.isActive == true) return   // una carga en camino ya lo trae
        when (recargaAlVolverDeFicha(huboCambios, listaEsFresca(), pacienteId)) {
            RecargaLista.NADA -> Unit
            RecargaLista.COMPLETA -> cargar()
            RecargaLista.SOLO_PACIENTE -> pacienteId?.let { refrescarPaciente(it) }
        }
    }

    /**
     * Volvió la red: se recarga solo si el aviso es NUEVO para la lista. Al volver a la
     * pestaña el `LaunchedEffect` vuelve a correr con el mismo token, y antes eso
     * disparaba una segunda carga encima de la de la vuelta (la doble carga del 19/10).
     */
    fun alVolverLaRed(token: Int) {
        if (token <= tokenRedVisto) return
        tokenRedVisto = token
        cargar()
    }

    /** Relee la fila de UN paciente y la reemplaza en la lista (no toca la frescura del resto). */
    private fun refrescarPaciente(id: String) {
        trabajo = viewModelScope.launch {
            val sede = SedeActiva.estado.value.sedePacientes
            recargando = true
            try {
                val fila = medirEnDepuracion<pe.saniape.app.data.staff.PacienteStaff?>("releer un paciente de la lista") {
                    PacientesRepo.filaLista(id, sede, conContacto = verContacto)
                }
                // Ya no se ve (fusionado, de otra sede…): sale de la lista.
                pacientes = if (fila == null) pacientes.filterNot { it.id == id }
                    else pacientes.map { if (it.id == id) fila else it }
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                // Sin red: se queda lo que había (la próxima carga completa lo corrige).
            }
            recargando = false
        }
    }

    /** Lista tras búsqueda + filtro de estado (default: oculta Inactivos). */
    val filtrados: List<PacienteStaff>
        get() = pacientes.filter { p ->
            coincideBusqueda(p.nombre, p.dni.takeIf { verContacto },
                p.diagnostico.takeIf { pe.saniape.app.data.staff.verHistoriaClinica(ctx) }, busqueda) &&
                when (filtroEstado) {
                    // Por defecto, sin inactivos — EXCEPTO al buscar: a quien vuelve
                    // tras la baja hay que poder encontrarlo (si no, "no existe" y se
                    // registra de nuevo). Igual que la web.
                    null -> p.estado != "Inactivo" || busqueda.isNotBlank()
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