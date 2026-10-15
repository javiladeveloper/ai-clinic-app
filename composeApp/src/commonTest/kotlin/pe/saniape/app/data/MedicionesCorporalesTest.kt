package pe.saniape.app.data

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import pe.saniape.app.data.staff.AtencionFisio
import pe.saniape.app.data.staff.BorradorMedicion
import pe.saniape.app.data.staff.MapaNutricion
import pe.saniape.app.data.staff.MedicionCorporal
import pe.saniape.app.data.staff.MetricaMedicion
import pe.saniape.app.data.staff.StaffContextoRepo
import pe.saniape.app.data.staff.citaEsNutricion
import pe.saniape.app.data.staff.cuerpoMedicion
import pe.saniape.app.data.staff.historialMediciones
import pe.saniape.app.data.staff.imcEnVivo
import pe.saniape.app.data.staff.imcPorMedicion
import pe.saniape.app.data.staff.lineaMedidas
import pe.saniape.app.data.staff.medicionDeJson
import pe.saniape.app.data.staff.metricasDisponibles
import pe.saniape.app.data.staff.pacienteEsNutricion
import pe.saniape.app.data.staff.resumenMediciones
import pe.saniape.app.data.staff.serieMetrica
import pe.saniape.app.data.staff.textoCambio
import pe.saniape.app.data.staff.textoCampoMedicion
import pe.saniape.app.data.staff.ultimaTalla
import pe.saniape.app.data.staff.validarMedicion

/**
 * Pestaña 📏 Medidas (nutrición). Validación gemela de `lib/mediciones-corporales.ts`
 * (mismos textos) y las ayudas de evolución / historial.
 */
class MedicionesCorporalesTest {
    private fun obj(s: String): JsonObject = Json.parseToJsonElement(s).jsonObject

    // ── ¿Es nutrición? ──
    private val mixta = MapaNutricion(ids = listOf("e-nutri"))

    @Test fun sinMapaNoHayPestana() {
        // Backend viejo o clínica sin nutrición: la ficha queda como antes.
        assertFalse(pacienteEsNutricion(MapaNutricion(), listOf(AtencionFisio(especialidadId = "e-nutri")), tieneMediciones = true))
        assertFalse(MapaNutricion().activo)
    }

    @Test fun soloNutricionSiempre() {
        assertTrue(pacienteEsNutricion(MapaNutricion(solo = true)))
        assertTrue(MapaNutricion(solo = true).activo)
    }

    @Test fun mixtaPorAtencionMedicionesOQuienMira() {
        assertTrue(pacienteEsNutricion(mixta, listOf(AtencionFisio(especialidadServicioId = "e-nutri"))))
        assertFalse(pacienteEsNutricion(mixta, listOf(AtencionFisio(especialidadServicioId = "e-fisio"))))
        assertTrue(pacienteEsNutricion(mixta, tieneMediciones = true))
        assertTrue(pacienteEsNutricion(mixta, especialidadesDeQuienMira = listOf("e-nutri")))
        assertFalse(pacienteEsNutricion(mixta, especialidadesDeQuienMira = listOf("e-fisio", "")))
    }

    @Test fun citaPorEspecialidadLuegoServicioLuegoProfesional() {
        assertTrue(citaEsNutricion(mixta, especialidadId = "e-nutri", especialidadServicioId = "e-fisio"))
        assertFalse(citaEsNutricion(mixta, especialidadId = "e-fisio", especialidadServicioId = "e-nutri"))
        assertTrue(citaEsNutricion(mixta, especialidadesProfesional = listOf("e-nutri")))
        // Profesional con otra especialidad además: no se sabe → NO.
        assertFalse(citaEsNutricion(mixta, especialidadesProfesional = listOf("e-nutri", "e-fisio")))
        assertFalse(citaEsNutricion(mixta))
    }

    @Test fun contextoLeeLosCamposNuevosYToleraBackendViejo() {
        val nuevo = StaffContextoRepo.parsear(obj("""{"clinicaId":"c","rol":"Admin","permisos":{},
            "especialidadesNutricion":["e1",null,""],"soloNutricion":true}"""))
        assertEquals(listOf("e1"), nuevo.mapaNutricion.ids)
        assertTrue(nuevo.mapaNutricion.solo)
        val viejo = StaffContextoRepo.parsear(obj("""{"clinicaId":"c","rol":"Admin","permisos":{}}"""))
        assertEquals(MapaNutricion(), viejo.mapaNutricion)
        assertFalse(viejo.mapaNutricion.activo)
    }

    // ── Validación (mismos textos que la web) ──
    @Test fun sinNingunaMedida() {
        assertEquals(listOf("Anota al menos una medida."), validarMedicion(BorradorMedicion("p1")))
        assertEquals(listOf("Anota al menos una medida."), validarMedicion(BorradorMedicion("p1", valores = mapOf("peso" to "  "))))
    }

    @Test fun validaRangosYBasura() {
        assertEquals(emptyList(), validarMedicion(BorradorMedicion("p1", "2026-10-14", mapOf("peso" to "72,5"))))
        assertEquals(
            listOf("Peso: 500 kg está fuera de lo posible (0.3–400)."),
            validarMedicion(BorradorMedicion("p1", valores = mapOf("peso" to "500"))),
        )
        assertEquals(
            listOf("Cintura: escribe solo el número."),
            validarMedicion(BorradorMedicion("p1", valores = mapOf("cintura" to "8x"))),
        )
        assertEquals(
            listOf("Grasa corporal: 90 % está fuera de lo posible (1–80)."),
            validarMedicion(BorradorMedicion("p1", valores = mapOf("grasa_pct" to "90"))),
        )
        assertEquals(listOf("Falta el paciente."), validarMedicion(BorradorMedicion("", valores = mapOf("peso" to "70"))))
        assertEquals(listOf("Fecha inválida."), validarMedicion(BorradorMedicion("p1", "14/10/2026", mapOf("peso" to "70"))))
    }

    @Test fun tallaEnMetrosSeEntiendeEnCm() {
        assertEquals(emptyList(), validarMedicion(BorradorMedicion("p1", valores = mapOf("talla" to "1.70"))))
        val cuerpo = cuerpoMedicion(BorradorMedicion("p1", "2026-10-14", mapOf("talla" to "1,70", "peso" to "72.5", "cintura" to ""), " ok "))
        assertEquals(JsonPrimitive(170.0), cuerpo["talla"])
        assertEquals(JsonPrimitive(72.5), cuerpo["peso"])
        assertNull(cuerpo["cintura"])
        assertEquals(JsonPrimitive("ok"), cuerpo["notas"])
        assertEquals(JsonPrimitive("2026-10-14"), cuerpo["fecha"])
    }

    @Test fun cuerpoUsaNombresDelApi() {
        val c = cuerpoMedicion(BorradorMedicion("p1", null, mapOf("perimetro_abdominal" to "90", "grasa_pct" to "25", "masa_muscular" to "30")))
        assertEquals(JsonPrimitive(90.0), c["perimetroAbdominal"])
        assertEquals(JsonPrimitive(25.0), c["grasaPct"])
        assertEquals(JsonPrimitive(30.0), c["masaMuscular"])
        assertNull(c["fecha"])
        assertNull(c["notas"])
    }

    // ── IMC ──
    @Test fun imcEnVivoConClasificacion() {
        val i = imcEnVivo("70", "1.75")!!
        assertEquals(22.86, i.valor)
        assertEquals("Normal", i.clasificacion)
        assertNull(imcEnVivo("70", ""))
        assertNull(imcEnVivo("abc", "170"))
        // En menores no se clasifica.
        assertNull(imcEnVivo("40", "150", edad = 12)!!.clasificacion)
    }

    // ── Evolución e historial ──
    private val a = MedicionCorporal("a", "p", "2026-09-01", peso = 80.0, talla = 170.0, cintura = 95.0, createdAt = "2026-09-01T10:00")
    private val b = MedicionCorporal("b", "p", "2026-09-15", peso = 78.4, cintura = 92.0, createdAt = "2026-09-15T10:00")
    private val c = MedicionCorporal("c", "p", "2026-10-01", cintura = 91.5, grasaPct = 30.0, createdAt = "2026-10-01T10:00")
    private val d = MedicionCorporal("d", "p", "2026-10-14", peso = 77.0, talla = 171.0, createdAt = "2026-10-14T10:00")

    @Test fun imcUsaLaUltimaTallaConocida() {
        val imcs = imcPorMedicion(listOf(d, b, a, c))
        assertEquals(27.68, imcs["a"])
        assertEquals(27.13, imcs["b"]) // sin talla propia: la de a
        assertNull(imcs["c"]) // sin peso
        assertEquals(26.33, imcs["d"])
        assertEquals(171.0, ultimaTalla(listOf(a, b, d)))
        assertNull(ultimaTalla(listOf(b, c)))
    }

    @Test fun seriesYMetricasDisponibles() {
        val todas = listOf(c, a, d, b)
        assertEquals(listOf(80.0, 78.4, 77.0), serieMetrica(todas, MetricaMedicion.PESO).map { it.valor })
        assertEquals(listOf("2026-09-01", "2026-09-15", "2026-10-01"), serieMetrica(todas, MetricaMedicion.CINTURA).map { it.fecha })
        // % grasa tiene un solo punto y masa muscular ninguno: no se ofrecen.
        assertEquals(listOf(MetricaMedicion.PESO, MetricaMedicion.IMC, MetricaMedicion.CINTURA), metricasDisponibles(todas))
        assertEquals(emptyList(), metricasDisponibles(listOf(a)))
    }

    @Test fun resumenPrimeraContraUltima() {
        val r = resumenMediciones(listOf(d, c, b, a))!!
        assertEquals("2026-09-01", r.desde)
        assertEquals("2026-10-14", r.hasta)
        assertEquals(4, r.total)
        assertEquals(-3.0, r.cambioPeso)
        assertEquals(-3.5, r.cambioCintura)
        assertEquals(26.33, r.imcActual)
        assertEquals("Sobrepeso", r.clasificacionImc)
        assertNull(resumenMediciones(emptyList()))
        val uno = resumenMediciones(listOf(a))!!
        assertNull(uno.cambioPeso)
        assertEquals(80.0, uno.pesoActual)
    }

    @Test fun historialMasRecientePrimeroConDeltaDePeso() {
        val h = historialMediciones(listOf(a, b, c, d))
        assertEquals(listOf("d", "c", "b", "a"), h.map { it.medicion.id })
        assertEquals(-1.4, h[0].deltaPeso) // contra b (c no tiene peso)
        assertNull(h[1].deltaPeso)
        assertEquals(-1.6, h[2].deltaPeso)
        assertNull(h[3].deltaPeso)
    }

    @Test fun mismoDiaOrdenaPorCreacion() {
        val x = MedicionCorporal("x", "p", "2026-10-14", peso = 70.0, createdAt = "2026-10-14T09:00")
        val y = MedicionCorporal("y", "p", "2026-10-14", peso = 71.0, createdAt = "2026-10-14T18:00")
        assertEquals(listOf("y", "x"), historialMediciones(listOf(y, x)).map { it.medicion.id })
        assertEquals(1.0, historialMediciones(listOf(y, x))[0].deltaPeso)
    }

    @Test fun textosNeutros() {
        assertEquals("+1.2 kg", textoCambio(1.2, "kg"))
        assertEquals("−0.8 cm", textoCambio(-0.8, "cm"))
        assertEquals("Sin cambio", textoCambio(0.01, "kg"))
        assertEquals("Peso 72.5 kg · Talla 170 cm · Grasa 30 %", lineaMedidas(MedicionCorporal("i", "p", "2026-10-14", peso = 72.5, talla = 170.0, grasaPct = 30.0)))
        assertEquals("170", textoCampoMedicion(170.0))
        assertEquals("", textoCampoMedicion(null))
    }

    @Test fun filaDelApi() {
        val m = medicionDeJson(obj("""{"id":"m1","paciente_id":"p","fecha":"2026-10-14","peso":"72.50","talla":170,
            "cintura":null,"grasa_pct":28.5,"notas":"","created_at":"2026-10-14T15:00:00+00:00"}"""))!!
        assertEquals(72.5, m.peso)
        assertEquals(170.0, m.talla)
        assertNull(m.cintura)
        assertEquals(28.5, m.grasaPct)
        assertNull(m.notas)
        assertNull(medicionDeJson(obj("""{"peso":70}""")))
    }
}
