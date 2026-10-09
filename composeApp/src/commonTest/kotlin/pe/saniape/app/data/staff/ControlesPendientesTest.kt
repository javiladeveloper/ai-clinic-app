package pe.saniape.app.data.staff

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Decisión del dueño (2026-10-08): servicio único / unidades con controles
 * post-tratamiento pendientes sigue ABIERTO "En control" hasta el último.
 * Gemelo de lib/__tests__/controles-pendientes.test.ts (lo que pinta la app).
 */
class ControlesPendientesTest {
    private fun trat(
        estado: String = "Activo", modalidad: String = "Consulta", anclaje: String? = null,
        citaOrigen: String? = null,
    ) = TratamientoPaciente(
        id = "t1", procedimiento = "Toxina botulínica", terapeutaId = null, terapeutaNombre = null, modalidad = modalidad,
        estado = estado, estadoPago = null, totalSesiones = 0, sesionesCompletadas = 0,
        precioPaquete = null, precioPorSesion = null, precioAcordado = 900.0,
        usaSesiones = false, diagnostico = null, medicacion = null, proximoControl = null,
        especialidadNombre = null, modoCobro = "simple", precioBase = 900.0,
        controlAnclaje = anclaje, citaOrigenId = citaOrigen,
    )

    private fun ctl(n: Int, estado: String) =
        CitaCtl("ctl$n", "Sesión", estado, "🔁 Control $n (15 días) — protocolo automático [control:$n]. Confirmar con el paciente.")

    @Test
    fun resumen_cuenta_pendientes_y_hechos_sin_cancelados() {
        val r = resumenControles(listOf(ctl(1, "Completada"), ctl(2, "Confirmada"), ctl(3, "Cancelada"), CitaCtl("x", "Sesión", "Completada", "otra")))
        assertEquals(ResumenControles(total = 2, hechos = 1, pendientes = 1), r)
        assertEquals("Control 1/2", etiquetaControles(r))
        assertEquals("Control", etiquetaControles(ResumenControles(1, 1, 0)))
        assertEquals("Controles", etiquetaControles(ResumenControles(2, 2, 0)))
    }

    @Test
    fun servicio_unico_realizado_con_control_pendiente_esta_en_control() {
        val aplicacion = CitaCtl("c1", "Sesión", "Completada", null)
        val citas = listOf(aplicacion, ctl(1, "Pendiente"))
        assertTrue(enControl(trat(), citas))
        assertTrue(realizadoParaRecorrido(trat(), citas))
        // "Registrar servicio" sin cita: la escalera arrancada es la señal.
        assertTrue(enControl(trat(anclaje = "2026-10-08"), listOf(ctl(1, "Pendiente"))))
    }

    @Test
    fun sin_controles_pendientes_manda_el_estado() {
        val citas = listOf(CitaCtl("c1", "Sesión", "Completada", null), ctl(1, "Completada"))
        assertFalse(enControl(trat(), citas))
        assertFalse(realizadoParaRecorrido(trat(), citas))   // Activo sin pendientes = "Por hacer" (revertido)
        assertTrue(realizadoParaRecorrido(trat(estado = "Completado"), citas))
    }

    @Test
    fun la_evaluacion_o_la_cita_de_origen_no_cuentan_como_realizado() {
        val pend = ctl(1, "Pendiente")
        assertFalse(enControl(trat(), listOf(CitaCtl("e", "Evaluación", "Completada", null), pend)))
        assertFalse(enControl(trat(citaOrigen = "o"), listOf(CitaCtl("o", "Consulta", "Completada", null), pend)))
    }

    @Test
    fun paquete_por_sesiones_nunca_esta_en_control() {
        val citas = listOf(CitaCtl("c1", "Sesión", "Completada", null), ctl(1, "Pendiente"))
        assertFalse(enControl(trat(modalidad = "Paquete"), citas))
    }
}
