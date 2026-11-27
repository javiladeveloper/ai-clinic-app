package pe.saniape.app.data.staff

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.jsonObject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** 🧰 Lo usado al cerrar una sesión (inventario clínico, 26/11/2026). */
class InventarioRepoTest {
    @Test
    fun soloProductosActivos_conSuUnidad() {
        val o = Json.parseToJsonElement(
            "{\"activa\":true,\"productos\":[" +
                "{\"id\":\"p1\",\"nombre\":\"Gel conductor\",\"unidad_contenido\":\"ml\",\"estado\":\"Activo\"}," +
                "{\"id\":\"p2\",\"nombre\":\"Guantes\",\"unidad_contenido\":null,\"estado\":\"Activo\"}," +
                "{\"id\":\"p3\",\"nombre\":\"Viejo\",\"unidad_contenido\":\"ml\",\"estado\":\"Inactivo\"}," +
                "{\"nombre\":\"Sin id\"}]}",
        ).jsonObject
        assertEquals(
            listOf(ProductoConsumoApp("p1", "Gel conductor", "ml"), ProductoConsumoApp("p2", "Guantes", "unidad")),
            parsearProductosConsumo(o),
        )
        assertTrue(parsearProductosConsumo(Json.parseToJsonElement("{\"activa\":false}").jsonObject).isEmpty())
    }

    /**
     * LA RECETA DEL SERVICIO VIENE PUESTA (27/11/2026). Caso: «Terapia física» gasta 20 ml de gel
     * y 1,5 de un producto dado de baja; «Masaje» gasta 2 guantes. Al cerrar una sesión de
     * Terapia física aparece solo el gel con «20»; sin servicio o sin receta, nada.
     */
    @Test
    fun laRecetaDelServicioVienePuesta() {
        val o = Json.parseToJsonElement(
            "{\"productos\":[" +
                "{\"id\":\"gel\",\"nombre\":\"Gel\",\"unidad_contenido\":\"ml\",\"estado\":\"Activo\"}," +
                "{\"id\":\"guantes\",\"nombre\":\"Guantes\",\"estado\":\"Activo\"}," +
                "{\"id\":\"viejo\",\"nombre\":\"Viejo\",\"estado\":\"Inactivo\"}]," +
                "\"recetas\":[" +
                "{\"procedimiento_id\":\"tf\",\"producto_id\":\"gel\",\"cantidad\":20}," +
                "{\"procedimiento_id\":\"tf\",\"producto_id\":\"viejo\",\"cantidad\":\"1.5\"}," +
                "{\"procedimiento_id\":\"masaje\",\"producto_id\":\"guantes\",\"cantidad\":2}," +
                "{\"procedimiento_id\":\"masaje\",\"producto_id\":\"gel\",\"cantidad\":0}," +
                "{\"producto_id\":\"gel\",\"cantidad\":5}]}",
        ).jsonObject
        val datos = InventarioCierre(parsearProductosConsumo(o), parsearRecetas(o))
        assertEquals(mapOf("gel" to "20"), recetaDeServicioApp(datos, "tf"))
        assertEquals(mapOf("guantes" to "2"), recetaDeServicioApp(datos, "masaje"))
        assertTrue(recetaDeServicioApp(datos, "otro").isEmpty())
        assertTrue(recetaDeServicioApp(datos, null).isEmpty())
        assertTrue(recetaDeServicioApp(null, "tf").isEmpty())
        // Una respuesta vieja, sin recetas: los productos salen igual y no se propone nada.
        assertTrue(parsearRecetas(Json.parseToJsonElement("{\"productos\":[]}").jsonObject).isEmpty())
        // Lo propuesto se manda tal cual al completar.
        assertEquals(listOf("gel" to 20.0), itemsDeConsumo(recetaDeServicioApp(datos, "tf")))
    }

    @Test
    fun laCantidadSeEscribeSinDecimalesDeMas() {
        assertEquals("20", cantidadEscrita(20.0))
        assertEquals("1.5", cantidadEscrita(1.5))
        assertEquals("0.25", cantidadEscrita(0.25))
    }

    @Test
    fun soloSeMandaLoQueTieneCantidad() {
        assertEquals(
            listOf("p1" to 20.0, "p2" to 1.5),
            itemsDeConsumo(linkedMapOf("p1" to "20", "p2" to " 1,5 ", "p3" to "", "p4" to "0", "p5" to "abc", "p6" to "-3")),
        )
        assertTrue(itemsDeConsumo(emptyMap()).isEmpty())
    }

    @Test
    fun elCuerpoLlevaSoloElIdDeLaAtencion() {
        val c = cuerpoConsumoSesion(null, "s1", listOf("p1" to 20.0))
        assertEquals("\"s1\"", c["sesionId"].toString())
        assertNull(c["citaId"])
        assertNull(c["terapeutaId"])   // lo deduce el servidor
        val item = (c["consumos"] as JsonArray).single().jsonObject
        assertEquals("\"p1\"", item["productoId"].toString())
        assertEquals("20.0", item["cantidad"].toString())
        assertEquals("\"c1\"", cuerpoConsumoSesion("c1", "", emptyList())["citaId"].toString())
    }
}
