package pe.saniape.app.data

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import pe.saniape.app.data.staff.HallazgoDental
import pe.saniape.app.data.staff.interpretarDictadoDental
import pe.saniape.app.data.staff.normalizarTextoDictado

/**
 * El dictado por voz del odontograma.
 *
 * Los casos son COPIA de `lib/__tests__/odontograma-voz.test.ts` en la web: si
 * el odontólogo dicta lo mismo en el celular y en la computadora, se tiene que
 * marcar lo mismo.
 */
private val CAT = listOf(
    HallazgoDental(id = "h-1", nombre = "Caries", color = "#dc2626"),
    HallazgoDental(id = "h-2", nombre = "Ausente", color = "#64748b", marcaAusente = true),
    HallazgoDental(id = "h-3", nombre = "Corona", color = "#0284c7"),
    HallazgoDental(id = "h-4", nombre = "Endodoncia", color = "#d97706"),
    HallazgoDental(id = "h-5", nombre = "Fractura", color = "#dc2626"),
    HallazgoDental(id = "h-6", nombre = "Sarro", color = "#16a34a"),
    HallazgoDental(id = "h-7", nombre = "Gingivitis", color = "#ef4444"),
)

class NormalizarDictadoTest {
    @Test
    fun convierte_numeros_hablados_en_FDI() {
        assertEquals("pieza 16 con caries", normalizarTextoDictado("pieza dieciséis con caries"))
        assertEquals("diente 24 ausente", normalizarTextoDictado("diente veinticuatro ausente"))
        assertEquals("pieza 36 oclusal", normalizarTextoDictado("pieza treinta y seis oclusal"))
        assertEquals("pieza 48 extracción", normalizarTextoDictado("pieza cuarenta y ocho extracción"))
        assertEquals("pieza 16 caries", normalizarTextoDictado("pieza uno seis caries"))
        assertEquals("diente 26 con corona", normalizarTextoDictado("diente 2 6 con corona"))
    }

    @Test
    fun el_punto_decimal_tambien_vale() {
        // "1.6" se dicta así en algunos teclados de voz.
        assertEquals("pieza 16", normalizarTextoDictado("pieza 1.6"))
    }
}

class InterpretarDictadoTest {
    @Test
    fun caries_en_pieza_con_cara_oclusal() {
        val r = interpretarDictadoDental("pieza 16 caries oclusal", CAT)
        assertEquals(1, r.size)
        assertEquals("diente", r[0].tipo)
        assertEquals("16", r[0].diente)
        assertEquals("Caries", r[0].hallazgoNombre)
        assertEquals(listOf("O"), r[0].superficies)
        // Encontró el hallazgo en el catálogo: se puede marcar directo.
        assertEquals("h-1", r[0].hallazgoId)
    }

    @Test
    fun caras_combinadas_MO_y_MOD() {
        val r = interpretarDictadoDental("pieza 26 caries mesio oclusal y pieza 36 caries MOD", CAT)
        assertEquals(2, r.size)
        assertEquals("26", r[0].diente)
        assertEquals(listOf("M", "O"), r[0].superficies)
        assertEquals("36", r[1].diente)
        assertEquals(listOf("M", "O", "D"), r[1].superficies)
    }

    @Test
    fun ausente_va_sin_caras() {
        val r = interpretarDictadoDental("pieza 38 ausente", CAT)
        assertEquals(1, r.size)
        assertEquals("Ausente", r[0].hallazgoNombre)
        assertNull(r[0].superficies)
    }

    @Test
    fun coronas_y_endodoncias() {
        val r = interpretarDictadoDental("pieza 11 corona y pieza 46 endodoncia", CAT)
        assertEquals(2, r.size)
        assertEquals("Corona", r[0].hallazgoNombre)
        assertEquals("Endodoncia", r[1].hallazgoNombre)
    }

    @Test
    fun condiciones_de_boca_completa() {
        val r = interpretarDictadoDental("pieza 16 caries oclusal y sarro generalizado con gingivitis", CAT)
        assertEquals(3, r.size)
        val boca = r.filter { it.tipo == "boca" }.map { it.hallazgoNombre }
        assertEquals(2, boca.size)
        assertTrue("Sarro" in boca)
        assertTrue("Gingivitis" in boca)
    }

    @Test
    fun numeros_dichos_en_palabras() {
        val r = interpretarDictadoDental("pieza dieciséis caries oclusal y pieza veinticuatro ausente", CAT)
        assertEquals(2, r.size)
        assertEquals("16", r[0].diente)
        assertEquals("24", r[1].diente)
        assertEquals("Ausente", r[1].hallazgoNombre)
    }

    @Test
    fun dientes_de_leche() {
        val r = interpretarDictadoDental("pieza 55 caries oclusal y pieza 84 ausente", CAT)
        assertEquals(2, r.size)
        assertEquals("55", r[0].diente)
        assertEquals("84", r[1].diente)
    }

    @Test
    fun varias_piezas_seguidas_sin_pausas() {
        val r = interpretarDictadoDental("pieza 16 caries oclusal pieza 26 caries mesial pieza 48 ausente", CAT)
        assertEquals(listOf("16", "26", "48"), r.map { it.diente })
    }

    @Test
    fun texto_vacio_o_sin_hallazgos_no_rompe() {
        assertTrue(interpretarDictadoDental("", CAT).isEmpty())
        assertTrue(interpretarDictadoDental("el paciente refiere que todo bien", CAT).isEmpty())
    }
}
