package pe.saniape.app.data.staff

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * Monto que propone el cierre de una sesión en la agenda (la regla del 💰 de
 * /citas web, lib/cobro-sesion-agenda) y el costo con que nace la cita de
 * sesión agendada desde la app (como AgendarSesionesModal de la web).
 */
class CobroCierreSesionTest {

    private fun estado(estado: String, deuda: Double?, paquete: Boolean = false) =
        EstadoPagoCita(estado = estado, deuda = deuda, etiqueta = "x", esPaquete = paquete)

    // ── montoSugeridoCobroSesion: mismos casos que agenda-psiquiatria-ajustes.test.ts ──

    @Test fun sugeridoEsElCostoSinPasarDeLaDeuda() {
        assertEquals(100.0, montoSugeridoCobroSesion(100.0, 1000.0))
        assertEquals(60.0, montoSugeridoCobroSesion(100.0, 60.0))
        assertEquals(400.0, montoSugeridoCobroSesion(0.0, 400.0))
        assertEquals(400.0, montoSugeridoCobroSesion(null, 400.0))
        assertEquals(0.2, montoSugeridoCobroSesion(0.3, 0.2))
        assertEquals(0.0, montoSugeridoCobroSesion(100.0, -5.0))
    }

    // ── El cierre ──

    @Test fun sesionCreadaEnLaAppConCostoCeroProponeLaDeuda() {
        // El bug: costo 0 → el cierre proponía S/ 0. Ahora, lo que debe de la sesión.
        assertEquals(80.0, montoPropuestoCierreSesion(0.0, estado("debe", 80.0)))
        assertEquals(80.0, montoPropuestoCierreSesion(null, estado("parcial", 80.0)))
    }

    @Test fun sueltaConCostoNoPasaDeLaDeuda() {
        assertEquals(80.0, montoPropuestoCierreSesion(80.0, estado("debe", 240.0)))
        assertEquals(30.0, montoPropuestoCierreSesion(80.0, estado("parcial", 30.0)))
    }

    @Test fun paqueteProponeSuSaldo() {
        assertEquals(400.0, montoPropuestoCierreSesion(0.0, estado("parcial", 400.0, paquete = true)))
        // La modalidad de la cita también lo dice, aunque el estado no.
        assertEquals(400.0, montoPropuestoCierreSesion(100.0, estado("debe", 400.0), esPaquete = true))
    }

    @Test fun pagadoOSinCostoNoPropone() {
        assertNull(montoPropuestoCierreSesion(80.0, estado("pagado", null)))
        assertNull(montoPropuestoCierreSesion(80.0, estado("gratis", null)))
        assertNull(montoPropuestoCierreSesion(80.0, estado("debe", 0.0)))
    }

    @Test fun sinEstadoDePagoQuedaElCostoDeLaCita() {
        assertEquals(80.0, montoPropuestoCierreSesion(80.0, null))
        assertEquals(80.0, montoPropuestoCierreSesion(80.0, estado("sin_dato", null)))
        assertNull(montoPropuestoCierreSesion(0.0, null))
    }

    @Test fun elCampoMuestraElMontoSinCerosDeMas() {
        assertEquals("80", textoPrecioCampo(montoPropuestoCierreSesion(0.0, estado("debe", 80.0))))
        assertEquals("80.5", textoPrecioCampo(montoPropuestoCierreSesion(0.0, estado("debe", 80.5))))
        assertEquals("", textoPrecioCampo(montoPropuestoCierreSesion(0.0, null)))
    }

    // ── Costo de la cita de sesión agendada desde la app ──

    @Test fun sueltaNaceConElPrecioDeUnaSesion() {
        assertEquals(80.0, costoCitaDeSesion("Sesión suelta", 80.0, null, 1))
        // Precio acordado (negociado): manda, repartido entre las sesiones pactadas.
        assertEquals(70.0, costoCitaDeSesion("Sesión suelta", 80.0, 70.0, 1))
        assertEquals(60.0, costoCitaDeSesion("Sesión suelta", 80.0, 600.0, 10))
        assertEquals(33.33, costoCitaDeSesion("Sesión suelta", null, 100.0, 3))
        assertEquals(0.0, costoCitaDeSesion("Sesión suelta", null, null, 1))
    }

    @Test fun paqueteUnidadesYPagoUnicoNacenSinCosto() {
        assertEquals(0.0, costoCitaDeSesion("Paquete", 80.0, 800.0, 10))
        assertEquals(0.0, costoCitaDeSesion("Unidades", null, 3000.0, null))
        assertEquals(0.0, costoCitaDeSesion("Consulta", null, 250.0, 1))
        assertEquals(0.0, costoCitaDeSesion(null, 80.0, null, null))
    }
}
