package pe.saniape.app.ui.clinica.atencion

import pe.saniape.app.data.staff.CitaConsultaApp
import pe.saniape.app.data.staff.DatosConsultaApp
import pe.saniape.app.data.staff.FlagsConsulta
import pe.saniape.app.data.staff.FraseFrecuenteApp
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Gemelo de lib/__tests__/frases-completas.test.ts y frases-dermatologia.test.ts
 * (web, 2026-10-16): tocar "Granitos" marcaba también "Tos" porque se buscaba
 * como subcadena (grani-TOS). La frase cuenta solo entera.
 */
class FrasesCompletasTest {

    private fun coma(texto: String, chip: String) = quitarFrase(texto, chip, porLinea = false)
    private fun linea(texto: String, chip: String) = quitarFrase(texto, chip, porLinea = true)
    private fun lineas(vararg xs: String) = xs.joinToString("\n")

    // ── frase completa (chips que no se marcan solos) ──

    @Test fun granitosNoContieneTos() {
        assertFalse(fraseUsada("Granitos", "Tos"))
        assertFalse(fraseUsada("Granitos en la cara", "Tos"))
        assertTrue(fraseUsada("Granitos", "Granitos"))
    }

    @Test fun dolorYDolorLumbarSonDistintos() {
        assertFalse(fraseUsada("Dolor lumbar", "Dolor"))
        assertFalse(fraseUsada("Dolor", "Dolor lumbar"))
        assertTrue(fraseUsada("Dolor lumbar, dolor", "Dolor"))
        assertTrue(fraseUsada("Fiebre, dolor de cabeza", "Dolor de cabeza"))
    }

    @Test fun separadoresBasicos() {
        assertTrue(contieneFraseCompleta("Fiebre, tos", "Tos"))
        assertTrue(contieneFraseCompleta("Fiebre; tos; diarrea", "Tos"))
        assertTrue(contieneFraseCompleta("Fiebre\nTos\nDiarrea", "Tos"))
        assertTrue(contieneFraseCompleta("Tiene fiebre. Tos.", "Tos"))
        assertTrue(contieneFraseCompleta("Motivo: tos", "Tos"))
        assertTrue(contieneFraseCompleta("Fiebre,tos", "Tos"))
    }

    @Test fun sinDistinguirMayusculasNiTildes() {
        assertTrue(contieneFraseCompleta("fiebre, congestion nasal", "Congestión nasal"))
        assertTrue(contieneFraseCompleta("PICAZÓN", "Picazón"))
    }

    @Test fun fraseConComasPropiasCuentaEntera() {
        val l = "Abdomen blando, depresible, no doloroso a la palpación"
        assertTrue(fraseUsada("Orofaringe sin alteraciones\n$l.", l))
        assertFalse(fraseUsada("Abdomen blando", l))
    }

    @Test fun insertarNoSeSaltaUnChipDentroDeOtraPalabra() {
        assertEquals("Granitos, tos", insertarFrase("Granitos", "Tos", porLinea = false))
    }

    @Test fun quitarTosNuncaCortaGranitos() {
        assertEquals("Granitos", coma("Granitos", "Tos"))
        assertEquals("Granitos", coma("Granitos, tos", "Tos"))
        assertEquals("Dolor lumbar", coma("Dolor lumbar, dolor", "Dolor"))
    }

    // ── conectores ──

    @Test fun conectoresTambienSeparan() {
        listOf(
            "Fiebre y tos" to "Tos", "Fiebre y tos" to "Fiebre", "Fiebre o tos" to "Tos", "Fiebre / tos" to "Tos",
            "Fiebre/tos" to "Tos", "Fiebre - tos" to "Tos", "Tos: 3 días" to "Tos", "Motivo: tos" to "Tos", "Fiebre (tos)" to "Tos",
        ).forEach { (texto, chip) -> assertTrue(fraseUsada(texto, chip), "\"$texto\" marca \"$chip\"") }
    }

    @Test fun loQueNoDebeMarcarseSigueSinMarcarse() {
        assertFalse(fraseUsada("Granitos", "Tos"))
        assertFalse(fraseUsada("Granitos y manchas", "Tos"))
        assertFalse(fraseUsada("Dolor lumbar", "Dolor"))
        assertFalse(fraseUsada("Dolor lumbar crónico", "Dolor lumbar"))
        assertFalse(fraseUsada("Dolor lumbar crónico", "Dolor"))
        assertFalse(fraseUsada("Fiebre de 38.5", "Tos"))
    }

    @Test fun frasesConSusPropiosConectores() {
        assertTrue(fraseUsada("Fiebre, náuseas y vómitos", "Náuseas y vómitos"))
        assertTrue(fraseUsada("Tristeza / ánimo bajo", "Tristeza / ánimo bajo"))
        assertFalse(fraseUsada("Tristeza", "Tristeza / ánimo bajo"))
    }

    // ── quitar deja el texto limpio ──

    @Test fun quitarDejaElTextoLimpio() {
        listOf(
            Triple("Fiebre, tos.", "Tos", "Fiebre."),
            Triple("Tiene fiebre. Tos.", "Tos", "Tiene fiebre."),
            Triple("Motivo: tos, fiebre", "Tos", "Motivo: fiebre"),
            Triple("Fiebre (tos)", "Tos", "Fiebre"),
            Triple("Fiebre (tos), diarrea", "Tos", "Fiebre, diarrea"),
            Triple("Fiebre y tos", "Tos", "Fiebre"),
            Triple("Fiebre y tos", "Fiebre", "Tos"),
            Triple("Fiebre / tos", "Tos", "Fiebre"),
            Triple("Tos.", "Tos", ""),
            Triple("Fiebre, tos, diarrea", "Tos", "Fiebre, diarrea"),
        ).forEach { (texto, chip, esperado) -> assertEquals(esperado, coma(texto, chip), "\"$texto\" sin \"$chip\"") }
    }

    @Test fun mismaNormalizacionQueElMarcado() {
        assertTrue(fraseUsada("Fiebre, Congestion nasal", "Congestión nasal"))
        assertEquals("Fiebre", coma("Fiebre, Congestion nasal", "Congestión nasal"))
        assertTrue(fraseUsada("Dolor  de cabeza", "Dolor de cabeza"))
        assertEquals("Fiebre", coma("Dolor  de cabeza, fiebre", "Dolor de cabeza"))
    }

    @Test fun quitarEnModoLinea() {
        assertEquals(lineas("Línea A", "Línea B"), linea(lineas("Línea A", "Sin edemas", "Línea B"), "Sin edemas"))
        assertEquals("Línea A", linea(lineas("Línea A", "Sin edemas"), "Sin edemas"))
    }

    // ── frases con paréntesis propios ──

    private val CRED = "Control de niño sano (CRED)"
    /** SIGNOS_ALARMA_MEDICINA de la web (termina en ")"). */
    private val ALARMA = "Acudir por emergencia si presenta signos de alarma: fiebre mayor de 39 °C que no cede, dificultad para respirar, dolor de pecho, vómitos persistentes, somnolencia o confusión, sangrado, deshidratación (boca seca, orina escasa)"
    private val XEROSIS = "Piel seca (xerosis)"
    private val ABCDE = FRASES_BASE_DERMA.getValue("examen").first { it.etiqueta == "Lunar con criterios ABCDE" }.texto

    @Test fun conParentesisSeSiguenMarcando() {
        assertTrue(ALARMA.endsWith(")"))
        assertTrue(fraseUsada("Fiebre, $CRED", CRED))
        assertTrue(fraseUsada(lineas("Reposo", ALARMA), ALARMA))
        assertTrue(fraseUsada("$XEROSIS, hiperhidrosis", XEROSIS))
        assertTrue(fraseUsada(lineas("Piel normal", ABCDE, "Otra"), ABCDE))
    }

    @Test fun conParentesisSeQuitanEnComa() {
        listOf(
            Triple("$CRED, fiebre", CRED, "Fiebre"),
            Triple("Fiebre, $CRED, tos", CRED, "Fiebre, tos"),
            Triple("Fiebre, $CRED", CRED, "Fiebre"),
            Triple("$XEROSIS, hiperhidrosis", XEROSIS, "Hiperhidrosis"),
            Triple("Hiperhidrosis, $XEROSIS, uñas en curva", XEROSIS, "Hiperhidrosis, uñas en curva"),
            Triple("Hiperhidrosis, $XEROSIS", XEROSIS, "Hiperhidrosis"),
        ).forEach { (texto, chip, esperado) -> assertEquals(esperado, coma(texto, chip), texto) }
    }

    @Test fun conParentesisSeQuitanEnLinea() {
        assertEquals(lineas("Piel normal", "Otra"), linea(lineas(ABCDE, "Piel normal", "Otra"), ABCDE))
        assertEquals(lineas("Piel normal", "Otra"), linea(lineas("Piel normal", ABCDE, "Otra"), ABCDE))
        assertEquals("Piel normal", linea(lineas("Piel normal", ABCDE), ABCDE))
        assertEquals(lineas("Reposo", "Control en 48–72 horas"), linea(lineas("Reposo", ALARMA, "Control en 48–72 horas"), ALARMA))
        assertEquals("Reposo", linea(lineas(ALARMA, "Reposo"), ALARMA))
        assertEquals("Reposo", linea(lineas("Reposo", ALARMA), ALARMA))
    }

    @Test fun modoLineaSeVaLaLineaEntera() {
        assertEquals(lineas("A.", "B"), linea(lineas("A.", "Sin edemas.", "B"), "Sin edemas"))
        assertEquals("A.", linea(lineas("A.", "Sin edemas."), "Sin edemas"))
        assertEquals("B", linea(lineas("Sin edemas.", "B"), "Sin edemas"))
        assertEquals(lineas("A", "B"), linea(lineas("A", "Sin edemas", "B"), "Sin edemas"))
    }

    // ── listas con viñetas o números ──

    @Test fun marcadorDeListaNoImpideMarcar() {
        assertTrue(fraseUsada("- Fiebre\n- Tos\n- Diarrea", "Tos"))
        assertTrue(fraseUsada("• Tos", "Tos"))
        assertTrue(fraseUsada("1. Fiebre\n2) Tos", "Tos"))
        assertFalse(fraseUsada("- Granitos", "Tos"))
    }

    @Test fun quitarSeLlevaLaLineaConSuMarcador() {
        assertEquals("- Fiebre\n- Diarrea", coma("- Fiebre\n- Tos\n- Diarrea", "Tos"))
        assertEquals("1. Fiebre\n3. Diarrea", linea("1. Fiebre\n2. Tos\n3. Diarrea", "Tos"))
        assertEquals("1. Fiebre", linea("1. Fiebre\n2. Tos", "Tos"))
    }

    // ── dermatología (frases-dermatologia.test.ts) ──

    @Test fun baseDeDermatologia() {
        val d = FRASES_BASE_DERMA
        val motivos = d.getValue("motivo").map { it.texto }
        assertTrue(motivos.containsAll(listOf("Granitos / acné", "Manchas en la piel", "Picazón", "Caída de cabello", "Lunar que cambia", "Verrugas", "Uñas")))
        assertFalse("Tos" in motivos)
        assertFalse("Fiebre" in motivos)
        val examen = d.getValue("examen")
        assertEquals("Examen normal", examen[0].etiqueta)
        assertTrue(examen[0].texto.split('\n').size > 3)
        assertTrue(examen.map { it.etiqueta }.containsAll(listOf("Acné inflamatorio", "Rosácea", "Lunar con criterios ABCDE")))
        assertTrue(d.getValue("indicaciones").map { it.etiqueta }.containsAll(listOf("Protector solar", "Signos de alarma (piel)")))
    }

    @Test fun dermaSinChipsRepetidos() {
        for ((campo, chips) in FRASES_BASE_DERMA) {
            assertEquals(chips.size, chips.map { it.etiqueta }.toSet().size, campo)
            assertEquals(chips.size, chips.map { it.texto }.toSet().size, campo)
        }
    }

    private fun datos(frases: List<FraseFrecuenteApp>, dental: Boolean = false) =
        DatosConsultaApp(cita = CitaConsultaApp(id = "c1"), flags = FlagsConsulta(dental = dental), frases = frases)

    @Test fun dermaGanaAPediatriaYAprendeEnLasClavesDeMedicina() {
        val d = datos(listOf(FraseFrecuenteApp("motivo", "Granitos", 3)))
        val r = frasesDe(d, "motivo", pediatria = true, derma = true).map { it.texto }
        assertTrue("Granitos / acné" in r)
        assertTrue("Granitos" in r)
        assertFalse("Vacunas" in r)
        // Dental manda: sin base de dermatología.
        assertFalse("Granitos / acné" in frasesDe(datos(emptyList(), dental = true), "motivo", derma = true).map { it.texto })
        // Sin variante: solo lo aprendido.
        assertEquals(listOf("Granitos"), frasesDe(d, "motivo").map { it.texto })
    }
}
