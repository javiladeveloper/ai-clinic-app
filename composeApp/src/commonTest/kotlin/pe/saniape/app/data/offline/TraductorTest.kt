package pe.saniape.app.data.offline

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * La traducción de ids temporales es la parte más delicada de la cola offline:
 * si un "tmp-" se escapa al servidor o se pierde un mapeo, se crean huérfanos o
 * duplicados. Por eso se testea aparte, sin BD ni red.
 */
class TraductorTest {

    @Test
    fun detectaIdsTemporales() {
        assertTrue(esTemporal("tmp-abc"))
        assertFalse(esTemporal("9f3a-real"))
    }

    @Test
    fun idTemporalLlevaPrefijo() {
        assertTrue(nuevoIdTemporal().startsWith("tmp-"))
    }

    @Test
    fun dosIdsTemporalesSonDistintos() {
        assertTrue(nuevoIdTemporal() != nuevoIdTemporal())
    }

    @Test
    fun traduceUnIdTemporalDelPayload() {
        val payload = buildJsonObject { put("pacienteId", "tmp-a1"); put("nota", "hola") }
        val r = traducir(payload, mapOf("tmp-a1" to "R9"))
        assertEquals("R9", r!!["pacienteId"]!!.jsonPrimitive.content)
        assertEquals("hola", r["nota"]!!.jsonPrimitive.content)
    }

    @Test
    fun sinMapeoDevuelveNull() {
        val payload = buildJsonObject { put("pacienteId", "tmp-a1") }
        assertNull(traducir(payload, emptyMap()))
    }

    @Test
    fun payloadSinTemporalesQuedaIgual() {
        val payload: JsonObject = buildJsonObject { put("citaId", "R1"); put("n", 3) }
        assertEquals(payload, traducir(payload, emptyMap()))
    }

    @Test
    fun traduceTemporalesAnidados() {
        val payload = buildJsonObject {
            put("citaId", "R1")
            putJsonObject("extra") { put("pacienteId", "tmp-a1") }
        }
        val r = traducir(payload, mapOf("tmp-a1" to "R9"))
        assertEquals("R9", r!!["extra"]!!.jsonObject["pacienteId"]!!.jsonPrimitive.content)
    }
}
