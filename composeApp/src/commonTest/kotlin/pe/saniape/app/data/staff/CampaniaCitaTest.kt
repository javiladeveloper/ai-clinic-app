package pe.saniape.app.data.staff

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Campañas en la cita (gemelo de lib/__tests__ de campañas web, 18/11/2026). */
class CampaniaCitaTest {
    private fun camp(
        id: String = "c1", tipo: String = "porcentaje", alcance: String = "citas", aplicaA: String? = "ambas",
        valor: Double? = 30.0, precio: Double? = null, servicios: List<String> = emptyList(),
        esps: List<String> = emptyList(), serviciosEsp: List<String> = emptyList(),
    ) = CampaniaApp(id, "Promo $id", tipo, alcance, aplicaA, valor, precio, null, servicios,
        especialidadIds = esps, serviciosDeEspecialidades = serviciosEsp)

    @Test
    fun todosLosServiciosNoDescuentaLaConsulta() {
        // GLAUCOMA: 30% en «Todos los servicios» dejaba la consulta de 200 en 140.
        assertFalse(camp(alcance = "todos").aplicaACita("Consulta", null))
        assertTrue(CatalogosCobroRepo.campaniasParaCita(listOf(camp(alcance = "todos")), "Consulta", 200.0).isEmpty())
    }

    @Test
    fun laDeConsultaSeOfreceConSuPrecio() {
        val ofertas = CatalogosCobroRepo.campaniasParaCita(
            listOf(camp("a", tipo = "precio_fijo", precio = 30.0, aplicaA = "Consulta"), camp("b", valor = 10.0)), "Consulta", 200.0,
        )
        assertEquals(listOf("a" to 30.0, "b" to 180.0), ofertas.map { it.first.id to it.second })
    }

    @Test
    fun porEspecialidadSoloLaCitaDeEsaEspecialidad() {
        val c = camp(esps = listOf("oftalmo"))
        assertTrue(c.aplicaACita("Consulta", null, "oftalmo"))
        assertFalse(c.aplicaACita("Consulta", null, "medicina"))
        assertFalse(c.aplicaACita("Consulta", null, null))
    }

    @Test
    fun porEspecialidadEnServiciosSoloLosDeEsaEspecialidad() {
        val c = camp(alcance = "todos", esps = listOf("oftalmo"), serviciosEsp = listOf("lentes"))
        assertTrue(c.alcanzaProcedimiento("lentes"))
        assertFalse(c.alcanzaProcedimiento("limpieza"))
        // Sin especialidades: todos los servicios, como siempre.
        assertTrue(camp(alcance = "todos").alcanzaProcedimiento("limpieza"))
    }
}
