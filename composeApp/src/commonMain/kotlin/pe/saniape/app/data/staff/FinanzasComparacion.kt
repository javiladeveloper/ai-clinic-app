package pe.saniape.app.data.staff

import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.isoDayNumber
import kotlinx.datetime.minus
import kotlinx.datetime.daysUntil
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * COMPARAR CON EL PERIODO ANTERIOR en el kardex — gemelo de
 * lib/finanzas-comparacion.ts (web, 18/11/2026: «Ingresos S/ 12.400 · ↑ 8% vs.
 * el mes pasado»). Se compara lo mismo con lo mismo: el mes en curso va del 1 a
 * hoy, así que se compara con el mes pasado del 1 al MISMO día.
 */
data class PeriodoAnterior(val desde: String, val hasta: String, val etiqueta: String)

private fun ultimoDiaDelMes(anio: Int, mes: Int): Int {
    val siguiente = if (mes == 12) LocalDate(anio + 1, 1, 1) else LocalDate(anio, mes + 1, 1)
    return siguiente.minus(DatePeriod(days = 1)).dayOfMonth
}

/** El tramo equivalente anterior al que se ve, o null si no hay con qué comparar ('Total'). */
fun periodoAnterior(p: PeriodoFinanzas, hoy: LocalDate, manual: Pair<String, String>?): PeriodoAnterior? {
    if (manual != null) {
        val (a, b) = if (manual.first <= manual.second) manual else manual.second to manual.first
        val d = LocalDate.parse(a)
        val dias = d.daysUntil(LocalDate.parse(b)) + 1
        val fin = d.minus(DatePeriod(days = 1))
        val ini = d.minus(DatePeriod(days = dias))
        return PeriodoAnterior(ini.toString(), fin.toString(), if (dias == 1) "vs. el día anterior" else "vs. los $dias días anteriores")
    }
    return when (p) {
        PeriodoFinanzas.Dia -> hoy.minus(DatePeriod(days = 1)).let { PeriodoAnterior(it.toString(), it.toString(), "vs. ayer") }
        PeriodoFinanzas.Semana -> {
            val lunes = hoy.minus(DatePeriod(days = hoy.dayOfWeek.isoDayNumber - 1))
            PeriodoAnterior(lunes.minus(DatePeriod(days = 7)).toString(), hoy.minus(DatePeriod(days = 7)).toString(), "vs. la semana pasada")
        }
        PeriodoFinanzas.Mes -> {
            val anio = if (hoy.monthNumber == 1) hoy.year - 1 else hoy.year
            val mes = if (hoy.monthNumber == 1) 12 else hoy.monthNumber - 1
            val fin = LocalDate(anio, mes, minOf(hoy.dayOfMonth, ultimoDiaDelMes(anio, mes)))
            PeriodoAnterior(LocalDate(anio, mes, 1).toString(), fin.toString(), "vs. el mes pasado a la misma fecha")
        }
        PeriodoFinanzas.Anio -> {
            val anio = hoy.year - 1
            val fin = LocalDate(anio, hoy.monthNumber, minOf(hoy.dayOfMonth, ultimoDiaDelMes(anio, hoy.monthNumber)))
            PeriodoAnterior(LocalDate(anio, 1, 1).toString(), fin.toString(), "vs. el año pasado a la misma fecha")
        }
        PeriodoFinanzas.Total -> null
    }
}

/** «↑ 8% vs. el mes pasado a la misma fecha». null si el anterior es 0 (un «↑ ∞%» no dice nada). */
fun textoVariacion(actual: Double, anterior: Double, etiqueta: String): String? {
    if (!(anterior > 0)) return null
    val pct = ((actual - anterior) / anterior * 100).roundToInt()
    if (pct == 0) return "= igual que ${etiqueta.removePrefix("vs. ")}"
    return "${if (pct > 0) "↑" else "↓"} ${abs(pct)}% $etiqueta"
}

/** Más ingresos es bueno; más egresos es malo (para pintarla en verde o rojo). */
fun variacionEsBuena(actual: Double, anterior: Double, campo: String): Boolean =
    if (campo == "egresos") actual <= anterior else actual >= anterior

/** Buscador de la lista del kardex: concepto, categoría, paciente, comprobante o medio (sin tildes). */
fun buscarMovimientos(movs: List<MovimientoKardex>, q: String): List<MovimientoKardex> {
    fun n(s: String?) = (s ?: "").lowercase()
        .replace('á', 'a').replace('é', 'e').replace('í', 'i').replace('ó', 'o').replace('ú', 'u')
    val t = n(q.trim())
    if (t.isEmpty()) return movs
    return movs.filter { m ->
        listOf(m.descripcion, m.categoria, m.pacienteNombre, m.comprobante, m.metodoPago, m.autor).any { n(it).contains(t) }
    }
}
