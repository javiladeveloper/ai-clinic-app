package pe.saniape.app.data.staff

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * El momento de una foto evolutiva: se leen «Después» (web) y «Despues» (las
 * viejas de la app) y se escribe el valor de la web (lib/tipos-imagen.ts).
 */
class MomentoFotoTest {

    @Test fun seEscribeElValorDeLaWeb() {
        assertEquals("Después", MOMENTO_DESPUES)
        assertEquals(listOf("Antes", "Durante", "Después"), MOMENTOS_EVOLUCION)
    }

    @Test fun seLeenLasDosVariantesEnCualquierCaja() {
        listOf("Después", "Despues", "después", "despues", "DESPUÉS", "DESPUES", " Despues ").forEach {
            assertEquals("Después", momentoCanonico(it), it)
            assertTrue(esMomento(it, MOMENTO_DESPUES), it)
        }
        assertEquals("Antes", momentoCanonico("antes"))
        assertEquals("Durante", momentoCanonico("DURANTE"))
        assertFalse(esMomento("Antes", MOMENTO_DESPUES))
    }

    @Test fun otrosTiposDeImagenQuedanTalCual() {
        assertEquals("Inicial", momentoCanonico(" Inicial "))
        assertEquals("Zona de tratamiento", momentoCanonico("Zona de tratamiento"))
        assertNull(momentoCanonico(""))
        assertNull(momentoCanonico(null))
    }

    @Test fun ordenAntesDuranteDespues() {
        // sortedBy es estable: los del mismo momento conservan su orden (el cronológico).
        assertEquals(listOf("antes", "Inicial", "Durante", "Despues", "Después"),
            listOf("Despues", "Inicial", "antes", "Después", "Durante").sortedBy { ordenMomento(it) })
        assertEquals(0, ordenMomento("ANTES"))
        assertEquals(2, ordenMomento("Despues"))
        assertEquals(2, ordenMomento("Después"))
        assertEquals(1, ordenMomento(null))
    }
}
