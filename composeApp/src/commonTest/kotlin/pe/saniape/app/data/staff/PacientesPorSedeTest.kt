package pe.saniape.app.data.staff

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** Sede del paciente al crearlo: solo con la opción por clínica y una sede puntual activa. */
class PacientesPorSedeTest {

    @Test
    fun sinLaOpcionNoSeManda() {
        assertNull(sedeParaPacienteNuevo(false, "s1"))
    }

    @Test
    fun conOpcionYSedeActivaSeManda() {
        assertEquals("s1", sedeParaPacienteNuevo(true, "s1"))
    }

    @Test
    fun consolidadoOSinSedeNoSeManda() {
        assertNull(sedeParaPacienteNuevo(true, ""))
        assertNull(sedeParaPacienteNuevo(true, null))
    }

    @Test
    fun elContextoPorDefectoEsFalse() {
        assertEquals(false, EstadoSede().pacientesPorSede)
        assertNull(EstadoSede().sedePacientes)
    }

    // ── Lista de pacientes por sede (gemelo de app/(app)/pacientes/page.tsx) ──

    @Test
    fun listaAdminConSedeElegidaFiltraPorEsaSede() {
        assertEquals("s1", sedeListaPacientes(multiSede = true, pacientesPorSede = true, sinLimite = true, sedeId = "s1"))
    }

    @Test
    fun listaEnConsolidadoNoFiltra() {
        assertNull(sedeListaPacientes(multiSede = true, pacientesPorSede = true, sinLimite = true, sedeId = ""))
    }

    @Test
    fun listaSinLaOpcionOSinMultisedeNoFiltra() {
        assertNull(sedeListaPacientes(multiSede = true, pacientesPorSede = false, sinLimite = true, sedeId = "s1"))
        assertNull(sedeListaPacientes(multiSede = false, pacientesPorSede = true, sinLimite = true, sedeId = "s1"))
    }

    @Test
    fun listaDelLimitadoLaAcotaLaRls() {
        assertNull(sedeListaPacientes(multiSede = true, pacientesPorSede = true, sinLimite = false, sedeId = "s1"))
    }

    @Test
    fun estadoSedeExponeLaSedeDeLaLista() {
        val e = EstadoSede(multiSede = true, sedeId = "s2", pacientesPorSede = true, sinLimiteSedes = true)
        assertEquals("s2", e.sedePacientes)
        assertNull(e.copy(sedeId = "").sedePacientes)
    }
}
