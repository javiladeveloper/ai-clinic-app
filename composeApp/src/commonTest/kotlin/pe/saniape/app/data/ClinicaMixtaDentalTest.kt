package pe.saniape.app.data

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import pe.saniape.app.data.staff.MapaDental
import pe.saniape.app.data.staff.citaEsDental
import pe.saniape.app.data.staff.pacienteEsDental

/**
 * Clínica con fisioterapia Y odontología: lo dental se decide por cita y por
 * paciente. Gemelo de `lib/__tests__/clinica-mixta-dental.test.ts` en la web
 * (el mapa lo arma la web; acá se prueban las dos decisiones).
 */
class ClinicaMixtaDentalTest {
    private val mixta = MapaDental(ids = listOf("e-odonto"), solo = false)
    private val soloDental = MapaDental(ids = emptyList(), solo = true)
    private val sinOdonto = MapaDental()

    @Test fun laEspecialidadDeLaCitaManda() {
        assertTrue(citaEsDental(mixta, especialidadId = "e-odonto"))
        assertFalse(citaEsDental(mixta, especialidadId = "e-fisio"))
    }

    @Test fun laDeLaCitaGanaALaDelServicioYAlProfesional() {
        assertFalse(citaEsDental(mixta, "e-fisio", "e-odonto", listOf("e-odonto")))
    }

    @Test fun sinEspecialidadEnLaCitaDecideElServicio() {
        assertTrue(citaEsDental(mixta, especialidadServicioId = "e-odonto"))
        assertFalse(citaEsDental(mixta, especialidadServicioId = "e-fisio", especialidadesProfesional = listOf("e-odonto")))
    }

    @Test fun elProfesionalSoloSiTodasSusEspecialidadesSonDentales() {
        assertTrue(citaEsDental(mixta, especialidadesProfesional = listOf("e-odonto")))
        assertFalse(citaEsDental(mixta, especialidadesProfesional = listOf("e-fisio")))
        assertFalse(citaEsDental(mixta, especialidadesProfesional = listOf("e-fisio", "e-odonto")))
    }

    @Test fun loQueNoSeSabeEsNo() {
        assertFalse(citaEsDental(mixta))
        assertFalse(citaEsDental(mixta, especialidadesProfesional = emptyList()))
    }

    @Test fun clinicaSoloDentalTodaCitaEsDental() {
        assertTrue(citaEsDental(soloDental))
    }

    @Test fun clinicaSinOdontologiaNingunaCitaEsDental() {
        assertFalse(citaEsDental(sinOdonto, especialidadId = "x"))
    }

    @Test fun pacienteDeFisioVistoPorRecepcionSinPestana() {
        assertFalse(pacienteEsDental(mixta, especialidadIds = listOf("e-fisio")))
    }

    @Test fun conAlgoDentalConPestana() {
        assertTrue(pacienteEsDental(mixta, especialidadIds = listOf("e-fisio", "e-odonto")))
    }

    @Test fun conHallazgosConPestana() {
        assertTrue(pacienteEsDental(mixta, tieneHallazgos = true))
    }

    @Test fun elDentistaLaVeSiempre() {
        assertTrue(pacienteEsDental(mixta, especialidadesDeQuienMira = listOf("e-odonto")))
        assertFalse(pacienteEsDental(mixta, especialidadesDeQuienMira = listOf("e-fisio")))
    }

    @Test fun soloDentalTodosSinOdontologiaNadie() {
        assertTrue(pacienteEsDental(soloDental))
        assertFalse(pacienteEsDental(sinOdonto, tieneHallazgos = true))
    }
}
