package pe.saniape.app.data.staff

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Fila de pago: un solo botón (🧾 o ⋯) con comprobante · corregir · 🗑 al final. */
class AccionesPagoTest {

    @Test
    fun adminVeTodoEnUnMenuConBorrarAlFinal() {
        val p = accionesPago(comprobante = true, editar = true, borrar = true)
        assertNull(p.primaria)
        assertTrue(p.secundarias.isEmpty())
        assertEquals(
            listOf(
                GrupoMenu("Comprobante", listOf(AccionPago.ComprobanteA4, AccionPago.Ticket)),
                GrupoMenu("Corregir", listOf(AccionPago.Editar)),
                GrupoMenu(GRUPO_PELIGRO, listOf(AccionPago.Borrar)),
            ),
            p.menu,
        )
    }

    @Test
    fun recepcionSoloElComprobante() {
        assertEquals(listOf(AccionPago.ComprobanteA4, AccionPago.Ticket), accionesPago(true, editar = false, borrar = false).enMenu)
    }

    @Test
    fun consumoDeSaldoSinComprobanteElAdminIgualCorrigeOBorra() {
        assertEquals(listOf(AccionPago.Editar, AccionPago.Borrar), accionesPago(false, editar = true, borrar = true).enMenu)
        // El pago agrupado sin parte de saldo: solo se borra.
        assertEquals(listOf(AccionPago.Borrar), accionesPago(false, editar = false, borrar = true).enMenu)
    }

    @Test
    fun sinNadaNoHayBoton() {
        assertTrue(accionesPago(false, editar = false, borrar = false).menu.isEmpty())
    }
}
