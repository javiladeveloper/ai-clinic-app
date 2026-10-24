package pe.saniape.app.data.staff

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Atención directa de psicología (gemelo de lib/__tests__/modo-rapido.test.ts). */
class AtencionDirectaTest {

    private val psico = Triple("e-psico", "psicologia", "Psicología")
    private val fisio = Triple("e-fisio", "fisioterapia", "Fisioterapia")
    private val psiq = Triple("e-psiq", "psicologia", "Psiquiatría")

    @Test
    fun soloPsicologiaYPsiquiatriaNo() {
        assertTrue(atencionEsPsicologia(listOf(psico), null))
        assertFalse(atencionEsPsicologia(listOf(fisio), null))
        assertFalse(atencionEsPsicologia(listOf(psiq), null))
        // Clínica mixta: decide la especialidad elegida o las del profesional.
        assertFalse(atencionEsPsicologia(listOf(psico, fisio), null))
        assertTrue(atencionEsPsicologia(listOf(psico, fisio), "e-psico"))
        assertFalse(atencionEsPsicologia(listOf(psico, fisio), "e-fisio"))
        assertTrue(atencionEsPsicologia(listOf(psico, fisio), null, listOf("e-psico")))
        assertFalse(atencionEsPsicologia(listOf(psico, fisio), null, listOf("e-psico", "e-fisio")))
        assertFalse(atencionEsPsicologia(emptyList(), null))
    }

    @Test
    fun laAtencionDirectaVaPrimeroSinDuplicarLaConsulta() {
        val v = { s: String -> s }
        assertEquals(listOf("Consulta", "Evaluación", "Sesión"), tiposConAtencionDirecta(listOf("Evaluación", "Sesión"), true, "Consulta", v))
        assertEquals(listOf("Consulta", "Evaluación", "Sesión"), tiposConAtencionDirecta(listOf("Consulta", "Evaluación", "Sesión"), true, "Consulta", v))
        // Otro rubro: la lista de siempre.
        assertEquals(listOf("Evaluación", "Sesión"), tiposConAtencionDirecta(listOf("Evaluación", "Sesión"), false, "Consulta", v))
        assertEquals("Consulta", TIPO_ATENCION_DIRECTA)
    }

    @Test
    fun tipoInicialSegunElPaciente() {
        val visibles = listOf("Consulta", "Evaluación", "Sesión")
        assertEquals("Sesión", tipoInicialAtencionDirecta(visibles, listOf("Paquete")))
        assertEquals("Sesión", tipoInicialAtencionDirecta(visibles, listOf(null)))
        assertEquals("Sesión", tipoInicialAtencionDirecta(visibles, listOf("Consulta", "Sesión suelta")))
        assertEquals(TIPO_ATENCION_DIRECTA, tipoInicialAtencionDirecta(visibles, emptyList()))
        assertEquals(TIPO_ATENCION_DIRECTA, tipoInicialAtencionDirecta(visibles, listOf("Consulta", "Unidades")))
        // Sin Sesión en el formulario: atención directa aunque tenga tratamiento.
        assertEquals(TIPO_ATENCION_DIRECTA, tipoInicialAtencionDirecta(listOf("Consulta", "Evaluación"), listOf("Paquete")))
    }

    private fun oferta(
        tipo: String? = "Consulta", estado: String? = "Confirmada", tratamientoId: String? = null,
        terapeutaId: String? = "t1", pacienteId: String? = "p1",
        rol: String? = "Fisioterapeuta", puedeSesiones: Boolean = true, historiaClinica: Boolean = true,
        miTerapeutaId: String? = "t1", soloLectura: Boolean = false, esPsicologia: Boolean = true,
    ) = ofertaAtencionDirecta(esPsicologia, tipo, estado, tratamientoId, terapeutaId, pacienteId,
        rol, puedeSesiones, historiaClinica, miTerapeutaId, soloLectura)

    @Test
    fun laCitaSinTratamientoDeLaPsicologaOfreceAtender() {
        assertEquals(OfertaAtencionDirecta.ATENDER, oferta())
        assertEquals(OfertaAtencionDirecta.ATENDER, oferta(estado = "Pendiente"))
        assertEquals(OfertaAtencionDirecta.ATENDER, oferta(tipo = "Sesión"))
        assertEquals(OfertaAtencionDirecta.EVOLUCION, oferta(estado = "Completada"))
    }

    @Test
    fun paquetesOtroRubroEvaluacionYCanceladaSiguenIgual() {
        assertNull(oferta(tipo = "Sesión", tratamientoId = "tr1"))
        assertNull(oferta(tratamientoId = "tr1"))
        assertNull(oferta(esPsicologia = false))
        assertNull(oferta(tipo = "Evaluación"))
        assertNull(oferta(estado = "Cancelada"))
        assertNull(oferta(pacienteId = null))
    }

    @Test
    fun soloElProfesionalDeLaCitaOElAdmin() {
        assertNull(oferta(terapeutaId = "t2"))
        assertEquals(OfertaAtencionDirecta.ATENDER, oferta(terapeutaId = "t2", rol = "Admin", miTerapeutaId = null))
        assertNull(oferta(rol = "Recepcionista", miTerapeutaId = null, historiaClinica = false))
        assertNull(oferta(puedeSesiones = false))
        assertNull(oferta(historiaClinica = false))
        assertNull(oferta(soloLectura = true))
        // Sin profesional: la toma el vinculado; el Admin sin vínculo no puede firmarla.
        assertEquals(OfertaAtencionDirecta.ATENDER, oferta(terapeutaId = null))
        assertNull(oferta(terapeutaId = null, rol = "Admin", miTerapeutaId = null))
    }

    @Test
    fun botonDeEvolucionResaltadoSoloSiFalta() {
        assertEquals(BotonEvolucionCita("📝 Anotar evolución", true), botonEvolucionCita(OfertaAtencionDirecta.EVOLUCION, false))
        assertEquals(BotonEvolucionCita("📝 Evolución", false), botonEvolucionCita(OfertaAtencionDirecta.EVOLUCION, true))
        // Sin saber todavía: el discreto.
        assertEquals(BotonEvolucionCita("📝 Evolución", false), botonEvolucionCita(OfertaAtencionDirecta.EVOLUCION, null))
        assertNull(botonEvolucionCita(OfertaAtencionDirecta.ATENDER, false))
        assertNull(botonEvolucionCita(null, false))
    }

    @Test
    fun pasosDelCobro() {
        assertEquals(PasosCobroAtencion(80.0, null), pasosCobroAtencionDirecta("Consulta", 0.0, 80.0))
        assertEquals(PasosCobroAtencion(80.0, null), pasosCobroAtencionDirecta("Consulta", null, 80.0))
        assertEquals(PasosCobroAtencion(null, null), pasosCobroAtencionDirecta("Consulta", 80.0, 80.0))
        assertEquals(PasosCobroAtencion(null, 60.0), pasosCobroAtencionDirecta("Consulta", 80.0, 60.0))
        assertNull(pasosCobroAtencionDirecta("Sesión", 80.0, 80.0))
        assertNull(pasosCobroAtencionDirecta("Consulta", 80.0, 0.0))
    }

    @Test
    fun elPagoSoloAlAtenderYNuncaEnUnaSesion() {
        assertTrue(ofrecePagoAtencion(atender = true, tipo = "Consulta", pagada = false, puedePagos = true, soloLectura = false))
        assertFalse(ofrecePagoAtencion(atender = false, tipo = "Consulta", pagada = false, puedePagos = true, soloLectura = false))
        assertFalse(ofrecePagoAtencion(atender = true, tipo = "Sesión", pagada = false, puedePagos = true, soloLectura = false))
        assertFalse(ofrecePagoAtencion(atender = true, tipo = "Consulta", pagada = true, puedePagos = true, soloLectura = false))
        assertFalse(ofrecePagoAtencion(atender = true, tipo = "Consulta", pagada = false, puedePagos = false, soloLectura = false))
    }

    @Test
    fun elBotonDiceLoQuePasa() {
        assertEquals("✓ Completar sin evolución", textoBotonAtencionDirecta(atender = true, conTexto = false, montoTexto = null))
        assertEquals("Guardar y completar", textoBotonAtencionDirecta(atender = true, conTexto = true, montoTexto = null))
        assertEquals("Guardar y cobrar S/ 80", textoBotonAtencionDirecta(atender = true, conTexto = true, montoTexto = "S/ 80"))
        assertEquals("Completar y cobrar S/ 80", textoBotonAtencionDirecta(atender = true, conTexto = false, montoTexto = "S/ 80"))
        assertEquals("Guardar evolución", textoBotonAtencionDirecta(atender = false, conTexto = true, montoTexto = null))
    }

    @Test
    fun bloqueos() {
        assertNull(bloqueoAtencionDirecta(false, atender = true, conTexto = false, yaTeniaEvolucion = false, pago = false, montoValido = false, montoError = null))
        assertEquals("Escribe la evolución", bloqueoAtencionDirecta(false, atender = false, conTexto = false, yaTeniaEvolucion = false, pago = false, montoValido = false, montoError = null))
        assertEquals("La evolución no puede quedar vacía", bloqueoAtencionDirecta(false, atender = true, conTexto = false, yaTeniaEvolucion = true, pago = false, montoValido = false, montoError = null))
        assertEquals("Indica cuánto pagó", bloqueoAtencionDirecta(false, atender = true, conTexto = true, yaTeniaEvolucion = false, pago = true, montoValido = false, montoError = null))
        assertEquals("Modo lectura", bloqueoAtencionDirecta(true, atender = true, conTexto = true, yaTeniaEvolucion = false, pago = false, montoValido = true, montoError = null))
    }

    @Test
    fun algoEscrito() {
        assertFalse(evolucionConContenido(EvolucionSesionPsico(evolucion = "  ")))
        assertTrue(evolucionConContenido(EvolucionSesionPsico(plan = "Seguir")))
    }

    @Test
    fun precioSugeridoDeLaSesionNuncaLaEvaluacion() {
        val sesion = ServicioPrecioAtencion("s1", 90.0, "e-psico", "sesiones", true, null, null)
        val evalPsico = ServicioPrecioAtencion("s2", 300.0, "e-psico", "sesiones", true, null, TIPO_CLINICO_EVALUACION_PSICO)
        val consulta = ServicioPrecioAtencion("s3", 50.0, "e-psico", "simple", true, "Consulta", null)
        val otra = ServicioPrecioAtencion("s4", 70.0, "e-fisio", "sesiones", true, null, null)
        assertEquals(90.0, precioSugeridoAtencionDirecta(listOf(evalPsico, consulta, otra, sesion), "e-psico"))
        assertNull(precioSugeridoAtencionDirecta(listOf(evalPsico, consulta), "e-psico"))
        assertEquals(70.0, precioSugeridoAtencionDirecta(listOf(otra), null))
    }

    @Test
    fun leeLaRespuestaDelServidor() {
        val raiz = Json.parseToJsonElement("""
            {"cita":{"id":"c1","tipo":"Consulta","estado":"Confirmada","fecha":"2026-10-24","hora":"10:00:00","costo":80,
              "pagada":false,"paciente":{"id":"p1","nombre":"Ana"},"terapeuta_id":"t1","especialidad_id":"e1",
              "tratamiento_id":null,"sede_id":null},
             "evolucion":null,
             "anterior":{"fecha":"2026-10-17","evolucion":{"evolucion":"Ansiedad","estado":"","tareas":"Registro","plan":""}},
             "soloLectura":false}
        """.trimIndent()) as JsonObject
        val d = assertNotNull(parsearAtencionDirecta(raiz))
        assertEquals("c1", d.cita.id)
        assertEquals(80.0, d.cita.costo)
        assertEquals("Ana", d.cita.pacienteNombre)
        assertNull(d.evolucion)
        assertTrue(d.atender)
        assertEquals("2026-10-17", d.anterior?.first)
        assertEquals(listOf("Temas trabajados y evolución" to "Ansiedad", "Tareas e indicaciones" to "Registro"),
            camposEvolucionAnterior(d.anterior!!.second))
        assertNull(parsearAtencionDirecta(Json.parseToJsonElement("{}") as JsonObject))
    }

    @Test
    fun titulos() {
        assertEquals("▶ Atender · Ana", tituloAtencionDirecta(true, false, "Ana"))
        assertEquals("📝 Evolución · Ana", tituloAtencionDirecta(false, true, "Ana"))
        assertEquals("📝 Anotar evolución · Paciente", tituloAtencionDirecta(false, false, null))
    }
}
