package pe.saniape.app.data.staff

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * 💵 «Mis pagos» (26/11/2026): el trabajador ve en la app sus pagos de planilla ya hechos.
 * El servidor manda el periodo y los conceptos ya en texto; la app no calcula nada.
 */
class MisPagosPlanillaTest {
    private fun cuerpo(s: String) = Json.parseToJsonElement(s).jsonObject

    @Test fun unPagoConSusConceptos() {
        val pagos = parsearMisPagos(cuerpo(
            """{"activa":true,"enLista":true,"pagos":[{"id":"p1","periodo":"Noviembre 2026","neto":1145.31,
            "pagado_at":"2026-11-30T22:00:00.000Z","metodo_pago":"Yape","lineas":[
            {"concepto":"Sueldo del periodo","monto":1500,"resta":false},{"concepto":"Faltas (1)","monto":50,"resta":true},
            {"concepto":"Adelantos","monto":300,"resta":true}]}]}""",
        ))
        assertEquals(1, pagos.size)
        val p = pagos[0]
        assertEquals("Noviembre 2026", p.periodo)
        assertEquals(1145.31, p.neto)
        assertEquals("30/11/2026", p.pagadoEl)
        assertEquals("Yape", p.metodo)
        assertEquals(
            listOf(LineaPago("Sueldo del periodo", 1500.0, false), LineaPago("Faltas (1)", 50.0, true), LineaPago("Adelantos", 300.0, true)),
            p.lineas,
        )
    }

    @Test fun sinPagosOSinElAgregadoListaVacia() {
        assertTrue(parsearMisPagos(cuerpo("""{"activa":false,"enLista":false,"pagos":[]}""")).isEmpty())
        assertTrue(parsearMisPagos(cuerpo("""{"error":"No encontrado"}""")).isEmpty())
        assertTrue(parsearMisPagos(null).isEmpty())
    }

    @Test fun unPagoIncompletoNoRompeLaLista() {
        val pagos = parsearMisPagos(cuerpo("""{"pagos":[{"id":"p1"},{"id":"p2","periodo":"Octubre 2026","neto":900}]}"""))
        assertEquals(listOf("p2"), pagos.map { it.id })
        assertEquals(null, pagos[0].pagadoEl)
    }
}
