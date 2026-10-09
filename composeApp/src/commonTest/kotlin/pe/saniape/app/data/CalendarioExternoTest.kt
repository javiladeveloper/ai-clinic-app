package pe.saniape.app.data

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import pe.saniape.app.data.staff.estadoCalendarioDe
import pe.saniape.app.data.staff.haceCuanto

/** Estado de "Agenda de Google Calendar" (GET /api/staff/calendario) y el "hace X". */
class CalendarioExternoTest {
    private val json = """
        {"googleConfigurado":true,"llaveConfigurada":true,
         "conexiones":[{"id":"c1","proveedor":"google","cuenta":"podologa@gmail.com","estado":"activa","ultimo_error":null},
                       {"id":"c2","proveedor":"ics","cuenta":"","estado":"activa"}],
         "fuentes":[{"id":"f1","nombre":"Agenda","terapeuta_nombre":"Lic. Ana","proveedor":"google",
                     "importacion_inicial_at":"2026-10-09T10:00:00Z","ultima_sync_at":"2026-10-09T11:55:00Z",
                     "ultimo_error":null,"citas":312,"pendientes":4},
                    {"id":"f2","nombre":"agenda.ics","terapeuta_nombre":null,"proveedor":"ics",
                     "importacion_inicial_at":null,"ultima_sync_at":null,"citas":0,"pendientes":0}]}
    """.trimIndent()

    @Test fun leeCuentasYCalendarios() {
        val e = estadoCalendarioDe(Json.parseToJsonElement(json).jsonObject)
        assertTrue(e.disponible)
        assertEquals(1, e.cuentas.size)                 // la conexión .ics no es una cuenta
        assertEquals("podologa@gmail.com", e.cuentas[0].cuenta)
        val g = e.calendarios[0]
        assertEquals(312, g.citas); assertEquals(4, g.sinCupo); assertEquals("Lic. Ana", g.profesional)
        assertFalse(g.esIcs)
        assertTrue(e.calendarios[1].esIcs)
        assertNull(e.calendarios[1].importadoEn)
    }

    @Test fun sinConfigurarNoEstaDisponible() {
        val e = estadoCalendarioDe(Json.parseToJsonElement("""{"googleConfigurado":false,"llaveConfigurada":true}""").jsonObject)
        assertFalse(e.disponible)
        assertTrue(e.cuentas.isEmpty() && e.calendarios.isEmpty())
    }

    @Test fun haceCuantoComoLaWeb() {
        val ahora = 1_000_000_000_000L
        assertEquals("nunca", haceCuanto(null, ahora))
        assertEquals("hace un momento", haceCuanto(ahora - 20_000, ahora))
        assertEquals("hace 5 min", haceCuanto(ahora - 5 * 60_000, ahora))
        assertEquals("hace 3 h", haceCuanto(ahora - 3 * 3_600_000, ahora))
        assertEquals("hace 2 días", haceCuanto(ahora - 48 * 3_600_000, ahora))
    }
}
