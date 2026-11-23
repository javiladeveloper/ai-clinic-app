package pe.saniape.app.data.staff

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * 🏷️ Cambiar especialidad (Centro OMER, 23/11/2026: «en la web se puede, en el
 * app no permite»). Gemelo de lib/cita-especialidad.ts de la web.
 */
class CitaEspecialidadTest {
    @Test
    fun `una consulta o evaluacion por atender se puede corregir`() {
        assertNull(motivoNoCambiarEspecialidad("Consulta", "Pendiente"))
        assertNull(motivoNoCambiarEspecialidad("Consulta", "Confirmada"))
        assertNull(motivoNoCambiarEspecialidad("Evaluación", "Confirmada"))
        assertNull(motivoNoCambiarEspecialidad(null, "Pendiente"))
    }

    @Test
    fun `la sesion no - su especialidad es la del tratamiento`() {
        assertNotNull(motivoNoCambiarEspecialidad("Sesión", "Confirmada"))
    }

    @Test
    fun `la ya atendida o cancelada no`() {
        assertNotNull(motivoNoCambiarEspecialidad("Consulta", "Completada"))
        assertNotNull(motivoNoCambiarEspecialidad("Consulta", "Cancelada"))
        assertNotNull(motivoNoCambiarEspecialidad("Consulta", null))
    }

    @Test
    fun `solo se ofrece con varias especialidades y a quien agenda o atiende`() {
        assertTrue(ofreceCambiarEspecialidad("Consulta", "Confirmada", 2, puedeCitas = true, puedeSesiones = false))
        assertTrue(ofreceCambiarEspecialidad("Evaluación", "Pendiente", 3, puedeCitas = false, puedeSesiones = true))
        assertFalse(ofreceCambiarEspecialidad("Consulta", "Confirmada", 1, puedeCitas = true, puedeSesiones = true))
        assertFalse(ofreceCambiarEspecialidad("Consulta", "Confirmada", 2, puedeCitas = false, puedeSesiones = false))
        assertFalse(ofreceCambiarEspecialidad("Sesión", "Confirmada", 2, puedeCitas = true, puedeSesiones = true))
        assertFalse(ofreceCambiarEspecialidad("Consulta", "Completada", 2, puedeCitas = true, puedeSesiones = true))
    }

    @Test
    fun `la accion sale en el menu de la cita, en el grupo Cita y antes de Cancelar`() {
        val con = accionesCita(EntradaAccionesCita(estado = "Confirmada", tipo = "Consulta", cambiarEspecialidad = true)).map { it.accion }
        val sin = accionesCita(EntradaAccionesCita(estado = "Confirmada", tipo = "Consulta")).map { it.accion }
        assertTrue(AccionTarjeta.CambiarEspecialidad in con)
        assertFalse(AccionTarjeta.CambiarEspecialidad in sin)
        assertTrue(con.indexOf(AccionTarjeta.CambiarEspecialidad) < con.indexOf(AccionTarjeta.Cancelar))
        assertEquals(GrupoAccion.CITA, grupoDe(AccionTarjeta.CambiarEspecialidad))
        // En una cita ya atendida no aparece aunque la bandera llegue encendida.
        val atendida = accionesCita(EntradaAccionesCita(estado = "Completada", tipo = "Consulta", cambiarEspecialidad = true)).map { it.accion }
        assertFalse(AccionTarjeta.CambiarEspecialidad in atendida)
    }
}
