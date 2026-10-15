package pe.saniape.app.data

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** GET /api/paciente/mis-indicaciones (contrato §5) → la sección 📝 del portal. */
class MisIndicacionesPortalTest {

    private val json = """
        {"indicaciones":[
          {"id":"i1","fecha":"2026-09-01","titulo":"Cuidados después de una extracción dental",
           "indicaciones":"Muerda la gasa 30 minutos","evitar":null,"signos_alarma":"Fiebre","control":null,
           "clinica":null,"profesional":null,"paciente":null},
          {"id":"i2","fecha":"2026-10-15","titulo":"Después del peeling químico",
           "indicaciones":"- Hidratar\n\nProtector solar FPS 50","evitar":"Sol directo","signos_alarma":"Ampollas\nFiebre",
           "control":"Control en 7 días","clinica":{"nombre":"Sonrisas","logo_url":"https://x/logo.png","color_principal":"#0f766e"},
           "profesional":"Dr. Luis Ruiz","paciente":{"nombre":"Ana Torres"}},
          {"titulo":"sin id"}
        ]}
    """.trimIndent()

    @Test fun laMasRecienteDelanteYLineasLimpias() {
        val l = parsearMisIndicaciones(json)!!
        assertEquals(listOf("i2", "i1"), l.map { it.id })
        val u = l[0]
        assertEquals("Hidratar\nProtector solar FPS 50", u.indicaciones)
        assertEquals("Ampollas\nFiebre", u.signosAlarma)
        assertEquals("Sonrisas", u.clinica?.nombre)
        assertEquals("Dr. Luis Ruiz", u.profesional)
        assertEquals("Ana Torres", u.pacienteNombre)
        assertNull(l[1].clinica); assertNull(l[1].evitar)
    }

    @Test fun vaciaYJsonInvalido() {
        assertEquals(emptyList(), parsearMisIndicaciones("""{"indicaciones":[]}"""))
        assertEquals(emptyList(), parsearMisIndicaciones("""{}"""))
        assertNull(parsearMisIndicaciones("<html>404</html>"))
    }

    @Test fun hojaDelPortalConLogoColorYAlarma() {
        val u = parsearMisIndicaciones(json)!![0]
        val h = htmlIndicacionPortal(u)
        assertTrue(h.contains("https://x/logo.png"))
        assertTrue(h.contains("#0f766e"))
        assertTrue(h.contains("Ana Torres"))
        assertTrue(h.contains("<li>Ampollas</li>"))
        assertTrue(h.contains("Control:</b> Control en 7 días"))
        assertFalse(h.contains("Documento:"))
    }
}
