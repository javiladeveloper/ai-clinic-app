package pe.saniape.app.data.staff

import kotlinx.datetime.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Calendario del mes de la agenda (pedido del dueño 2026-10-15: "que en agenda
 * se pueda ver los meses, algo así como está la web"). Gemelo de buildCalendar
 * de /citas: semana desde lunes, relleno hasta semanas completas.
 */
class CalendarioMesAgendaTest {

    // ── Grilla: semana desde LUNES ──

    @Test
    fun octubre2026EmpiezaJuevesConTresHuecosDelLunes() {
        val g = grillaMes(MesAgenda(2026, 10), hoy = "2026-10-15")
        assertEquals(35, g.size)                      // 3 huecos + 31 días + 1 = 5 semanas
        assertEquals("2026-09-28", g.first().iso)     // lunes 28/09
        assertFalse(g[0].delMes); assertFalse(g[2].delMes)
        assertEquals(CeldaMes("2026-10-01", 1, delMes = true, esHoy = false), g[3])
        assertEquals("2026-11-01", g.last().iso)      // domingo 1/11 de relleno
        assertFalse(g.last().delMes)
        assertEquals(31, g.count { it.delMes })
    }

    @Test
    fun siempreSemanasCompletasYCadaFilaEmpiezaEnLunes() {
        for (anio in 2024..2028) for (m in 1..12) {
            val mes = MesAgenda(anio, m)
            val g = grillaMes(mes, hoy = "2000-01-01")
            assertEquals(0, g.size % 7, "semanas completas en $anio-$m")
            assertTrue(g.size in 28..42, "entre 4 y 6 semanas en $anio-$m")
            g.chunked(7).forEach { semana ->
                assertEquals(kotlinx.datetime.DayOfWeek.MONDAY, kotlinx.datetime.LocalDate.parse(semana.first().iso).dayOfWeek)
            }
            assertEquals(diasDelMes(anio, m), g.count { it.delMes })
            assertEquals(mes.desdeIso, g.first { it.delMes }.iso)
            assertEquals(mes.hastaIso, g.last { it.delMes }.iso)
        }
    }

    @Test
    fun mesQueEmpiezaEnDomingoTieneSeisHuecos() {
        // 1/02/2026 cae domingo: la primera fila es casi toda de enero.
        val g = grillaMes(MesAgenda(2026, 2), hoy = "2026-02-10")
        assertEquals("2026-01-26", g.first().iso)
        assertEquals("2026-02-01", g[6].iso)
        assertTrue(g[6].delMes)
    }

    @Test
    fun febreroQueEmpiezaEnLunesCabeEnCuatroSemanas() {
        val g = grillaMes(MesAgenda(2027, 2), hoy = "2027-02-01")
        assertEquals(28, g.size)
        assertTrue(g.all { it.delMes })
        assertTrue(g.first().esHoy)
    }

    @Test
    fun mesDeSeisSemanas() {
        // 1/08/2026 cae sábado: 5 huecos + 31 días = 36 → 42 celdas.
        val g = grillaMes(MesAgenda(2026, 8), hoy = "2026-08-01")
        assertEquals(42, g.size)
        assertEquals("2026-07-27", g.first().iso)
        assertEquals("2026-09-06", g.last().iso)
    }

    // ── Meses de 28 / 29 / 30 / 31 días ──

    @Test
    fun diasDeCadaMes() {
        assertEquals(28, diasDelMes(2026, 2))
        assertEquals(29, diasDelMes(2028, 2))   // bisiesto
        assertEquals(29, diasDelMes(2000, 2))   // divisible por 400: bisiesto
        assertEquals(28, diasDelMes(2100, 2))   // divisible por 100: no
        assertEquals(30, diasDelMes(2026, 9))
        assertEquals(30, diasDelMes(2026, 11))
        assertEquals(31, diasDelMes(2026, 10))
        assertEquals(31, diasDelMes(2026, 12))
        assertEquals("2028-02-29", MesAgenda(2028, 2).hastaIso)
        assertEquals("2026-09-30", MesAgenda(2026, 9).hastaIso)
        assertEquals(29, grillaMes(MesAgenda(2028, 2), "x").count { it.delMes })
    }

    // ── Navegación y títulos ──

    @Test
    fun navegaEntreAniosYTituloEnEspanol() {
        assertEquals(MesAgenda(2027, 1), MesAgenda(2026, 12).siguiente())
        assertEquals(MesAgenda(2025, 12), MesAgenda(2026, 1).anterior())
        assertEquals("Octubre 2026", MesAgenda(2026, 10).titulo)
        assertEquals("Septiembre 2026", MesAgenda(2026, 9).titulo)
        assertEquals("2026-03", MesAgenda(2026, 3).clave)
        assertEquals(MesAgenda(2026, 10), MesAgenda.de("2026-10-15"))
        assertEquals(MesAgenda(2026, 10), MesAgenda.de("2026-10-15T10:00:00"))
        assertNull(MesAgenda.de("basura"))
        assertNull(MesAgenda.de("2026-13-01"))
        assertNull(MesAgenda.de(null))
    }

    // ── "Hoy" en la zona de la sede, nunca UTC ──

    @Test
    fun hoyEnLaZonaDeLaSedeNoEnUtc() {
        // 31/10 22:30 en Lima = 1/11 03:30 UTC: para Lima sigue siendo octubre.
        val ahora = Instant.parse("2026-11-01T03:30:00Z")
        assertEquals(MesAgenda(2026, 10), mesDeHoy("America/Lima", ahora))
        assertEquals(MesAgenda(2026, 11), mesDeHoy("Europe/Madrid", ahora))
        // Zona inválida → Lima (como hoyEnIso).
        assertEquals(MesAgenda(2026, 10), mesDeHoy("Marte/Olimpo", ahora))

        val hoyLima = hoyEnIso("America/Lima", ahora)
        val g = grillaMes(mesDeHoy("America/Lima", ahora), hoyLima)
        assertEquals(listOf("2026-10-31"), g.filter { it.esHoy }.map { it.iso })
        // En la grilla de noviembre el 31/10 es relleno: si lleva "hoy", nunca es del mes.
        val nov = grillaMes(MesAgenda(2026, 11), hoyLima)
        assertTrue(nov.filter { it.esHoy }.all { !it.delMes })
    }

    // ── Conteos por día ──

    private val filas = listOf(
        FilaCitaMes("2026-10-15", "Pendiente", "Consulta", "t1", "fisio"),
        FilaCitaMes("2026-10-15", "Confirmada", "Sesión", "t1", "fisio"),
        FilaCitaMes("2026-10-15", "Completada", "Sesión", "t2", "nutri"),
        FilaCitaMes("2026-10-15", "Cancelada", "Consulta", "t2", "nutri"),
        FilaCitaMes("2026-10-16", "Cancelada", "Consulta", "t1", null),
        FilaCitaMes("2026-10-20", "Confirmada", "Evaluación", null, null, especialidadServicioId = "fisio"),
    )

    @Test
    fun cuentaPorDiaSinCanceladasPeroConSuPunto() {
        val c = conteosPorDia(filas)
        assertEquals(3, c["2026-10-15"]!!.total)
        assertEquals(mapOf("Pendiente" to 1, "Confirmada" to 1, "Completada" to 1, "Cancelada" to 1), c["2026-10-15"]!!.porEstado)
        // Solo canceladas: 0 en el número, pero el punto rojo queda.
        assertEquals(0, c["2026-10-16"]!!.total)
        assertEquals(listOf("Cancelada"), c["2026-10-16"]!!.estadosPuntos)
        assertEquals(1, c["2026-10-20"]!!.total)
        assertNull(c["2026-10-17"])
    }

    @Test
    fun puntosEnOrdenYMaximoTres() {
        val c = conteosPorDia(filas)["2026-10-15"]!!
        assertEquals(listOf("Pendiente", "Confirmada", "Completada"), c.estadosPuntos)
    }

    @Test
    fun respetaLosFiltrosDeLaAgenda() {
        // Filtro de estado.
        assertEquals(1, conteosPorDia(filas, "Pendiente")["2026-10-15"]!!.total)
        assertNull(conteosPorDia(filas, "Pendiente")["2026-10-20"])
        // Filtrando "Cancelada", las canceladas SÍ suman.
        val canc = conteosPorDia(filas, "Cancelada")
        assertEquals(1, canc["2026-10-15"]!!.total)
        assertEquals(1, canc["2026-10-16"]!!.total)
        // Profesional / tipo.
        assertEquals(2, conteosPorDia(filas) { it.terapeutaId == "t1" }["2026-10-15"]!!.total)
        assertEquals(2, conteosPorDia(filas) { it.tipo == "Sesión" }["2026-10-15"]!!.total)
        // Especialidad con la misma cadena que la agenda (cita → servicio → profesional).
        val porFisio = conteosPorDia(filas) { f ->
            pasaFiltroEspecialidad(
                opcionDeEspecialidad(especialidadIdDeCita(f.especialidadId, f.especialidadServicioId, null), setOf("fisio", "nutri")),
                listOf("fisio"),
            )
        }
        assertEquals(2, porFisio["2026-10-15"]!!.total)
        assertEquals(1, porFisio["2026-10-20"]!!.total)   // por la especialidad del servicio
    }

    @Test
    fun filasRarasNoRompen() {
        val c = conteosPorDia(listOf(FilaCitaMes("", "Pendiente"), FilaCitaMes("2026-10-01T00:00:00", "Pendiente")))
        assertEquals(setOf("2026-10-01"), c.keys)
    }

    // ── Accesibilidad ──

    @Test
    fun descripcionParaLectorDePantalla() {
        val c = conteosPorDia(filas)
        assertEquals("15 de octubre, 3 citas", descripcionDia("2026-10-15", c["2026-10-15"]))
        assertEquals("20 de octubre, 1 cita", descripcionDia("2026-10-20", c["2026-10-20"]))
        assertEquals("17 de octubre, sin citas", descripcionDia("2026-10-17", null))
        assertEquals("1 de enero, hoy, sin citas, seleccionado", descripcionDia("2027-01-01", null, esHoy = true, elegido = true))
    }

    // ── La tira de días salta al día elegido en el calendario ──

    @Test
    fun tiraAlrededorDeHoyOMovidaAlDiaElegido() {
        assertEquals("2026-10-15", baseTiraDias("2026-10-15", "2026-10-15"))
        assertEquals("2026-10-15", baseTiraDias("2026-10-15", "2026-10-13"))  // -2: sigue en la tira
        assertEquals("2026-10-15", baseTiraDias("2026-10-15", "2026-10-27"))  // +12
        assertEquals("2026-10-28", baseTiraDias("2026-10-15", "2026-10-28"))  // fuera: se mueve
        assertEquals("2026-09-01", baseTiraDias("2026-10-15", "2026-09-01"))
        assertEquals("2026-10-15", baseTiraDias("2026-10-15", "basura"))
    }
}
