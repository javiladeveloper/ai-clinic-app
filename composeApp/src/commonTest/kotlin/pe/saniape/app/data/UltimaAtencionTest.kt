package pe.saniape.app.data

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNull
import pe.saniape.app.data.staff.AtencionRepo
import pe.saniape.app.data.staff.DiagnosticoCie
import pe.saniape.app.data.staff.ItemRecetaPrevia
import pe.saniape.app.data.staff.LaUltimaVezApp
import pe.saniape.app.data.staff.MAX_DIAGNOSTICOS_ATENCION
import pe.saniape.app.data.staff.ResumenClinicoRepo
import pe.saniape.app.data.staff.diagnosticoYaEsta
import pe.saniape.app.data.staff.fechaUltimaVez
import pe.saniape.app.data.staff.lineaDiagnostico
import pe.saniape.app.data.staff.lineaMedicamento
import pe.saniape.app.data.staff.repetirDiagnosticos

/**
 * «🗒 La última vez» (gemelo de lib/ultima-atencion.ts): el JSON que manda el
 * servidor y los atajos «↻ Mismos diagnósticos» (como Repetido, sin duplicar)
 * con las mismas reglas que la web.
 */
class UltimaAtencionTest {

    private val dxDepresion = DiagnosticoCie("F32.1", "Episodio depresivo moderado", "D")
    private val dxAnsiedad = DiagnosticoCie("F41.1", "Trastorno de ansiedad generalizada", "P")

    private fun consulta(ultima: String) = """
{ "ok": true,
  "cita": { "id": "cita1", "fecha": "2026-10-24", "tipo": "Consulta", "estado": "Confirmada", "paciente_id": "p1" },
  "laUltimaVez": $ultima }
""".trimIndent()

    @Test
    fun parseaLaAtencionAnteriorDeLaConsulta() {
        val d = AtencionRepo.parsearConsulta(consulta("""
{ "anterior": { "atencionId": "a1", "citaId": "c0", "fecha": "2026-10-01", "profesional": "Dra. Ríos",
    "diagnosticos": [{ "codigo": "F32.1", "descripcion": "Episodio depresivo moderado", "tipo": "D" }],
    "receta": [{ "dci": "Sertralina", "marca": null, "concentracion": "50 mg", "forma": "Tableta", "via": "Oral",
      "dosis": "1 tableta", "frecuencia": "cada 24 horas", "duracion": "30 días", "cantidad": 30, "unidad": "tabletas", "indicaciones": null }],
    "indicaciones": "Higiene del sueño", "control": { "fecha": "2026-11-01", "hora": "09:00:00", "estado": "Confirmada" } },
  "contexto": null, "evolucion": null,
  "diagnosticos": [{ "codigo": "F32.1", "descripcion": "Episodio depresivo moderado", "tipo": "D" }] }
"""))
        val u = assertNotNull(d.laUltimaVez)
        assertTrue(u.tieneDatos)
        val a = assertNotNull(u.anterior)
        assertEquals("Dra. Ríos", a.profesional)
        assertEquals(listOf("Sertralina"), a.receta.map { it.dci })
        assertEquals(30.0, a.receta[0].cantidad)
        assertEquals("2026-11-01", a.control?.fecha)
        assertEquals(listOf(dxDepresion), u.diagnosticos)
    }

    @Test
    fun sinAnteriorOServidorViejo_noHayBloque() {
        assertNull(AtencionRepo.parsearConsulta(consulta("null")).laUltimaVez)
        val viejo = """{ "ok": true, "cita": { "id": "c", "fecha": "2026-10-24", "tipo": "Consulta", "estado": "Confirmada" } }"""
        assertNull(AtencionRepo.parsearConsulta(viejo).laUltimaVez)
        assertFalse(LaUltimaVezApp().tieneDatos)
    }

    @Test
    fun psicologia_contextoYUltimaEvolucion() {
        val d = AtencionRepo.parsearConsulta(consulta("""
{ "anterior": null,
  "contexto": { "fuente": { "titulo": "Consulta", "fecha": "2026-10-01" },
    "secciones": [{ "clave": "motivo", "etiqueta": "Motivo de consulta", "texto": "Ansiedad" }, { "clave": "x", "etiqueta": "Vacía", "texto": "  " }] },
  "evolucion": { "fecha": "2026-10-15", "evolucion": { "evolucion": "Mejor", "estado": "", "tareas": "", "plan": "Exposición" } },
  "diagnosticos": [] }
"""))
        val u = assertNotNull(d.laUltimaVez)
        val ctx = assertNotNull(u.contexto).aContextoCaso()
        assertEquals(listOf("Ansiedad"), ctx.secciones.map { it.texto })
        assertEquals("Consulta · 01/10", ctx.detalleFuente())
        assertEquals(
            listOf("Temas trabajados y evolución" to "Mejor", "Plan para la próxima sesión" to "Exposición"),
            u.evolucion?.evolucion?.campos(),
        )
    }

    @Test
    fun resumenClinico_parseaLaUltimaVezTolerante() {
        val json = Json { ignoreUnknownKeys = true }
        val e = json.parseToJsonElement("""{ "anterior": { "atencionId": "a", "fecha": "2026-10-01", "diagnosticos": [] } }""")
        assertEquals("a", ResumenClinicoRepo.parsearLaUltimaVez(e)?.anterior?.atencionId)
        assertNull(ResumenClinicoRepo.parsearLaUltimaVez(JsonNull))
        assertNull(ResumenClinicoRepo.parsearLaUltimaVez(null))
        // Ilegible o vacío: el resumen sale igual, sin el bloque.
        assertNull(ResumenClinicoRepo.parsearLaUltimaVez(json.parseToJsonElement("""{ "anterior": 5 }""")))
        assertNull(ResumenClinicoRepo.parsearLaUltimaVez(json.parseToJsonElement("{}")))
    }

    @Test
    fun mismosDiagnosticos_comoRepetidoSinDuplicar() {
        val hoy = listOf(DiagnosticoCie("f32.1", "Episodio depresivo moderado", "P"))
        val (lista, agregados) = repetirDiagnosticos(hoy, listOf(dxDepresion, dxAnsiedad, DiagnosticoCie(null, "Insomnio", "P")))
        assertEquals(2, agregados)
        assertEquals(
            listOf(
                DiagnosticoCie("F32.1", "Episodio depresivo moderado", "P"),
                DiagnosticoCie("F41.1", "Trastorno de ansiedad generalizada", "R"),
                DiagnosticoCie(null, "Insomnio", "R"),
            ),
            lista,
        )
    }

    @Test
    fun mismosDiagnosticos_sinCodigoPorDescripcionYConTope() {
        val (_, n) = repetirDiagnosticos(listOf(DiagnosticoCie(null, "cefalea tensional", "P")), listOf(DiagnosticoCie(null, "Céfalea  Tensional", "D")))
        assertEquals(0, n)
        assertTrue(diagnosticoYaEsta(listOf(DiagnosticoCie("F321", "x", "P")), dxDepresion))
        val llenos = (0 until MAX_DIAGNOSTICOS_ATENCION - 1).map { DiagnosticoCie("A0$it", "x$it", "P") }
        val (lista, agregados) = repetirDiagnosticos(llenos, listOf(dxDepresion, dxAnsiedad))
        assertEquals(MAX_DIAGNOSTICOS_ATENCION, lista.size)
        assertEquals(1, agregados)
    }

    @Test
    fun lineas_comoLaWeb() {
        val it = ItemRecetaPrevia(dci = "Sertralina", concentracion = "50 mg", forma = "Tableta", dosis = "1 tableta", frecuencia = "cada 24 horas", duracion = "30 días")
        assertEquals("Sertralina 50 mg · Tableta — 1 tableta cada 24 horas por 30 días", lineaMedicamento(it))
        assertTrue(lineaMedicamento(it.copy(duracion = "uso continuo")).endsWith("cada 24 horas uso continuo"))
        assertEquals("F32.1 Episodio depresivo moderado (Definitivo)", lineaDiagnostico(dxDepresion))
        assertEquals("01/10/2026", fechaUltimaVez("2026-10-01"))
    }
}
