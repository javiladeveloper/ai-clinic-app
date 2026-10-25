package pe.saniape.app.data.staff

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Gemelo de lib/__tests__/plan-tras-evaluacion.test.ts y chips-cobro.test.ts (web): los mismos casos. */
class PlanTrasEvaluacionTest {

    private val evalFisio = ServicioDelPlan("eval", precio = 50.0, tipoCita = "Evaluación")
    private val fisio = ServicioDelPlan("fisio", precio = 60.0, tarifarios = listOf(5 to 280.0, 10 to 500.0))
    private val magneto = ServicioDelPlan("magneto", precio = 40.0, precioPaquete = 350.0)
    private val masaje = ServicioDelPlan("masaje", precio = 70.0)
    private val unico = ServicioDelPlan("una", precio = 120.0, modoCobro = "simple")

    // ── modoDeServicioPlan ──
    @Test
    fun modoDelServicioOElDeSuEspecialidad() {
        assertEquals("sesiones", modoDeServicioPlan(fisio))
        assertEquals("simple", modoDeServicioPlan(unico))
        assertEquals("unidades", modoDeServicioPlan(ServicioDelPlan("u", modoCobro = "unidades")))
        assertEquals("simple", modoDeServicioPlan(ServicioDelPlan("x", usaSesiones = false)))
        assertNull(modoDeServicioPlan(null))
    }

    // ── servicioSugerido ──
    private val usos = usosPorServicio(listOf("magneto", "fisio", "fisio", null))

    @Test
    fun cuentaLosUsos() {
        assertEquals(2, usos["fisio"]); assertEquals(1, usos["magneto"])
    }

    @Test
    fun elElegidoEnLaEvaluacionManda() {
        assertEquals("magneto", servicioSugerido(listOf(fisio, magneto), elegido = "magneto", usos = usos))
        assertEquals("fisio", servicioSugerido(listOf(fisio, magneto), elegido = "otro", usos = usos))
    }

    @Test
    fun unoSoloQuePuedaSerPlan() {
        assertEquals("masaje", servicioSugerido(listOf(evalFisio, masaje)))
        assertEquals("masaje", servicioSugerido(listOf(masaje, fisio), excluir = "fisio"))
    }

    @Test
    fun elMasUsadoYEmpateElPrimero() {
        assertEquals("fisio", servicioSugerido(listOf(magneto, fisio, masaje), usos = usos))
        assertEquals("magneto", servicioSugerido(listOf(magneto, masaje), usos = usosPorServicio(listOf("masaje", "magneto"))))
    }

    @Test
    fun sinHistoriaElUnicoPorSesionesOMinguno() {
        assertEquals("masaje", servicioSugerido(listOf(unico, masaje)))
        assertNull(servicioSugerido(listOf(fisio, masaje)))
        assertNull(servicioSugerido(emptyList()))
    }

    // ── planPrellenado + listaDelPlan ──
    @Test
    fun tarifarioPaqueteDe10() {
        assertEquals(PlanPrellenado(MODALIDAD_PAQUETE, 10), planPrellenado(fisio))
        assertEquals(PlanPrellenado(MODALIDAD_PAQUETE, 6), planPrellenado(ServicioDelPlan("x", tarifarios = listOf(6 to 300.0))))
        assertEquals(500.0, listaDelPlan(fisio, MODALIDAD_PAQUETE, 10))
        assertNull(listaDelPlan(fisio, MODALIDAD_PAQUETE, 12))
    }

    @Test
    fun precioDePaqueteYSuelta() {
        assertEquals(PlanPrellenado(MODALIDAD_PAQUETE, 10), planPrellenado(magneto))
        assertEquals(350.0, listaDelPlan(magneto, MODALIDAD_PAQUETE, 10))
        assertEquals(PlanPrellenado(MODALIDAD_SUELTA, 10), planPrellenado(masaje))
        assertEquals(70.0, listaDelPlan(masaje, MODALIDAD_SUELTA, 10))
    }

    // ── valoresDelPlan ──
    private fun ok(r: ResultadoValoresPlan) = (r as ResultadoValoresPlan.Ok).valores
    private fun valores(s: ServicioDelPlan, mod: String, n: String, precio: String, moneda: String? = null) =
        valoresDelPlan(s, mod, n, precio, "  Lumbalgia  ", "t1", "c1", "2026-10-24", moneda)

    @Test
    fun paqueteAPrecioDeLista() {
        val v = ok(valores(fisio, MODALIDAD_PAQUETE, "10", ""))
        assertEquals(500.0, v.total)
        assertEquals("fisio", v.procedimientoId); assertEquals("t1", v.terapeutaId); assertEquals("c1", v.citaOrigenId)
        assertEquals("Lumbalgia", v.diagnostico)
        assertEquals(MODALIDAD_PAQUETE, v.modalidad); assertEquals(10, v.totalSesiones)
        assertEquals(500.0, v.precioPaquete); assertEquals(60.0, v.precioPorSesion); assertNull(v.precioAcordado)
        assertEquals("2026-10-24", v.fechaInicio)
    }

    @Test
    fun paqueteNegociadoGuardaElAcordadoAparte() {
        val v = ok(valores(fisio, MODALIDAD_PAQUETE, "10", "450"))
        assertEquals(450.0, v.precioAcordado); assertEquals(500.0, v.precioPaquete); assertEquals(450.0, v.total)
        assertNull(ok(valores(fisio, MODALIDAD_PAQUETE, "10", "500.00")).precioAcordado)
    }

    @Test
    fun paqueteGratisConCeroEscrito() {
        val v = ok(valores(magneto, MODALIDAD_PAQUETE, "10", "0"))
        assertEquals(0.0, v.precioAcordado); assertEquals(0.0, v.total)
    }

    @Test
    fun paqueteSinListaPideElPrecio() {
        assertEquals(ResultadoValoresPlan.Error("Pon el precio del paquete. Si es gratis, escribe 0."),
            valores(masaje, MODALIDAD_PAQUETE, "8", ""))
        val v = ok(valores(masaje, MODALIDAD_PAQUETE, "8", "480"))
        assertEquals(480.0, v.precioPaquete); assertNull(v.precioAcordado); assertEquals(8, v.totalSesiones)
    }

    @Test
    fun sesionSueltaUnaSesionYSuPrecio() {
        val v = ok(valores(masaje, MODALIDAD_SUELTA, "10", ""))
        assertEquals(MODALIDAD_SUELTA, v.modalidad); assertEquals(1, v.totalSesiones)
        assertEquals(70.0, v.precioPorSesion); assertNull(v.precioPaquete); assertNull(v.precioAcordado)
        assertEquals(70.0, v.total)
        assertEquals(60.0, ok(valores(masaje, MODALIDAD_SUELTA, "10", "60")).precioPorSesion)
    }

    @Test
    fun rechazaLoQueElFormularioRechaza() {
        assertTrue(valores(fisio, MODALIDAD_PAQUETE, "0", "") is ResultadoValoresPlan.Error)
        assertTrue(valores(fisio, MODALIDAD_PAQUETE, "10", "-5") is ResultadoValoresPlan.Error)
        assertTrue(valores(fisio, MODALIDAD_PAQUETE, "10", "450.5", "CLP") is ResultadoValoresPlan.Error)
    }

    @Test
    fun sinDiagnosticoNiProfesionalNull() {
        val v = ok(valoresDelPlan(fisio, MODALIDAD_PAQUETE, "10", "", null, null, null, "2026-10-24"))
        assertNull(v.diagnostico); assertNull(v.terapeutaId); assertNull(v.citaOrigenId)
    }

    // ── abonoAlPlan ──
    @Test
    fun abonoNuncaMasDeLoQueFalta() {
        assertEquals(300.0, abonoAlPlan(300.0, 500.0))
        assertEquals(450.0, abonoAlPlan(500.0, 450.0))
        assertEquals(0.0, abonoAlPlan(500.0, 0.0))
        assertEquals(250.0, abonoAlPlan(250.0, null))
        assertEquals(0.0, abonoAlPlan(0.0, 500.0))
        assertEquals(0.0, abonoAlPlan(null, 500.0))
    }

    // ── desdePlanTrasAtencion ──
    @Test
    fun desdeElSiguienteDiaQueAtiende() {
        // 2026-10-24 es sábado; el domingo no atiende (horario por defecto) → lunes 26.
        assertEquals("2026-10-26", desdePlanTrasAtencion("2026-10-24", "2026-10-24", HORARIO_ATENCION_DEFAULT))
        // Atención pasada: se cuenta desde hoy.
        assertEquals("2026-10-23", desdePlanTrasAtencion("2026-10-22", "2026-10-20", HORARIO_ATENCION_DEFAULT))
        // Atención futura: desde ella.
        assertEquals("2026-10-29", desdePlanTrasAtencion("2026-10-22", "2026-10-28", HORARIO_ATENCION_DEFAULT))
    }

    // ── chipsCobro (gemelo de chips-cobro.test.ts) ──
    private fun montos(o: List<OpcionChipCobro>) = o.map { "${it.chip.name.lowercase()}:${textoMontoChip(it.monto)}" }

    @Test
    fun chipsNadaPagadoTodoY50() {
        assertEquals(listOf("todo:600", "mitad:300"), montos(chipsCobro(600.0, 600.0)))
        assertEquals(listOf("todo:600", "mitad:300"), montos(chipsCobro(600.0, null)))
    }

    @Test
    fun chipsAlgoPagadoSaldoYMitadSiCabe() {
        assertEquals(listOf("saldo:450", "mitad:300"), montos(chipsCobro(600.0, 450.0)))
        assertEquals(listOf("saldo:200"), montos(chipsCobro(600.0, 200.0)))
        assertEquals(listOf("saldo:300"), montos(chipsCobro(600.0, 300.0)))
    }

    @Test
    fun chipsDebeMasQueEsto() {
        assertEquals(listOf("todo:50", "saldo:150"), montos(chipsCobro(50.0, 150.0)))
    }

    @Test
    fun chipsSinTotalConSaldo() {
        assertEquals(listOf("saldo:90", "mitad:45"), montos(chipsCobro(null, 90.0)))
    }

    @Test
    fun chipsNadaQueCobrar() {
        assertTrue(chipsCobro(600.0, 0.0).isEmpty())
        assertTrue(chipsCobro(0.0, null).isEmpty())
        assertTrue(chipsCobro(null, null).isEmpty())
    }

    @Test
    fun chipsRedondeanALaMoneda() {
        assertEquals(listOf("todo:99.99", "mitad:50"), montos(chipsCobro(99.99, null)))
        assertEquals(listOf("todo:25001", "mitad:12501"), montos(chipsCobro(25001.0, null, "CLP")))
    }

    @Test
    fun chipActivoYEtiquetas() {
        val op = chipsCobro(600.0, null)
        assertEquals(ChipCobro.MITAD, chipActivo(op, "300"))
        assertEquals(ChipCobro.TODO, chipActivo(op, "600.00"))
        assertEquals(ChipCobro.TODO, chipActivo(op, "600,00"))
        assertNull(chipActivo(op, "280"))
        assertNull(chipActivo(op, ""))
        assertEquals(listOf("Todo", "50 %"), op.map { it.etiqueta })
        assertEquals(listOf("Saldo"), chipsCobro(100.0, 40.0).map { it.etiqueta })
        assertEquals("150.5", textoMontoChip(150.5))
    }
}
