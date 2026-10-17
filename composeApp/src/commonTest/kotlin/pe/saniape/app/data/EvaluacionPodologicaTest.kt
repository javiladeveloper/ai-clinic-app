package pe.saniape.app.data

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import pe.saniape.app.data.staff.AprendidoPlan
import pe.saniape.app.data.staff.AtencionFisio
import pe.saniape.app.data.staff.BorradorEvaluacionPodo
import pe.saniape.app.data.staff.CATEGORIAS_IWGDF
import pe.saniape.app.data.staff.EstadoRiesgoPodo
import pe.saniape.app.data.staff.EvaluacionPodologica
import pe.saniape.app.data.staff.HALLAZGOS_PODO
import pe.saniape.app.data.staff.HallazgoMarcado
import pe.saniape.app.data.staff.MapaPodologia
import pe.saniape.app.data.staff.PULSOS_PODO
import pe.saniape.app.data.staff.SITIOS_MONOFILAMENTO
import pe.saniape.app.data.staff.StaffContextoRepo
import pe.saniape.app.data.staff.SugerenciasPodo
import pe.saniape.app.data.staff.TamizajePodo
import pe.saniape.app.data.staff.ZONAS_PODO
import pe.saniape.app.data.staff.ZonaMarcadaPodo
import pe.saniape.app.data.staff.citaEsPodologia
import pe.saniape.app.data.staff.colorGrupoPodo
import pe.saniape.app.data.staff.compararEvaluacionesPodo
import pe.saniape.app.data.staff.cuerpoEvaluacionPodo
import pe.saniape.app.data.staff.diabeticoPorAntecedentes
import pe.saniape.app.data.staff.estadoRiesgo
import pe.saniape.app.data.staff.evaluacionPodoDeJson
import pe.saniape.app.data.staff.fechaControlParaAgendar
import pe.saniape.app.data.staff.fechaEvalPodo
import pe.saniape.app.data.staff.fechaProximoControl
import pe.saniape.app.data.staff.frasesCampoPodo
import pe.saniape.app.data.staff.hallazgosParaZona
import pe.saniape.app.data.staff.hayUlceraActiva
import pe.saniape.app.data.staff.lineasTamizaje
import pe.saniape.app.data.staff.ordenarEvaluacionesPodo
import pe.saniape.app.data.staff.pacienteEsPodologia
import pe.saniape.app.data.staff.paresDatosGenerales
import pe.saniape.app.data.staff.peorHallazgo
import pe.saniape.app.data.staff.pinturaZona
import pe.saniape.app.data.staff.recomendacionesRiesgo
import pe.saniape.app.data.staff.riesgoIwgdf
import pe.saniape.app.data.staff.tamizajeCompleto
import pe.saniape.app.data.staff.tamizajeInicialPodo
import pe.saniape.app.data.staff.tamizajePodoDeJson
import pe.saniape.app.data.staff.tamizajeTieneDatos
import pe.saniape.app.data.staff.tamizajeVacio
import pe.saniape.app.data.staff.textoCambioPodo
import pe.saniape.app.data.staff.textoRiesgo
import pe.saniape.app.data.staff.textoZonaMarcada
import pe.saniape.app.data.staff.validarEvaluacionPodo
import pe.saniape.app.data.staff.zonasPodoDeJson
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * 🦶 Evaluación del pie: gemelo de `lib/__tests__/evaluacion-podologica.test.ts`
 * (mismos casos y valores esperados): riesgo IWGDF 2023 con el tamizaje
 * incompleto, intervalo de control, catálogos, normalización, comparativo y textos.
 */
class EvaluacionPodologicaTest {
    private fun obj(s: String): JsonObject = Json.parseToJsonElement(s).jsonObject

    /** Diabético con TODO registrado normal (monofilamento siente, pulsos presentes). */
    private fun normal(f: (TamizajePodo) -> TamizajePodo = { it }): TamizajePodo = f(
        tamizajeVacio(true).copy(
            monofilamento = SITIOS_MONOFILAMENTO.associate { it.id to true },
            pulsos = PULSOS_PODO.associate { it.id to "presente" },
            vibracion = mapOf("der" to "presente", "izq" to "presente"),
        ),
    )
    private fun conLops(t: TamizajePodo) = t.copy(monofilamento = t.monofilamento + ("der_met5" to false))
    private fun conEap(t: TamizajePodo) = t.copy(pulsos = t.pulsos + ("izq_tibial" to "ausente"))

    // ── riesgoIwgdf — casos de referencia IWGDF 2023 ──

    @Test fun noDiabeticoOSinTamizajeNoAplica() {
        assertNull(riesgoIwgdf(null).categoria)
        assertNull(riesgoIwgdf(tamizajeVacio(false)).categoria)
        assertNull(riesgoIwgdf(normal { it.copy(diabetes = false) }).categoria)
    }

    @Test fun cat0ConSensibilidadYPulsos() {
        val r = riesgoIwgdf(normal())
        assertEquals(0, r.categoria)
        assertFalse(r.lops); assertFalse(r.eap); assertTrue(r.completo)
        assertEquals(emptyList(), r.avisos)
        assertEquals("Una vez al año", CATEGORIAS_IWGDF[0].control)
    }

    @Test fun cat0AunConDeformidad() {
        assertEquals(0, riesgoIwgdf(normal { it.copy(deformidades = listOf("hallux_valgus")) }).categoria)
    }

    @Test fun cat1LopsSola() {
        val r = riesgoIwgdf(conLops(normal()))
        assertEquals(1, r.categoria)
        assertTrue(r.lops)
        assertTrue(Regex("""5\.ª cabeza metatarsiana derecho""").containsMatchIn(r.motivos.joinToString(" ")))
    }

    @Test fun cat1VibracionAusente() {
        assertEquals(1, riesgoIwgdf(normal { it.copy(vibracion = mapOf("der" to "presente", "izq" to "ausente")) }).categoria)
    }

    @Test fun cat1EapSola() {
        assertEquals(1, riesgoIwgdf(conEap(normal())).categoria)
        assertEquals(1, riesgoIwgdf(normal { it.copy(pulsos = it.pulsos + ("der_pedio" to "disminuido")) }).categoria)
        assertEquals(1, riesgoIwgdf(normal { it.copy(eapDiagnosticada = true) }).categoria)
    }

    @Test fun cat2() {
        assertEquals(2, riesgoIwgdf(conEap(conLops(normal()))).categoria)
        assertEquals(2, riesgoIwgdf(conLops(normal { it.copy(deformidades = listOf("dedos_garra")) })).categoria)
        assertEquals(2, riesgoIwgdf(conEap(normal { it.copy(deformidades = listOf("charcot")) })).categoria)
    }

    @Test fun cat3() {
        assertEquals(3, riesgoIwgdf(conLops(normal { it.copy(antecedenteUlcera = true) })).categoria)
        assertEquals(3, riesgoIwgdf(conEap(normal { it.copy(amputacion = true) })).categoria)
        assertEquals(3, riesgoIwgdf(conLops(normal { it.copy(erc = true) })).categoria)
        assertEquals(3, riesgoIwgdf(conEap(conLops(normal { it.copy(deformidades = listOf("hallux_valgus"), antecedenteUlcera = true) }))).categoria)
    }

    @Test fun ulceraPreviaSinLopsNiEapAvisaRevisar() {
        val r = riesgoIwgdf(normal { it.copy(antecedenteUlcera = true) })
        assertEquals(0, r.categoria)
        assertTrue(r.avisos.joinToString(" ").contains("revise el tamizaje"))
    }

    @Test fun incompletoYNadaPositivoSinDeterminarNuncaAnual() {
        val vacio = riesgoIwgdf(tamizajeVacio(true))
        assertNull(vacio.categoria); assertTrue(vacio.aplica); assertTrue(vacio.sinDeterminar); assertFalse(vacio.completo)
        val t = tamizajeVacio(true).let { it.copy(monofilamento = it.monofilamento + ("der_hallux" to true)) }
        val r = riesgoIwgdf(t)
        assertNull(r.categoria)
        assertTrue(r.sinDeterminar)
        assertTrue(r.avisos[0].startsWith("Riesgo sin determinar: completa el tamizaje (falta monofilamento (1/6 sitios) y pulsos (0/4))"))
        // falta solo un pulso: igual sin determinar
        val casi = normal { it.copy(pulsos = it.pulsos + ("izq_tibial" to null)) }
        assertNull(riesgoIwgdf(casi).categoria)
        // sin control sugerido
        assertNull(fechaProximoControl("2026-10-15", r.categoria))
    }

    @Test fun incompletoPeroPositivoQuedaMarcado() {
        val t = tamizajeVacio(true).let { it.copy(monofilamento = it.monofilamento + ("der_met5" to false)) }
        val r = riesgoIwgdf(t)
        assertEquals(1, r.categoria); assertFalse(r.sinDeterminar); assertFalse(r.completo)
        assertTrue(Regex("""Tamizaje incompleto: .* puede subir""").containsMatchIn(r.avisos[0]))
        assertEquals(3, riesgoIwgdf(t.copy(erc = true)).categoria)
        // EAP ya diagnosticada reemplaza a los pulsos para "completo"
        val t2 = normal { it.copy(eapDiagnosticada = true, pulsos = PULSOS_PODO.associate { p -> p.id to null }) }
        assertTrue(riesgoIwgdf(t2).completo)
    }

    @Test fun tamizajeCompletoSinVibracion() {
        val t = normal { it.copy(vibracion = mapOf("der" to null, "izq" to null)) }
        assertTrue(tamizajeCompleto(t))
        assertFalse(tamizajeCompleto(t.copy(monofilamento = t.monofilamento + ("izq_hallux" to null))))
        assertFalse(tamizajeCompleto(null))
    }

    @Test fun estadoYTextoDeLoGuardado() {
        assertEquals(EstadoRiesgoPodo(null, sinDeterminar = true, incompleto = false), estadoRiesgo(tamizajeVacio(true), null))
        val inc = tamizajeVacio(true).let { it.copy(monofilamento = it.monofilamento + ("der_met5" to false)) }
        assertEquals(EstadoRiesgoPodo(1, sinDeterminar = false, incompleto = true), estadoRiesgo(inc, 1))
        assertEquals(EstadoRiesgoPodo(0, sinDeterminar = false, incompleto = false), estadoRiesgo(normal(), 0))
        assertEquals(EstadoRiesgoPodo(null, sinDeterminar = false, incompleto = false), estadoRiesgo(tamizajeVacio(false), null))
        assertEquals("Riesgo sin determinar: completa el tamizaje (monofilamento y pulsos)", textoRiesgo(EstadoRiesgoPodo(null, true, false)))
        assertEquals("Riesgo IWGDF 1 · Bajo (tamizaje incompleto)", textoRiesgo(EstadoRiesgoPodo(1, false, true)))
        assertNull(textoRiesgo(EstadoRiesgoPodo(null, false, false)))
    }

    @Test fun ceroGuardadoAntesDeLaReglaConTamizajeIncompletoEsSinDeterminar() {
        // Fila vieja: riesgo 0 con el monofilamento a medias → nunca "Muy bajo" ni el control anual.
        val t = tamizajeVacio(true).let { it.copy(monofilamento = it.monofilamento + ("der_hallux" to true)) }
        assertEquals(EstadoRiesgoPodo(null, sinDeterminar = true, incompleto = false), estadoRiesgo(t, 0))
        val ev = EvaluacionPodologica("e", "p", fecha = "2026-10-01", tamizaje = t, riesgoIwgdf = 0)
        assertNull(fechaControlParaAgendar(ev, "2026-10-15"))
        // Con el tamizaje completo, el 0 sigue siendo 0.
        assertEquals(EstadoRiesgoPodo(0, sinDeterminar = false, incompleto = false), estadoRiesgo(normal(), 0))
    }

    // ── Intervalo de control ──

    @Test fun fechaSugeridaPorCategoria() {
        assertEquals("2027-10-15", fechaProximoControl("2026-10-15", 0))
        assertEquals("2027-04-15", fechaProximoControl("2026-10-15", 1))
        assertEquals("2027-01-15", fechaProximoControl("2026-10-15", 2))
        assertEquals("2026-11-30", fechaProximoControl("2026-10-31", 3))
        assertNull(fechaProximoControl("2026-10-15", null))
    }

    @Test fun agendarControlNuncaConTamizajeIncompletoSinDeterminar() {
        val sinDet = EvaluacionPodologica("e", "p", fecha = "2026-10-01", tamizaje = tamizajeVacio(true), riesgoIwgdf = null)
        assertNull(fechaControlParaAgendar(sinDet, "2026-10-15"))
        val cat2 = EvaluacionPodologica("e", "p", fecha = "2026-10-01", tamizaje = conEap(conLops(normal())), riesgoIwgdf = 2)
        assertEquals("2027-01-01", fechaControlParaAgendar(cat2, "2026-10-15"))
        // Si la fecha desde la evaluación ya pasó, se cuenta desde hoy.
        val vieja = cat2.copy(fecha = "2025-01-01")
        assertEquals("2027-01-15", fechaControlParaAgendar(vieja, "2026-10-15"))
    }

    @Test fun lasCuatroCategorias() {
        assertEquals(
            listOf(0 to "Una vez al año", 1 to "Cada 6 a 12 meses", 2 to "Cada 3 a 6 meses", 3 to "Cada 1 a 3 meses"),
            CATEGORIAS_IWGDF.map { it.categoria to it.control },
        )
    }

    @Test fun recomendaciones() {
        assertEquals(emptyList(), recomendacionesRiesgo(null))
        assertTrue(recomendacionesRiesgo(0).size < recomendacionesRiesgo(1).size)
        assertTrue(recomendacionesRiesgo(3).joinToString(" ").contains("cada 1 a 3 meses"))
        assertTrue(recomendacionesRiesgo(2).joinToString(" ").contains("plantillas a medida"))
    }

    // ── Catálogos (paridad de ids con la web) ──

    @Test fun zonasHallazgosSitiosYPulsos() {
        assertEquals(17, ZONAS_PODO.size)
        assertEquals(8, ZONAS_PODO.count { it.vista == "plantar" })
        assertEquals(17, ZONAS_PODO.map { it.id }.toSet().size)
        assertEquals(HALLAZGOS_PODO.size, HALLAZGOS_PODO.map { it.id }.toSet().size)
        assertEquals(listOf("der_hallux", "der_met1", "der_met5", "izq_hallux", "izq_met1", "izq_met5"), SITIOS_MONOFILAMENTO.map { it.id })
        assertEquals(listOf("der_pedio", "der_tibial", "izq_pedio", "izq_tibial"), PULSOS_PODO.map { it.id })
        assertEquals(
            listOf("hallux", "dedos", "met1", "met_centrales", "met5", "arco", "borde_lateral", "talon", "una1", "una2", "una3", "una4", "una5",
                "dorso_dedos", "interdigital", "dorso", "borde_medial"),
            ZONAS_PODO.map { it.id },
        )
    }

    @Test fun hallazgosSugeridosPorTipoDeZona() {
        val una = hallazgosParaZona("una1")
        assertTrue(una.sugeridos.any { it.id == "onicocriptosis" })
        assertFalse(una.sugeridos.any { it.id == "heloma_duro" })
        assertTrue(una.otros.any { it.id == "heloma_duro" })
        assertTrue(hallazgosParaZona("talon").sugeridos.any { it.id == "fisura" })
    }

    // ── Normalización ──

    @Test fun zonasDescartaFusionaCorrigeVistaYSeveridad() {
        val z = zonasPodoDeJson(Json.parseToJsonElement("""[
            {"pie":"der","vista":"dorsal","zona":"talon","hallazgos":[{"tipo":"fisura","severidad":2},{"tipo":"inventado","severidad":3}]},
            {"pie":"der","vista":"plantar","zona":"talon","hallazgos":[{"tipo":"fisura","severidad":3}]},
            {"pie":"medio","zona":"talon","hallazgos":[{"tipo":"fisura"}]},
            {"pie":"izq","zona":"no-existe","hallazgos":[{"tipo":"fisura"}]},
            {"pie":"izq","zona":"una1","hallazgos":[{"tipo":"onicocriptosis","severidad":9}]},
            {"pie":"izq","zona":"arco","hallazgos":[]},
            "basura"]"""))
        assertEquals(
            listOf(
                ZonaMarcadaPodo("der", "plantar", "talon", listOf(HallazgoMarcado("fisura", 3))),
                ZonaMarcadaPodo("izq", "dorsal", "una1", listOf(HallazgoMarcado("onicocriptosis", 1))),
            ),
            z,
        )
        assertEquals(emptyList(), zonasPodoDeJson(JsonPrimitive("x")))
    }

    @Test fun tamizajeFormaCompletaYValoresRarosFuera() {
        val t = tamizajePodoDeJson(obj("""{"diabetes":true,"monofilamento":{"der_hallux":false,"der_met1":"si","otro":true},
            "vibracion":{"der":"ausente","izq":"quizas"},"pulsos":{"der_pedio":"ausente","izq_tibial":"raro"},
            "deformidades":["charcot","inventada"],"erc":"true"}"""))!!
        assertEquals(mapOf("der_hallux" to false, "der_met1" to null, "der_met5" to null, "izq_hallux" to null, "izq_met1" to null, "izq_met5" to null), t.monofilamento)
        assertEquals(mapOf("der" to "ausente", "izq" to null), t.vibracion)
        assertEquals("ausente", t.pulsos["der_pedio"])
        assertNull(t.pulsos["izq_tibial"])
        assertEquals(listOf("charcot"), t.deformidades)
        assertFalse(t.erc)
        assertNull(tamizajePodoDeJson(null))
        assertNull(tamizajePodoDeJson(Json.parseToJsonElement("[]")))
    }

    // ── Mapa: peor hallazgo y color ──

    @Test fun ulceraGanaSiempreRojaYPlena() {
        val hs = listOf(HallazgoMarcado("hiperqueratosis", 3), HallazgoMarcado("ulcera", 1))
        assertEquals("ulcera", peorHallazgo(hs)?.tipo)
        assertEquals(colorGrupoPodo("ulcera"), pinturaZona(hs)?.color)
        assertEquals(0.95f, pinturaZona(hs)?.opacidad)
    }

    @Test fun igualTipoGanaLaSeveridadMayor() {
        assertEquals(3, peorHallazgo(listOf(HallazgoMarcado("heloma_duro", 1), HallazgoMarcado("heloma_duro", 3)))?.severidad)
        val p = pinturaZona(listOf(HallazgoMarcado("onicomicosis", 1)))!!
        assertEquals(colorGrupoPodo("una"), p.color)
        assertEquals(0.45f, p.opacidad)
        assertNull(pinturaZona(emptyList()))
    }

    @Test fun ulceraActivaYTextoDeZona() {
        assertTrue(hayUlceraActiva(listOf(ZonaMarcadaPodo("izq", "plantar", "met1", listOf(HallazgoMarcado("ulcera", 2))))))
        assertFalse(hayUlceraActiva(emptyList()))
        assertEquals("Pie derecho · Talón: Fisura / grieta (moderado)",
            textoZonaMarcada(ZonaMarcadaPodo("der", "plantar", "talon", listOf(HallazgoMarcado("fisura", 2)))))
    }

    // ── Validación y cuerpo ──

    @Test fun validacion() {
        val p = "11111111-1111-4111-8111-111111111111"
        assertTrue(validarEvaluacionPodo(BorradorEvaluacionPodo(p))[0].contains("vacía"))
        assertTrue("Falta el paciente." in validarEvaluacionPodo(BorradorEvaluacionPodo("", tipoPie = "plano")))
        assertTrue("Tipo de pie no válido." in validarEvaluacionPodo(BorradorEvaluacionPodo(p, tipoPie = "egipcio")))
        assertTrue("Fecha inválida." in validarEvaluacionPodo(BorradorEvaluacionPodo(p, fecha = "15/10/2026", pisada = "neutra")))
        assertTrue(validarEvaluacionPodo(BorradorEvaluacionPodo(p, calzado = "x".repeat(501)))[0].contains("calzado"))
        assertEquals(emptyList(), validarEvaluacionPodo(BorradorEvaluacionPodo(p, higiene = "buena")))
        assertTrue(validarEvaluacionPodo(BorradorEvaluacionPodo(p, tamizaje = tamizajeVacio(false)))[0].contains("vacía"))
        assertFalse(tamizajeTieneDatos(tamizajeVacio(false)))
        assertTrue(tamizajeTieneDatos(tamizajeVacio(true)))
        assertTrue(tamizajeTieneDatos(tamizajeVacio(false).copy(deformidades = listOf("charcot"))))
    }

    @Test fun cuerpoNoMandaElRiesgoNiTamizajeVacio() {
        val b = BorradorEvaluacionPodo("p", fecha = "2026-10-15", citaId = "c", tamizaje = conLops(normal()), tipoPie = "plano", calzado = "  ")
        val c = cuerpoEvaluacionPodo(b)
        assertEquals("p", c["pacienteId"]!!.jsonPrimitive.content)
        assertEquals("c", c["citaId"]!!.jsonPrimitive.content)
        assertFalse("riesgoIwgdf" in c || "riesgo_iwgdf" in c)
        val tam = c["tamizaje"]!!.jsonObject
        assertEquals(false, tam["monofilamento"]!!.jsonObject["der_met5"]!!.jsonPrimitive.boolean)
        assertFalse("completo" in tam)
        assertTrue(c["calzado"] is kotlinx.serialization.json.JsonNull)
        // PATCH: id, sin pacienteId; no diabético sin nada → tamizaje null
        val p = cuerpoEvaluacionPodo(BorradorEvaluacionPodo("p", tamizaje = tamizajeVacio(false), higiene = "buena"), id = "e1")
        assertEquals("e1", p["id"]!!.jsonPrimitive.content)
        assertFalse("pacienteId" in p)
        assertTrue(p["tamizaje"] is kotlinx.serialization.json.JsonNull)
    }

    @Test fun filaDelApi() {
        val e = evaluacionPodoDeJson(obj("""{"id":"e","paciente_id":"p","fecha":"2026-10-01","riesgo_iwgdf":2,
            "zonas":[{"pie":"der","zona":"talon","hallazgos":[{"tipo":"fisura","severidad":2}]}],
            "tamizaje":{"diabetes":true,"completo":true},"tipo_pie":"plano","calzado":null}"""))!!
        assertEquals(2, e.riesgoIwgdf)
        assertEquals("plantar", e.zonas.single().vista)
        assertTrue(e.tamizaje!!.diabetes)
        assertNull(e.calzado)
        assertNull(evaluacionPodoDeJson(obj("""{"fecha":"2026-10-01"}""")))
    }

    @Test fun ordenaLaMasRecientePrimero() {
        val o = ordenarEvaluacionesPodo(listOf(
            EvaluacionPodologica("1", "p", fecha = "2026-01-01", createdAt = "a"),
            EvaluacionPodologica("2", "p", fecha = "2026-05-01", createdAt = "a"),
            EvaluacionPodologica("3", "p", fecha = "2026-05-01", createdAt = "b"),
        ))
        assertEquals(listOf("2026-05-01b", "2026-05-01a", "2026-01-01a"), o.map { "${it.fecha}${it.createdAt}" })
    }

    @Test fun precargaLoQueCasiNoCambia() {
        val ant = EvaluacionPodologica("a", "p", fecha = "2026-01-01",
            tamizaje = conLops(normal { it.copy(deformidades = listOf("charcot"), erc = true) }))
        val t = tamizajeInicialPodo(ant, false)
        assertTrue(t.diabetes)
        assertEquals(listOf("charcot"), t.deformidades)
        assertTrue(t.erc)
        assertTrue(t.monofilamento.values.all { it == null })
        assertFalse(tamizajeInicialPodo(null, false).diabetes)
    }

    // ── Comparativo ──

    private val zA = listOf(
        ZonaMarcadaPodo("der", "plantar", "talon", listOf(HallazgoMarcado("fisura", 3))),
        ZonaMarcadaPodo("izq", "dorsal", "una1", listOf(HallazgoMarcado("onicocriptosis", 2))),
        ZonaMarcadaPodo("izq", "plantar", "met1", listOf(HallazgoMarcado("hiperqueratosis", 1))),
    )
    private val zB = listOf(
        ZonaMarcadaPodo("der", "plantar", "talon", listOf(HallazgoMarcado("fisura", 1))),
        ZonaMarcadaPodo("izq", "plantar", "met1", listOf(HallazgoMarcado("hiperqueratosis", 2), HallazgoMarcado("ulcera", 1))),
    )

    @Test fun comparativoRiesgoYCambios() {
        val c = compararEvaluacionesPodo(zB, 3, zA, 1)
        assertEquals(1, c.riesgo.antes); assertEquals(3, c.riesgo.ahora); assertEquals("subio", c.riesgo.cambio)
        assertEquals(
            listOf(Triple("met1", "ulcera", "nuevo"), Triple("met1", "hiperqueratosis", "empeoro"), Triple("talon", "fisura", "mejoro"), Triple("una1", "onicocriptosis", "resuelto")),
            c.cambios.map { Triple(it.zona, it.hallazgo, it.cambio) },
        )
        assertEquals("Mejoró: Fisura / grieta — Der. Talón (severo → leve)", textoCambioPodo(c.cambios[2]))
    }

    @Test fun sinAnteriorSoloElRiesgo() {
        val c = compararEvaluacionesPodo(zB, 2, null, null)
        assertNull(c.riesgo.antes); assertEquals(2, c.riesgo.ahora); assertNull(c.riesgo.cambio)
        assertEquals(emptyList(), c.cambios)
    }

    // ── Textos del informe ──

    @Test fun lineasDelTamizaje() {
        val l = lineasTamizaje(conEap(conLops(normal { it.copy(deformidades = listOf("hallux_valgus")) })))
        assertEquals("Monofilamento 10 g — Pie derecho: pulpejo del hallux siente, 1.ª cabeza metatarsiana siente, 5.ª cabeza metatarsiana NO siente", l[0])
        assertTrue(l.joinToString("\n").contains("tibial posterior izq. ausente"))
        assertTrue(l.joinToString("\n").contains("Hallux valgus"))
        assertEquals(listOf("Paciente no diabético: no aplica la estratificación IWGDF."), lineasTamizaje(tamizajeVacio(false)))
        assertEquals(emptyList(), lineasTamizaje(null))
    }

    @Test fun paresYFecha() {
        assertEquals(listOf("Tipo de pie" to "Plano", "Pisada" to "Pronadora"), paresDatosGenerales("plano", "pronadora", null, " "))
        assertEquals("05/10/2026", fechaEvalPodo("2026-10-05"))
    }

    @Test fun diabetesPorAntecedentes() {
        assertTrue(diabeticoPorAntecedentes("HTA, Diabetes mellitus tipo 2"))
        assertTrue(diabeticoPorAntecedentes("DM2 hace 10 años"))
        assertTrue(diabeticoPorAntecedentes("DM 2"))
        assertFalse(diabeticoPorAntecedentes("Niega diabetes"))
        assertFalse(diabeticoPorAntecedentes(null, ""))
    }

    @Test fun autocompletadoQueAprende() {
        val s = SugerenciasPodo(calzado = listOf(AprendidoPlan("Punta estrecha", 2), AprendidoPlan("tacón alto", 1)))
        val f = frasesCampoPodo("calzado", s)
        assertFalse(f.porLinea)
        assertEquals("Punta estrecha", f.chips[0].texto)
        assertTrue(frasesCampoPodo("observaciones", null).porLinea)
    }

    // ── ¿Es podología? (por NOMBRE en la web: aquí llegan los ids del contexto) ──

    @Test fun mapaYPacienteEsPodologia() {
        assertTrue(pacienteEsPodologia(MapaPodologia(listOf("p"), solo = true)))
        val m = MapaPodologia(listOf("p"))
        assertTrue(citaEsPodologia(m, especialidadId = "p"))
        assertFalse(citaEsPodologia(m, especialidadId = "m"))
        assertFalse(pacienteEsPodologia(m, listOf(AtencionFisio(especialidadServicioId = "m"))))
        assertTrue(pacienteEsPodologia(m, listOf(AtencionFisio(especialidadServicioId = "p"))))
        assertTrue(pacienteEsPodologia(m, tieneEvaluaciones = true))
        assertTrue(pacienteEsPodologia(m, especialidadesDeQuienMira = listOf("p")))
        assertFalse(pacienteEsPodologia(MapaPodologia(), tieneEvaluaciones = true, especialidadesDeQuienMira = listOf("f")))
    }

    @Test fun contextoLeeLosCamposYToleraBackendViejo() {
        val nuevo = StaffContextoRepo.parsear(obj("""{"clinicaId":"c","rol":"Admin","permisos":{},
            "especialidadesPodologia":["p",null,""],"soloPodologia":true,"especialidadesEstetica":["e"],"soloEstetica":false}"""))
        assertEquals(listOf("p"), nuevo.mapaPodologia.ids)
        assertTrue(nuevo.mapaPodologia.solo)
        assertEquals(listOf("e"), nuevo.mapaEstetica.ids)
        assertFalse(nuevo.mapaEstetica.solo)
        val viejo = StaffContextoRepo.parsear(obj("""{"clinicaId":"c","rol":"Admin","permisos":{}}"""))
        assertFalse(viejo.mapaPodologia.activo)
        assertFalse(viejo.mapaEstetica.activo)
    }

    // Gemelo de lib/__tests__/podologia-evaluacion-pie-cta.test.ts (web, 2026-10-16).
    @Test fun diagnosticoQuePideEvaluarElPie() {
        listOf("Pie diabético", "PIE DIABETICO", "Úlcera plantar en paciente diabético", "Diabetes mellitus tipo 2", "DM2 con neuropatía", "Pie de riesgo")
            .forEach { kotlin.test.assertTrue(pe.saniape.app.data.staff.diagnosticoPideEvaluacionPie(it), it) }
        listOf("Onicocriptosis", "Uña encarnada", "Heloma plantar", "No diabético, hiperqueratosis", "", null)
            .forEach { kotlin.test.assertFalse(pe.saniape.app.data.staff.diagnosticoPideEvaluacionPie(it), it.toString()) }
    }
}
