package pe.saniape.app.data.staff

import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.Columns
import io.github.jan.supabase.postgrest.query.Order
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import pe.saniape.app.data.Supabase

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
}
