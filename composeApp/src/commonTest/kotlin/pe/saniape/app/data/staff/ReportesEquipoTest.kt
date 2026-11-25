package pe.saniape.app.data.staff

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** Gemelo de lib/__tests__/citas-agendador.test.ts y cierres-reporte (web, 24/11/2026). */
class ReportesEquipoTest {
    @Test fun periodos() {
        assertEquals(Periodo("2026-11-01", "2026-11-24"), rangoDelMes("2026-11-24"))
        assertEquals(Periodo("2026-10-01", "2026-10-31"), rangoMesAnterior("2026-11-24"))
        assertEquals(Periodo("2025-12-01", "2025-12-31"), rangoMesAnterior("2026-01-15"))
    }

    @Test fun quienAgendoOrdenaYPoneLoSinPersonaAlFinal() {
        val json = Json.parseToJsonElement("""[
          {"agendada_por":null,"agendadas":9,"asistieron":5,"no_asistieron":1},
          {"agendada_por":"a","nombre":"Lucía","rol":"Teleoperador","agendadas":4,"asistieron":3,"no_asistieron":1,"canceladas":0,"por_venir":0,"pacientes_nuevos":2},
          {"agendada_por":"b","nombre":"","rol":"Recepción","agendadas":7,"asistieron":0,"no_asistieron":0}
        ]""")
        val f = filasAgendador(json)
        assertEquals(listOf("b", "a", null), f.map { it.id })
        assertEquals("Ya no está en el equipo", f[0].nombre)
        assertNull(f[0].asistencia)
        assertEquals(75, f[1].asistencia)
        assertEquals(SIN_PERSONA, f[2].nombre)
        val t = totalesAgendador(f)
        assertEquals(20, t.agendadas)
        assertEquals(80, t.asistencia) // 8 / (8 + 2)
    }

    @Test fun cierres() {
        val filas = Json.parseToJsonElement("""[
          {"id":"1","fecha":"2026-11-20","efectivo_esperado":"100","efectivo_contado":"100","total_ingresos":300,"total_egresos":20,"retirado":50},
          {"id":"2","fecha":"2026-11-22","efectivo_esperado":200,"efectivo_contado":195,"total_ingresos":"400.5"},
          {"id":"3","fecha":"2026-11-21","efectivo_esperado":80,"efectivo_contado":90,"diferencia":"10"}
        ]""").jsonArray.map { it.jsonObject as JsonObject }
        val f = filasDeCierres(filas)
        assertEquals(listOf("2", "3", "1"), f.map { it.id })
        assertEquals(ResultadoArqueo.FALTA, f[0].resultado)
        assertEquals(-5.0, f[0].diferencia)
        assertEquals(ResultadoArqueo.SOBRA, f[1].resultado)
        assertEquals(ResultadoArqueo.CUADRO, f[2].resultado)
        val r = resumenCierres(f)
        assertEquals(3, r.cierres); assertEquals(1, r.cuadraron); assertEquals(2, r.conDiferencia)
        assertEquals(700.5, r.ingresos); assertEquals(10.0, r.sobrante); assertEquals(5.0, r.faltante); assertEquals(50.0, r.retirado)
    }
}
