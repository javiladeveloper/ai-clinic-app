package pe.saniape.app.data.staff

import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ApoderadoTest {

    private val hoy = "2026-10-01"

    @Test
    fun menorPorFechaDeNacimiento() {
        assertTrue(Apoderado.esMenor("2010-05-20", null, hoy))
        // Cumple 18 mañana: todavía es menor.
        assertTrue(Apoderado.esMenor("2008-10-02", null, hoy))
        // Cumple 18 hoy: ya es mayor.
        assertFalse(Apoderado.esMenor("2008-10-01", null, hoy))
    }

    @Test
    fun laFechaMandaSobreLaEdadGuardada() {
        // Edad vieja (17) pero la fecha dice 30 años → adulto.
        assertFalse(Apoderado.esMenor("1996-01-01", 17, hoy))
        assertTrue(Apoderado.esMenor("2015-01-01", 40, hoy))
    }

    @Test
    fun sinFechaUsaLaEdad() {
        assertTrue(Apoderado.esMenor(null, 17, hoy))
        assertFalse(Apoderado.esMenor(null, 18, hoy))
        assertTrue(Apoderado.esMenor("", 5, hoy))
    }

    @Test
    fun sinDatosNoEsMenor() {
        assertFalse(Apoderado.esMenor(null, null, hoy))
    }

    @Test
    fun necesitaSiEsMenorOSiLoRequiere() {
        assertTrue(Apoderado.necesita(esMenor = true, requiereApoderado = false))
        assertTrue(Apoderado.necesita(esMenor = false, requiereApoderado = true))
        assertFalse(Apoderado.necesita(esMenor = false, requiereApoderado = false))
    }

    @Test
    fun faltaCuandoLoNecesitaYNoHayNombreODni() {
        assertTrue(Apoderado.falta(true, null, null))
        assertTrue(Apoderado.falta(true, "Ana Pérez", " "))
        assertTrue(Apoderado.falta(true, "", "12345678"))
        assertFalse(Apoderado.falta(true, "Ana Pérez", "12345678"))
        assertFalse(Apoderado.falta(false, null, null))
    }

    @Test
    fun badgeMenorOConApoderado() {
        assertEquals("Menor", Apoderado.badge(esMenor = true, requiereApoderado = true))
        assertEquals("Con apoderado", Apoderado.badge(esMenor = false, requiereApoderado = true))
        assertNull(Apoderado.badge(esMenor = false, requiereApoderado = false))
        assertEquals("Menor", Apoderado.badgeDe(null, 9, false, hoy))
        assertNull(Apoderado.badgeDe(null, null, false, hoy))
    }

    @Test
    fun parentescosDeLaWeb() {
        assertEquals(listOf("Madre", "Padre", "Tutor legal", "Abuelo(a)", "Hermano(a)", "Otro"), Apoderado.PARENTESCOS)
    }

    @Test
    fun payloadLimpiaTextosYNuncaLlevaClinica() {
        val p = Apoderado.payload(DatosApoderado(
            nombre = "  Ana Pérez ", dni = "12345678", parentesco = "Madre", telefono = "",
            requiere = false, recibeAvisos = true,
        ))
        assertEquals(JsonPrimitive("Ana Pérez"), p["apoderado_nombre"])
        assertEquals(JsonPrimitive("12345678"), p["apoderado_dni"])
        assertEquals(JsonPrimitive("Madre"), p["apoderado_parentesco"])
        assertEquals(JsonNull, p["apoderado_telefono"])
        assertEquals(JsonPrimitive(false), p["requiere_apoderado"])
        assertEquals(JsonPrimitive(true), p["apoderado_recibe_avisos"])
        assertFalse("clinica_id" in p)
    }

    @Test
    fun tieneAlgo() {
        assertFalse(DatosApoderado().tieneAlgo)
        assertTrue(DatosApoderado(nombre = "Ana").tieneAlgo)
        assertTrue(DatosApoderado(requiere = true).tieneAlgo)
    }
}
