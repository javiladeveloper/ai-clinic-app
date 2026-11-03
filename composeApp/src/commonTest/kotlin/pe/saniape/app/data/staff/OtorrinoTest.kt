package pe.saniape.app.data.staff

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Gemelo de lib/__tests__/otorrino-rubro.test.ts y otorrino-video-fixes.test.ts
 * (web, 2026-11-02/03): clínica Odontología + Otorrino.
 */
class OtorrinoTest {
    private val orl = EspecialidadNombre("orl", "Otorrinolaringología", rubro = "medicina_general")
    private val odo = EspecialidadNombre("odo", "Odontología", rubro = "odontologia")
    private val oft = EspecialidadNombre("oft", "Oftalmología", rubro = "medicina_general")

    @Test fun seReconocePorElNombre() {
        assertTrue(esOtorrino("Otorrinolaringología"))
        assertTrue(esOtorrino("OTORRINO"))
        assertTrue(esOtorrino("Otolaringología"))
        assertTrue(esOtorrino("Audiología"))
        assertFalse(esOtorrino("Odontología"))
        assertFalse(esOtorrino("Medicina General"))
        assertFalse(esOtorrino(null))
        assertEquals("medicina_general", rubroPorNombre("Otorrinolaringología"))
        assertEquals("medicina_general", rubroPorNombre("Audiología"))
    }

    @Test fun laAtencionEsDeOtorrinoPorLaEspecialidadDeLaCita() {
        val esps = listOf(orl, odo)
        assertTrue(atencionEsOtorrino(esps, "orl"))
        assertFalse(atencionEsOtorrino(esps, "odo"))
        // Sin especialidad: solo si TODAS las activas lo son (clínica mixta: no).
        assertFalse(atencionEsOtorrino(esps, null))
        assertTrue(atencionEsOtorrino(listOf(orl), null))
        val v = variantesAtencion(esps, FlagsConsulta(especialidadId = "orl"), psiq = false)
        assertTrue(v.otorrino)
        assertFalse(v.oftalmo)
        // Oftalmología gana (como la web: `!oftalmo && atencionEsOtorrino`).
        assertFalse(variantesAtencion(listOf(oft), FlagsConsulta(especialidadId = "oft"), psiq = false).otorrino)
        // Lo dental manda antes.
        assertFalse(variantesAtencion(esps, FlagsConsulta(dental = true, especialidadId = "orl"), psiq = false).otorrino)
    }

    @Test fun cie10FrecuentesYEjemplo() {
        val f = frecuentesCie10Locales(dental = false, psiq = false, derma = false, otorrino = true)!!
        assertEquals(27, f.size)
        assertEquals("H61.2" to "Cerumen impactado", f.first().codigo to f.first().descripcion)
        assertTrue(f.any { it.codigo == "H93.1" && it.descripcion == "Tinnitus" })
        assertTrue(f.any { it.codigo == "T16.X" })
        // Oftalmología y dermatología mandan antes (frecuentesCie10 de la web).
        assertEquals("H52.1", frecuentesCie10Locales(false, false, false, oftalmo = true, otorrino = true)!!.first().codigo)
        assertNull(frecuentesCie10Locales(dental = true, psiq = false, derma = false, otorrino = true))
        assertEquals("H61.2" to "cerumen impactado", ejemploCie10(otorrino = true))
        assertEquals("J06.9" to "faringitis aguda", ejemploCie10())
    }

    @Test fun examenesFrecuentes() {
        assertTrue("Audiometría tonal" in EXAMENES_FRECUENTES_OTORRINO)
        assertTrue("Nasofibroscopía" in EXAMENES_FRECUENTES_OTORRINO)
        assertEquals(13, EXAMENES_FRECUENTES_OTORRINO.size)
    }

    @Test fun laBibliotecaDeMedicinaNoEsDelOido() {
        assertNull(rubroBibliotecaConsentimiento("Otorrinolaringología", "medicina_general"))
        assertEquals("medicina_general", rubroBibliotecaConsentimiento("Medicina General", "medicina_general"))
    }

    @Test fun flujoSinPresetPropioComoMedicinaGeneral() {
        val dental = FlujoClinica(usaConsulta = false, usaEvaluacion = true)
        val orlFlujo = dental.paraEspecialidad(null, "Otorrinolaringología")
        assertEquals(FLUJO_MEDICINA_GENERAL, orlFlujo)
        assertFalse(orlFlujo.pasaAEvaluacion("Consulta"))
        // Las demás sin preset siguen heredando el de la clínica.
        assertEquals(dental, dental.paraEspecialidad(null, "Odontología"))
    }
}
