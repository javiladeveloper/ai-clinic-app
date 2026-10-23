package pe.saniape.app.data.staff

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.time.TimeSource
import pe.saniape.app.data.VersionApp

/*
 * Rendimiento de Pacientes ↔ ficha (DALU, 23/10/2026).
 *
 * La Lic. Kathy (rol «Fisios», sin permiso de pacientes) cierra sesiones en tanda
 * desde la ficha en un Android 10 de gama baja, y la app "se congelaba de rato en
 * rato". El servidor respondía en 15–60 ms: el costo estaba en el camino de la app.
 *  - Cada vuelta a Pacientes bajaba los 609 pacientes de la clínica (con contacto)
 *    más dos consultas de hasta 3000 filas, y a veces dos veces seguidas.
 *  - La ficha se recargaba entera ante CUALQUIER cambio de citas/tratamientos/
 *    sesiones de toda la clínica (Realtime sin filtro ni espera).
 *
 * Aquí vive la lógica PURA de ese arreglo (con sus tests en commonTest); los
 * repositorios y las pantallas solo la aplican.
 */

// ── Reloj y medición ────────────────────────────────────────────────────────────

private val origenReloj = TimeSource.Monotonic.markNow()

/** Milisegundos de un reloj MONÓTONO (no salta si el usuario cambia la hora del teléfono). */
fun relojMonotonoMs(): Long = origenReloj.elapsedNow().inWholeMilliseconds

/**
 * Mide [bloque] y deja la duración en el logcat, SOLO en la build de depuración
 * (en la de la tienda no escribe nada). [detalle] se arma al final (p. ej. "45 pacientes").
 */
suspend fun <T> medirEnDepuracion(etiqueta: String, detalle: (T) -> String = { "" }, bloque: suspend () -> T): T {
    if (!VersionApp.depuracion) return bloque()
    val inicio = relojMonotonoMs()
    var resultado: Result<T>? = null
    try {
        val r = bloque()
        resultado = Result.success(r)
        return r
    } finally {
        val ms = relojMonotonoMs() - inicio
        val extra = resultado?.getOrNull()?.let { r -> runCatching { detalle(r) }.getOrDefault("") }.orEmpty()
        val estado = if (resultado == null) " (falló o se canceló)" else ""
        println("SaniaRendimiento: $etiqueta ${ms} ms${if (extra.isNotBlank()) " · $extra" else ""}$estado")
    }
}

// ── Lista de pacientes: frescura y vuelta de la ficha ─────────────────────────

/** Una lista cargada hace menos de esto no se vuelve a pedir al volver a la pestaña. */
const val VENTANA_LISTA_FRESCA_MS = 60_000L

/**
 * ¿La lista que está en pantalla es FRESCA? [ultimaCargaMs] = cuándo terminó la última
 * carga buena (reloj monótono), null = nunca. Un reloj que "va hacia atrás" (otro
 * proceso, otro arranque) cuenta como vieja: mejor una recarga de más que datos viejos.
 */
fun listaFresca(ultimaCargaMs: Long?, ahoraMs: Long, ventanaMs: Long = VENTANA_LISTA_FRESCA_MS): Boolean {
    if (ultimaCargaMs == null) return false
    val edad = ahoraMs - ultimaCargaMs
    return edad in 0 until ventanaMs
}

/** Qué hacer con la lista al volver de la ficha. */
enum class RecargaLista {
    /** Nada: la lista es fresca y en la ficha no se cambió nada. */
    NADA,
    /** Solo la fila del paciente que se abrió (se cambió algo suyo y la lista es fresca). */
    SOLO_PACIENTE,
    /** La lista entera (es vieja). */
    COMPLETA,
}

/**
 * Al volver de la ficha. Lo que se cambió en la ficha es del paciente abierto (una
 * sesión, un pago, un tratamiento), así que basta con releer SU fila. Si la lista ya
 * es vieja, se pide entera (y trae también ese cambio).
 */
fun recargaAlVolverDeFicha(huboCambios: Boolean, fresca: Boolean, pacienteId: String?): RecargaLista = when {
    !fresca -> RecargaLista.COMPLETA
    huboCambios && !pacienteId.isNullOrBlank() -> RecargaLista.SOLO_PACIENTE
    huboCambios -> RecargaLista.COMPLETA
    else -> RecargaLista.NADA
}

// ── Lista del profesional: solo SUS pacientes, pedidos al servidor ────────────

/**
 * Ids por tanda para un `in.(…)`: cada uuid ocupa ~37 caracteres en la URL, y 120 por
 * pedido dejan la URL en ~4,5 KB (lejos del límite del proxy). Sin repetidos ni vacíos.
 */
const val TAMANO_TANDA_IDS = 120

fun partirEnTandas(ids: Collection<String>, tamano: Int = TAMANO_TANDA_IDS): List<List<String>> =
    ids.filter { it.isNotBlank() }.distinct().chunked(tamano.coerceAtLeast(1))

/** Tope de filas del servidor (max-rows de PostgREST en Supabase). Llegar a él = respuesta cortada. */
const val TOPE_FILAS_SERVIDOR = 1000

/**
 * Una fila de "¿qué pacientes atiendo?": el paciente y el `terapeuta_id` de lo que la
 * hizo coincidir (la cita / sesión / tratamiento embebido). Sirve para VERIFICAR la
 * respuesta: si el servidor ignorara el filtro embebido, traería pacientes de otros.
 */
data class FilaAtiende(val pacienteId: String?, val terapeutasEmbebidos: List<String?>)

/**
 * Ids de los pacientes que [yo] atiende según [filas], o null si la respuesta NO es
 * confiable: alguna fila sin nada embebido o con algo de otro profesional (el filtro
 * no se aplicó), o la respuesta llegó cortada en el tope del servidor. Con null el
 * llamador cae al camino de siempre (la lista completa filtrada en el teléfono), así
 * que nunca se ve ni más ni menos que antes.
 */
fun idsVerificados(filas: List<FilaAtiende>, yo: String, tope: Int = TOPE_FILAS_SERVIDOR): Set<String>? {
    if (filas.size >= tope) return null
    val ids = mutableSetOf<String>()
    for (f in filas) {
        val id = f.pacienteId?.takeIf { it.isNotBlank() } ?: return null
        if (f.terapeutasEmbebidos.isEmpty() || f.terapeutasEmbebidos.any { it != yo }) return null
        ids.add(id)
    }
    return ids
}

// ── Catálogos que casi no cambian ─────────────────────────────────────────────

/** Vigencia de los catálogos de la ficha (especialidades): cada apertura los pedía de nuevo. */
const val VIGENCIA_CATALOGO_FICHA_MS = 5 * 60_000L

/**
 * Recuerda UN valor por [clave] (la clínica) durante [vigenciaMs]. Para catálogos que
 * la ficha pedía en cada apertura y que no cambian en minutos. Lo vacío no se guarda
 * (quien la usa decide): un fallo de red no deja la ficha sin catálogo 5 minutos.
 */
class CacheCorta<T>(private val vigenciaMs: Long = VIGENCIA_CATALOGO_FICHA_MS) {
    private var guardado: Triple<String, Long, T>? = null

    fun leer(clave: String, ahoraMs: Long): T? =
        guardado?.takeIf { it.first == clave && listaFresca(it.second, ahoraMs, vigenciaMs) }?.third

    fun guardar(clave: String, ahoraMs: Long, valor: T) { guardado = Triple(clave, ahoraMs, valor) }
}

// ── Peticiones en vuelo ───────────────────────────────────────────────────────

/**
 * Une las peticiones IGUALES que se piden mientras otra sigue en camino: la segunda
 * espera la respuesta de la primera en vez de mandar otra. Era la doble carga de la
 * lista (21:24:35 / 21:24:37): al volver de la ficha la pedían a la vez la recarga de
 * la vuelta y la de "volvió la red", y la segunda cancelaba a la primera, que el
 * servidor ya estaba resolviendo.
 *
 * La petición corre en [scope] (no en el de quien la pidió): si el primero se va de
 * la pantalla, la respuesta igual le sirve al segundo. Una respuesta ya terminada no
 * se reutiliza (eso es la frescura, que decide la pantalla).
 */
class PeticionesEnVuelo<T>(private val scope: CoroutineScope) {
    private val mutex = Mutex()
    private val activas = mutableMapOf<String, Deferred<T>>()

    /** [reutilizar] = false: tras un cambio propio, la que está en camino puede no traerlo. */
    suspend fun obtener(clave: String, reutilizar: Boolean = true, bloque: suspend () -> T): T {
        val d = mutex.withLock {
            activas[clave]?.takeIf { reutilizar && it.isActive }
                ?: scope.async { bloque() }.also { activas[clave] = it }
        }
        // Si se cancela QUIEN espera (salió de la pantalla), la petición sigue para el otro.
        return d.await()
    }
}

// ── Realtime de la ficha: solo lo del paciente abierto, y en ráfaga ───────────

/**
 * ¿Este evento de Realtime es del paciente abierto?
 *  - `citas` / `tratamientos`: por `paciente_id` (fila nueva o vieja).
 *  - `sesiones`: no tienen `paciente_id`; por `tratamiento_id` contra los tratamientos
 *    del paciente que la ficha ya tiene cargados.
 * [idNuevo]/[idViejo] = esa columna en la fila nueva / vieja. Si no viene en ninguna
 * (un DELETE sin la fila completa) no se puede saber: cuenta como suyo, para no
 * perder un cambio (la ráfaga lo junta con los demás, así que cuesta poco).
 */
fun eventoEsDelPaciente(
    tabla: String,
    idNuevo: String?,
    idViejo: String?,
    pacienteId: String,
    tratamientosDelPaciente: Set<String>,
): Boolean {
    val ids = listOfNotNull(idNuevo?.takeIf { it.isNotBlank() && it != "null" }, idViejo?.takeIf { it.isNotBlank() && it != "null" })
    if (ids.isEmpty()) return true
    return when (tabla) {
        "sesiones" -> ids.any { it in tratamientosDelPaciente }
        else -> ids.any { it == pacienteId }
    }
}

/** Columna que dice de quién es una fila de [tabla] (ver [eventoEsDelPaciente]). */
fun columnaDuenoEvento(tabla: String): String = if (tabla == "sesiones") "tratamiento_id" else "paciente_id"

/** Espera de la ráfaga: un completar dispara 3–5 eventos seguidos (sesión, cita, contador…). */
const val ESPERA_RAFAGA_MS = 800L

/**
 * Junta una ráfaga de eventos en UNA recarga: se dispara cuando pasan [esperaMs] sin
 * eventos nuevos (debounce). Máquina de estados pura (el reloj lo pone quien la usa)
 * para poder probarla sin corrutinas.
 */
class RafagaEventos(private val esperaMs: Long = ESPERA_RAFAGA_MS) {
    private var ultimoEvento: Long? = null

    /** Llegó un evento en [ahoraMs]. */
    fun evento(ahoraMs: Long) { ultimoEvento = ahoraMs }

    /** ms que faltan para disparar: null = no hay nada pendiente; 0 = ya. */
    fun faltan(ahoraMs: Long): Long? = ultimoEvento?.let { (it + esperaMs - ahoraMs).coerceAtLeast(0) }

    /** Dispara: devuelve cuándo llegó el ÚLTIMO evento de la ráfaga (y la vacía). */
    fun disparar(): Long? = ultimoEvento.also { ultimoEvento = null }
}

/** Qué hacer con una ráfaga de eventos del paciente abierto. */
enum class AccionRealtime {
    /** Recargar la ficha. */
    RECARGAR,
    /** Nada: una carga empezó DESPUÉS del último evento, así que ya lo trae (el propio cambio, p. ej.). */
    YA_INCLUIDO,
    /** Esperar: está abierto el cierre de sesión; se recarga al cerrarlo (si hace falta). */
    POSPONER,
}

/**
 * [ultimoEventoMs] = cuándo llegó el último evento de la ráfaga; [ultimaCargaIniciadaMs]
 * = cuándo empezó la última carga de la ficha (mismo reloj). Que el evento haya llegado
 * implica que su cambio ya estaba guardado: una carga que empezó después lo incluye.
 * Es lo que evita recargar la ficha 2–3 veces tras completar una sesión desde ella.
 */
fun accionRealtime(ultimoEventoMs: Long, ultimaCargaIniciadaMs: Long?, dialogoAbierto: Boolean): AccionRealtime = when {
    ultimaCargaIniciadaMs != null && ultimaCargaIniciadaMs >= ultimoEventoMs -> AccionRealtime.YA_INCLUIDO
    dialogoAbierto -> AccionRealtime.POSPONER
    else -> AccionRealtime.RECARGAR
}
