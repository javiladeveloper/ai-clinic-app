package pe.saniape.app.data.staff

import io.github.jan.supabase.postgrest.postgrest
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import pe.saniape.app.data.Supabase
import kotlin.math.roundToLong

/**
 * 💳 MÉTODOS DE PAGO (25/11/2026): cuánto ingresó por cada método (efectivo, Yape, tarjeta…),
 * cada uno por separado, por día, semana o mes. Gemelo de `lib/metodos-pago-reporte.ts` (web).
 * Las sumas las hace la base: RPC `ingresos_por_metodo(p_desde, p_hasta, p_agrupar, p_sede)`.
 */

enum class AgruparMetodos(val clave: String, val etiqueta: String) { DIA("dia", "Día"), SEMANA("semana", "Semana"), MES("mes", "Mes") }

data class FilaMetodo(val periodo: String, val metodo: String, val cobros: Int, val total: Double)

data class TotalMetodo(val total: Double, val cobros: Int, val porcentaje: Double)

data class MatrizMetodos(
    /** Métodos de mayor a menor total («Sin método» al final). */
    val metodos: List<String>,
    /** Periodos en orden (YYYY-MM-DD). */
    val periodos: List<String>,
    val celdas: Map<String, Map<String, Double>>,
    val porMetodo: Map<String, TotalMetodo>,
    val totalPeriodo: Map<String, Double>,
    val total: Double,
    val cobros: Int,
)

const val SIN_METODO = "Sin método"
private val FECHA_METODO = Regex("""^\d{4}-\d{2}-\d{2}""")
private fun redondear(n: Double) = (n * 100).roundToLong() / 100.0

/** Lo que devuelve la RPC, saneado. */
fun filasMetodos(data: JsonElement?): List<FilaMetodo> =
    (data as? JsonArray).orEmpty().mapNotNull { it as? JsonObject }.mapNotNull { d ->
        fun t(k: String) = (d[k] as? JsonPrimitive)?.content?.takeIf { it != "null" }
        val periodo = t("periodo")?.take(10)?.takeIf { FECHA_METODO.matches(it) } ?: return@mapNotNull null
        FilaMetodo(periodo, t("metodo")?.trim()?.takeIf { it.isNotEmpty() } ?: SIN_METODO,
            t("cobros")?.toDoubleOrNull()?.toInt() ?: 0, t("total")?.toDoubleOrNull() ?: 0.0)
    }

fun matrizMetodos(filas: List<FilaMetodo>): MatrizMetodos {
    val celdas = LinkedHashMap<String, MutableMap<String, Double>>()
    val tot = LinkedHashMap<String, Pair<Double, Int>>()
    val totalPeriodo = LinkedHashMap<String, Double>()
    var total = 0.0; var cobros = 0
    filas.forEach { f ->
        val c = celdas.getOrPut(f.periodo) { LinkedHashMap() }
        c[f.metodo] = redondear((c[f.metodo] ?: 0.0) + f.total)
        val (t0, n0) = tot[f.metodo] ?: (0.0 to 0)
        tot[f.metodo] = redondear(t0 + f.total) to (n0 + f.cobros)
        totalPeriodo[f.periodo] = redondear((totalPeriodo[f.periodo] ?: 0.0) + f.total)
        total = redondear(total + f.total); cobros += f.cobros
    }
    val porMetodo = tot.mapValues { (_, v) ->
        TotalMetodo(v.first, v.second, if (total > 0) (v.first / total * 1000).roundToLong() / 10.0 else 0.0)
    }
    val metodos = porMetodo.keys.sortedWith(
        compareBy<String> { if (it == SIN_METODO) 1 else 0 }.thenByDescending { porMetodo.getValue(it).total }.thenBy { it },
    )
    return MatrizMetodos(metodos, celdas.keys.sorted(), celdas, porMetodo, totalPeriodo, total, cobros)
}

private val MESES_CORTOS_MP = listOf("ene", "feb", "mar", "abr", "may", "jun", "jul", "ago", "sep", "oct", "nov", "dic")
private val MESES_LARGOS_MP = listOf("Enero", "Febrero", "Marzo", "Abril", "Mayo", "Junio", "Julio", "Agosto", "Septiembre", "Octubre", "Noviembre", "Diciembre")

/** «25 nov 2026», «Semana del 24 nov 2026», «Noviembre 2026» (como la web). */
fun etiquetaPeriodoMetodo(periodo: String, agrupar: AgruparMetodos): String {
    val p = periodo.split('-').mapNotNull { it.toIntOrNull() }
    if (p.size < 3) return periodo
    val (a, m, d) = Triple(p[0], p[1], p[2])
    return when (agrupar) {
        AgruparMetodos.MES -> "${MESES_LARGOS_MP.getOrElse(m - 1) { "" }} $a"
        AgruparMetodos.SEMANA -> "Semana del $d ${MESES_CORTOS_MP.getOrElse(m - 1) { "" }} $a"
        AgruparMetodos.DIA -> "$d ${MESES_CORTOS_MP.getOrElse(m - 1) { "" }} $a"
    }
}

private fun numCsv(n: Double): String = if (n == n.toLong().toDouble()) n.toLong().toString() else n.toString()
private fun celdaCsv(s: String): String = if (s.any { it == ',' || it == '"' || it == '\n' || it == ';' }) "\"" + s.replace("\"", "\"\"") + "\"" else s

/** El Excel (CSV) del resumen: una fila por periodo, una columna por método y el total; al final TOTAL, % y Nº de cobros. */
fun csvMetodos(m: MatrizMetodos, agrupar: AgruparMetodos): String = buildString {
    appendLine((listOf(agrupar.etiqueta) + m.metodos + "Total").joinToString(",") { celdaCsv(it) })
    m.periodos.forEach { p ->
        appendLine((listOf(etiquetaPeriodoMetodo(p, agrupar)) + m.metodos.map { numCsv(m.celdas[p]?.get(it) ?: 0.0) } + numCsv(m.totalPeriodo[p] ?: 0.0))
            .joinToString(",") { celdaCsv(it) })
    }
    if (m.periodos.isNotEmpty()) {
        appendLine((listOf("TOTAL") + m.metodos.map { numCsv(m.porMetodo.getValue(it).total) } + numCsv(m.total)).joinToString(",") { celdaCsv(it) })
        appendLine((listOf("% del total") + m.metodos.map { "${m.porMetodo.getValue(it).porcentaje}%" } + "100%").joinToString(",") { celdaCsv(it) })
        appendLine((listOf("Nº de cobros") + m.metodos.map { m.porMetodo.getValue(it).cobros.toString() } + m.cobros.toString()).joinToString(",") { celdaCsv(it) })
    }
}

private fun esc(s: String) = s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")

/** La hoja para imprimir / guardar en PDF (la misma tabla que el Excel). */
fun htmlMetodos(m: MatrizMetodos, agrupar: AgruparMetodos, titulo: String, subtitulo: String, soles: (Double) -> String): String = buildString {
    append("<!doctype html><html><head><meta charset='utf-8'><meta name='viewport' content='width=device-width,initial-scale=1'>")
    append("<title>${esc(titulo)}</title><style>body{font-family:sans-serif;margin:24px;color:#1e2d5e}h1{font-size:18px;margin:0}")
    append("p{color:#6b7280;font-size:12px;margin:4px 0 12px}table{border-collapse:collapse;width:100%;font-size:11px}")
    append("th,td{border:1px solid #dde1f0;padding:5px 6px;text-align:right}th:first-child,td:first-child{text-align:left}")
    append("thead th{background:#eef0f9}tr.t td{font-weight:bold;background:#f7f8fc}</style></head><body>")
    append("<h1>${esc(titulo)}</h1><p>${esc(subtitulo)}</p><table><thead><tr><th>${agrupar.etiqueta}</th>")
    m.metodos.forEach { append("<th>${esc(it)}</th>") }
    append("<th>Total</th></tr></thead><tbody>")
    m.periodos.forEach { p ->
        append("<tr><td>${esc(etiquetaPeriodoMetodo(p, agrupar))}</td>")
        m.metodos.forEach { append("<td>${soles(m.celdas[p]?.get(it) ?: 0.0)}</td>") }
        append("<td><b>${soles(m.totalPeriodo[p] ?: 0.0)}</b></td></tr>")
    }
    append("<tr class='t'><td>TOTAL</td>"); m.metodos.forEach { append("<td>${soles(m.porMetodo.getValue(it).total)}</td>") }; append("<td>${soles(m.total)}</td></tr>")
    append("<tr class='t'><td>% del total</td>"); m.metodos.forEach { append("<td>${m.porMetodo.getValue(it).porcentaje}%</td>") }; append("<td>100%</td></tr>")
    append("<tr class='t'><td>Nº de cobros</td>"); m.metodos.forEach { append("<td>${m.porMetodo.getValue(it).cobros}</td>") }; append("<td>${m.cobros}</td></tr>")
    append("</tbody></table></body></html>")
}

object MetodosPagoRepo {
    /** null = no se pudo leer. Sin sede elegida va null (nunca "": la base lo rechaza). */
    suspend fun leer(p: Periodo, agrupar: AgruparMetodos, sedeId: String?): List<FilaMetodo>? = try {
        filasMetodos(Supabase.client.postgrest.rpc("ingresos_por_metodo", buildJsonObject {
            put("p_desde", p.desde); put("p_hasta", p.hasta); put("p_agrupar", agrupar.clave)
            val sede = sedeId?.takeIf { it.isNotBlank() }
            if (sede != null) put("p_sede", sede) else put("p_sede", JsonNull)
        }).decodeAs<JsonElement>())
    } catch (e: CancellationException) { throw e } catch (_: Exception) { null }
}
