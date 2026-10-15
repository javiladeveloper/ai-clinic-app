package pe.saniape.app.data

import kotlin.math.abs
import kotlin.math.floor
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import pe.saniape.app.data.staff.CrecimientoOmsDatos
import pe.saniape.app.data.staff.DIAS_POR_MES
import pe.saniape.app.data.staff.IndicadorCrecimiento
import pe.saniape.app.data.staff.IndicadorCrecimiento.IMC_EDAD
import pe.saniape.app.data.staff.IndicadorCrecimiento.PC_EDAD
import pe.saniape.app.data.staff.IndicadorCrecimiento.PESO_EDAD
import pe.saniape.app.data.staff.IndicadorCrecimiento.PESO_TALLA
import pe.saniape.app.data.staff.IndicadorCrecimiento.TALLA_EDAD
import pe.saniape.app.data.staff.Lms
import pe.saniape.app.data.staff.MapaPediatria
import pe.saniape.app.data.staff.MedicionCrecimiento
import pe.saniape.app.data.staff.NivelCrecimiento
import pe.saniape.app.data.staff.TablaOms
import pe.saniape.app.data.staff.TablasOms
import pe.saniape.app.data.staff.VentanaCurva
import pe.saniape.app.data.staff.clasificarZ
import pe.saniape.app.data.staff.curvaReferencia
import pe.saniape.app.data.staff.edadEnDias
import pe.saniape.app.data.staff.evaluarMedicion
import pe.saniape.app.data.staff.lmsEn
import pe.saniape.app.data.staff.marcasXCurva
import pe.saniape.app.data.staff.pacienteEsPediatria
import pe.saniape.app.data.staff.peorNivel
import pe.saniape.app.data.staff.percentilDeZ
import pe.saniape.app.data.staff.puntosPaciente
import pe.saniape.app.data.staff.sexoValido
import pe.saniape.app.data.staff.textoEdad
import pe.saniape.app.data.staff.textoEdadEntre
import pe.saniape.app.data.staff.textoZ
import pe.saniape.app.data.staff.valorEnZ
import pe.saniape.app.data.staff.ventanaCurva
import pe.saniape.app.data.staff.zAjustado
import pe.saniape.app.data.staff.zIndicador
import pe.saniape.app.data.staff.zLms
import pe.saniape.app.data.staff.AtencionFisio
import pe.saniape.app.data.staff.htmlCurvaCrecimiento

/**
 * 👶 Crecimiento: gemelo de `lib/__tests__/crecimiento-infantil.test.ts` de la web,
 * con los MISMOS valores de referencia de la OMS (casos oficiales de `anthro`,
 * tablas simplificadas y filas del archivo de prueba de `anthroplus`).
 */
class CrecimientoInfantilTest {

    private val t = TablasOms.cargar()
    private fun meses(m: Double) = m * DIAS_POR_MES
    private fun r2(n: Double) = floor(n * 100 + 0.5) / 100
    private fun cerca(esperado: Double, real: Double, decimales: Int, msg: String = "") {
        // toBeCloseTo(x, d) de vitest: |a − b| < 10^−d / 2
        val tol = 0.5 * Math_pow10(-decimales)
        assertTrue(abs(esperado - real) < tol, "$msg esperado $esperado, real $real")
    }
    private fun Math_pow10(e: Int): Double { var r = 1.0; repeat(abs(e)) { r *= 10 }; return if (e < 0) 1 / r else r }

    @Test fun tablasCompletas() {
        val total = TablaOms.entries.sumOf { tb -> t.curva(tb, "M").size + t.curva(tb, "F").size }
        assertEquals(CrecimientoOmsDatos.NODOS, total)
        assertEquals(873, total)
    }

    // ── WHO Child Growth Standards 2006 (0–5 años) ──

    @Test fun anthroNina17kgA1522Dias() {
        assertEquals(0.24, zIndicador(t, PESO_EDAD, "F", 1522.0, 17.0)!!.z)
    }

    @Test fun anthroNino50cmA44Dias() {
        val esperado = r2(((50 / 56.4833) - 1) / 0.03492)
        assertEquals(esperado, zIndicador(t, TALLA_EDAD, "M", 44.0, 50.0)!!.z)
    }

    @Test fun ajusteOmsZMayorQue3() {
        val lms = Lms(0.3259, 3.1957, 0.14578)
        val sd3 = valorEnZ(3.0, lms); val sd2 = valorEnZ(2.0, lms)
        val esperado = r2(3 + (10.37 - sd3) / (sd3 - sd2))
        val r = zIndicador(t, PESO_EDAD, "F", 1.0, 10.37)!!
        assertEquals(esperado, r.z)
        assertTrue(r.z > 5)
        assertNotEquals(r2(zLms(10.37, lms)), r.z)
    }

    /** Tablas simplificadas de la OMS (mediana y ±2 DE, 1 decimal). */
    @Test fun tablasSimplificadasOms() {
        data class Caso(val nombre: String, val ind: IndicadorCrecimiento, val sexo: String, val dias: Double, val m2: Double, val med: Double, val p2: Double)
        val casos = listOf(
            Caso("P/E niño al nacer", PESO_EDAD, "M", 0.0, 2.5, 3.3, 4.4),
            Caso("P/E niña al nacer", PESO_EDAD, "F", 0.0, 2.4, 3.2, 4.2),
            Caso("P/E niño 12 meses", PESO_EDAD, "M", 365.0, 7.7, 9.6, 12.0),
            Caso("P/E niña 12 meses", PESO_EDAD, "F", 365.0, 7.0, 8.9, 11.5),
            Caso("T/E niño al nacer", TALLA_EDAD, "M", 0.0, 46.1, 49.9, 53.7),
            Caso("T/E niña al nacer", TALLA_EDAD, "F", 0.0, 45.4, 49.1, 52.9),
            Caso("T/E niño 5 años", TALLA_EDAD, "M", 1826.0, 100.7, 110.0, 119.2),
            Caso("PC/E niño al nacer", PC_EDAD, "M", 0.0, 31.9, 34.5, 37.0),
            Caso("PC/E niña al nacer", PC_EDAD, "F", 0.0, 31.5, 33.9, 36.2),
        )
        for (c in casos) {
            val tabla = when (c.ind) { PESO_EDAD -> TablaOms.WFA; TALLA_EDAD -> TablaOms.LHFA; else -> TablaOms.HCFA }
            val lms = lmsEn(t.curva(tabla, c.sexo), c.dias)!!
            cerca(c.m2, valorEnZ(-2.0, lms), 1, c.nombre)
            cerca(c.med, valorEnZ(0.0, lms), 1, c.nombre)
            cerca(c.p2, valorEnZ(2.0, lms), 1, c.nombre)
            assertTrue(abs(zIndicador(t, c.ind, c.sexo, c.dias, valorEnZ(0.0, lms))!!.z) <= 0.01, c.nombre)
        }
    }

    @Test fun pesoTallaLongitudInterpolada() {
        val lms = Lms(-0.3521, (10.4475 + 10.466) / 2, (0.08308 + 0.08307) / 2)
        val esperado = r2(zAjustado(10.0, lms))
        cerca(esperado, zIndicador(t, PESO_TALLA, "M", 400.0, 10.0, 80.05)!!.z, 2)
    }

    @Test fun pesoTallaNoAplica() {
        assertNull(zIndicador(t, PESO_TALLA, "M", 10.0, 2.0, 40.0))
        assertNull(zIndicador(t, PESO_TALLA, "M", meses(70.0), 20.0, 115.0))
    }

    // ── WHO Reference 2007 (5–19 años): z de la macro oficial (anthroplus survey_who2007_z.csv) ──

    @Test fun referencia2007MacroOficial() {
        // [sexo, edad en meses, peso, talla, zhfa, zwfa, zbfa]
        val filas = listOf(
            listOf(1.0, 80.65, 18.8, 114.6, -1.07, -1.26, -0.87),
            listOf(1.0, 89.78, 25.3, 129.2, 0.87, 0.35, -0.32),
            listOf(2.0, 99.0, 33.7, 141.5, 2.29, 1.52, 0.54),
            listOf(2.0, 74.27, 29.7, 125.0, 1.69, 2.23, 1.88),
            listOf(1.0, 78.74, 16.6, 115.7, -0.69, -2.13, -2.76),
            listOf(2.0, 97.94, 29.7, 126.3, -0.21, 0.9, 1.32),
        )
        for (f in filas) {
            val sexo = if (f[0] == 1.0) "M" else "F"
            val dias = meses(f[1])
            val peso = f[2]; val talla = f[3]
            val zh = zIndicador(t, TALLA_EDAD, sexo, dias, talla)!!.z
            cerca(f[4], zh, 1, "T/E $f")
            cerca(f[5], zIndicador(t, PESO_EDAD, sexo, dias, peso)!!.z, 1, "P/E $f")
            cerca(f[6], zIndicador(t, IMC_EDAD, sexo, dias, peso / ((talla / 100) * (talla / 100)))!!.z, 1, "IMC/E $f")
            assertTrue(abs(zh - f[4]) <= 0.011, "T/E exacto $f")
        }
    }

    @Test fun adolescente() {
        cerca(-1.13, zIndicador(t, TALLA_EDAD, "M", meses(166.59), 153.7)!!.z, 1)
        cerca(0.94, zIndicador(t, IMC_EDAD, "M", meses(166.59), 50.7 / (1.537 * 1.537))!!.z, 1)
        assertNull(zIndicador(t, PESO_EDAD, "M", meses(166.59), 50.7))
        assertNull(zIndicador(t, TALLA_EDAD, "M", meses(240.0), 170.0))
    }

    @Test fun percentil() {
        cerca(50.0, percentilDeZ(0.0), 5)
        cerca(3.0, percentilDeZ(-1.881), 1)
        cerca(97.0, percentilDeZ(1.881), 1)
        cerca(15.0, percentilDeZ(-1.036), 1)
        cerca(97.72, percentilDeZ(2.0), 2)
    }

    // ── Clasificación (NTS 238-MINSA/DGIESP-2025, Tabla N° 3) ──

    @Test fun clasificacionPesoEdad() {
        assertEquals("Sobrepeso", clasificarZ(PESO_EDAD, 2.01, 10.0)!!.texto)
        assertEquals("Normal", clasificarZ(PESO_EDAD, 2.0, 10.0)!!.texto)
        assertEquals("Normal", clasificarZ(PESO_EDAD, -2.0, 10.0)!!.texto)
        assertEquals("Bajo peso", clasificarZ(PESO_EDAD, -2.01, 10.0)!!.texto)
        assertEquals("Bajo peso", clasificarZ(PESO_EDAD, -3.0, 10.0)!!.texto)
        val sev = clasificarZ(PESO_EDAD, -3.01, 10.0)!!
        assertEquals("Bajo peso severo", sev.texto); assertEquals(NivelCrecimiento.RIESGO, sev.nivel)
        assertNull(clasificarZ(PESO_EDAD, 0.0, 70.0))
    }

    @Test fun clasificacionPesoTallaTallaPc() {
        assertEquals("Obesidad", clasificarZ(PESO_TALLA, 3.2, 20.0)!!.texto)
        assertEquals("Sobrepeso", clasificarZ(PESO_TALLA, 2.5, 20.0)!!.texto)
        assertEquals("Desnutrición aguda (emaciado)", clasificarZ(PESO_TALLA, -2.4, 20.0)!!.texto)
        assertEquals(NivelCrecimiento.RIESGO, clasificarZ(PESO_TALLA, -3.4, 20.0)!!.nivel)
        assertEquals("Talla baja (desnutrición crónica)", clasificarZ(TALLA_EDAD, -2.5, 30.0)!!.texto)
        assertEquals("Talla baja", clasificarZ(TALLA_EDAD, -2.5, 80.0)!!.texto)
        assertEquals(NivelCrecimiento.INFO, clasificarZ(TALLA_EDAD, 2.5, 80.0)!!.nivel)
        assertEquals("Riesgo de microcefalia", clasificarZ(PC_EDAD, -2.1, 3.0)!!.texto)
        assertEquals("Riesgo de macrocefalia", clasificarZ(PC_EDAD, 2.1, 3.0)!!.texto)
        assertEquals("Normal", clasificarZ(PC_EDAD, 1.9, 3.0)!!.texto)
    }

    @Test fun clasificacionImc() {
        assertEquals("Sobrepeso", clasificarZ(IMC_EDAD, 1.5, 80.0)!!.texto)
        assertEquals("Obesidad", clasificarZ(IMC_EDAD, 2.5, 80.0)!!.texto)
        assertEquals("Obesidad severa", clasificarZ(IMC_EDAD, 3.0, 80.0)!!.texto)
        assertEquals("Delgadez", clasificarZ(IMC_EDAD, -2.5, 80.0)!!.texto)
        val ref = clasificarZ(IMC_EDAD, 1.5, 20.0)!!
        assertEquals("Riesgo de sobrepeso", ref.texto); assertEquals("OMS (referencial)", ref.fuente)
    }

    // ── Evaluación y utilidades ──

    @Test fun edadEnDiasSinZona() {
        assertEquals(30, edadEnDias("2026-01-01", "2026-01-31"))
        assertEquals(365, edadEnDias("2024-02-29", "2025-02-28"))
        assertNull(edadEnDias("2026-05-01", "2026-04-01"))
        assertNull(edadEnDias(null, "2026-04-01"))
    }

    @Test fun textosDeEdad() {
        assertEquals("10 días", textoEdad(10))
        assertEquals("3 meses", textoEdad(100))
        assertEquals("16 meses", textoEdad(500))
        assertEquals("2 a 4 m", textoEdad(kotlin.math.ceil(meses(28.0)).toInt()))
        assertEquals("7 años", textoEdad(floor(meses(84.0) + 0.5).toInt()))
        assertEquals("12 meses", textoEdadEntre("2025-10-16", "2026-10-16"))
        assertEquals("11 meses", textoEdadEntre("2025-10-16", "2026-10-15"))
        assertEquals("2 meses", textoEdadEntre("2026-01-10", "2026-03-10"))
        assertEquals("2 a 4 m", textoEdadEntre("2024-06-01", "2026-10-01"))
        assertEquals("15 días", textoEdadEntre("2026-10-01", "2026-10-16"))
        assertEquals("—", textoEdadEntre(null, "2026-10-16"))
    }

    @Test fun medicionDeUnLactante() {
        val ev = evaluarMedicion(t, "M", "2026-01-01", MedicionCrecimiento("2027-01-01", 9.6, 75.7, 46.1))!!
        assertEquals(365, ev.edadDias)
        assertEquals(listOf(PESO_EDAD, TALLA_EDAD, PESO_TALLA, IMC_EDAD, PC_EDAD), ev.resultados.map { it.indicador })
        ev.resultados.forEach { assertTrue(abs(it.z) < 0.3, "${it.indicador} ${it.z}") }
        assertEquals(16.8, ev.imc)
        assertEquals(NivelCrecimiento.OK, peorNivel(ev.resultados.map { it.clasificacion }))
    }

    @Test fun sinNacimientoOMayorDe19() {
        assertNull(evaluarMedicion(t, "F", null, MedicionCrecimiento("2026-01-01", peso = 10.0)))
        assertNull(evaluarMedicion(t, "F", "2000-01-01", MedicionCrecimiento("2026-01-01", peso = 60.0)))
    }

    @Test fun textoZySexo() {
        assertEquals("z −1,25 · P10,6", textoZ(-1.25, 10.6))
        assertEquals("z +2,50 · P>99,9", textoZ(2.5, 99.95))
        assertEquals("z 0,00 · P50", textoZ(0.0, 50.0))
        assertEquals("F", sexoValido("F"))
        assertEquals("M", sexoValido("m"))
        assertNull(sexoValido("x"))
    }

    @Test fun curvaMedianaPesoEdad() {
        val v = ventanaCurva(PESO_EDAD, 10.0)!!
        assertEquals(VentanaCurva(false, 0.0, 24.0), v)
        val curva = curvaReferencia(t, PESO_EDAD, "M", v, 24)
        assertEquals(3.35, curva.first().z0)
        cerca(12.2, curva.last().z0, 1)
        for (i in 1 until curva.size) assertTrue(curva[i].z0 > curva[i - 1].z0)
        curva.forEach { p -> assertTrue(p.zm3 < p.zm2 && p.zm2 < p.z0 && p.z0 < p.z2 && p.z2 < p.z3) }
    }

    @Test fun ventanas() {
        assertEquals(VentanaCurva(false, 60.0, 228.0), ventanaCurva(TALLA_EDAD, 80.0))
        assertEquals(VentanaCurva(true, 65.0, 120.0), ventanaCurva(PESO_TALLA, 30.0))
        assertNull(ventanaCurva(PC_EDAD, 70.0))
        assertEquals(11, curvaReferencia(t, TALLA_EDAD, "F", ventanaCurva(TALLA_EDAD, 80.0)!!, 10).size)
        assertEquals(listOf("0", "3m", "6m", "9m", "12m", "15m", "18m", "21m", "24m"), marcasXCurva(VentanaCurva(false, 0.0, 24.0)).map { it.texto })
        assertEquals(listOf("5a", "6a", "7a"), marcasXCurva(VentanaCurva(false, 60.0, 228.0)).map { it.texto }.take(3))
    }

    @Test fun puntosDelNino() {
        val pts = puntosPaciente(t, PESO_EDAD, "F", "2026-01-01", listOf(
            MedicionCrecimiento("2026-07-01", peso = 7.3), MedicionCrecimiento("2026-01-01", peso = 3.2),
            MedicionCrecimiento("2026-04-01", talla = 60.0),
        ), VentanaCurva(false, 0.0, 24.0))
        assertEquals(listOf(3.2, 7.3), pts.map { it.valor })
        cerca(181 / DIAS_POR_MES, pts[1].x, 2)
    }

    @Test fun quienEsPediatrico() {
        assertTrue(!pacienteEsPediatria(MapaPediatria(), edadAnios = 3))
        assertTrue(pacienteEsPediatria(MapaPediatria(listOf("p"), solo = true)))
        val m = MapaPediatria(listOf("ped"))
        assertTrue(pacienteEsPediatria(m, atenciones = listOf(AtencionFisio(especialidadId = "ped"))))
        assertTrue(pacienteEsPediatria(m, tieneVacunas = true))
        assertTrue(pacienteEsPediatria(m, especialidadesDeQuienMira = listOf("ped")))
        assertTrue(pacienteEsPediatria(m, edadAnios = 17))
        assertTrue(!pacienteEsPediatria(m, atenciones = listOf(AtencionFisio(especialidadId = "med")), edadAnios = 35))
    }

    @Test fun hojaEscapadaConCurvas() {
        val html = htmlCurvaCrecimiento(
            t, "M", "2026-01-01",
            listOf(MedicionCrecimiento("2026-04-01", 6.0, 60.0, 40.0), MedicionCrecimiento("2027-01-01", 9.6, 75.7, 46.1)),
            "2027-01-02", "Mateo <b>Q</b>", null, "BABYMED", null, "javascript:alert(1)", "#zzz",
        )
        assertTrue("CURVA DE CRECIMIENTO" in html)
        assertTrue("Mateo &lt;b&gt;Q&lt;/b&gt;" in html)
        assertTrue("javascript:" !in html)
        assertTrue("#2c3e7a" in html) // color inválido → navy de Sania
        assertTrue(html.split("<svg").size - 1 >= 4) // P/E, T/E, P/T, IMC/E, PC/E
    }
}
