package pe.saniape.app.data.staff

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * «💰 Abonar al paquete» en la sesión PENDIENTE, solo con el ajuste por clínica
 * `abono_paquete_agenda` (/api/staff/contexto → `abonoPaqueteAgenda`). Apagado
 * por defecto: DALU (decisión del 2026-10-14) no ve ningún cambio.
 */
class AbonoPaqueteAgendaTest {

    private fun obj(s: String) = Json.parseToJsonElement(s) as JsonObject

    private fun pago(estado: String = "debe", deuda: Double? = 400.0, esPaquete: Boolean = true) =
        EstadoPagoCita(estado = estado, deuda = deuda, etiqueta = "⚠ Paquete · debe S/ 400", esPaquete = esPaquete)

    // ── El contexto ──

    @Test
    fun sinElCampoElAjusteQuedaApagado() {
        val viejo = StaffContextoRepo.parsear(obj("""{"clinicaId":"c1","rol":"Admin","permisos":{}}"""))
        assertFalse(viejo.abonoPaqueteAgenda)
        val apagado = StaffContextoRepo.parsear(obj("""{"clinicaId":"c1","permisos":{},"abonoPaqueteAgenda":false}"""))
        assertFalse(apagado.abonoPaqueteAgenda)
        val basura = StaffContextoRepo.parsear(obj("""{"clinicaId":"c1","permisos":{},"abonoPaqueteAgenda":"si"}"""))
        assertFalse(basura.abonoPaqueteAgenda)
        val encendido = StaffContextoRepo.parsear(obj("""{"clinicaId":"c1","permisos":{},"abonoPaqueteAgenda":true}"""))
        assertTrue(encendido.abonoPaqueteAgenda)
    }

    // ── Con el ajuste APAGADO: idéntico a antes ──

    @Test
    fun apagadoLaSesionPendienteNoOfreceAbono() {
        for (estado in listOf("Pendiente", "Confirmada")) {
            // Sin pasar el ajuste (default) y pasándolo en false: lo mismo.
            assertFalse(paqueteCobrableEnAgenda("Sesión", "t1", estado, "Paquete", pago()), estado)
            assertFalse(paqueteCobrableEnAgenda("Sesión", "t1", estado, "Paquete", pago(), abonoEnPendientes = false), estado)
        }
        // La ya atendida sigue igual que siempre.
        assertTrue(paqueteCobrableEnAgenda("Sesión", "t1", "Completada", "Paquete", pago(), abonoEnPendientes = false))
    }

    @Test
    fun apagadoLaTarjetaPendienteQuedaComoSiempre() {
        val abonar = paqueteCobrableEnAgenda("Sesión", "t1", "Confirmada", "Paquete", pago())
        val p = planAccionesCita(EntradaAccionesCita(estado = "Confirmada", tipo = "Sesión", abonarPaquete = abonar))
        assertEquals(AccionTarjeta.Completar, p.primaria?.accion)
        assertNull(p.cobro)
        assertTrue(AccionTarjeta.AbonarPaquete !in p.enMenu.map { it.accion })
        assertEquals(
            planAccionesCita(EntradaAccionesCita(estado = "Confirmada", tipo = "Sesión")),
            p,
        )
    }

    // ── Con el ajuste ENCENDIDO ──

    @Test
    fun encendidoLaSesionPendienteDeUnPaqueteConDeudaOfreceAbono() {
        for (estado in listOf("Pendiente", "Confirmada")) {
            assertTrue(paqueteCobrableEnAgenda("Sesión", "t1", estado, "Paquete", pago(), abonoEnPendientes = true), estado)
            assertTrue(paqueteCobrableEnAgenda("Sesión", "t1", estado, null, pago(estado = "parcial"), abonoEnPendientes = true), estado)
        }
        assertTrue(paqueteCobrableEnAgenda("Sesión", "t1", "Completada", "Paquete", pago(), abonoEnPendientes = true))
    }

    @Test
    fun encendidoSinDeudaNoPaqueteOCitaCerradaNoOfrece() {
        fun f(tipo: String? = "Sesión", tId: String? = "t1", estado: String = "Confirmada", mod: String? = "Paquete", ep: EstadoPagoCita? = pago()) =
            paqueteCobrableEnAgenda(tipo, tId, estado, mod, ep, abonoEnPendientes = true)
        assertFalse(f(estado = "Cancelada"))
        assertFalse(f(estado = "No asistió"))
        assertFalse(f(ep = pago(deuda = 0.0)))
        assertFalse(f(ep = pago(estado = "pagado", deuda = null)))
        assertFalse(f(ep = null))   // sin estado de pago (sin red): no se ofrece a ciegas
        assertFalse(f(mod = "Sesión suelta", ep = pago(esPaquete = false)))
        assertFalse(f(tipo = "Consulta"))
        assertFalse(f(tId = null))
    }

    @Test
    fun encendidoLaTarjetaPendienteMuestraAbonarJuntoALaDeuda() {
        val p = planAccionesCita(EntradaAccionesCita(estado = "Confirmada", tipo = "Sesión", abonarPaquete = true))
        // La principal sigue siendo el cierre; el 💰 va compacto junto a la píldora de pago.
        assertEquals(AccionTarjeta.Completar, p.primaria?.accion)
        assertEquals(AccionTarjeta.AbonarPaquete, p.cobro?.accion)
        assertEquals("💰 Abonar al paquete", p.cobro?.texto)
    }

    @Test
    fun laSesionAtendidaConservaSuTexto() {
        val p = planAccionesCita(EntradaAccionesCita(estado = "Completada", tipo = "Sesión", abonarPaquete = true))
        assertEquals("💰 Cobrar paquete", p.cobro?.texto)
    }
}
