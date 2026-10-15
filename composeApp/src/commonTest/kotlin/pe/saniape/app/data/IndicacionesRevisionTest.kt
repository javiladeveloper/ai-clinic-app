package pe.saniape.app.data

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import pe.saniape.app.data.staff.ContenidoEditorIndicaciones
import pe.saniape.app.data.staff.candidataIndicacionesTrasCompletar
import pe.saniape.app.data.staff.hayCambiosEditorIndicaciones

/** Revisión previa a 2.49.0: psiquiatría nunca avisa; "Cambiar" confirma si hay cambios. */
class IndicacionesRevisionTest {

    private fun candidata(nombre: String?) = candidataIndicacionesTrasCompletar(
        pacienteId = "p1", puedePacientes = true, puedeSesiones = true, encolada = false,
        esNutricion = false, ofreceReceta = false, esMedicaGuiada = false, otraEntrega = false,
        especialidadNombre = nombre,
    )

    @Test fun psiquiatriaNoAvisaSinImportarTildesNiMayusculas() {
        assertFalse(candidata("Psiquiatría"))
        assertFalse(candidata("PSIQUIATRIA infantil"))
        assertTrue(candidata("Psicología"))
        assertTrue(candidata("Estética facial"))
        assertTrue(candidata(null))
    }

    @Test fun cambiosDelEditorRespectoDelOrigen() {
        val origen = ContenidoEditorIndicaciones(titulo = "Después del peeling", indicaciones = "Hidratar")
        assertFalse(hayCambiosEditorIndicaciones(origen, origen))
        assertFalse(hayCambiosEditorIndicaciones(origen.copy(titulo = " Después del peeling "), origen))
        assertTrue(hayCambiosEditorIndicaciones(origen.copy(evitar = "Sol"), origen))
        // En blanco: cualquier texto escrito cuenta.
        assertFalse(hayCambiosEditorIndicaciones(ContenidoEditorIndicaciones(), null))
        assertTrue(hayCambiosEditorIndicaciones(ContenidoEditorIndicaciones(control = "7 días"), null))
    }
}
