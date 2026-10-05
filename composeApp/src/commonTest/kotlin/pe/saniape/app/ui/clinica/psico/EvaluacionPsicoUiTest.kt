package pe.saniape.app.ui.clinica.psico

import kotlin.test.Test
import kotlin.test.assertEquals
import pe.saniape.app.data.SaludRepo
import pe.saniape.app.data.staff.CATEGORIA_INFORME_PSICOLOGICO

/** Lógica pura de la pantalla de evaluación psicológica y del portal. */
class EvaluacionPsicoUiTest {

    @Test
    fun chips_de_estado() {
        assertEquals(TonoChip.COMPLETO, tonoChip("completo"))
        assertEquals(TonoChip.EN_CURSO, tonoChip("en_curso"))
        assertEquals(TonoChip.VACIO, tonoChip("vacio"))
        assertEquals(TonoChip.VACIO, tonoChip("cualquier cosa"))
        assertEquals("●", marcaChip("completo"))
        assertEquals("◐", marcaChip("en_curso"))
        assertEquals("○", marcaChip("vacio"))
        // El chip del informe: emitido = completo, borrador = en curso, sin informe = vacío.
        assertEquals("completo", estadoChipInforme("emitido"))
        assertEquals("en_curso", estadoChipInforme("borrador"))
        assertEquals("vacio", estadoChipInforme(null))
    }

    @Test
    fun montos() {
        assertEquals("80", formatoMonto(80.0))
        assertEquals("79.50", formatoMonto(79.5))
        assertEquals("960", formatoMonto(960.0))
    }

    @Test
    fun portal_lista_el_informe_psicologico_emitido() {
        // El servidor manda el PDF del informe como un documento más (las fotos
        // protegidas nunca llegan): el portal lo lista para abrirlo/descargarlo.
        val d = SaludRepo.parsearDocumentos("""
            { "documentos": [ { "id": "doc1", "nombre": "Informe psicológico — Ana.pdf", "categoria": "$CATEGORIA_INFORME_PSICOLOGICO",
              "path": "p1/t1/informe.pdf", "fecha": "2026-10-15", "tipo": "pdf", "tratamientoNombre": "Evaluación psicológica",
              "tratamientoId": "t1" } ], "fotos": [] }
        """)
        val doc = d.documentos.single()
        assertEquals(CATEGORIA_INFORME_PSICOLOGICO, doc.categoria)
        assertEquals(pe.saniape.app.data.ClaseArchivo.PDF, pe.saniape.app.data.claseArchivo(doc.tipo, doc.path))
    }
}
