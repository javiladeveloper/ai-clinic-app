package pe.saniape.app.data.staff

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Alta rápida desde Crear cita (gemelo de BuscadorPaciente web) + QR de admisión. */
class AltaRapidaCitaTest {

    @Test
    fun reconoceDniRutYPasaporteComoLaWeb() {
        assertTrue(pareceDocumento("44556677"))
        assertTrue(pareceDocumento(" 44556677 "))
        assertTrue(pareceDocumento("12345678-9"))      // RUT chileno
        assertTrue(pareceDocumento("12.345.678-K"))
        assertTrue(pareceDocumento("AB123456"))        // pasaporte
        assertTrue(pareceDocumento("1234567"))         // CI boliviano de 7
        assertFalse(pareceDocumento("rosa quispe"))
        assertFalse(pareceDocumento("4455"))
        assertFalse(pareceDocumento("-------"))        // sin dígitos
    }

    @Test
    fun soloSeOfreceSiNoHayCoincidencias() {
        assertEquals("44556677", documentoParaAltaRapida(" 44556677 ", hayCoincidencias = false))
        assertNull(documentoParaAltaRapida("44556677", hayCoincidencias = true))
        assertNull(documentoParaAltaRapida("rosa", hayCoincidencias = false))
    }

    @Test
    fun renieSoloConDniDe8EnSedeDePeru() {
        assertTrue(consultaPadron("44556677", "PE"))
        assertTrue(consultaPadron("44556677", null))   // sin país = Perú
        assertFalse(consultaPadron("44556677", "BO"))  // Bolivia: CI a mano
        assertFalse(consultaPadron("12345678-9", "PE"))
    }

    @Test
    fun altaSoloEnConsultaYEvaluacionYConPermisoDeAgendar() {
        assertTrue(altaRapidaPermitida("Consulta", puedeAgendar = true))
        assertTrue(altaRapidaPermitida("Evaluación", puedeAgendar = true))
        assertFalse(altaRapidaPermitida("Sesión", puedeAgendar = true))
        assertFalse(altaRapidaPermitida("Consulta", puedeAgendar = false))
    }

    @Test
    fun leeLaRespuestaDelPadron() {
        assertEquals(HallazgoPadron.Encontrado("ROSA QUISPE MAMANI"),
            interpretarPadron(200, """{"success":true,"data":{"nombre_completo":" ROSA QUISPE MAMANI "}}"""))
        assertEquals(HallazgoPadron.SinDatos, interpretarPadron(200, """{"success":false}"""))
        assertEquals(HallazgoPadron.SinDatos, interpretarPadron(404, """{"success":false,"error":"x"}"""))
        assertEquals(HallazgoPadron.SinDatos, interpretarPadron(200, "no-json"))
        assertTrue(interpretarPadron(503, "{}") is HallazgoPadron.Error)
        assertEquals(HallazgoPadron.Error("Demasiadas búsquedas. Espera un momento."), interpretarPadron(429, "{}"))
    }

    @Test
    fun leeElAltaYDetectaLaFichaDeBaja() {
        val ok = interpretarAltaRapida(200, """{"ok":true,"id":"p1","existente":false}""")
        assertTrue(ok.ok); assertEquals("p1", ok.id); assertFalse(ok.inactivo)
        val baja = interpretarAltaRapida(200, """{"ok":true,"id":"p9","existente":true,"inactivo":true}""")
        assertTrue(baja.inactivo)
        val err = interpretarAltaRapida(403, """{"error":"Modo lectura"}""")
        assertFalse(err.ok); assertEquals("Modo lectura", err.error)
        assertEquals("No se pudo registrar.", interpretarAltaRapida(500, "").error)
    }

    @Test
    fun qrDeAdmision() {
        val r = interpretarAdmisionQr(200, """{"url":"https://www.saniape.com/admision/abc","expira":"x","ttlMin":10}""")
        assertEquals(AdmisionQr("https://www.saniape.com/admision/abc", 10), r.qr)
        assertNull(r.error)
        assertEquals(10, interpretarAdmisionQr(200, """{"url":"https://x.pe/admision/a"}""").qr?.ttlMin)
        val sinPermiso = interpretarAdmisionQr(403, """{"error":"No tienes permiso para registrar pacientes"}""")
        assertNull(sinPermiso.qr); assertEquals("No tienes permiso para registrar pacientes", sinPermiso.error)
        assertNull(interpretarAdmisionQr(200, """{"url":"javascript:alert(1)"}""").qr)
        assertEquals("10:00", cuentaAtras(600))
        assertEquals("00:59", cuentaAtras(59))
        assertEquals("00:00", cuentaAtras(-3))
        assertTrue(textoWhatsAppAdmision("https://x/a", 10, "DALU").contains("en DALU"))
        assertTrue(textoWhatsAppAdmision("https://x/a", 10, null).endsWith("https://x/a"))
    }
}
