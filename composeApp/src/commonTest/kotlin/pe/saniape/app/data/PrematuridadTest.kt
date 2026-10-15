package pe.saniape.app.data

import kotlin.math.abs
import kotlin.math.ceil
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import pe.saniape.app.data.staff.AVISO_FIN_CORRECCION
import pe.saniape.app.data.staff.DIAS_POR_MES
import pe.saniape.app.data.staff.DatosCurvaFicha
import pe.saniape.app.data.staff.datosCurvaDeFila
import pe.saniape.app.data.staff.EdadEvaluada
import pe.saniape.app.data.staff.EdadGestacional
import pe.saniape.app.data.staff.IndicadorCrecimiento.PESO_EDAD
import pe.saniape.app.data.staff.MedicionCrecimiento
import pe.saniape.app.data.staff.TablaOms
import pe.saniape.app.data.staff.TablasOms
import pe.saniape.app.data.staff.cuerpoActualizarFicha
import pe.saniape.app.data.staff.edadCurvaMeses
import pe.saniape.app.data.staff.edadParaEvaluar
import pe.saniape.app.data.staff.edadParaEvaluarEntre
import pe.saniape.app.data.staff.egDe
import pe.saniape.app.data.staff.egDeFila
import pe.saniape.app.data.staff.egEnDias
import pe.saniape.app.data.staff.egValida
import pe.saniape.app.data.staff.esPrematuro
import pe.saniape.app.data.staff.evaluarMedicion
import pe.saniape.app.data.staff.fechaTermino
import pe.saniape.app.data.staff.htmlCurvaCrecimiento
import pe.saniape.app.data.staff.leyendaPrematuridad
import pe.saniape.app.data.staff.lmsEn
import pe.saniape.app.data.staff.mesesCumplidos
import pe.saniape.app.data.staff.primeraSinCorreccion
import pe.saniape.app.data.staff.puntosPaciente
import pe.saniape.app.data.staff.textoEdadCorregida
import pe.saniape.app.data.staff.textoEdadEvaluada
import pe.saniape.app.data.staff.textoEg
import pe.saniape.app.data.staff.valorEnZ
import pe.saniape.app.data.staff.ventanaCurva
import pe.saniape.app.data.staff.zIndicador

/**
 * 🍼 Corrección por prematuridad: gemelo de `lib/__tests__/prematuridad.test.ts`
 * de la web (mismos casos). Edad corregida = cronológica − (40 sem − EG), solo
 * con EG < 37 sem y hasta cumplir 2 años; antes de las 40 sem de EPM, ningún z.
 */
class PrematuridadTest {

    private val t = TablasOms.cargar()
    private val eg30 = EdadGestacional(30, 0)
    private val nac = "2026-01-01"
    private fun medianaPeso(dias: Int) = valorEnZ(0.0, lmsEn(t.curva(TablaOms.WFA, "M"), dias.toDouble())!!)
    private fun r1(n: Double) = kotlin.math.floor(n * 10 + 0.5) / 10
    private fun r2(n: Double) = kotlin.math.floor(n * 100 + 0.5) / 100

    // ─── La regla ────────────────────────────────────────────────────────────

    @Test fun treintaSemanasA120DiasSon50Corregidos() {
        assertEquals(EdadEvaluada(50, usaEdadCorregida = true, antesDelTermino = false, postmenstrualDias = 210 + 120), edadParaEvaluar(120, eg30))
        assertEquals(1.6, r1(50 / DIAS_POR_MES))
    }

    @Test fun treintaYSeisMasSeisTodaviaCorrige() {
        val e = edadParaEvaluar(60, EdadGestacional(36, 6))
        assertEquals(38, e.dias)
        assertTrue(e.usaEdadCorregida)
    }

    @Test fun treintaYSieteNoCorrige() {
        val e = edadParaEvaluar(60, EdadGestacional(37, 0))
        assertEquals(60, e.dias)
        assertFalse(e.usaEdadCorregida)
        assertFalse(e.antesDelTermino)
    }

    @Test fun corteALosDosAnios() {
        // Solo con días (sin calendario): meses OMS, como la web (23,98 m sí; 24,0 m no).
        assertEquals(660, edadParaEvaluar(730, eg30).dias)
        val d24 = ceil(24 * DIAS_POR_MES).toInt() // 731
        assertEquals(EdadEvaluada(d24, false, false, 210 + d24), edadParaEvaluar(d24, eg30))
        // Con las fechas: el corte es "cumplió 2 años" por CALENDARIO (730 días = 2 años exactos acá).
        assertEquals(24, mesesCumplidos(nac, "2028-01-01"))
        assertEquals(23, mesesCumplidos(nac, "2027-12-31"))
        assertFalse(edadParaEvaluar(730, eg30, mesesCal = 24).usaEdadCorregida)
        assertEquals(EdadEvaluada(659, true, false, 210 + 729), edadParaEvaluarEntre(nac, "2027-12-31", eg30))
        assertEquals(EdadEvaluada(730, false, false, 210 + 730), edadParaEvaluarEntre(nac, "2028-01-01", eg30))
        assertFalse(edadParaEvaluarEntre(nac, "2028-01-01", eg30)!!.usaEdadCorregida)
        val ev = evaluarMedicion(t, "M", nac, MedicionCrecimiento("2028-01-01", peso = 12.0), eg30)!!
        assertFalse(ev.usaEdadCorregida)
        assertNull(ev.edadCorregidaTexto)
        assertTrue(evaluarMedicion(t, "M", nac, MedicionCrecimiento("2027-12-31", peso = 12.0), eg30)!!.usaEdadCorregida)
        // 29 de febrero cumple el 1 de marzo (el 28/2 aún corrige).
        assertEquals(23, mesesCumplidos("2024-02-29", "2026-02-28"))
        assertEquals(24, mesesCumplidos("2024-02-29", "2026-03-01"))
        assertTrue(edadParaEvaluarEntre("2024-02-29", "2026-02-28", eg30)!!.usaEdadCorregida)
        assertFalse(edadParaEvaluarEntre("2024-02-29", "2026-03-01", eg30)!!.usaEdadCorregida)
    }

    @Test fun sinEgLaCronologica() {
        assertEquals(EdadEvaluada(120, false, false, null), edadParaEvaluar(120, null))
    }

    @Test fun antesDelTerminoSinEdadParaEvaluar() {
        assertEquals(EdadEvaluada(null, usaEdadCorregida = false, antesDelTermino = true, postmenstrualDias = 230), edadParaEvaluar(20, eg30))
        // Justo a las 40 semanas: corregida 0 (el patrón OMS al nacer).
        val e = edadParaEvaluar(70, eg30)
        assertEquals(0, e.dias)
        assertTrue(e.usaEdadCorregida)
        assertFalse(e.antesDelTermino)
    }

    // ─── evaluarMedicion ─────────────────────────────────────────────────────

    @Test fun conLaEdadCorregidaElPesoMedianoEsNormal() {
        val m = MedicionCrecimiento("2026-05-01", peso = r2(medianaPeso(50)))
        val sin = evaluarMedicion(t, "M", nac, m)!!
        val con = evaluarMedicion(t, "M", nac, m, eg30)!!
        val peSin = sin.resultados.first { it.indicador == PESO_EDAD }
        val peCon = con.resultados.first { it.indicador == PESO_EDAD }
        assertTrue(peSin.z < -2)
        assertTrue(peSin.clasificacion!!.texto != "Normal")
        assertTrue(abs(peCon.z) <= 0.02)
        assertEquals("Normal", peCon.clasificacion!!.texto)
        assertEquals(zIndicador(t, PESO_EDAD, "M", 50.0, m.peso)!!.z, peCon.z)
        assertEquals(120, con.edadDias)
        assertEquals("4 meses", con.edadTexto)
        assertEquals(50, con.edadCorregidaDias)
        assertEquals(1.6, con.edadCorregidaMeses)
        assertEquals("1 mes", con.edadCorregidaTexto)
        assertTrue(con.usaEdadCorregida)
        assertFalse(con.antesDelTermino)
        assertNull(con.edadPostmenstrualTexto)
        assertEquals("4 meses (corregida 1 mes)", textoEdadEvaluada(con))
        assertEquals("4 meses (corr. 1 mes)", textoEdadEvaluada(con, corto = true))
        assertEquals(1.6, edadCurvaMeses(con))
    }

    @Test fun egNulaOATerminoEsLaEvaluacionDeSiempre() {
        val m = MedicionCrecimiento("2026-05-01", 6.1, 61.0, 40.0)
        val base = evaluarMedicion(t, "F", nac, m)!!
        assertEquals(base, evaluarMedicion(t, "F", nac, m, null))
        assertEquals(base, evaluarMedicion(t, "F", nac, m, EdadGestacional(39, 2)))
        assertNull(base.edadCorregidaDias)
        assertNull(base.edadCorregidaMeses)
        assertNull(base.edadCorregidaTexto)
        assertFalse(base.usaEdadCorregida)
        assertFalse(base.antesDelTermino)
        assertEquals("4 meses", textoEdadEvaluada(base))
        assertEquals(base.edadMeses, edadCurvaMeses(base))
        // Puntos y hoja: idénticos sin EG.
        val v = ventanaCurva(PESO_EDAD, 4.0)!!
        assertEquals(puntosPaciente(t, PESO_EDAD, "F", nac, listOf(m), v), puntosPaciente(t, PESO_EDAD, "F", nac, listOf(m), v, null))
        val hoja = { e: EdadGestacional? -> htmlCurvaCrecimiento(t, "F", nac, listOf(m), "2026-05-02", "Ana", null, "BABYMED", null, null, null, e) }
        assertEquals(hoja(null), htmlCurvaCrecimiento(t, "F", nac, listOf(m), "2026-05-02", "Ana", null, "BABYMED", null, null, null))
        assertFalse(hoja(null).contains("corregida"))
    }

    @Test fun antesDelTerminoSinZNiClasificacion() {
        val ev = evaluarMedicion(t, "M", nac, MedicionCrecimiento("2026-01-21", 1.9, 43.0, 30.0), eg30)!!
        assertTrue(ev.antesDelTermino)
        assertEquals(emptyList(), ev.resultados)
        assertEquals("32+6 sem", ev.edadPostmenstrualTexto)
        assertEquals("20 días (32+6 sem posmenstruales)", textoEdadEvaluada(ev))
        assertNull(edadCurvaMeses(ev))
    }

    @Test fun puntosConEdadCorregidaYSinLosDeAntesDelTermino() {
        val meds = listOf(MedicionCrecimiento("2026-01-21", peso = 1.9), MedicionCrecimiento("2026-05-01", peso = 4.9))
        val v = ventanaCurva(PESO_EDAD, 4.0)!!
        assertEquals(listOf(r2(50 / DIAS_POR_MES)), puntosPaciente(t, PESO_EDAD, "M", nac, meds, v, eg30).map { it.x })
        assertEquals(listOf(r2(20 / DIAS_POR_MES), r2(120 / DIAS_POR_MES)), puntosPaciente(t, PESO_EDAD, "M", nac, meds, v).map { it.x })
    }

    @Test fun avisoEnLaPrimeraConLaEdadRealTrasCumplirDosAnios() {
        val fechas = listOf("2026-01-21", "2027-06-01", "2027-12-31", "2028-01-01", "2028-06-01")
        val evs = fechas.map { evaluarMedicion(t, "M", nac, MedicionCrecimiento(it, peso = 10.0), eg30)!! }
        assertEquals(listOf(false, true, true, false, false), evs.map { it.usaEdadCorregida })
        assertTrue(evs[0].antesDelTermino)
        assertEquals("2028-01-01", primeraSinCorreccion(evs, eg30))
        assertNull(primeraSinCorreccion(evs, null))
        assertNull(primeraSinCorreccion(evs, EdadGestacional(38)))
        assertNull(primeraSinCorreccion(evs.take(3), eg30))
        assertTrue(AVISO_FIN_CORRECCION.contains("desde los 2 años se evalúa con la edad real"))
        // La hoja lo marca en la fila y bajo el estado nutricional.
        val html = htmlCurvaCrecimiento(t, "M", nac, fechas.map { MedicionCrecimiento(it, peso = 10.0) }.take(4),
            "2028-01-02", "Mateo", null, "BABYMED", null, null, null, eg30)
        assertTrue(html.contains("desde aquí, edad real"))
        assertTrue(html.contains(AVISO_FIN_CORRECCION))
    }

    @Test fun elTextoDeLaEdadCorregidaEsUnoSolo() {
        assertEquals("1 mes", textoEdadCorregida(nac, "2026-05-01", eg30))
        assertEquals("1 mes", evaluarMedicion(t, "M", nac, MedicionCrecimiento("2026-05-01", peso = 5.0), eg30)!!.edadCorregidaTexto)
        assertNull(textoEdadCorregida(nac, "2028-01-01", eg30))
        assertNull(textoEdadCorregida(nac, "2026-01-21", eg30)) // antes del término
    }

    // ─── Validación, textos y hoja ───────────────────────────────────────────

    @Test fun rangoYTextos() {
        assertTrue(egValida(22)); assertTrue(egValida(42, 6))
        assertFalse(egValida(21)); assertFalse(egValida(43)); assertFalse(egValida(32, 7)); assertFalse(egValida(32, -1)); assertFalse(egValida(null))
        assertEquals(EdadGestacional(32, 4), egDe(32, 4))
        assertNull(egDe(12, null)); assertNull(egDe(null, 3))
        assertEquals("32+4 sem", textoEg(EdadGestacional(32, 4)))
        assertEquals("38 sem", textoEg(EdadGestacional(38)))
        assertEquals("", textoEg(null as EdadGestacional?))
        assertEquals(228, egEnDias(EdadGestacional(32, 4)))
        assertTrue(esPrematuro(EdadGestacional(36, 6))); assertFalse(esPrematuro(EdadGestacional(37))); assertFalse(esPrematuro(null))
        // 30 sem → 70 días hasta el término (cruza de mes y de año sin corrimiento).
        assertEquals("2026-03-12", fechaTermino("2026-01-01", eg30))
        assertEquals("2026-01-31", fechaTermino("2025-12-20", EdadGestacional(34, 0)))
        assertNull(fechaTermino("2026-01-01", EdadGestacional(38)))
        assertEquals("1 mes", textoEdadCorregida(nac, "2026-05-01", eg30))
        assertNull(textoEdadCorregida(nac, "2026-05-01", null))
        assertTrue(Regex("""32\+4 sem.*edad corregida.*INTERGROWTH-21st o Fenton""").containsMatchIn(leyendaPrematuridad(EdadGestacional(32, 4))!!))
        assertNull(leyendaPrematuridad(EdadGestacional(39)))
    }

    @Test fun hojaImprimibleConPrematuridad() {
        val html = htmlCurvaCrecimiento(
            t, "M", nac, listOf(MedicionCrecimiento("2026-01-21", peso = 1.9), MedicionCrecimiento("2026-05-01", peso = 4.9, talla = 55.0)),
            "2026-05-02", "Mateo", null, "BABYMED", null, null, null, eg30,
        )
        assertTrue(html.contains("prematuro de 30 sem"))
        assertTrue(html.contains("4 meses (corregida 1 mes)"))
        assertTrue(html.contains("20 días (32+6 sem EPM)"))
        assertTrue(html.contains("INTERGROWTH-21st o Fenton"))
        assertTrue(html.contains("Edad (corregida hasta los 24 meses)"))
        val aTermino = htmlCurvaCrecimiento(t, "M", nac, listOf(MedicionCrecimiento("2026-05-01", peso = 4.9)), "2026-05-02", "Mateo", null, "BABYMED", null, null, null, null)
        assertFalse(aTermino.contains("corregida hasta"))
    }

    // ─── Lectura y escritura de la ficha (staff) ─────────────────────────────

    @Test fun falloDeLecturaNoEsSinDato() {
        // Leída sin EG: sexo presente, eg null, leida = true (curvas con la edad cronológica, como siempre).
        val sinDato = datosCurvaDeFila(buildJsonObject {
            put("sexo", JsonPrimitive("M")); put("edad_gestacional_semanas", JsonNull); put("edad_gestacional_dias", JsonNull)
        })
        assertEquals(DatosCurvaFicha("M", null, leida = true), sinDato)
        // Leída con EG.
        assertEquals(DatosCurvaFicha("F", EdadGestacional(32, 4), leida = true), datosCurvaDeFila(buildJsonObject {
            put("sexo", JsonPrimitive("F")); put("edad_gestacional_semanas", JsonPrimitive(32)); put("edad_gestacional_dias", JsonPrimitive(4))
        }))
        // Falló (o no vino la fila): leida = false y SIN sexo → no se dibuja ninguna curva (nunca una sin corregir).
        val fallo = datosCurvaDeFila(null)
        assertEquals(DatosCurvaFicha.FALLO, fallo)
        assertFalse(fallo.leida)
        assertNull(fallo.sexo)
        assertTrue(fallo != sinDato)
    }

    @Test fun filaYCuerpoDelEndpoint() {
        assertEquals(EdadGestacional(32, 4), egDeFila(buildJsonObject { put("edad_gestacional_semanas", JsonPrimitive(32)); put("edad_gestacional_dias", JsonPrimitive(4)) }))
        assertNull(egDeFila(buildJsonObject { put("edad_gestacional_semanas", JsonNull); put("edad_gestacional_dias", JsonNull) }))
        assertNull(egDeFila(buildJsonObject { put("edad_gestacional_semanas", JsonPrimitive(12)) }))
        assertNull(egDeFila(buildJsonObject { }))
        // Semanas y días van juntos; sin días = 0; null borra los dos.
        val guardar = cuerpoActualizarFicha("p1", tocarEg = true, eg = EdadGestacional(32))
        assertEquals("32", (guardar["edadGestacionalSemanas"] as JsonPrimitive).content)
        assertEquals("0", (guardar["edadGestacionalDias"] as JsonPrimitive).content)
        val quitar = cuerpoActualizarFicha("p1", tocarEg = true, eg = null)
        assertEquals(JsonNull, quitar["edadGestacionalSemanas"])
        assertEquals(JsonNull, quitar["edadGestacionalDias"])
        // Sin tocarEg no viaja (el servidor no toca la columna).
        val sexo = cuerpoActualizarFicha("p1", sexo = "F", tocarSexo = true)
        assertNull(sexo["edadGestacionalSemanas"])
        assertNotNull(sexo["sexo"])
    }
}
