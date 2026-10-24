package pe.saniape.app.data.staff

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

/**
 * ¿Se ofrece el plan al completar la cita que evalúa? Gemelo de `planTrasEvaluar`
 * (lib/flujo.ts): la MISMA regla que abre la ventana de la evaluación, no el tipo
 * literal "Evaluación". Presets como los de lib/flujo.ts.
 */
class PlanTrasEvaluarTest {

    // fisioterapia (DEFAULT_FLUJO), estética/capilar, nutrición (también medicina general), psicología.
    private val fisio = FlujoClinica()
    private val estetica = FlujoClinica(usaConsulta = true, usaEvaluacion = false, labelConsulta = "Evaluación")
    private val nutricion = FlujoClinica(usaConsulta = true, usaEvaluacion = false, labelSesiones = "Controles")
    private val psico = FlujoClinica(usaConsulta = false, usaEvaluacion = true, labelEvaluacion = "Evaluación psicológica")
    private val directo = FlujoClinica(usaConsulta = false, usaEvaluacion = false)

    @Test fun esteticaYCapilarLaEvaluacionEsUnaConsultaYOfreceElPlan() {
        // El bug: la app comparaba con "Evaluación" literal.
        assertTrue(estetica.ofrecePlanAlCompletar("Consulta", porVentanaEvaluacion = true))
        assertEquals("Evaluación", estetica.nombreTipo("Consulta"))
    }

    @Test fun fisioSoloLaEvaluacionNoLaConsulta() {
        assertTrue(fisio.ofrecePlanAlCompletar("Evaluación", porVentanaEvaluacion = true))
        assertFalse(fisio.ofrecePlanAlCompletar("Consulta", porVentanaEvaluacion = true))
        assertFalse(fisio.ofrecePlanAlCompletar("Sesión", porVentanaEvaluacion = true))
    }

    @Test fun nutricionYMedicinaGeneralLaPrimeraConsultaSiElControlNo() {
        // Primera consulta: se cierra por la ventana de la evaluación → plan (como la web).
        assertTrue(nutricion.ofrecePlanAlCompletar("Consulta", porVentanaEvaluacion = true))
        // El control se cierra directo (decidirCompletar, sin ventana): sin plan.
        assertFalse(nutricion.ofrecePlanAlCompletar("Consulta", porVentanaEvaluacion = false))
        assertFalse(nutricion.ofrecePlanAlCompletar("Sesión", porVentanaEvaluacion = true))
    }

    @Test fun psicologiaYFlujoDirecto() {
        assertTrue(psico.ofrecePlanAlCompletar("Evaluación", porVentanaEvaluacion = true))
        assertFalse(psico.ofrecePlanAlCompletar("Consulta", porVentanaEvaluacion = true))
        // "Directo a sesiones": una Consulta histórica no evalúa (guard de usa_consulta).
        assertFalse(directo.ofrecePlanAlCompletar("Consulta", porVentanaEvaluacion = true))
    }

    // ── La tarjeta: «🩺 Crear tratamiento» principal en la cita evaluadora completada ──

    private fun plan(principal: Boolean, estado: String = "Completada", tipo: String = "Consulta") = planAccionesCita(
        EntradaAccionesCita(estado = estado, tipo = tipo, crearTratamiento = true, crearTratamientoPrincipal = principal),
    )

    @Test fun completadaConPlanPorCrearLaPrincipalEsCrearTratamiento() {
        assertEquals(AccionTarjeta.CrearTratamiento, plan(principal = true).primaria?.accion)
        // Repetir sigue a mano, como secundaria.
        assertEquals(AccionTarjeta.Repetir, plan(principal = true).secundaria?.accion)
    }

    @Test fun dentalMedicaONutricionLaDejanEnElMenu() {
        val p = plan(principal = false)
        assertNotEquals(AccionTarjeta.CrearTratamiento, p.primaria?.accion)
        assertTrue(p.enMenu.any { it.accion == AccionTarjeta.CrearTratamiento })
    }

    @Test fun activaSigueConElCierreYCanceladaSinPlan() {
        assertEquals(AccionTarjeta.Completar, plan(principal = true, estado = "Confirmada").primaria?.accion)
        assertTrue(plan(principal = true, estado = "Cancelada").let { p ->
            p.primaria == null && p.enMenu.none { it.accion == AccionTarjeta.CrearTratamiento }
        })
    }
}
