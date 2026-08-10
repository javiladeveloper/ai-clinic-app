package pe.saniape.app.ui

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * A quién saluda la app.
 *
 * Las clínicas guardan a su gente con título ("Lic. Fisio Prueba", "Dra. Ana
 * Quispe"), y quedarse con la primera palabra saludaba "Hola, Lic. 👋" — que no
 * es el nombre de nadie. Le pasaba a casi todos los profesionales, porque casi
 * todos se registran así (reportado al probar en la app, 2026-08-10).
 */
class NombreDeSaludoTest {

    @Test
    fun salta_el_titulo_profesional() {
        assertEquals("Fisio", nombreDeSaludo("Lic. Fisio Prueba"))
        assertEquals("Ana", nombreDeSaludo("Dra. Ana Quispe"))
        assertEquals("Carlos", nombreDeSaludo("Dr. Carlos Núñez"))
    }

    @Test
    fun acepta_el_titulo_sin_punto() {
        // Se escribe de las dos formas y ninguna puede romper el saludo.
        assertEquals("Ana", nombreDeSaludo("Dra Ana Quispe"))
        assertEquals("Fisio", nombreDeSaludo("Lic Fisio Prueba"))
    }

    @Test
    fun sin_titulo_devuelve_el_primer_nombre() {
        assertEquals("Ana", nombreDeSaludo("Ana Quispe Rojas"))
        assertEquals("Jonathan", nombreDeSaludo("Jonathan"))
    }

    @Test
    fun no_confunde_un_nombre_que_empieza_parecido() {
        // "Drago" empieza con "Dr" pero NO es un título: cortarlo dejaría a la
        // persona sin nombre.
        assertEquals("Drago", nombreDeSaludo("Drago Pérez"))
        assertEquals("Licia", nombreDeSaludo("Licia Fernández"))
    }

    @Test
    fun solo_titulo_devuelve_null() {
        // Alguien guardado solo como "Dr." no tiene nombre que decir: quien llama
        // saluda sin él ("Hola 👋"), mejor que saludar a una abreviatura.
        assertNull(nombreDeSaludo("Dr."))
        assertNull(nombreDeSaludo("Lic."))
    }

    @Test
    fun vacio_o_nulo_devuelve_null() {
        assertNull(nombreDeSaludo(null))
        assertNull(nombreDeSaludo(""))
        assertNull(nombreDeSaludo("   "))
    }

    @Test
    fun aguanta_espacios_de_mas() {
        // Los nombres se escriben a mano en el panel: doble espacio es común.
        assertEquals("Ana", nombreDeSaludo("  Dra.   Ana  Quispe "))
    }
}
