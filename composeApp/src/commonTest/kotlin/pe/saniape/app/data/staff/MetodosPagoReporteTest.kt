package pe.saniape.app.data.staff

import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Gemelo de `lib/__tests__/metodos-pago-reporte.test.ts` (web). El caso real (25/11/2026): «cuando pongo
 * todos los métodos no me lo separa» → cada método en su columna, con total, % y cobros.
 */
class MetodosPagoReporteTest {
    private val rpc = Json.parseToJsonElement("""[
        {"periodo":"2026-11-24","metodo":"Efectivo","cobros":2,"total":"150.00"},
        {"periodo":"2026-11-24","metodo":"Yape","cobros":1,"total":50},
        {"periodo":"2026-11-25","metodo":"Yape","cobros":3,"total":"250.5"},
        {"periodo":"2026-11-25","metodo":"","cobros":1,"total":10},
        {"periodo":"basura","metodo":"X","cobros":1,"total":1}
    ]""")

    @Test fun `separa cada metodo con su total, porcentaje y cobros`() {
        val m = matrizMetodos(filasMetodos(rpc))
        assertEquals(listOf("Yape", "Efectivo", SIN_METODO), m.metodos)
        assertEquals(listOf("2026-11-24", "2026-11-25"), m.periodos)
        assertEquals(460.5, m.total)
        assertEquals(7, m.cobros)
        assertEquals(TotalMetodo(300.5, 4, 65.3), m.porMetodo["Yape"])
        assertEquals(200.0, m.totalPeriodo["2026-11-24"])
        assertEquals(null, m.celdas["2026-11-24"]?.get(SIN_METODO))
    }

    @Test fun `el excel trae una columna por metodo y las filas de total`() {
        val csv = csvMetodos(matrizMetodos(filasMetodos(rpc)), AgruparMetodos.DIA).lines()
        assertEquals("Día,Yape,Efectivo,Sin método,Total", csv[0])
        assertEquals("24 nov 2026,50,150,0,200", csv[1])
        assertTrue(csv.any { it.startsWith("TOTAL,300.5,150,10,460.5") })
        assertTrue(csv.any { it.startsWith("Nº de cobros,4,2,1,7") })
    }

    @Test fun `etiquetas de periodo como la web`() {
        assertEquals("25 nov 2026", etiquetaPeriodoMetodo("2026-11-25", AgruparMetodos.DIA))
        assertEquals("Semana del 24 nov 2026", etiquetaPeriodoMetodo("2026-11-24", AgruparMetodos.SEMANA))
        assertEquals("Noviembre 2026", etiquetaPeriodoMetodo("2026-11-01", AgruparMetodos.MES))
    }
}
