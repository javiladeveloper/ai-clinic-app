package pe.saniape.app.data.staff

import io.github.jan.supabase.auth.auth
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.contentType
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put
import pe.saniape.app.data.Supabase
import pe.saniape.app.data.crearHttpClient
import pe.saniape.app.tutoriales.mensajeError

// «✍ Consentimiento» del ⋯ de la ficha (11/11/2026, paridad con la web
// components/pacientes/FirmarConsentimiento.tsx): recepción genera el enlace y el
// paciente lee y firma el compromiso en SU celular (QR o WhatsApp). Mismo
// endpoint que la web: POST/GET/DELETE /api/staff/firma/crear (Bearer del staff).

data class DocumentoFirmable(val id: String, val nombre: String, val porDefecto: Boolean)

object ConsentimientoFirmaRepo {
    sealed class R {
        /** Enlace listo para el QR / WhatsApp (vale 30 min). */
        data class Enlace(val url: String, val token: String) : R()
        /** La clínica tiene VARIOS documentos activos: primero se elige cuál. */
        data class Elegir(val documentos: List<DocumentoFirmable>) : R()
        /** La clínica todavía no escribió su compromiso (se hace en la web). */
        data object SinPlantilla : R()
        data class Error(val mensaje: String) : R()
    }

    private val json = Json { ignoreUnknownKeys = true }
    private val http by lazy { crearHttpClient() }
    private fun JsonObject.txt(k: String) = (this[k] as? JsonPrimitive)?.contentOrNull
    private fun JsonObject.bool(k: String) = (this[k] as? JsonPrimitive)?.booleanOrNull == true

    /** Respuesta de POST → R (puro, para probarlo). */
    internal fun interpretar(status: Int, cuerpo: String): R {
        val o = runCatching { json.parseToJsonElement(cuerpo).jsonObject }.getOrNull()
        if (status in 200..299 && o != null) {
            if (o.bool("elegir")) {
                val docs = (o["documentos"] as? JsonArray).orEmpty().mapNotNull { e ->
                    val d = e as? JsonObject ?: return@mapNotNull null
                    val id = d.txt("id") ?: return@mapNotNull null
                    DocumentoFirmable(id, d.txt("nombre") ?: "Documento", d.bool("por_defecto"))
                }
                return R.Elegir(docs.sortedByDescending { it.porDefecto })
            }
            val url = o.txt("url"); val token = o.txt("token")
            if (!url.isNullOrBlank() && !token.isNullOrBlank()) return R.Enlace(url, token)
        }
        if (o?.bool("sinPlantilla") == true) return R.SinPlantilla
        return R.Error(mensajeError(cuerpo) ?: when (status) {
            401 -> "Tu sesión expiró. Vuelve a entrar."
            403 -> "No tienes permiso para esto."
            else -> "No se pudo generar el enlace."
        })
    }

    private suspend fun token(): String? = Supabase.client.auth.currentSessionOrNull()?.accessToken

    /** Sin [plantillaId] pide elegir si hay varios documentos (como la web). */
    suspend fun generar(pacienteId: String, plantillaId: String? = null): R {
        val tk = token() ?: return R.Error("Tu sesión expiró. Vuelve a entrar.")
        return try {
            val r = http.post("${Supabase.SITE_URL}/api/staff/firma/crear") {
                header("Authorization", "Bearer $tk")
                contentType(ContentType.Application.Json)
                setBody(buildJsonObject {
                    put("pacienteId", pacienteId)
                    if (plantillaId != null) put("plantillaId", plantillaId) else put("elegir", true)
                }.toString())
            }
            interpretar(r.status.value, r.bodyAsText())
        } catch (e: kotlin.coroutines.cancellation.CancellationException) {
            throw e
        } catch (_: Exception) {
            R.Error("Sin conexión. Revisa tu internet.")
        }
    }

    /** ¿Ya firmó? null = no se pudo saber (sin red): se vuelve a preguntar. */
    suspend fun firmado(tokenEnlace: String): Boolean? {
        val tk = token() ?: return null
        return try {
            val r = http.get("${Supabase.SITE_URL}/api/staff/firma/crear") {
                header("Authorization", "Bearer $tk"); parameter("token", tokenEnlace)
            }
            val o = runCatching { json.parseToJsonElement(r.bodyAsText()).jsonObject }.getOrNull()
            o?.bool("firmado")
        } catch (e: kotlin.coroutines.cancellation.CancellationException) { throw e } catch (_: Exception) { null }
    }

    /** Vence un enlace sin usar (se cambió de documento o se cerró sin firmar). */
    suspend fun anular(tokenEnlace: String) {
        val tk = token() ?: return
        try {
            http.delete("${Supabase.SITE_URL}/api/staff/firma/crear") {
                header("Authorization", "Bearer $tk"); parameter("token", tokenEnlace)
            }
        } catch (e: kotlin.coroutines.cancellation.CancellationException) { throw e } catch (_: Exception) { }
    }

    /** El mensaje de WhatsApp (el de la web). */
    fun mensajeWhatsApp(nombre: String, url: String): String =
        "Hola ${nombre.trim().split(" ").first()} 👋 Antes de empezar tu tratamiento, léelo y fírmalo aquí:\n$url"
}
