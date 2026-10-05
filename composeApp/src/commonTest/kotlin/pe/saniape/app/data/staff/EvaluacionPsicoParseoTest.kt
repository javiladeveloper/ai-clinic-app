package pe.saniape.app.data.staff

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * Evaluación psicológica: parseo del contrato (docs/app-contrato-evaluacion-psico.md
 * en la web), los cuerpos que se mandan y las reglas de presentación puras.
 */
class EvaluacionPsicoParseoTest {

    private fun obj(texto: String): JsonObject = assertNotNull(jsonObjetoPsico(texto))

    private val espacioCompleto = """
    {
      "ok": true, "esEvaluacionPsico": true, "soloLectura": false,
      "tratamiento": { "id": "t1", "pacienteId": "p1", "terapeutaId": "ter1", "estado": "Activo",
        "totalSesiones": 3, "sesionesCompletadas": 1,
        "procedimiento": { "id": "pr1", "nombre": "Evaluación psicológica", "devolucion": "adicional", "devolucionProcedimientoId": null } },
      "paciente": { "id": "p1", "nombre": "Ana Pérez", "dni": "12345678", "fechaNacimiento": "2017-06-20",
        "edadMeses": 99, "edadTexto": "8 años 3 meses", "ocupacion": "Estudiante" },
      "evaluacion": {
        "id": "e1", "paciente_id": "p1", "tratamiento_id": "t1", "terapeuta_id": "ter1", "estado": "abierta",
        "entrevista": { "motivo": "Bajo rendimiento", "solicitante": "Colegio", "familia": "Vive con la madre",
          "genogramaDocumentoId": "d9", "campoRaro": 5 },
        "fuentes": [ { "id": "f1", "tipo": "colegio", "nombre": "Tutora", "fecha": "2026-10-02", "resumen": "Se distrae",
          "documentoIds": ["d1"] }, { "tipo": "desconocido" }, "basura" ],
        "observacion": { "seleccion": { "actitud": ["Colaboradora", null, "Tímida"] }, "notas": "Inquieta" },
        "analisis": { "areas": { "intelectual": "Promedio", "emocional": null }, "conclusiones": "TDAH probable" },
        "diagnosticos": [ { "codigo": "F90.0", "descripcion": "Perturbación de la actividad y de la atención", "tipo": "P" },
          { "codigo": null, "descripcion": "", "tipo": "P" } ],
        "plan": { "objetivos": ["Mejorar atención"], "enfoques": ["tcc", "parental"], "enfoqueOtro": "",
          "numeroSesiones": "12", "frecuencia": "semanal", "sesionesFamilia": 2, "reevaluarAlCerrar": false,
          "procedimientoId": "pr2", "precio": 960, "recomendaciones": "", "incluirEnInforme": true, "tratamientoCreadoId": null },
        "created_at": "x", "updated_at": "y"
      },
      "estados": { "entrevista": "completo", "fuentes": "vacio", "observacion": "en_curso",
        "tests": "en_curso", "analisis": "vacio", "plan": "otro" },
      "tests": [ {
        "id": "ta1", "evaluacion_id": "e1", "test_id": "c1", "fecha": "2026-10-05", "edad_meses": 99,
        "informante": null, "modalidad": "presencial", "forma": null, "baremo": "Lima 2002", "validez": "valido",
        "puntajes": [ { "escala": "ICV", "directo": 23, "transformado": "112", "percentil": "", "categoria": "Medio alto" } ],
        "global": { "puntaje": "CIT 98", "categoria": "Promedio", "descripcion": "" },
        "interpretacion": null, "observaciones": null, "estado": "calificado", "en_informe": false,
        "test": { "id": "c1", "nombre_corto": "WISC-V", "nombre": "Escala Wechsler", "categoria": "Inteligencia y cognitivo",
          "tipo_puntaje": "CI", "genera_imagen": false } } ],
      "fotos": [ { "id": "d1", "nombre": "HTP - casa.jpg", "path": "p1/t1/a.jpg", "tipo": "jpg", "testAplicadoId": null,
          "uso": "fuente", "created_at": "z" },
        { "id": "d2", "nombre": "Hoja", "verUrl": "/api/staff/evaluacion-psico/foto/ver?id=d2", "tipo": "pdf", "uso": "test" },
        { "id": "d3", "nombre": "Sin ruta" } ],
      "informe": { "id": "i1", "evaluacion_id": "e1", "version": 1, "estado": "borrador",
        "contenido": { "filiacion": { "nombre": "Ana Pérez", "edad": "8 años 3 meses" },
          "secciones": { "motivo": "Bajo rendimiento", "conclusiones": "" }, "lugar": "Tacna", "fecha": "2026-10-15" },
        "documento_id": null, "emitido_at": null, "emitido_por": null },
      "informePdf": null,
      "planPrefill": { "procedimiento_id": "pr2", "terapeuta_id": "ter1", "modalidad": "Paquete",
        "total_sesiones": 12, "precio_paquete": 960, "diagnostico": "F90.0 Perturbación…" },
      "sugerenciaSesiones": { "clave": "conducta_infantil", "nombre": "Problemas de conducta infantil (con padres)", "sesiones": 12 },
      "servicios": [ { "id": "pr2", "nombre": "Psicoterapia individual", "precio": 80 }, { "nombre": "sin id" } ]
    }
    """

    @Test
    fun espacio_completo_del_contrato() {
        val e = assertNotNull(parsearEspacioPsico(obj(espacioCompleto)))
        assertTrue(e.esEvaluacionPsico)
        assertFalse(e.soloLectura)
        val t = assertNotNull(e.tratamiento)
        assertEquals(3, t.totalSesiones)
        assertEquals("adicional", t.devolucion)
        assertEquals("Ana Pérez", e.paciente?.nombre)
        assertEquals("8 años 3 meses", e.paciente?.edadTexto)

        val ev = assertNotNull(e.evaluacion)
        assertEquals("Bajo rendimiento", ev.entrevista.texto("motivo"))
        assertEquals("", ev.entrevista.texto("salud"))
        assertEquals("d9", ev.entrevista.genogramaDocumentoId)
        // La fuente rota se descarta; la de tipo desconocido queda como "otro" con id estable.
        assertEquals(listOf("f1", "f1"), ev.fuentes.map { it.id })
        assertEquals("otro", ev.fuentes[1].tipo)
        assertEquals(listOf("d1"), ev.fuentes[0].documentoIds)
        // Un null dentro de la selección no tumba la observación.
        assertEquals(listOf("Colaboradora", "Tímida"), ev.observacion.elegidas("actitud"))
        assertEquals(mapOf("intelectual" to "Promedio"), ev.analisis.areas)
        assertEquals(1, ev.diagnosticos.size)
        // Número como texto y texto como número: los dos se leen.
        assertEquals(12, ev.plan.numeroSesiones)
        assertEquals("2", ev.plan.sesionesFamilia)
        assertEquals(960.0, ev.plan.precio)
        assertTrue(ev.plan.incluirEnInforme)

        assertEquals("completo", e.estados.de("entrevista"))
        assertEquals("en_curso", e.estados.de("tests"))
        assertEquals("vacio", e.estados.de("plan"))   // valor desconocido → vacío

        val ta = e.tests.single()
        assertEquals("WISC-V", ta.nombreCorto)
        assertEquals("23", ta.puntajes.single().directo)
        assertEquals("", ta.informante)
        assertEquals("calificado", ta.estado)
        assertFalse(ta.enInforme)
        assertEquals("CIT 98", ta.global.puntaje)

        // La foto sin ruta ni URL se descarta; la servida por endpoint vale aunque no tenga path.
        assertEquals(listOf("d1", "d2"), e.fotos.map { it.id })
        assertEquals("/api/staff/evaluacion-psico/foto/ver?id=d2", e.fotos[1].url)

        val inf = assertNotNull(e.informe)
        assertFalse(inf.emitido)
        assertEquals("Tacna", inf.contenido.lugar)
        assertNull(e.informePdf)

        val pre = assertNotNull(e.planPrefill)
        assertEquals(12, pre.totalSesiones)
        assertEquals(960.0, pre.precioPaquete)
        assertEquals("Paquete", pre.modalidad)
        assertEquals(12, e.sugerenciaSesiones?.sesiones)
        assertEquals(listOf("pr2"), e.servicios.map { it.id })
    }

    @Test
    fun no_aplica_y_campos_nulos() {
        val no = assertNotNull(parsearEspacioPsico(obj("""{ "ok": true, "esEvaluacionPsico": false }""")))
        assertFalse(no.esEvaluacionPsico)
        assertNull(no.evaluacion)

        // Evaluación todavía sin abrir y todo lo demás en null.
        val vacio = assertNotNull(parsearEspacioPsico(obj("""
            { "ok": true, "esEvaluacionPsico": true, "soloLectura": null, "tratamiento": null, "paciente": null,
              "evaluacion": null, "estados": null, "tests": null, "fotos": null, "informe": null, "informePdf": null,
              "planPrefill": null, "sugerenciaSesiones": null, "servicios": null }
        """)))
        assertNull(vacio.evaluacion)
        assertEquals("vacio", vacio.estados.de("entrevista"))
        assertTrue(vacio.tests.isEmpty() && vacio.fotos.isEmpty() && vacio.servicios.isEmpty())

        // Componentes JSONB en null o con otra forma: valores por defecto.
        val ev = assertNotNull(leerEvaluacion(obj("""
            { "id": "e1", "entrevista": null, "fuentes": {}, "observacion": [], "analisis": "x", "plan": null, "diagnosticos": null }
        """)))
        assertTrue(ev.fuentes.isEmpty())
        assertEquals(PlanPsico(), ev.plan)
        assertEquals("abierta", ev.estado)
        assertNull(parsearEspacioPsico(null))
    }

    @Test
    fun emitido_y_respuestas_de_escrituras() {
        val e = assertNotNull(parsearEspacioPsico(obj("""
            { "ok": true, "esEvaluacionPsico": true, "soloLectura": true,
              "evaluacion": { "id": "e1", "estado": "cerrada" },
              "informe": { "id": "i1", "estado": "emitido", "version": 1, "documento_id": "doc1", "emitido_at": "2026-10-15T15:00:00Z",
                "contenido": { "filiacion": {}, "secciones": {} } },
              "informePdf": { "id": "doc1", "path": "p1/t1/informe.pdf", "nombre": "Informe psicológico — Ana.pdf" } }
        """)))
        assertTrue(e.soloLectura)
        assertTrue(e.informe!!.emitido)
        assertEquals("p1/t1/informe.pdf", e.informePdf?.path)

        val g = parsearGuardadoPsico(obj("""{ "ok": true, "estados": { "plan": "completo" }, "sugerenciaSesiones": null }"""))
        assertEquals("completo", g.estados?.de("plan"))
        assertNull(g.sugerencia)

        assertEquals("ta9", testDeRespuesta(obj("""{ "ok": true, "test": { "id": "ta9", "estado": "raro" } }"""))?.id)
        assertEquals("aplicado", testDeRespuesta(obj("""{ "ok": true, "test": { "id": "ta9", "estado": "raro" } }"""))?.estado)
        assertEquals("d5", fotoDeRespuesta(obj("""{ "ok": true, "foto": { "id": "d5", "path": "x.jpg", "uso": "genograma" } }"""))?.id)
        assertEquals(4, totalSesionesDeRespuesta(obj("""{ "ok": true, "totalSesiones": 4 }""")))
        assertNull(totalSesionesDeRespuesta(obj("""{ "ok": true }""")))
        assertEquals("nuevo", idDeRespuesta(obj("""{ "ok": true, "id": "nuevo" }""")))
        assertEquals("i2", informeDeRespuesta(obj("""{ "ok": true, "informe": { "id": "i2" } }"""))?.id)
    }

    @Test
    fun catalogo_agrupado_y_filtrado() {
        val cat = parsearCatalogoPsico(obj("""
            { "ok": true, "tests": [
              { "id": "a", "clinica_id": null, "nombre_corto": "HTP", "nombre": "Casa-Árbol-Persona", "categoria": "Proyectivos y dibujos",
                "poblacion": ["ninos", "adolescentes", "adultos"], "tipo_puntaje": "cualitativo", "genera_imagen": true,
                "estructura_escalas": [], "orden": 1 },
              { "id": "b", "nombre_corto": "WISC-V", "nombre": "Wechsler niños", "categoria": "Inteligencia y cognitivo",
                "poblacion": ["ninos"], "estructura_escalas": [ { "nombre": "ICV", "grupo": "Índices" }, { "nombre": " " }, "IVE" ], "orden": 4 },
              { "id": "c", "nombre_corto": "Machover", "categoria": "Proyectivos y dibujos", "poblacion": ["adultos"], "orden": 5 },
              { "id": "d", "clinica_id": "cl1", "nombre_corto": "Propio", "categoria": null, "poblacion": null },
              { "nombre_corto": "sin id" }
            ] }
        """))
        assertEquals(listOf("a", "b", "c", "d"), cat.map { it.id })
        assertEquals(listOf("ICV", "IVE"), cat[1].escalas.map { it.nombre })
        assertEquals("Otros", cat[3].categoria)
        assertTrue(cat[0].generaImagen)

        // Grupos en el orden del servidor (ranking primero).
        val todos = agruparCatalogoPsico(cat, "")
        assertEquals(listOf("Proyectivos y dibujos", "Inteligencia y cognitivo", "Otros"), todos.map { it.first })
        assertEquals(listOf("a", "c"), todos[0].second.map { it.id })
        // Búsqueda sin tildes ni mayúsculas, en nombre, nombre corto o categoría.
        assertEquals(listOf("a"), agruparCatalogoPsico(cat, "arbol").flatMap { it.second }.map { it.id })
        assertEquals(listOf("b"), agruparCatalogoPsico(cat, "wisc").flatMap { it.second }.map { it.id })
        assertEquals(listOf("a", "c"), agruparCatalogoPsico(cat, "PROYECTIVOS").flatMap { it.second }.map { it.id })
        // Por población.
        assertEquals(listOf("a", "c"), agruparCatalogoPsico(cat, "", "adultos").flatMap { it.second }.map { it.id })
        assertEquals(listOf("ICV", "IVE"), puntajesDesdeEscalas(cat[1].escalas).map { it.escala })
    }

    @Test
    fun cuerpos_que_van_al_servidor() {
        val ent = jsonEntrevista(EntrevistaPsico().con("motivo", "M").con("derivadoPor", "Dr. X"))
        assertEquals("M", ent["motivo"]?.jsonPrimitive?.content)
        assertEquals("Dr. X", ent["derivadoPor"]?.jsonPrimitive?.content)
        assertEquals("", ent["salud"]?.jsonPrimitive?.content)   // el componente va COMPLETO
        assertEquals(JsonNull, ent["genogramaDocumentoId"])

        val plan = jsonPlan(PlanPsico(objetivos = listOf("Uno", " ", ""), enfoques = listOf("tcc"), numeroSesiones = 12, precio = 960.0,
            tratamientoCreadoId = "no-va"))
        assertEquals(listOf("Uno"), plan["objetivos"]!!.jsonArray.map { it.jsonPrimitive.content })
        assertEquals("12", plan["numeroSesiones"]?.jsonPrimitive?.content)
        assertFalse("tratamientoCreadoId" in plan)   // lo pone /plan-tratamiento, no `guardar`

        val obs = jsonObservacion(ObservacionPsico().alternar("actitud", "Tímida").alternar("afecto", "Triste").alternar("afecto", "Triste"))
        assertEquals(setOf("actitud"), obs["seleccion"]!!.jsonObject.keys)   // sin aspectos vacíos

        val fu = jsonFuentes(listOf(FuentePsico("f1", "colegio", documentoIds = listOf("d1"))))
        assertEquals("f1", (fu[0] as JsonObject)["id"]?.jsonPrimitive?.content)
        assertEquals(JsonNull, (fu[0] as JsonObject)["fecha"])

        val dx = jsonDiagnosticos(listOf(DiagnosticoCie("F41.1", "Ansiedad generalizada", "D")))
        assertEquals("D", (dx[0] as JsonObject)["tipo"]?.jsonPrimitive?.content)

        // Test: snake_case, como la fila.
        val dt = jsonDatosTest(TestAplicadoPsico(id = "x", fecha = "2026-10-05", enInforme = false,
            puntajes = listOf(PuntajeEscalaPsico("ICV", directo = "23"))))
        assertEquals("false", dt["en_informe"]?.jsonPrimitive?.content)
        assertEquals("23", ((dt["puntajes"] as JsonArray)[0] as JsonObject)["directo"]?.jsonPrimitive?.content)
        assertFalse("id" in dt)

        val inf = jsonContenidoInforme(ContenidoInformePsico(secciones = mapOf("motivo" to "M")))
        assertEquals(SECCIONES_INFORME.size, inf["secciones"]!!.jsonObject.size)
        assertEquals(CAMPOS_FILIACION.size, inf["filiacion"]!!.jsonObject.size)
    }

    @Test
    fun errores_legibles_y_endpoint_ausente() {
        assertTrue(mensajeErrorPsico(403, "SIN_ACCESO_EVALUACION", "x").contains("profesional tratante"))
        assertTrue(mensajeErrorPsico(409, "EVALUACION_CERRADA", null).contains("solo lectura"))
        assertTrue(mensajeErrorPsico(401, null, null).contains("sesión"))
        // Las escrituras idempotentes fallan SIN código: se muestra su texto.
        assertEquals("No se encontró el tratamiento", mensajeErrorPsico(404, null, "No se encontró el tratamiento"))
        // La página 404 de Next (endpoint aún no desplegado).
        assertTrue(mensajeErrorPsico(404, null, "No se pudo completar (HTTP 404).").contains("aún no está disponible"))

        val c = EvaluacionPsicoRepo.aCarga(404, "<!DOCTYPE html><html>404</html>")
        assertTrue(c is EvaluacionPsicoRepo.Carga.Error && c.mensaje.contains("aún no está disponible"))
        val sin = EvaluacionPsicoRepo.aCarga(403, """{ "error": "Sin acceso", "codigo": "SIN_ACCESO_EVALUACION" }""")
        assertEquals("SIN_ACCESO_EVALUACION", (sin as EvaluacionPsicoRepo.Carga.Error).codigo)
        val ok = EvaluacionPsicoRepo.aCarga(200, """{ "ok": true, "esEvaluacionPsico": false }""")
        assertTrue(ok is EvaluacionPsicoRepo.Carga.Ok && !ok.espacio.esEvaluacionPsico)

        assertEquals("https://x/y", EvaluacionPsicoRepo.urlDeRespuesta("""{ "url": "https://x/y" }"""))
        assertNull(EvaluacionPsicoRepo.urlDeRespuesta("""{ "url": "/relativa" }"""))
        assertNull(EvaluacionPsicoRepo.urlDeRespuesta("no es json"))
    }

    @Test
    fun quien_ve_y_quien_agrega_citas() {
        assertTrue(puedeVerEvaluacionPsico("Admin", null, null))
        assertTrue(puedeVerEvaluacionPsico("Psicólogo", "yo", "yo"))
        // Atendió una de sus citas (o está en su equipo).
        assertTrue(puedeVerEvaluacionPsico("Psicólogo", "yo", "otra", listOf("x", "yo")))
        assertFalse(puedeVerEvaluacionPsico("Psicólogo", "yo", "otra", listOf("x", null)))
        // Recepción (sin agenda propia) no ve el contenido.
        assertFalse(puedeVerEvaluacionPsico("Recepcionista", null, "otra"))

        assertTrue(puedeAgregarCitaEvaluacion(true, false, "Activo", false))
        assertTrue(puedeAgregarCitaEvaluacion(false, true, "Completado", false))
        assertFalse(puedeAgregarCitaEvaluacion(false, false, "Activo", false))
        assertFalse(puedeAgregarCitaEvaluacion(true, true, "Cancelado", false))
        assertFalse(puedeAgregarCitaEvaluacion(true, true, "Activo", fichaInactiva = true))
    }

    @Test
    fun textos_y_ayudas_puras() {
        assertEquals("8 años 3 meses", textoEdadMeses(99))
        assertEquals("1 año", textoEdadMeses(12))
        assertEquals("5 meses", textoEdadMeses(5))
        assertEquals("1 año 1 mes", textoEdadMeses(13))
        assertEquals("", textoEdadMeses(null))
        assertEquals("05/10/2026", fechaDmyPsico("2026-10-05"))
        assertEquals("15/10/2026", fechaDmyPsico("2026-10-15T15:00:00Z"))
        assertEquals("", fechaDmyPsico(null))

        val t = TratamientoEvalPsico("t", totalSesiones = 3, sesionesCompletadas = 1, devolucion = "incluida")
        assertEquals("cita 2 de 3 · la última es la devolución", textoCitaDeEvaluacion(t))
        assertEquals("cita 3 de 3", textoCitaDeEvaluacion(t.copy(sesionesCompletadas = 5, devolucion = null)))
        assertEquals("cita 1 de —", textoCitaDeEvaluacion(t.copy(totalSesiones = 0, sesionesCompletadas = 0, devolucion = null)))

        val ev = EvaluacionPsico("e", entrevista = EntrevistaPsico(genogramaDocumentoId = "g"),
            fuentes = listOf(FuentePsico("f1", documentoIds = listOf("g", "d")), FuentePsico("f2")))
        val sin = sinFoto(ev, "g")
        assertNull(sin.entrevista.genogramaDocumentoId)
        assertEquals(listOf("d"), sin.fuentes[0].documentoIds)

        assertEquals(960.0, precioPropuestoPlan(null, 80.0, 12))
        assertEquals(500.0, precioPropuestoPlan(500.0, 80.0, 12))   // lo negociado se respeta
        assertNull(precioPropuestoPlan(null, 80.0, null))

        val sug = SugerenciaSesionesPsico("depresion", "Depresión", 16)
        assertTrue(ofrecerSugerencia(sug, 12))
        assertFalse(ofrecerSugerencia(sug, 16))
        assertFalse(ofrecerSugerencia(null, 12))

        val id = nuevoIdFuente(listOf(FuentePsico("f${36L.toString(36)}")), 36L)
        assertEquals("f${37L.toString(36)}", id)
        assertTrue(id !in listOf("f10"))
        assertEquals(JsonPrimitive("x"), JsonPrimitive("x"))
    }
}
