package pe.saniape.app.data.staff

import kotlin.test.Test
import kotlin.test.assertEquals

/** Flujo #1 (gemelo web): «💰 Cobrar N U · S/ X» al guardar el mapa de aplicaciones. */
class CobrarUnidadesMapaTest {
    @Test fun elMontoEsCantidadPorPrecioCadaUno() {
        assertEquals(450.0, montoUnidadesMapa(50, 9.0))
        assertEquals(41.25, montoUnidadesMapa(15, 2.75))
        // Sin precio c/u (o sin cantidad) no hay cobro en un toque: queda el flujo de editar.
        assertEquals(0.0, montoUnidadesMapa(50, null))
        assertEquals(0.0, montoUnidadesMapa(50, 0.0))
        assertEquals(0.0, montoUnidadesMapa(0, 9.0))
    }
}
