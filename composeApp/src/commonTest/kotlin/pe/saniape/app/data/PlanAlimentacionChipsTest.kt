package pe.saniape.app.data

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import pe.saniape.app.data.staff.APRENDIDOS_EN_CHIPS
import pe.saniape.app.data.staff.AprendidoPlan
import pe.saniape.app.data.staff.CAMPOS_TEXTO_PLAN
import pe.saniape.app.data.staff.CHIPS_COMIDAS
import pe.saniape.app.data.staff.CHIPS_COMIDA_GENERAL
import pe.saniape.app.data.staff.CHIPS_PLAN
import pe.saniape.app.data.staff.TIEMPOS_COMIDA
import pe.saniape.app.data.staff.aprendidosDeComida
import pe.saniape.app.data.staff.chipsBaseComida
import pe.saniape.app.data.staff.claveTiempoComida
import pe.saniape.app.data.staff.frasesCampoPlan
import pe.saniape.app.data.staff.frasesCampoTextoPlan
import pe.saniape.app.data.staff.frasesComidaPlan
import pe.saniape.app.data.staff.sugerenciasPlanDeJson
import pe.saniape.app.ui.clinica.atencion.completarFragmento
import pe.saniape.app.ui.clinica.atencion.fragmentoEnCurso
import pe.saniape.app.ui.clinica.atencion.fraseUsada
import pe.saniape.app.ui.clinica.atencion.insertarFrase
import pe.saniape.app.ui.clinica.atencion.normalizarFrase
import pe.saniape.app.ui.clinica.atencion.quitarFrase
import pe.saniape.app.ui.clinica.atencion.sugerirPara
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertSame
import kotlin.test.assertTrue

/** Gemelo de lib/__tests__/plan-alimentacion-chips.test.ts (+ el agregar/quitar de frases-clinicas). */
class PlanAlimentacionChipsTest {

    private fun ap(vararg x: Pair<String, Int>) = x.map { AprendidoPlan(it.first, it.second) }

    // ── Listas base: las MISMAS que la web ──

    @Test fun listasBaseIgualesALaWeb() {
        assertEquals(listOf("objetivo", "evitar", "recomendaciones", "hidratacion"), CAMPOS_TEXTO_PLAN)
        assertEquals(
            listOf(
                "Bajar de peso", "Mantener el peso", "Ganar masa muscular", "Controlar la glucosa",
                "Bajar colesterol y triglicéridos", "Controlar la presión arterial", "Alimentación en el embarazo",
                "Mejorar la digestión", "Subir de peso", "Comer más saludable", "Mejorar el rendimiento deportivo",
            ),
            CHIPS_PLAN["objetivo"],
        )
        assertEquals(12, CHIPS_PLAN["evitar"]!!.size)
        assertEquals("Gaseosas y jugos azucarados", CHIPS_PLAN["evitar"]!!.first())
        assertEquals("Bebidas energizantes", CHIPS_PLAN["evitar"]!!.last())
        assertEquals(12, CHIPS_PLAN["recomendaciones"]!!.size)
        assertEquals("Llevar su lonchera saludable", CHIPS_PLAN["recomendaciones"]!!.last())
        assertEquals(7, CHIPS_PLAN["hidratacion"]!!.size)
        assertEquals("Agua en vez de gaseosa", CHIPS_PLAN["hidratacion"]!!.last())
        assertEquals(TIEMPOS_COMIDA, CHIPS_COMIDAS.keys.toList())
        assertEquals(listOf(11, 8, 11, 7, 8), TIEMPOS_COMIDA.map { CHIPS_COMIDAS[it]!!.size })
        assertEquals("¼ de proteína (pollo, pescado o menestra)", CHIPS_COMIDAS["Almuerzo"]!![1])
        assertEquals("Cena ligera", CHIPS_COMIDAS["Cena"]!!.last())
        assertEquals(8, CHIPS_COMIDA_GENERAL.size)
    }

    @Test fun cadaCampoConChipsSinRepetirYCortos() {
        for (k in CAMPOS_TEXTO_PLAN) {
            val l = CHIPS_PLAN[k]!!
            assertTrue(l.size >= 6, k)
            assertEquals(l.size, l.map(::normalizarFrase).toSet().size, k)
        }
        for (t in TIEMPOS_COMIDA) {
            val l = CHIPS_COMIDAS[t]!!
            assertTrue(l.size >= 6, t)
            assertEquals(l.size, l.map(::normalizarFrase).toSet().size, t)
        }
        val todos = CAMPOS_TEXTO_PLAN.flatMap { CHIPS_PLAN[it]!! } + CHIPS_COMIDAS.values.flatten() + CHIPS_COMIDA_GENERAL
        // Sin comas fuera de paréntesis (al trocear volverían partidas) y cortos.
        for (ch in todos) {
            assertTrue(ch.length <= 45, ch)
            assertFalse(ch.replace(Regex("""\([^)]*\)"""), "").contains(','), ch)
        }
    }

    @Test fun tiempoDeComida() {
        assertEquals("Media mañana", claveTiempoComida("  media manana "))
        assertEquals("Almuerzo", claveTiempoComida("ALMUERZO"))
        assertEquals("Pre entreno", claveTiempoComida("Pre  entreno"))
        assertSame(CHIPS_COMIDAS["Desayuno"], chipsBaseComida("desayuno"))
        assertSame(CHIPS_COMIDA_GENERAL, chipsBaseComida("Merienda"))
        assertSame(CHIPS_COMIDA_GENERAL, chipsBaseComida(null))
    }

    // ── Base + aprendido ──

    private val base = listOf("Frituras", "Embutidos", "Alcohol")

    @Test fun autocompletadoAprendidoPrimeroSinRepetir() {
        val f = frasesCampoPlan(base, ap("Alcohol" to 3, "Chizitos" to 1, "FRITURAS" to 1), porLinea = false)
        assertEquals(listOf("Alcohol", "Chizitos", "FRITURAS", "Embutidos"), f.opciones)
        assertFalse(f.porLinea)
    }

    @Test fun chipsBaseMasUsadaPrimeroLuegoAprendidosCortos() {
        val f = frasesCampoPlan(
            base,
            ap("alcohol" to 4, "Chizitos" to 2, "Una frase aprendida demasiado larga para ser un chip de verdad" to 5),
            porLinea = false,
        )
        assertEquals(listOf("Alcohol", "Chizitos", "Frituras", "Embutidos"), f.chips.map { it.texto })
        assertTrue(f.chips.first { it.texto == "Chizitos" }.aprendida)
        assertFalse(f.chips.first { it.texto == "Alcohol" }.aprendida)
    }

    @Test fun maximoDeAprendidosComoChip() {
        val f = frasesCampoPlan(base, ap("a1 x" to 2, "a2 x" to 2, "a3 x" to 2, "a4 x" to 2, "a5 x" to 2, "a6 x" to 2), porLinea = true)
        assertEquals(APRENDIDOS_EN_CHIPS, f.chips.count { it.aprendida })
        assertEquals(9, f.opciones.size)
    }

    @Test fun usadaUnaSolaVezNuncaEsChip() {
        val f = frasesCampoPlan(base, ap("Chizitos" to 1, "Cuñapé" to 2), porLinea = false)
        assertTrue("Chizitos" in f.opciones)
        assertFalse(f.chips.any { it.texto == "Chizitos" })
        assertTrue(f.chips.any { it.texto == "Cuñapé" })
    }

    @Test fun sinAprendidosLaBaseTalCual() {
        val f = frasesCampoPlan(base, null, porLinea = false)
        assertEquals(base, f.chips.map { it.texto })
        assertEquals(base, f.opciones)
    }

    @Test fun comidasPorLineaYTextosConComa() {
        assertTrue(frasesComidaPlan("Desayuno", null).porLinea)
        assertFalse(frasesCampoTextoPlan("evitar", null).porLinea)
        assertEquals(CHIPS_PLAN["evitar"], frasesCampoTextoPlan("evitar", null).chips.map { it.texto })
    }

    // ── JSON del servidor (tolerante) ──

    @Test fun sugerenciasConUsosYRespuestaVieja() {
        val o = Json.parseToJsonElement(
            """{"objetivo":[{"texto":"Bajar de peso","usos":5},{"texto":"Chizitos","usos":1}],
               "evitar":["Gaseosas", 3, null, {"usos":2}],
               "recomendaciones":"no es lista",
               "comidas":{"Desayuno":[{"texto":"Avena con fruta","usos":2}],"Pre entreno":["Plátano"],"Cena":[]}}""",
        ).jsonObject
        val s = sugerenciasPlanDeJson(o)
        assertEquals(listOf(AprendidoPlan("Bajar de peso", 5), AprendidoPlan("Chizitos", 1)), s.objetivo)
        // Un servidor anterior (solo textos): 1 uso → autocompletado, nunca chip.
        assertEquals(listOf(AprendidoPlan("Gaseosas", 1)), s.evitar)
        assertTrue(s.recomendaciones.isEmpty())
        assertTrue(s.hidratacion.isEmpty())
        assertEquals(setOf("Desayuno", "Pre entreno"), s.comidas.keys)
        assertEquals(listOf(AprendidoPlan("Avena con fruta", 2)), aprendidosDeComida(s, "desayuno"))
        assertEquals(listOf(AprendidoPlan("Plátano", 1)), aprendidosDeComida(s, "PRE ENTRENO"))
        assertTrue(aprendidosDeComida(s, "Almuerzo").isEmpty())
        assertTrue(aprendidosDeComida(null, "Almuerzo").isEmpty())
        val chips = frasesCampoTextoPlan("evitar", s).chips.map { it.texto }
        assertFalse("Gaseosas" in chips)
        assertTrue("Gaseosas" in frasesCampoTextoPlan("evitar", s).opciones)
        assertTrue(sugerenciasPlanDeJson(null).objetivo.isEmpty())
    }

    // ── Agregar / quitar un chip y el autocompletado (gemelos de frases-clinicas) ──

    @Test fun agregarYQuitarConComa() {
        var t = insertarFrase("", "Frituras", porLinea = false)
        assertEquals("Frituras", t)
        t = insertarFrase(t, "Embutidos", porLinea = false)
        assertEquals("Frituras, embutidos", t)
        assertTrue(fraseUsada(t, "Embutidos"))
        // Ya está (sin tildes ni mayúsculas): no se repite.
        assertEquals("Frituras, embutidos", insertarFrase(t, "EMBUTIDOS", porLinea = false))
        assertEquals("Frituras", quitarFrase(t, "Embutidos", porLinea = false))
        assertEquals("Embutidos", quitarFrase(t, "Frituras", porLinea = false))
        assertEquals("Frituras, gaseosas", quitarFrase("Frituras, embutidos, gaseosas", "Embutidos", porLinea = false))
    }

    @Test fun agregarYQuitarPorLinea() {
        var t = insertarFrase("", "Avena con fruta", porLinea = true)
        t = insertarFrase(t, "Huevo sancochado", porLinea = true)
        assertEquals("Avena con fruta\nHuevo sancochado", t)
        assertEquals("Huevo sancochado", quitarFrase(t, "Avena con fruta", porLinea = true))
        assertEquals("A\nC", quitarFrase("A\nHuevo sancochado\nC", "Huevo sancochado", porLinea = true))
        // Editado a mano: no se toca.
        assertEquals("2 huevos", quitarFrase("2 huevos", "Huevo sancochado", porLinea = true))
    }

    @Test fun autocompletadoDelFragmentoEnCurso() {
        assertEquals("emb", fragmentoEnCurso("Frituras, emb", porLinea = false))
        assertEquals("Hue", fragmentoEnCurso("Avena con fruta\nHue", porLinea = true))
        assertEquals("Abdo", fragmentoEnCurso("Paciente lúcido. Abdo", porLinea = true))
        assertEquals("Frituras, embutidos", completarFragmento("Frituras, emb", "Embutidos", porLinea = false))
        assertEquals("Avena con fruta\nHuevo sancochado", completarFragmento("Avena con fruta\nHue", "Huevo sancochado", porLinea = true))
        assertEquals(listOf("Congestión nasal", "Orofaringe congestiva"),
            sugerirPara("conge", listOf("Congestión nasal", "congestion nasal", "Orofaringe congestiva", "Tos")))
        assertTrue(sugerirPara("c", listOf("Congestión nasal")).isEmpty())
    }
}
