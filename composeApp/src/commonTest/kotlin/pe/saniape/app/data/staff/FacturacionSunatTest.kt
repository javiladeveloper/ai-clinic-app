package pe.saniape.app.data.staff

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Boleta/factura SUNAT: gemelos puros de ComprobanteSunat.tsx y PagoCard de la web. */
class FacturacionSunatTest {

    private fun comp(id: String, tipo: String = "03", estado: String = "accepted", anulado: String? = null) =
        ComprobanteSunat(id = id, tipo = tipo, numero = "B001-$id", estado = estado, anuladoAt = anulado)

    @Test
    fun vigenteEsLaUltimaNoRechazadaNiAnulada() {
        val filas = listOf(comp("1", estado = "rejected"), comp("2"), comp("3", estado = "error"))
        assertEquals("2", comprobanteVigente(filas)?.id)
        assertNull(comprobanteVigente(listOf(comp("1", anulado = "2026-11-01"), comp("2", tipo = "07"))))
        assertEquals("2", notaCreditoVigente(listOf(comp("1", anulado = "x"), comp("2", tipo = "07")))?.id)
        assertEquals("1", ultimoRechazo(listOf(comp("1", estado = "rejected")))?.id)
    }

    @Test
    fun receptorListoComoLaWeb() {
        assertTrue(receptorListo("03", "1", "12345678", "ANA", "", "", 50.0))
        assertFalse(receptorListo("03", "1", "", "ANA", "", "", 50.0))
        assertTrue(receptorListo("03", "0", "", "CLIENTE", "", "", 700.0))
        assertFalse(receptorListo("03", "0", "", "CLIENTE", "", "", 700.01))
        assertTrue(receptorListo("01", "1", "", "", "20100047218", "EMPRESA SAC", 50.0))
        assertFalse(receptorListo("01", "1", "", "", "2010004721", "EMPRESA SAC", 50.0))
    }

    @Test
    fun cuerpoReceptorBoletaSinDocumentoVaConGuion() {
        val r = cuerpoReceptor("03", "0", "123", "CLIENTE", null, "", "", "", "")
        assertEquals("\"-\"", r["num_doc"].toString())
        val f = cuerpoReceptor("01", "1", "", "", null, "20100047218", "EMPRESA", "Av. 1", "230101")
        assertEquals("\"6\"", f["tipo_doc"].toString())
        assertTrue(f["direccion"].toString().contains("230101"))
    }

    @Test
    fun pagoMixtoVaPorSuIdMenor() {
        fun p(id: String, grupo: String? = null, monto: Double = 10.0, saldo: String? = null) =
            PagoFicha(id, monto, "Yape", null, "2026-11-01", null, saldoTipo = saldo, grupoPagoId = grupo)
        val lista = listOf(p("b", "g"), p("a", "g", saldo = SALDO_TIPO_USO), p("c"))
        assertEquals("a", origenSunatDePago("b", "g", lista))
        assertTrue(pagoLlevaSunat(lista[1], lista))
        assertFalse(pagoLlevaSunat(lista[0], lista))
        assertTrue(pagoLlevaSunat(lista[2], lista))
        assertFalse(pagoLlevaSunat(p("d", monto = -5.0, saldo = SALDO_TIPO_CONSUMO), lista))
    }
}
