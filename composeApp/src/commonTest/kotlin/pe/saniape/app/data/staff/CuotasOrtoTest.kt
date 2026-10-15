package pe.saniape.app.data.staff

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * 💰 Cuotas en la pestaña 😁 Ortodoncia — gemelo de `lib/__tests__/cuotas.test.ts`
 * de la web (los MISMOS casos: inicial 700 + 16 cuotas de 125, total 2700) y de
 * lo que pinta `ResumenCuotasOrto.tsx`.
 */
class CuotasOrtoTest {

    /** generarCronograma({ total: 2700, inicial: 700, fechaInicial: '2026-05-15', nCuotas: 16, primerVencimiento: '2026-06-15' }) */
    private val plan: List<CuotaPlan> = listOf(CuotaPlan(0, 700.0, "2026-05-15")) + (1..16).map { i ->
        val mes = 5 + i // junio = 6
        val anio = 2026 + (mes - 1) / 12
        val m = (mes - 1) % 12 + 1
        CuotaPlan(i, 125.0, "$anio-${m.toString().padStart(2, '0')}-15")
    }

    @Test fun elEjemploInicialYCuatroPagadasLaQuintaAtrasada() {
        val c = estadoCuotas(plan, 700.0 + 4 * 125, "2026-10-25")
        assertEquals(EstadoCuota.PAGADA, c[0].estado)
        assertEquals(EstadoCuota.PAGADA, c[4].estado)
        assertEquals(5, c[5].numero)
        assertEquals(EstadoCuota.ATRASADA, c[5].estado)
        assertEquals(10, c[5].diasAtraso)
        assertEquals(125.0, c[5].saldo)
        assertEquals("Atrasada 10 días", textoEstadoCuota(c[5]))
        assertEquals(EstadoCuota.POR_VENCER, c[6].estado)
        assertEquals(EstadoCuota.PROGRAMADA, c[7].estado)
        val r = resumenCuotas(c)
        assertEquals(5, r.pagadas)
        assertEquals(16, r.nCuotas)
        assertEquals(1, r.atrasadas)
        assertEquals(125.0, r.montoAtrasado)
        assertEquals(false, r.alDia)
        assertEquals(2700.0, r.totalPlan)
        assertEquals(5, r.proxima?.numero)
        assertEquals("5 de 16", etiquetaCuota(5, r.nCuotas))
        assertEquals("Inicial", etiquetaCuota(0, r.nCuotas))
    }

    @Test fun pagoParcialSigueAtrasada() {
        val c = estadoCuotas(plan, 700.0 + 60, "2026-07-01")
        assertEquals(EstadoCuota.ATRASADA, c[1].estado)
        assertEquals(60.0, c[1].pagado)
        assertEquals(65.0, c[1].saldo)
        assertTrue(textoEstadoCuota(c[1]).contains("parcial"))
    }

    @Test fun venceHoyYLoDeSieteDiasEsPorVencer() {
        val c = estadoCuotas(plan, 700.0, "2026-06-15")
        assertEquals(EstadoCuota.VENCE_HOY, c[1].estado)
        assertEquals(EstadoCuota.PROGRAMADA, c[2].estado)
        val semanal = estadoCuotas(listOf(
            CuotaPlan(1, 10.0, "2026-10-01"), CuotaPlan(2, 10.0, "2026-10-05"), CuotaPlan(3, 10.0, "2026-11-05"),
        ), 0.0, "2026-09-30")
        assertEquals(listOf(EstadoCuota.POR_VENCER, EstadoCuota.POR_VENCER, EstadoCuota.PROGRAMADA), semanal.map { it.estado })
    }

    @Test fun todoPagadoODeMasQuedaAlDia() {
        val c = estadoCuotas(plan, 3000.0, "2030-01-01")
        assertTrue(c.all { it.estado == EstadoCuota.PAGADA })
        val r = resumenCuotas(c)
        assertEquals(0, r.atrasadas)
        assertTrue(r.alDia)
        assertNull(r.proxima)
        assertNull(textoProximaCuota(r, "PEN"))
        assertNull(textoAtrasoCuotas(r, "PEN"))
    }

    @Test fun sinPagosYTodoFuturo() {
        val c = estadoCuotas(plan, 0.0, "2026-05-01")
        assertEquals(EstadoCuota.POR_VENCER, c[0].estado)
        assertTrue(c.drop(1).all { it.estado == EstadoCuota.PROGRAMADA })
    }

    // ── lo que pinta la tarjeta (ResumenCuotasOrto.tsx) ──

    @Test fun lineasDeLaTarjetaEnLaMonedaDelTratamiento() {
        val r = resumenCuotasOrto(CuotasTratamientoOrto(plan, 700.0 + 4 * 125), "2026-10-25")
        val res = assertNotNull(r.resumen)
        assertEquals(17, r.cuotas.size)
        assertEquals("⚠ 1 atrasada (S/ 125.00)", textoAtrasoCuotas(res, "PEN"))
        assertEquals("5 de 16: S/ 125.00 — atrasada 10 días (15/10/2026)", textoProximaCuota(res, "PEN"))
        // Otra moneda: su símbolo, nunca soles.
        assertEquals("⚠ 1 atrasada (Bs 125.00)", textoAtrasoCuotas(res, "BOB"))
        // Dos atrasadas, en plural, con el saldo de cada una (la parcial cuenta lo que falta).
        val dos = resumenCuotas(estadoCuotas(plan, 700.0 + 60, "2026-07-20"))
        assertEquals("⚠ 2 atrasadas (S/ 190.00)", textoAtrasoCuotas(dos, "PEN"))
        assertEquals("1 de 16: S/ 65.00 — atrasada 35 días · parcial (15/06/2026)", textoProximaCuota(dos, "PEN"))
        // La inicial pendiente se nombra "Inicial".
        val ini = resumenCuotas(estadoCuotas(plan, 0.0, "2026-05-15"))
        assertEquals("Inicial: S/ 700.00 — vence hoy (15/05/2026)", textoProximaCuota(ini, "PEN"))
    }

    @Test fun sinCronogramaSoloLoPagado() {
        val r = resumenCuotasOrto(CuotasTratamientoOrto(emptyList(), 350.5), "2026-10-25")
        assertNull(r.resumen)
        assertTrue(r.cuotas.isEmpty())
        assertEquals(350.5, r.pagado)
    }

    // ── filas de la base (tolerante) ──

    private fun filas(json: String): List<JsonObject> = Json.parseToJsonElement(json).let { e ->
        (e as kotlinx.serialization.json.JsonArray).map { it.jsonObject }
    }

    @Test fun filasDeLaBase() {
        val d = cuotasOrtoDeFilas(
            filas("""[
                {"numero": 1, "monto": "125.00", "vence": "2026-06-15"},
                {"numero": 0, "monto": 700, "vence": "2026-05-15"},
                {"numero": 2, "monto": null, "vence": "2026-07-15"},
                {"numero": null, "monto": 10, "vence": "2026-08-15"},
                {"numero": 3, "monto": 10, "vence": "15/08/2026"}
            ]"""),
            filas("""[{"monto": "100.50"}, {"monto": 400}, {"monto": null}, {}]"""),
        )
        assertEquals(listOf(CuotaPlan(1, 125.0, "2026-06-15"), CuotaPlan(0, 700.0, "2026-05-15"), CuotaPlan(2, 0.0, "2026-07-15")), d.plan)
        assertEquals(500.5, d.pagado)
        // estadoCuotas ordena por número: la inicial primero.
        assertEquals(listOf(0, 1, 2), resumenCuotasOrto(d, "2026-05-01").cuotas.map { it.numero })
    }
}
