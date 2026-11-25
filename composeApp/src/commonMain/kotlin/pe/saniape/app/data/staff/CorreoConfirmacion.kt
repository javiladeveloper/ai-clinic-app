package pe.saniape.app.data.staff

import io.github.jan.supabase.auth.auth
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import pe.saniape.app.data.Supabase
import pe.saniape.app.data.crearHttpClient

/*
 * 📧 CONFIRMA TU CORREO (24/11/2026, versión suave). Gemelo de
 * components/seguridad/GuardiaCorreo.tsx + lib/confirmar-correo.ts (web): quien creó
 * su clínica tiene 24 horas para confirmar su correo; vencido, el panel se tapa hasta
 * que confirme. Endpoint: GET/POST /api/staff/correo-confirmacion (Bearer).
 */

enum class EstadoCorreo { NO_APLICA, CONFIRMADO, PENDIENTE, VENCIDO }

data class ConfirmacionCorreo(val estado: EstadoCorreo, val email: String? = null, val horasRestantes: Int = 0)

/** «te quedan 5 horas» / «te queda 1 hora» (gemelo de textoHorasRestantes). */
fun textoHorasRestantes(h: Int): String = if (h == 1) "te queda 1 hora" else "te quedan $h horas"

internal fun parsearConfirmacion(o: JsonObject?): ConfirmacionCorreo {
    fun s(k: String) = (o?.get(k) as? JsonPrimitive)?.content?.takeIf { it != "null" }
    val estado = when (s("estado")) {
        "pendiente" -> EstadoCorreo.PENDIENTE
        "vencido" -> EstadoCorreo.VENCIDO
        "confirmado" -> EstadoCorreo.CONFIRMADO
        else -> EstadoCorreo.NO_APLICA
    }
    return ConfirmacionCorreo(estado, s("email"), s("horasRestantes")?.toDoubleOrNull()?.toInt() ?: 0)
}

object CorreoConfirmacionRepo {
    private val json = Json { ignoreUnknownKeys = true }
    private val http = crearHttpClient()
    private val URL get() = "${Supabase.SITE_URL}/api/staff/correo-confirmacion"

    /** Ante cualquier fallo nuestro no se molesta ni se bloquea a nadie: NO_APLICA. */
    suspend fun leer(): ConfirmacionCorreo = try {
        val tk = Supabase.client.auth.currentSessionOrNull()?.accessToken
        if (tk == null) ConfirmacionCorreo(EstadoCorreo.NO_APLICA)
        else {
            val r = http.get(URL) { header("Authorization", "Bearer $tk") }
            if (!r.status.isSuccess()) ConfirmacionCorreo(EstadoCorreo.NO_APLICA)
            else parsearConfirmacion(json.parseToJsonElement(r.bodyAsText()) as? JsonObject)
        }
    } catch (e: CancellationException) {
        throw e
    } catch (_: Exception) {
        ConfirmacionCorreo(EstadoCorreo.NO_APLICA)
    }

    /** Reenvía el enlace (o corrige el correo y lo manda ahí). Devuelve el error o null si salió. */
    suspend fun pedir(accion: String, email: String? = null): Pair<String?, String?> = try {
        val tk = Supabase.client.auth.currentSessionOrNull()?.accessToken ?: return "Tu sesión venció. Vuelve a entrar." to null
        val r = http.post(URL) {
            header("Authorization", "Bearer $tk")
            contentType(ContentType.Application.Json)
            setBody(buildJsonObject { put("accion", accion); email?.let { put("email", it) } }.toString())
        }
        val o = runCatching { json.parseToJsonElement(r.bodyAsText()) as? JsonObject }.getOrNull()
        val emailResp = (o?.get("email") as? JsonPrimitive)?.content
        if (r.status.isSuccess()) null to emailResp
        else ((o?.get("error") as? JsonPrimitive)?.content ?: "No se pudo enviar el correo") to null
    } catch (e: CancellationException) {
        throw e
    } catch (_: Exception) {
        "No se pudo enviar el correo. Revisa tu conexión." to null
    }
}
