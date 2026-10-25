package pe.saniape.app.data.staff

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** El reparto común (gemelo de lib/menu-acciones.ts): vista · 💰 · «⋯ Más» con lo rojo al final. */
class PlanAccionesTest {

    private val grupos = listOf(
        GrupoMenu("Uno", listOf("a", "b", "borrar")),
        GrupoMenu("Dos", listOf("c", "d", "cancelar")),
    )

    @Test
    fun laPrimeraALaVistaEsLaPrimariaYLasDemasSecundarias() {
        val p = repartirAcciones(listOf("b", "c", "d"), grupos, listOf("a", "b", "c", "d"), max = 2)
        assertEquals("b", p.primaria)
        assertEquals(listOf("c"), p.secundarias)
        assertEquals(listOf(GrupoMenu("Uno", listOf("a")), GrupoMenu("Dos", listOf("d"))), p.menu)
    }

    @Test
    fun loNoDisponibleNoApareceEnNingunLado() {
        val p = repartirAcciones(listOf("b", "c"), grupos, listOf("c", "a"))
        assertEquals("c", p.primaria)
        assertTrue(p.secundarias.isEmpty())
        assertEquals(listOf("a"), p.enMenu)
    }

    @Test
    fun lasRojasVanAlFinalEnSuOrdenYSinTitulo() {
        val p = repartirAcciones(emptyList(), grupos, listOf("a", "borrar", "c", "cancelar"),
            rojas = listOf("cancelar", "borrar"))
        assertNull(p.primaria)
        assertEquals(GrupoMenu(GRUPO_PELIGRO, listOf("cancelar", "borrar")), p.menu.last())
        assertEquals(listOf("a", "c", "cancelar", "borrar"), p.enMenu)
    }

    @Test
    fun elCobroEsUnExtraFijoYNoSeRepiteEnElMenu() {
        val p = repartirAcciones(listOf("a"), grupos + GrupoMenu("Cobro", listOf("cobrar")), listOf("a", "cobrar", "c"),
            cobro = "cobrar")
        assertEquals("cobrar", p.cobro)
        assertEquals(listOf("c"), p.enMenu)
    }

    @Test
    fun unaDisponibleSinGrupoNoSePierde() {
        val p = repartirAcciones(listOf("a"), grupos, listOf("a", "x"))
        assertEquals(listOf(GrupoMenu(GRUPO_OTRAS, listOf("x"))), p.menu)
    }

    @Test
    fun cadaDisponibleApareceExactamenteUnaVez() {
        val todas = listOf("a", "b", "c", "d", "borrar", "cancelar", "cobrar")
        val p = repartirAcciones(listOf("d", "a", "b", "c"), grupos, todas, cobro = "cobrar", rojas = listOf("borrar"))
        val juntas = p.visibles + listOfNotNull(p.cobro) + p.enMenu
        assertEquals(todas.sorted(), juntas.sorted())
        assertEquals(3, p.visibles.size)   // 1 primaria + 2 secundarias por defecto
    }

    @Test
    fun sinNadaParaElMenuNoHayMenu() {
        assertTrue(repartirAcciones(listOf("a"), grupos, listOf("a")).menu.isEmpty())
        assertTrue(repartirAcciones(listOf("a"), grupos, emptyList<String>()).menu.isEmpty())
    }

    @Test
    fun laTarjetaDeCitaSeConvierteSinCambiarSuReparto() {
        val e = EntradaAccionesCita(estado = "Confirmada", tipo = "Consulta", cobrar = true, odontograma = true)
        val original = planAccionesCita(e)
        val comun = original.comoPlan()
        assertEquals(original.primaria, comun.primaria)
        assertEquals(listOfNotNull(original.secundaria), comun.secundarias)
        assertEquals(original.cobro, comun.cobro)
        assertEquals(original.menu.map { it.first.titulo }, comun.menu.map { it.titulo })
        assertEquals(original.enMenu, comun.enMenu)
    }
}
