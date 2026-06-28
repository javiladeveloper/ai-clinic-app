package pe.saniape.app.data

import io.github.jan.supabase.auth.auth
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.patch
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/** Perfil del paciente. nombre/email vienen de Google; dni es de una sola vez. */
data class PerfilPaciente(
    val nombre: String?,
    val dni: String?,
    val telefono: String?,
    val email: String?,
    val ocupacion: String?,
)

sealed class ResultadoPerfil {
    data object Ok : ResultadoPerfil()
    data class Error(val mensaje: String) : ResultadoPerfil()
}

/** Lee y actualiza el perfil del paciente vía el API web (con Bearer token). */
object PerfilRepo {

    private val json = Json { ignoreUnknownKeys = true }
    private val http = crearHttpClient()

    private suspend fun token(): String? = Supabase.client.auth.currentSessionOrNull()?.accessToken
    private fun JsonObject.str(k: String): String? =
        (this[k] as? JsonPrimitive)?.content?.takeIf { it != "null" }

    suspend fun cargar(): PerfilPaciente? {
        val tk = token() ?: return null
        val resp = http.get("${Supabase.SITE_URL}/api/paciente/perfil") {
            header("Authorization", "Bearer $tk")
        }
        if (resp.status != HttpStatusCode.OK) return null
        val o = json.parseToJsonElement(resp.bodyAsText()) as? JsonObject ?: return null
        return PerfilPaciente(o.str("nombre"), o.str("dni"), o.str("telefono"), o.str("email"), o.str("ocupacion"))
    }

    /** Guarda teléfono, ocupación y/o DNI (el DNI solo si aún no tenía). */
    suspend fun guardar(telefono: String?, dni: String?, ocupacion: String? = null): ResultadoPerfil {
        val tk = token() ?: return ResultadoPerfil.Error("Sesión expirada. Vuelve a entrar.")
        val cuerpo = buildJsonObject {
            if (telefono != null) put("telefono", telefono)
            if (ocupacion != null) put("ocupacion", ocupacion)
            if (!dni.isNullOrBlank()) put("dni", dni)
        }
        val resp = http.patch("${Supabase.SITE_URL}/api/paciente/perfil") {
            header("Authorization", "Bearer $tk")
            contentType(ContentType.Application.Json)
            setBody(cuerpo.toString())
        }
        if (resp.status == HttpStatusCode.OK) return ResultadoPerfil.Ok
        val err = runCatching {
            (json.parseToJsonElement(resp.bodyAsText()) as? JsonObject)?.str("error")
        }.getOrNull()
        return ResultadoPerfil.Error(err ?: "No se pudo guardar. Intenta de nuevo.")
    }
}
