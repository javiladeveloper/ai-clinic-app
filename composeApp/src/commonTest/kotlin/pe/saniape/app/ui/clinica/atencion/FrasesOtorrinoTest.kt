package pe.saniape.app.ui.clinica.atencion

import pe.saniape.app.data.staff.CitaConsultaApp
import pe.saniape.app.data.staff.DatosConsultaApp
import pe.saniape.app.data.staff.FlagsConsulta
import pe.saniape.app.data.staff.FraseFrecuenteApp
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Frases de otorrino y las variantes ACOTADAS (gemelo del bloque «frases: lo
 * aprendido en medicina no tapa la lista de otorrino» de
 * lib/__tests__/otorrino-video-fixes.test.ts, 2026-11-03).
 */
class FrasesOtorrinoTest {
    private fun datos(frases: List<FraseFrecuenteApp>, dental: Boolean = false) =
        DatosConsultaApp(cita = CitaConsultaApp(id = "c1"), flags = FlagsConsulta(dental = dental), frases = frases)

    private val murmullo = "Murmullo vesicular pasa bien en ambos campos pulmonares, sin ruidos agregados"

    @Test fun baseIgualQueLaWeb() {
        val b = FRASES_BASE_OTORRINO
        assertEquals(16, b.getValue("motivo").size)
        assertEquals("Dolor de oído", b.getValue("motivo").first().texto)
        assertEquals(listOf("Desde hoy", "1 día", "2–3 días", "1 semana", "1 mes", "Varios meses", "Más de 1 año"), b.getValue("tiempo").map { it.texto })
        assertEquals(15, b.getValue("examen").size)
        assertEquals("Otoscopía normal", b.getValue("examen").first().etiqueta)
        assertEquals(10, b.getValue("indicaciones").size)
        assertTrue(b.getValue("indicaciones").any { it.etiqueta == "Signos de alarma (ORL)" })
    }

    @Test fun ajenasComoLaWeb() {
        // Tamaños de frasesAjenas(...) calculados con la web (FRASES_BASE de lib/frases-clinicas.ts).
        assertEquals(95, frasesAjenas("otorrino", "motivo").size)
        assertEquals(4, frasesAjenas("otorrino", "tiempo").size)
        assertEquals(65, frasesAjenas("otorrino", "examen").size)
        assertEquals(65, frasesAjenas("otorrino", "indicaciones").size)
        assertEquals(94, frasesAjenas("oftalmo", "motivo").size)
        assertEquals(5, frasesAjenas("oftalmo", "tiempo").size)
        assertEquals(75, frasesAjenas("oftalmo", "examen").size)
        assertEquals(64, frasesAjenas("oftalmo", "indicaciones").size)
        // Lo que también es de la propia no es ajeno («Dolor de garganta» está en otorrino).
        assertFalse(normalizarFrase("Dolor de garganta") in frasesAjenas("otorrino", "motivo"))
        assertTrue(normalizarFrase(murmullo) in frasesAjenas("otorrino", "examen"))
    }

    @Test fun loAprendidoEnMedicinaNoTapaLaListaDeOtorrino() {
        val d = datos(listOf(
            FraseFrecuenteApp("examen", murmullo, 9),
            FraseFrecuenteApp("examen", "Tímpano retraído bilateral", 8),
        ))
        val r = frasesDe(d, "examen", otorrino = true)
        // La base de medicina no sale como chip en otorrino…
        assertFalse(r.any { normalizarFrase(it.texto) == normalizarFrase(murmullo) })
        // …y lo aprendido propio va DESPUÉS de la lista de otorrino.
        val base = FRASES_BASE_OTORRINO.getValue("examen")
        assertEquals(base.map { it.texto }, r.take(base.size).map { it.texto })
        assertEquals("Tímpano retraído bilateral", r[base.size].texto)
        assertEquals(base.size + 1, r.size)
    }

    @Test fun oftalmologiaTambienEsAcotada() {
        val d = datos(listOf(FraseFrecuenteApp("motivo", "Fiebre", 20), FraseFrecuenteApp("motivo", "Ojo seco al despertar", 3)))
        val r = frasesDe(d, "motivo", oftalmo = true).map { it.texto }
        assertFalse("Fiebre" in r) // base de medicina
        assertEquals("Ojo seco al despertar", r.last())
        assertEquals(FRASES_BASE_OFTALMO.getValue("motivo").first().texto, r.first())
    }

    @Test fun lasDemasVariantesIgualQueSiempre() {
        for (v in listOf("medicina", "pediatria", "derma", "odonto")) {
            assertEquals(emptySet(), frasesAjenas(v, "examen"))
            assertFalse(tieneBasePropia(v, "motivo"))
        }
        assertTrue(tieneBasePropia("otorrino", "motivo"))
        // Medicina: lo aprendido sale tal cual (con la base de medicina incluida).
        val d = datos(listOf(FraseFrecuenteApp("examen", murmullo, 9)))
        assertEquals(listOf(murmullo), frasesDe(d, "examen").map { it.texto })
        // Pediatría: lo aprendido con muchos usos se mezcla con la base (por uso), como antes.
        val p = frasesDe(datos(listOf(FraseFrecuenteApp("motivo", "Fiebre alta", 50))), "motivo", pediatria = true)
        assertEquals("Fiebre alta", p.first().texto)
    }

    @Test fun variantePorOrden() {
        assertEquals("derma", varianteFrases(derma = true, otorrino = true))
        assertEquals("pediatria", varianteFrases(pediatria = true, otorrino = true))
        assertEquals("oftalmo", varianteFrases(oftalmo = true, otorrino = true))
        assertEquals("otorrino", varianteFrases(otorrino = true))
        assertEquals("medicina", varianteFrases())
    }
}
