package pe.saniape.app.data.staff

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** Sede del paciente al crearlo: solo con la opción por clínica y una sede puntual activa. */
class PacientesPorSedeTest {

    @Test
    fun sinLaOpcionNoSeManda() {
        assertNull(sedeParaPacienteNuevo(false, "s1"))
    }

    @Test
    fun conOpcionYSedeActivaSeManda() {
        assertEquals("s1", sedeParaPacienteNuevo(true, "s1"))
    }

    @Test
    fun consolidadoOSinSedeNoSeManda() {
        assertNull(sedeParaPacienteNuevo(true, ""))
        assertNull(sedeParaPacienteNuevo(true, null))
    }

    @Test
    fun elContextoPorDefectoEsFalse() {
        assertEquals(false, EstadoSede().pacientesPorSede)
    }
}
