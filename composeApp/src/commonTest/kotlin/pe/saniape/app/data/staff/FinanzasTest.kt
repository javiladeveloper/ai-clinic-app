package pe.saniape.app.data.staff

import kotlinx.datetime.LocalDate
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Lógica pura de "Finanzas y caja" nativa (gemela de /finanzas web). */
class FinanzasTest {

    private val hoy = LocalDate(2026, 10, 8) // jueves

    private fun mov(
        tipo: String, monto: Double, fecha: String = "2026-10-08", metodo: String? = null,
        categoria: String = "Otro", comprobante: String? = null, sesionId: String? = null, comisionId: String? = null,
    ) = MovimientoKardex(
        id = "m${monto}${fecha}${metodo}", tipo = tipo, categoria = categoria, descripcion = "x", monto = monto,
        fecha = fecha, metodoPago = metodo, comprobante = comprobante, sesionId = sesionId, comisionId = comisionId,
    )

    @Test
    fun rangoDeCadaPeriodoHastaHoy() {
        // Sin techo en la consulta (como la web): el corte en hoy es de totales/lista.
        assertEquals(RangoFechas("2026-10-08", null), Finanzas.rango(PeriodoFinanzas.Dia, hoy, null))
        // La semana empieza el lunes.
        assertEquals(RangoFechas("2026-10-05", null), Finanzas.rango(PeriodoFinanzas.Semana, hoy, null))
        assertEquals(RangoFechas("2026-10-01", null), Finanzas.rango(PeriodoFinanzas.Mes, hoy, null))
        assertEquals(RangoFechas("2026-01-01", null), Finanzas.rango(PeriodoFinanzas.Anio, hoy, null))
        assertEquals(RangoFechas(null, null), Finanzas.rango(PeriodoFinanzas.Total, hoy, null))
        // Totales y lista: lo fechado a futuro no es dinero de hoy (salvo con rango manual).
        val futuros = listOf(mov("Ingreso", 1.0, "2026-10-08"), mov("Ingreso", 2.0, "2026-10-20"))
        assertEquals(1, Finanzas.hastaHoy(futuros, hoy, null).size)
        assertEquals(2, Finanzas.hastaHoy(futuros, hoy, "2026-10-01" to "2026-10-31").size)
        // Rango manual al revés: se ordena (como aplicarRango de la web).
        assertEquals(RangoFechas("2026-09-01", "2026-09-30"), Finanzas.rango(PeriodoFinanzas.Mes, hoy, "2026-09-30" to "2026-09-01"))
    }

    @Test
    fun resumenTotalesYMetodos() {
        val r = Finanzas.resumen(listOf(
            mov("Ingreso", 100.0, metodo = "Efectivo"),
            mov("Ingreso", 50.10, metodo = "Yape"),
            mov("Ingreso", 20.0, metodo = null),
            mov("Egreso", 30.0, metodo = "Efectivo"),
        ))
        assertEquals(170.1, r.ingresos)
        assertEquals(30.0, r.egresos)
        assertEquals(140.1, r.balance)
        // Sin método = "Otro", de mayor a menor.
        assertEquals(listOf("Efectivo" to 100.0, "Yape" to 50.1, "Otro" to 20.0), r.porMetodo)
    }

    @Test
    fun filtrosDeLaListaYNeto() {
        val movs = listOf(
            mov("Ingreso", 100.0, metodo = "Efectivo"),
            mov("Ingreso", 40.0, metodo = "Yape"),
            mov("Egreso", 25.0, categoria = "Comisiones"),
            mov("Egreso", 5.0, categoria = "Insumos"),
        )
        assertEquals(1, Finanzas.filtrar(movs, "Ingreso", "Yape", null).size)
        assertEquals(listOf(25.0), Finanzas.filtrar(movs, "Egreso", null, "Comisiones").map { it.monto })
        // Con ingresos y egresos mezclados, el neto lleva signo.
        assertEquals(110.0, Finanzas.neto(movs))
        assertEquals(listOf("Comisiones", "Insumos"), Finanzas.categoriasEgreso(movs))
        assertEquals(listOf("Efectivo", "Yape"), Finanzas.metodosUsados(movs))
    }

    @Test
    fun arqueoCuadraSobraFalta() {
        assertEquals(Finanzas.EstadoArqueo.CUADRA, Finanzas.compararArqueo(70.0, 70.0).estado)
        // Tolerancia de un centavo (coma flotante).
        assertEquals(Finanzas.EstadoArqueo.CUADRA, Finanzas.compararArqueo(339.99999, 340.0).estado)
        val falta = Finanzas.compararArqueo(70.0, 65.0)
        assertEquals(-5.0, falta.diferencia)
        assertEquals(Finanzas.EstadoArqueo.FALTA, falta.estado)
        assertEquals("Falta S/ 5.00", falta.mensaje)
        val sobra = Finanzas.compararArqueo(70.0, 72.5)
        assertEquals(2.5, sobra.diferencia)
        assertEquals("Sobra S/ 2.50", sobra.mensaje)
    }

    @Test
    fun barrasDelGraficoSegunElPeriodo() {
        val movs = listOf(mov("Ingreso", 10.0, "2026-10-08"), mov("Egreso", 4.0, "2026-10-07"), mov("Ingreso", 3.0, "2026-09-15"))
        assertEquals(3, Finanzas.barras(movs, PeriodoFinanzas.Dia, hoy, null).size)
        assertEquals(7, Finanzas.barras(movs, PeriodoFinanzas.Semana, hoy, null).size)
        val mes = Finanzas.barras(movs, PeriodoFinanzas.Mes, hoy, null)
        assertEquals(31, mes.size) // octubre entero, con los días futuros en 0
        assertEquals(10.0, mes[7].ingresos)
        assertEquals(4.0, mes[6].egresos)
        val anio = Finanzas.barras(movs, PeriodoFinanzas.Anio, hoy, null)
        assertEquals(12, anio.size)
        assertEquals("oct", anio.last().etiqueta)
        assertEquals(3.0, anio[anio.size - 2].ingresos)
        // Rango de más de 62 días → por mes.
        assertEquals(4, Finanzas.barras(movs, PeriodoFinanzas.Mes, hoy, "2026-07-01" to "2026-10-08").size)
        assertEquals(10, Finanzas.barras(movs, PeriodoFinanzas.Mes, hoy, "2026-09-29" to "2026-10-08").size)
    }

    @Test
    fun manualesYDondeSeCorrigen() {
        assertTrue(Finanzas.esManual(mov("Egreso", 1.0, comprobante = "F001-492")))
        assertTrue(Finanzas.esManual(mov("Egreso", 1.0)))
        assertFalse(Finanzas.esManual(mov("Ingreso", 1.0, comprobante = "pago:abc")))
        assertFalse(Finanzas.esManual(mov("Ingreso", 1.0, sesionId = "s1")))
        assertNull(Finanzas.dondeSeCorrige(mov("Egreso", 1.0)))
        assertTrue(Finanzas.dondeSeCorrige(mov("Ingreso", 1.0, comprobante = "cita:c1#2"))!!.contains("agenda"))
        assertEquals("F001", Finanzas.refVisible(mov("Egreso", 1.0, comprobante = "F001")))
        assertNull(Finanzas.refVisible(mov("Egreso", 1.0, comprobante = "recurrente:g:2026-10")))
    }

    @Test
    fun techoYMontos() {
        assertEquals(500.0, Finanzas.techoBonito(470.0))
        assertEquals(2000.0, Finanzas.techoBonito(1230.0))
        assertEquals(1.0, Finanzas.techoBonito(0.0))
        assertEquals(12.5, Finanzas.parsearMonto("12,5"))
        assertNull(Finanzas.parsearMonto("abc"))
        assertNull(Finanzas.parsearMonto("-3"))
    }

    @Test
    fun parseoDeRespuestasDelServidor() {
        val dia = FinanzasRepo.parsearDiaCierre(Json.parseToJsonElement("""
            {"fecha":"2026-10-08","multiSede":false,"cerrable":true,"cerradasConsolidado":null,
             "totalIngresos":160,"totalEgresos":30,"neto":130,"esperado":70,
             "porMetodo":[{"metodo":"Efectivo","total":100},{"metodo":"Yape","total":60}],
             "cierre":{"id":"k1","efectivoContado":65,"diferencia":-5,"nota":"vuelto","cerradoAt":"2026-10-08T23:00:00+00:00"},
             "movimientos":[{"tipo":"Ingreso","monto":100,"metodoPago":"Efectivo","categoria":"Pago","descripcion":null,"createdAt":null}]}
        """).jsonObject)!!
        assertEquals(70.0, dia.esperado)
        assertNull(dia.cerradasConsolidado)
        assertEquals(-5.0, dia.cierre!!.diferencia)
        assertEquals("Pago", dia.movimientos.single().descripcion)
        assertEquals(listOf("Efectivo" to 100.0, "Yape" to 60.0), dia.porMetodo)

        val g = FinanzasRepo.parsearGastos(Json.parseToJsonElement("""
            {"gastos":[{"id":"g1","nombre":"Alquiler","categoria":"Alquiler","monto":1500,"frecuencia":"Mensual","diaCobro":1,
              "fechaUnica":null,"activo":true,"pagado":false,"yaEnCaja":true,"unicoCerrado":false,"proximo":"2026-11-01"}],
             "categorias":{"egreso":["Insumos"],"ingreso":["Otro ingreso"],"gastoFijo":["Gasto fijo"]}}
        """).jsonObject)
        assertEquals("2026-11-01", g.gastos.single().proximo)
        assertTrue(g.gastos.single().yaEnCaja)
        assertEquals(listOf("Gasto fijo"), g.catGastoFijo)

        assertEquals("c1", FinanzasRepo.citaIdDeComprobante("cita:c1#2"))
        assertEquals("c1", FinanzasRepo.citaIdDeComprobante("cita:c1"))
        assertNull(FinanzasRepo.citaIdDeComprobante("pago:c1"))
    }

    @Test
    fun porcentajesRepartidosSumanCien() {
        // 29.8 / 40.1 / 30.1 truncados darian 29+40+30 = 99; repartidos suman 100.
        val p = Finanzas.porcentajesRepartidos(listOf(298.0, 401.0, 301.0))
        assertEquals(100, p.sum())
        assertEquals(listOf(30, 40, 30), p)
        assertEquals(listOf(34, 33, 33), Finanzas.porcentajesRepartidos(listOf(1.0, 1.0, 1.0)))
        assertEquals(listOf(0, 0), Finanzas.porcentajesRepartidos(listOf(0.0, 0.0)))
        assertEquals(emptyList(), Finanzas.porcentajesRepartidos(emptyList()))
    }
}
