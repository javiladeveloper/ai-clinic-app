package pe.saniape.app.data

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import pe.saniape.app.data.staff.EspecialidadesRepo

/** Parseo de `GET /api/staff/especialidad/sugerencias` y el texto del resumen de `sembrar`. */
class EspecialidadesRepoParseoTest {

    private val ejemplo = """
{
  "ok": true,
  "especialidad": {"id": "e1", "nombre": "Fisioterapia", "rubro": "fisioterapia"},
  "servicios": [
    {"nombre": "Terapia manual", "categoria": "Fisioterapia", "descripcion": "Sesión", "precio": 60,
     "modo_cobro": "por_unidad", "unidad_label": "zona", "precio_unitario_sugerido": 20.5, "especialidad": "Fisioterapia"},
    {"nombre": "Consulta"}
  ],
  "tiposImagen": [{"nombre": "Rx columna", "contexto": "general", "orden": 1}],
  "tipicos": 3
}
"""

    private fun cuerpo(s: String) = Json.parseToJsonElement(s).jsonObject

    @Test
    fun parseaSugerenciasDelContrato() {
        val s = EspecialidadesRepo.parsearSugerencias(ejemplo)!!
        assertEquals(2, s.servicios.size)
        assertEquals(60.0, s.servicios[0].precio)
        assertEquals("zona", s.servicios[0].unidad_label)
        assertEquals(20.5, s.servicios[0].precio_unitario_sugerido)
        // Los campos ausentes toman el default.
        assertEquals("General", s.servicios[1].categoria)
        assertEquals(0.0, s.servicios[1].precio)
        assertNull(s.servicios[1].modo_cobro)
        assertEquals("Rx columna", s.tiposImagen.single().nombre)
        assertEquals(3, s.tipicos)
    }

    @Test
    fun sugerenciasInvalidasDanNull() {
        assertNull(EspecialidadesRepo.parsearSugerencias("no es json"))
    }

    @Test
    fun resumenConTodo() {
        val r = EspecialidadesRepo.resumenSembrado(
            cuerpo("""{"ok":true,"servicios":8,"tiposImagen":4,"tipicos":{"creados":3,"existentes":0,"vinculados":0,"plantillas":0}}"""),
        )
        assertEquals("Listo: 8 servicios, 4 tipos de imagen y 3 procedimientos con consentimiento", r)
    }

    @Test
    fun resumenSoloServicios() {
        assertEquals(
            "Listo: 2 servicios",
            EspecialidadesRepo.resumenSembrado(cuerpo("""{"ok":true,"servicios":2,"tiposImagen":0,"tipicos":null}""")),
        )
    }

    @Test
    fun resumenNada() {
        assertEquals(
            "Nada que cargar",
            EspecialidadesRepo.resumenSembrado(cuerpo("""{"ok":true,"servicios":0,"tiposImagen":0,"tipicos":{"creados":0}}""")),
        )
        assertEquals("Nada que cargar", EspecialidadesRepo.resumenSembrado(null))
    }

    @Test
    fun resumenSingulares() {
        assertEquals(
            "Listo: 1 servicio, 1 tipo de imagen y 1 procedimiento con consentimiento",
            EspecialidadesRepo.resumenSembrado(cuerpo("""{"servicios":1,"tiposImagen":1,"tipicos":{"creados":1}}""")),
        )
        assertEquals(
            "Listo: 1 servicio y 3 procedimientos con consentimiento",
            EspecialidadesRepo.resumenSembrado(cuerpo("""{"servicios":1,"tiposImagen":0,"tipicos":{"creados":3}}""")),
        )
    }
}
