package pe.saniape.app.ui.clinica.atencion

import pe.saniape.app.data.staff.CitaConsultaApp
import pe.saniape.app.data.staff.DatosConsultaApp
import pe.saniape.app.data.staff.FlagsConsulta
import pe.saniape.app.data.staff.FraseFrecuenteApp
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Gemelo de lib/__tests__/frases-pediatria.test.ts (web, 2026-10-15). */
class FrasesPediatriaTest {

    private fun datos(frases: List<FraseFrecuenteApp>, dental: Boolean = false) =
        DatosConsultaApp(cita = CitaConsultaApp(id = "c1"), flags = FlagsConsulta(dental = dental), frases = frases)

    @Test fun baseParaNinos() {
        val p = FRASES_BASE_PEDIATRIA
        val motivo = p.getValue("motivo").map { it.texto }
        assertTrue(motivo.containsAll(listOf("Fiebre", "Diarrea", "No quiere comer", "Control de niño sano (CRED)", "Vacunas")))
        assertFalse("Control de presión arterial" in motivo)
        assertEquals(14, motivo.size)
        val examen = p.getValue("examen")
        assertEquals("Examen normal", examen[0].etiqueta)
        assertEquals(8, examen[0].texto.split('\n').size)
        assertTrue(examen.map { it.etiqueta }.containsAll(listOf("Fontanela normal", "Otitis", "Sin deshidratación")))
        assertTrue(p.getValue("indicaciones").map { it.etiqueta }.containsAll(listOf("Lavado nasal", "Sales de rehidratación", "Signos de alarma (niño)")))
        assertEquals(listOf("Desde hoy", "1 día", "2–3 días", "1 semana", "Más de 2 semanas"), p.getValue("tiempo").map { it.texto })
    }

    @Test fun sinChipsRepetidosEnCadaCampo() {
        for ((campo, chips) in FRASES_BASE_PEDIATRIA) {
            assertEquals(chips.size, chips.map { it.etiqueta }.toSet().size, campo)
            assertEquals(chips.size, chips.map { it.texto }.toSet().size, campo)
        }
    }

    @Test fun pediatriaTraeSuBaseYAprendeEnLasClavesDeMedicina() {
        val d = datos(listOf(
            FraseFrecuenteApp("motivo", "Dolor al orinar", 3),
            FraseFrecuenteApp("motivo", "Fiebre", 5),
        ))
        val f = frasesDe(d, "motivo", pediatria = true)
        // Lo aprendido aparece; lo que ya está en la base no se repite.
        assertTrue(f.any { it.texto == "Dolor al orinar" })
        assertEquals(1, f.count { it.texto == "Fiebre" })
        // "Fiebre" (5 usos) sube primero; luego lo aprendido (3); luego la base sin uso.
        assertEquals(listOf("Fiebre", "Dolor al orinar", "Tos"), f.take(3).map { it.texto })
        assertEquals(FRASES_BASE_PEDIATRIA.getValue("motivo").size + 1, f.size)
    }

    @Test fun elBloqueVaPrimero() {
        val d = datos(listOf(FraseFrecuenteApp("examen", "Abdomen distendido", 9)))
        val f = frasesDe(d, "examen", pediatria = true)
        assertEquals("Examen normal", f.first().etiqueta)
        assertEquals("Abdomen distendido", f[1].texto)
    }

    @Test fun sinPediatriaSoloLoAprendidoComoAntes() {
        val d = datos(listOf(FraseFrecuenteApp("motivo", "Cefalea", 2), FraseFrecuenteApp("motivo", "Tos", 4)))
        assertEquals(listOf("Tos", "Cefalea"), frasesDe(d, "motivo").map { it.texto })
        // Odontología no usa la base de pediatría (odontopediatría es dental).
        val dd = datos(listOf(FraseFrecuenteApp("odonto_motivo", "Caries", 1)), dental = true)
        assertEquals(listOf("Caries"), frasesDe(dd, "motivo", pediatria = true).map { it.texto })
    }
}
