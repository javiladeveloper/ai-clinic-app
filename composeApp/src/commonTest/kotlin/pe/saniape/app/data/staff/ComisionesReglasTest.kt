package pe.saniape.app.data.staff

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ComisionesReglasTest {

    private val R = ReglasComisiones

    private fun avance(
        aCobrar: Double? = 300.0, pagado: PagoComisionRef? = null, liquidacion: String? = "aprobar",
        terapeutaId: String? = "t1", perfilId: String? = null,
    ) = AvanceComision(terapeutaId = terapeutaId, perfilId = perfilId, nombre = "Rodas", logrado = 10.0,
        nivelActual = "Oro", aCobrar = aCobrar, pagado = pagado, liquidacion = liquidacion)

    private fun esquema(tipo: String = "tramos", avances: List<AvanceComision> = emptyList(), desde: String = "2026-09-01", hasta: String = "2026-10-01") =
        EsquemaComision(id = "pl1", nombre = "Pirámide 10", tipo = tipo, etiquetaRango = "Paquetes de 10 sesiones",
            desde = desde, hasta = hasta, avances = avances,
            tramos = listOf(TramoComision("Plata", 5.0, 100.0), TramoComision("Oro", 10.0, 300.0)))

    @Test
    fun mesesElegiblesCruzaElAnio() {
        val o = R.mesesElegibles("2026-01-15")
        assertEquals(listOf(null, "2025-12", "2025-11"), o.map { it.valor })
        assertEquals("Enero 2026 (en curso)", o[0].etiqueta)
        assertEquals("Diciembre 2025", o[1].etiqueta)
    }

    @Test
    fun rangoHumanoUsaHastaExcluido() {
        assertEquals("1 – 30 de septiembre", R.rangoHumano("2026-09-01", "2026-10-01"))
        assertEquals("1 de julio – 30 de septiembre", R.rangoHumano("2026-07-01", "2026-10-01"))
        assertEquals("1 de diciembre de 2025 – 31 de enero de 2026", R.rangoHumano("2025-12-01", "2026-02-01"))
        assertEquals("2024-02-29", R.diaAnterior("2024-03-01"))
    }

    @Test
    fun aQuienSaleSolaNuncaSeLePagaDesdeAca() {
        assertTrue(R.puedePagar(avance()))
        assertFalse(R.puedePagar(avance(liquidacion = "egreso")))
        assertFalse(R.puedePagar(avance(pagado = PagoComisionRef("p1", 300.0, "2026-10-01"))))
        assertFalse(R.puedePagar(avance(aCobrar = 0.0)))
        // El profesional no recibe montos: nunca hay botón.
        assertFalse(R.puedePagar(avance(aCobrar = null)))
    }

    @Test
    fun totalAPagarNoSumaLoYaPagado() {
        val lista = listOf(esquema(avances = listOf(
            avance(300.0), avance(100.0, pagado = PagoComisionRef("x", 100.0, "")), avance(50.0),
            // A quien le sale sola al cobrar no se le paga desde acá: no suma.
            avance(80.0, liquidacion = "egreso"),
        )))
        assertEquals(350.0, R.totalAPagar(lista))
    }

    @Test
    fun textoAnularDistingueElCorteEnCero() {
        assertEquals("Deshacer el corte (al 2026-09-15)", R.textoAnular(UltimoPagoComision("p", 0.0, "2026-09-15", "2026-09-15")))
        assertTrue(R.textoAnular(UltimoPagoComision("p", 300.0, "2026-10-01", null)).startsWith("Anular el pago de S/ 300.00"))
    }

    @Test
    fun piramideLlenaPorTramo() {
        val s = R.segmentosPiramide(esquema().tramos, 7.0)
        assertEquals(2, s.size)
        assertEquals(1f, s[0].llenado); assertTrue(s[0].alcanzado)
        assertEquals(0.4f, s[1].llenado, 0.001f); assertFalse(s[1].alcanzado)
        assertEquals(0.5f, s[0].peso, 0.001f)
        assertTrue(R.segmentosPiramide(emptyList(), 3.0).isEmpty())
    }

    @Test
    fun validarConLosMismosMensajesQueLaWeb() {
        assertEquals("Ponle un nombre al esquema", R.validar(FormEsquema()) { null })
        assertEquals("Completa al menos un nivel", R.validar(FormEsquema(nombre = "X")) { null })
        assertNull(R.validar(FormEsquema(nombre = "X", tramos = listOf(TramoForm("Oro", "10", "300")))) { null })
        assertEquals("Pon el porcentaje del esquema (entre 0 y 100)", R.validar(FormEsquema(nombre = "X", tipo = "porcentaje", porcentaje = "120")) { null })
        val f = FormEsquema(nombre = "Masajistas", tipo = "porcentaje", porcentaje = "40", asignados = listOf("t1"),
            ajustes = mapOf("t1" to AjusteForm(porcentaje = "150")))
        assertEquals("El porcentaje de Yannet debe estar entre 0 y 100", R.validar(f) { "Yannet" })
    }

    @Test
    fun cuerpoGuardarIgualAlDeLaWeb() {
        val f = FormEsquema(
            nombre = " Pirámide 10 ", minSes = "10", maxSes = "", montoPaq = "400", soloNuevos = true, periodoMeses = "1",
            tramos = listOf(TramoForm("Plata", "5", "100"), TramoForm("Oro", "10", "300"), TramoForm("Diamante", "", "")),
            asignados = listOf("t1"), ajustes = mapOf("t1" to AjusteForm(porcentaje = "", liquidacion = "egreso", metodoPago = "Yape")),
        )
        val b = R.cuerpoGuardar(f, "0f8e3a4b-1c2d-4e5f-8a9b-0c1d2e3f4a5b")
        assertEquals("0f8e3a4b-1c2d-4e5f-8a9b-0c1d2e3f4a5b", (b["idNuevo"] as JsonPrimitive).content)
        assertNull(b["id"])
        assertEquals("Pirámide 10", (b["nombre"] as JsonPrimitive).content)
        assertEquals("paquetes", (b["unidad"] as JsonPrimitive).content)
        assertEquals(JsonNull, b["maxSesiones"])
        assertEquals(2, (b["tramos"] as JsonArray).size)   // el nivel vacío no viaja
        assertEquals(JsonNull, b["porcentaje"])
        val aj = (b["ajustes"] as JsonObject)["t1"] as JsonObject
        assertEquals("egreso", (aj["liquidacion"] as JsonPrimitive).content)
        assertEquals("Yape", (aj["metodoPago"] as JsonPrimitive).content)
        // Editar: manda el id, no idNuevo.
        val e = R.cuerpoGuardar(f.copy(id = "pl1"), "otro")
        assertEquals("pl1", (e["id"] as JsonPrimitive).content)
        assertNull(e["idNuevo"])
    }

    @Test
    fun porEvaluacionesNoMandaRangoNiMontoYSiElEquipo() {
        val f = FormEsquema(nombre = "Bono recepción", unidad = "evaluaciones", minSes = "10", montoPaq = "400",
            soloNuevos = true, asignadosEquipo = listOf("perfil1"), tramos = listOf(TramoForm("Bronce", "30", "50")))
        val b = R.cuerpoGuardar(f, null)
        assertEquals("evaluaciones", (b["unidad"] as JsonPrimitive).content)
        assertEquals(JsonNull, b["minSesiones"])
        assertEquals(JsonNull, b["montoPaquete"])
        assertEquals("false", (b["soloPacientesNuevos"] as JsonPrimitive).content)
        assertEquals(1, (b["perfilIds"] as JsonArray).size)
    }

    @Test
    fun formDesdeEsquemaConservaLoPactado() {
        val p = esquema(tipo = "porcentaje", avances = listOf(
            AvanceComision(terapeutaId = "t1", porcentaje = 20.0, liquidacion = "egreso", metodoPago = "Plin"),
            AvanceComision(perfilId = "pf1"),
        )).copy(porcentaje = 40.0, tramos = emptyList())
        val f = FormEsquema.desde(p)
        assertEquals("porcentaje", f.tipo)
        assertEquals("40", f.porcentaje)
        assertEquals(listOf("t1"), f.asignados)
        assertEquals(listOf("pf1"), f.asignadosEquipo)
        assertEquals(AjusteForm("20", "egreso", "Plin"), f.ajusteDe("t1"))
        assertEquals(R.NIVELES_SUGERIDOS, f.tramos.map { it.nombre })
    }

    @Test
    fun historicoRangosYOpciones() {
        assertNull(R.rangoHistorico("todo"))
        assertEquals("2026-12-01" to "2027-01-01", R.rangoHistorico("2026-12"))
        assertEquals("2026-01-01" to "2027-01-01", R.rangoHistorico("2026"))
        val o = R.opcionesHistorico("2026-10-08")
        assertEquals("todo", o.first().valor)
        assertEquals("2026-10", o[1].valor)
        assertEquals("Octubre 2026", o[1].etiqueta)
        assertEquals(listOf("2026", "2025"), o.takeLast(2).map { it.valor })
    }

    @Test
    fun csvHistoricoConComillasYOrigen() {
        val csv = R.csvHistorico(listOf(
            FilaHistoricoComision(fecha = "2026-09-15", profesional = "Rodas, Ana", esquema = "Pirámide", nivel = "Oro", monto = 300.0),
            FilaHistoricoComision(origen = "caja", fecha = "2026-09-16", profesional = "Luis", esquema = "Registrado en caja", monto = 12.5),
        ))
        val lineas = csv.trim().lines()
        assertEquals("Fecha,Profesional,Esquema,Nivel,Período,Origen,Monto", lineas[0])
        assertEquals("2026-09-15,\"Rodas, Ana\",Pirámide,Oro,,Liquidado en Sania,300.00", lineas[1])
        assertEquals("2026-09-16,Luis,Registrado en caja,,,Registrado en caja,12.50", lineas[2])
    }

    @Test
    fun parseaLaRespuestaDelAdminYDelProfesional() {
        val admin = """{"plantillas":[{"id":"pl1","nombre":"P","tipo":"tramos","unidad":"paquetes","minSesiones":10,"maxSesiones":null,
            "soloPacientesNuevos":false,"montoPaquete":null,"etiquetaRango":"Paquetes de 10","periodoMeses":1,"porcentaje":null,
            "desde":"2026-10-01","hasta":"2026-11-01","tramos":[{"nombre":"Oro","objetivo":10,"monto_bono":300}],
            "avances":[{"terapeutaId":"t1","perfilId":null,"clave":"t1","nombre":"Rodas","logrado":10,"paquetesQueCuentan":10,
            "nivelActual":"Oro","siguienteNivel":null,"faltanParaSiguiente":0,"progreso":100,"texto":"Oro","porcentaje":null,
            "liquidacion":"aprobar","metodoPago":"Efectivo","aCobrar":300,"pagado":null,"ultimoPago":null}]}],
            "esAdmin":true,"cuentaConPagoParcial":false,"periodoRef":"2026-10","esPeriodoActual":true}"""
        val d = assertNotNull(ComisionesRepo.parsearEsquemas(admin))
        assertTrue(d.esAdmin)
        assertEquals(300.0, d.plantillas[0].avances[0].aCobrar)
        assertEquals("2026-10", d.periodoRef)
        // Al profesional no le llegan montos ni el bono de los tramos.
        val prof = """{"plantillas":[{"id":"pl1","nombre":"P","tipo":"tramos","unidad":"paquetes","etiquetaRango":"x","periodoMeses":1,
            "desde":"2026-10-01","hasta":"2026-11-01","tramos":[{"nombre":"Oro","objetivo":10}],
            "avances":[{"terapeutaId":"t1","nombre":"Yo","logrado":3,"paquetesQueCuentan":3,"nivelActual":null,"siguienteNivel":"Oro",
            "faltanParaSiguiente":7,"progreso":30,"texto":"","liquidacion":"aprobar","metodoPago":"Efectivo"}]}],"esAdmin":false}"""
        val p = assertNotNull(ComisionesRepo.parsearEsquemas(prof))
        assertFalse(p.esAdmin)
        assertNull(p.plantillas[0].avances[0].aCobrar)
        assertNull(p.plantillas[0].tramos[0].monto_bono)
        assertFalse(R.puedePagar(p.plantillas[0].avances[0]))
    }

    @Test
    fun cuerpoPagarMandaElPeriodoQueSeEstaViendo() {
        val b = ComisionesRepo.cuerpoPagar("pl1", avance(terapeutaId = null, perfilId = "pf1"), "Yape", "2026-09")
        assertEquals("pf1", (b["perfilId"] as JsonPrimitive).content)
        assertEquals(JsonNull, b["terapeutaId"])
        assertEquals("2026-09", (b["periodo"] as JsonPrimitive).content)
    }
}
