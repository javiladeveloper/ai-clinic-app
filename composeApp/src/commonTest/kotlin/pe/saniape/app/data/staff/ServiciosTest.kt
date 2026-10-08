package pe.saniape.app.data.staff

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ServiciosTest {

    private val fisio = EspecialidadServicio("e-fisio", "Fisioterapia", usaSesiones = true, createdAt = "2026-01-01T00:00:00Z")
    private val consulta = EspecialidadServicio("e-med", "Medicina", usaSesiones = false, createdAt = "2026-03-01T00:00:00Z")
    private val psico = EspecialidadServicio("e-psi", "Psicología clínica", createdAt = "2026-05-01T00:00:00Z")
    private val activas = listOf(consulta, fisio)

    private fun obj(s: String): JsonObject = Json.parseToJsonElement(s).jsonObject

    @Test
    fun parseaUnaFilaConPaquetesYEspecialidad() {
        val s = parsearServicio(obj("""
            {"id":"p1","nombre":"Terapia","precio":"80.00","modo_cobro":null,"estado":"Activo",
             "especialidad_id":"e-fisio","controles_dias":[7,30],"precio_por_caras":{"1":50,"3":90},
             "sesiones_auto":true,"sesiones_intervalo_dias":15,"tipo_clinico":"evaluacion_psicologica",
             "tarifarios":[{"id":"t2","cantidad_sesiones":10,"precio_total":600},{"id":"t1","cantidad_sesiones":5,"precio_total":350.5}],
             "especialidad":{"id":"e-fisio","nombre":"Fisioterapia","color":"#16a34a","icono":"🏃","usa_sesiones":true}}
        """.trimIndent()))!!
        assertEquals(80.0, s.precio)
        assertEquals(listOf(7, 30), s.controlesDias)
        assertEquals(mapOf("1" to 50.0, "3" to 90.0), s.precioPorCaras)
        assertEquals(listOf(5, 10), s.tarifarios.map { it.cantidadSesiones })   // ordenados
        assertEquals(350.5, s.tarifarios.first().precioTotal)
        assertTrue(s.esEvaluacionPsico)
        assertEquals(true, s.especialidad?.usaSesiones)
        assertNull(s.modoCobro)
    }

    @Test
    fun filaSinIdSeDescarta() = assertNull(parsearServicio(obj("""{"nombre":"x"}""")))

    @Test
    fun modoYSufijoComoLaWeb() {
        assertEquals("sesiones", modoCobroEfectivo(null, null))
        assertEquals("simple", modoCobroEfectivo(null, false))
        assertEquals("unidades", modoCobroEfectivo("unidades", false))
        val base = ServicioApp("1", "X", precio = 10.0)
        assertEquals("/ sesión", sufijoPrecio(base))
        assertEquals("/ folículos", sufijoPrecio(base.copy(modoCobro = "unidades", unidadLabel = " folículos ")))
        assertEquals("/ unidad", sufijoPrecio(base.copy(modoCobro = "unidades")))
        assertEquals("", sufijoPrecio(base.copy(especialidad = consulta)))
    }

    @Test
    fun tramosDeCarasSoloLosPositivos() {
        val s = ServicioApp("1", "Resina", precioPorCaras = mapOf("3" to 90.0, "1" to 50.0, "2" to 0.0))
        assertEquals(listOf("1" to 50.0, "3" to 90.0), tramosCaras(s))
    }

    @Test
    fun etiquetasDeControl() {
        assertEquals("1 semana", etiquetaControl(7))
        assertEquals("2 semanas", etiquetaControl(14))
        assertEquals("1 mes", etiquetaControl(30))
        assertEquals("6 meses", etiquetaControl(180))
        assertEquals("1 año", etiquetaControl(365))
        assertEquals("1 día", etiquetaControl(1))
        assertEquals("45 días", etiquetaControl(45))
    }

    @Test
    fun filtroPorDefectoSoloActivos() {
        val lista = listOf(
            ServicioApp("a", "Masaje", descripcion = "relajante", estado = "Activo", especialidadId = "e-fisio"),
            ServicioApp("b", "Electro", estado = "Inactivo", especialidadId = "e-fisio"),
            ServicioApp("c", "Consulta", estado = "Activo", especialidadId = "e-med"),
        )
        assertEquals(listOf("a", "c"), filtrarServicios(lista, "", "", null).map { it.id })
        assertEquals(listOf("b"), filtrarServicios(lista, "", "Inactivo", null).map { it.id })
        assertEquals(3, filtrarServicios(lista, "", "todos", null).size)
        assertEquals(listOf("a"), filtrarServicios(lista, "RELAJ", "", null).map { it.id })   // también la descripción
        assertEquals(listOf("c"), filtrarServicios(lista, "", "", "e-med").map { it.id })
    }

    @Test
    fun agrupaConLosSinEspecialidadAlFinal() {
        val lista = listOf(
            ServicioApp("x", "Suelto"),
            ServicioApp("a", "Masaje", especialidadId = "e-fisio"),
            ServicioApp("c", "Consulta", especialidadId = "e-med"),
            ServicioApp("b", "Electro", especialidadId = "e-fisio"),
        )
        val g = agruparPorEspecialidad(lista, activas)
        assertEquals(listOf("e-fisio", "e-med", null), g.map { it.especialidad?.id })
        assertEquals(listOf("e-fisio", "e-med", ""), g.map { it.clave })
        assertEquals(listOf("a", "b"), g[0].servicios.map { it.id })
        assertEquals(listOf("x"), g[2].servicios.map { it.id })
    }

    @Test
    fun especialidadQueNoSeVeNoChocaConLosSinEspecialidad() {
        // Una especialidad que ya no aparece en la lista: el grupo conserva su clave propia.
        val g = agruparPorEspecialidad(listOf(ServicioApp("a", "A", especialidadId = "e-borrada"), ServicioApp("b", "B")), activas)
        assertEquals(listOf("e-borrada", ""), g.map { it.clave })
        assertEquals(2, g.map { it.clave }.toSet().size)
    }

    @Test
    fun especialidadInicialEsLaPrimeraCreada() = assertEquals("e-fisio", especialidadInicial(activas)?.id)

    @Test
    fun psicologiaPorRubroONombrePeroNoPsicomotricidad() {
        assertTrue(esEspecialidadPsico(psico))
        assertTrue(esEspecialidadPsico(EspecialidadServicio("1", "Terapia", rubro = "psicologia")))
        assertFalse(esEspecialidadPsico(EspecialidadServicio("2", "Psicomotricidad")))
    }

    @Test
    fun formDesdeServicioYVuelta() {
        val s = ServicioApp(
            "p1", "Terapia", precio = 80.0, especialidadId = "e-fisio", estado = "Activo",
            tarifarios = listOf(TarifaServicio(10, 600.0)), controlesDias = listOf(7, 30),
            sesionesAuto = true, sesionesIntervaloDias = 10,
        )
        val f = FormServicio.desde(s, "e-med")
        assertEquals("80", f.precio)
        assertEquals(listOf("10" to "600"), f.tarifarios)
        assertEquals("10", f.serieIntervalo)
        // Nuevo: nace con la especialidad por defecto.
        assertEquals("e-med", FormServicio.desde(null, "e-med").especialidadId)
    }

    @Test
    fun cuerpoPorSesionesConPaquetesPasosYSerie() {
        val f = FormServicio(
            nombre = "  Terapia  ", descripcion = "", especialidadId = "e-fisio", precio = "80",
            tarifarios = listOf("10" to "600", "" to "100", "5" to ""), controlesDias = listOf(30, 7),
            pasos = listOf(PasoServicioForm("p2", "", ""), PasoServicioForm("", "7", ""), PasoServicioForm("p3", "15", "40")),
            serieAuto = true, serieIntervalo = "",
        )
        val c = cuerpoGuardarServicio(f, activas, idEditado = null, tipoClinicoInicial = null, mostrarTipoClinico = false, esDental = false)
        val s = c["servicio"]!!.jsonObject
        assertNull(c["id"])
        assertEquals(JsonPrimitive("Terapia"), s["nombre"])
        assertEquals(JsonNull, s["descripcion"])
        assertEquals(JsonPrimitive("General"), s["categoria"])
        assertEquals(JsonNull, s["categoria_libre"])                             // vacío → null (borra)
        assertEquals(JsonNull, s["modo_cobro"])                                  // heredado
        assertEquals(JsonPrimitive(true), s["sesiones_auto"])
        assertEquals(JsonPrimitive(15), s["sesiones_intervalo_dias"])            // vacío → 15
        assertEquals("[7,30]", s["controles_dias"].toString())
        assertNull(s["tipo_clinico"])                                            // no viaja
        assertEquals("""[{"cantidad_sesiones":10,"precio_total":600.0}]""", c["tarifarios"].toString())
        val pasos = c["pasos"] as JsonArray
        assertEquals(2, pasos.size)                                               // el vacío no viaja
        assertEquals("""{"paso_procedimiento_id":"p2","dias_habiles":7,"precio":null,"orden":0}""", pasos[0].toString())
        assertEquals("""{"paso_procedimiento_id":"p3","dias_habiles":15,"precio":40.0,"orden":1}""", pasos[1].toString())
    }

    @Test
    fun cuerpoPagoUnicoSinPaquetesNiSerieYConCarasDental() {
        val f = FormServicio(
            nombre = "Resina", especialidadId = "e-med", precio = "90", tarifarios = listOf("5" to "300"),
            serieAuto = true, precioCaras = mapOf("1" to "50", "2" to "", "3" to "0"),
        )
        val c = cuerpoGuardarServicio(f, activas, "p9", null, mostrarTipoClinico = false, esDental = true)
        val s = c["servicio"]!!.jsonObject
        assertEquals(JsonPrimitive("p9"), c["id"])
        assertEquals("[]", c["tarifarios"].toString())                           // solo en modo sesiones
        assertEquals(JsonPrimitive(false), s["sesiones_auto"])
        assertEquals(JsonNull, s["sesiones_intervalo_dias"])
        assertEquals("""{"1":50.0}""", s["precio_por_caras"].toString())
        // No dental → null.
        val nd = cuerpoGuardarServicio(f, activas, "p9", null, mostrarTipoClinico = false, esDental = false)
        assertEquals(JsonNull, nd["servicio"]!!.jsonObject["precio_por_caras"])
    }

    @Test
    fun cuerpoPorUnidades() {
        val f = FormServicio(nombre = "Injerto", precio = "5000", modoCobro = "unidades", unidadLabel = " folículos ", precioUnitario = "1.5")
        val s = cuerpoGuardarServicio(f, activas, null, null, false, false)["servicio"]!!.jsonObject
        assertEquals(JsonPrimitive("unidades"), s["modo_cobro"])
        assertEquals(JsonPrimitive("folículos"), s["unidad_label"])
        assertEquals(JsonPrimitive(1.5), s["precio_unitario_sugerido"])
        // Sin elegir especialidad → la inicial.
        assertEquals(JsonPrimitive("e-fisio"), s["especialidad_id"])
    }

    @Test
    fun evaluacionPsicologicaUnSoloPaqueteYDesmarcar() {
        val f = FormServicio(
            nombre = "Evaluación", precio = "800", modoCobro = "sesiones", evalPsico = true, citasEstimadas = "",
            tarifarios = listOf("10" to "100"), devolucion = "adicional", devolucionProcId = "p-dev",
        )
        val c = cuerpoGuardarServicio(f, activas + psico, null, null, mostrarTipoClinico = true, esDental = false)
        assertEquals("""[{"cantidad_sesiones":1,"precio_total":800.0}]""", c["tarifarios"].toString())
        val s = c["servicio"]!!.jsonObject
        assertEquals(JsonPrimitive(TIPO_CLINICO_EVALUACION_PSICO), s["tipo_clinico"])
        assertEquals(JsonPrimitive("p-dev"), s["devolucion_procedimiento_id"])
        // Desmarcar una que ya lo era → todo a null.
        val d = cuerpoGuardarServicio(f.copy(evalPsico = false), activas + psico, "p1", TIPO_CLINICO_EVALUACION_PSICO, true, false)["servicio"]!!.jsonObject
        assertEquals(JsonNull, d["tipo_clinico"])
        assertEquals(JsonNull, d["devolucion"])
    }

    @Test
    fun pasosLeidosDeLaBase() {
        val p = parsearPasos(listOf(
            obj("""{"paso_procedimiento_id":"p2","dias_habiles":7,"precio":null}"""),
            obj("""{"paso_procedimiento_id":"p3","dias_habiles":15,"precio":"40.00"}"""),
            obj("""{"paso_procedimiento_id":"p4","dias_habiles":3,"precio":0}"""),
        ))
        assertEquals(listOf(PasoServicioForm("p2", "7", ""), PasoServicioForm("p3", "15", "40"), PasoServicioForm("p4", "3", "")), p)
    }

    @Test
    fun resumenDeTipicos() = assertEquals(
        "3 servicio(s) nuevo(s), 2 ya existían; 1 consentimiento(s) asociado(s).",
        resumenTipicos(obj("""{"ok":true,"creados":3,"existentes":2,"vinculados":1}""")),
    )

    @Test
    fun pasosSinLeerNoViajan() {
        val f = FormServicio(nombre = "X", precio = "10", pasos = emptyList())
        val c = cuerpoGuardarServicio(f, activas, "p1", null, false, false, incluirPasos = false)
        assertNull(c["pasos"])                                                   // ausente = no se tocan
        assertEquals("[]", cuerpoGuardarServicio(f, activas, "p1", null, false, false)["pasos"].toString())
    }

    @Test
    fun diasEnCeroUsanLosDefaultsDeLaWeb() {
        val f = FormServicio(
            nombre = "X", precio = "10", especialidadId = "e-fisio", serieAuto = true, serieIntervalo = "0",
            pasos = listOf(PasoServicioForm("p2", "0", "")),
        )
        val c = cuerpoGuardarServicio(f, activas, null, null, false, false)
        assertEquals(JsonPrimitive(15), c["servicio"]!!.jsonObject["sesiones_intervalo_dias"])
        assertEquals(JsonPrimitive(7), (c["pasos"] as JsonArray)[0].jsonObject["dias_habiles"])
    }

    @Test
    fun precioQueNoSeEntiendeNoSeGuardaComoCero() {
        val f = FormServicio(nombre = "X", precio = "1.2.3", especialidadId = "e-fisio")
        assertEquals("El precio no es válido", problemaFormServicio(f, activas, false, false))
        assertEquals(JsonNull, cuerpoGuardarServicio(f, activas, null, null, false, false)["servicio"]!!.jsonObject["precio"])
        assertEquals("El precio no es válido", problemaFormServicio(f.copy(precio = "."), activas, false, false))
        assertNull(problemaFormServicio(f.copy(precio = "80"), activas, false, false))
    }

    @Test
    fun paqueteMalEscritoSeAvisaEnVezDeDescartarse() {
        val f = FormServicio(nombre = "X", precio = "80", especialidadId = "e-fisio", tarifarios = listOf("5" to "3.5.0"))
        assertTrue(problemaFormServicio(f, activas, false, false)!!.startsWith("Revisa los paquetes"))
        assertTrue(problemaFormServicio(f.copy(tarifarios = listOf("" to "300")), activas, false, false) != null)
        assertTrue(problemaFormServicio(f.copy(tarifarios = listOf("5" to "1000000")), activas, false, false) != null)
        assertNull(problemaFormServicio(f.copy(tarifarios = listOf("5" to "300", "" to "")), activas, false, false))
        // En pago único los paquetes no viajan: no bloquean.
        assertNull(problemaFormServicio(f.copy(especialidadId = "e-med"), activas, false, false))
    }

    @Test
    fun otrosPreciosMalEscritos() {
        val base = FormServicio(nombre = "X", precio = "80", especialidadId = "e-med")
        assertTrue(problemaFormServicio(base.copy(modoCobro = "unidades", precioUnitario = ".."), activas, false, false) != null)
        assertTrue(problemaFormServicio(base.copy(precioCaras = mapOf("1" to "x.")), activas, false, esDental = true) != null)
        assertNull(problemaFormServicio(base.copy(precioCaras = mapOf("1" to "x.")), activas, false, esDental = false))
        assertTrue(problemaFormServicio(base.copy(pasos = listOf(PasoServicioForm("p2", "7", "4..0"))), activas, false, false) != null)
    }
}
