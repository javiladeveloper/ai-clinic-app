package pe.saniape.app.data.staff

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject

/**
 * Gemelo de `lib/__tests__/frases-campo.test.ts` (web) en lo que la app usa:
 * registro de claves, filtros de privacidad, qué se aprende al guardar, qué se
 * muestra y el contrato de `/api/staff/frases`. Mismos casos.
 */
class FrasesCampoTest {

    private fun obj(s: String) = Json.parseToJsonElement(s).jsonObject

    // ── Registro de campos ──

    @Test fun cadaClaveEstaBienFormadaYEsUnica() {
        val forma = Regex("^[a-z]+(\\.[a-z_]+){1,3}$")
        for ((k, d) in CAMPOS_APRENDIBLES) {
            assertEquals(k, d.clave)
            assertTrue(forma.matches(k), k)
            assertTrue(k.length <= 60, k)
            assertTrue(d.max in 20..(if (d.sensible) MAX_SENSIBLE else 160), k)
        }
        assertEquals(
            listOf("capilar", "estetica", "fisio", "psico", "tratamiento"),
            CAMPOS_APRENDIBLES.keys.map { it.substringBefore('.') }.distinct().sorted(),
        )
    }

    @Test fun modoYLargoComoLaWeb() {
        assertFalse(CAMPOS_APRENDIBLES.getValue(ClaveFrase.PSICO_MOTIVO).porLinea)
        assertFalse(CAMPOS_APRENDIBLES.getValue(ClaveFrase.FISIO_CARACTERISTICAS).porLinea)
        assertFalse(CAMPOS_APRENDIBLES.getValue(ClaveFrase.ESTETICA_NOTAS_ZONA).porLinea)
        assertTrue(CAMPOS_APRENDIBLES.getValue(ClaveFrase.TRATAMIENTO_INDICACIONES).porLinea)
        assertEquals(160, CAMPOS_APRENDIBLES.getValue(ClaveFrase.TRATAMIENTO_INDICACIONES).max)
        assertEquals(60, CAMPOS_APRENDIBLES.getValue(ClaveFrase.FISIO_CARACTERISTICAS).max)
        assertEquals(120, CAMPOS_APRENDIBLES.getValue(ClaveFrase.CAPILAR_OBSERVACIONES).max)
    }

    @Test fun psicologiaSoloLasSeccionesTecnicas() {
        assertEquals(
            listOf(
                "psico.eval.area.emocional", "psico.eval.area.familiar_social", "psico.eval.area.intelectual",
                "psico.eval.area.personalidad", "psico.eval.area.visomotora", "psico.eval.area.vocacional",
                "psico.eval.conclusiones", "psico.eval.motivo", "psico.eval.observacion", "psico.eval.recomendaciones",
            ),
            CAMPOS_APRENDIBLES.values.filter { it.sensible }.map { it.clave }.sorted(),
        )
        assertEquals(CAMPOS_PSICO_EVAL.sorted(), CAMPOS_APRENDIBLES.keys.filter { it.startsWith("psico.") }.sorted())
        assertTrue(esCampoSensible("psico.otra.cosa"))
        assertFalse(esCampoSensible(ClaveFrase.TRATAMIENTO_INDICACIONES))
        assertFalse(esCampoAprendible("psico.eval.familia"))
        assertFalse(esCampoAprendible(null))
        assertTrue(CAMPOS_PSICO_EVAL.size <= MAX_CAMPOS_POR_LECTURA)
    }

    @Test fun unCampoPorCadaAreaDelAnalisis() {
        assertEquals(AREAS_ANALISIS.map { it.valor }.sorted(), CAMPO_AREA_PSICO.keys.sorted())
        CAMPO_AREA_PSICO.values.forEach { assertTrue(esCampoAprendible(it), it) }
    }

    // ── Filtros de privacidad (gemelos de registrar_frases) ──

    private val trico = ClaveFrase.CAPILAR_OBSERVACIONES

    @Test fun descartaDatosPersonalesNumerosFechasSimbolosYEmojis() {
        assertTrue(esFraseRegistrable("Puntos amarillos dispersos", trico))
        assertFalse(esFraseRegistrable("DNI 45871236", trico))
        assertFalse(esFraseRegistrable("Llamar al 952 123 456", trico))
        assertFalse(esFraseRegistrable("Control el 12/11/2026", trico))
        assertFalse(esFraseRegistrable("escribir a ana@correo.pe", trico))
        assertFalse(esFraseRegistrable("Avisar por WhatsApp", trico))
        assertFalse(esFraseRegistrable("Mejoría 👍", trico))
        assertFalse(esFraseRegistrable("[plantilla]", trico))
        assertFalse(esFraseRegistrable("123", trico))
        assertFalse(esFraseRegistrable("ok", trico))
        assertFalse(esFraseRegistrable("Cita cerrada sin registrar", trico))
        // Una clave fuera del registro nunca se aprende.
        assertFalse(esFraseRegistrable("Puntos amarillos", "psico.eval.familia"))
    }

    @Test fun descartaElNombreDelPacienteConOSinTildes() {
        assertFalse(esFraseRegistrable("Rosángela refiere caída difusa", trico, "Rosangela Quispe Mamani"))
        assertFalse(esFraseRegistrable("Caída difusa según QUISPE", trico, "Rosangela Quispe Mamani"))
        assertTrue(esFraseRegistrable("Caída difusa", trico, "Rosangela Quispe Mamani"))
    }

    @Test fun largoSegunElCampo() {
        assertTrue(esFraseRegistrable("x".repeat(10) + " " + "a".repeat(49), ClaveFrase.FISIO_CARACTERISTICAS)) // 60
        assertFalse(esFraseRegistrable("a".repeat(61), ClaveFrase.FISIO_CARACTERISTICAS))
        assertTrue(esFraseRegistrable("a".repeat(120), trico))
        assertFalse(esFraseRegistrable("a".repeat(121), trico))
    }

    @Test fun psicoSinParentescoNiNombresPropiosLasSiglasPasan() {
        val p = ClaveFrase.PSICO_OBSERVACION
        assertTrue(esFraseRegistrable("Requirió redirección constante a la tarea", p))
        assertTrue(esFraseRegistrable("Indicadores compatibles con TDAH", p))
        assertFalse(esFraseRegistrable("La madre refiere conductas oposicionistas", p))
        assertFalse(esFraseRegistrable("Según la profesora no copia de la pizarra", p))
        assertFalse(esFraseRegistrable("Juega con su amiga Lucía en el recreo", p))
        assertFalse(esFraseRegistrable("Derivado por el Dr. Pérez", p))
        assertFalse(esFraseRegistrable("Mamá ausente", p))
        assertFalse(esFraseRegistrable("a".repeat(81), p))
        // Instrumentos y especialidades con mayúscula no son personas.
        assertTrue(esFraseRegistrable("Test de Bender acorde a la edad", p))
        assertTrue(esFraseRegistrable("Escala Wechsler en rango promedio", "psico.eval.area.intelectual"))
        assertTrue(esFraseRegistrable("Derivar a Neurología", ClaveFrase.PSICO_RECOMENDACIONES))
        assertFalse(esFraseRegistrable("Test de Bender aplicado con Lucía", p))
        // Nombres cortos (3 letras) y al inicio de la oración.
        assertFalse(esFraseRegistrable("Luz se distrae con facilidad", p, "Luz Mamani"))
        assertFalse(esFraseRegistrable("Mateo lo agrede en el recreo", p, "Mateo Quispe"))
        assertTrue(esFraseRegistrable("Se distrae con facilidad", p, "Luz Mamani"))
        // Fuera de psico, un nombre propio (marca de producto) sí se aprende.
        assertTrue(esFraseRegistrable("Aplicar Bepanthen dos veces al día", ClaveFrase.TRATAMIENTO_INDICACIONES))
    }

    @Test fun pareceDatoPersonalCruzaElNombreSoloComoPalabraSuelta() {
        assertTrue(pareceDatoPersonal("Ana refiere mejoría", "Ana Ruiz"))
        assertFalse(pareceDatoPersonal("Analgésico si hay dolor", "Ana Ruiz"))
        assertFalse(pareceDatoPersonal("Reposo relativo", null))
        assertTrue(pareceDatoPersonal("Núñez con dolor", "Carlos Nuñez"))
    }

    // ── Qué se aprende al guardar ──

    @Test fun troceaPorElModoDelCampoYPorOraciones() {
        assertEquals(listOf("Punzante", "nocturno", "irradia a miembro inferior"),
            trocearCampo("Punzante, nocturno; irradia a miembro inferior", porLinea = false))
        assertEquals(listOf("Se mostró colaborador", "Requirió redirección", "Comprendió las consignas"),
            trocearCampo("Se mostró colaborador. Requirió redirección.\n- Comprendió las consignas", porLinea = true))
        // Sin mayúscula después del punto no corta ("aprox. 3 cm").
        assertEquals(listOf("Placa de aprox. 3 cm"), trocearCampo("Placa de aprox. 3 cm", porLinea = true))
        // Coma dentro de paréntesis: un solo ítem.
        assertEquals(listOf("Mecánico (empeora al caminar, al subir escaleras)"),
            trocearCampo("Mecánico (empeora al caminar, al subir escaleras)", porLinea = false))
        // Por línea, las comas no cortan.
        assertEquals(listOf("Aplicar frío 15 minutos, 3 veces al día"),
            trocearCampo("• Aplicar frío 15 minutos, 3 veces al día.", porLinea = true))
        assertEquals(emptyList(), trocearCampo(null, porLinea = true))
    }

    @Test fun soloLoNuevoRespectoDeAntesSinRepetirConMayuscula() {
        val c = ClaveFrase.FISIO_CARACTERISTICAS
        val antes = "Punzante, nocturno"
        assertEquals(listOf("Hormigueo"), frasesParaRegistrar(c, "Punzante, NOCTURNO, hormigueo, hormigueo", antes))
        assertEquals(emptyList(), frasesParaRegistrar(c, "Punzante, nocturno", antes))
        assertEquals(emptyList(), frasesParaRegistrar(c, "", null))
        assertEquals(emptyList(), frasesParaRegistrar(c, null, null))
        // Sin tildes ni mayúsculas es lo mismo que ya tenía.
        assertEquals(emptyList(), frasesParaRegistrar(c, "PUNZANTE, Nocturno.", antes))
        // Clave fuera del registro: nada.
        assertEquals(emptyList(), frasesParaRegistrar("psico.eval.familia", "Vive con sus abuelos", null))
    }

    @Test fun descartaLoPersonalYRespetaElTopePorGuardado() {
        assertEquals(listOf("Reposo 3 días"),
            frasesParaRegistrar(ClaveFrase.TRATAMIENTO_INDICACIONES, "Reposo 3 días\nLlamar a Ana al 987654321", nombrePaciente = "Ana Ruiz"))
        val letras = "abcdefghijklmnopqrstuvwxyzabcd"
        val muchas = (0 until 30).joinToString("\n") { "Indicación número ${letras[it]}x" }
        assertEquals(MAX_FRASES_POR_GUARDADO, frasesParaRegistrar(ClaveFrase.TRATAMIENTO_INDICACIONES, muchas).size)
    }

    @Test fun debeRegistrarSoloConAlgoNuevoYEnPsicoConPaciente() {
        val paciente = "22222222-2222-2222-2222-222222222222"
        assertTrue(debeRegistrarFrases(ClaveFrase.TRATAMIENTO_INDICACIONES, "Reposo relativo", null, null, null))
        assertFalse(debeRegistrarFrases(ClaveFrase.TRATAMIENTO_INDICACIONES, "Reposo", "reposo", null, null))
        assertFalse(debeRegistrarFrases(ClaveFrase.TRATAMIENTO_INDICACIONES, "   ", null, null, null))
        assertFalse(debeRegistrarFrases("nada.que.ver", "Reposo relativo", null, null, null))
        // psico.* sin paciente (o con un id mal formado) no se aprende: la RPC no podría contar pacientes.
        assertFalse(debeRegistrarFrases(ClaveFrase.PSICO_MOTIVO, "Ansiedad social", null, null, null))
        assertFalse(debeRegistrarFrases(ClaveFrase.PSICO_MOTIVO, "Ansiedad social", null, "no-uuid", null))
        assertTrue(debeRegistrarFrases(ClaveFrase.PSICO_MOTIVO, "Ansiedad social", null, paciente, null))
        // Solo datos del paciente: nada que aprender, no hay viaje.
        assertFalse(debeRegistrarFrases(ClaveFrase.PSICO_MOTIVO, "Mateo pelea en el recreo", null, paciente, "Mateo Quispe"))
    }

    // ── Contrato del POST ──

    @Test fun cuerpoDelPostConLosOpcionalesValidos() {
        val c = cuerpoRegistrarFrases(
            ClaveFrase.ESTETICA_NOTAS_ZONA, "Bolo profundo, con cánula", "Bolo profundo",
            especialidadId = "no-uuid", pacienteId = "22222222-2222-2222-2222-222222222222", nombrePaciente = "  ",
        )
        assertEquals(ClaveFrase.ESTETICA_NOTAS_ZONA, (c["campo"] as JsonPrimitive).content)
        // El texto TAL COMO SE GUARDÓ: el servidor lo trocea.
        assertEquals(listOf("Bolo profundo, con cánula"), (c["textos"] as JsonArray).map { (it as JsonPrimitive).content })
        assertEquals("Bolo profundo", (c["antes"] as JsonPrimitive).content)
        assertEquals("22222222-2222-2222-2222-222222222222", (c["pacienteId"] as JsonPrimitive).content)
        // Un id mal formado daría 400: no viaja. Lo vacío tampoco.
        assertNull(c["especialidadId"])
        assertNull(c["nombrePaciente"])
        val sinAntes = cuerpoRegistrarFrases(ClaveFrase.TRATAMIENTO_INDICACIONES, "Reposo", "", null, null, "Ana Ruiz")
        assertNull(sinAntes["antes"])
        assertEquals("Ana Ruiz", (sinAntes["nombrePaciente"] as JsonPrimitive).content)
    }

    // ── Qué se muestra: base ∪ aprendido, chip solo con usos ≥ 2 ──

    @Test fun parseaLaRespuestaDeVariosCamposYDeUnoSolo() {
        val varios = obj("""
            {"items":{
              "capilar.tricoscopia.observaciones":{"campo":"capilar.tricoscopia.observaciones","modo":"linea","max":120,
                "base":["Puntos amarillos","Puntos negros"],"aprendidas":[{"texto":"Miniaturización frontal","usos":2,"pacientes":2}]},
              "fisio.eval.caracteristicas":{"campo":"fisio.eval.caracteristicas","modo":"coma","max":60,"base":["Punzante"],"aprendidas":[]},
              "otro.campo":{"campo":"otro.campo","modo":"linea","base":[],"aprendidas":[]}
            }}
        """.trimIndent())
        val r = parsearFrasesCampos(varios, listOf(ClaveFrase.CAPILAR_OBSERVACIONES, ClaveFrase.FISIO_CARACTERISTICAS))
        assertEquals(setOf(ClaveFrase.CAPILAR_OBSERVACIONES, ClaveFrase.FISIO_CARACTERISTICAS), r.keys)
        val t = r.getValue(ClaveFrase.CAPILAR_OBSERVACIONES)
        assertTrue(t.porLinea)
        assertEquals(listOf("Puntos amarillos", "Puntos negros"), t.base)
        assertEquals(listOf(AprendidaCampo("Miniaturización frontal", 2, 2)), t.aprendidas)
        assertFalse(r.getValue(ClaveFrase.FISIO_CARACTERISTICAS).porLinea)

        val uno = obj("""{"campo":"tratamiento.indicaciones","modo":"linea","max":160,"base":["Control en 7 días"],"aprendidas":[{"texto":"Reposo","usos":"3"}]}""")
        val u = parsearFrasesCampos(uno, listOf(ClaveFrase.TRATAMIENTO_INDICACIONES)).getValue(ClaveFrase.TRATAMIENTO_INDICACIONES)
        assertEquals(listOf(AprendidaCampo("Reposo", 3, 0)), u.aprendidas)
    }

    @Test fun unaRespuestaRaraOUnErrorEsSinChips() {
        assertEquals(emptyMap(), parsearFrasesCampos(null, listOf(ClaveFrase.CAPILAR_OBSERVACIONES)))
        assertEquals(emptyMap(), parsearFrasesCampos(obj("""{"error":"Campo no válido","codigo":"CAMPO_INVALIDO"}"""), listOf(ClaveFrase.CAPILAR_OBSERVACIONES)))
        // Aprendidas sin texto se descartan; usos inválidos cuentan 1.
        val r = parsearFrasesCampos(
            obj("""{"items":{"capilar.tricoscopia.observaciones":{"base":"x","aprendidas":[{"texto":""},{"usos":4},{"texto":"Halo","usos":null},7]}}}"""),
            listOf(ClaveFrase.CAPILAR_OBSERVACIONES),
        ).getValue(ClaveFrase.CAPILAR_OBSERVACIONES)
        assertEquals(emptyList(), r.base)
        assertEquals(listOf(AprendidaCampo("Halo", 1, 0)), r.aprendidas)
        // Sin modo: el del registro.
        assertTrue(r.porLinea)
    }

    @Test fun unaFraseEscritaUnaVezVaAlAutocompletadoPeroNoEsChip() {
        val f = frasesDeCampoAbierto(RespuestaFrasesCampo(
            ClaveFrase.CAPILAR_OBSERVACIONES, porLinea = true,
            base = listOf("Puntos amarillos", "Puntos negros"),
            aprendidas = listOf(
                AprendidaCampo("Puntos amarillos", 5), // de la base: se reordena primero
                AprendidaCampo("Miniaturización frontal", 2),
                AprendidaCampo("Halo perifolicular", 1),
            ),
        ))
        val chips = f.chips.map { it.texto }
        assertTrue(f.porLinea)
        assertEquals("Puntos amarillos", chips.first())
        assertTrue("Miniaturización frontal" in chips)
        assertTrue(f.chips.first { it.texto == "Miniaturización frontal" }.aprendida)
        assertFalse("Halo perifolicular" in chips)
        assertTrue("Halo perifolicular" in f.opciones)
    }

    @Test fun psicoUnaFraseDeUnSoloPacienteNoSeOfrece() {
        val f = frasesDeCampoAbierto(RespuestaFrasesCampo(
            ClaveFrase.PSICO_OBSERVACION, porLinea = true, base = emptyList(),
            aprendidas = listOf(
                AprendidaCampo("Se agrede cuando pierde", 7, 1),
                AprendidaCampo("Requirió pausas frecuentes", 2, 2),
                AprendidaCampo("Llora al despedirse", 4), // sin dato de pacientes: 0
                AprendidaCampo("La madre refiere insomnio", 9, 5), // hoy no pasaría el filtro
            ),
        ))
        assertEquals(listOf("Requirió pausas frecuentes"), f.opciones)
        assertEquals(listOf("Requirió pausas frecuentes"), f.chips.map { it.texto })
        // Fuera de psico, pacientes no importa.
        val fisio = frasesDeCampoAbierto(RespuestaFrasesCampo(ClaveFrase.FISIO_CARACTERISTICAS, false, emptyList(), listOf(AprendidaCampo("Calambre", 1))))
        assertTrue("Calambre" in fisio.opciones)
    }

    @Test fun sinNadaAprendidoLaBaseDelServidor() {
        val base = listOf("No masajear la zona tratada por 24 horas", "Evitar el sol y usar protector solar FPS 50")
        val f = frasesDeCampoAbierto(RespuestaFrasesCampo(ClaveFrase.TRATAMIENTO_INDICACIONES, true, base, emptyList()))
        assertEquals(base, f.chips.map { it.texto })
        assertEquals(base, f.opciones)
    }

    // ── Dónde se aplica ──

    @Test fun evaluacionPsicologicaSoloLasSeccionesTecnicas() {
        val ev = EvaluacionPsico(
            id = "e1",
            entrevista = EntrevistaPsico(mapOf("motivo" to "Ansiedad", "historiaPersonal" to "relato", "familia" to "vive con la madre")),
            observacion = ObservacionPsico(notas = "Colaborador"),
            analisis = AnalisisPsico(areas = mapOf("emocional" to "Indicadores de ansiedad"), conclusiones = "Se sugiere psicoterapia"),
            plan = PlanPsico(recomendaciones = "Orientación familiar"),
        )
        val t = textosPsicoAprendibles(ev)
        assertEquals(CAMPOS_PSICO_EVAL.sorted(), t.keys.sorted())
        assertEquals("Ansiedad", t[ClaveFrase.PSICO_MOTIVO])
        assertEquals("Indicadores de ansiedad", t["psico.eval.area.emocional"])
        assertEquals("", t["psico.eval.area.intelectual"])
        assertFalse(t.values.joinToString(" ").contains(Regex("relato|madre")))
    }

    @Test fun soloViajanLosCamposQueCambiaronConSuAntes() {
        val previo = mapOf(ClaveFrase.PSICO_MOTIVO to "Ansiedad", ClaveFrase.PSICO_CONCLUSIONES to "algo")
        val ahora = mapOf(ClaveFrase.PSICO_MOTIVO to "Ansiedad, insomnio", ClaveFrase.PSICO_CONCLUSIONES to "algo", ClaveFrase.PSICO_OBSERVACION to "")
        assertEquals(listOf(CambioFrase(ClaveFrase.PSICO_MOTIVO, "Ansiedad, insomnio", "Ansiedad")), cambiosParaAprender(previo, ahora))
        assertEquals(emptyList(), cambiosParaAprender(ahora, ahora))
    }

    @Test fun rubroDeLaBaseDeIndicacionesPorLaEspecialidad() {
        val capilar = MapaCapilar(ids = listOf("cap"))
        val estetica = MapaEstetica(ids = listOf("est"))
        val fisio = MapaFisio(ids = listOf("fis"))
        assertEquals("capilar", rubroFrasesDe("cap", capilar, estetica, fisio))
        assertEquals("estetica", rubroFrasesDe("est", capilar, estetica, fisio))
        assertEquals("fisioterapia", rubroFrasesDe("fis", capilar, estetica, fisio))
        assertNull(rubroFrasesDe("med", capilar, estetica, fisio))
        assertNull(rubroFrasesDe(null, capilar, estetica, fisio))
        // Clínica solo de fisio (DALU): todo es fisio aunque no se sepa la especialidad.
        assertEquals("fisioterapia", rubroFrasesDe(null, mapaFisio = MapaFisio(solo = true)))
        val sinContexto: ContextoStaff? = null
        assertNull(sinContexto.rubroFrasesDe("fis"))
    }
}
