package pe.saniape.app.data.staff

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.coroutines.Continuation
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.coroutines.startCoroutine
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import pe.saniape.app.ui.clinica.odontologia.DecisionPresupuesto
import pe.saniape.app.ui.clinica.odontologia.RegistroPresupuesto
import pe.saniape.app.ui.clinica.odontologia.ResultadoTratamientoEvaluacion
import pe.saniape.app.ui.clinica.odontologia.asegurarTratamientoDeEvaluacion
import pe.saniape.app.ui.clinica.odontologia.hayPresupuestoPendiente
import pe.saniape.app.ui.clinica.odontologia.textoTratamientoEvaluacion

/**
 * Evaluación dental con dos cierres (web d75e56c3): «✓ Acepta hoy» crea lo
 * marcado; «Lo pensará · enviar enlace» no crea nada y manda el enlace para
 * aceptar (gemelo de lib/presupuesto-dental.ts).
 */
class PresupuestoDentalEnlaceTest {

    private fun item(id: String, nombre: String, detalle: String, piezas: List<String>, fase: Int, precio: Double) =
        ItemPresupuestoDental(id, "odontograma", id, nombre, detalle, piezas, fase, precio)

    // Los mismos ítems con que se calculó la huella en JS (lib/presupuesto-dental.ts → huellaPresupuesto).
    private val items = listOf(
        item("b2c4e6f8-0000-4000-8000-000000000002", "Resina / restauración", "Caries en pieza(s) 26, 14", listOf("26", "14"), 2, 160.0),
        item("a1b2c3d4-0000-4000-8000-000000000001", "Endodoncia \"molar\"", "Fractura en pieza(s) 36", listOf("36"), 1, 450.5),
        item("c3d5e7f9-0000-4000-8000-000000000003", "Profilaxis", "Sarro · Boca completa", emptyList(), 2, 80.0),
    )

    @Test fun laHuellaEsLaMismaQueLaDeLaWeb() {
        assertEquals("d9f28fec", huellaPresupuestoDental(items))
        assertEquals("cdcd1ab9", huellaPresupuestoDental(listOf(items[0])))
        assertEquals("741638a5", huellaPresupuestoDental(emptyList()))
        // El orden de las líneas no cambia la huella.
        assertEquals("d9f28fec", huellaPresupuestoDental(items.reversed()))
    }

    @Test fun numerosYTextosComoJsonStringify() {
        assertEquals("160", numeroJs(160.0))
        assertEquals("80.5", numeroJs(80.5))
        assertEquals("\"a\\\"b\\\\c\\n\"", textoJs("a\"b\\c\n"))
    }

    @Test fun faseSugeridaComoLaWeb() {
        assertEquals(1, faseSugerida("Endodoncia"))
        assertEquals(1, faseSugerida("Resina", "Fractura"))
        assertEquals(3, faseSugerida("Blanqueamiento"))
        assertEquals(2, faseSugerida("Resina / restauración", "Caries"))
    }

    @Test fun lineasDelOdontogramaYExtrasComoItems() {
        val caries = LineaPresupuesto("p1", "Resina", "Caries", listOf("14", "26"), listOf("h1"), 80.0, 160.0,
            preciosPieza = listOf(PrecioPieza("14", 1, 80.0), PrecioPieza("26", 1, 80.0)))
        val sarro = LineaPresupuesto("p2", "Profilaxis", "Sarro", listOf("BOCA"), listOf("h2"), 80.0, 80.0, porBoca = true)
        val brackets = ProcedimientoRef("p3", "Brackets", 2500.0, null, null, false, emptyList())
        val r = itemsPresupuestoDental(listOf(caries, sarro), listOf(brackets), preciosManuales = setOf("p2"))
        assertEquals("Caries en pieza(s) 14, 26", r[0].detalle)
        assertEquals(listOf("14", "26"), r[0].piezas)
        assertEquals(2, r[0].preciosPieza?.size)
        assertEquals("Sarro · Boca completa", r[1].detalle)
        assertTrue(r[1].piezas.isEmpty())
        assertEquals("extra-p3", r[2].clave)
        assertEquals("extra", r[2].tipo)
        assertEquals("Tratamiento complementario", r[2].detalle)
        // Precio escrito a mano: no viaja el precio por pieza.
        assertNull(itemsPresupuestoDental(listOf(caries), emptyList(), setOf("p1"))[0].preciosPieza)
    }

    @Test fun whatsappPorFasesConElEnlace() {
        val t = textoPresupuestoDentalWhatsApp(items, "https://saniape.com/presupuesto/abc", "PEN")
        assertTrue(t.startsWith("🦷 *Presupuesto Dental*"))
        assertTrue("🔴 *Fase 1 · Urgente* — S/ 450.50" in t)
        assertTrue("🟡 *Fase 2 · Rehabilitación* — S/ 240.00" in t)
        assertTrue("*Total:* S/ 690.50" in t)
        assertTrue("_Puedes empezar solo con la fase 1 · urgente: S/ 450.50._" in t)
        assertTrue(t.endsWith("Revísalo con tu odontograma y acéptalo desde tu celular:\nhttps://saniape.com/presupuesto/abc"))
        assertTrue(textoPresupuestoDentalWhatsApp(listOf(items[0]), null, "PEN").endsWith("_Para coordinar tus citas o consultas, responde a este mensaje._"))
    }

    @Test fun seReutilizaElAceptadoOElEnviadoVigenteIgual() {
        val ahora = kotlinx.datetime.Instant.parse("2026-10-24T12:00:00Z").toEpochMilliseconds()
        val futuro = "2026-11-20T12:00:00+00:00"
        val enviado = PresupuestoDentalGuardado("e", "tk-e", "Enviado", futuro, "h1", pideFirma = true)
        val aceptado = PresupuestoDentalGuardado("a", "tk-a", "Aceptado", futuro, "h1", pideFirma = true)
        assertEquals("a", presupuestoReutilizable(listOf(aceptado, enviado), "h1", true, ahora)?.id)
        assertEquals("e", presupuestoReutilizable(listOf(enviado), "h1", true, ahora)?.id)
        assertNull(presupuestoReutilizable(listOf(enviado), "h2", true, ahora))           // otro contenido
        assertNull(presupuestoReutilizable(listOf(enviado), "h1", false, ahora))          // otra firma pedida
        assertNull(presupuestoReutilizable(listOf(enviado.copy(venceAt = "2026-10-01T00:00:00Z")), "h1", true, ahora)) // vencido
        assertEquals("Vencido", estadoVisiblePresupuesto("Enviado", "no es fecha", ahora))
        assertEquals("Enviado", estadoVisiblePresupuesto("Enviado", null, ahora))
    }

    @Test fun elTokenTiene64Hex() {
        val t = nuevoTokenPresupuesto()
        assertTrue(Regex("^[0-9a-f]{64}$").matches(t), t)
        assertFalse(t == nuevoTokenPresupuesto())
    }

    @Test fun elSnapshotTieneLasClavesDeLaWebYSinNotas() {
        val h = DienteHallazgo("x", "pac", "14", "car", listOf("O"), notas = "dolor nocturno")
        val cat = listOf(HallazgoDental("car", "Caries", "#ff0000"))
        val s = snapshotPresupuestoDental(listOf(items[0]), listOf(h), cat)
        assertEquals(1, s["version"]!!.jsonPrimitive.content.toInt())
        val it0 = (s["items"] as JsonArray)[0] as JsonObject
        assertEquals("b2c4e6f8-0000-4000-8000-000000000002", it0["procedimiento_id"]!!.jsonPrimitive.content)
        val o0 = (s["odontograma"] as JsonArray)[0] as JsonObject
        assertEquals("Caries", o0["nombre"]!!.jsonPrimitive.content)
        assertEquals("null", o0["notas"].toString())
        assertEquals("160.0", s["total"]!!.jsonPrimitive.content)
    }

    // ── La decisión al completar la evaluación ──

    /** Corre un suspend que no suspende (aquí no hay red: «Lo pensará» sale antes). */
    private fun <T> sinSuspender(bloque: suspend () -> T): T {
        var r: Result<T>? = null
        bloque.startCoroutine(Continuation(EmptyCoroutineContext) { r = it })
        return r!!.getOrThrow()
    }

    @Test fun loPensaraNoCreaNadaNiConsulta() {
        var llamado = 0
        val registro = RegistroPresupuesto().apply { crear = { llamado++; ResultadoTratamientoEvaluacion.Creado(1) } }
        val r = sinSuspender { asegurarTratamientoDeEvaluacion("pac", "cita", registro, DecisionPresupuesto.LO_PENSARA) }
        assertEquals(ResultadoTratamientoEvaluacion.SinAceptar, r)
        assertEquals(0, llamado)
        assertTrue("lo pensará" in textoTratamientoEvaluacion(r))
    }

    @Test fun conPresupuestoPendienteHayDosCierres() {
        val pend = DienteHallazgo("1", "p", "14", "car")
        assertTrue(hayPresupuestoPendiente(listOf(pend)))
        assertFalse(hayPresupuestoPendiente(listOf(pend.copy(tratamientoId = "t"))))
        assertFalse(hayPresupuestoPendiente(listOf(pend.copy(estado = "Realizado"))))
        assertFalse(hayPresupuestoPendiente(emptyList()))
    }
}
