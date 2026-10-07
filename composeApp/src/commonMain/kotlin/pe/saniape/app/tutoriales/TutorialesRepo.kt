package pe.saniape.app.tutoriales

import io.github.jan.supabase.auth.auth
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.contentType
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.put
import pe.saniape.app.data.Supabase
import pe.saniape.app.data.crearHttpClient

/**
 * Endpoints de tutoriales de la web (con el Bearer del staff). Nunca se manda
 * `clinica_id`: sale de la sesión. Medir nunca rompe nada: sin señal, el evento
 * se descarta en silencio.
 */
object TutorialesRepo {

    private val http by lazy { crearHttpClient() }

    sealed class Resultado {
        data class Ok(val catalogo: CatalogoTutoriales) : Resultado()
        /** 404: la web todavía no tiene el endpoint → la función se oculta. */
        data object NoDisponible : Resultado()
        data class Error(val mensaje: String) : Resultado()
    }

    private suspend fun token(): String? = Supabase.client.auth.currentSessionOrNull()?.accessToken

    suspend fun catalogo(): Resultado {
        val tk = token() ?: return Resultado.Error("Tu sesión expiró. Vuelve a entrar.")
        return try {
            val resp = http.get("${Supabase.SITE_URL}/api/tutoriales/catalogo?plataforma=app") {
                header("Authorization", "Bearer $tk")
            }
            val cuerpo = runCatching { resp.bodyAsText() }.getOrNull().orEmpty()
            when {
                resp.status.value == 404 -> Resultado.NoDisponible
                resp.status.value in 200..299 ->
                    parsearCatalogo(cuerpo)?.let { Resultado.Ok(it) } ?: Resultado.Error("Respuesta inesperada del servidor.")
                else -> Resultado.Error(mensajeError(cuerpo) ?: "No se pudo cargar la ayuda.")
            }
        } catch (e: kotlin.coroutines.cancellation.CancellationException) {
            throw e
        } catch (_: Exception) {
            Resultado.Error("Sin conexión. Revisa tu internet.")
        }
    }

    /** `{ tour, evento, paso }`: medición de un tutorial. */
    suspend fun evento(tour: String, evento: String, paso: Int) = enviar(buildJsonObject {
        put("tour", tour); put("evento", evento); put("paso", paso.coerceIn(0, 50))
    })

    /** `{ clave, estado: 'visto' }`: píldora vista o "No mostrar más". */
    suspend fun marcarVisto(clave: String) = enviar(buildJsonObject { put("clave", clave); put("estado", "visto") })

    private suspend fun enviar(cuerpo: JsonObject) {
        val tk = token() ?: return
        try {
            http.post("${Supabase.SITE_URL}/api/tutoriales/eventos") {
                header("Authorization", "Bearer $tk")
                contentType(ContentType.Application.Json)
                setBody(cuerpo.toString())
            }
        } catch (e: kotlin.coroutines.cancellation.CancellationException) {
            throw e
        } catch (_: Exception) { /* sin señal: se descarta */ }
    }
}

/** `{ "error": "<mensaje para la persona>" }` del contrato, o null. */
internal fun mensajeError(cuerpo: String?): String? = runCatching {
    val o = kotlinx.serialization.json.Json.parseToJsonElement(cuerpo ?: return null) as? JsonObject
    (o?.get("error") as? JsonPrimitive)?.contentOrNull?.takeIf { it.isNotBlank() }
}.getOrNull()
