package pe.saniape.app.data

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

/** GET /api/paciente/mis-ejercicios/hoja: qué hace la app con cada respuesta. */
class MisEjerciciosHojaTest {

    private val ok = """
        { "hoja": { "titulo": "Ejercicios de apoyo", "etiqueta": "Sesión #1", "vigencia": "Hasta la próxima sesión", "fecha": "2026-10-10",
            "minutosTotales": 3, "ejercicios": [ { "numero": 1, "nombre": "Puente", "dosis": "3 series × 10 repeticiones", "dias": [1], "vecesAlDia": 1, "minutos": 3 } ] },
          "clinica": null, "paciente": { "nombre": "Ana" } }
    """.trimIndent()

    @Test
    fun okSeParsea() {
        val r = resultadoHojaDe(200, ok)
        assertIs<ResultadoHojaEjercicios.Ok>(r)
        assertEquals("Ana", r.hoja.pacienteNombre)
        assertEquals(null, r.hoja.clinica)
    }

    @Test
    fun okIlegibleEsError() {
        assertIs<ResultadoHojaEjercicios.Error>(resultadoHojaDe(200, "<html>"))
    }

    @Test
    fun notFoundConJsonEsElTextoDelServidor() {
        val r = resultadoHojaDe(404, """{ "error": "No encontramos estos ejercicios." }""")
        assertIs<ResultadoHojaEjercicios.Error>(r)
        assertEquals("No encontramos estos ejercicios.", r.mensaje)
    }

    @Test
    fun notFoundSinJsonEsWebVieja() {
        assertIs<ResultadoHojaEjercicios.NoDisponible>(resultadoHojaDe(404, "<!doctype html><h1>404</h1>"))
        assertIs<ResultadoHojaEjercicios.NoDisponible>(resultadoHojaDe(404, null))
    }

    @Test
    fun otrosErrores() {
        val r = resultadoHojaDe(500, null)
        assertIs<ResultadoHojaEjercicios.Error>(r)
        assertEquals("No se pudo abrir la hoja. Inténtalo de nuevo.", r.mensaje)
    }
}
