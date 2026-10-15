package pe.saniape.app.data.staff

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * 🤰 Vínculo del control con la cita del día (solo la de gineco-obstetricia,
 * como FormControl.tsx) y el carné perinatal imprimible (escapado, como la web).
 */
class EmbarazoCarneTest {

    private val mixta = MapaGineco(listOf("g"), solo = false)
    private fun cita(id: String, esp: String?, fecha: String = "2026-08-23", estado: String = "Confirmada", prof: List<String>? = null) =
        CitaVinculable(id, fecha, estado, esp, null, prof)

    @Test fun soloLaCitaDeGinecoDelDia() {
        // Clínica mixta: la de medicina general del mismo día NO es el control prenatal.
        assertEquals("c1", citaParaVincular(listOf(cita("c1", "g"), cita("c2", "m")), "2026-08-23", emptySet(), mixta))
        // Otra fecha, cancelada, no asistió o ya usada: no se vincula.
        assertNull(citaParaVincular(listOf(cita("c1", "g", fecha = "2026-08-22")), "2026-08-23", emptySet(), mixta))
        assertNull(citaParaVincular(listOf(cita("c1", "g", estado = "Cancelada")), "2026-08-23", emptySet(), mixta))
        assertNull(citaParaVincular(listOf(cita("c1", "g", estado = "No asistió")), "2026-08-23", emptySet(), mixta))
        assertNull(citaParaVincular(listOf(cita("c1", "g")), "2026-08-23", setOf("c1"), mixta))
        // Dos candidatas: no se adivina.
        assertNull(citaParaVincular(listOf(cita("c1", "g"), cita("c2", "g")), "2026-08-23", emptySet(), mixta))
        // Sin especialidad en la cita: decide el profesional (todas sus especialidades de gineco).
        assertEquals("c3", citaParaVincular(listOf(cita("c3", null, prof = listOf("g"))), "2026-08-23", emptySet(), mixta))
        assertNull(citaParaVincular(listOf(cita("c3", null, prof = listOf("g", "m"))), "2026-08-23", emptySet(), mixta))
        // Clínica solo de gineco: cualquier cita del día.
        assertEquals("c2", citaParaVincular(listOf(cita("c2", "m")), "2026-08-23", emptySet(), MapaGineco(emptyList(), solo = true)))
    }

    private val emb = Embarazo(
        id = "e1", pacienteId = "p1", fur = "2026-03-01", fpp = "2026-12-06", gestas = 2, partos = 1, abortos = 0, cesareas = 0,
        grupoSanguineo = "O", rh = "-", pesoPregestacional = 60.0, tallaCm = 160.0, riesgos = listOf("rh_negativo"),
    )
    private val controles = listOf(
        ControlPrenatal(id = "a", embarazoId = "e1", fecha = "2026-07-19", peso = 64.0, paSistolica = 110, paDiastolica = 70, alturaUterina = 20.0, lcf = 145, terapeutaId = "t1"),
        ControlPrenatal(id = "b", embarazoId = "e1", fecha = "2026-08-16", egDias = 168, peso = 66.0, alturaUterina = 24.0, suplementos = listOf("hierro")),
    )

    @Test fun carneConCuadroCurvasYSignos() {
        val html = htmlCarnePerinatal(emb, controles, "2026-08-23", "Qhali <Gyn>", null, "javascript:alert(1)", "red;}",
            "Ana \"Q\"", "12345678", 30, null, mapOf("t1" to "Dra. Ríos"))
        assertTrue(html.contains("CARNÉ PERINATAL"))
        assertTrue(html.contains("Qhali &lt;Gyn&gt;"))
        assertFalse(html.contains("<Gyn>"))
        assertTrue(html.contains("Ana &quot;Q&quot;"))
        // Logo solo https y color solo #rrggbb.
        assertFalse(html.contains("javascript:"))
        assertFalse(html.contains("red;}"))
        assertTrue(html.contains("06/12/2026"))
        assertTrue(html.contains("G2 P1 A0 C0"))
        assertTrue(html.contains("O Rh−"))
        assertTrue(html.contains("Dra. Ríos"))
        assertTrue(html.contains("Hierro"))
        assertTrue(html.contains("2 de 6 (NTS 105-MINSA)"))
        assertTrue(html.contains("Altura uterina vs EG"))
        assertTrue(html.contains("Ganancia de peso vs EG"))
        assertTrue(html.contains("Presión arterial"))
        assertTrue(html.contains("Sangrado por la vagina"))
    }

    @Test fun sinDatosNoHayCurvas() {
        assertEquals("", curvasCarne(emb, emptyList()))
        assertTrue(htmlCarnePerinatal(emb, emptyList(), "2026-08-23", null, null, null, null, null, null, null, null)
            .contains("Sin controles registrados."))
    }
}
