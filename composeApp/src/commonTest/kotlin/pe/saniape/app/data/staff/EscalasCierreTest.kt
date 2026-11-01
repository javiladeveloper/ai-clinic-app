package pe.saniape.app.data.staff

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

/** Escalas en el cierre: la fila de escalas_clinica y lo que se ve al marcar (gemelo de la web). */
class EscalasCierreTest {

    private fun fila(json: String) = Json.parseToJsonElement(json).jsonObject

    private val eva = escalaDeFila(fila("""
        {"id":"a1","definicion":{"nombre":"Escala analógica del dolor (EVA)","corto":"EVA",
         "opciones":[],"items":[{"texto":"Dolor","opciones":[
           {"valor":0,"texto":"0"},{"valor":5,"texto":"5"},{"valor":7,"texto":"7"},{"valor":10,"texto":"10"}]}],
         "rangos":[{"min":0,"max":0,"etiqueta":"Sin dolor"},{"min":1,"max":6,"etiqueta":"Moderado"},{"min":7,"max":10,"etiqueta":"Severo"}]}}
    """))!!

    private val fitz = escalaDeFila(fila("""
        {"id":"b2","definicion":{"nombre":"Fototipo de Fitzpatrick","corto":"Fitzpatrick",
         "opciones":[{"valor":1,"texto":"I · Muy clara: siempre se quema"},{"valor":2,"texto":"II · Clara: se quema fácil"}],
         "items":[{"texto":"Fototipo"}],
         "rangos":[{"min":1,"max":1,"etiqueta":"Tipo I"},{"min":2,"max":2,"etiqueta":"Tipo II"}]}}
    """))!!

    @Test
    fun leeLaFilaConOpcionesPropiasOCompartidas() {
        assertEquals("propia:a1", eva.instrumento)
        assertEquals(4, eva.items[0].opciones.size)
        // Sin opciones propias, el ítem usa las compartidas de la escala.
        assertEquals(2, fitz.items[0].opciones.size)
    }

    @Test
    fun puntajeLargoMuestraNumeroYEtiqueta() {
        assertEquals("7 · Severo", resultadoEscala(eva, listOf(7)))
    }

    @Test
    fun clasificacionCortaMuestraSoloElTipo() {
        assertEquals("Tipo II", resultadoEscala(fitz, listOf(2)))
    }

    @Test
    fun sinResponderNoHayResultado() {
        assertNull(resultadoEscala(eva, listOf(null)))
    }

    @Test
    fun filaSinItemsSeDescarta() {
        assertNull(escalaDeFila(fila("""{"id":"x","definicion":{"corto":"X","items":[]}}""")))
        assertNotNull(escalaDeFila(fila("""{"id":"y","definicion":{"corto":"Y","items":[{"texto":"a","opciones":[{"valor":0,"texto":"No"},{"valor":1,"texto":"Sí"}]}]}}""")))
    }

    @Test
    fun opcionCortaHastaLosDosPuntos() {
        assertEquals("I · Muy clara", textoCortoOpcion("I · Muy clara: siempre se quema"))
    }
}
