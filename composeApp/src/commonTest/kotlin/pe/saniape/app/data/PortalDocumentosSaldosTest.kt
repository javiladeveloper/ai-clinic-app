package pe.saniape.app.data

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Portal del paciente: GET /api/paciente/mis-documentos (documentos + FOTOS, que
 * antes se descartaban) y GET /api/paciente/mis-pagos (con el `saldoAFavor`
 * opcional). Los campos nuevos son opcionales: sin ellos, todo como antes.
 */
class PortalDocumentosSaldosTest {

    @Test
    fun documentos_y_fotos_se_leen_los_dos() {
        val d = SaludRepo.parsearDocumentos(
            """
            {
              "documentos": [
                { "id": "d1", "nombre": "Hemograma.pdf", "categoria": "Documento", "path": "p1/doc-1.pdf",
                  "tipo": "pdf", "fecha": "2026-10-01T10:00:00Z", "tratamientoNombre": "Fisioterapia lumbar" },
                { "id": "d2", "nombre": "Rx.jpg", "categoria": "Documento", "path": "p1/doc-2.jpg", "fecha": "2026-10-02" }
              ],
              "fotos": [
                { "id": "f1", "path": "p1/foto-1.jpg", "momento": "Antes", "fecha": "2026-09-20T08:00:00Z",
                  "url": "https://firmada/1" },
                { "id": "f2", "path": "p1/foto-2.jpg", "momento": "Despues", "fecha": "2026-10-01" },
                { "id": "sin-path" }
              ]
            }
            """.trimIndent()
        )
        assertEquals(2, d.documentos.size)
        assertEquals("pdf", d.documentos[0].tipo)
        assertEquals("Fisioterapia lumbar", d.documentos[0].tratamientoNombre)
        assertNull(d.documentos[1].tipo)
        assertNull(d.documentos[1].tratamientoNombre)
        assertEquals(listOf("f1", "f2"), d.fotos.map { it.id })
        assertEquals("https://firmada/1", d.fotos[0].url)
        assertNull(d.fotos[1].url)   // se firma por ?path= al mostrarla
        assertEquals("Despues", d.fotos[1].momento)
    }

    @Test
    fun servidor_viejo_sin_fotos_o_cuerpo_invalido() {
        val viejo = SaludRepo.parsearDocumentos("""{ "documentos": [] }""")
        assertTrue(viejo.documentos.isEmpty())
        assertTrue(viejo.fotos.isEmpty())
        val roto = SaludRepo.parsearDocumentos("no es json")
        assertTrue(roto.documentos.isEmpty() && roto.fotos.isEmpty())
    }

    @Test
    fun saldos_con_y_sin_saldoAFavor() {
        val conFavor = SaludRepo.parsearSaldos(
            """
            {
              "saldos": {
                "t1": { "acordado": 300, "pagado": 350, "saldo": 0, "estado": "Pagado",
                        "pagos": [ { "fecha": "2026-10-01", "monto": 350, "metodo": "Yape" } ] },
                "t2": { "acordado": 100, "pagado": 40, "saldo": 60, "estado": "Parcial", "puedePagarOnline": true }
              },
              "saldoAFavor": 50
            }
            """.trimIndent()
        )
        assertEquals(50.0, conFavor.saldoAFavor)
        assertEquals(350.0, conFavor.porTratamiento.getValue("t1").pagado)
        assertEquals(1, conFavor.porTratamiento.getValue("t1").pagos.size)
        assertTrue(conFavor.porTratamiento.getValue("t2").puedePagarOnline)

        val sinCampo = SaludRepo.parsearSaldos("""{ "saldos": {} }""")
        assertEquals(0.0, sinCampo.saldoAFavor)
        assertTrue(sinCampo.porTratamiento.isEmpty())
        assertEquals(0.0, SaludRepo.parsearSaldos("""{ "saldos": {}, "saldoAFavor": -5 }""").saldoAFavor)
    }

    @Test
    fun clase_e_icono_del_archivo() {
        assertEquals(ClaseArchivo.IMAGEN, claseArchivo("jpg", null))
        assertEquals(ClaseArchivo.IMAGEN, claseArchivo("image/webp", null))
        assertEquals(ClaseArchivo.IMAGEN, claseArchivo(null, "p1/doc-9.PNG"))
        assertEquals(ClaseArchivo.PDF, claseArchivo("pdf", "x.jpg"))   // manda el tipo
        assertEquals(ClaseArchivo.OTRO, claseArchivo("heic", null))    // HEIC se abre afuera
        assertEquals(ClaseArchivo.OTRO, claseArchivo(null, "sin-extension"))
        assertEquals("📄", iconoArchivo(ClaseArchivo.PDF))
        assertEquals("🖼", iconoArchivo(ClaseArchivo.IMAGEN))
    }

    @Test
    fun agrupar_por_tratamiento_plano_si_no_viene_el_campo() {
        val plano = agruparPorTratamiento(listOf("a", "b")) { null }
        assertEquals(listOf<Pair<String?, List<String>>>(null to listOf("a", "b")), plano)

        val docs = listOf("Fisio" to "d1", null to "d2", "Nutri" to "d3", "Fisio" to "d4")
        val g = agruparPorTratamiento(docs) { it.first }
        assertEquals(listOf("Fisio", "Nutri", "General"), g.map { it.first })
        assertEquals(listOf("d1", "d4"), g[0].second.map { it.second })
        assertEquals(listOf("d2"), g[2].second.map { it.second })
        assertTrue(agruparPorTratamiento(emptyList<String>()) { it }.isEmpty())
    }
}
