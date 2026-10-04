package pe.saniape.app.ui

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** Qué imagen se pide para "ver cómo se hace" un ejercicio de apoyo, según la plataforma. */
class ImagenEjercicioTest {

    @Test
    fun dondeSeAnimaVaElGif() {
        assertEquals("a.gif", urlAnimacionEjercicio("a.gif", "i.png", animaGif = true))
        // Sin GIF, la postura.
        assertEquals("i.png", urlAnimacionEjercicio(null, "i.png", animaGif = true))
    }

    @Test
    fun enIosVaLaPosturaEstatica() {
        assertEquals("i.png", urlAnimacionEjercicio("a.gif", "i.png", animaGif = false))
        // Sin postura, el GIF (se verá quieto, pero se ve).
        assertEquals("a.gif", urlAnimacionEjercicio("a.gif", " ", animaGif = false))
    }

    @Test
    fun sinImagenesNoHayNadaQuePedir() {
        assertNull(urlAnimacionEjercicio(null, null, animaGif = true))
        assertNull(urlAnimacionEjercicio("", " ", animaGif = false))
    }
}
