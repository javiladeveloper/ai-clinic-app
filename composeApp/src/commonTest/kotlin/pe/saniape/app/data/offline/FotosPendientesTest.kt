package pe.saniape.app.data.offline

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Reglas de las fotos de sesión que esperan señal (topes y cuándo se suben). */
class FotosPendientesTest {

    private fun foto(id: String = "f1", bytes: Long = 300_000, sesionId: String? = null, citaId: String? = null) =
        FotoPendiente(id = id, pacienteId = "p", tratamientoId = "t", sesionId = sesionId, citaId = citaId,
            nombre = "$id.jpg", archivo = "$id.jpg", bytes = bytes)

    private val mb = 1024L * 1024

    @Test
    fun admite_hasta_diez_fotos() {
        val existentes = (1..8).map { foto("e$it") }
        assertEquals(listOf(true, true, false), admitirFotos(existentes, listOf(300_000, 300_000, 300_000)))
    }

    @Test
    fun admite_hasta_veinte_mb_y_sigue_probando_las_chicas() {
        val existentes = listOf(foto("grande", bytes = 18 * mb))
        // 3 MB no cabe (21 > 20); 1 MB sí.
        assertEquals(listOf(false, true), admitirFotos(existentes, listOf(3 * mb, 1 * mb)))
    }

    @Test
    fun no_admite_archivos_vacios() {
        assertEquals(listOf(false), admitirFotos(emptyList(), listOf(0)))
    }

    @Test
    fun sesion_temporal_se_traduce_con_el_mapa() {
        val f = foto(sesionId = "tmp-abc")
        assertNull(sesionResuelta(f, emptyMap()))
        assertEquals("real-1", sesionResuelta(f, mapOf("tmp-abc" to "real-1")))
        assertEquals("s1", sesionResuelta(foto(sesionId = "s1"), emptyMap()))
        assertNull(sesionResuelta(foto(citaId = "c1"), emptyMap()))
    }

    @Test
    fun espera_mientras_la_cola_nombra_su_sesion_o_su_cita() {
        val porSesion = foto(sesionId = "s1")
        assertFalse(fotoLista(porSesion, listOf("""{"sesionId":"s1","estado":"Completada"}"""), emptyMap()))
        assertTrue(fotoLista(porSesion, listOf("""{"sesionId":"otra"}"""), emptyMap()))
        val porCita = foto(citaId = "c1")
        assertFalse(fotoLista(porCita, listOf("""{"citaId":"c1"}"""), emptyMap()))
        assertTrue(fotoLista(porCita, emptyList(), emptyMap()))
    }

    @Test
    fun sesion_temporal_sin_id_real_espera() {
        val f = foto(sesionId = "tmp-abc")
        assertFalse(fotoLista(f, emptyList(), emptyMap()))
        assertTrue(fotoLista(f, emptyList(), mapOf("tmp-abc" to "real-1")))
        // Con el id real, sigue esperando si la cola aún nombra la sesión real.
        assertFalse(fotoLista(f, listOf("""{"sesionId":"real-1"}"""), mapOf("tmp-abc" to "real-1")))
    }

    @Test
    fun avisos_al_guardar() {
        val (ok1, t1) = avisoFotosGuardadas(GuardadoFotos(2, 0))
        assertTrue(ok1); assertTrue(t1.contains("se subirán solas"))
        val (ok2, t2) = avisoFotosGuardadas(GuardadoFotos(0, 1))
        assertFalse(ok2); assertTrue(t2.contains("no se guardaron"))
        val (ok3, t3) = avisoFotosGuardadas(GuardadoFotos(1, 2))
        assertFalse(ok3); assertTrue(t3.contains("1 de 3"))
    }
}
