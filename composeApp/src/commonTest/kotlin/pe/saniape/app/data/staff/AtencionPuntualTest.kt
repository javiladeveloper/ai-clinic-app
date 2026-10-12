package pe.saniape.app.data.staff

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull

/** Atención puntual dental: el contrato con POST /api/staff/atencion-puntual (web). */
class AtencionPuntualTest {

    @Test
    fun cuerpoComoLoEsperaLaWeb() {
        val o = AtencionPuntualRepo.cuerpoRegistrar(
            citaId = "c1",
            items = listOf(ItemPuntual("46", "p1", 350.0), ItemPuntual(BOCA, "p2", 80.0)),
            terapeutaId = "t1", nota = "  pulpitis  ", cobrarConsulta = false, idempotencia = "k1",
        )
        assertEquals("c1", o["citaId"]!!.jsonPrimitive.content)
        assertEquals("t1", o["terapeutaId"]!!.jsonPrimitive.content)
        assertEquals("pulpitis", o["nota"]!!.jsonPrimitive.content)
        assertEquals("false", o["cobrarConsulta"]!!.jsonPrimitive.content)
        assertEquals("k1", o["idempotency_key"]!!.jsonPrimitive.content)
        val items = o["items"] as JsonArray
        assertEquals(2, items.size)
        assertEquals("46", items[0].jsonObject["diente"]!!.jsonPrimitive.content)
        assertEquals("350.0", items[0].jsonObject["precio"]!!.jsonPrimitive.content)
        assertEquals("BOCA", items[1].jsonObject["diente"]!!.jsonPrimitive.content)
    }

    @Test
    fun sinProfesionalNoLoManda() {
        val o = AtencionPuntualRepo.cuerpoRegistrar("c1", emptyList(), null, "", true, "k")
        assertFalse(o.containsKey("terapeutaId"))
    }

    @Test
    fun leeElResultado() {
        val o = Json.parseToJsonElement("""{"ok":true,"resumen":"Endodoncia (46)","total":350,"consulta":50,"tratamientos":[]}""").jsonObject
        val r = AtencionPuntualRepo.parsearResultado(o)
        assertEquals("Endodoncia (46)", r.resumen)
        assertEquals(400.0, r.porCobrar)
    }

    @Test
    fun elErrorConcretoGanaAlGeneral() {
        val (m, cod) = AtencionPuntualRepo.mensajeError("""{"error":"Operación parcial","causa":"Pon el precio de: Resina","codigo":"X"}""", 409)
        assertEquals("Pon el precio de: Resina", m)
        assertEquals("X", cod)
        val (m2, cod2) = AtencionPuntualRepo.mensajeError(null, 500)
        assertEquals("No se pudo completar (HTTP 500).", m2)
        assertNull(cod2)
    }
}
