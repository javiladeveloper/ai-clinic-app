package pe.saniape.app.data.staff

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * 🧠 Certificado de salud mental: gemelo de lib/certificado-salud-mental.ts.
 * Mismos casos (y mismos literales, carácter por carácter) que
 * lib/__tests__/certificado-salud-mental.test.ts de la web. Lo que allí es del
 * servidor (filaCertificado, borradorCertificadoDesdeBody, la migración) aquí
 * se prueba en su equivalente de la app: el cuerpo que se manda al emitir.
 */
class CertificadoSaludMentalTest {

    // El texto que mandaron los psiquiatras, LITERAL ([El/La] → El, [NOMBRE],
    // [DNI] y [EDAD] con los datos de prueba). Requisito del cliente: con todo
    // en normal, el certificado sale idéntico carácter por carácter.
    private val ORIGINAL =
        "El que suscribe certifica haber atendido a JUAN CARLOS PÉREZ MAMANI, identificado con el DNI 45678912, de 34 años de edad y que, luego de la evaluación, se concluye que goza de buena salud mental, capacidad de juicio y control de su voluntad adecuado ya que es capaz de responder a estímulos, no presenta ideas patológicas ni delirios, presenta inteligencia dentro del promedio normal clínico, tiene memoria conservada para hechos pasados y presentes de acuerdo a su edad y mantiene conservada su voluntad.\n" +
            "De esta manera, se expide el presente certificado de buena salud mental a solicitud expresa del interesado para los trámites que estime conveniente."

    private val JUAN = PacienteCertificado(
        nombre = "JUAN CARLOS PÉREZ MAMANI", documento = "45678912", tipoDocumento = "DNI", pais = "PE", edad = 34, sexo = "M",
    )

    private fun op(
        suscribe: String = "El",
        hallazgos: EstadoHallazgos = hallazgosNormales(),
        conclusion: String = CONCLUSION_BUENA,
        finalidad: String = "interesado",
        finalidadOtra: String? = null,
        examenMental: String? = null,
    ) = opcionesPorDefecto().copy(
        suscribe = suscribe, hallazgos = hallazgos, conclusion = conclusion,
        finalidad = finalidad, finalidadOtra = finalidadOtra, examenMental = examenMental,
    )

    private fun conAlt(k: String, opcion: String): EstadoHallazgos = hallazgosNormales() + (k to EstadoHallazgo(true, opcion))
    private fun sin(vararg ks: String, base: EstadoHallazgos = hallazgosNormales()): EstadoHallazgos =
        base + ks.map { it to EstadoHallazgo(false, "normal") }

    // ── Texto por defecto = el original, carácter por carácter ──

    @Test fun todasLasCasillasEnNormalDanElOriginal() {
        assertEquals(ORIGINAL, armarTexto(null, JUAN, op()))
        assertEquals(ORIGINAL, armarTexto(PLANTILLA_FABRICA, JUAN, op()))
    }

    @Test fun laQueSuscribeSoloCambiaElInicio() {
        assertEquals(ORIGINAL.replace(Regex("^El que"), "La que"), armarTexto(null, JUAN, op(suscribe = "La")))
    }

    // ── Identificación y género ──

    @Test fun pacienteMujerIdentificadaYDeLaInteresada() {
        val t = armarTexto(null, JUAN.copy(nombre = "ROSA QUISPE", sexo = "F"), op())
        assertTrue(t.contains("ROSA QUISPE, identificada con el DNI 45678912, de 34 años"))
        assertTrue(t.contains("a solicitud expresa de la interesada para"))
    }

    @Test fun sinSexoLaRedaccionOriginalIdentica() {
        assertEquals(ORIGINAL, armarTexto(null, JUAN.copy(sexo = null), op()))
        assertEquals("identificado", identificadoSegun("X"))
        assertEquals("del interesado", interesadoSegun(null))
        assertEquals("de la interesada", interesadoSegun("F"))
    }

    @Test fun sinDocumentoSeOmiteLaIdentificacion() {
        val t = armarTexto(null, JUAN.copy(documento = ""), op())
        assertTrue(t.contains("PÉREZ MAMANI, de 34 años de edad y que"))
        assertFalse(t.contains("identificado"))
    }

    @Test fun extranjeroConCarneDeExtranjeria() {
        val p = JUAN.copy(documento = "001234567", tipoDocumento = "Carné de extranjería", sexo = "F")
        assertEquals("el carné de extranjería N° 001234567", fraseDocumento(p))
        assertTrue(armarTexto(null, p, op()).contains("identificada con el carné de extranjería N° 001234567"))
    }

    @Test fun pasaporteCiYRut() {
        assertEquals("el pasaporte N° AB123", fraseDocumento(PacienteCertificado("", documento = "AB123", tipoDocumento = "Pasaporte")))
        assertEquals("la cédula de identidad N° 7654321", fraseDocumento(PacienteCertificado("", documento = "7654321", tipoDocumento = "CI")))
        assertEquals("el RUT N° 12345678-9", fraseDocumento(PacienteCertificado("", documento = "12345678-9", tipoDocumento = "RUT")))
        assertNull(fraseDocumento(PacienteCertificado("", documento = "  ")))
    }

    // ── Edad ──

    @Test fun unAnoEnSingularYSinEdadSeOmite() {
        assertEquals(", de 1 año de edad", piezasCertificado(JUAN.copy(edad = 1), op()).getValue("EDAD"))
        assertEquals("", piezasCertificado(JUAN.copy(edad = null), op()).getValue("EDAD"))
        assertTrue(armarTexto(null, JUAN.copy(edad = null), op()).contains("DNI 45678912 y que, luego"))
    }

    // ── Casillas: alternativas y desmarcar ──

    @Test fun desmarcarUnaCasillaQuitaSuFrase() {
        val c = conclusionCertificado(op(hallazgos = sin("inteligencia")))
        assertFalse(c.contains("inteligencia"))
        assertTrue(c.contains("no presenta ideas patológicas ni delirios, tiene memoria conservada"))
    }

    @Test fun memoriaConLeveAlteracionSeRearma() {
        val c = conclusionCertificado(op(hallazgos = conAlt("memoria", "leve")))
        assertTrue(c.contains("presenta leve alteración de la memoria para hechos recientes y mantiene conservada su voluntad"))
    }

    @Test fun juicioAlteradoSaleDeLaConclusionYPasaALaLista() {
        val c = conclusionCertificado(op(hallazgos = conAlt("juicio", "parcial")))
        assertTrue(c.startsWith(
            "se concluye que goza de buena salud mental, control de su voluntad adecuado ya que es capaz de responder a estímulos, presenta juicio parcialmente conservado, ",
        ))
    }

    @Test fun controlDesmarcadoSoloCapacidadDeJuicioAdecuada() {
        assertTrue(conclusionCertificado(op(hallazgos = sin("control"))).contains("goza de buena salud mental, capacidad de juicio adecuada ya que"))
    }

    @Test fun conclusionSobriaSinBuenaSaludMental() {
        val t = armarTexto(null, JUAN, op(hallazgos = conAlt("ideas", "delirantes"), conclusion = CONCLUSION_HALLAZGOS))
        assertFalse(t.contains("buena salud mental"))
        assertTrue(t.contains("se evidencia que es capaz de responder a estímulos, presenta capacidad de juicio adecuada, presenta control de su voluntad adecuado, presenta ideas delirantes,"))
        assertTrue(t.contains("se expide el presente certificado de salud mental a solicitud"))
    }

    @Test fun todasDesmarcadasGozaDeBuenaSaludMentalSinLista() {
        assertEquals("se concluye que goza de buena salud mental", conclusionCertificado(op(hallazgos = sin(*CLAVES_HALLAZGO.toTypedArray()))))
    }

    @Test fun alternativaDesconocidaCaeANormal() {
        assertFalse(hayAlternativas(conAlt("memoria", "inventada")))
    }

    @Test fun cadaHallazgoTieneUnaODosAlternativas() {
        assertEquals(listOf("estimulos", "juicio", "control", "ideas", "inteligencia", "memoria", "voluntad"), HALLAZGOS.map { it.clave })
        for (h in HALLAZGOS) assertTrue(h.alternativas.size in 1..2, h.clave)
    }

    // ── Coherencia de la conclusión ──

    @Test fun buenaSaludMentalConMayusculasEspaciosOEspacioDuro() {
        val h = conAlt("memoria", "leve")
        assertTrue(requiereConfirmarConclusion("Goza de BUENA  SALUD mental.", h))
        assertFalse(requiereConfirmarConclusion("Goza de buena salud física.", h))
    }

    @Test fun visibleParaElPacientePorDefectoSalvoHallazgosAlterados() {
        assertTrue(visiblePorDefecto(hallazgosNormales()))
        assertFalse(visiblePorDefecto(conAlt("ideas", "delirantes")))
    }

    @Test fun detalleAdminQuienLoEmitioOSaludMentalRecepcionNo() {
        assertTrue(veDetalleCertificado("Admin", null, false, "t1"))
        assertFalse(veDetalleCertificado("Recepcionista", null, false, "t1"))
        assertTrue(veDetalleCertificado("Médico", "t1", false, "t1"))
        assertFalse(veDetalleCertificado("Médico", "t2", false, "t1"))
        assertTrue(veDetalleCertificado("Médico", "t2", true, "t1"))
    }

    @Test fun pideConfirmarBuenaSaludMentalConHallazgosAlterados() {
        val h = conAlt("ideas", "delirantes")
        val t = armarTexto(null, JUAN, op(hallazgos = h))
        assertTrue(requiereConfirmarConclusion(t, h))
        assertFalse(requiereConfirmarConclusion(t, hallazgosNormales()))
        assertFalse(requiereConfirmarConclusion(armarTexto(null, JUAN, op(hallazgos = h, conclusion = CONCLUSION_HALLAZGOS)), h))
    }

    // ── unirConY ──

    @Test fun comaYYEAnteI() {
        assertEquals("a, b y c", unirConY(listOf("a", "b", "c")))
        assertEquals("a e impresiona x", unirConY(listOf("a", "impresiona x")))
        assertEquals("a y hielo", unirConY(listOf("a", "hielo")))
        assertEquals("solo", unirConY(listOf("solo")))
    }

    // ── Motivo (finalidad) ──

    @Test fun chipsYTextoLibre() {
        assertTrue(armarTexto(null, JUAN, op(finalidad = "sucamec"))
            .endsWith("del interesado para el trámite de licencia de uso de armas de fuego ante la SUCAMEC."))
        assertTrue(armarTexto(null, JUAN, op(finalidad = "licencia")).endsWith("para el trámite de su licencia de conducir."))
        assertTrue(armarTexto(null, JUAN, op(finalidad = "otro", finalidadOtra = "para la adopción.")).endsWith("del interesado para la adopción."))
        assertTrue(armarTexto(null, JUAN, op(finalidad = "otro", finalidadOtra = "")).endsWith("que estime conveniente."))
        assertEquals("Trabajo", lineaFinalidad("trabajo"))
        assertNull(lineaFinalidad("interesado"))
        assertEquals("La adopción", lineaFinalidad("otro", "la adopción"))
    }

    // ── Examen mental de hoy ──

    @Test fun examenMentalEnUnaLineaTrasLaConclusion() {
        assertEquals("Lúcido, orientado; Afecto eutímico", examenMentalEnLinea("Lúcido, orientado.\nAfecto eutímico.\n"))
        val t = armarTexto(null, JUAN, op(examenMental = "Lúcido, orientado."))
        assertTrue(t.contains("mantiene conservada su voluntad. Al examen mental: Lúcido, orientado.\nDe esta manera"))
    }

    // ── Plantilla propia ──

    @Test fun aPlantillaDevuelveLosMarcadoresYSeRearmaConOtroPaciente() {
        val o = op(finalidad = "trabajo")
        val editado = armarTexto(null, JUAN, o).replace("De esta manera", "Por lo expuesto")
        val pl = aPlantilla(editado, piezasCertificado(JUAN, o))
        assertTrue(pl.contains("[SUSCRIBE] certifica haber atendido a [NOMBRE][IDENTIFICACION][EDAD] y que, luego de la evaluación, [CONCLUSION]."))
        assertTrue(pl.contains("Por lo expuesto, se expide el presente [CERTIFICADO] [FINALIDAD]."))
        assertTrue(plantillaUsaCasillas(pl))
        val rosa = PacienteCertificado(nombre = "ROSA QUISPE", documento = "001", tipoDocumento = "Pasaporte", edad = 20, sexo = "F")
        assertTrue(armarTexto(pl, rosa, op()).contains("ROSA QUISPE, identificada con el pasaporte N° 001, de 20 años"))
    }

    @Test fun pacienteSinDocumentoNiEdadIgualDejaLosMarcadores() {
        val p = JUAN.copy(documento = null, edad = null)
        assertTrue(aPlantilla(armarTexto(null, p, op()), piezasCertificado(p, op())).contains("[NOMBRE][IDENTIFICACION][EDAD] y que"))
    }

    @Test fun sinConclusionLasCasillasNoMandan() {
        assertFalse(plantillaUsaCasillas("Certifico que [NOMBRE] está bien."))
        assertTrue(plantillaUsaCasillas(null))
    }

    @Test fun plantillaVigenteDelFirmanteLuegoLaDeLaClinica() {
        val ps = listOf(PlantillaCertificado(null, "clinica", null), PlantillaCertificado("t1", "propia", null))
        assertEquals("propia", plantillaVigente(ps, "t1")?.texto)
        assertEquals("clinica", plantillaVigente(ps, "t2")?.texto)
        assertNull(plantillaVigente(emptyList(), "t1"))
    }

    // ── Validación y cuerpo ──

    private val base = BorradorCertificado(
        pacienteId = "11111111-1111-4111-8111-111111111111", terapeutaId = "22222222-2222-4222-8222-222222222222",
        fecha = "2026-10-16", texto = ORIGINAL, hallazgos = hallazgosNormales(),
    )

    @Test fun validoConElTextoOriginal() {
        assertEquals(emptyList(), validarCertificado(base, "2026-10-16"))
    }

    @Test fun fechaFuturaViejaTextoCortoYMarcadorSinReemplazar() {
        assertTrue(validarCertificado(base.copy(fecha = "2026-10-17"), "2026-10-16")[0].contains("futura"))
        assertTrue(validarCertificado(base.copy(fecha = "2026-09-01"), "2026-10-16")[0].contains("30 días"))
        assertTrue(validarCertificado(base.copy(texto = "corto"), "2026-10-16")[0].contains("corto"))
        assertTrue(validarCertificado(base.copy(texto = ORIGINAL.replace("JUAN", "[NOMBRE]")), "2026-10-16")[0].contains("marcador"))
        assertTrue(validarCertificado(base.copy(terapeutaId = null))[0].contains("profesional"))
    }

    @Test fun buenaSaludMentalConHallazgoAlteradoExigeConfirmar() {
        val b = base.copy(hallazgos = conAlt("memoria", "leve"))
        assertTrue(validarCertificado(b, "2026-10-16").joinToString(" ").contains("confirma"))
        assertEquals(emptyList(), validarCertificado(b.copy(confirmaConclusion = true), "2026-10-16"))
    }

    @Test fun cuerpoSoloCasillasMarcadasYSinClinicaId() {
        val f = cuerpoEmitirCertificado(base, "abc")
        assertNull(f["clinica_id"])
        assertNull(f["clinicaId"])
        assertEquals(7, f.getValue("hallazgos").jsonArray.size)
        assertEquals("abc", f.getValue("claveCliente").jsonPrimitive.content)
        assertNull(f["visiblePaciente"])
        assertEquals("false", cuerpoEmitirCertificado(base.copy(visiblePaciente = false), "k").getValue("visiblePaciente").jsonPrimitive.content)
        assertEquals("true", cuerpoEmitirCertificado(base.copy(confirmaConclusion = true), "k").getValue("confirmaConclusion").jsonPrimitive.content)
    }

    @Test fun hallazgosIdaYVuelta() {
        val h = sin("voluntad", base = conAlt("ideas", "sobrevaloradas"))
        val guardar = hallazgosParaGuardar(h)
        assertTrue(("ideas" to "sobrevaloradas") in guardar)
        assertNull(guardar.firstOrNull { it.first == "voluntad" })
        val items = guardar.map { """{"clave":"${it.first}","opcion":"${it.second}"}""" } + """{"clave":"otro","opcion":"z"}"""
        val json = Json.parseToJsonElement(items.joinToString(",", prefix = "[", postfix = "]"))
        val est = assertNotNull(hallazgosDesdeJson(json))
        assertEquals(EstadoHallazgo(true, "sobrevaloradas"), est["ideas"])
        assertFalse(est.getValue("voluntad").incluido)
        assertNull(est["otro"])
        assertNull(hallazgosDesdeJson(JsonPrimitive("no")))
    }

    // ── Quién emite y especialidades de salud mental ──

    @Test fun psiquiatriaYPsicologiaNoMedicinaGeneral() {
        assertTrue(esEspecialidadSaludMental("Psiquiatría", "medicina_general"))
        assertTrue(esEspecialidadSaludMental("Psicología", "psicologia"))
        assertTrue(esEspecialidadSaludMental("Psiquiatria infantil", null))
        assertFalse(esEspecialidadSaludMental("Medicina general", "medicina_general"))
        // El rubro guardado manda: "Psicología" guardada en fisioterapia no cuenta.
        assertFalse(esEspecialidadSaludMental("Psicología", "fisioterapia"))
    }

    @Test fun emisorColegiaturaYSaludMental() {
        val psiq = "Psiquiatría" to "medicina_general"
        assertTrue(esEmisorSaludMental("45678", listOf(psiq)))
        assertFalse(esEmisorSaludMental("", listOf(psiq)))
        assertFalse(esEmisorSaludMental("45678", listOf("Fisioterapia" to "fisioterapia")))
    }

    @Test fun mapaYPacienteNuncaPorPresetMixtaPorCitaOQuienMira() {
        val m = mapaSaludMental(listOf(Triple("a", "Psiquiatría", "medicina_general"), Triple("b", "Medicina general", "medicina_general")))
        assertEquals(MapaSaludMental(listOf("a"), solo = false), m)
        assertTrue(citaEsSaludMental(m, especialidadId = "a"))
        assertFalse(pacienteEsSaludMental(m, atenciones = listOf(AtencionFisio(especialidadId = "b"))))
        assertTrue(pacienteEsSaludMental(m, especialidadesDeQuienMira = listOf("a")))
        assertFalse(pacienteEsSaludMental(mapaSaludMental(emptyList()), tieneCertificados = true))
    }

    @Test fun laQueSuscribePorElTitulo() {
        assertEquals("La", suscribePorTitulo("Médica Psiquiatra"))
        assertEquals("La", suscribePorTitulo("Psicóloga clínica"))
        assertEquals("El", suscribePorTitulo("Médico Psiquiatra"))
        assertEquals("El", suscribePorTitulo(null))
    }

    @Test fun numeroYMensajes() {
        assertEquals("CSM N° 000012", formatearNumeroCertificado(12))
        assertEquals("CSM N° —", formatearNumeroCertificado(null))
        assertTrue(mensajeErrorCertificado("CERTIFICADO_PROFESIONAL_NO_SALUD_MENTAL").contains("psiquiatra o un psicólogo"))
        assertTrue(mensajeErrorCertificado("new row violates check constraint \"certificados_medicos_conclusion_chk\"").contains("confirma"))
    }

    // ── Pestaña de la ficha (etiqueta de la web) ──

    @Test fun etiquetaDeLaPestanaConCertificados() {
        assertEquals("💊 Recetas y documentos", etiquetaPestanaRecetas(true, false, true))
        assertEquals("📄 Documentos médicos", etiquetaPestanaRecetas(false, true, true))
        assertEquals("📄 Documentos", etiquetaPestanaRecetas(false, false, true))
        assertEquals("💊 Recetas", etiquetaPestanaRecetas(true, false))
    }

    // ── Lo que llega del servidor ──

    @Test fun filaDelServidorConYSinDetalle() {
        val fila = Json.parseToJsonElement(
            """{"id":"c1","numero":12,"fecha":"2026-10-16","finalidad":"Licencia de conducir","texto":"El que suscribe…",
               "terapeuta_id":"t1","estado":"Emitido","visible_paciente":true,"conclusion_confirmada":false,
               "profesional":{"nombre":"Dra. Rosa Quispe","colegiatura":"CMP 45678"}}""",
        ).jsonObject
        val c = assertNotNull(certificadoDeJson(fila))
        assertEquals("CSM N° 000012", c.numeroTexto)
        assertEquals("Para: Licencia de conducir", resumenCertificado(c.finalidad))
        assertTrue(c.visiblePaciente)
        assertFalse(c.anulado)
        assertEquals(false, c.conclusionConfirmada)
        // Recepción: sin el detalle.
        val sinDetalle = assertNotNull(certificadoDeJson(Json.parseToJsonElement(
            """{"id":"c2","numero":"3","fecha":"2026-10-16","texto":null,"hallazgos":[],"conclusion_confirmada":null,"estado":"Anulado","motivo_anulacion":"Error"}""",
        ).jsonObject))
        assertNull(sinDetalle.texto)
        assertNull(sinDetalle.conclusionConfirmada)
        assertTrue(sinDetalle.anulado)
        assertEquals("A solicitud del interesado", resumenCertificado(sinDetalle.finalidad))
        assertNull(certificadoDeJson(JsonObject(emptyMap())))
    }

    @Test fun prefillDelContrato() {
        val o = Json.parseToJsonElement(
            """{ "ok": true,
              "paciente": { "id": "p1", "nombre": "JUAN PÉREZ", "documento": "45678912", "tipoDocumento": "DNI",
                            "tipoDocumentoHc": null, "pais": "PE", "edad": 34, "sexo": "M", "fechaNacimiento": "1992-01-05" },
              "hoy": "2026-10-16", "lugar": "Tacna",
              "firmantes": [{ "id": "t1", "nombre": "Dra. Rosa Quispe", "colegiatura": "45678", "especialidadId": "e1", "suscribe": "La" }],
              "terapeutaId": "t1",
              "cita": { "id": "c1", "especialidadId": "e1", "tratamientoId": null },
              "atencion": { "id": "a1", "examenMental": "Lúcido, orientado…" },
              "plantillas": [{ "terapeutaId": null, "texto": "[SUSCRIBE] certifica…", "updatedAt": "x" }] }""",
        ).jsonObject
        val p = assertNotNull(prefillCertificadoDeJson(o))
        assertEquals("p1", p.pacienteId)
        assertEquals(34, p.paciente.edad)
        assertEquals("M", p.paciente.sexo)
        assertEquals("1992-01-05", p.fechaNacimiento)
        assertEquals("La", p.firmantes.single().suscribe)
        assertEquals("a1", p.atencionId)
        assertEquals("Lúcido, orientado…", p.examenMental)
        assertNull(p.citaTratamientoId)
        assertNull(p.plantillas.single().terapeutaId)
        assertNull(prefillCertificadoDeJson(Json.parseToJsonElement("""{"hoy":"2026-10-16"}""").jsonObject))
    }

    @Test fun servidorSinElEndpointNoMuestraLaOpcion() {
        // 404 de Next (HTML, sin `codigo`): el servidor todavía no lo tiene.
        assertIs<CertificadoRepo.Lista.NoDisponible>(CertificadoRepo.aLista(404, "<html>404</html>"))
        assertIs<CertificadoRepo.Prefill.NoDisponible>(CertificadoRepo.aPrefill(404, null))
        // 404 NUESTRO (con código): el paciente no existe; no es "sin endpoint".
        assertIs<CertificadoRepo.Prefill.Error>(CertificadoRepo.aPrefill(404, """{"error":"Paciente no encontrado","codigo":"NO_ENCONTRADO"}"""))
        assertIs<CertificadoRepo.Lista.Fallo>(CertificadoRepo.aLista(503, """{"error":"No se pudo leer","codigo":"ERROR_LECTURA"}"""))
        val ok = assertIs<CertificadoRepo.Lista.Ok>(CertificadoRepo.aLista(200, """{"items":[{"id":"c1","numero":1},{"id":"c1"},{"x":1}]}"""))
        assertEquals(1, ok.items.size)
    }

    @Test fun hallazgosDeUnaListaVaciaQuedanDesmarcados() {
        val est = assertNotNull(hallazgosDesdeJson(JsonArray(emptyList())))
        assertTrue(est.values.none { it.incluido })
        assertEquals(CLAVES_HALLAZGO.toSet(), est.keys)
    }
}
