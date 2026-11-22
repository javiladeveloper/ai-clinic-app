package pe.saniape.app.data.staff

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** La regla de disponibilidad (gemelo de estadoProfesional de CitaForm web, 19/11/2026). */
class DisponibilidadReglaTest {

    private val turno = listOf("09:00:00" to "13:00:00")

    private fun evaluar(
        hora: String, duracion: Int = 30, tipo: String? = "Sesión",
        horarios: List<Pair<String, String>> = turno, citas: List<Pair<String, Int>> = emptyList(),
        flexible: Boolean = false,
    ) = evaluarDisponibilidad(flexible, "Lun", horarios, citas, emptyList(), hora, duracion, tipo)

    @Test
    fun libre_dentro_del_turno() {
        val d = evaluar("10:00")
        assertNull(d.motivo)
        assertNull(motivoNoDisponible(d))
    }

    @Test
    fun la_cita_entera_tiene_que_caber_en_el_turno() {
        // Empieza dentro pero termina a las 13:10: ya no está disponible.
        val d = evaluar("12:40")
        assertTrue(d.motivo!!.contains("termina después de su turno"))
        assertEquals(d.motivo, motivoNoDisponible(d))
        assertNull(evaluar("12:30").motivo)   // termina justo a las 13:00
    }

    @Test
    fun fuera_de_horario_y_dia_sin_atencion() {
        assertTrue(evaluar("14:00").motivo!!.startsWith("Fuera de su horario"))
        assertEquals("No atiende los Lun", evaluar("10:00", horarios = emptyList()).motivo)
        assertNull(evaluar("22:00", horarios = emptyList(), flexible = true).motivo)
    }

    @Test
    fun consulta_y_evaluacion_no_se_cruzan_las_sesiones_si() {
        val citas = listOf("10:00:00" to 45)
        val eval = evaluar("10:15", tipo = "Evaluación", citas = citas)
        assertEquals("Ocupado a las 10:00", eval.motivo)
        assertEquals("Ocupado a las 10:00", motivoNoDisponible(eval))
        val sesion = evaluar("10:15", tipo = "Sesión", citas = citas)
        assertTrue(sesion.motivo!!.contains("paralelo"))
        assertNull(motivoNoDisponible(sesion))   // en paralelo: no se pinta gris
        // Hora real: termina 10:45 → a las 10:45 ya está libre.
        assertNull(evaluar("10:45", tipo = "Consulta", citas = citas).motivo)
    }

    @Test
    fun el_cruce_del_paciente_no_pinta_gris_al_profesional() {
        val d = evaluarDisponibilidad(false, "Lun", turno, emptyList(), listOf("10:00" to 30), "10:00", 30, "Sesión")
        assertTrue(d.motivo!!.startsWith("El paciente"))
        assertNull(motivoNoDisponible(d))
    }

    @Test fun `sin horario ese dia sale en plomo, tambien el a demanda`() {
        assertTrue(diaSinHorario(listOf("Lun", "Mar", "Sáb"), "Dom"))
        assertFalse(diaSinHorario(listOf("Lun", "Mar", "Sáb"), "Sáb"))
        assertFalse(diaSinHorario(emptyList(), "Dom")) // sin horario cargado: a demanda de verdad
        val d = evaluarDisponibilidad(true, "Dom", emptyList(), emptyList(), emptyList(), "12:30", 15, "Consulta", sinHorarioEseDia = true)
        assertEquals("Sin horarios los Dom", d.motivo)
        assertEquals("Sin horarios los Dom", motivoNoDisponible(d))
        // A demanda sin horario cargado: libre, como siempre.
        assertEquals(null, evaluarDisponibilidad(true, "Dom", emptyList(), emptyList(), emptyList(), "12:30", 15, "Consulta").motivo)
    }
}
