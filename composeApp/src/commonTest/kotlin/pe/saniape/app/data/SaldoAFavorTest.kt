package pe.saniape.app.data

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import pe.saniape.app.data.staff.TratamientoPaciente

/**
 * Saldo a favor (regla del 2026-10-05, gemela de lib/saldo-a-favor.ts): todas las
 * modalidades; en Sesión suelta / Consulta el acordado efectivo crece con lo
 * realizado. Y el portal: cancelados aparte, eliminados nunca.
 */
class SaldoAFavorTest {

    private fun trat(
        id: String, modalidad: String, precioAcordado: Double?, estado: String = "Activo",
        totalSesiones: Int = 0, precioPorSesion: Double? = null,
        sesiones: Int? = 0, citas: Int? = 0,
    ) = TratamientoPaciente(
        id = id, procedimiento = id, terapeutaId = null, terapeutaNombre = null, modalidad = modalidad,
        estado = estado, estadoPago = null, totalSesiones = totalSesiones, sesionesCompletadas = 0,
        precioPaquete = null, precioPorSesion = precioPorSesion, precioAcordado = precioAcordado,
        usaSesiones = modalidad != "Consulta", diagnostico = null, medicacion = null, proximoControl = null,
        especialidadNombre = null, sesionesRealizadas = sesiones, citasNoCanceladas = citas,
    )

    @Test
    fun emerson_demo_tiene_205_97_a_favor() {
        val cuentas = listOf(
            trat("c1", "Consulta", 50.0, citas = 1).cuentaCon(150.0),          // 100
            trat("c2", "Consulta", 30.0, citas = 1).cuentaCon(45.0),           // 15
            trat("c3", "Consulta", 15.0, citas = 1).cuentaCon(50.0),           // 35
            trat("s1", "Sesión suelta", 80.0, totalSesiones = 1, sesiones = 1).cuentaCon(96.0),   // 16
            trat("u1", "Unidades", 180.0).cuentaCon(200.0),                    // 20
            trat("u2", "Unidades", 180.0).cuentaCon(199.97),                   // 19.97
            trat("p1", "Paquete", 400.0).cuentaCon(50.0),                      // debe, no resta
            trat("x1", "Paquete", 100.0, estado = "Cancelado").cuentaCon(300.0),
            trat("x2", "Unidades", 180.0, estado = "Cancelado").cuentaCon(0.0),
            trat("x3", "Consulta", 50.0, estado = "Eliminado", citas = 1).cuentaCon(500.0),
        )
        assertEquals(205.97, saldoAFavorDe(cuentas))
        assertEquals(listOf(100.0, 15.0, 35.0, 16.0, 20.0, 19.97, 0.0, 0.0, 0.0, 0.0),
            cuentas.map { saldoAFavorTratamiento(it) })
    }

    @Test
    fun suelta_con_sesiones_realizadas_pagadas_no_es_a_favor() {
        // pps 100, total 1, 7 sesiones hechas y 700 pagados: está al día, no a favor.
        val t = trat("s", "Sesión suelta", 100.0, totalSesiones = 1, precioPorSesion = 100.0, sesiones = 7)
        assertEquals(700.0, acordadoEfectivo(t.cuentaCon(700.0)))
        assertEquals(0.0, saldoAFavorTratamiento(t.cuentaCon(700.0)))
        assertEquals(50.0, saldoAFavorTratamiento(t.cuentaCon(750.0)))
    }

    @Test
    fun realizadas_es_el_mayor_entre_sesiones_y_citas() {
        val t = trat("s", "Sesión suelta", null, totalSesiones = 1, precioPorSesion = 50.0, sesiones = 2, citas = 4)
        assertEquals(200.0, acordadoEfectivo(t.cuentaCon(0.0)))
        // unit = precio_acordado / total_sesiones si no hay precio por sesión.
        val c = CuentaTratamiento(acordado = 300.0, pagado = 400.0, estado = "Activo", modalidad = "Consulta",
            precioAcordado = 300.0, totalSesiones = 3, citasNoCanceladas = 4)
        assertEquals(100.0, precioUnitario(c))
        assertEquals(0.0, saldoAFavorTratamiento(c))   // 4 × 100 = 400 = lo pagado
    }

    @Test
    fun ante_la_duda_no() {
        // Sin precio (unit 0 / acordado 0).
        assertEquals(0.0, saldoAFavorTratamiento(trat("a", "Consulta", null, citas = 1).cuentaCon(40.0)))
        assertEquals(0.0, saldoAFavorTratamiento(trat("b", "Paquete", 0.0).cuentaCon(40.0)))
        // Sin conteos cargados (lista, caché vieja): el cobro por atención no arriesga.
        assertEquals(0.0, saldoAFavorTratamiento(
            trat("c", "Sesión suelta", 80.0, totalSesiones = 1, sesiones = null, citas = null).cuentaCon(320.0)))
        // ...pero Paquete / Unidades no dependen de los conteos.
        assertEquals(50.0, saldoAFavorTratamiento(
            trat("d", "Paquete", 100.0, sesiones = null, citas = null).cuentaCon(150.0)))
        // Medio centavo y redondeo a 2 decimales.
        assertEquals(0.0, saldoAFavorTratamiento(trat("e", "Paquete", 100.0).cuentaCon(100.004)))
        assertEquals(0.1, saldoAFavorTratamiento(trat("f", "Paquete", 100.0).cuentaCon(100.104)))
        assertEquals(50.0, saldoAFavorTratamiento(trat("g", "Paquete", 100.0, estado = "Alta").cuentaCon(150.0)))
        assertEquals(0.0, saldoAFavorDe(emptyList()))
    }

    private fun portal(id: String, estado: String) = Tratamiento(
        id = id, procedimiento = id, clinica = "DALU", estado = estado, usaSesiones = true,
        totalSesiones = 10, sesionesCompletadas = 0, fechaInicio = null, sesiones = emptyList(),
    )

    @Test
    fun portal_cancelados_aparte_y_eliminados_nunca() {
        val s = separarTratamientosPortal(listOf(
            portal("alta", "Alta"), portal("cancelado", "Cancelado"), portal("activo", "Activo"),
            portal("eliminado", "Eliminado"), portal("cancelado2", "Cancelado"),
        ))
        assertEquals(listOf("activo", "alta"), s.vigentes.map { it.id })
        assertEquals(listOf("cancelado", "cancelado2"), s.cancelados.map { it.id })
        assertTrue(separarTratamientosPortal(listOf(portal("e", "Eliminado"))).let { it.vigentes.isEmpty() && it.cancelados.isEmpty() })
    }
}
