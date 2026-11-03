package pe.saniape.app.data.staff

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Gemelo del bloque «alergias: lo negativo no es alerta» de otorrino-video-fixes.test.ts. */
class HayAlergiaTest {
    @Test fun loNegativoNoEsAlerta() {
        for (t in listOf("Niega alergias", "niega", "Ninguna", "No refiere", "NKDA", "Sin alergias conocidas", "-", "", null, "no", "Ninguna conocida.", "N/A", "  ninguna  ")) {
            assertFalse(hayAlergia(t), "«$t» no debería ser alerta")
        }
    }

    @Test fun loDemasSiEsAlerta() {
        for (t in listOf("Penicilina", "AINES, látex", "Alergia a mariscos", "No tolera la penicilina", "Niacina")) {
            assertTrue(hayAlergia(t), "«$t» debería ser alerta")
        }
    }
}
