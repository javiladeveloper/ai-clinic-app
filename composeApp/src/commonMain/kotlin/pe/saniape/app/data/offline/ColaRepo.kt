package pe.saniape.app.data.offline

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonObject
import kotlin.random.Random

/** Una operación de escritura esperando (o intentando) sincronizarse. */
data class OpPendiente(
    val id: Long,
    val idemKey: String,
    val tipo: String,
    val endpoint: String,
    val payload: String,
    val dependeDe: Long?,
    val idTemporal: String?,
    val intentos: Int,
)

/**
 * Único acceso a la cola local de escrituras.
 *
 * Toda escritura de la app pasa por aquí: se guarda primero en el dispositivo
 * (sobrevive cierres de app y falta de señal) y el [Sincronizador] la envía
 * cuando hay red. Así ninguna sesión/cita/pago se pierde por conectividad.
 */
object ColaRepo {
    private val q get() = DbLocal.db.colaQueries

    /**
     * Guarda una operación pendiente y devuelve su id en la cola.
     * [idTemporal]: si la operación CREA algo que la UI ya está mostrando con un
     * id "tmp-…", para reconciliarlo con el id real al sincronizar.
     * [dependeDe]: id de otra operación que debe completarse antes que esta.
     */
    suspend fun encolar(
        tipo: String,
        endpoint: String,
        payload: JsonObject,
        idTemporal: String? = null,
        dependeDe: Long? = null,
    ): Long = withContext(Dispatchers.Default) {
        q.encolar(
            idempotency_key = nuevaIdemKey(),
            tipo = tipo,
            endpoint = endpoint,
            payload = payload.toString(),
            depende_de = dependeDe,
            id_temporal = idTemporal,
            created_at = ahoraMs(),
        )
        // La UI muestra el pendiente YA (aunque la sincronización aún no corra):
        // así el usuario ve al instante que su registro quedó guardado.
        EstadoSync.actualizar(q.contarPendientes().executeAsOne())
        q.ultimoId().executeAsOne()
    }

    /** Operaciones que faltan enviar, en orden de encolado. */
    suspend fun pendientes(): List<OpPendiente> = withContext(Dispatchers.Default) {
        q.pendientes().executeAsList().map {
            OpPendiente(
                id = it.id,
                idemKey = it.idempotency_key,
                tipo = it.tipo,
                endpoint = it.endpoint,
                payload = it.payload,
                dependeDe = it.depende_de,
                idTemporal = it.id_temporal,
                intentos = it.intentos.toInt(),
            )
        }
    }

    suspend fun marcarHecha(id: Long) = withContext(Dispatchers.Default) {
        q.marcarEstado("hecha", null, id)
    }

    /** Error definitivo del servidor: no se reintenta (sus dependientes quedan bloqueados). */
    suspend fun marcarFallida(id: Long, error: String) = withContext(Dispatchers.Default) {
        q.marcarEstado("fallida", error, id)
    }

    /** Fallo de red: sigue pendiente y se reintenta en el próximo ciclo. */
    suspend fun volverAPendiente(id: Long, error: String) = withContext(Dispatchers.Default) {
        q.sumarIntento(error, id)
        q.marcarEstado("pendiente", error, id)
    }

    /** Guarda la equivalencia id temporal → id real que asignó el servidor. */
    suspend fun guardarMapa(idTemporal: String, idReal: String) = withContext(Dispatchers.Default) {
        q.guardarMapa(idTemporal, idReal)
    }

    suspend fun mapa(): Map<String, String> = withContext(Dispatchers.Default) {
        q.todoElMapa().executeAsList().associate { it.id_temporal to it.id_real }
    }

    /** Cuántas operaciones faltan (lo que la UI muestra como "N pendientes"). */
    suspend fun contarPendientes(): Long = withContext(Dispatchers.Default) {
        q.contarPendientes().executeAsOne()
    }

    /** Limpia las ya sincronizadas (para que la tabla no crezca sin fin). */
    suspend fun limpiarHechas() = withContext(Dispatchers.Default) {
        q.limpiarHechas()
    }
}

/**
 * Clave de idempotencia de la operación: el servidor la usa para no duplicar si
 * un reintento llega dos veces. Se genera al ENCOLAR (no al enviar), para que
 * todos los reintentos de la misma operación compartan la misma clave.
 */
internal fun nuevaIdemKey(): String =
    (1..32).map { "0123456789abcdef"[Random.nextInt(16)] }.joinToString("")

/** Epoch en milisegundos (solo se usa para ordenar la cola). */
internal expect fun ahoraMs(): Long
