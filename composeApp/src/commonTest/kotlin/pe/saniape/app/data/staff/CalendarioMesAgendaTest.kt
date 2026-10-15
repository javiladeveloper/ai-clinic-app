package pe.saniape.app.data.staff

import kotlinx.datetime.Instant
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlin.coroutines.Continuation
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.coroutines.startCoroutine
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
        assertEquals("15 de octubre, 3 citas", descripcionDia("2026-10-15", conteosPorDia(filas) { it.estado != "Cancelada" }["2026-10-15"]))
        // Con activas, las canceladas se dicen aparte (no suman al número).
        assertEquals("15 de octubre, 3 citas, 1 cancelada", descripcionDia("2026-10-15", c["2026-10-15"]))
        assertEquals("20 de octubre, 1 cita", descripcionDia("2026-10-20", c["2026-10-20"]))
        assertEquals("17 de octubre, sin citas", descripcionDia("2026-10-17", null))
        assertEquals("1 de enero, hoy, sin citas, seleccionado", descripcionDia("2027-01-01", null, esHoy = true, elegido = true))
        // Solo canceladas: no es "sin citas".
        assertEquals("16 de octubre, 1 cita cancelada", descripcionDia("2026-10-16", c["2026-10-16"]))
        val dosCanc = conteosPorDia(listOf(FilaCitaMes("2026-10-16", "Cancelada"), FilaCitaMes("2026-10-16", "Cancelada")))
        assertEquals("16 de octubre, 2 citas canceladas", descripcionDia("2026-10-16", dosCanc["2026-10-16"]))
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

    @Test
    fun laTiraNoSeMueveMientrasElDiaSigaEnElla() {
        // Ya movida al 28: tocar el 30 o el 26 (dentro de su tira) no la rearma.
        assertEquals("2026-10-28", baseTiraDias("2026-10-28", "2026-10-30"))
        assertEquals("2026-10-28", baseTiraDias("2026-10-28", "2026-10-26"))
        // "Hoy" (fuera de la tira movida) la trae de vuelta.
        assertEquals("2026-10-15", baseTiraDias("2026-10-28", "2026-10-15"))
    }

    // ── Fila liviana desde JSON (tolerancia a backend viejo) ──

    private fun json(texto: String) = Json.parseToJsonElement(texto) as JsonObject

    @Test
    fun mapeaLaFilaLivianaYToleraFaltantes() {
        val o = json(
            """{"fecha":"2026-10-15","estado":"Confirmada","tipo":"Sesión","terapeuta_id":"t1","especialidad_id":null,
               "tratamiento":{"procedimiento":{"especialidad_id":"fisio"}}}"""
        )
        assertEquals(FilaCitaMes("2026-10-15", "Confirmada", "Sesión", "t1", null, "fisio"), filaCitaMesDeJson(o))
        // Sin el join (pedido sin servicio) o con el tratamiento null.
        assertEquals(FilaCitaMes("2026-10-15", "Pendiente"), filaCitaMesDeJson(json("""{"fecha":"2026-10-15","estado":"Pendiente","tratamiento":null}""")))
        // Vacía: no revienta, y el conteo la descarta por no tener fecha.
        val vacia = filaCitaMesDeJson(JsonObject(emptyMap()))
        assertEquals("", vacia.fecha)
        assertTrue(conteosPorDia(listOf(vacia)).isEmpty())
    }

    // ── Paginado hasta una tanda vacía ──

    /** Corre un suspend que no suspende de verdad (las fuentes falsas de abajo). */
    private fun <T> correr(bloque: suspend () -> T): T {
        var res: Result<T>? = null
        bloque.startCoroutine(Continuation(EmptyCoroutineContext) { res = it })
        return res!!.getOrThrow()
    }

    /** Servidor falso con [total] filas y un max_rows de [maxRows]. */
    private fun fuente(total: Int, maxRows: Int, pedidos: MutableList<Pair<Long, Long>>): suspend (Long, Long) -> List<Int> =
        { desde, hasta ->
            pedidos.add(desde to hasta)
            val fin = minOf(hasta + 1, desde + maxRows, total.toLong())
            (desde until fin).map { it.toInt() }
        }

    @Test
    fun paginaHastaTandaVaciaAunqueMaxRowsSeaMenor() {
        val pedidos = mutableListOf<Pair<Long, Long>>()
        // max_rows 500 < tanda 1000: una tanda "corta" NO es el final.
        val r = correr { paginarHastaVacia(1000, 20_000, fuente(1_200, 500, pedidos)) }
        assertEquals((0 until 1_200).toList(), r.filas)   // sin huecos ni repetidas
        assertFalse(r.llegoAlTope)
        assertEquals(listOf(0L to 999L, 500L to 1499L, 1000L to 1999L, 1200L to 2199L), pedidos)
    }

    @Test
    fun mesVacioEsUnSoloPedido() {
        val pedidos = mutableListOf<Pair<Long, Long>>()
        val r = correr { paginarHastaVacia(1000, 20_000, fuente(0, 1000, pedidos)) }
        assertTrue(r.filas.isEmpty()); assertFalse(r.llegoAlTope)
        assertEquals(1, pedidos.size)
    }

    @Test
    fun avisaElTopeEnVezDeCortarEnSilencio() {
        val pedidos = mutableListOf<Pair<Long, Long>>()
        val r = correr { paginarHastaVacia(1000, 2_500, fuente(10_000, 1000, pedidos)) }
        assertEquals(2_500, r.filas.size)
        assertTrue(r.llegoAlTope)
        assertEquals(3, pedidos.size)   // no sigue pidiendo pasado el tope
    }

    // ── Caché por mes: frescura, vencimiento y carreras ──

    @Test
    fun cacheFrescaNoSePideDeNuevo() {
        val cache = CacheMesAgenda<String>()
        val t = cache.empezar("2026-10")
        assertTrue(cache.enCurso("2026-10")); assertFalse(cache.fresco("2026-10"))
        assertTrue(cache.terminar("2026-10", t, "oct"))
        assertTrue(cache.fresco("2026-10")); assertEquals("oct", cache.datos("2026-10"))
        assertFalse(cache.enCurso("2026-10"))
    }

    @Test
    fun venceSoloElMesDelCambioYConservaLoMostrado() {
        val cache = CacheMesAgenda<String>()
        listOf("2026-10", "2026-10:t1:sede=s1", "2026-11", "2026-01").forEach { cache.terminar(it, cache.empezar(it), it) }
        cache.vencer(setOf("2026-10"))
        assertFalse(cache.fresco("2026-10")); assertFalse(cache.fresco("2026-10:t1:sede=s1"))
        assertTrue(cache.fresco("2026-11")); assertTrue(cache.fresco("2026-01"))
        // Vencido se sigue mostrando mientras se relee (la grilla no se vacía).
        assertEquals("2026-10", cache.datos("2026-10"))
        // null = no se sabe qué mes: todos.
        cache.vencer(null)
        assertFalse(cache.fresco("2026-11"))
    }

    @Test
    fun unFalloNoBorraLoQueHabia() {
        val cache = CacheMesAgenda<String>()
        cache.terminar("2026-10", cache.empezar("2026-10"), "viejo")
        cache.vencer(setOf("2026-10"))
        val t = cache.empezar("2026-10")
        assertFalse(cache.terminar("2026-10", t, null))
        assertEquals("viejo", cache.datos("2026-10"))
        assertFalse(cache.fresco("2026-10"))
    }

    @Test
    fun unPedidoViejoNoPisaAlNuevo() {
        val cache = CacheMesAgenda<String>()
        val viejo = cache.empezar("2026-10")
        val nuevo = cache.empezar("2026-10")   // se reemplazó (reintento)
        assertTrue(cache.terminar("2026-10", nuevo, "nuevo"))
        assertFalse(cache.terminar("2026-10", viejo, "viejo"))
        assertEquals("nuevo", cache.datos("2026-10"))
    }

    @Test
    fun siVenceMientrasSeLeeQuedaParaReleer() {
        val cache = CacheMesAgenda<String>()
        val t = cache.empezar("2026-10")
        cache.vencer(setOf("2026-10"))          // llega un cambio con el pedido en vuelo
        assertFalse(cache.terminar("2026-10", t, "quizá viejo"))
        assertEquals("quizá viejo", cache.datos("2026-10"))   // se muestra…
        assertFalse(cache.fresco("2026-10"))                    // …pero se vuelve a pedir
        assertTrue(cache.terminar("2026-10", cache.empezar("2026-10"), "al día"))
    }

    // ── Realtime: qué meses vencer ──

    @Test
    fun mesesDeUnCambio() {
        assertEquals(setOf("2026-10"), mesesDeCambio("2026-10-15", null))
        assertEquals(setOf("2026-10", "2026-11"), mesesDeCambio("2026-11-02", "2026-10-30"))   // reprogramada
        assertEquals(setOf("2026-10"), mesesDeCambio("2026-10-15", "2026-10-01"))
        assertNull(mesesDeCambio(null, null))   // sin fecha: todos
        assertEquals(setOf("2026-10", "2026-11"), unirMeses(setOf("2026-10"), setOf("2026-11")))
        assertNull(unirMeses(setOf("2026-10"), null))
        assertNull(unirMeses(null, setOf("2026-10")))
        assertEquals(setOf("2026-10"), unirMeses(emptySet(), setOf("2026-10")))
    }
}
