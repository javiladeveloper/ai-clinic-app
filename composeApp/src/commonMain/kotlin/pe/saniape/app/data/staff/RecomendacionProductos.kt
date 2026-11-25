package pe.saniape.app.data.staff

import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.Columns
import io.github.jan.supabase.postgrest.query.Order
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import pe.saniape.app.data.Supabase
import pe.saniape.app.data.crearHttpClient
import kotlin.math.roundToInt

/*
 * 🛍️ RECOMENDACIÓN DE PRODUCTOS CON PROFORMA (24/11/2026). Gemelo de
 * lib/recomendacion-productos.ts (web): la clínica recomienda productos (audífonos,
 * cremas…), el paciente se lleva la proforma (impresa o por WhatsApp) y compra todo,
 * una parte o nada. Crear y enviar van por /api/staff/recomendacion; la venta se hace
 * en la web (la app no tiene punto de venta).
 */

data class ItemRecomendado(
    val tipo: String,
    val id: String,
    val nombre: String,
    val cantidad: Int,
    val precioUnit: Double,
    val indicacion: String?,
    val comprado: Int,
)

data class Recomendacion(
    val id: String,
    val items: List<ItemRecomendado>,
    val nota: String?,
    val estado: String,
    val creada: String,
)

data class ProductoVenta(val id: String, val nombre: String, val categoria: String?, val precio: Double, val stock: Int?)

/** Lo que se va a recomendar (en el formulario). */
data class LineaRecomendacion(val producto: ProductoVenta, val cantidad: String = "1", val precio: String = "", val indicacion: String = "")

private fun r2(x: Double) = (x * 100).roundToInt() / 100.0

/** Lo guardado (jsonb) → la lista limpia (gemelo de itemsDeRecomendacion). */
fun itemsDeRecomendacion(crudo: JsonElement?): List<ItemRecomendado> =
    (crudo as? JsonArray).orEmpty().mapNotNull { e ->
        val x = e as? JsonObject ?: return@mapNotNull null
        fun s(k: String) = (x[k] as? JsonPrimitive)?.content?.takeIf { it != "null" }
        val tipo = s("tipo")?.takeIf { it == "producto" || it == "kit" } ?: return@mapNotNull null
        val id = s("id")?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
        val cantidad = s("cantidad")?.toDoubleOrNull()?.toInt()?.takeIf { it > 0 } ?: return@mapNotNull null
        ItemRecomendado(
            tipo = tipo, id = id, nombre = s("nombre")?.trim()?.ifEmpty { null } ?: "Producto", cantidad = cantidad,
            precioUnit = s("precio_unit")?.toDoubleOrNull()?.takeIf { it >= 0 }?.let(::r2) ?: 0.0,
            indicacion = s("indicacion")?.trim()?.takeIf { it.isNotEmpty() },
            comprado = (s("comprado")?.toDoubleOrNull()?.toInt() ?: 0).coerceIn(0, cantidad),
        )
    }

fun totalRecomendacion(items: List<ItemRecomendado>): Double = r2(items.sumOf { it.cantidad * it.precioUnit })
fun totalComprado(items: List<ItemRecomendado>): Double = r2(items.sumOf { it.comprado * it.precioUnit })

/** Total de lo que se está armando (cantidad × precio; sin precio escrito, el de lista). */
fun totalLineas(lineas: List<LineaRecomendacion>): Double = r2(lineas.sumOf { l ->
    (l.cantidad.toIntOrNull() ?: 0) * (l.precio.replace(',', '.').toDoubleOrNull() ?: l.producto.precio)
})

/** El texto de la línea de un ítem: «2 × Audífono RIC · compró 1». */
fun textoItem(i: ItemRecomendado): String = buildString {
    append("${i.cantidad} × ${i.nombre}")
    when {
        i.comprado >= i.cantidad -> append(" · ✓ comprado")
        i.comprado > 0 -> append(" · ✓ compró ${i.comprado}")
    }
}

object RecomendacionesRepo {
    private val json = Json { ignoreUnknownKeys = true }
    private val http = crearHttpClient()
    private val BASE get() = "${Supabase.SITE_URL}/api/staff/recomendacion"

    /** Las proformas del paciente (más reciente primero). null = error. */
    suspend fun listar(pacienteId: String): List<Recomendacion>? = try {
        Supabase.client.postgrest["recomendaciones_productos"].select(Columns.list("id, items, nota, estado, created_at")) {
            filter { eq("paciente_id", pacienteId) }
            order("created_at", Order.DESCENDING)
            limit(50)
        }.decodeList<JsonObject>().map { o ->
            fun s(k: String) = (o[k] as? JsonPrimitive)?.content?.takeIf { it != "null" }
            Recomendacion(s("id").orEmpty(), itemsDeRecomendacion(o["items"]), s("nota"), s("estado") ?: "Pendiente", s("created_at").orEmpty())
        }
    } catch (e: CancellationException) { throw e } catch (_: Exception) { null }

    /** Los productos activos de la clínica, para elegir. */
    suspend fun productos(): List<ProductoVenta> = try {
        Supabase.client.postgrest["productos"].select(Columns.list("id, nombre, categoria, precio, stock")) {
            filter { eq("estado", "Activo") }
            order("nombre", Order.ASCENDING)
            limit(500)
        }.decodeList<JsonObject>().mapNotNull { o ->
            fun s(k: String) = (o[k] as? JsonPrimitive)?.content?.takeIf { it != "null" }
            ProductoVenta(s("id") ?: return@mapNotNull null, s("nombre").orEmpty(), s("categoria"),
                s("precio")?.toDoubleOrNull() ?: 0.0, s("stock")?.toDoubleOrNull()?.toInt())
        }
    } catch (e: CancellationException) { throw e } catch (_: Exception) { emptyList() }

    private suspend fun post(url: String, cuerpo: JsonObject): Pair<JsonObject?, String?> = try {
        val tk = Supabase.client.auth.currentSessionOrNull()?.accessToken ?: return null to "Tu sesión venció. Vuelve a entrar."
        val r = http.post(url) {
            header("Authorization", "Bearer $tk")
            contentType(ContentType.Application.Json)
            setBody(cuerpo.toString())
        }
        val o = runCatching { json.parseToJsonElement(r.bodyAsText()) as? JsonObject }.getOrNull()
        if (r.status.isSuccess()) o to null
        else null to ((o?.get("error") as? JsonPrimitive)?.content ?: "No se pudo guardar. Intenta de nuevo.")
    } catch (e: CancellationException) { throw e } catch (_: Exception) { null to "Sin conexión. Intenta de nuevo." }

    /** Guarda la proforma. Devuelve el error o null si salió. */
    suspend fun crear(pacienteId: String, citaId: String?, lineas: List<LineaRecomendacion>, nota: String): String? {
        val cuerpo = buildJsonObject {
            put("pacienteId", pacienteId)
            citaId?.let { put("citaId", it) }
            put("items", buildJsonArray {
                lineas.forEach { l ->
                    add(buildJsonObject {
                        put("tipo", "producto"); put("id", l.producto.id)
                        put("cantidad", l.cantidad.toIntOrNull() ?: 1)
                        l.precio.replace(',', '.').toDoubleOrNull()?.let { put("precio", it) }
                        if (l.indicacion.isNotBlank()) put("indicacion", l.indicacion.trim())
                    })
                }
            })
            if (nota.isNotBlank()) put("nota", nota.trim())
        }
        return post(BASE, cuerpo).second
    }

    /** El enlace público de la proforma y el WhatsApp ya armado: (url, whatsappUrl) o el error. */
    suspend fun enlace(id: String): Pair<Pair<String, String?>?, String?> {
        val (o, err) = post("$BASE/$id/enlace", buildJsonObject {})
        if (err != null || o == null) return null to (err ?: "No se pudo preparar el enlace")
        fun s(k: String) = (o[k] as? JsonPrimitive)?.content?.takeIf { it != "null" && it.isNotBlank() }
        val url = s("url") ?: return null to "No se pudo preparar el enlace"
        return (url to s("whatsappUrl")) to null
    }

    /** «No compró» / «Reabrir» (la RLS acota a la clínica). */
    suspend fun marcar(id: String, estado: String): Boolean = try {
        Supabase.client.postgrest["recomendaciones_productos"].update(buildJsonObject { put("estado", estado) }) {
            filter { eq("id", id) }
        }
        true
    } catch (e: CancellationException) { throw e } catch (_: Exception) { false }
}
