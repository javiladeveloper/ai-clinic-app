package pe.saniape.app.data.staff

/**
 * "✓ Ya pagó" en la agenda (gemelo de `citaYaPago` / `yaPagoConRespaldo` de
 * lib/estado-pago-atencion.ts, 2026-10-17): un cliente no distinguía el 💰 verde
 * y pidió un TEXTO. NO es otra regla: es la misma del [EstadoPagoCita] del servidor.
 *
 *  - Con el estado que ya trajo la agenda (`/api/staff/cita/estado-pago`), manda
 *    ese: pagado ⇔ `estado == "pagado"`.
 *  - Sin él: la cita que se cobra sola (Consulta/Evaluación) con costo > 0 y
 *    `pagada_at` (la misma moneda verde). La sesión de un tratamiento sin su
 *    estado no se adivina: nunca un "Ya pagó" inventado.
 *
 * Gratis / sin costo y canceladas: nunca.
 */
fun citaYaPago(
    tipo: String?,
    estadoCita: String?,
    costo: Double?,
    pagadaAt: String?,
    tratamientoId: String?,
    /** `estado` del servidor ("pagado", "debe"…); null = no llegó. */
    estadoServidor: String? = null,
): Boolean {
    if (estadoCita == "Cancelada") return false
    if (estadoServidor != null && estadoServidor != "sin_dato") return estadoServidor == "pagado"
    // Sesión de un tratamiento: el dinero vive en el tratamiento → sin_dato.
    if (tipo == "Sesión" && !tratamientoId.isNullOrBlank()) return false
    val c = costo ?: 0.0
    if (!(c > 0)) return false
    return !pagadaAt.isNullOrBlank()
}

/**
 * Inicio: el `yaPago` que trae `/api/dashboard/stats` (solo las citas que se
 * cobran solas) completado con el estado de la agenda (sesiones). Si el estado
 * llegó, manda; si no (cargando, falló), el respaldo del endpoint.
 */
fun yaPagoConRespaldo(estadoCita: String?, estadoServidor: String?, respaldo: Boolean?): Boolean {
    if (estadoServidor != null && estadoServidor != "sin_dato") return estadoCita != "Cancelada" && estadoServidor == "pagado"
    return respaldo == true && estadoCita != "Cancelada"
}

/** Textos de la etiqueta: completa y la corta para las vistas apretadas (nunca solo ícono). */
const val TEXTO_YA_PAGO = "✓ Ya pagó"
const val TEXTO_YA_PAGO_CORTO = "✓ Pagó"
