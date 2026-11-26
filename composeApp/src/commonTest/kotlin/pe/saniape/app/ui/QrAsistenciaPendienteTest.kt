package pe.saniape.app.ui

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** 🕘 El enlace del QR de asistencia que abre la app (26/11/2026). */
class QrAsistenciaPendienteTest {
    private val llave = "0123456789abcdef0123456789abcdef"

    @Test
    fun reconoceElEnlaceDelQr() {
        assertEquals(llave, qrDeEnlaceAsistencia("https://www.saniape.com/asistencia?qr=$llave"))
        assertEquals(llave, qrDeEnlaceAsistencia("https://saniape.com/asistencia/?otro=1&qr=$llave#x"))
    }

    @Test
    fun ignoraLoQueNoEsElQr() {
        assertNull(qrDeEnlaceAsistencia(null))
        assertNull(qrDeEnlaceAsistencia("https://www.saniape.com/asistencia"))                 // sin llave
        assertNull(qrDeEnlaceAsistencia("https://www.saniape.com/asistencia?qr=corta"))         // llave inválida
        assertNull(qrDeEnlaceAsistencia("https://www.saniape.com/citas?qr=$llave"))             // otra pantalla
        assertNull(qrDeEnlaceAsistencia("https://otro-sitio.com/asistencia?qr=$llave"))         // otro dominio
        assertNull(qrDeEnlaceAsistencia("https://www.saniape.com.evil.com/asistencia?qr=$llave"))
    }

    @Test
    fun laLlaveSeUsaUnaSolaVez() {
        QrAsistenciaPendiente.pedir("https://www.saniape.com/asistencia?qr=$llave")
        assertEquals(llave, QrAsistenciaPendiente.actual)
        // Un enlace cualquiera después no la pisa.
        QrAsistenciaPendiente.pedir("https://www.saniape.com/citas")
        assertEquals(llave, QrAsistenciaPendiente.consumir())
        assertNull(QrAsistenciaPendiente.actual)
        assertNull(QrAsistenciaPendiente.consumir())
    }
}
