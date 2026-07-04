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

/** Una clínica del directorio público (con ubicación para el mapa). */
data class ClinicaDir(
    val nombre: String,
    val slug: String,
    val logoUrl: String?,
    val colorPrincipal: String?,
    val ciudad: String?,
    val distrito: String?,
    val direccion: String?,
    val referencia: String?,
    val lat: Double?,
    val lng: Double?,
    val descripcion: String?,
    val especialidades: List<String>,
    /** Reserva ONLINE habilitada (plan Plus/Trial + toggle). Premium sale en el directorio pero SIN reservar. */
    val permiteReserva: Boolean = true,
    /** WhatsApp de contacto: la alternativa cuando no se puede reservar online. */
    val whatsappContacto: String? = null,
)

/**
 * Directorio público de clínicas (mismo endpoint que la web: /api/directorio).
 * No requiere autenticación — datos públicos para que el paciente elija dónde reservar.
 */
object DirectorioRepo {

    private val json = Json { ignoreUnknownKeys = true }
    private val http = crearHttpClient()

    private fun JsonObject.str(k: String): String? =
        (this[k] as? JsonPrimitive)?.content?.takeIf { it != "null" }

    private fun JsonObject.dbl(k: String): Double? =
        (this[k] as? JsonPrimitive)?.content?.toDoubleOrNull()

    suspend fun clinicas(busqueda: String? = null, especialidad: String? = null): List<ClinicaDir> {
        val params = buildList {
            if (!busqueda.isNullOrBlank()) add("q=$busqueda")
            if (!especialidad.isNullOrBlank()) add("esp=$especialidad")
        }.joinToString("&")
        val url = "${Supabase.SITE_URL}/api/directorio" + if (params.isNotEmpty()) "?$params" else ""

        val resp = http.get(url)
        if (resp.status != HttpStatusCode.OK) return emptyList()
        val root = json.parseToJsonElement(resp.bodyAsText()).jsonObject
        val arr = root["clinicas"]?.jsonArray ?: return emptyList()

        return arr.mapNotNull { el ->
            val o = el.jsonObject
            val slug = o.str("slug") ?: return@mapNotNull null
            ClinicaDir(
                nombre = o.str("nombre") ?: "Clínica",
                slug = slug,
                logoUrl = o.str("logo_url"),
                colorPrincipal = o.str("color_principal"),
                ciudad = o.str("ciudad"),
                distrito = o.str("distrito"),
                direccion = o.str("direccion"),
                referencia = o.str("referencia"),
                lat = o.dbl("lat"),
                lng = o.dbl("lng"),
                descripcion = o.str("descripcion"),
                especialidades = (o["especialidades"] as? JsonArray)
                    ?.mapNotNull { (it as? JsonPrimitive)?.content } ?: emptyList(),
                // Si el backend aún no manda el campo (deploy viejo), asumimos true (comportamiento previo).
                permiteReserva = (o["permite_reserva"] as? JsonPrimitive)?.content != "false",
                whatsappContacto = o.str("whatsapp_contacto"),
            )
        }
    }
}
