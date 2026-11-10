package pe.saniape.app.data.staff

import kotlinx.datetime.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

/**
 * Gemelo de lib/__tests__/sala-espera.test.ts (sala de espera de TODAS las
 * clínicas, 09/11/2026): mismos casos, mismo orden de los bloques.
 * `cambioSala` (las guardas del UPDATE) es del servidor: acá solo su resultado,
 * [aplicarAccionSala].
 */
class SalaEsperaTest {
    private val hoy = "2026-11-09"
    private val lima = "America/Lima"
    private val ahora = Instant.parse("2026-11-09T15:17:00Z")   // 10:17 en Lima

    private fun cita(
        id: String = "c1", fecha: String = hoy, hora: String = "10:00", estado: String = "Confirmada",
        terapeutaId: String? = "t1", terapeutaNombre: String? = "Dra. Ruiz", paciente: String? = "Ana Pérez",
        llegadaAt: String? = null, consultaAt: String? = null, noAsistio: Boolean = false,
    ) = CitaSalaEntrada(
        id = id, fecha = fecha, hora = hora, estado = estado, noAsistio = noAsistio, terapeutaId = terapeutaId,
        terapeutaNombre = terapeutaNombre, pacienteNombre = paciente, llegadaAt = llegadaAt, consultaAt = consultaAt,
    )

    private fun atencion(llegada: String? = null, triaje: String? = null, consulta: String? = null) =
        AtencionLlegada(id = null, citaId = "c1", llegadaAt = llegada, triajeAt = triaje, consultaInicioAt = consulta)

    /** Lo mínimo con momentos (ConMomentosSala de la web), con o sin fecha. */
    private data class M(
        override val id: String,
        override val fecha: String? = null,
        override val llegadaAt: String? = null,
        override val consultaAt: String? = null,
    ) : ConMomentosSala

    // ── estadoSala ──────────────────────────────────────────────────────────

    @Test
    fun porLlegarEsperandoYEnConsultaLaCitaDeHoy() {
        assertEquals(EstadoSala.POR_LLEGAR, estadoSala(cita(), null, hoy, lima))
        assertEquals(EstadoSala.POR_LLEGAR, estadoSala(cita(estado = "Pendiente"), null, hoy, lima))
        assertEquals(EstadoSala.ESPERANDO, estadoSala(cita(llegadaAt = "2026-11-09T15:05:00Z"), null, hoy, lima))
        assertEquals(
            EstadoSala.EN_CONSULTA,
            estadoSala(cita(llegadaAt = "2026-11-09T15:05:00Z", consultaAt = "2026-11-09T15:10:00Z"), null, hoy, lima),
        )
    }

    @Test
    fun fueraCompletadaCanceladaNoVinoUOtroDia() {
        val llego = "2026-11-09T15:05:00Z"
        assertEquals(EstadoSala.FUERA, estadoSala(cita(estado = "Completada", llegadaAt = llego), null, hoy, lima))
        assertEquals(EstadoSala.FUERA, estadoSala(cita(estado = "Cancelada", llegadaAt = llego), null, hoy, lima))
        assertEquals(EstadoSala.FUERA, estadoSala(cita(estado = "No asistió"), null, hoy, lima))
        assertEquals(EstadoSala.FUERA, estadoSala(cita(noAsistio = true, llegadaAt = llego), null, hoy, lima))
        assertEquals(EstadoSala.FUERA, estadoSala(cita(fecha = "2026-11-10"), null, hoy, lima))
    }

    @Test
    fun laAtencionCuentaYLaCitaMandaParaAtendido() {
        assertEquals(EstadoSala.ESPERANDO, estadoSala(cita(), atencion(llegada = "2026-11-09T15:00:00Z"), hoy, lima))
        assertEquals(EstadoSala.ESPERANDO, estadoSala(cita(), atencion(triaje = "2026-11-09T15:03:00Z"), hoy, lima))
        assertEquals(EstadoSala.EN_CONSULTA, estadoSala(cita(), atencion(consulta = "2026-11-09T15:12:00Z"), hoy, lima))
        assertEquals(EstadoSala.FUERA, estadoSala(cita(estado = "Completada"), atencion(llegada = "2026-11-09T15:00:00Z"), hoy, lima))
    }

    @Test
    fun elDiaEsElDeLaZonaDeLaSedeYUnaLlegadaDeOtroDiaNoCuenta() {
        val c = cita(llegadaAt = "2026-11-10T04:30:00Z")   // 23:30 del 9 en Lima; 00:30 del 10 en La Paz
        assertEquals(EstadoSala.ESPERANDO, estadoSala(c, null, hoy, lima))
        assertEquals(EstadoSala.POR_LLEGAR, estadoSala(c, null, hoy, "America/La_Paz"))
        assertEquals(EstadoSala.POR_LLEGAR, estadoSala(cita(llegadaAt = "2026-11-08T15:00:00Z"), null, hoy, lima))
        assertEquals("2026-11-09", diaDelInstante("2026-11-10T04:30:00Z", lima))
    }

    // ── atencionSalaDeCita: la tarjeta cuenta la misma llegada que la franja ──

    private fun etapaFila(c: CitaSalaEntrada, a: AtencionLlegada? = null): EtapaLlegada? =
        etapaLlegada(c.estado, c.noAsistio, atencionSalaDeCita(c, a, lima))

    @Test
    fun soloLaLlegadaDeLaCitaDeHoyEsEnEsperaEnLaFilaComoEnLaFranja() {
        val c = cita(llegadaAt = "2026-11-09T15:05:00Z")
        assertEquals(EtapaLlegada.LLEGO, etapaFila(c))
        assertTrue(enSalaDeEspera(etapaFila(c)))
        assertEquals(EstadoSala.ESPERANDO, estadoSala(c, null, hoy, lima))
        // Los minutos de la fila corren desde esa llegada (los mismos de la franja).
        val a = atencionSalaDeCita(c, null, lima)
        assertEquals(12, minutosEsperando(a?.llegadaAt ?: a?.triajeAt, ahora))
        assertEquals(12, armarSala(listOf(c), emptyMap(), hoy, lima, ahora)[0].esperando[0].minutos)
    }

    @Test
    fun unaLlegadaDeOtroDiaSeIgnoraSiguePorLlegar() {
        val c = cita(llegadaAt = "2026-11-08T15:05:00Z")
        assertNull(atencionSalaDeCita(c, null, lima))
        assertEquals(EtapaLlegada.POR_LLEGAR, etapaFila(c))
        assertFalse(enSalaDeEspera(etapaFila(c)))
        assertEquals(EstadoSala.POR_LLEGAR, estadoSala(c, null, hoy, lima))
        // El día es el de la zona de la sede: 23:30 del 9 en Lima es el 10 en La Paz.
        val tarde = cita(llegadaAt = "2026-11-10T04:30:00Z")
        assertEquals(EtapaLlegada.LLEGO, etapaLlegada(tarde.estado, tarde.noAsistio, atencionSalaDeCita(tarde, null, lima)))
        assertEquals(EtapaLlegada.POR_LLEGAR, etapaLlegada(tarde.estado, tarde.noAsistio, atencionSalaDeCita(tarde, null, "America/La_Paz")))
    }

    @Test
    fun laConsultaDeLaCitaDeHoyEsEnConsultaYLaAtencionGanaSiYaEmpezoLaGuiada() {
        val c = cita(llegadaAt = "2026-11-09T15:05:00Z", consultaAt = "2026-11-09T15:10:00Z")
        assertEquals(EtapaLlegada.EN_CONSULTA, etapaFila(c))
        assertEquals(EstadoSala.EN_CONSULTA, estadoSala(c, null, hoy, lima))
        val a = atencionSalaDeCita(c, atencion(consulta = "2026-11-09T15:08:00Z"), lima)
        assertEquals("2026-11-09T15:08:00Z", a?.consultaInicioAt)
    }

    @Test
    fun mezclaConLaAtencionLaLlegadaMasTempranaYElRestoIntacto() {
        val c = cita(llegadaAt = "2026-11-09T15:10:00Z")
        val at = AtencionLlegada(
            id = "a1", citaId = "c1", llegadaAt = "2026-11-09T15:02:00Z", triajeAt = "2026-11-09T15:06:00Z",
            presionSistolica = 120.0,
        )
        val a = assertNotNull(atencionSalaDeCita(c, at, lima))
        assertEquals("2026-11-09T15:02:00Z", a.llegadaAt)
        assertEquals("2026-11-09T15:06:00Z", a.triajeAt)
        assertEquals(120.0, a.presionSistolica)
        assertEquals("a1", a.id)
        assertEquals(EtapaLlegada.TRIAJE, etapaLlegada(c.estado, c.noAsistio, a))
    }

    @Test
    fun sinMomentosDeLaCitaDeSuDiaLaAtencionTalCual() {
        val at = atencion(llegada = "2026-11-09T15:02:00Z")
        assertSame(at, atencionSalaDeCita(cita(), at, lima))
        assertNull(atencionSalaDeCita(cita(), null, lima))
    }

    // ── armarSala ───────────────────────────────────────────────────────────

    private val citas = listOf(
        cita(id = "b2", terapeutaId = "tB", terapeutaNombre = "Bruno Díaz", paciente = "Luis", hora = "09:45", llegadaAt = "2026-11-09T15:10:00Z"),
        cita(id = "b1", terapeutaId = "tB", terapeutaNombre = "Bruno Díaz", paciente = "Rosa", hora = "10:30", llegadaAt = "2026-11-09T15:05:00Z"),
        cita(
            id = "b0", terapeutaId = "tB", terapeutaNombre = "Bruno Díaz", paciente = "Juan", hora = "09:30",
            llegadaAt = "2026-11-09T14:20:00Z", consultaAt = "2026-11-09T15:00:00Z",
        ),
        cita(id = "a1", terapeutaId = "tA", terapeutaNombre = "Ana Torres", paciente = "Carla", llegadaAt = "2026-11-09T15:12:00Z"),
        cita(id = "m1", terapeutaId = "tM", terapeutaNombre = null, paciente = null, llegadaAt = "2026-11-09T15:15:00Z"),
        cita(id = "s1", terapeutaId = null, terapeutaNombre = null, paciente = "Pedro", llegadaAt = "2026-11-09T15:16:00Z"),
        cita(id = "x1", terapeutaId = "tA", terapeutaNombre = "Ana Torres", paciente = "Por llegar"),
        cita(
            id = "x2", terapeutaId = "tA", terapeutaNombre = "Ana Torres", estado = "Completada", paciente = "Atendida",
            llegadaAt = "2026-11-09T14:00:00Z",
        ),
    )

    @Test
    fun porProfesionalElPropioPrimeroLuegoPorNombreYSinProfesionalAlFinal() {
        val g = armarSala(citas, emptyMap(), hoy, lima, ahora, miTerapeutaId = "tB", terapeutas = mapOf("tM" to "Mario Paz"))
        assertEquals(listOf("Bruno Díaz", "Ana Torres", "Mario Paz", SALA_SIN_PROFESIONAL), g.map { it.nombre })
        val sinMio = armarSala(citas, emptyMap(), hoy, lima, ahora)
        assertEquals(listOf("Ana Torres", "Bruno Díaz", SALA_PROFESIONAL_SIN_NOMBRE, SALA_SIN_PROFESIONAL), sinMio.map { it.nombre })
    }

    @Test
    fun sigueElQueLlegoPrimeroNoElDeLaHoraMasTempranaYLosMinutosCorrenDesdeLaLlegada() {
        val b = armarSala(citas, emptyMap(), hoy, lima, ahora, miTerapeutaId = "tB")[0]
        assertEquals(listOf("Rosa", "Luis"), b.esperando.map { it.paciente })
        assertEquals("Rosa", b.sigue?.paciente)
        assertEquals(12, b.sigue?.minutos)
        assertEquals(listOf("Juan"), b.enConsulta.map { it.paciente })
        assertNull(b.enConsulta[0].minutos)
        assertEquals("2026-11-09T15:00:00Z", b.enConsulta[0].consultaAt)
    }

    @Test
    fun ningunaFilaSinNombreYPorLlegarOAtendidasNoAparecen() {
        val g = armarSala(citas, emptyMap(), hoy, lima, ahora, terapeutas = mapOf("tM" to "Mario Paz"))
        val mario = g.first { it.terapeutaId == "tM" }
        assertEquals(listOf(SALA_PACIENTE_SIN_NOMBRE), mario.esperando.map { it.paciente })
        val todas = g.flatMap { it.enConsulta + it.esperando }.map { it.citaId }
        assertFalse("x1" in todas)
        assertFalse("x2" in todas)
        assertEquals(5, totalEsperando(g))
    }

    @Test
    fun mezclaLaCitaYLaAtencionLaLlegadaEfectivaEsLaMasTemprana() {
        val c = cita(llegadaAt = "2026-11-09T15:10:00Z")
        val g = armarSala(listOf(c), mapOf("c1" to atencion(llegada = "2026-11-09T15:02:00Z")), hoy, lima, ahora)
        assertEquals("2026-11-09T15:02:00Z", g[0].esperando[0].llegadaAt)
        assertEquals(15, g[0].esperando[0].minutos)
        val enConsulta = armarSala(listOf(c), mapOf("c1" to atencion(consulta = "2026-11-09T15:12:00Z")), hoy, lima, ahora)
        assertEquals(listOf("c1"), enConsulta[0].enConsulta.map { it.citaId })
    }

    @Test
    fun sinNadieEnLaSalaSinGrupos() {
        assertTrue(armarSala(listOf(cita()), emptyMap(), hoy, lima, ahora).isEmpty())
    }

    // ── aplicarAccionSala (idempotente) ─────────────────────────────────────

    private val t = "2026-11-09T15:20:00.000Z"
    private val nada = MomentosSala()
    private val llego = MomentosSala(llegadaAt = "2026-11-09T15:05:00Z")
    private val enConsulta = MomentosSala("2026-11-09T15:05:00Z", "2026-11-09T15:10:00Z")

    @Test
    fun llegoSoloLaPrimeraVez() {
        assertEquals(MomentosSala(llegadaAt = t), aplicarAccionSala(AccionSala.LLEGO, nada, t))
        assertSame(llego, aplicarAccionSala(AccionSala.LLEGO, llego, t))
    }

    @Test
    fun pasarMarcaLaConsultaYSiNoHabiaTambienLaLlegada() {
        assertEquals(MomentosSala(llegadaAt = t, consultaAt = t), aplicarAccionSala(AccionSala.PASAR, nada, t))
        assertEquals(MomentosSala(llego.llegadaAt, t), aplicarAccionSala(AccionSala.PASAR, llego, t))
        assertSame(enConsulta, aplicarAccionSala(AccionSala.PASAR, enConsulta, t))
    }

    @Test
    fun volverYDeshacer() {
        assertEquals(llego, aplicarAccionSala(AccionSala.VOLVER, enConsulta, t))
        assertSame(llego, aplicarAccionSala(AccionSala.VOLVER, llego, t))
        assertEquals(nada, aplicarAccionSala(AccionSala.DESHACER, enConsulta, t))
        assertSame(nada, aplicarAccionSala(AccionSala.DESHACER, nada, t))
    }

    @Test
    fun aplicarAccionSalaEsLoQueSeVeAlTocar() {
        assertEquals(MomentosSala(llegadaAt = t, consultaAt = null), aplicarAccionSala(AccionSala.LLEGO, nada, t))
        assertEquals(llego, aplicarAccionSala(AccionSala.LLEGO, llego, t))
        assertEquals(MomentosSala(llegadaAt = llego.llegadaAt, consultaAt = t), aplicarAccionSala(AccionSala.PASAR, llego, t))
        assertEquals(llego, aplicarAccionSala(AccionSala.VOLVER, enConsulta, t))
        assertEquals(nada, aplicarAccionSala(AccionSala.DESHACER, enConsulta, t))
    }

    @Test
    fun esAccionSala() {
        assertEquals(AccionSala.PASAR, AccionSala.deClave("pasar"))
        assertNull(AccionSala.deClave("borrar"))
        assertNull(AccionSala.deClave(null))
        assertEquals("pasar", AccionSala.PASAR.clave)
    }

    // ── cambio optimista ────────────────────────────────────────────────────

    @Test
    fun aplicarPendientesPisaLaCitaConLoTocadoSinPendientesLaMismaLista() {
        val lista = listOf(cita(id = "a"))
        assertSame(lista, aplicarPendientes(lista, emptyMap()))
        val p = mapOf("a" to PendienteSala(MomentosSala(llegadaAt = "X"), confirmadaEn = null))
        val r = aplicarPendientes(lista, p)[0]
        assertEquals("X", r.llegadaAt)
        assertNull(r.consultaAt)
        assertEquals(lista[0].copy(llegadaAt = "X"), r)
    }

    @Test
    fun podarPendientesSeVaLoQueLaListaYaTraeYLoConfirmadoViejoQuedaLoOptimista() {
        val citas = listOf(
            M("a", llegadaAt = "2026-11-09T15:00:00.123+00:00"),
            M("b"),
        )
        val p = mapOf(
            "a" to PendienteSala(MomentosSala(llegadaAt = "2026-11-09T15:00:00Z"), confirmadaEn = 1000),
            "b" to PendienteSala(MomentosSala(llegadaAt = "2026-11-09T15:01:00Z"), confirmadaEn = null),
            "c" to PendienteSala(MomentosSala(llegadaAt = "2026-11-09T15:02:00Z"), confirmadaEn = 1000),
        )
        assertEquals(listOf("b"), podarPendientes(citas, p, 1000 + VIDA_PENDIENTE_MS + 1).keys.toList())
        val quieto = mapOf("b" to p.getValue("b"))
        assertSame(quieto, podarPendientes(citas, quieto, 2000))
    }

    @Test
    fun momentosDeSuDiaSoloLosDelDiaDeLaCitaSinFechaTalCual() {
        val ayer = "2026-11-08T15:00:00Z"
        val deHoy = "2026-11-09T15:00:00Z"
        assertEquals(MomentosSala(null, null), momentosDeSuDia(M("a", fecha = hoy, llegadaAt = ayer, consultaAt = ayer), lima))
        assertEquals(MomentosSala(deHoy, null), momentosDeSuDia(M("a", fecha = hoy, llegadaAt = deHoy), lima))
        assertEquals(MomentosSala(ayer, null), momentosDeSuDia(M("a", llegadaAt = ayer), lima))
    }

    @Test
    fun reprogramadaConLaLlegadaDeOtroDiaLlegoSeVeAlTocarYLaPodaComparaElEstadoDelDia() {
        val reprog = M("r", fecha = hoy, llegadaAt = "2026-11-08T15:00:00Z")
        // La base del optimista, como la ve el servidor: no llegó hoy → «Llegó» cambia algo.
        assertEquals(MomentosSala(llegadaAt = t, consultaAt = null), aplicarAccionSala(AccionSala.LLEGO, momentosDeSuDia(reprog, lima), t))
        // La lista todavía trae la llegada de ayer: NO es «ya llegó» → el pendiente queda.
        val p = mapOf("r" to PendienteSala(MomentosSala(llegadaAt = t), confirmadaEn = null))
        assertSame(p, podarPendientes(listOf(reprog), p, 2000, lima))
        // La lista recargada ya trae la de hoy → se va.
        assertEquals(emptyMap<String, PendienteSala>(), podarPendientes(listOf(reprog.copy(llegadaAt = t)), p, 2000, lima))
    }

    @Test
    fun mismoEstadoSalaMiraLlegoEnConsultaNoLaHoraExacta() {
        assertTrue(mismoEstadoSala(MomentosSala(llegadaAt = "A"), MomentosSala(llegadaAt = "B")))
        assertFalse(mismoEstadoSala(MomentosSala(llegadaAt = "A"), MomentosSala(llegadaAt = "A", consultaAt = "C")))
    }

    // ── textos (siempre con el nombre) ──────────────────────────────────────

    @Test
    fun cadaAccionNombraAlPaciente() {
        assertEquals("Ana Pérez en sala de espera", textoAccionSala(AccionSala.LLEGO, "Ana Pérez"))
        assertEquals("Ana Pérez pasó a consulta", textoAccionSala(AccionSala.PASAR, "Ana Pérez"))
        assertEquals("Ana Pérez volvió a la sala de espera", textoAccionSala(AccionSala.VOLVER, "Ana Pérez"))
        assertEquals("Se quitó la llegada de Ana Pérez", textoAccionSala(AccionSala.DESHACER, "Ana Pérez"))
    }

    @Test
    fun yAlProfesionalSiLaCitaLoTiene() {
        assertEquals("Ana Pérez en sala de espera con Dr. Mamani", textoAccionSala(AccionSala.LLEGO, "Ana Pérez", "Dr. Mamani"))
        assertEquals("Ana Pérez pasó a consulta con Lic. Kathy", textoAccionSala(AccionSala.PASAR, "Ana Pérez", " Lic. Kathy "))
        assertEquals("Ana Pérez volvió a la sala de espera con Dr. Mamani", textoAccionSala(AccionSala.VOLVER, "Ana Pérez", "Dr. Mamani"))
        assertEquals("Se quitó la llegada de Ana Pérez con Dr. Mamani", textoAccionSala(AccionSala.DESHACER, "Ana Pérez", "Dr. Mamani"))
        assertEquals("Paciente sin nombre en sala de espera", textoAccionSala(AccionSala.LLEGO, SALA_PACIENTE_SIN_NOMBRE, null))
        assertEquals("Ana Pérez en sala de espera", textoAccionSala(AccionSala.LLEGO, "Ana Pérez", "  "))
    }

    @Test
    fun esperaYHora() {
        assertEquals("", esperaTexto(null))
        assertEquals("recién llegó", esperaTexto(0))
        assertEquals("12 min", esperaTexto(12))
        assertEquals("1 h 05 min", esperaTexto(65))
        assertEquals("2 h", esperaTexto(120))
        assertEquals("10:20", horaDelInstante("2026-11-09T15:20:00Z", lima))
        assertEquals("11:20", horaDelInstante("2026-11-09T15:20:00Z", "America/La_Paz"))
        assertNull(horaDelInstante(null, lima))
    }
}
