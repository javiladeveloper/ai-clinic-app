package pe.saniape.app.data.staff

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Gemelo de `lib/__tests__/infiltraciones.test.ts` (web): mismas reglas, mismos textos. */
class InfiltracionesTest {

    private val base = BorradorInfiltracion(articulaciones = listOf("mcf2_der"), sustancia = "Corticoide")

    @Test fun `valida lo minimo - articulacion del mapa y sustancia`() {
        assertEquals(emptyList(), validarInfiltracion(base))
        assertTrue("Elige la articulación en el mapa." in validarInfiltracion(base.copy(articulaciones = listOf("codo_del_vecino"))))
        assertTrue("Elige en el mapa las articulaciones que infiltraste." in validarInfiltracion(base.copy(articulaciones = emptyList())))
        assertTrue("Elige la sustancia aplicada." in validarInfiltracion(base.copy(sustancia = "")))
        assertTrue("Escribe cuál sustancia se aplicó." in validarInfiltracion(base.copy(sustancia = "Otra")))
        assertEquals(emptyList(), validarInfiltracion(base.copy(sustancia = "Otra", producto = "Ozono")))
    }

    @Test fun `dosis en ml y EVA de 0 a 10`() {
        assertEquals(emptyList(), validarInfiltracion(base.copy(dosisMl = "1,5", eva = 7)))
        assertTrue(validarInfiltracion(base.copy(dosisMl = "0")).any { it.startsWith("La dosis va en ml") })
        assertTrue(validarInfiltracion(base.copy(dosisMl = "abc")).any { it.startsWith("La dosis va en ml") })
        assertTrue("El dolor (EVA) va de 0 a 10." in validarInfiltracion(base.copy(eva = 11)))
    }

    // FISIOSALUD (25/11/2026): «Corticoide + Anestésico (juntos), y opcionalmente Colágeno o Proloterapia»;
    // «1 punto en la base del pulgar»; «interfalángicas (en los dedos) o el tobillo / mediopié».
    @Test fun `sustancias y articulaciones que pidio FISIOSALUD`() {
        assertTrue(SUSTANCIAS_INFILTRACION.containsAll(listOf("Corticoide + Anestésico", "Colágeno", "Proloterapia")))
        assertEquals("Corticoide + Anestésico", SUSTANCIA_INICIAL_INFILTRACION)
        for (id in listOf("tmc_der", "tmc_izq", "mediopie_der", "mediopie_izq", "ifp3_izq", "ifd2_der", "tobillo_der")) {
            assertEquals(emptyList(), validarInfiltracion(base.copy(articulaciones = listOf(id))), id)
        }
    }

    @Test fun `varias articulaciones van en un solo envio, sin repetir`() {
        val o = cuerpoInfiltracion("p1", "c1", "2026-11-25", base.copy(articulaciones = listOf("rodilla_der", "rodilla_izq", "rodilla_der"), dosisMl = "2", eco = true))
        assertEquals(listOf("rodilla_der", "rodilla_izq"), o["articulaciones"]!!.jsonArray.map { it.jsonPrimitive.content })
        assertEquals("c1", o["citaId"]!!.jsonPrimitive.content)
        assertEquals("true", o["guiaEcografica"]!!.jsonPrimitive.content)
    }

    @Test fun `lee la fila del API y arma el resumen como la web`() {
        val o = Json.parseToJsonElement(
            """{"id":"i1","paciente_id":"p1","cita_id":null,"fecha":"2026-11-25","articulacion":"rodilla_der","sustancia":"Corticoide",
               "producto":"triamcinolona","dosis_ml":1.5,"guia_ecografica":true,"eva":6,"lote":"L1","caducidad":null,"observaciones":null}""",
        ).jsonObject
        val i = infiltracionDeJson(o)!!
        assertEquals("Rodilla derecha · Corticoide (triamcinolona) 1,5 ml · con guía ecográfica", resumenInfiltracion(i))
        assertEquals(6, i.eva)
    }

    @Test fun `anteriores de esa articulacion fuera de esta cita, y veces por articulacion`() {
        val l = listOf(
            Infiltracion("a", "rodilla_der", "Corticoide", fecha = "2026-10-01", citaId = "c0"),
            Infiltracion("b", "rodilla_der", "PRP", fecha = "2026-11-01", citaId = "c1"),
            Infiltracion("c", "rodilla_izq", "PRP", fecha = "2026-11-01", citaId = "c1"),
        )
        assertEquals(listOf("a"), anterioresInfiltracion(l, "rodilla_der", "c1").map { it.id })
        assertEquals(listOf("b", "a"), anterioresInfiltracion(l, "rodilla_der", null).map { it.id })
        assertEquals(mapOf("rodilla_der" to 2, "rodilla_izq" to 1), vecesPorArticulacion(l))
    }

    @Test fun `el servicio Infiltracion enciende el registro salvo especialidad apagada`() {
        assertTrue(esServicioInfiltracion("Infiltración articular"))
        assertFalse(esServicioInfiltracion("Consulta reumatológica"))
        val mapa = MapaInfiltraciones(apagadas = listOf("e-off"))
        assertTrue(servicioActivaInfiltracion(mapa, "Infiltración de rodilla", "e-on"))
        assertTrue(servicioActivaInfiltracion(mapa, "Infiltración de rodilla", null))
        assertFalse(servicioActivaInfiltracion(mapa, "Infiltración de rodilla", "e-off"))
    }

    @Test fun `acceso a la pestana - editar, leer o nada (accesoModuloEspecialidad)`() {
        val mixta = MapaInfiltraciones(ids = listOf("reuma"))
        assertEquals(AccesoModulo.EDITAR, accesoInfiltraciones(MapaInfiltraciones(ids = listOf("reuma"), solo = true)))
        assertEquals(AccesoModulo.EDITAR, accesoInfiltraciones(mixta, atenciones = listOf(AtencionFisio(especialidadId = "reuma"))))
        assertEquals(AccesoModulo.EDITAR, accesoInfiltraciones(mixta, especialidadesDeQuienMira = listOf("reuma")))
        assertEquals(AccesoModulo.EDITAR, accesoInfiltraciones(MapaInfiltraciones(), activoPorOtraVia = true))
        assertEquals(AccesoModulo.LEER, accesoInfiltraciones(mixta, tieneRegistros = true))
        assertEquals(null, accesoInfiltraciones(mixta))
    }
}
