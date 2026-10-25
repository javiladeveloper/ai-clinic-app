package pe.saniape.app.data.staff

import kotlin.math.abs
import kotlin.math.max

// Chips de cobro «Todo / 50 % / Saldo» (T3, 24/10/2026). Gemelo EXACTO de
// lib/chips-cobro.ts de la web (mismos casos en ChipsCobroTest): en cada cobro
// frecuente el monto se elige con un toque y el campo sigue editable.
//
//  - nada pagado (saldo = total, o desconocido): «Todo» y «50 %»;
//  - algo pagado (0 < saldo < total): «Saldo» y, si cabe, «50 %» del total;
//  - debe MÁS que esto (suelta con sesiones atrás): «Todo» (esta) y «Saldo»;
//  - sin total pero con saldo: «Saldo» y «50 %» del saldo;
//  - nada que cobrar (saldo ≤ 0): ninguno.
// Nunca dos chips con el mismo monto, ni uno en 0.

enum class ChipCobro(val etiqueta: String) { TODO("Todo"), MITAD("50 %"), SALDO("Saldo") }

data class OpcionChipCobro(val chip: ChipCobro, val monto: Double) {
    val etiqueta: String get() = chip.etiqueta
}

private const val CENTIMO = 0.005

fun chipsCobro(total: Double?, saldo: Double?, moneda: String? = null): List<OpcionChipCobro> {
    fun r(n: Double) = redondearPrecio(n, moneda)
    val t = total?.takeIf { it.isFinite() && it > CENTIMO }
    val s = saldo?.takeIf { it.isFinite() }?.let { max(0.0, r(it)) }
    if (s != null && s <= CENTIMO) return emptyList()
    val lista = mutableListOf<Pair<ChipCobro, Double>>()
    if (t != null) {
        val tt = r(t)
        val mitad = r(tt / 2)
        when {
            s == null || abs(s - tt) <= CENTIMO -> { lista += ChipCobro.TODO to tt; lista += ChipCobro.MITAD to mitad }
            s < tt -> { lista += ChipCobro.SALDO to s; if (mitad < s - CENTIMO) lista += ChipCobro.MITAD to mitad }
            else -> { lista += ChipCobro.TODO to tt; lista += ChipCobro.SALDO to s }
        }
    } else if (s != null) {
        lista += ChipCobro.SALDO to s
        lista += ChipCobro.MITAD to r(s / 2)
    }
    val vistos = mutableSetOf<Long>()
    return lista.mapNotNull { (chip, monto) ->
        val clave = redondeoJs(monto * 100).toLong()
        if (monto <= CENTIMO || !vistos.add(clave)) null else OpcionChipCobro(chip, monto)
    }
}

/** ¿Qué chip está puesto? El que coincide con lo escrito (null si ninguno). */
fun chipActivo(opciones: List<OpcionChipCobro>, escrito: String?): ChipCobro? {
    val n = escrito?.trim()?.replace(',', '.')?.toDoubleOrNull() ?: return null
    if (!n.isFinite() || n <= 0) return null
    return opciones.firstOrNull { abs(it.monto - n) <= CENTIMO }?.chip
}

/** El monto como texto para el campo (sin ceros de más: «300», «150.5»). */
fun textoMontoChip(monto: Double): String = textoPrecioCampo(monto)
