package pe.saniape.app.data.staff

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Gemelo de lib/__tests__/campania-entrada.test.ts (web, 2026-10-16). */
class CampaniaEntradaTest {

    private val DALU = FlujoClinica(usaConsulta = true, usaEvaluacion = true, labelConsulta = "Consulta", labelEvaluacion = "Evaluación")
    private val DALU_BOT = DALU.copy(usaConsulta = false)
    private val ESTETICA = FlujoClinica(usaConsulta = true, usaEvaluacion = false, labelConsulta = "Valoración", labelEvaluacion = "Evaluación")
    private val SOLO_SESIONES = FlujoClinica(usaConsulta = false, usaEvaluacion = false)

    private val FISIO = "55555555-5555-4555-8555-555555555555"
    private val OTRO = "66666666-6666-4666-8666-666666666666"
    private val CAMP = "ce000000-0000-4000-8000-0000000000a1"
    private fun camp(
        tipo: String = "paquete_fijo", cantidad: Int? = 10, precio: Double? = 600.0, valor: Double? = null,
        alcance: String = "servicios", servicios: List<String> = listOf(FISIO), incluye: Boolean = true,
    ) = CampaniaApp(CAMP, "Paquete 10 sesiones a S/ 600", tipo, alcance, null, valor, precio, cantidad, servicios, incluye)

    @Test fun nombreDeLaCitaDeEntrada() {
        assertEquals("consulta o evaluación", nombreCitaEntrada(DALU))
        assertEquals("evaluación", nombreCitaEntrada(DALU_BOT))
        assertEquals("valoración", nombreCitaEntrada(ESTETICA))
        assertEquals("cita de entrada", nombreCitaEntrada(SOLO_SESIONES))
        assertEquals("cita de entrada", nombreCitaEntrada(null))
        assertEquals("la evaluación", conArticulo("evaluación"))
        assertEquals("el diagnóstico", conArticulo("diagnóstico"))
        assertEquals("la consulta o evaluación", conArticulo("consulta o evaluación"))
        val (titulo, detalle) = etiquetaIncluyeEntrada("evaluación")
        assertEquals("Incluye la evaluación", titulo)
        assertTrue("se descuenta del paquete" in detalle)
        assertTrue("se cobra normal" in detalle)
    }

    @Test fun laOpcionEsDeLaCampania() {
        assertTrue(admiteIncluyeEntrada("servicios"))
        assertTrue(admiteIncluyeEntrada("todos"))
        assertFalse(admiteIncluyeEntrada("citas"))
        assertFalse(campaniaIncluyeEntrada("citas", true))
        assertTrue(campaniaIncluyeEntrada("servicios", true))
        assertFalse(campaniaIncluyeEntrada("servicios", null))
        assertFalse(campaniaIncluyeEntrada("servicios", false))
        assertNull(campoIncluyeEntrada(false, "servicios", false))
        assertEquals(true, campoIncluyeEntrada(true, "servicios", false))
        assertEquals(false, campoIncluyeEntrada(false, "servicios", true))
        assertEquals(false, campoIncluyeEntrada(true, "citas", true))
        assertNull(campoIncluyeEntrada(true, "citas", false))
    }

    @Test fun elFormSoloMandaElCampoSiAplica() {
        val base = FormCampania(nombre = "Promo", tipo = "paquete_fijo", cantidad = "10", precio = "600", alcance = "todos", desde = "2026-10-16")
        assertFalse("incluye_entrada" in cuerpoGuardarCampania(base, null))
        assertEquals(JsonPrimitive(true), cuerpoGuardarCampania(base.copy(incluyeEntrada = true), null)["incluye_entrada"])
        assertEquals(JsonPrimitive(false), cuerpoGuardarCampania(base.copy(incluyeEntrada = false, incluyeEntradaInicial = true), "c1")["incluye_entrada"])
        // Leída de la base: la editamos sin tocarla → se conserva.
        val leida = parsearCampania(Json.parseToJsonElement("""{"id":"c1","nombre":"P","tipo":"paquete_fijo","alcance":"todos","incluye_entrada":true}""") as JsonObject)!!
        assertTrue(leida.incluyeEntrada)
        val f = FormCampania.desde(leida, "2026-10-17")
        assertEquals(JsonPrimitive(true), cuerpoGuardarCampania(f, "c1")["incluye_entrada"])
    }

    @Test fun cobroDespuesDeCrearElPlan() {
        val cita = "cita-1"
        fun t(estado: String = "Activo", origen: String? = cita, alcance: String? = "servicios", incluye: Boolean? = true) =
            TratamientoAbonoCampania("t1", estado, origen, "2026-10-16T10:00", alcance, incluye)
        assertTrue(debeAbonarPorCampania(listOf(t()), cita))
        assertFalse(debeAbonarPorCampania(listOf(t(incluye = false)), cita))
        assertFalse(debeAbonarPorCampania(listOf(t(alcance = null, incluye = null)), cita))
        assertFalse(debeAbonarPorCampania(listOf(t(origen = "otra")), cita))
        assertFalse(debeAbonarPorCampania(listOf(t(estado = "Cancelado")), cita))
        assertFalse(debeAbonarPorCampania(emptyList(), cita))
    }

    // ── Presupuestos con campaña ──

    private val servicio = ServicioPresupuesto(
        id = FISIO, nombre = "Fisioterapia", precio = 100.0, usaSesiones = true,
        tarifarios = listOf(TarifaPresupuesto(null, 5, 400.0), TarifaPresupuesto(null, 10, 800.0)),
    )
    private val sueltas = ItemPresupuesto(
        clave = "l1", procedimientoId = FISIO, nombre = "Fisioterapia", detalle = null, modo = ModoItemPresu.SESIONES,
        unidadLabel = null, cantidad = 1, precioUnitario = 100.0, precioLista = 100.0, cobro = CobroPresu.SUELTAS, precioSesion = 100.0,
    )

    @Test fun ofreceLaCampaniaDelServicio() {
        assertEquals(listOf(CAMP), campaniasParaItem(listOf(camp()), sueltas).map { it.id })
        assertTrue(campaniasParaItem(listOf(camp()), sueltas.copy(modo = ModoItemPresu.SIMPLE)).isEmpty())
        assertTrue(campaniasParaItem(listOf(camp()), sueltas.copy(procedimientoId = OTRO)).isEmpty())
        assertTrue(campaniasParaItem(listOf(camp(alcance = "citas")), sueltas).isEmpty())
    }

    @Test fun aplicarPaquetePromocional() {
        val it = itemConCampania(sueltas, camp(), servicio, otraMoneda = false, moneda = "PEN")
        assertEquals(CobroPresu.PAQUETE, it.cobro)
        assertEquals(10, it.cantidad)
        assertEquals(600.0, it.precioUnitario)
        assertEquals(800.0, it.precioLista)
        assertEquals(CAMP, it.campaniaId)
        assertEquals(600.0, precioCampaniaDeItem(it, camp(), "PEN"))
        assertNull(precioCampaniaDeItem(it.copy(cantidad = 5), camp(), "PEN"))
        assertNull(itemSinCampania(it).campaniaId)
        assertEquals(80.0, itemConCampania(sueltas, camp(tipo = "precio_fijo", precio = 80.0, cantidad = null), servicio, false, "PEN").precioUnitario)
        assertEquals(90.0, itemConCampania(sueltas, camp(tipo = "porcentaje", valor = 10.0, precio = null, cantidad = null), servicio, false, "PEN").precioUnitario)
    }

    @Test fun leerEscribirYPasarALibre() {
        val leidos = leerItemsPresupuesto(Json.parseToJsonElement(
            """[{"clave":"l1","procedimiento_id":"$FISIO","nombre":"F","modo":"sesiones","cantidad":10,"precio_unitario":600,"cobro":"paquete","campania_id":"$CAMP"},""" +
                """{"clave":"l2","procedimiento_id":"$FISIO","nombre":"F","modo":"simple","cantidad":1,"precio_unitario":100,"campania_id":"no-uuid"}]""",
        ))
        assertEquals(CAMP, leidos[0].campaniaId)
        assertNull(leidos[1].campaniaId)
        val json = itemsAJson(leidos)
        assertEquals(JsonPrimitive(CAMP), (json[0] as JsonObject)["campaniaId"])
        assertFalse("campaniaId" in (json[1] as JsonObject))
        val libre = conListaDelCatalogo(listOf(sueltas.copy(campaniaId = CAMP)), emptyList()).single()
        assertNull(libre.procedimientoId)
        assertNull(libre.campaniaId)
        assertTrue(json is JsonArray)
    }
}
