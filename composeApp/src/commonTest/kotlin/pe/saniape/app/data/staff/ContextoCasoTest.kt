package pe.saniape.app.data.staff

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** «📋 Contexto del caso» del cierre de sesión (gemelo de PanelCasoCierre / lib/contexto-caso, web). */
class ContextoCasoTest {

    private fun obj(s: String) = Json.parseToJsonElement(s) as JsonObject

    @Test
    fun leeLaRespuestaDelServidor() {
        val c = parsearContextoCaso(obj("""
            {"ok":true,"psico":true,"contexto":{"fuente":{"titulo":"Consulta","fecha":"2026-10-12"},
             "secciones":[{"clave":"motivo","etiqueta":"Motivo","texto":"Ansiedad en el trabajo"},
                          {"clave":"desde","etiqueta":"Desde cuándo","texto":"  "},
                          {"clave":"plan","etiqueta":"Plan terapéutico","texto":"TCC 8 sesiones"}]}}
        """.trimIndent()))!!
        assertTrue(c.tieneDatos)
        // La sección vacía se salta.
        assertEquals(listOf("Motivo", "Plan terapéutico"), c.secciones.map { it.etiqueta })
        assertEquals("Consulta · 12/10", c.detalleFuente())
    }

    @Test
    fun sinFuenteNiSecciones() {
        val vacio = parsearContextoCaso(obj("""{"ok":true,"psico":false,"contexto":{"fuente":null,"secciones":[]}}"""))!!
        assertFalse(vacio.tieneDatos)
        assertNull(vacio.detalleFuente())
        // Respuesta sin contexto (servidor viejo / error): nada.
        assertNull(parsearContextoCaso(obj("""{"error":"x"}""")))
        assertNull(parsearContextoCaso(null))
    }

    @Test
    fun abiertoEnLaPrimeraSesion() {
        assertTrue(contextoAbiertoInicial(1, hayAnterior = false))
        assertFalse(contextoAbiertoInicial(3, hayAnterior = true))
        // Sin número: abierto solo si no hay sesión anterior.
        assertTrue(contextoAbiertoInicial(null, hayAnterior = false))
        assertFalse(contextoAbiertoInicial(null, hayAnterior = true))
        // La anterior: abierta solo en fisioterapia (DALU como antes).
        assertTrue(anteriorAbiertaInicial(esFisio = true))
        assertFalse(anteriorAbiertaInicial(esFisio = false))
    }

    @Test
    fun textoLargoSeRecorta() {
        assertFalse(esTextoLargo("corto"))
        assertTrue(esTextoLargo("x".repeat(181)))
        assertTrue(esTextoLargo("a\nb\nc\nd\ne"))
        assertFalse(esTextoLargo("a\nb\nc\nd"))
    }

    @Test
    fun evolucionPsicoPorCampos() {
        val nota = textoEvolucionPsico(EvolucionSesionPsico(evolucion = "Duelo", tareas = "Diario", plan = "Exposición"))
        assertEquals(
            listOf("Temas trabajados y evolución" to "Duelo", "Tareas e indicaciones" to "Diario",
                "Plan para la próxima sesión" to "Exposición"),
            camposEvolucionPsicoAnterior(nota),
        )
        // Nota libre: entera como "Temas trabajados y evolución".
        assertEquals(listOf("Temas trabajados y evolución" to "Habló de su madre"), camposEvolucionPsicoAnterior("Habló de su madre"))
        assertTrue(camposEvolucionPsicoAnterior(null).isEmpty())
        assertTrue(camposEvolucionPsicoAnterior("  ").isEmpty())
    }
}
