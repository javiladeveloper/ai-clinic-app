package pe.saniape.app.ui

import kotlinx.datetime.TimeZone
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * El 💬 de la cita (Centro OMER, 2026-10-14): llegó "Hola JOSE �ﾟﾑﾋ Te
 * recordamos tu cita de consulta el 2026-10-17 a las 3:00 PM". Mismo texto
 * que la web (lib/__tests__/no-vino-y-atajos-citas.test.ts).
 */
class MensajeCitaTest {

    @Test
    fun recordatorio_con_todos_los_datos() {
        val m = mensajeCita(
            nombrePaciente = "JOSE LUIS SANDOVAL BALDERA", fecha = "2026-10-17", hora = "15:00:00",
            clinica = "Centro OMER", hoy = "2026-10-14", enlace = "https://x.test/cita/t1",
            servicio = "Consulta", profesional = "Dra. Ana Quispe", direccion = "Calle Simón Bolívar 638",
        )
        assertEquals(
            listOf(
                "Hola Jose 👋",
                "Le recordamos su cita en Centro OMER:",
                "",
                "🙋 Paciente: Jose Luis Sandoval Baldera",
                "📅 Sábado 17 de octubre",
                "🕒 3:00 p. m.",
                "🩺 Consulta",
                "🧑‍⚕️ Con: Dra. Ana Quispe",
                "📍 Calle Simón Bolívar 638",
                "",
                "¿Nos confirma su asistencia?",
                "Puede confirmar aquí: https://x.test/cita/t1",
            ).joinToString("\n"),
            m,
        )
    }

    @Test
    fun recien_agendada_confirma_la_cita() {
        val m = mensajeCita(
            nombrePaciente = "JOSE LUIS SANDOVAL BALDERA", fecha = "2026-10-17", hora = "15:00",
            clinica = "Centro OMER", hoy = "2026-10-14", recienAgendada = true,
        )
        assertEquals(listOf("Hola Jose 👋", "Hemos confirmado su cita en Centro OMER ✅"), m.lines().take(2))
        assertTrue(m.contains("Si necesita reprogramar, escríbanos por aquí."))
        assertFalse(m.contains("¿Nos confirma"))
        assertFalse(m.contains("http"))
    }

    @Test
    fun hoy_manana_y_sin_huecos() {
        val hoy = mensajeCita("Lic. Ana Torres", "2026-09-27", "15:30", "MoviVida", "2026-09-27")
        assertTrue(hoy.contains("📅 Hoy domingo 27 de septiembre"))
        assertTrue(hoy.startsWith("Hola Ana 👋"))
        val manana = mensajeCita("MARTA MAMANI", "2026-09-28", null, "DALU", "2026-09-27", servicio = "Terapia física")
        assertTrue(manana.contains("📅 Mañana lunes 28 de septiembre\n🩺 Terapia física\n"))
        assertFalse(manana.contains("\n\n\n"))
        assertTrue(mensajeCita(null, "2026-10-05", null, null, "2026-09-27").startsWith("Hola 👋\nLe recordamos su cita:"))
    }

    @Test
    fun recien_agendada_en_el_dia_de_la_clinica() {
        val lima = TimeZone.of("America/Lima")
        // 23:07 de Lima del 12 = 04:07 UTC del 13 (la cita de JOSE).
        assertTrue(citaRecienAgendada("2026-10-13T04:07:04.088816+00:00", "2026-10-12", lima))
        assertFalse(citaRecienAgendada("2026-10-13T04:07:04.088816+00:00", "2026-10-13", lima))
        assertFalse(citaRecienAgendada(null, "2026-10-13", lima))
        assertFalse(citaRecienAgendada("basura", "2026-10-13", lima))
    }

    @Test
    fun url_encode_emojis_y_tildes_como_utf8() {
        // Antes: los bytes ≥ 0x80 pasaban como letras ("ﾟﾑﾋ") en vez de %XX.
        assertEquals("Hola%20%F0%9F%91%8B%20%C2%BFS%C3%AD%3F", urlEncode("Hola 👋 ¿Sí?"))
        assertEquals("Calle-Sim%C3%B3n_638.~", urlEncode("Calle-Simón_638.~"))
    }
}
