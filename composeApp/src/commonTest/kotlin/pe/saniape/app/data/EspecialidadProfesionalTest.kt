package pe.saniape.app.data

import pe.saniape.app.data.staff.EspecialidadProfesionalRepo
import pe.saniape.app.data.staff.ReglasEspecialidadProfesional
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class EspecialidadProfesionalTest {
    @Test fun avisaAlActivoSinEspecialidad() {
        assertTrue(ReglasEspecialidadProfesional.sinEspecialidad("Activo", 0))
        assertFalse(ReglasEspecialidadProfesional.sinEspecialidad("Activo", 2))
    }

    @Test fun noAvisaAlInactivo() {
        assertFalse(ReglasEspecialidadProfesional.sinEspecialidad("Inactivo", 0))
    }

    @Test fun exigeEspecialidadSoloSiLaClinicaTiene() {
        assertNotNull(ReglasEspecialidadProfesional.errorAlGuardar(activasEnClinica = 3, seleccionadas = 0))
        assertNull(ReglasEspecialidadProfesional.errorAlGuardar(activasEnClinica = 3, seleccionadas = 1))
        assertNull(ReglasEspecialidadProfesional.errorAlGuardar(activasEnClinica = 0, seleccionadas = 0))
    }

    @Test fun interpretaLaRespuestaDelServidor() {
        val ok = EspecialidadProfesionalRepo.interpretar(200, """{"disponibles":[{"id":"a","nombre":"Fisio"}],"seleccionadas":["a"]}""")
        assertIs<EspecialidadProfesionalRepo.R.Ok>(ok)
        assertEquals(listOf("a"), ok.datos.seleccionadas)
        val err = EspecialidadProfesionalRepo.interpretar(400, """{"error":"Elige al menos una especialidad"}""")
        assertIs<EspecialidadProfesionalRepo.R.Error>(err)
        assertEquals("Elige al menos una especialidad", err.mensaje)
    }
}
