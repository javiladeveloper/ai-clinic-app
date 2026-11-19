package pe.saniape.app.data.staff

import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Gemelo de lib/__tests__ de finanzas-comparacion (web, 18/11/2026). */
class FinanzasComparacionTest {
    private val hoy = LocalDate(2026, 11, 18)   // miércoles

    @Test
    fun mesContraElMesPasadoALaMismaFecha() {
        assertEquals(PeriodoAnterior("2026-10-01", "2026-10-18", "vs. el mes pasado a la misma fecha"), periodoAnterior(PeriodoFinanzas.Mes, hoy, null))
        // 31 de marzo → febrero hasta el 28.
        assertEquals("2026-02-28", periodoAnterior(PeriodoFinanzas.Mes, LocalDate(2026, 3, 31), null)?.hasta)
        // Enero → diciembre del año anterior.
        assertEquals("2025-12-01", periodoAnterior(PeriodoFinanzas.Mes, LocalDate(2026, 1, 10), null)?.desde)
    }

    @Test
    fun diaSemanaRangoYTotal() {
        assertEquals("2026-11-17", periodoAnterior(PeriodoFinanzas.Dia, hoy, null)?.desde)
        val s = periodoAnterior(PeriodoFinanzas.Semana, hoy, null)!!
        assertEquals("2026-11-09" to "2026-11-11", s.desde to s.hasta)
        val r = periodoAnterior(PeriodoFinanzas.Dia, hoy, "2026-11-10" to "2026-11-16")!!
        assertEquals(PeriodoAnterior("2026-11-03", "2026-11-09", "vs. los 7 días anteriores"), r)
        assertNull(periodoAnterior(PeriodoFinanzas.Total, hoy, null))
    }

    @Test
    fun textoYColor() {
        assertEquals("↑ 8% vs. el mes pasado", textoVariacion(108.0, 100.0, "vs. el mes pasado"))
        assertEquals("↓ 25% vs. ayer", textoVariacion(75.0, 100.0, "vs. ayer"))
        assertEquals("= igual que ayer", textoVariacion(100.0, 100.0, "vs. ayer"))
        assertNull(textoVariacion(50.0, 0.0, "vs. ayer"))
        assertTrue(variacionEsBuena(120.0, 100.0, "ingresos"))
        assertFalse(variacionEsBuena(120.0, 100.0, "egresos"))
    }

    @Test
    fun buscador() {
        val m = listOf(
            MovimientoKardex("1", "Egreso", "Insumos", "Compra de agujas", 50.0, "2026-11-18", "Efectivo", null),
            MovimientoKardex("2", "Ingreso", "Sesión", "Pago sesión #3", 80.0, "2026-11-18", "Yape", "pago:x", pacienteNombre = "Rosa Pérez"),
        )
        assertEquals(listOf("1"), buscarMovimientos(m, "AGUJAS").map { it.id })
        assertEquals(listOf("2"), buscarMovimientos(m, "perez").map { it.id })
        assertEquals(2, buscarMovimientos(m, "  ").size)
    }
}
