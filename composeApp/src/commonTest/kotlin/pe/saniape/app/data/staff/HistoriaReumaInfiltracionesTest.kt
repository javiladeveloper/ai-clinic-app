package pe.saniape.app.data.staff

import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull

/**
 * La historia clínica de la app lee las secciones del JSON de la web. QA FISIOSALUD (25/11/2026):
 * «las infiltraciones y el otro de articulaciones también deben aparecer en la historia clínica».
 * Antes la app las saltaba (tipo desconocido). Mismo JSON que arma lib/historia-construir.ts.
 */
class HistoriaReumaInfiltracionesTest {

    @Test fun `lee la seccion de infiltraciones`() {
        val s = parsearSeccionHc(Json.parseToJsonElement("""
            {"id":"infiltraciones","tipo":"infiltraciones","titulo":"Infiltraciones articulares","obligatoria":false,
             "datos":{"filas":[{"fecha":"2026-11-25","articulacion":"Rodilla derecha","sustancia":"Corticoide + Anestésico","dosis":"1 ml",
               "eco":true,"eva":6,"lote":"L-22","caducidad":"2027-03-01","profesional":"Dra. Ana","observaciones":null}],
               "mapa":{"rodilla_der":2,"tmc_izq":1},"total":3}}
        """))
        assertNotNull(s)
        val d = assertIs<DatosInfiltracionesHc>(s.datos)
        assertEquals(3, d.total)
        assertEquals("Corticoide + Anestésico", d.filas.single().sustancia)
        assertEquals(mapOf("rodilla_der" to 2, "tmc_izq" to 1), d.mapa)
    }

    @Test fun `lee la evaluacion articular (con y sin el mapa)`() {
        val s = parsearSeccionHc(Json.parseToJsonElement("""
            {"id":"reuma_evaluacion","tipo":"reuma_evaluacion","titulo":"Evaluación articular","obligatoria":false,
             "datos":{"fecha":"2026-11-20","profesional":"Dra. Ana","actividad":{"texto":"CDAI 12 · Actividad moderada","nivel":"moderada"},
               "dolorosas28":4,"inflamadas28":2,"mapa":{"rodilla_der":{"d":true,"i":true},"mcf2_izq":{"d":true}},
               "dolorosas":["Rodilla derecha","MCF 2 (nudillo) izquierda"],"inflamadas":["Rodilla derecha"],
               "cambios":["▼ CDAI: 17 → 12"],"fechaAnterior":"2026-10-20","datos":[{"etiqueta":"VSG","valor":"30 mm/h"}],
               "puntosDolorosos":[],"observaciones":"Mejor","total":2}}
        """))
        val d = assertIs<DatosReumaEvaluacionHc>(s!!.datos)
        assertEquals("moderada", d.actividad?.nivel)
        assertEquals(EstadoArticulacion(d = true, i = true), d.mapa["rodilla_der"]?.let { EstadoArticulacion(it.d, it.i) })
        val sinMapa = parsearSeccionHc(Json.parseToJsonElement("""
            {"tipo":"reuma_evaluacion","titulo":"Evaluación articular","datos":{"fecha":"2026-11-20","profesional":null,"actividad":null,
             "dolorosas28":0,"inflamadas28":0,"dolorosas":[],"inflamadas":[],"cambios":[],"fechaAnterior":null,"datos":[],
             "puntosDolorosos":[],"observaciones":null,"total":1}}
        """))
        assertIs<DatosReumaEvaluacionHc>(sinMapa!!.datos)
    }
}
