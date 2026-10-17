package pe.saniape.app.data.staff

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * "🧾 Comprobante" (reimprimir el recibo): gemelo de reciboDeMovimiento y de la
 * condición del menú de /citas (lib/__tests__/comprobante-reimpresion.test.ts).
 */
class ReciboComprobanteTest {

    private fun ing(id: String = "m", categoria: String? = null, comprobante: String? = null, monto: Double? = 50.0) =
        reciboDeMovimiento(id, "Ingreso", categoria, comprobante, monto)

    @Test
    fun citaYSusPartesPagoYVentaDeProductosNadaMas() {
        assertEquals(ReciboRef("cita", "abc"), ing(comprobante = "cita:abc"))
        assertEquals(ReciboRef("cita", "abc"), ing(comprobante = "cita:abc#2"))
        assertEquals(ReciboRef("pago", "p1"), ing(comprobante = "pago:p1"))
        assertEquals(ReciboRef("venta", "m1"), ing(id = "m1", categoria = "Venta de productos"))
        assertEquals(ReciboRef("venta", "m2"), ing(id = "m2", categoria = "Venta de productos", comprobante = "pedido:x"))
        assertNull(reciboDeMovimiento("m", "Egreso", null, "pago:p1", 10.0))
        assertNull(ing(categoria = "Otro", comprobante = "F001-492"))
        assertNull(ing(categoria = "Venta de productos", comprobante = "F001-492"))
        assertNull(ing(comprobante = "devolucion:x"))
        assertNull(ing(comprobante = "pago:"))
    }

    @Test
    fun unIngresoEnCeroNoTieneRecibo() {
        assertNull(ing(categoria = "Venta de productos", comprobante = "pedido:x", monto = 0.0))
        assertNull(ing(comprobante = "cita:a", monto = 0.0))
        assertNull(ing(comprobante = "cita:a", monto = null))
    }

    @Test
    fun comprobanteDeLaCitaCobradaComoElMenuDeLaWeb() {
        assertTrue(ofreceComprobanteCita("Consulta", 80.0, "2026-10-17T10:00:00Z", "Completada", puedePagos = true))
        assertTrue(ofreceComprobanteCita("Evaluación", 80.0, "x", "Confirmada", puedePagos = true))
        // Sin permiso 'pagos', sin cobrar, sin costo, sesión o cancelada: no.
        assertFalse(ofreceComprobanteCita("Consulta", 80.0, "x", "Completada", puedePagos = false))
        assertFalse(ofreceComprobanteCita("Consulta", 80.0, null, "Completada", puedePagos = true))
        assertFalse(ofreceComprobanteCita("Consulta", 0.0, "x", "Completada", puedePagos = true))
        assertFalse(ofreceComprobanteCita("Sesión", 80.0, "x", "Completada", puedePagos = true))
        assertFalse(ofreceComprobanteCita("Consulta", 80.0, "x", "Cancelada", puedePagos = true))
    }

    @Test
    fun elErrorEsElMensajeDelServidor() {
        assertEquals("Esta cita todavía no se cobró", mensajeErrorRecibo("""{"error":"Esta cita todavía no se cobró"}"""))
        assertEquals("No se pudo generar el recibo", mensajeErrorRecibo("""{"ok":false}"""))
        assertEquals("No se pudo generar el recibo", mensajeErrorRecibo("<html>502</html>"))
        assertEquals("No se pudo generar el recibo", mensajeErrorRecibo(null))
    }
}
