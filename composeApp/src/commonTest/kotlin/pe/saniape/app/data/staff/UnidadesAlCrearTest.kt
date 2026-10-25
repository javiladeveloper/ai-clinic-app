package pe.saniape.app.data.staff

import pe.saniape.app.data.TratPago
import pe.saniape.app.data.estadoPagoDe
import pe.saniape.app.data.montoAcordado
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** E3: la cantidad de un tratamiento por unidades es opcional al crearlo, sin inventar dinero. */
class UnidadesAlCrearTest {

    @Test
    fun laCantidadVaciaNoBloqueaElGuardado() {
        // Basta el precio por unidad; la cantidad ya no se pide.
        assertTrue(puedeCrearPorUnidades("12"))
        assertTrue(puedeCrearPorUnidades("1.5"))
        // Sin precio por unidad sigue sin poder crearse (como antes).
        assertFalse(puedeCrearPorUnidades(""))
        assertFalse(puedeCrearPorUnidades("0"))
        assertFalse(puedeCrearPorUnidades("abc"))
    }

    @Test
    fun cantidadVaciaCeroOBasuraSeGuardaComoNull() {
        assertNull(cantidadUnidadesAGuardar(""))
        assertNull(cantidadUnidadesAGuardar("   "))
        assertNull(cantidadUnidadesAGuardar("0"))
        assertNull(cantidadUnidadesAGuardar("1.5"))
        assertEquals(50, cantidadUnidadesAGuardar("50"))
        assertEquals(4000, cantidadUnidadesAGuardar(" 4000 "))
    }

    @Test
    fun sinCantidadNiAcordadoNaceSinMonto() {
        assertNull(acordadoUnidadesAlCrear("", null, 12.0))
        assertNull(acordadoUnidadesAlCrear("", null, null))
    }

    @Test
    fun conCantidadElAcordadoEsCantidadPorPrecioComoAntes() {
        assertEquals(600.0, acordadoUnidadesAlCrear("", 50, 12.0))
        assertEquals(6000.0, acordadoUnidadesAlCrear("", 4000, 1.5))
    }

    @Test
    fun elAcordadoEscritoMandaConOSinCantidad() {
        assertEquals(500.0, acordadoUnidadesAlCrear("500", 50, 12.0))
        assertEquals(1500.0, acordadoUnidadesAlCrear("1500", null, 12.0))
        // Un 0 escrito es gratis a propósito (precio negociable).
        assertEquals(0.0, acordadoUnidadesAlCrear("0", 50, 12.0))
    }

    @Test
    fun sinMontoNoQuedaPagadoNiConDeuda() {
        // Lo que guarda el servidor sin cantidad ni acordado: acordado 0 → ni
        // "Pagado" (no se cobró nada) ni una deuda inventada.
        val t = TratPago(modalidad = "Unidades", cantidadUnidades = null, precioUnitario = 12.0, precioAcordado = null)
        assertEquals(0.0, montoAcordado(t))
        assertEquals("Pendiente", estadoPagoDe(0.0, montoAcordado(t)))
        // Completada luego la cantidad en el mapa: el monto aparece.
        val completado = t.copy(cantidadUnidades = 50)
        assertEquals(600.0, montoAcordado(completado))
        assertEquals("Parcial", estadoPagoDe(100.0, montoAcordado(completado)))
    }
}
