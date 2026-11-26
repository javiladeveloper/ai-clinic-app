package pe.saniape.app.data.staff

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlin.math.roundToLong

/**
 * VUELTO CON OTRO MEDIO (26/11/2026; gemelo de lib/caja-vuelto.ts de la web):
 * «cuesta 50, me da 100 y le doy el vuelto por Yape». No es ingreso ni gasto: el
 * servidor lo ata al cobro y ajusta el efectivo de la caja. La app solo decide
 * si vale la pena mandarlo; el servidor vuelve a validar todo.
 */
data class VueltoCobro(val recibido: Double, val metodo: String)

private fun centimos(n: Double): Long = (n * 100).roundToLong()
private fun esEfectivo(m: String?): Boolean = m?.trim()?.lowercase() == "efectivo"

/** Lo entregado menos lo cobrado (0 si no alcanza). En céntimos: 0.1 + 0.2 no es 0.3. */
fun calcularVuelto(monto: Double, recibido: Double): Double {
    if (recibido.isNaN() || monto.isNaN()) return 0.0
    val v = centimos(recibido) - centimos(monto)
    return if (v > 0) v / 100.0 else 0.0
}

/**
 * El vuelto que se manda con el cobro: solo si cambia el cajón (hay vuelto y
 * UNO de los dos medios es efectivo). Efectivo con vuelto en efectivo es el de
 * siempre, y Yape ↔ Plin no pasa por el cajón: null.
 */
fun vueltoParaEnviar(monto: Double, metodoCobro: String, recibidoTxt: String, metodoVuelto: String): VueltoCobro? {
    val recibido = recibidoTxt.trim().replace(',', '.').toDoubleOrNull() ?: return null
    if (calcularVuelto(monto, recibido) <= 0 || metodoVuelto.isBlank()) return null
    if (esEfectivo(metodoCobro) == esEfectivo(metodoVuelto)) return null
    return VueltoCobro(recibido, metodoVuelto.trim())
}

/** El aviso del servidor cuando el cobro entró pero el vuelto no (o null). */
fun avisoVueltoDe(respuesta: JsonObject?): String? =
    (respuesta?.get("avisoVuelto") as? JsonPrimitive)?.contentOrNull?.takeIf { it.isNotBlank() && it != "null" }
