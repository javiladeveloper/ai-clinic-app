package pe.saniape.app.data.staff

import io.github.jan.supabase.auth.auth
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.bodyAsText
import kotlinx.datetime.Instant
import kotlinx.datetime.toLocalDateTime
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import pe.saniape.app.data.Supabase
import pe.saniape.app.data.crearHttpClient

// ─────────────────────────────────────────────────────────────────────────────
// Actividad del equipo (solo Admin). Gemelo de components/actividad/ActividadEquipo
// en la web: qué se REGISTRÓ en un rango de fechas y quién lo hizo. Es de
// lectura: se pide a GET /api/actividad (la misma que usa la web; el servidor
// valida Admin y agrega con fn_actividad_equipo — la auditoría cruda no se
// expone). El detalle viene acotado a 500 movimientos por el servidor.
// ─────────────────────────────────────────────────────────────────────────────

data class PersonaActividad(val quien: String, val creados: Int, val completadas: Int, val eliminados: Int, val editados: Int)

data class MovimientoActividad(
    /** Instante ISO (UTC) en que se registró. */
    val cuando: String,
    val quien: String,
    val que: String,
    /** INSERT | UPDATE | DELETE | COMPLETAR */
    val accion: String,
    val tabla: String,
    val paciente: String?,
    val dni: String?,
    val pacienteId: String?,
    /** Para citas y sesiones: la fecha PARA la que quedó agendada. */
    val agendadaPara: String?,
)

data class CreadosActividad(val pacientes: Int, val citas: Int, val sesiones: Int, val tratamientos: Int, val pagos: Int) {
    val total: Int get() = pacientes + citas + sesiones + tratamientos + pagos
}

data class DatosActividad(
    val desde: String,
    val hasta: String,
    val esHoy: Boolean,
    val creados: CreadosActividad,
    val sesionesCompletadas: Int,
    val eliminados: Int,
    val equipo: List<PersonaActividad>,
    val detalle: List<MovimientoActividad>,
)

/** Qué tarjeta del resumen filtra el detalle (el número de la tarjeta = esos movimientos). */
enum class TipoActividad(val etiqueta: String) {
    CREADOS("Registros creados"),
    PACIENTES("Pacientes nuevos"),
    COMPLETADAS("Sesiones completadas"),
    ELIMINADOS("Eliminaciones"),
}

fun coincideTipo(m: MovimientoActividad, tipo: TipoActividad): Boolean = when (tipo) {
    TipoActividad.CREADOS -> m.accion == "INSERT"
    TipoActividad.PACIENTES -> m.accion == "INSERT" && m.tabla == "pacientes"
    TipoActividad.COMPLETADAS -> m.accion == "COMPLETAR"
    TipoActividad.ELIMINADOS -> m.accion == "DELETE"
}

/** Persona + tarjeta + búsqueda (nombre o DNI del paciente) se combinan, como en la web. */
fun filtrarMovimientos(
    detalle: List<MovimientoActividad>, persona: String?, tipo: TipoActividad?, busqueda: String,
): List<MovimientoActividad> {
    val q = busqueda.trim().lowercase()
    return detalle.filter { m ->
        (persona == null || m.quien == persona) &&
            (tipo == null || coincideTipo(m, tipo)) &&
            (q.isEmpty() || (m.paciente ?: "").lowercase().contains(q) || (m.dni ?: "").contains(q))
    }
}

/** "01:49" en hora de Lima a partir de un instante ISO. Si no se entiende, vacío. */
fun horaDeMovimiento(iso: String): String = runCatching {
    val t = Instant.parse(iso).toLocalDateTime(ZONA_CLINICA)
    "${t.hour.toString().padStart(2, '0')}:${t.minute.toString().padStart(2, '0')}"
}.getOrDefault("")

/** "2026-10-08" (día de Lima) de un instante ISO. */
fun diaDeMovimiento(iso: String): String = runCatching {
    val t = Instant.parse(iso).toLocalDateTime(ZONA_CLINICA)
    "${t.year}-${t.monthNumber.toString().padStart(2, '0')}-${t.dayOfMonth.toString().padStart(2, '0')}"
}.getOrDefault(iso.take(10))

/** Iniciales para el avatar (quita "Dr."/"Lic." como en la web). */
fun inicialesActividad(nombre: String): String =
    nombre.replace(Regex("[A-Za-z]+\\.\\s*"), "").split(" ").filter { it.isNotBlank() }.take(2)
        .joinToString("") { it.first().uppercase() }.ifEmpty { "?" }

object ActividadRepo {

    private val json = Json { ignoreUnknownKeys = true }
    private val http = crearHttpClient()

    private fun JsonObject.str(k: String): String? = (this[k] as? JsonPrimitive)?.contentOrNull?.takeIf { it != "null" }
    private fun JsonObject.int(k: String): Int = str(k)?.toDoubleOrNull()?.toInt() ?: 0

    /** Datos del rango [desde, hasta] (AAAA-MM-DD, hora de Lima), o el motivo del fallo. */
    suspend fun cargar(desde: String, hasta: String): Pair<DatosActividad?, String?> {
        val tk = Supabase.client.auth.currentSessionOrNull()?.accessToken ?: return null to "Tu sesión expiró. Vuelve a entrar."
        return try {
            val resp = http.get("${Supabase.SITE_URL}/api/actividad?desde=$desde&hasta=$hasta") { header("Authorization", "Bearer $tk") }
            val texto = resp.bodyAsText()
            if (resp.status.value in 200..299) {
                parsear(texto)?.let { it to null } ?: (null to "Respuesta inesperada del servidor.")
            } else {
                val msg = runCatching { json.parseToJsonElement(texto).jsonObject.str("error") }.getOrNull()
                null to (msg ?: "No se pudo cargar la actividad.")
            }
        } catch (e: kotlin.coroutines.cancellation.CancellationException) {
            throw e
        } catch (_: Exception) {
            null to "Sin conexión. Revisa tu internet."
        }
    }

    internal fun parsear(cuerpo: String): DatosActividad? = runCatching {
        val o = json.parseToJsonElement(cuerpo).jsonObject
        val cr = o["creados"] as? JsonObject
        DatosActividad(
            desde = o.str("desde").orEmpty(),
            hasta = o.str("hasta").orEmpty(),
            esHoy = o.str("esHoy") == "true",
            creados = CreadosActividad(
                cr?.int("pacientes") ?: 0, cr?.int("citas") ?: 0, cr?.int("sesiones") ?: 0,
                cr?.int("tratamientos") ?: 0, cr?.int("pagos") ?: 0,
            ),
            sesionesCompletadas = o.int("sesionesCompletadas"),
            eliminados = o.int("eliminados"),
            equipo = (o["equipo"] as? JsonArray).orEmpty().mapNotNull { e ->
                val p = e as? JsonObject ?: return@mapNotNull null
                PersonaActividad(p.str("quien") ?: "(sistema)", p.int("creados"), p.int("completadas"), p.int("eliminados"), p.int("editados"))
            },
            detalle = (o["detalle"] as? JsonArray).orEmpty().mapNotNull { e ->
                val m = e as? JsonObject ?: return@mapNotNull null
                MovimientoActividad(
                    cuando = m.str("cuando") ?: return@mapNotNull null,
                    quien = m.str("quien") ?: "(sistema)",
                    que = m.str("que").orEmpty(),
                    accion = m.str("accion").orEmpty(),
                    tabla = m.str("tabla").orEmpty(),
                    paciente = m.str("paciente"),
                    dni = m.str("dni"),
                    pacienteId = m.str("pacienteId"),
                    agendadaPara = m.str("agendadaPara"),
                )
            },
        )
    }.getOrNull()
}
