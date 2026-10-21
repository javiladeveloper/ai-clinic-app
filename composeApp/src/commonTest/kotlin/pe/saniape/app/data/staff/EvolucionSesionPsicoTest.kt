package pe.saniape.app.data.staff

import kotlin.test.*

class EvolucionSesionPsicoTest {
    @Test fun conservaTextoLibreAnterior() {
        assertEquals("Nota anterior\nCon avances", leerEvolucionPsico("Nota anterior\nCon avances").evolucion)
    }
    @Test fun mismoFormatoDeLaWebConservaLosCuatroCampos() {
        val b = EvolucionSesionPsico("Se trabajó ansiedad.\nParticipa activamente.", "Ánimo estable", "Registro diario", "Revisar avances")
        assertEquals(b, leerEvolucionPsico(textoEvolucionPsico(b)))
        assertTrue(textoEvolucionPsico(b).contains("Tareas e indicaciones:\nRegistro diario"))
    }
    @Test fun noPierdeEspaciosAlEscribir() {
        val b = EvolucionSesionPsico("Se trabajó ", "Ánimo ")
        assertEquals(b, leerEvolucionPsico(textoEvolucionPsico(b)))
    }
    @Test fun soloPsicologiaRecibeElNuevoCierre() {
        val lista = listOf(Triple("psi", "psicologia", "Psicología"), Triple("fis", "fisioterapia", "Fisioterapia"))
        assertTrue(esCierrePsicologia(false, false, "psi", lista))
        assertFalse(esCierrePsicologia(false, false, "fis", lista))
        assertFalse(esCierrePsicologia(true, false, "psi", lista))
        assertFalse(esCierrePsicologia(false, false, null, lista))
        assertTrue(esCierrePsicologia(false, false, null, lista.take(1)))
        assertFalse(esCierrePsicologia(false, false, "psiq", listOf(Triple("psiq", "medicina_general", "Psiquiatría"))))
    }
}
