package pe.saniape.app.data

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** GET /api/paciente/mis-presupuestos (contrato del portal §16) → modelo, y su hoja. */
class MisPresupuestosPortalTest {

    private val json = """{ "presupuestos": [{
      "id": "p-1", "titulo": "Plan facial", "moneda": "PEN",
      "items": [{ "clave": "l_ab12", "nombre": "Toxina botulínica", "detalle": "Tercio superior",
                  "modo": "unidades", "unidad_label": "unidades", "cantidad": 50, "precio_unitario": 15, "precio_lista": 18 },
                { "clave": "l_paq", "nombre": "Láser CO2", "modo": "sesiones", "cobro": "paquete", "cantidad": 3,
                  "precio_unitario": 999, "precio_lista": 999, "precio_sesion": 380 }],
      "descuento_tipo": "porcentaje", "descuento_valor": 10,
      "subtotal": 1749, "descuento": 174.9, "total": 1574.1, "por_separado": 2040, "ahorro": 465.9,
      "notas_paciente": "Incluye controles.", "vigencia_hasta": "2026-10-31",
      "estado": "Enviado", "enviado_at": "2026-10-16T15:00:00Z", "aceptado_at": null, "aceptado_canal": null, "rechazado_at": null,
      "puede_responder": true, "updated_at": "2026-10-16T15:00:00.123456+00:00",
      "clinica": { "nombre": "Glam Center", "logo_url": "https://x/l.png", "color_principal": "#7c3aed", "direccion": "Av. Bolognesi 123" },
      "profesional": "Dr. Juan Villarroel", "paciente": { "nombre": "María Quispe" }
    }, { "titulo": "sin id" }, { "id": "p-venc", "titulo": "Viejo", "estado": "Vencido", "puede_responder": false, "items": [] }] }"""

    @Test fun parseaLoQueManda() {
        val l = parsearMisPresupuestos(json)!!
        assertEquals(listOf("p-1", "p-venc"), l.map { it.id })
        val p = l[0]
        assertEquals(1574.1, p.total); assertEquals(465.9, p.ahorro); assertTrue(p.puedeResponder)
        assertEquals("2026-10-16T15:00:00.123456+00:00", p.updatedAt)
        assertEquals("paquete", p.items[1].cobro); assertEquals(380.0, p.items[1].precioSesion)
        assertEquals("Glam Center", p.clinica?.nombre); assertEquals("María Quispe", p.pacienteNombre)
        assertFalse(l[1].puedeResponder); assertEquals("Vencido", l[1].estado)
        assertNull(parsearMisPresupuestos("no es json"))
    }

    @Test fun laHojaDelPortalNoLlevaNotasInternasYUsaSuMoneda() {
        val h = htmlMiPresupuesto(parsearMisPresupuestos(json)!![0])
        assertTrue(h.contains("Glam Center"))
        assertTrue(h.contains("Paquete de 3 sesiones"))
        assertTrue(h.contains("S/ 1,574.10"))
    }
}
