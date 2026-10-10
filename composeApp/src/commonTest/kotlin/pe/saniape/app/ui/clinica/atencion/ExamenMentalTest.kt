package pe.saniape.app.ui.clinica.atencion

import pe.saniape.app.data.staff.CitaConsultaApp
import pe.saniape.app.data.staff.DatosConsultaApp
import pe.saniape.app.data.staff.FraseFrecuenteApp
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Frases del examen mental (psiquiatría): base + lo aprendido bajo 'examen_mental'. */
class ExamenMentalTest {

    @Test fun baseYAprendidasSinRepetir() {
        val d = DatosConsultaApp(
            cita = CitaConsultaApp(id = "c1"),
            frases = listOf(
                FraseFrecuenteApp("examen_mental", "Sin alteraciones sensoperceptivas", 5),
                FraseFrecuenteApp("examen_mental", "Bradipsiquia leve", 2),
                FraseFrecuenteApp("examen_mental", "Ánimo ansioso", 7),
                FraseFrecuenteApp("examen", "Abdomen blando", 9),
            ),
        )
        val f = frasesExamenMental(d)
        // El bloque "normal" primero (varias líneas), luego cada componente.
        assertTrue(f.first().texto.lines().size == 9)
        assertEquals(FRASES_BASE_EXAMEN_MENTAL.size + 2, f.size)
        assertEquals(listOf("Ánimo ansioso", "Bradipsiquia leve"), f.drop(FRASES_BASE_EXAMEN_MENTAL.size).map { it.texto })
        assertTrue(f.none { it.texto == "Abdomen blando" })
    }

    @Test fun elChipDelBloqueSeAgregaPorLinea() {
        val bloque = FRASES_BASE_EXAMEN_MENTAL.first()
        val texto = insertarFrase("", bloque, porLinea = true)
        assertTrue(fraseUsada(texto, bloque))
        assertEquals("", quitarFrase(texto, bloque, porLinea = true))
    }
}
