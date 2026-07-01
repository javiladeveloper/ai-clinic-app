package pe.saniape.app.data.staff

import io.github.jan.supabase.auth.auth
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import pe.saniape.app.data.Supabase
import pe.saniape.app.data.crearHttpClient

/**
 * Carga el contexto del staff desde /api/staff/contexto (con Bearer). Cachea el
 * último contexto en memoria para que las pantallas lean permisos/plan/scope.
 */
object StaffContextoRepo {

    private val json = Json { ignoreUnknownKeys = true }
    private val http = crearHttpClient()

    /** Último contexto cargado (null si aún no se cargó o no es staff). */
    var actual: ContextoStaff? = null
        private set

    private suspend fun token(): String? = Supabase.client.auth.currentSessionOrNull()?.accessToken

    private fun JsonObject.str(k: String): String? =
        (this[k] as? JsonPrimitive)?.content?.takeIf { it != "null" }
    private fun JsonObject.bool(k: String): Boolean =
        (this[k] as? JsonPrimitive)?.content == "true"
    private fun JsonObject.intOrNull(k: String): Int? =
        (this[k] as? JsonPrimitive)?.content?.toIntOrNull()
    private fun JsonObject.obj(k: String): JsonObject? = this[k] as? JsonObject

    private fun permisos(o: JsonObject?): Permisos = Permisos(
        pacientes = o?.bool("pacientes") ?: false,
        citas = o?.bool("citas") ?: false,
        agendar = o?.bool("agendar") ?: false,
        sesiones = o?.bool("sesiones") ?: false,
        pagos = o?.bool("pagos") ?: false,
        finanzas = o?.bool("finanzas") ?: false,
        comisiones = o?.bool("comisiones") ?: false,
        servicios = o?.bool("servicios") ?: false,
        equipo = o?.bool("equipo") ?: false,
        ajustes = o?.bool("ajustes") ?: false,
    )

    private fun features(o: JsonObject?): PlanFeatures = PlanFeatures(
        finanzas = o?.bool("finanzas") ?: false,
        comisiones = o?.bool("comisiones") ?: false,
        reportes = o?.bool("reportes") ?: false,
        whatsapp = o?.bool("whatsapp") ?: false,
        ia = o?.bool("ia") ?: false,
        reservas = o?.bool("reservas") ?: false,
        derivaciones = o?.bool("derivaciones") ?: false,
        examenes = o?.bool("examenes") ?: false,
        fotosEvolutivas = o?.bool("fotosEvolutivas") ?: false,
    )

    /** Resultado de cargar el contexto. */
    sealed class Resultado {
        data class Ok(val contexto: ContextoStaff) : Resultado()
        data object NoEsClinica : Resultado()     // 403: la cuenta no tiene clínica
        data class Suspendida(val nombre: String) : Resultado()
        data class Error(val mensaje: String) : Resultado()
    }

    suspend fun cargar(): Resultado {
        val tk = token() ?: return Resultado.Error("Sesión expirada")
        // Toda la llamada de red va protegida: si el servidor no responde (offline,
        // conexión rechazada, timeout) devolvemos Error en vez de crashear la app.
        return try {
            cargarInterno(tk)
        } catch (e: Exception) {
            Resultado.Error("No se pudo conectar con el servidor. Revisa tu conexión.")
        }
    }

    private suspend fun cargarInterno(tk: String): Resultado {
        val resp = http.get("${Supabase.SITE_URL}/api/staff/contexto") {
            header("Authorization", "Bearer $tk")
        }
        if (resp.status == HttpStatusCode.Forbidden) return Resultado.NoEsClinica
        if (resp.status != HttpStatusCode.OK) return Resultado.Error("No se pudo cargar el contexto")

        val o = json.parseToJsonElement(resp.bodyAsText()).jsonObject
        if (o.bool("suspendida")) return Resultado.Suspendida(o.str("clinicaNombre") ?: "Tu clínica")

        val plan = o.obj("planEstado")
        val ctx = ContextoStaff(
            clinicaId = o.str("clinicaId") ?: "",
            clinicaNombre = o.str("clinicaNombre") ?: "Clínica",
            logoUrl = o.str("logoUrl"),
            colorPrincipal = o.str("colorPrincipal"),
            terminologiaProfesional = o.str("terminologiaProfesional") ?: "Profesional",
            rol = o.str("rol"),
            nombre = o.str("nombre"),
            permisos = permisos(o.obj("permisos")),
            plan = o.str("plan"),
            planEstado = PlanEstado(
                efectivo = plan?.str("efectivo") ?: "Basico",
                vencido = plan?.bool("vencido") ?: false,
                diasRestantes = plan?.intOrNull("diasRestantes"),
                features = features(plan?.obj("features")),
            ),
            miTerapeutaId = o.str("miTerapeutaId"),
            usaSesiones = o.bool("usaSesiones"),
            clinicas = (o["clinicas"]?.jsonArray ?: emptyList()).mapNotNull {
                val c = it.jsonObject
                val id = c.str("id") ?: return@mapNotNull null
                ClinicaRef(id, c.str("nombre") ?: "Clínica")
            },
            tienePortal = o.bool("tienePortal"),
        )
        actual = ctx
        return Resultado.Ok(ctx)
    }

    fun limpiar() { actual = null }
}
