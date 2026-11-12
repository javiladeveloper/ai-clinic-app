package pe.saniape.app.data.staff

import kotlin.test.Test
import kotlin.test.assertEquals

/** Flujo #2 estética (gemelo de lib/__tests__/foto-cierre): la foto del cierre por rubro. */
class FotoCierreTest {
    @Test fun esteticaYCapilarGuardanDespues() {
        assertEquals("📷 Foto «Después»" to MOMENTO_DESPUES, fotoCierre("estetica"))
        assertEquals("📷 Foto «Después»" to MOMENTO_DESPUES, fotoCierre("capilar"))
        assertEquals(MOMENTO_DURANTE, fotoCierre("fisio").second)
        assertEquals(MOMENTO_DURANTE, fotoCierre(null).second)
    }

    @Test fun porLaEspecialidadElRubroGuardadoManda() {
        assertEquals(MOMENTO_DESPUES, fotoCierreDeEspecialidad("estetica", "Cualquier nombre").second)
        assertEquals(MOMENTO_DURANTE, fotoCierreDeEspecialidad("fisio", "Medicina estética").second)
    }
}
