package pe.saniape.app.data.staff

import kotlin.test.Test
import kotlin.test.assertEquals

/** Gemelo de `lib/__tests__/etiquetas-paciente.test.ts` (web). FISIOSALUD 25/11/2026: «etiquetas para armar grupitos». */
class EtiquetasPacienteTest {

    @Test fun `normaliza espacios y la primera letra`() {
        assertEquals("Artritis reumatoide", normalizarEtiqueta("  artritis   reumatoide "))
        assertEquals(null, normalizarEtiqueta("   "))
        assertEquals(40, normalizarEtiqueta("x".repeat(60))!!.length)
    }

    @Test fun `no duplica sin importar mayusculas ni tildes y usa la escritura de la clinica`() {
        assertEquals(listOf("Lupus"), agregarEtiqueta(listOf("Lupus"), "lupus"))
        assertEquals(listOf("Artrosis"), agregarEtiqueta(listOf("Artrosis"), "ARTRÓSIS"))
        assertEquals(listOf("Control mensual"), agregarEtiqueta(emptyList(), "control MENSUAL", conocidas = listOf("Control mensual")))
        assertEquals(listOf("Lupus", "Gota"), agregarEtiqueta(listOf("Lupus"), "gota"))
        assertEquals(20, agregarEtiqueta((1..20).map { "e$it" }, "otra").size)
    }

    @Test fun `quitar y filtrar por etiqueta`() {
        assertEquals(listOf("Gota"), quitarEtiqueta(listOf("Lupus", "Gota"), "LUPUS"))
        assertEquals(true, tieneEtiqueta(listOf("Convenio X"), "convenio x"))
        assertEquals(false, tieneEtiqueta(emptyList(), "Lupus"))
    }

    @Test fun `color estable por etiqueta`() {
        assertEquals(colorEtiqueta("Lupus"), colorEtiqueta("lupus"))
    }

    @Test fun `etiquetas de la lista sin repetir, ordenadas`() {
        // La primera escritura gana («Lupus» y «lupus» son el mismo grupo).
        assertEquals(listOf("Gota", "Lupus"), etiquetasDeLista(listOf(listOf("Lupus"), listOf("Gota", "lupus"), emptyList())))
    }
}
