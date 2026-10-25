package pe.saniape.app.data.staff

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Reparto de las acciones de la tarjeta de cita: primaria · secundaria · 💰 · «⋯ Más». */
class AccionesCitaTest {

    private fun PlanAccionesCita.menuDe(g: GrupoAccion) =
        menu.firstOrNull { it.first == g }?.second.orEmpty().map { it.accion }

    // ── La captura del dueño (24/10): Diagnóstico confirmada, por llegar, con triaje,
    //    odontología, deuda, pedido y crear tratamiento: 9 botones → 1 + 💰 + ⋯ ──

    @Test
    fun capturaDelDuenoQuedaEnUnaAccionMasCobroYMenu() {
        val p = planAccionesCita(EntradaAccionesCita(
            estado = "Confirmada", tipo = "Consulta",
            sala = SalaAcciones(EtapaLlegada.POR_LLEGAR, triajeOn = true, medica = false),
            cobrar = true, odontograma = true, pedido = "🧾 Pedido para recepción", crearTratamiento = true,
        ))
        assertEquals(AccionTarjeta.Completar, p.primaria?.accion)
        assertNull(p.secundaria)
        assertEquals(AccionTarjeta.Cobrar, p.cobro?.accion)
        assertEquals(
            listOf(AccionTarjeta.Llego, AccionTarjeta.Triaje, AccionTarjeta.Odontograma, AccionTarjeta.Pedido, AccionTarjeta.CrearTratamiento),
            p.menuDe(GrupoAccion.ATENCION),
        )
        assertEquals(listOf(AccionTarjeta.Editar, AccionTarjeta.Cancelar), p.menuDe(GrupoAccion.CITA))
        assertTrue(p.menuDe(GrupoAccion.COBRO).isEmpty())   // el 💰 ya está visible
    }

    // ── «🔔 Llegó» sin triaje casi no se usa: ni primaria ni secundaria ──

    @Test
    fun porLlegarLaPrimariaEsAtenderYLlegoVaAlMenu() {
        val medica = planAccionesCita(EntradaAccionesCita(
            estado = "Confirmada", tipo = "Consulta",
            sala = SalaAcciones(EtapaLlegada.POR_LLEGAR, triajeOn = false, medica = true),
        ))
        assertEquals(AccionTarjeta.Atender, medica.primaria?.accion)
        assertEquals("▶ Atender", medica.primaria?.texto)
        assertEquals(listOf(AccionTarjeta.Llego), medica.menuDe(GrupoAccion.ATENCION))
    }

    @Test
    fun marcandoLlegadaSigueEnElMenuConSusPuntos() {
        val p = planAccionesCita(EntradaAccionesCita(
            estado = "Confirmada", tipo = "Consulta",
            sala = SalaAcciones(EtapaLlegada.POR_LLEGAR, triajeOn = false, medica = false, marcando = true),
        ))
        assertEquals("…", p.enMenu.first { it.accion == AccionTarjeta.Llego }.texto)
    }

    // ── Clínica CON triaje (OMER): la cita de hoy por llegar tiene «🔔 Llegó» de principal ──

    @Test
    fun conTriajePorLlegarLaPrimariaEsLlegoYAtenderQuedaASegunda() {
        val p = planAccionesCita(EntradaAccionesCita(
            estado = "Confirmada", tipo = "Consulta",
            sala = SalaAcciones(EtapaLlegada.POR_LLEGAR, triajeOn = true, medica = true, clinicaConTriaje = true),
        ))
        assertEquals(AccionTarjeta.Llego, p.primaria?.accion)
        assertEquals("🔔 Llegó", p.primaria?.texto)
        // El médico atiende aunque recepción no marque la llegada.
        assertEquals(AccionTarjeta.Atender, p.secundaria?.accion)
        assertEquals(listOf(AccionTarjeta.Triaje), p.menuDe(GrupoAccion.ATENCION))
    }

    @Test
    fun conTriajePorLlegarPendienteDejaConfirmarEnElMenu() {
        val p = planAccionesCita(EntradaAccionesCita(
            estado = "Pendiente", tipo = "Sesión",
            sala = SalaAcciones(EtapaLlegada.POR_LLEGAR, triajeOn = true, medica = false, clinicaConTriaje = true),
        ))
        assertEquals(AccionTarjeta.Llego, p.primaria?.accion)
        assertEquals(AccionTarjeta.Completar, p.secundaria?.accion)
        assertEquals(listOf(AccionTarjeta.Confirmar, AccionTarjeta.Editar, AccionTarjeta.Cancelar), p.menuDe(GrupoAccion.CITA))
    }

    @Test
    fun conTriajeMarcandoLaPrimariaMuestraSusPuntos() {
        val p = planAccionesCita(EntradaAccionesCita(
            estado = "Confirmada", tipo = "Consulta",
            sala = SalaAcciones(EtapaLlegada.POR_LLEGAR, triajeOn = true, medica = false, marcando = true, clinicaConTriaje = true),
        ))
        assertEquals(AccionTarjeta.Llego, p.primaria?.accion)
        assertEquals("…", p.primaria?.texto)
    }

    @Test
    fun conTriajeQuienNoVeLoClinicoIgualTieneLlegoDePrincipal() {
        // Recepción sin 'historia_clinica': la tarjeta le apaga triajeOn, pero la
        // clínica sí toma triaje → Llegó principal; luego cierra con ✓ Completar.
        val porLlegar = planAccionesCita(EntradaAccionesCita(
            estado = "Confirmada", tipo = "Consulta",
            sala = SalaAcciones(EtapaLlegada.POR_LLEGAR, triajeOn = false, medica = false, clinicaConTriaje = true),
        ))
        assertEquals(AccionTarjeta.Llego, porLlegar.primaria?.accion)
        assertEquals(AccionTarjeta.Completar, porLlegar.secundaria?.accion)
        assertTrue(AccionTarjeta.Triaje !in porLlegar.enMenu.map { it.accion })
        val llego = planAccionesCita(EntradaAccionesCita(
            estado = "Confirmada", tipo = "Consulta",
            sala = SalaAcciones(EtapaLlegada.LLEGO, triajeOn = false, medica = false, clinicaConTriaje = true),
        ))
        assertEquals(AccionTarjeta.Completar, llego.primaria?.accion)
        assertTrue(AccionTarjeta.Llego !in llego.enMenu.map { it.accion })
    }

    @Test
    fun conTriajeDespuesDeLlegarSigueTriajeYLuegoAtender() {
        val llego = planAccionesCita(EntradaAccionesCita(
            estado = "Confirmada", tipo = "Consulta",
            sala = SalaAcciones(EtapaLlegada.LLEGO, triajeOn = true, medica = true, clinicaConTriaje = true),
        ))
        assertEquals(AccionTarjeta.Triaje, llego.primaria?.accion)
        assertEquals(AccionTarjeta.Atender, llego.secundaria?.accion)
        val triaje = planAccionesCita(EntradaAccionesCita(
            estado = "Confirmada", tipo = "Consulta",
            sala = SalaAcciones(EtapaLlegada.TRIAJE, triajeOn = true, medica = true, triajeHecho = true, clinicaConTriaje = true),
        ))
        assertEquals(AccionTarjeta.Atender, triaje.primaria?.accion)
    }

    @Test
    fun conTriajeLaCitaCerradaNoOfreceLlego() {
        // Completada o cancelada: no hay sala que recorrer.
        for (estado in listOf("Completada", "Cancelada")) {
            val p = planAccionesCita(EntradaAccionesCita(
                estado = estado, tipo = "Consulta",
                sala = SalaAcciones(EtapaLlegada.POR_LLEGAR, triajeOn = true, medica = false, clinicaConTriaje = true),
            ))
            assertTrue(p.primaria?.accion != AccionTarjeta.Llego, estado)
            assertTrue(AccionTarjeta.Llego !in p.enMenu.map { it.accion }, estado)
        }
    }

    @Test
    fun sinTriajeConFlujoMedicoLlegoSigueEnElMenu() {
        // Sala de espera solo por el flujo médico (sin triaje): como en la 2.62.8.
        val p = planAccionesCita(EntradaAccionesCita(
            estado = "Confirmada", tipo = "Consulta",
            sala = SalaAcciones(EtapaLlegada.POR_LLEGAR, triajeOn = false, medica = true, clinicaConTriaje = false),
        ))
        assertEquals(AccionTarjeta.Atender, p.primaria?.accion)
        assertTrue(AccionTarjeta.Llego in p.menuDe(GrupoAccion.ATENCION))
    }

    @Test
    fun daluSinSalaDeEsperaNoCambiaNada() {
        // DALU: sin triaje ni flujo médico la tarjeta no lleva sala (null): ni rastro de Llegó.
        for (estado in listOf("Pendiente", "Confirmada")) {
            val p = planAccionesCita(EntradaAccionesCita(estado = estado, tipo = "Sesión"))
            assertEquals(AccionTarjeta.Completar, p.primaria?.accion, estado)
            assertTrue(AccionTarjeta.Llego !in p.enMenu.map { it.accion }, estado)
        }
    }

    // ── Triaje: primaria solo si llegó, la clínica lo toma y falta ──

    @Test
    fun llegoSinTriajeLaPrimariaEsTriajeYLaSecundariaAtender() {
        val p = planAccionesCita(EntradaAccionesCita(
            estado = "Confirmada", tipo = "Consulta",
            sala = SalaAcciones(EtapaLlegada.LLEGO, triajeOn = true, medica = true),
        ))
        assertEquals(AccionTarjeta.Triaje, p.primaria?.accion)
        assertEquals("🩺 Triaje", p.primaria?.texto)
        assertEquals(AccionTarjeta.Atender, p.secundaria?.accion)
    }

    @Test
    fun llegoEnClinicaSinTriajeLaPrimariaEsAtender() {
        val p = planAccionesCita(EntradaAccionesCita(
            estado = "Confirmada", tipo = "Consulta",
            sala = SalaAcciones(EtapaLlegada.LLEGO, triajeOn = false, medica = false),
        ))
        assertEquals(AccionTarjeta.Completar, p.primaria?.accion)
        assertTrue(AccionTarjeta.Triaje !in p.enMenu.map { it.accion })
    }

    @Test
    fun conTriajeTomadoLaPrimariaEsAtenderYElTriajeSeCorrigeDesdeElMenu() {
        val p = planAccionesCita(EntradaAccionesCita(
            estado = "Confirmada", tipo = "Consulta",
            sala = SalaAcciones(EtapaLlegada.TRIAJE, triajeOn = true, medica = true, triajeHecho = true),
        ))
        assertEquals(AccionTarjeta.Atender, p.primaria?.accion)
        assertEquals("🩺 Triaje ✓ (corregir)", p.enMenu.first { it.accion == AccionTarjeta.Triaje }.texto)
    }

    @Test
    fun enConsultaLaPrimariaEsContinuar() {
        val p = planAccionesCita(EntradaAccionesCita(
            estado = "Confirmada", tipo = "Consulta",
            sala = SalaAcciones(EtapaLlegada.EN_CONSULTA, triajeOn = true, medica = true, triajeHecho = true),
        ))
        assertEquals("▶ Continuar", p.primaria?.texto)
    }

    // ── DALU (fisio, sin sala de espera): completar y el paso del flujo a 1 toque ──

    @Test
    fun fisioPendienteCompletaYConfirmaAUnToque() {
        val p = planAccionesCita(EntradaAccionesCita(estado = "Pendiente", tipo = "Consulta", pasarA = "Evaluación"))
        assertEquals(AccionTarjeta.Completar, p.primaria?.accion)
        assertEquals(AccionTarjeta.Confirmar, p.secundaria?.accion)
        assertEquals(listOf(AccionTarjeta.PasarEvaluacion), p.menuDe(GrupoAccion.ATENCION))
    }

    @Test
    fun fisioConfirmadaPasaAEvaluacionAUnToque() {
        val p = planAccionesCita(EntradaAccionesCita(estado = "Confirmada", tipo = "Consulta", pasarA = "Evaluación"))
        assertEquals(AccionTarjeta.Completar, p.primaria?.accion)
        assertEquals(AccionTarjeta.PasarEvaluacion, p.secundaria?.accion)
        assertEquals("→ Evaluación", p.secundaria?.texto)
    }

    @Test
    fun psicologiaSinTratamientoAtiendeDirecto() {
        val p = planAccionesCita(EntradaAccionesCita(estado = "Confirmada", tipo = "Consulta", directa = true))
        assertEquals(AccionTarjeta.AtenderDirecto, p.primaria?.accion)
        assertEquals("▶ Atender", p.primaria?.texto)
    }

    // ── Completada ──

    @Test
    fun completadaSinEvolucionPideAnotarlaYRepetirQuedaASegunda() {
        val p = planAccionesCita(EntradaAccionesCita(
            estado = "Completada", tipo = "Consulta",
            evolucion = BotonEvolucionCita("📝 Anotar evolución", resaltado = true),
        ))
        assertEquals(AccionTarjeta.Evolucion, p.primaria?.accion)
        assertEquals(AccionTarjeta.Repetir, p.secundaria?.accion)
        assertEquals(listOf(AccionTarjeta.Revertir), p.menuDe(GrupoAccion.CITA))
    }

    @Test
    fun completadaConEvolucionYaEscritaLaDejaEnElMenu() {
        val p = planAccionesCita(EntradaAccionesCita(
            estado = "Completada", tipo = "Consulta",
            evolucion = BotonEvolucionCita("📝 Evolución", resaltado = false),
        ))
        assertNull(p.primaria)
        assertTrue(AccionTarjeta.Evolucion in p.menuDe(GrupoAccion.ATENCION))
    }

    @Test
    fun sesionQueCierraElPlanOfreceAltaYAmpliarYElSaldoDelPaquete() {
        val p = planAccionesCita(EntradaAccionesCita(
            estado = "Completada", tipo = "Sesión",
            terminado = AccionesTerminado(ampliar = true, alta = true), abonarPaquete = true,
        ))
        assertEquals(AccionTarjeta.DarAlta, p.primaria?.accion)
        assertEquals(AccionTarjeta.Ampliar, p.secundaria?.accion)
        assertEquals(AccionTarjeta.AbonarPaquete, p.cobro?.accion)
        assertEquals(listOf(AccionTarjeta.Repetir, AccionTarjeta.Revertir), p.menuDe(GrupoAccion.CITA))
    }

    @Test
    fun cobroYaHechoVaAlMenuDeCobro() {
        val p = planAccionesCita(EntradaAccionesCita(
            estado = "Completada", tipo = "Consulta", comprobante = true, anularCobro = true,
        ))
        assertNull(p.cobro)
        assertEquals(listOf(AccionTarjeta.Comprobante, AccionTarjeta.AnularCobro), p.menuDe(GrupoAccion.COBRO))
    }

    // ── Cancelada: nada visible, solo «⋯» ──

    @Test
    fun canceladaSoloTieneElMenu() {
        val p = planAccionesCita(EntradaAccionesCita(estado = "Cancelada", tipo = "Consulta", odontograma = true))
        assertNull(p.primaria)
        assertNull(p.secundaria)
        assertNull(p.cobro)
        assertEquals(listOf(AccionTarjeta.Odontograma), p.menuDe(GrupoAccion.ATENCION))
        assertEquals(listOf(AccionTarjeta.Revertir), p.menuDe(GrupoAccion.CITA))
    }

    // ── Invariantes: el reparto no agrega ni quita nada ──

    @Test
    fun elRepartoConservaExactamenteLasAccionesDeSiempre() {
        val estados = listOf("Pendiente", "Confirmada", "Completada", "Cancelada", "No asistió")
        val salas = listOf(null) + EtapaLlegada.entries.flatMap { e ->
            listOf(true, false).flatMap { t ->
                // La clínica con triaje, vista también por quien no ve lo clínico (triajeOn apagado).
                listOf(t to t, false to true).distinct().map { (verTriaje, clinica) ->
                    SalaAcciones(e, triajeOn = verTriaje, medica = !t, triajeHecho = e != EtapaLlegada.POR_LLEGAR && e != EtapaLlegada.LLEGO,
                        clinicaConTriaje = clinica)
                }
            }
        }
        for (estado in estados) for (sala in salas) for (todo in listOf(false, true)) {
            val e = EntradaAccionesCita(
                estado = estado, tipo = if (todo) "Consulta" else "Sesión", sala = sala,
                odontograma = todo, crearTratamiento = todo, pasarA = "Evaluación".takeIf { todo },
                cobrar = todo, ponerPrecio = !todo, evaluacionPsico = todo, anularCobro = todo, comprobante = todo,
                receta = "📝 Dar indicaciones".takeIf { todo }, nutricion = todo, embarazo = todo,
                pedido = "🧾 Pedido para recepción".takeIf { todo }, abonarPaquete = !todo,
                terminado = AccionesTerminado(ampliar = todo, alta = !todo),
                evolucion = BotonEvolucionCita("📝 Anotar evolución", resaltado = todo),
            )
            val todas = accionesCita(e)
            val p = planAccionesCita(e)
            val repartidas = listOfNotNull(p.primaria, p.secundaria, p.cobro) + p.enMenu
            val ctx = "$estado / $sala / $todo"
            assertEquals(todas.sortedBy { it.accion }, repartidas.sortedBy { it.accion }, ctx)
            assertTrue(p.visibles.size <= 2, ctx)
            // Llegó solo se ve (y de principal) en la clínica con triaje, hoy, por llegar y activa.
            val llegoVisible = sala != null && sala.clinicaConTriaje && sala.etapa == EtapaLlegada.POR_LLEGAR &&
                (estado == "Pendiente" || estado == "Confirmada")
            if (llegoVisible) assertEquals(AccionTarjeta.Llego, p.primaria?.accion, ctx)
            else assertTrue(p.visibles.none { it.accion == AccionTarjeta.Llego }, "Llegó no visible: $ctx")
            assertTrue(p.cobro == null || p.cobro.accion in setOf(AccionTarjeta.Cobrar, AccionTarjeta.AbonarPaquete), ctx)
            p.menuDe(GrupoAccion.CITA).let { cita ->
                if (AccionTarjeta.Cancelar in cita) assertEquals(AccionTarjeta.Cancelar, cita.last(), ctx)
            }
            assertTrue(p.menu.all { it.second.isNotEmpty() }, ctx)
            if (estado == "Cancelada" || estado == "No asistió") assertNull(p.primaria, ctx)
        }
    }
}
