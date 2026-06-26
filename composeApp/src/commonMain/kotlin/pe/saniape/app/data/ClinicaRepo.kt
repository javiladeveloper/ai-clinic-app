package pe.saniape.app.data

import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject

/** Página completa de una clínica (igual que /c/[slug] en la web). */
data class ClinicaDetalle(
    val nombre: String,
    val slug: String,
    val colorPrincipal: String?,
    val descripcion: String?,
    val ciudad: String?,
    val distrito: String?,
    val direccion: String?,
    val referencia: String?,
    val lat: Double?,
    val lng: Double?,
    val facebook: String?,
    val instagram: String?,
    val tiktok: String?,
    val sitioWeb: String?,
    val whatsappContacto: String?,
    val videoUrl: String?,
    val especialidades: List<String>,
    val profesionales: List<ProfDetalle>,
    val horarios: List<HorarioDia>,
    val reservas: Boolean,
    val resenas: List<Resena>,
    val calificacion: Double?,
    val totalResenas: Int,
) {
    /** Texto de ubicación "dirección, distrito, ciudad". */
    val ubicacion: String
        get() = listOfNotNull(direccion, distrito, ciudad).joinToString(", ")
}

data class ProfDetalle(val nombre: String, val especialidad: String?, val color: String?, val fotoUrl: String?)
data class HorarioDia(val dia: String, val activo: Boolean, val apertura: String, val cierre: String)
data class Resena(val pacienteNombre: String?, val calificacion: Int, val comentario: String?)

/**
 * Datos completos de una clínica para su mini-landing (endpoint /api/clinica/[slug],
 * el mismo que usa la página pública de la web).
 */
object ClinicaRepo {

    private val json = Json { ignoreUnknownKeys = true }
    private val http = crearHttpClient()

    private fun JsonObject.str(k: String): String? =
        (this[k] as? JsonPrimitive)?.content?.takeIf { it != "null" }
    private fun JsonObject.dbl(k: String): Double? =
        (this[k] as? JsonPrimitive)?.content?.toDoubleOrNull()
    private fun JsonObject.intp(k: String): Int =
        (this[k] as? JsonPrimitive)?.content?.toIntOrNull() ?: 0
    private fun JsonObject.bool(k: String): Boolean =
        (this[k] as? JsonPrimitive)?.content == "true"
    private fun JsonObject.lista(k: String): List<String> =
        (this[k] as? JsonArray)?.mapNotNull { (it as? JsonPrimitive)?.content } ?: emptyList()

    suspend fun detalle(slug: String): ClinicaDetalle? {
        val resp = http.get("${Supabase.SITE_URL}/api/clinica/$slug")
        if (resp.status != HttpStatusCode.OK) return null
        val o = json.parseToJsonElement(resp.bodyAsText()).jsonObject["clinica"]?.jsonObject ?: return null

        val profs = (o["profesionales"] as? JsonArray ?: JsonArray(emptyList())).mapNotNull {
            val p = it.jsonObject
            ProfDetalle(
                nombre = p.str("nombre") ?: return@mapNotNull null,
                especialidad = p.str("especialidad"),
                color = p.str("color"),
                fotoUrl = p.str("foto_url"),
            )
        }
        val horarios = (o["horarios"] as? JsonArray ?: JsonArray(emptyList())).mapNotNull {
            val h = it.jsonObject
            HorarioDia(
                dia = h.str("dia") ?: return@mapNotNull null,
                activo = h.bool("activo"),
                apertura = h.str("apertura") ?: "",
                cierre = h.str("cierre") ?: "",
            )
        }
        val resenas = (o["resenas"] as? JsonArray ?: JsonArray(emptyList())).map {
            val r = it.jsonObject
            Resena(r.str("paciente_nombre"), r.intp("calificacion"), r.str("comentario"))
        }

        return ClinicaDetalle(
            nombre = o.str("nombre") ?: "Clínica",
            slug = o.str("slug") ?: slug,
            colorPrincipal = o.str("color_principal"),
            descripcion = o.str("descripcion"),
            ciudad = o.str("ciudad"),
            distrito = o.str("distrito"),
            direccion = o.str("direccion"),
            referencia = o.str("referencia"),
            lat = o.dbl("lat"),
            lng = o.dbl("lng"),
            facebook = o.str("facebook"),
            instagram = o.str("instagram"),
            tiktok = o.str("tiktok"),
            sitioWeb = o.str("sitio_web"),
            whatsappContacto = o.str("whatsapp_contacto"),
            videoUrl = o.str("video_url"),
            especialidades = o.lista("especialidades"),
            profesionales = profs,
            horarios = horarios,
            reservas = o.bool("reservas"),
            resenas = resenas,
            calificacion = o.dbl("calificacion"),
            totalResenas = o.intp("totalResenas"),
        )
    }
}
