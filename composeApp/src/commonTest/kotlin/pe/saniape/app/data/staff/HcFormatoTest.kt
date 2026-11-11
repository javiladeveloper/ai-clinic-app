package pe.saniape.app.data.staff

import kotlin.test.Test
import kotlin.test.assertEquals

class HcFormatoTest {
    @Test
    fun cadaOjoEnSuRenglon() {
        val f = filasDeValorHc("Conjuntiva: OD Tranquila · OI Tranquila\nCórnea: OD Transparente · OI Transparente")
        assertEquals(
            listOf(
                FilaHc("Conjuntiva", listOf("OD Tranquila", "OI Tranquila")),
                FilaHc("Córnea", listOf("OD Transparente", "OI Transparente")),
            ),
            f,
        )
    }

    @Test
    fun laHoraNoEsUnNombreYLoQueNoEsDeUnOjoQuedaJunto() {
        // «08:40» no corta el nombre; cada ojo en su renglón y lo demás junto.
        assertEquals(
            listOf(FilaHc("PIO", listOf("OD 14 mmHg", "OI 14 mmHg", "Aire (no contacto) · 08:40"))),
            filasDeValorHc("PIO: OD 14 mmHg · OI 14 mmHg · Aire (no contacto) · 08:40"),
        )
        // «|» separa tramos (sin corrección / con estenopeico), cada uno en su renglón.
        assertEquals(
            listOf(FilaHc("AV lejos", listOf("SC OD 20/70 · OI 20/60", "AE OD 20/25 · OI 20/25"))),
            filasDeValorHc("AV lejos: SC OD 20/70 · OI 20/60 | AE OD 20/25 · OI 20/25"),
        )
        // Un texto suelto (sin «·» de ojos) no se parte.
        assertEquals(
            listOf(FilaHc("Indicaciones", listOf("Lentes monofocales con antirreflejo · Uso permanente."))),
            filasDeValorHc("Indicaciones: Lentes monofocales con antirreflejo · Uso permanente."),
        )
        assertEquals(listOf(FilaHc(null, listOf("08:40 control"))), filasDeValorHc("08:40 control"))
    }

    @Test
    fun cuandoSeApila() {
        assertEquals(false, valorHcLargo("Soltero(a)"))
        assertEquals(true, valorHcLargo("a\nb"))
        assertEquals(true, valorHcLargo("x".repeat(61)))
    }
}
