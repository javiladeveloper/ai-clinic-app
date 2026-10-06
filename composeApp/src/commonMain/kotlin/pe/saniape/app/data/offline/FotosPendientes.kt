package pe.saniape.app.data.offline

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import pe.saniape.app.data.Preferencias
import pe.saniape.app.ui.ArchivoSeleccionado
import pe.saniape.app.ui.Toaster

/**
 * FOTOS DE SESIÓN QUE ESPERAN SEÑAL.
 *
 * La cola offline ([ColaRepo]) guarda JSON, no binarios: meter fotos ahí era un
 * cambio grande (blobs en SQLite, payloads de MB en cada `pendientes()`). Así
 * que las fotos van a un almacén APARTE y simple:
 *  · los bytes (ya comprimidos al elegirlas, ~300 KB) en archivos privados de la
 *    app ([ArchivosLocales]), nunca en memoria;
 *  · la lista (a qué paciente/tratamiento/sesión van) como JSON en Preferencias.
 *
 * Se suben SOLAS después de cada ciclo del [Sincronizador] (al volver la señal,
 * al abrir la app y tras cada escritura encolada), y solo cuando la sesión ya
 * existe en el servidor: mientras en la cola quede una operación que nombra su
 * sesión o su cita (el "completar" sin señal), la foto espera.
 *
 * Tope: [MAX_FOTOS_PENDIENTES] fotos y [MAX_BYTES_PENDIENTES] en espera; lo
 * que no cabe se avisa (no se pierde en silencio).
 */

const val MAX_FOTOS_PENDIENTES = 10
const val MAX_BYTES_PENDIENTES = 20L * 1024 * 1024
/** Ciclos fallidos antes de descartar una foto (con aviso): cubre días sin buena señal. */
const val MAX_INTENTOS_FOTO = 15

/** Una foto de sesión guardada en el teléfono hasta que se pueda subir. */
@Serializable
data class FotoPendiente(
    val id: String,
    val pacienteId: String,
    val tratamientoId: String,
    /** La sesión, si se conoce (ficha/sesiones). Puede ser un `tmp-` de la cola. */
    val sesionId: String? = null,
    /** La cita (agenda): la sesión se busca por la cita una vez sincronizada. */
    val citaId: String? = null,
    val nombre: String,
    val mime: String? = null,
    /** Nombre del archivo en [ArchivosLocales]. */
    val archivo: String,
    val bytes: Long,
    val visiblePaciente: Boolean = false,
    val creadaMs: Long = 0,
    val intentos: Int = 0,
    /** Ya subida al bucket (falta registrarla): no se vuelve a subir el archivo. */
    val ruta: String? = null,
    val tipo: String? = null,
)

/**
 * Cuáles de las fotos nuevas caben (en orden), dados los topes de cantidad y
 * tamaño contando lo que ya espera. Pura, para probar los límites sin disco.
 */
fun admitirFotos(
    existentes: List<FotoPendiente>,
    tamanosNuevas: List<Long>,
    maxFotos: Int = MAX_FOTOS_PENDIENTES,
    maxBytes: Long = MAX_BYTES_PENDIENTES,
): List<Boolean> {
    var n = existentes.size
    var total = existentes.sumOf { it.bytes }
    return tamanosNuevas.map { t ->
        val cabe = t > 0 && n + 1 <= maxFotos && total + t <= maxBytes
        if (cabe) { n++; total += t }
        cabe
    }
}

/** La sesión real de la foto: la suya, o la que el servidor asignó a su `tmp-`. null si aún no hay. */
fun sesionResuelta(foto: FotoPendiente, mapa: Map<String, String>): String? {
    val s = foto.sesionId ?: return null
    return if (s.startsWith("tmp-")) mapa[s] else s
}

/**
 * ¿Ya se puede subir? No mientras en la cola quede una operación pendiente que
 * nombre su sesión (real o `tmp-`) o su cita: la sesión aún no se completó en el
 * servidor. Tampoco si su sesión es un `tmp-` sin id real todavía.
 */
fun fotoLista(foto: FotoPendiente, payloadsPendientes: List<String>, mapa: Map<String, String>): Boolean {
    if (foto.sesionId?.startsWith("tmp-") == true && mapa[foto.sesionId] == null) return false
    val claves = listOfNotNull(foto.sesionId, sesionResuelta(foto, mapa), foto.citaId).filter { it.isNotBlank() }.distinct()
    return payloadsPendientes.none { p -> claves.any { p.contains(it) } }
}

/** Resultado de guardar fotos para después: cuántas entraron y cuántas no cupieron (o no se pudieron escribir). */
data class GuardadoFotos(val guardadas: Int, val descartadas: Int)

/** El aviso al guardar fotos sin señal (puro, para probar los textos). */
fun avisoFotosGuardadas(r: GuardadoFotos): Pair<Boolean, String> {
    val total = r.guardadas + r.descartadas
    val nTotal = if (total == 1) "la foto" else "las $total fotos"
    return when {
        r.descartadas == 0 -> true to "Sin señal: $nTotal de la sesión quedaron en el teléfono y se subirán solas al volver la señal."
        r.guardadas == 0 -> false to "Sin señal y sin espacio para más fotos en espera (máx. $MAX_FOTOS_PENDIENTES fotos / ${MAX_BYTES_PENDIENTES / (1024 * 1024)} MB): $nTotal no se guardaron. Súbelas desde la galería del tratamiento con señal."
        else -> false to "Sin señal: ${r.guardadas} de $total fotos quedaron en el teléfono y se subirán solas; ${r.descartadas} no cupieron (máx. $MAX_FOTOS_PENDIENTES fotos / ${MAX_BYTES_PENDIENTES / (1024 * 1024)} MB en espera). Súbelas desde la galería del tratamiento."
    }
}

object FotosPendientes {
    private const val CLAVE = "fotos_sesion_pendientes_v1"
    private val json = Json { ignoreUnknownKeys = true }
    private val serializador = ListSerializer(FotoPendiente.serializer())
    /** Cambios a la lista (cortos). */
    private val mutexLista = Mutex()
    /** Una sola tanda de subida a la vez. */
    private val mutexSubida = Mutex()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private fun leerLista(): List<FotoPendiente> = runCatching {
        Preferencias.texto(CLAVE)?.let { json.decodeFromString(serializador, it) }
    }.getOrNull().orEmpty()

    private fun escribirLista(l: List<FotoPendiente>) {
        Preferencias.setTexto(CLAVE, if (l.isEmpty()) null else json.encodeToString(serializador, l))
    }

    private suspend fun cambiar(f: (List<FotoPendiente>) -> List<FotoPendiente>) = mutexLista.withLock {
        escribirLista(f(leerLista()))
    }

    /** Cuántas fotos esperan señal. */
    suspend fun cantidad(): Int = mutexLista.withLock { leerLista().size }

    /**
     * Guarda en el teléfono las fotos de una sesión completada SIN SEÑAL, para
     * subirlas solas después. [sesionId] y/o [citaId] dicen a qué sesión van.
     */
    suspend fun guardar(
        pacienteId: String, tratamientoId: String, sesionId: String?, citaId: String?,
        fotos: List<ArchivoSeleccionado>, visiblePaciente: Boolean,
    ): GuardadoFotos {
        if (fotos.isEmpty() || pacienteId.isBlank()) return GuardadoFotos(0, fotos.size)
        var guardadas = 0
        mutexLista.withLock {
            val lista = leerLista()
            val admite = admitirFotos(lista, fotos.map { it.bytes.size.toLong() })
            val nuevas = mutableListOf<FotoPendiente>()
            fotos.forEachIndexed { i, a ->
                if (!admite[i]) return@forEachIndexed
                val id = nuevaIdemKey()
                val ext = a.nombre.substringAfterLast('.', "jpg").lowercase().take(5).filter { it.isLetterOrDigit() }.ifEmpty { "jpg" }
                val archivo = "$id.$ext"
                if (!ArchivosLocales.guardar(archivo, a.bytes)) return@forEachIndexed
                nuevas += FotoPendiente(
                    id = id, pacienteId = pacienteId, tratamientoId = tratamientoId,
                    sesionId = sesionId?.takeIf { it.isNotBlank() }, citaId = citaId?.takeIf { it.isNotBlank() },
                    nombre = a.nombre, mime = a.mime, archivo = archivo, bytes = a.bytes.size.toLong(),
                    visiblePaciente = visiblePaciente, creadaMs = ahoraMs(),
                )
            }
            if (nuevas.isNotEmpty()) escribirLista(lista + nuevas)
            guardadas = nuevas.size
        }
        return GuardadoFotos(guardadas, fotos.size - guardadas)
    }

    /** Lanza una tanda de subida sin bloquear (la llama el Sincronizador al terminar cada ciclo). */
    fun disparar() {
        scope.launch { runCatching { subir() } }
    }

    private suspend fun quitar(f: FotoPendiente) {
        cambiar { l -> l.filterNot { it.id == f.id } }
        ArchivosLocales.borrar(f.archivo)
    }

    /** Suma un intento fallido; pasado el tope se descarta (devuelve true si se descartó). */
    private suspend fun fallo(f: FotoPendiente): Boolean {
        if (f.intentos + 1 >= MAX_INTENTOS_FOTO) {
            quitar(f)
            return true
        }
        cambiar { l -> l.map { if (it.id == f.id) it.copy(intentos = it.intentos + 1) else it } }
        return false
    }

    /**
     * Sube las fotos listas: archivo al bucket (como "Subir foto" de la galería)
     * y registro como foto evolutiva "Durante" del tratamiento, ligada a su
     * sesión. Un fallo de red corta la tanda (se reintenta en el próximo ciclo).
     */
    suspend fun subir() {
        if (!mutexSubida.tryLock()) return
        try {
            val lista = mutexLista.withLock { leerLista() }
            if (lista.isEmpty()) return
            val pendientes = ColaRepo.pendientes().map { it.payload }
            val mapa = ColaRepo.mapa()
            var subidas = 0
            var descartadas = 0
            for (foto in lista) {
                if (!fotoLista(foto, pendientes, mapa)) continue
                val bytes = ArchivosLocales.leer(foto.archivo)
                if (bytes == null) { quitar(foto); descartadas++; continue }
                var actual = foto
                if (actual.ruta == null) {
                    when (val r = pe.saniape.app.data.staff.SolicitudesRepo.subirArchivoDetalle(
                        foto.pacienteId, foto.nombre, bytes, foto.mime, "foto")) {
                        is pe.saniape.app.data.staff.SubidaArchivo.Ok -> {
                            actual = foto.copy(ruta = r.path, tipo = r.tipo)
                            cambiar { l -> l.map { if (it.id == foto.id) actual else it } }
                        }
                        is pe.saniape.app.data.staff.SubidaArchivo.Error -> {
                            if (r.limitePlan) {
                                quitar(foto); descartadas++
                                Toaster.error(r.mensaje)
                                continue
                            }
                            if (fallo(foto)) descartadas++
                            break // casi siempre es la red: se reintenta en el próximo ciclo
                        }
                    }
                }
                val sesion = sesionResuelta(actual, mapa)
                    ?: actual.citaId?.let { pe.saniape.app.data.staff.AgendaRepo.sesionDeCita(it)?.first }
                val ok = runCatching {
                    pe.saniape.app.data.staff.FotosRepo.registrarFoto(
                        actual.pacienteId, actual.tratamientoId, sesion, actual.nombre,
                        actual.ruta.orEmpty(), actual.tipo.orEmpty(), "Durante", null, actual.visiblePaciente,
                    )
                }.getOrDefault(false)
                if (ok) { quitar(actual); subidas++ }
                else { if (fallo(actual)) descartadas++; break }
            }
            if (subidas > 0) {
                pe.saniape.app.ui.clinica.pacientes.FotosSesionVersion.valor++
                Toaster.exito(if (subidas == 1) "Se subió la foto de la sesión que esperaba señal" else "Se subieron $subidas fotos de sesión que esperaban señal")
            }
            if (descartadas > 0) {
                Toaster.error(if (descartadas == 1) "Una foto de sesión que esperaba señal no se pudo subir y se descartó"
                    else "$descartadas fotos de sesión que esperaban señal no se pudieron subir y se descartaron")
            }
        } finally {
            mutexSubida.unlock()
        }
    }
}
