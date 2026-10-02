package pe.saniape.app.data.staff

import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.Count
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
import pe.saniape.app.data.offline.CacheLectura

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

    /** Sede con la que se cargó [cache] ("" = sin sede). Al cambiar de sede no se muestra la de otra. */
    private var cacheSede: String = ""

    /** [cache] solo si es de la sede activa (sin multisede: siempre). */
    val cacheVigente: StatsDashboard?
        get() = cache?.takeIf { cacheSede == sedeActual() }

    private fun sedeActual(): String = SedeActiva.filtro?.sedeId ?: ""

    fun limpiarCache() {
        claveDisco()?.let { CacheLectura.borrar(it) }
        cache = null
    }

    /** En disco va por usuario: un celular compartido no mezcla Inicios. */
    private fun claveDisco(): String? =
        Supabase.client.auth.currentSessionOrNull()?.user?.id?.let {
            // Multisede: el Inicio de cada sede se guarda aparte. Sin sede, la clave de siempre.
            CacheLectura.claveInicio(it) + sedeActual().let { s -> if (s.isEmpty()) "" else ":sede=$s" }
        }

    /**
     * Lo último que se vio, SIN red (caché en disco). Sin esto, abrir la app
     * sin señal mostraba "No se pudieron cargar tus datos" en la primera
     * pantalla, aunque el resto ya funcionara offline (2026-09-04).
     */
    fun desdeDisco(): StatsDashboard? = runCatching {
        val crudo = CacheLectura.leer(claveDisco() ?: return null) ?: return null
        parsear(json.parseToJsonElement(crudo).jsonObject).also { cache = it; cacheSede = sedeActual() }
    }.getOrNull()

    private fun JsonObject.str(k: String): String? =
        (this[k] as? JsonPrimitive)?.content?.takeIf { it != "null" }
    private fun JsonObject.intp(k: String): Int =
        (this[k] as? JsonPrimitive)?.content?.toIntOrNull() ?: 0
    private fun JsonObject.bool(k: String): Boolean =
        (this[k] as? JsonPrimitive)?.content == "true"

    suspend fun stats(): StatsDashboard? {
        val tk = token() ?: return null
        // Multisede: las stats de la sede activa (?sede=, misma regla que la web).
        // Sin sede elegida (o sin multisede) la URL es la de siempre.
        val sede = sedeActual()
        val clave = claveDisco()
        val url = "${Supabase.SITE_URL}/api/dashboard/stats" + if (sede.isEmpty()) "" else "?sede=$sede"
        val resp = http.get(url) {
            header("Authorization", "Bearer $tk")
        }
        if (resp.status != HttpStatusCode.OK) return null
        var obj = json.parseToJsonElement(resp.bodyAsText()).jsonObject
        // Pacientes por sede: "Total pacientes" cuenta los de la sede elegida (como
        // el badge de la web, hooks/useBadgesMenu.ts). El endpoint todavía devuelve
        // el total de la clínica aunque reciba ?sede=, así que se cuenta aquí.
        // Solo para el gestor (el profesional ve "Mis pacientes", que es otra cosa).
        val sedePac = SedeActiva.estado.value.sedePacientes
        if (sedePac != null && !obj.bool("esProfesional")) {
            contarPacientesDeSede(sedePac)?.let { n ->
                obj = JsonObject(obj + ("totalPacientes" to JsonPrimitive(n)))
            }
        }
        // Se guarda ya corregido: desde el disco también sale el de la sede.
        clave?.let { CacheLectura.guardar(it, obj.toString()) }
        return parsear(obj)
            .also { cache = it; cacheSede = sede }   // guarda el último resultado para mostrarlo al instante al volver
    }

    /** Pacientes de una sede (count en el servidor, sin bajar filas). null si falla. */
    private suspend fun contarPacientesDeSede(sedeId: String): Int? = runCatching {
        Supabase.client.postgrest["pacientes"]
            .select(io.github.jan.supabase.postgrest.query.Columns.list("id")) {
                head = true
                count(Count.EXACT)
                filter { eq("sede_id", sedeId) }
            }
            .countOrNull()?.toInt()
    }.getOrNull()

    private fun parsear(o: JsonObject): StatsDashboard {
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
        )
    }
}
