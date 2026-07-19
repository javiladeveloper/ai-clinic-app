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
        // Si el envío inline ya usó una clave, se reusa aquí: así el reintento no
        // duplica si aquel sí había llegado al servidor.
        idemKey: String? = null,
    ): Long = withContext(Dispatchers.Default) {
        q.encolar(
            idempotency_key = idemKey ?: nuevaIdemKey(),
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

    /**
     * Error definitivo: no se reintenta. Marca también EN CASCADA las operaciones
     * que quedarían huérfanas — las que referencian el id temporal de esta: si el
     * paciente no se pudo crear, su cita/tratamiento NUNCA podrían enviarse (su
     * `tmp-` jamás tendrá id real). Sin esto quedarían pendientes para siempre y el
     * chip nunca bajaría a 0.
     *
     * La detección es por el id temporal dentro del payload (no por `depende_de`,
     * que hoy nadie setea: las operaciones se encolan sueltas y el orden por id +
     * la traducción de temporales son lo que garantiza la secuencia correcta).
     */
    suspend fun marcarFallida(id: Long, error: String) = withContext(Dispatchers.Default) {
        q.marcarEstado("fallida", error, id)
        val tmp = q.porId(id).executeAsOneOrNull()?.id_temporal ?: return@withContext
        q.pendientes().executeAsList().forEach { op ->
            if (op.payload.contains(tmp)) {
                q.marcarEstado("fallida", "depende de una operación que falló: $error", op.id)
            }
        }
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
