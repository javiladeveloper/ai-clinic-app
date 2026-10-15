package pe.saniape.app.data

import pe.saniape.app.data.staff.MetricaMedicion
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Parser de /api/paciente/mi-nutricion (contrato: docs/app-contrato-portal-paciente.md
 * §4 de la web) y la presentación pura del portal: tolerar números como texto,
 * nulls y comidas mal formadas sin romper la pantalla Salud; y una hoja / PDF
 * que escapa todo lo que escribió la clínica.
 */
class MiNutricionPortalTest {
    private val ejemplo = """
        {
          "planes": [
            {
              "id": "p-viejo", "fecha": "2026-09-01", "objetivo": "Ordenar horarios",
              "kcal": null, "proteinas_pct": null, "carbohidratos_pct": null, "grasas_pct": null,
              "comidas": [ { "tiempo": "Desayuno", "hora": "7:30", "detalle": "Avena" } ],
              "recomendaciones": null, "evitar": "", "hidratacion": null,
              "created_at": "2026-09-01T10:00:00.000Z", "clinica": null, "profesional": null, "paciente": null
            },
            {
              "id": "p-nuevo", "fecha": "2026-10-14",
              "objetivo": "Bajar de peso, controlar la glucosa",
              "kcal": "1600", "proteinas_pct": "25", "carbohidratos_pct": 50, "grasas_pct": "25.0",
              "comidas": [
                { "tiempo": "Desayuno", "hora": "07:30", "detalle": "Avena con fruta\nHuevo sancochado" },
                { "tiempo": "Almuerzo", "hora": null, "detalle": "½ plato de verduras" },
                { "tiempo": "Media tarde", "hora": "25:00", "detalle": "  " },
                "basura", 42, null,
                { "tiempo": "", "hora": "16:00", "detalle": "Fruta" },
                { "detalle": { "raro": true } }
              ],
              "recomendaciones": "Comer cada 3 a 4 horas", "evitar": "Gaseosas", "hidratacion": "2 litros de agua al día",
              "created_at": "2026-10-14T15:02:11.000Z",
              "clinica": { "nombre": "NutriVida", "logo_url": "https://x.supabase.co/logo.png", "color_principal": "#0f766e" },
              "profesional": "Lic. Paola Ríos",
              "paciente": { "nombre": "Ana Torres" }
            },
            { "fecha": "2026-10-15", "objetivo": "sin id" },
            "nada"
          ],
          "mediciones": [
            { "id": "m2", "fecha": "2026-10-01", "peso": "71", "talla": "160", "cintura": 86, "cadera": null,
              "perimetro_abdominal": null, "grasa_pct": "30.5", "masa_muscular": null, "imc": "27.7" },
            { "id": "m1", "fecha": "2026-09-01", "peso": 72.5, "talla": 160, "cintura": 88, "cadera": null,
              "perimetro_abdominal": null, "grasa_pct": 31, "masa_muscular": null, "imc": 28.3 },
            { "id": "m3", "fecha": "2026-10-14", "peso": null, "talla": null, "cintura": "85", "imc": null },
            { "fecha": "2026-10-20", "peso": 70 }
          ]
        }
    """.trimIndent()

    @Test fun leeElContratoYOrdena() {
        val d = parsearMiNutricion(ejemplo)!!
        assertTrue(d.tieneDatos)
        // Sin id se descarta; el más reciente primero aunque llegue desordenado.
        assertEquals(listOf("p-nuevo", "p-viejo"), d.planes.map { it.id })
        val v = d.planes.first()
        assertEquals(1600, v.plan.kcal)
        assertEquals(25.0, v.plan.proteinasPct)
        assertEquals(50.0, v.plan.carbohidratosPct)
        assertEquals(25.0, v.plan.grasasPct)
        assertEquals("NutriVida", v.clinica?.nombre)
        assertEquals("#0f766e", v.clinica?.colorPrincipal)
        assertEquals("Lic. Paola Ríos", v.profesional)
        assertEquals("Ana Torres", v.pacienteNombre)
        assertNull(d.planes[1].clinica)
        assertNull(d.planes[1].plan.evitar)
        // Mediciones cronológicas, sin la que no trae id.
        assertEquals(listOf("m1", "m2", "m3"), d.mediciones.map { it.medicion.id })
        assertEquals(71.0, d.mediciones[1].medicion.peso)
        assertEquals(27.7, d.mediciones[1].imc)
    }

    @Test fun comidasMalFormadasSeDescartan() {
        val comidas = parsearMiNutricion(ejemplo)!!.planes.first().plan.comidas
        // Vacías, no-objetos y detalle no-texto fuera; hora inválida → null; tiempo vacío → "Comida".
        assertEquals(listOf("Desayuno", "Almuerzo", "Comida"), comidas.map { it.tiempo })
        assertEquals("07:30", comidas[0].hora)
        assertNull(comidas[1].hora)
        assertEquals("16:00", comidas[2].hora)
        assertEquals("Avena con fruta\nHuevo sancochado", comidas[0].detalle)
        // "7:30" no es HH:MM: se descarta la hora, no la comida.
        assertNull(parsearMiNutricion(ejemplo)!!.planes[1].plan.comidas.single().hora)
    }

    @Test fun respuestasRaras() {
        assertNull(parsearMiNutricion("no es json"))
        assertNull(parsearMiNutricion("[]"))
        val vacia = parsearMiNutricion("""{ "planes": [], "mediciones": [] }""")!!
        assertFalse(vacia.tieneDatos)
        // Campos ausentes o con otro tipo: sin datos, sin romper.
        assertFalse(parsearMiNutricion("""{ "planes": {}, "mediciones": "x" }""")!!.tieneDatos)
        assertFalse(parsearMiNutricion("{}")!!.tieneDatos)
        assertFalse(MiNutricion.VACIA.tieneDatos)
        // Solo mediciones también cuenta como datos (la sección muestra la evolución).
        assertTrue(parsearMiNutricion("""{ "mediciones": [ { "id": "a", "fecha": "2026-01-01", "peso": "60" } ] }""")!!.tieneDatos)
    }

    @Test fun imcDelServidorORecalculado() {
        val d = parsearMiNutricion(
            """{ "mediciones": [ { "id": "a", "fecha": "2026-01-01", "peso": 64, "talla": 160, "imc": null },
                                  { "id": "b", "fecha": "2026-02-01", "peso": 64, "talla": null } ] }""",
        )!!
        assertEquals(25.0, d.mediciones[0].imc)
        assertNull(d.mediciones[1].imc)
    }

    @Test fun ultimaMedicionYGrafico() {
        val d = parsearMiNutricion(ejemplo)!!
        // La última (m3) solo midió la cintura.
        assertEquals(listOf(DatoMedicionPortal("Cintura", "85 cm")), datosUltimaMedicion(d.mediciones))
        // Sin la m3: peso con el cambio desde la primera toma, IMC, cintura y % grasa.
        val dos = d.mediciones.take(2)
        assertEquals(
            listOf(
                DatoMedicionPortal("Peso", "71 kg", "−1.5 kg desde 01/09/2026"),
                DatoMedicionPortal("IMC", "27.7"),
                DatoMedicionPortal("Cintura", "86 cm"),
                DatoMedicionPortal("% grasa", "30.5 %"),
            ),
            datosUltimaMedicion(dos),
        )
        // Una sola toma: sin "desde".
        assertNull(datosUltimaMedicion(d.mediciones.take(1)).first().detalle)
        assertTrue(datosUltimaMedicion(emptyList()).isEmpty())

        assertEquals(listOf(72.5, 71.0), serieNutricionPortal(d.mediciones, MetricaMedicion.PESO).map { it.valor })
        assertEquals(listOf(28.3, 27.7), serieNutricionPortal(d.mediciones, MetricaMedicion.IMC).map { it.valor })
        assertEquals(listOf(MetricaMedicion.PESO, MetricaMedicion.IMC), metricasGraficoPortal(d.mediciones))
        // Con una sola toma no hay curva.
        assertTrue(metricasGraficoPortal(d.mediciones.take(1)).isEmpty())
    }

    @Test fun hojaDelPlanEscapaYValida() {
        val d = parsearMiNutricion(ejemplo)!!
        val html = htmlPlanPortal(d.planes.first())
        assertTrue("PLAN ALIMENTARIO" in html)
        assertTrue("NutriVida" in html)
        assertTrue("Ana Torres" in html)
        assertTrue("14/10/2026" in html)
        assertTrue("#0f766e" in html)
        assertTrue("https://x.supabase.co/logo.png" in html)
        assertTrue("Lic. Paola Ríos" in html)
        assertTrue("1600" in html)
        assertTrue("7:30 a. m." in html)

        val malicioso = parsearMiNutricion(
            """{ "planes": [ { "id": "x", "fecha": "2026-10-14",
                 "objetivo": "<script>alert(1)</script>",
                 "comidas": [ { "tiempo": "<b>Cena</b>", "hora": null, "detalle": "\"pan\" & 'té'" } ],
                 "clinica": { "nombre": "<img src=x onerror=alert(1)>", "logo_url": "http://inseguro/logo.png",
                              "color_principal": "red;background:url(x)" },
                 "profesional": "<i>Dr</i>", "paciente": { "nombre": "</title><script>" } } ] }""",
        )!!
        val h = htmlPlanPortal(malicioso.planes.single())
        assertFalse("<script>" in h)
        assertFalse("<img src=x" in h)
        assertFalse("<b>Cena</b>" in h)
        assertFalse("<i>Dr</i>" in h)
        assertTrue("&lt;script&gt;alert(1)&lt;/script&gt;" in h)
        assertTrue("&quot;pan&quot; &amp; &#39;té&#39;" in h)
        // Logo que no es https: fuera. Color que no es #rrggbb: el navy de Sania.
        assertFalse("http://inseguro" in h)
        assertFalse("red;background" in h)
        assertTrue("#2c3e7a" in h)
    }

    @Test fun planSinClinicaNiPacienteIgualArmaLaHoja() {
        val p = parsearMiNutricion(ejemplo)!!.planes[1]
        val h = htmlPlanPortal(p)
        assertNotNull(h)
        assertTrue("<b>Paciente:</b> —" in h)
        assertTrue("#2c3e7a" in h)
    }
}
