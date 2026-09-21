package pe.saniape.app.data

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
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

/**
 * Flujo POR ESPECIALIDAD (paridad con la web, 21/09/2026).
 *
 * En una clínica con fisioterapia Y odontología el camino no es el mismo:
 * fisioterapia entra por Consulta, odontología directo al Diagnóstico, porque
 * ahí la primera visita YA es la revisión con odontograma.
 *
 * Lo que estos tests protegen es a quien YA trabaja: sin flujo propio en la
 * especialidad manda el de la clínica, y hoy ninguna especialidad en producción
 * tiene uno.
 */
class FlujoPorEspecialidadTest {
    private fun j(txt: String): JsonObject = Json.parseToJsonElement(txt).jsonObject

    @Test
    fun sin_flujo_propio_manda_el_de_la_clinica() {
        val clinica = FlujoClinica()
        assertEquals(clinica, clinica.paraEspecialidad(null))
    }

    @Test
    fun la_especialidad_puede_quitar_la_consulta() {
        val clinica = FlujoClinica()   // fisioterapia: las dos etapas
        val odonto = clinica.paraEspecialidad(j(
            """{"usa_consulta":false,"usa_evaluacion":true,"label_evaluacion":"Diagnóstico"}"""
        ))
        assertFalse(odonto.usaConsulta)
        assertTrue(odonto.usaEvaluacion)
        assertEquals("Diagnóstico", odonto.labelEvaluacion)
        // Las etiquetas no redefinidas se heredan.
        assertEquals(clinica.labelSesiones, odonto.labelSesiones)
        // Y la clínica queda intacta para las demás especialidades.
        assertTrue(clinica.usaConsulta)
    }

    @Test
    fun un_json_a_medias_se_ignora_entero() {
        // Heredar la mitad dejaría una barra de recorrido sin sentido.
        val clinica = FlujoClinica()
        assertEquals(clinica, clinica.paraEspecialidad(j("""{"label_evaluacion":"Diagnóstico"}""")))
        assertEquals(clinica, clinica.paraEspecialidad(j("""{"usa_consulta":false}""")))
    }

    // ── Lo que ven las clínicas REALES al agendar ──────────────────────────
    // Antes se ofrecían los tres tipos SIEMPRE y con los nombres internos.

    private fun visibles(f: FlujoClinica, usaSesiones: Boolean = true) =
        listOf("Consulta", "Evaluación", "Sesión")
            .filter { f.usaTipo(it) }
            .filter { it != "Sesión" || usaSesiones }
            .map { f.nombreTipo(it) }

    @Test
    fun dalu_sigue_viendo_lo_de_siempre() {
        assertEquals(listOf("Consulta", "Evaluación", "Sesión"), visibles(FlujoClinica()))
    }

    @Test
    fun renova_ya_no_ve_una_consulta_que_no_hace() {
        // RENOVA tiene usa_consulta=false en producción, y la app le mostraba
        // igual la tarjeta "Consulta".
        val renova = FlujoClinica(usaConsulta = false)
        assertEquals(listOf("Evaluación", "Sesión"), visibles(renova))
    }

    @Test
    fun una_dental_entra_por_el_diagnostico() {
        val dental = FlujoClinica(
            usaConsulta = false, labelEvaluacion = "Diagnóstico",
            labelSesiones = "Citas de tratamiento",
        )
        assertEquals(listOf("Diagnóstico", "Citas de tratamiento"), visibles(dental))
    }
}
