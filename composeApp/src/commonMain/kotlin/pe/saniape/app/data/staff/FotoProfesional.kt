package pe.saniape.app.data.staff

import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.Columns
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import kotlinx.datetime.Clock
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.put
import pe.saniape.app.data.Supabase
import pe.saniape.app.data.crearHttpClient

// ─────────────────────────────────────────────────────────────────────────────
// FOTO DEL PROFESIONAL (gemelo de AvatarProfesional.tsx + la foto de
// TerapeutaForm.tsx de la web): `terapeutas.foto_url`, bucket PÚBLICO
// `clinica-assets`, ruta `profesional-<ms>.<ext>`, comprimida a 1200 px antes de
// subir. Sin foto (o si la imagen falla) → las iniciales de siempre.
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Iniciales del profesional: sin el "Lic." del inicio, las dos primeras
 * palabras (gemelo de `inicialesProfesional`). Tolera espacios dobles.
 */
fun inicialesProfesional(nombre: String?): String =
    nombre.orEmpty().replace(Regex("Lic\\.\\s*", RegexOption.IGNORE_CASE), "")
        .split(Regex("\\s+")).filter { it.isNotEmpty() }.take(2)
        .joinToString("") { it.take(1) }.uppercase()

/** Ruta en el bucket como la web: `profesional-<ms>.<ext>` (ext saneada; jpg si no hay). */
fun rutaFotoProfesional(nombreArchivo: String, ahoraMs: Long): String {
    val ext = nombreArchivo.substringAfterLast('.', "").lowercase().filter { it in 'a'..'z' || it in '0'..'9' }.ifEmpty { "jpg" }
    return "profesional-$ahoraMs.$ext"
}

object FotoProfesionalRepo {
    private const val BUCKET = "clinica-assets"
    private val http = crearHttpClient()

    /** id → foto_url de los profesionales de la clínica (RLS). Cache de la sesión; [forzar] relee. */
    private var cache: Map<String, String>? = null

    suspend fun mapa(forzar: Boolean = false): Map<String, String> {
        if (!forzar) cache?.let { return it }
        return runCatching {
            Supabase.client.postgrest["terapeutas"]
                .select(Columns.list("id", "foto_url")) { filter { neq("foto_url", "") } }
                .decodeList<JsonObject>()
                .mapNotNull { o ->
                    val id = (o["id"] as? JsonPrimitive)?.contentOrNull ?: return@mapNotNull null
                    val url = (o["foto_url"] as? JsonPrimitive)?.contentOrNull?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
                    id to url
                }.toMap()
        }.onSuccess { cache = it }.getOrElse {
            if (it is kotlin.coroutines.cancellation.CancellationException) throw it
            cache ?: emptyMap()
        }
    }

    /** La foto de un profesional (o null). Lee del cache si ya se cargó. */
    suspend fun de(terapeutaId: String?, forzar: Boolean = false): String? =
        terapeutaId?.takeIf { it.isNotBlank() }?.let { mapa(forzar)[it] }

    /**
     * Sube la foto (ya comprimida) al bucket público con la sesión del usuario,
     * igual que el navegador (misma política del bucket). Devuelve la URL
     * pública, o null si no se pudo.
     */
    suspend fun subir(nombreArchivo: String, bytes: ByteArray, mime: String?): String? {
        val tk = Supabase.client.auth.currentSessionOrNull()?.accessToken ?: return null
        val ruta = rutaFotoProfesional(nombreArchivo, Clock.System.now().toEpochMilliseconds())
        return try {
            val resp = http.post("${Supabase.URL}/storage/v1/object/$BUCKET/$ruta") {
                header("Authorization", "Bearer $tk")
                header("apikey", Supabase.ANON_KEY)
                header("cache-control", "max-age=3600")
                header("x-upsert", "false")
                contentType(ContentType.parse(mime ?: "image/jpeg"))
                setBody(bytes)
            }
            if (resp.status.isSuccess()) "${Supabase.URL}/storage/v1/object/public/$BUCKET/$ruta" else null
        } catch (e: kotlin.coroutines.cancellation.CancellationException) {
            throw e
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Guarda (o quita, con null) la foto en `terapeutas.foto_url`, como el
     * guardado de TerapeutaForm (UPDATE directo; la RLS lo acota a la clínica).
     */
    suspend fun guardar(terapeutaId: String, url: String?): Boolean = runCatching {
        Supabase.client.postgrest["terapeutas"].update(buildJsonObject {
            if (url == null) put("foto_url", JsonNull) else put("foto_url", url)
        }) { filter { eq("id", terapeutaId) } }
        cache = cache?.let { m -> if (url == null) m - terapeutaId else m + (terapeutaId to url) }
        true
    }.getOrElse {
        if (it is kotlin.coroutines.cancellation.CancellationException) throw it
        false
    }
}
