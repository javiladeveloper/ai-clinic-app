package pe.saniape.app.data.staff

import pe.saniape.app.ui.clinica.notaNoEvaluados
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** Parseo del bloque `embudo` de /api/reportes/rendimiento (forma de app/api/reportes/rendimiento/route.ts). */
class EmbudoPacientesParseoTest {

    private val respuesta = """
        {
          "embudo": {
            "atendidos": 120, "evaluados": 40, "compraron": 18, "pendientes": 5,
            "yaTenian": 7, "sinPaquete": 10, "soloConsulta": 12, "soloSesiones": 68,
            "evalSinCerrar": 3, "tasaCompra": 45
          },
          "periodo": { "desde": "2026-10-01", "hasta": "2026-10-31", "hastaEfectivo": "2026-10-01",
                       "etiqueta": "octubre 2026", "id": "mes" },
          "haceFisio": true,
          "profesionales": [],
          "ms": 120
        }
    """.trimIndent()

    @Test
    fun leeTodosLosCamposDelEmbudo() {
        val r = parsearReportePacientes(respuesta)
        val e = r.embudo!!
        assertEquals(120, e.atendidos)
        assertEquals(40, e.evaluados)
        assertEquals(18, e.compraron)
        assertEquals(5, e.pendientes)
        assertEquals(7, e.yaTenian)
        assertEquals(10, e.sinPaquete)
        assertEquals(12, e.soloConsulta)
        assertEquals(68, e.soloSesiones)
        assertEquals(3, e.evalSinCerrar)
        assertEquals(45, e.tasaCompra)
        assertEquals("octubre 2026", r.etiquetaPeriodo)
    }

    @Test
    fun embudoNuloCuandoElServidorNoLoCalcula() {
        val r = parsearReportePacientes("""{"embudo": null, "periodo": {"etiqueta": "septiembre 2026"}}""")
        assertNull(r.embudo)
        assertEquals("septiembre 2026", r.etiquetaPeriodo)
    }

    @Test
    fun tasaNulaSinEvaluadosYCamposFaltantesEnCero() {
        // Un backend anterior no manda evalSinCerrar ni soloConsulta: quedan en 0.
        val r = parsearReportePacientes(
            """{"embudo": {"atendidos": 9, "evaluados": 0, "compraron": 0, "tasaCompra": null, "soloSesiones": 9}}"""
        )
        val e = r.embudo!!
        assertNull(e.tasaCompra)
        assertEquals(0, e.evalSinCerrar)
        assertEquals(0, e.soloConsulta)
        assertEquals(9, e.soloSesiones)
        assertNull(r.etiquetaPeriodo)
    }

    @Test
    fun notaDeLosNoEvaluados() {
        val base = parsearReportePacientes(respuesta).embudo!!
        assertEquals(
            "Aparte, no se evaluaron: 12 vinieron solo a consulta · 68 solo a sesiones de un paquete anterior",
            notaNoEvaluados(base),
        )
        assertEquals(
            "Aparte, no se evaluaron: 68 solo a sesiones de un paquete anterior",
            notaNoEvaluados(base.copy(soloConsulta = 0)),
        )
        assertNull(notaNoEvaluados(base.copy(soloConsulta = 0, soloSesiones = 0)))
    }
}
