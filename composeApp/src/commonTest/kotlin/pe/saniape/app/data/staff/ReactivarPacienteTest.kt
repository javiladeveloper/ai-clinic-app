package pe.saniape.app.data.staff

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Paciente dado de baja que vuelve: lo que la app lee de /api/staff/paciente/de-baja. */
class ReactivarPacienteTest {

    private fun obj(s: String) = Json.parseToJsonElement(s) as JsonObject

    @Test
    fun parseaLaFichaDeBajaConSusDatosParaPrecargar() {
        val f = parsearFichaDeBaja(obj("""
            {"id":"p1","nombre":"Rosa Quispe","dni":"44556677","telefono":"999888777","email":null,
             "edad":52,"ocupacion":"Docente","diagnostico":"Lumbalgia","observaciones":"",
             "patologias":["Dolor lumbar"," "],"talla":158.0,"peso":61.5,"flag":"verde",
             "n_tratamientos":2,"fecha_baja_texto":"12/05/2026","estado":"Inactivo"}
        """.trimIndent()))
        assertNotNull(f)
        assertEquals("Rosa Quispe", f.nombre)
        assertEquals("44556677", f.dni)
        assertNull(f.email)                      // null del JSON no es el texto "null"
        assertNull(f.observaciones)              // vacío = no hay dato
        assertEquals(52, f.edad)
        assertEquals(158, f.talla)
        assertEquals(61.5, f.peso)
        assertEquals(listOf("Dolor lumbar"), f.patologias)
        assertEquals(2, f.nTratamientos)
        assertEquals("Rosa Quispe estaba dado de baja el 12/05/2026 · ¿Reactivar su ficha?", textoFichaDeBaja(f))
    }

    @Test
    fun sinIdNoHayFicha() {
        assertNull(parsearFichaDeBaja(obj("""{"nombre":"X"}""")))
    }

    @Test
    fun sinFechaDeBajaElTextoNoInventaUna() {
        val f = parsearFichaDeBaja(obj("""{"id":"p1","nombre":"Luis","n_tratamientos":0}"""))!!
        assertEquals("Luis estaba dado de baja · ¿Reactivar su ficha?", textoFichaDeBaja(f))
    }

    @Test
    fun documentoCompletoSegunPais() {
        assertTrue(documentoCompleto("44556677", "PE"))
        assertFalse(documentoCompleto("4455667", "PE"))
        assertFalse(documentoCompleto("4455667a", "PE"))
        assertTrue(documentoCompleto("12345678-9", "CL"))   // RUT chileno
        assertFalse(documentoCompleto("12-3", "CL"))
    }
}
