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
    fun todasLasOpcionesComoLaWeb() {
        val p = accionesMenuFicha(
            inactivo = false, puedeInvitar = true, conTelefono = true,
            conFiliacion = true, conHistoria = true, puedeAgendar = true,
        )
        assertEquals(
            listOf(AccionFicha.Agendar, AccionFicha.Editar, AccionFicha.Historia, AccionFicha.Filiacion,
                AccionFicha.InvitarWhatsApp, AccionFicha.CopiarPortal, AccionFicha.DarDeBaja),
            p.enMenu,
        )
    }

    @Test
    fun sinDatosPersonalesElMedicoIgualVeHistoriaYFiliacion() {
        val p = accionesMenuFicha(
            inactivo = false, puedeInvitar = true, conTelefono = true,
            conFiliacion = true, conHistoria = true, puedeAgendar = false, conDatos = false,
        )
        assertEquals(listOf(AccionFicha.Historia, AccionFicha.Filiacion), p.enMenu)
    }

    @Test
    fun inactivoReactivarEnVezDeDarDeBaja() {
        val p = accionesMenuFicha(inactivo = true, puedeInvitar = false, conTelefono = true, puedeAgendar = true)
        // Dada de baja: no se agenda (primero se reactiva).
        assertEquals(listOf(AccionFicha.Reactivar, AccionFicha.Editar), p.enMenu)
    }

    @Test
    fun sinTelefonoSoloSeCopiaElEnlace() {
        val conFil = accionesMenuFicha(inactivo = false, puedeInvitar = false, conTelefono = false, conFiliacion = true)
        assertEquals(listOf(AccionFicha.Editar, AccionFicha.Filiacion, AccionFicha.DarDeBaja), conFil.enMenu)
        // Dada de baja: sin filiación (se reactiva primero).
        assertEquals(listOf(AccionFicha.Reactivar, AccionFicha.Editar),
            accionesMenuFicha(inactivo = true, puedeInvitar = false, conTelefono = false, conFiliacion = true).enMenu)
        val p = accionesMenuFicha(inactivo = false, puedeInvitar = true, conTelefono = false)
        assertEquals(listOf(AccionFicha.Editar, AccionFicha.CopiarPortal, AccionFicha.DarDeBaja), p.enMenu)
    }

    @Test
    fun sinPortalNiInvitarNiCopiar() {
        assertEquals(listOf(AccionFicha.Editar, AccionFicha.DarDeBaja), accionesMenuFicha(false, false, true).enMenu)
    }
}
