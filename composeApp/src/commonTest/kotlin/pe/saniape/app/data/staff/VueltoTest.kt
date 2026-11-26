package pe.saniape.app.data.staff

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** Vuelto con otro medio (caso real 25/11/2026: cuesta 50, da 100, vuelto por Yape). */
class VueltoTest {
    @Test
    fun calculaEnCentimos() {
        assertEquals(50.0, calcularVuelto(50.0, 100.0))
        assertEquals(0.2, calcularVuelto(0.3, 0.5))
        assertEquals(0.1, calcularVuelto(19.9, 20.0))
        assertEquals(0.0, calcularVuelto(50.0, 50.0))
        assertEquals(0.0, calcularVuelto(50.0, 40.0))
    }

    @Test
    fun seMandaSoloSiCambiaElCajon() {
        assertEquals(VueltoCobro(100.0, "Yape"), vueltoParaEnviar(50.0, "Efectivo", "100", "Yape"))
        assertEquals(VueltoCobro(100.5, "Efectivo"), vueltoParaEnviar(50.0, "Yape", "100,5", " Efectivo "))
        assertNull(vueltoParaEnviar(50.0, "Efectivo", "100", "Efectivo"))   // el vuelto de siempre
        assertNull(vueltoParaEnviar(50.0, "Yape", "100", "Plin"))           // no pasa por el cajón
        assertNull(vueltoParaEnviar(50.0, "Efectivo", "", "Yape"))
        assertNull(vueltoParaEnviar(50.0, "Efectivo", "50", "Yape"))
        assertNull(vueltoParaEnviar(50.0, "Efectivo", "abc", "Yape"))
    }

    @Test
    fun viajaEnElCuerpoDelCobro_soloConUnMedio() {
        val v = VueltoCobro(100.0, "Yape")
        val c = cuerpoCobrarCita("c1", "Efectivo", "cobrar", null, vuelto = v)
        val enviado = c["vuelto"] as JsonObject
        assertEquals("100.0", enviado["recibido"].toString())
        assertEquals("\"Yape\"", enviado["metodo"].toString())
        // Sin vuelto, el cuerpo es el de siempre (ni la clave).
        assertNull(cuerpoCobrarCita("c1", "Efectivo", "cobrar", null)["vuelto"])
        // Dividido o sin costo: no se manda.
        assertNull(cuerpoCobrarCita("c1", "Efectivo", "cobrar", null, pagos = listOf(PartePago("Efectivo", 25.0), PartePago("Yape", 25.0)), vuelto = v)["vuelto"])
        assertNull(cuerpoCobrarCita("c1", "Efectivo", "gratis", null, vuelto = v)["vuelto"])
    }

    @Test
    fun avisoDelServidor() {
        val conAviso = Json.parseToJsonElement("{\"ok\":true,\"avisoVuelto\":\"El cobro se registró, pero el vuelto no\"}").jsonObject
        assertEquals("El cobro se registró, pero el vuelto no", avisoVueltoDe(conAviso))
        assertNull(avisoVueltoDe(Json.parseToJsonElement("{\"ok\":true,\"vuelto\":true}").jsonObject))
        assertNull(avisoVueltoDe(null))
    }
}
