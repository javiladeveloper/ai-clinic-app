package pe.saniape.app.data

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import pe.saniape.app.data.staff.FlujoClinica

/**
 * Cómo llama cada clínica a sus citas. El tipo interno NO cambia nunca (hay
 * miles de citas históricas guardadas con esos valores); lo que cambia es cómo
 * se muestra. RENOVA CAPILAR llama "Evaluación" a lo que internamente es una
 * Consulta, y sin esto vería un nombre en la web y otro en el celular.
 */
class FlujoClinicaTest {

    @Test
    fun muestra_el_nombre_que_le_puso_la_clinica() {
        val f = FlujoClinica(labelConsulta = "Evaluación capilar")
        assertEquals("Evaluación capilar", f.nombreTipo("Consulta"))
    }

    @Test
    fun una_cita_de_sesion_va_en_singular() {
        // "Sesiones" es el nombre del PASO; una cita suelta es una sesión.
        assertEquals("Sesión", FlujoClinica().nombreTipo("Sesión"))
    }

    @Test
    fun respeta_el_nombre_propio_de_las_sesiones() {
        assertEquals("Controles", FlujoClinica(labelSesiones = "Controles").nombreTipo("Sesión"))
    }

    @Test
    fun un_tipo_desconocido_no_deja_la_pantalla_en_blanco() {
        assertEquals("Cita", FlujoClinica().nombreTipo(null))
    }

    @Test
    fun solo_se_ofrecen_los_tipos_que_la_clinica_usa() {
        // RENOVA no hace consultas: ofrecerle ese filtro da siempre vacío.
        val f = FlujoClinica(usaConsulta = false)
        assertFalse(f.usaTipo("Consulta"))
        assertTrue(f.usaTipo("Evaluación"))
        // Las sesiones no dependen del preset: las decide la especialidad.
        assertTrue(f.usaTipo("Sesión"))
    }

    @Test
    fun por_defecto_una_clinica_usa_las_dos() {
        // Las clínicas que ya operan no pueden perder un tipo de cita porque el
        // servidor no haya mandado el flujo.
        val f = FlujoClinica()
        assertTrue(f.usaTipo("Consulta"))
        assertTrue(f.usaTipo("Evaluación"))
        assertEquals("Consulta", f.nombreTipo("Consulta"))
    }
}
