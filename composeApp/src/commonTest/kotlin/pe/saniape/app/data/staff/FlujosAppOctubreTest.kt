package pe.saniape.app.data.staff

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Gemelos de los arreglos de la web del 30–31/10/2026 que pasan a la app:
 * resumen corto del examen oftalmológico (Cierre), la frase del patrón del plan
 * entero y los paquetes del servicio en el plan tras la evaluación.
 */
class FlujosAppOctubreTest {

    private fun examenNormal(): ExamenOftalmo {
        val e = examenVacio()
        return e.copy(
            av = e.av.copy(lejosCc = OjosTxt("20/20-2", "20/20")),
            pio = PioOftalmo(od = 14.0, oi = 12.0),
            subjetiva = SubjetivaOftalmo(
                od = RefraccionAv(esf = 0.0, cil = -0.5, eje = 70.0, av = "20/20-2"),
                oi = RefraccionAv(esf = 0.0, cil = -0.25, eje = 135.0, av = "20/20"),
                addCerca = 2.0, dipLejos = 62.0, dipCerca = 60.0,
            ),
            bio = NORMAL_BIO.mapValues { (_, v) -> OjosTxt(v, v) },
            fondo = NORMAL_FONDO.mapValues { (_, v) -> OjosTxt(v, v) },
        )
    }

    @Test fun resumenDelExamenEsCortoYJuntaLoNormal() {
        val r = resumenExamenOftalmo(examenNormal())
        assertTrue(r.first().startsWith("AV lejos: CC OD 20/20-2 · OI 20/20"), r.first())
        assertTrue(r.any { it.startsWith("PIO: OD 14 mmHg · OI 12 mmHg") }, r.toString())
        assertTrue(r.any { it.startsWith("Refracción subjetiva:") && "ADD cerca +2.00" in it && "DIP 62/60" in it }, r.toString())
        assertEquals("Anexos, biomicroscopía y fondo de ojo: normales", r.last())
        assertTrue(r.size < 6)
        assertEquals(emptyList<String>(), resumenExamenOftalmo(null))
    }

    @Test fun loAlteradoSaleConSuOjo() {
        val e = examenNormal()
        val r = resumenExamenOftalmo(e.copy(bio = e.bio + ("cristalino" to OjosTxt("Catarata nuclear NO3", NORMAL_BIO.getValue("cristalino")))))
        assertTrue("Cristalino: OD Catarata nuclear NO3 · OI Transparente" in r, r.toString())
        assertEquals("Lo demás de anexos, biomicroscopía y fondo de ojo: normal", r.last())
    }

    @Test fun fraseDelPatronEnElOrdenDeLaSemana() {
        assertEquals("Cada lunes, miércoles y viernes a las 9:00 a. m., desde el lun 02/11.",
            fraseDelPatron(setOf(5, 1, 3), "9:00 a. m.", "2026-11-02"))
        assertEquals("Cada sábado a las 4:00 p. m.", fraseDelPatron(setOf(6), "4:00 p. m.", ""))
        assertEquals("", fraseDelPatron(emptySet(), "9:00 a. m.", "2026-11-02"))
    }

    @Test fun paquetesDelPlanDeMenorAMayor() {
        val s = ServicioDelPlan(id = "s", precio = 50.0, tarifarios = listOf(10 to 450.0, 5 to 240.0))
        assertEquals(listOf(5 to 240.0, 10 to 450.0), paquetesDelPlan(s))
        // Sin tarifario: el precio paquete del servicio es de 10 sesiones.
        assertEquals(listOf(10 to 400.0), paquetesDelPlan(ServicioDelPlan(id = "s", precioPaquete = 400.0)))
        assertEquals(emptyList<Pair<Int, Double>>(), paquetesDelPlan(ServicioDelPlan(id = "s", precio = 50.0)))
        assertEquals(emptyList<Pair<Int, Double>>(), paquetesDelPlan(null))
    }
}
