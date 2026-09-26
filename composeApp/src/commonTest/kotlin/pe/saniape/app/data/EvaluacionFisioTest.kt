package pe.saniape.app.data

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import pe.saniape.app.data.staff.AtencionFisio
import pe.saniape.app.data.staff.EvaluacionFisio
import pe.saniape.app.data.staff.EvaluacionFisioDatos
import pe.saniape.app.data.staff.FuerzaMedida
import pe.saniape.app.data.staff.MapaFisio
import pe.saniape.app.data.staff.ObjetivoBorrador
import pe.saniape.app.data.staff.ObjetivoTratamiento
import pe.saniape.app.data.staff.PruebaEspecial
import pe.saniape.app.data.staff.RangoMedido
import pe.saniape.app.data.staff.TestAplicado
import pe.saniape.app.data.staff.TratamientoOrigen
import pe.saniape.app.data.staff.ZONAS_CORPORALES
import pe.saniape.app.data.staff.alternarZona
import pe.saniape.app.data.staff.compararEvaluaciones
import pe.saniape.app.data.staff.datosAJson
import pe.saniape.app.data.staff.datosDeJson
import pe.saniape.app.data.staff.limpiarDatos
import pe.saniape.app.data.staff.pacienteEsFisio
import pe.saniape.app.data.staff.regionesProbables
import pe.saniape.app.data.staff.resumenEvaluacionFisio
import pe.saniape.app.data.staff.sugerirObjetivos
import pe.saniape.app.data.staff.textoFila
import pe.saniape.app.data.staff.textoZonas
import pe.saniape.app.data.staff.tieneDatos
import pe.saniape.app.data.staff.tratamientoDeEvaluacion

/** Gemelo de `lib/__tests__/evaluacion-fisio.test.ts` de la web: mismos casos, mismos resultados. */
class EvaluacionFisioTest {

    private fun ev(
        fecha: String, datos: EvaluacionFisioDatos, tipo: String = "inicial", id: String = "ev-$fecha",
        citaId: String? = null, tratamientoId: String? = null,
    ) = EvaluacionFisio(id, "pac", tratamientoId, citaId, null, tipo, fecha, datos)

    private val evs = listOf(
        ev("2026-09-01", EvaluacionFisioDatos(
            eva = 7,
            rangos = listOf(RangoMedido("rodilla", "flex", "D", 90), RangoMedido("rodilla", "ext", "D", -10)),
            fuerza = listOf(FuerzaMedida("Cuádriceps", "D", 3)),
            tests = listOf(TestAplicado("lysholm", emptyList(), 52.0), TestAplicado("oswestry", emptyList(), 40.0)),
            pruebas = listOf(PruebaEspecial("Lachman", "D", "+")),
        )),
        // Reevaluación rápida: solo mide la flexión.
        ev("2026-09-15", EvaluacionFisioDatos(rangos = listOf(RangoMedido("rodilla", "flex", "D", 110))), tipo = "reevaluacion"),
        ev("2026-09-25", EvaluacionFisioDatos(
            eva = 2,
            rangos = listOf(RangoMedido("rodilla", "flex", "D", 125), RangoMedido("rodilla", "ext", "D", -2)),
            fuerza = listOf(FuerzaMedida("Cuádriceps", "D", 4)),
            tests = listOf(TestAplicado("lysholm", emptyList(), 88.0), TestAplicado("oswestry", emptyList(), 50.0)),
            pruebas = listOf(PruebaEspecial("Lachman", "D", "-")),
        ), tipo = "reevaluacion"),
    )
    private val filas = compararEvaluaciones(evs)
    private fun fila(clave: String) = filas.first { it.clave == clave }

    @Test fun flexionRodilla90a125Mejora() {
        val f = fila("rango|rodilla|flex|D")
        assertEquals(90.0, f.inicial); assertEquals(125.0, f.ultimo); assertEquals(35.0, f.delta)
        assertEquals(true, f.mejora); assertEquals(135, f.referencia)
        assertEquals("Flexión rodilla D: 90° → 125° (+35°)", textoFila(f))
    }

    @Test fun deficitDeExtensionMasCercaDe0EsMejora() {
        val f = fila("rango|rodilla|ext|D")
        assertEquals(8.0, f.delta); assertEquals(true, f.mejora)
    }

    @Test fun evaBajaEsMejora() {
        val f = fila("eva")
        assertEquals(7.0, f.inicial); assertEquals(2.0, f.ultimo); assertEquals(-5.0, f.delta); assertEquals(true, f.mejora)
    }

    @Test fun lysholmSubeMejoraOswestrySubeEmpeora() {
        val l = fila("test|lysholm")
        assertEquals(36.0, l.delta); assertEquals(true, l.mejora); assertEquals("Bueno", l.interpretacion)
        val o = fila("test|oswestry")
        assertEquals(10.0, o.delta); assertEquals(false, o.mejora)
    }

    @Test fun fuerzaYPrueba() {
        val f = fila("fuerza|Cuádriceps|D")
        assertEquals(1.0, f.delta); assertEquals(true, f.mejora)
        val p = fila("prueba|lachman|D")
        assertEquals("+", p.inicialTexto); assertEquals("−", p.ultimoTexto); assertEquals(true, p.mejora)
    }

    @Test fun ordenDeCategorias() {
        assertEquals(listOf("dolor", "test", "test", "rango", "rango", "fuerza", "prueba"), filas.map { it.categoria })
    }

    @Test fun unaSolaMedicionSinDelta() {
        val f = compararEvaluaciones(listOf(evs[0])).first { it.clave == "eva" }
        assertNull(f.ultimo); assertNull(f.delta); assertNull(f.mejora); assertNull(f.fechaUltima)
        assertEquals("Dolor (EVA): 7/10", textoFila(f))
    }

    @Test fun sinCambioMejoraNull() {
        val f = compararEvaluaciones(listOf(
            ev("2026-01-01", EvaluacionFisioDatos(eva = 4)), ev("2026-01-10", EvaluacionFisioDatos(eva = 4)),
        ))[0]
        assertEquals(0.0, f.delta); assertNull(f.mejora)
    }

    // ── Resumen ──
    private val evsR = listOf(
        ev("2026-09-01", EvaluacionFisioDatos(eva = 6, zonas = mapOf("lumbar" to 3)), id = "a", citaId = "cita-1"),
        ev("2026-09-20", EvaluacionFisioDatos(eva = 2, zonas = mapOf("lumbar" to 1)), tipo = "reevaluacion", id = "b", tratamientoId = "t1"),
        ev("2026-05-01", EvaluacionFisioDatos(eva = 9), id = "c", tratamientoId = "otro"),
    )
    private val objetivos = listOf(
        ObjetivoTratamiento("o1", "pac", null, "a", "Estar sentado 1 hora sin dolor", null, null, null, null, "logrado", "2026-09-20", 0),
        ObjetivoTratamiento("o2", "pac", "otro", null, "Otro", null, null, null, null, "en_curso", null, 0),
    )
    private val trats = listOf(TratamientoOrigen("t1", "cita-1"), TratamientoOrigen("otro", null))

    @Test fun evaluacionAntesDelTratamientoSeEnlazaPorCitaOrigen() {
        assertEquals("t1", tratamientoDeEvaluacion(evsR[0], trats))
    }

    @Test fun resumenPorTratamiento() {
        val r = resumenEvaluacionFisio(evsR, objetivos, trats, "t1")
        assertEquals(listOf("a", "b"), r.evaluaciones.map { it.id })
        assertEquals(6.0, r.comparativo[0].inicial); assertEquals(2.0, r.comparativo[0].ultimo)
        assertEquals(mapOf("lumbar" to 3), r.zonasInicial)
        assertEquals(mapOf("lumbar" to 1), r.zonasUltima)
        assertEquals(listOf("o1"), r.objetivos.map { it.id })
        assertEquals(1, r.objetivosLogrados)
        assertTrue("Dolor (EVA): 6/10 → 2/10 (-4)" in r.lineas)
        assertTrue(r.lineas.any { it.startsWith("Objetivo: Estar sentado 1 hora sin dolor") && it.endsWith("Logrado") })
    }

    @Test fun resumenSinFiltroEnOrdenCronologico() {
        val r = resumenEvaluacionFisio(evsR, objetivos, trats)
        assertEquals(listOf("c", "a", "b"), r.evaluaciones.map { it.id })
        assertEquals(9.0, r.comparativo[0].inicial); assertEquals(2.0, r.comparativo[0].ultimo)
    }

    @Test fun resumenVacio() {
        assertFalse(resumenEvaluacionFisio(emptyList()).hayDatos)
    }

    // ── Limpieza y sugerencias ──
    @Test fun limpiarDatosQuitaFilasAMedioLlenar() {
        val d = limpiarDatos(EvaluacionFisioDatos(
            eva = 12, zonas = mapOf("lumbar" to 2, "inventada" to 3), localizacion = "  ",
            rangos = listOf(RangoMedido("rodilla", "flex", "D", null)),
            fuerza = listOf(FuerzaMedida("Cuádriceps", null, null)),
            tests = listOf(TestAplicado("ndi", listOf(null, null), null)),
        ))
        assertEquals(EvaluacionFisioDatos(eva = 10, zonas = mapOf("lumbar" to 2)), d)
        assertFalse(tieneDatos(EvaluacionFisioDatos(rangos = listOf(RangoMedido("rodilla", "flex", "D", null)))))
        assertTrue(tieneDatos(EvaluacionFisioDatos(eva = 0)))
    }

    @Test fun regionPorDiagnosticoYPorMapa() {
        assertEquals(listOf("lumbar"), regionesProbables("Lumbalgia mecánica con ciática derecha"))
        assertEquals(listOf("rodilla", "hombro"), regionesProbables("Post-op LCA", EvaluacionFisioDatos(zonas = mapOf("hombro_d" to 1))))
    }

    @Test fun sugiereObjetivosDesdeLoMedidoPrimero() {
        val s = sugerirObjetivos(listOf("rodilla"), EvaluacionFisioDatos(eva = 7, rangos = listOf(RangoMedido("rodilla", "flex", "D", 90))))
        assertEquals(ObjetivoBorrador("Flexión rodilla D", "°", 90.0, 122.0), s[0])
        assertEquals("Dolor (EVA)", s[1].texto); assertEquals(7.0, s[1].valorInicial); assertEquals(2.0, s[1].meta)
        assertTrue(s.any { it.texto == "Subir y bajar escaleras sin dolor" })
        assertFalse(sugerirObjetivos(listOf("rodilla"), null, listOf("Subir y bajar escaleras sin dolor"))
            .any { it.texto == "Subir y bajar escaleras sin dolor" })
    }

    @Test fun mapaZonasUnicasYTextoPorIntensidad() {
        val ids = ZONAS_CORPORALES.map { it.id }
        assertEquals(ids.size, ids.toSet().size)
        assertEquals(39, ids.size)
        assertEquals("Lumbar (severo), Rodilla D (moderado)", textoZonas(mapOf("rodilla_d" to 2, "lumbar" to 3)))
    }

    @Test fun tocarZonaCiclaLeveModeradoSeveroNada() {
        var z = emptyMap<String, Int>()
        z = alternarZona(z, "lumbar"); assertEquals(1, z["lumbar"])
        z = alternarZona(z, "lumbar"); assertEquals(2, z["lumbar"])
        z = alternarZona(z, "lumbar"); assertEquals(3, z["lumbar"])
        z = alternarZona(z, "lumbar"); assertNull(z["lumbar"])
    }

    @Test fun jsonIdaYVueltaConLaFormaDeLaWeb() {
        val d = EvaluacionFisioDatos(
            eva = 7, zonas = mapOf("lumbar" to 3), localizacion = "irradia",
            rangos = listOf(RangoMedido("rodilla", "flex", "D", 90), RangoMedido("lumbar", "flex", null, null)),
            fuerza = listOf(FuerzaMedida("Cuádriceps", "D", 3)),
            pruebas = listOf(PruebaEspecial("Lasègue", null, "+")),
            tests = listOf(TestAplicado("oswestry", listOf(2, 1, null), null)),
        )
        val j = datosAJson(d)
        // Se guarda limpio: el rango sin grados no viaja.
        assertEquals(1, (j["rangos"] as kotlinx.serialization.json.JsonArray).size)
        assertEquals(limpiarDatos(d), datosDeJson(j))
        // Una fila escrita por la web (lado null, puntaje decimal) se lee igual.
        val web = kotlinx.serialization.json.Json.parseToJsonElement(
            """{"eva":4,"tests":[{"id":"oswestry","respuestas":[2,1,2,3,2,2,1,null,2,1],"puntaje":35.6}],"pruebas":[{"nombre":"Neer","lado":null,"resultado":"-"}]}""",
        )
        val leido = datosDeJson(web)
        assertEquals(35.6, leido.tests[0].puntaje)
        assertNull(leido.pruebas[0].lado)
        assertEquals("-", leido.pruebas[0].resultado)
    }

    // ── Aislamiento por especialidad ──
    @Test fun pacienteEsFisioSoloFisioSiempre() {
        assertTrue(pacienteEsFisio(MapaFisio(solo = true)))
    }

    @Test fun pacienteEsFisioSoloDentalNunca() {
        assertFalse(pacienteEsFisio(MapaFisio(), especialidadesDeQuienMira = listOf("o")))
    }

    @Test fun pacienteEsFisioClinicaMixta() {
        val m = MapaFisio(ids = listOf("f"))
        assertFalse(pacienteEsFisio(m, listOf(AtencionFisio(especialidadId = "o"))))
        assertTrue(pacienteEsFisio(m, listOf(AtencionFisio(especialidadId = "o"), AtencionFisio(especialidadServicioId = "f"))))
        assertFalse(pacienteEsFisio(m, listOf(AtencionFisio())))
        assertTrue(pacienteEsFisio(m, especialidadesDeQuienMira = listOf("f")))
        assertTrue(pacienteEsFisio(m, tieneEvaluaciones = true))
    }
}
