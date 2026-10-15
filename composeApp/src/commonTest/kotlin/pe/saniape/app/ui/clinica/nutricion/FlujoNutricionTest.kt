package pe.saniape.app.ui.clinica.nutricion

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import pe.saniape.app.data.staff.BorradorPlan
import pe.saniape.app.data.staff.cuerpoPlan
import pe.saniape.app.data.staff.planDeJson
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Flujo de nutrición: agenda → medidas → plan (gemelo del de la web). */
class FlujoNutricionTest {

    @Test fun ofertaDeMedidasSoloEnNutricionConPermisoYCompletadaEnElServidor() {
        assertTrue(ofrecerMedidasTrasCompletar(true, "p1", puedePacientes = true, puedeSesiones = false, encolada = false))
        assertTrue(ofrecerMedidasTrasCompletar(true, "p1", puedePacientes = false, puedeSesiones = true, encolada = false))
        assertFalse(ofrecerMedidasTrasCompletar(false, "p1", true, true, false))      // no es de nutrición
        assertFalse(ofrecerMedidasTrasCompletar(true, null, true, true, false))       // sin paciente
        assertFalse(ofrecerMedidasTrasCompletar(true, " ", true, true, false))
        assertFalse(ofrecerMedidasTrasCompletar(true, "p1", false, false, false))     // sin permiso
        assertFalse(ofrecerMedidasTrasCompletar(true, "p1", true, true, encolada = true)) // sin señal
    }

    @Test fun aperturaDesdeLaAgenda() {
        val m = FlujoNutricionFicha.desde(AperturaNutricion("medidas", "c1", abrirFormulario = true, tratamientoId = "t1"))
        assertEquals(FlujoNutricionFicha("c1", abrirMedidas = true, abrirPlan = false, tratamientoId = "t1"), m)
        // 🥗 Plan alimentario desde la tarjeta: solo la pestaña, sin abrir el editor.
        val p = FlujoNutricionFicha.desde(AperturaNutricion("plan", "c1"))
        assertFalse(p.abrirPlan)
        assertEquals("c1", p.citaId)
        assertEquals(FlujoNutricionFicha(), FlujoNutricionFicha.desde(null))
    }

    @Test fun medidasLuegoPlanYLosAbrirValenUnaVez() {
        var f = FlujoNutricionFicha.desde(AperturaNutricion("medidas", "c1", abrirFormulario = true))
        f = f.continuarConPlan()
        assertFalse(f.abrirMedidas)
        assertTrue(f.abrirPlan)
        assertEquals("c1", f.citaId) // el plan queda vinculado a la misma cita
        // Se queda en el plan: sigue; pasa a otra pestaña: ya no se reabre.
        assertTrue(f.alCambiarTab("plan").abrirPlan)
        val fuera = f.alCambiarTab("atenciones")
        assertFalse(fuera.abrirPlan)
        assertFalse(fuera.abrirMedidas)
        assertEquals("c1", fuera.citaId)
    }

    @Test fun planNuevoLlevaCitaYVisibilidad() {
        val b = BorradorPlan(pacienteId = "p1", objetivo = "Bajar de peso", citaId = "c1", visiblePaciente = false)
        val nuevo = cuerpoPlan(b)
        assertEquals("c1", nuevo["citaId"]!!.jsonPrimitive.content)
        assertFalse(nuevo["visiblePaciente"]!!.jsonPrimitive.boolean)
        // Editar: la cita no viaja (un plan guardado no cambia de atención).
        val editar = cuerpoPlan(b, id = "x")
        assertNull(editar["citaId"])
        assertNull(editar["pacienteId"])
        assertTrue(cuerpoPlan(BorradorPlan(pacienteId = "p1"))["visiblePaciente"]!!.jsonPrimitive.boolean)
    }

    @Test fun filaViejaSinElDatoEsVisible() {
        fun fila(extra: String) = planDeJson(Json.parseToJsonElement("""{"id":"x","paciente_id":"p","fecha":"2026-10-14"$extra}""").jsonObject)!!
        assertTrue(fila("").visiblePaciente)
        assertTrue(fila(""","visible_paciente":null""").visiblePaciente)
        assertTrue(fila(""","visible_paciente":true""").visiblePaciente)
        assertFalse(fila(""","visible_paciente":false""").visiblePaciente)
    }
}
