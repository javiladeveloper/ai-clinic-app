package pe.saniape.app.data.staff

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PermisoTratamientoTest {
    private val sesiones = setOf("sesiones")
    private val pacientes = setOf("pacientes")

    @Test fun accionesNormalesPidenSesiones() {
        for (a in listOf("crear", "editar", "ampliar", "no_volvio")) {
            assertTrue(puedeAccionTratamiento(a, null, sesiones, false), a)
            assertFalse(puedeAccionTratamiento(a, null, pacientes, false), a)
            assertFalse(puedeAccionTratamiento(a, null, emptySet(), false), a)
        }
        assertTrue(puedeAccionTratamiento("estado", "Suspendido", sesiones, false))
        assertFalse(puedeAccionTratamiento("estado", "Completado", pacientes, false))
    }

    @Test fun eliminarPidePacientesOAdmin() {
        assertTrue(puedeAccionTratamiento("estado", "Eliminado", pacientes, false))
        assertFalse(puedeAccionTratamiento("estado", "Eliminado", sesiones, false))
        assertTrue(puedeAccionTratamiento("estado", "Eliminado", emptySet(), true))
    }

    @Test fun corregirSoloAdmin() {
        assertFalse(puedeAccionTratamiento("corregir", null, sesiones + pacientes, false))
        assertTrue(puedeAccionTratamiento("corregir", null, emptySet(), true))
    }

    @Test fun mensaje403() {
        val ep = "/api/staff/tratamiento/accion"
        assertEquals(MSJ_SIN_PERMISO_TRATAMIENTO, mensajeSinPermisoTratamiento(ep, 403, null))
        assertEquals(MSJ_SIN_PERMISO_TRATAMIENTO, mensajeSinPermisoTratamiento(ep, 400, "SIN_PERMISO"))
        assertNull(mensajeSinPermisoTratamiento(ep, 400, "OTRO"))
        assertNull(mensajeSinPermisoTratamiento("/api/staff/cita/crear", 403, "SIN_PERMISO"))
    }
}
