package pe.saniape.app.data.staff

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Abrir la cita que se tocó (aviso de cita nueva, Inicio): reporte DALU 24/10/2026. */
class CitaPedidaTest {
    private val katherine = "t-kath"
    private val otro = "t-otro"

    private fun cita(id: String, fecha: String = "2026-10-27", ter: String? = katherine, estado: String = "Confirmada") = CitaStaff(
        id = id, fecha = fecha, hora = "09:00", estado = estado, tipo = "Sesión",
        costo = null, duracion = null, origen = null, confirmadaPorPaciente = false, numeroSesion = null,
        terapeutaId = ter, terapeutaNombre = null, pacienteId = "p", pacienteNombre = "P", pacienteTelefono = null,
        tratamientoId = null, procedimiento = null, especialidadId = null, notaRecepcion = null,
    )

    // ── resolverCitaPedida ──

    @Test fun enLaListaDelDiaSeAbre() {
        val c = cita("c1")
        assertEquals(PasoCitaPedida.Abrir(c), resolverCitaPedida(CitaPedida("c1", "2026-10-27"), "2026-10-27", listOf(cita("c0"), c)))
    }

    @Test fun siYaEstaEnLaListaSeAbreAunqueElDiaSigaCargando() {
        // Inicio → agenda que ya tenía hoy cargado: no hay que esperar la recarga.
        val c = cita("c1")
        assertEquals(PasoCitaPedida.Abrir(c), resolverCitaPedida(CitaPedida("c1", "2026-10-27"), null, listOf(c)))
    }

    @Test fun mientrasNoCargueNingunDiaEspera() =
        assertEquals(PasoCitaPedida.Esperar, resolverCitaPedida(CitaPedida("c1", "2026-10-27"), null, emptyList()))

    @Test fun conOtroDiaCargadoEsperaAlDeLaCita() =
        // La agenda abrió en hoy y aún no llega el día del aviso: su ausencia no dice nada.
        assertEquals(PasoCitaPedida.Esperar, resolverCitaPedida(CitaPedida("c1", "2026-10-27"), "2026-10-24", listOf(cita("c9", "2026-10-24"))))

    @Test fun cargadoElDiaSinLaCitaSeBuscaPorId() =
        // Filtro de profesional, cita de equipo, otra sede, se movió de día…
        assertEquals(PasoCitaPedida.BuscarPorId, resolverCitaPedida(CitaPedida("c1", "2026-10-27"), "2026-10-27", listOf(cita("c2"))))

    @Test fun diaVacioSeBuscaPorId() =
        assertEquals(PasoCitaPedida.BuscarPorId, resolverCitaPedida(CitaPedida("c1", "2026-10-27"), "2026-10-27", emptyList()))

    @Test fun sinFechaBastaConCualquierDiaCargado() {
        assertEquals(PasoCitaPedida.Esperar, resolverCitaPedida(CitaPedida("c1", null), null, emptyList()))
        assertEquals(PasoCitaPedida.BuscarPorId, resolverCitaPedida(CitaPedida("c1", null), "2026-10-24", emptyList()))
    }

    @Test fun laCanceladaTambienSeAbre() {
        // La lista cruda trae las canceladas: se muestra su detalle (dice «Cancelada»).
        val c = cita("c1", estado = "Cancelada")
        assertEquals(PasoCitaPedida.Abrir(c), resolverCitaPedida(CitaPedida("c1", "2026-10-27"), "2026-10-27", listOf(c)))
    }

    // ── destinoCitaLeida ──

    @Test fun noExisteAvisa() =
        assertEquals(DestinoCitaLeida.Avisar(AVISO_CITA_NO_ENCONTRADA), destinoCitaLeida(null, katherine))

    @Test fun sinAgendaVinculadaSeAbreCualquiera() {
        // Recepción / admin: ve toda la agenda de la clínica.
        val c = cita("c1", ter = otro)
        assertEquals(DestinoCitaLeida.Abrir(c), destinoCitaLeida(c, null))
    }

    @Test fun laMiaSeAbre() {
        val c = cita("c1", ter = katherine)
        assertEquals(DestinoCitaLeida.Abrir(c), destinoCitaLeida(c, katherine))
    }

    @Test fun deOtroProfesionalPeroEstoyEnElEquipoSeAbre() {
        val c = cita("c1", ter = otro)
        assertEquals(DestinoCitaLeida.Abrir(c), destinoCitaLeida(c, katherine, equipo = listOf("t-x", katherine)))
    }

    @Test fun deOtroProfesionalSinSerDelEquipoAvisa() =
        assertEquals(DestinoCitaLeida.Avisar(AVISO_CITA_NO_ENCONTRADA), destinoCitaLeida(cita("c1", ter = otro), katherine, equipo = listOf("t-x")))

    @Test fun sinProfesionalYConAgendaVinculadaAvisa() =
        // Cita sin asignar: el profesional vinculado no la ve en su agenda.
        assertEquals(DestinoCitaLeida.Avisar(AVISO_CITA_NO_ENCONTRADA), destinoCitaLeida(cita("c1", ter = null), katherine))

    // ── hayQueLeerEquipo ──

    @Test fun elEquipoSoloSeLeeSiLaCitaEsDeOtroConAgendaVinculada() {
        assertFalse(hayQueLeerEquipo(null, katherine))
        assertFalse(hayQueLeerEquipo(cita("c1", ter = otro), null))
        assertFalse(hayQueLeerEquipo(cita("c1", ter = katherine), katherine))
        assertTrue(hayQueLeerEquipo(cita("c1", ter = otro), katherine))
        assertTrue(hayQueLeerEquipo(cita("c1", ter = null), katherine))
    }
}
