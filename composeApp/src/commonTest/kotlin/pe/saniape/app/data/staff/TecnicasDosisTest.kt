package pe.saniape.app.data.staff

import kotlin.test.Test
import kotlin.test.assertEquals

/** La dosis escrita se queda en la sesión; el catálogo aprende sin ella (DALU, 19/10/2026). */
class TecnicasDosisTest {
    @Test fun tensConSuDosis() =
        assertEquals(listOf("TENS 170-35"), TecnicasNormalizar.partirParaSesion("tens 170-35"))

    @Test fun variasCadaUnaConLoSuyo() =
        assertEquals(listOf("TENS 170-35", "Compresa"), TecnicasNormalizar.partirParaSesion("tens 170-35 + comprensa"))

    @Test fun sinDosisIgualQuePartir() =
        assertEquals(TecnicasNormalizar.partir("Liberacion miofacial"), TecnicasNormalizar.partirParaSesion("Liberacion miofacial"))

    @Test fun elCatalogoAprendeSinDosis() =
        assertEquals(listOf("TENS"), TecnicasNormalizar.partir("TENS 170-35"))
}
