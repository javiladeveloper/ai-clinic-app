package pe.saniape.app.data

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive
import pe.saniape.app.data.staff.BorradorVacuna
import pe.saniape.app.data.staff.CATALOGO_VACUNAS
import pe.saniape.app.data.staff.CarneVacunas
import pe.saniape.app.data.staff.DOSIS_OPCIONES
import pe.saniape.app.data.staff.ESQUEMA_NACIONAL
import pe.saniape.app.data.staff.EstadoDosis
import pe.saniape.app.data.staff.GRUPOS_ESQUEMA
import pe.saniape.app.data.staff.VacunaPaciente
import pe.saniape.app.data.staff.carneVacunas
import pe.saniape.app.data.staff.cortoVacuna
import pe.saniape.app.data.staff.cuerpoVacuna
import pe.saniape.app.data.staff.htmlCarneVacunas
import pe.saniape.app.data.staff.nombreVacuna
import pe.saniape.app.data.staff.sumarDias
import pe.saniape.app.data.staff.sumarMeses
import pe.saniape.app.data.staff.textoResumenCarne
import pe.saniape.app.data.staff.vacunaDeJson
import pe.saniape.app.data.staff.validarVacuna

/**
 * 💉 Carné de vacunas: gemelo de `lib/__tests__/vacunas-esquema.test.ts` de la web
 * (Esquema Nacional de Inmunizaciones, NTS 246-MINSA/DGIESP-2026): mismas
 * fechas, mismos registros, mismos estados esperados.
 */
class VacunasEsquemaTest {

    private var n = 0
    private fun reg(codigo: String, dosis: String, fecha: String) = VacunaPaciente(id = "r${++n}", vacunaCodigo = codigo, dosis = dosis, fechaAplicacion = fecha)
    private fun estado(c: CarneVacunas, id: String) = c.dosis.first { it.dosis.id == id }.estado
    private val nac = "2026-01-10"

    @Test fun idsUnicosYGruposEnOrden() {
        val ids = ESQUEMA_NACIONAL.map { it.id }
        assertEquals(ids.size, ids.toSet().size)
        assertEquals(listOf("Recién nacido", "2 meses", "4 meses", "6 meses", "7 meses", "12 meses", "15 meses", "18 meses", "2 años", "3 años", "4 años", "9 años"), GRUPOS_ESQUEMA)
        ESQUEMA_NACIONAL.forEach { d -> d.acepta.forEach { c -> assertTrue(CATALOGO_VACUNAS.any { it.codigo == c }, c) } }
    }

    @Test fun edadesDeLaNorma() {
        fun edades(p: String) = ESQUEMA_NACIONAL.filter { it.id.startsWith(p) }.map { it.edadMeses }
        assertEquals(listOf(2, 4, 6), edades("penta"))
        assertEquals(listOf(2, 4, 6), edades("ipv"))
        assertEquals(listOf(2, 4), edades("rota"))
        assertEquals(listOf(2, 4, 12), edades("neumo"))
        assertEquals(listOf(12, 18), edades("spr"))
        assertEquals(listOf(15), edades("ama"))
        assertEquals(listOf(15), edades("hva"))
        assertEquals(listOf(18, 48), edades("ref"))
    }

    @Test fun fechas() {
        assertEquals("2026-02-28", sumarMeses("2026-01-31", 1))
        assertEquals("2024-02-29", sumarMeses("2024-01-31", 1))
        assertEquals("2027-03-15", sumarMeses("2026-03-15", 12))
        assertEquals("2027-01-02", sumarDias("2026-12-30", 3))
    }

    @Test fun recienNacidoSinNada() {
        val c = carneVacunas(nac, "2026-01-12", emptyList())
        assertEquals(EstadoDosis.PENDIENTE, estado(c, "bcg"))
        assertEquals(EstadoDosis.PENDIENTE, estado(c, "hvb-rn"))
        assertEquals(EstadoDosis.SEGUN_INDICACION, estado(c, "vrs"))
        assertEquals(EstadoDosis.FUTURA, estado(c, "penta-1"))
    }

    @Test fun hepatitisBHasta7Dias() {
        assertEquals(EstadoDosis.PENDIENTE, estado(carneVacunas(nac, "2026-01-17", emptyList()), "hvb-rn"))
        assertEquals(EstadoDosis.FUERA_DE_EDAD, estado(carneVacunas(nac, "2026-01-18", emptyList()), "hvb-rn"))
    }

    @Test fun proximaPendienteAtrasada() {
        assertEquals(EstadoDosis.PROXIMA, estado(carneVacunas(nac, "2026-02-20", emptyList()), "penta-1"))
        assertEquals(EstadoDosis.PENDIENTE, estado(carneVacunas(nac, "2026-03-20", emptyList()), "penta-1"))
        assertEquals(EstadoDosis.ATRASADA, estado(carneVacunas(nac, "2026-04-15", emptyList()), "penta-1"))
    }

    @Test fun rotavirusHasta8MesesCeroDias() {
        assertEquals(EstadoDosis.ATRASADA, estado(carneVacunas(nac, "2026-09-10", emptyList()), "rota-1"))
        assertEquals(EstadoDosis.ATRASADA, estado(carneVacunas(nac, "2026-09-10", emptyList()), "rota-2"))
        assertEquals(EstadoDosis.FUERA_DE_EDAD, estado(carneVacunas(nac, "2026-09-11", emptyList()), "rota-1"))
        assertEquals(EstadoDosis.FUERA_DE_EDAD, estado(carneVacunas(nac, "2026-09-11", emptyList()), "rota-2"))
    }

    @Test fun hexavalenteCubreIpv() {
        val c = carneVacunas(nac, "2026-07-20", listOf(reg("hexavalente", "1.ª", "2026-03-10"), reg("pentavalente", "2.ª", "2026-05-10")))
        val ipv1 = c.dosis.first { it.dosis.id == "ipv-1" }
        assertEquals(EstadoDosis.APLICADA, ipv1.estado)
        assertTrue(ipv1.viaHexavalente)
        assertEquals(EstadoDosis.APLICADA, estado(c, "penta-2"))
        assertEquals(EstadoDosis.ATRASADA, estado(c, "ipv-2"))
    }

    @Test fun etiquetasDelEsquema() {
        assertEquals(ESQUEMA_NACIONAL.map { it.dosis }.toSet(), DOSIS_OPCIONES.toSet())
    }

    @Test fun influenzaAnualVaALaDe3Anios() {
        val c = carneVacunas("2022-01-10", "2025-03-01", listOf(reg("influenza", "Anual", "2025-01-15")))
        assertEquals(EstadoDosis.APLICADA, estado(c, "flu-3a"))
        assertEquals(EstadoDosis.FUERA_DE_EDAD, estado(c, "flu-1"))
    }

    @Test fun carneTranscritoConEtiquetasLibres() {
        val c = carneVacunas(nac, "2027-02-20", listOf(reg("neumococo", "x", "2026-05-12"), reg("neumococo", "refuerzo", "2027-01-12")))
        assertEquals(EstadoDosis.ATRASADA, estado(c, "neumo-1"))
        assertEquals(EstadoDosis.APLICADA, estado(c, "neumo-2"))
        assertEquals(EstadoDosis.APLICADA, estado(c, "neumo-3"))
        val d = carneVacunas(nac, "2027-08-01", listOf(reg("spr", "-", "2027-07-12")))
        assertEquals(EstadoDosis.APLICADA, estado(d, "spr-2"))
        assertEquals(EstadoDosis.ATRASADA, estado(d, "spr-1"))
    }

    @Test fun otraEtiquetaCubrePorEdad() {
        val c = carneVacunas(nac, "2026-07-20", listOf(reg("neumococo", "Primera", "2026-03-10"), reg("neumococo", "x", "2026-05-12")))
        assertEquals(EstadoDosis.APLICADA, estado(c, "neumo-1"))
        assertEquals(EstadoDosis.APLICADA, estado(c, "neumo-2"))
        assertEquals(EstadoDosis.FUTURA, estado(c, "neumo-3"))
    }

    @Test fun loQueNoEsDelEsquemaVaAOtras() {
        val c = carneVacunas(nac, "2026-07-20", listOf(reg("otra", "Única", "2026-06-01"), reg("bcg", "Única", "2026-01-10")))
        assertEquals(listOf("otra"), c.otras.map { it.vacunaCodigo })
        assertEquals(EstadoDosis.APLICADA, estado(c, "bcg"))
    }

    @Test fun varicelaProgresivaYVph() {
        val c = carneVacunas("2014-01-10", "2026-03-01", emptyList())
        assertEquals(EstadoDosis.FUERA_DE_EDAD, estado(c, "vari-2"))
        assertEquals(EstadoDosis.PENDIENTE, estado(carneVacunas("2023-01-10", "2026-03-01", emptyList()), "vari-2"))
        assertEquals(EstadoDosis.PENDIENTE, estado(carneVacunas("2016-01-10", "2026-03-01", emptyList()), "vph"))
        assertEquals(EstadoDosis.ATRASADA, estado(c, "vph"))
    }

    @Test fun sinFechaDeNacimiento() {
        val c = carneVacunas(null, "2026-03-01", listOf(reg("bcg", "Única", "2026-01-10")))
        assertEquals(EstadoDosis.APLICADA, estado(c, "bcg"))
        assertEquals(EstadoDosis.SIN_FECHA, estado(c, "penta-1"))
        assertFalse(c.resumen.alDia)
        assertTrue("fecha de nacimiento" in textoResumenCarne(c, false))
    }

    @Test fun resumen() {
        val regs = listOf(reg("bcg", "Única", nac), reg("hvb", "Única", nac))
        val c = carneVacunas(nac, "2026-02-01", regs)
        assertTrue(c.resumen.alDia)
        assertEquals("Al día con el esquema nacional", textoResumenCarne(c, true))
        val tarde = carneVacunas(nac, "2026-05-01", regs)
        assertEquals(4, tarde.resumen.atrasadas) // penta-1, ipv-1, rota-1, neumo-1
        assertEquals("penta-1", tarde.resumen.siguientes.first().dosis.id)
        assertTrue(textoResumenCarne(tarde, true).startsWith("4 atrasadas"))
    }

    @Test fun validacion() {
        val hoy = "2026-10-16"
        assertEquals(emptyList(), validarVacuna(BorradorVacuna("p", "bcg", dosis = "Única", fechaAplicacion = "2026-10-16"), hoy))
        assertEquals(listOf("Elige la vacuna.", "Indica la dosis."), validarVacuna(BorradorVacuna("p", "xx"), hoy))
        assertTrue("La fecha no puede ser futura." in validarVacuna(BorradorVacuna("p", "bcg", dosis = "1", fechaAplicacion = "2026-10-17"), hoy))
        assertTrue("Escribe el nombre de la vacuna." in validarVacuna(BorradorVacuna("p", "otra", dosis = "1"), hoy))
        assertEquals(1, validarVacuna(BorradorVacuna("p", "bcg", dosis = "1", lote = "x".repeat(61)), hoy).size)
        assertTrue("La fecha es anterior al nacimiento." in validarVacuna(BorradorVacuna("p", "bcg", dosis = "1", fechaAplicacion = "2026-01-01"), hoy, "2026-02-01"))
    }

    @Test fun cuerpoYParseo() {
        val c = cuerpoVacuna(BorradorVacuna("p", "spr", dosis = " 1.ª ", lote = " L123 ", externa = false, establecimiento = "C.S. X", fechaAplicacion = "2026-10-01"))
        assertEquals("p", (c["pacienteId"] as JsonPrimitive).content)
        assertEquals("1.ª", (c["dosis"] as JsonPrimitive).content)
        assertEquals("L123", (c["lote"] as JsonPrimitive).content)
        assertEquals(JsonNull, c["establecimiento"]) // solo si es externa (como la web)
        val e = cuerpoVacuna(BorradorVacuna("p", "spr", dosis = "1.ª", externa = true, establecimiento = " C.S. Tacna "), id = "v1")
        assertEquals("v1", (e["id"] as JsonPrimitive).content)
        assertFalse("pacienteId" in e)
        assertEquals("C.S. Tacna", (e["establecimiento"] as JsonPrimitive).content)
        val fila = kotlinx.serialization.json.Json.parseToJsonElement(
            """{"id":"v","paciente_id":"p","vacuna_codigo":"spr","vacuna_nombre":null,"dosis":"1.ª","fecha_aplicacion":"2026-01-01","lote":null,"establecimiento":"C.S. Tacna","externa":true}""",
        ) as kotlinx.serialization.json.JsonObject
        val v = vacunaDeJson(fila)!!
        assertTrue(v.externa)
        assertEquals("C.S. Tacna", v.establecimiento)
        assertEquals("Meningococo B", nombreVacuna("otra", "Meningococo B"))
        assertEquals("SPR", cortoVacuna("spr"))
    }

    @Test fun hojaDelCarne() {
        val c = carneVacunas(nac, "2026-07-20", listOf(reg("hexavalente", "1.ª", "2026-03-10"), reg("otra", "Única", "2026-06-01").copy(vacunaNombre = "<Meningo B>")))
        val html = htmlCarneVacunas(c, nac, "2026-07-20", "Ana", "123", "BABYMED", null, null, "#0f766e")
        assertTrue("CARNÉ DE VACUNAS" in html)
        assertTrue("Con la hexavalente" in html)
        assertTrue("&lt;Meningo B&gt;" in html)
        assertTrue("#0f766e" in html)
    }
}
