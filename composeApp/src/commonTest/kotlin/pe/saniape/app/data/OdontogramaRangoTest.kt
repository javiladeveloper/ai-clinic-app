package pe.saniape.app.data

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import pe.saniape.app.data.staff.COLOR_REALIZADO
import pe.saniape.app.data.staff.DienteHallazgo
import pe.saniape.app.data.staff.HallazgoDental
import pe.saniape.app.data.staff.MarcaRango
import pe.saniape.app.data.staff.ProcedimientoRef
import pe.saniape.app.data.staff.agruparPresupuesto
import pe.saniape.app.data.staff.buscarProcedimientoSugerido
import pe.saniape.app.data.staff.marcasDeRango
import pe.saniape.app.data.staff.piezasDeLaArcada

/**
 * Urbina (09/11/2026): marcaron un diastema en la 12 y una caries en la 16.
 * El diastema no se veía en el odontograma de la app (los de varias piezas no
 * se dibujaban nunca) y la caries salía presupuestada como «Carillas Resina».
 * Mismos casos que lib/__tests__/odontograma.test.ts de la web.
 */

private fun proc(id: String, nombre: String, precio: Double) = ProcedimientoRef(
    id = id, nombre = nombre, precio = precio,
    precioPaquete = null, especialidadId = null, usaSesiones = false, tarifarios = emptyList(),
)

private val SERVICIOS = listOf(
    proc("car", "Carillas Resina", 300.0),
    proc("cor", "Corona de resina", 400.0),
    proc("res", "Resina / restauración", 90.0),
)

private fun reg(id: String, diente: String, hallazgoId: String, hasta: String? = null, estado: String = "Pendiente") =
    DienteHallazgo(id = id, pacienteId = "p", diente = diente, hallazgoId = hallazgoId, estado = estado, dienteHasta = hasta)

class CariesNoEsCarillaTest {
    @Test
    fun caries_y_fractura_van_a_la_restauracion() {
        assertEquals("res", buscarProcedimientoSugerido("Caries", SERVICIOS)?.id)
        assertEquals("res", buscarProcedimientoSugerido("Fractura", SERVICIOS)?.id)
    }

    @Test
    fun sin_restauracion_en_el_tarifario_no_sugiere_la_carilla() {
        assertNull(buscarProcedimientoSugerido("Caries", SERVICIOS.take(2)))
    }

    @Test
    fun presupuesto_de_urbina_caries_a_resina_y_diastema_a_su_servicio() {
        val cat = listOf(
            HallazgoDental(id = "hc", nombre = "Caries", color = "#dc2626"),
            HallazgoDental(id = "hd", nombre = "Diastema", color = "#dc2626", procedimientoId = "car"),
        )
        val (lineas, sin) = agruparPresupuesto(listOf(reg("r1", "16", "hc"), reg("r2", "12", "hd")), cat, SERVICIOS)
        assertEquals(listOf("Resina / restauración" to listOf("16"), "Carillas Resina" to listOf("12")), lineas.map { it.nombre to it.piezas })
        assertEquals(listOf(90.0, 300.0), lineas.map { it.subtotal })
        assertTrue(sin.isEmpty())
    }
}

class MarcasDeRangoTest {
    private val cat = listOf(
        HallazgoDental(id = "d", nombre = "Diastema", color = "#dc2626"),
        HallazgoDental(id = "p", nombre = "Puente", color = "#dc2626"),
        HallazgoDental(id = "c", nombre = "Caries", color = "#dc2626"),
    )

    @Test
    fun la_arcada_va_en_orden_visual_y_solo_de_su_lado() {
        assertEquals(listOf("18", "17", "16", "15", "14", "13", "12", "11", "21", "22", "23", "24", "25", "26", "27", "28"), piezasDeLaArcada("12"))
        assertEquals("48", piezasDeLaArcada("31")?.first())
        assertNull(piezasDeLaArcada("BOCA"))
    }

    @Test
    fun diastema_solo_en_la_12_se_marca_en_la_12_punteado() {
        val m = marcasDeRango(listOf(reg("r", "12", "d")), cat)
        assertEquals(mapOf("12" to listOf(MarcaRango("#dc2626", punteada = true))), m)
    }

    @Test
    fun diastema_de_la_11_a_la_21_cruza_la_linea_media() {
        val m = marcasDeRango(listOf(reg("r", "11", "d", hasta = "21")), cat)
        assertEquals(setOf("11", "21"), m.keys)
    }

    @Test
    fun puente_de_la_13_a_la_11_cubre_las_tres_y_hecho_va_azul_continuo() {
        val m = marcasDeRango(listOf(reg("r", "13", "p", hasta = "11", estado = "Realizado")), cat)
        assertEquals(listOf("13", "12", "11"), m.keys.toList())
        assertEquals(MarcaRango(COLOR_REALIZADO, punteada = false), m["12"]?.single())
    }

    @Test
    fun una_caries_no_es_de_rango_y_una_pieza_final_de_otra_arcada_se_ignora() {
        assertTrue(marcasDeRango(listOf(reg("r", "16", "c")), cat).isEmpty())
        // 12 → 41 (otra arcada): queda solo en la 12, como «solo esta pieza».
        assertEquals(setOf("12"), marcasDeRango(listOf(reg("r", "12", "d", hasta = "41")), cat).keys)
    }
}
