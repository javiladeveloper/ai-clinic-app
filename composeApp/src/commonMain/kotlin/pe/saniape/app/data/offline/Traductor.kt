package pe.saniape.app.data.offline

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlin.random.Random

/**
 * IDs temporales de la cola offline.
 *
 * Cuando se crea algo sin señal (un paciente, una cita…), la UI necesita un id YA
 * para mostrarlo; se le da uno "tmp-…" y, al sincronizar, se reemplaza por el real
 * que asignó el servidor. El prefijo hace imposible confundirlo con un UUID real,
 * y el servidor NUNCA debe recibir uno: si falta el mapeo, la operación espera.
 */

private const val PREFIJO = "tmp-"

fun esTemporal(valor: String): Boolean = valor.startsWith(PREFIJO)

/** Genera un id temporal único para usar en la UI mientras no hay id real. */
fun nuevoIdTemporal(): String {
    val hex = (1..16).map { "0123456789abcdef"[Random.nextInt(16)] }.joinToString("")
    return "$PREFIJO$hex"
}

/**
 * Reemplaza en [payload] todo valor "tmp-…" por su id real según [mapa] (recorre
 * también objetos y arrays anidados).
 *
 * Devuelve null si algún temporal NO tiene mapeo todavía: significa que la
 * operación depende de otra que aún no sincronizó, así que debe esperar en vez de
 * enviarse huérfana.
 */
fun traducir(payload: JsonObject, mapa: Map<String, String>): JsonObject? {
    var faltaMapeo = false

    fun traducirElemento(e: JsonElement): JsonElement = when (e) {
        is JsonPrimitive -> {
            if (e.isString && esTemporal(e.content)) {
                val real = mapa[e.content]
                if (real == null) {
                    faltaMapeo = true
                    e
                } else {
                    JsonPrimitive(real)
                }
            } else {
                e
            }
        }
        is JsonObject -> JsonObject(e.mapValues { traducirElemento(it.value) })
        is JsonArray -> JsonArray(e.map { traducirElemento(it) })
    }

    val resultado = JsonObject(payload.mapValues { traducirElemento(it.value) })
    return if (faltaMapeo) null else resultado
}
