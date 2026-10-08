package pe.saniape.app.data

import pe.saniape.app.data.staff.*
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Gemelo de los tests de agruparPresupuesto de la web (odontograma.test.ts / auditoria-odonto.test.ts). */
class PresupuestoDentalTest {
    private fun hal(id: String = "h1", nombre: String = "Caries", color: String = "#dc2626", proc: String? = "p1",
                    ausente: Boolean = false, boca: Boolean = false) =
        HallazgoDental(id = id, nombre = nombre, color = color, procedimientoId = proc, marcaAusente = ausente, porBoca = boca)
    private fun reg(id: String = "r1", diente: String = "24", hallazgo: String = "h1", sup: List<String>? = null,
                    fecha: String = "2026-09-01") =
        DienteHallazgo(id = id, pacienteId = "pa", diente = diente, hallazgoId = hallazgo, superficies = sup, estado = "Pendiente", fecha = fecha)
    private fun proc(id: String, nombre: String, precio: Double, caras: Map<String, Double>? = null) =
        ProcedimientoRef(id, nombre, precio, null, null, false, emptyList(), precioPorCaras = caras)

    @Test fun dosCariesEnLaMismaPiezaSeCobranUnaVez() {
        val (l, _) = agruparPresupuesto(
            listOf(reg(diente = "26", sup = listOf("M")), reg(id = "r2", diente = "26", sup = listOf("O"))),
            listOf(hal()), listOf(proc("p1", "Resina simple", 180.0)))
        assertEquals(listOf("26"), l[0].piezas)
        assertEquals(180.0, l[0].subtotal)
        assertEquals(listOf("r1", "r2"), l[0].hallazgoIds)
    }

    @Test fun agrupaPorServicioYMultiplicaPorPiezas() {
        val (l, _) = agruparPresupuesto(listOf(reg(), reg(id = "r2", diente = "36")), listOf(hal()), listOf(proc("p1", "Resina simple", 180.0)))
        assertEquals(360.0, l[0].subtotal)
    }

    @Test fun sugiereElServicioPorNombreSiElHallazgoNoTieneUno() {
        val (l, _) = agruparPresupuesto(listOf(reg(diente = "16")), listOf(hal(proc = null)), listOf(proc("c", "Curación con resina", 120.0)))
        assertEquals("c", l[0].procedimientoId)
        assertEquals(120.0, l[0].subtotal)
    }

    @Test fun laSugerenciaSoloSaleDeLosServiciosDentales() {
        val todos = listOf(proc("facial", "Limpieza facial", 80.0), proc("prof", "Profilaxis dental", 120.0))
        val (l, _) = agruparPresupuesto(listOf(reg(hallazgo = "s")), listOf(hal(id = "s", nombre = "Sarro", proc = null)), todos, listOf(todos[1]))
        assertEquals("prof", l[0].procedimientoId)
    }

    @Test fun hallazgoSinServicioVaAparte() {
        val (l, sin) = agruparPresupuesto(listOf(reg(hallazgo = "x")), listOf(hal(id = "x", nombre = "Sarro", proc = null)), listOf(proc("p1", "Resina", 100.0)))
        assertTrue(l.isEmpty()); assertEquals(1, sin.size)
    }

    @Test fun hallazgoDeBocaSeCobraUnaVezSinImportarElOrden() {
        val cat = listOf(hal("a", "Gingivitis", proc = "limp", boca = true), hal("b", "Placa", proc = "limp"))
        val filas = listOf(reg("1", "BOCA", "a"), reg("2", "16", "b"), reg("3", "26", "b"))
        val procs = listOf(proc("limp", "Profilaxis", 120.0))
        assertEquals(120.0, agruparPresupuesto(filas, cat, procs).first[0].subtotal)
        assertEquals(120.0, agruparPresupuesto(filas.reversed(), cat, procs).first[0].subtotal)
    }

    @Test fun piezaAusenteNoCobraLoDeAntesPeroSiLoDeDespues() {
        val cat = listOf(hal("caries"), hal("ausente", "Ausente", color = "#6b7280", proc = null, ausente = true))
        val filas = listOf(
            reg("c", "36", "caries", fecha = "2026-09-01"),
            reg("x", "36", "ausente", fecha = "2026-09-10"),
            reg("c2", "36", "caries", fecha = "2026-09-20"),
        )
        assertEquals(listOf("c2"), agruparPresupuesto(filas, cat, listOf(proc("p1", "Resina", 90.0))).first[0].hallazgoIds)
    }

    @Test fun trabajoPrevioAzulSinServicioNoSeCobra() {
        val cat = listOf(hal("corona", "Corona existente", color = "#1d6fa8", proc = null))
        val (l, sin) = agruparPresupuesto(listOf(reg(hallazgo = "corona")), cat, listOf(proc("c", "Corona de porcelana", 650.0)))
        assertTrue(l.isEmpty()); assertTrue(sin.isEmpty())
    }

    @Test fun precioSegunCarasPorPieza() {
        val tramos = mapOf("1" to 80.0, "2" to 110.0, "3" to 140.0)
        assertEquals(80.0, precioSegunCaras(tramos, 1))
        assertEquals(140.0, precioSegunCaras(tramos, 4))
        assertEquals(140.0, precioSegunCaras(tramos, 0))
        assertEquals(80.0, precioSegunCaras(mapOf("1" to 80.0), 3))
        assertNull(precioSegunCaras(null, 2))
        val (l, _) = agruparPresupuesto(
            listOf(reg("a", "15", sup = listOf("O")), reg("b", "24", sup = listOf("M")), reg("c", "24", sup = listOf("O")), reg("d", "46", sup = listOf("M", "O", "D"))),
            listOf(hal(proc = "res")), listOf(proc("res", "Resina", 90.0, tramos)))
        assertEquals(listOf(PrecioPieza("15", 1, 80.0), PrecioPieza("24", 2, 110.0), PrecioPieza("46", 3, 140.0)), l[0].preciosPieza)
        assertEquals(330.0, l[0].subtotal)
    }

    @Test fun sinTramosSeUsaElPrecioUnico() {
        val (l, _) = agruparPresupuesto(listOf(reg("a", "46", sup = listOf("M", "O", "D"))), listOf(hal(proc = "res")), listOf(proc("res", "Resina", 90.0)))
        assertNull(l[0].preciosPieza); assertEquals(90.0, l[0].subtotal)
    }
}
