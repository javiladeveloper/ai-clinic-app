package pe.saniape.app.data

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import pe.saniape.app.data.staff.MapaDental
import pe.saniape.app.data.staff.TerapeutaRef
import pe.saniape.app.data.staff.odontologosActivos
import pe.saniape.app.data.staff.profesionalDeEvaluacionDental

/**
 * Quién atendió la evaluación dental, sin preguntar si se puede saber
 * (29/09/2026). Gemelo de los casos de `lib/__tests__/evaluacion-dental.test.ts`.
 */
class EvaluacionDentalTest {
    private val odonto = MapaDental(ids = listOf("e-odonto"), solo = false)
    private val draA = TerapeutaRef("dra-a", "Dra. A", listOf("e-odonto"))
    private val fisio = TerapeutaRef("fisio", "Lic. Fisio", listOf("e-fisio"))
    private val equipo = listOf(draA, fisio)

    @Test fun manda_el_profesional_de_la_cita() {
        assertEquals("x", profesionalDeEvaluacionDental("x", "yo", equipo, odonto))
    }

    @Test fun sin_profesional_en_la_cita_el_del_usuario() {
        assertEquals("yo", profesionalDeEvaluacionDental(null, "yo", equipo, odonto))
        assertEquals("yo", profesionalDeEvaluacionDental("", "yo", equipo, odonto))
    }

    @Test fun recepcion_con_un_solo_odontologo_activo_ese() {
        // El fisio no cuenta para "el único odontólogo" en una clínica mixta.
        assertEquals("dra-a", profesionalDeEvaluacionDental(null, null, equipo, odonto))
    }

    @Test fun varios_odontologos_es_ambiguo() {
        val dos = equipo + TerapeutaRef("dr-b", "Dr. B", listOf("e-odonto"))
        assertNull(profesionalDeEvaluacionDental(null, null, dos, odonto))
    }

    @Test fun equipo_sin_cargar_es_ambiguo() {
        assertNull(profesionalDeEvaluacionDental(null, null, emptyList(), odonto))
    }

    @Test fun clinica_solo_dental_cuenta_a_todos() {
        val solo = MapaDental(solo = true)
        assertEquals("dra-a", profesionalDeEvaluacionDental(null, null, listOf(draA), solo))
        assertEquals(2, odontologosActivos(equipo, solo).size)
        assertEquals(emptyList(), odontologosActivos(equipo, MapaDental()))
    }
}
