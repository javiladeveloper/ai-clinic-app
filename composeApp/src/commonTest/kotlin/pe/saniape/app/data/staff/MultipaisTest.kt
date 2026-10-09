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
        val casos = listOf(0.0, -0.0, 1.0, 0.1, 0.005, -0.004, 12.345, 99.999, 1234.5, -553.0, -0.5, 1205.97, 999999.99, 1e6, 123456789.5, -98765.4321)
        for (n in casos) assertEquals(soles(n), formatearDinero(n, "PEN"), "monto $n")
        for (n in casos) assertEquals(soles(n), formatearDinero(n, null), "monto $n (sin moneda)")
        assertEquals("S/ 1,234.50", formatearDinero(1234.5, "PEN"))
        assertEquals("-S/ 553.00", formatearDinero(-553.0, "PEN"))
        assertEquals("S/ 0.00", formatearDinero(null, "PEN"))
        assertEquals("S/ 0.00", formatearDinero(Double.NaN, "PEN"))
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
