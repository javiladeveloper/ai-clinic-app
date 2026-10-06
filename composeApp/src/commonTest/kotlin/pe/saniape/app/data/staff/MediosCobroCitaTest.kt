package pe.saniape.app.data.staff

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Con qué medios se pagó una cita (gemelo de lib/comprobante-cita.ts y
 * metodosPagoDeCitas de la web) y cuándo se ofrece "Anular cobro".
 */
class MediosCobroCitaTest {

    private val id = "0b6f7a1e-1111-4222-8333-444455556666"

    @Test
    fun comprobantes_de_una_cita() {
        assertEquals("cita:$id", comprobanteCita(id))
        assertEquals("cita:$id#3", comprobanteCita(id, 3))
        assertEquals(listOf("cita:$id#2", "cita:$id#3", "cita:$id#4"), comprobantesExtraDeCita(id))
    }

    @Test
    fun id_y_parte_del_comprobante() {
        assertEquals(id, citaIdDeComprobante("cita:$id"))
        assertEquals(id, citaIdDeComprobante("cita:$id#2"))
        assertEquals(1, parteDeComprobante("cita:$id"))
        assertEquals(4, parteDeComprobante("cita:$id#4"))
        assertNull(citaIdDeComprobante("pago:$id"))
        assertNull(citaIdDeComprobante("cita:"))
        assertNull(citaIdDeComprobante(null))
        assertNull(parteDeComprobante("F001-492"))
    }

    @Test
    fun partes_de_la_descripcion() {
        assertEquals(2, partesDeDescripcion("Cobro de Consulta — Ana (parte 1 de 2)"))
        assertEquals(4, partesDeDescripcion("Cobro (parte 1 de 4)  "))
        assertNull(partesDeDescripcion("Cobro de Consulta — Ana"))
        assertNull(partesDeDescripcion("Cobro (parte 1 de 7)"))
        assertNull(partesDeDescripcion(null))
    }

    @Test
    fun cobro_simple_un_solo_medio() {
        val base = listOf(MovimientoCobroCita("cita:$id", "Yape", "Cobro de Consulta"))
        assertTrue(citasConCobroDividido(base).isEmpty())
        assertEquals(mapOf(id to "Yape"), metodosPorCita(base))
    }

    @Test
    fun cobro_dividido_en_orden_de_parte_y_sin_repetir() {
        val base = listOf(MovimientoCobroCita("cita:$id", "Efectivo", "Cobro (parte 1 de 3)"))
        assertEquals(setOf(id), citasConCobroDividido(base))
        val extra = listOf(
            MovimientoCobroCita("cita:$id#3", "Efectivo"),
            MovimientoCobroCita("cita:$id#2", "Yape"),
        )
        assertEquals(mapOf(id to "Efectivo + Yape"), metodosPorCita(base, extra))
    }

    @Test
    fun dividido_sin_partes_leidas_queda_con_el_medio_base() {
        val base = listOf(MovimientoCobroCita("cita:$id", "Plin", "Cobro (parte 1 de 2)"))
        assertEquals(mapOf(id to "Plin"), metodosPorCita(base, emptyList()))
    }

    @Test
    fun movimiento_sin_metodo_no_inventa_medio() {
        val base = listOf(MovimientoCobroCita("cita:$id", null, null))
        assertTrue(metodosPorCita(base).isEmpty())
    }

    @Test
    fun etiqueta_de_metodos() {
        assertEquals("Efectivo + Yape", etiquetaDeMetodos(listOf("Efectivo", " Yape ", null, "", "Efectivo")))
        assertTrue(esCobroDividido("Efectivo + Yape"))
        assertFalse(esCobroDividido("Yape"))
        assertFalse(esCobroDividido(null))
    }

    @Test
    fun anular_cobro_solo_admin_con_cobro_dividido() {
        fun puede(rol: String? = "Admin", pagos: Boolean = true, tipo: String? = "Consulta", costo: Double? = 40.0,
                  estado: String? = "Completada", pagada: String? = "2026-10-05T10:00:00Z", medios: String? = "Efectivo + Yape") =
            puedeAnularCobro(rol, pagos, tipo, costo, estado, pagada, medios)
        assertTrue(puede())
        assertTrue(puede(estado = "Confirmada"))   // pagó por adelantado
        assertFalse(puede(rol = "Recepcionista"))
        assertFalse(puede(pagos = false))
        assertFalse(puede(medios = "Yape"))         // cobro simple: el servidor respondería 409
        assertFalse(puede(medios = null))           // aún no se sabe con qué se pagó
        assertFalse(puede(pagada = null))
        assertFalse(puede(tipo = "Sesión"))
        assertFalse(puede(costo = 0.0))
        assertFalse(puede(estado = "Cancelada"))
    }

    @Test
    fun mensajes_de_anular_cobro() {
        val simple = "Este cobro no se pagó con varios medios: corrígelo con \"Corregir cobro\"."
        assertEquals(simple, mensajeAnularCobro(409, simple))
        assertEquals("Solo el administrador puede anular un cobro", mensajeAnularCobro(403, "Solo el administrador puede anular un cobro"))
        assertEquals("Solo el administrador puede anular un cobro.", mensajeAnularCobro(403, null))
        assertTrue(mensajeAnularCobro(0, null).startsWith("Sin conexión"))
        assertTrue(mensajeAnularCobro(504, "x").contains("Recarga la agenda"))
        assertEquals("Tu sesión expiró. Vuelve a entrar.", mensajeAnularCobro(401, "No autenticado"))
        assertEquals("No se pudo anular el cobro (HTTP 418).", mensajeAnularCobro(418, null))
    }
}
