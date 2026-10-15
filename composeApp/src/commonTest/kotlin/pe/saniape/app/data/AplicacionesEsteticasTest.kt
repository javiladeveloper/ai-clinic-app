package pe.saniape.app.data

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import pe.saniape.app.data.staff.Aplicacion
import pe.saniape.app.data.staff.AplicacionEstetica
import pe.saniape.app.data.staff.AtencionFisio
import pe.saniape.app.data.staff.BorradorAplicacion
import pe.saniape.app.data.staff.CrudoAplicacion
import pe.saniape.app.data.staff.LimitesAplicacion
import pe.saniape.app.data.staff.MapaEstetica
import pe.saniape.app.data.staff.ProductoReciente
import pe.saniape.app.data.staff.ResumenZonaAplicada
import pe.saniape.app.data.staff.TotalProducto
import pe.saniape.app.data.staff.TratamientoParaMapa
import pe.saniape.app.data.staff.UnidadesCobrables
import pe.saniape.app.data.staff.ZONAS_FACIALES
import pe.saniape.app.data.staff.aplicacionEsteticaDeJson
import pe.saniape.app.data.staff.avisoRiesgoVascular
import pe.saniape.app.data.staff.calcularTotales
import pe.saniape.app.data.staff.chipsProducto
import pe.saniape.app.data.staff.citaEsEstetica
import pe.saniape.app.data.staff.copiarParaRepetir
import pe.saniape.app.data.staff.cuerpoAplicacion
import pe.saniape.app.data.staff.diasEntreIso
import pe.saniape.app.data.staff.dosisSugeridas
import pe.saniape.app.data.staff.evaluarDosis
import pe.saniape.app.data.staff.factorToxina
import pe.saniape.app.data.staff.finDeVencimiento
import pe.saniape.app.data.staff.inferirTipoProducto
import pe.saniape.app.data.staff.leerNumeroDosis
import pe.saniape.app.data.staff.normalizarAplicacion
import pe.saniape.app.data.staff.normalizarAplicaciones
import pe.saniape.app.data.staff.ofertaUnidadesCobradas
import pe.saniape.app.data.staff.ordenarAplicaciones
import pe.saniape.app.data.staff.pacienteEsEstetica
import pe.saniape.app.data.staff.productosRecientesDeJson
import pe.saniape.app.data.staff.profundidadPorDefecto
import pe.saniape.app.data.staff.rangoTipico
import pe.saniape.app.data.staff.servicioParaIndicaciones
import pe.saniape.app.data.staff.textoDosis
import pe.saniape.app.data.staff.textoTotales
import pe.saniape.app.data.staff.totalesPorZona
import pe.saniape.app.data.staff.ultimaToxina
import pe.saniape.app.data.staff.unidadPorDefecto
import pe.saniape.app.data.staff.unidadesCobrables
import pe.saniape.app.data.staff.validarAplicacionEstetica
import pe.saniape.app.data.staff.vencidoEn
import pe.saniape.app.data.staff.vistaDeAplicaciones
import pe.saniape.app.data.staff.vistaDeZona
import pe.saniape.app.data.staff.zonaFacial
import pe.saniape.app.data.staff.zonasDeVista
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * 💉 Mapa de aplicación: gemelo de `lib/__tests__/aplicaciones-esteticas.test.ts`
 * (mismos casos y valores esperados): zonas, rangos orientativos con Dysport,
 * normalización, totales, unidades cobrables (solo pre-llenar) e historial.
 */
class AplicacionesEsteticasTest {
    private fun ap(
        zona: String = "glabela", producto: String = "Botox", tipo: String = "toxina", dosis: Double = 20.0, unidad: String = "U",
        puntos: Int? = 5, lote: String? = null, vencimiento: String? = null, notas: String? = null,
    ) = Aplicacion(zona, producto, tipo, dosis, unidad, puntos, "aguja", "intramuscular", lote, vencimiento, notas)

    private fun crudo(zona: String = "glabela", producto: String = "Botox", dosis: String = "20", tipo: String? = null, unidad: String? = null,
                      puntos: String? = null, tecnica: String? = null, profundidad: String? = null, lote: String? = null,
                      vencimiento: String? = null) =
        CrudoAplicacion(zona, producto, tipo, dosis, unidad, puntos, tecnica, profundidad, lote, vencimiento)

    // ── Catálogo de zonas ──

    @Test fun idsUnicosParesYVistas() {
        val ids = ZONAS_FACIALES.map { it.id }
        assertEquals(ids.size, ids.toSet().size)
        for (z in ZONAS_FACIALES.filter { it.lado != null }) {
            val otro = z.id.replace(Regex("_(der|izq)$")) { if (it.groupValues[1] == "der") "_izq" else "_der" }
            assertEquals(z.vista, zonaFacial(otro)?.vista)
        }
        assertEquals("Entrecejo (glabela)", zonaFacial("glabela")?.nombre)
        assertNull(zonaFacial("nada"))
        assertTrue(ZONAS_FACIALES.maxOf { it.id.length } <= 40)
    }

    @Test fun zonasDelBrief() {
        for (id in listOf("frente", "glabela", "patas_gallo_der", "patas_gallo_izq", "cola_ceja_der", "nariz_lineas", "surco_nasogeniano_izq",
            "labio_superior", "labio_inferior", "comisura_der", "codigo_barras", "menton", "mandibula_izq", "malar_der", "ojera_izq", "sien_der", "cuello", "masetero_der")) {
            assertNotNull(zonaFacial(id), id)
        }
    }

    @Test fun perfilesLineaMediaYSuLado() {
        val der = zonasDeVista("lateral_der").map { it.id }
        assertTrue("dorso_nasal" in der)
        assertTrue("angulo_mandibular_der" in der)
        assertFalse("angulo_mandibular_izq" in der)
        assertTrue(zonasDeVista("frontal").all { it.vista == "frontal" })
        assertEquals("lateral_izq", vistaDeZona("angulo_mandibular_izq"))
        assertEquals("lateral_der", vistaDeZona("punta_nasal"))
        assertEquals("frontal", vistaDeZona("glabela"))
    }

    @Test fun riesgoVascularDeLasZonas() {
        // Beleznay 2015: glabela, nariz, frente, surco, sien, periocular y labios.
        val conRiesgo = ZONAS_FACIALES.filter { it.riesgoVascular }.map { it.id }.toSet()
        assertEquals(setOf("frente", "glabela", "sien_der", "sien_izq", "ojera_der", "ojera_izq", "nariz", "surco_nasogeniano_der",
            "surco_nasogeniano_izq", "labio_superior", "labio_inferior", "dorso_nasal", "punta_nasal"), conRiesgo)
    }

    // ── Productos ──

    @Test fun deduceElTipoPorLaMarca() {
        assertEquals("toxina", inferirTipoProducto("Botox 100U"))
        assertEquals("toxina", inferirTipoProducto("TOXINA BOTULINICA FULL FACE"))
        assertEquals("toxina", inferirTipoProducto("Xeomin"))
        assertEquals("acido_hialuronico", inferirTipoProducto("Juvéderm Volbella"))
        assertEquals("acido_hialuronico", inferirTipoProducto("Ácido hialurónico"))
        assertEquals("bioestimulador", inferirTipoProducto("Radiesse"))
        assertEquals("bioestimulador", inferirTipoProducto("Sculptra (ácido poliláctico)"))
        assertEquals("regenerativo", inferirTipoProducto("Rejuran PDRN"))
        assertEquals("regenerativo", inferirTipoProducto("Exosomas"))
        assertNull(inferirTipoProducto("Vitamina C"))
        assertNull(inferirTipoProducto(""))
    }

    @Test fun unidadYProfundidadPorDefecto() {
        assertEquals("U", unidadPorDefecto("toxina"))
        assertEquals("ml", unidadPorDefecto("acido_hialuronico"))
        assertEquals("intramuscular", profundidadPorDefecto("toxina", "glabela"))
        assertEquals("supraperiostica", profundidadPorDefecto("acido_hialuronico", "malar_der"))
        assertEquals("subcutanea", profundidadPorDefecto("acido_hialuronico", "labio_superior"))
        assertNull(profundidadPorDefecto("otro", "frente"))
    }

    @Test fun chipsLosDeLaClinicaPrimero() {
        val rec = listOf(ProductoReciente("BOTOX", "toxina", "U", 3, "C1234", "2027-03"))
        val chips = chipsProducto("toxina", rec)
        assertEquals("BOTOX", chips[0].producto); assertTrue(chips[0].propio)
        assertEquals(1, chips.count { it.producto.lowercase() == "botox" })
        assertTrue(chips.all { it.tipo == "toxina" })
        assertTrue(chipsProducto(null, emptyList()).size > 10)
    }

    // ── Rangos orientativos (nunca bloquean) ──

    @Test fun glabelaConDysportPorDosComaCinco() {
        val b = rangoTipico("glabela", "toxina", "Botox")!!
        assertEquals(15.0, b.min); assertEquals(25.0, b.max); assertEquals("U", b.unidad); assertFalse(b.conversion)
        val d = rangoTipico("glabela", "toxina", "Dysport")!!
        assertEquals(37.5, d.min); assertEquals(62.5, d.max); assertTrue(d.conversion)
        assertEquals("Habitual: 37,5–62,5 U (Dysport ≈ ×2,5, aproximado) · orientativo", d.texto)
        assertTrue(evaluarDosis(ap(dosis = 20.0))!!.texto.contains("orientativo"))
        assertTrue(evaluarDosis(ap(dosis = 90.0, producto = "Dysport"))!!.texto.endsWith("Dysport ≈ ×2,5) · orientativo"))
        assertEquals(1.0, factorToxina("Xeomin"))
        assertEquals("Habitual: 15–25 U · orientativo", rangoTipico("glabela", "toxina")!!.texto)
    }

    @Test fun patasDeGalloYMasetero() {
        rangoTipico("patas_gallo_der", "toxina")!!.let { assertEquals(6.0, it.min); assertEquals(15.0, it.max) }
        rangoTipico("masetero_izq", "toxina")!!.let { assertEquals(20.0, it.min); assertEquals(40.0, it.max) }
    }

    @Test fun rellenoEnMlYSinReferencia() {
        val l = rangoTipico("labio_superior", "acido_hialuronico")!!
        assertEquals(0.3, l.min); assertEquals(1.0, l.max); assertEquals("ml", l.unidad)
        assertEquals("Habitual: 0,3–1 ml · orientativo", l.texto)
        assertNull(rangoTipico("masetero_der", "acido_hialuronico"))
        assertNull(rangoTipico("glabela", "bioestimulador"))
    }

    @Test fun evaluarDosisAltoBajoOk() {
        assertEquals("ok", evaluarDosis(ap(dosis = 20.0))!!.nivel)
        assertEquals("alto", evaluarDosis(ap(dosis = 40.0))!!.nivel)
        assertEquals("bajo", evaluarDosis(ap(dosis = 5.0))!!.nivel)
        assertEquals("ok", evaluarDosis(ap(dosis = 50.0, producto = "Dysport"))!!.nivel)
        assertNull(evaluarDosis(ap(zona = "labio_superior", tipo = "acido_hialuronico", unidad = "jeringa", dosis = 1.0)))
    }

    @Test fun avisoVascularConRellenoNoConToxina() {
        assertTrue(avisoRiesgoVascular("nariz", "acido_hialuronico")!!.contains("hialuronidasa"))
        assertNotNull(avisoRiesgoVascular("glabela", "acido_hialuronico"))
        assertNull(avisoRiesgoVascular("glabela", "toxina"))
        for (z in listOf("labio_superior", "labio_inferior", "sien_der", "ojera_izq", "surco_nasogeniano_der", "dorso_nasal")) {
            assertNotNull(avisoRiesgoVascular(z, "acido_hialuronico"), z)
        }
        assertNull(avisoRiesgoVascular("malar_der", "acido_hialuronico"))
        assertNull(avisoRiesgoVascular("menton", "acido_hialuronico"))
    }

    @Test fun dosisSugeridasDentroDelRango() {
        val g = dosisSugeridas("glabela", "toxina")
        assertEquals(15.0, g.first()); assertEquals(25.0, g.last()); assertTrue(g.size <= 6)
        val m = dosisSugeridas("malar_der", "acido_hialuronico")
        assertEquals(0.5, m.first()); assertEquals(1.5, m.last())
        assertEquals(listOf(0.1, 0.2, 0.5, 1.0), dosisSugeridas("cuello", "acido_hialuronico"))
        // Muchos valores: los extremos y 4 del medio (≤ 6).
        val c = dosisSugeridas("cuello", "toxina")
        assertEquals(20.0, c.first()); assertEquals(50.0, c.last()); assertTrue(c.size <= 6)
    }

    // ── Normalizar y validar ──

    @Test fun comaDecimalYDeduceTipoYUnidad() {
        val r = normalizarAplicacion(crudo(zona = "labio_superior", producto = "  Juvéderm  ", dosis = "0,5", puntos = "4", tecnica = "canula"))
        assertEquals(emptyList(), r.errores)
        assertEquals(Aplicacion("labio_superior", "Juvéderm", "acido_hialuronico", 0.5, "ml", 4, "canula", null, null, null, null), r.aplicacion)
        assertEquals(emptyList(), normalizarAplicacion(crudo(tipo = "toxina", unidad = "U")).errores)
    }

    @Test fun rechazos() {
        fun e(c: CrudoAplicacion) = normalizarAplicacion(c).errores.joinToString(" ")
        assertTrue(e(crudo(zona = "oreja")).contains("zona"))
        assertTrue(e(crudo(dosis = "0")).contains("mayor que 0"))
        assertTrue(e(crudo(dosis = "veinte")).contains("mayor que 0"))
        assertTrue(e(crudo(dosis = "500")).contains("demasiado alta"))
        assertTrue(e(crudo(unidad = "ml")).contains("no se mide en ml"))
        assertTrue(e(crudo(unidad = "cc")).contains("unidad"))
        assertTrue(e(crudo(tipo = "crema")).contains("tipo"))
        assertTrue(e(crudo(puntos = "2.5")).contains("puntos"))
        assertTrue(e(crudo(vencimiento = "03/2027")).contains("vencimiento"))
        assertTrue(e(crudo(vencimiento = "2027-13")).contains("vencimiento"))
        assertTrue(e(crudo(lote = "x".repeat(41))).contains("lote"))
        assertTrue(e(crudo(tecnica = "pistola")).contains("técnica"))
        assertTrue(e(crudo(profundidad = "profunda")).contains("profundidad"))
        assertEquals("", e(crudo(vencimiento = "2027-03")))
        assertEquals("", e(crudo(vencimiento = "2027-03-15")))
    }

    @Test fun listaAlMenosUnaMaximo60YNumerada() {
        assertTrue(normalizarAplicaciones(emptyList()).second[0].contains("al menos una zona"))
        assertTrue(normalizarAplicaciones(null).second[0].contains("al menos una zona"))
        assertTrue(normalizarAplicaciones(List(61) { crudo() }).second[0].contains("máx. 60"))
        val r = normalizarAplicaciones(listOf(crudo(), crudo(producto = "", dosis = "1")))
        assertEquals(emptyList(), r.first)
        assertTrue(r.second[0].startsWith("Aplicación 2: falta el producto"))
        assertEquals(60, LimitesAplicacion.APLICACIONES)
        assertEquals(400.0, LimitesAplicacion.DOSIS["U"])
    }

    @Test fun validarPacienteFechaObservaciones() {
        assertEquals(emptyList(), validarAplicacionEstetica(BorradorAplicacion("p", listOf(ap()))))
        assertTrue("Falta el paciente." in validarAplicacionEstetica(BorradorAplicacion("", listOf(ap()))))
        assertTrue("Fecha inválida." in validarAplicacionEstetica(BorradorAplicacion("p", listOf(ap()), fecha = "15/10/2026")))
        assertTrue(validarAplicacionEstetica(BorradorAplicacion("p", listOf(ap()), observaciones = "x".repeat(2001)))[0].contains("observaciones"))
    }

    @Test fun vencimientoFinDeMesYVencido() {
        assertEquals("2027-02-28", finDeVencimiento("2027-02"))
        assertEquals("2028-02-29", finDeVencimiento("2028-02"))
        assertEquals("2027-02-10", finDeVencimiento("2027-02-10"))
        assertNull(finDeVencimiento("mal"))
        assertTrue(vencidoEn("2026-09", "2026-10-15"))
        assertFalse(vencidoEn("2026-10", "2026-10-15"))
        assertFalse(vencidoEn(null, "2026-10-15"))
    }

    @Test fun leerNumeroYTextoDosis() {
        assertEquals(1.5, leerNumeroDosis("1,5"))
        assertTrue(leerNumeroDosis("").isNaN())
        assertEquals("0,5 ml", textoDosis(0.5, "ml"))
        assertEquals("2 viales", textoDosis(2.0, "vial"))
        assertEquals("1 jeringa", textoDosis(1.0, "jeringa"))
    }

    // ── Totales ──

    private val lista = listOf(
        ap(zona = "glabela", dosis = 20.0, puntos = 5, lote = "C1"),
        ap(zona = "frente", dosis = 10.0, puntos = 5, producto = "BOTOX", lote = "C1"),
        ap(zona = "patas_gallo_der", dosis = 12.0, puntos = 3),
        ap(zona = "labio_superior", producto = "Juvéderm", tipo = "acido_hialuronico", dosis = 0.5, unidad = "ml", puntos = null),
        ap(zona = "labio_inferior", producto = "Juvéderm", tipo = "acido_hialuronico", dosis = 0.6, unidad = "ml", puntos = 2),
    )

    @Test fun totalesPorProductoUnidadPuntosYZonas() {
        val t = calcularTotales(lista)
        assertEquals(mapOf("U" to 42.0, "ml" to 1.1), t.porUnidad)
        assertEquals(15, t.puntos); assertEquals(5, t.zonas); assertEquals(5, t.aplicaciones)
        assertEquals(
            listOf(
                TotalProducto("Botox", "toxina", "U", 42.0, 13, 3, listOf("C1")),
                TotalProducto("Juvéderm", "acido_hialuronico", "ml", 1.1, 2, 2, emptyList()),
            ),
            t.porProducto,
        )
        assertEquals("42 U · 1,1 ml", textoTotales(t))
        assertEquals("", textoTotales(null as Map<String, Double>?))
    }

    @Test fun porZonaUnidadesDistintasNoSeSuman() {
        val z = totalesPorZona(lista + ap(zona = "labio_superior", dosis = 4.0))
        assertEquals("4 U · 0,5 ml", z.getValue("labio_superior").texto)
        assertEquals(listOf("acido_hialuronico", "toxina"), z.getValue("labio_superior").tipos.sorted())
        assertEquals(ResumenZonaAplicada("20 U", listOf("toxina"), 1), z["glabela"])
    }

    @Test fun vistaSegunLasZonas() {
        assertEquals("frontal", vistaDeAplicaciones(listOf("glabela")))
        assertEquals("lateral", vistaDeAplicaciones(listOf("dorso_nasal")))
        assertEquals("ambas", vistaDeAplicaciones(listOf("glabela", "angulo_mandibular_der")))
    }

    @Test fun ordenarMasRecientePrimero() {
        val o = ordenarAplicaciones(listOf(
            AplicacionEstetica("1", "p", fecha = "2026-01-01"),
            AplicacionEstetica("2", "p", fecha = "2026-05-01", createdAt = "b"),
            AplicacionEstetica("3", "p", fecha = "2026-05-01", createdAt = "c"),
        ))
        assertEquals(listOf("3", "2", "1"), o.map { it.id })
    }

    // ── Cobro por unidades: solo pre-llenar (el dinero no se toca) ──

    private val tCobro = calcularTotales(listOf(
        ap(dosis = 20.0), ap(zona = "frente", dosis = 12.5),
        ap(zona = "labio_superior", producto = "Juvéderm", tipo = "acido_hialuronico", dosis = 1.2, unidad = "ml"),
    ))

    @Test fun unidadesDeToxinaEnterasHaciaArriba() {
        assertEquals(UnidadesCobrables(33, "32,5 → 33 U", true, emptyList(), false), unidadesCobrables(tCobro, "unidades"))
        assertEquals(UnidadesCobrables(50, "50 U", false, emptyList(), false), unidadesCobrables(calcularTotales(listOf(ap(dosis = 50.0))), "U"))
        assertEquals(33, unidadesCobrables(tCobro, null)!!.cantidad)
    }

    @Test fun jeringasDeUnMl() {
        assertEquals(UnidadesCobrables(2, "≈ 2 jeringas de 1 ml (1,2 ml)", true, emptyList(), false), unidadesCobrables(tCobro, "JERINGA"))
        val conJeringas = calcularTotales(listOf(ap(zona = "labio_superior", producto = "Juvéderm", tipo = "acido_hialuronico", dosis = 1.0, unidad = "jeringa")))
        assertEquals(UnidadesCobrables(1, "1 jeringas", false, emptyList(), false), unidadesCobrables(conJeringas, "jeringas"))
    }

    @Test fun botoxMasDysportNoEquivalen() {
        val mezcla = calcularTotales(listOf(ap(dosis = 20.0), ap(zona = "masetero_der", producto = "Dysport", dosis = 60.0)))
        assertEquals(
            UnidadesCobrables(80, "≈ 80 U (Botox 20 U + Dysport 60 U)", true, listOf("Botox 20 U", "Dysport 60 U"), true),
            unidadesCobrables(mezcla, "unidades"),
        )
        val equiv = calcularTotales(listOf(ap(dosis = 20.0), ap(zona = "frente", producto = "Xeomin", dosis = 10.0)))
        val u = unidadesCobrables(equiv, "U")!!
        assertEquals(30, u.cantidad); assertFalse(u.aproximado); assertFalse(u.mezclaMarcas)
        assertEquals(listOf("Botox 20 U", "Xeomin 10 U"), u.detalle)
    }

    @Test fun mlVialesYUnidadesQueElMapaNoConoce() {
        assertEquals(UnidadesCobrables(2, "1,2 → 2 ml", true, emptyList(), false), unidadesCobrables(tCobro, "ml"))
        assertNull(unidadesCobrables(tCobro, "vial"))
        assertNull(unidadesCobrables(tCobro, "folículos"))
        assertNull(unidadesCobrables(calcularTotales(listOf(ap(zona = "labio_superior", producto = "J", tipo = "acido_hialuronico", dosis = 1.0, unidad = "ml"))), "U"))
        assertNull(unidadesCobrables(null, "U"))
    }

    @Test fun ofertaSoloConTratamientoPorUnidadesYCantidadDistinta() {
        val aps = listOf(ap(dosis = 20.0))
        assertNull(ofertaUnidadesCobradas(aps, null))
        assertNull(ofertaUnidadesCobradas(aps, TratamientoParaMapa("t", "Botox", "Activo", "Paquete", "U", null)))
        assertNull(ofertaUnidadesCobradas(aps, TratamientoParaMapa("t", "Botox", "Activo", "Unidades", "U", 20)))
        assertEquals(20, ofertaUnidadesCobradas(aps, TratamientoParaMapa("t", "Botox", "Activo", "Unidades", "U", 10))!!.cantidad)
    }

    // ── Historial ──

    private fun fila(fecha: String, tipo: String) = AplicacionEstetica("x$fecha", "p", fecha = fecha,
        aplicaciones = listOf(ap(tipo = tipo, unidad = if (tipo == "toxina") "U" else "ml")))

    @Test fun ultimaToxina() {
        assertEquals(125, diasEntreIso("2026-06-12", "2026-10-15"))
        val u = ultimaToxina(listOf(fila("2026-06-12", "toxina"), fila("2026-09-01", "acido_hialuronico")), "2026-10-15")!!
        assertEquals("2026-06-12", u.fecha); assertEquals(17, u.semanas); assertEquals("habitual", u.nivel)
        assertTrue(u.texto.contains("hace 4 meses · momento habitual"))
        ultimaToxina(listOf(fila("2026-09-01", "toxina")), "2026-10-15")!!.let {
            assertEquals(6, it.semanas); assertEquals("pronto", it.nivel)
            assertTrue(it.texto.contains("hace 6 semanas · menos de 3 meses"))
        }
        assertEquals("Última toxina: hoy", ultimaToxina(listOf(fila("2026-10-15", "toxina")), "2026-10-15")!!.texto)
        assertNull(ultimaToxina(listOf(fila("2025-01-01", "toxina")), "2026-10-15")!!.nivel)
        assertNull(ultimaToxina(listOf(fila("2026-09-01", "acido_hialuronico")), "2026-10-15"))
        // Una futura (cargada por error) no cuenta.
        assertNull(ultimaToxina(listOf(fila("2026-12-01", "toxina")), "2026-10-15"))
    }

    @Test fun repetirSinLoteVencimientoNiNotas() {
        val r = copiarParaRepetir(listOf(ap(lote = "C1", vencimiento = "2027-01", notas = "leve hematoma")))
        assertEquals("glabela", r[0].zona); assertEquals(20.0, r[0].dosis); assertEquals(5, r[0].puntos)
        assertNull(r[0].lote); assertNull(r[0].vencimiento); assertNull(r[0].notas)
    }

    @Test fun servicioParaIndicaciones() {
        assertEquals("Toxina botulínica", servicioParaIndicaciones(listOf(ap())))
        assertEquals("Ácido hialurónico", servicioParaIndicaciones(listOf(ap(tipo = "acido_hialuronico"), ap(tipo = "acido_hialuronico"), ap())))
        assertEquals("Toxina botulínica", servicioParaIndicaciones(listOf(ap(tipo = "acido_hialuronico"), ap())))
        assertNull(servicioParaIndicaciones(listOf(ap(tipo = "otro"))))
    }

    // ── JSON ──

    @Test fun productosRecientesYFila() {
        val p = productosRecientesDeJson(Json.parseToJsonElement("""{"productos":[
            {"producto":"Botox","tipo_producto":"toxina","unidad":"U","usos":3,"ultimo_lote":"C2","ultimo_vencimiento":"2027-05"},
            {"producto":"X","tipo_producto":"crema"}]}""").jsonObject)
        assertEquals(listOf(ProductoReciente("Botox", "toxina", "U", 3, "C2", "2027-05")), p)
        val f = aplicacionEsteticaDeJson(Json.parseToJsonElement("""{"id":"a","paciente_id":"p","fecha":"2026-10-15","vista":"frontal",
            "aplicaciones":[{"zona":"glabela","producto":"Botox","tipo_producto":"toxina","dosis":20,"unidad":"U","puntos":5,"lote":"C7254AC4","vencimiento":"2027-03"}],
            "totales":{"por_unidad":{"U":999}}}""").jsonObject)!!
        assertEquals(20.0, f.aplicaciones.single().dosis)
        // Los totales se recalculan desde las aplicaciones (no del jsonb).
        assertEquals("20 U", textoTotales(f.totales))
    }

    @Test fun cuerpoDelPostYPatch() {
        val c = cuerpoAplicacion(BorradorAplicacion("p", listOf(ap()), citaId = "c", fecha = "2026-10-15"))
        assertEquals("p", c["pacienteId"]!!.jsonPrimitive.content)
        assertEquals("toxina", c["aplicaciones"]!!.jsonArray[0].jsonObject["tipo_producto"]!!.jsonPrimitive.content)
        assertFalse("terapeutaId" in c)
        assertFalse("totales" in c)
        val p = cuerpoAplicacion(BorradorAplicacion("p", listOf(ap()), terapeutaId = "t"), id = "a1")
        assertEquals("a1", p["id"]!!.jsonPrimitive.content)
        assertFalse("pacienteId" in p)
        assertEquals("t", p["terapeutaId"]!!.jsonPrimitive.content)
        assertTrue(p["citaId"] is JsonNull)
    }

    // ── ¿Es estética? (solo el rubro guardado: aquí llegan los ids del contexto) ──

    @Test fun glamCenterPorCitaTratamientoProfesional() {
        val m = MapaEstetica(listOf("e"))
        assertTrue(citaEsEstetica(m, especialidadId = "e"))
        assertFalse(citaEsEstetica(m, especialidadId = "c"))
        assertTrue(citaEsEstetica(m, especialidadServicioId = "e"))
        assertFalse(citaEsEstetica(m))
        assertFalse(pacienteEsEstetica(m, listOf(AtencionFisio(especialidadId = "c"))))
        assertTrue(pacienteEsEstetica(m, listOf(AtencionFisio(especialidadId = "c")), tieneAplicaciones = true))
        assertTrue(pacienteEsEstetica(m, especialidadesDeQuienMira = listOf("e")))
        assertTrue(pacienteEsEstetica(MapaEstetica(listOf("e"), solo = true)))
        assertFalse(pacienteEsEstetica(MapaEstetica(), tieneAplicaciones = true))
    }
}
