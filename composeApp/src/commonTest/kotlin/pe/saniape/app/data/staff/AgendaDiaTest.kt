package pe.saniape.app.data.staff

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * T2 agenda (auditoría de flujos 2026-10-24): No vino el mismo día, documentos
 * desde «⋯ Más» y lo atendido hoy sin cobrar. Gemelo de lib/__tests__/agenda-dia.test.ts.
 */
class AgendaDiaTest {

    private val hoy = "2026-10-24"

    // ── «🚫 No vino» ──

    @Test
    fun noVinoEnLaDeHoyYaEmpezada() {
        assertTrue(ofreceNoVino("Confirmada", hoy, "09:00:00", hoy, "10:30"))
        assertTrue(ofreceNoVino("Pendiente", hoy, "10:30", hoy, "10:30"))
    }

    @Test
    fun noVinoNoEnLaDeHoyQueAunNoEmpieza() {
        assertFalse(ofreceNoVino("Confirmada", hoy, "16:00", hoy, "10:30"))
    }

    @Test
    fun noVinoSinHoraElDiaYaLlego() {
        assertTrue(ofreceNoVino("Confirmada", hoy, "", hoy, "10:30"))
    }

    @Test
    fun noVinoDiasAnterioresSiFuturosNo() {
        assertTrue(ofreceNoVino("Pendiente", "2026-10-20", "18:00", hoy, "10:30"))
        assertFalse(ofreceNoVino("Confirmada", "2026-10-25", "08:00", hoy, "10:30"))
    }

    @Test
    fun noVinoNoEnCerradasNiFechasRotas() {
        listOf("Completada", "Cancelada", "No asistió", null).forEach {
            assertFalse(ofreceNoVino(it, hoy, "08:00", hoy, "10:30"))
        }
        assertFalse(ofreceNoVino("Confirmada", "", "08:00", hoy, "10:30"))
    }

    // ── Documentos de la atención ──

    @Test
    fun documentosDelRubroComoLaConsultaGuiada() {
        assertEquals(listOf(DOC_INFORME, DOC_DESCANSO, DOC_ORDEN), documentosDelRubro("medica").map { it.tipo })
        assertEquals(listOf(DOC_INFORME, DOC_DESCANSO, DOC_ORDEN), documentosDelRubro(null).map { it.tipo })
        assertEquals(listOf(TITULO_INFORME_PSICOLOGICO, TITULO_CONSTANCIA_PSICOLOGICA), documentosDelRubro("psicologia").map { it.titulo })
        assertEquals(listOf("Informe de fisioterapia", "Constancia de atención"), documentosDelRubro("fisioterapia").map { it.titulo })
        assertEquals("Informe de capilar", documentosDelRubro("capilar").first().titulo)
    }

    @Test
    fun apartadoPorRubroONombre() {
        assertEquals("medica", apartadoDocumentos("medicina_general", "Medicina general"))
        assertEquals("medica", apartadoDocumentos(null, "Psiquiatría"))
        assertEquals("psicologia", apartadoDocumentos("psicologia", "Psicología clínica"))
        assertEquals("podologia", apartadoDocumentos(null, "Podología"))
        assertEquals("fisioterapia", apartadoDocumentos(null, "MASOTERAPIA"))
        assertNull(apartadoDocumentos(null, "Otra cosa"))
    }

    @Test
    fun apartadoDeLaAtencionPorCitaProfesionalOClinica() {
        val esps = listOf(
            EspecialidadRef(id = "e1", nombre = "Psicología", rubro = "psicologia"),
            EspecialidadRef(id = "e2", nombre = "Medicina general", rubro = "medicina_general"),
        )
        assertEquals("psicologia", apartadoDeAtencionDocs(esps, "e1", null))
        assertEquals("medica", apartadoDeAtencionDocs(esps, null, listOf("e2")))
        // Clínica mixta sin especialidad en la cita ni profesional: no se sabe (médica de siempre).
        assertNull(apartadoDeAtencionDocs(esps, null, null))
        assertEquals("psicologia", apartadoDeAtencionDocs(esps.take(1), null, null))
    }

    @Test
    fun documentosSoloEnLaCompletadaYConPermiso() {
        val docs = documentosDelRubro("medica")
        assertEquals(
            listOf(ClaveDocumentoCita.INFORME, ClaveDocumentoCita.DESCANSO, ClaveDocumentoCita.ORDEN),
            documentosDeCita("Completada", "p1", docs, puedeEmitirDocs = true, consentimiento = false, mapaAplicacion = false).map { it.clave },
        )
        assertEquals("🛌 Descanso médico", documentosDeCita("Completada", "p1", docs, true, false, false)[1].texto)
        // DALU (sin documentos médicos o sin quien firme): nada.
        assertTrue(documentosDeCita("Completada", "p1", docs, puedeEmitirDocs = false, consentimiento = false, mapaAplicacion = false).isEmpty())
        listOf("Pendiente", "Confirmada", "Cancelada").forEach {
            assertTrue(documentosDeCita(it, "p1", docs, true, false, false).isEmpty())
        }
    }

    @Test
    fun constanciaPsicologicaConSuTitulo() {
        val d = documentosDeCita("Completada", "p1", documentosDelRubro("psicologia"), true, false, false, psico = true)
        assertEquals(listOf(ClaveDocumentoCita.INFORME, ClaveDocumentoCita.CONSTANCIA), d.map { it.clave })
        assertEquals(TITULO_CONSTANCIA_PSICOLOGICA, d[1].titulo)
        assertTrue(d.all { it.psico && it.tipo == DOC_INFORME })
    }

    @Test
    fun mapaYConsentimientoAntesYDespuesNuncaCancelada() {
        assertEquals(
            listOf(ClaveDocumentoCita.CONSENTIMIENTO, ClaveDocumentoCita.MAPA),
            documentosDeCita("Confirmada", "p1", null, false, consentimiento = true, mapaAplicacion = true).map { it.clave },
        )
        assertEquals(listOf(TEXTO_MAPA_CITA), documentosDeCita("Completada", "p1", null, false, false, true).map { it.texto })
        assertTrue(documentosDeCita("Cancelada", "p1", null, false, true, true).isEmpty())
        assertTrue(documentosDeCita("Completada", null, documentosDelRubro("medica"), true, true, true).isEmpty())
    }

    // ── Lo atendido hoy sin cobrar ──

    private fun cita(id: String, tipo: String = "Consulta", estado: String = "Completada", fecha: String = hoy,
                     costo: Double? = 50.0, pagadaAt: String? = null) = CitaStaff(
        id = id, fecha = fecha, hora = "09:00", estado = estado, tipo = tipo,
        costo = costo, duracion = null, origen = null, confirmadaPorPaciente = false, numeroSesion = null,
        terapeutaId = null, terapeutaNombre = null, pacienteId = "p", pacienteNombre = "Ana", pacienteTelefono = null,
        tratamientoId = null, procedimiento = null, especialidadId = null, notaRecepcion = null,
        pagadaAt = pagadaAt,
    )

    @Test
    fun sinCobrarSoloConsultasYEvaluacionesDeHoy() {
        val (filas, total) = atendidasSinCobrar(listOf(
            cita("a"),
            cita("b", tipo = "Evaluación", costo = 80.1),
            cita("c", pagadaAt = "2026-10-24T10:00:00Z"),
            cita("d", costo = 0.0),
            cita("e", estado = "Confirmada"),
            cita("f", fecha = "2026-10-23"),
            cita("g", tipo = "Sesión"),
            cita("h", estado = "Cancelada"),
        ), hoy)
        assertEquals(listOf("a", "b"), filas.map { it.id })
        assertEquals(130.1, total)
    }

    @Test
    fun sinCobrarSumaEnCentimos() {
        assertEquals(0.3, atendidasSinCobrar(listOf(cita("a", costo = 0.1), cita("b", costo = 0.2)), hoy).second)
    }

    @Test
    fun cerrarMiCajaQuienCobraOVeFinanzasConPlan() {
        assertTrue(ofreceCerrarMiCaja(pagos = true, finanzas = false, planConCierre = true))
        assertTrue(ofreceCerrarMiCaja(pagos = false, finanzas = true, planConCierre = true))
        assertFalse(ofreceCerrarMiCaja(pagos = false, finanzas = false, planConCierre = true))
        assertFalse(ofreceCerrarMiCaja(pagos = true, finanzas = false, planConCierre = false))
    }

    // ── En la tarjeta: «⋯ Más» ──

    @Test
    fun noVinoVaEnCitaAntesDeCancelarYNuncaEsPrimaria() {
        val p = planAccionesCita(EntradaAccionesCita(estado = "Confirmada", tipo = "Sesión", noVino = true))
        assertEquals(AccionTarjeta.Completar, p.primaria?.accion)
        val cita = p.menu.first { it.first == GrupoAccion.CITA }.second.map { it.accion }
        assertEquals(listOf(AccionTarjeta.Editar, AccionTarjeta.NoVino, AccionTarjeta.Cancelar), cita)
    }

    @Test
    fun sinNoVinoLaTarjetaQuedaComoSiempre() {
        val p = planAccionesCita(EntradaAccionesCita(estado = "Confirmada", tipo = "Sesión"))
        assertFalse(p.enMenu.any { it.accion == AccionTarjeta.NoVino })
    }

    @Test
    fun documentosVanEnAtencion() {
        val docs = documentosDeCita("Completada", "p1", documentosDelRubro("medica"), true, consentimiento = true, mapaAplicacion = true)
        val p = planAccionesCita(EntradaAccionesCita(estado = "Completada", tipo = "Consulta", documentos = docs))
        val atencion = p.menu.firstOrNull { it.first == GrupoAccion.ATENCION }?.second.orEmpty().map { it.accion }
        assertEquals(
            listOf(AccionTarjeta.DocInforme, AccionTarjeta.DocDescanso, AccionTarjeta.DocOrden, AccionTarjeta.MapaAplicacion),
            atencion.filter { it in setOf(AccionTarjeta.DocInforme, AccionTarjeta.DocDescanso, AccionTarjeta.DocOrden, AccionTarjeta.MapaAplicacion) },
        )
        // El consentimiento aún no se emite en la app: no aparece.
        assertNull(accionDeDocumento(ClaveDocumentoCita.CONSENTIMIENTO))
    }
}
