package pe.saniape.app.data.staff

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Historia clínica con la evaluación psicológica (contrato §13.3). */
class HistoriaPsicoTest {

    private val respuesta = """
    { "ok": true, "evaluaciones": [
      { "evaluacionId": "ev1", "tratamientoId": "t1",
        "informe": { "id": "inf2", "version": 2, "emitido_at": "2026-10-04T15:00:00Z",
          "contenido": {
            "filiacion": { "nombre": "Ana <Pérez>", "edad": "9 años", "psicologo": "Lic. Rosa", "colegiatura": "C.Ps.P. 123" },
            "secciones": { "motivo": "Dificultad de atención", "conclusiones": "Rinde en el promedio", "antecedentes": "" },
            "lugar": "Tacna", "fecha": "2026-10-04",
            "reemplazaA": { "version": 1, "emitido": "2026-10-01" },
            "plantilla": { "secciones": [ {"clave":"motivo","titulo":"Motivo de consulta","visible":true} ],
                           "encabezado": "Confidencial", "pie": "Pie de la clínica" } } },
        "tests": [
          { "id": "ta1", "nombre": "Escala (WISC-V)", "fecha": "2026-10-03", "edadTexto": "9 años 3 meses",
            "validez": "valido", "estado": "interpretado", "enInforme": true,
            "puntajes": [ {"escala":"CV","directo":"30","transformado":"105","percentil":"63","categoria":"Promedio"},
                          {"escala":"VACIA","directo":"","transformado":"","percentil":"","categoria":""} ],
            "global": {"puntaje":"102","categoria":"Promedio"} } ] },
      { "evaluacionId": "ev2", "tratamientoId": null, "informe": null, "tests": null },
      { "sinId": true }
    ] }
    """.trimIndent()

    private fun parsear() = parsearHistoriaPsico(Json.parseToJsonElement(respuesta).jsonObject)

    @Test
    fun parsea_informe_vigente_y_tests() {
        val evals = parsear()
        assertEquals(2, evals.size)
        val e = evals[0]
        assertEquals("t1", e.tratamientoId)
        val inf = assertNotNull(e.informe)
        assertEquals(2, inf.version)
        assertTrue(inf.emitido)
        assertEquals("Dificultad de atención", inf.contenido.secciones["motivo"])
        assertEquals(1, inf.contenido.reemplazaA?.version)
        assertEquals("Pie de la clínica", inf.contenido.plantilla?.pie)
        val t = e.tests!!.single()
        assertEquals("Escala (WISC-V)", t.nombre)
        assertEquals("valido", t.validez)
        assertEquals(2, t.puntajes.size)
        assertEquals("102", t.global.puntaje)
        assertNull(evals[1].informe)
        assertNull(evals[1].tests)
    }

    @Test
    fun que_imprimir_segun_lo_marcado() {
        val evals = parsear()
        assertEquals(1, evaluacionesAImprimir(evals, conInforme = true, conTests = false).size)
        assertEquals(1, evaluacionesAImprimir(evals, conInforme = false, conTests = true).size)
        assertTrue(evaluacionesAImprimir(evals, conInforme = false, conTests = false).isEmpty())
        assertNull(motivoSinContenidoPsico(evals, true, false))
        assertNotNull(motivoSinContenidoPsico(evals, false, false))
        assertNotNull(motivoSinContenidoPsico(emptyList(), true, true))
        val sinInforme = listOf(EvalHistoriaPsico("x"))
        assertEquals("Todavía no hay un informe psicológico emitido.", motivoSinContenidoPsico(sinInforme, true, false))
    }

    @Test
    fun html_con_informe_sin_tests() {
        val html = htmlSeccionPsico(parsear(), conInforme = true, conTests = false) { "Evaluación infantil" }
        assertTrue(html.contains("Evaluación psicológica — Evaluación infantil"))
        assertTrue(html.contains("Informe psicológico (versión 2)"))
        assertTrue(html.contains("2. Motivo de consulta"))           // título de la plantilla copiada
        assertTrue(html.contains("Reemplaza a la versión 1 emitida el 01/10/2026"))
        assertTrue(html.contains("Ana &lt;Pérez&gt;"))                // escapado
        assertTrue(html.contains("Pie de la clínica"))
        assertTrue(html.contains("Tacna, 04/10/2026"))
        assertFalse(html.contains("Puntajes de tests"))
        assertFalse(html.contains("Antecedentes"))                    // sección vacía no sale
    }

    @Test
    fun html_con_tests_omite_filas_vacias() {
        val html = htmlSeccionPsico(parsear(), conInforme = false, conTests = true)
        assertTrue(html.contains("Puntajes de tests (material protegido)"))
        assertTrue(html.contains("protocolo válido"))
        assertTrue(html.contains("Global:</span> 102 — Promedio"))
        assertTrue(html.contains(">CV<"))
        assertFalse(html.contains("VACIA"))
        assertFalse(html.contains("Informe psicológico"))
    }

    @Test
    fun se_inserta_antes_del_pie_de_la_historia() {
        val base = """<html><body><div class="barra"></div><div>contenido</div><div style="margin-top:36px;padding-top:14px">pie</div></body></html>"""
        val r = historiaConSeccionPsico(base, "<p>PSICO</p>", "Ana")
        assertTrue(r.indexOf("contenido") < r.indexOf("PSICO"))
        assertTrue(r.indexOf("PSICO") < r.indexOf("pie"))
    }

    @Test
    fun sin_historia_imprimible_arma_documento_propio() {
        val premium = """<!doctype html><html><body><h1>Función Premium</h1></body></html>"""
        val r = historiaConSeccionPsico(premium, "<p>PSICO</p>", "Ana <P>")
        assertFalse(r.contains("Función Premium"))
        assertTrue(r.contains("PSICO"))
        assertTrue(r.contains("Ana &lt;P&gt;"))
        assertTrue(historiaConSeccionPsico(null, "<p>PSICO</p>", "Ana").contains("window.print()"))
    }
}
