package pe.saniape.app.data

import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Parser de /api/paciente/mis-ejercicios (contrato: docs/app-contrato-portal-paciente.md §3
 * de la web). Lo importante: los textos y el "toca hoy / hecho hoy" se toman TAL CUAL
 * del servidor, y una respuesta rara no rompe el portal.
 */
class MisEjerciciosTest {
    private val ejemplo = """
        {
          "hoy": "2026-10-04",
          "diaSemana": 7,
          "planes": [{
            "id": "plan1", "titulo": "Ejercicios de apoyo",
            "momento": "sesion", "etiqueta": "Sesión #4 · 24/06/26", "vigencia": "Hasta la próxima sesión",
            "motivo": "Lumbalgia", "indicaciones": "Por la mañana…", "precauciones": null,
            "fechaInicio": "2026-06-24", "clinica": "Clinica Test", "indicadoPor": "Lic. Ana",
            "ejercicios": [{
              "itemId": "i1", "nombre": "Activación del transverso",
              "gifUrl": "https://x/animacion.gif", "posturaInicialUrl": "https://x/inicio.png", "posturaFinalUrl": "https://x/fin.png",
              "pasos": ["Uno", "Dos", "Tres"], "materiales": ["colchoneta"],
              "indicaciones": "Hazlo despacio…",
              "series": 2, "repeticiones": 12, "sostenerSeg": null, "descansoSeg": 20, "vecesAlDia": 1,
              "dias": [1, 3, 5], "lado": null, "carga": "Sin peso", "dolorMaximo": 4,
              "dosisTexto": "2 series × 12 repeticiones", "diasTexto": "Lun · Mié · Vie",
              "tocaHoy": false, "hechoHoy": false, "dolorHoy": null
            }, {
              "itemId": "i2", "nombre": "Gato-camello", "gifUrl": null, "posturaInicialUrl": null, "posturaFinalUrl": null,
              "pasos": [], "materiales": [], "indicaciones": null,
              "series": 2, "repeticiones": null, "sostenerSeg": 20, "descansoSeg": null, "vecesAlDia": 2,
              "dias": [1, 2, 3, 4, 5, 6, 7], "lado": "ambos", "carga": null, "dolorMaximo": null,
              "dosisTexto": "2 series × 20 s sostenido", "diasTexto": "Todos los días",
              "tocaHoy": true, "hechoHoy": true, "dolorHoy": 3
            }, { "nombre": "Sin itemId" }]
          },
          { "id": "vacio", "etiqueta": "Sesión #1", "vigencia": "Hasta la próxima sesión", "ejercicios": [] },
          { "etiqueta": "Sin id", "ejercicios": [ { "itemId": "x", "nombre": "X" } ] }]
        }
    """.trimIndent()

    @Test fun leeElContratoCompleto() {
        val d = parsearMisEjercicios(ejemplo)!!
        assertEquals("2026-10-04", d.hoy)
        assertEquals(7, d.diaSemana)
        // El plan sin ejercicios y el que no tiene id se descartan.
        assertEquals(1, d.planes.size)
        val p = d.planes[0]
        assertEquals("Sesión #4 · 24/06/26", p.etiqueta)
        assertEquals("Hasta la próxima sesión", p.vigencia)
        assertEquals("Lic. Ana", p.indicadoPor)
        assertEquals("Clinica Test", p.clinica)
        assertNull(p.precauciones)
        // El ejercicio sin itemId se descarta: no se podría marcar como hecho.
        assertEquals(listOf("i1", "i2"), p.ejercicios.map { it.itemId })
    }

    @Test fun losTextosYElDiaVienenDelServidorTalCual() {
        val (a, b) = parsearMisEjercicios(ejemplo)!!.planes[0].ejercicios
        assertEquals("2 series × 12 repeticiones", a.dosisTexto)
        assertEquals("Lun · Mié · Vie", a.diasTexto)
        assertEquals(listOf(1, 3, 5), a.dias)
        assertEquals(4, a.dolorMaximo)
        assertEquals(20, a.descansoSeg)
        assertEquals(listOf("colchoneta"), a.materiales)
        assertFalse(a.tocaHoy); assertFalse(a.hechoHoy); assertNull(a.dolorHoy)
        assertEquals("2 series × 20 s sostenido", b.dosisTexto)
        assertEquals("Todos los días", b.diasTexto)
        assertTrue(b.tocaHoy); assertTrue(b.hechoHoy); assertEquals(3, b.dolorHoy)
        assertNull(b.gifUrl); assertNull(b.posturaInicialUrl)
        assertEquals("ambos", b.lado); assertEquals(2, b.vecesAlDia)
    }

    @Test fun sinPlanesLaSeccionNoAparece() {
        val d = parsearMisEjercicios("""{ "planes": [], "hoy": "2026-10-04", "diaSemana": 7 }""")!!
        assertTrue(d.planes.isEmpty())
        // Un servidor que no manda `planes` tampoco rompe.
        assertTrue(parsearMisEjercicios("""{}""")!!.planes.isEmpty())
        assertEquals(0, parsearMisEjercicios("""{}""")!!.diaSemana)
    }

    @Test fun respuestaQueNoEsJsonNoRompe() {
        assertNull(parsearMisEjercicios("<html>404</html>"))
    }

    @Test fun marcarYDeshacerEnLaCopiaLocal() {
        val plan = parsearMisEjercicios(ejemplo)!!.planes[0]
        val hecho = conEjercicioMarcado(plan, "i1", hecho = true, dolor = 2)
        assertTrue(hecho.ejercicios[0].hechoHoy); assertEquals(2, hecho.ejercicios[0].dolorHoy)
        // El otro ejercicio no cambia.
        assertEquals(plan.ejercicios[1], hecho.ejercicios[1])
        val deshecho = conEjercicioMarcado(hecho, "i1", hecho = false, dolor = 2)
        assertFalse(deshecho.ejercicios[0].hechoHoy); assertNull(deshecho.ejercicios[0].dolorHoy)
    }

    @Test fun elCuerpoDeMarcarSoloLlevaLoDelContrato() {
        val c = cuerpoMarcarEjercicio("i1", hecho = true, dolor = 2)
        assertEquals(setOf("itemId", "hecho", "dolor"), c.keys)
        assertEquals(JsonPrimitive("i1"), c["itemId"])
        assertEquals(JsonPrimitive(true), c["hecho"])
        assertEquals(JsonPrimitive(2), c["dolor"])
        assertEquals(JsonNull, cuerpoMarcarEjercicio("i1", hecho = true, dolor = null)["dolor"])
        // Al deshacer no viaja dolor.
        val d = cuerpoMarcarEjercicio("i1", hecho = false, dolor = 5)
        assertEquals(JsonPrimitive(false), d["hecho"]); assertEquals(JsonNull, d["dolor"])
    }
}
