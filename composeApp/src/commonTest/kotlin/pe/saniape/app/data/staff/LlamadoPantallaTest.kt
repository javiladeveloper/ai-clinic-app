package pe.saniape.app.data.staff

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** 📢 Llamado en pantalla (21/11/2026): gemelo de «📢 Llamar en pantalla» de /citas web. */
class LlamadoPantallaTest {
    private fun json(t: String) = Json.parseToJsonElement(t) as JsonObject
    private val general = PantallaLlamadoRef(null)
    private val sedeA = PantallaLlamadoRef("sede-a")

    @Test
    fun la_pantalla_de_la_sede_manda_sobre_la_general() {
        assertEquals(sedeA, pantallaDeSede(listOf(general, sedeA), "sede-a"))
        assertEquals(general, pantallaDeSede(listOf(general, sedeA), "sede-b"))
        assertEquals(general, pantallaDeSede(listOf(general, sedeA), null))
        // Solo hay pantalla de otra sede: esta sede no tiene dónde llamar.
        assertNull(pantallaDeSede(listOf(sedeA), "sede-b"))
        assertNull(pantallaDeSede(emptyList(), null))
    }

    @Test
    fun se_ofrece_en_la_cita_de_hoy_en_curso_y_con_pantalla() {
        val hoy = "2026-11-21"
        assertTrue(ofreceLlamarPantalla("Confirmada", hoy, hoy, null, listOf(general)))
        assertTrue(ofreceLlamarPantalla("Pendiente", "2026-11-21T00:00:00", hoy, "sede-a", listOf(sedeA)))
        // No en la de otro día, ni en la ya cerrada, ni sin pantalla para su sede.
        assertFalse(ofreceLlamarPantalla("Confirmada", "2026-11-22", hoy, null, listOf(general)))
        assertFalse(ofreceLlamarPantalla("Completada", hoy, hoy, null, listOf(general)))
        assertFalse(ofreceLlamarPantalla("Cancelada", hoy, hoy, null, listOf(general)))
        assertFalse(ofreceLlamarPantalla("Confirmada", hoy, hoy, "sede-b", listOf(sedeA)))
        assertFalse(ofreceLlamarPantalla("Confirmada", hoy, hoy, null, emptyList()))
    }

    @Test
    fun el_aviso_dice_lo_que_salio_en_el_televisor() {
        assertEquals(ResultadoLlamado(true, "📢 Elena H. → Consultorio 1"),
            resultadoLlamado(true, json("""{"ok":true,"llamado":true,"nombre":"Elena H.","destino":"Consultorio 1"}""")))
        assertEquals(ResultadoLlamado(true, "📢 Llamando a Elena H."),
            resultadoLlamado(true, json("""{"ok":true,"llamado":true,"nombre":"Elena H."}""")))
    }

    @Test
    fun cuando_no_sale_explica_por_que() {
        assertEquals("La clínica no tiene una pantalla activa para esta sede.",
            resultadoLlamado(true, json("""{"ok":true,"llamado":false,"motivo":"sin_pantalla"}""")).texto)
        assertEquals("Ya se llamó hace un momento.",
            resultadoLlamado(true, json("""{"ok":true,"llamado":false,"motivo":"repetido"}""")).texto)
        // El rechazo del servidor (plan, permiso, cita de otro día) llega con su texto.
        val plan = resultadoLlamado(false, json("""{"error":"El llamado en pantalla es del plan Plus","codigo":"LIMITE_PLAN"}"""))
        assertFalse(plan.ok); assertEquals("El llamado en pantalla es del plan Plus", plan.texto)
        assertEquals("No se pudo llamar al paciente.", resultadoLlamado(false, null).texto)
    }

    @Test
    fun la_accion_va_al_menu_de_atencion_solo_en_la_cita_en_curso() {
        val base = EntradaAccionesCita(estado = "Confirmada", tipo = "Consulta", llamarPantalla = true)
        val plan = planAccionesCita(base)
        assertTrue(plan.enMenu.any { it.accion == AccionTarjeta.LlamarPantalla && it.texto == "📢 Llamar en pantalla" })
        assertFalse(plan.visibles.any { it.accion == AccionTarjeta.LlamarPantalla })
        assertEquals(GrupoAccion.ATENCION, grupoDe(AccionTarjeta.LlamarPantalla))
        // Ya completada o cancelada no se llama; y sin la bandera, no aparece.
        assertFalse(accionesCita(base.copy(estado = "Completada")).any { it.accion == AccionTarjeta.LlamarPantalla })
        assertFalse(accionesCita(base.copy(estado = "Cancelada")).any { it.accion == AccionTarjeta.LlamarPantalla })
        assertFalse(accionesCita(base.copy(llamarPantalla = false)).any { it.accion == AccionTarjeta.LlamarPantalla })
    }
}
