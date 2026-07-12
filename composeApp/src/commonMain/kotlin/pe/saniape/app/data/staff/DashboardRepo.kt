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
import kotlinx.serialization.json.jsonObject
import pe.saniape.app.data.Supabase
import pe.saniape.app.data.crearHttpClient

/** Una cita de la agenda de hoy (resumen del dashboard). */
data class CitaAgenda(
    val id: String,
    val hora: String,
    val estado: String,
    val paciente: String,
    val procedimiento: String,
)

/** Stats del dashboard (server-side, ya filtrados por miTerapeutaId si aplica). */
data class StatsDashboard(
    val esProfesional: Boolean,
    val totalPacientes: Int,
    val citasHoy: Int,
    val atendidasHoy: Int,
    val misCitasPendientes: Int,
    val misSesionesCompletadas: Int,
    val misSesionesSemana: Int,
    val citasSinConfirmar: Int,
    val citasSinProfesional: Int,
    val pacientesNuevosSemana: Int,
    val derivacionesPend: Int,
    val agendaHoy: List<CitaAgenda>,
)

object DashboardRepo {
    private val json = Json { ignoreUnknownKeys = true }
    private val http = crearHttpClient()
    private suspend fun token(): String? = Supabase.client.auth.currentSessionOrNull()?.accessToken

    /**
     * Últimos stats cargados. Sobreviven al cambio de tab (Inicio se remonta al volver):
     * la pantalla los muestra al instante y refresca en segundo plano, sin spinner que
     * borre todo. Se limpia al cerrar sesión ([[dev-vivo-app-nativa]] patrón de fluidez).
     */
    var cache: StatsDashboard? = null
        private set

    fun limpiarCache() { cache = null }

    private fun JsonObject.str(k: String): String? =
        (this[k] as? JsonPrimitive)?.content?.takeIf { it != "null" }
    private fun JsonObject.intp(k: String): Int =
        (this[k] as? JsonPrimitive)?.content?.toIntOrNull() ?: 0
    private fun JsonObject.bool(k: String): Boolean =
        (this[k] as? JsonPrimitive)?.content == "true"

    suspend fun stats(): StatsDashboard? {
        val tk = token() ?: return null
        val resp = http.get("${Supabase.SITE_URL}/api/dashboard/stats") {
            header("Authorization", "Bearer $tk")
        }
        if (resp.status != HttpStatusCode.OK) return null
        val o = json.parseToJsonElement(resp.bodyAsText()).jsonObject
        val agenda = (o["agendaHoy"] as? JsonArray ?: JsonArray(emptyList())).mapNotNull {
            val a = it.jsonObject
            CitaAgenda(
                id = a.str("id") ?: return@mapNotNull null,
                hora = a.str("hora") ?: "",
                estado = a.str("estado") ?: "",
                paciente = a.str("paciente") ?: "Paciente",
                procedimiento = a.str("procedimiento") ?: "Consulta",
            )
        }
        return StatsDashboard(
            esProfesional = o.bool("esProfesional"),
            totalPacientes = o.intp("totalPacientes"),
            citasHoy = o.intp("citasHoy"),
            atendidasHoy = o.intp("atendidasHoy"),
            misCitasPendientes = o.intp("misCitasPendientes"),
            misSesionesCompletadas = o.intp("misSesionesCompletadas"),
            misSesionesSemana = o.intp("misSesionesSemana"),
            citasSinConfirmar = o.intp("citasSinConfirmar"),
            citasSinProfesional = o.intp("citasSinProfesional"),
            pacientesNuevosSemana = o.intp("pacientesNuevosSemana"),
            derivacionesPend = o.intp("derivacionesPend"),
            agendaHoy = agenda,
        ).also { cache = it }   // guarda el último resultado para mostrarlo al instante al volver
    }
}
