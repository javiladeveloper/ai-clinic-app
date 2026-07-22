package pe.saniape.app.data.staff

import io.github.jan.supabase.auth.auth
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import pe.saniape.app.data.Supabase
import pe.saniape.app.data.crearHttpClient

/** Un tratamiento resumido (el en curso o del historial). Espeja el JSON del endpoint. */
data class TratamientoResumen(
    val id: String,
    val servicio: String,
    val estado: String,
    val hechas: Int,
    val total: Int,
    val desde: String?,
    val diagnostico: String?,
)

/**
 * Resumen clínico rápido de un paciente: lo que el fisio necesita para atender sin abrir
 * la ficha completa (motivo, diagnóstico, tratamiento en curso e historial).
 */
data class ResumenClinico(
    val nombre: String,
    val edad: Int?,
    val ocupacion: String?,
    val diagnosticoVigente: String?,
    val tipoPatologia: String?,
    val motivo: String?,
    val tratamientoActivo: TratamientoResumen?,
    val tratamientos: List<TratamientoResumen>,
)

/**
 * Lee el resumen clínico del paciente vía el API web (con Bearer token), igual patrón
 * que [pe.saniape.app.data.PerfilRepo]. El endpoint ya existe; aquí solo se consume.
 */
object ResumenClinicoRepo {

    private val json = Json { ignoreUnknownKeys = true }
    private val http = crearHttpClient()

    private suspend fun token(): String? = Supabase.client.auth.currentSessionOrNull()?.accessToken

    private fun JsonObject.str(k: String): String? =
        (this[k] as? JsonPrimitive)?.content?.takeIf { it != "null" }

    private fun JsonObject.int(k: String): Int? =
        (this[k] as? JsonPrimitive)?.content?.takeIf { it != "null" }?.toIntOrNull()

    private fun tratamiento(o: JsonObject): TratamientoResumen = TratamientoResumen(
        id = o.str("id") ?: "",
        servicio = o.str("servicio") ?: "Tratamiento",
        estado = o.str("estado") ?: "",
        hechas = o.int("hechas") ?: 0,
        total = o.int("total") ?: 0,
        desde = o.str("desde"),
        diagnostico = o.str("diagnostico"),
    )

    /** GET al endpoint. Devuelve null si falla (sin sesión, error de red o HTTP no OK). */
    suspend fun cargar(pacienteId: String): ResumenClinico? {
        val tk = token() ?: return null
        val resp = try {
            http.get("${Supabase.SITE_URL}/api/staff/paciente/resumen-clinico?pacienteId=$pacienteId") {
                header("Authorization", "Bearer $tk")
            }
        } catch (_: Exception) {
            return null
        }
        if (resp.status != HttpStatusCode.OK) return null
        val o = runCatching { json.parseToJsonElement(resp.bodyAsText()) as? JsonObject }.getOrNull() ?: return null
        val trats = (o["tratamientos"] as? JsonArray)
            ?.mapNotNull { (it as? JsonObject)?.let(::tratamiento) }
            ?: emptyList()
        return ResumenClinico(
            nombre = o.str("nombre") ?: "Paciente",
            edad = o.int("edad"),
            ocupacion = o.str("ocupacion"),
            diagnosticoVigente = o.str("diagnosticoVigente"),
            tipoPatologia = o.str("tipoPatologia"),
            motivo = o.str("motivo"),
            tratamientoActivo = (o["tratamientoActivo"] as? JsonObject)?.let(::tratamiento),
            tratamientos = trats,
        )
    }
}
