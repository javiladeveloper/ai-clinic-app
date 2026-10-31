package pe.saniape.app.data.staff

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/** Gemelo de lib/__tests__/oftalmologia.test.ts (web, 2026-10-30). */
class OftalmologiaTest {

    @Test fun rangosYPasos() {
        assertEquals(-1.25, valorEnRango("-1,25", RANGOS.esf))
        assertEquals(2.0, valorEnRango("+2", RANGOS.esf))
        assertEquals(2.25, valorEnRango(2.2, RANGOS.esf))
        assertNull(valorEnRango("26", RANGOS.esf))
        assertNull(valorEnRango("0.5", RANGOS.cil)) // el cilindro es negativo
        assertEquals(90.0, valorEnRango("92", RANGOS.eje))
        assertNull(valorEnRango("300", RANGOS.eje))
        assertNull(valorEnRango("abc", RANGOS.pio))
        assertNull(valorEnRango("", RANGOS.pio))
        assertEquals(0.25, pasoSiguiente(null, RANGOS.add, 1)) // ADD arranca en su mínimo
        assertEquals(0.0, pasoSiguiente(null, RANGOS.esf, 1))
        assertEquals(-0.25, pasoSiguiente(0.0, RANGOS.esf, -1))
        assertEquals(0.0, pasoSiguiente(0.0, RANGOS.cil, 1)) // no sale del rango
        assertEquals(180.0, pasoSiguiente(180.0, RANGOS.eje, 1))
    }

    @Test fun dioptriasComoEnLaReceta() {
        assertEquals("+0.00", dioptria(0.0))
        assertEquals("-0.50", dioptria(-0.5))
        assertEquals("+2.25", dioptria(2.25))
        assertEquals("", dioptria(null))
        assertEquals("62", mostrarNum(62.0, RANGOS.dip, false))
    }

    @Test fun agudezaVisual() {
        assertEquals("20/20-2", normalizarAV(" 20/20 -2 "))
        assertEquals("CD", normalizarAV("cd"))
        assertNull(normalizarAV("20/35"))
        assertNull(normalizarAV("20/20-5"))
        assertEquals("20/40" to "+1", partesAV("20/40+1"))
        assertEquals("NPL" to "", partesAV("NPL"))
        assertEquals("" to "", partesAV(null))
    }

    @Test fun recetaDeLaSubjetivaConDip() {
        val e = examenVacio().copy(subjetiva = SubjetivaOftalmo(
            od = RefraccionAv(-1.25, -0.5, 90.0), oi = RefraccionAv(-1.0), addCerca = 1.5, dipLejos = 62.0, dipCerca = 60.0,
        ))
        val r = assertNotNull(recetaDeLentes(e))
        assertEquals("62/60", r.dip)
        assertEquals(1.5, r.addCerca)
        assertNull(recetaDeLentes(examenVacio()))
    }

    @Test fun avisosDeEjeYDip() {
        val e = examenVacio().copy(subjetiva = SubjetivaOftalmo(od = RefraccionAv(-1.0, -0.75, null), dipLejos = 62.0))
        val a = avisosExamenOftalmo(e)
        assertTrue(a.any { it.contains("falta el eje") })
        assertTrue(a.any { it.startsWith("DIP") })
    }

    @Test fun idaYVueltaJsonYVacio() {
        assertNull(examenOftalmoDesdeJson(null))
        assertNull(examenOftalmoDesdeJson(examenOftalmoAJson(examenVacio()))) // sin nada anotado = null
        val e = examenVacio().copy(pio = PioOftalmo(od = 18.0, oi = 30.0, metodo = "Goldmann", hora = "10:30"))
        val json = examenOftalmoAJson(e)
        assertEquals("Goldmann", json.jsonObject["pio"]!!.jsonObject["metodo"]!!.jsonPrimitive.content)
        assertEquals(e.pio, examenOftalmoDesdeJson(json)!!.pio)
        // Lo que viene de la web (números enteros, claves de más, hora inválida) también se lee.
        val web = Json.parseToJsonElement("{\"v\":1,\"pio\":{\"od\":25,\"oi\":null,\"metodo\":null,\"hora\":\"99:00\"},\"otro\":1}")
        val leido = assertNotNull(examenOftalmoDesdeJson(web))
        assertEquals(25.0, leido.pio.od)
        assertNull(leido.pio.hora)
    }

    @Test fun normalSoloLlenaLoVacio() {
        val bio = examenVacio().bio + ("cristalino" to OjosTxt(od = "Opacidad nuclear"))
        val r = normalEnMapa(bio, NORMAL_BIO, "od")
        assertEquals("Opacidad nuclear", r["cristalino"]!!.od)
        assertEquals("Tranquila", r["conjuntiva"]!!.od)
        assertNull(r["conjuntiva"]!!.oi)
    }

    @Test fun sugerenciasPorRefraccionPresbiciaYGlaucoma() {
        val e = examenVacio().copy(
            subjetiva = SubjetivaOftalmo(od = RefraccionAv(-1.25, -0.75, 180.0), oi = RefraccionAv(1.5), addCerca = 1.5),
            pio = PioOftalmo(od = 24.0),
        )
        val cods = sugerirDiagnosticos(e, 52).map { it.codigo }
        assertTrue("H52.1" in cods) // OD EE −1.625
        assertTrue("H52.2" in cods)
        assertTrue("H52.0" in cods) // OI +1.50
        assertTrue("H52.3" in cods) // diferencia ≥ 2 D
        assertTrue("H52.4" in cods)
        assertTrue("H40.0" in cods)
        // Presbicia no a los 20 años.
        assertFalse("H52.4" in sugerirDiagnosticos(e, 20).map { it.codigo })
    }

    @Test fun sugerenciasPorTextoSinNegacion() {
        val e = examenVacio().copy(
            bio = examenVacio().bio + ("cristalino" to OjosTxt(od = "Opacidad nuclear 2+", oi = "Sin opacidades")),
            anexos = AnexosOftalmo(but = OjosNum(od = 6.0)),
            fondo = examenVacio().fondo + ("papila" to OjosTxt(oi = "E/P 0.7")),
        )
        val s = sugerirDiagnosticos(e)
        assertEquals("OD: según la exploración", s.first { it.codigo == "H25.9" }.motivo)
        assertTrue(s.any { it.codigo == "H04.1" })
        assertTrue(s.first { it.codigo == "H40.0" }.motivo.contains("E/P 0.7"))
        assertFalse(mencionaHallazgo("no hay secreción", Regex("secreci")))
        assertTrue(mencionaHallazgo("hiperemia leve", Regex("hiperemi")))
    }

    @Test fun textoParaLaHistoria() {
        val e = examenVacio().copy(
            pio = PioOftalmo(od = 15.0, oi = 16.0),
            subjetiva = SubjetivaOftalmo(od = RefraccionAv(-1.0, -0.5, 90.0), dipLejos = 62.0, dipCerca = 60.0),
        )
        val t = textoExamenOftalmo(e)
        assertTrue(t.contains("PIO: OD 15 mmHg · OI 16 mmHg"), t)
        assertTrue(t.contains("Refracción subjetiva: OD -1.00 -0.50 x 90° · DIP 62/60"), t)
    }

    @Test fun pasosSinVitales() {
        val pasos = listOf(PasoAtencion("motivo", "Motivo"), PasoAtencion("vitales", "Funciones vitales"), PasoAtencion("examen", "Examen físico"))
        val r = pe.saniape.app.ui.clinica.atencion.pasosOftalmologia(pasos)
        assertEquals(listOf("motivo", "examen"), r.map { it.clave })
        assertEquals("Examen oftalmológico", r.last().titulo)
    }

    @Test fun deteccionYListas() {
        assertTrue(esOftalmologia("Oftalmología"))
        assertTrue(esOftalmologia("OPTOMETRÍA"))
        assertFalse(esOftalmologia("Medicina General"))
        assertEquals("medicina_general", rubroPorNombre("Oftalmología"))
        assertEquals("medicina_general", rubroPorNombre("Optometría"))
        val esps = listOf(EspecialidadNombre("o", "Oftalmología", rubro = "medicina_general"), EspecialidadNombre("m", "Medicina General"))
        assertTrue(atencionEsOftalmologia(esps, "o"))
        assertFalse(atencionEsOftalmologia(esps, "m"))
        assertEquals("H52.1", frecuentesCie10Locales(dental = false, psiq = false, derma = false, oftalmo = true)!!.first().codigo)
        assertNull(frecuentesCie10Locales(dental = true, psiq = false, derma = false, oftalmo = true))
        assertEquals("H52.4" to "presbicia", ejemploCie10(oftalmo = true))
        assertNull(rubroBibliotecaConsentimiento("Oftalmología", "medicina_general"))
    }
}
