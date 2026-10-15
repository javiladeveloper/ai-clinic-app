package pe.saniape.app.data.staff

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Gemelo de lib/__tests__/consentimiento-acceso.test.ts (web, 2026-10-15). El
 * `mapaMedico` lo resuelve el servidor (resolverModulosClinicos); acá se pone
 * el que la web obtiene para cada caso.
 */
class ConsentimientoAccesoTest {

    private fun esp(id: String, nombre: String, rubro: String?) = EspecialidadNombre(id, nombre, "Activa", rubro)
    private val vacio = MapaClinico()

    @Test fun esteticaCapilarYPodologiaSolasEntran() {
        for (esps in listOf(
            listOf(esp("e", "Estética", "estetica")),
            // GLAM
            listOf(esp("e", "Estética", "estetica"), esp("c", "Medicina Capilar", "capilar"), esp("d", "Dermatología", "estetica")),
            // PodoBlack
            listOf(esp("p", "Podología", null)),
        )) {
            assertFalse(clinicaConConsentimiento(vacio), "no es médica")
            val m = mapaConsentimiento(vacio, esps)
            assertTrue(m.solo, esps.toString())
            assertEquals(AccesoConsentimiento(historiaMedica = false, consentimiento = true), accesoConsentimiento(vacio, esps))
        }
    }

    @Test fun lasMedicasExactamenteLoDeSiempre() {
        val esps = listOf(esp("m", "Medicina General", "medicina_general"), esp("o", "Odontología", "odontologia"))
        val medico = MapaClinico(listOf("m", "o"), solo = true)
        assertEquals(medico, mapaConsentimiento(medico, esps))
        // Medicina + fisio: la de fisio sigue fuera.
        val mixta = listOf(esp("m", "Medicina General", "medicina_general"), esp("f", "Fisioterapia", "fisioterapia"))
        val mm = MapaClinico(listOf("m"), solo = false)
        assertEquals(mm, mapaConsentimiento(mm, mixta))
    }

    @Test fun medicaConElFlujoMedicoApagadoNoGanaPorLaBiblioteca() {
        // RENOVA: estética + medicina general, sin flujo médico → solo la de estética.
        val esps = listOf(esp("e", "Estética", "estetica"), esp("m", "Medicina General", "medicina_general"))
        assertEquals(MapaClinico(listOf("e"), solo = false), mapaConsentimiento(vacio, esps))
    }

    @Test fun mixtaMedicaMasPodologiaSumaPodologia() {
        val esps = listOf(esp("m", "Medicina General", "medicina_general"), esp("p", "Podología", null))
        val medico = MapaClinico(listOf("m"), solo = false)
        val m = mapaConsentimiento(medico, esps)
        assertEquals(listOf("m", "p"), m.ids.sorted())
        assertEquals(listOf("m"), medico.ids)
    }

    @Test fun fisioPsicologiaONutricionSolasNada() {
        for (esps in listOf(
            listOf(esp("f", "Fisioterapia", "fisioterapia")),
            listOf(esp("p", "Psicología", "psicologia")),
            listOf(esp("n", "Nutrición", "nutricion")),
            emptyList(),
        )) {
            assertFalse(clinicaConConsentimiento(mapaConsentimiento(vacio, esps)), esps.toString())
            assertFalse(accesoConsentimiento(vacio, esps).consentimiento)
        }
    }

    @Test fun psiquiatriaSolaSigueEntrandoPorSerMedica() {
        val esps = listOf(esp("q", "Psiquiatría", "medicina_general"))
        val medico = MapaClinico(listOf("q"), solo = true)
        assertEquals(AccesoConsentimiento(historiaMedica = true, consentimiento = true), accesoConsentimiento(medico, esps))
    }

    @Test fun rubroDeBiblioteca() {
        assertNull(rubroBibliotecaConsentimiento("Psiquiatría", "medicina_general"))
        assertEquals("podologia", rubroBibliotecaConsentimiento("Podología", null))
        assertEquals("dermatologia", rubroBibliotecaConsentimiento("Dermatología", "medicina_general"))
        assertEquals("estetica", rubroBibliotecaConsentimiento("Dermatología", "estetica"))
        assertEquals("estetica", rubroBibliotecaConsentimiento("Cosmiatría", null))
        assertFalse(tieneBibliotecaConsentimiento("Fisioterapia", "fisioterapia"))
        assertFalse(tieneBibliotecaConsentimiento("Láser y Aparatología", null))
        assertTrue(tieneBibliotecaConsentimiento("Medicina Capilar", "capilar"))
    }
}
