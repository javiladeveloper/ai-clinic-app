package pe.saniape.app.data.staff

// LA FILA DE UN PAGO DEL TRATAMIENTO (pedido del dueño, 24–25/10/2026). Antes:
// 🧾 + ✏ + 🗑 sueltos (Admin) y, al tocar 🧾, otra fila «A4 · Ticket 80 mm ·
// Cancelar» que en un teléfono no entraba. Ahora un solo botón (🧾, o ⋯ si el
// pago no tiene comprobante) abre el menú: comprobante · corregir · 🗑 al final
// en rojo. Mismo criterio que la PagoCard web (`lib/acciones-pago.ts`). Solo reparte: quién puede
// editar o borrar lo sigue decidiendo la pantalla (Admin, ficha no dada de baja).

enum class AccionPago { ComprobanteA4, Ticket, Editar, Borrar }

val GRUPOS_MENU_PAGO: List<GrupoMenu<AccionPago>> = listOf(
    GrupoMenu("Comprobante", listOf(AccionPago.ComprobanteA4, AccionPago.Ticket)),
    GrupoMenu("Corregir", listOf(AccionPago.Editar)),
    GrupoMenu(GRUPO_PELIGRO, listOf(AccionPago.Borrar)),
)

fun textoAccionPago(a: AccionPago): String = when (a) {
    AccionPago.ComprobanteA4 -> "🧾 Comprobante A4"
    AccionPago.Ticket -> "🎫 Ticket 80 mm"
    AccionPago.Editar -> "✏ Editar pago"
    AccionPago.Borrar -> "🗑 Borrar pago"
}

/**
 * El menú de la fila de un pago. [comprobante] = el pago tiene recibo (no es el
 * «consumo» del saldo a favor); [editar] / [borrar] ya con su permiso resuelto.
 * Nada queda a la vista: el disparador es el único botón de la fila.
 */
fun accionesPago(comprobante: Boolean, editar: Boolean, borrar: Boolean): PlanAcciones<AccionPago> {
    val disponibles = buildList {
        if (comprobante) { add(AccionPago.ComprobanteA4); add(AccionPago.Ticket) }
        if (editar) add(AccionPago.Editar)
        if (borrar) add(AccionPago.Borrar)
    }
    return repartirAcciones(emptyList(), GRUPOS_MENU_PAGO, disponibles, rojas = listOf(AccionPago.Borrar))
}
