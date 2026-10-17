package pe.saniape.app.data.staff

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Apertura automática de caja (web 2026-10-16): lo que la app lee y muestra. */
class CajaAperturaTest {

    private fun dia(json: String) = FinanzasRepo.parsearDiaCierre(Json.parseToJsonElement(json) as JsonObject)!!

    @Test fun leeAperturaAjustesYEsperados() {
        val d = dia(
            """{"fecha":"2026-10-17","cerrable":true,"totalIngresos":300,"totalEgresos":20,"neto":280,
               "esperado":330,"esperadoMovimientos":180,"esperadoAlActualizar":330,"totalAjustes":-50,
               "apertura":{"monto":200,"desde":"2026-10-15","cierreId":"c1","guardada":false,"diasSinCerrar":["2026-10-16"]},
               "ajustes":[{"id":"a1","monto":-50,"motivo":"Depósito al banco","creadoPorNombre":"Rosa","createdAt":"2026-10-17T15:00:00Z","despuesDelCierre":false}],
               "ajustesDisponibles":true,"cierrePosterior":null,"porMetodo":[],"movimientos":[]}""",
        )
        assertEquals(200.0, d.apertura?.monto)
        assertEquals("2026-10-15", d.apertura?.desde)
        assertEquals(listOf("2026-10-16"), d.apertura?.diasSinCerrar)
        assertEquals(1, d.ajustes.size)
        assertEquals("Rosa", d.ajustes[0].creadoPorNombre)
        assertEquals(-50.0, d.totalAjustes)
        assertEquals(180.0, d.esperadoMovimientos)
        assertEquals(330.0, d.esperado)
        assertTrue(d.ajustesDisponibles)
        assertNull(d.cierrePosterior)
    }

    @Test fun servidorSinMigracionSeVeComoAntes() {
        val d = dia("""{"fecha":"2026-10-17","cerrable":true,"esperado":150,"porMetodo":[],"movimientos":[]}""")
        assertNull(d.apertura)
        assertTrue(d.ajustes.isEmpty())
        assertNull(d.esperadoMovimientos)
        assertNull(d.esperadoAlActualizar)
        assertFalse(d.ajustesDisponibles)
        assertEquals("(cobros en efectivo − gastos en efectivo)", CajaApertura.rotuloEsperado(conApertura = false, hayAjustes = false))
    }

    @Test fun textosComoLaWeb() {
        assertEquals("(lo contado al cerrar el 15/10)", CajaApertura.origenApertura(AperturaCajaApp(200.0, "2026-10-15")))
        assertEquals("(lo contado al cerrar el 15/10 + los días sin cerrar)", CajaApertura.origenApertura(AperturaCajaApp(200.0, "2026-10-15", listOf("2026-10-16"))))
        assertEquals("(primera apertura con Sania)", CajaApertura.origenApertura(AperturaCajaApp(0.0, null)))
        assertTrue(CajaApertura.mensajeDiasSinCerrar(listOf("2026-10-14", "2026-10-15")).startsWith("Hay 2 días con movimientos sin cerrar (14/10, 15/10)"))
        assertTrue(CajaApertura.mensajeDiasSinCerrar(listOf("2026-10-14")).startsWith("Hay 1 día "))
        assertTrue(CajaApertura.mensajeCierrePosterior("2026-10-16").startsWith("Ya se cerró la caja del 16/10"))
        assertEquals("Retiro", CajaApertura.etiquetaAjuste(-10.0))
        assertEquals("Ingreso de efectivo", CajaApertura.etiquetaAjuste(10.0))
        assertEquals("(apertura + ajustes + cobros − gastos en efectivo)", CajaApertura.rotuloEsperado(true, true))
        assertEquals("(apertura + cobros − gastos en efectivo)", CajaApertura.rotuloEsperado(true, false))
    }

    @Test fun validarAjusteYRecuento() {
        assertEquals("Escribe el monto", CajaApertura.validarAjuste(null, "Depósito"))
        assertEquals("Escribe el monto", CajaApertura.validarAjuste(0.0, "Depósito"))
        assertTrue(CajaApertura.validarAjuste(50.0, " ab ")!!.startsWith("Escribe el motivo"))
        assertNull(CajaApertura.validarAjuste(50.0, "Depósito al banco"))
        assertTrue(CajaApertura.recuentoCambia(cerrado = true, cerrable = true, esperado = 100.0, esperadoAlActualizar = 50.0))
        assertFalse(CajaApertura.recuentoCambia(cerrado = true, cerrable = true, esperado = 100.0, esperadoAlActualizar = 100.001))
        assertFalse(CajaApertura.recuentoCambia(cerrado = false, cerrable = true, esperado = 100.0, esperadoAlActualizar = 50.0))
        assertFalse(CajaApertura.recuentoCambia(cerrado = true, cerrable = true, esperado = 100.0, esperadoAlActualizar = null))
    }
}
