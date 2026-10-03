package pe.saniape.app.data.staff

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import pe.saniape.app.ui.clinica.atencion.borradorDesde
import pe.saniape.app.ui.clinica.atencion.prefillPorGuardar

/**
 * Ficha del staff: documentos por tratamiento → categoría, saldo a favor (solo
 * se muestra; la deuda no cambia) y el triaje de hoy compartido entre médicos
 * (`triajeDeHoy`, campo opcional de GET /api/staff/atencion/consulta).
 */
class DocumentosSaldoTriajeTest {

    private fun doc(id: String, trat: String?, cat: String? = "Documento") =
        DocumentoFicha(id, "$id.pdf", "p/$id.pdf", "pdf", categoria = cat, tratamientoId = trat)

    @Test
    fun documentos_por_tratamiento_categoria_y_general_al_final() {
        val g = agruparDocumentosFicha(
            listOf(
                doc("a", "t2"), doc("b", null), doc("c", "t1", "Receta"), doc("d", "t1"),
                doc("e", "t-viejo"), doc("foto", "t1", CATEGORIA_FOTO_EVOLUTIVA), doc("f", null, null),
            ),
            listOf("t1" to "Fisioterapia", "t2" to "Nutrición"),
        )
        assertEquals(listOf("Fisioterapia", "Nutrición", "Tratamiento anterior", "General"), g.map { it.titulo })
        assertEquals(listOf("Receta", "Documento"), g[0].categorias.map { it.first })
        // La foto evolutiva NO aparece (vive en la galería).
        assertTrue(g.flatMap { it.categorias }.flatMap { it.second }.none { it.id == "foto" })
        // Sin categoría → "Documento".
        assertEquals(listOf("b", "f"), g.last().categorias.single().second.map { it.id })
        assertNull(g.last().tratamientoId)
        assertTrue(agruparDocumentosFicha(listOf(doc("x", null, CATEGORIA_FOTO_EVOLUTIVA)), emptyList()).isEmpty())
    }

    @Test
    fun saldo_a_favor_suma_solo_lo_pagado_de_mas() {
        // t1 pagó 50 de más; t2 debe 60 (no se resta); t3 justo.
        assertEquals(50.0, saldoAFavorDe(listOf(300.0 to 350.0, 100.0 to 40.0, 80.0 to 80.0)))
        assertEquals(0.0, saldoAFavorDe(listOf(100.0 to 40.0)))
        assertEquals(0.0, saldoAFavorDe(emptyList()))
        assertEquals(0.0, saldoAFavorDe(listOf(100.0 to 100.001)))   // centavos de redondeo
    }

    private fun consulta(atencion: String, triaje: String?) = AtencionRepo.parsearConsulta(
        """
        {
          "ok": true,
          "cita": { "id": "c2", "terapeuta_id": "t1" },
          "atencion": $atencion
          ${triaje?.let { ""","triajeDeHoy": $it""" } ?: ""}
        }
        """.trimIndent()
    )

    private val triaje = """
        { "citaId": "c1", "hora": "09:15:00", "registradoPor": "Lic. Ana Ruiz",
          "vitales": { "presion_sistolica": 120, "presion_diastolica": 80, "temperatura": "36,5",
                       "peso": 70.0, "talla": null, "otra": 5 } }
    """.trimIndent()

    @Test
    fun triaje_de_hoy_se_lee_y_se_precarga_si_no_hay_vitales_propios() {
        val d = consulta("null", triaje)
        val t = assertNotNull(d.triajeDeHoy)
        assertEquals("c1", t.citaId)
        assertEquals(
            mapOf("presion_sistolica" to "120", "presion_diastolica" to "80", "temperatura" to "36.5", "peso" to "70"),
            vitalesDeTriajeHoy(t),
        )
        assertEquals("Del triaje de hoy (09:15 · Lic. Ana Ruiz)", avisoTriajeHoy(t))
        assertNotNull(triajeDeHoyAplicable(d))
        val b = borradorDesde(d)
        assertEquals("120", b.vitales["presion_sistolica"])
        assertEquals("36.5", b.vitales["temperatura"])
        assertEquals("", b.vitales["talla"])
        // Queda por guardar en ESTA cita (con el próximo guardado del médico).
        assertTrue(prefillPorGuardar(d))
    }

    @Test
    fun triaje_de_hoy_no_pisa_vitales_propios_ni_aplica_sin_campo() {
        val propia = consulta("""{ "presion_sistolica": 110, "presion_diastolica": 70 }""", triaje)
        assertNull(triajeDeHoyAplicable(propia))
        assertEquals("110", borradorDesde(propia).vitales["presion_sistolica"])
        assertEquals("", borradorDesde(propia).vitales["peso"])

        val viejo = consulta("null", null)
        assertNull(viejo.triajeDeHoy)
        assertNull(triajeDeHoyAplicable(viejo))

        val vacio = consulta("null", """{ "citaId": "c1", "vitales": {} }""")
        assertNull(triajeDeHoyAplicable(vacio))
        assertEquals("Del triaje de hoy", avisoTriajeHoy(vacio.triajeDeHoy!!))
        assertEquals("07:40", horaTriajeHoy(TriajeDeHoyApp(hora = "2026-10-03T07:40:00")))
    }
}
