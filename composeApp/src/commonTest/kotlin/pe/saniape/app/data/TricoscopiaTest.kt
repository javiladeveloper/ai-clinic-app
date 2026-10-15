package pe.saniape.app.data

import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import pe.saniape.app.data.staff.AtencionFisio
import pe.saniape.app.data.staff.BorradorTricoscopia
import pe.saniape.app.data.staff.DonanteTrico
import pe.saniape.app.data.staff.ESCALAS
import pe.saniape.app.data.staff.EntradaTrasplante
import pe.saniape.app.data.staff.EvaluacionTricoscopica
import pe.saniape.app.data.staff.MapaCapilar
import pe.saniape.app.data.staff.MetricaZona
import pe.saniape.app.data.staff.ParCrudo
import pe.saniape.app.data.staff.ReceptorTrico
import pe.saniape.app.data.staff.SerieZonaSvg
import pe.saniape.app.data.staff.StaffContextoRepo
import pe.saniape.app.data.staff.TricoscopiaRepo
import pe.saniape.app.data.staff.ZonaCapilar
import pe.saniape.app.data.staff.ZonaCruda
import pe.saniape.app.data.staff.ZonaMedida
import pe.saniape.app.data.staff.PuntoSerieHc
import pe.saniape.app.data.staff.aNumeroTrico
import pe.saniape.app.data.staff.cambioMetrica
import pe.saniape.app.data.staff.citaEsCapilar
import pe.saniape.app.data.staff.comparacionConDonante
import pe.saniape.app.data.staff.copiaDeAnterior
import pe.saniape.app.data.staff.cuerpoTricoscopia
import pe.saniape.app.data.staff.cumpleCriteriosAndrogeneticos
import pe.saniape.app.data.staff.IndiciosAndrogeneticos
import pe.saniape.app.data.staff.escalaSugerida
import pe.saniape.app.data.staff.estimarTrasplante
import pe.saniape.app.data.staff.evaluacionDeJson
import pe.saniape.app.data.staff.evolucionZonas
import pe.saniape.app.data.staff.flechaCambio
import pe.saniape.app.data.staff.formasDeGrado
import pe.saniape.app.data.staff.fraccionesTiempo
import pe.saniape.app.data.staff.gradoValido
import pe.saniape.app.data.staff.htmlInformeTricoscopia
import pe.saniape.app.data.staff.indiciosAndrogeneticos
import pe.saniape.app.data.staff.leerDonante
import pe.saniape.app.data.staff.leerReceptor
import pe.saniape.app.data.staff.leerZonas
import pe.saniape.app.data.staff.mensajeErrorTricoscopia
import pe.saniape.app.data.staff.metricasConEvolucion
import pe.saniape.app.data.staff.milesTrico
import pe.saniape.app.data.staff.ordenarEvaluaciones
import pe.saniape.app.data.staff.pacienteEsCapilar
import pe.saniape.app.data.staff.paresDelFormulario
import pe.saniape.app.data.staff.pelosPorUfCalculado
import pe.saniape.app.data.staff.pelosPorUfDe
import pe.saniape.app.data.staff.pelosPorUfParaEditar
import pe.saniape.app.data.staff.referenciaCasilla
import pe.saniape.app.data.staff.serieZona
import pe.saniape.app.data.staff.svgEvolucionZonas
import pe.saniape.app.data.staff.svgSilueta
import pe.saniape.app.data.staff.textoCambio
import pe.saniape.app.data.staff.textoClasificacion
import pe.saniape.app.data.staff.textoMedicion
import pe.saniape.app.data.staff.trasplanteDeEvaluacion
import pe.saniape.app.data.staff.validarTricoscopia
import pe.saniape.app.data.staff.zonasConEvolucion

/**
 * 💇 Tricoscopía: gemelo de `lib/__tests__/tricoscopia.test.ts` de la web
 * (mismos valores esperados) + el parseo del API, el cuerpo del pedido, el
 * informe y el eje de tiempo.
 */
class TricoscopiaTest {

    private fun cruda(zona: String, vararg v: Pair<MetricaZona, String?>, signos: List<String> = emptyList()) =
        ZonaCruda(zona, v.toMap(), signos)

    private fun ev(id: String, fecha: String, zonas: List<ZonaMedida>, escala: String? = null, grado: String? = null,
                   donante: DonanteTrico? = null, receptor: ReceptorTrico? = null, observaciones: String? = null) =
        EvaluacionTricoscopica(id = id, pacienteId = "p", fecha = fecha, escala = escala, grado = grado, zonas = zonas,
            donante = donante, receptor = receptor, observaciones = observaciones)

    private fun obj(s: String): JsonObject = Json.parseToJsonElement(s).jsonObject

    // ── Catálogos ──

    @Test fun catalogos() {
        assertEquals(listOf("I", "II", "IIa", "III", "IIIa", "III vértex", "IV", "IVa", "V", "Va", "VI", "VII"),
            ESCALAS.first { it.id == "hamilton_norwood" }.grados)
        assertEquals(listOf("I", "II", "III"), ESCALAS.first { it.id == "ludwig" }.grados)
        assertEquals(listOf("1", "2", "3", "4", "5"), ESCALAS.first { it.id == "sinclair" }.grados)
        assertEquals(listOf("frontal", "media", "vertex", "occipital", "temporal"), ZonaCapilar.entries.map { it.id })
    }

    @Test fun cadaGradoTieneSilueta() {
        for (e in ESCALAS) for (g in e.grados) assertNotNull(formasDeGrado(e.id, g), "${e.id} $g")
        assertNull(formasDeGrado("otro", "Alopecia areata"))
        assertNull(formasDeGrado("hamilton_norwood", "VIII"))
        // El SVG del informe: "?" sin dibujo; con dibujo, las zonas recortadas a la cabeza.
        assertTrue(svgSilueta("otro", "x", 64, "a").contains(">?</text>"))
        assertTrue(svgSilueta("ludwig", "II", 64, "a").contains("clip-path=\"url(#a)\""))
    }

    @Test fun textoDeLaClasificacion() {
        assertEquals("Hamilton-Norwood III vértex", textoClasificacion("hamilton_norwood", "III vértex"))
        assertEquals("Ludwig II", textoClasificacion("ludwig", "II"))
        assertEquals("Patrón difuso (moderado)", textoClasificacion("difuso", "Moderado"))
        assertEquals("Alopecia areata", textoClasificacion("otro", "Alopecia areata"))
        assertNull(textoClasificacion(null, null))
    }

    // ── Números y zonas ──

    @Test fun numerosConComaYBasura() {
        assertEquals(12.5, aNumeroTrico("12,5"))
        assertEquals(80.0, aNumeroTrico(" 80 "))
        assertNull(aNumeroTrico(""))
        assertNull(aNumeroTrico(null))
        assertTrue(aNumeroTrico("abc")!!.isNaN())
    }

    @Test fun zonasOrdenAnatomicoSinVaciasYSignosLimpios() {
        val (zonas, errores) = leerZonas(listOf(
            cruda("occipital", MetricaZona.UF_CM2 to "80", MetricaZona.DENSIDAD to "200"),
            cruda("frontal", MetricaZona.DENSIDAD to "120,5", MetricaZona.MINIATURIZACION to "25",
                signos = listOf("peripilar", "peripilar", "inventado")),
            cruda("media"),
        ))
        assertEquals(emptyList(), errores)
        assertEquals(listOf(ZonaCapilar.FRONTAL, ZonaCapilar.OCCIPITAL), zonas.map { it.zona })
        assertEquals(120.5, zonas[0].densidad)
        assertEquals(25.0, zonas[0].miniaturizacionPct)
        assertEquals(listOf("peripilar"), zonas[0].signos)
        // Pelos por UF NO medido: no se guarda; al mostrarlo se calcula (200 ÷ 80) y se marca ≈.
        assertNull(zonas[1].pelosPorUf)
        assertEquals(2.5, pelosPorUfDe(zonas[1]).valor)
        assertTrue(pelosPorUfDe(zonas[1]).calculado)
        assertEquals("≈2.5", textoMedicion(zonas[1], MetricaZona.PELOS_POR_UF))
        val medido = pelosPorUfDe(200.0, 80.0, 2.3)
        assertEquals(2.3, medido.valor)
        assertFalse(medido.calculado)
        assertEquals("—", textoMedicion(zonas[0], MetricaZona.UF_CM2))
        assertEquals("120.5", textoMedicion(zonas[0], MetricaZona.DENSIDAD))
    }

    @Test fun pelosPorUfCalculadoSoloEnRango() {
        assertEquals(2.2, pelosPorUfCalculado(220.0, 100.0))
        assertNull(pelosPorUfCalculado(200.0, 10.0)) // 20 pelos/UF: imposible
        assertNull(pelosPorUfCalculado(200.0, 0.0))
        assertNull(pelosPorUfCalculado(null, 80.0))
    }

    @Test fun rangosRepetidasYDesconocidas() {
        assertTrue(leerZonas(listOf(cruda("frontal", MetricaZona.DENSIDAD to "700"))).errores[0].contains("fuera de rango (0–600"))
        assertTrue(leerZonas(listOf(cruda("frontal", MetricaZona.DENSIDAD to "1"), cruda("frontal", MetricaZona.DENSIDAD to "2")))
            .errores[0].contains("repetida"))
        assertTrue(leerZonas(listOf(cruda("nuca", MetricaZona.DENSIDAD to "1"))).errores[0].contains("desconocida"))
        assertTrue(leerZonas(listOf(cruda("frontal", MetricaZona.MINIATURIZACION to "x"))).errores[0].contains("no es un número"))
        // Cada UF tiene al menos un pelo: densidad < UF/cm² = columnas cruzadas.
        assertTrue(leerZonas(listOf(cruda("frontal", MetricaZona.DENSIDAD to "60", MetricaZona.UF_CM2 to "80")))
            .errores[0].contains("no puede ser menor que las UF"))
        assertEquals(emptyList(), leerZonas(listOf(cruda("frontal", MetricaZona.DENSIDAD to "80", MetricaZona.UF_CM2 to "80"))).errores)
        assertEquals("Frontal · Densidad: fuera de rango (0–600 pelos/cm²).",
            leerZonas(listOf(cruda("frontal", MetricaZona.DENSIDAD to "700"))).errores[0])
    }

    @Test fun donanteYReceptor() {
        assertNull(leerDonante(null).donante)
        assertNull(leerDonante(ParCrudo("", null)).donante)
        assertEquals(DonanteTrico(150.0, 25.0), leerDonante(ParCrudo("150", "25")).donante)
        assertTrue(leerDonante(ParCrudo("5", null)).errores[0].contains("10–500"))
        assertTrue(leerReceptor(ParCrudo(null, "200")).errores[0].contains("5–120"))
    }

    // ── Validación ──

    @Test fun haceFaltaClasificacionOMedicion() {
        val falta = "Anota la clasificación o al menos una medición de alguna zona."
        assertTrue(falta in validarTricoscopia(BorradorTricoscopia("p")))
        assertEquals(emptyList(), validarTricoscopia(BorradorTricoscopia("p", escala = "ludwig", grado = "II")))
        assertEquals(emptyList(), validarTricoscopia(BorradorTricoscopia("p", zonas = listOf(cruda("vertex", MetricaZona.DENSIDAD to "90")))))
        // Solo signos no alcanza: no hay qué seguir en el tiempo.
        assertEquals(1, validarTricoscopia(BorradorTricoscopia("p", zonas = listOf(cruda("vertex", signos = listOf("eritema"))))).size)
    }

    @Test fun elGradoTieneQueSerDeSuEscala() {
        assertTrue(gradoValido("hamilton_norwood", "III vértex"))
        assertFalse(gradoValido("hamilton_norwood", "VIII"))
        assertFalse(gradoValido("ludwig", "IV"))
        assertFalse(gradoValido(null, "II"))
        assertTrue(gradoValido("otro", "Alopecia areata en placa"))
        assertTrue(validarTricoscopia(BorradorTricoscopia("p", escala = "hamilton_norwood", grado = "VIII"))[0].contains("no corresponde"))
        assertTrue(validarTricoscopia(BorradorTricoscopia("p", escala = "otro"))[0].contains("Escribe qué patrón"))
        assertTrue(validarTricoscopia(BorradorTricoscopia("p", escala = "norwood"))[0].contains("desconocida"))
        assertTrue("Fecha inválida." in validarTricoscopia(BorradorTricoscopia("p", escala = "ludwig", fecha = "16/10/2026")))
        assertTrue("La fecha no puede ser futura." in
            validarTricoscopia(BorradorTricoscopia("p", escala = "ludwig", fecha = "2026-10-17"), "2026-10-16"))
        assertEquals(emptyList(), validarTricoscopia(BorradorTricoscopia("p", escala = "ludwig", fecha = "2026-10-16"), "2026-10-16"))
    }

    @Test fun erroresDeLaBase() {
        assertEquals("La cita no es de este paciente.", mensajeErrorTricoscopia("TRICOSCOPIA_CITA_INVALIDA"))
        assertTrue(mensajeErrorTricoscopia("new row violates check constraint \"evaluaciones_tricoscopicas_zonas_chk\"").contains("fuera de rango"))
        assertTrue(mensajeErrorTricoscopia(null).contains("No se pudo guardar"))
    }

    // ── Pedido y lectura del API ──

    @Test fun cuerpoDelPostLimpioYSinPelosCalculado() {
        val (don, rec) = paresDelFormulario("", "", "60", "")
        val b = BorradorTricoscopia(
            "pac", fecha = "2026-10-16", escala = "hamilton_norwood", grado = " III  vértex ",
            zonas = listOf(cruda("occipital", MetricaZona.DENSIDAD to "200", MetricaZona.UF_CM2 to "80"), cruda("media")),
            donante = don, receptor = rec, observaciones = "  ",
        )
        val o = cuerpoTricoscopia(b)
        assertEquals("pac", (o["pacienteId"] as JsonPrimitive).content)
        assertEquals("III vértex", (o["grado"] as JsonPrimitive).content)
        assertEquals(JsonNull, o["observaciones"])
        val zonas = o["zonas"] as JsonArray
        assertEquals(1, zonas.size)
        val z = zonas[0].jsonObject
        assertEquals("80", (z["ufCm2"] as JsonPrimitive).content.removeSuffix(".0"))
        assertEquals(JsonNull, z["pelosPorUf"]) // el calculado no se congela
        // Con área receptora se guardan los parámetros por defecto (150 cm², 25 %, 35 UF/cm²).
        assertEquals("150", (o["donante"]!!.jsonObject["areaCm2"] as JsonPrimitive).content.removeSuffix(".0"))
        assertEquals("35", (o["receptor"]!!.jsonObject["densidadObjetivo"] as JsonPrimitive).content.removeSuffix(".0"))
        // PATCH: con id y sin pacienteId.
        val patch = cuerpoTricoscopia(b, id = "ev1")
        assertEquals("ev1", (patch["id"] as JsonPrimitive).content)
        assertNull(patch["pacienteId"])
        // Sin nada de trasplante: null.
        assertEquals(null to null, paresDelFormulario("", "", "", ""))
    }

    @Test fun filaDelApiNormalizada() {
        val e = evaluacionDeJson(obj("""{"id":"a","paciente_id":"p","fecha":"2026-10-01","escala":"x",
            "zonas":"basura","donante":[],"visible_paciente":null}"""))!!
        assertNull(e.escala)
        assertEquals(emptyList(), e.zonas)
        assertNull(e.donante)
        assertTrue(e.visiblePaciente)
        val f = evaluacionDeJson(obj("""{"id":"b","fecha":"2026-10-01","escala":"ludwig","grado":"II","visible_paciente":false,
            "zonas":[{"zona":"frontal","densidad":"110","uf_cm2":null,"miniaturizacion_pct":22,"signos":["vellos",3]}],
            "donante":{"area_cm2":150,"extraible_pct":25},"receptor":{"area_cm2":60,"densidad_objetivo":null}}"""))!!
        assertEquals("ludwig", f.escala)
        assertFalse(f.visiblePaciente)
        assertEquals(110.0, f.zonas[0].densidad)
        assertEquals(listOf("vellos"), f.zonas[0].signos)
        assertEquals(DonanteTrico(150.0, 25.0), f.donante)
        assertEquals(ReceptorTrico(60.0, null), f.receptor)
        assertNull(evaluacionDeJson(obj("""{"fecha":"2026-10-01"}""")))
    }

    @Test fun lecturaDelGet() {
        val ok = TricoscopiaRepo.lecturaDeRespuesta(200, """{"items":[
            {"id":"c","fecha":"2026-12-01","escala":"ludwig","grado":"I"},{"id":"a","fecha":"2026-08-01","escala":"ludwig"}]}""")
        assertEquals(listOf("a", "c"), ok.items!!.map { it.id })
        val sin = TricoscopiaRepo.lecturaDeRespuesta(403,
            """{"error":"No tienes acceso a las tricoscopías de este paciente","codigo":"SIN_PERMISO"}""")
        assertTrue(sin.sinAcceso)
        assertEquals("No tienes acceso a las tricoscopías de este paciente", sin.error)
        assertTrue(TricoscopiaRepo.lecturaDeRespuesta(403, null).error!!.startsWith("No tienes acceso"))
        assertNull(TricoscopiaRepo.lecturaDeRespuesta(503, """{"error":"No se pudo leer"}""").items)
    }

    // ── Evolución ──

    private val items = listOf(
        ev("c", "2026-12-01", listOf(ZonaMedida(ZonaCapilar.FRONTAL, densidad = 120.0, miniaturizacionPct = 20.0), ZonaMedida(ZonaCapilar.VERTEX, densidad = 95.0))),
        ev("a", "2026-08-01", listOf(ZonaMedida(ZonaCapilar.FRONTAL, densidad = 100.0, miniaturizacionPct = 30.0), ZonaMedida(ZonaCapilar.VERTEX, densidad = 80.0))),
        ev("b", "2026-10-01", listOf(ZonaMedida(ZonaCapilar.FRONTAL, densidad = 110.0, miniaturizacionPct = 25.0))),
    )

    @Test fun ordenaCronologicamente() {
        assertEquals(listOf("a", "b", "c"), ordenarEvaluaciones(items).map { it.id })
    }

    @Test fun comparaConLaAnteriorYLaPrimera() {
        val (fr, vx) = evolucionZonas(items)
        assertEquals(ZonaCapilar.FRONTAL, fr.zona)
        val dAnt = fr.vsAnterior.getValue(MetricaZona.DENSIDAD)
        assertEquals(10.0, dAnt.delta); assertEquals(9.0, dAnt.pct); assertEquals("mejora", dAnt.tendencia); assertEquals("2026-10-01", dAnt.fechaDesde)
        val dPri = fr.vsPrimera.getValue(MetricaZona.DENSIDAD)
        assertEquals(20.0, dPri.delta); assertEquals(20.0, dPri.pct); assertEquals("2026-08-01", dPri.fechaDesde)
        val mPri = fr.vsPrimera.getValue(MetricaZona.MINIATURIZACION)
        assertEquals(-10.0, mPri.delta); assertNull(mPri.pct); assertEquals("mejora", mPri.tendencia)
        // La coronilla no se midió en "b": su anterior es "a".
        assertEquals(15.0, vx.vsAnterior.getValue(MetricaZona.DENSIDAD).delta)
        assertEquals("2026-08-01", vx.vsAnterior.getValue(MetricaZona.DENSIDAD).fechaDesde)
        assertEquals(15.0, vx.vsPrimera.getValue(MetricaZona.DENSIDAD).delta)
    }

    @Test fun evaluacionDelMedioYPrimera() {
        assertEquals(10.0, evolucionZonas(items, "b")[0].vsAnterior[MetricaZona.DENSIDAD]?.delta)
        assertEquals(emptyMap(), evolucionZonas(items, "a")[0].vsAnterior)
        assertEquals(emptyList(), evolucionZonas(items, "zzz"))
    }

    @Test fun caidaDeDensidadEmpeora() {
        val c = cambioMetrica(90.0, 100.0, MetricaZona.DENSIDAD, "2026-01-01")!!
        assertEquals(-10.0, c.delta); assertEquals(-10.0, c.pct); assertEquals("empeora", c.tendencia)
        assertEquals("empeora", cambioMetrica(35.0, 30.0, MetricaZona.MINIATURIZACION, "x")!!.tendencia)
        assertEquals("igual", cambioMetrica(30.0, 30.0, MetricaZona.DENSIDAD, "x")!!.tendencia)
        assertNull(cambioMetrica(null, 30.0, MetricaZona.DENSIDAD, "x"))
    }

    @Test fun textosDeCambio() {
        assertEquals("+20 pelos/cm² (+20 %)", textoCambio(cambioMetrica(120.0, 100.0, MetricaZona.DENSIDAD, "x"), MetricaZona.DENSIDAD))
        assertEquals("−10 pp", textoCambio(cambioMetrica(20.0, 30.0, MetricaZona.MINIATURIZACION, "x"), MetricaZona.MINIATURIZACION))
        assertEquals("—", textoCambio(null, MetricaZona.DENSIDAD))
        assertEquals("↓", flechaCambio(cambioMetrica(90.0, 100.0, MetricaZona.DENSIDAD, "x")))
    }

    @Test fun seriesParaElGrafico() {
        assertEquals(listOf(80.0, 95.0), serieZona(items, ZonaCapilar.VERTEX, MetricaZona.DENSIDAD).map { it.valor })
        assertEquals(listOf(ZonaCapilar.FRONTAL, ZonaCapilar.VERTEX), zonasConEvolucion(items, MetricaZona.DENSIDAD))
        assertEquals(listOf(ZonaCapilar.FRONTAL), zonasConEvolucion(items, MetricaZona.MINIATURIZACION))
        assertEquals(listOf(MetricaZona.DENSIDAD, MetricaZona.MINIATURIZACION), metricasConEvolucion(items))
    }

    @Test fun ejeDeTiempoProporcionalALosDias() {
        val f = fraccionesTiempo(listOf("2026-01-01", "2026-01-31", "2026-12-27"))
        assertEquals(0.0, f[0])
        assertEquals(1.0, f[2])
        // 30 días vs 330 días: el segundo tramo es 11 veces el primero.
        assertTrue(abs((f[2] - f[1]) / (f[1] - f[0]) - 11.0) < 0.01)
        assertEquals(listOf(0.0), fraccionesTiempo(listOf("2026-01-01")))
    }

    @Test fun graficoImpreso() {
        assertEquals("", svgEvolucionZonas(listOf(SerieZonaSvg(ZonaCapilar.FRONTAL, listOf(PuntoSerieHc("2026-01-01", 100.0))))))
        val svg = svgEvolucionZonas(listOf(
            SerieZonaSvg(ZonaCapilar.FRONTAL, listOf(PuntoSerieHc("2026-01-01", 100.0), PuntoSerieHc("2026-03-01", 120.0))),
            SerieZonaSvg(ZonaCapilar.TEMPORAL, listOf(PuntoSerieHc("2026-01-01", 90.0), PuntoSerieHc("2026-03-01", 95.0))),
            SerieZonaSvg(ZonaCapilar.VERTEX, listOf(PuntoSerieHc("2026-03-01", 70.0))),
        ))
        assertEquals(2, Regex("<polyline").findAll(svg).count())
        assertTrue(svg.contains("stroke-dasharray=\"4 3\""))
        assertTrue(svg.contains("01/01/26"))
        assertTrue(svg.contains("Frontal"))
        assertFalse(svg.contains("Coronilla"))
        val t = svgEvolucionZonas(listOf(SerieZonaSvg(ZonaCapilar.FRONTAL, listOf(
            PuntoSerieHc("2026-01-01", 100.0), PuntoSerieHc("2026-01-31", 105.0), PuntoSerieHc("2026-12-27", 120.0),
        ))))
        val xs = Regex("<circle cx=\"([\\d.]+)\"").findAll(t).map { it.groupValues[1].toDouble() }.toList()
        assertTrue(abs((xs[2] - xs[1]) / (xs[1] - xs[0]) - 11.0) < 0.1)
        assertTrue(t.contains("27/12/26"))
    }

    // ── Lectura clínica ──

    private val clinica = ev("a", "2026-10-01", listOf(
        ZonaMedida(ZonaCapilar.FRONTAL, densidad = 120.0, miniaturizacionPct = 28.0, diametroUm = 55.0, signos = listOf("peripilar", "vellos")),
        ZonaMedida(ZonaCapilar.OCCIPITAL, densidad = 200.0, miniaturizacionPct = 8.0, diametroUm = 72.0),
    ))

    @Test fun cadaZonaContraLaDonante() {
        val r = comparacionConDonante(clinica)
        assertEquals(1, r.size)
        assertEquals(ZonaCapilar.FRONTAL, r[0].zona); assertEquals(60.0, r[0].densidadPct); assertEquals(20.0, r[0].miniaturizacionPp)
        assertEquals(emptyList(), comparacionConDonante(ev("b", "x", listOf(ZonaMedida(ZonaCapilar.FRONTAL, densidad = 1.0)))))
    }

    @Test fun criteriosDeRakowska() {
        val r = indiciosAndrogeneticos(clinica)
        assertEquals(2, r.mayores.size)
        assertEquals(listOf("Signo peripilar en la zona frontal", "Vellos aumentados en la zona frontal"), r.menores)
        // 10 % exacto NO cuenta (> 10 %).
        assertEquals(emptyList(), indiciosAndrogeneticos(ev("b", "x", listOf(ZonaMedida(ZonaCapilar.FRONTAL, miniaturizacionPct = 10.0)))).mayores)
        assertTrue(cumpleCriteriosAndrogeneticos(IndiciosAndrogeneticos(listOf("a", "b"), emptyList())))
        assertTrue(cumpleCriteriosAndrogeneticos(IndiciosAndrogeneticos(listOf("a"), listOf("x", "y"))))
        assertFalse(cumpleCriteriosAndrogeneticos(IndiciosAndrogeneticos(listOf("a"), listOf("x"))))
        assertFalse(cumpleCriteriosAndrogeneticos(IndiciosAndrogeneticos(emptyList(), listOf("x", "y"))))
    }

    // ── Estimación para trasplante ──

    @Test fun alcanzaConMargen() {
        // 80 UF/cm² × 150 cm² × 25 % = 3000 UF; 60 cm² × 35 = 2100.
        val r = estimarTrasplante(EntradaTrasplante(ufCm2Donante = 80.0, areaReceptoraCm2 = 60.0))
        assertEquals("alcanza", r.nivel)
        assertEquals(12000L, r.ufDonanteTotal); assertEquals(3000L, r.ufDisponibles); assertEquals(2100L, r.ufNecesarias)
        assertEquals(900L, r.balance); assertEquals(1.43, r.cobertura); assertEquals(50.0, r.densidadLograble)
        assertEquals("uf_cm2", r.origenDonante); assertEquals(emptyList(), r.faltan); assertEquals(emptyList(), r.avisos)
        assertTrue(r.mensaje.startsWith("Alcanza con margen"))
        assertTrue(r.mensaje.contains("~2,100 UF de ~3,000"))
    }

    @Test fun justoYNoAlcanza() {
        val j = estimarTrasplante(EntradaTrasplante(ufCm2Donante = 80.0, areaReceptoraCm2 = 80.0))
        assertEquals("justo", j.nivel); assertEquals(2800L, j.ufNecesarias)
        val r = estimarTrasplante(EntradaTrasplante(ufCm2Donante = 80.0, areaReceptoraCm2 = 100.0))
        assertEquals("no_alcanza", r.nivel); assertEquals(3500L, r.ufNecesarias); assertEquals(-500L, r.balance); assertEquals(30.0, r.densidadLograble)
        assertTrue(r.mensaje.contains("faltan ~500 UF"))
        assertTrue(r.mensaje.contains("~30 UF/cm² en 100 cm²"))
    }

    @Test fun desdeLaDensidadYPelosEstimados() {
        val r = estimarTrasplante(EntradaTrasplante(densidadDonante = 200.0, pelosPorUfDonante = 2.5, areaDonanteCm2 = 200.0,
            extraiblePct = 30.0, areaReceptoraCm2 = 50.0, densidadObjetivo = 40.0))
        assertEquals(80.0, r.ufCm2Donante); assertEquals("densidad", r.origenDonante); assertEquals(4800L, r.ufDisponibles)
        assertEquals(2000L, r.ufNecesarias); assertEquals(12000L, r.pelosDisponibles); assertEquals("alcanza", r.nivel)
    }

    @Test fun avisaSobreElTreintaYDiceQueFalta() {
        assertEquals(1, estimarTrasplante(EntradaTrasplante(ufCm2Donante = 80.0, extraiblePct = 40.0, areaReceptoraCm2 = 10.0)).avisos.size)
        val r = estimarTrasplante(EntradaTrasplante())
        assertEquals("incompleto", r.nivel); assertEquals(2, r.faltan.size); assertNull(r.ufDisponibles)
    }

    @Test fun deUnaEvaluacionGuardada() {
        val e = ev("a", "x", listOf(ZonaMedida(ZonaCapilar.OCCIPITAL, ufCm2 = 70.0)), receptor = ReceptorTrico(40.0, null))
        val r = trasplanteDeEvaluacion(e)!!
        assertEquals(2625L, r.ufDisponibles); assertEquals(1400L, r.ufNecesarias); assertEquals("alcanza", r.nivel)
        assertNull(trasplanteDeEvaluacion(ev("b", "x", emptyList())))
        assertEquals("12,000", milesTrico(12000)); assertEquals("3,000", milesTrico(3000L)); assertEquals("500", milesTrico(500))
    }

    // ── Formulario ──

    @Test fun copiarDeLaAnteriorNuncaLasMediciones() {
        val e = ev("a", "x", listOf(ZonaMedida(ZonaCapilar.FRONTAL, densidad = 100.0, signos = listOf("eritema"))),
            escala = "ludwig", grado = "I", observaciones = "no", donante = DonanteTrico(150.0, 25.0))
        val c = copiaDeAnterior(e)
        assertEquals("ludwig", c.escala); assertEquals("I", c.grado); assertEquals(DonanteTrico(150.0, 25.0), c.donante); assertNull(c.receptor)
    }

    @Test fun referenciaGrisDeCadaCasilla() {
        val previa = ZonaMedida(ZonaCapilar.FRONTAL, densidad = 120.0, pelosPorUf = 2.1)
        assertEquals("ant. 120" to "anterior", referenciaCasilla(MetricaZona.DENSIDAD, null, null, previa).let { it.texto to it.tipo })
        assertEquals("≈2.5" to "calculado", referenciaCasilla(MetricaZona.PELOS_POR_UF, 200.0, 80.0, previa).let { it.texto to it.tipo })
        assertEquals("ant. 2.1" to "anterior", referenciaCasilla(MetricaZona.PELOS_POR_UF, null, 80.0, previa).let { it.texto to it.tipo })
        assertEquals("80" to "ejemplo", referenciaCasilla(MetricaZona.UF_CM2, null, null, null).let { it.texto to it.tipo })
    }

    @Test fun alEditarElPelosPorUfCalculadoQuedaVacio() {
        assertNull(pelosPorUfParaEditar(ZonaMedida(ZonaCapilar.FRONTAL, densidad = 200.0, ufCm2 = 80.0, pelosPorUf = 2.5)))
        assertEquals(2.3, pelosPorUfParaEditar(ZonaMedida(ZonaCapilar.FRONTAL, densidad = 200.0, ufCm2 = 80.0, pelosPorUf = 2.3)))
    }

    @Test fun escalaSugeridaLaDeLaUltimaQueLaTiene() {
        assertEquals("ludwig", escalaSugerida(listOf(ev("a", "2026-01-01", emptyList(), escala = "ludwig"), ev("b", "2026-02-01", emptyList()))))
        assertNull(escalaSugerida(emptyList()))
    }

    // ── Informe ──

    @Test fun informeEscapaYNoMuestraLoQueVinoDespues() {
        val a = ev("a", "2026-08-01", listOf(ZonaMedida(ZonaCapilar.FRONTAL, densidad = 100.0)), escala = "ludwig", grado = "I",
            observaciones = "<script>x</script>")
        val b = ev("b", "2026-10-01", listOf(ZonaMedida(ZonaCapilar.FRONTAL, densidad = 110.0)), escala = "ludwig", grado = "II",
            receptor = ReceptorTrico(60.0, null))
        val c = ev("c", "2026-12-01", listOf(ZonaMedida(ZonaCapilar.FRONTAL, densidad = 999.0)))
        val html = htmlInformeTricoscopia(b, listOf(c, a, b), "Clínica <A>", null, "http://inseguro/logo.png", "red",
            "Ana & Co", "123", "Dra. Ruiz")
        assertTrue(html.contains("Clínica &lt;A&gt;"))
        assertTrue(html.contains("Ana &amp; Co"))
        assertFalse(html.contains("http://inseguro"))
        assertTrue(html.contains("Ludwig II"))
        assertTrue(html.contains("+10 pelos/cm² (+10 %)"))
        assertTrue(html.contains("Estimación para trasplante"))
        assertFalse(html.contains("999"))
        val conObs = htmlInformeTricoscopia(a, listOf(a), null, null, null, null, null, null, null)
        assertTrue(conObs.contains("&lt;script&gt;"))
        assertFalse(conObs.contains("<script>"))
    }

    // ── Visibilidad (rubro capilar) ──

    @Test fun visibilidadPorEspecialidad() {
        val renova = MapaCapilar(listOf("cc"), solo = true)
        assertTrue(pacienteEsCapilar(renova))
        val glam = MapaCapilar(listOf("mc"))
        assertFalse(pacienteEsCapilar(glam))
        assertFalse(pacienteEsCapilar(glam, atenciones = listOf(AtencionFisio(especialidadServicioId = "es"))))
        assertTrue(pacienteEsCapilar(glam, atenciones = listOf(AtencionFisio(especialidadServicioId = "mc"))))
        assertTrue(pacienteEsCapilar(glam, tieneEvaluaciones = true))
        assertTrue(pacienteEsCapilar(glam, especialidadesDeQuienMira = listOf("mc")))
        assertTrue(citaEsCapilar(glam, especialidadId = "mc"))
        assertFalse(pacienteEsCapilar(MapaCapilar(), tieneEvaluaciones = true))
    }

    @Test fun contextoLeeLosCamposNuevosYToleraBackendViejo() {
        val nuevo = StaffContextoRepo.parsear(obj("""{"clinicaId":"c","rol":"Admin","permisos":{},
            "especialidadesCapilar":["mc",null,""],"soloCapilar":false}"""))
        assertEquals(listOf("mc"), nuevo.mapaCapilar.ids)
        assertFalse(nuevo.mapaCapilar.solo)
        val viejo = StaffContextoRepo.parsear(obj("""{"clinicaId":"c","rol":"Admin","permisos":{}}"""))
        assertEquals(MapaCapilar(), viejo.mapaCapilar)
        assertFalse(viejo.mapaCapilar.activo)
    }
}
