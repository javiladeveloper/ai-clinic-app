package pe.saniape.app.ui

import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * El rótulo debe ser ESTABLE durante toda la ráfaga de una acción.
 *
 * Una sola acción del usuario encadena varias operaciones (borrar sesión → recargar
 * lista → recargar pagos → recargar ficha). Si el texto se recalculara en cada una,
 * el usuario ve un desfile de palabras cambiando solas.
 */
class EstadoGuardandoTest {

    /** Deja el singleton limpio: los tests comparten proceso. */
    private fun reset() {
        repeat(20) { EstadoGuardando.fin(Gestion.CARGANDO) }
    }

    @Test
    fun borrar_mantiene_Eliminando_durante_toda_la_rafaga() {
        reset()
        // Borrado + sus recargas encadenadas (el caso que reportó el usuario).
        EstadoGuardando.inicio(Gestion.ELIMINANDO)
        assertEquals("Eliminando…", EstadoGuardando.rotulo.value)

        EstadoGuardando.inicio(Gestion.CARGANDO)   // recarga de la lista
        assertEquals("Eliminando…", EstadoGuardando.rotulo.value, "la recarga no debe pisar el texto")

        EstadoGuardando.fin(Gestion.ELIMINANDO)    // el borrado ya terminó…
        assertEquals("Eliminando…", EstadoGuardando.rotulo.value, "sigue la ráfaga: no debe cambiar")

        EstadoGuardando.fin(Gestion.CARGANDO)
        assertEquals(0, EstadoGuardando.enCurso.value)
    }

    @Test
    fun la_escritura_asciende_sobre_una_lectura_ya_en_curso() {
        reset()
        EstadoGuardando.inicio(Gestion.CARGANDO)
        assertEquals("Cargando…", EstadoGuardando.rotulo.value)

        // Entra un borrado mientras se estaba cargando: manda la escritura.
        EstadoGuardando.inicio(Gestion.ELIMINANDO)
        assertEquals("Eliminando…", EstadoGuardando.rotulo.value)

        EstadoGuardando.fin(Gestion.ELIMINANDO)
        EstadoGuardando.fin(Gestion.CARGANDO)
    }

    @Test
    fun el_rotulo_nunca_baja_de_prioridad_dentro_de_la_rafaga() {
        reset()
        EstadoGuardando.inicio(Gestion.ELIMINANDO)
        EstadoGuardando.inicio(Gestion.ACTUALIZANDO)  // menos prioritaria
        assertEquals("Eliminando…", EstadoGuardando.rotulo.value)
        EstadoGuardando.fin(Gestion.ACTUALIZANDO)
        EstadoGuardando.fin(Gestion.ELIMINANDO)
    }

    @Test
    fun una_nueva_accion_tras_cerrar_la_rafaga_fija_su_propio_rotulo() {
        reset()
        EstadoGuardando.inicio(Gestion.ELIMINANDO)
        EstadoGuardando.fin(Gestion.ELIMINANDO)
        assertEquals(0, EstadoGuardando.enCurso.value)

        // Acción nueva: debe poder decir lo suyo (la anterior no se queda pegada).
        EstadoGuardando.inicio(Gestion.CARGANDO)
        assertEquals("Cargando…", EstadoGuardando.rotulo.value)
        EstadoGuardando.fin(Gestion.CARGANDO)
    }

    @Test
    fun abrir_una_ficha_dice_Cargando_no_Guardando() {
        reset()
        // El bug reportado: abrir una ficha decía "Guardando…" (mentira, no se modificó nada).
        EstadoGuardando.inicio(Gestion.CARGANDO)
        assertEquals("Cargando…", EstadoGuardando.rotulo.value)
        EstadoGuardando.fin(Gestion.CARGANDO)
    }

    @Test
    fun el_contador_nunca_baja_de_cero() {
        reset()
        EstadoGuardando.fin(Gestion.GUARDANDO)
        EstadoGuardando.fin(Gestion.GUARDANDO)
        assertEquals(0, EstadoGuardando.enCurso.value)
    }
}
