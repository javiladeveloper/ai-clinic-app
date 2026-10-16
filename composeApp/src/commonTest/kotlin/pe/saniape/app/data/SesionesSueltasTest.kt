package pe.saniape.app.data

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import pe.saniape.app.data.staff.TratamientoPaciente
import pe.saniape.app.data.staff.pagosDevengoDe
import pe.saniape.app.data.staff.sesionesDevengoDe
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * SESIONES SUELTAS: el acordado suma lo atendido (Centro OMER, 2026-10-16).
 * Paridad EXACTA con lib/__tests__/sueltas-acordado.test.ts (web) y con los 31
 * casos de supabase/fixes/sueltas-prueba-gemelo.sql (SesionesSueltasFixture.kt).
 */
class SesionesSueltasTest {

    // La suelta de OMER tal como la guarda el formulario: total_sesiones = 1.
    private val omer = TratPago(modalidad = "Sesión suelta", precioPorSesion = 100.0, totalSesiones = 1, precioAcordado = null)
    private val paquete = TratPago(modalidad = "Paquete", precioPaquete = 400.0, totalSesiones = 5, precioPorSesion = 100.0)

    private fun ses(id: String, estado: String, costo: Double? = 100.0, numero: Int? = null, fecha: String? = null) =
        SesionDevengo(id, estado, costo, numero, fecha)
    private fun pago(monto: Double, sesionId: String? = null) = PagoDevengo(sesionId, monto)
    private fun ctx(sesiones: List<SesionDevengo>, pagos: List<PagoDevengo> = emptyList(), incluir: String? = null, nueva: SesionNueva? = null) =
        ContextoSesiones(sesiones, pagos, incluir, nueva)
    private fun saldo(t: TratPago, pagado: Double, c: ContextoSesiones?) = kotlin.math.round((montoAcordado(t, c) - pagado) * 100) / 100
    private fun saldado(t: TratPago, pagado: Double, c: ContextoSesiones?) = estadoPagoDe(pagado, montoAcordado(t, c)) == "Pagado"
    private val dinero = { n: Double -> "S/ " + (kotlin.math.round(n * 100) / 100).toString() }

    // ── Centro OMER: sesión 1 pagada, sesión 2 pagada → 200 de 200 ──

    @Test fun omer_sesion1_atendida_y_pagada_100_de_100() {
        val c = ctx(listOf(ses("s1", "Completada"), ses("s2", "Planificada")), listOf(pago(100.0)))
        assertEquals(100.0, montoAcordado(omer, c))
        assertEquals("Pagado", estadoPagoDe(100.0, montoAcordado(omer, c)))
    }

    @Test fun omer_sesion2_atendida_y_pagada_200_de_200() {
        val c = ctx(listOf(ses("s1", "Completada"), ses("s2", "Completada")), listOf(pago(100.0), pago(100.0, "s2")))
        assertEquals(100.0, montoAcordado(omer))   // sin contexto: el pactado de siempre
        assertEquals(200.0, montoAcordado(omer, c))
        assertEquals(0.0, saldo(omer, 200.0, c))
        assertTrue(saldado(omer, 200.0, c))
    }

    @Test fun omer_sesion2_atendida_sin_pagar_debe_100() {
        val c = ctx(listOf(ses("s1", "Completada"), ses("s2", "Completada")), listOf(pago(100.0)))
        assertEquals(100.0, saldo(omer, 100.0, c))
        assertEquals("Parcial", estadoPagoDe(100.0, montoAcordado(omer, c)))
    }

    @Test fun al_cerrar_la_sesion2_no_parece_saldado() {
        val c = ctx(listOf(ses("s1", "Completada"), ses("s2", "Planificada")), listOf(pago(100.0)))
        assertTrue(saldado(omer, 100.0, c))   // sin la sesión que se cierra: el bug
        assertFalse(saldado(omer, 100.0, c.copy(incluirSesionId = "s2")))
        assertEquals(100.0, saldo(omer, 100.0, c.copy(incluirSesionId = "s2")))
    }

    @Test fun sesion2_pagada_por_adelantado_sin_ligar_no_se_cobra_otra_vez() {
        val c = ctx(listOf(ses("s1", "Completada"), ses("s2", "Planificada")), listOf(pago(100.0), pago(100.0)), incluir = "s2")
        assertTrue(saldado(omer, 200.0, c))
    }

    @Test fun programadas_sin_pagar_no_suman() {
        val c = ctx(listOf(ses("s1", "Completada"), ses("s2", "Planificada"), ses("s3", "Planificada"), ses("s4", "Reprogramada")), listOf(pago(100.0)))
        assertEquals(100.0, montoAcordado(omer, c))
    }

    // ── Qué se devenga ──

    @Test fun pago_ligado_a_sesion_no_atendida_suma() {
        val d = detalleSesionesSueltas(omer, ctx(listOf(ses("s1", "Completada"), ses("s2", "Planificada")), listOf(pago(100.0, "s1"), pago(100.0, "s2"))))
        assertEquals(1, d.atendidas); assertEquals(1, d.adelantadas)
        assertEquals(200.0, d.devengado); assertEquals(200.0, d.acordado); assertFalse(d.porPactado)
    }

    @Test fun reprogramada_con_pago_ligado_suma() {
        assertEquals(200.0, montoAcordado(omer, ctx(listOf(ses("s1", "Completada"), ses("s2", "Reprogramada")), listOf(pago(100.0, "s2")))))
    }

    @Test fun cancelada_y_no_asistio_no_suman_aunque_tengan_pago() {
        val c = ctx(listOf(ses("s1", "Completada"), ses("s2", "Cancelada"), ses("s3", "No asistió")),
            listOf(pago(100.0, "s1"), pago(100.0, "s2"), pago(100.0, "s3")))
        assertEquals(100.0, montoAcordado(omer, c))
        // Lo pagado de más es saldo a favor REAL (sin otras sesiones agendadas).
        val cuenta = CuentaTratamiento(acordado = montoAcordado(omer, c), pagado = 300.0, estado = "Activo", modalidad = "Sesión suelta",
            precioPorSesion = 100.0, totalSesiones = 1, sesionesRealizadas = 1)
        assertEquals(200.0, saldoAFavorTratamiento(cuenta))
    }

    @Test fun pago_adelantado_de_sesiones_ya_agendadas_no_es_saldo_a_favor() {
        val c = ctx(listOf(ses("s1", "Completada"), ses("s2", "Planificada"), ses("s3", "Planificada")), listOf(pago(300.0)))
        val acordado = montoAcordado(omer, c)
        assertEquals(100.0, acordado)
        val cuenta = CuentaTratamiento(acordado = acordado, pagado = 300.0, estado = "Activo", modalidad = "Sesión suelta",
            precioPorSesion = 100.0, totalSesiones = 1, sesionesRealizadas = 3)
        assertEquals(0.0, saldoAFavorTratamiento(cuenta))
    }

    @Test fun pago_negativo_o_cero_no_marca_la_sesion_como_cobrada() {
        assertEquals(100.0, montoAcordado(omer, ctx(listOf(ses("s1", "Completada"), ses("s2", "Planificada")), listOf(pago(-50.0, "s2"), pago(0.0, "s2")))))
    }

    @Test fun la_que_se_cierra_no_se_cuenta_dos_veces() {
        val c = ctx(listOf(ses("s1", "Completada"), ses("s2", "Planificada")), listOf(pago(100.0, "s2")))
        assertEquals(200.0, montoAcordado(omer, c.copy(incluirSesionId = "s1")))
        assertEquals(200.0, montoAcordado(omer, c.copy(incluirSesionId = "s2")))
    }

    @Test fun una_cancelada_que_se_cierra_no_suma() {
        assertEquals(100.0, montoAcordado(omer, ctx(listOf(ses("s1", "Completada"), ses("s2", "Cancelada")), incluir = "s2")))
    }

    @Test fun sesion_nueva_suma_su_costo_o_el_precio() {
        val base = ctx(listOf(ses("s1", "Completada")), listOf(pago(100.0)))
        assertEquals(220.0, montoAcordado(omer, base.copy(sesionNueva = SesionNueva(120.0))))
        assertEquals(200.0, montoAcordado(omer, base.copy(sesionNueva = SesionNueva(null))))
    }

    // ── Costos ──

    @Test fun costos_distintos_suman_cada_costo() {
        assertEquals(330.0, montoAcordado(omer, ctx(listOf(ses("a", "Completada", 100.0), ses("b", "Completada", 150.0), ses("c", "Completada", 80.0)))))
    }

    @Test fun costo_0_o_vacio_vale_el_precio_de_la_sesion() {
        assertEquals(200.0, montoAcordado(omer, ctx(listOf(ses("a", "Completada", 0.0), ses("b", "Completada", null)))))
    }

    @Test fun sin_precio_por_sesion_podosalud() {
        val t = TratPago(modalidad = "Sesión suelta", precioPorSesion = null, totalSesiones = null)
        val c = ctx(listOf(ses("a", "Completada", 50.0), ses("b", "Completada", 50.0), ses("c", "Planificada", 50.0)), listOf(pago(50.0, "a"), pago(50.0)))
        assertEquals(0.0, montoAcordado(t))
        assertEquals(100.0, montoAcordado(t, c))
        assertEquals("Pagado", estadoPagoDe(100.0, montoAcordado(t, c)))
    }

    @Test fun decimales_redondea_a_centimos() {
        val t = TratPago(modalidad = "Sesión suelta", precioAcordado = 100.0, totalSesiones = 3)
        val c = ctx(listOf(ses("a", "Completada", null), ses("b", "Completada", null), ses("c", "Completada", null), ses("d", "Completada", null)))
        assertEquals(33.333, kotlin.math.round(precioSesionSuelta(t) * 1000) / 1000)
        assertEquals(133.33, montoAcordado(t, c))
    }

    // ── precio_acordado manda como PISO ──

    @Test fun garcia_negociado_por_sesion_1500_lista_1800() {
        val t = TratPago(modalidad = "Sesión suelta", precioAcordado = 1500.0, precioPorSesion = 1800.0, totalSesiones = 1)
        assertEquals(1500.0, montoAcordado(t, ctx(listOf(ses("a", "Planificada", 1800.0)), listOf(pago(1000.0)))))
        assertEquals(1500.0, montoAcordado(t, ctx(listOf(ses("a", "Completada", 1800.0)), listOf(pago(1000.0)))))
        assertEquals(3000.0, montoAcordado(t, ctx(listOf(ses("a", "Completada", 1800.0), ses("b", "Completada", 1800.0)))))
    }

    @Test fun garcia_serie_de_1800_con_sesiones_de_380() {
        val t = TratPago(modalidad = "Sesión suelta", precioAcordado = 1800.0, precioPorSesion = 380.0, totalSesiones = 1)
        fun n(k: Int) = ctx(List(k) { ses("s$it", "Completada", 380.0) })
        assertEquals(1800.0, montoAcordado(t, n(1)))
        assertEquals(1800.0, montoAcordado(t, n(4)))
        assertEquals(1900.0, montoAcordado(t, n(5)))
        assertTrue(detalleSesionesSueltas(t, n(1)).porPactado)
    }

    @Test fun omer_ampliada_3_por_900_una_atendida_sigue_el_pacto() {
        val t = TratPago(modalidad = "Sesión suelta", precioAcordado = 900.0, precioPorSesion = 300.0, totalSesiones = 3)
        val c = ctx(listOf(ses("a", "Completada", 300.0), ses("b", "Planificada", 300.0)), listOf(pago(300.0)))
        assertEquals(900.0, montoAcordado(t, c))
        assertEquals(600.0, saldo(t, 300.0, c))
    }

    @Test fun ampliada_mas_1_por_0_es_cortesia() {
        val t = TratPago(modalidad = "Sesión suelta", precioAcordado = 100.0, precioPorSesion = 100.0, totalSesiones = 2)
        val c = ctx(listOf(ses("a", "Completada", 100.0), ses("b", "Completada", 100.0)), listOf(pago(100.0), pago(100.0)))
        assertEquals(100.0, montoAcordado(t, c))
        assertEquals("Pagado", estadoPagoDe(200.0, montoAcordado(t, c)))   // DALU 12f5c1fb: sin deuda falsa
    }

    @Test fun dalu_1e6f4d90_tal_cual_pagado() {
        // Alta, acordado 100, total 2 (+1 por S/0), 2 completadas de 100, un pago de 100.
        val t = TratPago(modalidad = "Sesión suelta", precioAcordado = 100.0, precioPorSesion = 100.0, totalSesiones = 2)
        val c = ctx(listOf(ses("s1", "Completada", 100.0, 1, "2026-07-21"), ses("s2", "Completada", 100.0, 2, "2026-09-29")), listOf(pago(100.0)))
        assertEquals(100.0, montoAcordado(t, c))
        assertEquals("Pagado", estadoPagoDe(100.0, montoAcordado(t, c)))
        assertEquals(0.0, saldo(t, 100.0, c))
    }

    @Test fun pacto_por_n_suman_solo_las_que_exceden() {
        val t = TratPago(modalidad = "Sesión suelta", precioAcordado = 200.0, precioPorSesion = 100.0, totalSesiones = 2)
        fun s3(costos: List<Double>) = ctx(costos.mapIndexed { i, c -> ses("x$i", "Completada", c, i + 1) })
        assertEquals(200.0, montoAcordado(t, s3(listOf(100.0, 100.0))))
        assertEquals(350.0, montoAcordado(t, s3(listOf(100.0, 100.0, 150.0))))
        assertEquals(300.0, montoAcordado(t, s3(listOf(150.0, 100.0, 100.0))))
        val sinNumero = ctx(listOf(ses("z", "Completada", 120.0, null, "2026-10-01"), ses("a", "Completada", 100.0, 1), ses("b", "Completada", 100.0, 2)))
        assertEquals(320.0, montoAcordado(t, sinNumero))
        val porFecha = ctx(listOf(ses("a", "Completada", 150.0, null, "2026-10-03"), ses("b", "Completada", 100.0, null, "2026-10-01"),
            ses("c", "Completada", 100.0, null, "2026-10-02")))
        assertEquals(350.0, montoAcordado(t, porFecha))
        val dos = s3(listOf(100.0, 100.0))
        assertEquals(290.0, montoAcordado(t, dos.copy(sesiones = dos.sesiones + ses("n", "Planificada", 90.0, 3), incluirSesionId = "n")))
        assertEquals(280.0, montoAcordado(t, dos.copy(sesionNueva = SesionNueva(80.0))))
        assertEquals(200.0, montoAcordado(t, s3(listOf(100.0)).copy(sesionNueva = SesionNueva(80.0))))
    }

    @Test fun pacto_por_n_costo_0_no_es_cortesia() {
        val t = TratPago(modalidad = "Sesión suelta", precioAcordado = 200.0, precioPorSesion = 100.0, totalSesiones = 2)
        assertEquals(200.0, montoAcordado(t, ctx(listOf(ses("a", "Completada", 0.0), ses("b", "Completada", 100.0)))))
        assertEquals(300.0, montoAcordado(t, ctx(listOf(ses("a", "Completada", 100.0), ses("b", "Completada", 100.0), ses("c", "Completada", 0.0)))))
    }

    @Test fun suelta_recien_creada_y_historial_de_papel() {
        assertEquals(100.0, montoAcordado(omer, ctx(emptyList())))
        assertEquals("Pendiente", estadoPagoDe(0.0, montoAcordado(omer, ctx(emptyList()))))
        assertEquals(100.0, montoAcordado(omer, ctx(emptyList(), listOf(pago(100.0)))))
    }

    // ── Otras modalidades: sin cambios ──

    private val muchas = ctx(List(9) { ses("s$it", "Completada", 100.0) }, listOf(pago(400.0)))

    @Test fun paquete_unidades_y_consulta_igual_que_antes() {
        assertEquals(400.0, montoAcordado(paquete, muchas))
        assertEquals(350.0, montoAcordado(paquete.copy(precioAcordado = 350.0), muchas))
        assertEquals(6000.0, montoAcordado(TratPago(modalidad = "Unidades", cantidadUnidades = 4000, precioUnitario = 1.5), muchas))
        assertEquals(80.0, montoAcordado(TratPago(modalidad = "Consulta", precioAcordado = 80.0, precioPorSesion = 80.0, totalSesiones = 1), muchas))
    }

    @Test fun solo_sesion_suelta_con_espacios_tolerados() {
        assertTrue(esSesionSuelta(" Sesión suelta "))
        assertFalse(esSesionSuelta("Paquete"))
        assertFalse(esSesionSuelta(null))
    }

    // ── Texto de la ficha (PagoCard web) ──

    @Test fun texto_n_sesiones_atendidas() {
        val d = detalleSesionesSueltas(omer, ctx(listOf(ses("a", "Completada"), ses("b", "Completada"))))
        assertEquals("2 sesiones atendidas · ${dinero(200.0)}", textoSesionesSueltas(d, dinero))
    }

    @Test fun texto_atendidas_y_adelantadas() {
        val d = detalleSesionesSueltas(omer, ctx(listOf(ses("a", "Completada"), ses("b", "Planificada")), listOf(pago(100.0, "b"))))
        assertEquals("1 sesión atendida + 1 pagada por adelantado · ${dinero(200.0)}", textoSesionesSueltas(d, dinero))
    }

    @Test fun texto_sin_atenciones_y_piso_negociado() {
        assertEquals("Aún sin sesiones atendidas · se cobra ${dinero(100.0)} por sesión",
            textoSesionesSueltas(detalleSesionesSueltas(omer, ctx(emptyList())), dinero))
        val t = TratPago(modalidad = "Sesión suelta", precioAcordado = 1800.0, precioPorSesion = 380.0, totalSesiones = 1)
        assertEquals("1 sesión atendida · ${dinero(380.0)} (pactado ${dinero(1800.0)})",
            textoSesionesSueltas(detalleSesionesSueltas(t, ctx(listOf(ses("a", "Completada", 380.0)))), dinero))
    }

    @Test fun texto_pacto_por_n() {
        val t = TratPago(modalidad = "Sesión suelta", precioAcordado = 900.0, totalSesiones = 3)
        assertEquals("1 sesión atendida · dentro de lo pactado ${dinero(900.0)} por 3 sesiones",
            textoSesionesSueltas(detalleSesionesSueltas(t, ctx(listOf(ses("a", "Completada", 300.0)))), dinero))
        val d2 = detalleSesionesSueltas(t, ctx(List(4) { ses("s$it", "Completada", 300.0, it + 1) }))
        assertEquals(1200.0, d2.acordado); assertEquals(1, d2.fueraDelPacto); assertEquals(300.0, d2.montoFueraDelPacto)
        assertEquals("4 sesiones atendidas · pactado ${dinero(900.0)} por 3 sesiones + 1 adicional ${dinero(300.0)}", textoSesionesSueltas(d2, dinero))
        assertEquals("Aún sin sesiones atendidas · pactado ${dinero(900.0)} por 3 sesiones",
            textoSesionesSueltas(detalleSesionesSueltas(t, ctx(emptyList())), dinero))
    }

    // ── 💰 de la sesión 2 ──

    @Test fun cobro_de_la_sesion2_propone_su_precio() {
        val acordado = montoAcordado(omer, ctx(listOf(ses("s1", "Completada"), ses("s2", "Planificada")), listOf(pago(100.0)), incluir = "s2"))
        assertEquals(100.0, acordado - 100.0)
    }

    // ── Pago parcial: no baja el precio de una sesión suelta ──

    private val omerT = TratPago(modalidad = "Sesión suelta", precioPorSesion = 100.0, totalSesiones = 1)

    @Test fun costo_tras_cobro_al_registrar() {
        assertEquals(100.0, costoSesionTrasCobro(omerT, null, 60.0))
        assertEquals(120.0, costoSesionTrasCobro(omerT, 0.0, 120.0))
        assertNull(costoSesionTrasCobro(omerT, 100.0, 60.0))
        assertEquals(1500.0, costoSesionTrasCobro(TratPago(modalidad = "Sesión suelta", precioAcordado = 1500.0, precioPorSesion = 1800.0, totalSesiones = 1), null, 1000.0))
        assertEquals(60.0, costoSesionTrasCobro(TratPago(modalidad = "Sesión suelta", precioPorSesion = null), null, 60.0))
        assertEquals(60.0, costoSesionTrasCobro(paquete, null, 60.0))
        assertEquals(60.0, costoSesionTrasCobro(null, null, 60.0))
    }

    @Test fun costo_tras_cobro_al_editar() {
        assertNull(costoSesionTrasCobro(omerT, 100.0, 60.0, true))
        assertEquals(130.0, costoSesionTrasCobro(omerT, 100.0, 130.0, true))
        assertEquals(100.0, costoSesionTrasCobro(omerT, 0.0, 60.0, true))
        assertEquals(60.0, costoSesionTrasCobro(paquete, 100.0, 60.0, true))
        // Cobró 1000 por error y lo corrige a 100 → vuelve a 100 (sin deuda de 900).
        assertEquals(100.0, costoSesionTrasCobro(omerT, 1000.0, 100.0, true))
        assertEquals(0.0, saldo(omerT, 100.0, ctx(listOf(ses("s1", "Completada", 100.0)), listOf(pago(100.0, "s1")))))
        // Un parcial de 60 a 40: el costo sigue en 100 y debe 60.
        assertNull(costoSesionTrasCobro(omerT, 100.0, 40.0, true))
        assertEquals(100.0, costoSesionTrasCobro(omerT, 0.0, 40.0, true))
        assertEquals(60.0, saldo(omerT, 40.0, ctx(listOf(ses("s1", "Completada", 100.0)), listOf(pago(40.0, "s1")))))
    }

    // ── El fixture del gemelo SQL = esta regla (31 casos) ──

    @Test fun fixture_trae_los_31_casos() {
        assertEquals(26, CASOS_ACORDADO_FIXTURE.size)
        assertEquals(5, CASOS_COSTO_FIXTURE.size)
    }

    @Test fun fixture_acordado_igual_al_sql() {
        for (c in CASOS_ACORDADO_FIXTURE) {
            val t = TratPago(modalidad = "Sesión suelta", precioAcordado = c.precioAcordado, precioPorSesion = c.precioPorSesion, totalSesiones = c.totalSesiones)
            assertEquals(c.pactado, montoAcordado(t), "pactado «${c.nombre}»")
            val sesiones = c.sesiones.map { SesionDevengo(it.id, it.estado, it.costo, it.numero, it.fecha) }
            val pagos = c.sesiones.filter { it.pagada }.map { PagoDevengo(it.id, 1.0) }
            assertEquals(c.esperado, montoAcordado(t, ContextoSesiones(sesiones, pagos, c.incluir)), "acordado «${c.nombre}»")
        }
    }

    @Test fun fixture_costo_al_cobrar_igual_al_sql() {
        for (c in CASOS_COSTO_FIXTURE) {
            val t = TratPago(modalidad = c.modalidad, precioAcordado = c.precioAcordado, precioPorSesion = c.precioPorSesion, totalSesiones = c.totalSesiones)
            assertEquals(c.esperado, costoSesionTrasCobro(t, null, c.monto), "costo «${c.nombre}»")
        }
    }

    // ── En la app: TratamientoPaciente con las sesiones embebidas de la ficha ──

    private fun trat(
        modalidad: String = "Sesión suelta", total: Int = 1, precioPorSesion: Double? = 100.0, precioAcordado: Double? = null,
        estadoPago: String? = null, sesiones: List<SesionDevengo>? = null, pagos: List<PagoDevengo>? = null,
    ) = TratamientoPaciente(
        id = "t", procedimiento = "Fisio", terapeutaId = null, terapeutaNombre = null, modalidad = modalidad,
        estado = "Activo", estadoPago = estadoPago, totalSesiones = total, sesionesCompletadas = 0,
        precioPaquete = if (modalidad == "Paquete") 400.0 else null, precioPorSesion = precioPorSesion, precioAcordado = precioAcordado,
        usaSesiones = true, diagnostico = null, medicacion = null, proximoControl = null, especialidadNombre = null,
        sesionesDevengo = sesiones, pagosDevengo = pagos,
    )

    @Test fun ficha_omer_100_mas_100_pagado_200_de_200() {
        val t = trat(estadoPago = "Pagado", sesiones = listOf(ses("s1", "Completada"), ses("s2", "Completada")), pagos = listOf(pago(100.0, "s1"), pago(100.0, "s2")))
        assertEquals(200.0, t.montoAcordado)
        assertEquals(100.0, t.montoPactado)
        assertEquals("Pagado", t.estadoPagoVivo())
        assertTrue(t.sinSaldoCon(200.0))
    }

    @Test fun ficha_cierre_de_la_2a_pregunta_si_pago() {
        // Estado guardado "Pagado" (por la 1ª): al cerrar la 2ª, NO está saldado.
        val t = trat(estadoPago = "Pagado", sesiones = listOf(ses("s1", "Completada"), ses("s2", "Planificada")), pagos = listOf(pago(100.0, "s1")))
        assertTrue(t.sinSaldoCon(null))
        assertFalse(t.sinSaldoCon(null, "s2"))
        assertFalse(t.sinSaldoCon(100.0, "s2"))
        assertEquals(200.0, t.acordadoCon("s2"))
    }

    @Test fun ficha_suelta_sin_sesiones_cargadas_nunca_esconde_el_cobro() {
        val t = trat(estadoPago = "Pagado")
        assertEquals(100.0, t.montoAcordado)   // el pactado
        assertFalse(t.sinSaldoCon(null, "s2"))
        assertEquals("Pagado", t.estadoPagoVivo())   // se muestra el guardado
    }

    @Test fun ficha_estado_vivo_cuando_el_guardado_va_atrasado() {
        val t = trat(estadoPago = "Pagado", sesiones = listOf(ses("s1", "Completada"), ses("s2", "Completada")), pagos = listOf(pago(100.0)))
        assertEquals("Parcial", t.estadoPagoVivo())
        assertEquals("Parcial", t.estadoPagoVivo(100.0))
    }

    @Test fun ficha_pagos_frescos_de_la_seccion_mandan() {
        val t = trat(sesiones = listOf(ses("s1", "Completada"), ses("s2", "Planificada")), pagos = listOf(pago(100.0, "s1")))
        assertEquals(100.0, t.montoAcordado)
        assertEquals(200.0, t.acordadoCon(pagos = listOf(pago(100.0, "s1"), pago(100.0, "s2"))))
    }

    @Test fun ficha_paquete_y_suelta_cada_uno_con_su_cuenta() {
        val p = trat(modalidad = "Paquete", total = 5, sesiones = listOf(ses("p1", "Completada", 0.0)), pagos = listOf(pago(400.0)))
        val s = trat(sesiones = listOf(ses("s1", "Completada"), ses("s2", "Completada")), pagos = listOf(pago(100.0)))
        assertEquals(400.0, p.montoAcordado)
        assertTrue(p.sinSaldoCon(400.0, "p1"))
        assertEquals(200.0, s.montoAcordado)
        assertEquals(100.0, s.montoAcordado - s.pagadoEmbebido!!)
        // Saldo a favor: recibe el acordado nuevo (no aparece un "a favor" falso).
        assertEquals(0.0, saldoAFavorTratamiento(s.copy(sesionesRealizadas = 2).cuentaCon(200.0)))
    }

    @Test fun ficha_garcia_y_1e6f4d90() {
        val garcia = trat(precioPorSesion = 380.0, precioAcordado = 1800.0, sesiones = List(4) { ses("g$it", "Completada", 380.0, it + 1) }, pagos = emptyList())
        assertEquals(1800.0, garcia.montoAcordado)
        val laser = trat(precioPorSesion = 1800.0, precioAcordado = 1500.0, sesiones = listOf(ses("l", "Completada", 1800.0)), pagos = listOf(pago(1500.0, "l")))
        assertEquals(1500.0, laser.montoAcordado)
        assertTrue(laser.sinSaldoCon(null))
        val dalu = trat(total = 2, precioAcordado = 100.0, estadoPago = "Parcial",
            sesiones = listOf(ses("s1", "Completada", 100.0, 1, "2026-07-21"), ses("s2", "Completada", 100.0, 2, "2026-09-29")), pagos = listOf(pago(100.0)))
        assertEquals(100.0, dalu.montoAcordado)
        assertEquals("Pagado", dalu.estadoPagoVivo())
    }

    // ── El select de la ficha: una sesión sin costo no rompe nada ──

    @Test fun parseo_del_embebido_tolera_costos_nulos() {
        val json = Json.parseToJsonElement(
            """{"ses_estados":[{"id":"s1","estado":"Completada","costo":null,"numero":1,"fecha":"2026-10-01"},
               {"id":"s2","estado":"Completada","costo":"120.50","numero":null,"fecha":null},
               {"id":"s3","estado":"Planificada"}],
               "pagos_dev":[{"sesion_id":null,"monto":100},{"sesion_id":"s3","monto":"50.00"}]}""",
        ).jsonObject
        val ses = sesionesDevengoDe(json["ses_estados"])!!
        val pag = pagosDevengoDe(json["pagos_dev"])!!
        assertEquals(3, ses.size)
        assertNull(ses[0].costo); assertEquals(120.5, ses[1].costo); assertNull(ses[2].numero)
        assertEquals("s3", pag[1].sesionId); assertEquals(50.0, pag[1].monto)
        assertNull(sesionesDevengoDe(null))
        assertEquals(0, sesionesDevengoDe(Json.parseToJsonElement("[]").jsonArray)!!.size)
        // s1 sin costo = 100, s2 = 120.50, s3 adelantada (pago ligado) = 100.
        assertEquals(320.5, trat(sesiones = ses, pagos = pag).montoAcordado)
    }
}
