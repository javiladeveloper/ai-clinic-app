package pe.saniape.app.ui.clinica.atencion

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** Gemelo de lib/__tests__/receta-posologia.test.ts (web, 19/11/2026). */
class RecetaPosologiaTest {
    @Test
    fun dosis() {
        assertEquals("1 tableta", textoDosis("1", "tableta"))
        assertEquals("2 gotas", textoDosis("2", "gota"))
        assertEquals("½ tableta", textoDosis("½", "tableta"))
        assertEquals("1" to "gota", leerDosis("1 GOTA"))
        assertEquals("1" to "tableta", leerDosis("1 TAB"))
        assertEquals("½" to "tableta", leerDosis("1/2 tableta"))
        assertNull(leerDosis("APLICAR EN SACO CONJUNTIVAL"))
        assertEquals("gota", unidadDosisDeForma("Solución oftálmica"))
        assertEquals("ml", unidadDosisDeForma("Jarabe"))
    }

    @Test
    fun frecuenciaYDuracion() {
        assertEquals("cada 8 horas", frecuenciaDeLista("8"))
        assertEquals("cada 8 horas", frecuenciaDeLista("c/8h"))
        assertEquals("1 vez al día", frecuenciaDeLista("una vez al día"))
        assertEquals(3.0, tomasPorDia("8"))
        assertEquals("1 semana", textoDuracion("1", "semanas"))
        assertEquals(DuracionLeida("6", "semanas", false), leerDuracion("por 6 semanas"))
    }

    @Test
    fun cantidad() {
        assertEquals(21, cantidadAutomatica("1", "8", "7 días", "Tableta"))
        assertEquals(1, cantidadAutomatica("1 gota", "cada 8 horas", "uso continuo", "Solución oftálmica"))
        assertEquals(1, cantidadAutomatica("5 ml", "cada 8 horas", "7 días", "Jarabe"))
        assertEquals(0.25, unidadesPorToma("¼ tableta", "Tableta"))
    }

    @Test
    fun envases() {
        assertEquals(2, cantidadAutomatica("5 ml", "cada 8 horas", "10 días", "Jarabe"))
        assertEquals(2, cantidadAutomatica("500 mg", "cada 8 horas", "7 días", "Suspensión", "250mg/5mL"))
        assertEquals(2, cantidadAutomatica("20 gotas", "cada 8 horas", "10 días", "Gotas orales"))
        assertEquals(2, cantidadAutomatica("2 inhalaciones", "cada 6 horas", "30 días", "Inhalador"))
        assertEquals(1, cantidadAutomatica("5 ml", "si hay dolor", "5 días", "Jarabe"))
    }
}
