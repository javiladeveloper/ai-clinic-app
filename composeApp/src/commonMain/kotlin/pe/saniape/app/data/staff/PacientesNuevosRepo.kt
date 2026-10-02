package pe.saniape.app.data.staff

import io.github.jan.supabase.auth.auth
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.bodyAsText
import io.ktor.http.isSuccess
import kotlinx.datetime.LocalDate
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import pe.saniape.app.data.Supabase
import pe.saniape.app.data.crearHttpClient

// 🌱 "Pacientes nuevos": los que se registraron en un mes y hasta dónde llegaron
// (evaluación → tratamiento → pago), paciente por paciente. Gemelo de
// GET /api/staff/pacientes-nuevos de la web: el servidor arma el embudo y las
// filas; la app solo los pinta y filtra.

/** Mes del reporte (lo que devuelve el servidor en `periodo`). */
@Serializable
data class PeriodoPacientesNuevos(
    val mes: String = "",
    val desde: String = "",
    val hasta: String = "",
    /** "octubre 2026" (texto listo para mostrar). */
    val etiqueta: String = "",
)

/** Los cuatro escalones del embudo (cada uno ⊆ el anterior). */
@Serializable
data class EmbudoNuevos(
    val nuevos: Int = 0,
    val evaluados: Int = 0,
    val enTratamiento: Int = 0,
    val pagaron: Int = 0,
)

@Serializable
data class EvaluacionNuevo(
    val fecha: String = "",
    /** "atendida" | "agendada" | "no_asistio". */
    val estado: String = "",
    val profesional: String? = null,
)

@Serializable
data class TratamientoNuevo(
    val id: String = "",
    val servicio: String = "",
    val modalidad: String? = null,
    val totalSesiones: Int? = null,
    val sesionesCompletadas: Int = 0,
)

@Serializable
data class PagoNuevo(
    /** "pagado" | "parcial" | "sin_pagar". */
    val estado: String = "sin_pagar",
    /** Lo que falta (S/). null = el servidor no lo informa (no se muestra "debe"). */
    val deuda: Double? = null,
)

@Serializable
data class ProximaCitaNuevo(val fecha: String = "", val hora: String? = null)

/** Etapa a la que llegó el paciente (= el escalón del embudo en el que "se quedó"). */
object EtapaNuevo {
    const val SIN_EVALUACION = "sin_evaluacion"
    const val EVALUADO = "evaluado"
    const val EN_TRATAMIENTO = "en_tratamiento"
    const val PAGO = "pago"
}

@Serializable
data class FilaPacienteNuevo(
    val pacienteId: String = "",
    val nombre: String = "",
    val telefono: String? = null,
    val fechaRegistro: String = "",
    val sedeId: String? = null,
    val etapa: String = EtapaNuevo.SIN_EVALUACION,
    val evaluacion: EvaluacionNuevo? = null,
    val tratamiento: TratamientoNuevo? = null,
    val pago: PagoNuevo? = null,
    val proximaCita: ProximaCitaNuevo? = null,
) {
    /** ¿Ya pagó todo? (para no ofrecer WhatsApp de seguimiento al que ya cerró). */
    val pagado: Boolean get() = pago?.estado == "pagado"
}

@Serializable
data class ReportePacientesNuevos(
    val periodo: PeriodoPacientesNuevos = PeriodoPacientesNuevos(),
    val embudo: EmbudoNuevos = EmbudoNuevos(),
    val filas: List<FilaPacienteNuevo> = emptyList(),
)

sealed class ResultadoPacientesNuevos {
    data class Ok(val reporte: ReportePacientesNuevos) : ResultadoPacientesNuevos()
    /** Sin plan (402), sin permiso (403) u otro error: el mensaje ya es para mostrar. */
    data class Error(val mensaje: String, val porPlan: Boolean = false) : ResultadoPacientesNuevos()
}

private val jsonNuevos = Json { ignoreUnknownKeys = true; coerceInputValues = true }

/** Parseo del cuerpo 200 (puro: se prueba sin red). */
fun parsearPacientesNuevos(cuerpo: String): ReportePacientesNuevos =
    jsonNuevos.decodeFromString(ReportePacientesNuevos.serializer(), cuerpo)

/**
 * Mensaje para mostrar ante una respuesta que no es 200. Prioriza el texto del
 * servidor (`error` o `mensaje`); si no viene, uno por código.
 */
fun mensajeErrorPacientesNuevos(status: Int, cuerpo: String?): String {
    val o = runCatching { jsonNuevos.parseToJsonElement(cuerpo.orEmpty()) as? JsonObject }.getOrNull()
    fun campo(k: String) = (o?.get(k) as? JsonPrimitive)?.content?.takeIf { it.isNotBlank() && it != "null" }
    campo("error")?.let { return it }
    campo("mensaje")?.let { return it }
    return when {
        campo("codigo") == "PERIODO_INVALIDO" || status == 400 -> "El mes elegido no es válido."
        status == 401 -> "Tu sesión expiró. Vuelve a entrar."
        status == 402 -> "Tu plan no incluye este reporte."
        status == 403 -> "No tienes permiso para ver este reporte."
        status == 404 -> "Este reporte todavía no está disponible. Inténtalo más tarde."
        else -> "No se pudo cargar el reporte."
    }
}

/** Un mes elegible en los chips: "2026-10" + "Oct 2026". */
data class MesReporte(val id: String, val etiqueta: String)

private val MESES_CORTOS = listOf("Ene", "Feb", "Mar", "Abr", "May", "Jun", "Jul", "Ago", "Set", "Oct", "Nov", "Dic")

/**
 * Este mes y los [anteriores] previos, del más reciente al más antiguo. El
 * primero se llama "Este mes". [hoyIso] = hoy en la zona de la clínica.
 */
fun mesesRecientes(hoyIso: String, anteriores: Int = 5): List<MesReporte> {
    val hoy = runCatching { LocalDate.parse(hoyIso.take(10)) }.getOrNull() ?: return emptyList()
    var anio = hoy.year
    var mes = hoy.monthNumber
    return buildList {
        repeat(anteriores + 1) { i ->
            val id = "$anio-${mes.toString().padStart(2, '0')}"
            add(MesReporte(id, if (i == 0) "Este mes" else "${MESES_CORTOS[mes - 1]} $anio"))
            mes--
            if (mes == 0) { mes = 12; anio-- }
        }
    }
}

/** % de [actual] sobre [anterior] (redondeado); null si el anterior es 0. */
fun porcentajeEscalon(actual: Int, anterior: Int): Int? =
    if (anterior <= 0) null else kotlin.math.round(actual * 100.0 / anterior).toInt()

/** Filas que se quedaron en [etapa] (null = todas). */
fun filtrarPorEtapa(filas: List<FilaPacienteNuevo>, etapa: String?): List<FilaPacienteNuevo> =
    if (etapa == null) filas else filas.filter { it.etapa == etapa }

object PacientesNuevosRepo {

    private val http = crearHttpClient()

    /**
     * GET {SITE_URL}/api/staff/pacientes-nuevos?mes=YYYY-MM[&sede=…][&terapeuta=…]
     * con el Bearer del staff. Permiso y plan los valida el servidor. Cualquier
     * respuesta que no sea 200 (incluido el 404 mientras no esté desplegado) →
     * Error con un mensaje para mostrar; nunca lanza.
     */
    suspend fun cargar(mes: String, sedeId: String?, terapeutaId: String?): ResultadoPacientesNuevos {
        val tk = runCatching { Supabase.client.auth.currentSessionOrNull()?.accessToken }.getOrNull()
            ?: return ResultadoPacientesNuevos.Error("Tu sesión expiró. Vuelve a entrar.")
        val url = "${Supabase.SITE_URL}/api/staff/pacientes-nuevos?mes=$mes" +
            (sedeId?.takeIf { it.isNotBlank() }?.let { "&sede=$it" } ?: "") +
            (terapeutaId?.takeIf { it.isNotBlank() }?.let { "&terapeuta=$it" } ?: "")
        return try {
            val resp = http.get(url) { header("Authorization", "Bearer $tk") }
            val texto = resp.bodyAsText()
            if (resp.status.isSuccess()) {
                runCatching { ResultadoPacientesNuevos.Ok(parsearPacientesNuevos(texto)) }
                    .getOrElse { ResultadoPacientesNuevos.Error("No se pudo leer el reporte. Inténtalo más tarde.") }
            } else {
                ResultadoPacientesNuevos.Error(
                    mensajeErrorPacientesNuevos(resp.status.value, texto),
                    porPlan = resp.status.value == 402,
                )
            }
        } catch (e: kotlin.coroutines.cancellation.CancellationException) {
            throw e
        } catch (_: Exception) {
            ResultadoPacientesNuevos.Error("Sin conexión con el servidor. Revisa tu internet e inténtalo de nuevo.")
        }
    }
}
