package pe.saniape.app.data.staff

// TRATAMIENTO POR UNIDADES (estética, capilar): la CANTIDAD ya no es obligatoria
// al crearlo (auditoría 24/10, punto E3). Se pedía dos veces: al crear el
// tratamiento, antes de saber cuántas unidades se aplicarán, y luego la real en
// el mapa de aplicación («🔢 Usar … como unidades cobradas» → editar). Ahora
// vacía no bloquea y se completa después.
//
// ES DINERO. Sin cantidad (ni precio acordado escrito) el tratamiento nace SIN
// monto: no se manda cantidad ni acordado, el servidor guarda null y el pactado
// es 0 × precio = 0 (montoPactado). Eso NO es "Pagado" (estadoPagoDe exige
// acordado > 0) ni ofrece 💰 en la agenda (estado-pago: sin_dato), así que no
// se cobra nada de más; el monto aparece al completar la cantidad. Gemelo del
// criterio de TratamientoForm (web): `cantidad_unidades: cantidad ? … : null`,
// `precio_acordado: escrito ?? (cantidad × unitario || null)`.

/** Cantidad a guardar: vacía, 0 o algo que no es un entero = null (se completa luego). */
fun cantidadUnidadesAGuardar(texto: String): Int? = texto.trim().toIntOrNull()?.takeIf { it > 0 }

/** ¿Se puede crear por unidades? Basta el precio por unidad (> 0): la cantidad es opcional. */
fun puedeCrearPorUnidades(precioUnitarioTexto: String): Boolean =
    (precioUnitarioTexto.trim().toDoubleOrNull() ?: 0.0) > 0.0

/**
 * Precio acordado al crear por unidades: el escrito manda (precio negociado);
 * si no, cantidad × precio unitario; sin cantidad, null (sin monto todavía:
 * nunca un total inventado).
 */
fun acordadoUnidadesAlCrear(acordadoTexto: String, cantidad: Int?, precioUnitario: Double?): Double? {
    acordadoTexto.toDoubleOrNull()?.let { return it }
    val total = (cantidad ?: 0) * (precioUnitario ?: 0.0)
    return total.takeIf { it > 0.0 }
}
