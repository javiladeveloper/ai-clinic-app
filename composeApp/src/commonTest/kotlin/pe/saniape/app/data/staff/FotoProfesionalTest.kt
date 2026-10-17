package pe.saniape.app.data.staff

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import pe.saniape.app.ui.clinica.agenda.modales.leerPrecioCita

/** Gemelo de components/ui/__tests__/avatar-profesional.test.ts (web) + piezas puras del release 2.61. */
class FotoProfesionalTest {

    @Test fun inicialesSinLicYDosPalabras() {
        assertEquals("AM", inicialesProfesional("Lic. Ana María Torres"))
        assertEquals("RQ", inicialesProfesional("  rosa   quispe "))
        assertEquals("", inicialesProfesional(""))
        assertEquals("", inicialesProfesional(null))
    }

    @Test fun rutaComoLaWeb() {
        assertEquals("profesional-123.jpg", rutaFotoProfesional("foto.JPG", 123))
        assertEquals("profesional-5.jpg", rutaFotoProfesional("sin-extension", 5))
        assertEquals("profesional-9.png", rutaFotoProfesional("a.b.p n g".replace(" ", ""), 9))
    }

    @Test fun precioDeLaCita() {
        assertEquals(40.0, leerPrecioCita("40"))
        assertEquals(40.5, leerPrecioCita("40,5"))
        assertEquals(40.5, leerPrecioCita(" 40.5 "))
        assertNull(leerPrecioCita("0"))
        assertNull(leerPrecioCita(""))
        assertNull(leerPrecioCita("abc"))
    }
}
