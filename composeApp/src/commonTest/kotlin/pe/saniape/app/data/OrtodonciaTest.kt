package pe.saniape.app.data

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import pe.saniape.app.data.staff.ARCOS_COMUNES
import pe.saniape.app.data.staff.AprendidoPlan
import pe.saniape.app.data.staff.AtencionOrto
import pe.saniape.app.data.staff.BorradorControlOrto
import pe.saniape.app.data.staff.BorradorPlanOrto
import pe.saniape.app.data.staff.BrechaControl
import pe.saniape.app.data.staff.CitaOrto
import pe.saniape.app.data.staff.ControlOrtodoncia
import pe.saniape.app.data.staff.DIAS_CONTROL_ATRASADO
import pe.saniape.app.data.staff.ElasticoOrto
import pe.saniape.app.data.staff.FASES_ORTO
import pe.saniape.app.data.staff.MapaOrtodoncia
import pe.saniape.app.data.staff.PlanOrtodoncia
import pe.saniape.app.data.staff.SECUENCIA_ARCOS
import pe.saniape.app.data.staff.SIN_DATOS_CONTROL_ORTO
import pe.saniape.app.data.staff.TratamientoOrto
import pe.saniape.app.data.staff.aparatologiaSugerida
import pe.saniape.app.data.staff.controlOrtoDeJson
import pe.saniape.app.data.staff.copiarControlAnterior
import pe.saniape.app.data.staff.cuerpoControlOrto
import pe.saniape.app.data.staff.cuerpoPlanOrto
import pe.saniape.app.data.staff.elegirTratamientoPlan
import pe.saniape.app.data.staff.esAtencionOrtodoncia
import pe.saniape.app.data.staff.esEspecialidadOrtodoncia
import pe.saniape.app.data.staff.esPiezaFDI
import pe.saniape.app.data.staff.esServicioOrtodoncia
import pe.saniape.app.data.staff.estadoControles
import pe.saniape.app.data.staff.estadosFases
import pe.saniape.app.data.staff.faseEsperada
import pe.saniape.app.data.staff.frasesObservacionesOrto
import pe.saniape.app.data.staff.htmlFichaOrtodoncia
import pe.saniape.app.data.staff.mapaOrtodonciaDe
import pe.saniape.app.data.staff.mesesCompletos
import pe.saniape.app.data.staff.mismoArco
import pe.saniape.app.data.staff.normalizarElastico
import pe.saniape.app.data.staff.normalizarObjetivos
import pe.saniape.app.data.staff.normalizarPiezas
import pe.saniape.app.data.staff.ofreceCuidadosBrackets
import pe.saniape.app.data.staff.ofreceRegistrarControl
import pe.saniape.app.data.staff.ofrecerControlOrtoTrasCompletar
import pe.saniape.app.data.staff.ordenarControles
import pe.saniape.app.data.staff.pacienteEsOrtodoncia
import pe.saniape.app.data.staff.planOrtoDeJson
import pe.saniape.app.data.staff.progresionArcos
import pe.saniape.app.data.staff.progresoTratamiento
import pe.saniape.app.data.staff.proximaCitaAgendada
import pe.saniape.app.data.staff.siguienteArco
import pe.saniape.app.data.staff.sumarDiasOrto
import pe.saniape.app.data.staff.tendenciaHigiene
import pe.saniape.app.data.staff.textoClaseInicial
import pe.saniape.app.data.staff.textoElastico
import pe.saniape.app.data.staff.textoLigadura
import pe.saniape.app.data.staff.tipoAtencionOrtodoncia
import pe.saniape.app.data.staff.tipoOrtoDeCita
import pe.saniape.app.data.staff.validarControlOrto
import pe.saniape.app.data.staff.validarPlanOrto
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * 😁 Ortodoncia: gemelo de lib/__tests__/ortodoncia.test.ts de la web (los mismos
 * casos y valores esperados): qué es ortodoncia (incl. "brackets ungueales"),
 * validación, "repetir el anterior" SIN la fase, meses, fase esperada, controles
 * atrasados, progresión de arcos, higiene y textos.
 */
class OrtodonciaTest {

    private val p = "aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa"

    private fun plan(
        aparatologia: String = "brackets_metalicos",
        fechaInstalacion: String? = "2026-01-15",
        duracion: Int? = 24,
        fase: String = "alineacion",
        fechaRetiro: String? = null,
        claseMolarDer: String? = "II",
        claseMolarIzq: String? = "I",
    ) = PlanOrtodoncia(
        id = "pl", pacienteId = p, tratamientoId = "t", aparatologia = aparatologia, fechaInstalacion = fechaInstalacion,
        duracionEstimadaMeses = duracion, fase = fase, claseMolarDer = claseMolarDer, claseMolarIzq = claseMolarIzq, fechaRetiro = fechaRetiro,
    )

    private var n = 0
    private fun control(
        fecha: String = "2026-02-15",
        fase: String? = null,
        arcoSuperior: String? = null,
        arcoInferior: String? = null,
        higiene: String? = null,
        proximaSemanas: Int? = null,
    ) = ControlOrtodoncia(
        id = "c${n++}", planId = "pl", pacienteId = p, fecha = fecha, fase = fase, arcoSuperior = arcoSuperior,
        arcoInferior = arcoInferior, higiene = higiene, proximaSemanas = proximaSemanas,
    )

    // ── ¿Es ortodoncia? ──

    @Test fun serviciosDelCatalogoYVariantesReales() {
        for (s in listOf("Instalación de brackets", "Control mensual de ortodoncia", "Retenedores", "Ortodoncia — control mensual",
            "Brakets metálicos", "Alineadores invisibles", "Expansor palatino", "Ortopedia maxilar", "Consulta de ortodoncia")) {
            assertTrue(esServicioOrtodoncia(s), s)
        }
        for (s in listOf("Resina simple", "Profilaxis", "Ortonixia", "Ortopedia y traumatología", "Consulta general", "Brackets ungueales",
            "Corrección con bracket ungueal", null, "")) {
            assertFalse(esServicioOrtodoncia(s), s.toString())
        }
    }

    @Test fun especialidadPorNombreSoloDentalesOSinRubro() {
        assertTrue(esEspecialidadOrtodoncia("Ortodoncista"))
        assertFalse(esEspecialidadOrtodoncia("Ortopedia"))
        val m = mapaOrtodonciaDe(listOf(
            Triple("o", "Ortodoncia", "odontologia"),
            Triple("x", "Ortodoncia", null),
            Triple("f", "Ortodoncia (mal cargada)", "fisioterapia"),
            Triple("d", "Odontología", "odontologia"),
        ))
        assertEquals(listOf("o", "x"), m.ids)
        assertTrue(esAtencionOrtodoncia(m, servicio = "Resina", especialidadServicioId = "o"))
        assertFalse(esAtencionOrtodoncia(m, servicio = "Resina", especialidadServicioId = "d"))
    }

    @Test fun laPestanaSoloPacientesDentales() {
        val m = MapaOrtodoncia(listOf("o"))
        assertFalse(pacienteEsOrtodoncia(m, esDental = false, tienePlan = true))
        assertTrue(pacienteEsOrtodoncia(m, esDental = true, tienePlan = true))
        assertTrue(pacienteEsOrtodoncia(m, esDental = true, atenciones = listOf(AtencionOrto(servicio = "Instalación de brackets"))))
        assertFalse(pacienteEsOrtodoncia(m, esDental = true, atenciones = listOf(AtencionOrto(servicio = "Endodoncia"))))
        assertTrue(pacienteEsOrtodoncia(m, esDental = true, especialidadesDeQuienMira = listOf("o")))
        // Podología: "Brackets ungueales" nunca abre la pestaña.
        assertFalse(pacienteEsOrtodoncia(m, esDental = true, atenciones = listOf(AtencionOrto(servicio = "Brackets ungueales"))))
    }

    @Test fun tipoDeAtencionYCuandoSeOfreceRegistrarControl() {
        assertEquals("control", tipoAtencionOrtodoncia("Control mensual de ortodoncia"))
        assertEquals("instalacion", tipoAtencionOrtodoncia("Instalación de brackets"))
        assertEquals("consulta", tipoAtencionOrtodoncia("Consulta de ortodoncia"))
        assertEquals("consulta", tipoAtencionOrtodoncia("Estudio de ortodoncia"))
        assertEquals("retencion", tipoAtencionOrtodoncia("Retenedores"))
        assertEquals("control", tipoAtencionOrtodoncia("Tratamiento de ortodoncia", "Sesión"))
        assertEquals("consulta", tipoAtencionOrtodoncia("Tratamiento de ortodoncia", "Consulta"))
        assertNull(tipoAtencionOrtodoncia("Profilaxis", "Sesión"))
        assertTrue(ofreceRegistrarControl("control"))
        assertTrue(ofreceRegistrarControl("instalacion"))
        assertFalse(ofreceRegistrarControl("consulta"))
        assertFalse(ofreceRegistrarControl(null))
    }

    @Test fun agendaRegistrarControlConLaReglaDeLaWeb() {
        // Especialidad de ortodoncia + Sesión con un servicio genérico → control.
        assertEquals("control", tipoOrtoDeCita("Resina", "Sesión", especialidadEsOrto = true))
        assertNull(tipoOrtoDeCita("Resina", "Consulta", especialidadEsOrto = true))
        assertNull(tipoOrtoDeCita("Brackets ungueales", "Sesión", especialidadEsOrto = false))
        // Con tratamiento en la cita: se ofrece; sin tratamiento, solo si ya hay plan.
        assertTrue(ofrecerControlOrtoTrasCompletar(p, true, true, "control", tieneTratamiento = true, tienePlan = null))
        assertFalse(ofrecerControlOrtoTrasCompletar(p, true, true, "control", tieneTratamiento = false, tienePlan = null))
        assertFalse(ofrecerControlOrtoTrasCompletar(p, true, true, "control", tieneTratamiento = false, tienePlan = false))
        assertTrue(ofrecerControlOrtoTrasCompletar(p, true, true, "instalacion", tieneTratamiento = false, tienePlan = true))
        // Ni consulta, ni sin permiso, ni fuera de lo dental.
        assertFalse(ofrecerControlOrtoTrasCompletar(p, true, true, "consulta", tieneTratamiento = true, tienePlan = true))
        assertFalse(ofrecerControlOrtoTrasCompletar(p, false, true, "control", tieneTratamiento = true, tienePlan = true))
        assertFalse(ofrecerControlOrtoTrasCompletar(p, true, false, "control", tieneTratamiento = true, tienePlan = true))
        assertFalse(ofrecerControlOrtoTrasCompletar(null, true, true, "control", tieneTratamiento = true, tienePlan = true))
    }

    @Test fun aparatologiaSugeridaYElTratamientoDelPlan() {
        assertEquals("alineadores", aparatologiaSugerida("Alineadores Invisalign"))
        assertEquals("brackets_esteticos", aparatologiaSugerida("Brackets de zafiro"))
        assertEquals("autoligado", aparatologiaSugerida("Brackets autoligado Damon"))
        assertEquals("brackets_metalicos", aparatologiaSugerida("Instalación de brackets"))
        assertEquals("i", elegirTratamientoPlan(listOf(
            TratamientoOrto("c", "Control mensual de ortodoncia", "Activo"),
            TratamientoOrto("q", "Consulta de ortodoncia", "Activo"),
            TratamientoOrto("i", "Instalación de brackets", "Activo"),
        )))
        assertEquals("c", elegirTratamientoPlan(listOf(
            TratamientoOrto("i", "Instalación de brackets", "Alta"),
            TratamientoOrto("c", "Control mensual de ortodoncia", "Activo"),
        )))
        assertNull(elegirTratamientoPlan(emptyList()))
    }

    // ── Validación y cuerpos ──

    @Test fun planNuevoExigeTratamientoYAparatologia() {
        val e = validarPlanOrto(BorradorPlanOrto(pacienteId = p), nuevo = true)
        assertTrue("Elige el tratamiento de ortodoncia." in e)
        assertTrue("Elige la aparatología." in e)
        val b = BorradorPlanOrto(pacienteId = p, tratamientoId = "t", aparatologia = "alineadores",
            objetivos = listOf("Corregir clase II", " corregir CLASE ii ", ""))
        assertEquals(emptyList(), validarPlanOrto(b, nuevo = true))
        val c = cuerpoPlanOrto(b)
        assertEquals("ambas", c["arcadas"]!!.jsonPrimitive.content)
        assertEquals("alineacion", c["fase"]!!.jsonPrimitive.content)
        assertEquals(listOf("Corregir clase II"), c["objetivos"]!!.jsonArray.map { it.jsonPrimitive.content })
        assertFalse("clinica_id" in c)
        assertFalse("clinicaId" in c)
        // PATCH: con id y sin pacienteId.
        val patch = cuerpoPlanOrto(b, id = "pl")
        assertEquals("pl", patch["id"]!!.jsonPrimitive.content)
        assertFalse("pacienteId" in patch)
    }

    @Test fun planRangosClasesYFechas() {
        val e = validarPlanOrto(BorradorPlanOrto(
            pacienteId = p, aparatologia = "brackets_metalicos", duracionEstimadaMeses = 61, claseMolarDer = "IV",
            fechaInstalacion = "2026-05-01", fechaRetiro = "2026-04-01", arcadas = "medio", fase = "x", retencionPlanificada = "y",
        ))
        for (m in listOf("La duración estimada va de 1 a 60 meses.", "Clase molar derecha: elige I, II o III.",
            "El retiro no puede ser antes de la instalación.", "Arcadas inválidas.", "Fase inválida.", "Retención inválida.")) {
            assertTrue(m in e, m)
        }
        assertEquals(emptyList(), validarPlanOrto(BorradorPlanOrto(pacienteId = p, aparatologia = "brackets_metalicos", duracionEstimadaMeses = 24)))
        assertTrue("Máximo 20 objetivos." in validarPlanOrto(BorradorPlanOrto(pacienteId = p, aparatologia = "otro",
            objetivos = List(21) { "obj $it" })))
    }

    @Test fun sinDatosClinicosNoEsUnControl() {
        assertEquals(listOf(SIN_DATOS_CONTROL_ORTO), validarControlOrto(BorradorControlOrto(planId = "pl", fecha = "2026-03-01", proximaSemanas = 4)))
        assertEquals(emptyList(), validarControlOrto(BorradorControlOrto(planId = "pl", higiene = "buena")))
    }

    @Test fun controlCatalogosFdiYRangos() {
        val e = validarControlOrto(BorradorControlOrto(
            planId = "pl", ligaduraTipo = "dorada", cadeneta = "media", higiene = "excelente", dolor = "mucho",
            bracketsDespegados = listOf("11", "19"), proximaSemanas = 30, elasticos = listOf(ElasticoOrto("clase_iv")), fecha = "2026-13-01",
        ))
        for (m in listOf("Tipo de ligadura inválido.", "Cadeneta inválida.", "Higiene: elige buena, regular o mala.", "Dolor / molestias inválido.",
            "Brackets despegados: pieza inválida (numeración FDI, ej. 11, 36).", "La próxima cita va de 1 a 26 semanas.",
            "Algún elástico no tiene un tipo válido.", "Fecha inválida.")) {
            assertTrue(m in e, m)
        }
        assertTrue("Máximo 6 elásticos." in validarControlOrto(BorradorControlOrto(planId = "pl", elasticos = List(7) { ElasticoOrto("clase_ii") })))
    }

    @Test fun cuerpoDelControlPiezasOrdenadasYElasticosNormalizados() {
        val c = cuerpoControlOrto(BorradorControlOrto(
            planId = "pl", arcoSuperior = "  NiTi .016 ", bracketsDespegados = listOf("36", "11", "36"),
            elasticos = listOf(ElasticoOrto("clase_ii", "ambos", "3/16\"", "media", 22)), proximaSemanas = 5,
        ))
        assertEquals("pl", c["planId"]!!.jsonPrimitive.content)
        assertEquals("NiTi .016", c["arcoSuperior"]!!.jsonPrimitive.content)
        assertEquals(listOf("11", "36"), c["bracketsDespegados"]!!.jsonArray.map { it.jsonPrimitive.content })
        val el = c["elasticos"]!!.jsonArray[0].jsonObject
        assertEquals("clase_ii", el["tipo"]!!.jsonPrimitive.content)
        assertEquals("22", el["horas"]!!.jsonPrimitive.content)
        assertEquals("5", c["proximaSemanas"]!!.jsonPrimitive.content)
        // Sin profesional no se manda (el servidor pone el de la cita o el del usuario); nunca paciente ni clínica.
        assertFalse("terapeutaId" in c)
        assertFalse("pacienteId" in c)
        assertFalse("clinicaId" in c)
    }

    @Test fun repetirElAnteriorSinLoDelDiaNiLaFase() {
        val anterior = ControlOrtodoncia(
            id = "a", planId = "pl", fecha = "2026-02-15", fase = "cierre_espacios",
            arcoSuperior = "NiTi .016", arcoInferior = "NiTi .014", ligaduraTipo = "elasticas", ligaduraColor = "Azul",
            elasticos = listOf(ElasticoOrto("clase_ii", "der", "1/4\"", "ligera", 12)), cadeneta = "superior",
            bracketsDespegados = listOf("21"), higiene = "mala", dolor = "leve", observaciones = "Se recementó", proximaSemanas = 5,
        )
        // El control nuevo parte de la fase ACTUAL del plan.
        val nuevo = BorradorControlOrto(planId = "pl", fase = "alineacion", proximaSemanas = 4)
        val r = copiarControlAnterior(nuevo, anterior)
        assertEquals("NiTi .016", r.arcoSuperior)
        assertEquals("Azul", r.ligaduraColor)
        assertEquals("superior", r.cadeneta)
        assertEquals(5, r.proximaSemanas)
        assertEquals(listOf(ElasticoOrto("clase_ii", "der", "1/4\"", "ligera", 12)), r.elasticos)
        // La FASE no se copia: repetir un control viejo nunca hace retroceder (ni avanzar) el plan.
        assertEquals("alineacion", r.fase)
        assertEquals(emptyList(), r.bracketsDespegados)
        assertNull(r.higiene)
        assertNull(r.dolor)
        assertNull(r.observaciones)
        // Sin semanas en el anterior, quedan las que había.
        assertEquals(4, copiarControlAnterior(nuevo, anterior.copy(proximaSemanas = null)).proximaSemanas)
    }

    // ── Piezas FDI, elásticos, arcos ──

    @Test fun fdiPermanentesYTemporales() {
        for (x in listOf("11", "18", "21", "36", "48", "51", "55", "85")) assertTrue(esPiezaFDI(x), x)
        for (x in listOf("10", "19", "49", "56", "90", "1", "111", "BOCA")) assertFalse(esPiezaFDI(x), x)
        assertEquals(listOf("11", "36"), normalizarPiezas("36, 11 11;x"))
    }

    @Test fun elasticoLadoPorDefectoYLoInvalidoSeCae() {
        assertEquals(ElasticoOrto("clase_iii", "ambos", null, null, null),
            normalizarElastico(ElasticoOrto("clase_iii", lado = "x", tamano = "9\"", fuerza = "brutal", horas = 30)))
        assertNull(normalizarElastico(ElasticoOrto("nada")))
    }

    @Test fun objetivosUnicosSinDistinguirTildes() {
        assertEquals(listOf("Corregir mordida cruzada"), normalizarObjetivos(listOf("Corregir mordida cruzada", "corregir mordida cruzáda", "")))
    }

    @Test fun secuenciaDeArcosElSiguienteSugerido() {
        assertEquals("NiTi .016", siguienteArco("NiTi .014"))
        assertEquals("NiTi .019×.025", siguienteArco("niti 0.016x0.022"))
        assertEquals("Acero .019×.025", siguienteArco("NiTi .019×.025"))
        assertNull(siguienteArco("Acero .019×.025"))
        assertNull(siguienteArco("TMA .017×.025"))
        assertTrue(mismoArco("NiTi .016×.022", "niti .016x.022"))
        for (a in SECUENCIA_ARCOS) assertTrue(a in ARCOS_COMUNES, a)
    }

    // ── Progreso, fase esperada y adherencia ──

    @Test fun mesesCompletosDeCalendario() {
        assertEquals(0, mesesCompletos("2026-01-15", "2026-02-14"))
        assertEquals(1, mesesCompletos("2026-01-15", "2026-02-15"))
        assertEquals(11, mesesCompletos("2025-01-31", "2026-01-30"))
        assertEquals("2026-03-02", sumarDiasOrto("2026-02-26", 4))
    }

    @Test fun mesNDeMExcedidoRetiradoSinInstalar() {
        progresoTratamiento(plan(), "2026-08-20").let {
            assertEquals(7, it.meses); assertEquals(29, it.pct); assertEquals("Mes 8 de 24", it.texto); assertFalse(it.excedido)
        }
        progresoTratamiento(plan(), "2028-03-01").let {
            assertEquals(25, it.meses); assertEquals(100, it.pct); assertTrue(it.excedido); assertEquals("25 meses (estimado: 24)", it.texto)
        }
        progresoTratamiento(plan(fechaRetiro = "2027-12-20"), "2028-03-01").let {
            assertTrue(it.retirado); assertFalse(it.excedido); assertEquals("Retirado a los 23 meses", it.texto)
        }
        progresoTratamiento(plan(fechaInstalacion = null), "2026-08-20").let { assertNull(it.meses); assertEquals("Sin instalar", it.texto) }
        progresoTratamiento(plan(duracion = null), "2026-08-20").let { assertNull(it.pct); assertEquals("Mes 8", it.texto) }
    }

    @Test fun faseEsperadaPorElTiempo() {
        assertEquals(1.0, FASES_ORTO.take(4).sumOf { it.peso }, 1e-9)
        assertEquals("alineacion", faseEsperada(0, 24))
        assertEquals("alineacion", faseEsperada(7, 24)) // 29 %
        assertEquals("cierre_espacios", faseEsperada(8, 24)) // 33 %
        assertEquals("correccion", faseEsperada(14, 24)) // 58 %
        assertEquals("finalizacion", faseEsperada(20, 24)) // 83 %
        assertEquals("finalizacion", faseEsperada(30, 24))
        assertNull(faseEsperada(null, 24))
        assertNull(faseEsperada(5, null))
        // La barra: hechas / actual / pendientes.
        assertEquals(listOf("hecha", "actual", "pendiente", "pendiente", "pendiente"), estadosFases("cierre_espacios").map { it.second })
    }

    @Test fun controlAtrasadoBrechasYRetencion() {
        val cs = listOf(
            control(fecha = "2026-02-15", proximaSemanas = 4),
            control(fecha = "2026-03-15", proximaSemanas = 4),
            control(fecha = "2026-06-01", proximaSemanas = 5), // 11 semanas después
        )
        val e = estadoControles(cs, plan(), "2026-07-01")
        assertEquals("2026-06-01", e.ultimo?.fecha)
        assertEquals("2026-07-06", e.proximoEstimado)
        assertFalse(e.atrasado); assertFalse(e.toca); assertEquals(4, e.semanasDesdeUltimo)
        assertEquals(listOf(BrechaControl("2026-03-15", "2026-06-01", 11)), e.brechas)
        // Pasó la fecha estimada, aún sin llegar a 8 semanas → "toca".
        estadoControles(cs, plan(), "2026-07-10").let { assertTrue(it.toca); assertFalse(it.atrasado) }
        // 57 días sin control → atrasado.
        estadoControles(cs, plan(), sumarDiasOrto("2026-06-01", DIAS_CONTROL_ATRASADO + 1)).let { assertTrue(it.atrasado); assertFalse(it.toca) }
        assertFalse(estadoControles(cs, plan(), sumarDiasOrto("2026-06-01", DIAS_CONTROL_ATRASADO)).atrasado)
        // Sin controles: cuenta desde la instalación; las semanas, de la aparatología.
        estadoControles(emptyList(), plan(aparatologia = "alineadores"), "2026-02-01").let {
            assertEquals("2026-02-26", it.proximoEstimado); assertFalse(it.atrasado)
        }
        assertTrue(estadoControles(emptyList(), plan(), "2026-04-01").atrasado)
        // Retención: espaciados a propósito.
        estadoControles(cs, plan(fase = "retencion"), "2027-01-01").let { assertFalse(it.atrasado); assertFalse(it.toca) }
    }

    @Test fun progresionDeArcosSinRepetirElMismoSeguido() {
        val cs = listOf(
            control(fecha = "2026-02-15", arcoSuperior = "NiTi .014", arcoInferior = "NiTi .014"),
            control(fecha = "2026-03-15", arcoSuperior = "niti .014"),
            control(fecha = "2026-04-15", arcoSuperior = "NiTi .016", arcoInferior = "NiTi .016"),
        )
        assertEquals(listOf("NiTi .014" to "2026-02-15", "NiTi .016" to "2026-04-15"), progresionArcos(cs, superior = true))
        assertEquals(listOf("NiTi .014", "NiTi .016"), progresionArcos(cs, superior = false).map { it.first })
    }

    @Test fun tendenciaDeLaHigiene() {
        fun h(vararg xs: String) = xs.mapIndexed { i, x -> control(fecha = sumarDiasOrto("2026-01-01", i * 30), higiene = x) }
        tendenciaHigiene(h("buena")).let { assertEquals(listOf("buena"), it.serie); assertNull(it.tendencia) }
        assertEquals("mejora", tendenciaHigiene(h("mala", "mala", "regular", "buena", "buena", "buena")).tendencia)
        assertEquals("empeora", tendenciaHigiene(h("buena", "buena", "buena", "regular", "mala", "regular")).tendencia)
        assertEquals("estable", tendenciaHigiene(h("buena", "regular", "buena", "regular", "buena", "regular")).tendencia)
        assertEquals("empeora", tendenciaHigiene(h("buena", "mala")).tendencia)
        assertEquals(6, tendenciaHigiene(h("mala", "mala", "mala", "mala", "mala", "mala", "buena")).serie.size)
    }

    @Test fun ordenarElMasRecientePrimero() {
        val cs = listOf(control(fecha = "2026-02-01"), control(fecha = "2026-05-01"), control(fecha = "2026-03-01"))
        assertEquals(listOf("2026-05-01", "2026-03-01", "2026-02-01"), ordenarControles(cs).map { it.fecha })
        // A igual fecha manda el registrado después (created_at), como el servidor.
        val a = control(fecha = "2026-05-01").copy(createdAt = "2026-05-01T10:00:00Z")
        val b = control(fecha = "2026-05-01").copy(createdAt = "2026-05-01T11:00:00Z")
        assertEquals(listOf(b.id, a.id), ordenarControles(listOf(a, b)).map { it.id })
    }

    // ── Textos ──

    @Test fun elasticoLigaduraYClaseInicial() {
        assertEquals("Clase II · ambos lados · 3/16\" · media · 22 h/día", textoElastico(ElasticoOrto("clase_ii", "ambos", "3/16\"", "media", 22)))
        assertEquals("Elásticas azul", textoLigadura("elasticas", "Azul"))
        assertEquals("Sin ligadura", textoLigadura("sin_ligadura", "Azul"))
        assertNull(textoLigadura(null, null))
        assertEquals("Molar: der. II · izq. I", textoClaseInicial(plan()))
        assertNull(textoClaseInicial(plan(claseMolarDer = null, claseMolarIzq = null)))
    }

    @Test fun observacionesChipsBaseMasLoAprendido() {
        val f = frasesObservacionesOrto(listOf(AprendidoPlan("Paciente puntual", 2), AprendidoPlan("Se activó el arco", 1)))
        assertTrue(f.chips.any { it.texto == "Sin novedades" })
        assertEquals(true, f.chips.firstOrNull { it.texto == "Paciente puntual" }?.aprendida)
        assertFalse(f.porLinea) // modo "coma", como la web
    }

    @Test fun cuidadosConBracketsSoloTrasLaInstalacion() {
        val inst = CitaOrto("c", "2026-10-01", estado = "Completada", tipo = "Sesión", servicio = "Instalación de brackets")
        val ctl = inst.copy(servicio = "Control mensual de ortodoncia")
        assertTrue(ofreceCuidadosBrackets(true, "brackets_metalicos", inst))
        assertFalse(ofreceCuidadosBrackets(false, "brackets_metalicos", inst))
        assertFalse(ofreceCuidadosBrackets(true, "alineadores", inst))
        assertFalse(ofreceCuidadosBrackets(true, "brackets_metalicos", ctl))
        assertFalse(ofreceCuidadosBrackets(true, "brackets_metalicos", null))
    }

    @Test fun proximaCitaAgendadaIgnoraLasCerradas() {
        val cs = listOf(
            CitaOrto("a", "2026-10-20", "09:00", "Cancelada"),
            CitaOrto("b", "2026-10-25", "16:00", "Confirmada"),
            CitaOrto("c", "2026-10-22", "10:00", "Pendiente"),
            CitaOrto("d", "2026-10-01", "10:00", "Pendiente"),
        )
        assertEquals("c", proximaCitaAgendada(cs, "2026-10-15")?.id)
    }

    // ── JSON del API (backend viejo / campos raros) ──

    @Test fun filasDelApiTolerantes() {
        val pl = planOrtoDeJson(Json.parseToJsonElement("""{"id":"pl","paciente_id":"$p","aparatologia":"alineadores","objetivos":["A",null,""],"duracion_estimada_meses":18,"fecha_instalacion":"2026-01-15"}""").jsonObject)
        assertEquals(listOf("A"), pl?.objetivos)
        assertEquals("ambas", pl?.arcadas)
        assertEquals("alineacion", pl?.fase)
        assertEquals(18, pl?.duracionEstimadaMeses)
        assertNull(planOrtoDeJson(Json.parseToJsonElement("""{"id":"x"}""").jsonObject))
        val c = controlOrtoDeJson(Json.parseToJsonElement(
            """{"id":"c","plan_id":"pl","fecha":"2026-03-01","elasticos":[{"tipo":"clase_ii","horas":22},{"tipo":"raro"}],"brackets_despegados":["36","11","99"],"proxima_semanas":4}""",
        ).jsonObject)
        assertEquals(listOf(ElasticoOrto("clase_ii", "ambos", null, null, 22)), c?.elasticos)
        assertEquals(listOf("11", "36"), c?.bracketsDespegados)
        assertEquals(4, c?.proximaSemanas)
    }

    @Test fun laFichaImprimibleEscapaLoDeLaBase() {
        val html = htmlFichaOrtodoncia(
            plan().copy(objetivos = listOf("<b>x</b>")),
            listOf(control(fecha = "2026-02-15", arcoSuperior = "NiTi .014").copy(observaciones = "<script>alert(1)</script>")),
            "Clínica <Sonríe>", null, "javascript:alert(1)", "red", "Ana & Luis", "123", "Dra. Ortiz", "Instalación de brackets", "2026-10-16",
        )
        assertFalse("<script>" in html)
        assertTrue("&lt;script&gt;" in html)
        assertTrue("Clínica &lt;Sonríe&gt;" in html)
        assertTrue("Ana &amp; Luis" in html)
        assertFalse("javascript:" in html) // logo solo https
        assertTrue("#2c3e7a" in html) // color inválido → navy
        assertTrue("FICHA DE ORTODONCIA" in html)
        assertTrue("Mes 10 de 24" in html)
    }
}
