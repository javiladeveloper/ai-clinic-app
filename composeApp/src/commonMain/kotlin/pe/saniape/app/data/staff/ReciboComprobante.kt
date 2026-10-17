package pe.saniape.app.data.staff

/**
 * "🧾 Comprobante": reimprimir el recibo de un pago YA hecho (gemelo de
 * lib/recibo-cliente.ts, 2026-10-17: "solo se da en el pago"). Es el MISMO recibo
 * de `/api/staff/comprobante/{tipo}/{id}` — mismo número, monto, medio y fecha —
 * con la marca "Reimpresión · <hoy>" (`reimpresion=1`).
 */

/** Tipos del endpoint: `pago`, `cita`, `atencion` y `venta` (id = el ingreso en caja). */
data class ReciboRef(val tipo: String, val id: String)

/**
 * ¿Este movimiento de caja tiene un recibo que se pueda reimprimir? Solo los
 * INGRESOS que nacen de un cobro con datos detrás:
 *  · `cita:<id>` (y sus partes `#2…`) → el cobro de la cita
 *  · `pago:<id>` → el pago del tratamiento
 *  · "Venta de productos" (carrito, venta suelta o `pedido:<id>`) → la venta, por su ingreso
 * Lo manual, comisiones, devoluciones y gastos no tienen recibo de cobro; un
 * ingreso en 0 (un pedido todo de regalo) tampoco.
 */
fun reciboDeMovimiento(id: String, tipo: String, categoria: String?, comprobante: String?, monto: Double?): ReciboRef? {
    if (tipo != "Ingreso" || !((monto ?: 0.0) > 0)) return null
    val comp = comprobante.orEmpty().trim()
    citaIdDeComprobante(comp)?.let { return ReciboRef("cita", it) }
    if (comp.startsWith("pago:") && comp.length > 5) return ReciboRef("pago", comp.substring(5))
    if (categoria == "Venta de productos" && (comp.isEmpty() || comp.startsWith("pedido:"))) return ReciboRef("venta", id)
    return null
}

/**
 * "🧾 Comprobante" en la cita cobrada de la agenda (misma condición que /citas web):
 * no Sesión (se cobra en su tratamiento), con costo, ya cobrada, no cancelada y
 * quien mira tiene el permiso 'pagos'. Un cobro anulado ya no está cobrado.
 */
fun ofreceComprobanteCita(tipo: String?, costo: Double?, pagadaAt: String?, estado: String?, puedePagos: Boolean): Boolean =
    puedePagos && tipo != "Sesión" && (costo ?: 0.0) > 0 && !pagadaAt.isNullOrBlank() && estado != "Cancelada"
