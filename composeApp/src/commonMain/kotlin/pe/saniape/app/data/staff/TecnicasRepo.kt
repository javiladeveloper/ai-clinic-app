package pe.saniape.app.data.staff

import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.Columns
import io.github.jan.supabase.postgrest.query.Order
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import pe.saniape.app.data.Supabase

private const val SEPARADOR = " + "

/**
 * Técnicas/procedimientos de la clínica (tabla tecnicas_sesion), para el
 * autocomplete al completar una sesión. RLS de staff filtra por clínica.
 */
object TecnicasRepo {
    /** Nombres de técnicas más usadas (orden por usos desc). */
    suspend fun sugerencias(): List<String> {
        val filas = Supabase.client.postgrest["tecnicas_sesion"]
            .select(Columns.list("nombre, usos")) {
                order("usos", Order.DESCENDING)
                limit(200)
            }
            .decodeList<JsonObject>()
        return filas.mapNotNull { (it["nombre"] as? JsonPrimitive)?.content?.takeIf { n -> n != "null" } }
    }

    /**
     * Aprende las técnicas usadas para que se sugieran en próximas sesiones de la
     * clínica: incrementa el contador de las existentes y crea las nuevas. Espeja
     * registrarTecnicas() de la web. Llamar al completar la sesión (fire-and-forget).
     * [texto] viene unido con " + " (mismo formato que sesiones.notas).
     */
    suspend fun registrar(texto: String) {
        val nombres = texto.split(SEPARADOR).map { it.trim() }.filter { it.isNotEmpty() }
        if (nombres.isEmpty()) return
        fun norm(s: String) = s.lowercase().trim()
        try {
            val existentes = Supabase.client.postgrest["tecnicas_sesion"]
                .select(Columns.list("id, nombre, usos"))
                .decodeList<JsonObject>()
            val porNombre = existentes.associateBy { norm((it["nombre"] as? JsonPrimitive)?.content ?: "") }
            for (nombre in nombres) {
                val previa = porNombre[norm(nombre)]
                if (previa != null) {
                    val id = (previa["id"] as? JsonPrimitive)?.content ?: continue
                    val usos = (previa["usos"] as? JsonPrimitive)?.content?.toIntOrNull() ?: 1
                    Supabase.client.postgrest["tecnicas_sesion"]
                        .update(buildJsonObject { put("usos", usos + 1) }) { filter { eq("id", id) } }
                } else {
                    Supabase.client.postgrest["tecnicas_sesion"]
                        .insert(buildJsonObject { put("nombre", nombre) })
                }
            }
        } catch (e: Exception) {
            // silencioso: aprender técnicas no debe bloquear el completar la sesión
        }
    }
}
