package pe.saniape.app.data.staff

import kotlin.test.Test
import kotlin.test.assertEquals

class FichaHistorialTest {
    @Test
    fun realizadoConSaldoSigueALaVista() {
        assertEquals(false, vaAlHistorial("Completado", "Pendiente"))
        assertEquals(false, vaAlHistorial("Completado", "Parcial"))
        assertEquals(true, vaAlHistorial("Completado", "Pagado"))
    }

    @Test
    fun losCerradosDeSiempre() {
        assertEquals(true, vaAlHistorial("Alta", "Pendiente"))
        assertEquals(true, vaAlHistorial("Cancelado", null))
        assertEquals(true, vaAlHistorial("Suspendido", null))
        assertEquals(false, vaAlHistorial("Activo", "Pendiente"))
    }
}
