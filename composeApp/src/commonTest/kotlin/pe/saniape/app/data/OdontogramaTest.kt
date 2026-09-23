package pe.saniape.app.data

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import pe.saniape.app.data.staff.COLOR_REALIZADO
import pe.saniape.app.data.staff.DienteHallazgo
import pe.saniape.app.data.staff.HallazgoDental
import pe.saniape.app.data.staff.ProcedimientoRef
import pe.saniape.app.data.staff.agruparPresupuesto
import pe.saniape.app.data.staff.diagnosticoDesdeHallazgos
import pe.saniape.app.data.staff.pintarDiente

/**
 * El odontograma de la app tiene que dar EXACTAMENTE lo mismo que el de la web
 * (`lib/odontograma.ts`): el mismo paciente se abre desde el celular del
 * odontólogo y desde la computadora de recepción.
 *
 * Los casos y los resultados esperados están copiados de los tests de la web,
 * para que una divergencia salte acá.
 */

private val CAT = listOf(
    HallazgoDental(id = "c", nombre = "Caries", color = "#dc2626", procedimientoId = "P2"),
    HallazgoDental(id = "a", nombre = "Ausente", color = "#6b7280", marcaAusente = true),
    HallazgoDental(id = "s", nombre = "Sarro", color = "#d97706", procedimientoId = "P1", porBoca = true),
    HallazgoDental(id = "g", nombre = "Gingivitis", color = "#d97706", porBoca = true),
    // Azul = trabajo YA hecho: describe la boca, no es un diagnóstico de hoy.
    HallazgoDental(id = "x", nombre = "Corona existente", color = COLOR_REALIZADO),
)

private fun proc(id: String, nombre: String, precio: Double) = ProcedimientoRef(
    id = id, nombre = nombre, precio = precio,
    precioPaquete = null, especialidadId = null, usaSesiones = false, tarifarios = emptyList(),
)

private val PROCS = listOf(
    proc("P1", "Profilaxis (limpieza)", 80.0),
    proc("P2", "Resina", 60.0),
)

private fun h(
    diente: String,
    hallazgoId: String,
    estado: String = "Pendiente",
    superficies: List<String>? = null,
) = DienteHallazgo(
    id = "$diente-$hallazgoId-$estado-${superficies?.joinToString("") ?: ""}",
    pacienteId = "p", diente = diente, hallazgoId = hallazgoId,
    superficies = superficies, estado = estado,
)

class PintarDienteTest {
    @Test
    fun sin_hallazgos_todas_las_caras_quedan_limpias() {
        val p = pintarDiente(emptyList(), CAT)
        assertTrue(p.porSuperficie.values.all { it == null })
        assertFalse(p.ausente)
    }

    @Test
    fun un_hallazgo_sin_superficies_pinta_el_diente_entero() {
        val p = pintarDiente(listOf(h("26", "c")), CAT)
        assertTrue(p.porSuperficie.values.all { it == "#dc2626" })
    }

    @Test
    fun con_superficies_pinta_solo_esas() {
        val p = pintarDiente(listOf(h("26", "c", superficies = listOf("O", "M"))), CAT)
        assertEquals("#dc2626", p.porSuperficie["O"])
        assertEquals("#dc2626", p.porSuperficie["M"])
        assertEquals(null, p.porSuperficie["V"])
    }

    @Test
    fun lo_pendiente_manda_sobre_lo_realizado() {
        // De un vistazo importa lo que FALTA por hacer. Un diente con una
        // caries sin tratar y una restauración vieja se ve rojo.
        val p = pintarDiente(
            listOf(h("26", "c", estado = "Realizado"), h("26", "c", estado = "Pendiente")),
            CAT,
        )
        assertEquals("#dc2626", p.porSuperficie["O"])
    }

    @Test
    fun el_ausente_se_marca_aparte() {
        val p = pintarDiente(listOf(h("46", "a")), CAT)
        assertTrue(p.ausente)
        // Y no pinta caras: la UI tacha el diagrama entero.
        assertTrue(p.porSuperficie.values.all { it == null })
    }
}

class PresupuestoTest {
    @Test
    fun agrupa_por_servicio_y_cobra_por_pieza() {
        val (lineas, _) = agruparPresupuesto(
            listOf(h("11", "c"), h("21", "c")), CAT, PROCS,
        )
        assertEquals(1, lineas.size)
        assertEquals(120.0, lineas[0].subtotal)   // 2 piezas x S/60
    }

    @Test
    fun lo_de_boca_se_cobra_UNA_vez() {
        // El caso que lo motivó: sarro marcado en 12 dientes son 12 limpiezas
        // (S/960) si se cobra por pieza. Es una sola profilaxis.
        val doce = listOf("16", "17", "18", "26", "27", "28", "36", "37", "38", "46", "47", "48")
            .map { h(it, "s") }
        val (lineas, _) = agruparPresupuesto(doce, CAT, PROCS)
        assertEquals(1, lineas.size)
        assertEquals(80.0, lineas[0].subtotal)
        // Las piezas se conservan: sirven para saber dónde estaba.
        assertEquals(12, lineas[0].piezas.size)
    }

    @Test
    fun lo_realizado_no_entra_al_presupuesto() {
        val (lineas, _) = agruparPresupuesto(
            listOf(h("11", "c", estado = "Realizado")), CAT, PROCS,
        )
        assertTrue(lineas.isEmpty())
    }

    @Test
    fun un_hallazgo_sin_servicio_se_reporta_aparte() {
        // No se puede cobrar, pero el odontólogo tiene que verlo.
        val (lineas, sin) = agruparPresupuesto(listOf(h("31", "g")), CAT, PROCS)
        assertTrue(lineas.isEmpty())
        assertEquals(1, sin.size)
    }
}

class DiagnosticoTest {
    @Test
    fun agrupa_por_hallazgo_como_lo_diria_un_dentista() {
        val r = diagnosticoDesdeHallazgos(listOf(h("26", "c"), h("27", "c"), h("36", "c")), CAT)
        assertEquals("Caries en piezas 26, 27 y 36.", r)
    }

    @Test
    fun una_sola_pieza_va_en_singular() {
        assertEquals("Caries en pieza 11.", diagnosticoDesdeHallazgos(listOf(h("11", "c")), CAT))
    }

    @Test
    fun lo_de_boca_con_varias_piezas_se_redacta_generalizado() {
        val r = diagnosticoDesdeHallazgos(listOf(h("16", "s"), h("17", "s"), h("26", "s")), CAT)
        assertEquals("Sarro generalizado.", r)
    }

    @Test
    fun marcado_en_BOCA_se_redacta_general_aunque_sea_una_sola_marca() {
        // "BOCA" es la marca para la boca entera (la web la usa igual).
        assertEquals("Sarro generalizado.", diagnosticoDesdeHallazgos(listOf(h("BOCA", "s")), CAT))
    }

    @Test
    fun los_que_ya_son_generales_no_llevan_la_palabra() {
        // "Gingivitis generalizado" es redundante y mal concordado.
        val r = diagnosticoDesdeHallazgos(listOf(h("BOCA", "g")), CAT)
        assertEquals("Gingivitis.", r)
    }

    @Test
    fun el_trabajo_previo_azul_no_entra() {
        // "Corona existente" describe la boca, no algo a tratar.
        val r = diagnosticoDesdeHallazgos(listOf(h("26", "c"), h("11", "x")), CAT)
        assertEquals("Caries en pieza 26.", r)
    }

    @Test
    fun sin_hallazgos_no_inventa_nada() {
        assertEquals("", diagnosticoDesdeHallazgos(emptyList(), CAT))
    }

    @Test
    fun el_mismo_odontograma_da_siempre_el_mismo_texto() {
        // Sin orden estable, reabrir la ficha reescribía el diagnóstico.
        val a = diagnosticoDesdeHallazgos(listOf(h("26", "c"), h("46", "a"), h("31", "g")), CAT)
        val b = diagnosticoDesdeHallazgos(listOf(h("31", "g"), h("26", "c"), h("46", "a")), CAT)
        assertEquals(a, b)
    }
}

class PlanTratamientoTest {
    private fun procCon(modo: String?, sesiones: Int? = null) = pe.saniape.app.data.staff.ProcedimientoRef(
        id = "P", nombre = "Servicio", precio = 60.0, precioPaquete = null, especialidadId = null,
        usaSesiones = false,
        tarifarios = sesiones?.let { listOf(pe.saniape.app.data.staff.TarifarioRef("T", it, 300.0)) } ?: emptyList(),
        modoCobro = modo,
    )
    private fun linea(piezas: List<String>, subtotal: Double, porBoca: Boolean = false) =
        pe.saniape.app.data.staff.LineaPresupuesto(
            procedimientoId = "P", nombre = "Resina", hallazgoNombre = "Caries",
            piezas = piezas, hallazgoIds = piezas.map { "h$it" },
            precioUnitario = 60.0, subtotal = subtotal, porBoca = porBoca,
        )

    @Test
    fun por_unidades_una_por_pieza() {
        // 3 caries = 3 resinas.
        val p = pe.saniape.app.data.staff.planTratamiento(linea(listOf("26", "27", "36"), 180.0), procCon(null))
        assertEquals("Unidades", p.modalidad)
        assertEquals(3, p.cantidadUnidades)
        assertEquals(180.0, p.precioAcordado)
        assertEquals("Caries en pieza(s) 26, 27, 36", p.diagnostico)
    }

    @Test
    fun por_sesiones_usa_el_primer_tarifario() {
        val p = pe.saniape.app.data.staff.planTratamiento(linea(listOf("26"), 300.0), procCon("sesiones", 4))
        assertEquals("Sesiones", p.modalidad)
        assertEquals(4, p.totalSesiones)
        assertEquals(300.0, p.precioPaquete)
        assertEquals(null, p.cantidadUnidades)
    }

    @Test
    fun lo_de_boca_es_UNA_sesion_suelta_no_doce_unidades() {
        // Profilaxis marcada en 12 dientes: un acto, se cobra una vez.
        val doce = listOf("16", "17", "18", "26", "27", "28", "36", "37", "38", "46", "47", "48")
        val p = pe.saniape.app.data.staff.planTratamiento(linea(doce, 80.0, porBoca = true), procCon(null))
        assertEquals("Sesión suelta", p.modalidad)
        assertEquals(1, p.totalSesiones)
        assertEquals(80.0, p.precioAcordado)
        assertEquals("Caries generalizado", p.diagnostico)
    }

    @Test
    fun marcado_en_BOCA_dice_boca_completa() {
        val p = pe.saniape.app.data.staff.planTratamiento(linea(listOf("BOCA"), 80.0, porBoca = true), procCon(null))
        assertEquals("Caries (Boca completa)", p.diagnostico)
    }

    @Test
    fun ata_los_hallazgos_que_lo_originaron() {
        val p = pe.saniape.app.data.staff.planTratamiento(linea(listOf("26", "27"), 120.0), procCon(null))
        assertEquals(listOf("h26", "h27"), p.hallazgoIds)
    }
}
