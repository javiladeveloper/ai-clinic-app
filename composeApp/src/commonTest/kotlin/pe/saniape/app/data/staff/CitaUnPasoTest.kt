package pe.saniape.app.data.staff

import kotlin.coroutines.Continuation
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.coroutines.startCoroutine
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** «Crear tratamiento y agendar cita» en un toque (gemelo de lib/__tests__/cita-un-paso.test.ts). */
class CitaUnPasoTest {

    private fun estado(
        fecha: String = "2026-10-25", hora: String = "10:00", horaPasada: Boolean = false,
        regularizacion: Boolean = false, conflicto: String? = null, fueraDelBloque: String? = null,
    ) = estadoCitaUnPaso(fecha, hora, horaPasada, regularizacion, conflicto, fueraDelBloque)

    // ── estadoCitaUnPaso: qué ofrece el bloque «📅 La cita» del tratamiento ──

    @Test
    fun conFechaHoraYHorarioLibreEstaListo() {
        assertEquals(EstadoCitaUnPaso.LISTO, estado())
    }

    @Test
    fun sinHoraOSinFechaFaltaYSeEligeEnElBloque() {
        assertEquals(EstadoCitaUnPaso(EstadoCitaUnPaso.Estado.FALTA, "Falta la hora de la cita"), estado(hora = ""))
        assertEquals(EstadoCitaUnPaso.Estado.FALTA, estado(fecha = "").estado)
    }

    @Test
    fun horaPasadaFaltaSalvoQueMarquenQueYaOcurrio() {
        assertEquals(EstadoCitaUnPaso.Estado.FALTA, estado(horaPasada = true).estado)
        assertEquals(EstadoCitaUnPaso.Estado.LISTO, estado(horaPasada = true, regularizacion = true).estado)
    }

    @Test
    fun horarioOcupadoFaltaConElMotivoDeLaVerificacion() {
        assertEquals(
            EstadoCitaUnPaso(EstadoCitaUnPaso.Estado.FALTA, "Ana ya tiene una evaluación a esa hora"),
            estado(conflicto = "Ana ya tiene una evaluación a esa hora"),
        )
    }

    @Test
    fun unaRegularizacionNoLaFrenaElHorario() {
        assertEquals(
            EstadoCitaUnPaso.Estado.LISTO,
            estado(horaPasada = true, regularizacion = true, conflicto = "Fuera de su horario").estado,
        )
        // Marcada «ya ocurrió» pero a futuro: el horario sí frena (como la web).
        assertEquals(EstadoCitaUnPaso.Estado.FALTA, estado(regularizacion = true, conflicto = "Fuera de su horario").estado)
    }

    @Test
    fun loQueNoSeResuelveEnElBloqueGana() {
        assertEquals(
            EstadoCitaUnPaso(EstadoCitaUnPaso.Estado.NO, "Videollamada: el enlace no es válido"),
            estado(hora = "", conflicto = "x", fueraDelBloque = "Videollamada: el enlace no es válido"),
        )
    }

    // ── horaDeCitaYaPaso ──

    @Test
    fun horaPasadaSeMideEnLaZonaDeLaSede() {
        val hoy = "2026-10-24"
        assertTrue(horaDeCitaYaPaso("2026-10-23", "18:00", hoy, minutosAhora = 8 * 60))
        assertFalse(horaDeCitaYaPaso("2026-10-25", "07:00", hoy, minutosAhora = 23 * 60))
        assertTrue(horaDeCitaYaPaso(hoy, "09:30", hoy, minutosAhora = 10 * 60))
        // El mismo minuto todavía no pasó.
        assertFalse(horaDeCitaYaPaso(hoy, "10:00", hoy, minutosAhora = 10 * 60))
        assertFalse(horaDeCitaYaPaso(hoy, "10:30", hoy, minutosAhora = 10 * 60))
        // Sin hora no hay nada que juzgar (eso lo dice «Falta la hora»).
        assertFalse(horaDeCitaYaPaso(hoy, "", hoy, minutosAhora = 10 * 60))
    }

    // ── fechaCitaPorInicio: la fecha de inicio cambiada a mano ──

    private val hoy = "2026-10-24"

    @Test
    fun programadoParaMasAdelanteLaCitaSeVaAEseDia() {
        assertEquals("2026-10-30", fechaCitaPorInicio("2026-10-30", "2026-10-24", hoy))
    }

    @Test
    fun hoyTambienMueveUnaCitaQueEstabaOtroDia() {
        assertEquals(hoy, fechaCitaPorInicio(hoy, "2026-10-28", hoy))
    }

    @Test
    fun yaEmpezoLaCitaNoSeArrastraAlPasado() {
        assertNull(fechaCitaPorInicio("2026-09-01", "2026-10-25", hoy))
    }

    @Test
    fun elPacienteQueYaEstaAquiNoSeMueve() {
        assertNull(fechaCitaPorInicio("2026-10-30", hoy, hoy, ahoraAqui = true))
    }

    @Test
    fun mismaFechaOVaciaNadaQueMover() {
        assertNull(fechaCitaPorInicio("2026-10-25", "2026-10-25", hoy))
        assertNull(fechaCitaPorInicio("", "2026-10-25", hoy))
        assertNull(fechaCitaPorInicio(null, "2026-10-25", hoy))
    }

    // ── crearTratamientoYAgendar: el orden del toque único ──

    /** Corre un bloque suspend que no suspende de verdad (las dependencias son falsas). */
    private fun <T> correr(bloque: suspend () -> T): T {
        var res: Result<T>? = null
        bloque.startCoroutine(Continuation(EmptyCoroutineContext) { res = it })
        return res!!.getOrThrow()
    }

    private data class Creado(val id: String)

    private class Deps(
        val verificar: String? = null,
        val alta: ResultadoAltaTratamiento = ResultadoAltaTratamiento(ok = true, id = "t1"),
        val cita: String? = null,
        val citaLanza: Exception? = null,
    ) {
        val llamadas = mutableListOf<String>()
        var agendadoCon: Creado? = null

        suspend fun ejecutar(agendar: Boolean): ResultadoUnPaso<Creado> = crearTratamientoYAgendar(
            agendar = agendar,
            verificarCita = { llamadas += "verificar"; verificar },
            crearTratamiento = { llamadas += "crear"; alta },
            alCrear = { id -> llamadas += "alCrear"; Creado(id) },
            agendarCita = { c ->
                llamadas += "agendar"; agendadoCon = c
                citaLanza?.let { throw it }
                cita
            },
        )
    }

    private fun Deps.toque(agendar: Boolean = true) = correr { ejecutar(agendar) }

    @Test
    fun verificaCreaYRecienDespuesGuardaLaCita() {
        val d = Deps()
        assertEquals(ResultadoUnPaso.Ambos(Creado("t1")), d.toque())
        assertEquals(listOf("verificar", "crear", "alCrear", "agendar"), d.llamadas)
        assertEquals(Creado("t1"), d.agendadoCon)
    }

    @Test
    fun siLaCitaNoSePuedeAgendarNoSeCreaNada() {
        val d = Deps(verificar = "Fuera de su horario")
        assertEquals(ResultadoUnPaso.CitaInvalida("Fuera de su horario"), d.toque())
        assertEquals(listOf("verificar"), d.llamadas)
    }

    @Test
    fun soloCrearTratamientoNiVerificaNiGuardaLaCita() {
        val d = Deps()
        assertEquals(ResultadoUnPaso.SoloTratamiento(Creado("t1")), d.toque(agendar = false))
        assertEquals(listOf("crear", "alCrear"), d.llamadas)
    }

    @Test
    fun tratamientoQueNoSeCreaLaCitaNoSeIntenta() {
        val d = Deps(alta = ResultadoAltaTratamiento(ok = false, id = null, error = "Sin permiso"))
        assertEquals(ResultadoUnPaso.TratamientoFallido<Creado>("Sin permiso", null), d.toque())
        assertEquals(listOf("verificar", "crear"), d.llamadas)
    }

    @Test
    fun tratamientoCreadoAMediasQuedaEnLaCitaPeroNoSeAgenda() {
        val d = Deps(alta = ResultadoAltaTratamiento(ok = false, id = "t9", error = "Guardado parcial"))
        assertEquals(ResultadoUnPaso.TratamientoFallido("Guardado parcial", Creado("t9")), d.toque())
        assertEquals(listOf("verificar", "crear", "alCrear"), d.llamadas)
    }

    @Test
    fun sinSenalElTratamientoVaALaColaYLaCitaNoSeAgendaACiegas() {
        val d = Deps(alta = ResultadoAltaTratamiento(ok = true, id = null, encolada = true))
        assertEquals(ResultadoUnPaso.TratamientoEnCola, d.toque())
        assertEquals(listOf("verificar", "crear"), d.llamadas)
    }

    @Test
    fun laCitaFallaConElTratamientoYaCreadoSeInformaNuncaSeRecrea() {
        val d = Deps(cita = "No se pudo agendar. Intenta de nuevo.")
        assertEquals(ResultadoUnPaso.CitaFallida("No se pudo agendar. Intenta de nuevo.", Creado("t1")), d.toque())
        assertEquals(1, d.llamadas.count { it == "crear" })
    }

    @Test
    fun unaExcepcionAlGuardarLaCitaCuentaComoCitaFallida() {
        val d = Deps(citaLanza = IllegalStateException("red caída"))
        assertEquals(ResultadoUnPaso.CitaFallida("red caída", Creado("t1")), d.toque())
    }
}
