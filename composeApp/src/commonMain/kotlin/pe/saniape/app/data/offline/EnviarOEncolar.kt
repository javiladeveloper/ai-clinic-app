package pe.saniape.app.data.offline

import kotlinx.serialization.json.JsonObject
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
): Boolean = pe.saniape.app.ui.conIndicador {
    enviarOEncolarInterno(tipo, endpoint, cuerpo, idTemporal, dependeDe)
}

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
): Boolean {
    val idemKey = nuevaIdemKey()

    // Si el payload trae ids temporales, no tiene sentido intentarlo inline:
    // el servidor no los conoce. Va directo a la cola, que los traducirá.
    val tieneTemporales = cuerpo.toString().contains("tmp-")
    if (!tieneTemporales) {
        when (runCatching { Sincronizador.enviarAhora(endpoint, cuerpo, idemKey) }.getOrNull()) {
            ResultadoEnvio.OK -> return true
            // RECHAZO del servidor (400/403…): NO encolar. Reintentarlo daría el mismo
            // error una y otra vez, y decirle al usuario "se registrará al volver la
            // señal" sería mentirle: el problema no es la conexión.
            ResultadoEnvio.RECHAZADO -> return false
            // Fallo de red (o excepción) → sigue abajo y se encola.
            else -> Unit
        }
    }

    return runCatching {
        ColaRepo.encolar(
            tipo = tipo, endpoint = endpoint, payload = cuerpo,
            idTemporal = idTemporal, dependeDe = dependeDe, idemKey = idemKey,
        )
        Toaster.exito("Guardado — se registrará al volver la señal")
        Sincronizador.disparar()
        true
    }.getOrDefault(false)
}
