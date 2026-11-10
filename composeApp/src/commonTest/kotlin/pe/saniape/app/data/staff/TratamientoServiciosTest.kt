package pe.saniape.app.data.staff

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlin.test.Test
import kotlin.test.assertEquals

class TratamientoServiciosTest {
    private fun obj(s: String): JsonObject = Json.parseToJsonElement(s).jsonObject

    @Test
    fun conVariasLineasSeNombraConTodasEnOrden() {
        val t = obj("""{"procedimiento":{"nombre":"OCT"},"servicios":[
            {"orden":1,"procedimiento":{"nombre":"Campimetría"}},
            {"orden":0,"procedimiento":{"nombre":"OCT"}}]}""")
        assertEquals("OCT + Campimetría", TratamientoServicios.nombre(t, "OCT"))
    }

    @Test
    fun sinLineasOConUnaQuedaElDeSiempre() {
        assertEquals("Limpieza", TratamientoServicios.nombre(obj("""{"servicios":[]}"""), "Limpieza"))
        assertEquals("Limpieza", TratamientoServicios.nombre(obj("""{"servicios":[{"orden":0,"procedimiento":{"nombre":"Otra"}}]}"""), "Limpieza"))
        assertEquals("Limpieza", TratamientoServicios.nombre(obj("""{}"""), "Limpieza"))
        assertEquals(null, TratamientoServicios.nombre(null, null))
    }
}
