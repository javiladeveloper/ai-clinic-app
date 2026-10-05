package pe.saniape.app.data.staff

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.double
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import pe.saniape.app.data.offline.RechazoServidor
import pe.saniape.app.ui.clinica.atencion.textoCobrado

/**
 * Cobro dividido: la app tiene que aceptar EXACTAMENTE lo que acepta el servidor
 * (`lib/cobro-dividido.ts` de la web). Si la pantalla habilitara un "Cobrar" que
 * el servidor rechaza, recepción quedaría atascada con el paciente delante.
 */
class CobroDivididoTest {

    private fun ok(pagos: List<PartePago>, total: Double) =
        assertIs<ValidacionPagos.Ok>(validarPagosDivididos(pagos, total)).pagos

    private fun error(pagos: List<PartePago>, total: Double) =
        assertIs<ValidacionPagos.Error>(validarPagosDivididos(pagos, total)).mensaje

    // ── Validación (mismas reglas y textos que el servidor) ──────────────────

    @Test fun dosMediosQueSumanElTotal() {
        val p = ok(listOf(PartePago("Efectivo", 20.0), PartePago("Yape", 20.0)), 40.0)
        assertEquals(listOf(PartePago("Efectivo", 20.0), PartePago("Yape", 20.0)), p)
    }

    @Test fun centimosSinErrorDeComaFlotante() {
        // 0.1 + 0.2 != 0.3 en Double; en céntimos sí cuadra.
        ok(listOf(PartePago("Efectivo", 0.1), PartePago("Yape", 0.2)), 0.3)
        ok(listOf(PartePago("Efectivo", 33.33), PartePago("Yape", 33.33), PartePago("Plin", 33.34)), 100.0)
    }

    @Test fun unCentimoDeMasOMenosNoCuadra() {
        assertEquals(
            "Los pagos no suman el total de S/ 40.00: faltan S/ 0.01.",
            error(listOf(PartePago("Efectivo", 20.0), PartePago("Yape", 19.99)), 40.0),
        )
        assertEquals(
            "Los pagos no suman el total de S/ 40.00: sobran S/ 0.01.",
            error(listOf(PartePago("Efectivo", 20.0), PartePago("Yape", 20.01)), 40.0),
        )
    }

    @Test fun deDosACuatroMedios() {
        val msj = "Un pago dividido lleva de 2 a 4 medios de pago."
        assertEquals(msj, error(listOf(PartePago("Efectivo", 40.0)), 40.0))
        assertEquals(msj, error(emptyList(), 40.0))
        ok(List(4) { PartePago("M$it", 10.0) }, 40.0)
        assertEquals(msj, error(List(5) { PartePago("M$it", 8.0) }, 40.0))
    }

    @Test fun montosCeroONegativosSeRechazan() {
        assertEquals(
            "El monto del pago 2 debe ser mayor que cero.",
            error(listOf(PartePago("Efectivo", 40.0), PartePago("Yape", 0.0)), 40.0),
        )
        assertEquals(
            "El monto del pago 1 debe ser mayor que cero.",
            error(listOf(PartePago("Efectivo", -10.0), PartePago("Yape", 50.0)), 40.0),
        )
        assertEquals(
            "El monto del pago 1 debe ser mayor que cero.",
            error(listOf(PartePago("Efectivo", Double.NaN), PartePago("Yape", 40.0)), 40.0),
        )
    }

    @Test fun masDeDosDecimalesSeRechaza() {
        assertEquals(
            "El monto del pago 1 tiene más de dos decimales.",
            error(listOf(PartePago("Efectivo", 20.005), PartePago("Yape", 19.995)), 40.0),
        )
    }

    @Test fun medioVacioSeRechazaYSeRecortaLoSobrante() {
        assertEquals(
            "Falta el medio de pago del pago 2.",
            error(listOf(PartePago("Efectivo", 20.0), PartePago("  ", 20.0)), 40.0),
        )
        assertEquals("Yape", ok(listOf(PartePago("Efectivo", 20.0), PartePago(" Yape ", 20.0)), 40.0)[1].metodo)
    }

    // ── Filas del formulario (texto) ─────────────────────────────────────────

    @Test fun filasInicialesUsanElPreferidoYElSiguiente() {
        val m = listOf("Efectivo", "Yape", "Plin")
        assertEquals(listOf(FilaPago("Yape"), FilaPago("Efectivo")), filasIniciales(m, "Yape"))
        assertEquals(listOf(FilaPago("Efectivo"), FilaPago("Yape")), filasIniciales(m, "Tarjeta"))
        assertEquals(listOf(FilaPago("Efectivo"), FilaPago("Efectivo")), filasIniciales(emptyList()))
    }

    @Test fun filaNuevaTomaUnMedioSinUsar() {
        val m = listOf("Efectivo", "Yape", "Plin")
        assertEquals(FilaPago("Plin"), filaNueva(m, listOf(FilaPago("Efectivo"), FilaPago("Yape"))))
        assertEquals(FilaPago("Efectivo"), filaNueva(m, m.map { FilaPago(it) }))
    }

    @Test fun estadoEnVivoFaltaSobraYCuadra() {
        val f = listOf(FilaPago("Efectivo", "20"), FilaPago("Yape", ""))
        assertEquals("Falta S/ 20.00", estadoReparto(f, 40.0))
        assertNull(repartoValido(f, 40.0))
        assertEquals("Sobra S/ 5.50", estadoReparto(listOf(FilaPago("Efectivo", "20"), FilaPago("Yape", "25,5")), 40.0))
        val bien = listOf(FilaPago("Efectivo", "20"), FilaPago("Yape", "20.00"))
        assertEquals("✓ Suma S/ 40.00", estadoReparto(bien, 40.0))
        assertEquals(listOf(PartePago("Efectivo", 20.0), PartePago("Yape", 20.0)), repartoValido(bien, 40.0))
    }

    @Test fun sumaBienPeroConUnMontoCeroNoHabilita() {
        val f = listOf(FilaPago("Efectivo", "40"), FilaPago("Yape", "0"))
        assertEquals("El monto del pago 2 debe ser mayor que cero.", estadoReparto(f, 40.0))
        assertNull(repartoValido(f, 40.0))
    }

    @Test fun completarPoneLoQueFaltaEnLaPrimeraVacia() {
        val f = listOf(FilaPago("Efectivo", "12.50"), FilaPago("Yape", ""), FilaPago("Plin", ""))
        assertEquals(1 to "27.50", completarFaltante(f, 40.0))
        // Sin vacías: a la última, sumando.
        assertEquals(1 to "30.00", completarFaltante(listOf(FilaPago("Efectivo", "10"), FilaPago("Yape", "10")), 40.0))
        // Si no falta nada (o sobra), no hay nada que completar.
        assertNull(completarFaltante(listOf(FilaPago("Efectivo", "30"), FilaPago("Yape", "10")), 40.0))
        assertNull(completarFaltante(listOf(FilaPago("Efectivo", "30"), FilaPago("Yape", "20")), 40.0))
    }

    @Test fun montoDeTextoAceptaComa() {
        assertEquals(12.5, montoDeTexto("12,5"))
        assertEquals(12.5, montoDeTexto(" 12.5 "))
        assertNull(montoDeTexto(""))
        assertNull(montoDeTexto("abc"))
    }

    // ── Cuerpo del POST ──────────────────────────────────────────────────────

    @Test fun cuerpoDivididoMandaPagosEnVezDeMetodo() {
        val c = cuerpoCobrarCita(
            "c1", "Efectivo", "cobrar", "2026-10-05",
            listOf(PartePago("Efectivo", 20.0), PartePago("Yape", 20.5)),
        )
        assertFalse("metodo" in c)
        val pagos = c["pagos"] as JsonArray
        assertEquals(2, pagos.size)
        assertEquals("Yape", pagos[1].jsonObject["metodo"]!!.jsonPrimitive.content)
        assertEquals(20.5, pagos[1].jsonObject["monto"]!!.jsonPrimitive.double)
        assertEquals("cobrar", c["modo"]!!.jsonPrimitive.content)
        assertEquals("2026-10-05", c["fecha"]!!.jsonPrimitive.content)
    }

    @Test fun cuerpoSimpleYGratisNoMandanPagos() {
        val simple = cuerpoCobrarCita("c1", "Yape", "cobrar", null)
        assertEquals("Yape", simple["metodo"]!!.jsonPrimitive.content)
        assertFalse("pagos" in simple)
        assertFalse("fecha" in simple)
        val gratis = cuerpoCobrarCita("c1", "Yape", "gratis", "", listOf(PartePago("Efectivo", 20.0), PartePago("Yape", 20.0)))
        assertFalse("pagos" in gratis)
    }

    // ── Respuestas del servidor ──────────────────────────────────────────────

    @Test fun yaEstabaSeLeeDeLaRespuesta() {
        assertTrue(yaEstabaDe(buildJsonObject { put("ok", true); put("yaEstaba", true) }))
        assertFalse(yaEstabaDe(buildJsonObject { put("ok", true) }))
        assertFalse(yaEstabaDe(null))
        assertFalse(yaEstabaDe(JsonObject(mapOf("yaEstaba" to JsonPrimitive(false)))))
    }

    @Test fun cobroParcialPrevioPideElMismoReparto() {
        val r = RechazoServidor("Este cobro quedó a medias…", CODIGO_COBRO_PARCIAL_PREVIO, 409)
        assertTrue(mensajeRechazoCobro(r, dividido = true).contains("MISMO reparto"))
        assertTrue(mensajeRechazoCobro(r, dividido = false).contains("revisa los pagos del tratamiento"))
    }

    @Test fun cincoXXEsInciertoYUnRechazoDeNegocioNo() {
        assertTrue(rechazoIncierto(RechazoServidor("x", null, 500)))
        assertTrue(rechazoIncierto(RechazoServidor("HTTP 504", null, 504)))
        assertFalse(rechazoIncierto(RechazoServidor("x", CODIGO_PAGO_DIVIDIDO_INVALIDO, 400)))
        assertFalse(rechazoIncierto(RechazoServidor("x", CODIGO_COBRO_PARCIAL_PREVIO, 409)))
        assertFalse(rechazoIncierto(null))
        assertTrue(mensajeRechazoCobro(RechazoServidor("x", null, 502), dividido = true).contains("mismo reparto"))
        // Un 400 de validación se muestra con el texto del servidor, tal cual.
        val v = RechazoServidor("Los pagos no suman el total de S/ 40.00: faltan S/ 1.00.", CODIGO_PAGO_DIVIDIDO_INVALIDO, 400)
        assertEquals(v.error, mensajeRechazoCobro(v, dividido = true))
    }

    @Test fun toastDelCobroDividido() {
        val pagos = listOf(PartePago("Efectivo", 20.0), PartePago("Yape", 20.0))
        assertEquals("Cobrado S/ 40.00 (Efectivo + Yape)",
            textoCobrado("Evaluación", "S/ 40.00", "cobrar", "2026-10-05", "2026-10-05", pagos))
        assertEquals("S/ 40.00 abonados al tratamiento (Efectivo + Yape)",
            textoCobrado("Evaluación", "S/ 40.00", "abonar", "2026-10-05", "2026-10-05", pagos))
        assertEquals("Esta evaluación ya estaba cobrada: no se registró de nuevo",
            textoCobrado("Evaluación", "S/ 40.00", "cobrar", "2026-10-05", "2026-10-05", pagos, yaEstaba = true))
        assertEquals("Efectivo + Yape", etiquetaMetodos(pagos + PartePago("Efectivo", 1.0)))
    }
}
