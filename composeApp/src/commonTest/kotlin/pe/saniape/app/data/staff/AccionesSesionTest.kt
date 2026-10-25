package pe.saniape.app.data.staff

import pe.saniape.app.data.staff.AccionSesion.Alta
import pe.saniape.app.data.staff.AccionSesion.Ampliar
import pe.saniape.app.data.staff.AccionSesion.Borrar
import pe.saniape.app.data.staff.AccionSesion.Cancelo
import pe.saniape.app.data.staff.AccionSesion.Cobrar
import pe.saniape.app.data.staff.AccionSesion.Completar
import pe.saniape.app.data.staff.AccionSesion.Editar
import pe.saniape.app.data.staff.AccionSesion.NoAsistio
import pe.saniape.app.data.staff.AccionSesion.Otro
import pe.saniape.app.data.staff.AccionSesion.Reactivar
import pe.saniape.app.data.staff.AccionSesion.Reasignar
import pe.saniape.app.data.staff.AccionSesion.Receta
import pe.saniape.app.data.staff.AccionSesion.Reprogramar
import pe.saniape.app.data.staff.AccionSesion.Revertir
import pe.saniape.app.data.staff.AccionSesion.VerReceta
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Fila de sesión (ficha y Sesiones): 1 primaria + máx. 1–2 más + «⋯», con lo rojo al final. */
class AccionesSesionTest {

    private val estadosPend = listOf(Reprogramar, NoAsistio, Cancelo, Otro)

    @Test
    fun momentoPorEstado() {
        for (e in listOf("Planificada", "En progreso", "Reprogramada")) assertEquals(MomentoSesion.PENDIENTE, momentoSesion(e))
        assertEquals(MomentoSesion.COMPLETADA, momentoSesion("Completada"))
        for (e in listOf("Cancelada", "No asistió", "Otro")) assertEquals(MomentoSesion.ANULADA, momentoSesion(e))
    }

    @Test
    fun losEstadosDelMenuSonLosDeSiempreEnSuOrden() {
        assertEquals(estadosPend, ACCIONES_ESTADO_SESION)
        assertEquals(ESTADOS_MENU_SESION, ACCIONES_ESTADO_SESION.map { estadoDeAccionSesion(it) })
        assertNull(estadoDeAccionSesion(Completar))
    }

    @Test
    fun pendienteDeLaFichaCompletarYTodoLoDemasAlMenu() {
        val p = repartirAccionesSesion("Planificada", listOf(Completar, Editar) + estadosPend + listOf(Reasignar, Borrar))
        assertEquals(Completar, p.primaria)
        assertTrue(p.secundarias.isEmpty())
        assertEquals(
            listOf(
                GrupoMenu("Sesión", listOf(Editar, Reprogramar, NoAsistio, Otro, Reasignar)),
                GrupoMenu(GRUPO_PELIGRO, listOf(Cancelo, Borrar)),
            ),
            p.menu,
        )
    }

    @Test
    fun completadaConDeudaCobrarYRevertirAlFinalEnRojo() {
        val p = repartirAccionesSesion("Completada", listOf(Cobrar, Editar, Receta, Revertir, Reasignar, Borrar))
        assertEquals(Cobrar, p.primaria)
        assertEquals(listOf(Receta), p.secundarias)
        assertEquals(GrupoMenu(GRUPO_PELIGRO, listOf(Revertir, Borrar)), p.menu.last())
    }

    @Test
    fun completadaDeSesionesSinCobroLaRecetaVisibleYRevertirAlMenu() {
        // PantallaSesiones: antes «↩ Revertir» estaba a la vista en CADA fila completada.
        val p = repartirAccionesSesion("Completada", listOf(Revertir, VerReceta))
        assertEquals(VerReceta, p.primaria)
        assertEquals(listOf(Revertir), p.enMenu)
    }

    @Test
    fun laUltimaDelPlanAltaYAmpliarALaVista() {
        val p = repartirAccionesSesion("Completada", listOf(Revertir, Receta, Alta, Ampliar))
        assertEquals(Alta, p.primaria)
        assertEquals(listOf(Ampliar), p.secundarias)
        assertEquals(listOf(Receta, Revertir), p.enMenu)
    }

    @Test
    fun anuladaReactivarEsLaPrimaria() {
        for (e in listOf("Cancelada", "No asistió", "Otro")) {
            val p = repartirAccionesSesion(e, listOf(Reactivar, Editar, Reasignar))
            assertEquals(Reactivar, p.primaria)
            assertEquals(listOf(Editar, Reasignar), p.enMenu)
        }
    }

    @Test
    fun sesionesCanceloEnRojoAlFinalDelMenuDeEstados() {
        val p = repartirAccionesSesion("Planificada", listOf(Completar, Reasignar) + estadosPend)
        assertEquals(listOf(Reprogramar, NoAsistio, Otro, Reasignar, Cancelo), p.enMenu)
        assertEquals(GrupoMenu(GRUPO_PELIGRO, listOf(Cancelo)), p.menu.last())
    }

    @Test
    fun ningunaDisponibleSePierdeNiSeRepite() {
        val todas = AccionSesion.entries.toList()
        for (e in listOf("Planificada", "Completada", "Cancelada")) {
            val p = repartirAccionesSesion(e, todas)
            assertEquals(todas.sorted(), (p.visibles + p.enMenu).sorted())
            assertTrue(p.visibles.size <= 2)
        }
    }

    @Test
    fun sinPermisosNoHayNada() {
        val p = repartirAccionesSesion("Completada", emptyList())
        assertNull(p.primaria)
        assertTrue(p.menu.isEmpty())
    }
}
