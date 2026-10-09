package pe.saniape.app.data.staff

import kotlinx.datetime.Instant
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
}
