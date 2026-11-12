package pe.saniape.app.data.staff

import kotlin.math.roundToInt

// ─────────────────────────────────────────────────────────────────────────────
// «➕ CREAR PLAN CON N UF» DESDE LA TRICOSCOPÍA (flujos 26/10, #4). Gemelo de
// lib/capilar-plan.ts: las UF de la estimación abren «Nuevo tratamiento» con el
// servicio de trasplante, por Unidades, la cantidad y el precio. Todo sale de
// los datos de la clínica: el servicio que cobra por unidades y cuya unidad es
// el folículo (o se llama trasplante / injerto / FUE).
// ─────────────────────────────────────────────────────────────────────────────

private val ES_FOLICULAR = Regex("""fol[ií]cul|\bUFs?\b|trasplant|injert|\bFUE\b|\bFUT\b""", RegexOption.IGNORE_CASE)

/** El servicio de trasplante de la clínica (cobra por unidades foliculares), o null. */
fun servicioDeTrasplante(procedimientos: List<ProcedimientoRef>): ProcedimientoRef? {
    val candidatos = procedimientos.filter { p ->
        p.modoCobro == "unidades" &&
            (ES_FOLICULAR.containsMatchIn(p.unidadLabel.orEmpty()) || ES_FOLICULAR.containsMatchIn(p.nombre))
    }
    // El que nombra la unidad folicular primero; si no, el primero.
    return candidatos.firstOrNull { ES_FOLICULAR.containsMatchIn(it.unidadLabel.orEmpty()) } ?: candidatos.firstOrNull()
}

/** Lo que precarga «Nuevo tratamiento» con las UF de la estimación. */
fun planDesdeTricoscopia(uf: Int, procedimientos: List<ProcedimientoRef>): PrefillPlanPsico {
    val n = maxOf(0, uf)
    val s = servicioDeTrasplante(procedimientos)
    val unit = s?.let { it.precioUnitarioSugerido ?: it.precio } ?: 0.0
    return PrefillPlanPsico(
        procedimientoId = s?.id,
        modalidad = "Unidades",
        cantidadUnidades = n,
        precioUnitario = unit.takeIf { it > 0.0 },
        diagnostico = "Trasplante capilar: ${milesUf(n)} UF estimadas en la tricoscopía",
        respetarModalidad = true,
    )
}

/** 2100 → "2,100" (como `toLocaleString('es-PE')` de la web). */
internal fun milesUf(n: Int): String = n.toString().reversed().chunked(3).joinToString(",").reversed()

/** Redondeo de la web (Math.round) para UF que llegan con decimales. */
fun ufEntera(x: Double): Int = x.roundToInt()
