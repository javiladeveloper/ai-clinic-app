package pe.saniape.app.data.staff

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Parseo de `GET /api/staff/cita/estado-pago?ids=…` y armado de los lotes. */
class EstadoPagoCitaParseoTest {

    private val respuesta = """
        {
          "estados": {
            "c1": { "estado": "pagado", "deuda": null, "etiqueta": "✓ Pagado", "esPaquete": false },
            "c2": { "estado": "debe", "deuda": 80, "etiqueta": "Debe S/ 80", "esPaquete": false },
            "c3": { "estado": "parcial", "deuda": 120.5, "etiqueta": "Paquete: debe S/ 120.50", "esPaquete": true },
            "c4": { "estado": "gratis", "deuda": null, "etiqueta": "Sin costo", "esPaquete": false },
            "c5": { "estado": "sin_dato", "deuda": null, "etiqueta": "", "esPaquete": false, "extra": 1 }
          },
          "otro": true
        }
    """.trimIndent()

    @Test
    fun leeCadaEstado() {
        val m = parsearEstadosPago(respuesta)
        assertEquals(5, m.size)
        assertEquals("pagado", m["c1"]!!.estado)
        assertNull(m["c1"]!!.deuda)
        assertEquals(80.0, m["c2"]!!.deuda)
        assertEquals("Debe S/ 80", m["c2"]!!.etiqueta)
        assertTrue(m["c3"]!!.esPaquete)
        assertEquals(120.5, m["c3"]!!.deuda)
        assertEquals("gratis", m["c4"]!!.estado)
    }

    @Test
    fun sinDatoNoSeMuestra() {
        val m = parsearEstadosPago(respuesta)
        assertFalse(m["c5"]!!.mostrable)
        assertTrue(m["c1"]!!.mostrable)
        // Sin etiqueta tampoco hay nada que pintar.
        assertFalse(EstadoPagoCita(estado = "debe", deuda = 10.0, etiqueta = " ").mostrable)
    }

    @Test
    fun respuestaVaciaOSinEstados() {
        assertTrue(parsearEstadosPago("""{ "estados": {} }""").isEmpty())
        assertTrue(parsearEstadosPago("""{}""").isEmpty())
    }

    @Test
    fun lotesDeCienSinRepetidosNiVacios() {
        val ids = (1..250).map { "id$it" } + listOf("id1", "", " ")
        val lotes = lotesEstadoPago(ids)
        assertEquals(3, lotes.size)
        assertEquals(100, lotes[0].size)
        assertEquals(100, lotes[1].size)
        assertEquals(50, lotes[2].size)
        assertEquals(250, lotes.flatten().toSet().size)
        assertTrue(lotesEstadoPago(emptyList()).isEmpty())
    }
}
