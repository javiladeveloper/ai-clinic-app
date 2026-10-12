package pe.saniape.app.data.staff

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject

/**
 * Seis detalles que se vieron grabando el tutorial de psiquiatría (demo
 * Equilibrio, 2026-10-12). Gemelo de lib/__tests__/psiquiatria-detalles.test.ts
 * de la web. DALU (fisio) y los demás rubros tienen su caso de "sin cambios".
 */
class PsiquiatriaDetallesTest {

    // ── 1. Paso 3 de la consulta guiada ──
    @Test
    fun pasoExamenEnPsiquiatria() {
        assertEquals("Examen mental y físico", pasosDeAtencion(false, false, psiq = true).first { it.clave == "examen" }.titulo)
        assertEquals("Examen físico", pasosDeAtencion(false, false).first { it.clave == "examen" }.titulo)
        assertEquals("Examen estomatológico", pasosDeAtencion(false, true, psiq = true).first { it.clave == "examen" }.titulo)
    }

    @Test
    fun servidorViejoConPasoFisicoSeRenombraSoloEnPsiquiatria() {
        val delServidor = pasosDeAtencion(false, false)
        assertEquals("Examen mental y físico", pasosConPsiquiatria(delServidor, true).first { it.clave == "examen" }.titulo)
        assertEquals(delServidor, pasosConPsiquiatria(delServidor, false))
        // Un servidor que ya lo manda bien: no se toca.
        val nuevo = pasosDeAtencion(false, false, psiq = true)
        assertEquals(nuevo, pasosConPsiquiatria(nuevo, true))
    }

    // ── 2. Selector de escalas ──
    @Test
    fun apgarDeTresYCincoOpcionesSeDistinguen() {
        fun ins(id: String, corto: String, n: Int) =
            InstrumentoPsico(id = id, catalogo = corto, corto = corto, opciones = (0 until n).map { OpcionItemPsico(it, "o$it") })
        val todos = listOf(ins("phq9", "PHQ-9", 4), ins("apgar3", "APGAR familiar", 3), ins("apgar5", "APGAR familiar", 5))
        val etiquetas = todos.map { etiquetaInstrumento(it, todos) }
        assertEquals(listOf("PHQ-9", "APGAR familiar (3 opciones)", "APGAR familiar (5 opciones)"), etiquetas)
    }

    // ── 3. Informe desde la ficha ──
    @Test
    fun prefillDeLaFichaSeLee() {
        val json = Json.parseToJsonElement(
            """{"ok":true,"atencionId":"a2","psiq":true,
               "atenciones":[{"id":"a2","fecha":"2026-10-01","hora":"09:30","resumen":"Control PHQ-9","profesional":"Dr. Valdivia","psiq":true},
                             {"id":"a1","fecha":"2026-09-01","hora":null,"resumen":"Ánimo bajo","profesional":null,"psiq":true}],
               "prefill":{"motivo":"Control PHQ-9","antecedentes":"Madre con ansiedad","examen":null,"examenMental":"Lúcido",
                          "escalas":"PHQ-9: 12 / 27 — Depresión moderada","diagnosticos":[{"codigo":"F32.1","descripcion":"Episodio depresivo moderado","tipo":"D"}],
                          "tratamiento":"Sertralina","recomendaciones":"Control en 4 semanas","examenes":[{"nombre":"Litemia","indicacion":null}],
                          "psiq":true,"citaId":"c2","atencionId":"a2","tratamientoId":null,"terapeutaId":"t1"}}"""
        ).jsonObject
        val r = parsearPrefillFicha(json)
        assertEquals("a2", r.atencionId)
        assertTrue(r.psiq)
        assertEquals(listOf("a2", "a1"), r.atenciones.map { it.id })
        assertEquals("01/10/2026 09:30 · Control PHQ-9 — Dr. Valdivia", r.atenciones[0].etiqueta)
        assertEquals("01/09/2026 · Ánimo bajo", r.atenciones[1].etiqueta)
        val p = r.prefill!!
        assertEquals("Lúcido", p.examenMental)
        assertEquals("PHQ-9: 12 / 27 — Depresión moderada", p.escalas)
        assertEquals("F32.1", p.diagnosticos.single().codigo)
        assertEquals("c2", p.citaId)
        assertEquals("t1", p.terapeutaId)
        assertTrue(p.psiq)
        assertEquals(listOf("Litemia"), p.examenes.map { it.nombre })
    }

    @Test
    fun prefillEnBlancoOSinAtenciones() {
        val r = parsearPrefillFicha(Json.parseToJsonElement("""{"ok":true,"atenciones":[],"atencionId":null,"psiq":false,"prefill":null}""").jsonObject)
        assertNull(r.prefill)
        assertNull(r.atencionId)
        assertFalse(r.psiq)
        assertTrue(r.atenciones.isEmpty())
    }

    // ── 4. Orden de exámenes ──
    @Test
    fun sugerenciasSinRepetidos() {
        val aprendidos = listOf(
            ExamenAprendido("Litemia (litio sérico)", 2),
            ExamenAprendido("Perfil renal (urea, creatinina, electrolitos)", 2),
            ExamenAprendido("Hemograma completo", 4),
            ExamenAprendido("Electrocardiograma (EKG)", 2),
            ExamenAprendido("TSH", 2),
        )
        val s = sugerenciasExamenes(aprendidos, psiq = true)
        val bases = s.map { claveBaseExamen(it) }
        assertEquals(bases.size, bases.toSet().size)
        assertTrue("Litemia (litio sérico)" in s)
        assertFalse("Litemia (nivel de litio en sangre)" in s)
        assertTrue("Electrocardiograma (EKG)" in s)
        assertFalse("Electrocardiograma" in s)
        assertTrue("TSH" in s && "Perfil tiroideo (TSH, T4 libre)" in s)
        assertEquals(claveBaseExamen("Glucosa En Ayúnas"), claveBaseExamen("glucosa en ayunas"))
    }

    @Test
    fun yaEnLaOrden() {
        val orden = listOf(ExamenOrden("Litemia (litio sérico)", null))
        assertTrue(examenYaEnOrden(orden, "Litemia (nivel de litio en sangre)"))
        assertTrue(examenYaEnOrden(orden, "LITEMIA"))
        assertFalse(examenYaEnOrden(orden, "Prolactina"))
    }

    // ── 5. "→ Evaluación" y el flujo de la especialidad ──
    private fun obj(s: String): JsonObject = Json.parseToJsonElement(s).jsonObject

    @Test
    fun psiquiatriaSinFlujoGuardadoNoPasaAEvaluacion() {
        val clinicaFisio = FlujoClinica()
        val f = clinicaFisio.paraEspecialidad(null, "Psiquiatría")
        assertFalse(f.pasaAEvaluacion("Consulta"))
        assertEquals("Controles", f.labelSesiones)
        // Con su flujo guardado, manda el guardado (como siempre).
        val conPreset = clinicaFisio.paraEspecialidad(obj("""{"usa_consulta":true,"usa_evaluacion":false,"label_sesiones":"Controles"}"""), "Psiquiatría")
        assertFalse(conPreset.pasaAEvaluacion("Consulta"))
    }

    @Test
    fun daluYDemasSinCambios() {
        val clinicaFisio = FlujoClinica()
        assertEquals(clinicaFisio, clinicaFisio.paraEspecialidad(null, "Fisioterapia"))
        assertTrue(clinicaFisio.paraEspecialidad(null, "Fisioterapia").pasaAEvaluacion("Consulta"))
        assertEquals(clinicaFisio, clinicaFisio.paraEspecialidad(null, null))
        // Psicología NO es psiquiatría: hereda el de la clínica.
        assertEquals(clinicaFisio, clinicaFisio.paraEspecialidad(null, "Psicología"))
        // La firma vieja (sin nombre) sigue igual.
        assertEquals(clinicaFisio, clinicaFisio.paraEspecialidad(null))
    }

    // ── 6. Evolución del cierre de sesión ──
    private val esps = listOf(
        Triple("psico", "psicologia", "Psicología"),
        Triple("psiq", "medicina_general", "Psiquiatría"),
        Triple("est", "estetica", "Estética"),
    )

    @Test
    fun psicoterapiaNoSugiereFisio() {
        for (id in listOf("psico", "psiq")) {
            val r = rubroCierreSesion(esFisio = false, esDental = false, especialidadId = id, especialidades = esps)
            assertEquals(RubroCierre.PSICO, r)
            assertTrue("ansiedad" in placeholderMejorias(r))
            assertFalse("dolor" in placeholderMejorias(r).lowercase())
            assertEquals(MEJORIAS_RAPIDAS_PSICO, mejoriasRapidasNoFisio(r))
        }
        // Sin especialidad: clínica solo de psicología.
        assertEquals(RubroCierre.PSICO, rubroCierreSesion(false, false, null, listOf(esps[0])))
    }

    @Test
    fun fisioYOtrosRubros() {
        assertEquals(RubroCierre.FISIO, rubroCierreSesion(true, false, "psico", esps))
        assertEquals("Ej: Menos dolor al caminar, mayor rango…", placeholderMejorias(RubroCierre.FISIO))
        assertEquals(RubroCierre.DENTAL, rubroCierreSesion(false, true, null, esps))
        val otro = rubroCierreSesion(false, false, "est", esps)
        assertEquals(RubroCierre.OTRO, otro)
        assertFalse("dolor" in placeholderMejorias(otro).lowercase())
        assertFalse(mejoriasRapidasNoFisio(otro).any { it in MEJORIAS_RAPIDAS && it != "Sin cambios" && it != "Peor" })
    }
}
