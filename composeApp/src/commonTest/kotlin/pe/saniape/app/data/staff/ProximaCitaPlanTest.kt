package pe.saniape.app.data.staff

import kotlin.test.Test
import kotlin.test.assertEquals

/** «📅 … agendada» en el aviso de tratamientos abiertos (gemelo de PlanPaciente.tsx web, 2026-11-05). */
class ProximaCitaPlanTest {

    @Test fun laPrimeraDeCadaTratamientoSinImportarElOrden() {
        val m = primeraCitaPorTratamiento(listOf(
            ProximaCitaPlan("resina", "2026-11-12", "10:00", null),
            ProximaCitaPlan("resina", "2026-11-06", "09:00:00", "Dr. Mamani"),
            ProximaCitaPlan("extraccion", "2026-11-06", "11:30", null),
            ProximaCitaPlan("resina", "2026-11-06", "08:00", null),
        ))
        assertEquals("08:00", m["resina"]?.hora)
        assertEquals("2026-11-06", m["extraccion"]?.fecha)
        assertEquals(2, m.size)
    }

    @Test fun elTextoDiceQueEsCuandoYConQuien() {
        val c = ProximaCitaPlan("resina", "2026-11-06", "09:00:00", "Dr. Luis Fernando Mamani")
        assertEquals("📅 Intervención agendada · vie 06/11 · 09:00 · Dr. Luis Fernando Mamani",
            textoCitaAgendada("Unidades", 0, esConsulta = false, c = c))
        assertEquals("📅 Sesión agendada · vie 06/11 · 09:00",
            textoCitaAgendada("Paquete", 10, esConsulta = false, c = c.copy(terapeutaNombre = null)))
        assertEquals("📅 Cita agendada · vie 06/11",
            textoCitaAgendada("Sesión suelta", 0, esConsulta = true, c = c.copy(hora = null, terapeutaNombre = "")))
    }
}
