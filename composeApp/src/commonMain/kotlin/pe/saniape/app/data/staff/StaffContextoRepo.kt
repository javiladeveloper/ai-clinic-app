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
import pe.saniape.app.data.offline.CacheLectura

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
    /** `null` si la clave no vino: distinto de `false`. El flujo lo necesita
     *  porque ausente significa "sí lo usa", no "no lo usa". */
    private fun JsonObject.boolOrNull(k: String): Boolean? =
        (this[k] as? JsonPrimitive)?.content?.let { if (it == "true") true else if (it == "false") false else null }
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
            // SIN RED: se entra con el último contexto que se vio (caché local),
            // igual que la lista de pacientes. Sin esto, cerrar la app en una zona
            // sin señal dejaba al fisio AFUERA aunque su cola offline estuviera
            // intacta (hallazgo de la prueba en modo avión, 2026-09-04). Permisos
            // o plan pueden quedar un rato desactualizados: el servidor los vuelve
            // a validar en cada escritura, así que no es un riesgo.
            desdeCache() ?: Resultado.Error("No se pudo conectar con el servidor. Revisa tu conexión.")
        }
    }

    private fun claveCache(): String? =
        Supabase.client.auth.currentSessionOrNull()?.user?.id?.let { CacheLectura.claveContexto(it) }

    private fun desdeCache(): Resultado.Ok? = runCatching {
        val clave = claveCache() ?: return null
        val crudo = CacheLectura.leer(clave) ?: return null
        val ctx = parsear(json.parseToJsonElement(crudo).jsonObject)
        actual = ctx
        Resultado.Ok(ctx)
    }.getOrNull()

    private suspend fun cargarInterno(tk: String): Resultado {
        val resp = http.get("${Supabase.SITE_URL}/api/staff/contexto") {
            header("Authorization", "Bearer $tk")
        }
        if (resp.status == HttpStatusCode.Forbidden) return Resultado.NoEsClinica
        if (resp.status != HttpStatusCode.OK) return Resultado.Error("No se pudo cargar el contexto")

        val crudo = resp.bodyAsText()
        val o = json.parseToJsonElement(crudo).jsonObject
        if (o.bool("suspendida")) return Resultado.Suspendida(o.str("clinicaNombre") ?: "Tu clínica")

        val ctx = parsear(o)
        // Se guarda tal cual para poder ENTRAR sin red la próxima vez (ver `cargar`).
        claveCache()?.let { CacheLectura.guardar(it, crudo) }
        actual = ctx
        return Resultado.Ok(ctx)
    }

    private fun parsear(o: JsonObject): ContextoStaff {
        val plan = o.obj("planEstado")
        return ContextoStaff(
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
            // Con defaults: una app nueva contra un backend viejo (o al revés)
            // no puede quedarse sin nombres para sus citas.
            flujo = o.obj("flujo")?.let { f ->
                FlujoClinica(
                    usaConsulta = f.boolOrNull("usa_consulta") ?: true,
                    usaEvaluacion = f.boolOrNull("usa_evaluacion") ?: true,
                    labelConsulta = f.str("label_consulta") ?: "Consulta",
                    labelEvaluacion = f.str("label_evaluacion") ?: "Evaluación",
                    labelSesiones = f.str("label_sesiones") ?: "Sesiones",
                    labelAlta = f.str("label_alta") ?: "Alta",
                )
            } ?: FlujoClinica(),
            clinicas = (o["clinicas"]?.jsonArray ?: emptyList()).mapNotNull {
                val c = it.jsonObject
                val id = c.str("id") ?: return@mapNotNull null
                ClinicaRef(id, c.str("nombre") ?: "Clínica")
            },
            tienePortal = o.bool("tienePortal"),
        )
    }

    fun limpiar() {
        // Al salir se borra el contexto guardado: en un celular compartido, el
        // siguiente que entre no debe poder arrancar con la clínica del anterior.
        claveCache()?.let { CacheLectura.borrar(it) }
        actual = null
    }
}
