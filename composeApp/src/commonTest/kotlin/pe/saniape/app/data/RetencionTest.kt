package pe.saniape.app.data

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive
import pe.saniape.app.data.staff.*
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class RetencionTest {

    private fun fila(saldo: Double, pagado: Double, completadas: Int, total: Int = 10, dias: Int = 20, id: String = "t") = FilaRetencion(
        tratamientoId = id, pacienteId = "p", nombre = "N", dni = null, telefono = null, terapeutaId = null, profesional = null,
        servicio = null, especialidad = null, modalidad = "Paquete", estado = "Activo", noVolvio = false,
        totalSesiones = total, completadas = completadas, quedan = total - completadas, usaSesiones = true,
        fechaInicio = null, ultimaSesion = null, diasSin = dias, proximaCita = null, proximaHora = null, proximoControl = null,
        pagado = pagado, saldo = saldo, diagnostico = null, motivo = null, edad = null,
    )

    @Test fun presetAbandonoSeReconoceYSeTraduceARpc() {
        val f = aplicarPresetRet("abandono")
        assertEquals("abandono", presetActivoRet(f))
        val rpc = filtrosARpc(f)
        assertEquals("45", (rpc["dias_min"] as JsonPrimitive).content)
        assertEquals(true, (rpc["sin_cita"] as JsonPrimitive).content.toBoolean())
        assertEquals(1, (rpc["estados"] as JsonArray).size)
        // Tocar otro filtro deja de ser el preset.
        assertEquals("", presetActivoRet(f.copy(saldo = "si")))
    }

    @Test fun rangosAlReves() {
        val rpc = filtrosARpc(FiltrosLlamadas(sesionDesde = 10, sesionHasta = 5))
        assertEquals("5", (rpc["sesion_desde"] as JsonPrimitive).content)
        assertEquals("10", (rpc["sesion_hasta"] as JsonPrimitive).content)
    }

    @Test fun prioridadPonderaDeudaYPagoSinRecibir() {
        val debeMucho = fila(saldo = 500.0, pagado = 0.0, completadas = 5, id = "a")
        val pagoTodoSinVenir = fila(saldo = 0.0, pagado = 800.0, completadas = 0, id = "b")
        val poco = fila(saldo = 20.0, pagado = 100.0, completadas = 1, id = "c")
        val r = priorizarNoVuelven(listOf(poco, debeMucho, pagoTodoSinVenir))
        assertEquals(listOf("b", "a", "c"), r.map { it.fila.tratamientoId })
        assertEquals(SituacionPago.PAGO_TODO_NO_VOLVIO, r[0].situacion)
        assertEquals("alta", r[0].prioridad)
        assertEquals("baja", r[2].prioridad)
    }

    @Test fun nivelAbandonoPorDias() {
        assertNull(nivelAbandono(fila(0.0, 0.0, 1, dias = 44)))
        assertEquals("probable", nivelAbandono(fila(0.0, 0.0, 1, dias = 45)))
        assertEquals("critico", nivelAbandono(fila(0.0, 0.0, 1, dias = 60)))
    }

    @Test fun cuotasSeAplicanEnOrden() {
        val plan = listOf(CuotaPlan(0, 100.0, "2026-09-01"), CuotaPlan(1, 100.0, "2026-10-01"), CuotaPlan(2, 100.0, "2026-10-20"))
        val r = estadoCuotas(plan, 150.0, "2026-10-08")
        assertEquals(EstadoCuota.PAGADA, r[0].estado)
        assertEquals(EstadoCuota.POR_VENCER.takeIf { false } ?: EstadoCuota.ATRASADA, r[1].estado)
        assertEquals(50.0, r[1].saldo)
        assertTrue(textoEstadoCuota(r[1]).contains("parcial"))
        assertEquals(7, r[1].diasAtraso)
        assertEquals(EstadoCuota.POR_VENCER, r[2].estado)
        assertTrue(cuotaParaAvisar(r[2]).not()) // vence en 12 días
    }

    @Test fun controlVencidoSeAgendaHoy() {
        assertEquals("2026-10-08", fechaParaControlRet("2026-09-01", "2026-10-08"))
        assertEquals("2026-10-20", fechaParaControlRet("2026-10-20", "2026-10-08"))
    }

    @Test fun csvEscapaComillas() {
        assertTrue(csvDe(listOf(listOf("a\"b", "c"))).contains("\"a\"\"b\",\"c\""))
    }
}
