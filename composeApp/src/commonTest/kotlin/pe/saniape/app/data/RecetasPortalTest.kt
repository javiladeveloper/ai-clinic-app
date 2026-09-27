package pe.saniape.app.data

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Parser de /api/paciente/recetas (contrato: docs/receta-medica-normativa.md §7
 * de la web). Lo importante: tolerar respuestas raras sin romper el portal y
 * recalcular la vigencia con la fecha de hoy (la caché puede tener días).
 */
class RecetasPortalTest {
    private val ejemplo = """
        {
          "aviso": "Copia informativa. Presenta la receta impresa.",
          "recetas": [
            {
              "id": "r1", "numero": 12, "numeroTexto": "N° 000012",
              "fecha": "2026-09-27", "validaHasta": "2026-10-27", "vigente": true,
              "diagnostico": "Faringitis aguda", "cie10": "J02.9",
              "indicacionesGenerales": null, "clinica": "Clínica X", "clinicaSlug": "x", "clinicaLogo": null,
              "clinicaDireccion": "Av. Bolognesi 123", "clinicaTelefono": null,
              "prescriptor": { "nombre": "Dr. Pérez", "colegiatura": "CMP 45678", "profesion": "Médico Cirujano", "especialidad": null },
              "items": [
                { "dci": "Amoxicilina", "marca": null, "concentracion": "500 mg", "forma": "Tableta",
                  "via": "Oral", "dosis": "1 tableta", "frecuencia": "cada 8 horas", "duracion": "7 días",
                  "cantidad": 21, "unidad": "tabletas", "cantidadTexto": "21 (veintiuno) tabletas",
                  "indicaciones": "Después de comer",
                  "indicacionTexto": "1 tableta vía oral cada 8 horas por 7 días. Después de comer" }
              ]
            },
            { "id": "r2", "fecha": "2026-01-01", "validaHasta": "2026-01-31", "items": [] },
            { "fecha": "2026-01-01", "validaHasta": "2026-01-31", "items": [ { "dci": "X" } ] }
          ]
        }
    """.trimIndent()

    @Test fun leeElContratoCompleto() {
        val d = parsearRecetas(ejemplo, hoy = "2026-09-27")!!
        assertEquals("Copia informativa. Presenta la receta impresa.", d.aviso)
        // r2 sin medicamentos y la tercera sin id se descartan.
        assertEquals(1, d.recetas.size)
        val r = d.recetas[0]
        assertEquals("N° 000012", r.numeroTexto)
        assertEquals("Dr. Pérez", r.prescriptor?.nombre)
        assertNull(r.prescriptor?.especialidad)
        assertNull(r.indicacionesGenerales)
        assertEquals("21 (veintiuno) tabletas", r.items[0].cantidadTexto)
        assertNull(r.items[0].marca)
        assertTrue(r.vigente)
        // Campos del establecimiento: opcionales (null o ausentes) sin romper.
        assertEquals("Av. Bolognesi 123", r.clinicaDireccion)
        assertNull(r.clinicaTelefono)
        assertNull(r.clinicaCiudad)
    }

    @Test fun laVigenciaSeRecalculaConLaFechaDeHoy() {
        // El servidor dijo vigente=true, pero la caché es de hace dos meses.
        assertTrue(parsearRecetas(ejemplo, hoy = "2026-10-27")!!.recetas[0].vigente)
        assertFalse(parsearRecetas(ejemplo, hoy = "2026-10-28")!!.recetas[0].vigente)
    }

    @Test fun sinAvisoUsaElDeLaApp() {
        val d = parsearRecetas("""{"recetas":[]}""", hoy = "2026-09-27")!!
        assertTrue(d.recetas.isEmpty())
        assertEquals(AVISO_RECETA_POR_DEFECTO, d.aviso)
    }

    @Test fun respuestaQueNoEsJsonNoRompe() {
        assertNull(parsearRecetas("<html>404</html>", hoy = "2026-09-27"))
    }
}
