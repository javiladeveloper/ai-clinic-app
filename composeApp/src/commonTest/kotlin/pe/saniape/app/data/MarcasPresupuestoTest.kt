package pe.saniape.app.data

import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import pe.saniape.app.data.staff.esCreacionParcial
import pe.saniape.app.data.staff.marcasTrasCambio
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class MarcasPresupuestoTest {
    @Test fun desmarcadaSigueDesmarcadaTrasDesaparecerOtraLinea() {
        // a, b, c vistas; b desmarcada; c se creó y desaparece.
        val (m, v) = marcasTrasCambio(setOf("a", "c"), setOf("a", "b", "c"), listOf("a", "b"))
        assertEquals(setOf("a"), m)
        assertEquals(setOf("a", "b"), v)
    }

    @Test fun lineaNuevaEntraMarcada() {
        val (m, v) = marcasTrasCambio(setOf("a"), setOf("a", "b"), listOf("a", "b", "n"))
        assertEquals(setOf("a", "n"), m)
        assertEquals(setOf("a", "b", "n"), v)
    }

    @Test fun parcialRequiere409ParcialConId() {
        val ok = buildJsonObject { put("parcial", true); put("id", "t1") }
        assertTrue(esCreacionParcial(409, ok))
        assertFalse(esCreacionParcial(500, ok))
        assertFalse(esCreacionParcial(409, buildJsonObject { put("parcial", true) }))
        assertFalse(esCreacionParcial(409, null))
    }
}
