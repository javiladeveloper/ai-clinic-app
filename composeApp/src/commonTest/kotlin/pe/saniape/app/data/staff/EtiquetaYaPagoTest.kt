package pe.saniape.app.data.staff

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * "✓ Ya pagó" en la agenda: gemelo de lib/__tests__/etiqueta-ya-pago.test.ts.
 * La regla es la MISMA que el badge del servidor y el 💰 verde.
 */
class EtiquetaYaPagoTest {

    private fun ya(
        tipo: String? = null, estado: String? = null, costo: Double? = null,
        pagadaAt: String? = null, tratamientoId: String? = null, servidor: String? = null,
    ) = citaYaPago(tipo, estado, costo, pagadaAt, tratamientoId, servidor)

    @Test
    fun conElEstadoDeLaAgendaMandaEse() {
        assertTrue(ya(tipo = "Sesión", tratamientoId = "t1", servidor = "pagado"))
        assertFalse(ya(tipo = "Sesión", tratamientoId = "t1", servidor = "debe"))
        assertFalse(ya(tipo = "Sesión", tratamientoId = "t1", servidor = "parcial"))
        assertFalse(ya(tipo = "Consulta", costo = 0.0, servidor = "gratis"))
    }

    @Test
    fun sinEstadoConsultaOEvaluacionConCostoYPagadaAt() {
        assertTrue(ya(tipo = "Consulta", costo = 80.0, pagadaAt = "2026-10-17T10:00:00Z"))
        assertFalse(ya(tipo = "Evaluación", costo = 80.0, pagadaAt = null))
    }

    @Test
    fun sinCostoOGratisNuncaAunqueTengaPagadaAt() {
        assertFalse(ya(tipo = "Consulta", costo = 0.0, pagadaAt = "2026-10-17T10:00:00Z"))
        assertFalse(ya(tipo = "Consulta", costo = null, pagadaAt = "2026-10-17T10:00:00Z"))
    }

    @Test
    fun sesionDeUnTratamientoSinSuEstadoNoSeAdivina() {
        assertFalse(ya(tipo = "Sesión", tratamientoId = "t1", costo = 50.0, pagadaAt = "2026-10-17T10:00:00Z"))
        assertFalse(ya(tipo = "Sesión", tratamientoId = "t1", servidor = "sin_dato"))
    }

    @Test
    fun canceladaNunca() {
        assertFalse(ya(estado = "Cancelada", servidor = "pagado"))
        assertFalse(ya(tipo = "Consulta", estado = "Cancelada", costo = 80.0, pagadaAt = "x"))
    }

    @Test
    fun coincideConLaMonedaVerdeEnLasCitasQueSeCobranSolas() {
        // estadoPagoAtencion de la cita sola: pagado ⇔ costo > 0 y pagada_at.
        assertTrue(ya(tipo = "Consulta", costo = 80.0, pagadaAt = "x"))
        assertFalse(ya(tipo = "Consulta", costo = 80.0, pagadaAt = null))
        assertFalse(ya(tipo = "Evaluación", costo = 0.0, pagadaAt = "x"))
    }

    @Test
    fun yaPagoConRespaldoElEstadoDeLaAgendaMandaSinElElDelEndpoint() {
        assertTrue(yaPagoConRespaldo("Confirmada", "pagado", false))
        assertFalse(yaPagoConRespaldo("Confirmada", "debe", true))
        assertTrue(yaPagoConRespaldo("Confirmada", null, true))
        assertTrue(yaPagoConRespaldo("Confirmada", "sin_dato", true))
        assertFalse(yaPagoConRespaldo("Confirmada", null, false))
        assertFalse(yaPagoConRespaldo("Cancelada", "pagado", true))
        assertFalse(yaPagoConRespaldo("Cancelada", null, true))
    }

    @Test
    fun textosCompletoYCortoNuncaSoloIcono() {
        assertEquals("✓ Ya pagó", TEXTO_YA_PAGO)
        assertEquals("✓ Pagó", TEXTO_YA_PAGO_CORTO)
    }
}
