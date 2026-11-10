package pe.saniape.app.data.staff

import kotlinx.datetime.Instant
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** «Sigue» del Inicio: la agenda de hoy de /api/dashboard/stats → la sala de espera (09/11/2026). */
class SalaInicioTest {
    private fun obj(json: String) = Json.parseToJsonElement(json).jsonObject

    @Test
    fun leeLaCitaDeLaAgendaConSuSala() {
        val c = citaAgendaDeJson(obj("""
            { "id": "c1", "hora": "10:00", "estado": "Confirmada", "paciente": "Ana Pérez", "procedimiento": "Sesión",
              "yaPago": true, "tipo": "Sesión", "terapeutaId": "t1", "terapeutaNombre": "Dra. Ruiz", "noAsistio": false,
              "llegadaAt": "2026-11-09T15:05:00+00:00", "consultaAt": null }
        """))!!
        assertEquals("Ana Pérez", c.paciente)
        assertEquals("t1", c.terapeutaId)
        assertEquals("Dra. Ruiz", c.terapeutaNombre)
        assertEquals("2026-11-09T15:05:00+00:00", c.llegadaAt)
        assertNull(c.consultaAt)
        assertTrue(c.yaPago)
        assertFalse(c.noAsistio)
        assertEquals("Sesión", c.tipo)
        assertNull(citaAgendaDeJson(obj("""{ "hora": "10:00" }""")))
        // Servidor viejo (sin las claves nuevas): la cita se lee igual, sin sala.
        val vieja = citaAgendaDeJson(obj("""{ "id": "c2", "hora": "11:00", "estado": "Pendiente", "paciente": "Luis" }"""))!!
        assertNull(vieja.llegadaAt)
        assertNull(vieja.terapeutaId)
        assertFalse(vieja.yaPago)
        assertEquals("Consulta", vieja.procedimiento)
        // «No vino» marcado.
        assertTrue(citaAgendaDeJson(obj("""{ "id": "c3", "noAsistio": true }"""))!!.noAsistio)
    }

    @Test
    fun leeLaColaDeLaAtencion() {
        val cola = colaDeJson(obj("""{ "c1": { "llegada_at": "2026-11-09T15:00:00Z", "triaje_at": null, "consulta_inicio_at": "2026-11-09T15:10:00Z" } }"""))
        assertEquals("2026-11-09T15:00:00Z", cola["c1"]?.llegadaAt)
        assertEquals("2026-11-09T15:10:00Z", cola["c1"]?.consultaInicioAt)
        assertNull(cola["c1"]?.triajeAt)
        assertEquals("c1", cola["c1"]?.citaId)
        assertTrue(colaDeJson(null).isEmpty())
    }

    @Test
    fun armaLaSalaDelInicio() {
        val agenda = listOf(
            CitaAgenda(id = "c1", hora = "10:30", estado = "Confirmada", paciente = "Rosa", procedimiento = "Sesión",
                terapeutaId = "t1", terapeutaNombre = "Dra. Ruiz", llegadaAt = "2026-11-09T15:05:00Z"),
            CitaAgenda(id = "c2", hora = "09:00", estado = "Confirmada", paciente = "Juan", procedimiento = "Sesión",
                terapeutaId = "t1", terapeutaNombre = "Dra. Ruiz"),
        )
        val g = gruposSalaDeAgenda(agenda, mapOf("c2" to AtencionLlegada(id = null, citaId = "c2", consultaInicioAt = "2026-11-09T15:10:00Z")),
            "2026-11-09", "America/Lima", Instant.parse("2026-11-09T15:17:00Z"), "t1")
        // El grupo lleva el nombre del profesional; cada fila, el del paciente.
        assertEquals("Dra. Ruiz", g.single().nombre)
        assertEquals("Rosa", g.single().sigue?.paciente)
        assertEquals(12, g.single().sigue?.minutos)
        assertEquals(listOf("Juan"), g.single().enConsulta.map { it.paciente })
    }

    @Test
    fun loPasadoEnEsteTelefonoSeVeAlInstante() {
        val agenda = listOf(
            CitaAgenda(id = "c1", hora = "10:30", estado = "Confirmada", paciente = "Rosa", procedimiento = "Sesión",
                terapeutaId = "t1", terapeutaNombre = "Dra. Ruiz", llegadaAt = "2026-11-09T15:05:00Z"),
        )
        val pendientes = mapOf("c1" to PendienteSala(MomentosSala("2026-11-09T15:05:00Z", "2026-11-09T15:20:00Z")))
        val g = gruposSalaDeAgenda(agenda, emptyMap(), "2026-11-09", "America/Lima",
            Instant.parse("2026-11-09T15:21:00Z"), null, pendientes)
        assertNull(g.single().sigue)
        assertEquals(listOf("Rosa"), g.single().enConsulta.map { it.paciente })
        assertEquals("2026-11-09T15:20:00Z", g.single().enConsulta.single().consultaAt)
    }

    @Test
    fun sinLlegadasNiSalaMedicaElInicioQuedaIgual() {
        // DALU sin «Llegó»: ni grupos ni «Sigue» visible.
        val hoy = "2026-11-09"
        val agenda = listOf(
            CitaAgenda(id = "c1", hora = "10:30", estado = "Confirmada", paciente = "Rosa", procedimiento = "Sesión", terapeutaId = "t1"),
        )
        val entradas = agenda.map { entradaSalaDeAgenda(it, hoy) }
        assertFalse(sigueVisibleEnInicio(false, entradas, hoy, "America/Lima"))
        assertTrue(gruposSalaDeAgenda(agenda, emptyMap(), hoy, "America/Lima", Instant.parse("2026-11-09T15:21:00Z"), null).isEmpty())
        // Con la primera llegada de hoy, sí.
        val conLlegada = entradas.map { it.copy(llegadaAt = "2026-11-09T15:05:00Z") }
        assertTrue(sigueVisibleEnInicio(false, conLlegada, hoy, "America/Lima"))
        // Una llegada de AYER no cuenta.
        val deAyer = entradas.map { it.copy(llegadaAt = "2026-11-08T15:05:00Z") }
        assertFalse(sigueVisibleEnInicio(false, deAyer, hoy, "America/Lima"))
    }

    @Test
    fun laEntradaDeLaAgendaLlevaPacienteYProfesional() {
        val e = entradaSalaDeAgenda(
            CitaAgenda(id = "c1", hora = "10:30", estado = "Confirmada", paciente = "Rosa", procedimiento = "Sesión",
                tipo = "Sesión", terapeutaId = "t1", terapeutaNombre = "Dra. Ruiz", noAsistio = true,
                llegadaAt = "2026-11-09T15:05:00Z", consultaAt = "2026-11-09T15:10:00Z"),
            "2026-11-09",
        )
        assertEquals("2026-11-09", e.fecha)
        assertEquals("Rosa", e.pacienteNombre)
        assertEquals("Dra. Ruiz", e.terapeutaNombre)
        assertTrue(e.noAsistio)
        assertEquals("2026-11-09T15:10:00Z", e.consultaAt)
        // El aviso del ▶ Pasar nombra a los dos.
        assertEquals("Rosa pasó a consulta con Dra. Ruiz",
            textoAccionSala(AccionSala.PASAR, nombrePacienteSala(e.pacienteNombre), profesionalSala(e, null).nombre))
    }
}
