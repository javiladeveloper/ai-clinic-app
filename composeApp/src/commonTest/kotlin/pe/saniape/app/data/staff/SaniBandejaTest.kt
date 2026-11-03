package pe.saniape.app.data.staff

import kotlinx.datetime.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Bandeja de Sani: gemelos puros de app/(app)/mensajes y lib/sani-bandeja.ts. */
class SaniBandejaTest {

    private fun lead(id: String, estado: String = "nuevo", etapa: String? = null) = LeadSani(id = id, estado = estado, etapaEmbudo = etapa)

    @Test
    fun mezclaYAgregaSinDuplicar() {
        val actuales = listOf(lead("a"), lead("b"), lead("c"))
        assertEquals(listOf("b", "x", "a", "c"), mezclarPaginaReciente(actuales, listOf(lead("b"), lead("x"))).map { it.id })
        assertEquals(listOf("a", "b", "c", "d"), agregarPaginaVieja(actuales, listOf(lead("c"), lead("d"))).map { it.id })
    }

    @Test
    fun etapaPersonalizadaManda() {
        assertEquals("ganado", etapaVisibleSani(lead("a", estado = "ganado"), ETAPAS_SANI_DEFAULT).id)
        assertEquals("perdido", etapaVisibleSani(lead("a", estado = "ganado", etapa = "perdido"), ETAPAS_SANI_DEFAULT).id)
        assertEquals("Agendaron", nombreEtapaSani(EtapaSani("x", "Ganados")))
    }

    @Test
    fun firmaYCanal() {
        assertTrue(esDelBot(null)); assertTrue(esDelBot("ia"))
        assertFalse(esDelBot("humano")); assertFalse(esDelBot("ia_editada"))
        assertTrue(bloqueaRespuestaPorCanal("whatsapp"))
        assertFalse(bloqueaRespuestaPorCanal("instagram"))
    }

    @Test
    fun ventanaDe24y72Horas() {
        val ahora = Instant.parse("2026-11-02T12:00:00Z")
        val m = listOf(MensajeSani("1", "entrante", creadoEn = "2026-11-01T13:00:00Z"))
        val v = ventanaSani(m, vinoDeAnuncio = false, ahora = ahora)
        assertTrue(v.abierta); assertEquals(60, v.restanMin)
        assertFalse(ventanaSani(emptyList(), false, ahora).abierta)
        assertEquals(72, ventanaSani(m, true, ahora).horas)
    }

    @Test
    fun mesAnteriorYAviso() {
        assertEquals("2025-12", mesAnteriorSani("2026-01"))
        assertEquals("2026-10", mesAnteriorSani("2026-11"))
        assertTrue(mostrarAvisoHistorial(5, 2, "", ""))
        assertFalse(mostrarAvisoHistorial(5, 2, "nuevo", ""))
    }
}
