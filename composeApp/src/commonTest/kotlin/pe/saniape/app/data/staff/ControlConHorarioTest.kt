package pe.saniape.app.data.staff

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Gemelo de lib/__tests__/control-con-horario.test.ts (web, 2026-10-16): "en 30
 * días" desde el jueves 2026-10-15 proponía el sábado 2026-11-14 y la agenda
 * respondía "sin cupo".
 */
class ControlConHorarioTest {

    private val LV = listOf("Lun", "Mar", "Mié", "Jue", "Vie").map { FranjaHorarioControl(it, "09:00:00", "18:00:00", true) }
    private val CITA = "2026-10-15" // jueves

    @Test fun sinHorarioAunLaDeSiempre() {
        assertEquals(sumarDiasIso(CITA, 30), fechaControlConHorario(CITA, 30, "15:00", null))
        assertEquals("2026-11-14", sumarDiasIso(CITA, 30)) // sábado
    }

    @Test fun caeSabadoYAtiendeLVPasaALunes() {
        assertEquals("2026-11-16", fechaControlConHorario(CITA, 30, "15:00", LV))
    }

    @Test fun sabadoSoloDeManana() {
        val conSab = LV + FranjaHorarioControl("Sáb", "09:00", "13:00", true)
        assertEquals("2026-11-16", fechaControlConHorario(CITA, 30, "15:00", conSab))
        assertEquals("2026-11-14", fechaControlConHorario(CITA, 30, "10:00", conSab))
    }

    @Test fun unDiaQueAtiendeNoSeMueve() {
        assertEquals("2026-10-22", fechaControlConHorario(CITA, 7, "10:00", LV))
    }

    @Test fun franjasInactivasNoCuentan() {
        val sabInactivo = LV + FranjaHorarioControl("Sáb", "09:00", "18:00", false)
        assertEquals("2026-11-16", fechaControlConHorario(CITA, 30, "10:00", sabInactivo))
    }

    @Test fun todasInactivasEsNoAtiende() {
        val inactivas = LV.map { it.copy(activo = false) }
        assertEquals("2026-11-14", fechaControlConHorario(CITA, 30, "15:00", inactivas))
    }

    @Test fun sinFilasHorarioPorDefecto() {
        assertEquals("2026-11-14", fechaControlConHorario(CITA, 30, "10:00", emptyList()))
        assertEquals("2026-11-16", fechaControlConHorario(CITA, 30, "15:00", emptyList()))
        assertEquals("2026-10-19", fechaControlConHorario("2026-10-11", 7, "10:00", emptyList()))
    }

    @Test fun aDemandaNoSeCorre() {
        assertEquals("2026-11-14", fechaControlConHorario(CITA, 30, "15:00", LV, flexible = true))
    }

    @Test fun ningunDiaConEsaHoraPrimerDiaQueAtiende() {
        assertEquals("2026-11-16", fechaControlConHorario(CITA, 30, "20:00", LV))
    }

    // ── multisede ──

    private val filas = listOf(
        FranjaHorarioControl("Sáb", "09:00", "18:00", true, null),   // principal
        FranjaHorarioControl("Lun", "09:00", "18:00", true, "B"),
        FranjaHorarioControl("Mar", "09:00", "18:00", false, "B"),
    )

    @Test fun sinSedeTodasLasActivas() {
        assertEquals(listOf("Sáb", "Lun"), franjasParaControl(filas)?.map { it.dia })
    }

    @Test fun laFranjaSinSedeEsDeLaPrincipal() {
        assertEquals(listOf("Sáb"), franjasParaControl(filas, "A", "A")?.map { it.dia })
        assertEquals(listOf("Lun"), franjasParaControl(filas, "B", "A")?.map { it.dia })
    }

    @Test fun sinFranjasEnEsaSedeNoAtiendeAhi() {
        assertEquals(emptyList(), franjasParaControl(filas, "C", "A"))
        assertEquals("2026-11-14", fechaControlConHorario(CITA, 30, "10:00", filas, sedeId = "C", principalId = "A"))
    }

    @Test fun laFechaUsaSoloLaSedeDeLaCita() {
        assertEquals("2026-11-16", fechaControlConHorario(CITA, 30, "10:00", filas, sedeId = "B", principalId = "A"))
        assertEquals("2026-11-14", fechaControlConHorario(CITA, 30, "10:00", filas, sedeId = "A", principalId = "A"))
    }

    @Test fun sinFilasElDefecto() {
        assertTrue((franjasParaControl(emptyList())?.size ?: 0) > 0)
        assertNull(franjasParaControl(null))
    }
}
