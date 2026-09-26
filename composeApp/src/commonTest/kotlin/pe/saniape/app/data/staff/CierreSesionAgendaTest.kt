package pe.saniape.app.data.staff

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Cierre de sesión desde la AGENDA (gemelo de `abrirCompletarSesion` en /citas
 * web): sesión anterior, "↩ Repetir", plan y mejorías desde la #2.
 */
class CierreSesionAgendaTest {

    private fun ses(n: Int, estado: String, fecha: String, hora: String? = null, notas: String? = null) = SesionFicha(
        id = "s$n", numero = n, fecha = fecha, hora = hora, estado = estado, costo = null,
        notas = notas, mejorias = null, duracion = null, terapeutaNombre = null, motivoEstado = null,
    )

    @Test
    fun laAnteriorEsLaUltimaCompletadaAntesDeEstaSesion() {
        val lista = listOf(
            ses(3, "Planificada", "2026-09-26"),
            ses(2, "Completada", "2026-09-24", notas = "TENS + Compresa"),
            ses(1, "Completada", "2026-09-22", notas = "Evaluación"),
        )
        val cc = armarContextoCierre(lista, "2026-09-26", "10:00", numeroCita = 3, tecnicasPlan = "TENS")
        assertEquals("s3", cc.sesion?.id)
        assertEquals("s2", cc.anterior?.id)
        assertEquals(2, cc.completadas)
        assertEquals("TENS", cc.tecnicasPlan)
        assertTrue(cc.pideMejorias(3))
    }

    @Test
    fun sinNumeroEnLaCitaSeBuscaPorFechaYHora() {
        val lista = listOf(
            ses(2, "Planificada", "2026-09-26", hora = "16:00:00"),
            ses(1, "Planificada", "2026-09-26", hora = "10:00:00"),
        )
        val cc = armarContextoCierre(lista, "2026-09-26", "16:00", numeroCita = null, tecnicasPlan = " ")
        assertEquals("s2", cc.sesion?.id)
        assertNull(cc.anterior)
        assertNull(cc.tecnicasPlan)
        // Es la #2 por número aunque ninguna esté completada: pide mejorías (como la web).
        assertTrue(cc.pideMejorias(null))
    }

    @Test
    fun primeraSesionNoPideMejorias() {
        val cc = armarContextoCierre(listOf(ses(1, "Planificada", "2026-09-26")), "2026-09-26", null, 1, null)
        assertFalse(cc.pideMejorias(1))
    }

    @Test
    fun sinSesionVinculadaMandanLasCompletadas() {
        // Cita de Sesión sin sesión todavía (caso de las citas sin sesion_id): si ya
        // hubo completadas, esta es de la #2 en adelante → mejorías.
        val cc = armarContextoCierre(listOf(ses(1, "Completada", "2026-09-20")), "2026-09-26", "09:00", null, null)
        assertNull(cc.sesion)
        assertEquals("s1", cc.anterior?.id)
        assertTrue(cc.pideMejorias(null))
    }

    @Test
    fun lasCanceladasNoSonLaSesionDeLaCita() {
        val lista = listOf(
            ses(2, "Cancelada", "2026-09-26"),
            ses(1, "Completada", "2026-09-20"),
        )
        val cc = armarContextoCierre(lista, "2026-09-26", null, null, null)
        assertNull(cc.sesion)
    }
}
