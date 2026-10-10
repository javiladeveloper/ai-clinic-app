package pe.saniape.app.data.staff

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import pe.saniape.app.ui.clinica.atencion.cuerpoEmitirReceta

/** "📝 Dar receta / indicaciones" tras completar una atención: cuándo, con qué, y cómo se llama. */
class RecetaAtencionTest {

    /** DALU: clínica NO médica con recetas encendidas → todas sus especialidades, modo indicaciones. */
    private val dalu = ModulosClinicos(recetas = true, recetasOptIn = true, mapaReceta = MapaClinico(listOf("fisio"), solo = true))

    /** Clínica médica mixta: medicina receta, fisioterapia no. */
    private val mixta = ModulosClinicos(recetas = true, recetasOptIn = false, mapaReceta = MapaClinico(listOf("med", "odo")))

    /** RENOVA / cualquier clínica con el módulo apagado. */
    private val apagado = ModulosClinicos()

    // ── Cuándo se ofrece ──

    @Test
    fun daluOfreceEnTodaAtencionConPermisoDeSesiones() {
        assertTrue(recetaAplicaAtencion(dalu, puedeSesiones = true))
        assertTrue(recetaAplicaAtencion(dalu, puedeSesiones = true, especialidadServicioId = "otra"))
        // Recepción sin 'sesiones' (el permiso que exige /api/staff/receta/emitir): nunca.
        assertFalse(recetaAplicaAtencion(dalu, puedeSesiones = false))
    }

    @Test
    fun conElModuloApagadoNoSeOfreceNunca() {
        assertFalse(recetaAplicaAtencion(apagado, puedeSesiones = true, especialidadServicioId = "med"))
        // Interruptor encendido pero sin especialidades que reciten (mapa vacío): tampoco.
        assertFalse(recetaAplicaAtencion(ModulosClinicos(recetas = true), puedeSesiones = true))
    }

    @Test
    fun enUnaMixtaMandaLaEspecialidadDeLaCitaLuegoLaDelServicio() {
        assertTrue(recetaAplicaAtencion(mixta, true, especialidadServicioId = "med"))
        assertFalse(recetaAplicaAtencion(mixta, true, especialidadServicioId = "fisio"))
        // La de la cita gana sobre la del servicio.
        assertFalse(recetaAplicaAtencion(mixta, true, especialidadCitaId = "fisio", especialidadServicioId = "med"))
        assertTrue(recetaAplicaAtencion(mixta, true, especialidadCitaId = "odo", especialidadServicioId = "fisio"))
        // Sin especialidad: la del profesional.
        assertTrue(recetaAplicaAtencion(mixta, true, especialidadesProfesional = listOf("fisio", "med")))
        assertFalse(recetaAplicaAtencion(mixta, true, especialidadesProfesional = listOf("fisio")))
        assertFalse(recetaAplicaAtencion(mixta, true))
        // Vacío cuenta como "sin especialidad".
        assertTrue(recetaAplicaAtencion(mixta, true, especialidadCitaId = " ", especialidadServicioId = "med"))
    }

    // ── Receta vs indicaciones ──

    @Test
    fun clinicaNoMedicaDiceIndicaciones() {
        assertTrue(recetaComoIndicaciones(dalu))
        assertEquals("📝 Dar indicaciones", textoDarReceta(dalu))
        assertEquals("Indicaciones", nombreHojaReceta(dalu))
        val p = PrefillRecetaAtencion(pacienteId = "p", pacienteNombre = "Ana María Quispe", atencion = "Sesión #3")
        assertEquals("¿Le dejas indicaciones a Ana?", textoOfertaReceta(dalu, p))
        assertEquals("✓ Sesión #3 completada", textoAtencionCompletada(p))
    }

    @Test
    fun clinicaMedicaDiceRecetaOIndicaciones() {
        assertFalse(recetaComoIndicaciones(mixta))
        assertEquals("📝 Dar receta / indicaciones", textoDarReceta(mixta))
        assertEquals("Receta", nombreHojaReceta(mixta))
        val p = PrefillRecetaAtencion(pacienteId = "p")
        assertEquals("¿Le das una receta o indicaciones?", textoOfertaReceta(mixta, p))
        assertEquals("✓ Atención completada", textoAtencionCompletada(p))
    }

    @Test
    fun indicadorDiceLoQueRealmenteSeEmitio() {
        val receta = RecetaVinculada("r1", 12, "c1", "s1", "t1")
        val indicaciones = receta.copy(esIndicaciones = true)
        assertEquals("💊 Receta N° 000012", etiquetaRecetaVinculada(receta))
        assertEquals("📋 Indicaciones N° 000012", etiquetaRecetaVinculada(indicaciones))
        assertEquals("Indicaciones", indicaciones.nombreHoja)
        assertEquals("Receta", receta.nombreHoja)
    }

    @Test
    fun sinEspecialidadTambienCuentaQuienMira() {
        assertTrue(recetaAplicaAtencion(mixta, true, especialidadesProfesional = listOf("fisio"),
            especialidadesDeQuienMira = listOf("med")))
        // Con especialidad de la atención, quien mira no cambia nada.
        assertFalse(recetaAplicaAtencion(mixta, true, especialidadServicioId = "fisio",
            especialidadesDeQuienMira = listOf("med")))
    }

    // ── Prellenado ──

    private fun cita(
        tipo: String = "Sesión", numero: Int? = 4, terapeuta: String? = "ter-cita", paciente: String? = "pac",
        sesion: String? = null,
    ) = CitaStaff(
        id = "cita1", fecha = "2026-10-09", hora = "10:00", estado = "Completada", tipo = tipo, costo = null,
        duracion = 45, origen = null, confirmadaPorPaciente = false, numeroSesion = numero,
        terapeutaId = terapeuta, terapeutaNombre = "Lic. Rosa", pacienteId = paciente, pacienteNombre = "Ana Quispe",
        pacienteTelefono = null, tratamientoId = "trat1", procedimiento = "Fisioterapia", especialidadId = "fisio",
        notaRecepcion = null, sesionId = sesion,
    )

    @Test
    fun prefillDeCitaDeLaAgenda() {
        val p = prefillRecetaDeCita(cita(), terapeutaId = null, diagnostico = "  Lumbalgia ", nombreTipo = "Sesión")!!
        assertEquals("pac", p.pacienteId)
        assertEquals("cita1", p.citaId)
        assertEquals("trat1", p.tratamientoId)
        // La sesión se lee de la cita al ABRIR (nunca en el camino de completar).
        assertNull(p.sesionId)
        assertEquals("ter-cita", p.terapeutaId)
        assertEquals("Lumbalgia", p.diagnostico)
        assertEquals("Sesión #4", p.atencion)
        // La cita que ya tenía su sesión la lleva desde el principio.
        assertEquals("s4", prefillRecetaDeCita(cita(sesion = "s4"))!!.sesionId)
    }

    @Test
    fun prefillDeCitaUsaQuienAtendioYElNombreDelTipo() {
        val p = prefillRecetaDeCita(cita(tipo = "Evaluación", numero = null, terapeuta = null), terapeutaId = "ter-elegido",
            diagnostico = " ", nombreTipo = "Diagnóstico")!!
        assertEquals("ter-elegido", p.terapeutaId)
        assertNull(p.diagnostico)   // en blanco = se lee del tratamiento al abrir
        assertEquals("Diagnóstico", p.atencion)
        // Sin paciente no hay receta posible.
        assertNull(prefillRecetaDeCita(cita(paciente = null)))
    }

    @Test
    fun prefillDeSesionDeLaFicha() {
        val p = prefillRecetaDeSesion(
            pacienteId = "pac", pacienteNombre = "Ana", sesionId = "s7", numero = 7, tratamientoId = "t1",
            citaId = "", terapeutaSesion = null, terapeutaTratamiento = "ter-trat", diagnostico = "Cervicalgia",
        )
        assertEquals("s7", p.sesionId)
        assertNull(p.citaId)
        assertEquals("ter-trat", p.terapeutaId)
        assertEquals("Sesión #7", p.atencion)
        assertEquals("Cervicalgia", p.diagnostico)
        val conCita = prefillRecetaDeSesion("pac", null, "s1", 0, "t1", citaId = "c1", terapeutaSesion = "ter-ses")
        assertEquals("c1", conCita.citaId)
        assertEquals("ter-ses", conCita.terapeutaId)
        assertEquals("Sesión", conCita.atencion)
    }

    @Test
    fun prefillDeTratamientoSoloLlevaLaCitaEnUnaConsulta() {
        val consulta = prefillRecetaDeTratamiento("pac", "Ana", "t1", esConsulta = true, citaOrigenId = "c0",
            terapeutaId = "ter", diagnostico = "Faringitis", atencion = null, medicacion = " Paracetamol 500 ")
        assertEquals("c0", consulta.citaId)
        assertEquals("Paracetamol 500", consulta.medicacionRef)
        val paquete = prefillRecetaDeTratamiento("pac", "Ana", "t2", esConsulta = false, citaOrigenId = "c0",
            terapeutaId = "ter", diagnostico = null, atencion = null, medicacion = "")
        assertNull(paquete.citaId)
        assertNull(paquete.medicacionRef)
        assertEquals("t2", paquete.tratamientoId)
    }

    @Test
    fun trasLaConsultaGuiadaSoloSiNoSeEmitioNinguna() {
        fun pf(aplica: Boolean = true, estados: List<String> = emptyList(), pac: String? = "pac") = prefillRecetaTrasConsulta(
            recetasAplica = aplica, estadosRecetasDeLaCita = estados, pacienteId = pac, pacienteNombre = "Ana",
            citaId = "c1", tratamientoId = "t1", terapeutaId = "med", diagnostico = "Faringitis aguda (J02.9)",
            cie10 = "J02.9", indicaciones = " Abundantes líquidos ",
        )
        val p = pf()!!
        assertEquals("c1", p.citaId)
        assertEquals("J02.9", p.cie10)
        assertEquals("Abundantes líquidos", p.indicaciones)
        assertEquals("Consulta", p.atencion)
        // Ya tiene una receta vigente en esta consulta: no se repite la oferta.
        assertNull(pf(estados = listOf("Emitida")))
        // La única que hubo se anuló: sí se ofrece.
        assertTrue(pf(estados = listOf("Anulada")) != null)
        // La consulta no admite receta (flags del servidor) o no hay paciente: no.
        assertNull(pf(aplica = false))
        assertNull(pf(pac = null))
    }

    // ── Indicador: qué receta es de qué atención ──

    @Test
    fun laRecetaDeUnaAtencionVaPorSesionYSiNoPorCita() {
        val lista = listOf(
            RecetaVinculada("a", 3, citaId = "c1", sesionId = null, tratamientoId = "t"),
            RecetaVinculada("b", 5, citaId = "c1", sesionId = null, tratamientoId = "t"),
            RecetaVinculada("c", 4, citaId = null, sesionId = "s1", tratamientoId = "t"),
            RecetaVinculada("d", 6, citaId = null, sesionId = null, tratamientoId = "t"),
        )
        assertEquals("c", recetaDeAtencion(lista, "c1", "s1")?.id)       // por sesión primero
        assertEquals("b", recetaDeAtencion(lista, "c1", "s9")?.id)       // si no, la más nueva de la cita
        assertEquals("b", recetaDeAtencion(lista, "c1", null)?.id)
        assertNull(recetaDeAtencion(lista, "c2", null))
        // Sin cita ni sesión no se atribuye ninguna (la "d" es del tratamiento, no de una atención).
        assertNull(recetaDeAtencion(lista, null, null))
    }

    @Test
    fun filaDeRecetaSinAnuladas() {
        fun fila(estado: String, items: Int = 1, prescribe: Boolean? = true) = buildJsonObject {
            put("id", "r1"); put("numero", 12); put("estado", estado)
            put("cita_id", "c1"); put("sesion_id", JsonNull); put("tratamiento_id", "t1")
            put("items", kotlinx.serialization.json.JsonArray(List(items) { buildJsonObject { put("dci", "Paracetamol") } }))
            put("prescriptor", buildJsonObject { put("nombre", "Dr. X"); prescribe?.let { put("prescribe", it) } })
        }
        val r = aRecetaVinculada(fila("Emitida"))!!
        assertEquals(12, r.numero)
        assertEquals("c1", r.citaId)
        assertNull(r.sesionId)
        assertFalse(r.esIndicaciones)
        assertNull(aRecetaVinculada(fila("Anulada")))
        assertNull(aRecetaVinculada(buildJsonObject { put("numero", 1) }))
        // Hoja de INDICACIONES: sin productos, o el prescriptor no prescribe (fisio de DALU).
        assertTrue(aRecetaVinculada(fila("Emitida", items = 0))!!.esIndicaciones)
        assertTrue(aRecetaVinculada(fila("Emitida", prescribe = false))!!.esIndicaciones)
        assertFalse(aRecetaVinculada(fila("Emitida", prescribe = null))!!.esIndicaciones)
    }

    @Test
    fun filaLivianaSinLosJsonbEnteros() {
        // Lo que devuelve COLUMNAS: primer_dci (items->0->>dci) y prescribe (prescriptor->>prescribe).
        fun fila(dci: String?, prescribe: String?) = buildJsonObject {
            put("id", "r9"); put("numero", 3); put("estado", "Emitida")
            put("cita_id", JsonNull); put("sesion_id", "s1"); put("tratamiento_id", "t1")
            put("primer_dci", dci?.let { JsonPrimitive(it) } ?: JsonNull)
            put("prescribe", prescribe?.let { JsonPrimitive(it) } ?: JsonNull)
        }
        assertFalse(aRecetaVinculada(fila("Ibuprofeno", "true"))!!.esIndicaciones)
        assertTrue(aRecetaVinculada(fila(null, "true"))!!.esIndicaciones)        // sin productos
        assertTrue(aRecetaVinculada(fila("Árnica gel", "false"))!!.esIndicaciones) // no prescribe
        assertFalse(aRecetaVinculada(fila("Ibuprofeno", null))!!.esIndicaciones)
        assertEquals("s1", aRecetaVinculada(fila("x", null))!!.sesionId)
        // Las columnas pedidas no traen los jsonb completos.
        assertFalse(Regex("(^|, )items(,|$)").containsMatchIn(RecetaAtencionRepo.COLUMNAS))
        assertFalse(Regex("(^|, )prescriptor(,|$)").containsMatchIn(RecetaAtencionRepo.COLUMNAS))
    }

    // ── La receta viaja atada a la sesión ──

    @Test
    fun elCuerpoDeEmitirLlevaLaSesion() {
        fun cuerpo(sesion: String?) = cuerpoEmitirReceta(
            pacienteId = "p1", terapeutaId = "t1", fecha = "2026-10-09", vigenciaDias = 30,
            diagnostico = null, cie10 = null, indicacionesGenerales = "Hielo 15 min 3 veces al día",
            infoFarmaceutico = null, items = emptyList(), citaId = "c1", tratamientoId = "tr1",
            claveCliente = "k1", sesionId = sesion,
        )
        assertEquals(JsonPrimitive("s1"), cuerpo("s1")["sesionId"])
        assertEquals(JsonPrimitive("c1"), cuerpo("s1")["citaId"])
        assertEquals(JsonPrimitive("tr1"), cuerpo("s1")["tratamientoId"])
        assertEquals(JsonNull, cuerpo(null)["sesionId"])
        assertEquals(JsonNull, cuerpo("  ")["sesionId"])
        // Indicaciones sin productos: la lista viaja vacía (el servidor lo permite al no médico).
        assertEquals(0, (cuerpo(null)["items"] as kotlinx.serialization.json.JsonArray).size)
    }
}
