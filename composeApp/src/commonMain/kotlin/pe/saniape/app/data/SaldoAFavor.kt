package pe.saniape.app.data

import kotlin.math.round

// ─────────────────────────────────────────────────────────────────────────────
// SALDO A FAVOR (QA Emerson, 2026-10-03) — GEMELO de lib/saldo-a-favor.ts (web).
//
// Solo se MUESTRA: no mueve plata ni descuenta la deuda de otro tratamiento (la
// deuda se sigue calculando aparte, como siempre). Ante la duda, NO hay a favor:
//  · SOLO Paquete y Unidades (el acordado es un tope). 'Sesión suelta' guarda
//    total_sesiones=1 y cada sesión cobrada entra al tratamiento: todo lo cobrado
//    después de la primera saldría "a favor" (falso); Consulta cobra por cita;
//    modalidad vacía/desconocida → no se arriesga.
//  · acordado <= 0 → no hay contra qué comparar.
//  · Cancelado / Eliminado → no es facturable.
// Redondeo a 2 decimales por tratamiento; tolerancia de medio centavo.
// ─────────────────────────────────────────────────────────────────────────────

private val ESTADOS_SIN_A_FAVOR = setOf("Cancelado", "Eliminado")
private val MODALIDADES_CON_A_FAVOR = setOf("Paquete", "Unidades")

/** Soles a 2 decimales. */
internal fun redondear2(n: Double): Double = round(n * 100) / 100

/** La cuenta de UN tratamiento, lo mínimo para decidir si hay excedente. */
data class CuentaTratamiento(
    val acordado: Double,
    val pagado: Double,
    val estado: String?,
    val modalidad: String?,
)

/** Lo pagado DE MÁS en un tratamiento (0 si no aplica según las reglas de arriba). */
fun saldoAFavorTratamiento(acordado: Double, pagado: Double, estado: String?, modalidad: String?): Double {
    if (acordado <= 0.005) return 0.0
    if (estado in ESTADOS_SIN_A_FAVOR) return 0.0
    if (modalidad !in MODALIDADES_CON_A_FAVOR) return 0.0
    val excedente = pagado - acordado
    return if (excedente > 0.005) redondear2(excedente) else 0.0
}

/** Saldo a favor del paciente: Σ excedentes de sus tratamientos. */
fun saldoAFavorDe(cuentas: List<CuentaTratamiento>): Double {
    val total = cuentas.sumOf { saldoAFavorTratamiento(it.acordado, it.pagado, it.estado, it.modalidad) }
    return if (total > 0.005) redondear2(total) else 0.0
}
