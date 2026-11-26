package pe.saniape.app.data.staff

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * 26/11/2026, Centro OMER: la doctora no podía agendar una consulta a las 15:15 porque ya
 * tenía una de 15:00 a 16:00 que entró del calendario. El servidor ahora responde con quién
 * choca y `forzable: true`; la app ofrece «Agendar igual a esta hora».
 */
class SolapeForzableTest {
    private fun cuerpo(s: String) = Json.parseToJsonElement(s).jsonObject

    @Test fun elRechazoPorOtraConsultaSePuedeForzar() {
        assertTrue(rechazoEsSolapeForzable(cuerpo(
            """{"error":"Maricely Niebla Hernández ya tiene una consulta con Olga Vargas Zapata de 15:00 a 16:00.","codigo":"capacidad_citas_agotada","forzable":true}""",
        )))
    }

    @Test fun elRechazoDeSiempreNoOfreceForzar() {
        // Sin horario o sin especialidad: mismo código, sin `forzable`.
        assertFalse(rechazoEsSolapeForzable(cuerpo("""{"error":"Ya no hay cupo en ese horario.","codigo":"capacidad_citas_agotada"}""")))
        assertFalse(rechazoEsSolapeForzable(cuerpo("""{"error":"x","forzable":false}""")))
        assertFalse(rechazoEsSolapeForzable(null))
    }
}
