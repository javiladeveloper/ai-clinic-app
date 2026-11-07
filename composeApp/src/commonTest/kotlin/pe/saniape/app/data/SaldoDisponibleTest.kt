package pe.saniape.app.data

import kotlin.test.Test
import kotlin.test.assertEquals
import pe.saniape.app.data.staff.TratamientoPaciente

/**
 * Saldo a favor DISPONIBLE (pagar con saldo, 2026-10-06), gemelo de
 * saldoDisponibleTratamiento / valorAtendidoCancelado (lib/saldo-a-favor.ts) y de
 * _saldo_por_tratamiento() en SQL. Casos del spec §2.2 y la opción A del dueño:
 * lo pagado de un CANCELADO no es saldo hasta que un Admin lo libera.
 */
class SaldoDisponibleTest {

    private fun trat(
        modalidad: String, precioAcordado: Double?, estado: String = "Activo", totalSesiones: Int = 0,
        precioPorSesion: Double? = null, sesiones: Int? = 0, citas: Int? = 0, liberado: Boolean = false,
    ) = TratamientoPaciente(
        id = "t", procedimiento = "t", terapeutaId = null, terapeutaNombre = null, modalidad = modalidad,
        estado = estado, estadoPago = null, totalSesiones = totalSesiones, sesionesCompletadas = 0,
        precioPaquete = null, precioPorSesion = precioPorSesion, precioAcordado = precioAcordado,
        usaSesiones = modalidad != "Consulta", diagnostico = null, medicacion = null, proximoControl = null,
        especialidadNombre = null, sesionesRealizadas = sesiones, citasNoCanceladas = citas,
        saldoLiberadoAt = if (liberado) "2026-10-06T15:00:00Z" else null,
    )

    @Test
    fun cancelado_sin_liberar_nunca_es_saldo() {
        val c = trat("Paquete", 1200.0, "Cancelado", totalSesiones = 10).cuentaCon(1200.0)
        assertEquals(0.0, saldoDisponibleTratamiento(c))
        // …pero el aviso al Admin sí dice cuánto podría pasar.
        assertEquals(1200.0, noAtendidoCancelado(c))
    }

    @Test
    fun cancelado_liberado_aporta_lo_pagado_menos_lo_atendido() {
        // Paquete 1200 sin sesiones → 1200.
        assertEquals(1200.0, saldoDisponibleTratamiento(trat("Paquete", 1200.0, "Cancelado", 10, liberado = true).cuentaCon(1200.0)))
        // Paquete 250 con 3/3 hechas → 0.
        assertEquals(0.0, saldoDisponibleTratamiento(trat("Paquete", 250.0, "Cancelado", 3, sesiones = 3, liberado = true).cuentaCon(250.0)))
        // Paquete 300 de 3 con 1 hecha → 200.
        assertEquals(200.0, saldoDisponibleTratamiento(trat("Paquete", 300.0, "Cancelado", 3, sesiones = 1, liberado = true).cuentaCon(300.0)))
        // Unidades con una atención → 0 (no se puede prorratear: ante la duda, no).
        assertEquals(0.0, saldoDisponibleTratamiento(trat("Unidades", 500.0, "Cancelado", citas = 1, liberado = true).cuentaCon(500.0)))
        // Consulta 180 con una atención y 300 pagados → 120.
        assertEquals(120.0, saldoDisponibleTratamiento(trat("Consulta", 180.0, "Cancelado", 1, citas = 1, liberado = true).cuentaCon(300.0)))
    }

    @Test
    fun cancelado_sin_conteos_no_arriesga_saldo() {
        val c = trat("Paquete", 300.0, "Cancelado", 3, sesiones = null, citas = null, liberado = true).cuentaCon(300.0)
        assertEquals(0.0, saldoDisponibleTratamiento(c))
        assertEquals(0.0, noAtendidoCancelado(c))
    }

    @Test
    fun lo_usado_en_otro_tratamiento_ya_viene_restado() {
        // Liberado 400, ya dio 150 como saldo (consumo negativo): quedan 250.
        val c = trat("Paquete", 400.0, "Cancelado", 4, liberado = true).cuentaCon(400.0 - 150.0)
        assertEquals(250.0, saldoDisponibleTratamiento(c))
    }

    @Test
    fun eliminado_nunca_y_los_demas_como_siempre() {
        assertEquals(0.0, saldoDisponibleTratamiento(trat("Paquete", 100.0, "Eliminado").cuentaCon(300.0)))
        assertEquals(50.0, saldoDisponibleTratamiento(trat("Paquete", 100.0).cuentaCon(150.0)))
        // Disponible del paciente: excedente + cancelado liberado; el no liberado no suma.
        val total = saldoDisponibleDe(listOf(
            trat("Paquete", 100.0).cuentaCon(150.0),                                         // 50
            trat("Paquete", 300.0, "Cancelado", 3, sesiones = 1, liberado = true).cuentaCon(300.0), // 200
            trat("Paquete", 900.0, "Cancelado", 3).cuentaCon(900.0),                         // 0 (sin liberar)
            trat("Consulta", 50.0, "Eliminado", citas = 1).cuentaCon(500.0),                 // 0
        ))
        assertEquals(250.0, total)
    }

    @Test
    fun suspendido_liberado_cuenta_solo_lo_completado() {
        // Jonathan 06/11/2026 (opción A): paquete de 10 a S/1000 pagado completo;
        // 4 sesiones completadas y 2 quedaron agendadas al suspender.
        val base = CuentaTratamiento(
            acordado = 1000.0, pagado = 1000.0, estado = "Suspendido", modalidad = "Paquete",
            totalSesiones = 10, sesionesRealizadas = 6, citasNoCanceladas = 6, sesionesCompletadas = 4,
        )
        assertEquals(0.0, saldoDisponibleTratamiento(base))                      // sin liberar: como siempre
        assertEquals(600.0, noAtendidoCancelado(base))                          // el aviso al Admin
        assertEquals(600.0, saldoDisponibleTratamiento(base.copy(saldoLiberado = true)))
        assertEquals(0.0, noAtendidoCancelado(base.copy(sesionesCompletadas = null))) // sin conteo: ante la duda, no
        // Un excedente normal de un suspendido sigue siendo automático.
        assertEquals(100.0, saldoDisponibleTratamiento(base.copy(pagado = 1100.0)))
    }
}
