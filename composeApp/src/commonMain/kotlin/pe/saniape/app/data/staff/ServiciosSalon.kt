package pe.saniape.app.data.staff

import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.Columns
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import pe.saniape.app.data.Supabase

/**
 * 💅 SALÓN (atención directa, 2026-11-01): la «Atención» ES el servicio. En la
 * Nueva cita se elige uno de la especialidad y el precio sale de él. Gemelo de
 * `modalidades` con `atencionDirecta` en CitaForm (web).
 */
data class ServicioSalon(val id: String, val nombre: String, val categoria: String?, val precio: Double, val especialidadId: String?)

/** Agrupados por categoría (Manicura, Pedicura…), en el orden en que aparecen. */
fun ordenarServiciosSalon(lista: List<ServicioSalon>): List<ServicioSalon> {
    val orden = lista.map { it.categoria ?: "Otros" }.distinct()
    return lista.sortedWith(compareBy({ orden.indexOf(it.categoria ?: "Otros") }, { it.nombre.lowercase() }))
}

/** 35.0 → "35"; 35.5 → "35.50". */
fun precioSalonTexto(n: Double): String =
    if (n % 1.0 == 0.0) n.toLong().toString() else ((kotlin.math.round(n * 100)) / 100).toString().let { t ->
        if (t.substringAfter('.', "").length == 1) "${t}0" else t
    }

object ServiciosSalonRepo {
    /** Activos de esa especialidad (y los que no cuelgan de ninguna). Vacío si falla. */
    suspend fun deEspecialidad(especialidadId: String?): List<ServicioSalon> = try {
        val filas = Supabase.client.postgrest["procedimientos"]
            .select(Columns.raw("id, nombre, categoria, precio, especialidad_id")) { filter { eq("estado", "Activo") } }
            .decodeList<JsonObject>()
        ordenarServiciosSalon(filas.mapNotNull { o ->
            fun s(k: String) = (o[k] as? JsonPrimitive)?.takeIf { it.isString || it.content != "null" }?.content
            val esp = s("especialidad_id")
            if (especialidadId != null && esp != null && esp != especialidadId) return@mapNotNull null
            ServicioSalon(s("id") ?: return@mapNotNull null, s("nombre") ?: return@mapNotNull null,
                s("categoria")?.takeIf { it.isNotBlank() }, s("precio")?.toDoubleOrNull() ?: 0.0, esp)
        })
    } catch (e: kotlin.coroutines.cancellation.CancellationException) {
        throw e
    } catch (_: Exception) {
        emptyList()
    }
}
