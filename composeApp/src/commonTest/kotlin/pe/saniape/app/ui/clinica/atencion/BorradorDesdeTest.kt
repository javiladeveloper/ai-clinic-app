package pe.saniape.app.ui.clinica.atencion

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import pe.saniape.app.data.staff.AtencionRepo

/**
 * Armado del borrador de la pantalla a partir de `GET consulta` (gemelo de lo
 * que hace ConsultaGuiada.tsx al abrir): todas las claves presentes para que
 * `guardar` siempre mande el estado completo.
 */
class BorradorDesdeTest {

    private val claves = listOf(
        "motivo_consulta", "tiempo_enfermedad", "relato", "funciones_biologicas",
        "examen_fisico", "plan_trabajo", "tratamiento", "observaciones", "nota_procedimiento",
    )
    private val vitales = listOf(
        "presion_sistolica", "presion_diastolica", "frecuencia_cardiaca", "frecuencia_respiratoria",
        "temperatura", "saturacion_o2", "peso", "talla", "perimetro_abdominal",
    )

    private fun consulta(atencion: String, extra: String = "") = AtencionRepo.parsearConsulta(
        """
        {
          "ok": true,
          "cita": { "id": "c1", "terapeuta_id": "t-cita", "tratamiento_id": "tr1" },
          "atencion": $atencion
          $extra
        }
        """.trimIndent()
    )

    @Test
    fun vitalATexto_sinCeroFinal() {
        assertEquals("", vitalATexto(null))
        assertEquals("36.5", vitalATexto(36.5))
        assertEquals("70", vitalATexto(70.0))
        assertEquals("120", vitalATexto(120.0))
    }

    @Test
    fun sinAtencion_todasLasClavesVacias_yDatosDeLaCita() {
        val b = borradorDesde(consulta("null"))
        assertEquals(claves.toSet(), b.textos.keys)
        assertTrue(b.textos.values.all { it == "" })
        assertEquals(vitales.toSet(), b.vitales.keys)
        assertTrue(b.vitales.values.all { it == "" })
        assertEquals("t-cita", b.terapeutaId)
        assertEquals("tr1", b.tratamientoId)
        assertTrue(b.diagnosticos.isEmpty())
        assertTrue(b.examenes.isEmpty())
    }

    @Test
    fun conAtencion_copiaTextosVitalesYTerapeuta() {
        val b = borradorDesde(
            consulta(
                """{ "terapeuta_id": "t-at", "motivo_consulta": "Cefalea", "temperatura": 36.5,
                     "peso": 70, "examenes": [{ "nombre": "Hemograma" }],
                     "diagnosticos": [{ "codigo": "R51", "descripcion": "Cefalea", "tipo": "D" }] }""",
                """, "diagnosticosSugeridos": [{ "codigo": "Z00", "descripcion": "Otro" }]""",
            )
        )
        assertEquals("Cefalea", b.textos["motivo_consulta"])
        assertEquals("", b.textos["relato"])
        assertEquals("36.5", b.vitales["temperatura"])
        assertEquals("70", b.vitales["peso"])
        assertEquals("", b.vitales["talla"])
        assertEquals("t-at", b.terapeutaId)
        assertEquals(listOf("R51"), b.diagnosticos.map { it.codigo })
        assertEquals(listOf("Hemograma"), b.examenes.map { it.nombre })
    }

    @Test
    fun sinDiagnosticos_usaLosSugeridos() {
        val b = borradorDesde(
            consulta("{}", """, "diagnosticosSugeridos": [{ "codigo": "M54.5", "descripcion": "Lumbago" }]""")
        )
        assertEquals(listOf("M54.5"), b.diagnosticos.map { it.codigo })
    }

    @Test
    fun motivoVacio_tomaElSugerido() {
        val d = consulta("""{ "motivo_consulta": "  " }""", """, "motivoSugerido": "Dolor de rodilla"""")
        assertEquals("Dolor de rodilla", borradorDesde(d).textos["motivo_consulta"])
        assertTrue(motivoSugeridoAplica(d))
    }

    @Test
    fun camposFrases_procedimientoSoloIndicaciones() {
        assertEquals(setOf("motivo_consulta", "examen_fisico", "tratamiento"), camposFrases(false))
        assertEquals(setOf("tratamiento"), camposFrases(true))
    }

    @Test
    fun motivoEscrito_noSeReemplaza() {
        val d = consulta("""{ "motivo_consulta": "Fiebre" }""", """, "motivoSugerido": "Dolor de rodilla"""")
        assertEquals("Fiebre", borradorDesde(d).textos["motivo_consulta"])
        assertFalse(motivoSugeridoAplica(d))
        val sinSugerido = consulta("null")
        assertFalse(motivoSugeridoAplica(sinSugerido))
        assertNull(sinSugerido.atencion)
    }
}
