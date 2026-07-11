package pe.saniape.app.data

import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject

/** Resultado del chequeo de versión: si hay una más nueva en la tienda + la URL. */
data class ChequeoVersion(
    val hayActualizacion: Boolean,
    val urlTienda: String,
)

/**
 * Consulta la última versión disponible (endpoint /api/app/version de la web) y la
 * compara con la de ESTA build. Si hay una más nueva, la UI muestra el aviso "Actualizar".
 * Endpoint público (sin token). Cualquier fallo → no molesta (devuelve null).
 */
object VersionRepo {
    private val json = Json { ignoreUnknownKeys = true }
    private val http = crearHttpClient()

    private fun JsonObject.intp(k: String): Int =
        (this[k] as? JsonPrimitive)?.content?.toIntOrNull() ?: 0
    private fun JsonObject.str(k: String): String? =
        (this[k] as? JsonPrimitive)?.content?.takeIf { it != "null" }

    suspend fun chequear(): ChequeoVersion? {
        val resp = runCatching {
            http.get("${Supabase.SITE_URL}/api/app/version")
        }.getOrNull() ?: return null
        if (resp.status != HttpStatusCode.OK) return null

        val raiz = runCatching { json.parseToJsonElement(resp.bodyAsText()).jsonObject }.getOrNull() ?: return null
        // Elige el bloque de la plataforma actual (android / ios).
        val bloque = (raiz[VersionApp.plataforma] as? JsonObject) ?: return null

        val latest = bloque.intp("latest")
        val url = bloque.str("url") ?: return null
        return ChequeoVersion(
            hayActualizacion = latest > VersionApp.codigo,
            urlTienda = url,
        )
    }
}
