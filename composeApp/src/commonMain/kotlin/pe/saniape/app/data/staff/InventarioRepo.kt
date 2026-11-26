package pe.saniape.app.data.staff

import io.github.jan.supabase.auth.auth
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.contentType
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put
import pe.saniape.app.data.Supabase
import pe.saniape.app.data.crearHttpClient

/** Un producto de consumo (gel, guantes…) y la unidad en que se cuenta. */
data class ProductoConsumoApp(val id: String, val nombre: String, val unidad: String)

private fun JsonObject.texto(k: String): String? = (this[k] as? JsonPrimitive)?.contentOrNull?.takeIf { it != "null" }

/** Los productos de consumo activos de la respuesta de /api/staff/inventario. Puro. */
fun parsearProductosConsumo(o: JsonObject): List<ProductoConsumoApp> =
    (o["productos"] as? JsonArray).orEmpty().mapNotNull {
        val p = it as? JsonObject ?: return@mapNotNull null
        if ((p.texto("estado") ?: "Activo") != "Activo") return@mapNotNull null
        ProductoConsumoApp(p.texto("id") ?: return@mapNotNull null, p.texto("nombre") ?: return@mapNotNull null, p.texto("unidad_contenido") ?: "unidad")
    }

/**
 * Lo escrito en el cierre → lo que vale la pena mandar: producto con una
 * cantidad mayor que cero (acepta coma decimal). Puro.
 */
fun itemsDeConsumo(cantidades: Map<String, String>): List<Pair<String, Double>> =
    cantidades.mapNotNull { (id, txt) ->
        val n = txt.trim().replace(',', '.').toDoubleOrNull() ?: return@mapNotNull null
        if (id.isBlank() || n <= 0 || n.isNaN() || n.isInfinite()) null else id to n
    }

/** El cuerpo de «lo usado en una sesión»: el servidor deduce profesional, fecha y consultorio. Puro. */
fun cuerpoConsumoSesion(citaId: String?, sesionId: String?, items: List<Pair<String, Double>>): JsonObject = buildJsonObject {
    put("consumos", JsonArray(items.map { (id, n) -> buildJsonObject { put("productoId", id); put("cantidad", n) } }))
    if (!citaId.isNullOrBlank()) put("citaId", citaId)
    if (!sesionId.isNullOrBlank()) put("sesionId", sesionId)
}

/**
 * 🧰 Inventario clínico en la app (26/11/2026): solo anotar lo usado al cerrar
 * una sesión. Lo descuenta el servidor (/api/staff/inventario) del consultorio
 * donde atiende ese profesional ese día; el resto del módulo vive en la web.
 */
object InventarioRepo {
    private val json = Json { ignoreUnknownKeys = true }
    private val http = crearHttpClient()

    private suspend fun token(): String? = Supabase.client.auth.currentSessionOrNull()?.accessToken

    /** Vacío si no hay productos, no hay red o el agregado no está activo. */
    suspend fun productosConsumo(): List<ProductoConsumoApp> {
        val tk = token() ?: return emptyList()
        return try {
            val resp = http.get("${Supabase.SITE_URL}/api/staff/inventario") { header("Authorization", "Bearer $tk") }
            if (resp.status.value !in 200..299) return emptyList()
            parsearProductosConsumo(json.parseToJsonElement(resp.bodyAsText()).jsonObject)
        } catch (e: kotlin.coroutines.cancellation.CancellationException) {
            throw e
        } catch (_: Exception) {
            emptyList()
        }
    }

    /** null = registrado; si no, el motivo para decírselo a quien cerró la sesión. */
    suspend fun registrarConsumo(citaId: String?, sesionId: String?, items: List<Pair<String, Double>>): String? {
        if (items.isEmpty()) return null
        val tk = token() ?: return "tu sesión expiró"
        return try {
            val resp = http.post("${Supabase.SITE_URL}/api/staff/inventario") {
                header("Authorization", "Bearer $tk"); contentType(ContentType.Application.Json)
                setBody(cuerpoConsumoSesion(citaId, sesionId, items).toString())
            }
            if (resp.status.value in 200..299) return null
            val obj = runCatching { json.parseToJsonElement(resp.bodyAsText()).jsonObject }.getOrNull()
            val primero = ((obj?.get("fallidos") as? JsonArray)?.firstOrNull() as? JsonObject)?.texto("error")
            primero ?: obj?.texto("error") ?: "no se pudo registrar"
        } catch (e: kotlin.coroutines.cancellation.CancellationException) {
            throw e
        } catch (_: Exception) {
            "sin conexión"
        }
    }
}
