package pe.saniape.app.data.staff

import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.Columns
import io.github.jan.supabase.postgrest.query.Order
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.client.request.post
import io.ktor.client.request.put
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.contentType
import kotlinx.coroutines.delay
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.put
import pe.saniape.app.data.Supabase
import pe.saniape.app.data.crearHttpClient
import pe.saniape.app.tutoriales.mensajeError

/** Días cortos del contrato, en orden de la semana. */
val DIAS_HORARIO: List<String> = listOf("Lun", "Mar", "Mié", "Jue", "Vie", "Sáb", "Dom")

@Serializable
data class TerapeutaHorario(val id: String, val nombre: String = "", val turno: String? = null, val sedeId: String? = null)

@Serializable
data class Franja(
    val id: String? = null,
    val dia: String,
    val horaInicio: String,
    val horaFin: String,
    val sedeId: String? = null,
)

@Serializable
data class SedeHorario(val id: String, val nombre: String = "Sede", val esPrincipal: Boolean = false)

@Serializable
data class RangoTurno(val inicio: String, val fin: String)

/** Respuesta de GET/PUT/POST `/api/staff/profesional/horario` (contrato §3.1). */
@Serializable
data class HorarioProfesional(
    val terapeuta: TerapeutaHorario,
    val franjas: List<Franja> = emptyList(),
    val multiSede: Boolean = false,
    val sedes: List<SedeHorario> = emptyList(),
    val sedeDefectoId: String? = null,
    val rangoTurno: RangoTurno? = null,
    val puedeEditar: Boolean = false,
    /** "horario_guardado" cuando una escritura insertó o actualizó algo. */
    val tarea: String? = null,
) {
    /** Franjas de un día, ordenadas por hora. */
    fun delDia(dia: String): List<Franja> = franjas.filter { it.dia == dia }.sortedBy { it.horaInicio }
    fun nombreSede(id: String?): String? = sedes.firstOrNull { it.id == id }?.nombre
}

/** Un profesional de la lista. */
data class ProfesionalItem(
    val id: String,
    val nombre: String,
    val especialidad: String?,
    val turno: String?,
)

/** HH:MM (también acepta HH:MM:SS) → "HH:MM". */
fun horaCorta(h: String): String = h.trim().split(":").let { p ->
    if (p.size >= 2) "${p[0].padStart(2, '0')}:${p[1].padStart(2, '0')}" else h
}

/** ¿La hora de fin es posterior a la de inicio? (guarda de UI; el servidor valida todo). */
fun finDespuesDeInicio(inicio: String, fin: String): Boolean = horaCorta(fin) > horaCorta(inicio)

/**
 * Horario semanal de un profesional (`horarios_terapeuta`). Las REGLAS (cruces,
 * "aplicar a todos", sede) viven en la web (`lib/horarios-profesional.ts`): la
 * app manda lo que la persona tocó y muestra el error del servidor tal cual.
 */
object HorarioProfesionalRepo {

    private val json = Json { ignoreUnknownKeys = true; coerceInputValues = true; isLenient = true }
    private val http by lazy { crearHttpClient() }

    sealed class R {
        data class Ok(val horario: HorarioProfesional) : R()
        /** La web todavía no tiene el endpoint (404 sin JSON). */
        data object NoDisponible : R()
        data class Error(val mensaje: String, val status: Int = 0) : R()
    }

    internal fun parsear(texto: String): HorarioProfesional? = runCatching {
        val h = json.decodeFromString(HorarioProfesional.serializer(), texto)
        h.copy(franjas = h.franjas.map { it.copy(horaInicio = horaCorta(it.horaInicio), horaFin = horaCorta(it.horaFin)) })
    }.getOrNull()

    /** Respuesta → R: 404 SIN cuerpo de error = endpoint ausente; con error = profesional inexistente. */
    internal fun interpretar(status: Int, cuerpo: String): R {
        if (status in 200..299) return parsear(cuerpo)?.let { R.Ok(it) } ?: R.Error("Respuesta inesperada del servidor.", status)
        val msg = mensajeError(cuerpo)
        if (status == 404 && msg == null) return R.NoDisponible
        return R.Error(msg ?: when (status) {
            403 -> "No tienes acceso a este horario."
            401 -> "Tu sesión expiró. Vuelve a entrar."
            else -> "No se pudo guardar el horario."
        }, status)
    }

    private suspend fun token(): String? = Supabase.client.auth.currentSessionOrNull()?.accessToken

    private suspend fun llamar(bloque: suspend (String) -> R): R {
        val tk = token() ?: return R.Error("Tu sesión expiró. Vuelve a entrar.", 401)
        return try { bloque(tk) } catch (e: kotlin.coroutines.cancellation.CancellationException) {
            throw e
        } catch (_: Exception) { R.Error("Sin conexión. Revisa tu internet.") }
    }

    private fun url() = "${Supabase.SITE_URL}/api/staff/profesional/horario"

    suspend fun cargar(terapeutaId: String): R = llamar { tk ->
        val r = http.get(url()) { header("Authorization", "Bearer $tk"); parameter("terapeutaId", terapeutaId) }
        interpretar(r.status.value, r.bodyAsText())
    }

    private suspend fun post(terapeutaId: String, cuerpo: JsonObject): R = llamar { tk ->
        val r = http.post(url()) {
            header("Authorization", "Bearer $tk"); parameter("terapeutaId", terapeutaId)
            contentType(ContentType.Application.Json); setBody(cuerpo.toString())
        }
        interpretar(r.status.value, r.bodyAsText())
    }

    /** ✓ Un bloque en un día. */
    suspend fun agregar(terapeutaId: String, dia: String, inicio: String, fin: String, sedeId: String?): R =
        post(terapeutaId, buildJsonObject {
            put("accion", "agregar"); put("dia", dia); put("horaInicio", inicio); put("horaFin", fin)
            if (sedeId != null) put("sedeId", sedeId)
        })

    /** "Aplicar a todos": el bloque en los 7 días (acumula, no pisa). */
    suspend fun aplicarATodos(terapeutaId: String, inicio: String, fin: String, sedeId: String?): R =
        post(terapeutaId, buildJsonObject {
            put("accion", "aplicar_todos"); put("horaInicio", inicio); put("horaFin", fin)
            if (sedeId != null) put("sedeId", sedeId)
        })

    /** ✕ de un bloque (no el último: ese va por [vaciar], con confirmación). */
    suspend fun quitar(terapeutaId: String, franjaId: String): R =
        post(terapeutaId, buildJsonObject { put("accion", "quitar"); put("id", franjaId) })

    /**
     * Deja al profesional SIN horario: `PUT { franjas: [], vaciar: true }`
     * (contrato §3.2: sin `vaciar` el servidor lo rechaza). Ante un 5xx se
     * reintenta el MISMO PUT (es idempotente).
     */
    suspend fun vaciar(terapeutaId: String): R {
        val cuerpo = buildJsonObject { put("franjas", buildJsonArray { }); put("vaciar", true) }
        var intento = 0
        while (true) {
            val r = llamar { tk ->
                val resp = http.put(url()) {
                    header("Authorization", "Bearer $tk"); parameter("terapeutaId", terapeutaId)
                    contentType(ContentType.Application.Json); setBody(cuerpo.toString())
                }
                interpretar(resp.status.value, resp.bodyAsText())
            }
            if (r is R.Error && r.status >= 500 && intento < 2) { intento++; delay(600L * intento); continue }
            return r
        }
    }

    /** Profesionales activos de la clínica (lectura directa con RLS). */
    suspend fun listar(): List<ProfesionalItem> =
        Supabase.client.postgrest["terapeutas"]
            .select(Columns.list("id, nombre, especialidad, turno, estado")) {
                order("nombre", Order.ASCENDING)
            }
            .decodeList<JsonObject>()
            .filter { (it["estado"] as? JsonPrimitive)?.contentOrNull != "Inactivo" }
            .mapNotNull { o ->
                val id = (o["id"] as? JsonPrimitive)?.contentOrNull ?: return@mapNotNull null
                ProfesionalItem(
                    id = id,
                    nombre = (o["nombre"] as? JsonPrimitive)?.contentOrNull ?: "Profesional",
                    especialidad = (o["especialidad"] as? JsonPrimitive)?.contentOrNull?.takeIf { it.isNotBlank() },
                    turno = (o["turno"] as? JsonPrimitive)?.contentOrNull?.takeIf { it.isNotBlank() },
                )
            }
}
