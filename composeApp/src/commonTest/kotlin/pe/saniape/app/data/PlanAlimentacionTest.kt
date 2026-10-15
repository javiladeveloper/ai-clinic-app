package pe.saniape.app.data

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import pe.saniape.app.data.staff.BorradorPlan
import pe.saniape.app.data.staff.ComidaPlan
import pe.saniape.app.data.staff.ContenidoEditorPlan
import pe.saniape.app.data.staff.DatosCalculo
import pe.saniape.app.data.staff.NivelActividad
import pe.saniape.app.data.staff.ObjetivoPeso
import pe.saniape.app.data.staff.PlanAlimentacion
import pe.saniape.app.data.staff.PlantillaPlan
import pe.saniape.app.data.staff.Sexo
import pe.saniape.app.data.staff.TIEMPOS_COMIDA
import pe.saniape.app.data.staff.aJson
import pe.saniape.app.data.staff.borradorDesdeEditor
import pe.saniape.app.data.staff.calcularRequerimiento
import pe.saniape.app.data.staff.comidasPorDefecto
import pe.saniape.app.data.staff.contenidoDesde
import pe.saniape.app.data.staff.cuerpoPlan
import pe.saniape.app.data.staff.cuerpoPlantilla
import pe.saniape.app.data.staff.fechaPlan
import pe.saniape.app.data.staff.gramosMacros
import pe.saniape.app.data.staff.horaPlan
import pe.saniape.app.data.staff.htmlPlanAlimentario
import pe.saniape.app.data.staff.kcalParaUsar
import pe.saniape.app.data.staff.normalizarComidas
import pe.saniape.app.data.staff.ordenarPlanes
import pe.saniape.app.data.staff.planDeJson
import pe.saniape.app.data.staff.tasaMetabolicaBasal
import pe.saniape.app.data.staff.telefonoParaAvisos
import pe.saniape.app.data.staff.textoMacros
import pe.saniape.app.data.staff.textoPlanWhatsApp
import pe.saniape.app.data.staff.usarCalculoEnEditor
import pe.saniape.app.data.staff.validarDatosCalculo
import pe.saniape.app.data.staff.validarPlan
import pe.saniape.app.data.staff.validarPlantilla

/**
 * Pestaña 🥗 Plan alimentario (nutrición). Paridad con la web: mismas entradas y
 * mismas salidas que `lib/__tests__/plan-alimentacion.test.ts` y
 * `lib/__tests__/requerimiento-energetico.test.ts`.
 */
class PlanAlimentacionTest {
    private val pac = "11111111-1111-4111-8111-111111111111"

    // ── Calculadora (requerimiento-energetico.test.ts) ──

    @Test fun tmbMifflinStJeor() {
        assertEquals(1320.25, tasaMetabolicaBasal(Sexo.MUJER, 60.0, 165.0, 30.0), 1e-9)
        assertEquals(1730.0, tasaMetabolicaBasal(Sexo.HOMBRE, 80.0, 180.0, 40.0), 1e-9)
    }

    @Test fun factoresDeActividad() {
        assertEquals(listOf(1.2, 1.375, 1.55, 1.725, 1.9), NivelActividad.entries.map { it.factor })
    }

    private fun calc(s: Sexo, peso: Double, talla: Double, edad: Double, a: NivelActividad, o: ObjetivoPeso) =
        calcularRequerimiento(DatosCalculo(s, edad, peso, talla, a, o))!!

    @Test fun mantenerRedondeaA10() {
        val r = calc(Sexo.MUJER, 60.0, 165.0, 30.0, NivelActividad.SEDENTARIO, ObjetivoPeso.MANTENER)
        assertEquals(1320, r.tmb)
        assertEquals(1580, r.get)
        assertEquals(1580, r.kcal)
        assertFalse(r.conPiso)
        assertEquals("mifflin_st_jeor", r.metodo)
    }

    @Test fun subirSuma300BajarResta500() {
        assertEquals(2350, calc(Sexo.MUJER, 60.0, 165.0, 30.0, NivelActividad.MODERADA, ObjetivoPeso.SUBIR).kcal)
        val h = calc(Sexo.HOMBRE, 80.0, 180.0, 40.0, NivelActividad.MODERADA, ObjetivoPeso.BAJAR)
        assertEquals(2680, h.get)
        assertEquals(-500, h.ajuste)
        assertEquals(2180, h.kcal)
    }

    @Test fun nuncaPorDebajoDelPiso() {
        val m = calc(Sexo.MUJER, 60.0, 165.0, 30.0, NivelActividad.SEDENTARIO, ObjetivoPeso.BAJAR)
        assertEquals(1200, m.kcal)
        assertTrue(m.conPiso)
        val h = calc(Sexo.HOMBRE, 50.0, 150.0, 70.0, NivelActividad.SEDENTARIO, ObjetivoPeso.BAJAR)
        assertEquals(1500, h.kcal)
        assertTrue(h.conPiso)
    }

    @Test fun fotoConLosDatosDeEntrada() {
        val r = calc(Sexo.HOMBRE, 80.0, 180.0, 40.0, NivelActividad.MUY_INTENSA, ObjetivoPeso.MANTENER)
        assertEquals(1.9, r.factor)
        assertEquals(3290, r.kcal)
        val j = r.aJson()
        assertEquals("hombre", j["sexo"]!!.jsonPrimitive.content)
        assertEquals("muy_intensa", j["actividad"]!!.jsonPrimitive.content)
        assertEquals("80", j["peso"]!!.jsonPrimitive.content)
        assertEquals("3290", j["kcal"]!!.jsonPrimitive.content)
        assertEquals("mifflin_st_jeor", j["metodo"]!!.jsonPrimitive.content)
    }

    @Test fun datosInvalidos() {
        assertEquals(6, validarDatosCalculo(DatosCalculo()).size)
        val e = validarDatosCalculo(DatosCalculo(Sexo.MUJER, 30.0, 60.0, 1.65, NivelActividad.LIGERA, ObjetivoPeso.BAJAR))
        assertEquals(listOf("Talla: entre 100 y 250 cm."), e)
        assertNull(calcularRequerimiento(DatosCalculo(Sexo.MUJER, 30.0, 0.0, 165.0, NivelActividad.LIGERA, ObjetivoPeso.BAJAR)))
    }

    @Test fun gramos() {
        val g = gramosMacros(2000.0, 25.0, 50.0, 25.0)
        assertEquals(listOf(125, 250, 56), listOf(g.proteinas, g.carbohidratos, g.grasas))
        val sin = gramosMacros(null, 25.0, 50.0, 25.0)
        assertEquals(listOf(null, null, null), listOf(sin.proteinas, sin.carbohidratos, sin.grasas))
        assertNull(gramosMacros(1800.0, 20.0, null, null).carbohidratos)
    }

    @Test fun usarCalculoAcotaALasKcalValidasYPone25_50_25() {
        // 250 kg, 2 m, muy intenso, subir → más de 6000: el campo queda en 6000.
        val alto = calc(Sexo.HOMBRE, 250.0, 200.0, 20.0, NivelActividad.MUY_INTENSA, ObjetivoPeso.SUBIR)
        assertTrue(alto.kcal > 6000)
        assertEquals(6000, kcalParaUsar(alto))
        val r = calc(Sexo.MUJER, 60.0, 165.0, 30.0, NivelActividad.SEDENTARIO, ObjetivoPeso.MANTENER)
        val c = usarCalculoEnEditor(ContenidoEditorPlan(), r)
        assertEquals(listOf("1580", "25", "50", "25"), listOf(c.kcal, c.prot, c.carb, c.grasa))
        // Con porcentajes ya escritos, no los pisa.
        val c2 = usarCalculoEnEditor(ContenidoEditorPlan(prot = "30", carb = "40", grasa = "30"), r)
        assertEquals(listOf("30", "40", "30"), listOf(c2.prot, c2.carb, c2.grasa))
    }

    // ── Comidas (plan-alimentacion.test.ts) ──

    @Test fun arrancaConLos5Tiempos() {
        assertEquals(listOf("Desayuno", "Media mañana", "Almuerzo", "Media tarde", "Cena"), TIEMPOS_COMIDA)
        assertEquals(TIEMPOS_COMIDA, comidasPorDefecto().map { it.tiempo })
        assertTrue(comidasPorDefecto().all { it.detalle == "" && it.hora == null })
    }

    @Test fun normalizarComidasDescartaVaciasYLimpiaHora() {
        assertEquals(
            listOf(ComidaPlan("Desayuno", "07:30", "Avena con fruta"), ComidaPlan("Comida", null, "Ensalada")),
            normalizarComidas(listOf(
                ComidaPlan("Desayuno", "07:30", " Avena con fruta "),
                ComidaPlan("Media mañana", null, "   "),
                ComidaPlan("", "25:00", "Ensalada"),
            )),
        )
    }

    // ── validarPlan ──

    @Test fun pideObjetivoOUnaComida() {
        assertEquals(listOf("Escribe el objetivo o al menos una comida."), validarPlan(BorradorPlan(pac, comidas = comidasPorDefecto())))
        assertEquals(emptyList(), validarPlan(BorradorPlan(pac, objetivo = "Bajar 4 kg")))
        assertEquals(emptyList(), validarPlan(BorradorPlan(pac, comidas = listOf(ComidaPlan("Cena", null, "Sopa")))))
    }

    @Test fun faltaPacienteOFechaInvalida() {
        assertTrue("Falta el paciente." in validarPlan(BorradorPlan("", objetivo = "x")))
        assertTrue("Fecha inválida." in validarPlan(BorradorPlan(pac, objetivo = "x", fecha = "14/10/2026")))
    }

    @Test fun kcalEnteraYEnRango() {
        assertEquals(emptyList(), validarPlan(BorradorPlan(pac, objetivo = "x", kcal = "1800")))
        assertEquals("Kcal: entre 500 y 6000.", validarPlan(BorradorPlan(pac, objetivo = "x", kcal = "300"))[0])
        assertEquals("Kcal: entre 500 y 6000.", validarPlan(BorradorPlan(pac, objetivo = "x", kcal = "7000"))[0])
        assertEquals("Kcal: escribe un número entero.", validarPlan(BorradorPlan(pac, objetivo = "x", kcal = "1800.5"))[0])
        assertEquals("Kcal: escribe un número entero.", validarPlan(BorradorPlan(pac, objetivo = "x", kcal = "mil"))[0])
    }

    @Test fun porcentajesLosTresOYSuman100() {
        fun v(p: String?, c: String?, g: String?) = validarPlan(BorradorPlan(pac, objetivo = "x", proteinasPct = p, carbohidratosPct = c, grasasPct = g))
        assertEquals(emptyList(), v("25", "50", "25"))
        assertEquals(emptyList(), v("33.3", "33.3", "33.3"))
        assertEquals("Los porcentajes suman 110 %: tienen que sumar 100 %.", v("30", "50", "30")[0])
        assertTrue(v("25", null, null)[0].contains("tres porcentajes"))
        assertEquals("Proteínas: entre 0 y 100 %.", v("120", "0", "0")[0])
    }

    @Test fun limitesDeTexto() {
        assertTrue(validarPlan(BorradorPlan(pac, objetivo = "a".repeat(501)))[0].contains("objetivo"))
        assertTrue(validarPlan(BorradorPlan(pac, objetivo = "x", evitar = "a".repeat(4001)))[0].contains("evitar"))
        val muchas = (0 until 13).map { ComidaPlan("C$it", null, "algo") }
        assertTrue(validarPlan(BorradorPlan(pac, comidas = muchas))[0].startsWith("Máximo 12"))
    }

    @Test fun plantillas() {
        assertEquals(listOf("Ponle un nombre a la plantilla."), validarPlantilla("", PlantillaPlan("x", "", objetivo = "x")))
        assertTrue(validarPlantilla("Hipocalórica 1500", PlantillaPlan("x", "")).first().contains("vacía"))
        assertEquals(emptyList(), validarPlantilla("Hipocalórica 1500", PlantillaPlan("x", "", objetivo = "Bajar", kcal = 1500)))
        val j = cuerpoPlantilla("  Diabético ", PlanAlimentacion("p", pac, "2026-10-14", objetivo = "Control glucémico"))
        assertEquals("Diabético", j["nombre"]!!.jsonPrimitive.content)
        assertNull(j["pacienteId"])
        assertNull(j["calculo"])
    }

    // ── Editor ──

    @Test fun partirDeUnPlanOEnBlanco() {
        val blanco = contenidoDesde(null)
        assertEquals(TIEMPOS_COMIDA, blanco.comidas.map { it.tiempo })
        assertEquals("", blanco.kcal)
        val previo = PlanAlimentacion(
            "a", pac, "2026-10-01", objetivo = "Bajar", kcal = 1800, proteinasPct = 25.0, carbohidratosPct = 50.0, grasasPct = 25.0,
            comidas = listOf(ComidaPlan("Desayuno", "07:00", "Avena"), ComidaPlan("Cena", null, "")),
            evitar = "Frituras",
        )
        val c = contenidoDesde(previo)
        assertEquals(listOf("Bajar", "1800", "25", "50", "25", "Frituras"), listOf(c.objetivo, c.kcal, c.prot, c.carb, c.grasa, c.evitar))
        assertEquals(listOf("Desayuno"), c.comidas.map { it.tiempo })
        // Claves distintas (la lista del editor las usa).
        assertEquals(c.comidas.size, c.comidas.map { it.k }.toSet().size)
        // Un plan sin comidas vuelve a los 5 tiempos.
        assertEquals(TIEMPOS_COMIDA, contenidoDesde(previo.copy(comidas = emptyList())).comidas.map { it.tiempo })
    }

    @Test fun cuerpoDelPostSiempreConFecha() {
        val c = contenidoDesde(null).let { it.copy(objetivo = " Bajar de peso ", kcal = "1800", prot = "25", carb = "50", grasa = "25,0") }
        val b = borradorDesdeEditor(pac, "2026-10-14", c, null)
        assertEquals(emptyList(), validarPlan(b))
        val j = cuerpoPlan(b)
        assertEquals(pac, j["pacienteId"]!!.jsonPrimitive.content)
        assertEquals("2026-10-14", j["fecha"]!!.jsonPrimitive.content)
        assertEquals("Bajar de peso", j["objetivo"]!!.jsonPrimitive.content)
        assertEquals("1800", j["kcal"]!!.jsonPrimitive.content)
        assertEquals(25.0, j["grasasPct"]!!.jsonPrimitive.content.toDouble())
        assertEquals(JsonNull, j["recomendaciones"])
        assertEquals(5, j["comidas"]!!.jsonArray.size)
        assertEquals(JsonNull, j["comidas"]!!.jsonArray[0].jsonObject["hora"])
        // PATCH: con id, sin pacienteId.
        val p = cuerpoPlan(b, id = "abc")
        assertEquals("abc", p["id"]!!.jsonPrimitive.content)
        assertNull(p["pacienteId"])
    }

    // ── JSON del API ──

    @Test fun planDeJsonToleraNumericComoTexto() {
        val o = Json.parseToJsonElement(
            """{"id":"p1","paciente_id":"$pac","fecha":"2026-10-14","kcal":1800,"proteinas_pct":"25.00","carbohidratos_pct":50,
               "grasas_pct":"25","comidas":[{"tiempo":"Desayuno","hora":"07:30","detalle":"Avena"}],"objetivo":null,
               "calculo":{"kcal":1800},"terapeuta_id":"t1","created_at":"2026-10-14T10:00:00Z"}""",
        ).jsonObject
        val p = planDeJson(o)!!
        assertEquals(1800, p.kcal)
        assertEquals(25.0, p.proteinasPct)
        assertNull(p.objetivo)
        assertEquals(listOf(ComidaPlan("Desayuno", "07:30", "Avena")), p.comidas)
        assertNotNull(p.calculo)
        assertNull(planDeJson(JsonObject(mapOf("fecha" to JsonPrimitive("2026-10-14")))))
    }

    // ── Presentación ──

    @Test fun ordenarPlanesElMasRecientePrimero() {
        val r = ordenarPlanes(listOf(
            PlanAlimentacion("a", pac, "2026-09-01", createdAt = "2026-09-01T10:00:00Z"),
            PlanAlimentacion("b", pac, "2026-10-01", createdAt = "2026-10-01T10:00:00Z"),
            PlanAlimentacion("c", pac, "2026-10-01", createdAt = "2026-10-01T12:00:00Z"),
        ))
        assertEquals(listOf("c", "b", "a"), r.map { it.id })
    }

    @Test fun fechaYHoraDeterministas() {
        assertEquals("04/10/2026", fechaPlan("2026-10-04"))
        assertEquals("7:30 a. m.", horaPlan("07:30"))
        assertEquals("12:00 p. m.", horaPlan("12:00"))
        assertEquals("12:15 a. m.", horaPlan("00:15"))
        assertNull(horaPlan(null))
    }

    @Test fun textoMacrosConGramos() {
        assertEquals(
            "Proteínas 25 % (125 g) · Carbohidratos 50 % (250 g) · Grasas 25 % (56 g)",
            textoMacros(PlanAlimentacion("x", pac, "2026-10-14", kcal = 2000, proteinasPct = 25.0, carbohidratosPct = 50.0, grasasPct = 25.0)),
        )
        assertNull(textoMacros(PlanAlimentacion("x", pac, "2026-10-14", kcal = 2000, carbohidratosPct = 50.0, grasasPct = 25.0)))
    }

    private val plan = PlanAlimentacion(
        id = "p", pacienteId = pac, fecha = "2026-10-14", objetivo = "Bajar 4 kg en 2 meses", kcal = 1800,
        proteinasPct = 25.0, carbohidratosPct = 50.0, grasasPct = 25.0,
        comidas = listOf(
            ComidaPlan("Desayuno", "07:30", "Avena con leche descremada\n1 plátano"),
            ComidaPlan("Media mañana", null, ""),
            ComidaPlan("Almuerzo", "13:00", "Pollo a la plancha con ensalada"),
        ),
        recomendaciones = "Comer despacio", evitar = "Gaseosas y frituras", hidratacion = "2 litros de agua al día",
    )

    @Test fun whatsappIdenticoALaWeb() {
        val t = textoPlanWhatsApp(plan, clinica = "NutriVida", paciente = "Ana María Torres", profesional = "Lic. Rosa Pérez")
        assertEquals(listOf(
            "🥗 *Plan alimentario* — NutriVida",
            "Hola Ana, este es tu plan del 14/10/2026.",
            "",
            "🎯 *Objetivo:* Bajar 4 kg en 2 meses",
            "",
            "🔥 *Energía:* 1800 kcal al día · Proteínas 25 % (113 g) · Carbohidratos 50 % (225 g) · Grasas 25 % (50 g)",
            "",
            "🍽️ *Comidas del día*",
            "",
            "*Desayuno* (7:30 a. m.)",
            "Avena con leche descremada\n1 plátano",
            "",
            "*Almuerzo* (1:00 p. m.)",
            "Pollo a la plancha con ensalada",
            "",
            "✅ *Recomendaciones*",
            "Comer despacio",
            "",
            "🚫 *Evitar*",
            "Gaseosas y frituras",
            "",
            "💧 *Hidratación*",
            "2 litros de agua al día",
            "",
            "— Lic. Rosa Pérez",
        ).joinToString("\n"), t)
    }

    @Test fun whatsappSinOpcionalesNoDejaSeccionesVacias() {
        val t = textoPlanWhatsApp(plan.copy(kcal = null, proteinasPct = null, recomendaciones = null, evitar = "", hidratacion = null, comidas = emptyList()))
        assertEquals("🥗 *Plan alimentario*\nEste es tu plan del 14/10/2026.\n\n🎯 *Objetivo:* Bajar 4 kg en 2 meses", t)
    }

    @Test fun hojaImprimibleEscapaYUsaElColorValido() {
        val h = htmlPlanAlimentario(
            plan.copy(objetivo = "<script>x</script>"), "Clínica \"A\"", null, "javascript:alert(1)", "red",
            "Ana", "12345678", "Lic. Rosa",
        )
        assertFalse(h.contains("<script>x"))
        assertTrue(h.contains("&lt;script&gt;x"))
        assertTrue(h.contains("#2c3e7a"))          // color no válido → navy
        assertFalse(h.contains("javascript:"))      // logo solo https
        assertTrue(h.contains("PLAN ALIMENTARIO"))
        assertTrue(h.contains("Lic. Rosa"))
        assertTrue(h.contains("7:30 a. m."))
        assertFalse(h.contains("Media mañana"))     // comida sin detalle no sale
    }

    @Test fun telefonoDelApoderadoSiRecibeAvisos() {
        assertEquals("999", telefonoParaAvisos("111", " 999 ", true))
        assertEquals("111", telefonoParaAvisos("111", "999", false))
        assertEquals("111", telefonoParaAvisos("111", "  ", true))
    }
}
