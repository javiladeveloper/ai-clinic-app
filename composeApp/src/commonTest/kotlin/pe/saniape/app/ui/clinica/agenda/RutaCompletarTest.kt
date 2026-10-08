package pe.saniape.app.ui.clinica.agenda

import kotlin.test.Test
import kotlin.test.assertEquals
import pe.saniape.app.ui.clinica.agenda.modales.RutaCompletar
import pe.saniape.app.ui.clinica.agenda.modales.rutaCompletar

/**
 * Bug 2026-10-08 (PodoBlack): una sesión de PODOLOGÍA completada desde la agenda
 * con "¿El paciente pagó esta sesión?" (S/ 60, Efectivo) quedó sin pago. El modal
 * salía por el callback "con profesional" —que no lleva el cobro ni las fotos—
 * en TODA cita que no fuera sesión de fisio, porque la agenda siempre lo pasa.
 * Ese callback es solo para cuando el modal PIDIÓ quién atendió.
 */
class RutaCompletarTest {

    @Test
    fun sesionNoFisioSaleConElCobro() {
        // Podología/odontología/estética: sin selector de profesional → la ruta normal (lleva el pago).
        assertEquals(RutaCompletar.Normal, rutaCompletar(fisioSesion = false, pideProfesional = false, hayConProfesional = true))
    }

    @Test
    fun sesionFisioVaPorSuCierre() {
        assertEquals(RutaCompletar.Fisio, rutaCompletar(fisioSesion = true, pideProfesional = false, hayConProfesional = true))
    }

    @Test
    fun evaluacionQuePidioProfesionalLoManda() {
        assertEquals(RutaCompletar.ConProfesional, rutaCompletar(fisioSesion = false, pideProfesional = true, hayConProfesional = true))
    }

    @Test
    fun sinCallbackConProfesionalSiempreLaNormal() {
        assertEquals(RutaCompletar.Normal, rutaCompletar(fisioSesion = false, pideProfesional = true, hayConProfesional = false))
    }
}
