package pe.saniape.app.data.staff

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Gemelo de lib/__tests__/sani-ajustes.test.ts (lo que corresponde a la pantalla). */
class SaniAjustesTest {

    @Test fun lasOpcionesCabenEnElRangoDeLeadAI() {
        for (h in OPCIONES_HORAS_SEGUIMIENTO) assertTrue(h in HorasSeguimiento.MIN..HorasSeguimiento.MAX)
        assertEquals("A las ~20 horas de nuestro último mensaje", etiquetaHorasSeguimiento(20))
    }

    @Test fun unLeadAIQueNoLoConoceNoMuestraElInterruptor() {
        assertEquals(SeguimientoVentana(false, false, 20), seguimientoDesdeMiPlan(null, null))
        assertEquals(SeguimientoVentana(true, true, 18), seguimientoDesdeMiPlan(true, 18))
        assertEquals(SeguimientoVentana(true, false, 20), seguimientoDesdeMiPlan(false, 99))
        val o = Json.parseToJsonElement("""{"seguimientoVentanaDisponible":false,"seguimientoVentanaActivo":false,"seguimientoVentanaHoras":20}""").jsonObject
        assertFalse(seguimientoDeComportamiento(o).disponible)
        val o2 = Json.parseToJsonElement("""{"seguimientoVentanaDisponible":true,"seguimientoVentanaActivo":true,"seguimientoVentanaHoras":16}""").jsonObject
        assertEquals(SeguimientoVentana(true, true, 16), seguimientoDeComportamiento(o2))
        // Backend viejo: sin los campos, se oculta.
        assertFalse(seguimientoDeComportamiento(Json.parseToJsonElement("""{"ritmoSeguimiento":"normal"}""").jsonObject).disponible)
    }

    @Test fun precioRepetidoPorDefectoAmable() {
        assertEquals(ModoPrecioRepetido(false, 3), modoPrecioRepetido(emptyMap()))
        assertEquals(ModoPrecioRepetido(false, 3), modoPrecioRepetido(mapOf("escalarPrecioTras" to 0)))
        assertEquals("escalarPrecioTras" to 0, cambioPrecioRepetido(false, 4))
        assertEquals(ModoPrecioRepetido(true, 4), modoPrecioRepetido(mapOf("escalarPrecioTras" to 4)))
        assertEquals("escalarPrecioTras" to 4, cambioPrecioRepetido(true, 4))
    }

    @Test fun listaCerradaYEnRango() {
        assertEquals(
            mapOf("escalarPrecioTras" to 3, "pedirCorreo" to 1),
            numerosCaptacionValidos(JsonPrimitive("""{"escalarPrecioTras":3,"inventada":1,"reintentosDni":9,"pedirCorreo":1}""")),
        )
        assertEquals(emptyMap(), numerosCaptacionValidos(JsonPrimitive("no es json")))
        assertEquals(emptyMap(), numerosCaptacionValidos(null))
        assertEquals(mapOf("escalarPrecioTras" to 2), numerosCaptacionValidos(Json.parseToJsonElement("""{"escalarPrecioTras":2}""")))
    }

    @Test fun bienvenidaPropiaSinAvisoIntegraLaFrase() {
        assertEquals(
            "¡Hola! Te escribe el asistente virtual de Clínica Sol. Gracias por escribirnos 😊",
            vistaPreviaBienvenida("¡Hola! Gracias por escribirnos 😊", "Clínica Sol"),
        )
        val yaDice = "¡Hola! 👋 Te saluda el asistente virtual de Clínica Sol. ¿En qué te ayudo?"
        assertTrue(bienvenidaDivulga(yaDice))
        assertEquals(yaDice, vistaPreviaBienvenida(yaDice, "Clínica Sol"))
    }

    @Test fun leeElTextoGuardadoYValidaElLargo() {
        assertEquals("Hola", leerTextoConfig(JsonPrimitive("\" Hola \"")))
        assertEquals("", leerTextoConfig(null))
        assertNull(validarBienvenida("Hola"))
        assertNull(validarBienvenida(""))
        assertNotNull(validarBienvenida("a".repeat(MAX_BIENVENIDA + 1)))
        assertNotNull(validarBienvenida(null))
    }

    @Test fun virtualASecasNoDivulga() {
        assertFalse(bienvenidaDivulga("Tenemos consultas virtuales"))
        assertEquals(
            "¡Hola! Te escribe el asistente virtual de Clínica Sol. Tenemos consultas virtuales 😊",
            vistaPreviaBienvenida("¡Hola! Tenemos consultas virtuales 😊", "Clínica Sol"),
        )
        for (t in listOf("soy el asistente virtual", "nuestro bot", "atención automática", "inteligencia artificial")) {
            assertTrue(bienvenidaDivulga(t), t)
        }
    }

    @Test fun noCortaEnDrNiSr() {
        assertEquals(
            "¡Hola! Te escribe el asistente virtual de Clínica Sol. Te atiende la Dra. Pérez. Cuéntame.",
            vistaPreviaBienvenida("¡Hola! Te atiende la Dra. Pérez. Cuéntame.", "Clínica Sol"),
        )
        assertEquals(
            "Consultorio del Dr. Ruiz. Te escribe el asistente virtual de Dr. Ruiz. ¿En qué te ayudo?",
            vistaPreviaBienvenida("Consultorio del Dr. Ruiz. ¿En qué te ayudo?", "Dr. Ruiz"),
        )
    }

    @Test fun conUstedPasaPorAUsted() {
        assertEquals(
            "¡Hola! Le escribe el asistente virtual de Clínica Sol. Cuénteme qué necesita.",
            vistaPreviaBienvenida("¡Hola! Cuéntame qué necesitas.", "Clínica Sol", "usted"),
        )
        assertEquals("¿En qué le puedo ayudar? Agende su cita con usted mismo.", aUsted("¿En qué te puedo ayudar? Agenda tu cita contigo mismo.").replace("con usted mismo", "con usted mismo"))
        // Mayúsculas acentuadas: IGNORE_CASE con unicode.
        assertEquals("Está listo y Tiene su cita", aUsted("ESTÁS listo y TIENES tu cita"))
        assertEquals("Cuénteme", aUsted("CUÉNTAME"))
        assertEquals("Venga con ropa cómoda; se ven los resultados.", aUsted("Ven con ropa cómoda; se ven los resultados."))
        assertEquals("Podemos agendarle hoy y se lo explica la doctora.", aUsted("Podemos agendarte hoy y te lo explica la doctora."))
    }
}
