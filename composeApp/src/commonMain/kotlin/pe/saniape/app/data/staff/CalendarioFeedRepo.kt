package pe.saniape.app.data.staff

import io.github.jan.supabase.auth.auth
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.contentType
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put
import pe.saniape.app.data.Supabase
import pe.saniape.app.data.crearHttpClient

/** El enlace de calendario (ICS) del propio profesional. */
data class FeedCalendario(
    /** https://…/api/calendario/<token>.ics — para pegar en "Desde URL". */
    val url: String,
    /** webcal://… — Apple / Outlook. */
    val webcal: String,
    /** Abre Google Calendar con la suscripción lista. */
    val google: String,
    /** "iniciales" | "completo" */
    val privacidad: String,
)

data class EstadoCalendario(val tieneAgenda: Boolean, val feed: FeedCalendario?)

/**
 * "Mis citas en mi Google Calendar" (/api/staff/calendario/feed). Todo lo decide
 * el servidor (token, privacidad, rotar/revocar); acá solo se pide y se muestra.
 * Directo, sin cola offline: crear o rotar un enlace necesita la respuesta.
 */
object CalendarioFeedRepo {

    private val json = Json { ignoreUnknownKeys = true }
    private val http = crearHttpClient()
    private const val RUTA = "/api/staff/calendario/feed"

    private fun JsonObject.str(k: String): String? =
        (this[k] as? JsonPrimitive)?.contentOrNull?.takeIf { it != "null" }

    private fun feedDe(o: JsonObject?): FeedCalendario? {
        val f = (o?.get("feed") as? JsonObject) ?: return null
        return FeedCalendario(
            url = f.str("url") ?: return null,
            webcal = f.str("webcal") ?: return null,
            google = f.str("google") ?: return null,
            privacidad = if (f.str("privacidad") == "completo") "completo" else "iniciales",
        )
    }

    /** (estado, mensaje de error). */
    suspend fun cargar(): Pair<EstadoCalendario?, String?> {
        val tk = Supabase.client.auth.currentSessionOrNull()?.accessToken
            ?: return null to "Tu sesión expiró. Vuelve a entrar."
        return try {
            val resp = http.get("${Supabase.SITE_URL}$RUTA") { header("Authorization", "Bearer $tk") }
            val o = json.parseToJsonElement(resp.bodyAsText()).jsonObject
            if (resp.status.value !in 200..299) return null to (o.str("error") ?: "No se pudo cargar.")
            EstadoCalendario((o["tieneAgenda"] as? JsonPrimitive)?.contentOrNull == "true", feedDe(o)) to null
        } catch (e: CancellationException) { throw e } catch (_: Exception) {
            null to "Sin conexión. Revisa tu internet."
        }
    }

    /**
     * [accion]: `crear` · `rotar` · `revocar` · `privacidad` (con [privacidad]).
     * Devuelve (feed nuevo o null si se desactivó, mensaje de error).
     */
    suspend fun accion(accion: String, privacidad: String? = null): Pair<FeedCalendario?, String?> {
        val tk = Supabase.client.auth.currentSessionOrNull()?.accessToken
            ?: return null to "Tu sesión expiró. Vuelve a entrar."
        return try {
            val resp = http.post("${Supabase.SITE_URL}$RUTA") {
                header("Authorization", "Bearer $tk")
                contentType(ContentType.Application.Json)
                setBody(buildJsonObject {
                    put("accion", accion)
                    if (privacidad != null) put("privacidad", privacidad) else put("privacidad", JsonNull)
                }.toString())
            }
            val o = runCatching { json.parseToJsonElement(resp.bodyAsText()).jsonObject }.getOrNull()
            if (resp.status.value !in 200..299) return null to (o?.str("error") ?: "No se pudo completar. Intenta de nuevo.")
            feedDe(o) to null
        } catch (e: CancellationException) { throw e } catch (_: Exception) {
            null to "Sin conexión. Revisa tu internet."
        }
    }
}
