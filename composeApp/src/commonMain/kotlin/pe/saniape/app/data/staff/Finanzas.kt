package pe.saniape.app.data.staff

import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.isoDayNumber
import kotlinx.datetime.minus
import kotlinx.datetime.plus

/**
 * Lógica PURA de "Finanzas y caja" nativa (gemela de app/(app)/finanzas/page.tsx).
 *
 * Solo lo que la pantalla necesita para MOSTRAR: periodos, totales, filtros de la
 * lista y las barras del gráfico. Lo que escribe (registrar, corregir, cerrar
 * caja, gastos fijos) lo decide el servidor (/api/staff/…): acá no se reimplementa
 * ninguna regla de dinero, y el arqueo que se guarda lo calcula la web.
 */

/** Un movimiento del kardex tal como lo pinta la lista. */
data class MovimientoKardex(
    val id: String,
    val tipo: String,              // Ingreso | Egreso
    val categoria: String,
    val descripcion: String?,
    val monto: Double,
    val fecha: String,             // YYYY-MM-DD
    val metodoPago: String?,
    val comprobante: String?,
    val comisionId: String? = null,
    val sesionId: String? = null,
    val sedeId: String? = null,
    val pacienteId: String? = null,
    val pacienteNombre: String? = null,
    /** Quién lo registró (resuelto desde el pago/la cita). */
    val autor: String? = null,
) {
    val esIngreso: Boolean get() = tipo == "Ingreso"
}

/** Botones de periodo de la web: Hoy / Semana / Mes / Año / Total. */
enum class PeriodoFinanzas(val etiqueta: String) {
    Dia("Hoy"), Semana("Semana"), Mes("Mes"), Anio("Año"), Total("Total"),
}

/**
 * Rango de fechas a pedir (null = sin límite). Con un periodo NO hay techo en la
 * consulta, como la web: el gráfico del mes incluye lo ya fechado a futuro; los
 * totales y la lista se cortan en hoy con [Finanzas.hastaHoy].
 */
data class RangoFechas(val desde: String?, val hasta: String?)

object Finanzas {

    /**
     * Porcentajes enteros de cada monto sobre su suma, por el método del mayor
     * resto: cada uno se redondea hacia abajo y los puntos que faltan para 100
     * van a los de mayor parte decimal. Así lo mostrado siempre suma 100%.
     */
    fun porcentajesRepartidos(montos: List<Double>): List<Int> {
        val total = montos.sumOf { it }
        if (montos.isEmpty() || total <= 0.0) return montos.map { 0 }
        val exactos = montos.map { it / total * 100.0 }
        val base = exactos.map { kotlin.math.floor(it).toInt() }.toMutableList()
        var faltan = 100 - base.sum()
        exactos.indices.sortedByDescending { exactos[it] - base[it] }.forEach { i ->
            if (faltan > 0) { base[i] += 1; faltan-- }
        }
        return base
    }

    /** Tope de movimientos (en páginas de 1000, lo máximo de PostgREST; solo muerde con "Total"). */
    const val TOPE_MOVIMIENTOS = 20000
    const val PAGINA_MOVIMIENTOS = 1000
    const val POR_PAGINA = 25

    /** Comprobantes que el sistema genera solo: cada prefijo es un origen con su propio botón. */
    private val PREFIJOS_SISTEMA = Regex("^(pago|cita|sesion|recurrente|devolucion|comision|meta|mp|online):")

    /** Inicio (inclusive) del periodo, con lunes como primer día de la semana. */
    fun inicioPeriodo(p: PeriodoFinanzas, hoy: LocalDate): LocalDate? = when (p) {
        PeriodoFinanzas.Dia -> hoy
        PeriodoFinanzas.Semana -> hoy.minus(DatePeriod(days = hoy.dayOfWeek.isoDayNumber - 1))
        PeriodoFinanzas.Mes -> LocalDate(hoy.year, hoy.monthNumber, 1)
        PeriodoFinanzas.Anio -> LocalDate(hoy.year, 1, 1)
        PeriodoFinanzas.Total -> null
    }

    /**
     * Lo que se pide al servidor. Con rango manual manda el rango (ordenado si
     * vino al revés, como `aplicarRango` de la web); si no, el periodo hasta hoy.
     */
    fun rango(p: PeriodoFinanzas, hoy: LocalDate, manual: Pair<String, String>?): RangoFechas {
        if (manual != null) {
            val (a, b) = manual
            return if (a <= b) RangoFechas(a, b) else RangoFechas(b, a)
        }
        return RangoFechas(inicioPeriodo(p, hoy)?.toString(), null)
    }

    /**
     * Lo que cuenta para los totales y la lista: con un periodo, hasta hoy (un
     * movimiento con fecha futura por un error de tipeo no es dinero de hoy);
     * con rango manual, todo lo pedido. Igual que movsFiltrados de la web.
     */
    fun hastaHoy(movs: List<MovimientoKardex>, hoy: LocalDate, manual: Pair<String, String>?): List<MovimientoKardex> {
        if (manual != null) return movs
        val h = hoy.toString()
        return movs.filter { it.fecha <= h }
    }

    /** Manual = sin origen del sistema (los demás se corrigen desde su origen). Gemelo de lib/movimientos-manuales.ts. */
    fun esManual(m: MovimientoKardex): Boolean {
        if (m.comisionId != null || m.sesionId != null) return false
        return !PREFIJOS_SISTEMA.containsMatchIn((m.comprobante ?: "").trim())
    }

    /** Para el que NO se puede tocar acá: dónde se corrige. Null si es manual. */
    fun dondeSeCorrige(m: MovimientoKardex): String? {
        if (esManual(m)) return null
        val c = (m.comprobante ?: "").trim()
        return when {
            m.comisionId != null -> "Viene de una comisión: se corrige desde Comisiones."
            m.sesionId != null || c.startsWith("sesion:") -> "Viene de una sesión: edítala o bórrala desde la ficha del paciente."
            c.startsWith("pago:") -> "Viene de un pago: edítalo o bórralo desde la ficha del paciente (tarjeta de pago)."
            c.startsWith("cita:") -> "Viene del cobro de una cita: revierte la cita desde la agenda."
            c.startsWith("recurrente:") -> "Viene de un gasto fijo: gestiónalo desde la pestaña Gastos fijos."
            c.startsWith("devolucion:") || c.startsWith("mp:") || c.startsWith("online:") -> "Viene de un cobro con tarjeta: se corrige desde Mercado Pago."
            else -> "Lo generó el sistema: se corrige desde su origen."
        }
    }

    /** ¿Se muestra el N° de comprobante? (los del sistema no son un número que la clínica conozca). */
    fun refVisible(m: MovimientoKardex): String? =
        m.comprobante?.takeIf { it.isNotBlank() && !Regex("^(pago|cita|sesion|recurrente|devolucion|comision):").containsMatchIn(it) }

    data class Resumen(
        val ingresos: Double,
        val egresos: Double,
        val balance: Double,
        /** Ingresos por método, de mayor a menor (sin método = "Otro", como la web). */
        val porMetodo: List<Pair<String, Double>>,
    )

    fun resumen(movs: List<MovimientoKardex>): Resumen {
        var ing = 0.0
        var egr = 0.0
        val metodos = LinkedHashMap<String, Double>()
        for (m in movs) {
            if (m.esIngreso) {
                ing += m.monto
                val met = m.metodoPago?.trim()?.takeIf { it.isNotEmpty() } ?: "Otro"
                metodos[met] = (metodos[met] ?: 0.0) + m.monto
            } else if (m.tipo == "Egreso") egr += m.monto
        }
        return Resumen(r2(ing), r2(egr), r2(ing - egr), metodos.entries.map { it.key to r2(it.value) }.sortedByDescending { it.second })
    }

    /** Filtros de la LISTA (no tocan los totales de arriba: un filtro por Yape no es "lo que facturó"). */
    fun filtrar(movs: List<MovimientoKardex>, tipo: String?, metodo: String?, categoria: String?): List<MovimientoKardex> =
        movs.filter { m ->
            (tipo == null || m.tipo == tipo) &&
                (categoria == null || m.categoria == categoria) &&
                (metodo == null || (m.metodoPago?.trim() ?: "") == metodo)
        }

    /** Neto de lo que se está viendo: con ingresos y egresos mezclados, con su signo. */
    fun neto(movs: List<MovimientoKardex>): Double = r2(movs.sumOf { if (it.tipo == "Egreso") -it.monto else it.monto })

    /** Categorías de EGRESO del periodo (filtro rápido: las comisiones del día de DALU). */
    fun categoriasEgreso(movs: List<MovimientoKardex>): List<String> =
        movs.filter { it.tipo == "Egreso" && it.categoria.isNotBlank() }.map { it.categoria.trim() }.distinct().sorted()

    /** Métodos realmente usados en el periodo (no ofrecer "Plin" si nadie pagó con Plin). */
    fun metodosUsados(movs: List<MovimientoKardex>): List<String> =
        movs.mapNotNull { it.metodoPago?.trim()?.takeIf { s -> s.isNotEmpty() } }.distinct().sorted()

    data class Barra(val etiqueta: String, val ingresos: Double, val egresos: Double)

    private val MESES = listOf("ene", "feb", "mar", "abr", "may", "jun", "jul", "ago", "sep", "oct", "nov", "dic")

    /**
     * Barras del gráfico, como la web: rango manual → por día (o por mes si pasa
     * de 62 días); Hoy → últimos 3 días; Semana → últimos 7; Mes → el mes entero;
     * Año/Total → últimos 12 meses.
     */
    fun barras(movs: List<MovimientoKardex>, p: PeriodoFinanzas, hoy: LocalDate, manual: Pair<String, String>?): List<Barra> {
        val porDia = HashMap<String, Pair<Double, Double>>()
        for (m in movs) {
            val (i, e) = porDia[m.fecha] ?: (0.0 to 0.0)
            porDia[m.fecha] = if (m.esIngreso) (i + m.monto) to e else if (m.tipo == "Egreso") i to (e + m.monto) else i to e
        }
        fun dia(d: LocalDate): Barra {
            val (i, e) = porDia[d.toString()] ?: (0.0 to 0.0)
            return Barra(d.dayOfMonth.toString(), r2(i), r2(e))
        }
        fun mes(anio: Int, mes: Int): Barra {
            val pref = "$anio-${mes.toString().padStart(2, '0')}"
            var i = 0.0; var e = 0.0
            for ((f, v) in porDia) if (f.startsWith(pref)) { i += v.first; e += v.second }
            return Barra(MESES[mes - 1] + if (anio != hoy.year) " ${anio % 100}" else "", r2(i), r2(e))
        }
        fun dias(desde: LocalDate, hasta: LocalDate): List<Barra> {
            val out = ArrayList<Barra>()
            var d = desde
            while (d <= hasta && out.size < 400) { out += dia(d); d = d.plus(DatePeriod(days = 1)) }
            return out
        }
        fun meses(desde: LocalDate, hasta: LocalDate): List<Barra> {
            val out = ArrayList<Barra>()
            var a = desde.year; var m = desde.monthNumber
            while ((a < hasta.year || (a == hasta.year && m <= hasta.monthNumber)) && out.size < 240) {
                out += mes(a, m)
                m++; if (m > 12) { m = 1; a++ }
            }
            return out
        }
        if (manual != null) {
            val r = rango(p, hoy, manual)
            val desde = runCatching { LocalDate.parse(r.desde!!) }.getOrNull() ?: return emptyList()
            val hasta = runCatching { LocalDate.parse(r.hasta!!) }.getOrNull() ?: return emptyList()
            val n = desde.daysUntilSeguro(hasta) + 1
            return if (n > 62) meses(desde, hasta) else dias(desde, hasta)
        }
        return when (p) {
            PeriodoFinanzas.Anio, PeriodoFinanzas.Total -> meses(LocalDate(hoy.year, hoy.monthNumber, 1).minus(DatePeriod(months = 11)), hoy)
            PeriodoFinanzas.Mes -> {
                val ini = LocalDate(hoy.year, hoy.monthNumber, 1)
                dias(ini, ini.plus(DatePeriod(months = 1)).minus(DatePeriod(days = 1)))
            }
            PeriodoFinanzas.Semana -> dias(hoy.minus(DatePeriod(days = 6)), hoy)
            PeriodoFinanzas.Dia -> dias(hoy.minus(DatePeriod(days = 2)), hoy)
        }
    }

    private fun LocalDate.daysUntilSeguro(otra: LocalDate): Int = (otra.toEpochDays() - this.toEpochDays())

    /** Máximo "bonito" para la escala del gráfico (470 → 500, 1230 → 2000). */
    fun techoBonito(n: Double): Double {
        if (n <= 0) return 1.0
        var mag = 1.0
        while (mag * 10 <= n) mag *= 10
        while (mag > n) mag /= 10
        val norm = n / mag
        val paso = when { norm <= 1 -> 1.0; norm <= 2 -> 2.0; norm <= 5 -> 5.0; else -> 10.0 }
        return paso * mag
    }

    // ── Arqueo (vista previa mientras se escribe; lo que se GUARDA lo calcula el servidor) ──

    enum class EstadoArqueo { CUADRA, SOBRA, FALTA }
    data class Arqueo(val diferencia: Double, val estado: EstadoArqueo, val mensaje: String)

    /** Gemelo de compararArqueo (lib/cierre-caja.ts): tolerancia de un centavo. */
    fun compararArqueo(esperado: Double, contado: Double, moneda: String = monedaActiva()): Arqueo {
        val dif = r2(contado - esperado)
        val estado = when {
            kotlin.math.abs(dif) < 0.01 -> EstadoArqueo.CUADRA
            dif > 0 -> EstadoArqueo.SOBRA
            else -> EstadoArqueo.FALTA
        }
        val abs = formatearDinero(kotlin.math.abs(dif), moneda)
        val msg = when (estado) {
            EstadoArqueo.CUADRA -> "La caja cuadra"
            EstadoArqueo.SOBRA -> "Sobra $abs"
            EstadoArqueo.FALTA -> "Falta $abs"
        }
        return Arqueo(dif, estado, msg)
    }

    /** Monto tecleado ("12,50" o "12.50") → número, o null si no es un monto. */
    fun parsearMonto(txt: String): Double? =
        txt.trim().replace(',', '.').toDoubleOrNull()?.takeIf { it.isFinite() && it >= 0 }

    private fun r2(v: Double): Double = kotlin.math.round(v * 100) / 100
}
