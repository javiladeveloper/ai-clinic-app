package pe.saniape.app.data.staff

import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * «Nueva cita» en el menor número de toques (24/10/2026): prellenado como la web
 * (lib/prefill-cita), duración de la configuración (lib/duracion-cita), paciente
 * nuevo directo a un servicio de una vez y «✓ Se realizó en esta visita»
 * (lib/servicio-en-visita). Gemelos de los tests de la web.
 */
class AgendarMenosToquesTest {

    // ── Prellenado (PrefillCita.kt) ──────────────────────────────────────────

    @Test
    fun conUnSoloTratamientoActivoSeEligeSolo() {
        assertEquals("t1", tratamientoInicial(null, listOf("t1")))
        assertNull(tratamientoInicial(null, listOf("t1", "t2")))       // dos: se pregunta
        assertNull(tratamientoInicial(null, emptyList()))
        assertEquals("t2", tratamientoInicial("t2", listOf("t1", "t2"))) // «Agendar siguiente» manda
        assertEquals("t1", tratamientoInicial("viejo", listOf("t1")))     // el prefijado ya no está activo
    }

    @Test
    fun conUnPlanPorSesionesElTipoPasaASesion() {
        val visibles = listOf("Consulta", "Evaluación", "Sesión")
        assertEquals("Sesión", tipoSegunPaciente(listOf("Paquete"), "Consulta", visibles, tocadoAMano = false, prefijado = false))
        // Pago único o unidades no van «por sesiones»: vuelve al tipo de entrada.
        assertEquals("Consulta", tipoSegunPaciente(listOf("Consulta", "Unidades"), "Consulta", visibles, false, false))
        assertEquals("Consulta", tipoSegunPaciente(emptyList(), "Consulta", visibles, false, false))
    }

    @Test
    fun nuncaPisaLoQueEligioQuienAgendaNiUnTipoPrefijado() {
        val visibles = listOf("Consulta", "Evaluación", "Sesión")
        assertNull(tipoSegunPaciente(listOf("Paquete"), "Consulta", visibles, tocadoAMano = true, prefijado = false))
        assertNull(tipoSegunPaciente(listOf("Paquete"), "Evaluación", visibles, tocadoAMano = false, prefijado = true))
        assertNull(tipoSegunPaciente(listOf("Paquete"), "Consulta", listOf("Consulta"), false, false)) // sin Sesión
    }

    @Test
    fun decidenElTipoSoloLosDeLaEspecialidadDeLaCita() {
        val dental = TratamientoRef("d1", "Ortodoncia", "Paquete", null, especialidadId = "odonto")
        val fisio = TratamientoRef("f1", "Fisioterapia", "Paquete", null, especialidadId = "fisio")
        val sinEsp = TratamientoRef("s1", "Masaje", "Sesión suelta", null, especialidadId = null)
        val terminado = TratamientoRef("x1", "Fisioterapia", "Paquete", null, especialidadId = "fisio", estado = "Completado")
        val todos = listOf(dental, fisio, sinEsp, terminado)
        assertEquals(listOf("f1", "s1"), tratamientosQueDecidenTipo(todos, "fisio").map { it.id })
        assertEquals(listOf("d1", "f1", "s1"), tratamientosQueDecidenTipo(todos, null).map { it.id })
    }

    // ── Duración (DuracionCita.kt) ───────────────────────────────────────────

    @Test
    fun laDuracionSaleDeLaConfiguracionComoEnLaWeb() {
        val d = DuracionesCita(
            leerDuraciones(mapOf("duracion_evaluacion" to "40", "duracion_sesion" to "basura")),
            leerDuracionesEspecialidad("""{"fisio":{"Sesión":50},"roto":{"Sesión":"x"}}"""),
        )
        assertEquals(15, duracionSugerida("Consulta", d, null))      // respaldo
        assertEquals(40, duracionSugerida("Evaluación", d, null))    // la de la clínica
        assertEquals(60, duracionSugerida("Sesión", d, null))        // inválida → respaldo
        assertEquals(50, duracionSugerida("Sesión", d, "fisio"))     // la de la especialidad
        assertEquals(60, duracionSugerida("Sesión", d, "roto"))
        // Antes la app ponía 60 a la Evaluación: el respaldo es 30, como la web.
        assertEquals(30, duracionSugerida("Evaluación", DuracionesCita(), null))
    }

    @Test
    fun duracionInvalidaSeRechazaYLasOpcionesIncluyenLaSugerida() {
        assertNull(duracionValida("4"))
        assertNull(duracionValida("721"))
        assertNull(duracionValida("30.5"))
        assertEquals(30, duracionValida(" 30 "))
        assertTrue(leerDuracionesEspecialidad("no es json").isEmpty())
        assertEquals(listOf(15, 30, 45, 50, 60, 90, 120), opcionesDuracion(50))
    }

    // ── Paciente nuevo en la Sesión (AltaRapidaCita.kt) ──────────────────────

    @Test
    fun laSesionRegistraAlPacienteNuevoSoloSiHayServicioDeUnaVezYPermiso() {
        assertTrue(altaRapidaPermitida("Sesión", puedeAgendar = true, servicioDeUnaVez = true, puedeSesiones = true))
        assertFalse(altaRapidaPermitida("Sesión", puedeAgendar = true, servicioDeUnaVez = true, puedeSesiones = false))
        // DALU (todo por paquetes): como siempre.
        assertFalse(altaRapidaPermitida("Sesión", puedeAgendar = true, servicioDeUnaVez = false, puedeSesiones = true))
        assertTrue(altaSoloConDocumento("Sesión", soloSesion = false, servicioDeUnaVez = true, puedeSesiones = true))
        // Psicología (la Sesión es la puerta de entrada): también por nombre, como siempre.
        assertFalse(altaSoloConDocumento("Sesión", soloSesion = true, servicioDeUnaVez = true, puedeSesiones = true))
        assertFalse(altaSoloConDocumento("Consulta", soloSesion = false, servicioDeUnaVez = true, puedeSesiones = true))
    }

    @Test
    fun registradoEnUnaSesionSeAbreSuTratamiento() {
        assertTrue(abrirTratamientoTrasAlta("Sesión", puedeSesiones = true, tratamientoPrefijado = false))
        assertFalse(abrirTratamientoTrasAlta("Consulta", puedeSesiones = true, tratamientoPrefijado = false))
        assertFalse(abrirTratamientoTrasAlta("Sesión", puedeSesiones = false, tratamientoPrefijado = false))
        assertFalse(abrirTratamientoTrasAlta("Sesión", puedeSesiones = true, tratamientoPrefijado = true))
    }

    @Test
    fun elPadronSeConsultaSoloUnaVezRevisadaLaBaja() {
        assertTrue(consultarPadronSolo("45678912", "PE", hayHallazgo = false, bajasRevisadas = true, esDeBaja = false))
        assertFalse(consultarPadronSolo("45678912", "PE", hayHallazgo = true, bajasRevisadas = true, esDeBaja = false))
        assertFalse(consultarPadronSolo("45678912", "PE", hayHallazgo = false, bajasRevisadas = false, esDeBaja = false))
        assertFalse(consultarPadronSolo("45678912", "PE", hayHallazgo = false, bajasRevisadas = true, esDeBaja = true))
        assertFalse(consultarPadronSolo("45678912", "BO", hayHallazgo = false, bajasRevisadas = true, esDeBaja = false))
        assertFalse(consultarPadronSolo("12345678-9", "PE", hayHallazgo = false, bajasRevisadas = true, esDeBaja = false))
    }

    @Test
    fun elTelefonoDelAltaSeGuardaTalCualOSeOmite() {
        assertEquals("987 654 321", telefonoDeAlta(" 987  654 321 "))
        assertNull(telefonoDeAlta(""))
        assertNull(telefonoDeAlta("no tiene"))
        assertNull(telefonoDeAlta(null))
    }

    // ── «✓ Se realizó en esta visita» (ServicioEnVisita.kt) ──────────────────

    private val unaEncarnada = ServicioUnaVez("p1", "Uña encarnada", 80.0, modoCobro = "simple", especialidadId = "podo")
    private val botox = ServicioUnaVez("e1", "Bótox", 30.0, precioUnitarioSugerido = 12.0, modoCobro = "unidades", especialidadId = "est")
    private val paquete = ServicioUnaVez("f1", "Fisioterapia 10 sesiones", 600.0, modoCobro = "sesiones", especialidadId = "fisio")
    private val heredado = ServicioUnaVez("f2", "Terapia", 60.0, modoCobro = null, usaSesiones = true)
    private val consultaGratis = ServicioUnaVez("m1", "Consulta", 0.0, modoCobro = "simple")
    private val laEvaluacion = ServicioUnaVez("p0", "Evaluación podológica", 40.0, modoCobro = "simple", tipoCita = "Evaluación")
    private val evalPsico = ServicioUnaVez("s1", "Evaluación psicológica", 300.0, modoCobro = "simple", tipoClinico = "evaluacion_psicologica")
    private val limpieza = ServicioUnaVez("g1", "Limpieza facial", 90.0, usaSesiones = false)

    @Test
    fun queServicioSeHaceDeUnaVezEnCuatroRubros() {
        assertEquals(ModoUnaVez.SIMPLE, modoServicioDeUnaVez(unaEncarnada))   // podología
        assertEquals(ModoUnaVez.UNIDADES, modoServicioDeUnaVez(botox))        // estética
        assertEquals(ModoUnaVez.SIMPLE, modoServicioDeUnaVez(limpieza))       // especialidad sin sesiones
        assertNull(modoServicioDeUnaVez(paquete))                             // fisio / DALU
        assertNull(modoServicioDeUnaVez(heredado))
        assertNull(modoServicioDeUnaVez(consultaGratis))                      // medicina
        assertNull(modoServicioDeUnaVez(laEvaluacion))
        assertNull(modoServicioDeUnaVez(evalPsico))                           // psicología
        assertNull(modoServicioDeUnaVez(unaEncarnada.copy(estado = "Inactivo")))
    }

    @Test
    fun serviciosDeUnaVezPorEspecialidad() {
        val todos = listOf(unaEncarnada, botox, paquete, heredado, consultaGratis, laEvaluacion, evalPsico, limpieza)
        assertEquals(listOf("p1", "g1"), serviciosDeUnaVez(todos, "podo").map { it.id })
        assertFalse(hayServicioDeUnaVez(listOf(paquete, heredado), null))
        assertTrue(hayServicioDeUnaVez(listOf(botox), null))
    }

    @Test
    fun cuandoSeOfreceSeRealizoEnEstaVisita() {
        fun o(
            tipo: String = "Evaluación", estado: String = "Confirmada", fecha: String = "2026-10-24",
            dental: Boolean = false, puedeSesiones: Boolean = true, hay: Boolean = true,
        ) = ofreceServicioEnVisita(tipo, estado, fecha, "2026-10-24", dental, puedeSesiones, false, false, hay)
        assertTrue(o())
        assertTrue(o(estado = "Completada"))
        assertTrue(o(tipo = "Consulta", fecha = "2026-10-20"))
        assertFalse(o(fecha = "2026-10-25"))     // futura
        assertFalse(o(tipo = "Sesión"))
        assertFalse(o(estado = "Cancelada"))
        assertFalse(o(dental = true))            // su atención puntual
        assertFalse(o(puedeSesiones = false))
        assertFalse(o(hay = false))
    }

    @Test
    fun enLaEvaluacionDelDiaQuedaAUnToque() {
        // Confirmada: ✓ Completar principal y «Se realizó en esta visita» la secundaria.
        val plan = planAccionesCita(EntradaAccionesCita(estado = "Confirmada", tipo = "Evaluación", servicioEnVisita = true))
        assertEquals(AccionTarjeta.Completar, plan.primaria?.accion)
        assertEquals(AccionTarjeta.ServicioEnVisita, plan.secundaria?.accion)
        // Completada sin plan: «Crear tratamiento» principal; la otra, en «⋯ Más».
        val hecha = planAccionesCita(EntradaAccionesCita(
            estado = "Completada", tipo = "Evaluación", servicioEnVisita = true,
            crearTratamiento = true, crearTratamientoPrincipal = true,
        ))
        assertEquals(AccionTarjeta.CrearTratamiento, hecha.primaria?.accion)
        assertTrue(hecha.enMenu.any { it.accion == AccionTarjeta.ServicioEnVisita })
        // Sin la bandera (DALU) no aparece en ningún lado.
        val dalu = planAccionesCita(EntradaAccionesCita(estado = "Confirmada", tipo = "Evaluación"))
        assertTrue((dalu.visibles + dalu.enMenu).none { it.accion == AccionTarjeta.ServicioEnVisita })
    }

    @Test
    fun precioDeListaYCuerpoDelPedido() {
        assertEquals(80.0, precioListaServicioEnVisita(unaEncarnada, 1))
        assertEquals(600.0, precioListaServicioEnVisita(botox, 50))
        val c = ServicioEnVisitaRepo.cuerpo(
            PedidoServicioEnVisita("c1", "p1", 70.0, nota = "  ", metodo = "Yape", cobrarConsulta = false), "k1",
        )
        assertEquals(JsonPrimitive("Yape"), c["metodo"])
        assertEquals(JsonPrimitive(false), c["cobrarConsulta"])
        assertNull(c["nota"])          // vacía: no viaja
        assertNull(c["cantidad"])
        assertEquals(JsonPrimitive("k1"), c["idempotency_key"])
        val r = ServicioEnVisitaRepo.parsear(buildJsonObject {
            put("ok", true); put("tratamientoId", "t9"); put("cobrado", 110.0); put("errorCobro", "sin red")
        })
        assertEquals(ResultadoServicioEnVisita("t9", 110.0, null, "sin red"), r)
        val (msg, parcial) = ServicioEnVisitaRepo.error("""{"error":"x","parcial":true}""", 409)
        assertEquals("x", msg); assertTrue(parcial)
    }
}
