package pe.saniape.app.data.staff

import io.github.jan.supabase.auth.auth
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.bodyAsText
import io.ktor.http.isSuccess
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import pe.saniape.app.data.Supabase
import pe.saniape.app.data.crearHttpClient

/**
 * "Pacientes del período" (embudo evaluados → paquete). Espejo de
 * `EmbudoPacientes` en lib/metricas.ts de la web: los números los calcula la
 * base (RPC metricas_embudo_pacientes) y la ruta /api/reportes/rendimiento los
 * entrega ya armados. La app solo los muestra.
 */
data class EmbudoPacientes(
    /** Pacientes distintos con al menos una cita completada en el período. */
    val atendidos: Int,
    /** De esos, los que vinieron a Evaluación (la Consulta cuenta si ES la evaluación). */
    val evaluados: Int,
    /** Evaluados que pagaron (algo de) un paquete dentro del período. */
    val compraron: Int,
    /** Evaluados con paquete abierto en el período, sin pago aún. */
    val pendientes: Int,
    /** Evaluados que ya tenían un paquete pagado de antes (control). */
    val yaTenian: Int,
    /** Evaluados que se fueron sin paquete. */
    val sinPaquete: Int,
    /** Atendidos que no se evaluaron pero vinieron a una Consulta. */
    val soloConsulta: Int,
    /** Atendidos que solo vinieron a sesiones de un paquete anterior. */
    val soloSesiones: Int,
    /** Evaluaciones ya pasadas que nadie cerró (no cuentan como evaluados). */
    val evalSinCerrar: Int,
    /** compraron / evaluados en %; null sin evaluados. */
    val tasaCompra: Int?,
)

/** Período del reporte (los mismos cuatro de la web: OPCIONES_PERIODO). */
enum class PeriodoReporte(val id: String, val etiqueta: String) {
    MES("mes", "Este mes"),
    MES_ANTERIOR("mes_anterior", "Mes anterior"),
    TRES_MESES("3m", "Últimos 3 meses"),
    SEIS_MESES("6m", "Últimos 6 meses"),
}

/** Lo que la pantalla necesita de la respuesta. */
data class ReportePacientesPeriodo(
    /** null = el servidor todavía no calcula el embudo (RPC ausente). */
    val embudo: EmbudoPacientes?,
    /** "octubre 2026", "últimos 3 meses"… (periodo.etiqueta de la web). */
    val etiquetaPeriodo: String?,
)

sealed class ResultadoReporte {
    data class Ok(val reporte: ReportePacientesPeriodo) : ResultadoReporte()
    /** Sin plan (402), sin permiso (403) u otro error: el mensaje ya es para mostrar. */
    data class Error(val mensaje: String, val porPlan: Boolean = false) : ResultadoReporte()
}

private fun JsonObject.entero(k: String): Int =
    (this[k] as? JsonPrimitive)?.content?.toDoubleOrNull()?.toInt() ?: 0

private fun JsonObject.enteroONull(k: String): Int? =
    (this[k] as? JsonPrimitive)?.content?.takeIf { it != "null" }?.toDoubleOrNull()?.toInt()

/** Lee el bloque `embudo` (camelCase, como lo arma la ruta). null si no viene. */
fun parsearEmbudo(o: JsonObject?): EmbudoPacientes? {
    if (o == null) return null
    return EmbudoPacientes(
        atendidos = o.entero("atendidos"),
        evaluados = o.entero("evaluados"),
        compraron = o.entero("compraron"),
        pendientes = o.entero("pendientes"),
        yaTenian = o.entero("yaTenian"),
        sinPaquete = o.entero("sinPaquete"),
        soloConsulta = o.entero("soloConsulta"),
        soloSesiones = o.entero("soloSesiones"),
        evalSinCerrar = o.entero("evalSinCerrar"),
        tasaCompra = o.enteroONull("tasaCompra"),
    )
}

/** Respuesta completa de /api/reportes/rendimiento → lo que pinta "Pacientes del período". */
fun parsearReportePacientes(cuerpo: String): ReportePacientesPeriodo {
    val o = Json.parseToJsonElement(cuerpo).jsonObject
    return ReportePacientesPeriodo(
        embudo = parsearEmbudo(o["embudo"] as? JsonObject),
        etiquetaPeriodo = ((o["periodo"] as? JsonObject)?.get("etiqueta") as? JsonPrimitive)
            ?.content?.takeIf { it != "null" && it.isNotBlank() },
    )
}

object ReportesRepo {

    private val http = crearHttpClient()

    /**
     * GET {SITE_URL}/api/reportes/rendimiento?periodo=…[&sede=…] con el Bearer del
     * staff. El permiso `reportes` y el plan los valida el servidor: si el plan no
     * incluye reportes responde 402 con el mensaje que se muestra tal cual.
     */
    suspend fun pacientesDelPeriodo(periodo: PeriodoReporte, sedeId: String?): ResultadoReporte {
        val tk = runCatching { Supabase.client.auth.currentSessionOrNull()?.accessToken }.getOrNull()
            ?: return ResultadoReporte.Error("Tu sesión expiró. Vuelve a entrar.")
        val url = "${Supabase.SITE_URL}/api/reportes/rendimiento?periodo=${periodo.id}" +
            (sedeId?.takeIf { it.isNotBlank() }?.let { "&sede=$it" } ?: "")
        return try {
            val resp = http.get(url) { header("Authorization", "Bearer $tk") }
            val texto = resp.bodyAsText()
            if (resp.status.isSuccess()) {
                ResultadoReporte.Ok(parsearReportePacientes(texto))
            } else {
                val msg = runCatching {
                    ((Json.parseToJsonElement(texto) as? JsonObject)?.get("error") as? JsonPrimitive)?.content
                }.getOrNull()?.takeIf { it.isNotBlank() }
                ResultadoReporte.Error(
                    msg ?: "No se pudo cargar el reporte.",
                    porPlan = resp.status.value == 402,
                )
            }
        } catch (e: kotlin.coroutines.cancellation.CancellationException) {
            throw e
        } catch (_: Exception) {
            ResultadoReporte.Error("Sin conexión con el servidor. Revisa tu internet e inténtalo de nuevo.")
        }
    }
}
