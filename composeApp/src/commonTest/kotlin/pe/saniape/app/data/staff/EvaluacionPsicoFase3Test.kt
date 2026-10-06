package pe.saniape.app.data.staff

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/**
 * Evaluación psicológica, FASE 3 (contrato §14): definiciones de instrumentos,
 * armado de respuestas (la app NO puntúa), resultado del servidor, perfil de
 * puntajes, retest, anexo de perfiles y borrador con IA.
 */
class EvaluacionPsicoFase3Test {

    private fun obj(texto: String): JsonObject = assertNotNull(jsonObjetoPsico(texto))

    private val INSTRUMENTOS_JSON = """
        { "ok": true, "instrumentos": [
          { "id": "phq9", "catalogo": "PHQ-9", "corto": "PHQ-9", "nombre": "Cuestionario de Salud del Paciente", "variante": null,
            "consigna": "Durante las últimas 2 semanas…",
            "opciones": [{ "valor": 0, "texto": "Ningún día" }, { "valor": 1, "texto": "Varios días" }, { "valor": 2, "texto": "Más de la mitad" }, { "valor": 3, "texto": "Casi todos" }],
            "items": [
              { "n": 1, "texto": "Poco interés", "opciones": null, "inverso": false, "puntua": true, "opcional": false, "umbralPositivo": null },
              { "n": 2, "texto": "Desanimado", "opciones": null, "inverso": false, "puntua": true, "opcional": false, "umbralPositivo": null },
              { "n": 9, "texto": "Pensamientos de muerte", "opciones": null, "inverso": false, "puntua": true, "opcional": false, "umbralPositivo": null },
              { "n": 10, "texto": "Dificultad", "opciones": [{ "valor": 0, "texto": "Nada difícil" }, { "valor": 3, "texto": "Extremadamente" }],
                "inverso": false, "puntua": false, "opcional": true, "umbralPositivo": null }
            ],
            "modo": "suma",
            "total": { "clave": "total", "nombre": "PHQ-9 total", "items": [1, 2, 9], "min": 0, "max": 27,
              "bandas": [{ "min": 0, "max": 4, "categoria": "Mínima", "nivel": 0 }, { "min": 5, "max": 9, "categoria": "Leve", "nivel": 1 },
                         { "min": 10, "max": 27, "categoria": "Moderada o más", "nivel": 2, "positivo": true }], "bandasSexo": null },
            "subescalas": [], "mayorEsPeor": true,
            "alertas": [{ "item": 9, "desde": 1, "mensaje": "Ítem 9 positivo: evaluar riesgo suicida." }],
            "cambioRelevante": { "puntos": 5, "fuente": "Löwe 2004" }, "sinTextoItems": false,
            "fuente": "Kroenke 2001", "licencia": "Pfizer: libre", "poblacion": "Adultos" },
          { "id": "asrs6", "catalogo": "ASRS", "corto": "ASRS v1.1 (cribado)", "consigna": "Use el formulario oficial",
            "opciones": [{ "valor": 0, "texto": "Nunca" }, { "valor": 4, "texto": "Muy frecuentemente" }],
            "items": [{ "n": 1, "texto": "Ítem 1 del formulario oficial (Parte A)", "umbralPositivo": 2 }],
            "modo": "positivos", "total": { "clave": "total", "nombre": "Ítems positivos", "items": [1], "min": 0, "max": 6 },
            "subescalas": [], "mayorEsPeor": true, "alertas": [], "sinTextoItems": true, "fuente": "Kessler", "licencia": "© NYU", "poblacion": "Adultos" },
          { "id": "auditc", "catalogo": "AUDIT", "corto": "AUDIT-C", "variante": "AUDIT-C (3 ítems)",
            "opciones": [{ "valor": 0, "texto": "Nunca" }, { "valor": 4, "texto": "4 o más" }],
            "items": [{ "n": 1, "texto": "¿Con qué frecuencia…?" }],
            "total": { "clave": "total", "nombre": "AUDIT-C", "items": [1], "min": 0, "max": 12,
              "bandas": [{ "min": 0, "max": 2, "categoria": "Negativo", "nivel": 0 }, { "min": 3, "max": 12, "categoria": "Positivo en mujeres", "nivel": 1 }],
              "bandasSexo": { "F": [{ "min": 0, "max": 2, "categoria": "Negativo", "nivel": 0 }, { "min": 3, "max": 12, "categoria": "Positivo", "nivel": 2 }],
                              "M": [{ "min": 0, "max": 3, "categoria": "Negativo", "nivel": 0 }, { "min": 4, "max": 12, "categoria": "Positivo", "nivel": 2 }] } } },
          { "id": "audit", "catalogo": "AUDIT", "corto": "AUDIT", "variante": "AUDIT (10 ítems)", "items": [{ "n": 1, "texto": "x" }] },
          { "id": "sin_items", "catalogo": "X", "items": [] },
          { "catalogo": "SinId", "items": [{ "n": 1 }] },
          "basura"
        ] }
    """

    private fun instrumentos() = parsearInstrumentosPsico(obj(INSTRUMENTOS_JSON))

    // ── Definiciones ─────────────────────────────────────────────────────────

    @Test
    fun parsea_instrumentos_y_descarta_los_invalidos() {
        val l = instrumentos()
        assertEquals(listOf("phq9", "asrs6", "auditc", "audit"), l.map { it.id })
        val phq = l[0]
        assertEquals(4, phq.items.size)
        assertEquals(listOf(0, 1, 2, 3), phq.opcionesDe(phq.items[0]).map { it.valor })
        // Ítem 10: opciones propias, no suma y opcional.
        assertEquals(listOf(0, 3), phq.opcionesDe(phq.items[3]).map { it.valor })
        assertFalse(phq.items[3].puntua)
        assertTrue(phq.items[3].opcional)
        assertEquals(27.0, phq.total?.max)
        assertEquals(3, phq.total?.bandas?.size)
        assertTrue(phq.total!!.bandas[2].positivo)
        assertEquals(AlertaItemPsico(9, 1, "Ítem 9 positivo: evaluar riesgo suicida."), phq.alertas.single())
        val asrs = l[1]
        assertTrue(asrs.sinTextoItems)
        assertEquals("positivos", asrs.modo)
        assertEquals(2, asrs.items[0].umbralPositivo)
        val auditc = l[2]
        assertEquals(setOf("F", "M"), auditc.total?.bandasSexo?.keys)
        assertEquals("AUDIT-C (3 ítems)", auditc.nombreVersion)
        assertEquals("PHQ-9", phq.nombreVersion)
    }

    @Test
    fun autocalculo_solo_en_tests_del_catalogo_global() {
        val l = instrumentos()
        assertEquals(listOf("phq9"), instrumentosDeTest(TestRefPsico(nombreCorto = "phq-9 "), l).map { it.id })
        // AUDIT tiene dos variantes: se elige al responder.
        assertEquals(listOf("auditc", "audit"), instrumentosDeTest(TestRefPsico(nombreCorto = "AUDIT"), l).map { it.id })
        // Un test PROPIO de la clínica con el mismo nombre no se puntúa solo.
        assertTrue(instrumentosDeTest(TestRefPsico(nombreCorto = "PHQ-9", clinicaId = "c1"), l).isEmpty())
        assertTrue(instrumentosDeTest(TestRefPsico(nombreCorto = "WISC-V"), l).isEmpty())
        // Sin definiciones (servidor sin fase 3) no hay "Responder ítems".
        assertTrue(instrumentosDeTest(TestRefPsico(nombreCorto = "PHQ-9"), null).isEmpty())
        assertTrue(instrumentosDeTest(null, l).isEmpty())
    }

    // ── Construcción de las respuestas (sin puntuar) ─────────────────────────

    @Test
    fun arma_los_valores_y_el_cuerpo_de_responder() {
        val phq = instrumentos()[0]
        var v = valoresIniciales(phq, null)
        assertEquals(listOf(null, null, null, null), v)
        assertEquals(listOf(1, 2, 9), itemsFaltantes(phq, v))   // el 10 es opcional y no suma
        v = alternarValor(v, 0, 2)
        v = alternarValor(v, 2, 1)
        assertEquals(listOf(2), itemsFaltantes(phq, v))
        // Tocar la opción marcada la quita.
        assertEquals(listOf(null, null, 1, null), alternarValor(v, 0, 2))
        // Vista previa de la alerta de riesgo (texto de la definición).
        assertEquals(listOf("Ítem 9 positivo: evaluar riesgo suicida."), alertasVistaPrevia(phq, v))
        assertTrue(alertasVistaPrevia(phq, alternarValor(v, 2, 1)).isEmpty())

        val cuerpo = jsonResponder("ta1", "phq9", v)
        assertEquals("responder", (cuerpo["accion"] as JsonPrimitive).content)
        assertEquals("ta1", (cuerpo["testAplicadoId"] as JsonPrimitive).content)
        assertEquals("phq9", (cuerpo["instrumento"] as JsonPrimitive).content)
        val valores = cuerpo["valores"] as JsonArray
        assertEquals(4, valores.size)   // uno por ítem, en orden
        assertEquals("2", (valores[0] as JsonPrimitive).content)
        assertEquals(JsonNull, valores[1])
        assertEquals("1", (valores[2] as JsonPrimitive).content)
    }

    @Test
    fun valores_iniciales_saneados_de_lo_guardado() {
        val phq = instrumentos()[0]
        val previo = RespuestasTestPsico("phq9", listOf(3, 7, null, 3, 99))
        // 7 no es una opción → null; sobran valores → se cortan; ítem 10 acepta 3.
        assertEquals(listOf(3, null, null, 3), valoresIniciales(phq, previo))
        // Respuestas de OTRO instrumento: arranca vacío.
        assertEquals(listOf(null, null, null, null), valoresIniciales(phq, RespuestasTestPsico("gad7", listOf(1, 1, 1, 1))))
        val l = instrumentos()
        assertEquals("audit", instrumentoInicial(listOf(l[2], l[3]), RespuestasTestPsico("audit", emptyList()))?.id)
        assertEquals("auditc", instrumentoInicial(listOf(l[2], l[3]), null)?.id)
    }

    @Test
    fun asrs_no_muestra_el_texto_de_los_items() {
        val l = instrumentos()
        val asrs = l[1]
        assertEquals("Ítem 1", textoItemPsico(asrs, asrs.items[0]))
        assertEquals("1. Poco interés", textoItemPsico(l[0], l[0].items[0]))
        assertFalse(muestraValorOpcion(asrs, asrs.items[0]))       // modo positivos
        assertTrue(muestraValorOpcion(l[0], l[0].items[0]))
        assertFalse(muestraValorOpcion(l[0], l[0].items[3]))       // no suma
    }

    // ── Lo que devuelve el servidor ──────────────────────────────────────────

    private val TEST_RESPONDIDO = """
        { "id": "ta1", "evaluacion_id": "e1", "test_id": "tc1", "fecha": "2026-10-07", "estado": "calificado", "en_informe": true,
          "puntajes": [{ "escala": "PHQ-9 total", "directo": "10", "transformado": "", "percentil": "", "categoria": "Depresión moderada" },
                       { "escala": "Notas a mano", "directo": "", "transformado": "", "percentil": "", "categoria": "" }],
          "global": { "puntaje": "10 / 27", "categoria": "Depresión moderada", "descripcion": "" },
          "test": { "id": "tc1", "nombre_corto": "PHQ-9", "nombre": "PHQ-9", "tipo_puntaje": "directo", "clinica_id": null },
          "respuestas": { "instrumento": "phq9", "valores": [2, 2, 1, 3, 0, 1, 0, 0, 1, null],
            "resultado": { "instrumento": "phq9", "completo": true, "faltantes": [],
              "total": { "clave": "total", "nombre": "PHQ-9 total", "puntaje": 10, "min": 0, "max": 27, "categoria": "Depresión moderada", "nivel": 2, "positivo": true },
              "subescalas": [], "categoria": "Depresión moderada",
              "alertas": ["Ítem 9 positivo (pensamientos de muerte o de hacerse daño): evaluar riesgo suicida."] },
            "sexo": null, "calculado_at": "2026-10-07T15:00:00.000Z" } }
    """

    @Test
    fun lee_respuestas_resultado_y_alertas_del_test() {
        val t = assertNotNull(leerTestAplicado(obj(TEST_RESPONDIDO)))
        val r = assertNotNull(t.respuestas)
        assertEquals("phq9", r.instrumento)
        assertEquals(10, r.valores.size)
        assertNull(r.valores[9])
        assertFalse(r.editado)
        val res = assertNotNull(r.resultado)
        assertTrue(res.completo)
        assertEquals(10.0, res.total?.puntaje)
        assertEquals(2, res.total?.nivel)
        assertEquals("10 / 27", textoPuntajeEscala(res.total))
        assertEquals(1, t.alertas.size)
        assertNull(t.test?.clinicaId)
        assertEquals("PHQ-9: 10 / 27 · Depresión moderada. Puntajes llenados (puedes editarlos).", textoTrasResponder("PHQ-9", res))
        assertEquals("Respuestas guardadas. Faltan ítems para calcular.", textoTrasResponder("PHQ-9", res.copy(completo = false)))

        // Sin respuestas (puntuado a mano) o con una forma inválida: null, y sin alertas.
        assertNull(leerTestAplicado(obj("""{ "id": "x", "respuestas": null }"""))?.respuestas)
        assertNull(leerTestAplicado(obj("""{ "id": "x", "respuestas": { "instrumento": "phq9" } }"""))?.respuestas)
        assertTrue(leerTestAplicado(obj("""{ "id": "x" }"""))!!.alertas.isEmpty())
        // Corregido a mano.
        assertTrue(leerRespuestasTest(obj("""{ "instrumento": "phq9", "valores": [], "editado": true }"""))!!.editado)
    }

    @Test
    fun respuesta_de_responder_incompleta() {
        val o = obj("""
            { "ok": true, "test": { "id": "ta1", "estado": "aplicado" },
              "resultado": { "instrumento": "gad7", "completo": false, "faltantes": [3, 4],
                "total": { "clave": "total", "nombre": "GAD-7 total", "puntaje": null, "min": 0, "max": 21, "categoria": null, "nivel": null, "positivo": false },
                "subescalas": [], "categoria": null, "alertas": [] } }
        """)
        val r = assertNotNull(resultadoDeResponder(o))
        assertFalse(r.completo)
        assertEquals(listOf(3, 4), r.faltantes)
        assertEquals("", textoPuntajeEscala(r.total))
        assertEquals("ta1", testDeRespuesta(o)?.id)
        assertEquals("31 (10–40)", textoPuntajeEscala(ResultadoEscalaPsico(puntaje = 31.0, min = 10.0, max = 40.0)))
        assertEquals("7.5", numeroPsico(7.5))
    }

    @Test
    fun instrumentos_segun_el_status() {
        assertIs<EvaluacionPsicoRepo.Instrumentos.Ok>(EvaluacionPsicoRepo.aInstrumentos(200, INSTRUMENTOS_JSON))
        assertEquals(EvaluacionPsicoRepo.Instrumentos.NoDisponible, EvaluacionPsicoRepo.aInstrumentos(404, "<html>"))
        assertEquals(EvaluacionPsicoRepo.Instrumentos.Fallo, EvaluacionPsicoRepo.aInstrumentos(500, null))
        assertEquals(EvaluacionPsicoRepo.Instrumentos.Fallo, EvaluacionPsicoRepo.aInstrumentos(200, "no json"))
    }

    // ── Perfil ───────────────────────────────────────────────────────────────

    @Test
    fun perfil_de_instrumento_usa_los_puntajes_del_test() {
        val l = instrumentos()
        val t = assertNotNull(leerTestAplicado(obj(TEST_RESPONDIDO)))
        val p = assertNotNull(perfilDeTest(t, l))
        assertEquals("PHQ-9", p.titulo)
        assertEquals("Puntaje directo", p.unidad)
        assertEquals(EjePerfilPsico(0.0, 27.0), p.eje)
        assertEquals(3, p.bandas.size)                     // una sola escala → bandas de fondo
        val b = p.barras.single()
        assertEquals(10.0, b.valor)
        assertEquals("10 / 27", b.texto)
        assertEquals(2, b.nivel)

        // Corregido a mano: valor y categoría salen de `puntajes`; el color, de las bandas.
        val corregido = t.copy(puntajes = listOf(PuntajeEscalaPsico("PHQ-9 total", directo = "4", categoria = "Mínima (revisado)")))
        val pc = assertNotNull(perfilDeTest(corregido, l)).barras.single()
        assertEquals(4.0, pc.valor)
        assertEquals("Mínima (revisado)", pc.categoria)
        assertEquals("4 / 27", pc.texto)
        assertEquals(0, pc.nivel)
        // Sin definiciones: el nivel del servidor no se usa con un valor cambiado.
        assertNull(assertNotNull(perfilDeTest(corregido, null)).barras.single().nivel)
        assertEquals(2, assertNotNull(perfilDeTest(t, null)).barras.single().nivel)
        // Categoría vacía en la tabla → la de la banda; aunque el resultado esté incompleto.
        val sinCategoria = t.copy(
            puntajes = listOf(PuntajeEscalaPsico("phq-9 TOTAL", directo = "6")),
            respuestas = t.respuestas!!.copy(resultado = t.respuestas!!.resultado!!.copy(completo = false)),
        )
        val ps = assertNotNull(perfilDeTest(sinCategoria, l)).barras.single()
        assertEquals("Leve", ps.categoria)
        assertEquals(1, ps.nivel)
        // Sin filas con número: se grafica como cualquier test (aquí, directo → sin filas → null).
        assertNull(perfilDeTest(t.copy(puntajes = emptyList()), l))
    }

    @Test
    fun perfil_normalizado_directo_y_cualitativo() {
        fun test(tipo: String, vararg p: PuntajeEscalaPsico) = TestAplicadoPsico(
            id = "t", fecha = "2026-10-01", puntajes = p.toList(), test = TestRefPsico(nombreCorto = "MMPI-2", tipoPuntaje = tipo),
        )
        val t = assertNotNull(perfilDeTest(test("T", PuntajeEscalaPsico("Hs", transformado = "T 65"), PuntajeEscalaPsico("D", transformado = "115"),
            PuntajeEscalaPsico("Pt", transformado = "—"))))
        assertEquals(50.0, t.media)
        assertEquals(EjePerfilPsico(20.0, 115.0), t.eje)    // el 115 agranda el eje
        assertEquals(listOf("T 65", "T 115"), t.barras.map { it.texto })
        assertEquals("Alto", t.barras[0].categoria)
        assertEquals(2, t.barras[0].nivel)
        // La categoría escrita por la psicóloga gana a la descriptiva.
        val ci = assertNotNull(perfilDeTest(test("CI", PuntajeEscalaPsico("CIT", transformado = "98", categoria = "Promedio (Wechsler)"))))
        assertEquals("Promedio (Wechsler)", ci.barras[0].categoria)
        val pc = assertNotNull(perfilDeTest(test("percentil", PuntajeEscalaPsico("Total", percentil = "Pc 30"))))
        assertEquals(30.0, pc.barras[0].valor)
        val d = assertNotNull(perfilDeTest(test("directo", PuntajeEscalaPsico("A", directo = "7,5"), PuntajeEscalaPsico("B", directo = "12"))))
        assertNull(d.media)
        assertTrue(d.bandas.isEmpty())
        assertEquals(EjePerfilPsico(0.0, 12.0), d.eje)
        assertNull(perfilDeTest(test("cualitativo", PuntajeEscalaPsico("A", directo = "5"))))
        assertNull(perfilDeTest(test("directo", PuntajeEscalaPsico("A", directo = "alto"))))
        assertEquals(65.0, numeroDePuntaje("T 65"))
        assertNull(numeroDePuntaje("II+ sin número"))
    }

    @Test
    fun geometria_del_grafico() {
        val p = PerfilTestPsico(
            testAplicadoId = "t", titulo = "X", eje = EjePerfilPsico(0.0, 27.0),
            bandas = listOf(BandaPerfilPsico(0.0, 4.0, "Mínima", 0), BandaPerfilPsico(5.0, 27.0, "Más", 2)),
            barras = listOf(BarraPerfilPsico("Total", 13.5, 0.0, 27.0, "13.5 / 27")),
        )
        val g = geometriaPerfil(p)
        assertFalse(g.porcentaje)
        assertEquals(0.5f, g.filas[0].hasta)
        assertEquals(listOf("0", "5", "10", "15", "20", "25"), g.ticks.map { it.texto })
        // La banda llega al "desde" de la siguiente (sin huecos entre enteros).
        assertEquals(5f / 27f, g.bandas[0].hasta)
        assertEquals(1f, g.bandas[1].hasta)
        // Rangos distintos: % del máximo de cada escala.
        val sinEje = geometriaPerfil(p.copy(eje = null, bandas = emptyList(), barras = listOf(BarraPerfilPsico("A", 5.0, 0.0, 10.0, "5 / 10"))))
        assertTrue(sinEje.porcentaje)
        assertEquals(0.5f, sinEje.filas[0].hasta)
        assertEquals("100%", sinEje.ticks.last().texto)
        assertEquals("Cada barra, en % del máximo de su escala", leyendaPerfil(p.copy(eje = null, bandas = emptyList())))
        assertEquals(0xFFB91C1C, colorBarraPsico(9))
        assertEquals(COLOR_BARRA_PSICO, colorBarraPsico(null))
    }

    // ── Retest ───────────────────────────────────────────────────────────────

    @Test
    fun retest_lee_la_comparacion_del_servidor() {
        val r = parsearRetestPsico(obj("""
            { "ok": true, "actual": { "id": "ta2", "fecha": "2026-10-01" },
              "anteriores": [{ "id": "ta1", "fecha": "2026-07-01", "global": { "puntaje": "15 / 27", "categoria": "Moderadamente grave" } }, { "sin": "id" }],
              "comparacion": {
                "anterior": { "id": "ta1", "fecha": "2026-07-01", "forma": null, "baremo": null, "global": "15 / 27" },
                "actual": { "id": "ta2", "fecha": "2026-10-01", "forma": null, "baremo": null, "global": "8 / 27" },
                "diasEntre": 92, "unidad": "Puntaje directo",
                "filas": [
                  { "escala": "PHQ-9 total", "antes": 15, "despues": 8, "diferencia": -7, "categoriaAntes": "Mod. grave", "categoriaDespues": "Leve", "sentido": "mejora", "relevante": true },
                  { "escala": "Otra", "antes": null, "despues": 3, "diferencia": null, "categoriaAntes": "", "categoriaDespues": "", "sentido": null, "relevante": null }
                ],
                "avisos": ["La forma o el baremo no son los mismos: compara con cautela."] } }
        """))
        assertEquals("ta2", r.actual?.id)
        assertEquals(listOf("ta1"), r.anteriores.map { it.id })
        assertEquals("01/07/2026 · 15 / 27 · Moderadamente grave", etiquetaAplicacionPsico(r.anteriores[0]))
        val c = assertNotNull(r.comparacion)
        assertEquals(92, c.diasEntre)
        assertEquals(1, c.avisos.size)
        val (f1, f2) = c.filas
        assertEquals(-7.0, f1.diferencia)
        assertEquals("-7", textoDiferenciaPsico(f1.diferencia))
        assertEquals("▼ mejora · clínicamente relevante", textoSentidoPsico(f1))
        // Sin sentido del servidor no se dice mejora/empeora.
        assertNull(textoSentidoPsico(f2))
        assertEquals("—", textoDiferenciaPsico(f2.diferencia))
        assertTrue(mostrarCambioRetest(c))
        assertFalse(mostrarCambioRetest(c.copy(filas = listOf(f2))))
        // Rosenberg: subir es mejorar → la flecha sigue al signo.
        assertEquals("▲ mejora", textoSentidoPsico(FilaComparacionPsico("Autoestima", 20.0, 26.0, 6.0, sentido = "mejora", relevante = null)))
        assertEquals("+6", textoDiferenciaPsico(6.0))

        // Sin anteriores: comparación null.
        val vacio = parsearRetestPsico(obj("""{ "ok": true, "actual": { "id": "ta2" }, "anteriores": [], "comparacion": null }"""))
        assertTrue(vacio.anteriores.isEmpty())
        assertNull(vacio.comparacion)
    }

    @Test
    fun retest_errores_y_servidor_sin_fase3() {
        val e = assertIs<EvaluacionPsicoRepo.Retest.Error>(EvaluacionPsicoRepo.aRetest(404, "<html>"))
        assertEquals("Comparar con una aplicación anterior aún no está disponible. Inténtalo más tarde.", e.mensaje)
        val sinAcceso = assertIs<EvaluacionPsicoRepo.Retest.Error>(
            EvaluacionPsicoRepo.aRetest(404, """{ "ok": false, "codigo": "APLICACION_ANTERIOR_NO_ENCONTRADA", "error": "x" }"""))
        assertEquals("No se encontró esa aplicación anterior (o no tienes acceso).", sinAcceso.mensaje)
        assertIs<EvaluacionPsicoRepo.Retest.Ok>(EvaluacionPsicoRepo.aRetest(200, """{ "ok": true, "anteriores": [] }"""))
        // Responder ítems en un servidor sin la acción (400 DATOS_INVALIDOS).
        assertEquals("Responder ítems aún no está disponible. Inténtalo más tarde.",
            mensajeErrorAccionFase2("Responder ítems", 400, "DATOS_INVALIDOS", "Acción no válida"))
        assertEquals("Responder ítems aún no está disponible. Inténtalo más tarde.", mensajeErrorPsico(409, "AUTOCALCULO_NO_DISPONIBLE", null))
    }

    // ── Informe: anexo de perfiles e IA ──────────────────────────────────────

    @Test
    fun anexo_de_perfiles_en_el_contenido() {
        val i = assertNotNull(leerInforme(obj("""
            { "id": "i1", "estado": "borrador", "contenido": { "filiacion": {}, "secciones": { "motivo": "M" },
              "anexoPerfiles": { "incluir": true, "perfiles": [
                { "testAplicadoId": "ta1", "titulo": "PHQ-9", "fecha": "2026-10-07", "unidad": "Puntaje directo", "eje": { "min": 0, "max": 27 },
                  "media": null, "bandas": [{ "desde": 0, "hasta": 4, "nombre": "Mínima", "nivel": 0 }],
                  "barras": [{ "escala": "PHQ-9 total", "valor": 10, "min": 0, "max": 27, "texto": "10 / 27", "categoria": "Moderada", "nivel": 2 }],
                  "instrumento": "phq9" },
                { "titulo": "Sin barras", "barras": [] } ] },
              "asistido_ia": { "secciones": ["motivo"], "modelo": "llama3", "fecha": "2026-10-10", "sinConsentimientoConfirmado": true } } }
        """)))
        val a = assertNotNull(i.contenido.anexoPerfiles)
        assertTrue(a.incluir)
        val p = a.perfiles.single()
        assertEquals(EjePerfilPsico(0.0, 27.0), p.eje)
        assertEquals(2, p.barras.single().nivel)
        assertEquals("llama3", i.contenido.asistidoIa?.modelo)
        assertTrue(seccionAsistidaIa(i.contenido, "motivo"))
        assertFalse(seccionAsistidaIa(i.contenido, "antecedentes"))

        // Al guardar solo viaja `incluir` (los perfiles los calcula el servidor) y la marca de IA.
        val j = jsonContenidoInforme(conAnexoPerfiles(i.contenido, false))
        val anexo = j["anexoPerfiles"] as JsonObject
        assertEquals("false", (anexo["incluir"] as JsonPrimitive).content)
        assertNull(anexo["perfiles"])
        assertEquals("motivo", ((j["asistido_ia"] as JsonObject)["secciones"] as JsonArray).single().let { (it as JsonPrimitive).content })
        // Sin anexo ni IA en el contenido (servidor sin fase 3): no viajan.
        val viejo = jsonContenidoInforme(ContenidoInformePsico())
        assertNull(viejo["anexoPerfiles"])
        assertNull(viejo["asistido_ia"])
        assertFalse(ofrecerAnexoPerfiles(false, ContenidoInformePsico()))
        assertTrue(ofrecerAnexoPerfiles(true, ContenidoInformePsico()))
        assertTrue(ofrecerAnexoPerfiles(false, i.contenido))
    }

    @Test
    fun propuestas_de_ia_y_aceptar_por_seccion() {
        val p = parsearPropuestasIa(obj("""
            { "ok": true, "propuestas": { "motivo": " Texto M ", "impresionDiagnostica": "nunca", "conclusiones": "", "resultados": "R" },
              "secciones": ["motivo", "resultados"], "modelo": "llama3.2:3b", "consentimientoIA": false }
        """))
        assertEquals(mapOf("motivo" to "Texto M", "resultados" to "R"), p.propuestas)
        assertEquals("llama3.2:3b", p.modelo)
        assertFalse(p.consentimientoIA)

        val base = ContenidoInformePsico(secciones = mapOf("resultados" to "ya escrito"))
        val c1 = aceptarPropuestaIa(base, "motivo", "  Texto M ", "llama3.2:3b", "2026-10-10", sinConsentimientoConfirmado = false)
        assertEquals("Texto M", c1.secciones["motivo"])
        assertEquals(AsistidoIaPsico(listOf("motivo"), "llama3.2:3b", "2026-10-10", false), c1.asistidoIa)
        // Una sección con texto no se pisa sin "reemplazar".
        assertEquals(c1, aceptarPropuestaIa(c1, "resultados", "R", "m", "2026-10-11", false))
        val c2 = aceptarPropuestaIa(c1, "resultados", "R", "", "2026-10-11", sinConsentimientoConfirmado = true, reemplazar = true)
        assertEquals("R", c2.secciones["resultados"])
        assertEquals(listOf("motivo", "resultados"), c2.asistidoIa?.secciones)
        assertEquals("llama3.2:3b", c2.asistidoIa?.modelo)     // sin modelo nuevo, queda el anterior
        assertTrue(c2.asistidoIa!!.sinConsentimientoConfirmado)
        // La marca solo crece: una vez confirmado sin consentimiento, queda.
        assertTrue(aceptarPropuestaIa(c2, "conclusiones", "C", "m", "2026-10-12", false).asistidoIa!!.sinConsentimientoConfirmado)
        // Nunca la impresión diagnóstica ni texto vacío.
        assertEquals(c2, aceptarPropuestaIa(c2, "impresionDiagnostica", "X", "m", "2026-10-12", false))
        assertEquals(c2, aceptarPropuestaIa(c2, "conclusiones", "   ", "m", "2026-10-12", false))
        assertEquals("El borrador con IA no está incluido en el plan de la clínica.", mensajeErrorPsico(403, "PLAN_SIN_IA", "x"))
        // 503 IA_NO_DISPONIBLE: el texto del servidor tal cual.
        assertEquals("El asistente de IA no está disponible en este momento.", mensajeErrorPsico(503, "IA_NO_DISPONIBLE", "El asistente de IA no está disponible en este momento."))
    }
}
