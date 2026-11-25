package pe.saniape.app.data.staff

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Gemelo de lib/__tests__/piezas-tratamiento-directo.test.ts y unidades-precio (web, 24/11/2026). */
class PiezasTratamientoDirectoTest {
    private fun cat(id: String, nombre: String = "Caries", proc: String? = "resina", ausente: Boolean = false, color: String = "#dc2626") =
        HallazgoDental(id = id, nombre = nombre, color = color, procedimientoId = proc, marcaAusente = ausente)
    private val catalogo = listOf(
        cat("caries"), cat("ausente", "Ausente", null, ausente = true), cat("endo", "Endodoncia indicada", "endodoncia"),
        cat("corona", "Corona existente", null, color = COLOR_REALIZADO),
    )
    private fun h(id: String, diente: String, hal: String = "caries", trat: String? = null, estado: String = "Pendiente", hasta: String? = null) =
        DienteHallazgo(id = id, pacienteId = "p", diente = diente, hallazgoId = hal, estado = estado, tratamientoId = trat, dienteHasta = hasta)
    private val hallazgos = listOf(
        h("a", "25"), h("b", "17"), h("c", "36", "endo"), h("d", "38", "ausente"), h("e", "11", trat = "otro"),
        h("f", "12", estado = "Realizado"), h("g", "13", "endo", hasta = "23"), h("z", "37", "corona"),
    )

    @Test fun soloPendientesSinTratamientoNiAusenteNiTrabajoPrevio() {
        assertEquals(listOf("b", "a", "g", "c"), piezasPendientes(hallazgos, catalogo, "resina").map { it.id })
    }

    @Test fun primeroLasDelServicioYEsasVienenMarcadas() {
        val r = piezasPendientes(hallazgos, catalogo, "resina")
        assertEquals(listOf("b", "a"), piezasSugeridas(r))
        assertEquals(emptyList(), piezasSugeridas(piezasPendientes(hallazgos, catalogo, null)))
        assertEquals("13–23", r.first { it.id == "g" }.etiquetaDiente)
        assertEquals("endodoncia", r.first { it.id == "c" }.procedimientoId)
    }

    @Test fun totalPorPiezas() {
        assertEquals(270.0, totalPorPiezas(3, 90.0))
        assertEquals(0.0, totalPorPiezas(0, 90.0))
        assertEquals(0.0, totalPorPiezas(2, null))
    }

    @Test fun enOdontologiaElPasoPrevioEsElDiagnostico() {
        assertEquals("Diagnóstico", nombrePasoPrevio(true, "Evaluación"))
        assertEquals("Diagnóstico inicial", nombrePasoPrevio(true, "Diagnóstico inicial"))
        assertEquals("Evaluación", nombrePasoPrevio(false, ""))
    }

    @Test fun zonasPiezasYUnasNacenEnUno() {
        for (u in listOf("zonas", "Zona", "piezas", "uñas", "áreas", "lesiones")) {
            assertTrue(unidadSeCuentaAntes(u), u); assertEquals("1", cantidadInicialUnidades(u), u)
        }
        for (u in listOf("unidades", "U", "ml", "folículos", "", null)) {
            assertFalse(unidadSeCuentaAntes(u), u.toString()); assertEquals("", cantidadInicialUnidades(u), u.toString())
        }
    }
}
