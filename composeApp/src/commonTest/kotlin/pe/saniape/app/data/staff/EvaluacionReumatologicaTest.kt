package pe.saniape.app.data.staff

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Gemelo de `lib/__tests__/evaluacion-reumatologica.test.ts` (web): mismos ids, conteos y fórmulas. */
class EvaluacionReumatologicaTest {

    private val mapa = mapOf(
        "rodilla_der" to EstadoArticulacion(d = true, i = true), "mcf2_der" to EstadoArticulacion(d = true),
        "mcf3_izq" to EstadoArticulacion(i = true), "tobillo_der" to EstadoArticulacion(d = true, i = true),
    )

    @Test fun `28 articulaciones en el conteo estandar y el lado izquierdo en espejo`() {
        assertEquals(28, ARTICULACIONES_REUMA.count { it.en28 })
        assertEquals(ARTICULACIONES_REUMA.size, ARTICULACIONES_REUMA.map { it.id }.toSet().size)
        val d = ARTICULACIONES_REUMA.first { it.id == "rodilla_der" }
        val i = ARTICULACIONES_REUMA.first { it.id == "rodilla_izq" }
        assertEquals(MapaArticularGeo.ANCHO, d.x + i.x)
        // 20 de las 28 son de las manos: se marcan en el cuadro ampliado.
        assertEquals(20, ARTICULACIONES_REUMA.count { it.en28 && it.zona == "mano" })
        assertEquals(10, ARTICULACIONES_REUMA.count { it.zona == "pie" })
    }

    @Test fun `tocar rota dolor, inflamacion, ambas, nada`() {
        val a = rotarEstadoArticulacion(null); assertEquals(EstadoArticulacion(d = true), a)
        val b = rotarEstadoArticulacion(a); assertEquals(EstadoArticulacion(i = true), b)
        val c = rotarEstadoArticulacion(b); assertEquals(EstadoArticulacion(d = true, i = true), c)
        assertNull(rotarEstadoArticulacion(c))
    }

    @Test fun `el toque sobre el cuerpo da la articulacion grande o la bolita de la mano`() {
        val visibles = ARTICULACIONES_REUMA.filter { it.en28 }
        assertEquals(ToqueMapaReuma.Articulacion("rodilla_der"), toqueEnMapaReuma(86f, 385f, visibles))
        assertEquals(ToqueMapaReuma.Zona("mano", "der"), toqueEnMapaReuma(22f, 294f, visibles))
        assertEquals(ToqueMapaReuma.Zona("mano", "izq"), toqueEnMapaReuma(218f, 290f, visibles))
        assertNull(toqueEnMapaReuma(120f, 200f, visibles))
        // Los pies solo en el mapa completo.
        assertNull(toqueEnMapaReuma(74f, 526f, visibles))
        assertEquals(ToqueMapaReuma.Zona("pie", "der"), toqueEnMapaReuma(74f, 526f, ARTICULACIONES_REUMA))
    }

    @Test fun `el toque dentro de la mano ampliada encuentra el nudillo, tambien en espejo`() {
        // Mano derecha: tal cual. MCF 2 está en (41, 42.5).
        assertEquals("mcf2_der", toqueEnZonaReuma("mano", "der", 41f, 43f, ARTICULACIONES_REUMA, PROPORCION_MANO_REUMA))
        // Mano izquierda: en espejo → x = 100 − 41 = 59.
        assertEquals("mcf2_izq", toqueEnZonaReuma("mano", "izq", 59f, 43f, ARTICULACIONES_REUMA, PROPORCION_MANO_REUMA))
        assertNull(toqueEnZonaReuma("mano", "der", 60f, 85f, ARTICULACIONES_REUMA, PROPORCION_MANO_REUMA))
        // En el mapa de 28 no hay IFD: la punta del dedo no marca nada.
        assertNull(toqueEnZonaReuma("mano", "der", 57f, 10f, ARTICULACIONES_REUMA.filter { it.en28 }, PROPORCION_MANO_REUMA))
    }

    @Test fun `los indices cuentan solo las 28`() {
        val c = conteosReuma(mapa)
        assertEquals(ConteosReuma(nad28 = 2, nat28 = 2, dolorosas = 3, inflamadas = 3), c)
        assertEquals(13.0, cdaiReuma(c, 5.0, 4.0))
        assertNull(cdaiReuma(c, 5.0, null))
        assertEquals(4.27 to "VSG", das28Reuma(c, 5.0, 30.0, null))
        assertEquals(3.71 to "PCR", das28Reuma(c, 5.0, null, 10.0))
        assertNull(das28Reuma(c, 5.0, null, null))
    }

    @Test fun `niveles de actividad`() {
        assertEquals(listOf(NivelActividadReuma.REMISION, NivelActividadReuma.BAJA, NivelActividadReuma.MODERADA, NivelActividadReuma.ALTA),
            listOf(nivelCdai(2.8), nivelCdai(10.0), nivelCdai(22.0), nivelCdai(22.1)))
        assertEquals(listOf(NivelActividadReuma.REMISION, NivelActividadReuma.BAJA, NivelActividadReuma.MODERADA, NivelActividadReuma.ALTA),
            listOf(nivelDas28(2.5), nivelDas28(3.2), nivelDas28(5.1), nivelDas28(5.2)))
    }

    @Test fun `vacia no se guarda y con solo el mapa si`() {
        assertEquals(1, validarEvaluacionReuma(BorradorEvaluacionReuma("p", "2026-11-22")).size)
        assertTrue(validarEvaluacionReuma(BorradorEvaluacionReuma("p", "2026-11-22", articulaciones = mapOf("codo_der" to EstadoArticulacion(d = true)))).isEmpty())
    }

    @Test fun `el cuerpo y la fila viajan con el contrato de la API`() {
        val cuerpo = cuerpoEvaluacionReuma(BorradorEvaluacionReuma("p", "2026-11-22", articulaciones = mapa, evgPaciente = 5.0, fuerza = mapOf("prension_der" to 4)))
        assertEquals("""{"d":true,"i":true}""", cuerpo["articulaciones"]!!.jsonObject["rodilla_der"].toString())
        assertEquals("p", cuerpo["pacienteId"].toString().trim('"'))
        val fila = Json.parseToJsonElement(
            """{"id":"1","paciente_id":"p","fecha":"2026-11-22","articulaciones":{"rodilla_der":{"d":true,"i":true},"inventada":{"d":true}},
               "puntos_dolorosos":["trapecio_der"],"fuerza":{"prension_der":4},"eva":7,"evg_paciente":6,"evg_medico":5,"rigidez_min":60,
               "vsg":38,"pcr":null,"nad28":1,"nat28":1,"cdai":13,"das28":4.9,"das28_tipo":"VSG","observaciones":null}""",
        ).jsonObject
        val ev = evaluacionReumaDeJson(fila)!!
        assertEquals(setOf("rodilla_der"), ev.articulaciones.keys)
        assertEquals(ActividadReuma("DAS28-VSG", 4.9, NivelActividadReuma.MODERADA), ev.actividad)
        val antes = ev.copy(id = "0", nad28 = 4, cdai = 20.0, das28 = null, das28Tipo = null)
        assertEquals(listOf("Articulaciones dolorosas", "CDAI"), compararEvaluacionesReuma(ev, antes).map { it.etiqueta })
    }
}
