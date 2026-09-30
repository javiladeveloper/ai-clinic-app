package pe.saniape.app.ui.clinica.atencion

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import pe.saniape.app.data.staff.AtencionRepo
import pe.saniape.app.data.staff.DiagnosticoCie
import pe.saniape.app.ui.ArchivoSeleccionado

/** Reglas puras de los pasos Plan y Cierre (gemelas de PlanAtencion.tsx / ConsultaGuiada.tsx). */
class PasosPlanCierreTest {

    private fun consulta(servicios: String, cita: String = """"id": "c1", "tipo": "Consulta", "especialidad_id": "e1"""") =
        AtencionRepo.parsearConsulta("""{ "ok": true, "cita": { $cita }, "servicios": $servicios }""")

    @Test
    fun procedimientosDelPlanSinConsultaNiControlNiOtraEspecialidad() {
        val d = consulta(
            """[
              {"id":"s1","nombre":"Infiltración","especialidad_id":"e1"},
              {"id":"s2","nombre":"Consulta general","especialidad_id":"e1"},
              {"id":"s3","nombre":"Control","especialidad_id":"e1"},
              {"id":"s4","nombre":"Evaluación","tipo_cita":"Evaluación","especialidad_id":"e1"},
              {"id":"s5","nombre":"Curación","especialidad_id":null},
              {"id":"s6","nombre":"Limpieza","especialidad_id":"e2"}
            ]""",
        )
        assertEquals(listOf("s1", "s5"), procedimientosDelPlan(d).map { it.id })
    }

    @Test
    fun servicioControlPrefiereElQueDiceControl() {
        val d = consulta(
            """[
              {"id":"s1","nombre":"Consulta","tipo_cita":"Consulta","especialidad_id":"e1"},
              {"id":"s2","nombre":"Control médico","especialidad_id":"e1"}
            ]""",
        )
        assertEquals("s2", servicioControlDe(d)?.id)
        val sinControl = consulta("""[{"id":"s1","nombre":"Consulta","tipo_cita":"Consulta","especialidad_id":"e1"}]""")
        assertEquals("s1", servicioControlDe(sinControl)?.id)
    }

    @Test
    fun mimeResultadoAceptaFotoYPdf() {
        assertEquals("application/pdf", mimeResultado(ArchivoSeleccionado("x.PDF", ByteArray(1), null)))
        assertEquals("image/png", mimeResultado(ArchivoSeleccionado("x", ByteArray(1), "image/png")))
        assertEquals("image/jpeg", mimeResultado(ArchivoSeleccionado("foto.jpg", ByteArray(1), "application/octet-stream")))
        assertNull(mimeResultado(ArchivoSeleccionado("hoja.xlsx", ByteArray(1), null)))
    }

    @Test
    fun textoDiagnosticosComoLaWeb() {
        val l = listOf(DiagnosticoCie("J06.9", "Infección aguda de vías respiratorias", "P"), DiagnosticoCie(null, "Fiebre", "D"))
        assertEquals("J06.9 Infección aguda de vías respiratorias (P); Fiebre (D)", textoDiagnosticos(l))
    }

    @Test
    fun textoCobradoConFechaDistinta() {
        assertEquals("Cobrado S/ 80.00", textoCobrado("Consulta", "S/ 80.00", "cobrar", "2026-09-30", "2026-09-30"))
        assertEquals("Cobrado S/ 80.00 (fechado el 29/09)", textoCobrado("Consulta", "S/ 80.00", "cobrar", "2026-09-29", "2026-09-30"))
        assertEquals("Consulta sin costo: quedó saldada", textoCobrado("Consulta", "S/ 80.00", "gratis", "2026-09-29", "2026-09-30"))
    }

    @Test
    fun examenesDeLaRespuestaDelAdjunto() {
        val cuerpo = Json.parseToJsonElement(
            """{"ok":true,"examenes":[{"nombre":"Hemograma completo","documento_id":"doc1","fecha_resultado":"2026-09-30"}]}""",
        ).jsonObject
        val l = AtencionRepo.examenesDeRespuesta(cuerpo)
        assertEquals("doc1", l?.single()?.documento_id)
        assertNull(AtencionRepo.examenesDeRespuesta(null))
    }
}
