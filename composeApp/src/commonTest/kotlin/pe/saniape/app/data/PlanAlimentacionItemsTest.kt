package pe.saniape.app.data

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import pe.saniape.app.data.staff.BorradorPlan
import pe.saniape.app.data.staff.ComidaPlan
import pe.saniape.app.data.staff.ItemComida
import pe.saniape.app.data.staff.PlanAlimentacion
import pe.saniape.app.data.staff.PlantillaPlan
import pe.saniape.app.data.staff.avisoItemsIlegibles
import pe.saniape.app.data.staff.borradorDesdeEditor
import pe.saniape.app.data.staff.itemsIlegibles
import pe.saniape.app.data.staff.comidasParaMostrar
import pe.saniape.app.data.staff.contenidoDesde
import pe.saniape.app.data.staff.cuerpoPlan
import pe.saniape.app.data.staff.cuerpoPlantilla
import pe.saniape.app.data.staff.detalleDeItems
import pe.saniape.app.data.staff.detalleVisible
import pe.saniape.app.data.staff.htmlPlanAlimentario
import pe.saniape.app.data.staff.normalizarComidas
import pe.saniape.app.data.staff.normalizarItem
import pe.saniape.app.data.staff.planDeJson
import pe.saniape.app.data.staff.sugerenciasPlanDeJson
import pe.saniape.app.data.staff.textoPlanWhatsApp
import pe.saniape.app.data.staff.validarPlan

/** Comidas con alimentos: paridad con `lib/__tests__/plan-alimentacion-items.test.ts` (lo que hace el cliente). */
class PlanAlimentacionItemsTest {
    private val pac = "11111111-1111-4111-8111-111111111111"

    private val quinuaJson = """{ "origen": "catalogo", "alimentoId": "TPCA-A53", "nombre": "Quinua cocida", "gramos": 177.3,
        "medida": { "nombre": "Taza de loza al ras", "gramos": 354.5, "cantidad": 0.5 },
        "por100": { "kcal": 89, "proteinas": 2.8, "grasas": 1.3, "carbohidratos": 16.3, "fibra": null, "sodio": 24, "hierro": 1.6, "calcio": 27 } }"""
    private val pechugaJson = """{ "origen": "catalogo", "alimentoId": "TPCA-F82", "nombre": "Pollo, pechuga de, sin piel, sancochada sin sal",
        "gramos": 120, "medida": null,
        "por100": { "kcal": 150, "proteinas": 30.4, "grasas": 2.2, "carbohidratos": 0, "fibra": null, "sodio": 31, "hierro": 0.5, "calcio": 12 } }"""
    private val QUINUA: ItemComida = normalizarItem(Json.parseToJsonElement(quinuaJson))!!
    private val PECHUGA: ItemComida = normalizarItem(Json.parseToJsonElement(pechugaJson))!!

    private val LINEA_QUINUA = "Quinua cocida — ½ taza de loza al ras (177 g)"
    private val LINEA_PECHUGA = "Pollo, pechuga de, sin piel, sancochada sin sal — 120 g"

    @Test fun conAlimentosYSinTextoElDetalleEsAutomatico() {
        val roto = QUINUA.copy(gramos = 0.0, medida = null)
        val r = normalizarComidas(listOf(
            ComidaPlan("Almuerzo", "13:00", "", listOf(QUINUA, PECHUGA)),
            ComidaPlan("Cena", null, "Sopa"),
            ComidaPlan("Media tarde", null, "", listOf(roto)),
            ComidaPlan("Desayuno", null, "Avena con fruta", listOf(QUINUA)),
        ))
        assertEquals(listOf(
            ComidaPlan("Almuerzo", "13:00", "$LINEA_QUINUA\n$LINEA_PECHUGA", listOf(QUINUA, PECHUGA), detalleAuto = true),
            ComidaPlan("Cena", null, "Sopa"),
            // Con texto libre propio: se respeta, sin marca.
            ComidaPlan("Desayuno", null, "Avena con fruta", listOf(QUINUA)),
        ), r)
    }

    @Test fun elAutomaticoSeRegeneraYNoSeRepite() {
        val c = normalizarComidas(listOf(ComidaPlan("Almuerzo", null, "texto viejo", listOf(PECHUGA), detalleAuto = true))).single()
        assertEquals(LINEA_PECHUGA, c.detalle)
        assertTrue(c.detalleAuto)
        assertEquals("", detalleVisible(c))
        assertEquals("Sopa", detalleVisible(ComidaPlan("Cena", null, "Sopa")))
        assertEquals(LINEA_QUINUA, detalleDeItems(listOf(QUINUA)))
        // Sin alimentos ni detalle: se descarta (una comida que se vació a propósito).
        assertEquals(emptyList(), normalizarComidas(listOf(ComidaPlan("Almuerzo", null, "", emptyList()))))
    }

    @Test fun validarPlanBastaUnaComidaConAlimentos() {
        assertEquals(emptyList(), validarPlan(BorradorPlan(pac, comidas = listOf(ComidaPlan("Almuerzo", null, "", listOf(QUINUA))))))
        val e = validarPlan(BorradorPlan(pac, objetivo = "x",
            comidas = listOf(ComidaPlan("Almuerzo", null, "x", listOf(QUINUA.copy(gramos = 0.0, medida = null))))))
        assertEquals(1, e.size)
        assertTrue(e[0].contains("«Quinua cocida» en «Almuerzo»"))
    }

    @Test fun paraMostrarToleraSinFotoYNoRepiteElAutomatico() {
        val sinFoto = QUINUA.copy(por100 = null)
        val r = comidasParaMostrar(listOf(ComidaPlan("Almuerzo", "13:00", LINEA_QUINUA, listOf(sinFoto), detalleAuto = true)))
        assertEquals(listOf(ComidaPlan("Almuerzo", "13:00", "", listOf(sinFoto))), r)
        assertEquals(QUINUA.por100, comidasParaMostrar(listOf(ComidaPlan("Cena", null, "x", listOf(QUINUA))))[0].items[0].por100)
    }

    @Test fun whatsappCadaAlimentoAntesDelTextoLibre() {
        val plan = PlanAlimentacion(
            id = "p", pacienteId = pac, fecha = "2026-10-25",
            comidas = listOf(
                ComidaPlan("Almuerzo", null, "Ensalada libre", listOf(QUINUA)),
                ComidaPlan("Cena", null, LINEA_PECHUGA, listOf(PECHUGA), detalleAuto = true),
            ),
        )
        val t = textoPlanWhatsApp(plan, paciente = "Ana Torres")
        assertTrue("*Almuerzo*\n• $LINEA_QUINUA\nEnsalada libre" in t)
        assertTrue("*Cena*\n• $LINEA_PECHUGA" in t)
        assertEquals(2, t.split(LINEA_PECHUGA).size) // una sola vez
    }

    @Test fun cuerpoConMarcaYItemsExplicitos() {
        val b = BorradorPlan(pac, fecha = "2026-10-25", comidas = listOf(
            ComidaPlan("Almuerzo", null, "", listOf(QUINUA)),
            ComidaPlan("Cena", null, "Sopa"),
        ))
        val c = cuerpoPlan(b)
        assertEquals(1, (c["itemsV"] as JsonPrimitive).content.toInt())
        val comidas = c["comidas"]!!.jsonArray.map { it.jsonObject }
        // `items` va SIEMPRE (también []): con la marca el pedido es la verdad completa.
        assertEquals(1, (comidas[0]["items"] as JsonArray).size)
        assertEquals(0, (comidas[1]["items"] as JsonArray).size)
        assertEquals(QUINUA, normalizarItem(comidas[0]["items"]!!.jsonArray[0]))
        assertEquals(1, (cuerpoPlan(b, "id-1")["itemsV"] as JsonPrimitive).content.toInt())
    }

    @Test fun plantillaConMarcaYComidasLimpias() {
        val p = PlantillaPlan(id = "t", nombre = "Hipo", comidas = listOf(
            ComidaPlan("Almuerzo", null, "", listOf(QUINUA)), ComidaPlan("Media mañana", null, "  "),
        ))
        val c = cuerpoPlantilla("Hipocalórica 1500", p)
        assertEquals(1, (c["itemsV"] as JsonPrimitive).content.toInt())
        val comidas = c["comidas"]!!.jsonArray.map { it.jsonObject }
        assertEquals(1, comidas.size)
        assertEquals(LINEA_QUINUA, (comidas[0]["detalle"] as JsonPrimitive).content)
        assertEquals("true", (comidas[0]["detalleAuto"] as JsonPrimitive).content)
        assertEquals(1, (comidas[0]["items"] as JsonArray).size)
    }

    @Test fun filaDelApiConItemsYCacheVieja() {
        val o = Json.parseToJsonElement(
            """{ "id": "p1", "paciente_id": "$pac", "fecha": "2026-10-25", "comidas": [
                 { "tiempo": "Almuerzo", "hora": "13:00", "detalle": "$LINEA_QUINUA", "detalleAuto": true, "items": [ $quinuaJson ] },
                 { "tiempo": "Cena", "hora": null, "detalle": "Sopa" } ] }""",
        ).jsonObject
        val p = planDeJson(o)!!
        assertEquals(listOf(QUINUA), p.comidas[0].items)
        assertTrue(p.comidas[0].detalleAuto)
        assertEquals(emptyList(), p.comidas[1].items) // sin `items` (caché vieja / plan de antes) = sin alimentos
        assertEquals(2, normalizarComidas(p.comidas).size)
    }

    @Test fun elEditorNoMuestraElDetalleAutomaticoYConservaLosItems() {
        val p = PlanAlimentacion(id = "p", pacienteId = pac, fecha = "2026-10-25", comidas = listOf(
            ComidaPlan("Almuerzo", null, LINEA_QUINUA, listOf(QUINUA), detalleAuto = true),
            ComidaPlan("Cena", null, "Sopa", listOf(PECHUGA)),
        ))
        val ed = contenidoDesde(p)
        assertEquals(listOf("", "Sopa"), ed.comidas.map { it.detalle })
        assertEquals(listOf(listOf(QUINUA), listOf(PECHUGA)), ed.comidas.map { it.items })
        // Ida y vuelta: el pedido lleva los ítems y el detalle vacío (el servidor regenera el automático).
        val b = borradorDesdeEditor(pac, "2026-10-25", ed, null)
        val comidas = cuerpoPlan(b)["comidas"]!!.jsonArray.map { it.jsonObject }
        assertEquals("", (comidas[0]["detalle"] as JsonPrimitive).content)
        assertFalse("detalleAuto" in comidas[0])
        assertEquals(1, (comidas[0]["items"] as JsonArray).size)
    }

    @Test fun alimentosIlegiblesNoSeBorranEnSilencio() {
        // Un ítem guardado que esta app no puede validar (p. ej. sin foto de nutrientes).
        val raro = QUINUA.copy(por100 = null)
        val p = PlanAlimentacion(id = "p", pacienteId = pac, fecha = "2026-10-25", comidas = listOf(
            ComidaPlan("Almuerzo", null, "", listOf(QUINUA, raro)),
            ComidaPlan("Cena", null, "", listOf(raro)),
        ))
        assertEquals(2, itemsIlegibles(p.comidas))
        val ed = contenidoDesde(p)
        assertEquals(2, ed.itemsIlegibles)
        assertEquals(listOf(listOf(QUINUA)), ed.comidas.map { it.items }) // el editor no los muestra…
        assertTrue(avisoItemsIlegibles(ed.itemsIlegibles)!!.contains("2 alimentos")) // …y al editar, el guardado se bloquea
        assertNull(avisoItemsIlegibles(0))
        assertEquals(0, contenidoDesde(p.copy(comidas = listOf(ComidaPlan("Cena", null, "Sopa", listOf(PECHUGA))))).itemsIlegibles)
    }

    @Test fun portalConItemsSinNutrientes() {
        val d = parsearMiNutricion(
            """{ "planes": [ { "id": "p1", "fecha": "2026-10-25", "comidas": [
                 { "tiempo": "Almuerzo", "hora": null, "detalle": "$LINEA_QUINUA", "detalleAuto": true,
                   "items": [ { "nombre": "Quinua cocida", "gramos": 177.3, "medida": { "nombre": "Taza de loza al ras", "gramos": 354.5, "cantidad": 0.5 } } ] },
                 { "tiempo": "Cena", "hora": null, "detalle": "Sopa" },
                 { "tiempo": "Vacía", "hora": null, "detalle": "" } ] } ] }""",
        )!!
        val comidas = d.planes.single().plan.comidas
        assertEquals(listOf("Almuerzo", "Cena"), comidas.map { it.tiempo })
        assertEquals("", comidas[0].detalle) // el automático no se repite debajo de los alimentos
        assertEquals("Quinua cocida", comidas[0].items.single().nombre)
        assertNull(comidas[0].items.single().por100)
        assertEquals("Sopa", comidas[1].detalle)
        // La hoja / PDF del paciente lista los alimentos con su cantidad.
        val html = htmlPlanPortal(d.planes.single())
        assertTrue("<b>Quinua cocida</b> — ½ taza de loza al ras (177 g)" in html)
        assertFalse("kcal</th>" in html)
    }

    @Test fun hojaDelStaffConAlimentosYTextoLibre() {
        val p = PlanAlimentacion(id = "p", pacienteId = pac, fecha = "2026-10-25", comidas = listOf(
            ComidaPlan("Almuerzo", null, "Ensalada libre", listOf(QUINUA)),
        ))
        val html = htmlPlanAlimentario(p, "Clínica", null, null, null, "Ana", null, null)
        assertTrue("<li>• <b>Quinua cocida</b> — ½ taza de loza al ras (177 g)</li>" in html)
        assertTrue("<div class=\"pre\">Ensalada libre</div>" in html)
    }

    @Test fun sugerenciasConAlimentosFrecuentes() {
        val s = sugerenciasPlanDeJson(Json.parseToJsonElement(
            """{ "objetivo": [], "alimentos": [ { "origen": "catalogo", "alimentoId": "TPCA-A53", "nombre": "Quinua cocida", "usos": 3,
                 "gramos": 177.3, "medida": { "nombre": "Taza de loza al ras", "gramos": 354.5, "cantidad": 0.5 } } ] }""",
        ).jsonObject)
        assertEquals(1, s.alimentos.size)
        assertEquals(3, s.alimentos[0].usos)
        // Un servidor viejo no lo manda.
        assertEquals(emptyList(), sugerenciasPlanDeJson(JsonObject(emptyMap())).alimentos)
    }
}
