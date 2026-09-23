package pe.saniape.app.data

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import pe.saniape.app.data.staff.Zona
import pe.saniape.app.data.staff.caraEnZona
import pe.saniape.app.data.staff.esInferior
import pe.saniape.app.data.staff.mesialALaDerecha
import pe.saniape.app.data.staff.zonaEn

/**
 * Qué cara se marca al tocar el diagrama.
 *
 * Un error acá no se ve en la pantalla —el color aparece en el lado tocado— pero
 * queda escrito MAL en la historia clínica: "caries distal" cuando era mesial.
 * Por eso se prueba cada cuadrante por separado.
 */
class DienteGeometriaTest {

    @Test
    fun mesial_mira_a_la_linea_media() {
        // Cuadrantes 1 y 4 quedan a la derecha de la línea media (mirando de frente).
        assertTrue(mesialALaDerecha("16"))
        assertTrue(mesialALaDerecha("46"))
        assertTrue(mesialALaDerecha("55"))   // deciduo del 1
        assertTrue(mesialALaDerecha("84"))   // deciduo del 4
        // Cuadrantes 2 y 3, a la izquierda.
        assertFalse(mesialALaDerecha("26"))
        assertFalse(mesialALaDerecha("36"))
        assertFalse(mesialALaDerecha("63"))
        assertFalse(mesialALaDerecha("75"))
    }

    @Test
    fun la_arcada_inferior_son_los_cuadrantes_3_y_4() {
        assertTrue(esInferior("36"))
        assertTrue(esInferior("46"))
        assertTrue(esInferior("74"))
        assertFalse(esInferior("16"))
        assertFalse(esInferior("26"))
    }

    @Test
    fun el_centro_siempre_es_oclusal() {
        for (d in listOf("11", "26", "36", "48", "55", "85")) {
            assertEquals("O", caraEnZona(d, Zona.CENTRO), "pieza $d")
        }
    }

    @Test
    fun arriba_vestibular_en_superior_y_lingual_en_inferior() {
        assertEquals("V", caraEnZona("16", Zona.ARRIBA))
        assertEquals("L", caraEnZona("16", Zona.ABAJO))
        // Inferior: se invierte.
        assertEquals("L", caraEnZona("46", Zona.ARRIBA))
        assertEquals("V", caraEnZona("46", Zona.ABAJO))
    }

    @Test
    fun cada_cuadrante_pone_mesial_y_distal_donde_corresponde() {
        // Cuadrante 1: mesial a la derecha.
        assertEquals("M", caraEnZona("16", Zona.DERECHA))
        assertEquals("D", caraEnZona("16", Zona.IZQUIERDA))
        // Cuadrante 2: mesial a la izquierda.
        assertEquals("M", caraEnZona("26", Zona.IZQUIERDA))
        assertEquals("D", caraEnZona("26", Zona.DERECHA))
        // Cuadrante 3: mesial a la izquierda.
        assertEquals("M", caraEnZona("36", Zona.IZQUIERDA))
        // Cuadrante 4: mesial a la derecha.
        assertEquals("M", caraEnZona("46", Zona.DERECHA))
    }

    // ── Detección de toques: cuadrado de 100 con centro desde 30 hasta 70 ──

    @Test
    fun el_centro_se_detecta() {
        assertEquals(Zona.CENTRO, zonaEn(50f, 50f, 100f, 30f))
        assertEquals(Zona.CENTRO, zonaEn(31f, 69f, 100f, 30f))
    }

    @Test
    fun los_cuatro_lados_se_detectan() {
        assertEquals(Zona.ARRIBA, zonaEn(50f, 5f, 100f, 30f))
        assertEquals(Zona.ABAJO, zonaEn(50f, 95f, 100f, 30f))
        assertEquals(Zona.IZQUIERDA, zonaEn(5f, 50f, 100f, 30f))
        assertEquals(Zona.DERECHA, zonaEn(95f, 50f, 100f, 30f))
    }

    @Test
    fun cerca_de_una_esquina_gana_el_lado_mas_cercano() {
        // (10, 25): está a 10 de la izquierda y a 25 de arriba → izquierda.
        // Es lo que se ve dibujado: el trapecio izquierdo llega hasta ahí.
        assertEquals(Zona.IZQUIERDA, zonaEn(10f, 25f, 100f, 30f))
        // (25, 10): al revés → arriba.
        assertEquals(Zona.ARRIBA, zonaEn(25f, 10f, 100f, 30f))
    }

    @Test
    fun fuera_del_cuadrado_no_hay_zona() {
        assertNull(zonaEn(-1f, 50f, 100f, 30f))
        assertNull(zonaEn(50f, 101f, 100f, 30f))
    }
}
