package pe.saniape.app.data.staff

import io.github.jan.supabase.auth.auth
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.client.request.put
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.contentType
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import pe.saniape.app.data.Supabase
import pe.saniape.app.data.crearHttpClient
import pe.saniape.app.tutoriales.mensajeError

/**
 * Un profesional que ATIENDE necesita al menos una especialidad: sin ella la
 * agenda responde "Ya no hay cupo" aunque tenga horario. Gemelo de
 * `lib/especialidad-profesional.ts` de la web. La lógica de capacidad no cambia:
 * aquí solo se hace visible y se puede corregir.
 */
object ReglasEspecialidadProfesional {
    const val AVISO = "Sin especialidad: no se le pueden agendar citas"
    const val AYUDA = "Asígnale al menos una especialidad; mientras no la tenga, la agenda responde \"Ya no hay cupo\"."

    /** Quien está Inactivo no se agenda: no se avisa. */
    fun sinEspecialidad(estado: String?, cantidadEspecialidades: Int): Boolean =
        estado != "Inactivo" && cantidadEspecialidades == 0

    /** Mensaje de error si falta la especialidad al guardar (null = bien). */
    fun errorAlGuardar(activasEnClinica: Int, seleccionadas: Int): String? =
        if (activasEnClinica > 0 && seleccionadas == 0) "Elige al menos una especialidad: sin ella no se le pueden agendar citas." else null
}

@Serializable
data class EspecialidadOpcion(val id: String, val nombre: String = "")

@Serializable
data class EspecialidadesDeProfesional(
    val disponibles: List<EspecialidadOpcion> = emptyList(),
    val seleccionadas: List<String> = emptyList(),
)

/** `/api/staff/profesional/especialidades` (permiso `equipo`; las reglas las valida el servidor). */
object EspecialidadProfesionalRepo {
    private val json = Json { ignoreUnknownKeys = true; coerceInputValues = true; isLenient = true }
    private val http by lazy { crearHttpClient() }

    sealed class R {
        data class Ok(val datos: EspecialidadesDeProfesional) : R()
        data class Error(val mensaje: String, val status: Int = 0) : R()
    }

    internal fun interpretar(status: Int, cuerpo: String): R {
        if (status in 200..299) {
            val d = runCatching { json.decodeFromString(EspecialidadesDeProfesional.serializer(), cuerpo) }.getOrNull()
            return if (d != null) R.Ok(d) else R.Error("Respuesta inesperada del servidor.", status)
        }
        return R.Error(mensajeError(cuerpo) ?: when (status) {
            403 -> "No tienes permiso para editar al equipo."
            401 -> "Tu sesión expiró. Vuelve a entrar."
            else -> "No se pudieron guardar las especialidades."
        }, status)
    }

    private fun url() = "${Supabase.SITE_URL}/api/staff/profesional/especialidades"

    private suspend fun llamar(bloque: suspend (String) -> R): R {
        val tk = Supabase.client.auth.currentSessionOrNull()?.accessToken ?: return R.Error("Tu sesión expiró. Vuelve a entrar.", 401)
        return try { bloque(tk) } catch (e: kotlin.coroutines.cancellation.CancellationException) {
            throw e
        } catch (_: Exception) { R.Error("Sin conexión. Revisa tu internet.") }
    }

    suspend fun cargar(terapeutaId: String): R = llamar { tk ->
        val r = http.get(url()) { header("Authorization", "Bearer $tk"); parameter("terapeutaId", terapeutaId) }
        interpretar(r.status.value, r.bodyAsText())
    }

    suspend fun guardar(terapeutaId: String, especialidadIds: List<String>): R = llamar { tk ->
        val r = http.put(url()) {
            header("Authorization", "Bearer $tk"); parameter("terapeutaId", terapeutaId)
            contentType(ContentType.Application.Json)
            setBody(buildJsonObject { put("especialidadIds", buildJsonArray { especialidadIds.forEach { add(kotlinx.serialization.json.JsonPrimitive(it)) } }) }.toString())
        }
        // El PUT responde { ok, seleccionadas }: se vuelve a leer lo fresco.
        if (r.status.value in 200..299) cargar(terapeutaId) else interpretar(r.status.value, r.bodyAsText())
    }
}
