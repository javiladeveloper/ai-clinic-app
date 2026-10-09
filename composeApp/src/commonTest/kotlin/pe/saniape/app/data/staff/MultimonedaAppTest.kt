package pe.saniape.app.data.staff

import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import pe.saniape.app.ui.proximaHoraEnPunto
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Multipaís, Etapa 2 (app): los formateadores unificados con moneda, la moneda de
 * cada fila por su sede, el teléfono y el documento por país, y la zona de la sede.
 * Regla de oro: con PEN (DALU) todo sale EXACTAMENTE como antes.
 */
class MultimonedaAppTest {

    private fun obj(s: String) = Json.parseToJsonElement(s) as JsonObject

    private val ctxDosPaises by lazy {
        StaffContextoRepo.parsear(obj("""
            {"clinicaId":"c1","rol":"Admin","permisos":{},"multiSede":true,"sedePrincipalId":"s-tacna",
             "moneda":"PEN","zona":"America/Lima","pais":"PE",
             "sedes":[
               {"id":"s-tacna","nombre":"Tacna","es_principal":true,"moneda":"PEN","zona":"America/Lima","pais":"PE"},
               {"id":"s-lapaz","nombre":"La Paz","es_principal":false,"moneda":"BOB","zona":"America/La_Paz","pais":"BO"}]}
        """))
    }

    // ── PEN idéntico a lo de siempre ──

    @Test
    fun pen_queda_igual_que_antes() {
        // soles(): el de siempre.
        assertEquals("S/ 1,205.97", soles(1205.97))
        assertEquals("-S/ 205.97", soles(-205.97))
        assertEquals("S/ 0.00", soles(0.0))
        // Gráficos (los mismos casos que SeriesMensualesTest).
        assertEquals("S/ 28,349", dineroGrafico(28349.0, "PEN"))
        assertEquals("S/ 28.3k", dineroGrafico(28349.0, "PEN", compacto = true))
        assertEquals("S/ 2k", dineroGrafico(2000.0, "PEN", compacto = true))
        assertEquals("S/ 950", dineroGrafico(950.0, "PEN", compacto = true))
        for (n in listOf(0.0, 999.0, 1250.0, 1550.0, -2450.0, 123456.0, 2500.5)) {
            assertEquals(solesGrafico(n), dineroGrafico(n, "PEN"), "detalle $n")
            assertEquals(solesGrafico(n, compacto = true), dineroGrafico(n, "PEN", compacto = true), "compacto $n")
        }
        // Céntimos del cobro dividido: sin separador de miles, como antes.
        assertEquals("S/ 20.00", solesDeCentimos(2000))
        assertEquals("S/ 1234.56", dineroDeCentimos(123456, "PEN"))
        assertEquals("S/ 0.05", dineroDeCentimos(-5, "PEN"))
        // Etiquetas de promociones.
        assertEquals("S/500", dineroCorto(500.0))
        assertEquals("S/49.90", dineroCorto(49.9))
        assertEquals("500", montoCorto(500.0))
        assertEquals("12.50", montoCorto(12.5))
    }

    @Test
    fun cobro_dividido_y_saldo_en_soles_sin_cambios() {
        val f = listOf(FilaPago("Efectivo", "20"), FilaPago("Yape", ""))
        assertEquals("Falta S/ 20.00", estadoReparto(f, 40.0))
        assertEquals("Falta S/ 20.00", estadoReparto(f, 40.0, "PEN"))
        assertEquals("Saldo a favor S/ 205.97 + Yape S/ 284.03",
            resumenPartes(listOf(PartePago("Yape", 284.03), PartePago(METODO_SALDO_A_FAVOR, 205.97))))
        assertEquals("Falta S/ 5.00", Finanzas.compararArqueo(100.0, 95.0).mensaje)
    }

    @Test
    fun campanias_en_soles_sin_contexto() {
        val c = CampaniaGestion(id = "1", nombre = "Promo", tipo = "paquete_fijo", cantidad = 10, precio = 500.0)
        assertEquals("10 sesiones a S/500", etiquetaCampania(c))
        assertEquals("➖ Descuento en soles", TIPOS_CAMPANIA.last().second)
        assertEquals("S/30 menos en la evaluación", TIPOS_CAMPANIA.last().third)
    }

    // ── Otras monedas: solo cambia el símbolo ──

    @Test
    fun bolivianos_cambian_solo_el_simbolo() {
        assertEquals("Bs 28,349", dineroGrafico(28349.0, "BOB"))
        assertEquals("Bs 28.3k", dineroGrafico(28349.0, "BOB", compacto = true))
        assertEquals("Bs 20.00", dineroDeCentimos(2000, "BOB"))
        assertEquals("Bs500", dineroCorto(500.0, "BOB"))
        val f = listOf(FilaPago("Efectivo", "20"), FilaPago("QR", ""))
        assertEquals("Falta Bs 20.00", estadoReparto(f, 40.0, "BOB"))
        assertEquals("Efectivo Bs 10.00 + QR Bs 30.00",
            resumenPartes(listOf(PartePago("Efectivo", 10.0), PartePago("QR", 30.0)), "BOB"))
        assertEquals("Sobra Bs 5.00", Finanzas.compararArqueo(100.0, 105.0, "BOB").mensaje)
        val c = CampaniaGestion(id = "1", nombre = "Promo", tipo = "monto_fijo", valor = 30.0)
        assertEquals("Bs30 de descuento", etiquetaCampania(c, "BOB"))
        assertEquals("➖ Descuento en bolivianos", tiposCampania("BOB").last().second)
        assertEquals("CLP$ 1,500", dineroDeCentimos(150000, "CLP"))
    }

    @Test
    fun pagos_con_saldo_validan_con_su_moneda() {
        val v = partesDelFormulario(100.0, "150", usable = 120.0, metodoResto = "Efectivo", moneda = "BOB")
        assertEquals("Con saldo a favor puedes usar hasta Bs 120.00.", (v as ValidacionPartes.Error).mensaje)
        val vPen = partesDelFormulario(100.0, "150", usable = 120.0, metodoResto = "Efectivo")
        assertEquals("Con saldo a favor puedes usar hasta S/ 120.00.", (vPen as ValidacionPartes.Error).mensaje)
    }

    // ── Moneda de cada fila: su sede → la activa → principal → clínica → PEN ──

    @Test
    fun moneda_de_la_fila_por_su_sede() {
        val ctx = ctxDosPaises
        assertEquals("BOB", monedaDeFila(ctx, sedeActiva = null, sedeFila = "s-lapaz"))
        assertEquals("PEN", monedaDeFila(ctx, sedeActiva = null, sedeFila = "s-tacna"))
        // Fila sin sede → la activa; consolidado → la principal.
        assertEquals("BOB", monedaDeFila(ctx, sedeActiva = "s-lapaz", sedeFila = null))
        assertEquals("PEN", monedaDeFila(ctx, sedeActiva = "", sedeFila = null))
        assertEquals("PEN", monedaDeFila(ctx, sedeActiva = null, sedeFila = ""))
        // La sede de la fila manda sobre la activa.
        assertEquals("PEN", monedaDeFila(ctx, sedeActiva = "s-lapaz", sedeFila = "s-tacna"))
        assertEquals("BO", paisDeFila(ctx, null, "s-lapaz"))
        assertEquals("PE", paisDeFila(ctx, "", null))
        // Sin contexto (tests, login) → PEN / Perú.
        assertEquals("PEN", monedaDeFila(null, "s-lapaz", "s-lapaz"))
        assertEquals("PE", paisDeFila(null, null, null))
        assertEquals("PEN", monedaActiva())
        assertFalse(hayVariasMonedas())
        assertFalse(consolidadoMultimoneda())
    }

    @Test
    fun dalu_un_solo_local_siempre_soles() {
        val dalu = StaffContextoRepo.parsear(obj("""{"clinicaId":"dalu","rol":"Admin","permisos":{}}"""))
        assertEquals("PEN", monedaDeFila(dalu, null, null))
        assertEquals("PEN", monedaDeFila(dalu, null, "cualquier-sede"))
        assertEquals("PE", paisDeFila(dalu, null, null))
    }

    @Test
    fun totales_consolidados_uno_por_moneda() {
        val filas = listOf(100.0 to "PEN", 50.0 to "BOB", 20.5 to "PEN", 0.5 to "BOB")
        val t = totalesPorMoneda(filas.map { it.first to it.second })
        assertEquals(listOf("PEN" to 120.5, "BOB" to 50.5), t)
        assertEquals("S/ 120.50 · Bs 50.50", formatearTotalesPorMoneda(t))
        // Una sola moneda: igual que antes.
        assertEquals(soles(120.5), formatearTotalesPorMoneda(totalesPorMoneda(listOf(100.0 to "PEN", 20.5 to "PEN"))))
    }

    // ── Teléfono y documento por país ──

    @Test
    fun whatsapp_peru_igual_bolivia_con_591() {
        // Perú: la regla de siempre.
        assertEquals("51987654321", numeroWhatsApp("987 654 321", "PE"))
        assertEquals("51987654321", numeroWhatsApp("987654321"))
        assertEquals("51987654321", numeroWhatsApp("51987654321", "PE"))
        assertEquals(enlaceWhatsApp("987654321", "Hola"), enlaceWhatsAppPais("987654321", "PE", "Hola"))
        assertNull(enlaceWhatsAppPais("12345", "PE"))
        assertEquals(enlaceWhatsApp("987654321"), enlaceWhatsAppSede("987654321"))
        // Bolivia: celular de 8 dígitos → 591.
        assertEquals("59171234567", numeroWhatsApp("71234567", "BO"))
        assertEquals("59171234567", numeroWhatsApp("+591 71234567", "BO"))
        assertEquals("https://wa.me/59171234567", enlaceWhatsAppPais("7123 4567", "BO"))
        assertNull(enlaceWhatsAppPais("123", "BO"))
        assertEquals("591", prefijoTelefonico("BO"))
        assertEquals("51", prefijoTelefonico(null))
    }

    @Test
    fun documento_por_pais() {
        assertTrue(usaReniec("PE"))
        assertTrue(usaReniec(null))
        assertFalse(usaReniec("BO"))
        assertEquals("DNI", nombreDocumentoNacional("PE"))
        assertEquals("CI", nombreDocumentoNacional("BO"))
        assertEquals(listOf("PE", "CL", "OTRO"), opcionesDocumento("PE").map { it.first })
        assertEquals("🇵🇪 DNI", opcionesDocumento(null).first().second)
        assertEquals(listOf("BO", "OTRO"), opcionesDocumento("BO").map { it.first })
        assertEquals("🇧🇴 CI", opcionesDocumento("BO").first().second)
        assertEquals("🇧🇴", banderaPais("bo"))
    }

    // ── Ajustes → Sedes ──

    @Test
    fun regional_de_la_sede() {
        assertEquals(RegionalSedeForm("BO", "BOB", "America/La_Paz"), elegirPaisSede("BO"))
        assertEquals(RegionalSedeForm(), elegirPaisSede("XX"))
        assertNull(errorRegionalSede(RegionalSedeForm()))
        assertNull(errorRegionalSede(RegionalSedeForm("BO", "BOB", "America/La_Paz")))
        assertEquals("País no válido", errorRegionalSede(RegionalSedeForm(pais = "Bolivia")))
        assertEquals("Moneda no válida", errorRegionalSede(RegionalSedeForm(moneda = "Bs")))
        assertEquals("Zona horaria no válida", errorRegionalSede(RegionalSedeForm(zona = "Marte/Base")))
        assertTrue("America/La_Paz" in ZONAS_SOPORTADAS)
        assertEquals("PEN", MONEDAS_ELEGIBLES.first().first)
    }

    // ── Zona: la de la sede, nunca la del teléfono ──

    @Test
    fun proxima_hora_en_la_zona_de_la_sede() {
        // 2026-10-08 18:30 UTC = 13:30 en Lima = 14:30 en La Paz.
        val t = Instant.parse("2026-10-08T18:30:00Z")
        assertEquals("14:00", proximaHoraEnPunto(t, TimeZone.of("America/Lima")))
        assertEquals("15:00", proximaHoraEnPunto(t, TimeZone.of("America/La_Paz")))
        // Teléfono en UTC: antes proponía 19:00; con la zona de la sede, 14:00.
        assertEquals("14:00", proximaHoraEnPunto(t))
        // Topes 8–20 intactos.
        assertEquals("08:00", proximaHoraEnPunto(Instant.parse("2026-10-08T08:00:00Z"), TimeZone.of("America/Lima")))
        assertEquals("20:00", proximaHoraEnPunto(Instant.parse("2026-10-09T02:30:00Z"), TimeZone.of("America/Lima")))
        // "Hoy" de la clínica sin contexto: Lima (el último cobro de la noche no salta de día).
        assertEquals("2026-10-08", hoyClinicaIso(Instant.parse("2026-10-09T03:30:00Z")))
        assertEquals("2026-10-08", hoyEnIso("America/La_Paz", Instant.parse("2026-10-09T03:30:00Z")))
    }
}
