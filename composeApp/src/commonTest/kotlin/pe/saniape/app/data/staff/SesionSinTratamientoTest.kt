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
    fun todosLosServiciosDeLaEspecialidad() {
        // Desde la cita se ofrecen TODOS (sesiones, pago único, unidades): dueño, 24/10/2026.
        val l = listOf(
            servicio("terapia", "psico"),
            servicio("informe", "psico", modo = "simple"),
            servicio("consulta", "psico", usaSesiones = false),
            servicio("taller", null),                                 // sin especialidad: sirve siempre
            servicio("fisio", "fisio"),
            servicio("botox", "psico", modo = "unidades"),
        )
        assertEquals(listOf("terapia", "informe", "consulta", "taller", "botox"), serviciosParaCita(l, "psico").map { it.id })
        assertEquals(l.map { it.id }, serviciosParaCita(l, null).map { it.id })
        assertEquals(l.map { it.id }, serviciosParaCita(l, " ").map { it.id })
    }

    @Test
    fun costoDeLaPrimeraAtencionPorModo() {
        // Paquete: la sesión no se cobra suelta.
        assertNull(costoPrimeraSesion("Paquete", 80.0, 800.0))
        // Sesión suelta: su precio por sesión.
        assertEquals(60.0, costoPrimeraSesion("Sesión suelta", 60.0))
        assertNull(costoPrimeraSesion("Sesión suelta", null))
        // Pago único: el precio acordado lo lleva la primera atención.
        assertEquals(500.0, costoPrimeraSesion("Consulta", null, 500.0))
        assertNull(costoPrimeraSesion("Consulta", null, 0.0))     // consulta gratis
        assertNull(costoPrimeraSesion("Consulta", null, null))
        // Unidades: se cobra el acordado del tratamiento, no la cita.
        assertNull(costoPrimeraSesion("Unidades", null, 1200.0))
    }

    @Test
    fun nombreDeLaCitaDelTratamiento() {
        assertEquals("su Sesión #1", nombreCitaDelTratamiento("Paquete"))
        assertEquals("su Sesión #1", nombreCitaDelTratamiento("Sesión suelta"))
        assertEquals("su primera atención", nombreCitaDelTratamiento("Consulta"))
        assertEquals("su intervención", nombreCitaDelTratamiento("Unidades"))
        assertEquals("su Sesión #1", nombreCitaDelTratamiento(null))
    }

    @Test
    fun agendableEnSesionIncluyeElPagoUnicoRealizado() {
        assertTrue(tratamientoAgendableEnSesion("Activo", "Paquete"))
        assertTrue(tratamientoAgendableEnSesion("Activo", "Consulta"))
        // Pago único realizado y sin alta: otra atención.
        assertTrue(tratamientoAgendableEnSesion("Completado", "Consulta"))
        // Un paquete completado se amplía, no se agenda; alta / cancelado, nunca.
        assertFalse(tratamientoAgendableEnSesion("Completado", "Paquete"))
        assertFalse(tratamientoAgendableEnSesion("Completado", "Unidades"))
        assertFalse(tratamientoAgendableEnSesion("Alta", "Consulta"))
        assertFalse(tratamientoAgendableEnSesion(null, "Consulta"))
    }

    @Test
    fun etiquetaDelPagoUnicoRealizado() {
        val activo = TratamientoRef("t1", "Piel Renovada", "Consulta", null)
        assertFalse("realizado" in activo.etiqueta())
        val hecho = activo.copy(estado = "Completado")
        assertEquals("Piel Renovada · realizado (otra atención)", hecho.etiqueta())
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

    // ── «＋ Ampliar tratamiento anterior» (gemelo de lib/__tests__/ampliar-desde-cita.test.ts) ──

    private fun trat(
        id: String, estado: String? = "Completado", modalidad: String? = "Paquete", procedimientoId: String? = null,
        fechaInicio: String? = null, ultimaSesion: String? = null, usaSesiones: Boolean? = null, tipoClinico: String? = null,
        precioServicio: Double? = 50.0, precioPorSesion: Double? = null,
    ) = TratAmpliable(
        id = id, estado = estado, modalidad = modalidad, procedimientoId = procedimientoId, servicio = "Psicoterapia",
        precioServicio = precioServicio, precioPorSesion = precioPorSesion, totalSesiones = 4, terapeutaId = "t1",
        especialidadId = null, diagnostico = null, fechaInicio = fechaInicio, creadoAt = null,
        usaSesionesServicio = usaSesiones, ultimaSesion = ultimaSesion, tipoClinico = tipoClinico,
    )

    @Test
    fun ampliableConLaReglaDeLaFicha() {
        assertTrue(esAmpliableDesdeCita(trat("a")))
        assertTrue(esAmpliableDesdeCita(trat("b", modalidad = "Sesión suelta")))
        // Activo ya es vigente (se elige directo); Alta / Cancelado no.
        assertFalse(esAmpliableDesdeCita(trat("a", estado = "Activo")))
        for (e in listOf("Alta", "Cancelado", "Suspendido")) assertFalse(esAmpliableDesdeCita(trat("a", estado = e)))
        // Pago único y unidades no van por sesiones.
        assertFalse(esAmpliableDesdeCita(trat("a", modalidad = "Consulta")))
        assertFalse(esAmpliableDesdeCita(trat("a", modalidad = "Unidades")))
        // Evaluación psicológica: por el servicio marcado o su tipo clínico.
        assertFalse(esAmpliableDesdeCita(trat("a", procedimientoId = "eval"), setOf("eval")))
        assertFalse(esAmpliableDesdeCita(trat("a", tipoClinico = TIPO_CLINICO_EVALUACION_PSICO)))
        // Sin modalidad: decide la especialidad (sin dato, por sesiones).
        assertFalse(esAmpliableDesdeCita(trat("a", modalidad = null, usaSesiones = false)))
        assertTrue(esAmpliableDesdeCita(trat("a", modalidad = null, usaSesiones = true)))
        assertTrue(esAmpliableDesdeCita(trat("a", modalidad = null)))
        // Un paquete completado no se agenda en Sesión: por eso se amplía.
        assertFalse(tratamientoAgendableEnSesion("Completado", "Paquete"))
    }

    @Test
    fun ampliablesElMasRecientePrimero() {
        val viejo = trat("viejo", modalidad = "Sesión suelta", fechaInicio = "2026-09-01")
        val reciente = trat("reciente", modalidad = "Sesión suelta", fechaInicio = "2026-10-10")
        val otros = listOf(trat("activo", estado = "Activo"), trat("alta", estado = "Alta"))
        assertEquals(listOf("reciente", "viejo"), tratamientosAmpliables(listOf(viejo, reciente) + otros).map { it.id })
        // Por la última sesión, si se sabe.
        assertEquals(listOf("viejo", "reciente"), tratamientosAmpliables(listOf(
            viejo.copy(ultimaSesion = "2026-10-15"), reciente.copy(ultimaSesion = "2026-10-12"))).map { it.id })
        // Con sesión gana al que no tiene ninguna.
        assertEquals("viejo", tratamientosAmpliables(listOf(viejo.copy(ultimaSesion = "2026-09-02"), reciente)).first().id)
    }

    @Test
    fun ofreceAmpliarSoloDondeSaleCrearTratamiento() {
        assertTrue(ofreceAmpliarAnterior(AvisoSinTratamiento.OFRECER_CREAR, pacienteInactivo = false, ampliables = 1))
        assertFalse(ofreceAmpliarAnterior(AvisoSinTratamiento.PEDIR_A_QUIEN_PUEDE, pacienteInactivo = false, ampliables = 1))
        assertFalse(ofreceAmpliarAnterior(AvisoSinTratamiento.NINGUNO, pacienteInactivo = false, ampliables = 2))
        assertFalse(ofreceAmpliarAnterior(AvisoSinTratamiento.OFRECER_CREAR, pacienteInactivo = true, ampliables = 1))
        assertFalse(ofreceAmpliarAnterior(AvisoSinTratamiento.OFRECER_CREAR, pacienteInactivo = false, ampliables = 0))
    }

    @Test
    fun textoDelAmpliable() {
        assertEquals("Psicoterapia — última sesión 12 oct", textoTratamientoAmpliable("Psicoterapia", "2026-10-12"))
        assertEquals("Psicoterapia — terminado", textoTratamientoAmpliable("Psicoterapia", null))
        assertEquals("Tratamiento — terminado", textoTratamientoAmpliable(" ", null))
    }

    @Test
    fun listaDeLaAmpliacion() {
        assertEquals(100.0, precioListaAmpliacion(trat("a"), 2))
        assertEquals(120.0, precioListaAmpliacion(trat("a", modalidad = "Sesión suelta", precioPorSesion = 60.0), 2))
        // Suelta sin precio propio: el del catálogo; en otra moneda, nada.
        assertEquals(50.0, precioListaAmpliacion(trat("a", modalidad = "Sesión suelta"), 1))
        assertNull(precioListaAmpliacion(trat("a"), 1, otraMoneda = true))
        assertNull(precioListaAmpliacion(trat("a", modalidad = "Consulta"), 1))
        assertNull(precioListaAmpliacion(trat("a"), 0))
        assertNull(precioListaAmpliacion(trat("a", precioServicio = null), 1))
    }

    @Test
    fun pedidoDeAmpliacion() {
        // Vacío = la lista (nunca una cortesía que nadie decidió); 0 escrito = cortesía.
        assertEquals(PedidoAmpliacion.Ok(1, 50.0), pedidoAmpliacion("1", "", 50.0, "PEN"))
        assertEquals(PedidoAmpliacion.Ok(1, 0.0), pedidoAmpliacion("1", "0", 50.0, "PEN"))
        assertEquals(PedidoAmpliacion.Ok(2, 0.0), pedidoAmpliacion("2", "", null, "PEN"))
        assertEquals(PedidoAmpliacion.Ok(1, 45.5), pedidoAmpliacion("1", "45.5", 50.0, "PEN"))
        assertTrue(pedidoAmpliacion("0", "", 50.0, "PEN") is PedidoAmpliacion.Error)
        assertTrue(pedidoAmpliacion("1.5", "", 50.0, "PEN") is PedidoAmpliacion.Error)
        assertTrue(pedidoAmpliacion("1", "abc", 50.0, "PEN") is PedidoAmpliacion.Error)
    }
}
