package pe.saniape.app.data.staff

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * «📅 Agendar próxima» y «Agendar las N restantes»: la app NO calcula fechas
 * (la regla única vive en la web, lib/proxima-cita.ts); acá se prueba que lee
 * bien lo que devuelve el servidor y que manda lo mismo que la web.
 */
class ProximaCitaTest {
    private fun json(s: String): JsonObject = Json.parseToJsonElement(s).jsonObject

    private val respuesta = """
        {"propuesta":{"fecha":"2026-11-04","hora":"10:00","terapeutaId":"t1","terapeutaNombre":"Lic. Ana",
          "tipo":"Sesión","origen":"intervalo","dias":4,"porQue":"cada 4 días, como viene","horaCambiada":false,
          "corridaPor":null,"etiqueta":"mié 4 nov · 10:00 a. m. con Lic. Ana","verificada":true,
          "cita":{"pacienteId":"p1","tipo":"Sesión","fecha":"2026-11-04","hora":"10:00","terapeutaId":"t1",
            "tratamientoId":"tr1","especialidadId":null,"sedeId":null,"costo":0,"duracion":60,"citaOrigenId":"c0"},
          "tratamiento":{"id":"tr1","nombre":"Fisio","totalSesiones":10,"completadas":4,"vivas":6,"costoSesion":null,"procedimientoId":null},
          "paciente":{"id":"p1","nombre":"MARTA"}},
         "puedeAgendar":true}
    """.trimIndent()

    @Test
    fun leeLaPropuestaDelServidor() {
        val r = parsearRespuestaProxima(json(respuesta))
        assertNotNull(r)
        assertTrue(r.puedeAgendar)
        val p = assertNotNull(r.propuesta)
        assertEquals("2026-11-04", p.fecha)
        assertEquals("10:00", p.hora)
        assertEquals("mié 4 nov · 10:00 a. m. con Lic. Ana", p.etiqueta)
        assertEquals(4, p.dias)
        assertEquals("tr1", p.cita.tratamientoId)
        assertNull(p.cita.especialidadId)
        assertEquals(60, p.cita.duracion)
        assertEquals("c0", p.cita.citaOrigenId)
        assertEquals("MARTA", p.pacienteNombre)
        assertEquals(4, p.faltanPlan) // 10 − 6 vivas
    }

    @Test
    fun sinPropuestaTraeElMotivo() {
        val r = parsearRespuestaProxima(json("""{"propuesta":null,"motivo":"ya_tiene_proxima","puedeAgendar":false}"""))
        assertNotNull(r)
        assertNull(r.propuesta)
        assertFalse(r.puedeAgendar)
        assertEquals("ya_tiene_proxima", r.motivo)
        assertNull(parsearRespuestaProxima(null))
    }

    @Test
    fun laSegundaLineaEsLaMismaQueLaWeb() {
        val p = parsearRespuestaProxima(json(respuesta))!!.propuesta!!
        assertEquals("Sugerido cada 4 días, como viene", detallePropuesta(p))
        assertEquals("A la hora de siempre ya no hay espacio: es la primera libre", detallePropuesta(p.copy(horaCambiada = true)))
        assertEquals("Ana está de vacaciones hasta el lun 27: se corrió la fecha",
            detallePropuesta(p.copy(corridaPor = "Ana está de vacaciones hasta el lun 27")))
    }

    @Test
    fun mandaACrearLaCitaTalCual() {
        val c = parsearRespuestaProxima(json(respuesta))!!.propuesta!!.cita
        val cuerpo = cuerpoCitaPropuesta(c)
        assertEquals(JsonPrimitive("p1"), cuerpo["pacienteId"])
        assertEquals(JsonPrimitive("Sesión"), cuerpo["tipo"])
        assertEquals(JsonPrimitive("tr1"), cuerpo["tratamientoId"])
        assertEquals(JsonPrimitive("c0"), cuerpo["citaOrigenId"])
        assertFalse(cuerpo.containsKey("especialidadId")) // null no viaja
    }

    @Test
    fun diaDeLaSemanaComoLaWeb() {
        assertEquals(1, diaSemanaPlan("2026-10-26")) // lunes
        assertEquals(0, diaSemanaPlan("2026-11-01")) // domingo
        assertEquals(6, diaSemanaPlan("2026-10-31")) // sábado
        assertEquals(listOf("L", "M", "X", "J", "V", "S", "D"), CHIPS_DIAS_PLAN.map { it.second })
    }

    @Test
    fun leeElPlanConSusConflictos() {
        val filas = parsearFilasPlan(json("""
            {"filas":[{"fecha":"2026-12-03","hora":"10:00:00","conflictos":[],"incluida":true},
                      {"fecha":"2026-12-08","hora":"10:00","conflictos":[{"codigo":"feriado","mensaje":"Ese día es feriado (Inmaculada Concepción)"}],"incluida":false}],
             "faltan":3,"cantidad":3}
        """.trimIndent()))
        assertEquals(2, filas.size)
        assertEquals("10:00", filas[0].hora)
        assertTrue(filas[0].incluida)
        assertFalse(filas[1].incluida)
        assertEquals("feriado", filas[1].conflictos.single().codigo)
    }

    @Test
    fun resultadoDelPlanListaLasQueFaltaron() {
        val r = parsearResultadoPlan(json("""
            {"ok":true,"creadas":2,"fallidas":[{"fecha":"2026-11-04","hora":"10:00","error":"Ya no hay cupo en ese horario."}]}
        """.trimIndent()))
        assertEquals(2, r.creadas)
        assertEquals(1, r.fallidas.size)
        assertEquals("mié 04/11 10:00 — Ya no hay cupo en ese horario.", textoFallidas(r.fallidas))
        // Todas fallaron (409): el detalle viaja en el rechazo.
        val todas = parsearResultadoPlan(json("""{"creadas":0,"fallidas":[],"error":"x"}"""), "No se agendó ninguna sesión")
        assertEquals(0, todas.creadas)
        assertEquals("No se agendó ninguna sesión", todas.error)
    }
}
