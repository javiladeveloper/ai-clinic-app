package pe.saniape.app.data.staff

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class FriccionesAgendaTest {

    // ── Agendar siguiente ──

    @Test
    fun siguienteSesionUsaElIntervaloDelServicioOSieteDias() {
        assertEquals("2026-10-04", fechaSugeridaSiguienteSesion("2026-09-27", null))
        assertEquals("2026-10-04", fechaSugeridaSiguienteSesion("2026-09-27", 0))
        assertEquals("2026-09-30", fechaSugeridaSiguienteSesion("2026-09-27", 3))
        assertEquals("2026-10-12", fechaSugeridaSiguienteSesion("2026-09-27", 15))
    }

    @Test
    fun siguienteSesionSoloSiQuedanYNoHayFutura() {
        assertTrue(debeOfrecerSiguienteSesion("Activo", 10, 4, hayFutura = false))
        assertFalse(debeOfrecerSiguienteSesion("Activo", 10, 4, hayFutura = true))   // ya agendada
        assertFalse(debeOfrecerSiguienteSesion("Activo", 10, 10, hayFutura = false)) // no quedan
        assertFalse(debeOfrecerSiguienteSesion("Completado", 10, 9, hayFutura = false))
        assertFalse(debeOfrecerSiguienteSesion(null, 10, 2, hayFutura = false))
    }

    // ── Selectores de fecha ──

    @Test
    fun isoAMillisEsElInicioDelDiaEnUtc() {
        assertEquals(1_759_363_200_000L, isoAMillisUtc("2025-10-02"))
        assertEquals(isoAMillisUtc("2025-10-02"), isoAMillisUtc("2025-10-02T15:00:00"))
        assertNull(isoAMillisUtc(""))
        assertNull(isoAMillisUtc(null))
        assertNull(isoAMillisUtc("mañana"))
    }

    // ── Título del formulario ──

    @Test
    fun tituloSegunTipoConPrefill() {
        assertEquals("Nueva cita", tituloFormularioCita(false, "Sesión"))
        assertEquals("Agendar sesión", tituloFormularioCita(true, "Sesión"))
        assertEquals("Agendar evaluación", tituloFormularioCita(true, "Evaluación"))
        assertEquals("Agendar diagnóstico", tituloFormularioCita(true, "Diagnóstico"))
        assertEquals("Nueva cita", tituloFormularioCita(true, ""))
    }

    // ── Búsqueda en el servidor ──

    @Test
    fun patronToleraTildesYEnies() {
        val jose = Regex(patronRegexSinTildes("jose"), RegexOption.IGNORE_CASE)
        assertTrue(jose.containsMatchIn("JOSÉ LUIS PÉREZ"))
        assertTrue(jose.containsMatchIn("maria jose"))
        assertFalse(jose.containsMatchIn("JUAN"))
        val nunez = Regex(patronRegexSinTildes("nunez"), RegexOption.IGNORE_CASE)
        assertTrue(nunez.containsMatchIn("CARLOS NÚÑEZ"))
        assertEquals("12345678", patronRegexSinTildes("12345678"))
        // Ningún metacarácter del usuario llega a la regex (ni rompe el or=(…)).
        assertEquals("p[eéèëêEÉÈËÊ]r", patronRegexSinTildes("pe.(r,"))
    }

    @Test
    fun palabrasDeLaBusqueda() {
        assertEquals(listOf("jorge", "oli"), palabrasBusqueda("  jorge   oli "))
        assertEquals(listOf("a", "b", "c", "d"), palabrasBusqueda("a b c d e f"))
        assertEquals(emptyList(), palabrasBusqueda(" , % "))
    }

    // ── Método de pago por defecto ──

    private val metodos = listOf("Efectivo", "Yape", "Plin", "BCP")

    @Test
    fun metodoDelPacienteLuegoDelUsuarioLuegoEfectivo() {
        assertEquals("Efectivo", elegirMetodoPago(null, null, metodos))
        assertEquals("Plin", elegirMetodoPago(null, "Plin", metodos))
        assertEquals("Yape", elegirMetodoPago("Yape", "Plin", metodos))
        // Uno que la clínica ya no ofrece no se propone.
        assertEquals("Plin", elegirMetodoPago("Tarjeta", "Plin", metodos))
        assertEquals("Efectivo", elegirMetodoPago("Tarjeta", "Otro", metodos))
        // Clínica sin Efectivo: el primero que sí tiene.
        assertEquals("Yape", elegirMetodoPago(null, null, listOf("Yape", "BCP")))
    }

    @Test
    fun mapaDeMetodosPorPacienteRecuerdaYRecorta() {
        var crudo: String? = null
        crudo = recordarMetodoEnMapa(crudo, "p1", "Yape")
        crudo = recordarMetodoEnMapa(crudo, "p2", "Plin")
        crudo = recordarMetodoEnMapa(crudo, "p1", "BCP")
        val m = decodificarMetodosPorPaciente(crudo)
        assertEquals("BCP", m["p1"])
        assertEquals("Plin", m["p2"])
        // p1 pasó a ser el más reciente (al final).
        assertEquals(listOf("p2", "p1"), m.keys.toList())
        // Recorta a los más recientes.
        var c2: String? = null
        for (i in 1..5) c2 = recordarMetodoEnMapa(c2, "p$i", "Yape", max = 3)
        assertEquals(listOf("p3", "p4", "p5"), decodificarMetodosPorPaciente(c2).keys.toList())
        assertTrue(decodificarMetodosPorPaciente(null).isEmpty())
        assertTrue(decodificarMetodosPorPaciente("basura\n=x\ny=").isEmpty())
    }

    // ── Agendar control ──

    @Test
    fun controlAbreEnLaFechaAnotadaSiEsFutura() {
        assertEquals("2026-10-15", fechaParaAgendarControl("2026-10-15", "2026-09-27"))
        assertEquals("2026-09-27", fechaParaAgendarControl("2026-09-27", "2026-09-27"))
        assertEquals("2026-09-27", fechaParaAgendarControl("2026-09-01", "2026-09-27"))  // ya pasó
        assertEquals("2026-09-27", fechaParaAgendarControl(null, "2026-09-27"))
        assertEquals("2026-09-27", fechaParaAgendarControl("en 2 semanas", "2026-09-27"))
    }

    @Test
    fun fechaLegibleCortaConDia() {
        assertEquals("vie 02/10", fechaLegibleCorta("2026-10-02"))
        assertEquals("dom 27/09", fechaLegibleCorta("2026-09-27"))
        assertEquals("x", fechaLegibleCorta("x"))
    }
}
