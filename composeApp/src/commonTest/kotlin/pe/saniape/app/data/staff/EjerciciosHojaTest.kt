package pe.saniape.app.data.staff

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Hoja "Ejercicios para casa": gemela de lib/ejercicios-hoja.ts y HojaEjercicios.tsx. */
class EjerciciosHojaTest {

    private fun item(
        id: String, orden: Int, activo: Boolean = true, nombre: String = "Ej $id",
        inicio: String? = null, fin: String? = null, gif: String? = null,
        series: Int = 3, reps: Int? = 10, sostener: Int? = null, descanso: Int? = 30, lado: String? = null,
        dias: List<Int> = listOf(1, 3, 5), veces: Int = 1, dolor: Int? = null, materiales: List<String> = emptyList(),
    ) = ItemPlanEjercicios(
        id = id, ejercicioId = "e$id", ejercicioNombre = nombre, orden = orden, series = series, repeticiones = reps,
        sostenerSeg = sostener, descansoSeg = descanso, vecesAlDia = veces, dias = dias, lado = lado, dolorMaximo = dolor,
        activo = activo,
        ejercicio = EjercicioDeItem(id = "e$id", posturaInicialUrl = inicio, posturaFinalUrl = fin, gifUrl = gif,
            pasos = listOf("Paso 1"), materiales = materiales),
    )

    private fun plan(items: List<ItemPlanEjercicios>) = PlanEjercicios(
        id = "p1", momento = "sesion", sesion = SesionDePlan("s1", 8, "2026-10-10"), titulo = "Ejercicios de apoyo",
        motivo = "Lumbalgia", fechaInicio = "2026-10-10", creadoPorNombre = "Lic. Ana", items = items,
    )

    @Test
    fun segundosComoLaWeb() {
        // 3 × 10 reps × 4 s + 2 descansos de 30 s = 180
        assertEquals(180, segundosEstimados(3, 10, null, 30, null))
        // reps con sostener: 2 × (5 × (10 + 2)) + 1 × 0 = 120
        assertEquals(120, segundosEstimados(2, 5, 10, null, null))
        // solo sostener, ambos lados: 2 × (3 × 30 + 2 × 15) = 240
        assertEquals(240, segundosEstimados(3, null, 30, 15, "ambos"))
        // sin reps ni sostener: 10 reps por defecto
        assertEquals(40, segundosEstimados(1, null, null, null, null))
        assertEquals(1, minutosDeSegundos(5))
        assertEquals(3, minutosDeSegundos(180))
        assertEquals(4, minutosDeSegundos(181))
    }

    @Test
    fun hojaDesdePlanSoloActivosYConImagenes() {
        val h = hojaDesdePlan(plan(listOf(
            item("b", 2, inicio = "https://x/i.webp", fin = "https://x/f.webp", lado = "ambos", dolor = 4, materiales = listOf("banda_elastica")),
            item("a", 1, gif = "https://x/a.gif"),
            item("z", 0, activo = false),
        )))
        assertEquals(listOf("Ej a", "Ej b"), h.ejercicios.map { it.nombre })
        assertEquals(listOf(1, 2), h.ejercicios.map { it.numero })
        val a = h.ejercicios[0]
        assertEquals("https://x/a.gif", a.imagenInicio)
        assertNull(a.imagenFin) // con un GIF solo, una imagen basta
        val b = h.ejercicios[1]
        assertEquals("https://x/i.webp", b.imagenInicio)
        assertEquals("https://x/f.webp", b.imagenFin)
        assertEquals(listOf("Ambos lados", "Descansa 30 s entre series"), b.detalles)
        assertEquals("Detente si el dolor pasa de 4/10", b.precaucion)
        assertEquals("Sesión #8 · 10/10/26", h.etiqueta)
        assertEquals("Hasta la próxima sesión", h.vigencia)
        assertEquals("Lic. Ana", h.profesional)
        assertEquals(h.ejercicios.sumOf { it.minutos }, h.minutosTotales)
        assertEquals("3 series × 10 repeticiones", a.dosis)
        assertEquals("Lun · Mié · Vie", a.frecuencia)
    }

    @Test
    fun tablaSemanalCasillasSoloLosDiasQueTocan() {
        val h = hojaDesdePlan(plan(listOf(item("a", 1, dias = listOf(1, 2), veces = 2), item("b", 2, dias = listOf(7), veces = 9))))
        val t = tablaSeguimiento(h.ejercicios)
        assertEquals(listOf(2, 2, 0, 0, 0, 0, 0), t[0].casillas)
        assertEquals(listOf(0, 0, 0, 0, 0, 0, 6), t[1].casillas) // tope 6
    }

    @Test
    fun fechaLarga() {
        assertEquals("15 de octubre de 2026", fechaHoja("2026-10-15"))
        assertEquals("1 de enero de 2026", fechaHoja("2026-01-01T10:00:00Z"))
        assertEquals("", fechaHoja(""))
        assertEquals("", fechaHoja("x"))
    }

    @Test
    fun parseaLaHojaDelPortal() {
        val r = parsearHojaEjercicios("""
            { "hoja": { "titulo": "Ejercicios de apoyo", "etiqueta": "Sesión #8 · 10/10/26", "vigencia": "Hasta la próxima sesión",
                "fecha": "2026-10-10", "motivo": "Lumbalgia mecánica", "indicaciones": null, "precauciones": "Ojo",
                "profesional": "Lic. Ana Ríos", "minutosTotales": 24,
                "ejercicios": [
                  { "numero": 1, "nombre": "Puente glúteo básico", "imagenInicio": "https://x/i.webp", "imagenFin": null,
                    "dosis": "3 series × 10 repeticiones", "frecuencia": "Lun · Mié · Vie", "dias": [5, 1, 3], "vecesAlDia": 1,
                    "minutos": 3, "detalles": ["Ambos lados"], "indicaciones": "Despacio", "pasos": ["Uno"], "materiales": ["Colchoneta"],
                    "precaucion": "Detente si el dolor pasa de 4/10" },
                  { "numero": 2, "nombre": null }
                ] },
              "clinica": { "nombre": "DALU Fisioterapia", "logo_url": "https://x/l.png", "color_principal": "#2c3e7a" },
              "paciente": { "nombre": "María Pérez" } }
        """.trimIndent())
        assertNotNull(r)
        assertEquals(1, r.hoja.ejercicios.size)
        assertEquals(listOf(1, 3, 5), r.hoja.ejercicios[0].dias)
        assertEquals(24, r.hoja.minutosTotales)
        assertNull(r.hoja.indicaciones)
        assertEquals("DALU Fisioterapia", r.clinica?.nombre)
        assertEquals("https://x/l.png", r.clinica?.logoUrl)
        assertEquals("María Pérez", r.pacienteNombre)
    }

    @Test
    fun hojaInvalidaOVaciaEsNull() {
        assertNull(parsearHojaEjercicios("<html>404</html>"))
        assertNull(parsearHojaEjercicios("""{ "error": "x" }"""))
        assertNull(parsearHojaEjercicios("""{ "hoja": { "ejercicios": [] } }"""))
    }

    @Test
    fun escapaTodo() {
        assertEquals("&lt;b&gt; &amp; &quot;x&quot; &#39;y&#39;", escHoja("<b> & \"x\" 'y'"))
        assertEquals("", escHoja(null))
    }

    @Test
    fun colorYLogoSeguros() {
        assertEquals("#0f766e", colorDeHoja("#0f766e"))
        assertEquals("#2c3e7a", colorDeHoja("red; background:url(x)"))
        assertEquals("#2c3e7a", colorDeHoja("#fff"))
        assertEquals("#2c3e7a", colorDeHoja(null))
        assertEquals("https://x/l.png", urlSeguraHoja("https://x/l.png"))
        assertNull(urlSeguraHoja("http://x/l.png"))
        assertNull(urlSeguraHoja("javascript:alert(1)"))
        assertNull(urlSeguraHoja("data:image/png;base64,AAA"))
        assertNull(urlSeguraHoja("https://x/a b.png"))
    }

    @Test
    fun htmlEscapadoYSinPartirse() {
        val malo = "<script>alert(1)</script>"
        val p = plan(listOf(item("a", 1, nombre = malo, inicio = "javascript:alert(1)", fin = "https://x/f.png")))
            .copy(motivo = malo, precauciones = "a\nb", titulo = malo)
        val html = htmlHojaEjercicios(
            hojaDesdePlan(p), ClinicaHoja("Clínica \"<x>\"", "http://inseguro/logo.png", "#12345g"), malo, "DNI <1>",
        )
        assertFalse("<script>" in html)
        assertTrue("&lt;script&gt;" in html)
        assertFalse("javascript:" in html)
        assertFalse("http://inseguro" in html)
        assertFalse("#12345g" in html)
        assertTrue("#2c3e7a" in html)
        assertTrue("page-break-inside: avoid" in html)
        assertTrue("a<br>b" in html)
        // Sin imagen inicial segura no va tampoco la final.
        assertFalse("https://x/f.png" in html)
        listOf("Lun", "Mar", "Mié", "Jue", "Vie", "Sáb", "Dom").forEach { assertTrue("<th>$it</th>" in html) }
        assertTrue("Dolor del día (0 a 10)" in html)
        assertTrue("EJERCICIOS PARA CASA" in html)
    }

    @Test
    fun htmlConImagenesYLogoHttps() {
        val h = hojaDesdePlan(plan(listOf(item("a", 1, inicio = "https://x/i.png", fin = "https://x/f.png"))))
        val html = htmlHojaEjercicios(h, ClinicaHoja("DALU", "https://x/logo.png", "#0f766e"), "Ana", null)
        assertTrue("src=\"https://x/logo.png\"" in html)
        assertTrue("src=\"https://x/i.png\"" in html)
        assertTrue("src=\"https://x/f.png\"" in html)
        assertTrue("border-bottom: 2px solid #0f766e" in html)
        assertFalse("Documento:" in html)
    }
}
