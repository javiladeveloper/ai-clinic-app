package pe.saniape.app.data.staff

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Agendar una Sesión a un paciente sin tratamiento (psicología) desde Crear cita. */
class SesionSinTratamientoTest {

    private val psicologia = FlujoClinica(usaConsulta = false, usaEvaluacion = false)
    private val fisio = FlujoClinica()
    private val nutricion = FlujoClinica(usaConsulta = true, usaEvaluacion = false)

    @Test
    fun soloPsicologiaEntraDirectoASesiones() {
        assertTrue(flujoDirectoASesiones(psicologia))
        assertFalse(flujoDirectoASesiones(fisio))
        assertFalse(flujoDirectoASesiones(nutricion))
        assertFalse(flujoDirectoASesiones(FlujoClinica(usaConsulta = false, usaEvaluacion = true)))
    }

    @Test
    fun altaRapidaEnSesionSoloSiElFlujoEsDirecto() {
        assertTrue(altaRapidaPermitida("Sesión", puedeAgendar = true, directoASesiones = true))
        assertFalse(altaRapidaPermitida("Sesión", puedeAgendar = true, directoASesiones = false))
        assertFalse(altaRapidaPermitida("Sesión", puedeAgendar = false, directoASesiones = true))
        // Consulta/Evaluación: como siempre.
        assertTrue(altaRapidaPermitida("Consulta", puedeAgendar = true, directoASesiones = false))
        assertTrue(altaRapidaPermitida("Evaluación", puedeAgendar = true))
    }

    private fun aviso(
        tipo: String = "Sesión", directo: Boolean = true, elegido: Boolean = true, cargados: Boolean = true,
        hay: Boolean = false, puede: Boolean = true,
    ) = avisoSinTratamiento(tipo, directo, elegido, cargados, hay, puede)

    @Test
    fun ofreceCrearTratamientoSoloCuandoCorresponde() {
        assertEquals(AvisoSinTratamiento.OFRECER_CREAR, aviso())
        // Sin permiso de tratamientos: a quién pedírselo.
        assertEquals(AvisoSinTratamiento.PEDIR_A_QUIEN_PUEDE, aviso(puede = false))
        // Ya tiene tratamiento: se elige de la lista, sin aviso.
        assertEquals(AvisoSinTratamiento.NINGUNO, aviso(hay = true))
        // Clínicas con Consulta/Evaluación: sin cambios.
        assertEquals(AvisoSinTratamiento.NINGUNO, aviso(directo = false))
        // Otro tipo de cita, sin paciente, o la lista no llegó (no se afirma que no tenga).
        assertEquals(AvisoSinTratamiento.NINGUNO, aviso(tipo = "Consulta"))
        assertEquals(AvisoSinTratamiento.NINGUNO, aviso(elegido = false))
        assertEquals(AvisoSinTratamiento.NINGUNO, aviso(cargados = false))
    }

    @Test
    fun textosDelAvisoYDelGuardar() {
        assertEquals("Este paciente aún no tiene tratamiento", tituloSinTratamiento("paciente"))
        assertEquals("Este cliente aún no tiene tratamiento", tituloSinTratamiento("cliente"))
        assertTrue("pídeselo a su profesional o al Admin" in detalleSinTratamiento(AvisoSinTratamiento.PEDIR_A_QUIEN_PUEDE))
        assertTrue("Sesión #1" in detalleSinTratamiento(AvisoSinTratamiento.OFRECER_CREAR))
        assertNull(mensajeFaltaTratamiento(AvisoSinTratamiento.NINGUNO, "paciente"))
        assertEquals(
            "Este paciente aún no tiene tratamiento: créalo para agendarle la sesión.",
            mensajeFaltaTratamiento(AvisoSinTratamiento.OFRECER_CREAR, "paciente"),
        )
        assertTrue(mensajeFaltaTratamiento(AvisoSinTratamiento.PEDIR_A_QUIEN_PUEDE, "paciente")!!.contains("Admin"))
    }

    @Test
    fun elCreadoQuedaElegido() {
        val a = TratamientoRef("t1", "Terapia individual", "Paquete", null)
        val b = TratamientoRef("t2", "Terapia de pareja", "Paquete", null)
        assertEquals(b, tratamientoCreadoEnLista(listOf(a, b), "t2"))
        // Sin id (respuesta vieja): el único que haya; con varios, ninguno (que elija).
        assertEquals(a, tratamientoCreadoEnLista(listOf(a), null))
        assertNull(tratamientoCreadoEnLista(listOf(a, b), null))
        // Id que no está (aún no se ve): el único, si hay uno.
        assertEquals(a, tratamientoCreadoEnLista(listOf(a), "t9"))
    }
}
