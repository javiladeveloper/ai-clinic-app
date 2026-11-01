package pe.saniape.app.data.staff

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Ampliaciones junto a los pagos y motivo obligatorio (gemelos de la web, DALU 01/11). */
class AmpliacionesTest {
    @Test
    fun leeLasLineasDeAmpliacion() {
        val notas = "Prefiere las tardes\nAmpliación 2026-10-29: +5 sesión(es) por S/ 300.00 — Etapa de ejercicios y promo fiestas\nAmpliación 2026-11-05: +1 sesión(es) por S/ 0.00"
        assertEquals(listOf(
            AmpliacionRegistrada("2026-10-29", 5, "S/", 300.0, "Etapa de ejercicios y promo fiestas"),
            AmpliacionRegistrada("2026-11-05", 1, "S/", 0.0, null),
        ), ampliacionesDeNotas(notas))
        assertTrue(ampliacionesDeNotas(null).isEmpty())
    }

    @Test
    fun fechaCorta() = assertEquals("29 oct", fechaCortaAmpliacion("2026-10-29"))

    @Test
    fun motivoObligatorioSoloSiNoEsPrecioDeLista() {
        assertTrue(motivoAmpliacionObligatorio(500.0, 300.0))
        assertFalse(motivoAmpliacionObligatorio(500.0, 500.0))
        assertFalse(motivoAmpliacionObligatorio(null, 300.0))
    }
}
