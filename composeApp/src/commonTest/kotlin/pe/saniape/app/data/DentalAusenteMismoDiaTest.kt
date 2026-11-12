package pe.saniape.app.data

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import pe.saniape.app.data.staff.DienteHallazgo
import pe.saniape.app.data.staff.HallazgoDental
import pe.saniape.app.data.staff.ProcedimientoRef
import pe.saniape.app.data.staff.agruparPresupuesto
import pe.saniape.app.data.staff.anteriorALaAusencia

/**
 * Pieza ausente: lo registrado el MISMO día DESPUÉS de marcarla ausente (un
 * implante) sigue en el presupuesto (auditoría dental 09/11, I1). Gemelo de
 * lib/__tests__/odontograma-ausente-mismo-dia.test.ts de la web.
 */
class DentalAusenteMismoDiaTest {
    private val cat = listOf(
        HallazgoDental(id = "aus", nombre = "Ausente", color = "#6b7280", marcaAusente = true),
        HallazgoDental(id = "imp", nombre = "Implante indicado", color = "#dc2626", procedimientoId = "p-imp"),
        HallazgoDental(id = "car", nombre = "Caries", color = "#dc2626", procedimientoId = "p-res"),
    )
    private val procs = listOf(
        ProcedimientoRef(id = "p-imp", nombre = "Implante", precio = 2800.0, precioPaquete = null, especialidadId = null, usaSesiones = false, tarifarios = emptyList()),
        ProcedimientoRef(id = "p-res", nombre = "Resina", precio = 90.0, precioPaquete = null, especialidadId = null, usaSesiones = false, tarifarios = emptyList()),
    )
    private fun reg(id: String, hallazgoId: String, creado: String?) = DienteHallazgo(
        id = id, pacienteId = "pa", diente = "36", hallazgoId = hallazgoId, fecha = "2026-11-12", createdAt = creado,
    )

    @Test
    fun implante_registrado_despues_de_la_ausencia_el_mismo_dia_se_presupuesta() {
        val (lineas, _) = agruparPresupuesto(
            listOf(reg("a", "aus", "2026-11-12T15:00:00+00:00"), reg("i", "imp", "2026-11-12T15:05:00+00:00")), cat, procs,
        )
        assertEquals(listOf("Implante"), lineas.map { it.nombre })
    }

    @Test
    fun caries_registrada_antes_de_la_ausencia_el_mismo_dia_ya_no_se_cobra() {
        val (lineas, _) = agruparPresupuesto(
            listOf(reg("c", "car", "2026-11-12T14:00:00+00:00"), reg("a", "aus", "2026-11-12T15:00:00+00:00")), cat, procs,
        )
        assertTrue(lineas.isEmpty())
    }

    @Test
    fun filas_viejas_sin_hora_se_comparan_por_dia() {
        assertTrue(anteriorALaAusencia("2026-11-12", null, "2026-11-12", null))
        assertFalse(anteriorALaAusencia("2026-11-13", null, "2026-11-12", "2026-11-12T15:00:00+00:00"))
    }
}
