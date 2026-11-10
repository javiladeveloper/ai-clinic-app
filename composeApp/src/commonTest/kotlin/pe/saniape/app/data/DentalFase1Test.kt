package pe.saniape.app.data

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import pe.saniape.app.data.offline.OpPendiente
import pe.saniape.app.data.staff.DienteHallazgo
import pe.saniape.app.data.staff.HallazgoDental
import pe.saniape.app.data.staff.LineaPresupuesto
import pe.saniape.app.data.staff.ProcedimientoRef
import pe.saniape.app.data.staff.agruparPresupuesto
import pe.saniape.app.data.staff.buscarProcedimientoSugerido
import pe.saniape.app.data.staff.esHallazgoSinPieza
import pe.saniape.app.data.staff.hallazgosEnCola
import pe.saniape.app.data.staff.hallazgosParaBoca
import pe.saniape.app.data.staff.libresParaPresupuesto
import pe.saniape.app.data.staff.nombresSinPrecio
import pe.saniape.app.data.staff.presupuestoDesactualizado
import pe.saniape.app.data.staff.tratamientosEnCola

/**
 * Dental — Fase 1 (críticos de dinero). Mismos casos que la web
 * (.superpowers/sdd/dental-fase1-reglas.md, R1, R2 y R5 + la cola offline).
 */

private fun proc(id: String, nombre: String, precio: Double = 100.0) = ProcedimientoRef(
    id = id, nombre = nombre, precio = precio,
    precioPaquete = null, especialidadId = null, usaSesiones = false, tarifarios = emptyList(),
)

private fun reg(id: String, diente: String, hallazgoId: String, tratamientoId: String? = null) =
    DienteHallazgo(id = id, pacienteId = "pa", diente = diente, hallazgoId = hallazgoId, tratamientoId = tratamientoId)

/** R1 — el servicio sugerido no es el primero del abecedario. */
class ServicioSugeridoR1Test {
    @Test
    fun tokio_ortodoncia_brackets_va_a_la_instalacion_no_a_la_consulta() {
        val cat = listOf(proc("con", "Consulta de ortodoncia", 50.0), proc("ins", "Instalación de brackets", 1800.0))
        assertEquals("Instalación de brackets", buscarProcedimientoSugerido("Ortodoncia / Brackets", cat)?.nombre)
    }

    @Test
    fun urbina_extraccion_indicada_va_a_la_simple_no_a_la_de_tercer_molar() {
        val cat = listOf(proc("ter", "Extracción de tercer molar", 250.0), proc("sim", "Extracción simple", 100.0))
        assertEquals("Extracción simple", buscarProcedimientoSugerido("Extracción indicada", cat)?.nombre)
    }

    @Test
    fun solo_extraccion_de_tercer_molar_en_el_catalogo_se_usa_esa() {
        val cat = listOf(proc("ter", "Extracción de tercer molar", 250.0))
        assertEquals("Extracción de tercer molar", buscarProcedimientoSugerido("Extracción indicada", cat)?.nombre)
    }

    @Test
    fun caries_con_carillas_resina_y_resina_restauracion_va_a_la_restauracion() {
        val cat = listOf(proc("car", "Carillas Resina", 300.0), proc("res", "Resina / restauración", 90.0))
        assertEquals("Resina / restauración", buscarProcedimientoSugerido("Caries", cat)?.nombre)
    }

    @Test
    fun la_consulta_queda_si_es_lo_unico_que_coincide() {
        val cat = listOf(proc("con", "Consulta de ortodoncia", 50.0))
        assertEquals("Consulta de ortodoncia", buscarProcedimientoSugerido("Ortodoncia / Brackets", cat)?.nombre)
    }

    @Test
    fun extraccion_evita_lo_quirurgico_aunque_no_diga_simple() {
        val cat = listOf(
            proc("q", "Cirugía de cordal impactada", 400.0),
            proc("e", "Evaluación de extracción", 30.0),
            proc("x", "Exodoncia / Extracción dental", 120.0),
        )
        assertEquals("x", buscarProcedimientoSugerido("Extracción indicada", cat)?.id)
    }

    // ── Detalles de la revisión (web 0c2c0b11) ──
    @Test
    fun extraccion_tercera_molar_tambien_se_evita() {
        val cat = listOf(proc("a", "Extracción de tercera molar", 400.0), proc("b", "Extracción simple", 80.0))
        assertEquals("b", buscarProcedimientoSugerido("Extracción indicada", cat)?.id)
    }

    @Test
    fun caries_restauracion_antes_que_curacion_y_curacion_de_herida_no_cuenta() {
        val cat = listOf(
            proc("h", "Curación de herida", 20.0),
            proc("e", "Empaste provisional", 30.0),
            proc("r", "Resina / restauración", 90.0),
        )
        assertEquals("r", buscarProcedimientoSugerido("Caries", cat)?.id)
        assertEquals("e", buscarProcedimientoSugerido("Caries", cat.take(2))?.id)
    }

    @Test
    fun desempate_por_orden_del_catalogo() {
        val cat = listOf(proc("a", "Extracción simple adulto"), proc("b", "Extracción simple niño"))
        assertEquals("a", buscarProcedimientoSugerido("Extracción indicada", cat)?.id)
    }
}

/** R2 — un hallazgo de PIEZA guardado en 'BOCA' no se presupuesta. */
class HallazgoDePiezaEnBocaR2Test {
    private val caries = HallazgoDental(id = "hc", nombre = "Caries", color = "#dc2626")
    private val sarro = HallazgoDental(id = "hs", nombre = "Sarro", color = "#d97706", porBoca = true)
    private val procs = listOf(proc("res", "Resina / restauración", 90.0), proc("prof", "Profilaxis", 80.0))

    @Test
    fun urbina_caries_16_17_y_boca_resina_por_dos_piezas_y_la_de_boca_sin_pieza() {
        val filas = listOf(reg("a", "16", "hc"), reg("b", "17", "hc"), reg("c", "BOCA", "hc"))
        val (lineas, sin, sinPieza) = agruparPresupuesto(filas, listOf(caries), procs)
        assertEquals(1, lineas.size)
        val l = lineas.single()
        assertEquals(listOf("16", "17"), l.piezas)
        assertEquals(listOf("a", "b"), l.hallazgoIds)
        assertEquals(180.0, l.subtotal)
        assertFalse(l.porBoca)
        assertEquals(listOf("c"), sin.map { it.id })
        assertEquals(listOf("c"), sinPieza.map { it.id })
        assertTrue(esHallazgoSinPieza(sin.single(), caries))
    }

    @Test
    fun con_servicio_enlazado_y_solo_en_boca_tampoco_se_presupuesta() {
        val conServicio = caries.copy(procedimientoId = "res")
        val (lineas, _, sinPieza) = agruparPresupuesto(listOf(reg("c", "BOCA", "hc")), listOf(conServicio), procs)
        assertTrue(lineas.isEmpty())
        assertEquals(listOf("c"), sinPieza.map { it.id })
    }

    @Test
    fun el_panel_de_boca_ofrece_solo_hallazgos_de_boca_activos() {
        val cat = listOf(
            caries, sarro,
            HallazgoDental(id = "hg", nombre = "Gingivitis", color = "#d97706", porBoca = true, estado = "Inactivo"),
            HallazgoDental(id = "ha", nombre = "Ausente", color = "#6b7280", marcaAusente = true, porBoca = true),
        )
        assertEquals(listOf("hs"), hallazgosParaBoca(cat).map { it.id })
    }

    @Test
    fun un_hallazgo_de_boca_en_boca_sigue_cobrandose_una_vez() {
        val filas = listOf(reg("s", "BOCA", "hs"), reg("s2", "16", "hs"))
        val (lineas, sin, sinPieza) = agruparPresupuesto(filas, listOf(sarro), procs)
        assertEquals(80.0, lineas.single().subtotal)
        assertTrue(lineas.single().porBoca)
        assertTrue(sin.isEmpty())
        assertTrue(sinPieza.isEmpty())
        assertFalse(esHallazgoSinPieza(filas[0], sarro))
    }

    @Test
    fun la_caries_sola_en_boca_no_crea_linea() {
        val (lineas, sin) = agruparPresupuesto(listOf(reg("c", "BOCA", "hc")), listOf(caries), procs)
        assertTrue(lineas.isEmpty())
        assertEquals(1, sin.size)
    }

    @Test
    fun una_caries_en_pieza_no_es_sin_pieza() {
        assertFalse(esHallazgoSinPieza(reg("a", "16", "hc"), caries))
    }
}

/** R5 + cola offline (app C1, C2, C3). */
class CrearDesdePresupuestoTest {
    private fun linea(proc: String, nombre: String, subtotal: Double) =
        LineaPresupuesto(proc, nombre, "Caries", listOf("16"), listOf("h-$proc"), subtotal, subtotal)

    @Test
    fun sin_precio_no_se_crea_ni_la_linea_ni_el_extra() {
        val nombres = nombresSinPrecio(
            listOf(linea("res", "Resina", 90.0), linea("end", "Endodoncia", 0.0)),
            listOf(proc("orto", "Ortodoncia", 0.0), proc("bla", "Blanqueamiento", 300.0)),
        )
        assertEquals(listOf("Endodoncia", "Ortodoncia"), nombres)
        assertTrue(nombresSinPrecio(listOf(linea("res", "Resina", 90.0)), emptyList()).isEmpty())
    }

    private fun op(id: Long, payload: String, endpoint: String = "/api/staff/tratamiento/accion") =
        OpPendiente(id = id, idemKey = "k$id", tipo = "tratamiento:accion", endpoint = endpoint,
            payload = payload, dependeDe = null, idTemporal = null, intentos = 0)

    private val cola = listOf(
        op(1, """{"accion":"crear","pacienteId":"pa","procedimientoId":"res","precioAcordado":180,"diagnostico":"Caries en pieza(s) 16, 17","hallazgoIds":["a","b"]}"""),
        op(2, """{"accion":"crear","pacienteId":"otro","procedimientoId":"res","hallazgoIds":["z"]}"""),
        op(3, """{"accion":"editar","pacienteId":"pa","tratamientoId":"t","hallazgoIds":["y"]}"""),
        op(4, """{"accion":"crear","pacienteId":"pa","hallazgoIds":["w"]}""", endpoint = "/api/staff/cita/crear"),
        op(5, "no es json"),
    )

    @Test
    fun la_cola_solo_cuenta_crear_tratamiento_de_este_paciente() {
        assertEquals(setOf("a", "b"), hallazgosEnCola(cola, "pa"))
        val t = tratamientosEnCola(cola, "pa").single()
        assertEquals("Caries en pieza(s) 16, 17", t.diagnostico)
        assertEquals(180.0, t.precioAcordado)
    }

    @Test
    fun lo_encolado_no_vuelve_a_ofrecerse_en_el_presupuesto() {
        val filas = listOf(reg("a", "16", "hc"), reg("b", "17", "hc"), reg("c", "26", "hc"), reg("d", "27", "hc", tratamientoId = "t1"))
        assertEquals(listOf("c"), libresParaPresupuesto(filas, hallazgosEnCola(cola, "pa")).map { it.id })
    }

    @Test
    fun desactualizado_si_una_pieza_ya_esta_atada_borrada_o_en_cola() {
        val frescos = listOf(reg("a", "16", "hc"), reg("b", "17", "hc", tratamientoId = "t9"))
        assertTrue(presupuestoDesactualizado(listOf("a", "b"), frescos, emptySet()))   // atada en la web
        assertTrue(presupuestoDesactualizado(listOf("a", "x"), frescos, emptySet()))   // ya no existe
        assertTrue(presupuestoDesactualizado(listOf("a"), frescos, setOf("a")))         // ya en la cola
        assertFalse(presupuestoDesactualizado(listOf("a"), frescos, emptySet()))
        // Sin señal no se puede releer: se deja seguir (la cola y el servidor protegen).
        assertFalse(presupuestoDesactualizado(listOf("a"), null, emptySet()))
    }
}
