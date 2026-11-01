package pe.saniape.app.data.staff

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Salón: la «Atención» es el servicio, agrupado por categoría; no evalúa. */
class ServiciosSalonTest {
    @Test
    fun agrupaPorCategoriaEnElOrdenDeAparicion() {
        val l = listOf(
            ServicioSalon("1", "Pedicura spa", "Pedicura", 50.0, null),
            ServicioSalon("2", "Manicura clásica", "Manicura", 20.0, null),
            ServicioSalon("3", "Pedicura clásica", "Pedicura", 25.0, null),
        )
        assertEquals(listOf("3", "1", "2"), ordenarServiciosSalon(l).map { it.id })
    }

    @Test
    fun precioSinDecimalesDeMas() {
        assertEquals("35", precioSalonTexto(35.0))
        assertEquals("35.50", precioSalonTexto(35.5))
    }

    @Test
    fun laAtencionDelSalonNoEvalua() {
        val salon = FlujoClinica(usaConsulta = true, usaEvaluacion = false, labelConsulta = "Atención", atencionDirecta = true)
        assertFalse(salon.esCitaQueEvalua("Consulta"))
        assertTrue(FlujoClinica(usaConsulta = true, usaEvaluacion = false).esCitaQueEvalua("Consulta"))
    }
}
