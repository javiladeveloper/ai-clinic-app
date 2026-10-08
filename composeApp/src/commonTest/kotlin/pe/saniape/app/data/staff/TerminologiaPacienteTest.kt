package pe.saniape.app.data.staff

import kotlin.test.Test
import kotlin.test.assertEquals

class TerminologiaPacienteTest {
    @Test fun sinDatosEsPaciente() {
        val t = TerminologiaPaciente.de(null, null)
        assertEquals(TerminologiaPaciente(), t)
        assertEquals("Pacientes", t.Pacientes)
    }

    @Test fun clienteDerivaPlural() {
        val t = TerminologiaPaciente.de("cliente", "")
        assertEquals("cliente", t.paciente); assertEquals("clientes", t.pacientes)
        assertEquals("Cliente", t.Paciente); assertEquals("Clientes", t.Pacientes)
    }

    @Test fun pluralExplicito() {
        val t = TerminologiaPaciente.de("Consultante", "Consultantes ")
        assertEquals("Consultantes", t.Pacientes)
    }
}
