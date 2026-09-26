package pe.saniape.app.data

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertSame
import pe.saniape.app.data.staff.LISTA_TESTS
import pe.saniape.app.data.staff.TESTS_FUNCIONALES
import pe.saniape.app.data.staff.calcularPuntaje
import pe.saniape.app.data.staff.interpretar
import pe.saniape.app.data.staff.textoPuntaje

/**
 * Gemelo de `lib/__tests__/tests-funcionales.test.ts` de la web: los MISMOS casos
 * y los MISMOS puntajes. Si una fórmula se desvía, un test cargado en la app daría
 * otro número que en la web.
 */
class TestsFuncionalesTest {

    private fun fill(n: Int, v: Int?): List<Int?> = List(n) { v }

    // ── Catálogo ──
    @Test fun oswestryYNdiTienen10SeccionesDe6Opciones0a5() {
        for (id in listOf("oswestry", "ndi")) {
            val t = TESTS_FUNCIONALES.getValue(id)
            assertEquals(10, t.preguntas.size)
            for (p in t.preguntas) assertEquals(listOf(0, 1, 2, 3, 4, 5), p.opciones.map { it.valor })
        }
    }

    @Test fun quickDashTiene11ItemsDe1a5() {
        val t = TESTS_FUNCIONALES.getValue("quickdash")
        assertEquals(11, t.preguntas.size)
        for (p in t.preguntas) assertEquals(listOf(1, 2, 3, 4, 5), p.opciones.map { it.valor })
    }

    @Test fun lysholmMaximosSuman100YMinimos0() {
        val t = TESTS_FUNCIONALES.getValue("lysholm")
        assertEquals(8, t.preguntas.size)
        assertEquals(100, t.preguntas.sumOf { p -> p.opciones.maxOf { it.valor } })
        assertEquals(0, t.preguntas.sumOf { p -> p.opciones.minOf { it.valor } })
    }

    @Test fun todosLosIdsDeLaListaEstanEnElMapa() {
        for (t in LISTA_TESTS) assertSame(t, TESTS_FUNCIONALES[t.id])
    }

    // ── Oswestry ──
    @Test fun oswestryExtremos() {
        assertEquals(0.0, calcularPuntaje("oswestry", fill(10, 0)).puntaje)
        assertEquals(100.0, calcularPuntaje("oswestry", fill(10, 5)).puntaje)
    }

    @Test fun oswestrySuma17de50Da34() {
        val p = calcularPuntaje("oswestry", listOf(2, 1, 2, 3, 2, 2, 1, 1, 2, 1))
        assertEquals(34.0, p.puntaje)
        assertEquals("Limitación moderada", p.interpretacion)
    }

    @Test fun oswestrySinVidaSexualDivideEntre45() {
        val p = calcularPuntaje("oswestry", listOf(2, 1, 2, 3, 2, 2, 1, null, 2, 1)) // suma 16
        assertEquals(35.6, p.puntaje)
    }

    @Test fun oswestryConMenosDe8NoEsValido() {
        val p = calcularPuntaje("oswestry", listOf(3, 3, 3, 3, 3, 3, 3, null, null, null))
        assertNull(p.puntaje)
        assertEquals(7, p.contestadas)
    }

    @Test fun oswestryCortes() {
        assertEquals("Limitación mínima", interpretar("oswestry", 20.0))
        assertEquals("Limitación moderada", interpretar("oswestry", 21.0))
        assertEquals("Limitación intensa", interpretar("oswestry", 60.0))
        assertEquals("Discapacidad", interpretar("oswestry", 80.0))
        assertEquals("Limitación máxima", interpretar("oswestry", 81.0))
    }

    // ── NDI ──
    @Test fun ndiSuma14Da28Leve() {
        val p = calcularPuntaje("ndi", listOf(2, 1, 2, 1, 2, 1, 2, 1, 1, 1))
        assertEquals(28.0, p.puntaje)
        assertEquals("Discapacidad leve", p.interpretacion)
    }

    @Test fun ndiCortesDeVernon() {
        assertEquals("Sin discapacidad", interpretar("ndi", 8.0))
        assertEquals("Discapacidad leve", interpretar("ndi", 10.0))
        assertEquals("Discapacidad moderada", interpretar("ndi", 30.0))
        assertEquals("Discapacidad grave", interpretar("ndi", 50.0))
        assertEquals("Discapacidad completa", interpretar("ndi", 70.0))
    }

    @Test fun ndiAdmiteUnoSinContestarNoDos() {
        assertEquals(20.0, calcularPuntaje("ndi", listOf(1, 1, 1, 1, 1, 1, 1, null, 1, 1)).puntaje)
        assertNull(calcularPuntaje("ndi", listOf(1, 1, 1, 1, 1, 1, null, null, 1, 1)).puntaje)
    }

    // ── QuickDASH ──
    @Test fun quickDashExtremos() {
        assertEquals(0.0, calcularPuntaje("quickdash", fill(11, 1)).puntaje)
        assertEquals(100.0, calcularPuntaje("quickdash", fill(11, 5)).puntaje)
    }

    @Test fun quickDashFormula() {
        assertEquals(50.0, calcularPuntaje("quickdash", fill(11, 3)).puntaje)
        val p = calcularPuntaje("quickdash", listOf(3, 3, 2, 3, 2, 3, 2, 2, 2, 2, 2))
        assertEquals(34.1, p.puntaje)
        assertEquals("Discapacidad moderada", p.interpretacion)
    }

    @Test fun quickDashCon10de11SiCon9No() {
        assertEquals(50.0, calcularPuntaje("quickdash", fill(10, 3) + listOf(null)).puntaje)
        assertNull(calcularPuntaje("quickdash", fill(9, 3) + listOf(null, null)).puntaje)
    }

    // ── Lysholm ──
    @Test fun lysholmExtremos() {
        val t = TESTS_FUNCIONALES.getValue("lysholm")
        val mejor = calcularPuntaje("lysholm", t.preguntas.map { p -> p.opciones.maxOf { it.valor } })
        assertEquals(100.0, mejor.puntaje)
        assertEquals("Excelente", mejor.interpretacion)
        assertEquals(0.0, calcularPuntaje("lysholm", t.preguntas.map { p -> p.opciones.minOf { it.valor } }).puntaje)
    }

    @Test fun lysholmSumaDirecta() {
        val r52 = calcularPuntaje("lysholm", listOf(3, 5, 10, 15, 5, 6, 6, 2))
        assertEquals(52.0, r52.puntaje)
        assertEquals("Malo", r52.interpretacion)
        val r86 = calcularPuntaje("lysholm", listOf(5, 5, 15, 20, 20, 6, 10, 5))
        assertEquals(86.0, r86.puntaje)
        assertEquals("Bueno", r86.interpretacion)
    }

    @Test fun lysholmIncompletoNoDaPuntaje() {
        assertNull(calcularPuntaje("lysholm", listOf(5, 5, 15, 25, 25, 10, 10, null)).puntaje)
    }

    @Test fun textoDelPuntajeComoLaWeb() {
        assertEquals("Oswestry 34 %", textoPuntaje("oswestry", 34.0))
        assertEquals("QuickDASH 34.1 pts", textoPuntaje("quickdash", 34.1))
        assertEquals("Lysholm: incompleto", textoPuntaje("lysholm", null))
    }
}
