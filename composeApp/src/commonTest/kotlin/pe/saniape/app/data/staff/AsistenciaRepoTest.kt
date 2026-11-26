package pe.saniape.app.data.staff

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** 🕘 «Mi asistencia»: la app solo lee lo que el servidor ya calculó (26/11/2026). */
class AsistenciaRepoTest {
    private fun obj(s: String) = Json.parseToJsonElement(s).jsonObject

    @Test
    fun leeLaRespuestaDelServidor() {
        val m = parsearMiAsistencia(obj("""
            {"activa":true,"enLista":true,"nombre":"Ana","hoy":"2026-11-25","siguiente":"salida","jornadaHoy":"08:00–17:00",
             "estadoHoy":"Tardanza","tardanzaHoyMin":20,
             "marcasHoy":[{"tipo":"entrada","hora":"08:20","medio":"Botón"}],
             "mes":{"dias":3,"tardanzas":1,"tardanzaTexto":"20 min","faltas":0,"horas":"16 h 40 min","extra":"40 min"},
             "dias":[{"fecha":"2026-11-24","dia":"Mar 24/11","marcas":"08:20 · 18:00","estado":"Tardanza","detalle":"20 min tarde · 8 h 40 min","corregida":true}]}
        """))
        assertTrue(m.activa); assertTrue(m.enLista)
        assertEquals("salida", m.siguiente)
        assertEquals(20, m.tardanzaHoyMin)
        assertEquals(listOf(MarcaHoy("entrada", "08:20", "Botón")), m.marcasHoy)
        assertEquals(MesAsistencia(3, 1, "20 min", 0, "16 h 40 min", "40 min"), m.mes)
        assertEquals(1, m.dias.size)
        assertTrue(m.dias[0].corregida)
        assertEquals("08:20 · 18:00", m.dias[0].marcas)
    }

    @Test
    fun sinElAgregadoOConCamposQueFaltan_noRompe() {
        val apagado = parsearMiAsistencia(obj("""{"activa":false}"""))
        assertFalse(apagado.activa); assertFalse(apagado.enLista)
        assertEquals("entrada", apagado.siguiente)
        assertTrue(apagado.marcasHoy.isEmpty()); assertTrue(apagado.dias.isEmpty())
        assertEquals("0 min", apagado.mes.horas)
        // Una marca sin hora o un día sin fecha se descartan, no tumban la lista.
        val raro = parsearMiAsistencia(obj("""{"activa":true,"enLista":true,"siguiente":"otra","marcasHoy":[{"tipo":"entrada"},"x"],"dias":[{"dia":"Lun"}]}"""))
        assertEquals("entrada", raro.siguiente)
        assertTrue(raro.marcasHoy.isEmpty()); assertTrue(raro.dias.isEmpty())
    }

    @Test
    fun mensajeAlMarcar() {
        assertEquals("✓ Entrada registrada a las 08:02", mensajeDeMarca(obj("""{"ok":true,"tipo":"entrada","hora":"08:02"}""")))
        assertEquals("✓ Salida registrada a las 17:05", mensajeDeMarca(obj("""{"ok":true,"tipo":"salida","hora":"17:05"}""")))
        assertEquals("✓ Entrada registrada", mensajeDeMarca(null))
    }
}
