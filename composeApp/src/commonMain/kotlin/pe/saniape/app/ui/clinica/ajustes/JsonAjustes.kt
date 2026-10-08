package pe.saniape.app.ui.clinica.ajustes

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull

// Ayudantes de lectura del JSON de Ajustes (cortos a propósito: son muchos
// campos chicos y la web es la dueña de su forma).

internal fun JsonObject.s(k: String): String? = (this[k] as? JsonPrimitive)?.takeIf { it !is JsonNull }?.contentOrNull
internal fun JsonObject.t(k: String): String = s(k) ?: ""
internal fun JsonObject.b(k: String): Boolean = (this[k] as? JsonPrimitive)?.booleanOrNull == true
internal fun JsonObject.bn(k: String): Boolean? = (this[k] as? JsonPrimitive)?.booleanOrNull
internal fun JsonObject.n(k: String): Double? = (this[k] as? JsonPrimitive)?.doubleOrNull
internal fun JsonObject.i(k: String): Int? = (this[k] as? JsonPrimitive)?.intOrNull ?: n(k)?.toInt()
internal fun JsonObject.o(k: String): JsonObject? = this[k] as? JsonObject
internal fun JsonObject.a(k: String): List<JsonElement> = (this[k] as? JsonArray) ?: emptyList()
internal fun JsonObject.objetos(k: String): List<JsonObject> = a(k).mapNotNull { it as? JsonObject }
internal fun JsonObject.textos(k: String): List<String> = a(k).mapNotNull { (it as? JsonPrimitive)?.takeIf { p -> p !is JsonNull }?.contentOrNull }

/** Estado de Sani tras guardar algo que lee el bot (mismos textos que la web, lib/playbook-estado). */
internal fun mensajeGuardadoSani(botSync: String?): String = when (botSync) {
    "actualizado" -> "Guardado; Sani actualizado"
    "pendiente", null -> "Guardado; actualización de Sani pendiente"
    else -> "Guardado"
}
