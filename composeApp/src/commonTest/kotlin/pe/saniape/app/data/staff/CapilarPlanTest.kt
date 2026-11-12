package pe.saniape.app.data.staff

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** Flujo #4 (gemelo de lib/__tests__/capilar-plan): la tricoscopía → «Crear plan con N UF». */
class CapilarPlanTest {
    private fun proc(id: String, nombre: String, modo: String?, unidad: String? = null, precio: Double = 0.0, sugerido: Double? = null) =
        ProcedimientoRef(id, nombre, precio, null, null, false, emptyList(), modoCobro = modo, unidadLabel = unidad, precioUnitarioSugerido = sugerido)

    @Test fun elServicioDeTrasplantePorDatos() {
        val consulta = proc("c", "Consulta capilar", "simple")
        val fue = proc("f", "Trasplante FUE", "unidades", unidad = "UF", precio = 0.0, sugerido = 4.5)
        val prp = proc("p", "PRP capilar", "sesiones")
        assertEquals("f", servicioDeTrasplante(listOf(consulta, prp, fue))?.id)
        // Por nombre si la unidad no lo dice; la unidad folicular manda.
        val porNombre = proc("n", "Injerto capilar", "unidades", unidad = "unidades")
        val porUnidad = proc("u", "Cirugía", "unidades", unidad = "folículos")
        assertEquals("u", servicioDeTrasplante(listOf(porNombre, porUnidad))?.id)
        assertNull(servicioDeTrasplante(listOf(consulta, prp)))
    }

    @Test fun elPrefillTraeUnidadesCantidadYPrecio() {
        val fue = proc("f", "Trasplante FUE", "unidades", unidad = "UF", precio = 5.0, sugerido = 4.5)
        val p = planDesdeTricoscopia(2100, listOf(fue))
        assertEquals("f", p.procedimientoId)
        assertEquals("Unidades", p.modalidad)
        assertEquals(2100, p.cantidadUnidades)
        assertEquals(4.5, p.precioUnitario)
        assertEquals("Trasplante capilar: 2,100 UF estimadas en la tricoscopía", p.diagnostico)
        // Sin servicio de trasplante: igual abre por Unidades con la cantidad.
        val sin = planDesdeTricoscopia(800, emptyList())
        assertNull(sin.procedimientoId)
        assertNull(sin.precioUnitario)
        assertEquals(800, sin.cantidadUnidades)
    }
}

/** I8 (gemelo de presupuesto-rango-por-aparato.test.ts): el puente se cobra por aparato. */
class RangoPorAparatoTest {
    @Test fun unTramoOPiezasVecinasSonUnAparato() {
        assertEquals(1, contarAparatos(listOf("13" to "23")))
        assertEquals(1, contarAparatos(listOf("14" to null, "15" to null, "16" to null)))
        assertEquals(2, contarAparatos(listOf("14" to null, "16" to null)))
        assertEquals(1, contarAparatos(listOf("11" to null, "21" to null)))
        assertEquals(2, contarAparatos(listOf("16" to null, "46" to null)))
        val l = LineaPresupuesto("p", "Puente", "Prótesis fija (puente)", listOf("24", "25", "26"), listOf("a", "b", "c"), 1800.0, 1800.0, aparatos = 1)
        assertEquals(1, unidadesCobro(l))
    }
}
