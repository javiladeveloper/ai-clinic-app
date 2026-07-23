package pe.saniape.app.data.staff

import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.Columns
import io.github.jan.supabase.postgrest.query.Order
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import pe.saniape.app.data.Supabase

/**
 * Diagnósticos/motivos frecuentes de la clínica (tabla diagnosticos_frecuentes), para
 * el autocomplete del campo "Diagnóstico / Motivo" al completar una evaluación. Espeja
 * lib/diagnosticos.ts de la web: aprende de lo que se escribe. RLS filtra por clínica.
 */
object DiagnosticosRepo {

    /** Trocea un diagnóstico libre en términos (por coma / ; / salto), min 3 chars. */
    private fun trocear(texto: String): List<String> =
        texto.split(',', ';', '\n').map { it.trim() }.filter { it.length >= 3 }

    private fun norm(s: String) = s.lowercase().trim()

    /** Diagnósticos más usados (orden por usos desc). Para el typeahead. */
    suspend fun sugerencias(): List<String> = try {
        Supabase.client.postgrest["diagnosticos_frecuentes"]
            .select(Columns.list("nombre, usos")) {
                order("usos", Order.DESCENDING)
                limit(200)
            }
            .decodeList<JsonObject>()
            .mapNotNull { (it["nombre"] as? JsonPrimitive)?.content?.takeIf { n -> n != "null" } }
    } catch (e: Exception) {
        emptyList()
    }

    /**
     * Aprende los términos de un diagnóstico: suma uso a los existentes y crea los nuevos.
     * Llamar al completar la evaluación (fire-and-forget). No bloquea el completar.
     */
    suspend fun registrar(texto: String, especialidadId: String? = null) {
        val nombres = trocear(texto)
        if (nombres.isEmpty()) return
        try {
            val existentes = Supabase.client.postgrest["diagnosticos_frecuentes"]
                .select(Columns.list("id, nombre, usos"))
                .decodeList<JsonObject>()
            val porNombre = existentes.associateBy { norm((it["nombre"] as? JsonPrimitive)?.content ?: "") }
            for (nombre in nombres) {
                val previa = porNombre[norm(nombre)]
                if (previa != null) {
                    val id = (previa["id"] as? JsonPrimitive)?.content ?: continue
                    val usos = (previa["usos"] as? JsonPrimitive)?.content?.toIntOrNull() ?: 1
                    Supabase.client.postgrest["diagnosticos_frecuentes"]
                        .update(buildJsonObject { put("usos", usos + 1) }) { filter { eq("id", id) } }
                } else {
                    Supabase.client.postgrest["diagnosticos_frecuentes"]
                        .insert(buildJsonObject {
                            put("nombre", nombre)
                            if (especialidadId != null) put("especialidad_id", especialidadId)
                        })
                }
            }
        } catch (e: Exception) {
            // silencioso: aprender diagnósticos no debe bloquear el completar la evaluación
        }
    }
}
