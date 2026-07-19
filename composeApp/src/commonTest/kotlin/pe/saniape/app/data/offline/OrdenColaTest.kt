package pe.saniape.app.data.offline

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * El orden de la cola importa: una cita que se agendó offline a un paciente
 * recién creado NO puede enviarse antes que el paciente (llegaría con un id
 * inexistente). [puedeEnviarse] es quien lo decide.
 */
class OrdenColaTest {

    private fun op(id: Long, dependeDe: Long?) =
        OpPendiente(
            id = id, idemKey = "k$id", tipo = "t", endpoint = "/e",
            payload = "{}", dependeDe = dependeDe, idTemporal = null, intentos = 0,
        )

    @Test
    fun sinDependenciaSeEnviaSiempre() {
        assertTrue(puedeEnviarse(op(1, null), emptySet()))
    }

    @Test
    fun conDependenciaPendienteNoSeEnvia() {
        assertFalse(puedeEnviarse(op(2, 1), emptySet()))
    }

    @Test
    fun conDependenciaHechaSeEnvia() {
        assertTrue(puedeEnviarse(op(2, 1), setOf(1L)))
    }
}
