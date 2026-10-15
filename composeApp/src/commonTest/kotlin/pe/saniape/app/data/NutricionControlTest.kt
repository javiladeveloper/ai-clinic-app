package pe.saniape.app.data

import kotlin.coroutines.Continuation
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.coroutines.startCoroutine
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import pe.saniape.app.data.staff.AtencionNutricion
import pe.saniape.app.data.staff.CitaNutricionControl
import pe.saniape.app.data.staff.FlujoClinica
import pe.saniape.app.data.staff.abreVentanaAlCompletar
import pe.saniape.app.data.staff.FilaAtendio
import pe.saniape.app.data.staff.FilaCitaNutricion
import pe.saniape.app.data.staff.FilaPlanNutricion
import pe.saniape.app.data.staff.PlanNutricionRef
import pe.saniape.app.data.staff.SeguimientoNutricion
import pe.saniape.app.data.staff.TOPE_SEGUIMIENTO
import pe.saniape.app.data.staff.abreEvaluacionAlCompletar
import pe.saniape.app.data.staff.armarSeguimientoNutricion
import pe.saniape.app.data.staff.atendioAlPaciente
import pe.saniape.app.data.staff.esControlDeNutricion
import pe.saniape.app.data.staff.especialidadEsNutricionPorRubros
import pe.saniape.app.data.staff.faltaPlanNutricion
import pe.saniape.app.data.staff.ofreceAgendarControlNutri
import pe.saniape.app.data.staff.permiteControlPropioNutricion

/**
 * Nutrición: control vs primera consulta, chip "🩺 Falta plan" y "📅 Agendar
 * siguiente control". Los mismos casos que `lib/__tests__/nutricion-control.test.ts`
 * de la web (demo "Centro Médico Integral Vida": Carmen, consultas mensuales de
 * nutrición SIN tratamiento, con planes alimentarios hechos en dos de ellas).
 */
class NutricionControlTest {

    private val vacio = SeguimientoNutricion()
    private val primera = CitaNutricionControl("c-0618", "Consulta", "2026-06-18", "17:00:00")
    private val control = CitaNutricionControl("c-1008", "Consulta", "2026-10-08", "17:00:00")
    private val carmen = SeguimientoNutricion(
        atenciones = listOf(
            AtencionNutricion("c-0618", "2026-06-18", "17:00:00"),
            AtencionNutricion("c-0716", "2026-07-16", "17:00:00"),
            AtencionNutricion("c-1008", "2026-10-08", "17:00:00"),
        ),
        planes = listOf(PlanNutricionRef("c-0618", "2026-06-18"), PlanNutricionRef("c-1008", "2026-10-08")),
    )

    /** Corre un suspend que no se suspende de verdad (lecturas falsas). */
    private fun <T> correr(bloque: suspend () -> T): T {
        var r: Result<T>? = null
        bloque.startCoroutine(Continuation(EmptyCoroutineContext) { r = it })
        return r!!.getOrThrow()
    }

    // ── esControlDeNutricion ────────────────────────────────────────────────

    @Test fun pacienteEnSeguimientoEsControl() {
        assertTrue(esControlDeNutricion(control, carmen))
    }

    @Test fun laProximaCitaAunSinCompletarTambien() {
        assertTrue(esControlDeNutricion(CitaNutricionControl("c-1105", "Consulta", "2026-11-05", "17:00"), carmen))
    }

    @Test fun laPrimeraConsultaEvaluaAunqueElPlanSeHayaHechoEnElla() {
        assertFalse(esControlDeNutricion(primera, carmen))
        assertFalse(esControlDeNutricion(primera, vacio))
    }

    @Test fun pacienteNuevoSinNadaEvalua() {
        assertFalse(esControlDeNutricion(CitaNutricionControl("x", "Consulta", "2026-10-15"), vacio))
    }

    @Test fun planDeOtraAtencionLoPoneEnSeguimiento() {
        assertTrue(esControlDeNutricion(CitaNutricionControl("x", "Consulta", "2026-10-15"),
            SeguimientoNutricion(planes = listOf(PlanNutricionRef("otra", "2026-09-01")))))
    }

    @Test fun planDesdeLaFichaCuentaSiEsDeUnDiaAnterior() {
        val cita = CitaNutricionControl("x", "Consulta", "2026-10-15")
        assertTrue(esControlDeNutricion(cita, SeguimientoNutricion(planes = listOf(PlanNutricionRef(null, "2026-10-01")))))
        assertFalse(esControlDeNutricion(cita, SeguimientoNutricion(planes = listOf(PlanNutricionRef(null, "2026-10-15")))))
    }

    @Test fun atencionCompletadaDespuesNoLaVuelveControl() {
        assertFalse(esControlDeNutricion(primera, SeguimientoNutricion(atenciones = carmen.atenciones)))
    }

    @Test fun elMismoDiaManda()  {
        val s = SeguimientoNutricion(atenciones = listOf(AtencionNutricion("m", "2026-10-15", "09:00:00")))
        assertTrue(esControlDeNutricion(CitaNutricionControl("t", "Consulta", "2026-10-15", "16:00:00"), s))
        assertFalse(esControlDeNutricion(CitaNutricionControl("t", "Consulta", "2026-10-15", "08:00:00"), s))
    }

    @Test fun ligadaATratamientoQueNoNacioDeEllaEsControl() {
        val cita = CitaNutricionControl("c2", "Consulta", "2026-10-15", tratamientoId = "t1")
        assertTrue(esControlDeNutricion(cita, vacio.copy(origenTratamiento = mapOf("t1" to "c1"))))
        assertTrue(esControlDeNutricion(cita, vacio.copy(origenTratamiento = mapOf("t1" to null))))
    }

    @Test fun ligadaAlTratamientoQueNacioDeEllaSigueEvaluando() {
        val cita = CitaNutricionControl("c1", "Consulta", "2026-10-15", tratamientoId = "t1")
        assertFalse(esControlDeNutricion(cita, vacio.copy(origenTratamiento = mapOf("t1" to "c1"))))
        // Sin poder leer el tratamiento no se inventa un control.
        assertFalse(esControlDeNutricion(cita, vacio))
    }

    @Test fun soloDecideSobreConsultas() {
        assertFalse(esControlDeNutricion(control.copy(tipo = "Evaluación"), carmen))
        assertFalse(esControlDeNutricion(control.copy(tipo = "Sesión"), carmen))
    }

    // ── faltaPlanNutricion ──────────────────────────────────────────────────

    @Test fun faltaPlan() {
        assertFalse(faltaPlanNutricion(control, carmen, false))
        assertFalse(faltaPlanNutricion(control, SeguimientoNutricion(atenciones = carmen.atenciones), false))
        assertFalse(faltaPlanNutricion(primera, SeguimientoNutricion(listOf(carmen.atenciones[0]), listOf(PlanNutricionRef("c-0618", "2026-06-18"))), false))
        assertTrue(faltaPlanNutricion(primera, SeguimientoNutricion(listOf(carmen.atenciones[0])), false))
        assertFalse(faltaPlanNutricion(primera, vacio, true))
    }

    // ── ofreceAgendarControlNutri ───────────────────────────────────────────

    private fun ofrece(
        puedeAgendar: Boolean = false, puedeSesiones: Boolean = true, miTerapeutaId: String? = "valeria",
        atendioAntes: Boolean = true, soloLectura: Boolean = false, inactivo: Boolean = false, citasPorDelante: Int = 0,
    ) = ofreceAgendarControlNutri(puedeAgendar, puedeSesiones, miTerapeutaId, atendioAntes, soloLectura, inactivo, citasPorDelante)

    @Test fun laNutricionistaConSuPacienteLoVe() = assertTrue(ofrece())

    @Test fun enfermeriaSinFichaDeProfesionalNo() = assertFalse(ofrece(miTerapeutaId = null))

    @Test fun laProfesionalQueNuncaAtendioNo() = assertFalse(ofrece(atendioAntes = false))

    @Test fun recepcionLoVeAunqueNoSeaProfesional() =
        assertTrue(ofrece(puedeAgendar = true, puedeSesiones = false, miTerapeutaId = null, atendioAntes = false))

    @Test fun sinNingunPermisoNo() = assertFalse(ofrece(puedeSesiones = false))

    @Test fun conCitaPorDelanteSoloLecturaOFichaDeBajaNo() {
        for (agendar in listOf(false, true)) {
            assertFalse(ofrece(puedeAgendar = agendar, citasPorDelante = 1))
            assertFalse(ofrece(puedeAgendar = agendar, soloLectura = true))
            assertFalse(ofrece(puedeAgendar = agendar, inactivo = true))
        }
    }

    // ── atendioAlPaciente ───────────────────────────────────────────────────

    @Test fun atendioCuentaCitaCompletadaTratamientoOPlan() {
        assertTrue(atendioAlPaciente("v", citas = listOf(FilaAtendio("v", "Completada"))))
        assertTrue(atendioAlPaciente("v", tratamientos = listOf(FilaAtendio("v", "Activo"))))
        assertTrue(atendioAlPaciente("v", planes = listOf(FilaAtendio("v"))))
    }

    @Test fun atendioNoCuentaLoAjeno() {
        assertFalse(atendioAlPaciente("v",
            citas = listOf(FilaAtendio("otro", "Completada"), FilaAtendio("v", "Confirmada")),
            tratamientos = listOf(FilaAtendio("v", "Eliminado")),
            planes = listOf(FilaAtendio("otro")),
        ))
    }

    @Test fun atendioSinFichaDeProfesionalNunca() =
        assertFalse(atendioAlPaciente(null, citas = listOf(FilaAtendio(null, "Completada"))))

    // ── permiteControlPropioNutricion (el gate del servidor) ─────────────────

    private fun permite(
        tipo: String = "Consulta", puedeSesiones: Boolean = true, miTerapeutaId: String? = "v",
        terapeutaCita: String? = "v", especialidadEsNutricion: Boolean = true, atendioAntes: Boolean = true,
    ) = permiteControlPropioNutricion(tipo, puedeSesiones, miTerapeutaId, terapeutaCita, especialidadEsNutricion, atendioAntes)

    @Test fun elControlDeSuPacienteDeNutricionSi() = assertTrue(permite())

    @Test fun cadaCondicionEsObligatoria() {
        assertFalse(permite(tipo = "Evaluación"))
        assertFalse(permite(puedeSesiones = false))
        assertFalse(permite(miTerapeutaId = null, terapeutaCita = null))
        assertFalse(permite(terapeutaCita = "otro"))     // agendarle a otro
        assertFalse(permite(terapeutaCita = null))       // sin profesional
        assertFalse(permite(especialidadEsNutricion = false))  // fisio, medicina…
        assertFalse(permite(atendioAntes = false))       // paciente ajeno o nuevo
    }

    @Test fun especialidadDeNutricionPorRubro() {
        assertTrue(especialidadEsNutricionPorRubros(listOf("nutricion")))
        assertFalse(especialidadEsNutricionPorRubros(listOf("nutricion", "fisioterapia")))
        assertFalse(especialidadEsNutricionPorRubros(listOf("fisioterapia")))
        // Lectura fallida: niega (nunca abre de más).
        assertFalse(especialidadEsNutricionPorRubros(emptyList()))
    }

    // ── abreEvaluacionAlCompletar ───────────────────────────────────────────

    private val citaC = CitaNutricionControl("c-1105", "Consulta", "2026-11-05", "17:00")
    private val conHistoria = SeguimientoNutricion(atenciones = listOf(AtencionNutricion("c-0618", "2026-06-18", "17:00:00")))

    private fun abre(
        cita: CitaNutricionControl = citaC, evalua: Boolean = true, dental: Boolean = false, nutri: Boolean = true,
        leer: suspend () -> SeguimientoNutricion?,
    ) = correr { abreEvaluacionAlCompletar(cita, "p1", evalua, dental, nutri, leer) }

    @Test fun elControlDeNutricionNoAbreLaEvaluacion() = assertFalse(abre { conHistoria })

    @Test fun laPrimeraConsultaDeNutricionSi() = assertTrue(abre { vacio })

    @Test fun lecturaFallidaEvaluaComoSiempre() {
        assertTrue(abre { null })
        assertTrue(abre { throw IllegalStateException("sin red") })
    }

    @Test fun citaQueNoEsDeNutricionNoLeeNada() {
        var lecturas = 0
        val leer: suspend () -> SeguimientoNutricion? = { lecturas++; conHistoria }
        assertTrue(abre(nutri = false, leer = leer))
        assertFalse(abre(nutri = false, evalua = false, leer = leer))
        assertTrue(abre(dental = true, leer = leer))
        assertTrue(abre(cita = citaC.copy(tipo = "Evaluación"), leer = leer))
        assertEquals(0, lecturas)
    }

    @Test fun loQueNoEvaluaNoLaAbre() =
        assertFalse(abre(cita = citaC.copy(tipo = "Sesión"), evalua = false) { conHistoria })

    @Test fun sinPacienteEvaluaSinLeer() {
        var lecturas = 0
        assertTrue(correr { abreEvaluacionAlCompletar(citaC, null, true, false, true) { lecturas++; conHistoria } })
        assertEquals(0, lecturas)
    }

    // ── "✓ Completar" / "✓ Sí vino": ¿ventana de cierre o directo? (citas/page.tsx) ──

    /** La decisión completa de la agenda: flujo de la cita → abre evaluación → ventana. */
    private fun ventana(
        tipo: String, flujo: FlujoClinica, dental: Boolean = false, nutri: Boolean = false,
        seg: SeguimientoNutricion? = vacio,
    ): Boolean {
        val cita = citaC.copy(tipo = tipo)
        val abre = correr {
            abreEvaluacionAlCompletar(cita, "p1", flujo.esCitaQueEvalua(tipo), dental, nutri) { seg }
        }
        return abreVentanaAlCompletar(abre, tipo)
    }

    private val sinEvaluacion = FlujoClinica(usaConsulta = true, usaEvaluacion = false)   // medicina, estética, nutrición
    private val conEvaluacion = FlujoClinica(usaConsulta = true, usaEvaluacion = true)    // fisio, odontología

    @Test fun siVinoConsultaQueEvaluaPideDiagnostico() {
        // Medicina (también la consulta médica guiada: en "Sí vino" la web abre la evaluación) y estética.
        assertTrue(ventana("Consulta", sinEvaluacion))
        // La primera consulta de nutrición, también.
        assertTrue(ventana("Consulta", sinEvaluacion, nutri = true, seg = vacio))
    }

    @Test fun siVinoControlDeNutricionSeCierraDirecto() =
        assertFalse(ventana("Consulta", sinEvaluacion, nutri = true, seg = conHistoria))

    @Test fun siVinoNutricionConLecturaFallidaPideDiagnostico() =
        assertTrue(ventana("Consulta", sinEvaluacion, nutri = true, seg = null))

    @Test fun siVinoEvaluacionYDentalAbrenLaVentana() {
        assertTrue(ventana("Evaluación", conEvaluacion))
        // Odontología: la ventana es la revisión previa (dental + evalúa), como setRevisionPrevia.
        assertTrue(ventana("Evaluación", conEvaluacion, dental = true))
        // Aunque la dental fuera de nutrición, no se lee nada: evalúa.
        assertTrue(ventana("Evaluación", conEvaluacion, dental = true, nutri = true, seg = conHistoria))
    }

    @Test fun siVinoConsultaDeFlujoConEvaluacionSeCierraDirecto() =
        assertFalse(ventana("Consulta", conEvaluacion))

    @Test fun siVinoSesionAbreSuVentana() {
        assertTrue(ventana("Sesión", conEvaluacion))
        assertTrue(ventana("Sesión", sinEvaluacion, nutri = true, seg = conHistoria))
    }

    // ── armarSeguimientoNutricion (el armado de leerSeguimientoNutricion) ─────

    private val esNutri: (FilaCitaNutricion) -> Boolean = { it.especialidadId == "nutri" }

    @Test fun armaAtencionesDeNutricionYPlanes() {
        val m = armarSeguimientoNutricion(
            listOf("p1", "p1"),
            citas = listOf(
                FilaCitaNutricion("a", "p1", "2026-09-10", "17:00:00", "nutri"),
                FilaCitaNutricion("b", "p1", "2026-09-11", "10:00:00", "derma"),
            ),
            planes = listOf(FilaPlanNutricion("p1", "a", "2026-09-10")),
            origen = mapOf("t1" to "a"),
            esNutricion = esNutri,
        )
        assertEquals(
            SeguimientoNutricion(
                atenciones = listOf(AtencionNutricion("a", "2026-09-10", "17:00:00")),
                planes = listOf(PlanNutricionRef("a", "2026-09-10")),
                origenTratamiento = mapOf("t1" to "a"),
            ),
            m?.get("p1"),
        )
    }

    @Test fun lecturaQueLlegaAlTopeNoDecide() {
        val llenas = List(TOPE_SEGUIMIENTO) { FilaCitaNutricion("c$it", "p1", "2026-01-01", null, "nutri") }
        assertNull(armarSeguimientoNutricion(listOf("p1"), llenas, emptyList(), null, esNutri))
        val planes = List(TOPE_SEGUIMIENTO) { FilaPlanNutricion("p1", null, "2026-01-01") }
        assertNull(armarSeguimientoNutricion(listOf("p1"), emptyList(), planes, null, esNutri))
    }

    @Test fun sinPermisoParaLosPlanesDecideConLasAtenciones() {
        val m = armarSeguimientoNutricion(listOf("p1"), emptyList(), null, null, esNutri)
        assertEquals(SeguimientoNutricion(emptyList(), emptyList(), emptyMap()), m?.get("p1"))
    }

    @Test fun citasIlegiblesDevuelveNull() =
        assertNull(armarSeguimientoNutricion(listOf("p1"), null, emptyList(), null, esNutri))

    @Test fun sinPacientesMapaVacio() =
        assertEquals(emptyMap(), armarSeguimientoNutricion(emptyList(), null, null, null, esNutri))
}
