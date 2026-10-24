package pe.saniape.app.data.staff

import pe.saniape.app.data.TratPago
import pe.saniape.app.data.precioSesionSuelta

// ─────────────────────────────────────────────────────────────────────────────
// Cuánto propone cobrar el cierre de una SESIÓN en la agenda, y con qué costo
// nace la cita de sesión que se agenda desde la app.
//
// Antes el cierre proponía `cita.costo`, y una Sesión agendada desde la app
// guardaba costo 0: el monto llegaba en S/ 0 (o vacío) y había que teclearlo.
// Ahora es la regla del 💰 de /citas web (lib/cobro-sesion-agenda):
//   · paquete → lo que se debe del paquete (el abono);
//   · si no → `montoSugeridoCobroSesion`: el costo de la sesión sin pasar de lo
//     que se debe del tratamiento; sin costo propio, lo que se debe.
// La deuda la calcula el servidor (/api/staff/cita/estado-pago: en una suelta
// ya cuenta ESTA sesión). Es solo la propuesta: se cobra lo que se teclee.
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Gemelo de `montoSugeridoCobroSesion` (web): el costo de la sesión sin pasar de
 * lo que se debe; sin costo propio (sesión de un paquete), lo que se debe.
 */
fun montoSugeridoCobroSesion(costoSesion: Double?, deuda: Double): Double {
    val d = maxOf(aCentimos(deuda), 0L)
    val c = maxOf(aCentimos(costoSesion ?: 0.0), 0L)
    return (if (c > 0) minOf(c, d) else d) / 100.0
}

/**
 * Lo que el cierre de la sesión propone cobrar. null = nada que proponer (el
 * campo queda vacío, como en la web).
 *  · Sin estado de pago o sin deuda conocida: el costo de la cita, si tiene.
 *  · Ya pagado o sin costo: nada (el cierre ni pregunta si está pagado).
 *  · Paquete: lo que se debe del paquete (el 💰 de la web: `esPaqueteCita ? deuda`).
 *  · Suelta / pago único: [montoSugeridoCobroSesion].
 */
fun montoPropuestoCierreSesion(
    costoCita: Double?,
    estadoPago: EstadoPagoCita?,
    esPaquete: Boolean = estadoPago?.esPaquete == true,
): Double? {
    val costo = costoCita?.takeIf { it > 0 }
    if (estadoPago?.estado == "pagado" || estadoPago?.estado == "gratis") return null
    val deuda = estadoPago?.deuda?.takeIf { estadoPago.estado != "sin_dato" } ?: return costo
    val monto = if (esPaquete) maxOf(aCentimos(deuda), 0L) / 100.0 else montoSugeridoCobroSesion(costo, deuda)
    return monto.takeIf { it > 0 }
}

/**
 * Costo con que nace la cita de SESIÓN de un tratamiento que ya existe, agendada
 * desde la app. Como las sesiones que agenda la web desde el plan
 * (AgendarSesionesModal: `costoSesion` solo en la suelta): suelta → el precio de
 * UNA sesión (lo acordado entre las sesiones pactadas, o el precio por sesión;
 * `precioSesionSuelta`); paquete / unidades / pago único → 0 (el dinero vive en
 * el tratamiento; ponérselo a cada sesión duplicaría el cobro).
 */
fun costoCitaDeSesion(
    modalidad: String?,
    precioPorSesion: Double?,
    precioAcordado: Double?,
    totalSesiones: Int?,
): Double {
    if (modalidad?.trim() != "Sesión suelta") return 0.0
    val p = precioSesionSuelta(TratPago(modalidad, precioAcordado = precioAcordado, precioPorSesion = precioPorSesion, totalSesiones = totalSesiones))
    return if (p.isFinite() && p > 0) aCentimos(p) / 100.0 else 0.0
}
