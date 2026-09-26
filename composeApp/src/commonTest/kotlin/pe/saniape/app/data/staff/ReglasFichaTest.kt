package pe.saniape.app.data.staff

import kotlinx.datetime.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ReglasFichaTest {

    // ── B9: fecha local de Perú, no UTC ──

    @Test
    fun hoyDeNocheEnLimaNoSaltaAlDiaSiguiente() {
        // 2026-09-10 01:30 UTC = 2026-09-09 20:30 en Lima: sigue siendo el 9.
        val ahora = Instant.parse("2026-09-10T01:30:00Z")
        assertEquals("2026-09-09", hoyClinicaIso(ahora))
    }

    @Test
    fun agendarSiguienteEsMasSieteDiasDesdeLaFechaLocal() {
        val ahora = Instant.parse("2026-09-10T01:30:00Z")
        assertEquals("2026-09-16", sumarDiasIso(hoyClinicaIso(ahora), 7))
    }

    @Test
    fun sumarDiasCruzaMesYAnio() {
        assertEquals("2026-10-05", sumarDiasIso("2026-09-28", 7))
        assertEquals("2027-01-03", sumarDiasIso("2026-12-27", 7))
        assertEquals("no-es-fecha", sumarDiasIso("no-es-fecha", 7))
    }

    // ── B4: cambio de estado de sesión ──

    @Test
    fun reprogramarExigeFechaValida() {
        assertNotNull(validarCambioEstadoSesion("Reprogramada", "", null, "09:00"))
        assertNotNull(validarCambioEstadoSesion("Reprogramada", "", "2026-9-3", "09:00"))
        assertNotNull(validarCambioEstadoSesion("Reprogramada", "", "2026-09-03", "930"))
        assertNull(validarCambioEstadoSesion("Reprogramada", "", "2026-09-03", "09:30"))
    }

    @Test
    fun otroExigeMotivoYLosDemasNo() {
        assertNotNull(validarCambioEstadoSesion("Otro", "  ", null, null))
        assertNull(validarCambioEstadoSesion("Otro", "Se fue de viaje", null, null))
        assertNull(validarCambioEstadoSesion("No asistió", "", null, null))
        assertNull(validarCambioEstadoSesion("Cancelada", "", null, null))
        assertTrue(motivoObligatorio("Otro"))
        assertFalse(motivoObligatorio("Cancelada"))
    }

    @Test
    fun titulosComoLaWeb() {
        assertEquals("📅 Reprogramar sesión", tituloCambioEstadoSesion("Reprogramada"))
        assertEquals("✗ Cancelar sesión", tituloCambioEstadoSesion("Cancelada"))
    }

    // ── B4: alta + encuesta ──

    @Test
    fun confirmarAltaAvisaSesionesPendientes() {
        assertTrue(textoConfirmarAlta(2).startsWith("Quedan 2 sesiones"))
        assertTrue(textoConfirmarAlta(1).startsWith("Quedan 1 sesión "))
        assertEquals("Se cierra el tratamiento. El historial se conserva.", textoConfirmarAlta(0))
        assertEquals("Se cierra el tratamiento. El historial se conserva.", textoConfirmarAlta(null))
    }

    @Test
    fun enlaceWhatsAppComoLaWeb() {
        assertNull(enlaceWhatsApp("12345"))
        assertNull(enlaceWhatsApp(null))
        assertEquals("https://wa.me/51987654321", enlaceWhatsApp("987 654 321"))
        assertEquals("https://wa.me/51987654321", enlaceWhatsApp("+51 987654321"))
        assertEquals("https://wa.me/51987654321?text=Hola%20Ana", enlaceWhatsApp("987654321", "Hola Ana"))
    }

    @Test
    fun textoEncuestaUsaElPrimerNombreYElSitioPublico() {
        val t = textoEncuestaAlta("Ana María Quispe", "tok123")
        assertTrue(t.startsWith("Hola Ana 👋"))
        assertTrue(t.endsWith("https://www.saniape.com/encuesta/tok123"))
    }

    @Test
    fun codificarUrlConTildesYEmoji() {
        assertEquals("%C2%BFNos%20cuentas%3F", codificarUrl("¿Nos cuentas?"))
        assertEquals("%F0%9F%91%8B", codificarUrl("👋"))
    }

    // ── B5 / SIN_PROFESIONAL ──

    @Test
    fun fichaInactivaSoloConEstadoInactivo() {
        assertTrue(fichaInactiva("Inactivo"))
        assertFalse(fichaInactiva("En tratamiento"))
        assertFalse(fichaInactiva(null))
    }

    @Test
    fun pideProfesionalSoloEnEvaluacionSinProfesionalYSinVinculo() {
        assertTrue(pideProfesionalAlCompletar("Evaluación", null, null))
        assertTrue(pideProfesionalAlCompletar("Consulta", "", null))
        assertFalse(pideProfesionalAlCompletar("Evaluación", "ter-1", null))
        assertFalse(pideProfesionalAlCompletar("Evaluación", null, "yo"))
        assertFalse(pideProfesionalAlCompletar("Sesión", null, null))
    }
}
