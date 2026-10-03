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


    private fun cuenta(acordado: Double, pagado: Double, estado: String? = "Activo", modalidad: String? = "Paquete") =
        pe.saniape.app.data.CuentaTratamiento(acordado, pagado, estado, modalidad)

    @Test
    fun saldo_a_favor_suma_solo_lo_pagado_de_mas() {
        // t1 pagó 50 de más; t2 debe 60 (no se resta); t3 justo.
        assertEquals(50.0, pe.saniape.app.data.saldoAFavorDe(listOf(cuenta(300.0, 350.0), cuenta(100.0, 40.0), cuenta(80.0, 80.0))))
        assertEquals(0.0, pe.saniape.app.data.saldoAFavorDe(listOf(cuenta(100.0, 40.0))))
        assertEquals(0.0, pe.saniape.app.data.saldoAFavorDe(emptyList()))
        assertEquals(0.0, pe.saniape.app.data.saldoAFavorDe(listOf(cuenta(100.0, 100.001))))   // medio centavo
        // Redondeo a 2 decimales por tratamiento.
        assertEquals(0.1, pe.saniape.app.data.saldoAFavorTratamiento(100.0, 100.104, "Activo", "Paquete"))
    }

    @Test
    fun saldo_a_favor_ante_la_duda_no() {
        // Sin precio acordado: no hay contra qué comparar.
        assertEquals(0.0, pe.saniape.app.data.saldoAFavorTratamiento(0.0, 40.0, "Activo", "Consulta"))
        // Sesión suelta: total_sesiones=1 y cada sesión cobrada entra al tratamiento.
        assertEquals(0.0, pe.saniape.app.data.saldoAFavorTratamiento(80.0, 320.0, "Activo", "Sesión suelta"))
        // No facturables.
        assertEquals(0.0, pe.saniape.app.data.saldoAFavorTratamiento(100.0, 150.0, "Eliminado", "Paquete"))
        assertEquals(0.0, pe.saniape.app.data.saldoAFavorTratamiento(100.0, 150.0, "Cancelado", "Paquete"))
        // Solo Paquete y Unidades: Consulta y modalidad desconocida/vacía = 0.
        assertEquals(0.0, pe.saniape.app.data.saldoAFavorTratamiento(100.0, 150.0, "Activo", "Consulta"))
        assertEquals(0.0, pe.saniape.app.data.saldoAFavorTratamiento(100.0, 150.0, "Activo", null))
        assertEquals(0.0, pe.saniape.app.data.saldoAFavorTratamiento(100.0, 150.0, "Activo", ""))
        assertEquals(30.0, pe.saniape.app.data.saldoAFavorTratamiento(700.0, 730.0, "Activo", "Unidades"))
        assertEquals(50.0, pe.saniape.app.data.saldoAFavorTratamiento(100.0, 150.0, "Alta", "Paquete"))
        assertEquals(0.0, pe.saniape.app.data.saldoAFavorDe(listOf(
            cuenta(0.0, 40.0), cuenta(80.0, 320.0, modalidad = "Sesión suelta"), cuenta(100.0, 150.0, estado = "Eliminado"),
        )))
    }

    private fun trat(id: String, estado: String?, inicio: String?, creado: String? = null) = TratamientoPaciente(
        id = id, procedimiento = id, terapeutaId = null, terapeutaNombre = null, modalidad = "Paquete",
        estado = estado, estadoPago = null, totalSesiones = 10, sesionesCompletadas = 0,
        precioPaquete = null, precioPorSesion = null, precioAcordado = null, usaSesiones = true,
        diagnostico = null, medicacion = null, proximoControl = null, especialidadNombre = null,
        fechaInicio = inicio, createdAt = creado,
    )

    @Test
    fun documento_por_defecto_el_activo_mas_reciente() {
        assertEquals("nuevo", tratamientoPorDefectoDoc(listOf(
            trat("viejo", "Activo", "2026-08-01"), trat("nuevo", "Activo", "2026-09-20"),
            trat("alta", "Alta", "2026-10-01"), trat("sin-fecha", "Activo", null, "2026-07-01T10:00:00Z"),
        )))
        assertNull(tratamientoPorDefectoDoc(listOf(trat("alta", "Alta", "2026-10-01"))))
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
        { "citaId": "c1", "hora": "09:15", "registradoPor": "Lic. Ana Ruiz", "motivo": "Cefalea",
          "vitales": { "presion_sistolica": 120, "presion_diastolica": 80, "temperatura": "36,5",
                       "peso": 70.0, "talla": null, "otra": 5 } }
    """.trimIndent()

    @Test
    fun triaje_de_hoy_es_prestado_no_se_aplica_solo() {
        val d = consulta("null", triaje)
        val t = assertNotNull(d.triajeDeHoy)
        assertEquals("Cefalea", t.motivo)
        assertEquals(
            mapOf("presion_sistolica" to "120", "presion_diastolica" to "80", "temperatura" to "36.5", "peso" to "70"),
            vitalesDeTriajeHoy(t),
        )
        assertEquals("Del triaje de hoy (09:15 · Lic. Ana Ruiz)", avisoTriajeHoy(t))
        assertEquals("Usar triaje de hoy (09:15 · Lic. Ana Ruiz)", botonUsarTriajeHoy(t))
        assertNotNull(triajeDeHoyAplicable(d))
        // Al abrir NO se copia nada ni queda nada por guardar.
        val b = borradorDesde(d)
        assertEquals("", b.vitales["presion_sistolica"])
        assertEquals("", b.textos["motivo_consulta"])
        assertTrue(!prefillPorGuardar(d))
        // Solo al tocar "Usar triaje de hoy".
        val usado = aplicarTriajeDeHoy(b, t)
        assertEquals("120", usado.vitales["presion_sistolica"])
        assertEquals("36.5", usado.vitales["temperatura"])
        assertEquals("", usado.vitales["talla"])
        assertEquals("Cefalea", usado.textos["motivo_consulta"])
    }

    @Test
    fun usar_triaje_no_pisa_el_motivo_y_no_manda_media_presion() {
        val t = TriajeDeHoyApp(
            motivo = "Cefalea",
            vitales = kotlinx.serialization.json.buildJsonObject {
                put("presion_sistolica", kotlinx.serialization.json.JsonPrimitive(120))
                put("peso", kotlinx.serialization.json.JsonPrimitive(70))
            },
        )
        val b = BorradorAtencion(textos = mapOf("motivo_consulta" to "Dolor lumbar"), vitales = mapOf("presion_sistolica" to ""))
        val usado = aplicarTriajeDeHoy(b, t)
        assertEquals("Dolor lumbar", usado.textos["motivo_consulta"])
        assertEquals("", usado.vitales["presion_sistolica"])   // sin la diastólica no se copia
        assertEquals("70", usado.vitales["peso"])
        // Solo llena los campos VACÍOS: lo que el médico escribió no se pisa.
        val escrito = BorradorAtencion(vitales = mapOf("peso" to "72", "temperatura" to ""))
        assertEquals("72", aplicarTriajeDeHoy(escrito, t).vitales["peso"])
    }

    @Test
    fun triaje_de_hoy_no_aplica_con_vitales_propios_ni_sin_campo() {
        val propia = consulta("""{ "presion_sistolica": 110, "presion_diastolica": 70 }""", triaje)
        assertNull(triajeDeHoyAplicable(propia))
        assertEquals("110", borradorDesde(propia).vitales["presion_sistolica"])

        val viejo = consulta("null", null)
        assertNull(viejo.triajeDeHoy)
        assertNull(triajeDeHoyAplicable(viejo))

        val vacio = consulta("null", """{ "citaId": "c1", "vitales": {} }""")
        assertNull(triajeDeHoyAplicable(vacio))
        assertEquals("Del triaje de hoy", avisoTriajeHoy(vacio.triajeDeHoy!!))
        assertEquals("07:40", horaTriajeHoy(TriajeDeHoyApp(hora = "2026-10-03T07:40:00")))
    }
}
