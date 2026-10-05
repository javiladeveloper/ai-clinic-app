package pe.saniape.app.data

import kotlin.math.max
import kotlin.math.round

// ─────────────────────────────────────────────────────────────────────────────
// SALDO A FAVOR — GEMELO de lib/saldo-a-favor.ts (web). Regla del 2026-10-05
// (QA: el paciente demo EMERSON tenía S/ 205.97 a favor y la app mostraba menos).
//
// Solo se MUESTRA: no mueve plata ni descuenta la deuda de otro tratamiento (la
// deuda se sigue calculando aparte, como siempre).
//  · Cuentan TODAS las modalidades; fuera los estados Cancelado / Eliminado.
//  · Paquete / Unidades: el acordado es el monto acordado actual (un tope).
//  · Sesión suelta / Consulta (y cualquier otra): se cobra por atención, así que
//    el acordado EFECTIVO crece con lo realizado:
//        acordado efectivo = max(acordado, unit × realizadas)
//        unit       = precio_por_sesion ?: precio_acordado / max(total_sesiones ?: 1, 1)
//        realizadas = max(sesiones no anuladas, citas no canceladas)  del tratamiento
//    Sesión anulada = Cancelada / Reprogramada / No asistió.
//  · Acordado efectivo 0 o unit 0 → 0 (no hay contra qué comparar).
//  · Excedente = pagado − acordado efectivo si pasa de medio centavo, a 2 decimales.
// ─────────────────────────────────────────────────────────────────────────────

private val ESTADOS_SIN_A_FAVOR = setOf("Cancelado", "Eliminado")
private val MODALIDADES_PRECIO_CERRADO = setOf("Paquete", "Unidades")

/** Estados de sesión que NO cuentan como atención realizada (ni cobrable). */
val ESTADOS_SESION_ANULADA = setOf("Cancelada", "Reprogramada", "No asistió")

/** Soles a 2 decimales. */
internal fun redondear2(n: Double): Double = round(n * 100) / 100

/** La cuenta de UN tratamiento, lo mínimo para decidir si hay excedente. */
data class CuentaTratamiento(
    /** Monto acordado actual (TratamientoPaciente.montoAcordado). */
    val acordado: Double,
    val pagado: Double,
    val estado: String?,
    val modalidad: String?,
    // ── Solo para Sesión suelta / Consulta (cobro por atención) ──
    val precioPorSesion: Double? = null,
    /** Columna precio_acordado tal cual (null = no se fijó). */
    val precioAcordado: Double? = null,
    val totalSesiones: Int? = null,
    /** Sesiones del tratamiento con estado fuera de [ESTADOS_SESION_ANULADA]. */
    val sesionesRealizadas: Int = 0,
    /** Citas del tratamiento con estado != Cancelada. */
    val citasNoCanceladas: Int = 0,
)

/** Precio de UNA atención (Sesión suelta / Consulta). */
fun precioUnitario(c: CuentaTratamiento): Double =
    c.precioPorSesion ?: ((c.precioAcordado ?: 0.0) / max(c.totalSesiones ?: 1, 1))

/**
 * Lo que de verdad se acordó a la fecha: en Paquete / Unidades el acordado; en el
 * resto, max(acordado, unit × realizadas). 0 si no hay precio.
 */
fun acordadoEfectivo(c: CuentaTratamiento): Double {
    if (c.modalidad?.trim() in MODALIDADES_PRECIO_CERRADO) return c.acordado.coerceAtLeast(0.0)
    val unit = precioUnitario(c)
    if (unit <= 0.005) return 0.0
    val realizadas = max(c.sesionesRealizadas, c.citasNoCanceladas)
    return max(c.acordado, unit * realizadas).coerceAtLeast(0.0)
}

/** Lo pagado DE MÁS en un tratamiento (0 si no aplica según las reglas de arriba). */
fun saldoAFavorTratamiento(c: CuentaTratamiento): Double {
    if (c.estado in ESTADOS_SIN_A_FAVOR) return 0.0
    val acordado = acordadoEfectivo(c)
    if (acordado <= 0.005) return 0.0
    val excedente = c.pagado - acordado
    return if (excedente > 0.005) redondear2(excedente) else 0.0
}

/** Saldo a favor del paciente (de UNA clínica): Σ excedentes de sus tratamientos. */
fun saldoAFavorDe(cuentas: List<CuentaTratamiento>): Double {
    val total = cuentas.sumOf { saldoAFavorTratamiento(it) }
    return if (total > 0.005) redondear2(total) else 0.0
}
