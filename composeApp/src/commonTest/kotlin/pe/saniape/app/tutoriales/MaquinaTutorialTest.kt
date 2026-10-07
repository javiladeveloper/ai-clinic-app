package pe.saniape.app.tutoriales

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * Paridad con la web (lib/tutoriales/__tests__/maquina.test.ts): mismos casos,
 * con el vocabulario de la app (pantallas en vez de rutas, tareas en vez de eventos).
 */
class MaquinaTutorialTest {

    private fun espera(tipo: String, valor: String? = null) = EsperaApp(tipo, valor, listOfNotNull(valor))

    // agendar-cita tal como lo sirve el catálogo de la app (saltarSiYa ya resuelto por el servidor).
    private val PASOS = listOf(
        PasoApp("ir", anclas = listOf("nav.agenda"), espera = espera("pantalla", "Agenda"), saltarSiYa = true),
        PasoApp("nueva", anclas = listOf("agenda.nueva_cita"), pantallas = listOf("Agenda"), espera = espera("aparece", "cita_form"), saltarSiYa = true),
        PasoApp("paciente", pantallas = listOf("Agenda"), espera = espera("clic", "cita_form.paciente")),
        PasoApp("guardar", pantallas = listOf("Agenda"), espera = espera("tarea", "cita_creada")),
    )

    private fun en(pantalla: String?, vis: List<String> = emptyList()) = Entorno(pantalla) { it in vis }

    @Test
    fun iniciarMideIniciadoYSaltaLoYaCumplido() {
        val r = Maquina.iniciar("agendar-cita", PASOS, en("Agenda"))
        assertEquals(EstadoTour(Fase.ACTIVO, "agendar-cita", 1, 4), r.estado)
        assertEquals(listOf(Medicion("iniciado", 1)), r.medir)
        // Con el formulario ya abierto, arranca en "paciente".
        assertEquals(2, Maquina.iniciar("x", PASOS, en("Agenda", listOf("cita_form"))).estado.paso)
        assertEquals(INACTIVO, Maquina.iniciar("x", emptyList(), en("Inicio")).estado)
    }

    @Test
    fun avanzaConLaAccionReal() {
        val s = Maquina.iniciar("x", PASOS, en("Inicio")).estado
        assertEquals(0, s.paso)
        var r = Maquina.procesarSenal(s, PASOS, "cita_creada", Senal.Pantalla("Agenda"), en("Agenda"))
        assertEquals(1, r.estado.paso)
        assertEquals(listOf(Medicion("paso", 1)), r.medir)
        val en1 = r.estado
        r = Maquina.procesarSenal(en1, PASOS, "cita_creada", Senal.Clic(listOf("otra")), en("Agenda"))
        assertEquals(1, r.estado.paso)
        r = Maquina.procesarSenal(en1, PASOS, "cita_creada", Senal.Dom { it == "cita_form" }, en("Agenda", listOf("cita_form")))
        assertEquals(2, r.estado.paso)
        r = Maquina.procesarSenal(r.estado, PASOS, "cita_creada", Senal.Clic(listOf("cita_form.paciente")), en("Agenda", listOf("cita_form")))
        assertEquals(3, r.estado.paso)
        r = Maquina.procesarSenal(r.estado, PASOS, "cita_creada", Senal.Tarea("cita_creada"), en("Agenda"))
        assertEquals(Fase.COMPLETADO, r.estado.fase)
        assertEquals(listOf(Medicion("completado", 3)), r.medir)
    }

    @Test
    fun laMetaCompletaDesdeCualquierPaso() {
        val s = Maquina.iniciar("x", PASOS, en("Agenda")).estado
        assertEquals(Fase.COMPLETADO, Maquina.procesarSenal(s, PASOS, "cita_creada", Senal.Tarea("cita_creada"), en("Agenda")).estado.fase)
        // Otra tarea no completa.
        assertEquals(Fase.ACTIVO, Maquina.procesarSenal(s, PASOS, "cita_creada", Senal.Tarea("paciente_creado"), en("Agenda")).estado.fase)
    }

    @Test
    fun sePausaAlIrseDeLaPantallaYSeReanudaAlVolver() {
        val s = Maquina.iniciar("x", PASOS, en("Agenda")).estado
        val p = Maquina.procesarSenal(s, PASOS, null, Senal.Pantalla("Pacientes"), en("Pacientes"))
        assertEquals(Fase.PAUSADO, p.estado.fase)
        // En pausa no avanza con toques.
        assertEquals(Fase.PAUSADO, Maquina.procesarSenal(p.estado, PASOS, null, Senal.Clic(listOf("agenda.nueva_cita")), en("Pacientes")).estado.fase)
        val v = Maquina.procesarSenal(p.estado, PASOS, null, Senal.Pantalla("Agenda"), en("Agenda"))
        assertEquals(EstadoTour(Fase.ACTIVO, "x", 1, 4), v.estado)
    }

    @Test
    fun unPasoSinPantallasNoSePausaAlNavegar() {
        val s = Maquina.iniciar("x", PASOS, en("Inicio")).estado
        assertEquals(Fase.ACTIVO, Maquina.procesarSenal(s, PASOS, null, Senal.Pantalla("Pacientes"), en("Pacientes")).estado.fase)
    }

    @Test
    fun saltarYPosponerMidenDondeQuedo() {
        val s = Maquina.iniciar("x", PASOS, en("Agenda")).estado
        assertEquals(Transicion(INACTIVO, listOf(Medicion("saltado", 1))), Maquina.abandonar(s, "saltado"))
        assertEquals(listOf(Medicion("pospuesto", 1)), Maquina.abandonar(s, "pospuesto").medir)
        assertEquals(emptyList(), Maquina.abandonar(INACTIVO, "saltado").medir)
    }

    @Test
    fun elUltimoPasoNuncaSeSaltaYAvanzarEnElUltimoCompleta() {
        val pasos = listOf(
            PasoApp("a", espera = espera("manual")),
            PasoApp("b", espera = espera("pantalla", "Agenda"), saltarSiYa = true),
        )
        val s = Maquina.iniciar("x", pasos, en("Agenda")).estado
        val r = Maquina.avanzar(s, pasos, en("Agenda"))
        assertEquals(Fase.ACTIVO, r.estado.fase)
        assertEquals(1, r.estado.paso)
        assertEquals(Fase.COMPLETADO, Maquina.avanzar(r.estado, pasos, en("Agenda")).estado.fase)
        assertEquals(emptyList(), Maquina.completar(INACTIVO).medir)
    }

    @Test
    fun omitePasosOpcionalesCuyaAnclaNoEstaEnPantalla() {
        val pasos = listOf(
            PasoApp("mas", anclas = listOf("nav.mas"), opcional = true, espera = espera("manual")),
            PasoApp("fin", anclas = listOf("mas.caja", "nav.mas"), opcional = true, espera = espera("manual")),
            PasoApp("ayuda", espera = espera("manual")),
        )
        assertEquals(1, Maquina.iniciar("x", pasos, en("Inicio", listOf("mas.caja"))).estado.paso)
        assertEquals(0, Maquina.iniciar("x", pasos, en("Inicio", listOf("nav.mas"))).estado.paso)
        // Sin ninguna: llega al último (que nunca se salta).
        assertEquals(2, Maquina.iniciar("x", pasos, en("Inicio")).estado.paso)
    }

    @Test
    fun saltarSiYaFalseNoSaltaAunqueYaSeCumpla() {
        val pasos = listOf(
            PasoApp("ir", espera = espera("pantalla", "Agenda"), saltarSiYa = false),
            PasoApp("fin", espera = espera("manual")),
        )
        assertEquals(0, Maquina.iniciar("x", pasos, en("Agenda")).estado.paso)
    }

    @Test
    fun esperaValorExigeElMinimoSinEspacios() {
        val paso = PasoApp("dni", espera = EsperaApp("valor", "paciente_form.dni", listOf("paciente_form.dni"), min = 8))
        assertEquals(false, cumpleEspera(paso.espera, Senal.Campo(listOf("paciente_form.dni"), " 1234567 ")))
        assertEquals(true, cumpleEspera(paso.espera, Senal.Campo(listOf("paciente_form.dni"), "12345678")))
        assertEquals(false, cumpleEspera(paso.espera, Senal.Campo(listOf("otro"), "12345678")))
        // Sin min: al menos 1.
        assertEquals(true, cumpleEspera(EsperaApp("valor", "cobro.medios"), Senal.Campo(listOf("cobro.medios"), "5")))
    }

    @Test
    fun esperaPantallaAceptaCualquieraDeSusValores() {
        val e = EsperaApp("pantalla", "Pacientes", listOf("Pacientes", "Ficha"))
        assertEquals(true, cumpleEspera(e, Senal.Pantalla("Ficha")))
        assertEquals(false, cumpleEspera(e, Senal.Pantalla("Agenda")))
        assertEquals(false, cumpleEspera(EsperaApp("manual"), Senal.Pantalla("Agenda")))
        // Un tipo que esta versión no conoce solo avanza con el botón.
        assertEquals(false, cumpleEspera(EsperaApp("nuevo_tipo", "x"), Senal.Clic(listOf("x"))))
    }

    @Test
    fun progreso() {
        assertEquals(0f, Maquina.progreso(INACTIVO))
        assertEquals(0.5f, Maquina.progreso(EstadoTour(Fase.ACTIVO, "x", 2, 4)))
        assertEquals(1f, Maquina.progreso(EstadoTour(Fase.COMPLETADO, "x", 3, 4)))
    }

    @Test
    fun alReabrirVuelveEnPausaYSoloRetomarLaLevanta() {
        assertEquals(EstadoTour(Fase.PAUSADO, "x", 3, 4, soloManual = true), Maquina.restaurar("x", 99, 4))
        val s2 = Maquina.procesarSenal(Maquina.restaurar("x", 1, 4), PASOS, "cita_creada", Senal.Pantalla("Agenda"), en("Agenda"))
        assertEquals(Fase.PAUSADO, s2.estado.fase)
        assertEquals(EstadoTour(Fase.ACTIVO, "x", 1, 4), Maquina.reanudar(Maquina.restaurar("x", 1, 4)).estado)
        assertEquals(
            Fase.COMPLETADO,
            Maquina.procesarSenal(Maquina.restaurar("x", 1, 4), PASOS, "cita_creada", Senal.Tarea("cita_creada"), en("Agenda")).estado.fase,
        )
    }

    @Test
    fun pausaGuardadaSeCodificaYDecodifica() {
        assertEquals("agendar-cita" to 2, decodificarPausa(codificarPausa("agendar-cita", 2)))
        assertNull(decodificarPausa(null))
        assertNull(decodificarPausa("basura"))
        assertEquals("x" to 0, decodificarPausa("x|zz"))
    }
}
