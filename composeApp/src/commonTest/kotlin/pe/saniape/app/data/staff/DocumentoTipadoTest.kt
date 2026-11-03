package pe.saniape.app.data.staff

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** Gemelo del bloque «documento con tipo (foto del audiograma)» de otorrino-rubro.test.ts. */
class DocumentoTipadoTest {
    @Test fun elNombreLlevaElTipoYLaFecha() {
        assertEquals("Audiometría 02-11-2026.jpg", nombreDocumentoTipado("Audiometría", "JPG", "2026-11-02"))
        assertEquals("Rx tórax 02-11-2026.pdf", nombreDocumentoTipado("Rx / tórax", "pdf", "2026-11-02"))
        assertEquals("Documento 02-11-2026", nombreDocumentoTipado("  ", null, "2026-11-02"))
    }

    @Test fun sinTipoComoSiempre() {
        assertEquals("IMG_1.jpg" to "Documento", nombreYCategoriaDocumento(null, "IMG_1.jpg", "jpg", "2026-11-02"))
        assertEquals("IMG_1.jpg" to "Documento", nombreYCategoriaDocumento(" ", "IMG_1.jpg", "jpg", "2026-11-02"))
        assertEquals("Audiometría 02-11-2026.jpg" to "Audiometría", nombreYCategoriaDocumento("Audiometría", "foto-1.jpg", "jpg", "2026-11-02"))
    }

    @Test fun extension() {
        assertEquals("JPG", extensionDe("IMG_1.JPG"))
        assertEquals("pdf", extensionDe("informe.final.pdf"))
        assertNull(extensionDe("sin-extension"))
    }
}
