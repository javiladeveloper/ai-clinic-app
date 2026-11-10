package pe.saniape.app.data.staff

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Sala de espera de todas las clínicas — lo que la app suma a [SalaEsperaTest]:
 * `pasarEnInicio` (lib/cola-inicio.ts, casos de lib/__tests__/sala-espera-web.test.ts),
 * `llegoALaVista` (lib/acciones-cita.ts), `usaSala` / `sigueVisible` / `profesionalSala`
 * de /citas y del Inicio web, las piezas puras de hooks/useAccionSala.ts y el
 * botón de cada fila de la franja.
 */
class SalaEsperaAppTest {
    private val hoy = "2026-11-09"
    private val lima = "America/Lima"

    private fun cita(
        id: String = "c1", fecha: String = hoy, terapeutaId: String? = "t1", terapeutaNombre: String? = "Dra. Ruiz",
        llegadaAt: String? = null, consultaAt: String? = null,
    ) = CitaSalaEntrada(
        id = id, fecha = fecha, hora = "10:00", estado = "Confirmada", terapeutaId = terapeutaId,
        terapeutaNombre = terapeutaNombre, pacienteNombre = "Ana Pérez", llegadaAt = llegadaAt, consultaAt = consultaAt,
    )

    // ── REGLA: ningún «▶ Pasar» del Inicio en una clínica con sala médica ──

    @Test
    fun pasarEnInicioSoloSinTriajeNiFlujoMedicoYConLaConfiguracionCargada() {
        val sinMedico = MapaClinico(ids = emptyList(), solo = false)
        // DALU / RENOVA: sin sala médica → ▶ Pasar.
        assertTrue(pasarEnInicio(sinMedico, triaje = false, contextoListo = true))
        // Triaje: toda cita tiene su etapa (🩺 Triaje / ▶ Atender) → nunca ▶ Pasar.
        assertFalse(pasarEnInicio(sinMedico, triaje = true, contextoListo = true))
        // Flujo médico (mixta o solo médica).
        assertFalse(pasarEnInicio(MapaClinico(ids = listOf("med"), solo = false), triaje = false, contextoListo = true))
        assertFalse(pasarEnInicio(MapaClinico(ids = emptyList(), solo = true), triaje = false, contextoListo = true))
        assertFalse(pasarEnInicio(MapaClinico(ids = listOf("med"), solo = true), triaje = true, contextoListo = true))
        // Mientras carga la configuración el mapa vacío NO quiere decir «sin sala médica».
        assertFalse(pasarEnInicio(sinMedico, triaje = false, contextoListo = false))
    }

    // ── «🔔 Llegó» a la vista (lib/acciones-cita.ts) ──

    @Test
    fun llegoALaVistaSoloConAvisosDelPlanOLaSalaYaEnUso() {
        assertTrue(llegoALaVista(notificacionesFlujo = true, usaSala = false))
        assertTrue(llegoALaVista(notificacionesFlujo = false, usaSala = true))
        assertTrue(llegoALaVista(notificacionesFlujo = true, usaSala = true))
        // La clínica que no usa la sala (sin avisos): «🔔 Llegó» al ⋯ y Reprogramar se queda.
        assertFalse(llegoALaVista(notificacionesFlujo = false, usaSala = false))
    }

    // ── La franja / «Sigue» aparecen solo con sala médica o desde la primera llegada de hoy ──

    @Test
    fun usaSalaHoyDaluSinLlegoNoVeNadaNuevo() {
        val sinLlegadas = listOf(cita(id = "a"), cita(id = "b"))
        assertFalse(usaSalaHoy(sinLlegadas, salaMedica = false, hoy = hoy, zona = lima))
        // Con sala médica, la franja está desde el inicio del día (dice «Sala vacía»).
        assertTrue(usaSalaHoy(sinLlegadas, salaMedica = true, hoy = hoy, zona = lima))
        // Desde la primera llegada (o ▶ Pasar) de HOY.
        assertTrue(usaSalaHoy(sinLlegadas + cita(id = "c", llegadaAt = "2026-11-09T15:05:00Z"), false, hoy, lima))
        assertTrue(usaSalaHoy(listOf(cita(consultaAt = "2026-11-09T15:05:00Z")), false, hoy, lima))
        // Una llegada de otro día (cita reprogramada) no abre nada.
        assertFalse(usaSalaHoy(listOf(cita(llegadaAt = "2026-11-08T15:05:00Z")), false, hoy, lima))
        // Sin citas de hoy a la vista (mirando otro día), nada — ni con sala médica.
        assertFalse(usaSalaHoy(emptyList(), salaMedica = true, hoy = hoy, zona = lima))
    }

    @Test
    fun llegoHoyEsElDiaDeLaZonaDeLaSede() {
        val tarde = cita(llegadaAt = "2026-11-10T04:30:00Z")   // 23:30 del 9 en Lima; 00:30 del 10 en La Paz
        assertTrue(llegoHoy(tarde, hoy, lima))
        assertFalse(llegoHoy(tarde, hoy, "America/La_Paz"))
        assertFalse(llegoHoy(cita(), hoy, lima))
    }

    @Test
    fun sigueVisibleEnInicioConLaColaODesdeLaPrimeraLlegadaDeHoy() {
        val sin = listOf(cita())
        assertFalse(sigueVisibleEnInicio(colaActiva = false, citasHoy = sin, hoy = hoy, zona = lima))
        assertTrue(sigueVisibleEnInicio(colaActiva = true, citasHoy = sin, hoy = hoy, zona = lima))
        assertTrue(sigueVisibleEnInicio(false, listOf(cita(llegadaAt = "2026-11-09T15:05:00Z")), hoy, lima))
        assertFalse(sigueVisibleEnInicio(false, listOf(cita(llegadaAt = "2026-11-08T15:05:00Z")), hoy, lima))
    }

    // ── El botón de cada fila de la franja ──

    @Test
    fun elBotonDeLaFilaSinSalaMedica() {
        assertEquals(BotonFilaSala.PASAR, botonFilaSala(EstadoSala.ESPERANDO, salaMedica = false, medica = false, directa = false, faltaTriaje = false))
        assertEquals(BotonFilaSala.PASAR, botonFilaSala(EstadoSala.ESPERANDO, salaMedica = false, medica = false, directa = true, faltaTriaje = false))
        assertEquals(BotonFilaSala.COMPLETAR, botonFilaSala(EstadoSala.EN_CONSULTA, salaMedica = false, medica = false, directa = false, faltaTriaje = false))
        // ✓ Completar de la franja dice lo mismo que la tarjeta: ▶ Atender en la atención directa.
        assertEquals(BotonFilaSala.ATENDER_DIRECTO, botonFilaSala(EstadoSala.EN_CONSULTA, salaMedica = false, medica = false, directa = true, faltaTriaje = false))
        assertNull(botonFilaSala(EstadoSala.POR_LLEGAR, salaMedica = false, medica = false, directa = false, faltaTriaje = false))
        assertNull(botonFilaSala(EstadoSala.FUERA, salaMedica = false, medica = false, directa = false, faltaTriaje = false))
        assertEquals("▶ Pasar", BotonFilaSala.PASAR.texto)
        assertEquals("✓ Completar", BotonFilaSala.COMPLETAR.texto)
    }

    @Test
    fun elBotonDeLaFilaConSalaMedicaEsElPasoDeSiempre() {
        assertEquals(BotonFilaSala.TRIAJE, botonFilaSala(EstadoSala.ESPERANDO, salaMedica = true, medica = true, directa = false, faltaTriaje = true))
        assertEquals(BotonFilaSala.ATENDER, botonFilaSala(EstadoSala.ESPERANDO, salaMedica = true, medica = true, directa = false, faltaTriaje = false))
        assertEquals(BotonFilaSala.CONTINUAR, botonFilaSala(EstadoSala.EN_CONSULTA, salaMedica = true, medica = true, directa = false, faltaTriaje = false))
        // Clínica con triaje, cita que no es de la consulta guiada (fisio) y el triaje ya tomado:
        // su cierre de siempre (✓ Completar), NUNCA ▶ Pasar.
        assertEquals(BotonFilaSala.COMPLETAR, botonFilaSala(EstadoSala.ESPERANDO, salaMedica = true, medica = false, directa = false, faltaTriaje = false))
        assertEquals(BotonFilaSala.TRIAJE, botonFilaSala(EstadoSala.ESPERANDO, salaMedica = true, medica = false, directa = false, faltaTriaje = true))
    }

    @Test
    fun reglaNuncaPasarConSalaMedica() {
        val b = listOf(false, true)
        for (estado in EstadoSala.entries) for (medica in b) for (directa in b) for (falta in b) {
            assertNotEquals(
                BotonFilaSala.PASAR, botonFilaSala(estado, salaMedica = true, medica = medica, directa = directa, faltaTriaje = falta),
                "salaMedica, $estado medica=$medica directa=$directa faltaTriaje=$falta",
            )
            // La consulta guiada o el triaje que falta ya son sala médica, aunque no se diga.
            if (medica || falta) assertNotEquals(
                BotonFilaSala.PASAR, botonFilaSala(estado, salaMedica = false, medica = medica, directa = directa, faltaTriaje = falta),
            )
        }
    }

    // ── La respuesta del servidor y el aviso (hooks/useAccionSala.ts) ──

    @Test
    fun momentosYAvisoDeLaRespuestaDelServidor() {
        val j = Json.parseToJsonElement(
            """{"ok":true,"cita":{"id":"c1","llegada_at":"2026-11-09T15:05:00+00:00","consulta_at":null},"cambio":true,"avisoSolicitado":true}""",
        ).jsonObject
        assertEquals(MomentosSala("2026-11-09T15:05:00+00:00", null), momentosDeRespuesta(j))
        assertEquals(RespuestaSala(MomentosSala("2026-11-09T15:05:00+00:00", null), cambio = true, avisoSolicitado = true), respuestaSala(j))
        val sinAviso = Json.parseToJsonElement("""{"ok":true,"cita":{"id":"c1","llegada_at":null,"consulta_at":null},"cambio":false}""").jsonObject
        assertEquals(RespuestaSala(MomentosSala(), cambio = false, avisoSolicitado = false), respuestaSala(sinAviso))
        assertNull(momentosDeRespuesta(null))
        assertNull(respuestaSala(Json.parseToJsonElement("""{"error":"x"}""").jsonObject))
    }

    @Test
    fun elAvisoNombraAlPacienteYAlProfesionalYSeAvisoSoloSiCorresponde() {
        val dra = profesionalSala(cita(), miTerapeutaId = "yo")
        assertEquals(ProfesionalSala("Dra. Ruiz", avisable = true), dra)
        assertEquals("Ana Pérez en sala de espera con Dra. Ruiz · se avisó", avisoAccionSala(AccionSala.LLEGO, "Ana Pérez", dra, avisoSolicitado = true))
        // Sin aviso pedido por el servidor (no es la primera llegada o el plan no avisa): sin «· se avisó».
        assertEquals("Ana Pérez en sala de espera con Dra. Ruiz", avisoAccionSala(AccionSala.LLEGO, "Ana Pérez", dra, avisoSolicitado = false))
        // Solo el «Llegó» avisa.
        assertEquals("Ana Pérez pasó a consulta con Dra. Ruiz", avisoAccionSala(AccionSala.PASAR, "Ana Pérez", dra, avisoSolicitado = true))
        // Quien marca es el profesional de la cita: el servidor no se avisa a sí mismo.
        val yo = profesionalSala(cita(terapeutaId = "yo"), miTerapeutaId = "yo")
        assertFalse(yo.avisable)
        assertEquals("Ana Pérez en sala de espera con Dra. Ruiz", avisoAccionSala(AccionSala.LLEGO, "Ana Pérez", yo, avisoSolicitado = true))
    }

    @Test
    fun elProfesionalDelAvisoEmbebidoODelCatalogo() {
        assertEquals(ProfesionalSala("Mario Paz", avisable = true),
            profesionalSala(cita(terapeutaId = "tM", terapeutaNombre = "  "), miTerapeutaId = null, terapeutas = mapOf("tM" to " Mario Paz ")))
        // Sin profesional: sin nombre y nadie a quien avisar.
        assertEquals(ProfesionalSala(null, avisable = false), profesionalSala(cita(terapeutaId = null), miTerapeutaId = null))
        assertEquals("Ana Pérez en sala de espera", avisoAccionSala(AccionSala.LLEGO, "Ana Pérez", ProfesionalSala(null, false), true))
        assertEquals(SALA_PACIENTE_SIN_NOMBRE, nombrePacienteSala("  "))
        assertEquals(SALA_PACIENTE_SIN_NOMBRE, nombrePacienteSala(null))
        assertEquals("Ana", nombrePacienteSala(" Ana "))
    }

    @Test
    fun laBaseDelOptimistaSoloCuentaLoDelDiaYLoTocadoAqui() {
        val t = "2026-11-09T15:20:00.000Z"
        val reprog = cita(id = "r", llegadaAt = "2026-11-08T15:00:00Z")
        // Reprogramada con la llegada de ayer: para el servidor no llegó → «Llegó» cambia algo.
        assertEquals(MomentosSala(), baseDeAccionSala(reprog, emptyMap(), lima))
        assertEquals(MomentosSala(llegadaAt = t), aplicarAccionSala(AccionSala.LLEGO, baseDeAccionSala(reprog, emptyMap(), lima), t))
        // Lo tocado aquí manda sobre la lista (aunque la lista no lo traiga todavía).
        val p = mapOf("r" to PendienteSala(MomentosSala(llegadaAt = t)))
        assertEquals(MomentosSala(llegadaAt = t), baseDeAccionSala(reprog, p, lima))
        assertEquals(MomentosSala(t, "2026-11-09T15:21:00Z"), aplicarAccionSala(AccionSala.PASAR, baseDeAccionSala(reprog, p, lima), "2026-11-09T15:21:00Z"))
    }
}
