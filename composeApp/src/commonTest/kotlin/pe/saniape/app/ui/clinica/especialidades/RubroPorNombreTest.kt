package pe.saniape.app.ui.clinica.especialidades

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Gemelo de lib/__tests__/rubro-especialidad.test.ts y de la parte de nombres
 * de lib/__tests__/dermatologia-medica.test.ts (web, 2026-10-15): dermatología
 * clínica es MÉDICA; la estética (y la cosmiatría) sigue en estética.
 */
class RubroPorNombreTest {

    private val casos = listOf(
        "Odontología" to "odontologia",
        "Estética Dental" to "odontologia",           // dental gana a estética
        "Ortodoncia" to "odontologia",
        "Medicina General" to "medicina_general",
        "Médico general" to "medicina_general",
        "Pediatría" to "medicina_general",
        "Fisioterapia" to "fisioterapia",
        "TERAPIA FISICA Y REHABILITACION" to "fisioterapia",
        "Masoterapia" to "fisioterapia",
        "Psicología" to "psicologia",
        "Medicina Estética" to "estetica",            // no es medicina general
        "Medicina Capilar" to "capilar",
        "Nutrición" to "nutricion",
        "Fertilidad" to "ginecologia",
        // Dermatología: médica desde 2026-10-15; la estética, en estética.
        "Dermatología" to "medicina_general",
        "DERMATOLOGIA" to "medicina_general",
        "Dermatólogo" to "medicina_general",
        "Dermatología clínica" to "medicina_general",
        "Dermatología pediátrica" to "medicina_general",
        "Dermatología estética" to "estetica",
        "Dermatologia Estetica" to "estetica",
        "Dermocosmética" to "estetica",
        "Cosmiatría" to "estetica",
        "Dermatología capilar" to "capilar",
        // Psiquiatría es médica (2026-10-09) y gana a psicología.
        "Psiquiatría" to "medicina_general",
        "PSIQUIATRIA" to "medicina_general",
        "Neuropsiquiatría" to "medicina_general",
        "Psiquiatría infantil" to "medicina_general",
        "Psiquiatría y Psicología" to "medicina_general",
        "Psicología clínica" to "psicologia",
        "Psicoterapia" to "psicologia",
    )

    @Test
    fun rubroPorElNombreComoLaWeb() {
        for ((nombre, rubro) in casos) assertEquals(rubro, rubroPorNombre(nombre), nombre)
    }

    @Test
    fun loQueElNombreNoDiceQuedaSinRubro() {
        assertNull(rubroPorNombre("Láser y Aparatología"))
        assertNull(rubroPorNombre("Podología"))
        assertNull(rubroPorNombre(""))
        assertNull(rubroPorNombre(null))
    }

    @Test
    fun esteticaVaAntesQueDermatologia() {
        val claves = PALABRAS_RUBRO.flatMap { (r, ps) -> ps.map { r to it } }
        val estetica = claves.indexOf("estetica" to "cosmiat")
        val dermat = claves.indexOf("medicina_general" to "dermat")
        assertTrue(estetica >= 0 && dermat > estetica)
        assertTrue(claves.indexOf("medicina_general" to "psiquiatr") < claves.indexOf("psicologia" to "psico"))
    }

    @Test
    fun etiquetaDeEstetica() {
        assertEquals("💆 Estética / Cosmiatría", NOMBRE_RUBRO["estetica"])
    }
}
