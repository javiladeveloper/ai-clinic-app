package pe.saniape.app.data

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import pe.saniape.app.data.staff.COLOR_REALIZADO
import pe.saniape.app.data.staff.DienteHallazgo
import pe.saniape.app.data.staff.HallazgoDental
import pe.saniape.app.data.staff.calcularCPO
import pe.saniape.app.data.staff.contarPlanPiezas
import pe.saniape.app.data.staff.esDePiezaEntera
import pe.saniape.app.data.staff.filasPiezasTratadas
import pe.saniape.app.data.staff.filasPlanPiezas
import pe.saniape.app.data.staff.ordenarCaras
import pe.saniape.app.data.staff.pintarDiente
import pe.saniape.app.data.staff.sumarTecnica

/**
 * Piezas por sesión, sumar caras y símbolos de la norma: gemelos de
 * PiezasTratadas.tsx / PlanPiezas.tsx / DientePanel.tsx / lib/odontograma.ts.
 */
private val CAT = listOf(
    HallazgoDental(id = "c", nombre = "Caries", color = "#dc2626", procedimientoId = "P2"),
    HallazgoDental(id = "a", nombre = "Ausente", color = "#6b7280", marcaAusente = true),
    HallazgoDental(id = "x", nombre = "Corona existente", color = COLOR_REALIZADO),
    HallazgoDental(id = "k", nombre = "Corona", color = "#dc2626"),
    HallazgoDental(id = "e", nombre = "Extracción indicada", color = "#dc2626"),
    HallazgoDental(id = "n", nombre = "Endodoncia indicada", color = "#dc2626"),
)

private fun h(
    id: String, diente: String, hallazgoId: String, estado: String = "Pendiente",
    superficies: List<String>? = null, tratamientoId: String? = null, sesionId: String? = null,
) = DienteHallazgo(
    id = id, pacienteId = "p", diente = diente, hallazgoId = hallazgoId, superficies = superficies,
    estado = estado, tratamientoId = tratamientoId, sesionId = sesionId,
)

class PiezasPorSesionTest {
    @Test
    fun caras_en_orden_fijo() {
        assertEquals(listOf("O", "M"), ordenarCaras(listOf("M", "O")))
        assertEquals(listOf("O", "M", "D", "V", "L"), ordenarCaras(setOf("L", "V", "D", "M", "O")))
    }

    @Test
    fun que_se_le_hizo_hoy_lista_lo_pendiente_sin_ausentes_ni_azules() {
        val filas = filasPiezasTratadas(
            listOf(
                h("1", "26", "c", tratamientoId = "otro"),
                h("2", "16", "c", tratamientoId = "T"),
                h("3", "46", "a"),                          // ausente: fuera
                h("4", "11", "x"),                          // corona existente (azul): fuera
                h("5", "36", "c", estado = "Realizado"),    // hecha en otra sesión: fuera
                h("6", "37", "c", estado = "Realizado", sesionId = "S"),   // de ESTA sesión: dentro
            ),
            CAT, sesionId = "S", tratamientoId = "T",
        )
        // Primero las del tratamiento de la sesión, luego por número de pieza.
        assertEquals(listOf("2", "1", "6"), filas.map { it.id })
    }

    @Test
    fun plan_cuenta_piezas_no_registros() {
        val todos = listOf(
            h("1", "16", "c", tratamientoId = "T", superficies = listOf("O")),
            h("2", "16", "c", tratamientoId = "T", superficies = listOf("M")),   // misma pieza: una resina
            h("3", "26", "c", estado = "Realizado", sesionId = "S1"),              // otro plan, hecho por su sesión
            h("4", "36", "c", tratamientoId = "OTRO"),                              // no es de este plan
        )
        val filas = filasPlanPiezas(todos, "T", setOf("S1"))
        assertEquals(3, filas.size)
        assertEquals(1 to 2, contarPlanPiezas(filas))
    }

    @Test
    fun sumar_tecnica_no_duplica() {
        assertEquals("Resina", sumarTecnica("", "Resina"))
        assertEquals("Anestesia + Resina", sumarTecnica("Anestesia", "Resina"))
        assertEquals("Anestesia + Resina", sumarTecnica("Anestesia + Resina", "resina"))
    }

    @Test
    fun pieza_entera_no_suma_caras() {
        assertTrue(esDePiezaEntera(CAT.first { it.id == "k" }))
        assertTrue(esDePiezaEntera(CAT.first { it.id == "a" }))
        assertTrue(!esDePiezaEntera(CAT.first { it.id == "c" }))
    }
}

class SimbolosNormaTest {
    @Test
    fun caries_tratada_se_lee_restaurada() {
        val p = pintarDiente(listOf(h("1", "16", "c", estado = "Realizado", superficies = listOf("O"))), CAT)
        assertEquals(COLOR_REALIZADO, p.porSuperficie["O"])
        assertEquals(listOf("R"), p.siglas)
    }

    @Test
    fun corona_es_circulo_no_pinta_caras() {
        val p = pintarDiente(listOf(h("1", "16", "k")), CAT)
        assertEquals("#dc2626", p.corona)
        assertTrue(p.porSuperficie.values.all { it == null })
    }

    @Test
    fun extraccion_indicada_aspa_roja_hecha_ausente() {
        assertEquals("#dc2626", pintarDiente(listOf(h("1", "16", "e")), CAT).extraccion)
        assertTrue(pintarDiente(listOf(h("1", "16", "e", estado = "Realizado")), CAT).ausente)
    }

    @Test
    fun endodoncia_hecha_lleva_TC() {
        val p = pintarDiente(listOf(h("1", "16", "n", estado = "Realizado")), CAT)
        assertEquals(COLOR_REALIZADO, p.endodoncia)
        assertEquals(listOf("TC"), p.siglas)
    }

    @Test
    fun cpo_cuenta_cada_pieza_una_vez() {
        val cpo = calcularCPO(
            listOf(
                h("1", "16", "c"), h("2", "16", "c", estado = "Realizado"),  // cariada manda
                h("3", "26", "c", estado = "Realizado"),                      // obturada
                h("4", "46", "a"),                                            // perdida
                h("5", "55", "c"),                                            // temporal: no cuenta en CPO-D
            ),
            CAT, permanente = true,
        )
        assertEquals(1, cpo.c); assertEquals(1, cpo.p); assertEquals(1, cpo.o); assertEquals(3, cpo.total)
    }
}
