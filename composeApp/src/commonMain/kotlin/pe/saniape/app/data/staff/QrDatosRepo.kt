package pe.saniape.app.data.staff

import io.github.jan.supabase.auth.auth
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.contentType
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put
import pe.saniape.app.data.Supabase
import pe.saniape.app.data.crearHttpClient
import pe.saniape.app.tutoriales.mensajeError

// «📱 QR datos» del ⋯ de la ficha (11/11/2026, paridad con la web): el paciente
// corrige su teléfono/correo/dirección desde SU celular, sin cuenta (enlace de
// 24 h, página pública /datos/{token}). Mismo endpoint que la web
// (POST /api/staff/paciente/datos-qr, permiso 'pacientes').

object QrDatosRepo {
    sealed class R {
        data class Ok(val url: String) : R()
        data class Error(val mensaje: String) : R()
    }

    private val json = Json { ignoreUnknownKeys = true }
    private val http by lazy { crearHttpClient() }

    /** Respuesta → R (puro, para probarlo). */
    internal fun interpretar(status: Int, cuerpo: String): R {
        val obj = runCatching { json.parseToJsonElement(cuerpo).jsonObject }.getOrNull()
        val url = (obj?.get("url") as? JsonPrimitive)?.contentOrNull
        if (status in 200..299 && !url.isNullOrBlank()) return R.Ok(url)
        return R.Error(mensajeError(cuerpo) ?: when (status) {
            401 -> "Tu sesión expiró. Vuelve a entrar."
            403 -> "No tienes permiso para editar pacientes."
            else -> "No se pudo generar el código."
        })
    }

    suspend fun generar(pacienteId: String): R {
        val tk = Supabase.client.auth.currentSessionOrNull()?.accessToken ?: return R.Error("Tu sesión expiró. Vuelve a entrar.")
        return try {
            val r = http.post("${Supabase.SITE_URL}/api/staff/paciente/datos-qr") {
                header("Authorization", "Bearer $tk")
                contentType(ContentType.Application.Json)
                setBody(buildJsonObject { put("pacienteId", pacienteId) }.toString())
            }
            interpretar(r.status.value, r.bodyAsText())
        } catch (e: kotlin.coroutines.cancellation.CancellationException) {
            throw e
        } catch (_: Exception) {
            R.Error("Sin conexión. Revisa tu internet.")
        }
    }

    /** El mensaje para mandarle el enlace por WhatsApp. */
    fun mensajeWhatsApp(nombre: String, url: String): String =
        "Hola ${nombre.trim().split(" ").first()}, actualiza tus datos de contacto aquí (vale por 24 horas): $url"
}
