package pe.saniape.app.data.staff

import io.github.jan.supabase.auth.auth
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.client.request.request
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpMethod
import io.ktor.http.contentType
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.put
import pe.saniape.app.data.Supabase
import pe.saniape.app.data.crearHttpClient
import pe.saniape.app.data.offline.RechazoServidor
import pe.saniape.app.data.offline.ResultadoEscritura

/**
 * Equipo y accesos. TODO va por los endpoints de la web (`/api/staff/equipo/…`),
 * que reusan la MISMA lógica que las server actions de /equipo
 * (lib/equipo-acciones.ts): solo el Admin de la clínica, corte del soporte de
 * solo lectura, nunca dejar a la clínica sin Admin. Es dar y quitar acceso a la
 * clínica: la app no escribe nada de esto directo a la base.
 */
object EquipoRepo {

    private val json = Json { ignoreUnknownKeys = true; coerceInputValues = true; explicitNulls = false }
    private val http = crearHttpClient()

    private suspend fun token(): String? = Supabase.client.auth.currentSessionOrNull()?.accessToken

    private val sinSesion = ResultadoEscritura(
        registrada = false,
        rechazo = RechazoServidor("Tu sesión expiró. Vuelve a entrar.", "NO_AUTENTICADO", 401),
    )
    private val sinRed = ResultadoEscritura(
        registrada = false,
        rechazo = RechazoServidor("Sin conexión. Revisa tu internet.", "SIN_RED"),
    )

    private fun JsonObject.str(k: String): String? = (this[k] as? JsonPrimitive)?.contentOrNull

    /** Lo que muestra la pantalla, o el motivo del fallo. */
    suspend fun cargar(): Pair<EquipoDatos?, String?> {
        val tk = token() ?: return null to "Tu sesión expiró. Vuelve a entrar."
        return try {
            val resp = http.get("${Supabase.SITE_URL}/api/staff/equipo") { header("Authorization", "Bearer $tk") }
            val texto = resp.bodyAsText()
            if (resp.status.value in 200..299) {
                parsear(texto)?.let { it to null } ?: (null to "Respuesta inesperada del servidor.")
            } else {
                null to (mensajeDeError(texto) ?: "No se pudo cargar el equipo.")
            }
        } catch (e: kotlin.coroutines.cancellation.CancellationException) {
            throw e
        } catch (e: Exception) {
            null to "Sin conexión. Revisa tu internet."
        }
    }

    internal fun parsear(cuerpo: String): EquipoDatos? =
        runCatching { json.decodeFromString(EquipoDatos.serializer(), cuerpo) }.getOrNull()

    internal fun mensajeDeError(cuerpo: String): String? =
        runCatching { (json.parseToJsonElement(cuerpo) as? JsonObject)?.str("error") }.getOrNull()?.takeIf { it.isNotBlank() }

    private suspend fun enviar(metodo: HttpMethod, ruta: String, cuerpo: JsonObject): ResultadoEscritura {
        val tk = token() ?: return sinSesion
        return try {
            val resp = http.request("${Supabase.SITE_URL}/api/staff/equipo/$ruta") {
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

    /** Invitar: crea la cuenta y le manda sus credenciales por correo. `rolId` = 'Admin' o id del rol. */
    suspend fun invitar(email: String, nombre: String, rolId: String, vincularTerapeutaId: String?): ResultadoEscritura =
        enviar(HttpMethod.Post, "miembro", buildJsonObject {
            put("email", email.trim())
            put("nombre", nombre.trim())
            put("rolId", rolId)
            if (!vincularTerapeutaId.isNullOrBlank()) put("vincularTerapeutaId", vincularTerapeutaId)
        })

    /** Editar nombre, rol y (opcional) correo. Correo vacío = no cambiar; `rolId` null = mantener su rol. */
    suspend fun editar(perfilId: String, nombre: String, rolId: String?, email: String?): ResultadoEscritura =
        enviar(HttpMethod.Patch, "miembro", cuerpoEditar(perfilId, nombre, rolId, email))

    internal fun cuerpoEditar(perfilId: String, nombre: String, rolId: String?, email: String?): JsonObject =
        buildJsonObject {
            put("perfilId", perfilId)
            put("nombre", nombre.trim())
            rolId?.let { put("rolId", it) }
            email?.trim()?.takeIf { it.isNotEmpty() }?.let { put("email", it) }
        }

    /** Permisos individuales; `null` = restablecer a los del rol. */
    suspend fun guardarPermisos(perfilId: String, permisos: Map<String, Boolean>?): ResultadoEscritura =
        enviar(HttpMethod.Post, "permisos", cuerpoPermisos(perfilId, permisos))

    internal fun cuerpoPermisos(perfilId: String, permisos: Map<String, Boolean>?): JsonObject = buildJsonObject {
        put("perfilId", perfilId)
        put("permisos", permisos?.let { p -> JsonObject(p.mapValues { JsonPrimitive(it.value) }) } ?: JsonNull)
    }

    suspend fun revocar(perfilId: String): ResultadoEscritura =
        enviar(HttpMethod.Post, "revocar", buildJsonObject { put("perfilId", perfilId) })

    /** Clave temporal nueva + correo. El servidor responde el correo al que se envió. */
    suspend fun reenviar(perfilId: String): ResultadoEscritura =
        enviar(HttpMethod.Post, "reenviar", buildJsonObject { put("perfilId", perfilId) })

    /** Multisede: `null` = todas las sedes. */
    suspend fun guardarSedes(perfilId: String, sedes: List<String>?): ResultadoEscritura =
        enviar(HttpMethod.Post, "sedes", buildJsonObject {
            put("perfilId", perfilId)
            put("sedes", sedes?.let { s -> JsonArray(s.map { JsonPrimitive(it) }) } ?: JsonNull)
        })

    /** Vincula su cuenta a un registro de personal (`terapeutaId` o "nuevo" para crearlo). */
    suspend fun vincular(perfilId: String, terapeutaId: String): ResultadoEscritura =
        enviar(HttpMethod.Post, "vincular", buildJsonObject {
            put("perfilId", perfilId)
            put("terapeutaId", terapeutaId)
        })

    suspend fun desvincular(perfilId: String): ResultadoEscritura =
        enviar(HttpMethod.Delete, "vincular", buildJsonObject { put("perfilId", perfilId) })

    /** Enlace/QR de invitación (vale 72 h y una sola vez). */
    suspend fun generarEnlace(rolId: String): Pair<EnlaceInvitacion?, String?> {
        val r = enviar(HttpMethod.Post, "invitacion", buildJsonObject { put("rolId", rolId) })
        if (!r.registrada) return null to (r.rechazo?.error ?: "No se pudo generar el enlace")
        val c = r.cuerpo
        val token = c?.str("token")
        val url = c?.str("url")
        return if (token != null && url != null) EnlaceInvitacion(token, url) to null else null to "Respuesta inesperada del servidor."
    }

    /**
     * ¿Ya se registró alguien con el enlace? null = falla pasajera (se reintenta).
     * Sin sesión, o si el servidor ya no reconoce la sesión o el enlace
     * (401/403/404), `detener`: preguntar para siempre no sirve de nada.
     */
    suspend fun estadoEnlace(token: String): EstadoEnlace? {
        val tk = token() ?: return EstadoEnlace(usado = false, nombre = null, detener = true)
        return try {
            val resp = http.get("${Supabase.SITE_URL}/api/staff/equipo/invitacion") {
                header("Authorization", "Bearer $tk")
                parameter("token", token)
            }
            val st = resp.status.value
            if (st == 401 || st == 403 || st == 404) return EstadoEnlace(usado = false, nombre = null, detener = true)
            if (st !in 200..299) return null
            val o = json.parseToJsonElement(resp.bodyAsText()) as? JsonObject ?: return null
            EstadoEnlace(usado = (o["usado"] as? JsonPrimitive)?.booleanOrNull == true, nombre = o.str("nombre"))
        } catch (e: kotlin.coroutines.cancellation.CancellationException) {
            throw e
        } catch (e: Exception) {
            null
        }
    }
}
