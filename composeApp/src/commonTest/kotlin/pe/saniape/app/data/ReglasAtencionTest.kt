package pe.saniape.app.data

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import pe.saniape.app.data.staff.ConsentimientoApp
import pe.saniape.app.data.staff.avisosCierre
import pe.saniape.app.data.staff.pasosDeAtencion

/**
 * Gemelo de lib/atencion-medica.ts (pasosDeAtencion / avisosCierre) en la web.
 * El servidor manda los pasos y avisos en `flags`; esto solo sirve de respaldo
 * y para que los textos no se desvíen (si cambia uno, cambia el otro).
 */
class ReglasAtencionTest {
    private fun cons(estado: String) = ConsentimientoApp(id = "c", procedimiento = "x", estado = estado)

    @Test fun consultaMedicaTieneSeisPasos() {
        val pasos = pasosDeAtencion(esProcedimiento = false, dental = false)
        assertEquals(listOf("motivo", "vitales", "examen", "diagnostico", "plan", "cierre"), pasos.map { it.clave })
        assertEquals(
            listOf("Motivo y anamnesis", "Funciones vitales", "Examen físico", "Diagnóstico CIE-10", "Plan", "Cierre"),
            pasos.map { it.titulo },
        )
    }

    @Test fun consultaDentalCambiaTitulos() {
        val pasos = pasosDeAtencion(esProcedimiento = false, dental = true)
        assertEquals("Funciones vitales (opcional)", pasos[1].titulo)
        assertEquals("Examen estomatológico", pasos[2].titulo)
    }

    @Test fun procedimientoReemplazaMotivoYExamen() {
        val pasos = pasosDeAtencion(esProcedimiento = true, dental = false)
        assertEquals(listOf("procedimiento", "vitales", "diagnostico", "plan", "cierre"), pasos.map { it.clave })
        assertEquals("Consentimiento y procedimiento", pasos[0].titulo)
        assertEquals("Indicaciones y control", pasos[3].titulo)
        // En un procedimiento el dental no cambia los títulos.
        assertEquals(pasos, pasosDeAtencion(esProcedimiento = true, dental = true))
    }

    @Test fun avisaConsentimientoSinFirmar() {
        val avisos = avisosCierre(esProcedimiento = true, consentimientos = listOf(cons("Pendiente")), requiereConsentimiento = true, faltantesHc = emptyList())
        assertEquals(1, avisos.size)
        assertTrue(avisos[0].startsWith("El consentimiento informado de este procedimiento no está registrado como firmado."))
        assertTrue(avisos[0].endsWith("(Ley 26842, art. 15.4)."))
    }

    @Test fun avisaConsentimientoRechazado() {
        val avisos = avisosCierre(true, listOf(cons("Rechazado")), true, emptyList())
        assertEquals(listOf("El paciente NO aceptó el procedimiento (consentimiento rechazado). No debería realizarse."), avisos)
    }

    @Test fun sinAvisoSiHayFirmadoOSiNoSeRequiere() {
        assertTrue(avisosCierre(true, listOf(cons("Rechazado"), cons("Firmado")), true, emptyList()).isEmpty())
        assertTrue(avisosCierre(false, listOf(cons("Pendiente")), true, emptyList()).isEmpty())
        assertTrue(avisosCierre(true, emptyList(), false, emptyList()).isEmpty())
    }

    @Test fun avisaFaltantesDeHc() {
        val avisos = avisosCierre(false, emptyList(), false, listOf("Motivo de consulta", "Diagnóstico"))
        assertEquals(listOf("Para una HC completa (NTS 139) falta: Motivo de consulta · Diagnóstico."), avisos)
    }
}
