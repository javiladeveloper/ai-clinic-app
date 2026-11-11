package pe.saniape.app.data.staff

/**
 * ¿El tratamiento va al «📁 Historial» de la ficha? Gemelo de `cerradoDeVerdad`
 * (web, lib/seguimiento-post-alta.ts): Alta, Cancelado, Suspendido y Completado
 * van al historial, SALVO lo realizado con saldo (estado de pago Pendiente o
 * Parcial): falta cobrarlo y darle de alta, y escondido se olvidaba (10/11/2026).
 */
fun vaAlHistorial(estado: String?, estadoPago: String?): Boolean {
    if (estado == "Completado" && (estadoPago == "Pendiente" || estadoPago == "Parcial")) return false
    return estado == "Alta" || estado == "Cancelado" || estado == "Suspendido" || estado == "Completado"
}
