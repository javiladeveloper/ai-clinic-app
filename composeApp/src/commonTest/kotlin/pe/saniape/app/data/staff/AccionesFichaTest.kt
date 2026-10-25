package pe.saniape.app.data.staff

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** El ⋯ de la cabecera de la ficha: Paciente · Portal · Dar de baja al final. */
class AccionesFichaTest {

    @Test
    fun activoConPortalTodoAgrupadoYDarDeBajaAlFinal() {
        val p = accionesMenuFicha(inactivo = false, puedeInvitar = true, conTelefono = true)
        assertTrue(p.visibles.isEmpty())
        assertEquals(
            listOf(
                GrupoMenu("Paciente", listOf(AccionFicha.Editar)),
                GrupoMenu("Portal", listOf(AccionFicha.InvitarWhatsApp, AccionFicha.CopiarPortal)),
                GrupoMenu(GRUPO_PELIGRO, listOf(AccionFicha.DarDeBaja)),
            ),
            p.menu,
        )
    }

    @Test
    fun inactivoReactivarEnVezDeDarDeBaja() {
        val p = accionesMenuFicha(inactivo = true, puedeInvitar = false, conTelefono = true)
        assertEquals(listOf(AccionFicha.Editar, AccionFicha.Reactivar), p.enMenu)
    }

    @Test
    fun sinTelefonoSoloSeCopiaElEnlace() {
        val p = accionesMenuFicha(inactivo = false, puedeInvitar = true, conTelefono = false)
        assertEquals(listOf(AccionFicha.Editar, AccionFicha.CopiarPortal, AccionFicha.DarDeBaja), p.enMenu)
    }

    @Test
    fun sinPortalNiInvitarNiCopiar() {
        assertEquals(listOf(AccionFicha.Editar, AccionFicha.DarDeBaja), accionesMenuFicha(false, false, true).enMenu)
    }
}
