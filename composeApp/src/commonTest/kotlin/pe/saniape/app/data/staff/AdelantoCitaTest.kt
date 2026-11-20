package pe.saniape.app.data.staff

import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Adelanto de una consulta/evaluación (gemelo de lib/cobro-adelanto-cita.ts, 20/11/2026). */
class AdelantoCitaTest {

    @Test
    fun adelanto_valido_es_mayor_que_cero_y_menor_que_lo_que_falta() {
        assertEquals(20.0, adelantoValido("20", 40.0, "PEN"))
        assertEquals(20.5, adelantoValido("20,50", 40.0, "PEN"))
        assertNull(adelantoValido("", 40.0, "PEN"))
        assertNull(adelantoValido("0", 40.0, "PEN"))
        assertNull(adelantoValido("40", 40.0, "PEN"))   // cubre todo: es un cobro completo
        assertNull(adelantoValido("50", 40.0, "PEN"))
        assertNull(adelantoValido("abc", 40.0, "PEN"))
    }

    @Test
    fun falta_es_total_menos_abonado_sin_negativos() {
        assertEquals(20.0, faltaDeCita(40.0, 20.0))
        assertEquals(40.0, faltaDeCita(40.0, 0.0))
        assertEquals(0.0, faltaDeCita(40.0, 50.0))
        assertEquals(0.1, faltaDeCita(0.3, 0.2))        // céntimos exactos
    }

    @Test
    fun cuerpo_del_adelanto() {
        val c = cuerpoCobrarCita("c1", "Yape", "cobrar", null, adelanto = 20.0, primerAdelanto = true)
        assertEquals("true", c["adelanto"]?.jsonPrimitive?.content)
        assertEquals("20.0", c["monto"]?.jsonPrimitive?.content)
        assertEquals("true", c["primerAdelanto"]?.jsonPrimitive?.content)
        assertEquals("Yape", c["metodo"]?.jsonPrimitive?.content)
        // «No cobrar» o un cobro dividido nunca llevan adelanto.
        assertFalse("adelanto" in cuerpoCobrarCita("c1", "Yape", "gratis", null, adelanto = 20.0))
        assertFalse("adelanto" in cuerpoCobrarCita("c1", "Yape", "cobrar", null, pagos = listOf(PartePago("Yape", 10.0), PartePago("Efectivo", 30.0)), adelanto = 20.0))
        assertTrue("primerAdelanto" !in cuerpoCobrarCita("c1", "Yape", "cobrar", null, adelanto = 20.0))
    }
}
