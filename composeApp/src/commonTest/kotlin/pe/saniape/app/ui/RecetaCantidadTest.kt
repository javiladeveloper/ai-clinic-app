package pe.saniape.app.ui.clinica.atencion

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** Gemelo de lib/__tests__/receta-cantidad.test.ts («formas reales de escribir», 12/11/2026). */
class RecetaCantidadTest {
    private fun total(dosis: String, frecuencia: String, duracion: String, forma: String = "Tableta", concentracion: String = "500 mg") =
        cantidadSugerida(dosis, frecuencia, duracion, forma, concentracion)

    @Test
    fun la_forma_de_libro() {
        assertEquals(21, total("1 tableta", "cada 8 horas", "7 días"))
        assertEquals(7, total("1/2 tableta", "cada 12 horas", "7 días"))
        assertEquals(30, total("1", "cada 24 horas", "1 mes"))
    }

    @Test
    fun frecuencias_abreviadas_o_en_palabras() {
        assertEquals(21, total("1 tableta", "c/8h", "7 días"))
        assertEquals(21, total("1 tableta", "C/ 8 hrs", "7 días"))
        assertEquals(21, total("1 tableta", "cada ocho horas", "7 días"))
        assertEquals(10, total("1 tableta", "cada 12", "5 días"))
        assertEquals(21, total("1 tableta", "3 x día", "7 días"))
        assertEquals(20, total("1 tableta", "2 veces/día", "10 días"))
        assertEquals(20, total("1 tableta", "mañana y noche", "10 días"))
        assertEquals(15, total("1 tableta", "desayuno, almuerzo y cena", "5 días"))
        assertEquals(14, total("1 tableta", "BID", "7 días"))
    }

    @Test
    fun dosis_con_texto_alrededor() {
        assertEquals(21, total("1 tab VO", "cada 8 horas", "7 días"))
        assertEquals(21, total("1 tableta vía oral", "cada 8 horas", "7 días"))
        assertEquals(14, total("1 tab. de 500 mg", "cada 12 horas", "7 días"))
        assertEquals(30, total("una cápsula", "cada 24 horas", "30 días", "Cápsula"))
        assertEquals(12, total("2 pastillas", "cada 12 horas", "3 días"))
    }

    @Test
    fun dosis_en_mg_solo_si_cuadra_con_la_concentracion() {
        assertEquals(21, total("500 mg", "cada 8 horas", "7 días"))
        assertEquals(4, total("250 mg", "cada 12 horas", "4 días"))
        assertNull(total("1 g", "cada 8 horas", "7 días"))
        assertNull(total("300 mg", "cada 8 horas", "7 días"))
        assertNull(total("500 mg", "cada 8 horas", "7 días", "Jarabe"))
    }

    @Test
    fun duracion_en_palabras_o_abreviada() {
        assertEquals(21, total("1 tableta", "cada 8 horas", "por siete días"))
        assertEquals(14, total("1 tableta", "cada 24 horas", "x 2 sem"))
        assertEquals(30, total("1 tableta", "cada 24 horas", "un mes"))
    }

    @Test
    fun lo_que_no_se_puede_contar_queda_a_mano() {
        assertNull(total("5 ml", "cada 8 horas", "7 días", "Jarabe"))
        assertNull(total("1 tableta", "si hay dolor", "7 días"))
        assertNull(total("1 tableta", "cada 8 horas", "uso continuo"))
    }

    @Test
    fun la_cantidad_se_pone_sola_y_respeta_lo_escrito_a_mano() {
        val base = ItemRecetaForm(dci = "Amoxicilina", concentracion = "500 mg", forma = "Tableta", dosis = "1 tableta", frecuencia = "cada 8 horas")
        // Al completar la duración, sale la cantidad.
        val conDias = conCantidadSugerida(base, base.copy(duracion = "7 días"))
        assertEquals("21", conDias.cantidad)
        // El médico la cambia: manda lo suyo aunque después toque la dosis.
        val aMano = conCantidadSugerida(conDias, conDias.copy(cantidad = "24"))
        assertEquals(true, aMano.cantidadAMano)
        assertEquals("24", conCantidadSugerida(aMano, aMano.copy(duracion = "10 días")).cantidad)
        // La borra: vuelve a calcularse.
        val borrada = conCantidadSugerida(aMano, aMano.copy(cantidad = ""))
        assertEquals("30", conCantidadSugerida(borrada, borrada.copy(duracion = "10 días")).cantidad)
    }
}
