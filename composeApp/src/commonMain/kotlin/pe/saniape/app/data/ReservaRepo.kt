package pe.saniape.app.data

import io.github.jan.supabase.auth.auth
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put

/** Datos para mostrar el formulario de reserva de una clínica. */
data class InfoReserva(
    val clinicaNombre: String,
    val direccion: String?,
    val profesionales: List<ProfReserva>,
    val especialidades: List<EspReserva>,
)
data class ProfReserva(val id: String, val nombre: String, val especialidad: String?)
data class EspReserva(val id: String, val nombre: String)

/** Resultado de crear una reserva. */
/** Otro profesional que SÍ puede a la hora pedida. */
data class ProfesionalLibre(val id: String, val nombre: String)

sealed class ResultadoReserva {
    data class Ok(val token: String) : ResultadoReserva()
    /**
     * No se pudo, pero con salidas: quién más puede a esa hora y a qué otras
     * horas podría el elegido. Un "no se puede" a secas hace que el paciente
     * abandone; esto es lo que diría recepción por teléfono.
     */
    data class Error(
        val mensaje: String,
        val profesionales: List<ProfesionalLibre> = emptyList(),
        val horas: List<String> = emptyList(),
    ) : ResultadoReserva()
}

/**
 * Reserva de citas: llama a los MISMOS endpoints de la web (saniape.com/api/reservar)
 * con el Bearer token del paciente. Reutiliza la lógica segura (verificación de DNI
 * con MaxFind, rate-limit, validación de horario) — sin secretos en el APK.
 */
object ReservaRepo {

    private val json = Json { ignoreUnknownKeys = true }
    private val http = crearHttpClient()

    private suspend fun token(): String? =
        Supabase.client.auth.currentSessionOrNull()?.accessToken

    private fun JsonObject.str(key: String): String? =
        (this[key] as? JsonPrimitive)?.content?.takeIf { it != "null" }

    /** GET /api/reservar/[slug] — info de la clínica para armar el formulario. */
    suspend fun infoClinica(slug: String): InfoReserva? {
        val resp = http.get("${Supabase.SITE_URL}/api/reservar/$slug")
        if (resp.status != HttpStatusCode.OK) return null
        val root = json.parseToJsonElement(resp.bodyAsText()).jsonObject

        val cl = root["clinica"]?.jsonObject
        val profs = (root["profesionales"]?.jsonArray ?: emptyList()).mapNotNull {
            val o = it.jsonObject
            val id = o.str("id") ?: return@mapNotNull null
            ProfReserva(id, o.str("nombre") ?: "Profesional", o.str("especialidad"))
        }
        val esps = (root["especialidades"]?.jsonArray ?: emptyList()).mapNotNull {
            val o = it.jsonObject
            val id = o.str("id") ?: return@mapNotNull null
            EspReserva(id, o.str("nombre") ?: "")
        }
        return InfoReserva(
            clinicaNombre = cl?.str("nombre") ?: "Clínica",
            direccion = cl?.str("direccion"),
            profesionales = profs,
            especialidades = esps,
        )
    }

    /** POST /api/reservar/[slug] — crea la reserva con el token del paciente. */
    suspend fun reservar(
        slug: String,
        dni: String,
        telefono: String,
        fecha: String,
        hora: String,
        terapeutaId: String?,
        motivo: String,
    ): ResultadoReserva {
        val tk = token() ?: return ResultadoReserva.Error("Sesión expirada. Vuelve a entrar.")

        val cuerpo = buildJsonObject {
            put("dni", dni)
            put("telefono", telefono)
            put("fecha", fecha)
            put("hora", hora)
            if (terapeutaId != null) put("terapeuta_id", terapeutaId)
            put("motivo", motivo)
        }

        val resp = http.post("${Supabase.SITE_URL}/api/reservar/$slug") {
            header("Authorization", "Bearer $tk")
            contentType(ContentType.Application.Json)
            setBody(cuerpo.toString())
        }
        val body = runCatching { json.parseToJsonElement(resp.bodyAsText()).jsonObject }.getOrNull()
        return if (resp.status == HttpStatusCode.OK && body?.str("ok") == "true") {
            ResultadoReserva.Ok(body.str("token") ?: "")
        } else {
            {
                val alt = body?.get("alternativas")?.jsonObject
                val profs = (alt?.get("profesionales") as? JsonArray ?: JsonArray(emptyList())).mapNotNull {
                    val o = it.jsonObject
                    ProfesionalLibre(o.str("id") ?: return@mapNotNull null, o.str("nombre") ?: "")
                }
                val horas = (alt?.get("horas") as? JsonArray ?: JsonArray(emptyList()))
                    .mapNotNull { (it as? JsonPrimitive)?.content }
                ResultadoReserva.Error(
                    body?.str("error") ?: "No se pudo reservar. Intenta de nuevo.",
                    profs, horas,
                )
            }()
        }
    }
}
