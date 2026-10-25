package pe.saniape.app.data.staff

import io.github.jan.supabase.auth.auth
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.contentType
import kotlinx.datetime.LocalDate
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.put
import pe.saniape.app.data.Supabase
import pe.saniape.app.data.crearHttpClient

/**
 * PRÓXIMA CITA PROPUESTA y PLAN ENTERO (2026-10-24) — la app NO calcula fechas:
 * la regla única vive en la web (lib/proxima-cita.ts) y llega por
 *  - GET  /api/staff/cita/proxima   (al cerrar una atención, «🔁 Repetir» y
 *    «Agendar sesión» del tratamiento);
 *  - POST /api/staff/sesion/plan    («Agendar las N restantes»: previsualizar /
 *    agendar; si una falla, las demás se guardan).
 * Antes la app proponía +7 días fijos (Repetir) y la web +intervalo o «hoy»:
 * cuatro reglas para lo mismo. Aquí solo se lee, se muestra y se manda.
 */

/** El cuerpo de /api/staff/cita/crear que el chip manda tal cual. */
data class CitaPropuesta(
    val pacienteId: String,
    val tipo: String,
    val fecha: String,
    val hora: String,
    val terapeutaId: String?,
    val tratamientoId: String?,
    val especialidadId: String?,
    val sedeId: String?,
    val costo: Double,
    val duracion: Int,
    val citaOrigenId: String?,
)

data class PropuestaProxima(
    val fecha: String,
    val hora: String,
    val terapeutaId: String?,
    val terapeutaNombre: String?,
    val tipo: String,
    /** 'control' | 'intervalo' | 'servicio' | 'protocolo' | 'defecto'. */
    val origen: String,
    val dias: Int,
    /** «cada 4 días, como viene». */
    val porQue: String,
    val horaCambiada: Boolean,
    val corridaPor: String?,
    /** «mié 4 nov · 10:00 a. m. con Lic. Ana». */
    val etiqueta: String,
    val verificada: Boolean,
    val cita: CitaPropuesta,
    val pacienteNombre: String?,
    /** Sesiones que faltan agendar del plan (total − vivas); null si no declara sesiones. */
    val faltanPlan: Int? = null,
)

data class RespuestaProxima(val propuesta: PropuestaProxima?, val puedeAgendar: Boolean, val motivo: String?)

private fun JsonObject.s(k: String): String? = when (val v = this[k]) {
    is JsonPrimitive -> v.contentOrNull?.takeIf { it != "null" }
    else -> null
}
private fun JsonObject.b(k: String): Boolean = s(k) == "true"
private fun JsonObject.o(k: String): JsonObject? = this[k] as? JsonObject

/** Lee la respuesta de GET /api/staff/cita/proxima. null = no se entendió. */
fun parsearRespuestaProxima(o: JsonObject?): RespuestaProxima? {
    if (o == null) return null
    val p = o.o("propuesta")
    val c = p?.o("cita")
    val propuesta = if (p != null && c != null) {
        val fecha = p.s("fecha"); val hora = p.s("hora"); val pac = c.s("pacienteId")
        if (fecha == null || hora == null || pac == null) null else PropuestaProxima(
            fecha = fecha, hora = hora.take(5),
            terapeutaId = p.s("terapeutaId"), terapeutaNombre = p.s("terapeutaNombre"),
            tipo = p.s("tipo") ?: "Sesión", origen = p.s("origen") ?: "defecto",
            dias = p.s("dias")?.toDoubleOrNull()?.toInt() ?: 7, porQue = p.s("porQue") ?: "",
            horaCambiada = p.b("horaCambiada"), corridaPor = p.s("corridaPor"),
            etiqueta = p.s("etiqueta") ?: "$fecha ${hora.take(5)}", verificada = p.b("verificada"),
            cita = CitaPropuesta(
                pacienteId = pac, tipo = c.s("tipo") ?: p.s("tipo") ?: "Sesión",
                fecha = c.s("fecha") ?: fecha, hora = (c.s("hora") ?: hora).take(5),
                terapeutaId = c.s("terapeutaId"), tratamientoId = c.s("tratamientoId"),
                especialidadId = c.s("especialidadId"), sedeId = c.s("sedeId"),
                costo = c.s("costo")?.toDoubleOrNull() ?: 0.0,
                duracion = c.s("duracion")?.toDoubleOrNull()?.toInt() ?: 45,
                citaOrigenId = c.s("citaOrigenId"),
            ),
            pacienteNombre = p.o("paciente")?.s("nombre"),
            faltanPlan = p.o("tratamiento")?.let { t ->
                val total = t.s("totalSesiones")?.toDoubleOrNull()?.toInt() ?: 0
                val vivas = t.s("vivas")?.toDoubleOrNull()?.toInt() ?: 0
                if (total > 0) maxOf(0, total - vivas) else null
            },
        )
    } else null
    return RespuestaProxima(propuesta, o.b("puedeAgendar"), o.s("motivo"))
}

/** La segunda línea del chip (la MISMA frase que la web, components/citas/proxima-cita-oferta.ts). */
fun detallePropuesta(p: PropuestaProxima): String = when {
    p.corridaPor != null -> "${p.corridaPor}: se corrió la fecha"
    p.horaCambiada -> "A la hora de siempre ya no hay espacio: es la primera libre"
    else -> "Sugerido ${p.porQue}"
}

/** El cuerpo para /api/staff/cita/crear. */
fun cuerpoCitaPropuesta(c: CitaPropuesta): JsonObject = buildJsonObject {
    put("pacienteId", c.pacienteId)
    put("tipo", c.tipo)
    put("fecha", c.fecha)
    put("hora", c.hora)
    c.terapeutaId?.let { put("terapeutaId", it) }
    c.tratamientoId?.let { put("tratamientoId", it) }
    c.especialidadId?.let { put("especialidadId", it) }
    c.sedeId?.let { put("sedeId", it) }
    put("costo", c.costo)
    put("duracion", c.duracion)
    c.citaOrigenId?.let { put("citaOrigenId", it) }
}

// ── Plan entero ──────────────────────────────────────────────────────────────

data class ConflictoPlan(val codigo: String, val mensaje: String)
data class FilaPlanSesion(val fecha: String, val hora: String, val conflictos: List<ConflictoPlan>, val incluida: Boolean)
data class SesionNoAgendada(val fecha: String, val hora: String, val error: String)
data class ResultadoPlan(
    val creadas: Int,
    val fallidas: List<SesionNoAgendada>,
    val error: String?,
    /** Filas que ya estaban agendadas por un envío anterior: el servidor no las duplicó. */
    val yaExistian: Int = 0,
)

/**
 * UNA clave de idempotencia por envío del plan (gemelo de firmaEnvioPlan de la
 * web, lib/plan-idempotente.ts): repetir el MISMO envío (se perdió la respuesta)
 * reusa la clave y el servidor devuelve lo mismo sin crear nada; cambiar la
 * lista (marcar o quitar una fecha) es otro envío y lleva otra.
 */
class ClaveEnvioPlan(private val nueva: () -> String = { pe.saniape.app.data.offline.nuevaIdemKey() }) {
    private var firma: String? = null
    private var clave: String = ""
    fun para(tratamientoId: String, filas: List<FilaPlanSesion>): String {
        val f = firmaEnvioPlan(tratamientoId, filas)
        if (f != firma) { firma = f; clave = nueva() }
        return clave
    }
}

/** "tr|2026-11-03 10:00,2026-11-05 10:00" — solo las marcadas, ordenadas (igual que la web). */
fun firmaEnvioPlan(tratamientoId: String, filas: List<FilaPlanSesion>): String =
    tratamientoId + "|" + filas.filter { it.incluida }.map { "${it.fecha.take(10)} ${it.hora.take(5)}" }.sorted().joinToString(",")

/** Chips de días, lunes primero: (día 0=dom…6=sáb, letra). Gemelo de CHIPS_DIAS (lib/proxima-cita). */
val CHIPS_DIAS_PLAN: List<Pair<Int, String>> = listOf(1 to "L", 2 to "M", 3 to "X", 4 to "J", 5 to "V", 6 to "S", 0 to "D")

/** 0 = domingo … 6 = sábado (como Date.getDay de la web). */
fun diaSemanaPlan(fecha: String): Int = runCatching { (LocalDate.parse(fecha.take(10)).dayOfWeek.ordinal + 1) % 7 }.getOrDefault(1)

fun parsearFilasPlan(o: JsonObject?): List<FilaPlanSesion> = (o?.get("filas") as? JsonArray).orEmpty().mapNotNull { e ->
    val f = e as? JsonObject ?: return@mapNotNull null
    FilaPlanSesion(
        fecha = f.s("fecha") ?: return@mapNotNull null,
        hora = (f.s("hora") ?: return@mapNotNull null).take(5),
        conflictos = (f["conflictos"] as? JsonArray).orEmpty().mapNotNull { c ->
            (c as? JsonObject)?.let { ConflictoPlan(it.s("codigo") ?: "", it.s("mensaje") ?: "") }
        },
        incluida = f.b("incluida"),
    )
}

fun parsearResultadoPlan(o: JsonObject?, error: String? = null): ResultadoPlan = ResultadoPlan(
    creadas = o?.s("creadas")?.toDoubleOrNull()?.toInt() ?: 0,
    yaExistian = (o?.get("yaExistian") as? JsonArray)?.size ?: 0,
    fallidas = (o?.get("fallidas") as? JsonArray).orEmpty().mapNotNull { e ->
        val f = e as? JsonObject ?: return@mapNotNull null
        SesionNoAgendada(f.s("fecha") ?: return@mapNotNull null, (f.s("hora") ?: "").take(5), f.s("error") ?: "No se pudo agendar")
    },
    error = error ?: o?.s("error"),
)

/** «mié 04/11 10:00 — Ya no hay cupo a esa hora» por línea (el aviso de las que faltaron). */
fun textoFallidas(fallidas: List<SesionNoAgendada>): String =
    fallidas.joinToString("\n") { "${fechaLegibleCorta(it.fecha)} ${it.hora} — ${it.error}" }

object ProximaCitaRepo {
    private val http = crearHttpClient()
    private val json = Json { ignoreUnknownKeys = true }
    private suspend fun token(): String? = Supabase.client.auth.currentSessionOrNull()?.accessToken

    /** [modo]: "ofrecer" (al cerrar), "repetir" o "agendar". null = sin red o sin respuesta. */
    suspend fun proponer(citaId: String? = null, tratamientoId: String? = null, modo: String = "ofrecer"): RespuestaProxima? {
        val tk = token() ?: return null
        if (citaId == null && tratamientoId == null) return null
        return try {
            val resp = http.get("${Supabase.SITE_URL}/api/staff/cita/proxima") {
                header("Authorization", "Bearer $tk")
                citaId?.let { parameter("citaId", it) }
                tratamientoId?.let { parameter("tratamientoId", it) }
                if (modo != "ofrecer") parameter("modo", modo)
            }
            if (resp.status.value !in 200..299) null
            else parsearRespuestaProxima(runCatching { json.parseToJsonElement(resp.bodyAsText()) as? JsonObject }.getOrNull())
        } catch (e: kotlin.coroutines.cancellation.CancellationException) {
            throw e
        } catch (_: Exception) {
            null // es solo una propuesta
        }
    }

    /** Agenda la propuesta tal cual (el camino de siempre). Directo, sin cola: es un toque que espera respuesta. */
    suspend fun agendar(p: PropuestaProxima): Pair<Boolean, String?> {
        val (res, rechazo, cuerpo) = try {
            pe.saniape.app.data.offline.Sincronizador.enviarAhoraCompleto(
                "/api/staff/cita/crear", cuerpoCitaPropuesta(p.cita), pe.saniape.app.data.offline.nuevaIdemKey(),
            )
        } catch (e: kotlin.coroutines.cancellation.CancellationException) {
            throw e
        } catch (_: Exception) {
            return false to "Sin conexión: no se pudo agendar"
        }
        return when (res) {
            pe.saniape.app.data.offline.ResultadoEnvio.OK -> true to cuerpo?.s("avisoAusencia")
            pe.saniape.app.data.offline.ResultadoEnvio.SIN_RED -> false to "Sin conexión: no se pudo agendar"
            pe.saniape.app.data.offline.ResultadoEnvio.RECHAZADO -> false to (rechazo?.error ?: "No se pudo agendar")
        }
    }

    sealed class Previa {
        data class Ok(val filas: List<FilaPlanSesion>) : Previa()
        data class Error(val mensaje: String) : Previa()
    }

    suspend fun previsualizarPlan(
        tratamientoId: String, terapeutaId: String?, hora: String, desde: String, dias: List<Int>, cantidad: Int, sedeId: String? = null,
    ): Previa {
        val tk = token() ?: return Previa.Error("Tu sesión expiró. Vuelve a entrar.")
        val cuerpo = buildJsonObject {
            put("accion", "previsualizar")
            put("tratamientoId", tratamientoId)
            terapeutaId?.let { put("terapeutaId", it) }
            sedeId?.let { put("sedeId", it) }
            put("hora", hora); put("desde", desde); put("cantidad", cantidad)
            put("dias", buildJsonArray { dias.forEach { add(JsonPrimitive(it)) } })
        }
        return try {
            val resp = http.post("${Supabase.SITE_URL}/api/staff/sesion/plan") {
                header("Authorization", "Bearer $tk")
                contentType(ContentType.Application.Json)
                setBody(cuerpo.toString())
            }
            val o = runCatching { json.parseToJsonElement(resp.bodyAsText()) as? JsonObject }.getOrNull()
            if (resp.status.value !in 200..299) Previa.Error(o?.s("error") ?: "No se pudieron calcular las fechas")
            else Previa.Ok(parsearFilasPlan(o))
        } catch (e: kotlin.coroutines.cancellation.CancellationException) {
            throw e
        } catch (_: Exception) {
            Previa.Error("Sin conexión: no se pudieron calcular las fechas.")
        }
    }

    /** Guarda las elegidas. Directo, sin cola (son varias citas: quien agenda tiene que ver cuáles quedaron). */
    suspend fun agendarPlan(
        tratamientoId: String, terapeutaId: String?, filas: List<FilaPlanSesion>, idemKey: String, sedeId: String? = null,
    ): ResultadoPlan {
        val cuerpo = buildJsonObject {
            put("accion", "agendar")
            put("tratamientoId", tratamientoId)
            terapeutaId?.let { put("terapeutaId", it) }
            sedeId?.let { put("sedeId", it) }
            put("filas", buildJsonArray {
                filas.forEach { f -> add(buildJsonObject { put("fecha", f.fecha); put("hora", f.hora) }) }
            })
        }
        val (res, rechazo, resp) = try {
            pe.saniape.app.data.offline.Sincronizador.enviarAhoraCompleto(
                "/api/staff/sesion/plan", cuerpo, idemKey,
            )
        } catch (e: kotlin.coroutines.cancellation.CancellationException) {
            throw e
        } catch (_: Exception) {
            return ResultadoPlan(0, emptyList(), "Sin conexión: revisa la agenda antes de reintentar.")
        }
        return when (res) {
            pe.saniape.app.data.offline.ResultadoEnvio.OK -> parsearResultadoPlan(resp)
            pe.saniape.app.data.offline.ResultadoEnvio.SIN_RED -> ResultadoPlan(0, emptyList(), "Sin conexión: revisa la agenda antes de reintentar.")
            pe.saniape.app.data.offline.ResultadoEnvio.RECHAZADO -> parsearResultadoPlan(rechazo?.datos, rechazo?.error ?: "No se agendó ninguna sesión")
        }
    }
}
