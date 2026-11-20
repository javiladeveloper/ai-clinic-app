package pe.saniape.app.data.staff

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Gemelo de lib/__tests__/horarios-dia.test.ts de la web (20/11/2026). */
class HorariosDiaTest {
    private fun h(t: String): Int { val p = t.split(":"); return p[0].toInt() * 60 + p[1].toInt() }

    @Test
    fun une_las_citas_que_se_cruzan_o_se_tocan() {
        assertEquals(listOf(Tramo(h("16:00"), h("17:00")), Tramo(h("18:00"), h("18:30"))),
            ocupadosUnidos(listOf(h("16:00") to 45, h("16:30") to 30, h("18:00") to 30)))
    }

    @Test
    fun libres_turno_menos_citas_y_permisos() {
        val libres = libresDelDia(
            listOf(Tramo(h("09:00"), h("13:00")), Tramo(h("15:00"), h("19:00"))),
            listOf(Tramo(h("10:00"), h("10:45")), Tramo(h("12:55"), h("13:30"))),
            flexible = false, rango = Tramo(h("07:00"), h("20:00")), bloqueados = listOf(Tramo(h("17:00"), h("18:00"))),
        )
        assertEquals(listOf("9:00–10:00 a. m.", "10:45 a. m.–12:55 p. m.", "3:00–5:00 p. m.", "6:00–7:00 p. m."), libres.map(::tramoTexto))
    }

    @Test
    fun a_demanda_libre_todo_el_rango_menos_lo_ocupado() {
        val libres = libresDelDia(emptyList(), listOf(Tramo(h("16:00"), h("16:45"))), flexible = true, rango = Tramo(h("07:00"), h("20:00")))
        assertEquals(listOf("7:00 a. m.–4:00 p. m.", "4:45–8:00 p. m."), libres.map(::tramoTexto))
    }

    @Test
    fun rango_y_textos() {
        assertEquals(Tramo(h("06:00"), h("22:00")), rangoDelDia(listOf(Tramo(h("06:30"), h("21:15")))))
        assertEquals(Tramo(h("07:00"), h("20:00")), rangoDelDia(emptyList()))
        assertEquals("12:00 p. m.", horaDeMin(h("12:00")))
        assertEquals("1 h 30 min", duracionTexto(90))
        assertEquals("45 min", duracionTexto(45))
    }

    @Test
    fun ausente_y_permiso_por_horas() {
        val d = armarDiaHorarios(DatosHorarios(
            profesionales = listOf(Triple("a", "Ana", false), Triple("b", "Beto", false)),
            turnos = listOf(Triple("a", "09:00:00", "13:00:00"), Triple("b", "09:00:00", "13:00:00")),
            citas = listOf(Triple("b", "10:00:00", 45)),
            ausencias = listOf(listOf("a", "Vacaciones", null, null), listOf("b", "Permiso", "11:00", "12:00")),
        ))
        assertEquals("Beto", d.filas.first().nombre)          // los que atienden, primero
        val ana = d.filas.first { it.nombre == "Ana" }
        assertEquals("🌴 De vacaciones", ana.ausente)
        assertTrue(ana.libres.isEmpty())
        val beto = d.filas.first { it.nombre == "Beto" }
        assertNull(beto.ausente)
        assertEquals(listOf("9:00–10:00 a. m.", "10:45–11:00 a. m.", "12:00–1:00 p. m."), beto.libres.map(::tramoTexto))
    }
}
