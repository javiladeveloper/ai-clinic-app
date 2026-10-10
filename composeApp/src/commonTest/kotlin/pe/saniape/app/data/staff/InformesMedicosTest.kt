package pe.saniape.app.data.staff

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Documentos médicos (informe, descanso, orden) y escalas de la consulta:
 * gemelas de lib/informes-medicos.ts y lib/escalas-atencion.ts (mismos casos que
 * lib/__tests__/psiquiatria-documentos.test.ts).
 */
class InformesMedicosTest {

    private fun borrador(tipo: String, fecha: String = "2026-10-09") =
        BorradorInforme(tipo = tipo, pacienteId = "p1", terapeutaId = "t1", fecha = fecha)

    // ── Numeración y fechas ──

    @Test fun numeroConPrefijoPorTipo() {
        assertEquals("IM N° 000012", formatearNumeroInforme(DOC_INFORME, 12))
        assertEquals("DM N° 000003", formatearNumeroInforme(DOC_DESCANSO, 3))
        assertEquals("OE N° —", formatearNumeroInforme(DOC_ORDEN, null))
    }

    @Test fun descansoCuentaLosDosExtremos() {
        assertEquals(7, diasDescanso("2026-10-09", "2026-10-15"))
        assertEquals(1, diasDescanso("2026-10-09", "2026-10-09"))
        assertEquals(0, diasDescanso("2026-10-09", "2026-10-08"))
        assertNull(diasDescanso("2026-10-09", null))
        assertNull(diasDescanso("9/10/2026", "2026-10-10"))
        assertEquals("2026-10-11", hastaPorDias("2026-10-09", 3))
        assertEquals("2026-11-07", hastaPorDias("2026-10-09", 30))
        assertEquals("2026-10-09", hastaPorDias("2026-10-09", 0))
        assertEquals("2026-09-09", restarDias("2026-10-09", 30))
    }

    // ── Validación (validarInforme) ──

    @Test fun fechaEntreHoyYTreintaDiasAtras() {
        val b = borrador(DOC_ORDEN).copy(examenes = listOf(ExamenOrden("Hemograma completo")))
        assertEquals(emptyList(), validarInforme(b, "2026-10-09"))
        assertTrue(validarInforme(b.copy(fecha = "2026-10-10"), "2026-10-09").any { "futura" in it })
        assertTrue(validarInforme(b.copy(fecha = "2026-09-08"), "2026-10-09").any { "30 días" in it })
        assertEquals(emptyList(), validarInforme(b.copy(fecha = "2026-09-09"), "2026-10-09"))
    }

    @Test fun reglasPorTipo() {
        assertTrue(validarInforme(borrador(DOC_INFORME), "2026-10-09").any { "vacío" in it })
        assertEquals(emptyList(), validarInforme(borrador(DOC_INFORME).copy(examenMental = "Lúcido"), "2026-10-09"))
        assertEquals(emptyList(), validarInforme(
            borrador(DOC_INFORME).copy(diagnosticos = listOf(DiagnosticoCie("F32.1", "Episodio depresivo moderado", "D"))), "2026-10-09"))
        assertTrue(validarInforme(borrador(DOC_DESCANSO), "2026-10-09").any { "desde y hasta" in it })
        assertTrue(validarInforme(borrador(DOC_DESCANSO).copy(descansoDesde = "2026-10-09", descansoHasta = "2026-10-08"), "2026-10-09")
            .any { "anterior" in it })
        assertTrue(validarInforme(borrador(DOC_DESCANSO).copy(descansoDesde = "2026-01-01", descansoHasta = "2027-01-05"), "2026-10-09")
            .any { "un año" in it })
        assertTrue(validarInforme(borrador(DOC_ORDEN).copy(examenes = listOf(ExamenOrden(" "))), "2026-10-09").any { "al menos un examen" in it })
        assertTrue(validarInforme(borrador(DOC_ORDEN).copy(terapeutaId = null, examenes = listOf(ExamenOrden("TSH"))), "2026-10-09")
            .any { "profesional que firma" in it })
    }

    @Test fun examenesSinRepetidosNiVacios() {
        val l = normalizarExamenesOrden(listOf(
            ExamenOrden("  Hemograma   completo "), ExamenOrden("hemograma completo."), ExamenOrden("x"),
            ExamenOrden("Litemia", "  12 h tras la última toma "), ExamenOrden("Litemia", ""),
        ))
        assertEquals(listOf(ExamenOrden("Hemograma completo", null), ExamenOrden("Litemia", "12 h tras la última toma")), l)
        assertEquals("ácido valproico".let { claveExamen(it) }, claveExamen("Acido  Valproico."))
    }

    @Test fun sugerenciasYTypeahead() {
        val s = sugerenciasExamenes(listOf(ExamenAprendido("Dosaje de vitamina D", 2), ExamenAprendido("Litemia (nivel de litio en sangre)", 9)), psiq = true)
        assertEquals("Litemia (nivel de litio en sangre)", s[0])
        assertEquals("Dosaje de vitamina D", s[1])
        assertEquals(1, s.count { claveExamen(it) == claveExamen("Hemograma completo") })
        assertTrue("Prolactina" !in sugerenciasExamenes(emptyList(), psiq = false))
        assertEquals(listOf("Perfil tiroideo (TSH, T4 libre)"), filtrarExamenes(EXAMENES_FRECUENTES_PSIQUIATRIA, "tiro"))
        assertEquals(emptyList(), filtrarExamenes(EXAMENES_FRECUENTES_PSIQUIATRIA, "t"))
    }

    // ── Quién emite y quién firma ──

    @Test fun soloMedicosUOdontologosFirman() {
        assertTrue(esEmisorMedico(listOf("medicina_general")))
        assertTrue(esEmisorMedico(listOf("odontologia", "estetica")))
        assertTrue(esEmisorMedico(emptyList()))
        assertFalse(esEmisorMedico(listOf("psicologia")))
        assertFalse(esEmisorMedico(listOf("fisioterapia", null)))
    }

    @Test fun reglaDeFirmante() {
        assertTrue(puedeEmitirDocumentos("Admin", null, true))
        assertTrue(puedeEmitirDocumentos("Profesional", "t1", true))
        assertFalse(puedeEmitirDocumentos("Recepcionista", null, true))
        assertFalse(puedeEmitirDocumentos("Admin", null, false))
        assertTrue(puedeFirmarComo("Admin", null, "t9"))
        assertTrue(puedeFirmarComo("Profesional", "t1", "t1"))
        assertFalse(puedeFirmarComo("Profesional", "t1", "t2"))
        assertFalse(puedeFirmarComo("Admin", null, null))

        val equipo = listOf(
            FirmanteDoc("t1", "Dra. Campos", "12345", "Activo", listOf("medicina_general"), listOf("Psiquiatría")),
            FirmanteDoc("t2", "Dr. Salazar", "45678", "Activo", listOf("medicina_general")),
            FirmanteDoc("t3", "Lic. Ruiz", "99999", "Activo", listOf("psicologia")),
            FirmanteDoc("t4", "Dr. Sin CMP", " 1", "Activo", listOf("medicina_general")),
            FirmanteDoc("t5", "Dr. Baja", "77777", "Inactivo", listOf("medicina_general")),
        )
        assertEquals(listOf("t1", "t2"), firmantesDocumento(equipo, esAdmin = true, miTerapeutaId = null).map { it.id })
        assertEquals(listOf("t2"), firmantesDocumento(equipo, esAdmin = false, miTerapeutaId = "t2").map { it.id })
        assertEquals(emptyList(), firmantesDocumento(equipo, esAdmin = false, miTerapeutaId = "t3"))
        assertTrue(equipo[0].psiquiatra)
        assertFalse(equipo[1].psiquiatra)

        val f = firmantesDocumento(equipo, esAdmin = true, miTerapeutaId = null)
        assertEquals("t2", firmanteInicial(f, esAdmin = true, miTerapeutaId = null, sugerido = "t2"))
        assertEquals("", firmanteInicial(f, esAdmin = true, miTerapeutaId = null, sugerido = "t3"))
        assertEquals("t1", firmanteInicial(f.take(1), esAdmin = true, miTerapeutaId = null, sugerido = null))
        val propio = firmantesDocumento(equipo, esAdmin = false, miTerapeutaId = "t1")
        assertEquals("t1", firmanteInicial(propio, esAdmin = false, miTerapeutaId = "t1", sugerido = "t2"))
    }

    // ── Visibilidad (DALU / RENOVA no ven nada) ──

    @Test fun documentosSoloEnClinicasMedicas() {
        val receta = MapaClinico(listOf("e1"))
        // Clínica médica: flujo médico o recetas.
        assertTrue(documentosMedicosActivos(ModulosClinicos(flujoMedico = true)))
        assertTrue(documentosMedicosActivos(ModulosClinicos(recetas = true, mapaReceta = receta)))
        // DALU: recetas como hoja de indicaciones (no médica) → nada.
        assertFalse(documentosMedicosActivos(ModulosClinicos(recetas = true, recetasOptIn = true, mapaReceta = MapaClinico(solo = true))))
        // RENOVA y la mayoría: todo apagado.
        assertFalse(documentosMedicosActivos(ModulosClinicos()))
        // Si el servidor lo manda resuelto, manda el servidor.
        assertFalse(documentosMedicosActivos(ModulosClinicos(flujoMedico = true, documentosMedicos = false)))
        assertTrue(documentosMedicosActivos(ModulosClinicos(documentosMedicos = true)))
    }

    @Test fun pestanaPorPaciente() {
        val medica = ModulosClinicos(flujoMedico = true, recetas = true, mapaReceta = MapaClinico(solo = true), mapaMedico = MapaClinico(solo = true))
        assertTrue(pacienteRecibeDocumentos(medica, esPacienteMedico = true, esPacienteReceta = false))
        assertTrue(pacienteRecibeDocumentos(medica, esPacienteMedico = false, esPacienteReceta = true))
        assertFalse(pacienteRecibeDocumentos(medica, esPacienteMedico = false, esPacienteReceta = false))
        val dalu = ModulosClinicos(recetas = true, recetasOptIn = true, mapaReceta = MapaClinico(solo = true))
        assertFalse(pacienteRecibeDocumentos(dalu, esPacienteMedico = false, esPacienteReceta = true))
        assertEquals("💊 Recetas y documentos", etiquetaPestanaRecetas(true, true))
        assertEquals("📄 Documentos médicos", etiquetaPestanaRecetas(false, true))
        assertEquals("💊 Recetas", etiquetaPestanaRecetas(true, false))
    }

    @Test fun psiquiatriaPorNombreDeEspecialidad() {
        val esps = listOf(EspecialidadNombre("e1", "Psiquiatría", "Activa"), EspecialidadNombre("e2", "Psicología", "Activa"))
        assertTrue(atencionEsPsiquiatria(esps, "e1", dental = false))
        assertFalse(atencionEsPsiquiatria(esps, "e2", dental = false))
        assertFalse(atencionEsPsiquiatria(esps, "e1", dental = true))
        // Sin especialidad: solo si todas las activas son de psiquiatría.
        assertFalse(atencionEsPsiquiatria(esps, null, dental = false))
        assertTrue(atencionEsPsiquiatria(listOf(esps[0], EspecialidadNombre("e3", "Psicología", "Inactiva")), null, dental = false))
        assertTrue(esPsiquiatria("PSIQUIATRIA infantil"))
    }

    // ── Prellenado y JSON ──

    @Test fun prefillDesdeLaAtencion() {
        val p = prefillDesdeAtencion(
            FuenteAtencion(
                motivoConsulta = "Ánimo bajo", tiempoEnfermedad = "2 meses", relato = " ",
                examenMental = "Lúcido, orientado", escalasTexto = "PHQ-9: 14 / 27 — Depresión moderada",
                diagnosticos = listOf(DiagnosticoCie("f32.1", " Episodio depresivo  moderado ", "X"), DiagnosticoCie("F32.1", "otra", "D")),
                planTrabajo = "Control en 4 semanas", examenes = listOf(ExamenOrden("TSH", "ayunas")),
            ),
            antecedentes = "HTA", alergias = "Penicilina", medicacionActual = null,
        )
        assertEquals("Ánimo bajo\nTiempo de enfermedad: 2 meses", p.motivo)
        assertEquals("HTA\nAlergias: Penicilina", p.antecedentes)
        assertEquals(listOf(DiagnosticoCie("F32.1", "Episodio depresivo moderado", "P")), p.diagnosticos)
        assertEquals("Control en 4 semanas", p.recomendaciones)
        assertNull(p.examen)
        assertEquals(listOf(ExamenOrden("TSH", "ayunas")), p.examenes)
    }

    @Test fun cuerpoDeEmitir() {
        val b = borrador(DOC_DESCANSO).copy(
            motivo = "no viaja", descansoDesde = "2026-10-09", descansoHasta = "2026-10-11", ocultarDiagnostico = true,
            examenes = listOf(ExamenOrden("TSH")), titulo = "  ", citaId = "c1",
        )
        val j = cuerpoEmitirInforme(b, "clave-1", seguimiento = true)
        assertEquals("descanso", j["tipo"]!!.jsonPrimitive.content)
        assertEquals("2026-10-11", j["descansoHasta"]!!.jsonPrimitive.content)
        assertEquals(JsonNull, j["titulo"])
        assertEquals(0, j["examenes"]!!.jsonArray.size)
        // El seguimiento es solo de la orden.
        assertEquals(false, j["seguimiento"]!!.jsonPrimitive.content.toBoolean())
        assertEquals(true, j["ocultarDiagnostico"]!!.jsonPrimitive.content.toBoolean())
        val orden = cuerpoEmitirInforme(borrador(DOC_ORDEN).copy(examenes = listOf(ExamenOrden("TSH"), ExamenOrden("tsh"))), "k", seguimiento = true)
        assertEquals(1, orden["examenes"]!!.jsonArray.size)
        assertEquals(JsonNull, orden["descansoDesde"])
        assertEquals(true, orden["seguimiento"]!!.jsonPrimitive.content.toBoolean())
    }

    @Test fun filaDeLaLista() {
        val o = Json.parseToJsonElement(
            """{"id":"i1","tipo":"descanso","numero":3,"fecha":"2026-10-09","terapeuta_id":"t1","estado":"Anulado",
               "motivo_anulacion":"Error en las fechas","ocultar_diagnostico":true,"descanso_desde":"2026-10-09",
               "descanso_hasta":"2026-10-15","descanso_dias":7,"examenes":[],
               "profesional":{"nombre":"Dra. Campos","colegiatura":"CMP 12345"}}"""
        ).jsonObject
        val inf = assertNotNull(aInformeMedico(o))
        assertTrue(inf.anulado)
        assertEquals("DM N° 000003", inf.numeroTexto)
        assertEquals("7 día(s): 09/10/2026 → 15/10/2026", resumenInforme(inf))
        assertEquals("CMP 12345", inf.profesionalColegiatura)
        assertNull(aInformeMedico(Json.parseToJsonElement("""{"id":"x","tipo":"receta"}""").jsonObject))
        val orden = aInformeMedico(Json.parseToJsonElement(
            """{"id":"i2","tipo":"orden_examenes","examenes":[{"nombre":"TSH","indicacion":null},{"nombre":"Litemia"}]}""").jsonObject)!!
        assertEquals("TSH · Litemia", resumenInforme(orden))
        assertEquals("Emitido", orden.estado)
    }

    // ── Escalas ──

    /** Mini PHQ (3 ítems 0..3, cortes) para probar el cálculo orientativo. */
    private val mini = InstrumentoPsico(
        id = "phq9", catalogo = "PHQ-9", corto = "PHQ-9", nombre = "Mini",
        opciones = (0..3).map { OpcionItemPsico(it, "o$it") },
        items = listOf(ItemInstrumentoPsico(1, "a"), ItemInstrumentoPsico(2, "b"), ItemInstrumentoPsico(3, "c", inverso = true),
            ItemInstrumentoPsico(4, "dificultad", puntua = false, opcional = true)),
        total = EscalaInstrumentoPsico("total", "Total", items = listOf(1, 2, 3), min = 0.0, max = 9.0,
            bandas = listOf(BandaCortePsico(0.0, 4.0, "Mínima", 0), BandaCortePsico(5.0, 9.0, "Moderada", 2, positivo = true))),
        alertas = listOf(AlertaItemPsico(2, 1, "Riesgo: revisar")),
    )

    @Test fun calculoOrientativoDeEscala() {
        val e = aplicarEscalaLocal(mini, listOf(3, 1, 0, null))
        // Ítem 3 inverso: 0 → 3. Total 3 + 1 + 3 = 7.
        assertEquals(7.0, e.puntaje)
        assertTrue(e.completo)
        assertEquals("Moderada", e.categoria)
        assertEquals(listOf("Riesgo: revisar"), e.alertas)
        assertEquals("PHQ-9: 7 / 9 — Moderada · ⚠ Riesgo: revisar", textoEscala(e))
        val inc = aplicarEscalaLocal(mini, listOf(3, null, 9))
        assertFalse(inc.completo)
        assertNull(inc.puntaje)
        assertEquals(listOf(3, null, null, null), inc.valores)
        assertEquals("PHQ-9: incompleta", textoEscala(inc))
    }

    @Test fun escalasIdaYVuelta() {
        val e = aplicarEscalaLocal(mini, listOf(0, 0, 3, null), sexo = "F")
        val j = jsonEscalas(listOf(e))
        val o = j[0].jsonObject
        assertEquals(setOf("instrumento", "valores", "sexo"), o.keys)
        assertEquals(JsonNull, o["valores"]!!.jsonArray[3])
        val servidor = leerEscalasAtencion(
            Json.parseToJsonElement(
                """[{"instrumento":"phq9","corto":"PHQ-9","nombre":"Mini","valores":[0,0,3,null],"puntaje":0,"min":0,"max":9,
                    "categoria":"Mínima","alertas":[],"completo":true,"sexo":"F"},{"sin":"instrumento"}]""").jsonArray,
        )
        assertEquals(1, servidor.size)
        assertEquals("PHQ-9: 0 / 9 — Mínima", textoEscala(servidor[0]))
        assertEquals(servidor, escalasDelServidorSiCoinciden(listOf(e), servidor))
        assertNull(escalasDelServidorSiCoinciden(listOf(e.copy(valores = listOf(1, 0, 3, null))), servidor))
        assertNull(escalasDelServidorSiCoinciden(emptyList(), servidor))
    }

    @Test fun guardarMandaPsiquiatriaSoloSiLoEs() {
        val sinPsiq = AtencionRepo.cuerpoGuardar("c", BorradorAtencion())["borrador"]!!.jsonObject
        assertFalse("examen_mental" in sinPsiq)
        assertFalse("escalas" in sinPsiq)
        val conPsiq = AtencionRepo.cuerpoGuardar("c", BorradorAtencion(examenMental = "", escalas = emptyList()))["borrador"]!!.jsonObject
        assertEquals(JsonPrimitive(""), conPsiq["examen_mental"])
        assertEquals(JsonArray(emptyList()), conPsiq["escalas"])
    }

    @Test fun sexoDeLaHc() {
        fun pac(h: String) = PacienteConsultaApp(id = "p", historia = Json.parseToJsonElement(h))
        assertEquals("F", sexoHc(pac("""{"sexo":"F"}""")))
        assertEquals("M", sexoHc(pac("""[{"sexo":"M"}]""")))
        assertNull(sexoHc(pac("""{"sexo":null}""")))
        assertNull(sexoHc(PacienteConsultaApp(id = "p")))
    }
}
