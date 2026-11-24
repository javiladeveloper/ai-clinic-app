package pe.saniape.app.data.staff

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class EsteticaAvisosTest {
    @Test fun controlSugerido() {
        assertEquals(15, diasControlSugerido(listOf("toxina")))
        assertEquals(15, diasControlSugerido(listOf("bioestimulador", "acido_hialuronico")))
        assertEquals(30, diasControlSugerido(listOf("bioestimulador")))
        assertEquals(30, diasControlSugerido(listOf("regenerativo")))
        assertEquals(15, diasControlSugerido(emptyList()))
    }

    @Test fun mapaSuelto() {
        assertNull(avisoMapaSuelto(false, null, null, true, true))
        assertNull(avisoMapaSuelto(true, "c1", null, true, true))
        assertNull(avisoMapaSuelto(true, null, "t1", true, true))
        assertTrue(avisoMapaSuelto(true, null, "", true, true)!!.endsWith("Elige su tratamiento arriba."))
        assertTrue(avisoMapaSuelto(true, "", null, true, false)!!.endsWith("Elige su cita arriba."))
        assertTrue(avisoMapaSuelto(true, null, null, false, false)!!.contains("crea primero"))
    }

    @Test fun afirmativa() {
        assertTrue(respuestaAfirmativa("true"))
        assertTrue(respuestaAfirmativa("Sí"))
        assertTrue(respuestaAfirmativa("isotretinoína hace 2 meses"))
        for (no in listOf(null, "", "false", "No", "ninguna", "Niega", "-", "n/a", "N/A", "sin alergias")) assertFalse(respuestaAfirmativa(no), "$no")
    }

    @Test fun alertas() {
        val campos = listOf("f" to "Fototipo (Fitzpatrick)", "e" to "¿Embarazo o lactancia?", "r" to "Uso de retinoides", "s" to "Exposición solar reciente", "x" to "Densidad folicular")
        val a = alertasEstetica(campos, mapOf("f" to "Fototipo IV", "e" to "false", "r" to "true", "s" to "No", "x" to "80"))
        assertEquals(listOf("fototipo", "retinoides"), a.map { it.clave })
        assertEquals("Fototipo IV", a[0].texto)
        assertEquals(TonoAlerta.RIESGO, a[1].tono)
        assertTrue(alertasEstetica(campos, emptyMap()).isEmpty())
        assertTrue(alertasEstetica(emptyList(), mapOf("f" to "III")).isEmpty())
    }

    @Test fun pendienteDeAlta() {
        val completados = listOf(Triple("a", "Completado", "2026-10-01"), Triple("b", "Completado", "2026-11-01"))
        assertEquals("b", tratamientoPendienteDeAlta("En tratamiento", completados))
        assertNull(tratamientoPendienteDeAlta("Alta", completados))
        assertNull(tratamientoPendienteDeAlta("En tratamiento", completados + Triple("c", "Activo", null)))
        assertNull(tratamientoPendienteDeAlta("En tratamiento", completados + Triple("c", "Suspendido", null)))
        assertNull(tratamientoPendienteDeAlta("En tratamiento", listOf(Triple("c", "Cancelado", null))))
        assertNull(tratamientoPendienteDeAlta("En tratamiento", emptyList()))
    }

    @Test fun fotos() {
        val t = listOf(
            Triple("v1", "Completado", "2026-01-01"), Triple("v2", "Activo", "2026-02-01"),
            Triple("n1", "Completado", "2026-09-01"), Triple("n2", "Completado", "2026-10-01"),
            Triple("n3", "Completado", "2026-11-01"), Triple("x", "Eliminado", "2026-11-20"),
        )
        assertEquals(listOf("n3", "n2", "n1", "v2"), tratamientosParaFotos(t))
    }
}
