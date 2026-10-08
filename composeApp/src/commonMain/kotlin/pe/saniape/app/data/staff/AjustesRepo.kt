package pe.saniape.app.data.staff

import io.github.jan.supabase.auth.auth
import io.ktor.client.request.forms.MultiPartFormDataContent
import io.ktor.client.request.forms.formData
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.request
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.Headers
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.contentType
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.put
import pe.saniape.app.data.Supabase
import pe.saniape.app.data.crearHttpClient
import pe.saniape.app.data.offline.RechazoServidor
import pe.saniape.app.data.offline.ResultadoEscritura

/**
 * ⚙️ Ajustes de la clínica (gemelo de la web /configuracion). TODO pasa por los
 * endpoints de la web (/api/staff/configuracion/… y los que la web ya usa:
 * duraciones, flujo del bot, Mercado Pago, redes, sedes, informe psicológico),
 * que aplican el permiso "ajustes", los candados de plan y de Admin y las
 * MISMAS reglas que la web (lib/configuracion-ajustes.ts, lib/flujo-especialidades.ts…).
 * La app no escribe nada de esto directo a la base.
 *
 * Las respuestas se leen como JSON (ver ui/clinica/ajustes/JsonAjustes.kt): son
 * formularios de muchos campos chicos y la web es la dueña de su forma.
 */
object AjustesRepo {

    private val json = Json { ignoreUnknownKeys = true; coerceInputValues = true; explicitNulls = false }
    private val http = crearHttpClient()

    const val BASE = "/api/staff/configuracion"

    private suspend fun token(): String? = Supabase.client.auth.currentSessionOrNull()?.accessToken

    private val sinSesion = ResultadoEscritura(
        registrada = false,
        rechazo = RechazoServidor("Tu sesión expiró. Vuelve a entrar.", "NO_AUTENTICADO", 401),
    )
    private val sinRed = ResultadoEscritura(
        registrada = false,
        rechazo = RechazoServidor("Sin conexión. Revisa tu internet.", "SIN_RED"),
    )

    /** Lo que respondió un GET: el JSON, el motivo del fallo y el status HTTP (0 = sin red/sesión). */
    data class Lectura(val json: JsonElement?, val error: String?, val status: Int)

    suspend fun leerConEstado(ruta: String): Lectura {
        val tk = token() ?: return Lectura(null, "Tu sesión expiró. Vuelve a entrar.", 401)
        return try {
            val resp = http.get("${Supabase.SITE_URL}$ruta") { header("Authorization", "Bearer $tk") }
            val texto = resp.bodyAsText()
            val el = runCatching { json.parseToJsonElement(texto) }.getOrNull()
            val st = resp.status.value
            if (st in 200..299 && el != null) Lectura(el, null, st)
            else Lectura(null, (el as? JsonObject)?.let { (it["error"] as? JsonPrimitive)?.contentOrNull }?.takeIf { it.isNotBlank() } ?: "No se pudo cargar (HTTP $st).", st)
        } catch (e: kotlin.coroutines.cancellation.CancellationException) {
            throw e
        } catch (e: Exception) {
            Lectura(null, "Sin conexión. Revisa tu internet.", 0)
        }
    }

    /** GET de una ruta de la web (relativa, "/api/…"). Devuelve el JSON o el motivo del fallo. */
    suspend fun leer(ruta: String): Pair<JsonElement?, String?> = leerConEstado(ruta).let { it.json to it.error }

    /** GET que debe devolver un objeto. */
    suspend fun leerObjeto(ruta: String): Pair<JsonObject?, String?> {
        val (el, err) = leer(ruta)
        return (el as? JsonObject) to (if (el != null && el !is JsonObject) "Respuesta inesperada del servidor." else err)
    }

    /** Todo lo que muestran las secciones (una ida). */
    suspend fun cargar(): Pair<JsonObject?, String?> = leerObjeto(BASE)

    suspend fun enviar(metodo: HttpMethod, ruta: String, cuerpo: JsonObject): ResultadoEscritura {
        val tk = token() ?: return sinSesion
        return try {
            val resp = http.request("${Supabase.SITE_URL}$ruta") {
                method = metodo
                header("Authorization", "Bearer $tk")
                contentType(ContentType.Application.Json)
                setBody(cuerpo.toString())
            }
            AtencionRepo.resultadoDeRespuesta(resp.status.value, runCatching { resp.bodyAsText() }.getOrNull())
        } catch (e: kotlin.coroutines.cancellation.CancellationException) {
            throw e
        } catch (e: Exception) {
            sinRed
        }
    }

    /** Guarda UNA sección de la pantalla (PUT /api/staff/configuracion). */
    suspend fun guardarSeccion(seccion: String, datos: JsonObject): ResultadoEscritura =
        enviar(HttpMethod.Put, BASE, buildJsonObject { put("seccion", seccion); put("datos", datos) })

    /** Catálogos chicos (métodos de pago, categorías, tipos de imagen, campos, equipos, hallazgos). */
    suspend fun catalogo(tabla: String): Pair<JsonObject?, String?> = leerObjeto("$BASE/catalogo?tabla=$tabla")

    suspend fun crearEnCatalogo(tabla: String, datos: JsonObject) =
        enviar(HttpMethod.Post, "$BASE/catalogo", buildJsonObject { put("tabla", tabla); put("datos", datos) })

    suspend fun editarEnCatalogo(tabla: String, id: String, datos: JsonObject) =
        enviar(HttpMethod.Patch, "$BASE/catalogo", buildJsonObject { put("tabla", tabla); put("id", id); put("datos", datos) })

    suspend fun borrarDeCatalogo(tabla: String, id: String) =
        enviar(HttpMethod.Delete, "$BASE/catalogo", buildJsonObject { put("tabla", tabla); put("id", id) })

    /**
     * Sube una imagen (logo, portada o foto de la clínica) al MISMO bucket y
     * ruta que la web. Responde `{ url }` (y `fotos` para la galería).
     */
    suspend fun subirImagen(tipo: String, nombre: String, bytes: ByteArray, mime: String?): ResultadoEscritura {
        val tk = token() ?: return sinSesion
        return try {
            val resp = http.post("${Supabase.SITE_URL}$BASE/imagen") {
                header("Authorization", "Bearer $tk")
                setBody(MultiPartFormDataContent(formData {
                    append("tipo", tipo)
                    append("archivo", bytes, Headers.build {
                        append(HttpHeaders.ContentType, mime ?: "image/jpeg")
                        append(HttpHeaders.ContentDisposition, "filename=\"${nombre.replace("\"", "")}\"")
                    })
                }))
            }
            AtencionRepo.resultadoDeRespuesta(resp.status.value, runCatching { resp.bodyAsText() }.getOrNull())
        } catch (e: kotlin.coroutines.cancellation.CancellationException) {
            throw e
        } catch (e: Exception) {
            sinRed
        }
    }

    suspend fun quitarFotoClinica(url: String) =
        enviar(HttpMethod.Delete, "$BASE/imagen", buildJsonObject { put("url", url) })

    /**
     * Guarda una clave que también lee Sani (flujo de la clínica sin
     * especialidades, entrada del bot, preguntas de mostrador). Mismo endpoint
     * que la web: avisa al bot y responde `botSync`.
     */
    suspend fun guardarConfigSani(clave: String, valor: JsonElement) =
        enviar(HttpMethod.Put, "/api/staff/leadai/actualizar-configuracion", buildJsonObject { put("clave", clave); put("valor", valor) })

    /** "Reintentar actualización de Sani" (no reescribe nada). */
    suspend fun reintentarSani() = enviar(HttpMethod.Post, "/api/staff/leadai/actualizar-configuracion", JsonObject(emptyMap()))

    /** Lo que se autocompleta al ubicar el pin: geocodificación inversa + la regla de la web. */
    suspend fun ubicacionDePin(lat: Double, lng: Double): JsonObject? {
        val (inv, _) = leerObjeto("/api/geocode/reverse?lat=$lat&lng=$lng")
        fun q(k: String) = (inv?.get(k) as? JsonPrimitive)?.takeIf { it !is JsonNull }?.contentOrNull?.takeIf { it.isNotBlank() }?.let { "&$k=${pe.saniape.app.ui.urlEncode(it)}" } ?: ""
        return leerObjeto("$BASE/geo?lat=$lat&lng=$lng${q("direccion")}${q("ciudad")}${q("distrito")}").first
    }
}
