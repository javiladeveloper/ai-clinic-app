package pe.saniape.app.data.staff

import kotlin.test.Test
import kotlin.test.assertEquals

/** FISIOSALUD (25/11/2026): «quiere saber qué pacientes nuevos llegaron por qué tratamiento». Gemelo de la web. */
class PacientesNuevosServicioTest {
    private fun fila(id: String, servicio: String?, etapa: String) =
        FilaPacienteNuevo(pacienteId = id, etapa = etapa, tratamiento = servicio?.let { TratamientoNuevo(id = "t$id", servicio = it) })

    private val filas = listOf(
        fila("1", "Infiltración articular", EtapaNuevo.PAGO),
        fila("2", "Infiltración articular", EtapaNuevo.EN_TRATAMIENTO),
        fila("3", "Fisioterapia", EtapaNuevo.PAGO),
        fila("4", null, EtapaNuevo.EVALUADO),
        fila("5", null, EtapaNuevo.SIN_EVALUACION),
    )

    @Test fun `opciones del mas frecuente al menos y sin tratamiento al final`() {
        assertEquals(listOf("Infiltración articular" to 2, "Fisioterapia" to 1, SIN_TRATAMIENTO_NUEVOS to 2),
            serviciosDeFilasNuevos(filas).map { it.valor to it.n })
    }

    @Test fun `con un tratamiento elegido la lista y el embudo son solo de esos`() {
        val infil = filtrarPorServicioNuevos(filas, "Infiltración articular")
        assertEquals(listOf("1", "2"), infil.map { it.pacienteId })
        assertEquals(EmbudoNuevos(2, 2, 2, 1), embudoDeFilasNuevos(infil))
        assertEquals(listOf("4", "5"), filtrarPorServicioNuevos(filas, SIN_TRATAMIENTO_NUEVOS).map { it.pacienteId })
        assertEquals(5, filtrarPorServicioNuevos(filas, null).size)
        assertEquals(EmbudoNuevos(5, 4, 3, 2), embudoDeFilasNuevos(filas))
    }
}
