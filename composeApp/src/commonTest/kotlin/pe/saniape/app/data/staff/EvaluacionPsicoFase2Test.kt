package pe.saniape.app.data.staff

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.serialization.json.JsonObject

/**
 * Evaluación psicológica, FASE 2 (contrato §13): versiones del informe
 * (`informes`, `reemplazado_por`, `reemplazaA`), plantilla del informe
 * (`contenido.plantilla`) y el orden/numeración de las secciones (gemelo de
 * seccionesNumeradas de lib/informe-psicologico-plantilla.ts).
 */
class EvaluacionPsicoFase2Test {

    private fun obj(texto: String): JsonObject = assertNotNull(jsonObjetoPsico(texto))

    @Test
    fun historial_de_versiones_y_vigente() {
        val e = assertNotNull(parsearEspacioPsico(obj("""
            { "ok": true, "esEvaluacionPsico": true, "soloLectura": true,
              "evaluacion": { "id": "e1", "estado": "cerrada" },
              "informe": { "id": "i3", "version": 3, "estado": "borrador", "reemplazado_por": null, "reemplazado_at": null,
                "contenido": { "filiacion": {}, "secciones": {}, "reemplazaA": { "version": 2, "emitido": "2026-10-20" } } },
              "informePdf": { "id": "doc2", "path": "protegido/p1/t1/v2.pdf", "nombre": "Informe (v2).pdf" },
              "informes": [
                { "id": "i1", "version": 1, "estado": "emitido", "emitido_at": "2026-10-05T15:00:00Z", "documento_id": "doc1",
                  "reemplazado_por": "i2", "reemplazadoPorVersion": 2, "vigente": false,
                  "pdf": { "id": "doc1", "path": "protegido/p1/t1/v1.pdf", "nombre": "Informe.pdf" } },
                { "id": "i3", "version": 3, "estado": "borrador", "emitido_at": null, "documento_id": null,
                  "reemplazado_por": null, "reemplazadoPorVersion": null, "vigente": false, "pdf": null },
                { "id": "i2", "version": 2, "estado": "emitido", "emitido_at": "2026-10-20T15:00:00Z", "documento_id": "doc2",
                  "reemplazado_por": null, "reemplazadoPorVersion": null, "vigente": true,
                  "pdf": { "id": "doc2", "path": "protegido/p1/t1/v2.pdf", "nombre": "Informe (v2).pdf" } },
                { "version": 9 }, { "id": "x", "version": 0 }, "basura"
              ] }
        """)))
        // De la más nueva a la más vieja; las filas sin id o sin versión válida, fuera.
        assertEquals(listOf(3, 2, 1), e.informes.map { it.version })
        val (v3, v2, v1) = e.informes
        assertTrue(v2.vigente)
        assertEquals("doc2", v2.pdf?.id)
        assertEquals("i2", v1.reemplazadoPor)
        assertEquals(2, v1.reemplazadoPorVersion)
        assertFalse(v1.vigente)
        assertNull(v3.pdf)
        assertEquals("borrador", v3.estado)
        // `informe` es el último (el borrador nuevo); `informePdf` el del vigente.
        assertEquals("i3", e.informe?.id)
        assertTrue(e.informe!!.esVersionNueva)
        assertEquals("doc2", e.informePdf?.id)
        assertEquals(ReemplazaAPsico(2, "2026-10-20"), e.informe!!.contenido.reemplazaA)

        assertEquals("Vigente · lo ve el paciente" to "vigente", estadoVersionInforme(v2))
        assertEquals("Reemplazado por v2" to "reemplazado", estadoVersionInforme(v1))
        assertEquals("Borrador" to "borrador", estadoVersionInforme(v3))
        assertTrue(mostrarHistorialInforme(e.informes))
        assertFalse(mostrarHistorialInforme(e.informes.take(1)))
    }

    @Test
    fun pdf_pendiente_no_es_vigente_y_no_se_mezcla_con_el_vigente() {
        val e = assertNotNull(parsearEspacioPsico(obj("""
            { "ok": true, "esEvaluacionPsico": true, "evaluacion": { "id": "e1", "estado": "cerrada" },
              "informe": { "id": "i2", "version": 2, "estado": "emitido", "documento_id": null, "contenido": {} },
              "informePdf": { "id": "doc1", "path": "protegido/v1.pdf", "nombre": "v1.pdf" },
              "informes": [
                { "id": "i2", "version": 2, "estado": "emitido", "documento_id": null, "vigente": true, "pdf": null },
                { "id": "i1", "version": 1, "estado": "emitido", "documento_id": "doc1", "vigente": true, "reemplazado_por": null,
                  "pdf": { "id": "doc1", "path": "protegido/v1.pdf", "nombre": "v1.pdf" } }
              ] }
        """)))
        val (v2, v1) = e.informes
        // La v2 se congeló pero su PDF falló: NO es vigente aunque el campo lo diga.
        assertFalse(v2.vigente)
        assertTrue(v2.pdfPendiente)
        assertEquals("PDF pendiente" to "pendiente", estadoVersionInforme(v2))
        assertTrue(v1.vigente)
        // El PDF del informe mostrado (v2) no es el vigente (v1): no se mezcla.
        assertNull(idPdfDeInforme(e.informe, e.informes))
        assertTrue(pdfPendienteDeInforme(e.informe, e.informes))
        assertEquals("doc1", e.informePdf?.id)

        // Con su PDF: el propio, por la fila o por el documento_id del informe.
        val conPdf = e.informe!!.copy(documentoId = "doc2")
        assertEquals("doc2", idPdfDeInforme(conPdf, e.informes))
        assertFalse(pdfPendienteDeInforme(conPdf, e.informes))
        // Borrador v2 abierto: sin PDF propio (el vigente queda en el historial).
        val borrador = InformePsico(id = "i3", version = 3, estado = "borrador")
        assertNull(idPdfDeInforme(borrador, e.informes))
        assertFalse(pdfPendienteDeInforme(borrador, e.informes))
    }

    @Test
    fun descartar_borrador_solo_de_version_nueva() {
        assertTrue(puedeDescartarBorrador(InformePsico(id = "i2", version = 2, estado = "borrador")))
        assertFalse(puedeDescartarBorrador(InformePsico(id = "i1", version = 1, estado = "borrador")))
        assertFalse(puedeDescartarBorrador(InformePsico(id = "i2", version = 2, estado = "emitido")))
        assertFalse(puedeDescartarBorrador(null))
        assertTrue(mensajeErrorAccionFase2("Descartar el borrador", 400, "DATOS_INVALIDOS", "Acción no válida").startsWith("Descartar el borrador aún no está disponible"))
        assertEquals("Ya hay un borrador de una versión nueva. Recarga la evaluación.",
            mensajeErrorAccionFase2("x", 409, "INFORME_BORRADOR_EXISTENTE", null))
    }

    @Test
    fun servidor_sin_fase_2_degrada() {
        // Sin `informes`, sin `plantilla`, sin `reemplazado_por`: lo de la fase 1 tal cual.
        val e = assertNotNull(parsearEspacioPsico(obj("""
            { "ok": true, "esEvaluacionPsico": true, "evaluacion": { "id": "e1" },
              "informe": { "id": "i1", "version": 1, "estado": "emitido", "contenido": { "secciones": { "motivo": "x" } } } }
        """)))
        assertTrue(e.informes.isEmpty())
        assertFalse(mostrarHistorialInforme(e.informes))
        val i = e.informe!!
        assertNull(i.reemplazadoPor)
        assertNull(i.contenido.plantilla)
        assertNull(i.contenido.reemplazaA)
        assertFalse(i.esVersionNueva)
        // Sin plantilla en el contenido = la estándar.
        assertEquals(plantillaInformePorDefecto(), plantillaDelInforme(i.contenido))
        // "Acción no válida" de un servidor viejo → mensaje claro.
        assertTrue(mensajeErrorNuevaVersion(400, "DATOS_INVALIDOS", "Acción no válida").contains("aún no está disponible"))
        assertTrue(mensajeErrorNuevaVersion(404, null, null).contains("aún no está disponible"))
        assertEquals("El informe todavía es un borrador: edítalo y emítelo.", mensajeErrorNuevaVersion(409, "INFORME_NO_EMITIDO", "x"))
    }

    @Test
    fun informe_reemplazado_en_la_fila() {
        val i = assertNotNull(informeDeRespuesta(obj("""
            { "ok": true, "creado": true, "informe": { "id": "i1", "version": 1, "estado": "emitido",
              "reemplazado_por": "i2", "reemplazado_at": "2026-10-20T15:00:00Z", "contenido": {} } }
        """)))
        assertEquals("i2", i.reemplazadoPor)
        assertEquals("2026-10-20T15:00:00Z", i.reemplazadoAt)
    }

    @Test
    fun texto_de_reemplazo() {
        assertEquals("Versión 2. Reemplaza a la versión 1 emitida el 05/10/2026.", textoReemplazoInforme(ReemplazaAPsico(1, "2026-10-05")))
        assertEquals("Versión 3. Reemplaza a la versión 2.", textoReemplazoInforme(ReemplazaAPsico(2, "")))
        assertEquals("", textoReemplazoInforme(null))
        assertNull(leerReemplazaA(obj("""{ "version": 0 }""")))
        assertEquals(ReemplazaAPsico(1, "2026-10-05"), leerReemplazaA(obj("""{ "version": 1, "emitido": "2026-10-05" }""")))
    }

    @Test
    fun numeracion_estandar() {
        val s = seccionesNumeradasInforme(null)
        assertEquals(SECCIONES_INFORME.map { it.clave }, s.map { it.clave })
        // La impresión diagnóstica comparte el número de las conclusiones.
        assertEquals(listOf(2, 3, 4, 5, 6, 7, 7, 8), s.map { it.numero })
        assertEquals(SECCIONES_INFORME.map { it.numero }, s.map { it.numero })
        assertEquals(SECCIONES_INFORME.map { it.titulo }, s.map { it.titulo })
    }

    @Test
    fun plantilla_de_la_clinica_orden_nombres_y_ocultas() {
        val p = leerPlantillaInforme(obj("""
            { "secciones": [
                { "clave": "motivo", "titulo": "Motivo de consulta", "visible": true },
                { "clave": "observacion", "titulo": "  Conducta   observada ", "visible": true },
                { "clave": "antecedentes", "titulo": "Antecedentes", "visible": false },
                { "clave": "resultados", "titulo": "", "visible": true },
                { "clave": "recomendaciones", "titulo": "Sugerencias", "visible": true },
                { "clave": "conclusiones", "titulo": "Conclusión", "visible": false },
                { "clave": "impresionDiagnostica", "titulo": "Diagnóstico", "visible": true },
                { "clave": "motivo", "titulo": "repetida", "visible": true },
                { "clave": "desconocida", "titulo": "fuera", "visible": true }
              ],
              "encabezado": "Confidencial", "pie": "Uso exclusivo del evaluado." }
        """))
        // Repetida y desconocida fuera; la que falta ("tecnicas") va al final, visible y con su título estándar.
        assertEquals(
            listOf("motivo", "observacion", "antecedentes", "resultados", "recomendaciones", "conclusiones", "impresionDiagnostica", "tecnicas"),
            p.secciones.map { it.clave },
        )
        assertEquals("Conducta observada", p.secciones[1].titulo)
        assertEquals("Resultados, análisis e interpretación", p.secciones[3].titulo)
        // Conclusiones es obligatoria: nunca se oculta.
        assertTrue(p.secciones.first { it.clave == "conclusiones" }.visible)
        assertEquals("Confidencial", p.encabezado)
        assertEquals("Uso exclusivo del evaluado.", p.pie)

        val n = seccionesNumeradasInforme(p)
        // Antecedentes oculta: no sale ni cuenta número.
        assertEquals(
            listOf("motivo", "observacion", "resultados", "recomendaciones", "conclusiones", "impresionDiagnostica", "tecnicas"),
            n.map { it.clave },
        )
        // La impresión diagnóstica va justo después de las conclusiones: comparte número.
        assertEquals(listOf(2, 3, 4, 5, 6, 6, 7), n.map { it.numero })
        assertEquals("Motivo de consulta", n[0].titulo)
        assertEquals("Diagnóstico", n[5].titulo)
    }

    @Test
    fun impresion_diagnostica_separada_lleva_su_numero() {
        val p = PlantillaInformePsico(
            secciones = listOf(
                SeccionPlantillaPsico("motivo", "Motivo"),
                SeccionPlantillaPsico("impresionDiagnostica", "Impresión"),
                SeccionPlantillaPsico("conclusiones", "Conclusiones"),
                SeccionPlantillaPsico("recomendaciones", "Recomendaciones", visible = false),
                // Obligatoria marcada oculta (plantilla rara): igual se muestra.
            ),
        )
        assertEquals(listOf(2, 3, 4), seccionesNumeradasInforme(p).map { it.numero })
        val conMotivoOculto = p.copy(secciones = p.secciones.map { if (it.clave == "motivo") it.copy(visible = false) else it })
        assertEquals("motivo", seccionesNumeradasInforme(conMotivoOculto).first().clave)
    }

    @Test
    fun plantilla_en_el_contenido_y_encabezado_por_defecto() {
        val i = assertNotNull(informeDeRespuesta(obj("""
            { "informe": { "id": "i1", "contenido": { "plantilla": { "secciones": [ { "clave": "recomendaciones", "titulo": "Primero" } ] } } } }
        """)))
        val p = assertNotNull(i.contenido.plantilla)
        assertEquals("recomendaciones", p.secciones.first().clave)
        assertEquals(8, p.secciones.size)
        // Sin encabezado ni pie en la plantilla: los de la estándar.
        assertEquals(ENCABEZADO_INFORME_POR_DEFECTO, p.encabezado)
        assertEquals("", p.pie)
        // Encabezado vacío a propósito = sin encabezado.
        assertEquals("", leerPlantillaInforme(obj("""{ "encabezado": "" }""")).encabezado)
        // Plantilla ilegible (no objeto) = la del informe estándar.
        val raro = assertNotNull(informeDeRespuesta(obj("""{ "informe": { "id": "i1", "contenido": { "plantilla": "x" } } }""")))
        assertNull(raro.contenido.plantilla)
        assertEquals(seccionesNumeradasInforme(null), seccionesNumeradasInforme(plantillaDelInforme(raro.contenido)))
    }

    @Test
    fun documentos_protegidos_y_reemplazados() {
        fun doc(cat: String?, visible: Boolean?) = DocumentoFicha("d", "x.pdf", "protegido/x.pdf", "pdf", categoria = cat, visiblePaciente = visible)
        assertTrue(doc(CATEGORIA_INFORME_PSICOLOGICO, false).informeReemplazado)
        assertTrue(doc(CATEGORIA_INFORME_PSICOLOGICO, false).protegidoPsico)
        assertFalse(doc(CATEGORIA_INFORME_PSICOLOGICO, true).informeReemplazado)
        assertFalse(doc(CATEGORIA_INFORME_PSICOLOGICO, null).informeReemplazado)
        // Un documento común oculto al paciente no es "reemplazado" ni protegido.
        assertFalse(doc("Documento", false).informeReemplazado)
        assertFalse(doc("Documento", false).protegidoPsico)
        // Borrar el PDF del informe: el motivo legible de la base.
        assertEquals(MOTIVO_INFORME_NO_SE_BORRA, fraseDeErrorBasePsico("P0001: INFORME_EMITIDO_NO_SE_BORRA"))
    }
}
