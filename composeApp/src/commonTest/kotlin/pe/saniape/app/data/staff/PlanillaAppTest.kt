package pe.saniape.app.data.staff

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * 💵 Planilla en la app (26/11/2026): el Admin ve el periodo y paga. El servidor manda todo
 * calculado; la app solo suma lo que muestra.
 */
class PlanillaAppTest {
    private val cuerpo = Json.parseToJsonElement(
        """{"ok":true,"desde":"2026-11-01","hasta":"2026-11-30","periodo":"Noviembre 2026",
        "filas":[
          {"id":"p1","personal_id":"a","nombre":"Rosa Quispe","cargo":"Limpieza","sueldo_base":1500,"dias_trabajados":20,"faltas":1,"tardanza_min":45,
           "desc_faltas":50,"desc_tardanzas":4.69,"otros_descuentos":0,"bonos":0,"adelantos":300,"neto":1145.31,"estado":"Pendiente","metodo_pago":null,"nota":null},
          {"id":"p2","personal_id":"b","nombre":"Luis Ramos","cargo":null,"sueldo_base":1200,"dias_trabajados":1,"faltas":0,"tardanza_min":0,
           "desc_faltas":0,"desc_tardanzas":0,"otros_descuentos":0,"bonos":100,"adelantos":0,"neto":1300,"estado":"Pagado","metodo_pago":"Yape","nota":"bono"}],
        "personas":[{"id":"a","estado":"Activo","sueldo":1500},{"id":"b","estado":"Activo","sueldo":1200},
                    {"id":"c","estado":"Activo","sueldo":null},{"id":"d","estado":"Inactivo","sueldo":900}],
        "adelantos":[]}""",
    ).jsonObject

    @Test fun leeLaPlanillaYSusTotales() {
        val p = parsearPlanilla(cuerpo)
        assertEquals("Noviembre 2026", p.periodo)
        assertEquals(2, p.filas.size)
        assertEquals(2, p.conSueldo) // activos con sueldo: ni el que no tiene, ni el inactivo
        val rosa = p.filas[0]
        assertEquals(54.69, rosa.descuentos, 0.001)
        assertEquals(false, rosa.pagado)
        assertTrue(p.filas[1].pagado)
        assertEquals(listOf("p1"), p.pendientes.map { it.id })
        assertEquals(2445.31, p.totalNeto, 0.001)
        assertEquals(1300.0, p.totalPagado, 0.001)
        assertEquals(1145.31, p.totalPendiente, 0.001)
    }

    @Test fun resumenDeAsistenciaDeUnaFila() {
        val p = parsearPlanilla(cuerpo)
        assertEquals("20 días · 1 falta · 45 min tarde", asistenciaDeFila(p.filas[0]))
        assertEquals("1 día", asistenciaDeFila(p.filas[1]))
    }

    @Test fun mesAnteriorYSiguienteCruzanElAnio() {
        assertEquals("2026-10", mesCorrido("2026-11", -1))
        assertEquals("2027-01", mesCorrido("2026-12", 1))
        assertEquals("2025-12", mesCorrido("2026-01", -1))
    }

    @Test fun unaRespuestaVaciaNoRompe() {
        val p = parsearPlanilla(Json.parseToJsonElement("""{"error":"x"}""").jsonObject)
        assertTrue(p.filas.isEmpty())
        assertEquals(0, p.conSueldo)
    }
}
