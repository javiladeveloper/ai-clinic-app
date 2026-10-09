package pe.saniape.app.data.staff

import kotlinx.datetime.Instant
import kotlinx.serialization.json.jsonPrimitive
import pe.saniape.app.ui.clinica.atencion.textoCobrado
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Bugs de CAJA hallados grabando tutoriales (2026-10-08), lado app.
 * La regla de dinero vive en el servidor; acá, lo que decide la pantalla.
 */
class BugsCajaTest {

    private fun trat(estadoPago: String?, modalidad: String = "Paquete") = TratamientoPaciente(
        id = "t1", procedimiento = "Rehabilitación", terapeutaId = null, terapeutaNombre = null, modalidad = modalidad,
        estado = "Activo", estadoPago = estadoPago, totalSesiones = 10, sesionesCompletadas = 3,
        precioPaquete = 500.0, precioPorSesion = null, precioAcordado = null, usaSesiones = true,
        diagnostico = null, medicacion = null, proximoControl = null, especialidadNombre = null,
    )

    // ── Bug 4: paquete pagado completo → sin "💳 Cobrar" por sesión ──

    @Test
    fun paquetePagadoCompletoNoOfreceCobrarPorSesion() {
        assertTrue(trat("Pagado").sinSaldo)
    }

    @Test
    fun conSaldoOSinDatoSeSigueCobrando() {
        assertFalse(trat("Parcial").sinSaldo)
        assertFalse(trat("Pendiente").sinSaldo)
        // Sin estado de pago leído: ante la duda se ofrece cobrar, como antes.
        assertFalse(trat(null).sinSaldo)
    }

    // ── Bug 3: el cobro entra a la caja de HOY ──

    @Test
    fun deNocheEnLimaElCobroSigueSiendoDeHoy() {
        // 2026-10-08 04:00 UTC = 2026-10-07 23:00 en Lima.
        assertEquals("2026-10-07", hoyClinicaIso(Instant.parse("2026-10-08T04:00:00Z")))
    }

    @Test
    fun elToastSoloDiceFechadoSiNoEsHoy() {
        // Cobro de hoy de una cita de ayer: entra hoy, sin "fechado el".
        assertEquals("Cobrado S/ 40.00", textoCobrado("Evaluación", "S/ 40.00", "cobrar", "2026-10-07", "2026-10-07"))
        // Recepción eligió otro día (el paciente pagó ayer).
        assertEquals("Cobrado S/ 40.00 (fechado el 06/10)", textoCobrado("Evaluación", "S/ 40.00", "cobrar", "2026-10-06", "2026-10-07"))
    }

    // ── Revisión: con los pagos cargados manda lo pagado, no el estado_pago guardado ──

    @Test
    fun conPagosCargadosMandaLoPagadoNoElEstadoGuardado() {
        // estado_pago viejo "Pagado" pero el precio subió a 700 y se pagaron 500: se cobra.
        assertFalse(tratamientoSinSaldo(700.0, 500.0, "Pagado"))
        // estado_pago viejo "Parcial" pero ya se pagó todo: no se cobra.
        assertTrue(tratamientoSinSaldo(500.0, 500.0, "Parcial"))
        assertTrue(tratamientoSinSaldo(500.0, 499.996, null))
        // Sin precio acordado nunca queda saldado.
        assertFalse(tratamientoSinSaldo(0.0, 100.0, "Pagado"))
        // Sin pagos cargados: el estado guardado de respaldo.
        assertTrue(tratamientoSinSaldo(500.0, null, "Pagado"))
        assertTrue(trat("Parcial").sinSaldoCon(500.0))
        assertFalse(trat("Pagado").sinSaldoCon(100.0))
    }

    @Test
    fun montoAcordadoDeUnidadesComoLaWeb() {
        val u = trat(null, modalidad = "Unidades").copy(precioPaquete = null, cantidadUnidades = 4000, precioUnitario = 1.5)
        assertEquals(6000.0, u.montoAcordado)
        assertEquals(400.0, u.copy(precioAcordado = 400.0).montoAcordado)
    }

    // ── Revisión: la app nueva marca la fecha como elegida ──

    @Test
    fun cobrarConFechaMandaFechaElegida() {
        val c = cuerpoCobrarCita("c1", "Yape", "cobrar", "2026-10-07")
        assertEquals("2026-10-07", c["fecha"]!!.jsonPrimitive.content)
        assertEquals("true", c["fechaElegida"]!!.jsonPrimitive.content)
        val sin = cuerpoCobrarCita("c1", "Yape", "cobrar", null)
        assertFalse("fecha" in sin)
        assertFalse("fechaElegida" in sin)
    }
}
