package pe.saniape.app.data.staff

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Nueva cita de Sesión para un paciente sin tratamiento (gemelo de lib/cita-sin-tratamiento.ts). */
class SesionSinTratamientoTest {

    @Test
    fun entraSoloPorSesionMiraLosTiposQueSeOfrecen() {
        assertTrue(entraSoloPorSesion(listOf("Sesión")))
        assertFalse(entraSoloPorSesion(listOf("Consulta", "Evaluación", "Sesión")))
        assertFalse(entraSoloPorSesion(listOf("Consulta", "Sesión")))
        assertFalse(entraSoloPorSesion(listOf("Evaluación", "Sesión")))
        // Sin Sesión (la clínica no usa sesiones) tampoco.
        assertFalse(entraSoloPorSesion(listOf("Consulta")))
        assertFalse(entraSoloPorSesion(emptyList()))
    }

    @Test
    fun altaRapidaEnSesionSoloSiEntraSoloPorSesion() {
        assertTrue(altaRapidaPermitida("Sesión", puedeAgendar = true, soloSesion = true))
        assertFalse(altaRapidaPermitida("Sesión", puedeAgendar = true, soloSesion = false))
        assertFalse(altaRapidaPermitida("Sesión", puedeAgendar = false, soloSesion = true))
        // Consulta/Evaluación: como siempre.
        assertTrue(altaRapidaPermitida("Consulta", puedeAgendar = true, soloSesion = false))
        assertTrue(altaRapidaPermitida("Evaluación", puedeAgendar = true))
    }

    private fun aviso(
        tipo: String = "Sesión", elegido: Boolean = true, cargados: Boolean = true,
        hay: Boolean = false, prefijado: Boolean = false, puede: Boolean = true,
    ) = avisoSinTratamiento(tipo, elegido, cargados, hay, prefijado, puede)

    @Test
    fun elAvisoValeEnTodasLasClinicas() {
        assertEquals(AvisoSinTratamiento.OFRECER_CREAR, aviso())
        // Sin permiso de sesiones: a quién pedírselo.
        assertEquals(AvisoSinTratamiento.PEDIR_A_QUIEN_PUEDE, aviso(puede = false))
        // Ya tiene tratamiento, o la cita vino con el suyo: nada.
        assertEquals(AvisoSinTratamiento.NINGUNO, aviso(hay = true))
        assertEquals(AvisoSinTratamiento.NINGUNO, aviso(prefijado = true))
        // Otro tipo de cita, sin paciente, o la lista no llegó (no se afirma que no tenga).
        assertEquals(AvisoSinTratamiento.NINGUNO, aviso(tipo = "Consulta"))
        assertEquals(AvisoSinTratamiento.NINGUNO, aviso(elegido = false))
        assertEquals(AvisoSinTratamiento.NINGUNO, aviso(cargados = false))
    }

    @Test
    fun textosComoLaWeb() {
        assertEquals("Este paciente aún no tiene tratamiento", tituloSinTratamiento(AvisoSinTratamiento.OFRECER_CREAR, "paciente"))
        assertEquals("Este cliente necesita un tratamiento", tituloSinTratamiento(AvisoSinTratamiento.PEDIR_A_QUIEN_PUEDE, "cliente"))
        // Con otra puerta en la clínica se sugiere; solo-Sesión, no.
        val conConsulta = detalleSinTratamiento(AvisoSinTratamiento.OFRECER_CREAR, "Consulta")
        assertTrue("Sesión #1" in conConsulta)
        assertTrue(conConsulta.endsWith("Si viene por algo nuevo, elige Consulta arriba."))
        assertFalse("Si viene" in detalleSinTratamiento(AvisoSinTratamiento.OFRECER_CREAR, null))
        val pedir = detalleSinTratamiento(AvisoSinTratamiento.PEDIR_A_QUIEN_PUEDE, "Evaluación")
        assertTrue(pedir.startsWith("Pídeselo a su profesional o al Admin"))
        assertTrue(pedir.endsWith("agéndale una Evaluación."))
        // Guardar queda bloqueado mientras falte.
        assertNull(mensajeFaltaTratamiento(AvisoSinTratamiento.NINGUNO, "paciente"))
        assertEquals("Crea el tratamiento del paciente para agendar la sesión.",
            mensajeFaltaTratamiento(AvisoSinTratamiento.OFRECER_CREAR, "paciente"))
        assertEquals("El paciente necesita un tratamiento: pídeselo a su profesional o al Admin.",
            mensajeFaltaTratamiento(AvisoSinTratamiento.PEDIR_A_QUIEN_PUEDE, "paciente"))
    }

    private fun servicio(id: String, esp: String?, modo: String? = null, usaSesiones: Boolean = true) =
        ProcedimientoRef(id, id, 50.0, null, esp, usaSesiones, emptyList(), modoCobro = modo)

    @Test
    fun soloServiciosPorSesionesDeLaEspecialidad() {
        val l = listOf(
            servicio("terapia", "psico"),
            servicio("evaluacion", "psico", modo = "sesiones"),
            servicio("informe", "psico", modo = "simple"),
            servicio("consulta", "psico", usaSesiones = false),      // especialidad sin sesiones
            servicio("taller", null),                                 // sin especialidad: sirve siempre
            servicio("fisio", "fisio"),
            servicio("botox", "psico", modo = "unidades"),
            servicio("raro", "psico", modo = "otro"),                 // modo inválido → el de la especialidad
        )
        assertEquals(listOf("terapia", "evaluacion", "taller", "raro"), serviciosParaSesion(l, "psico").map { it.id })
        assertEquals(listOf("terapia", "evaluacion", "taller", "fisio", "raro"), serviciosParaSesion(l, null).map { it.id })
    }

    @Test
    fun costoDeLaPrimeraSesion() {
        assertNull(costoPrimeraSesion("Paquete", 80.0))
        assertEquals(60.0, costoPrimeraSesion("Sesión suelta", 60.0))
        assertNull(costoPrimeraSesion("Sesión suelta", null))
    }

    @Test
    fun elCreadoQuedaElegido() {
        val a = TratamientoRef("t1", "Terapia individual", "Paquete", null)
        val b = TratamientoRef("t2", "Terapia de pareja", "Paquete", null)
        assertEquals(b, tratamientoCreadoEnLista(listOf(a, b), "t2"))
        // Sin id (respuesta vieja): el único que haya; con varios, ninguno (que elija).
        assertEquals(a, tratamientoCreadoEnLista(listOf(a), null))
        assertNull(tratamientoCreadoEnLista(listOf(a, b), null))
    }
}
