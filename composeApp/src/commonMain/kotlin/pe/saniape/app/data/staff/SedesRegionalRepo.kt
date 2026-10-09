package pe.saniape.app.data.staff

import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.Columns
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import pe.saniape.app.data.Supabase

/**
 * País, moneda y zona horaria de UNA sede (Ajustes → Sedes), gemelo de
 * RegionalSede.tsx + EditorSede.tsx de la web: se lee y se guarda directo en
 * `sedes` con la RLS del staff (la web hace lo mismo). NULL = lo de la clínica.
 * La base valida la zona (trigger); acá se valida el formato como la web.
 */
object SedesRegionalRepo {

    private fun JsonObject.txt(k: String): String =
        (this[k] as? JsonPrimitive)?.content?.takeIf { it != "null" }.orEmpty()

    /** Lo guardado ("" = el/la de la clínica). null = no se pudo leer (sin red o base sin las columnas). */
    suspend fun leer(sedeId: String): RegionalSedeForm? = try {
        Supabase.client.postgrest["sedes"]
            .select(Columns.list("pais", "moneda", "zona_horaria")) { filter { eq("id", sedeId) } }
            .decodeList<JsonObject>().firstOrNull()
            ?.let { RegionalSedeForm(it.txt("pais"), it.txt("moneda"), it.txt("zona_horaria")) }
    } catch (e: CancellationException) { throw e } catch (_: Exception) { null }

    /**
     * Guarda SOLO lo que cambió (como la web: una base sin las columnas no se rompe
     * al guardar una sede a la que nadie le tocó el país). null = ok; si no, el mensaje.
     */
    suspend fun guardar(sedeId: String, antes: RegionalSedeForm, ahora: RegionalSedeForm): String? {
        errorRegionalSede(ahora)?.let { return it }
        fun v(s: String, mayus: Boolean = true) = s.trim().let { if (mayus) it.uppercase() else it }
        val cambios = buildJsonObject {
            if (v(ahora.pais) != v(antes.pais)) put("pais", v(ahora.pais).ifEmpty { null }?.let { JsonPrimitive(it) } ?: JsonNull)
            if (v(ahora.moneda) != v(antes.moneda)) put("moneda", v(ahora.moneda).ifEmpty { null }?.let { JsonPrimitive(it) } ?: JsonNull)
            if (v(ahora.zona, false) != v(antes.zona, false)) put("zona_horaria", v(ahora.zona, false).ifEmpty { null }?.let { JsonPrimitive(it) } ?: JsonNull)
        }
        if (cambios.isEmpty()) return null
        return try {
            Supabase.client.postgrest["sedes"].update(cambios) { filter { eq("id", sedeId) } }
            null
        } catch (e: CancellationException) { throw e } catch (e: Exception) {
            val m = e.message.orEmpty()
            when {
                m.contains("zona", ignoreCase = true) -> "Zona horaria no válida"
                m.contains("42501") || m.contains("permission", ignoreCase = true) -> "No tienes permiso para cambiar esta sede"
                else -> "No se pudo guardar el país, la moneda o la zona de la sede"
            }
        }
    }
}
