package pe.saniape.app.data.staff

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Rendimiento de Pacientes ↔ ficha (DALU, 23/10/2026): frescura, tandas, Realtime en ráfaga. */
class RendimientoPacientesTest {

    // ── Frescura de la lista ──

    @Test
    fun listaFrescaMenosDeUnMinuto() {
        assertFalse(listaFresca(null, 10_000))                 // nunca se cargó
        assertTrue(listaFresca(10_000, 10_000))                // recién cargada
        assertTrue(listaFresca(10_000, 69_999))                // 59,9 s
        assertFalse(listaFresca(10_000, 70_000))               // 60 s: ya es vieja
        assertFalse(listaFresca(10_000, 5_000))                // reloj hacia atrás: vieja
        assertTrue(listaFresca(0, 4_000, ventanaMs = 5_000))
    }

    @Test
    fun vueltaDeLaFichaReleeSoloLoNecesario() {
        // Lista fresca y nada cambió: nada (antes: la lista entera).
        assertEquals(RecargaLista.NADA, recargaAlVolverDeFicha(huboCambios = false, fresca = true, pacienteId = "p1"))
        // Se completó una sesión: solo ese paciente.
        assertEquals(RecargaLista.SOLO_PACIENTE, recargaAlVolverDeFicha(huboCambios = true, fresca = true, pacienteId = "p1"))
        // Lista vieja: entera, haya cambios o no.
        assertEquals(RecargaLista.COMPLETA, recargaAlVolverDeFicha(huboCambios = false, fresca = false, pacienteId = "p1"))
        assertEquals(RecargaLista.COMPLETA, recargaAlVolverDeFicha(huboCambios = true, fresca = false, pacienteId = "p1"))
        // Cambios sin saber de quién: entera.
        assertEquals(RecargaLista.COMPLETA, recargaAlVolverDeFicha(huboCambios = true, fresca = true, pacienteId = null))
    }

    // ── Lista del profesional ──

    @Test
    fun tandasSinRepetidosNiVacios() {
        val ids = (1..250).map { "id$it" } + listOf("id1", "", " ")
        val tandas = partirEnTandas(ids, 120)
        assertEquals(listOf(120, 120, 10), tandas.map { it.size })
        assertEquals(250, tandas.flatten().toSet().size)
        assertTrue(partirEnTandas(emptyList()).isEmpty())
    }

    @Test
    fun idsVerificadosAceptaSoloLoDelProfesional() {
        val yo = "kathy"
        val ok = listOf(
            FilaAtiende("p1", listOf("kathy")),
            FilaAtiende("p2", listOf("kathy", "kathy")),
        )
        assertEquals(setOf("p1", "p2"), idsVerificados(ok, yo))
        assertEquals(emptySet(), idsVerificados(emptyList(), yo))
        // El servidor ignoró el filtro embebido: trae lo de otro profesional → no confiable.
        assertNull(idsVerificados(ok + FilaAtiende("p3", listOf("rodas")), yo))
        // `!inner` sin nada embebido → no confiable.
        assertNull(idsVerificados(ok + FilaAtiende("p4", emptyList()), yo))
        // Fila sin id → no confiable.
        assertNull(idsVerificados(listOf(FilaAtiende(null, listOf("kathy"))), yo))
        // Respuesta cortada en el tope del servidor → no confiable.
        assertNull(idsVerificados(List(1000) { FilaAtiende("p$it", listOf("kathy")) }, yo))
    }

    // ── Catálogos recordados ──

    @Test
    fun cacheCortaPorClinicaYVigencia() {
        val c = CacheCorta<List<String>>(vigenciaMs = 1_000)
        assertNull(c.leer("dalu", 0))
        c.guardar("dalu", 100, listOf("Fisioterapia"))
        assertEquals(listOf("Fisioterapia"), c.leer("dalu", 900))
        assertNull(c.leer("otra", 900))      // otra clínica: no
        assertNull(c.leer("dalu", 1_100))    // vencida
    }

    // ── Realtime de la ficha ──

    @Test
    fun eventoSoloDelPacienteAbierto() {
        val trats = setOf("t1", "t2")
        // citas / tratamientos: por paciente_id (fila nueva o vieja).
        assertTrue(eventoEsDelPaciente("citas", "p1", null, "p1", trats))
        assertFalse(eventoEsDelPaciente("citas", "p9", null, "p1", trats))
        assertTrue(eventoEsDelPaciente("tratamientos", "p9", "p1", "p1", trats))   // se movió de él a otro
        // sesiones: por tratamiento_id contra los tratamientos del paciente.
        assertTrue(eventoEsDelPaciente("sesiones", "t2", null, "p1", trats))
        assertFalse(eventoEsDelPaciente("sesiones", "t9", "t8", "p1", trats))
        // DELETE sin la fila completa: no se sabe → cuenta (no perder un cambio).
        assertTrue(eventoEsDelPaciente("citas", null, null, "p1", trats))
        assertTrue(eventoEsDelPaciente("sesiones", "null", "", "p1", trats))
        assertEquals("tratamiento_id", columnaDuenoEvento("sesiones"))
        assertEquals("paciente_id", columnaDuenoEvento("citas"))
    }

    @Test
    fun rafagaJuntaLosEventosEnUnaRecarga() {
        val r = RafagaEventos(esperaMs = 800)
        assertNull(r.faltan(0))                 // nada pendiente
        r.evento(1_000)
        assertEquals(800, r.faltan(1_000))
        r.evento(1_300)                         // otro evento: corre la espera
        r.evento(1_500)
        assertEquals(300, r.faltan(2_000))
        assertEquals(0, r.faltan(2_300))        // 800 ms sin eventos: ya
        assertEquals(0, r.faltan(9_999))
        assertEquals(1_500, r.disparar())       // una sola recarga, con la hora del último
        assertNull(r.disparar())
        assertNull(r.faltan(9_999))
    }

    @Test
    fun accionRealtimeNoRecargaLoQueYaSeCargo() {
        // Una carga empezó DESPUÉS del último evento (el propio completar): ya lo trae.
        assertEquals(AccionRealtime.YA_INCLUIDO, accionRealtime(ultimoEventoMs = 1_000, ultimaCargaIniciadaMs = 1_200, dialogoAbierto = false))
        assertEquals(AccionRealtime.YA_INCLUIDO, accionRealtime(1_000, 1_000, dialogoAbierto = true))
        // Cambio de otro, después de la última carga: recargar.
        assertEquals(AccionRealtime.RECARGAR, accionRealtime(1_000, 500, dialogoAbierto = false))
        assertEquals(AccionRealtime.RECARGAR, accionRealtime(1_000, null, dialogoAbierto = false))
        // …pero con el cierre de sesión abierto se espera a que se cierre.
        assertEquals(AccionRealtime.POSPONER, accionRealtime(1_000, 500, dialogoAbierto = true))
    }
}
