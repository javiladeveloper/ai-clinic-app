package pe.saniape.app.data.staff

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * 🤰 Carné perinatal — gemelo de `lib/__tests__/embarazo.test.ts` de la web:
 * los MISMOS valores de referencia (Naegele, ACOG CO 700, McDonald/ACOG PB
 * 227, ACOG PB 222, FIGO, IOM 2009, NTS 105-MINSA) y los mismos mensajes.
 */
class EmbarazoTest {

    private val HOY = "2026-08-23"

    private fun emb(
        estado: String = "activo", desenlace: String? = null, fechaFin: String? = null,
        riesgos: List<String> = emptyList(), id: String = "e1", fpp: String = "2026-12-06",
    ) = Embarazo(
        id = id, pacienteId = "p1", fur = "2026-03-01", fpp = fpp, metodoDatacion = "fur",
        gestas = 2, partos = 1, abortos = 0, cesareas = 0, grupoSanguineo = "O", rh = "+",
        pesoPregestacional = 60.0, tallaCm = 160.0, riesgos = riesgos, estado = estado, desenlace = desenlace, fechaFin = fechaFin,
    )

    private fun ctrl(id: String, fecha: String, eg: Int? = null) = ControlPrenatal(id = id, embarazoId = "e1", pacienteId = "p1", fecha = fecha, egDias = eg)

    // ── fechas ──

    @Test fun sumaYRestaDiasSinCorrerse() {
        assertEquals("2026-10-08", sumarDiasEmb("2026-01-01", 280))
        assertEquals(175, diasEntreEmb("2026-03-01", "2026-08-23"))
        assertEquals(-175, diasEntreEmb("2026-08-23", "2026-03-01"))
        // Cambios de año y bisiestos (2028).
        assertEquals("2028-03-01", sumarDiasEmb("2028-02-28", 2))
    }

    @Test fun fechasRealesSolamente() {
        assertTrue(esFechaIsoEmb("2026-02-28"))
        assertFalse(esFechaIsoEmb("2026-02-31"))
        assertFalse(esFechaIsoEmb("01/03/2026"))
    }

    @Test fun aNumeroAceptaComa() {
        assertEquals(62.5, aNumeroEmb("62,5"))
        assertNull(aNumeroEmb(""))
        assertNull(aNumeroEmb("abc"))
    }

    // ── datación ──

    @Test fun fppPorNaegele() {
        assertEquals("2026-10-08", fppNaegele("2026-01-01"))
        val d = datacion(EntradaDatacion(fur = "2026-03-01"))!!
        assertEquals("2026-03-01", d.inicio)
        assertEquals("2026-12-06", d.fpp)
        assertEquals("fur", d.metodo)
        assertFalse(d.corregidaPorEco)
    }

    @Test fun umbralesDeRedatacion() {
        assertEquals(5, umbralRedatacionDias(8 * 7 + 6))
        assertEquals(7, umbralRedatacionDias(9 * 7))
        assertEquals(7, umbralRedatacionDias(15 * 7 + 6))
        assertEquals(10, umbralRedatacionDias(16 * 7))
        assertEquals(10, umbralRedatacionDias(21 * 7 + 6))
        assertEquals(14, umbralRedatacionDias(22 * 7))
        assertEquals(14, umbralRedatacionDias(27 * 7 + 6))
        assertEquals(21, umbralRedatacionDias(28 * 7))
    }

    @Test fun ecoDe7SemanasCorrigeLaFur() {
        val d = datacion(EntradaDatacion(fur = "2026-03-01", ecoFecha = "2026-04-26", ecoEgSemanas = 7, ecoEgDias = 0))!!
        assertEquals("eco", d.metodo)
        assertTrue(d.corregidaPorEco)
        assertEquals(7, d.discrepanciaDias)
        assertEquals(5, d.umbralDias)
        assertEquals("2026-03-08", d.inicio)
        assertEquals("2026-12-13", d.fpp)
    }

    @Test fun dentroDelUmbralSeQuedaLaFur() {
        val d = datacion(EntradaDatacion(fur = "2026-03-01", ecoFecha = "2026-04-26", ecoEgSemanas = 7, ecoEgDias = 3))!!
        assertEquals("fur", d.metodo)
        assertEquals("2026-12-06", d.fpp)
        assertEquals(4, d.discrepanciaDias)
        assertFalse(d.corregidaPorEco)
        val fur = "2026-03-01"
        assertEquals("fur", datacion(EntradaDatacion(fur = fur, ecoFecha = sumarDiasEmb(fur, 84 + 7), ecoEgSemanas = 12, ecoEgDias = 0))!!.metodo)
        assertEquals("eco", datacion(EntradaDatacion(fur = fur, ecoFecha = sumarDiasEmb(fur, 84 + 8), ecoEgSemanas = 12, ecoEgDias = 0))!!.metodo)
    }

    @Test fun sinFurOFurInciertaMandaLaEco() {
        val sinFur = datacion(EntradaDatacion(ecoFecha = "2026-04-26", ecoEgSemanas = 7, ecoEgDias = 0))!!
        assertEquals("eco", sinFur.metodo)
        assertEquals("2026-03-08", sinFur.inicio)
        val d = datacion(EntradaDatacion(fur = "2026-03-01", furIncierta = true, ecoFecha = "2026-04-26", ecoEgSemanas = 7, ecoEgDias = 3))!!
        assertEquals("eco", d.metodo)
        assertTrue(d.motivo.contains("incierta"))
    }

    @Test fun sinNadaConQueDatar() {
        assertNull(datacion(EntradaDatacion()))
        assertNull(datacion(EntradaDatacion(fur = "2026-02-31")))
    }

    // ── edad gestacional ──

    @Test fun egEnDiasTextoYTrimestre() {
        assertEquals(175, egDiasEn("2026-03-01", HOY))
        assertEquals("25 sem", textoEg(175))
        assertEquals("25 sem 3 d", textoEg(178))
        assertEquals(1, trimestre(13 * 7 + 6))
        assertEquals(2, trimestre(14 * 7))
        assertEquals(3, trimestre(28 * 7))
    }

    @Test fun egFueraDeRango() {
        assertNull(egDiasEn("2026-03-01", "2026-02-28"))
        assertNull(egDiasEn("2025-01-01", HOY))
    }

    // ── altura uterina ──

    @Test fun auEsperadaEntre20Y38() {
        assertEquals(AuEsperada(28.0, 26.0, 30.0), auEsperada(28 * 7))
        assertNull(auEsperada(19 * 7 + 6))
        assertNotNull(auEsperada(20 * 7))
        assertNotNull(auEsperada(38 * 7 + 6))
        assertNull(auEsperada(39 * 7))
    }

    @Test fun alertaAcogSoloDe24A38() {
        evaluarAu(19.9, 23 * 7 + 6)!!.let { assertEquals("atencion", it.nivel); assertFalse(it.acog) }
        evaluarAu(20.9, 24 * 7)!!.let { assertEquals("alerta", it.nivel); assertTrue(it.acog) }
        evaluarAu(21.0, 24 * 7)!!.let { assertEquals("atencion", it.nivel); assertTrue(it.acog) }
        assertEquals("alerta", evaluarAu(34.0, 38 * 7 + 6)?.nivel)
        assertNull(evaluarAu(34.0, 39 * 7))
        val antes = alertasControl(ctrl("c", HOY, 22 * 7).copy(alturaUterina = 17.0))
        assertEquals("au", antes[0].id)
        assertEquals("atencion", antes[0].nivel)
        assertFalse(antes[0].fuente.orEmpty().contains("ACOG"))
        val despues = alertasControl(ctrl("c", HOY, 30 * 7).copy(alturaUterina = 26.0))
        assertEquals("au", despues[0].id)
        assertEquals("alerta", despues[0].nivel)
        assertEquals("ACOG PB 227 (2021)", despues[0].fuente)
    }

    @Test fun auAcordeAtencionYAlerta() {
        evaluarAu(28.0, 28 * 7)!!.let { assertEquals("acorde", it.estado); assertEquals("ok", it.nivel) }
        evaluarAu(30.5, 28 * 7)!!.let { assertEquals("alta", it.estado); assertEquals("atencion", it.nivel); assertEquals(2.5, it.diferencia) }
        evaluarAu(24.0, 28 * 7)!!.let { assertEquals("baja", it.estado); assertEquals("alerta", it.nivel); assertEquals(-4.0, it.diferencia) }
        assertNull(evaluarAu(null, 28 * 7))
    }

    @Test fun textoDeLaAlertaDeAu() {
        val a = alertasControl(ctrl("c", HOY, 30 * 7).copy(alturaUterina = 26.0))[0]
        assertEquals("AU 26 cm a las 30 sem: -4 cm de lo esperado — ecografía para descartar restricción del crecimiento u oligohidramnios.", a.texto)
        val b = alertasControl(ctrl("c", HOY, 22 * 7).copy(alturaUterina = 25.0))[0]
        assertEquals("AU 25 cm a las 22 sem: +3 cm de lo esperado (fuera de EG ± 2 cm).", b.texto)
    }

    // ── presión arterial ──

    @Test fun paElevadaYNormal() {
        assertEquals(emptyList(), alertasPa(139, 89, 200, null))
        assertEquals(listOf("pa_elevada"), alertasPa(140, 85, 200, null).map { it.id })
        assertEquals(listOf("pa_elevada"), alertasPa(120, 90, 200, null).map { it.id })
    }

    @Test fun paSevera() {
        assertEquals(listOf("pa_severa"), alertasPa(160, 100, 200, null).map { it.id })
        assertEquals("alerta", alertasPa(150, 110, 200, null)[0].nivel)
    }

    @Test fun preeclampsiaYHtaCronica() {
        assertEquals(listOf("pa_elevada", "sospecha_preeclampsia"), alertasPa(145, 95, 30 * 7, "+").map { it.id })
        val antes = alertasPa(145, 95, 16 * 7, "+")
        assertEquals(listOf("pa_elevada"), antes.map { it.id })
        assertTrue(antes[0].texto.contains("HTA crónica"))
    }

    // ── LCF ──

    @Test fun lcf110a160DesdeLa20() {
        assertEquals("lcf_baja", alertaLcf(105, 30 * 7)?.id)
        assertEquals("lcf_alta", alertaLcf(165, 30 * 7)?.id)
        assertNull(alertaLcf(140, 30 * 7))
        assertNull(alertaLcf(170, 12 * 7))
    }

    // ── ganancia de peso ──

    @Test fun imcYCategoria() {
        assertEquals(23.4, imcEmb(60.0, 160.0))
        assertEquals("bajo", categoriaImc(18.4))
        assertEquals("normal", categoriaImc(18.5))
        assertEquals("sobrepeso", categoriaImc(25.0))
        assertEquals("obesidad", categoriaImc(30.0))
        assertNull(imcEmb(60.0, null))
    }

    @Test fun rangosDelIom() {
        assertEquals(
            mapOf("bajo" to RangoKg(12.5, 18.0), "normal" to RangoKg(11.5, 16.0), "sobrepeso" to RangoKg(7.0, 11.5), "obesidad" to RangoKg(5.0, 9.0)),
            GANANCIA_IOM,
        )
    }

    @Test fun bandaIomPorSemana() {
        assertEquals(RangoKg(0.0, 0.0), bandaIom("normal", 0))
        assertEquals(RangoKg(0.5, 2.0), bandaIom("normal", 13 * 7))
        assertEquals(RangoKg(11.5, 16.0), bandaIom("normal", 40 * 7))
        assertEquals(RangoKg(6.0, 9.0), bandaIomEn("normal", 26.5 * 7))
        assertEquals(RangoKg(5.0, 9.0), bandaIom("obesidad", 40 * 7))
    }

    @Test fun gemelarRangosProvisionales() {
        assertEquals(mapOf("normal" to RangoKg(17.0, 25.0), "sobrepeso" to RangoKg(14.0, 23.0), "obesidad" to RangoKg(11.0, 19.0)), GANANCIA_IOM_GEMELAR)
        assertEquals(RangoKg(17.0, 25.0), bandaIom("normal", 40 * 7, true))
        assertNull(bandaIom("bajo", 40 * 7, true))
        assertEquals(emptyList(), bandaGanancia("bajo", true))
        assertEquals("dentro", evaluarGanancia(75.0, 60.0, 160.0, 28 * 7, true)?.estado)
        assertNull(evaluarGanancia(48.0, 45.0, 160.0, 28 * 7, true))
        val a = alertasControl(ctrl("c", HOY, 28 * 7).copy(peso = 90.0), emb(riesgos = listOf("embarazo_multiple")))
        assertEquals("ganancia", a[0].id)
        assertEquals("IOM (2009), guía provisional para gemelares", a[0].fuente)
        assertEquals("17–25 kg (gemelar)", textoGananciaRecomendada("normal", true))
        assertTrue(textoGananciaRecomendada("bajo", true).contains("sin rango"))
        assertEquals("11.5–16 kg", textoGananciaRecomendada("normal"))
    }

    @Test fun dentroEncimaDebajo() {
        assertEquals("dentro", evaluarGanancia(68.0, 60.0, 160.0, 28 * 7)?.estado)
        assertEquals("encima", evaluarGanancia(75.0, 60.0, 160.0, 28 * 7)?.estado)
        assertEquals("debajo", evaluarGanancia(61.0, 60.0, 160.0, 28 * 7)?.estado)
        assertNull(evaluarGanancia(68.0, null, 160.0, 28 * 7))
    }

    // ── NTS 105-MINSA ──

    @Test fun seisAtencionesMinimas() {
        cumplimientoMinsa(2, 23 * 7).let { assertEquals(2, it.esperadosALaFecha); assertFalse(it.atrasada); assertFalse(it.controlada) }
        assertTrue(cumplimientoMinsa(1, 23 * 7).atrasada)
        cumplimientoMinsa(6, 40 * 7).let { assertTrue(it.controlada); assertEquals(6, it.minimo) }
        assertEquals(0, cumplimientoMinsa(0, 10 * 7).esperadosALaFecha)
    }

    @Test fun mensualQuincenalSemanal() {
        assertEquals("2026-06-29", proximoControlSugerido("2026-06-01", 20 * 7))
        assertEquals("2026-06-15", proximoControlSugerido("2026-06-01", 33 * 7))
        assertEquals("2026-06-08", proximoControlSugerido("2026-06-01", 37 * 7))
        assertEquals("2026-06-29", proximoControlSugerido("2026-06-01", null))
    }

    @Test fun alCruzarDePeriodoSeRecorta() {
        assertEquals("2026-06-22", proximoControlSugerido("2026-06-01", 29 * 7))
        assertEquals("2026-06-15", proximoControlSugerido("2026-06-01", 30 * 7))
        assertEquals("2026-06-15", proximoControlSugerido("2026-06-01", 31 * 7 + 5))
        assertEquals("2026-06-08", proximoControlSugerido("2026-06-01", 35 * 7))
        assertEquals("2026-06-12", proximoControlSugerido("2026-06-01", 34 * 7 + 3))
    }

    @Test fun suplementosPorEg() {
        assertEquals(listOf("acido_folico"), suplementosSugeridos(10 * 7))
        assertEquals(listOf("acido_folico", "hierro"), suplementosSugeridos(16 * 7))
        assertEquals(listOf("acido_folico", "hierro", "calcio"), suplementosSugeridos(24 * 7))
    }

    @Test fun riesgosDeducidos() {
        assertEquals(listOf("edad_35", "rh_negativo", "cesarea_previa"), riesgosSugeridos(edad = 38, rh = "-", cesareas = 1))
        assertEquals(listOf("adolescente"), riesgosSugeridos(edad = 17))
        assertEquals(emptyList(), riesgosSugeridos(edad = 28, rh = "+"))
    }

    // ── alertas del control ──

    @Test fun loGravePrimero() {
        assertEquals(emptyList(), alertasControl(ctrl("c", HOY), emb()))
        val a = alertasControl(
            ctrl("c", HOY, 28 * 7).copy(
                paSistolica = 120, paDiastolica = 80, alturaUterina = 30.5, lcf = 150,
                movimientosFetales = "disminuidos", signosAlarma = listOf("sangrado"), peso = 75.0,
            ),
            emb(),
        )
        assertEquals(listOf("signos_alarma", "mov_disminuidos", "au", "ganancia"), a.map { it.id })
        assertEquals("alerta", a[0].nivel)
        assertEquals("Refiere signos de alarma: Sangrado vaginal.", a[0].texto)
    }

    @Test fun proteinuriaSinHipertension() {
        assertEquals(listOf("proteinuria"), alertasControl(ctrl("c", HOY, 200).copy(proteinuria = "++")).map { it.id })
    }

    // ── resumen ──

    @Test fun resumenEgImcMinsaYProximo() {
        val controles = listOf(
            ctrl("a", "2026-05-10").copy(peso = 61.0),
            ctrl("b", "2026-07-19", 140).copy(peso = 64.0, proximoControl = "2026-08-16"),
        )
        val r = resumenEmbarazo(emb(), controles, HOY)
        assertEquals(175, r.egHoyDias)
        assertEquals(2, r.trimestre)
        assertEquals(23.4, r.imcPregestacional)
        assertEquals("normal", r.categoriaImc)
        assertEquals(2, r.minsa.realizados)
        assertEquals("2026-08-16", r.proximoControl)
        assertTrue(r.proximoVencido)
        assertEquals(diasEntreEmb(HOY, "2026-12-06"), r.diasParaFpp)
    }

    @Test fun sinControlesYFinalizado() {
        assertEquals(HOY, resumenEmbarazo(emb(), emptyList(), HOY).proximoControl)
        val fin = resumenEmbarazo(emb(estado = "finalizado", desenlace = "parto", fechaFin = "2026-08-01"), emptyList(), HOY)
        assertNull(fin.egHoyDias)
        assertNull(fin.proximoControl)
    }

    @Test fun formulaYGrupo() {
        assertEquals("G3 P1 A1 C0", formulaObstetrica(3, 1, 1, 0))
        assertNull(formulaObstetrica(null, null, null, null))
        assertEquals("O Rh−", grupoRh("O", "-"))
        assertNull(grupoRh(null, null))
    }

    @Test fun ordenActivoPrimeroYControlesCronologicos() {
        val l = ordenarEmbarazos(listOf(
            emb(id = "x", estado = "finalizado", fpp = "2027-01-01", desenlace = "parto", fechaFin = "2026-12-30"),
            emb(id = "y"),
        ))
        assertEquals(listOf("y", "x"), l.map { it.id })
        assertEquals(listOf("1", "2"), ordenarControles(listOf(ctrl("2", "2026-06-01"), ctrl("1", "2026-05-01"))).map { it.id })
    }

    // ── series de los gráficos ──

    private val dSerie = datacion(EntradaDatacion(fur = "2026-03-01"))
    private val csSerie = listOf(
        ctrl("a", "2026-07-19").copy(alturaUterina = 20.0, peso = 64.0, paSistolica = 110, paDiastolica = 70),
        ctrl("b", "2026-08-16", 168).copy(alturaUterina = 24.0, peso = 66.0),
    )

    @Test fun serieAuYBanda() {
        assertEquals(listOf(PuntoSerieEmb(20.0, 20.0, "2026-07-19"), PuntoSerieEmb(24.0, 24.0, "2026-08-16")), serieAu(csSerie, dSerie))
        val b = bandaAu()
        assertEquals(PuntoBandaEmb(20.0, 18.0, 22.0), b.first())
        assertEquals(PuntoBandaEmb(38.0, 36.0, 40.0), b.last())
    }

    @Test fun serieGananciaYPa() {
        assertEquals(listOf(4.0, 6.0), serieGanancia(csSerie, dSerie, 60.0).map { it.y })
        assertEquals(emptyList(), serieGanancia(csSerie, dSerie, null))
        assertEquals(PuntoBandaEmb(40.0, 11.5, 16.0), bandaGanancia("normal").last())
        assertEquals(listOf(PuntoSerieEmb(20.0, 110.0, "2026-07-19")), seriePa(csSerie, dSerie).first)
    }

    // ── validación del embarazo ──

    @Test fun furOEcoObligatoriaYNoFuturas() {
        assertTrue(validarEmbarazo(BorradorEmbarazo("p"), HOY).joinToString(" ").contains("FUR o una ecografía"))
        assertTrue(validarEmbarazo(BorradorEmbarazo("p", fur = "2026-09-01"), HOY).joinToString(" ").contains("futura"))
        assertTrue(validarEmbarazo(BorradorEmbarazo("p", ecoFecha = "2026-05-01"), HOY).joinToString(" ").contains("semanas"))
        assertEquals(emptyList(), validarEmbarazo(BorradorEmbarazo("p", fur = "2026-03-01"), HOY))
    }

    @Test fun terminoNoAntesDelInicio() {
        assertTrue(validarEmbarazo(BorradorEmbarazo("p", fur = "2026-03-01", estado = "finalizado", desenlace = "aborto", fechaFin = "2026-02-20"), HOY)
            .joinToString(" ").contains("anterior al inicio del embarazo (01/03/2026)"))
        assertEquals(emptyList(), validarEmbarazo(BorradorEmbarazo("p", fur = "2026-03-01", estado = "finalizado", desenlace = "aborto", fechaFin = "2026-03-01"), HOY))
    }

    @Test fun masDe44SemanasSoloFinalizado() {
        assertTrue(validarEmbarazo(BorradorEmbarazo("p", fur = "2025-06-01"), HOY).joinToString(" ").contains("44 semanas"))
        assertEquals(emptyList(), validarEmbarazo(BorradorEmbarazo("p", fur = "2025-06-01", estado = "finalizado", desenlace = "parto", fechaFin = "2026-03-05"), HOY))
        assertEquals(2, validarEmbarazo(BorradorEmbarazo("p", fur = "2026-03-01", estado = "finalizado"), HOY).size)
    }

    @Test fun rangosGrupoRhPesoFormula() {
        val e = validarEmbarazo(BorradorEmbarazo("p", fur = "2026-03-01", grupoSanguineo = "C", rh = "x", pesoPregestacional = "10", gestas = "0"), HOY)
        assertEquals(4, e.size)
        assertTrue(e.contains("Peso pregestacional: fuera de rango (25–250)."))
        assertTrue(e.contains("Gestas: fuera de rango (1–30)."))
    }

    @Test fun avisosFormulaIncoherente() {
        assertEquals(1, avisosEmbarazo(BorradorEmbarazo("p", gestas = "2", partos = "1", abortos = "1")).size)
        assertEquals(emptyList(), avisosEmbarazo(BorradorEmbarazo("p", gestas = "3", partos = "1", abortos = "1")))
    }

    @Test fun borradorDesdeFilaIdaYVuelta() {
        val b = borradorDesdeEmbarazo(emb())
        assertEquals("60", b.pesoPregestacional)
        assertEquals("2", b.gestas)
        assertEquals("2026-12-06", datacion(b.entrada)!!.fpp)
        val cuerpo = cuerpoEmbarazo(BorradorEmbarazo("p", fur = "2026-03-01", ecoFecha = "2026-04-26", ecoEgSemanas = "7", ecoEgDias = "0",
            pesoPregestacional = "60,5", riesgos = listOf("edad_35")))
        assertEquals("p", cuerpo["pacienteId"]!!.jsonPrimitive.content)
        assertEquals(60.5, cuerpo["pesoPregestacional"]!!.jsonPrimitive.content.toDouble())
        assertEquals("7", cuerpo["ecoEgSemanas"]!!.jsonPrimitive.content)
        assertEquals(JsonNull, cuerpo["gestas"])
    }

    // ── validación del control ──

    @Test fun controlAlMenosUnaMedidaYPaCoherente() {
        assertTrue(validarControl(BorradorControl("e"), HOY).joinToString(" ").contains("al menos una medida"))
        assertTrue(validarControl(BorradorControl("e", paSistolica = "120"), HOY).joinToString(" ").contains("sistólica y diastólica"))
        assertTrue(validarControl(BorradorControl("e", paSistolica = "80", paDiastolica = "120"), HOY).joinToString(" ").contains("mayor"))
        assertTrue(validarControl(BorradorControl("e", lcf = "300"), HOY).joinToString(" ").contains("LCF"))
        assertEquals(emptyList(), validarControl(BorradorControl("e", peso = "62,5"), HOY))
        assertTrue(validarControl(BorradorControl("e", peso = "62", fecha = "2026-09-01"), HOY).joinToString(" ").contains("futura"))
    }

    @Test fun controlLocalCalculaEgYProximo() {
        val c = controlLocal(
            BorradorControl("e", fecha = HOY, peso = "62,5", alturaUterina = "25", suplementos = listOf("hierro", "x"), movimientosFetales = "raro"),
            datacion(EntradaDatacion(fur = "2026-03-01")), HOY, "tmp-1", "p",
        )
        assertEquals(175, c.egDias)
        assertEquals(62.5, c.peso)
        assertEquals(25.0, c.alturaUterina)
        assertEquals(listOf("hierro"), c.suplementos)
        assertNull(c.movimientosFetales)
        assertEquals("2026-09-20", c.proximoControl)
        assertTrue(c.pendiente)
        // EG explícita (corregida a mano) manda.
        assertEquals(180, controlLocal(BorradorControl("e", fecha = HOY, egDias = "180", peso = "60"), null, HOY, "t", "p").egDias)
    }

    @Test fun controlDentroDelEmbarazo() {
        val e = emb()
        assertTrue(validarControlEnEmbarazo("2026-02-28", e, false).joinToString(" ").contains("anterior al inicio"))
        assertEquals(emptyList(), validarControlEnEmbarazo("2026-03-01", e, false))
        assertTrue(validarControlEnEmbarazo("2027-01-05", e, false).joinToString(" ").contains("44 semanas"))
        val fin = emb(estado = "finalizado", desenlace = "parto", fechaFin = "2026-08-01")
        assertTrue(validarControlEnEmbarazo("2026-07-01", fin, false).joinToString(" ").contains("finalizado"))
        assertEquals(emptyList(), validarControlEnEmbarazo("2026-07-01", fin, true))
        assertTrue(validarControlEnEmbarazo("2026-08-02", fin, true).joinToString(" ").contains("posterior al fin"))
    }

    @Test fun claveDeIdempotenciaYProximoPosterior() {
        assertTrue(validarControl(BorradorControl("e", peso = "60", clave = "corta"), HOY).joinToString(" ").contains("idempotencia"))
        val uuid = "2b1f6c1e-6d3a-4a8e-9a55-1c2b3d4e5f60"
        assertEquals(emptyList(), validarControl(BorradorControl("e", peso = "60", clave = uuid), HOY))
        assertTrue(validarControl(BorradorControl("e", peso = "60", fecha = HOY, proximoControl = HOY), HOY).joinToString(" ").contains("después"))
        // POST: lleva la clave y el embarazo; PATCH: ni clave ni embarazo.
        val post = cuerpoControl(BorradorControl("e", peso = "60", clave = uuid, citaId = "c1"))
        assertEquals(uuid, post["clave"]!!.jsonPrimitive.content)
        assertEquals("e", post["embarazoId"]!!.jsonPrimitive.content)
        assertEquals("c1", post["citaId"]!!.jsonPrimitive.content)
        assertEquals("60", post["peso"]!!.jsonPrimitive.content)
        val patch = cuerpoControl(BorradorControl("e", peso = "60", clave = uuid), id = "x")
        assertNull(patch["clave"])
        assertNull(patch["embarazoId"])
        assertEquals("x", patch["id"]!!.jsonPrimitive.content)
    }

    // ── lectura de filas ──

    @Test fun filasDeLaBaseTolerantes() {
        val o = Json.parseToJsonElement("""{"id":"e1","paciente_id":"p1","fur":"2026-03-01","fur_incierta":false,"eco_fecha":null,
            "eco_eg_semanas":null,"fpp":"2026-12-06","metodo_datacion":"fur","gestas":2,"peso_pregestacional":"60.00","talla_cm":"160.0",
            "riesgos":["edad_35"],"estado":"activo","desenlace":null,"fecha_fin":null}""").jsonObject
        val e = embarazoDeJson(o)!!
        assertEquals(60.0, e.pesoPregestacional)
        assertEquals(160.0, e.tallaCm)
        assertEquals(listOf("edad_35"), e.riesgos)
        assertTrue(e.activo)
        val c = controlDeJson(Json.parseToJsonElement("""{"id":"c1","embarazo_id":"e1","paciente_id":"p1","fecha":"2026-08-23",
            "eg_dias":175,"peso":"62.50","pa_sistolica":120,"pa_diastolica":80,"altura_uterina":"25.0","suplementos":["hierro"],
            "signos_alarma":[],"signos_alarma_explicados":true,"proximo_control":"2026-09-20"}""").jsonObject)!!
        assertEquals(62.5, c.peso)
        assertEquals(25.0, c.alturaUterina)
        assertTrue(c.signosAlarmaExplicados)
        assertNull(embarazoDeJson(Json.parseToJsonElement("""{"fur":"2026-03-01"}""").jsonObject))
        // Sin fecha válida el control se salta (nunca llega una fecha vacía a las reglas).
        assertNull(controlDeJson(Json.parseToJsonElement("""{"id":"c2","fecha":""}""").jsonObject))
        assertNull(controlDeJson(Json.parseToJsonElement("""{"id":"c3","fecha":"2026-02-31"}""").jsonObject))
        assertNull(controlDeJson(Json.parseToJsonElement("""{"id":"c4","fecha":"2026-08-23","proximo_control":"x"}""").jsonObject)!!.proximoControl)
    }

    // ── ¿es gineco-obstetricia? (solo por rubro guardado) ──

    @Test fun mapaGinecoSoloRubroGuardado() {
        assertEquals(MapaGineco(listOf("g", "o"), true), mapaGineco(listOf("g" to "ginecologia", "o" to "ginecologia")))
        assertEquals(MapaGineco(emptyList(), false), mapaGineco(listOf("m" to "medicina_general", "x" to null)))
        assertEquals(MapaGineco(emptyList(), false), mapaGineco(null))
    }

    @Test fun clinicaMixtaPorCitaEmbarazosOQuienMira() {
        val m = mapaGineco(listOf("g" to "ginecologia", "m" to "medicina_general"))
        assertTrue(citaEsGineco(m, especialidadId = "g"))
        assertFalse(citaEsGineco(m, especialidadId = "m"))
        assertFalse(pacienteEsGineco(m))
        assertTrue(pacienteEsGineco(m, tieneEmbarazos = true))
        assertTrue(pacienteEsGineco(m, atenciones = listOf(AtencionFisio(especialidadServicioId = "g"))))
        assertTrue(pacienteEsGineco(m, especialidadesDeQuienMira = listOf("g")))
        // Fisio / DALU: nunca.
        assertFalse(pacienteEsGineco(mapaGineco(listOf("f" to "fisioterapia")), tieneEmbarazos = true))
    }

    @Test fun aprendidosDeLasSugerencias() {
        val o = Json.parseToJsonElement("""{"observaciones":[{"texto":"Gestante con cefalea leve","usos":3},"Niega signos de alarma",{"texto":""}]}""").jsonObject
        val a = aprendidosControlDeJson(o)
        assertEquals(listOf("Gestante con cefalea leve", "Niega signos de alarma"), a.map { it.texto })
        assertTrue(frasesObservacionControl(a).porLinea)
        assertEquals(JsonPrimitive("x"), JsonPrimitive("x"))
    }
}
