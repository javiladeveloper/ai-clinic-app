package pe.saniape.app.data.offline

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.JsonPrimitive
import pe.saniape.app.ui.Toaster

/**
 * Envía una escritura al servidor y, SOLO si falla, la deja en la cola local.
 *
 * Por qué en ese orden: con señal el comportamiento es idéntico al de siempre
 * (la pantalla puede recargar y ya ve su cambio). La cola entra en juego
 * únicamente cuando de verdad hace falta —sin conexión o servidor caído—, y ahí
 * se avisa al usuario que lo suyo quedó guardado y se subirá solo.
 *
 * La `idempotency_key` se genera UNA vez y se reusa en el reintento encolado: si
 * el envío inline en realidad llegó al servidor (y solo se perdió la respuesta),
 * el reintento no duplica.
 *
 * Devuelve true siempre que la operación quedó registrada (en el servidor o en la
 * cola); false solo si ni siquiera se pudo encolar.
 */
suspend fun enviarOEncolar(
    tipo: String,
    endpoint: String,
    cuerpo: JsonObject,
    idTemporal: String? = null,
    dependeDe: Long? = null,
): Boolean {
    val r = enviarOEncolarDetalle(tipo, endpoint, cuerpo, idTemporal, dependeDe)
    // El "no" del servidor se muestra con SU texto (sin cupo, ficha dada de baja,
    // sin permiso…). Antes se perdía y el usuario solo veía "No se pudo" sin saber
    // qué arreglar. Quien necesita reaccionar al código usa [enviarOEncolarDetalle].
    r.rechazo?.let { Toaster.error(it.error) }
    return r.registrada
}

/**
 * Resultado de una escritura: se registró (en el servidor o en la cola), o el
 * servidor la rechazó ([rechazo] con su texto y `codigo`), o ni se intentó
 * (doble toque / no se pudo encolar).
 */
data class ResultadoEscritura(
    /** true = quedó registrada: en el servidor ([encolada]=false) o en la cola local. */
    val registrada: Boolean,
    val encolada: Boolean = false,
    val rechazo: RechazoServidor? = null,
    /**
     * El JSON que respondió el servidor (si lo hubo), para quien necesita leer
     * lo que devuelve el endpoint (p. ej. la receta emitida o el id de la cita
     * de control). La cola offline no lo usa: siempre queda en null.
     */
    val cuerpo: JsonObject? = null,
) {
    val codigo: String? get() = rechazo?.codigo
}

/**
 * Como [enviarOEncolar], pero devuelve el detalle y NO muestra el rechazo: la
 * pantalla decide (p. ej. ante SIN_PROFESIONAL abre el selector de profesional
 * en vez de un toast).
 */
suspend fun enviarOEncolarDetalle(
    tipo: String,
    endpoint: String,
    cuerpo: JsonObject,
    idTemporal: String? = null,
    dependeDe: Long? = null,
    /**
     * La clave de idempotencia a usar; null = una nueva. Quien recibió una
     * respuesta INCIERTA (5xx, timeout) la conserva y la pasa aquí al reintentar
     * la MISMA operación, para que el servidor la reconozca y no la duplique.
     */
    idemKey: String? = null,
): ResultadoEscritura {
    // Clave LÓGICA de la operación ("qué se está haciendo", no "qué envío es"):
    // tipo + el id sobre el que actúa. Dos toques del mismo botón comparten clave.
    val claveLogica = "$tipo|" + listOf("sesionId", "citaId", "pagoId", "tratamientoId", "pacienteId")
        .firstNotNullOfOrNull { k -> (cuerpo[k] as? JsonPrimitive)?.contentOrNull }.orEmpty()

    // Si esa MISMA gestión ya está en curso, no se manda otra vez: sin esto, el
    // usuario que no ve respuesta vuelve a tocar y se crean DOS sesiones/cobros
    // (la idempotencia no lo evita: cada toque genera su propia clave).
    val reservada = mutexEnVuelo.withLock { enVuelo.add(claveLogica) }
    if (!reservada) {
        Toaster.error("Esta operación se está guardando — espera unos segundos, no la repitas")
        return ResultadoEscritura(registrada = false)
    }
    try {
        return pe.saniape.app.ui.conIndicador {
            enviarOEncolarInterno(tipo, endpoint, cuerpo, idTemporal, dependeDe, idemKey)
        }
    } finally {
        mutexEnVuelo.withLock { enVuelo.remove(claveLogica) }
    }
}

/**
 * Gestiones en curso (por clave lógica), para no repetirlas con un segundo toque.
 * Con mutex: hoy las gestiones salen del hilo principal, pero el sincronizador ya
 * corre en Dispatchers.Default y no conviene depender de esa invariante.
 */
private val enVuelo = mutableSetOf<String>()
private val mutexEnVuelo = Mutex()

/**
 * Toda escritura pasa por aquí, así que envolverla con el indicador cubre TODAS
 * las gestiones de la app (sesiones, pagos, citas, tratamientos) de una vez, sin
 * tener que acordarse en cada pantalla.
 */
private suspend fun enviarOEncolarInterno(
    tipo: String,
    endpoint: String,
    cuerpo: JsonObject,
    idTemporal: String?,
    dependeDe: Long?,
    idemKeyDada: String?,
): ResultadoEscritura {
    val idemKey = idemKeyDada ?: nuevaIdemKey()

    // Si el payload trae ids temporales, no tiene sentido intentarlo inline:
    // el servidor no los conoce. Va directo a la cola, que los traducirá.
    val tieneTemporales = cuerpo.toString().contains("tmp-")
    if (!tieneTemporales) {
        val (resultado, rechazo, respuesta) = runCatching { Sincronizador.enviarAhoraCompleto(endpoint, cuerpo, idemKey) }
            .getOrDefault(Triple(ResultadoEnvio.SIN_RED, null, null))
        when (resultado) {
            ResultadoEnvio.OK -> {
                // Tutoriales: la acción real terminó (cita creada, cobro, sesión completada…).
                pe.saniape.app.tutoriales.TareasEscritura.emitir(endpoint, cuerpo, respuesta)
                return ResultadoEscritura(registrada = true, cuerpo = respuesta)
            }
            // RECHAZO del servidor (400/403/409 de negocio…): NO encolar. Reintentarlo
            // daría el mismo error una y otra vez, y decirle al usuario "se registrará
            // al volver la señal" sería mentirle: el problema no es la conexión.
            ResultadoEnvio.RECHAZADO -> return ResultadoEscritura(
                registrada = false, rechazo = rechazo ?: RechazoServidor("El servidor rechazó la operación"),
            )
            // Fallo de red (o excepción) → sigue abajo y se encola.
            ResultadoEnvio.SIN_RED -> Unit
        }
    }

    return runCatching {
        ColaRepo.encolar(
            tipo = tipo, endpoint = endpoint, payload = cuerpo,
            idTemporal = idTemporal, dependeDe = dependeDe, idemKey = idemKey,
        )
        Toaster.exito("Guardado — se registrará al volver la señal")
        // Encolada con éxito local también cuenta como hecha (contrato §1.4).
        pe.saniape.app.tutoriales.TareasEscritura.emitir(endpoint, cuerpo, null)
        Sincronizador.disparar()
        ResultadoEscritura(registrada = true, encolada = true)
    }.getOrDefault(ResultadoEscritura(registrada = false))
}
