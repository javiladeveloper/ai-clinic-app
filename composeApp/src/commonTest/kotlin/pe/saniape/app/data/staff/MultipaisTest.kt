package pe.saniape.app.data.staff

import kotlinx.datetime.Instant
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Multipaís, Etapa 1: formatearDinero, hoy por zona y resolución sede → principal → clínica → default (gemelos de la web). */
class MultipaisTest {

    private fun obj(s: String) = Json.parseToJsonElement(s) as JsonObject

    // ── formatearDinero ──

    @Test
    fun pen_es_identico_a_soles() {
        // Literales = lo que daba soles() en origin/master (PagarConSaldo.kt):
        //   c = kotlin.math.round(monto * 100).toLong()  (empate → PAR, como Math.rint)
        //   (if (c < 0) "-" else "") + "S/ " + miles(|c| / 100) + "." + 2 dígitos
        // Ya no se compara contra soles(): hoy soles() ES formatearDinero (sería tautológico).
        val casos = listOf(
            0.0 to "S/ 0.00",
            -0.0 to "S/ 0.00",
            Double.NaN to "S/ 0.00",            // round(NaN).toLong() = 0
            1.0 to "S/ 1.00",
            0.1 to "S/ 0.10",
            0.005 to "S/ 0.00",                 // 0.5 céntimos → empate al par (0)
            -0.004 to "S/ 0.00",                // -0.4 → -0 → sin signo
            -0.5 to "-S/ 0.50",
            2.675 to "S/ 2.68",                 // 2.675 * 100 = 267.5 exacto en double → par 268
            12.345 to "S/ 12.34",               // 1234.5 → empate al par (1234)
            99.999 to "S/ 100.00",
            1234.5 to "S/ 1,234.50",
            -553.0 to "-S/ 553.00",
            1205.97 to "S/ 1,205.97",
            999999.99 to "S/ 999,999.99",
            999999.995 to "S/ 1,000,000.00",    // 99999999.5 → par 100000000
            1e6 to "S/ 1,000,000.00",
            123456789.5 to "S/ 123,456,789.50",
            -98765.4321 to "-S/ 98,765.43",
        )
        for ((n, esperado) in casos) {
            assertEquals(esperado, formatearDinero(n, "PEN"), "monto $n")
            assertEquals(esperado, formatearDinero(n, null), "monto $n (sin moneda)")
            assertEquals(esperado, formatearDinero(n, ""), "monto $n (moneda vacía)")
            assertEquals(esperado, soles(n), "soles($n)")
        }
        assertEquals("S/ 0.00", formatearDinero(null, "PEN"))
    }

    @Test
    fun simbolos_del_contrato() {
        assertEquals("S/", simboloMoneda("PEN"))
        assertEquals("Bs", simboloMoneda("BOB"))
        assertEquals("US$", simboloMoneda("USD"))
        assertEquals("CLP$", simboloMoneda("CLP"))
        assertEquals("COL$", simboloMoneda("COP"))
        assertEquals("MX$", simboloMoneda("MXN"))
        assertEquals("AR$", simboloMoneda("ARS"))
        assertEquals("€", simboloMoneda("EUR"))
        assertEquals("S/", simboloMoneda(null))
        assertEquals("S/", simboloMoneda(""))
    }

    @Test
    fun bob_clp_cop_y_otras_iguales_a_la_web() {
        assertEquals("Bs 1,234.50", formatearDinero(1234.5, "BOB"))
        assertEquals("-Bs 80.00", formatearDinero(-80.0, "BOB"))
        assertEquals("Bs 0.30", formatearDinero(0.1 + 0.2, "BOB"))
        assertEquals("CLP$ 1,500,000", formatearDinero(1_500_000.0, "CLP"))
        assertEquals("CLP$ 1,500", formatearDinero(1499.6, "CLP"))
        assertEquals("COL$ 2,500,000", formatearDinero(2_500_000.0, "COP"))
        assertEquals("US$ 1,234.50", formatearDinero(1234.5, "USD"))
        assertEquals("€ 21,234.50", formatearDinero(21234.5, "EUR"))
        assertEquals("PYG 10.00", formatearDinero(10.0, "PYG"))
        assertEquals("S/ 1,235", formatearDinero(1234.56, "PEN", decimales = 0))
    }

    @Test
    fun numeros_grandes_numeric_14_2() {
        assertEquals("S/ 999,999,999,999.99", formatearDinero(999_999_999_999.99, "PEN"))
        assertEquals("Bs 123,456,789,012.34", formatearDinero(123_456_789_012.34, "BOB"))
        assertEquals("CLP$ 987,654,321,098", formatearDinero(987_654_321_098.0, "CLP"))
    }

    @Test
    fun totales_por_moneda_nunca_mezclados() {
        val t = totalesPorMoneda(listOf(0.1 to "PEN", 0.2 to "PEN", 50.5 to "BOB", 10.0 to null, null to "BOB"))
        assertEquals(listOf("PEN" to 10.3, "BOB" to 50.5), t)
        assertEquals("S/ 12,400.00 · Bs 3,100.00", formatearTotalesPorMoneda(listOf("BOB" to 3100.0, "PEN" to 12400.0)))
        assertEquals(formatearDinero(3100.0, "BOB"), formatearTotalesPorMoneda(listOf("BOB" to 3100.0)))
        assertEquals("S/ 0.00", formatearTotalesPorMoneda(emptyList()))
        assertEquals(listOf("PEN", "BOB", "USD"), ordenarMonedas(listOf("USD", "BOB", "PEN", "bob")))
    }

    // ── Hoy por zona ──

    @Test
    fun las_2330_en_la_paz_y_en_lima() {
        // 23:30 en La Paz del 15 = 03:30 UTC del 16 = 22:30 en Lima del 15.
        val laPaz2330 = Instant.parse("2026-10-16T03:30:00Z")
        assertEquals("2026-10-15", hoyEnIso("America/La_Paz", laPaz2330))
        assertEquals("2026-10-15", hoyEnIso("America/Lima", laPaz2330))
        assertEquals("23:30", ahoraEn("America/La_Paz", laPaz2330).hora)
        assertEquals("22:30", ahoraEn("America/Lima", laPaz2330).hora)
        // 23:30 en Lima del 15 = 00:30 en La Paz del 16.
        val lima2330 = Instant.parse("2026-10-16T04:30:00Z")
        assertEquals("2026-10-15", hoyEnIso("America/Lima", lima2330))
        assertEquals("2026-10-16", hoyEnIso("America/La_Paz", lima2330))
        assertEquals(AhoraEnZona("2026-10-16", "00:30", 30, 5, "America/La_Paz"), ahoraEn("America/La_Paz", lima2330))
        // Sin contexto cargado, ZONA_CLINICA = Lima: hoyClinicaIso no cambia.
        assertEquals("2026-10-15", hoyClinicaIso(lima2330))
        assertEquals(hoyClinicaIso(lima2330), hoyEnIso("Marte/Olimpo", lima2330))
        assertEquals(hoyClinicaIso(lima2330), hoyEnIso(null, lima2330))
        assertFalse(esZonaValida("Marte/Olimpo"))
        assertTrue(esZonaValida("America/La_Paz"))
    }

    // ── Contexto: sede → (principal) → clínica → default ──

    @Test
    fun contexto_con_sedes_en_dos_paises() {
        val ctx = StaffContextoRepo.parsear(obj("""
            {"clinicaId":"c1","rol":"Admin","permisos":{},"multiSede":true,"sedePrincipalId":"s-tacna",
             "moneda":"PEN","zona":"America/Lima","pais":"PE",
             "sedes":[
               {"id":"s-tacna","nombre":"Tacna","es_principal":true,"moneda":"PEN","zona":"America/Lima","pais":"PE"},
               {"id":"s-lapaz","nombre":"La Paz","es_principal":false,"moneda":"BOB","zona":"America/La_Paz","pais":"BO"}]}
        """))
        assertEquals("BOB", ctx.monedaDeSede("s-lapaz"))
        assertEquals("America/La_Paz", ctx.zonaDeSede("s-lapaz"))
        assertEquals("BO", ctx.paisDeSede("s-lapaz"))
        assertEquals("PEN", ctx.monedaDeSede("s-tacna"))
        assertEquals("PEN", ctx.monedaDeSede(""))      // consolidado → principal
        assertEquals("PEN", ctx.monedaDeSede("otra"))  // no está en la lista → clínica
        assertEquals(listOf("PEN", "BOB"), ctx.monedasEnUso)
        assertEquals("Bs 50.00", formatearDinero(50.0, ctx.monedaDeSede("s-lapaz")))
    }

    @Test
    fun principal_en_otro_pais_rige_sin_sede() {
        val ctx = StaffContextoRepo.parsear(obj("""
            {"clinicaId":"c1","permisos":{},"multiSede":true,"sedePrincipalId":"s-lapaz","moneda":"PEN","zona":"America/Lima",
             "sedes":[{"id":"s-lapaz","nombre":"La Paz","es_principal":true,"moneda":"BOB","zona":"America/La_Paz"}]}
        """))
        assertEquals("BOB", ctx.monedaDeSede(null))
        assertEquals("America/La_Paz", ctx.zonaDeSede(null))
    }

    @Test
    fun backend_viejo_y_dalu_quedan_en_soles_y_lima() {
        val viejo = StaffContextoRepo.parsear(obj("""{"clinicaId":"c1","rol":"Admin","permisos":{},
            "sedes":[{"id":"s1","nombre":"Única","es_principal":true}]}"""))
        assertEquals("PEN", viejo.moneda)
        assertEquals("America/Lima", viejo.zona)
        assertEquals("PE", viejo.pais)
        assertEquals("PEN", viejo.monedaDeSede(null))
        assertEquals("PEN", viejo.monedaDeSede("s1"))   // sede sin moneda → la de la clínica
        assertEquals("America/Lima", viejo.zonaDeSede("s1"))
        assertEquals(listOf("PEN"), viejo.monedasEnUso)
        // Una clínica de un local en Bolivia: sin sedes, todo por los de la clínica.
        val bo = StaffContextoRepo.parsear(obj("""{"clinicaId":"c2","permisos":{},"moneda":"BOB","zona":"America/La_Paz","pais":"BO"}"""))
        assertEquals("BOB", bo.monedaDeSede(null))
        assertEquals("America/La_Paz", bo.zonaDeSede(null))
        // Basura del servidor → default, nunca un crash.
        val basura = StaffContextoRepo.parsear(obj("""{"clinicaId":"c3","permisos":{},"moneda":"soles","zona":"x","pais":"Peru"}"""))
        assertEquals("PEN", basura.moneda)
        assertEquals("America/Lima", basura.zona)
        assertEquals("PE", basura.pais)
    }
}
