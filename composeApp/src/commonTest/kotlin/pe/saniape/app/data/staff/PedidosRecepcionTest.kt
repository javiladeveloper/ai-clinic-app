package pe.saniape.app.data.staff

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Gemelo de lo puro de lib/pedidos-recepcion.ts (web 2026-10-16). */
class PedidosRecepcionTest {

    private fun item(clave: String, tipo: String, ref: String, cant: Int, precio: Double, lista: Double? = null) =
        ItemPedido(clave, tipo, ref, "X", cant, precio, lista, null)

    // Valores calculados con la función de la web (node) — la huella tiene que ser idéntica.
    @Test fun huellaIgualALaDeLaWeb() {
        assertEquals("2-e2848e08", huellaPedido(listOf(
            item("a1", "producto", "11111111-1111-4111-8111-111111111111", 2, 12.5),
            item("b2", "servicio", "22222222-2222-4222-8222-222222222222", 1, 80.005),
        )))
        assertEquals("0-1505", huellaPedido(emptyList()))
        assertEquals("1-8f7bce56", huellaPedido(listOf(item("ñ", "kit", "x", 3, 0.1))))
    }

    @Test fun totalesYResumen() {
        val items = listOf(
            item("a", "producto", "p", 2, 10.0), item("b", "kit", "k", 1, 25.5), item("c", "servicio", "s", 1, 80.0),
        )
        val t = totalesPedidoDe(items, "PEN")
        assertEquals(45.5, t.productos)
        assertEquals(80.0, t.servicios)
        assertEquals(125.5, t.total)
        assertEquals("3 productos · 1 servicio", resumenPedido(items))
        assertEquals("Sin ítems", resumenPedido(emptyList()))
        assertEquals("1 producto", resumenPedido(listOf(item("a", "producto", "p", 1, 1.0))))
    }

    @Test fun citaCobrableSoloConsultaConPrecioSinPagar() {
        fun p(cita: CitaPedido?) = PedidoRecepcion("p1", "pac", cita?.id, null, "PEN", null, emptyList(), 0.0, null, "Pendiente", null, null, cita)
        assertEquals(50.0, citaCobrableConPedido(p(CitaPedido("c", "2026-10-17", "Evaluación", "Completada", 50.0, null))))
        assertEquals(0.0, citaCobrableConPedido(p(CitaPedido("c", null, "Sesión", "Completada", 50.0, null))))
        assertEquals(0.0, citaCobrableConPedido(p(CitaPedido("c", null, "Consulta", "Cancelada", 50.0, null))))
        assertEquals(0.0, citaCobrableConPedido(p(CitaPedido("c", null, "Consulta", "Completada", 50.0, "2026-10-17T10:00:00Z"))))
        assertEquals(0.0, citaCobrableConPedido(p(CitaPedido("c", null, "Consulta", "Completada", 0.0, null))))
        assertEquals(0.0, citaCobrableConPedido(p(null)))
        assertTrue(puedeTocarPedido("Pendiente"))
        assertFalse(puedeTocarPedido("Cobrado"))
    }

    @Test fun leeLaApiYToleraFilasRotas() {
        val o = Json.parseToJsonElement(
            """{"id":"p1","paciente_id":"pac","cita_id":"c1","moneda":"pen","total":"120.5","estado":"Pendiente",
               "items":[{"clave":"a","tipo":"producto","ref_id":"r1","nombre":"Gel","cantidad":2,"precio_unit":10,"precio_lista":12},
                        {"clave":"b","tipo":"raro","ref_id":"r2"},{"tipo":"servicio","ref_id":"r3"}],
               "paciente":{"id":"pac","nombre":"Ana"},"terapeuta":{"id":"t","nombre":"Dr. X"},
               "cita":{"id":"c1","fecha":"2026-10-17","tipo":"Evaluación","estado":"Completada","costo":50,"pagada_at":null}}""",
        ) as JsonObject
        val p = pedidoDeJson(o)!!
        assertEquals("PEN", p.moneda)
        assertEquals(120.5, p.total)
        assertEquals(1, p.items.size)
        assertEquals(12.0, p.items[0].precioLista)
        assertEquals("Ana", p.pacienteNombre)
        assertEquals(50.0, p.cita?.costo)
        assertNull(p.cita?.pagadaAt)
        val pp = PedidosPendientes(listOf(p), 1, true)
        assertEquals(listOf("p1"), pp.porCita["c1"]?.map { it.id })
    }

    @Test fun cuerpoDeGuardar() {
        val lineas = listOf(
            LineaPedidoForm(null, "producto", "r1", "Gel", 2, "", 12.0, ""),
            LineaPedidoForm("k1", "servicio", "s1", "Limpieza", 1, "75,5", 80.0, " zona lumbar "),
        )
        val b = cuerpoGuardarPedido(null, "c1", null, lineas, "  ")
        assertEquals(JsonPrimitive("c1"), b["citaId"])
        assertEquals(JsonNull, b["notas"])
        val items = b["items"] as JsonArray
        assertEquals(JsonNull, (items[0] as JsonObject)["precio_unit"])   // vacío = el de lista
        assertEquals(JsonPrimitive("75.5"), (items[1] as JsonObject)["precio_unit"])
        assertEquals(JsonPrimitive("zona lumbar"), (items[1] as JsonObject)["notas"])
        assertEquals(JsonPrimitive("k1"), (items[1] as JsonObject)["clave"])
        // Editar: solo el id (sin cita ni paciente).
        val e = cuerpoGuardarPedido("p1", "c1", null, lineas, "ok")
        assertEquals(JsonPrimitive("p1"), e["id"])
        assertFalse("citaId" in e)
    }
}
