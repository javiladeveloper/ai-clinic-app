package pe.saniape.app.data

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import pe.saniape.app.data.staff.EvaluacionPrevia
import pe.saniape.app.data.staff.OrigenSugerencia
import pe.saniape.app.data.staff.SugerenciaProfesional
import pe.saniape.app.data.staff.fechaParaNuevaCita
import pe.saniape.app.data.staff.horaInicialNuevaCita
import pe.saniape.app.data.staff.profesionalSugerido
import pe.saniape.app.data.staff.puedeAtender

/** Gemelo de lib/__tests__/profesional-sugerido.test.ts y prefill-cita.test.ts (web). */
class ProfesionalSugeridoTest {
    private val fisio = "esp-fisio"
    private val odonto = "esp-odonto"
    // Solo ACTIVOS (el repo trae terapeutasActivos): "ex" no está.
    private val activos = mapOf(
        "kat" to listOf(fisio),
        "rodas" to listOf(fisio),
        "ana" to listOf(odonto),
        "sin" to emptyList(),
    )

    @Test fun elDelTratamientoManda() {
        assertEquals(
            SugerenciaProfesional("kat", OrigenSugerencia.Tratamiento),
            profesionalSugerido("kat", listOf(EvaluacionPrevia("rodas", null, "2026-09-20")), fisio, activos),
        )
    }

    @Test fun elDelTratamientoSoloNecesitaEstarActivo() {
        assertEquals("ana", profesionalSugerido("ana", emptyList(), fisio, activos)?.id)
    }

    @Test fun sinProfesionalEnTratamientoVaElDeLaEvaluacionMasReciente() {
        val s = profesionalSugerido(null, listOf(
            EvaluacionPrevia("kat", null, "2026-08-01", "10:00"),
            EvaluacionPrevia("rodas", null, "2026-09-10", "09:00:00"),
        ), fisio, activos)
        assertEquals(SugerenciaProfesional("rodas", OrigenSugerencia.Diagnostico), s)
        assertEquals("Mismo profesional de su diagnóstico", s?.texto)
    }

    @Test fun prefiereLaMismaEspecialidad() {
        val s = profesionalSugerido(null, listOf(
            EvaluacionPrevia("sin", odonto, "2026-09-20"),
            EvaluacionPrevia("kat", fisio, "2026-06-01"),
        ), fisio, activos)
        assertEquals("kat", s?.id)
    }

    @Test fun saltaInactivosYOtraEspecialidad() {
        val s = profesionalSugerido("ex", listOf(
            EvaluacionPrevia("ex", null, "2026-09-20"),
            EvaluacionPrevia("ana", null, "2026-09-15"),
            EvaluacionPrevia("rodas", null, "2026-09-01"),
        ), fisio, activos)
        assertEquals(SugerenciaProfesional("rodas", OrigenSugerencia.Diagnostico), s)
    }

    @Test fun sinNadaValidoNoSugiere() {
        assertNull(profesionalSugerido(null, emptyList(), null, activos))
        assertNull(profesionalSugerido("borrado", listOf(EvaluacionPrevia(null, null, "2026-09-01")), null, activos))
    }

    @Test fun puedeAtenderSinEspecialidadesNoRestringe() {
        assertTrue(puedeAtender("sin", activos, fisio))
        assertFalse(puedeAtender("ana", activos, fisio))
        assertTrue(puedeAtender("ana", activos, null))
    }

    @Test fun fechaDeNuevaCita() {
        val hoy = "2026-09-26"
        assertEquals("2026-10-02", fechaParaNuevaCita("2026-10-02", hoy))
        assertEquals(hoy, fechaParaNuevaCita(hoy, hoy))
        assertEquals(hoy, fechaParaNuevaCita("2026-09-20", hoy))
        assertEquals(hoy, fechaParaNuevaCita(null, hoy))
        assertEquals(hoy, fechaParaNuevaCita("basura", hoy))
    }

    @Test fun horaDeNuevaCita() {
        assertEquals("09:00", horaInicialNuevaCita("2026-10-02", "2026-09-26", "16:00"))
        assertEquals("16:00", horaInicialNuevaCita("2026-09-26", "2026-09-26", "16:00"))
    }
}
