package pe.saniape.app.data.staff

import io.github.jan.supabase.auth.auth
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.contentType
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put
import pe.saniape.app.data.Supabase
import pe.saniape.app.data.crearHttpClient

/**
 * 💵 PLANILLA en la app (26/11/2026): el Admin ve la planilla del periodo, la arma con la
 * asistencia y PAGA desde el celular. Los sueldos, los adelantos y la revisión de cada
 * descuento se hacen en la web. Todo el cálculo es del servidor (/api/staff/planilla).
 */

/** Una fila de la planilla tal como la manda el servidor. */
data class FilaPlanillaApp(
    val id: String, val nombre: String, val cargo: String?,
    val sueldoBase: Double, val diasTrabajados: Int, val faltas: Int, val tardanzaMin: Int,
    val descuentos: Double, val bonos: Double, val adelantos: Double, val neto: Double,
    val pagado: Boolean, val metodo: String?, val nota: String?,
)

data class PlanillaApp(
    /** «Noviembre 2026». */
    val periodo: String,
    val filas: List<FilaPlanillaApp>,
    /** Personas activas con sueldo cargado (0 = todavía no se puede armar). */
    val conSueldo: Int,
) {
    val pendientes: List<FilaPlanillaApp> get() = filas.filter { !it.pagado }
    val totalNeto: Double get() = filas.sumOf { it.neto }
    val totalPagado: Double get() = filas.filter { it.pagado }.sumOf { it.neto }
    val totalPendiente: Double get() = pendientes.sumOf { it.neto }
}

data class ResultadoPlanilla(val ok: Boolean, val mensaje: String)

/** «mensual» | «quincena1» | «quincena2», con su etiqueta corta. */
val TIPOS_PERIODO_PLANILLA = listOf("mensual" to "Mes", "quincena1" to "1.ª quincena", "quincena2" to "2.ª quincena")

private fun JsonObject.txt(k: String): String? = (this[k] as? JsonPrimitive)?.contentOrNull?.takeIf { it != "null" }
private fun JsonObject.dbl(k: String): Double = (this[k] as? JsonPrimitive)?.doubleOrNull ?: 0.0
private fun JsonObject.ent(k: String): Int = (this[k] as? JsonPrimitive)?.intOrNull ?: 0

/** Parseo tolerante de GET /api/staff/planilla. Puro. */
fun parsearPlanilla(o: JsonObject): PlanillaApp = PlanillaApp(
    periodo = o.txt("periodo") ?: "",
    filas = (o["filas"] as? JsonArray).orEmpty().mapNotNull {
        val f = it as? JsonObject ?: return@mapNotNull null
        FilaPlanillaApp(
            id = f.txt("id") ?: return@mapNotNull null, nombre = f.txt("nombre") ?: "—", cargo = f.txt("cargo"),
            sueldoBase = f.dbl("sueldo_base"), diasTrabajados = f.ent("dias_trabajados"), faltas = f.ent("faltas"), tardanzaMin = f.ent("tardanza_min"),
            descuentos = f.dbl("desc_faltas") + f.dbl("desc_tardanzas") + f.dbl("otros_descuentos"),
            bonos = f.dbl("bonos"), adelantos = f.dbl("adelantos"), neto = f.dbl("neto"),
            pagado = f.txt("estado") == "Pagado", metodo = f.txt("metodo_pago"), nota = f.txt("nota"),
        )
    },
    conSueldo = (o["personas"] as? JsonArray).orEmpty().count {
        val p = it as? JsonObject
        p != null && p.txt("estado") == "Activo" && p.txt("sueldo") != null
    },
)

/** «2026-11» corrido n meses. Puro. */
fun mesCorrido(mes: String, n: Int): String {
    val a = mes.take(4).toIntOrNull() ?: return mes
    val m = mes.drop(5).take(2).toIntOrNull() ?: return mes
    val total = a * 12 + (m - 1) + n
    return "${total / 12}-${(total % 12 + 1).toString().padStart(2, '0')}"
}

/** Lo que resume la asistencia de una fila: «20 días · 1 falta · 45 min tarde». Puro. */
fun asistenciaDeFila(f: FilaPlanillaApp): String = listOfNotNull(
    "${f.diasTrabajados} día" + if (f.diasTrabajados == 1) "" else "s",
    if (f.faltas > 0) "${f.faltas} falta" + (if (f.faltas == 1) "" else "s") else null,
    if (f.tardanzaMin > 0) "${f.tardanzaMin} min tarde" else null,
).joinToString(" · ")

object PlanillaRepo {
    private val json = Json { ignoreUnknownKeys = true }
    private val http = crearHttpClient()

    private suspend fun token(): String? = Supabase.client.auth.currentSessionOrNull()?.accessToken

    private suspend fun leer(resp: HttpResponse): Pair<Boolean, JsonObject?> {
        val obj = runCatching { json.parseToJsonElement(resp.bodyAsText()).jsonObject }.getOrNull()
        return (resp.status.value in 200..299) to obj
    }

    /** La planilla del periodo, o el motivo por el que no se pudo leer. */
    suspend fun planilla(mes: String, tipo: String): Pair<PlanillaApp?, String?> {
        val tk = token() ?: return null to "Tu sesión expiró. Vuelve a entrar."
        return try {
            val (ok, obj) = leer(http.get("${Supabase.SITE_URL}/api/staff/planilla?mes=$mes&tipo=$tipo") { header("Authorization", "Bearer $tk") })
            if (ok && obj != null) parsearPlanilla(obj) to null else null to (obj?.txt("error") ?: "No se pudo leer la planilla.")
        } catch (e: kotlin.coroutines.cancellation.CancellationException) {
            throw e
        } catch (_: Exception) {
            null to "Sin conexión. Revisa tu internet."
        }
    }

    private suspend fun accion(cuerpo: JsonObject, listo: (JsonObject?) -> String): ResultadoPlanilla {
        val tk = token() ?: return ResultadoPlanilla(false, "Tu sesión expiró. Vuelve a entrar.")
        return try {
            val (ok, obj) = leer(http.post("${Supabase.SITE_URL}/api/staff/planilla") {
                header("Authorization", "Bearer $tk"); contentType(ContentType.Application.Json); setBody(cuerpo.toString())
            })
            if (ok) ResultadoPlanilla(true, listo(obj)) else ResultadoPlanilla(false, obj?.txt("error")?.takeIf { it.isNotBlank() } ?: "No se pudo guardar.")
        } catch (e: kotlin.coroutines.cancellation.CancellationException) {
            throw e
        } catch (_: Exception) {
            // Sin respuesta: el pago PUDO haber entrado. No se reintenta solo.
            ResultadoPlanilla(false, "Sin conexión. Actualiza la planilla antes de volver a intentarlo.")
        }
    }

    /** Arma o actualiza la planilla con la asistencia del periodo. */
    suspend fun armar(mes: String, tipo: String): ResultadoPlanilla =
        accion(buildJsonObject { put("accion", "armar"); put("mes", mes); put("tipo", tipo) }) { o ->
            val avisos = (o?.get("avisos") as? JsonArray).orEmpty().mapNotNull { (it as? JsonPrimitive)?.contentOrNull }
            if (avisos.isNotEmpty()) avisos.first() else "Planilla actualizada con la asistencia"
        }

    /** Registra el pago de una fila (el egreso entra solo a Finanzas). */
    suspend fun pagar(id: String, metodo: String): ResultadoPlanilla =
        accion(buildJsonObject { put("accion", "pagar"); put("id", id); put("metodo", metodo) }) { "Pago registrado" }
}
