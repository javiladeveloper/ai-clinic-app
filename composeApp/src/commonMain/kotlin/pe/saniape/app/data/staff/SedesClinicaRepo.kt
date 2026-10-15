package pe.saniape.app.data.staff

import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.Columns
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import pe.saniape.app.data.Supabase

/**
 * TODAS las sedes de la clínica (activas o no, permitidas o no) con su moneda,
 * zona y si es la principal: lo que la web tiene en `useSedes().data` para
 * resolver la moneda de una ficha o de un tratamiento vendido en una sede que
 * este usuario no tiene permitida (`ctx.sedes` trae solo las permitidas).
 *
 * Lectura directa a `sedes` por la RLS del staff (`sedes_select`: misma
 * clínica, sin filtro por sede), como Ajustes → Sedes. Se lee UNA vez por
 * sesión y clínica; si falla (sin red, base sin las columnas de multipaís)
 * devuelve null y quien llama usa `ctx.sedes` (se reintenta la próxima vez).
 */
object SedesClinicaRepo {

    private var cache: Pair<String, List<SedeRef>>? = null

    /** Lo ya leído para esta clínica, o null. */
    fun enCache(clinicaId: String?): List<SedeRef>? = cache?.takeIf { it.first == clinicaId }?.second

    suspend fun todas(clinicaId: String?): List<SedeRef>? {
        if (clinicaId.isNullOrBlank()) return null
        enCache(clinicaId)?.let { return it }
        val filas = try {
            withTimeoutOrNull(12_000) {
                Supabase.client.postgrest["sedes"]
                    .select(Columns.list("id", "nombre", "es_principal", "moneda", "zona_horaria", "pais"))
                    .decodeList<JsonObject>()
            }
        } catch (e: CancellationException) { throw e } catch (_: Exception) { null } ?: return null
        return sedesDeFilas(filas).also { cache = clinicaId to it }
    }

    /** Al cerrar sesión o cambiar de clínica. */
    fun olvidar() { cache = null }
}

/** Filas de `sedes` → [SedeRef] (tolerante: sin id se descarta; el resto puede faltar). */
fun sedesDeFilas(filas: List<JsonObject>): List<SedeRef> {
    fun JsonObject.txt(k: String): String? =
        (this[k] as? JsonPrimitive)?.takeIf { it !is JsonNull }?.content?.trim()?.takeIf { it.isNotEmpty() && it != "null" }
    return filas.mapNotNull { o ->
        SedeRef(
            id = o.txt("id") ?: return@mapNotNull null,
            nombre = o.txt("nombre") ?: "Sede",
            esPrincipal = o.txt("es_principal") == "true",
            moneda = o.txt("moneda"),
            zona = o.txt("zona_horaria"),
            pais = paisValidoONull(o.txt("pais")),
        )
    }.distinctBy { it.id }
}
