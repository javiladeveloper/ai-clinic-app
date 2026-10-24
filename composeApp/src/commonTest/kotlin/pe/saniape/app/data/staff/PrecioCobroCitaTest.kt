package pe.saniape.app.data.staff

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * "Precio acordado" del cobro de una Consulta/Evaluación (ModalCobrarCita y el
 * cobro de la consulta guiada): gemelo de costoCobro / precioCambiado /
 * precioCobroInvalido de /citas web. El catálogo es la sugerencia.
 */
class PrecioCobroCitaTest {

    @Test fun sinTocarSeCobraElDeLaCitaYNoViajaAcordado() {
        val p = precioCobroCita(textoLista(80.0, "PEN"), 80.0, "PEN")
        assertEquals(80.0, p.total)
        assertNull(p.costoAcordado)
        assertFalse(p.invalido)
    }

    @Test fun otroPrecioSeCobraYViajaAlServidor() {
        val p = precioCobroCita("60", 80.0, "PEN")
        assertEquals(60.0, p.total)
        assertEquals(60.0, p.costoAcordado)
        // "1 500" es 1500 (leerPrecio), no 1.
        assertEquals(1500.0, precioCobroCita("1 500", 80.0, "PEN").costoAcordado)
        assertEquals(80.5, precioCobroCita("80,50", 80.0, "PEN").costoAcordado)
    }

    @Test fun vacioEsElDeLaCita() {
        val p = precioCobroCita("", 80.0, "PEN")
        assertEquals(80.0, p.total)
        assertNull(p.costoAcordado)
        assertFalse(p.invalido)
    }

    @Test fun ceroLetrasONegativoNoSeCobran() {
        assertTrue(precioCobroCita("0", 80.0, "PEN").invalido)
        assertTrue(precioCobroCita("abc", 80.0, "PEN").invalido)
        assertTrue(precioCobroCita("-5", 80.0, "PEN").invalido)
        // Inválido: el total que se muestra sigue siendo el de la cita.
        assertEquals(80.0, precioCobroCita("abc", 80.0, "PEN").total)
    }
}
