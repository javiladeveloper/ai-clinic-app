package pe.saniape.app.data.staff

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/**
 * UN tratamiento con varios servicios (web 10/11/2026, tabla `tratamiento_servicios`).
 * Gemelo de lib/tratamiento-servicios.ts (`nombreTratamiento`): con 2+ líneas el
 * tratamiento se llama «OCT + Campimetría»; si no, el nombre de su servicio de siempre.
 * Fase 1 de la app: solo leer (crear/editar con varios servicios sigue en la web).
 */
object TratamientoServicios {
    /** El select anidado de las líneas (va dentro de un `tratamientos(...)`). */
    const val SELECT_LINEAS = "servicios:tratamiento_servicios(orden, precio_acordado, procedimiento:procedimientos(nombre))"

    fun nombre(tratamiento: JsonObject?, porDefecto: String?): String? {
        val lineas = (tratamiento?.get("servicios") as? JsonArray)?.mapNotNull { it as? JsonObject } ?: return porDefecto
        if (lineas.size < 2) return porDefecto
        val nombres = lineas
            .sortedBy { (it["orden"] as? JsonPrimitive)?.content?.toIntOrNull() ?: 0 }
            .mapNotNull { l ->
                ((l["procedimiento"] as? JsonObject)?.get("nombre") as? JsonPrimitive)?.content
                    ?.trim()?.takeIf { it.isNotEmpty() && it != "null" }
            }
        return if (nombres.isEmpty()) porDefecto else nombres.joinToString(" + ")
    }
}
