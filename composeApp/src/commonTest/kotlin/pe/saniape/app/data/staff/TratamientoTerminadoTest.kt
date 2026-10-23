package pe.saniape.app.data.staff

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Sesión que cerró el plan: Ampliar / Dar de alta y el 💰 del paquete (gemelo de lib/tratamiento-terminado, web). */
class TratamientoTerminadoTest {

    private fun t(estado: String? = "Activo", total: Int? = 3, hechas: Int? = 3) = TratamientoParaTerminar(estado, total, hechas)

    @Test
    fun terminadoConTodasLasSesionesHechas() {
        assertTrue(tratamientoTerminado(t()))
        // Al completar la última pasa solo a Completado: también cuenta.
        assertTrue(tratamientoTerminado(t(estado = "Completado")))
        assertFalse(tratamientoTerminado(t(hechas = 2)))
        // Sin contador (pago único / unidades) o ya cerrado: no.
        assertFalse(tratamientoTerminado(t(total = null)))
        assertFalse(tratamientoTerminado(t(total = 0, hechas = 0)))
        assertFalse(tratamientoTerminado(t(estado = "Alta")))
        assertFalse(tratamientoTerminado(t(estado = "Cancelado")))
        assertFalse(tratamientoTerminado(null))
    }

    private fun acciones(
        tipo: String? = "Sesión", estado: String? = "Completada", trat: TratamientoParaTerminar? = t(),
        puede: Boolean = true, inactivo: Boolean = false, evalPsico: Boolean = false, numero: Int? = null,
    ) = accionesSesionTerminada(tipo, estado, trat, puede, inactivo, evalPsico, numero)

    @Test
    fun soloLaSesionCompletadaQueCerroElPlan() {
        assertEquals(AccionesTerminado(ampliar = true, alta = true), acciones())
        assertTrue(acciones().alguna)
        // Pendiente, otra cita o el plan aún con sesiones: nada.
        assertFalse(acciones(estado = "Planificada").alguna)
        assertFalse(acciones(tipo = "Consulta").alguna)
        assertFalse(acciones(trat = t(hechas = 1)).alguna)
        // Sin tipo (lista de sesiones): vale igual.
        assertTrue(acciones(tipo = null).alguna)
        // Con el número: solo la última (3/3), no la #1 ni la #2.
        assertTrue(acciones(numero = 3).alguna)
        assertFalse(acciones(numero = 2).alguna)
        assertTrue(acciones(numero = null).alguna)
    }

    @Test
    fun permisosYFichaDeBaja() {
        assertFalse(acciones(puede = false).alguna)
        assertFalse(acciones(inactivo = true).alguna)
        // La evaluación psicológica no se amplía así, pero sí se da de alta.
        assertEquals(AccionesTerminado(ampliar = false, alta = true), acciones(evalPsico = true))
    }

    private fun pago(estado: String = "debe", deuda: Double? = 400.0, esPaquete: Boolean = false) =
        EstadoPagoCita(estado = estado, deuda = deuda, etiqueta = "x", esPaquete = esPaquete)

    @Test
    fun cobroDelPaqueteSoloEnLaSesionAtendidaConDeuda() {
        assertTrue(paqueteCobrableEnAgenda("Sesión", "t1", "Completada", "Paquete", pago()))
        assertTrue(paqueteCobrableEnAgenda("Sesión", "t1", "Completada", null, pago(estado = "parcial", esPaquete = true)))
        // Pendiente (decisión DALU 2026-10-14), sin deuda, pagado o no es paquete: no.
        assertFalse(paqueteCobrableEnAgenda("Sesión", "t1", "Confirmada", "Paquete", pago()))
        assertFalse(paqueteCobrableEnAgenda("Sesión", "t1", "Completada", "Paquete", pago(deuda = 0.0)))
        assertFalse(paqueteCobrableEnAgenda("Sesión", "t1", "Completada", "Paquete", pago(estado = "pagado")))
        assertFalse(paqueteCobrableEnAgenda("Sesión", "t1", "Completada", "Sesión suelta", pago()))
        assertFalse(paqueteCobrableEnAgenda("Consulta", "t1", "Completada", "Paquete", pago()))
        assertFalse(paqueteCobrableEnAgenda("Sesión", null, "Completada", "Paquete", pago()))
        assertFalse(paqueteCobrableEnAgenda("Sesión", "t1", "Completada", "Paquete", null))
    }

    @Test
    fun abonoNuncaPasaDeLaDeuda() {
        assertEquals(400.0, montoAbonoPaquete(400.0, 400.0))
        assertEquals(150.5, montoAbonoPaquete(150.5, 400.0))
        assertNull(montoAbonoPaquete(400.01, 400.0))
        assertNull(montoAbonoPaquete(0.0, 400.0))
        assertNull(montoAbonoPaquete(-5.0, 400.0))
        assertNull(montoAbonoPaquete(10.005, 400.0))
        assertNull(montoAbonoPaquete(Double.NaN, 400.0))
    }

    @Test
    fun laFichaVuelveAComprobar() {
        assertTrue(puedeAbrirAccionTratamiento("Activo", puedeSesiones = true, fichaInactiva = false))
        assertTrue(puedeAbrirAccionTratamiento("Completado", puedeSesiones = true, fichaInactiva = false))
        assertFalse(puedeAbrirAccionTratamiento("Alta", puedeSesiones = true, fichaInactiva = false))
        assertFalse(puedeAbrirAccionTratamiento("Activo", puedeSesiones = false, fichaInactiva = false))
        assertFalse(puedeAbrirAccionTratamiento("Activo", puedeSesiones = true, fichaInactiva = true))
    }
}
