package pe.saniape.app.data

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put
import pe.saniape.app.data.staff.Alimento
import pe.saniape.app.data.staff.BorradorAlimentoClinica
import pe.saniape.app.data.staff.FilaAlimentoClinica
import pe.saniape.app.data.staff.LimitesAlimento
import pe.saniape.app.data.staff.MedidaCasera
import pe.saniape.app.data.staff.MedidaItem
import pe.saniape.app.data.staff.Nutrientes100
import pe.saniape.app.data.staff.OrigenAlimento
import pe.saniape.app.data.staff.alimentoDeCatalogo
import pe.saniape.app.data.staff.alimentoDeClinica
import pe.saniape.app.data.staff.alimentosFrecuentesDeJson
import pe.saniape.app.data.staff.buscarAlimentos
import pe.saniape.app.data.staff.cambiarUnidad
import pe.saniape.app.data.staff.catalogoDeJson
import pe.saniape.app.data.staff.comparadorNombresEs
import pe.saniape.app.data.staff.compararNombresEsAprox
import pe.saniape.app.data.staff.conCantidad
import pe.saniape.app.data.staff.cuerpoAlimentoClinica
import pe.saniape.app.data.staff.erroresItems
import pe.saniape.app.data.staff.filaClinicaDeJson
import pe.saniape.app.data.staff.fraccion
import pe.saniape.app.data.staff.indexarAlimentos
import pe.saniape.app.data.staff.itemAJson
import pe.saniape.app.data.staff.itemConHabitual
import pe.saniape.app.data.staff.itemDesdeAlimento
import pe.saniape.app.data.staff.lineaItem
import pe.saniape.app.data.staff.medidasDe
import pe.saniape.app.data.staff.normalizarBusqueda
import pe.saniape.app.data.staff.normalizarItem
import pe.saniape.app.data.staff.normalizarItems
import pe.saniape.app.data.staff.normalizarItemsParaMostrar
import pe.saniape.app.data.staff.normalizarMedidas
import pe.saniape.app.data.staff.parsearCantidad
import pe.saniape.app.data.staff.refAlimento
import pe.saniape.app.data.staff.textoCantidad
import pe.saniape.app.data.staff.textoGramos
import pe.saniape.app.data.staff.validarAlimentoClinica

/**
 * Base de alimentos: paridad con `lib/__tests__/alimentos.test.ts` y la parte
 * de búsqueda de `alimentos-semilla.test.ts` (mismas entradas, mismas salidas).
 */
class AlimentosTest {

    /** Una fila del catálogo como la manda GET /api/staff/alimentos (los valores, como en la web). */
    private fun fila(id: String, nombre: String, vararg extra: Pair<String, Any?>): JsonObject {
        val base = linkedMapOf<String, Any?>(
            "id" to id, "nombre" to nombre, "grupo" to "x", "energia_kcal" to 100, "proteinas_g" to 1, "grasas_g" to 1,
            "carbohidratos_g" to 1, "fibra_g" to null, "sodio_mg" to null, "hierro_mg" to null, "calcio_mg" to null,
            "medidas" to emptyList<Any>(),
        )
        extra.forEach { (k, v) -> base[k] = v }
        return jsonDe(base) as JsonObject
    }

    private val catalogo = listOf(
        fila("TPCA-C55", "Palta", "sinonimos" to listOf("aguacate"), "busqueda" to "palta aguacate", "frecuente" to true, "energia_kcal" to 130),
        fila("TPCA-C161", "Palta Hass", "sinonimos" to listOf("aguacate"), "busqueda" to "palta hass aguacate", "frecuente" to true, "energia_kcal" to 204),
        fila("TPCA-C160", "Palta dedo o cocktail", "sinonimos" to listOf("aguacate"), "busqueda" to "palta dedo o cocktail aguacate", "energia_kcal" to 166),
        fila("TPCA-F82", "Pollo, pechuga de, sin piel, sancochada sin sal", "busqueda" to "pollo pechuga de sin piel sancochada sin sal", "frecuente" to true),
        fila("TPCA-F26", "Pollo, carne pulpa", "busqueda" to "pollo carne pulpa", "frecuente" to true),
        fila("TPCA-U18", "Papa amarilla sin cáscara", "busqueda" to "papa amarilla sin cascara", "frecuente" to true),
        fila("TPCA-T56", "Pallar seco", "sinonimos" to listOf("frijol lima"), "busqueda" to "pallar seco frijol lima", "frecuente" to true),
        fila("TPCA-B3", "Ají amarillo fresco", "busqueda" to "aji amarillo fresco", "frecuente" to true),
        fila("TPCA-U62", "Papa variedad nativa capulí con cáscara", "busqueda" to "papa variedad nativa capuli con cascara"),
    ).map { alimentoDeCatalogo(it)!! }

    private val propio = FilaAlimentoClinica(
        id = "4f6c2d1e-0000-4000-8000-000000000001", nombre = "Yogur griego natural (marca)", grupo = "Leche y derivados",
        kcal = 97.0, proteinas = 9.0, grasas = 5.0, carbohidratos = 4.0, fibra = null, sodio = null, hierro = null, calcio = 110.0,
        medidas = listOf(MedidaCasera("Pote", 150.0)), fuente = "Etiqueta del producto",
    )

    private val indice = indexarAlimentos(listOf(alimentoDeClinica(propio)) + catalogo)
    private fun nombres(q: String, limite: Int = 12, usos: Map<String, Int> = emptyMap()) =
        buscarAlimentos(indice, q, limite, usos).map { it.nombre }

    // ── Búsqueda ──

    @Test fun normalizaSinTildes() {
        assertEquals("aji amarillo nuna tostada", normalizarBusqueda("  Ají Amarillo, ÑUÑA  (tostada)* "))
        assertEquals("", normalizarBusqueda(null))
    }

    @Test fun encuentraPorSinonimoSinTildesYComienzos() {
        assertEquals(listOf("Palta", "Palta Hass", "Palta dedo o cocktail"), nombres("aguacate"))
        assertEquals(listOf("Ají amarillo fresco"), nombres("aji"))
        assertEquals("Pollo, pechuga de, sin piel, sancochada sin sal", nombres("pech poll").first())
        assertEquals(listOf("Papa variedad nativa capulí con cáscara"), nombres("capuli"))
        assertEquals(emptyList(), nombres("xyz"))
        assertEquals(emptyList(), nombres("   "))
    }

    @Test fun ordenEmpiezaIgualFrecuentesYCorto() {
        val r = nombres("pa")
        assertEquals("Papa variedad nativa capulí con cáscara", r.last())
        assertEquals("Palta", r.first())
    }

    @Test fun usosDeLaClinicaYPropios() {
        val usos = mapOf(refAlimento(OrigenAlimento.CATALOGO, "TPCA-C160") to 10)
        assertEquals("Palta dedo o cocktail", nombres("palta", usos = usos).first())
        assertEquals(listOf("Yogur griego natural (marca)"), nombres("yogur"))
        assertEquals(2, nombres("pa", limite = 2).size)
    }

    // ── Sobre el catálogo REAL (las filas de la semilla que calzan con estas búsquedas) ──

    private val indiceReal by lazy { indexarAlimentos(SEMILLA_BUSQUEDA) }
    private fun real(q: String, n: Int = 8) = buscarAlimentos(indiceReal, q, n).map { it.nombre }

    @Test fun primeroElGenerico() {
        assertEquals("Papa blanca", real("papa").first())
        assertEquals("Pollo, carne pulpa", real("pollo").first())
        assertEquals("Huevo de gallina entero, sancochado en agua", real("huevo").first())
        assertEquals("Arroz pilado o pulido cocido", real("arroz").first())
        assertEquals("Huevo de gallina entero, sancochado en agua", real("huevo sanc").first())
        // La palabra completa gana aunque el nombre sea más largo.
        assertFalse("Papaya" in real("papa", 5))
    }

    @Test fun loEspecificoEscritoCompletoVaPrimero() {
        assertEquals("Papaya", real("papaya").first())
        assertEquals("Pollo, hígado de", real("hígado de pollo").first())
        assertEquals("Pollo, hígado de", real("higado pollo").first())
        assertEquals("Huevo de gallina, clara de", real("clara de huevo").first())
    }

    @Test fun ordenCompletoIgualALaWeb() {
        // Salida de buscarAlimentos (web) sobre la semilla 20261025210000, límite 8.
        assertEquals(listOf(
            "Papa blanca", "Papa seca", "Papa chuño negro", "Papa huayro con cáscara", "Papa morada sin cáscara",
            "Papa moraya chuño pelado", "Papa amarilla sin cáscara", "Papa \"cóctel\", con cáscara, cruda",
        ), real("papa"))
        assertEquals(listOf(
            "Pollo, carne pulpa", "Pollo, hígado de", "Pollo, molleja, cruda", "Pollo, sangre de, sancochada sin sal",
            "Pollo, pecho y ala de, con piel a la brasa", "Pollo, nuggets de pechuga de, crudo, envasado",
            "Pollo, pierna de, con piel, sancochada sin sal", "Pollo, pechuga de, sin piel, sancochada sin sal",
        ), real("pollo"))
        assertEquals(listOf(
            "Huevo de gallina entero, sancochado en agua", "Huevo de gallina, yema de", "Huevo de gallina, clara de",
            "Huevo de gallina entero, crudo", "Huevo de codorniz, entero crudo", "Huevo de gallina, clara sancochada en agua",
            "Huevo de codorniz, entero sancochado en agua", "Huevo de gallina, entero, frito en aceite vegetal, sin sal",
        ), real("huevo"))
        assertEquals(listOf(
            "Arroz pilado o pulido cocido", "Arroz blanco corriente", "Arroz con cáscara", "Barra de cereales (avena, arroz) con aguaymanto",
        ), real("arroz"))
        assertEquals(listOf(
            "Huevo de gallina entero, sancochado en agua", "Huevo de gallina, clara sancochada en agua",
            "Huevo de codorniz, entero sancochado en agua", "Huevo de gallina, yema sancochada en agua",
            "Huevo de codorniz, yema sancochada en agua", "Huevo de codorniz, clara sancochada en agua",
        ), real("huevo sanc"))
        assertEquals(listOf("Papaya", "Papaya arequipeña", "Papaya, néctar natural, sin azúcar"), real("papaya"))
        assertEquals(listOf("Pollo, hígado de", "Pate de hígado de pollo, envasado", "Paté de hígado de pollo, pavo y carne de cerdo"),
            real("hígado de pollo"))
        assertEquals(listOf("Huevo de gallina, clara de", "Huevo de gallina, clara sancochada en agua", "Huevo de codorniz, clara sancochada en agua"),
            real("clara de huevo"))
        assertEquals(listOf(
            "Leche fresca de vaca", "Leche en polvo entera", "Leche evaporada entera", "Leche en polvo descremada",
            "Leche condensada endulzada", "Leche evaporada descremada", "Leche concentrada, sin lactosa", "Leche fresca c/menos de 1% grasa",
        ), real("leche"))
        assertEquals(listOf(
            "Quinua cocida", "Quinua", "Quinua, harina de", "Quinua, hojuela de", "Quinua, hojas de", "Quinua, sémola de",
            "Quinua, afrecho de", "Quinua blanca (Puno)",
        ), real("quinua"))
        assertEquals(listOf("Palta", "Palta Hass", "Palta \"fuerte\"", "Palta dedo o cocktail"), real("palta"))
        assertEquals(listOf(
            "Pollo, pecho y ala de, con piel a la brasa", "Pollo, nuggets de pechuga de, crudo, envasado",
            "Pollo, pechuga de, sin piel, sancochada sin sal", "Pollo, pechuga de, sin piel, frita en aceite vegetal, sin sal",
        ), real("pech poll"))
    }

    @Test fun empatesComoLocaleCompareEs() {
        // Mismo puntaje (largo normalizado y marcas iguales): decide el orden alfabético español.
        val empates = indexarAlimentos(listOf("Ñame", "Nuez", "Té", "Te", "Ajo", "Ají").map { F("X-$it", it, normalizarBusqueda(it), false, false) })
        assertEquals(listOf("Nuez", "Ñame"), buscarAlimentos(empates, "n").map { it.nombre }) // la ñ va después de la n
        assertEquals(listOf("Te", "Té"), buscarAlimentos(empates, "te").map { it.nombre })   // sin tilde primero
        assertEquals(listOf("Ají", "Ajo"), buscarAlimentos(empates, "aj").map { it.nombre }) // la tilde no cambia la letra
        // La versión sin plataforma (iOS) ordena igual que el Collator.
        val pares = listOf("Nuez" to "Ñame", "Te" to "Té", "Ají" to "Ajo", "papa" to "Papa", "Pan 2" to "Pan b")
        val col = comparadorNombresEs()
        for ((x, y) in pares) assertEquals(col.compare(x, y) < 0, compararNombresEsAprox(x, y) < 0, "$x vs $y")
        // Signos: como ICU (V8 y Android), el espacio va antes que la coma (el Collator del JVM de escritorio los ignora).
        assertTrue(compararNombresEsAprox("Pan blanco", "Pan, blanco") < 0)
    }

    // ── Filas → Alimento ──

    @Test fun catalogoNumerosNullMedidasYFuente() {
        val a = alimentoDeCatalogo(fila(
            "TPCA-A2", "Arroz pilado o pulido cocido", "energia_kcal" to "115.0", "proteinas_g" to "2.40", "carbohidratos_g" to 25.2,
            "fibra_g" to null,
            "medidas" to listOf(
                mapOf("nombre" to "Taza de loza al ras", "gramos" to 193.2, "fuente" to "TAFERA 2016 (INS/CENAN) 1-20"),
                mapOf("nombre" to "", "gramos" to 5), "x",
            ),
            "fuente" to "TPCA 2023 (INS/CENAN)", "codigo_fuente" to "A 2",
        ))!!
        assertEquals(115.0, a.por100.kcal)
        assertEquals(2.4, a.por100.proteinas)
        assertEquals(25.2, a.por100.carbohidratos)
        assertNull(a.por100.fibra)
        // Sin disponibles en la fila: totales − fibra (sin fibra = totales).
        assertEquals(25.2, a.por100.carbohidratosDisponibles)
        assertEquals(listOf(MedidaCasera("Taza de loza al ras", 193.2, "TAFERA 2016 (INS/CENAN) 1-20")), a.medidas)
        assertEquals("TPCA 2023 (INS/CENAN) · A 2", a.fuente)
        assertEquals("catalogo:TPCA-A2", refAlimento(a))
    }

    @Test fun propioOrigenClinica() {
        val a = alimentoDeClinica(propio)
        assertEquals(OrigenAlimento.CLINICA, a.origen)
        assertFalse(a.frecuente)
        assertEquals(97.0, a.por100.kcal)
        assertEquals(110.0, a.por100.calcio)
        assertEquals(
            listOf(MedidaCasera("Vaso", 200.0)),
            normalizarMedidas(jsonDe(listOf(mapOf("nombre" to "Vaso", "gramos" to "200"), mapOf("nombre" to "X", "gramos" to 0)))),
        )
        // La fila del API (NUMERIC como texto) se lee igual.
        val f = filaClinicaDeJson(jsonDe(mapOf(
            "id" to "u1", "nombre" to "Yogur", "grupo" to null, "energia_kcal" to "97", "proteinas_g" to "9", "grasas_g" to "5",
            "carbohidratos_g" to "4", "fibra_g" to null, "sodio_mg" to null, "hierro_mg" to null, "calcio_mg" to "110",
            "medidas" to listOf(mapOf("nombre" to "Pote", "gramos" to 150)), "fuente" to null,
        )) as JsonObject)!!
        assertEquals(97.0, f.kcal)
        assertEquals(listOf(MedidaCasera("Pote", 150.0)), f.medidas)
    }

    @Test fun catalogoDelApi() {
        val o = Json.parseToJsonElement(
            """{ "version": "2026-10-25", "items": [
                 { "id": "TPCA-A53", "nombre": "Quinua cocida", "grupo": "Cereales y derivados", "sinonimos": ["quinoa"],
                   "busqueda": "quinua cocida quinoa", "energia_kcal": "89", "proteinas_g": 2.8, "grasas_g": 1.3,
                   "carbohidratos_g": 16.3, "carbohidratos_disp_g": 16.3, "carbohidratos_disp_derivado": true, "fibra_g": null,
                   "sodio_mg": 24, "hierro_mg": 1.6, "calcio_mg": 27, "principal": false, "frecuente": true,
                   "medidas": [{ "nombre": "Taza de loza al ras", "gramos": 354.5, "fuente": "TAFERA 2016 (INS/CENAN) 1-155" }],
                   "fuente": "TPCA 2023 (INS/CENAN)", "codigo_fuente": "A 53", "nota": null },
                 { "nombre": "sin id" } ] }""",
        ).jsonObject
        val c = catalogoDeJson(o)
        assertEquals("2026-10-25", c.version)
        assertEquals(1, c.alimentos.size)
        assertEquals(89.0, c.alimentos[0].por100.kcal)
        assertEquals("quinua cocida quinoa", c.alimentos[0].busqueda)
        assertTrue(c.alimentos[0].frecuente)
    }

    // ── Cantidades ──

    @Test fun parsearCantidades() {
        assertEquals(0.5, parsearCantidad("½"))
        assertEquals(1.5, parsearCantidad("1½"))
        assertEquals(1.5, parsearCantidad("1 1/2"))
        assertEquals(0.5, parsearCantidad("1/2"))
        assertEquals(0.75, parsearCantidad("3/4"))
        assertEquals(1.5, parsearCantidad("1,5"))
        assertEquals(80.0, parsearCantidad("80"))
        assertNull(parsearCantidad(""))
        assertNull(parsearCantidad(null))
        assertTrue(parsearCantidad("abc")!!.isNaN())
        assertTrue(parsearCantidad("1/0")!!.isNaN())
    }

    @Test fun fraccionYGramos() {
        assertEquals("½", fraccion(0.5))
        assertEquals("1½", fraccion(1.5))
        assertEquals("¼", fraccion(0.25))
        assertEquals("⅓", fraccion(1.0 / 3))
        assertEquals("2", fraccion(2.0))
        assertEquals("0.4", fraccion(0.4))
        assertEquals("2.4 g", textoGramos(2.44))
        assertEquals("177 g", textoGramos(177.3))
    }

    @Test fun textoDeLaCantidad() {
        assertEquals("½ taza de loza al ras (177 g)", textoCantidad(177.3, MedidaItem("Taza de loza al ras", 354.5, 0.5)))
        assertEquals("2 × unidad mediana (120 g)", textoCantidad(119.8, MedidaItem("Unidad mediana", 59.9, 2.0)))
        assertEquals("1 unidad mediana (60 g)", textoCantidad(59.9, MedidaItem("Unidad mediana", 59.9, 1.0)))
        assertEquals("80 g", textoCantidad(80.0, null))
        val it = itemDesdeAlimento(quinua(), medida = "Taza de loza al ras", cantidad = 0.5)
        assertEquals("Quinua cocida — ½ taza de loza al ras (177 g)", lineaItem(it))
    }

    // ── Ítems de una comida ──

    private val por100 = mapOf(
        "kcal" to 89, "proteinas" to 2.8, "grasas" to 1.3, "carbohidratos" to 16.3, "fibra" to null,
        "sodio" to 24, "hierro" to 1.6, "calcio" to 27,
    )
    private fun item(vararg extra: Pair<String, Any?>): JsonElement {
        val m = linkedMapOf<String, Any?>("origen" to "catalogo", "alimentoId" to "TPCA-A53", "nombre" to "Quinua cocida", "por100" to por100)
        extra.forEach { (k, v) -> m[k] = v }
        return jsonDe(m)
    }

    private fun quinua(): Alimento = alimentoDeCatalogo(fila(
        "TPCA-A53", "Quinua cocida", "energia_kcal" to 89, "proteinas_g" to 2.8, "grasas_g" to 1.3, "carbohidratos_g" to 16.3,
        "medidas" to listOf(mapOf("nombre" to "Taza de loza al ras", "gramos" to 354.5)),
    ))!!

    @Test fun conMedidaLosGramosSeRecalculan() {
        val it = normalizarItem(item("gramos" to 999, "medida" to mapOf("nombre" to "Taza de loza al ras", "gramos" to 354.5, "cantidad" to 0.5)))!!
        assertEquals(177.3, it.gramos)
        assertEquals(MedidaItem("Taza de loza al ras", 354.5, 0.5), it.medida)
        assertEquals(89.0, it.por100!!.kcal)
    }

    @Test fun rechazaLoQueNoCuadra() {
        val ok = normalizarItem(item("gramos" to 80, "medida" to null))!!
        assertEquals(80.0, ok.gramos)
        assertNull(ok.medida)
        assertNull(normalizarItem(item("origen" to "otro", "gramos" to 80)))
        assertNull(normalizarItem(item("nombre" to " ", "gramos" to 80)))
        assertNull(normalizarItem(item("gramos" to 0)))
        assertNull(normalizarItem(item("gramos" to 6000)))
        assertNull(normalizarItem(item("gramos" to 80, "por100" to por100 + ("kcal" to 1000))))
        assertNull(normalizarItem(item("gramos" to 80, "por100" to por100 + ("fibra" to 150))))
        assertNull(normalizarItem(item("gramos" to 80, "por100" to por100 + ("proteinas" to null))))
        assertNull(normalizarItem(item("gramos" to 80, "medida" to mapOf("nombre" to "Taza", "gramos" to 200, "cantidad" to 0))))
        // Carbohidratos o micronutrientes sin dato: válido (null).
        val sin = normalizarItem(item("gramos" to 80, "por100" to por100 + ("carbohidratos" to null) + ("sodio" to "")))!!
        assertNull(sin.por100!!.carbohidratos)
        assertNull(sin.por100!!.sodio)
    }

    @Test fun listaYErrores() {
        val muchos = jsonDe(List(35) { mapOf("origen" to "catalogo", "alimentoId" to "TPCA-A53", "nombre" to "Quinua cocida", "por100" to por100, "gramos" to 10) })
        assertEquals(LimitesAlimento.ITEMS_POR_COMIDA, normalizarItems(muchos).size)
        assertEquals(1, normalizarItems(buildJsonArray { add(item("gramos" to 10)); add(jsonDe(mapOf("x" to 1))) }).size)
        assertEquals(emptyList(), normalizarItems(JsonPrimitive("no")))
        val q = itemDesdeAlimento(quinua(), gramos = 10.0)
        assertEquals(emptyList(), erroresItems(listOf(q), "Cena"))
        assertEquals(listOf("Máximo 30 alimentos en «Cena»."), erroresItems(List(31) { q }, "Cena"))
        assertTrue(erroresItems(listOf(q.copy(gramos = 0.0)), "Cena")[0].contains("«Quinua cocida» en «Cena»"))
    }

    @Test fun paraMostrarSinNutrientes() {
        val r = normalizarItemsParaMostrar(jsonDe(listOf(
            mapOf("nombre" to "Quinua cocida", "gramos" to 177.3, "medida" to mapOf("nombre" to "Taza de loza al ras", "gramos" to 354.5, "cantidad" to 0.5)),
            mapOf("nombre" to "", "gramos" to 1),
        )))
        assertEquals(1, r.size)
        assertEquals("Quinua cocida", r[0].nombre)
        assertEquals(177.3, r[0].gramos)
        assertEquals(MedidaItem("Taza de loza al ras", 354.5, 0.5), r[0].medida)
        assertNull(r[0].por100)
    }

    @Test fun itemDesdeUnAlimento() {
        val conMedida = alimentoDeCatalogo(fila("TPCA-J3", "Huevo",
            "medidas" to listOf(mapOf("nombre" to "Unidad mediana", "gramos" to 59.9), mapOf("nombre" to "Unidad grande", "gramos" to 67.7))))!!
        val sinMedida = alimentoDeCatalogo(fila("TPCA-F82", "Pechuga"))!!
        itemDesdeAlimento(conMedida).let { assertEquals(59.9, it.gramos); assertEquals("Unidad mediana", it.medida?.nombre); assertEquals(1.0, it.medida?.cantidad) }
        itemDesdeAlimento(conMedida, medida = "Unidad grande", cantidad = 2.0).let { assertEquals(135.4, it.gramos); assertEquals(2.0, it.medida?.cantidad) }
        itemDesdeAlimento(conMedida, gramos = 50.0).let { assertEquals(50.0, it.gramos); assertNull(it.medida) }
        // La medida habitual ya no existe en el catálogo: se usan sus gramos.
        itemDesdeAlimento(conMedida, medida = "Ya no existe", cantidad = 1.0, gramos = 61.0).let { assertEquals(61.0, it.gramos); assertNull(it.medida) }
        itemDesdeAlimento(sinMedida).let {
            assertEquals(100.0, it.gramos); assertNull(it.medida)
            assertEquals(OrigenAlimento.CATALOGO, it.origen); assertEquals("TPCA-F82", it.alimentoId)
        }
        assertEquals(120.0, conCantidad(itemDesdeAlimento(sinMedida), 120.0, null).gramos)
    }

    @Test fun cambiarDeUnidadYMedidas() {
        val huevo = alimentoDeCatalogo(fila("TPCA-J3", "Huevo", "medidas" to listOf(mapOf("nombre" to "Unidad mediana", "gramos" to 59.9))))!!
        val it = itemDesdeAlimento(huevo, gramos = 130.0)
        // A una medida: la cantidad más cercana de a ¼ (130 / 59,9 = 2,17 → 2,25).
        val m = cambiarUnidad(it, huevo.medidas[0])
        assertEquals(2.25, m.medida?.cantidad)
        assertEquals(134.8, m.gramos)
        // A gramos: conserva el peso.
        assertEquals(134.8, cambiarUnidad(m, null).gramos)
        assertNull(cambiarUnidad(m, null).medida)
        // Mínimo ¼.
        assertEquals(0.25, cambiarUnidad(itemDesdeAlimento(huevo, gramos = 1.0), huevo.medidas[0]).medida?.cantidad)
        // La medida del ítem que ya no está en el alimento se ofrece igual (primero).
        val viejo = it.copy(medida = MedidaItem("Unidad chica", 50.0, 1.0))
        assertEquals(listOf("Unidad chica", "Unidad mediana"), medidasDe(viejo, huevo).map { x -> x.nombre })
        assertEquals(listOf("Unidad chica"), medidasDe(viejo, null).map { x -> x.nombre })
    }

    @Test fun habitualDeLaClinica() {
        val fr = alimentosFrecuentesDeJson(Json.parseToJsonElement(
            """[ { "origen": "catalogo", "alimentoId": "TPCA-A53", "nombre": "Quinua cocida", "usos": 7, "gramos": 177.3,
                   "medida": { "nombre": "Taza de loza al ras", "gramos": 354.5, "cantidad": 0.5 } },
                 { "origen": "clinica", "alimentoId": "u1", "nombre": "Yogur", "usos": 2, "gramos": 150, "medida": null },
                 { "origen": "x", "alimentoId": "z" }, "basura" ]""",
        ))
        assertEquals(listOf("catalogo:TPCA-A53", "clinica:u1"), fr.map { refAlimento(it) })
        assertEquals(7, fr[0].usos)
        val it = itemConHabitual(quinua(), fr[0])
        assertEquals(0.5, it.medida?.cantidad)
        assertEquals(177.3, it.gramos)
        assertEquals(150.0, itemConHabitual(alimentoDeClinica(propio), fr[1]).gramos)
    }

    @Test fun itemAlJsonYDeVuelta() {
        val it = itemDesdeAlimento(quinua(), medida = "Taza de loza al ras", cantidad = 0.5)
        val j = itemAJson(it)
        assertEquals("catalogo", (j["origen"] as JsonPrimitive).content)
        assertEquals(it, normalizarItem(j))
        // Los disponibles van solo si están (un ítem viejo no los trae).
        val viejo = it.copy(por100 = it.por100!!.copy(carbohidratosDisponibles = null))
        assertFalse("carbohidratosDisponibles" in (itemAJson(viejo)["por100"] as JsonObject))
        assertEquals(JsonNull, itemAJson(it.copy(medida = null))["medida"])
    }

    // ── Alimentos propios ──

    private val ok = BorradorAlimentoClinica(nombre = "Yogur griego", kcal = "97", proteinas = "9", grasas = "5", carbohidratos = "4")

    @Test fun propioValidoYCuerpo() {
        assertEquals(emptyList(), validarAlimentoClinica(ok))
        val c = cuerpoAlimentoClinica(ok.copy(fibra = "", sodio = "45", medidas = listOf("Pote" to "150"), fuente = " Etiqueta "))
        assertEquals("Yogur griego", (c["nombre"] as JsonPrimitive).content)
        assertEquals(JsonNull, c["grupo"])
        assertEquals(97.0, (c["kcal"] as JsonPrimitive).content.toDouble())
        assertEquals(JsonNull, c["fibra"])
        assertEquals(45.0, (c["sodio"] as JsonPrimitive).content.toDouble())
        assertEquals("""[{"nombre":"Pote","gramos":150}]""", c["medidas"].toString())
        assertEquals("Etiqueta", (c["fuente"] as JsonPrimitive).content)
        assertFalse("id" in c)
        assertEquals("u1", (cuerpoAlimentoClinica(ok, "u1")["id"] as JsonPrimitive).content)
    }

    @Test fun propioErroresGemelosDeLosCheck() {
        assertTrue("Ponle un nombre al alimento." in validarAlimentoClinica(ok.copy(nombre = " ")))
        assertTrue("Escribe las kcal por 100 g." in validarAlimentoClinica(ok.copy(kcal = null)))
        assertTrue(validarAlimentoClinica(ok.copy(kcal = "1200"))[0].contains("entre 0 y 950"))
        assertTrue(validarAlimentoClinica(ok.copy(proteinas = "60", grasas = "30", carbohidratos = "20"))[0].contains("suman 110 g"))
        assertTrue(validarAlimentoClinica(ok.copy(carbohidratos = ""))[0].contains("carbohidratos por 100 g"))
        assertEquals(listOf("Sodio: entre 0 y 50000."), validarAlimentoClinica(ok.copy(sodio = "mucho")))
        assertEquals(listOf("Cada medida casera necesita nombre y gramos (entre 0.1 y 5000)."),
            validarAlimentoClinica(ok.copy(medidas = listOf("" to "10"))))
        assertEquals(listOf("Máximo 10 medidas caseras."), validarAlimentoClinica(ok.copy(medidas = List(11) { "Vaso" to "200" })))
    }

    @Test fun porcentajeDelCatalogoNoSeComparte() {
        // La foto es una copia: cambiar el ítem no toca el alimento.
        val a = quinua()
        val it = itemDesdeAlimento(a)
        assertNotNull(it.por100)
        assertEquals(a.por100, it.por100)
        assertTrue(Nutrientes100(1.0, 1.0, 1.0, null) != it.por100)
    }
}

/** Mapa / lista / número / texto de Kotlin → JSON (para armar las filas de prueba). */
internal fun jsonDe(v: Any?): JsonElement = when (v) {
    null -> JsonNull
    is JsonElement -> v
    is String -> JsonPrimitive(v)
    is Boolean -> JsonPrimitive(v)
    is Number -> JsonPrimitive(v)
    is Map<*, *> -> buildJsonObject { v.forEach { (k, x) -> put(k.toString(), jsonDe(x)) } }
    is List<*> -> JsonArray(v.map { jsonDe(it) })
    else -> JsonPrimitive(v.toString())
}

private fun F(id: String, nombre: String, busqueda: String, frecuente: Boolean, principal: Boolean) = Alimento(
    origen = OrigenAlimento.CATALOGO, id = id, nombre = nombre, grupo = null,
    por100 = Nutrientes100(100.0, 1.0, 1.0, 1.0), medidas = emptyList(),
    frecuente = frecuente, principal = principal, busqueda = busqueda, fuente = null, nota = null,
)

/**
 * Las filas REALES de la semilla (20261025210000_alimentos_semilla.sql) cuyo
 * nombre o búsqueda contiene "papa", "pollo", "huevo", "arroz", "higado",
 * "clara", "leche", "pech", "quinua" o "palta": todo lo que puede calzar con
 * las búsquedas de los tests (para lo demás no cambia nada).
 */
private val SEMILLA_BUSQUEDA: List<Alimento> = listOf(
    F("TPCA-A2", "Arroz pilado o pulido cocido", "arroz pilado o pulido cocido", true, true),
    F("TPCA-A3", "Arroz blanco corriente", "arroz blanco corriente", true, false),
    F("TPCA-A4", "Arroz con cáscara", "arroz con cascara", false, false),
    F("TPCA-A50", "Quinua, afrecho de", "quinua afrecho de quinoa", false, false),
    F("TPCA-A51", "Quinua blanca, (Junín)", "quinua blanca junin quinoa", false, false),
    F("TPCA-A52", "Quinua blanca (Puno)", "quinua blanca puno quinoa", false, false),
    F("TPCA-A53", "Quinua cocida", "quinua cocida quinoa", true, true),
    F("TPCA-A54", "Quinua", "quinua quinoa", true, false),
    F("TPCA-A55", "Quinua dulce, blanca (Junín)", "quinua dulce blanca junin quinoa", false, false),
    F("TPCA-A56", "Quinua dulce, blanca (Puno)", "quinua dulce blanca puno quinoa", false, false),
    F("TPCA-A57", "Quinua dulce, rosada (Junín)", "quinua dulce rosada junin quinoa", false, false),
    F("TPCA-A58", "Quinua, harina de", "quinua harina de quinoa", true, false),
    F("TPCA-A59", "Quinua, hojuela de", "quinua hojuela de quinoa", true, false),
    F("TPCA-A60", "Quinua rosada (Puno)", "quinua rosada puno quinoa", false, false),
    F("TPCA-A61", "Quinua, sémola de", "quinua semola de quinoa", false, false),
    F("TPCA-A76", "Barra de cereales (avena, arroz) con aguaymanto", "barra de cereales avena arroz con aguaymanto physalis uchuva", false, false),
    F("TPCA-A118", "Pan de huevo", "pan de huevo", true, false),
    F("TPCA-A122", "Pan de quinua de Lima", "pan de quinua de lima quinoa", false, false),
    F("TPCA-A136", "Pan 3 granos (quinua, cañihua y kiwicha) de Lima", "pan 3 granos quinua canihua y kiwicha de lima amaranto kanihua kaniwa quinoa", false, false),
    F("TPCA-A139", "Pasta con huevo sin fortificar, cruda", "pasta con huevo sin fortificar cruda", false, false),
    F("TPCA-A140", "Pasta con huevo fortificada, cruda", "pasta con huevo fortificada cruda", false, false),
    F("TPCA-A143", "Quinua, variedad real", "quinua variedad real quinoa", false, false),
    F("TPCA-A144", "Quinua, variedad Ayara (Puno)", "quinua variedad ayara puno quinoa", false, false),
    F("TPCA-A145", "Quinua, variedad blanca de Juli (Puno)", "quinua variedad blanca de juli puno quinoa", false, false),
    F("TPCA-A146", "Quinua, variedad CICA-18 (Puno)", "quinua variedad cica 18 puno quinoa", false, false),
    F("TPCA-A147", "Quinua, variedad CICA-127 (Puno)", "quinua variedad cica 127 puno quinoa", false, false),
    F("TPCA-A148", "Quinua, variedad Cuchiwilla (Puno)", "quinua variedad cuchiwilla puno quinoa", false, false),
    F("TPCA-A149", "Quinua, variedad Choclito (Puno)", "quinua variedad choclito puno quinoa", false, false),
    F("TPCA-A150", "Quinua, variedad Chullpi- roja (Puno)", "quinua variedad chullpi roja puno quinoa", false, false),
    F("TPCA-A151", "Quinua, variedad Misa Jiura (Puno)", "quinua variedad misa jiura puno quinoa", false, false),
    F("TPCA-A152", "Quinua, variedad Pasankalla-roja (Puno)", "quinua variedad pasankalla roja puno quinoa", false, false),
    F("TPCA-A153", "Quinua, variedad Q' OITU-negra (Puno)", "quinua variedad q oitu negra puno quinoa", false, false),
    F("TPCA-A154", "Quinua, variedad Wariponcho (Puno)", "quinua variedad wariponcho puno quinoa", false, false),
    F("TPCA-A155", "Quinua, variedad Witulla (Puno)", "quinua variedad witulla puno quinoa", false, false),
    F("TPCA-A157", "Siete harinas (trigo, habas, arvejas, kiwicha, cañihua, cebada, quinua)", "siete harinas trigo habas arvejas kiwicha canihua cebada quinua amaranto kanihua kaniwa quinoa", false, false),
    F("TPCA-A161", "Alfajor con relleno de leche y maní cubierto con chocolate, envasado", "alfajor con relleno de leche y mani cubierto con chocolate envasado cacahuate cacahuete", false, false),
    F("TPCA-A166", "Avena con cereales andinos (cañihua, kiwicha y quinua), hojuela precocida de", "avena con cereales andinos canihua kiwicha y quinua hojuela precocida de amaranto kanihua kaniwa quinoa", false, false),
    F("TPCA-A167", "Avena con quinua, hojuela precocida de", "avena con quinua hojuela precocida de quinoa", false, false),
    F("TPCA-A168", "Avena con quinua, canela y clavo en polvo, hojuela precocida de", "avena con quinua canela y clavo en polvo hojuela precocida de quinoa", false, false),
    F("TPCA-B32", "Col crespa o repollo, sin cogollo", "col crespa o repollo sin cogollo", true, false),
    F("TPCA-B72", "Quinua, hojas de", "quinua hojas de quinoa", false, false),
    F("TPCA-C55", "Palta", "palta aguacate", true, true),
    F("TPCA-C59", "Papaya", "papaya", true, false),
    F("TPCA-C131", "Palta \"fuerte\"", "palta fuerte aguacate", true, false),
    F("TPCA-C132", "Papaya arequipeña", "papaya arequipena", false, false),
    F("TPCA-C160", "Palta dedo o cocktail", "palta dedo o cocktail aguacate", false, false),
    F("TPCA-C161", "Palta Hass", "palta hass aguacate", true, false),
    F("TPCA-C162", "Papaya, néctar natural, sin azúcar", "papaya nectar natural sin azucar", false, false),
    F("TPCA-F4", "Carnero, hígado de", "carnero higado de", false, false),
    F("TPCA-F14", "Cerdo, hígado de", "cerdo higado de chancho porcino", false, false),
    F("TPCA-F20", "Gallina, pechuga de, sin piel", "gallina pechuga de sin piel", true, false),
    F("TPCA-F26", "Pollo, carne pulpa", "pollo carne pulpa", true, true),
    F("TPCA-F27", "Pollo, corazón de", "pollo corazon de", false, false),
    F("TPCA-F28", "Pollo, hígado de", "pollo higado de", true, false),
    F("TPCA-F29", "Pollo, sangre de, sancochada sin sal", "pollo sangre de sancochada sin sal sangrecita", true, false),
    F("TPCA-F30", "Pollo, sangre cruda de", "pollo sangre cruda de sangrecita", false, false),
    F("TPCA-F38", "Res, hígado de", "res higado de carne de res vaca vacuno", true, false),
    F("TPCA-F77", "Pate de hígado de pollo, envasado", "pate de higado de pollo envasado", false, false),
    F("TPCA-F78", "Pollo, alas con piel, crudas", "pollo alas con piel crudas", false, false),
    F("TPCA-F79", "Pollo, molleja, cruda", "pollo molleja cruda", true, false),
    F("TPCA-F80", "Pollo, molleja de, sancochada sin sal", "pollo molleja de sancochada sin sal", false, false),
    F("TPCA-F81", "Pollo, pata de, con piel, cruda", "pollo pata de con piel cruda", false, false),
    F("TPCA-F82", "Pollo, pechuga de, sin piel, sancochada sin sal", "pollo pechuga de sin piel sancochada sin sal", true, false),
    F("TPCA-F83", "Pollo, pechuga de, sin piel, frita en aceite vegetal, sin sal", "pollo pechuga de sin piel frita en aceite vegetal sin sal", true, false),
    F("TPCA-F84", "Pollo, pierna de, con piel, sancochada sin sal", "pollo pierna de con piel sancochada sin sal", true, false),
    F("TPCA-F85", "Pollo, pierna de, con piel, frita en aceite vegetal, sin sal", "pollo pierna de con piel frita en aceite vegetal sin sal", true, false),
    F("TPCA-F86", "Pollo, vísceras o menudencia cruda", "pollo visceras o menudencia cruda", false, false),
    F("TPCA-F94", "Res, hígado de, frito en aceite vegetal, sin sal", "res higado de frito en aceite vegetal sin sal carne de res vaca vacuno", true, false),
    F("TPCA-F111", "Cuy, pecho de, pulpa con piel, cruda", "cuy pecho de pulpa con piel cruda cobayo", false, false),
    F("TPCA-F114", "Hot dog, embutido de carne de pollo, cerdo y pavo, frito en aceite vegetal", "hot dog embutido de carne de pollo cerdo y pavo frito en aceite vegetal chancho porcino", false, false),
    F("TPCA-F117", "Paté de hígado de pollo, pavo y carne de cerdo", "pate de higado de pollo pavo y carne de cerdo chancho porcino", false, false),
    F("TPCA-F118", "Pavo, pechuga de, con piel", "pavo pechuga de con piel", true, false),
    F("TPCA-F120", "Pollo, ala de, con piel, sancochada sin sal (brazuelo, centro y punta)", "pollo ala de con piel sancochada sin sal brazuelo centro y punta", false, false),
    F("TPCA-F121", "Pollo, corazón de, sancochado sin sal", "pollo corazon de sancochado sin sal", false, false),
    F("TPCA-F122", "Pollo, encuentro de, con piel, frito en aceite vegetal, sin sal", "pollo encuentro de con piel frito en aceite vegetal sin sal", false, false),
    F("TPCA-F126", "Pollo, nuggets de pechuga de, crudo, envasado", "pollo nuggets de pechuga de crudo envasado", true, false),
    F("TPCA-F127", "Pollo, pecho y ala de, con piel a la brasa", "pollo pecho y ala de con piel a la brasa", true, false),
    F("TPCA-F128", "Pollo, pierna y encuentro de, con piel a la brasa", "pollo pierna y encuentro de con piel a la brasa", true, false),
    F("TPCA-G1", "Crema de leche, espesa", "crema de leche espesa", true, false),
    F("TPCA-G2", "Crema de leche, rala", "crema de leche rala", false, false),
    F("TPCA-G3", "Leche condensada endulzada", "leche condensada endulzada", true, false),
    F("TPCA-G4", "Leche en polvo descremada", "leche en polvo descremada", true, false),
    F("TPCA-G5", "Leche en polvo entera", "leche en polvo entera", true, false),
    F("TPCA-G6", "Leche evaporada descremada", "leche evaporada descremada leche de tarro", true, false),
    F("TPCA-G7", "Leche evaporada entera", "leche evaporada entera leche de tarro", true, false),
    F("TPCA-G8", "Leche fresca c/menos de 1% grasa", "leche fresca c menos de 1 grasa", true, false),
    F("TPCA-G9", "Leche fresca entera (Plusa)", "leche fresca entera plusa", false, false),
    F("TPCA-G10", "Leche fresca de cabra", "leche fresca de cabra", false, false),
    F("TPCA-G11", "Leche fresca de vaca", "leche fresca de vaca", true, true),
    F("TPCA-G12", "Leche materna", "leche materna", false, false),
    F("TPCA-G17", "Yogurt de leche entera", "yogurt de leche entera yogur", true, false),
    F("TPCA-G18", "Yogurt frutado de leche semidescremada", "yogurt frutado de leche semidescremada yogur", true, false),
    F("TPCA-G19", "Yogurt frutado de leche descremada", "yogurt frutado de leche descremada yogur", false, false),
    F("TPCA-G20", "Yogurt natural de leche descremada", "yogurt natural de leche descremada yogur", true, false),
    F("TPCA-G21", "Leche fresca de vaca, descremada", "leche fresca de vaca descremada", true, false),
    F("TPCA-G22", "Leche parcialmente descremada sabor \"chocolate\" UHT", "leche parcialmente descremada sabor chocolate uht", false, false),
    F("TPCA-G27", "Leche concentrada endulzada, enriquecida con DHA, zinc y vitaminas", "leche concentrada endulzada enriquecida con dha zinc y vitaminas", false, false),
    F("TPCA-G28", "Leche concentrada, sin lactosa", "leche concentrada sin lactosa", true, false),
    F("TPCA-J1", "Huevo de charapa", "huevo de charapa", false, false),
    F("TPCA-J2", "Huevo de gallina, clara de", "huevo de gallina clara de", true, false),
    F("TPCA-J3", "Huevo de gallina entero, sancochado en agua", "huevo de gallina entero sancochado en agua", true, true),
    F("TPCA-J4", "Huevo de gallina entero, crudo", "huevo de gallina entero crudo", true, false),
    F("TPCA-J5", "Huevo de gallina, yema de", "huevo de gallina yema de", true, false),
    F("TPCA-J6", "Huevo de tortuga motelo", "huevo de tortuga motelo", false, false),
    F("TPCA-J7", "Huevo de pata", "huevo de pata", false, false),
    F("TPCA-J8", "Huevo de pata, yema de", "huevo de pata yema de", false, false),
    F("TPCA-J9", "Huevo de tortuga taricaya", "huevo de tortuga taricaya", false, false),
    F("TPCA-J10", "Huevo de gallina, clara sancochada en agua", "huevo de gallina clara sancochada en agua", true, false),
    F("TPCA-J11", "Huevo de gallina, yema sancochada en agua", "huevo de gallina yema sancochada en agua", false, false),
    F("TPCA-J12", "Huevo de codorniz, entero crudo", "huevo de codorniz entero crudo", true, false),
    F("TPCA-J13", "Huevo de codorniz, entero sancochado en agua", "huevo de codorniz entero sancochado en agua", true, false),
    F("TPCA-J14", "Huevo de codorniz, clara sancochada en agua", "huevo de codorniz clara sancochada en agua", false, false),
    F("TPCA-J15", "Huevo de codorniz, yema sancochada en agua", "huevo de codorniz yema sancochada en agua", false, false),
    F("TPCA-J16", "Huevo de gallina, entero, frito en aceite vegetal, sin sal", "huevo de gallina entero frito en aceite vegetal sin sal", true, false),
    F("TPCA-L18", "Caldo de pollo, o gallina", "caldo de pollo o gallina", false, false),
    F("TPCA-L49", "Sopa con fideo precocido y sazonador en polvo sabor a pollo, envasado", "sopa con fideo precocido y sazonador en polvo sabor a pollo envasado pasta", false, false),
    F("TPCA-T72", "Frejol soya, bebida de, (\"leche\" de soya UHT)", "frejol soya bebida de leche de soya uht frijol judia poroto", true, false),
    F("TPCA-U17", "Olluco sin cáscara", "olluco sin cascara papa lisa ulluco", true, false),
    F("TPCA-U18", "Papa amarilla sin cáscara", "papa amarilla sin cascara", true, false),
    F("TPCA-U19", "Papa blanca", "papa blanca", true, true),
    F("TPCA-U20", "Papa chuño negro", "papa chuno negro", true, false),
    F("TPCA-U21", "Papa color morado deshidratada", "papa color morado deshidratada", false, false),
    F("TPCA-U22", "Papa helada amarga", "papa helada amarga", false, false),
    F("TPCA-U23", "Papa huayro con cáscara", "papa huayro con cascara", true, false),
    F("TPCA-U24", "Papa moraya chuño pelado", "papa moraya chuno pelado", true, false),
    F("TPCA-U25", "Papa seca", "papa seca", true, false),
    F("TPCA-U26", "Papa, harina de", "papa harina de", false, false),
    F("TPCA-U27", "Papas deshidratadas con lejía", "papas deshidratadas con lejia", false, false),
    F("TPCA-U32", "Sachapapa", "sachapapa", false, false),
    F("TPCA-U33", "Sachapapa blanca, bulbo", "sachapapa blanca bulbo", false, false),
    F("TPCA-U46", "Iguana papa (tubérculo)", "iguana papa tuberculo", false, false),
    F("TPCA-U55", "Papa moraya, harina de", "papa moraya harina de", false, false),
    F("TPCA-U56", "Papa negra andina", "papa negra andina", false, false),
    F("TPCA-U57", "Papa sin cáscara, variedad amarilis", "papa sin cascara variedad amarilis", false, false),
    F("TPCA-U58", "Papa sin cáscara, variedad huevo de indio", "papa sin cascara variedad huevo de indio", false, false),
    F("TPCA-U59", "Papa sin cáscara, variedad tumbay", "papa sin cascara variedad tumbay", false, false),
    F("TPCA-U60", "Papa sin cáscara, variedad única", "papa sin cascara variedad unica", false, false),
    F("TPCA-U61", "Papa sin cáscara, variedad Yungay", "papa sin cascara variedad yungay", false, false),
    F("TPCA-U62", "Papa variedad nativa capulí con cáscara", "papa variedad nativa capuli con cascara", false, false),
    F("TPCA-U63", "Papa variedad nativa caramarquina con cáscara", "papa variedad nativa caramarquina con cascara", false, false),
    F("TPCA-U64", "Papa variedad nativa chimbina amarilla con cáscara", "papa variedad nativa chimbina amarilla con cascara", false, false),
    F("TPCA-U65", "Papa variedad nativa huagalina con cáscara", "papa variedad nativa huagalina con cascara", false, false),
    F("TPCA-U66", "Papa variedad nativa hualash con cáscara", "papa variedad nativa hualash con cascara", false, false),
    F("TPCA-U67", "Papa variedad nativa huayro con cáscara", "papa variedad nativa huayro con cascara", false, false),
    F("TPCA-U68", "Papa variedad nativa huevo de toro con cáscara", "papa variedad nativa huevo de toro con cascara", false, false),
    F("TPCA-U69", "Papa variedad nativa limeña con cáscara", "papa variedad nativa limena con cascara", false, false),
    F("TPCA-U70", "Papa variedad nativa maga blanca con cáscara", "papa variedad nativa maga blanca con cascara", false, false),
    F("TPCA-U71", "Papa variedad nativa peruanita con cáscara", "papa variedad nativa peruanita con cascara", false, false),
    F("TPCA-U72", "Papa variedad nativa puca pampamachay con cáscara", "papa variedad nativa puca pampamachay con cascara", false, false),
    F("TPCA-U73", "Papa variedad nativa puca pishgosh con cáscara", "papa variedad nativa puca pishgosh con cascara", false, false),
    F("TPCA-U74", "Papa variedad nativa quinuapa tulum con cáscara", "papa variedad nativa quinuapa tulum con cascara", false, false),
    F("TPCA-U75", "Papa variedad nativa sapa negra con cáscara", "papa variedad nativa sapa negra con cascara", false, false),
    F("TPCA-U76", "Papa variedad nativa yana shaque con cáscara", "papa variedad nativa yana shaque con cascara", false, false),
    F("TPCA-U78", "Sachapapa, bulbo aéreo", "sachapapa bulbo aereo", false, false),
    F("TPCA-U82", "Papa \"cóctel\", con cáscara, cruda", "papa coctel con cascara cruda", true, false),
    F("TPCA-U83", "Papa, prefrita en aceite vegetal, congelada, envasada", "papa prefrita en aceite vegetal congelada envasada", false, false),
    F("TPCA-U84", "Papa, chuño de, envasado (almidón de papa o fécula de papa)", "papa chuno de envasado almidon de papa o fecula de papa", true, false),
    F("TPCA-U85", "Papa morada sin cáscara", "papa morada sin cascara", true, false),
    F("TPCA-U86", "Papa, tocosh fresco", "papa tocosh fresco", false, false),
    F("TPCA-U87", "Papa, tocosh harina", "papa tocosh harina", false, false),
)
