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
                  "tipo": "pdf", "fecha": "2026-10-01T10:00:00Z", "tratamientoNombre": "Fisioterapia lumbar", "tratamientoId": "t1" },
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
        assertEquals("t1", d.documentos[0].tratamientoId)
        assertNull(d.documentos[1].tratamientoId)
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
                "t1": { "clinicaId": "c1", "acordado": 300, "pagado": 350, "saldo": -50, "aFavor": 50, "estado": "Pagado",
                        "pagos": [ { "fecha": "2026-10-01", "monto": 350, "metodo": "Yape" } ] },
                "t2": { "acordado": 100, "pagado": 40, "saldo": 60, "estado": "Parcial", "puedePagarOnline": true }
              },
              "saldoAFavor": 50,
              "saldoAFavorPorClinica": { "c1": 50, "c2": 0 }
            }
            """.trimIndent()
        )
        assertEquals(50.0, conFavor.saldoAFavor)
        assertEquals(350.0, conFavor.porTratamiento.getValue("t1").pagado)
        assertEquals(1, conFavor.porTratamiento.getValue("t1").pagos.size)
        assertTrue(conFavor.porTratamiento.getValue("t2").puedePagarOnline)
        // aFavor tal cual del servidor (no se recalcula); sin el campo = 0.
        assertEquals(50.0, conFavor.porTratamiento.getValue("t1").aFavor)
        assertEquals("c1", conFavor.porTratamiento.getValue("t1").clinicaId)
        assertEquals(0.0, conFavor.porTratamiento.getValue("t2").aFavor)
        assertEquals(mapOf("c1" to 50.0), conFavor.saldoAFavorPorClinica)

        val sinCampo = SaludRepo.parsearSaldos("""{ "saldos": {} }""")
        assertEquals(0.0, sinCampo.saldoAFavor)
        assertTrue(sinCampo.porTratamiento.isEmpty())
        assertTrue(sinCampo.saldoAFavorPorClinica.isEmpty())
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

    private data class D(val id: String, val tratId: String?, val nombre: String?)

    @Test
    fun agrupar_por_tratamiento_plano_si_no_viene_el_campo() {
        val plano = agruparPorTratamiento(listOf(D("a", null, null), D("b", null, "Fisio")), { it.tratId }, { it.nombre })
        assertEquals(1, plano.size)
        assertNull(plano[0].titulo)
        assertEquals("todos", plano[0].clave)

        // Por ID: dos paquetes con el mismo nombre NO se funden, y un procedimiento
        // llamado "General" no choca con el grupo de los sueltos (claves distintas).
        val docs = listOf(
            D("d1", "t1", "Fisio"), D("d2", null, null), D("d3", "t2", "Fisio"),
            D("d4", "t1", "Fisio"), D("d5", "t3", "General"),
        )
        val g = agruparPorTratamiento(docs, { it.tratId }, { it.nombre })
        assertEquals(listOf("t:t1", "t:t2", "t:t3", "general"), g.map { it.clave })
        assertEquals(listOf("Fisio", "Fisio", "General", "General"), g.map { it.titulo })
        assertEquals(listOf("d1", "d4"), g[0].items.map { it.id })
        assertEquals(g.size, g.map { it.clave }.toSet().size)
        assertEquals("Tratamiento", agruparPorTratamiento(listOf(D("x", "t9", null)), { it.tratId }, { it.nombre })[0].titulo)
        assertTrue(agruparPorTratamiento(emptyList<D>(), { it.tratId }, { it.nombre }).isEmpty())
    }

    // Pagar con saldo a favor (2026-10-06): un pago mixto llega como UNA entrada con
    // `detalle`, y el saldo que salió hacia otro tratamiento con monto negativo.
    @Test
    fun portal_lee_el_detalle_del_pago_mixto_y_del_consumo() {
        val s = SaludRepo.parsearSaldos(
            """
            { "saldos": { "t1": { "acordado": 490, "pagado": 490, "saldo": 0, "aFavor": 0, "estado": "Pagado",
                "pagos": [
                  { "fecha": "2026-10-06", "monto": 490, "metodo": "Saldo a favor + Yape", "detalle": "Saldo a favor S/ 205.97 + Yape S/ 284.03" },
                  { "fecha": "2026-10-01", "monto": -205.97, "metodo": "Saldo a favor", "detalle": "Saldo aplicado a Ortodoncia" },
                  { "fecha": "2026-09-01", "monto": 100, "metodo": "Efectivo" }
                ] } } }
            """.trimIndent()
        )
        val pagos = s.porTratamiento["t1"]!!.pagos
        assertEquals("Saldo a favor S/ 205.97 + Yape S/ 284.03", pagos[0].etiqueta)
        assertEquals(-205.97, pagos[1].monto)
        assertEquals("Saldo aplicado a Ortodoncia", pagos[1].etiqueta)
        assertEquals("Efectivo", pagos[2].etiqueta)
    }
}
