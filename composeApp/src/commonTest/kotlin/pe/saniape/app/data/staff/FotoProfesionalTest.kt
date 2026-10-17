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

    @Test fun fotoSinComprimirSoloFormatosWeb() {
        kotlin.test.assertTrue(fotoSubibleSinComprimir("image/jpeg"))
        kotlin.test.assertTrue(fotoSubibleSinComprimir("image/PNG"))
        kotlin.test.assertFalse(fotoSubibleSinComprimir("image/heic"))
        kotlin.test.assertFalse(fotoSubibleSinComprimir(null))
    }

    // PacientesRepo: el número ya no se pisa con el texto (put devolvía null y el elvis volvía a escribir).
    @Test fun camposCustomTipados() {
        val campos = listOf(
            CampoPaciente("peso", "Peso", "numero", emptyList(), null),
            CampoPaciente("ok", "Ok", "booleano", emptyList(), null),
            CampoPaciente("nota", "Nota", "texto", emptyList(), null),
        )
        val j = jsonCamposCustom(campos, mapOf("peso" to "72,5", "ok" to "true", "nota" to "hola", "vacio" to " "))
        assertEquals(kotlinx.serialization.json.JsonPrimitive(72.5), j["peso"])
        assertEquals(kotlinx.serialization.json.JsonPrimitive(true), j["ok"])
        assertEquals(kotlinx.serialization.json.JsonPrimitive("hola"), j["nota"])
        kotlin.test.assertFalse("vacio" in j)
        assertEquals(kotlinx.serialization.json.JsonPrimitive("abc"), jsonCamposCustom(campos, mapOf("peso" to "abc"))["peso"])
    }
}
