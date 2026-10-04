package pe.saniape.app.data.staff

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Ejercicios de apoyo: las reglas puras de la pestaña del staff. Gemelo de
 * `lib/__tests__/ejercicios.test.ts` de la web: mismos casos, mismos resultados
 * (lo que allá valida o escribe el servidor no se porta, así que no se prueba aquí).
 */
class EjerciciosTest {

    private fun ej(
        slug: String, nombre: String = "Ejercicio", zona: String = "08_lumbar_core", zonaNombre: String = "Lumbar y core",
        zonaOrden: Int = 8, objetivo: String = "fortalecimiento", posicion: String = "boca_arriba",
        materiales: List<String> = emptyList(), condiciones: List<String> = emptyList(), dificultad: String = "basico",
        estadoRevision: String = "pendiente",
    ) = EjercicioBiblioteca(
        id = slug, slug = slug, nombre = nombre, zona = zona, zonaNombre = zonaNombre, zonaOrden = zonaOrden,
        objetivo = objetivo, posicion = posicion, materiales = materiales, condiciones = condiciones, dificultad = dificultad,
        series = 3, repeticiones = 10, descansoSeg = 30, diasSemana = 5, estadoRevision = estadoRevision,
    )

    private val biblioteca = listOf(
        ej("puente", "Puente de glúteos", materiales = listOf("colchoneta"), condiciones = listOf("lumbalgia")),
        ej("remo", "Remo con banda", zona = "04_hombro", zonaNombre = "Hombro", zonaOrden = 4, posicion = "de_pie",
            materiales = listOf("banda_elastica"), condiciones = listOf("hombro_congelado"), dificultad = "intermedio"),
        ej("gato", "Gato-camello", objetivo = "movilidad", posicion = "cuadrupedia",
            condiciones = listOf("lumbalgia", "ciatica"), estadoRevision = "aprobado"),
        ej("flexion", "Flexión de hombro con bastón", zona = "04_hombro", zonaNombre = "Hombro", zonaOrden = 4,
            objetivo = "movilidad", materiales = listOf("baston", "silla"), condiciones = listOf("hombro_congelado")),
    )

    private fun slugs(f: FiltrosBiblioteca) = filtrarBiblioteca(biblioteca, f).map { it.slug }

    // ── Biblioteca: filtros ──

    @Test fun sinFiltrosDevuelveTodo() {
        assertEquals(4, filtrarBiblioteca(biblioteca, FiltrosBiblioteca()).size)
        assertFalse(FiltrosBiblioteca().hayFiltros)
        assertTrue(FiltrosBiblioteca(soloRevisados = true).hayFiltros)
    }

    @Test fun porRegionYPorMalestar() {
        assertEquals(listOf("remo", "flexion"), slugs(FiltrosBiblioteca(zona = "04_hombro")))
        assertEquals(listOf("gato"), slugs(FiltrosBiblioteca(condicion = "ciatica")))
        assertEquals(listOf("puente", "gato"), slugs(FiltrosBiblioteca(zona = "08_lumbar_core", condicion = "lumbalgia")))
    }

    @Test fun porObjetivoPosicionYNivel() {
        assertEquals(listOf("gato", "flexion"), slugs(FiltrosBiblioteca(objetivo = "movilidad")))
        assertEquals(listOf("remo"), slugs(FiltrosBiblioteca(posicion = "de_pie")))
        assertEquals(listOf("remo"), slugs(FiltrosBiblioteca(dificultad = "intermedio")))
    }

    @Test fun sinEquipoEspecialDejaLoQueHayEnCualquierCasa() {
        // Silla y colchoneta sí; la banda y el bastón no.
        assertEquals(listOf("puente", "gato"), slugs(FiltrosBiblioteca(equipo = "sin_equipo")))
        assertEquals(listOf("remo"), slugs(FiltrosBiblioteca(equipo = "banda_elastica")))
        assertTrue(sinEquipoEspecial(emptyList()))
        assertTrue(sinEquipoEspecial(listOf("silla", "pared")))
        assertFalse(sinEquipoEspecial(listOf("silla", "peso_ligero")))
        assertTrue(sinEquipoEspecial(null))
    }

    @Test fun soloLosRevisadosPorUnFisio() {
        assertEquals(listOf("gato"), slugs(FiltrosBiblioteca(soloRevisados = true)))
    }

    @Test fun elTextoBuscaSinTildesPorTodasLasPalabras() {
        assertEquals(listOf("flexion"), slugs(FiltrosBiblioteca(texto = "flexion")))
        assertEquals(listOf("puente"), slugs(FiltrosBiblioteca(texto = "GLUTEOS")))
        assertEquals(listOf("remo", "flexion"), slugs(FiltrosBiblioteca(texto = "hombro congelado")))
        assertEquals(listOf("gato"), slugs(FiltrosBiblioteca(texto = "ciática")))
        assertEquals(listOf("remo"), slugs(FiltrosBiblioteca(texto = "hombro banda")))
        assertTrue(slugs(FiltrosBiblioteca(texto = "rodilla")).isEmpty())
        assertEquals("flexion", normalizarTexto("  Flexión "))
        assertEquals("muneca", normalizarTexto("Muñeca"))
        assertEquals("", normalizarTexto(null))
    }

    @Test fun losFiltrosSeCombinan() {
        assertEquals(listOf("flexion"), slugs(FiltrosBiblioteca(zona = "04_hombro", objetivo = "movilidad", equipo = "baston")))
    }

    // ── Biblioteca: opciones de los filtros ──

    @Test fun lasRegionesSalenEnSuOrdenConSuTotal() {
        assertEquals(
            listOf(OpcionFiltro("04_hombro", "Hombro", 2), OpcionFiltro("08_lumbar_core", "Lumbar y core", 2)),
            opcionesDeBiblioteca(biblioteca).zonas,
        )
    }

    @Test fun malestaresYMaterialesSeAcotanALaRegionElegida() {
        assertEquals(listOf("ciatica", "hombro_congelado", "lumbalgia"), opcionesDeBiblioteca(biblioteca).condiciones.map { it.id })
        val lumbar = opcionesDeBiblioteca(biblioteca, "08_lumbar_core")
        assertEquals(listOf(OpcionFiltro("ciatica", "Ciática", 1), OpcionFiltro("lumbalgia", "Lumbalgia", 2)), lumbar.condiciones)
        assertEquals(listOf("colchoneta"), lumbar.materiales.map { it.id })
        // Las regiones NO se acotan: hay que poder cambiar de región.
        assertEquals(2, lumbar.zonas.size)
    }

    @Test fun nombresParaLaGente() {
        assertEquals("Hombro congelado", nombreCondicion("hombro_congelado"))
        assertEquals("EPOC", nombreCondicion("epoc"))
        assertEquals("Banda elástica", nombreMaterial("banda_elastica"))
        assertEquals("Cosa rara", nombreMaterial("cosa_rara"))
    }

    // ── Dosis ──

    @Test fun diasPorSemanaADiasConcretos() {
        assertEquals(listOf(1, 3, 5), diasPorDefecto(3))
        assertEquals(listOf(1, 2, 3, 4, 5), diasPorDefecto(5))
        assertEquals(listOf(1, 2, 3, 4, 5, 6, 7), diasPorDefecto(7))
        assertEquals(listOf(1, 2, 3, 4, 5), diasPorDefecto(null))
        assertEquals(listOf(2, 4), diasPorDefecto(2))
    }

    @Test fun laSugeridaSaleDeLaBibliotecaYCaeEnValoresSanos() {
        val d = dosisSugerida(EjercicioBiblioteca(id = "a", nombre = "A", series = 2, repeticiones = 15, descansoSeg = 45, diasSemana = 3))
        assertEquals(DosisEjercicio(2, 15, null, 45, 1, listOf(1, 3, 5)), d)
        val vacia = dosisSugerida(EjercicioBiblioteca(id = "b", nombre = "B"))
        assertEquals(DosisEjercicio(3, 10, null, 30, 1, listOf(1, 2, 3, 4, 5)), vacia)
        // Un valor disparatado de la biblioteca no entra al plan.
        val rara = dosisSugerida(EjercicioBiblioteca(id = "c", nombre = "C", series = 999, repeticiones = -4, descansoSeg = 30, diasSemana = 5))
        assertEquals(3, rara.series); assertEquals(10, rara.repeticiones)
    }

    @Test fun textosDeLaDosisYDeLosDias() {
        assertEquals("3 series × 10 repeticiones", textoDosis(3, 10, null))
        assertEquals("1 serie × 1 repetición", textoDosis(1, 1, null))
        assertEquals("3 series × 20 s sostenido", textoDosis(3, null, 20))
        assertEquals("2 series × 5 rep. de 10 s", textoDosis(2, 5, 10))
        assertEquals("3 series × — repeticiones", textoDosis(3, null, null))
        assertEquals("Lunes a viernes", textoDias(listOf(1, 2, 3, 4, 5)))
        assertEquals("Todos los días", textoDias(listOf(1, 2, 3, 4, 5, 6, 7)))
        assertEquals("Lun · Mié · Vie", textoDias(listOf(5, 1, 3)))
        assertEquals("Sáb · Dom", textoDias(listOf(6, 7)))
        assertEquals("Sin días", textoDias(emptyList()))
        assertEquals("Sin días", textoDias(null))
    }

    // ── Días y cumplimiento ──

    @Test fun diaIsoSinZonasHorarias() {
        assertEquals(7, diaIsoDe("2026-10-04")) // domingo
        assertEquals(1, diaIsoDe("2026-10-05")) // lunes
        assertEquals(3, diaIsoDe("2026-09-30")) // miércoles
        assertTrue(tocaEnFecha(listOf(1, 3, 5), "2026-09-30"))
        assertFalse(tocaEnFecha(listOf(1, 3, 5), "2026-10-04"))
        assertFalse(tocaEnFecha(null, "2026-10-04"))
        assertFalse(tocaEnFecha(listOf(1, 3, 5), "no-es-fecha"))
    }

    private val item = ItemPlanEjercicios(id = "i1", dias = listOf(1, 3, 5), createdAt = "2026-09-28T15:00:00Z")
    private val registros = listOf(
        RegistroEjercicio("i1", "2026-10-02", true, 2),
        RegistroEjercicio("i1", "2026-09-30", false),
        RegistroEjercicio("i1", "2026-09-28", true, 4),
        RegistroEjercicio("otro", "2026-10-02", true, 9),
    )

    @Test fun cuentaSoloLosDiasEnQueTocabaDesdeQueSeIndico() {
        // Lunes 28, miércoles 30 y viernes 2: tocaban 3; hizo 2 (el miércoles lo desmarcó).
        assertEquals(Adherencia(3, 2, 67, 2, "2026-10-02"), adherenciaDeItem(item, registros, "2026-10-04", 14))
    }

    @Test fun sinDiasProgramadosNoHayPorcentaje() {
        val nuevo = ItemPlanEjercicios(id = "i2", dias = listOf(1), createdAt = "2026-10-03T10:00:00Z")
        assertEquals(Adherencia(0, 0, null, null, null), adherenciaDeItem(nuevo, emptyList(), "2026-10-04"))
    }

    @Test fun laVentanaAcotaHaciaAtras() {
        val viejo = ItemPlanEjercicios(id = "i1", dias = listOf(1, 2, 3, 4, 5, 6, 7), createdAt = "2026-01-01T00:00:00Z")
        assertEquals(7, adherenciaDeItem(viejo, registros, "2026-10-04", 7).programados)
    }

    @Test fun elPlanSumaSusEjerciciosActivos() {
        val plan = PlanEjercicios(id = "p", items = listOf(
            item,
            ItemPlanEjercicios(id = "otro", dias = listOf(5), createdAt = "2026-09-28T00:00:00Z"),
            ItemPlanEjercicios(id = "quitado", dias = listOf(1, 2, 3, 4, 5, 6, 7), createdAt = "2026-09-28T00:00:00Z", activo = false),
        ))
        val a = adherenciaDePlan(plan, registros, "2026-10-04", 14)
        assertEquals(4, a.programados) // 3 del primero + el viernes del segundo
        assertEquals(3, a.hechos)
        assertEquals(75, a.porcentaje)
    }

    @Test fun elDiaEnQueSeIndicoSoloCuentaSiLoHizo() {
        val diario = ItemPlanEjercicios(id = "i3", dias = listOf(1, 2, 3, 4, 5), createdAt = "2026-09-28T15:00:00Z") // lunes
        val sin = adherenciaDeItem(diario, emptyList(), "2026-09-30")
        assertEquals(2, sin.programados); assertEquals(0, sin.hechos); assertEquals(0, sin.porcentaje)
        val con = adherenciaDeItem(diario, listOf(RegistroEjercicio("i3", "2026-09-28", true)), "2026-09-30")
        assertEquals(3, con.programados); assertEquals(1, con.hechos)
    }

    @Test fun elDiaEnQueSeIndicoEsElDeLimaNoElDeUtc() {
        // `created_at` viene en UTC: las 01:30 UTC del 29 son las 20:30 del 28 en Lima.
        assertEquals("2026-09-28", diaLimaDe("2026-09-29T01:30:00Z"))
        // Como lo manda Supabase (microsegundos y +00:00).
        assertEquals("2026-09-28", diaLimaDe("2026-09-29T01:30:00.123456+00:00"))
        assertEquals("2026-09-28", diaLimaDe("2026-09-28T15:00:00Z"))
        // Lo que no es un timestamp se recorta tal cual; sin valor, vacío.
        assertEquals("2026-09-28", diaLimaDe("2026-09-28"))
        assertEquals("", diaLimaDe(null))
        // Sesión de la tarde del lunes 28 (20:30 de Lima): ese lunes es "el día en que se
        // indicó" — solo cuenta si lo hizo — y desde el martes ya se le espera.
        val tarde = ItemPlanEjercicios(id = "i4", dias = listOf(1, 2, 3, 4, 5), createdAt = "2026-09-29T01:30:00Z")
        assertEquals(2, adherenciaDeItem(tarde, emptyList(), "2026-09-30").programados)   // martes 29 y miércoles 30
        val loHizo = adherenciaDeItem(tarde, listOf(RegistroEjercicio("i4", "2026-09-28", true)), "2026-09-30")
        assertEquals(3, loHizo.programados); assertEquals(1, loHizo.hechos)
    }

    @Test fun unPlanCerradoSeMideHastaElDiaEnQueSeCerro() {
        val plan = PlanEjercicios(id = "p", fechaFin = "2026-09-30", items = listOf(item))
        // Lunes 28 (lo hizo) y miércoles 30 (no): el viernes 2 ya es de la sesión siguiente.
        val a = adherenciaDePlan(plan, registros, "2026-10-04")
        assertEquals(2, a.programados); assertEquals(1, a.hechos); assertEquals(50, a.porcentaje)
        assertEquals("2026-10-04", hastaDePlan(null, "2026-10-04"))
        assertEquals("2026-09-30", hastaDePlan("2026-09-30", "2026-10-04"))
    }

    @Test fun losVigentesSonLosActivosElRestoEsHistorial() {
        fun p(id: String, estado: String) = PlanEjercicios(id = id, estado = estado)
        val (vigentes, anteriores) = separarPlanes(listOf(p("a", "finalizado"), p("b", "activo"), p("c", "pausado"), p("d", "activo")))
        assertEquals(listOf("b", "d"), vigentes.map { it.id })
        assertEquals(listOf("a", "c"), anteriores.map { it.id })
        assertTrue(separarPlanes(listOf(p("a", "pausado"))).first.isEmpty())
    }

    // ── Se indican por sesión, y al terminar el tratamiento ──

    private val sesiones = listOf(
        SesionDestino("s1", 1, "2026-09-21", "Completada", "t1"),
        SesionDestino("s2", 2, "2026-09-28", "Completada", "t1"),
        SesionDestino("s3", 3, "2026-10-05", "Planificada", "t1"),
        SesionDestino("x", 9, "2026-09-25", "Cancelada", "t1"),
    )
    private val trats = listOf(TratamientoDestino("t1", "Terapia lumbar", "Activo"))

    @Test fun seIndicaEnLasAtendidasYEnLaDeHoy() {
        // No en las futuras ni en las canceladas.
        assertEquals(listOf("s2", "s1"), sesionesParaIndicar(sesiones, "2026-10-04").map { it.id })
        assertEquals(listOf("s3", "s2", "s1"), sesionesParaIndicar(sesiones, "2026-10-05").map { it.id })
    }

    @Test fun elSelectorOfreceLasSesionesYAlTerminar() {
        val grupos = destinosEjercicios(sesiones, trats, "2026-10-04")
        assertEquals(1, grupos.size)
        val g = grupos[0]
        assertEquals("Terapia lumbar", g.nombre)
        assertEquals(
            listOf("Sesión #2 · 28/09/26 (la última)", "Sesión #1 · 21/09/26", "🏁 Al terminar el tratamiento (para casa)"),
            g.opciones.map { it.etiqueta },
        )
        assertEquals(listOf("sesion:s2", "sesion:s1", "alta:t1"), g.opciones.map { it.clave })
    }

    @Test fun sinSesionesAtendidasOSinTratamiento() {
        val g = destinosEjercicios(emptyList(), trats, "2026-10-04")[0]
        assertEquals(listOf("Antes de la primera sesión", "🏁 Al terminar el tratamiento (para casa)"), g.opciones.map { it.etiqueta })
        assertEquals(DestinoEjercicios("sesion", null, "t1"), g.opciones[0].destino)
        assertEquals("sesion:antes:t1", g.opciones[0].clave)
        val sin = destinosEjercicios(emptyList(), emptyList(), "2026-10-04")[0]
        assertEquals(listOf(OpcionDestino("alta:", DestinoEjercicios("alta", null, null), "Para casa")), sin.opciones)
        // Un tratamiento cancelado no cuenta.
        assertNull(destinosEjercicios(sesiones, listOf(TratamientoDestino("t1", "X", "Cancelado")), "2026-10-04")[0].tratamientoId)
    }

    @Test fun porDefectoLaUltimaSesionAtendida() {
        assertEquals(DestinoEjercicios("sesion", "s2", "t1"), destinoPorDefecto(sesiones, trats, "2026-10-04"))
        assertEquals(DestinoEjercicios("sesion", null, "t1"), destinoPorDefecto(emptyList(), trats, "2026-10-04"))
        assertEquals(DestinoEjercicios("alta", null, null), destinoPorDefecto(emptyList(), emptyList(), "2026-10-04"))
    }

    @Test fun viniendoDeCerrarUnaSesion() {
        // Esa sesión; si con ella terminó el tratamiento, "al terminar".
        assertEquals(DestinoEjercicios("sesion", "s1", "t1"), destinoPorDefecto(sesiones, trats, "2026-10-04", PedidoEjercicios(sesionId = "s1")))
        assertEquals(
            DestinoEjercicios("alta", null, "t1"),
            destinoPorDefecto(sesiones, trats, "2026-10-04", PedidoEjercicios("s2", "t1", alTerminar = true)),
        )
        // La agenda no siempre sabe la sesión: cae en la última atendida de ese tratamiento.
        assertEquals(DestinoEjercicios("sesion", "s2", "t1"), destinoPorDefecto(sesiones, trats, "2026-10-04", PedidoEjercicios(null, "t1")))
        // … y si con ella terminó el tratamiento, "al terminar", aunque no se sepa la sesión.
        assertEquals(
            DestinoEjercicios("alta", null, "t1"),
            destinoPorDefecto(sesiones, trats, "2026-10-04", PedidoEjercicios(null, "t1", alTerminar = true)),
        )
    }

    @Test fun tratamientoYaTerminadoPorDefectoParaSeguirEnCasa() {
        val fin = listOf(TratamientoDestino("t1", "Terapia lumbar", "Completado"))
        assertEquals(DestinoEjercicios("alta", null, "t1"), destinoPorDefecto(sesiones, fin, "2026-10-04"))
        assertEquals(listOf("alta:t1"), destinosEjercicios(emptyList(), fin, "2026-10-04")[0].opciones.map { it.clave })
    }

    private fun plan(
        id: String = "p", momento: String = "sesion", sesionId: String? = null, sesion: SesionDePlan? = null,
        tratamientoId: String? = "t1", estado: String = "activo",
    ) = PlanEjercicios(id = id, momento = momento, sesionId = sesionId, sesion = sesion, tratamientoId = tratamientoId, estado = estado)

    @Test fun cadaPlanDiceDeCuandoEsYHastaCuandoVale() {
        val deSesion = plan(sesionId = "s2", sesion = SesionDePlan("s2", 2, "2026-09-28"))
        assertEquals("Sesión #2 · 28/09/26", etiquetaPlan(deSesion))
        assertEquals("Hasta la próxima sesión", vigenciaPlan(deSesion.momento))
        assertEquals("Al terminar el tratamiento", etiquetaPlan(plan(momento = "alta")))
        assertEquals("Para seguir en casa", vigenciaPlan("alta"))
        assertEquals("Para casa", etiquetaPlan(plan(momento = "alta", tratamientoId = null)))
        assertEquals("Antes de la primera sesión", etiquetaPlan(plan()))
        assertEquals("Entre sesiones", etiquetaPlan(plan(tratamientoId = null)))
    }

    @Test fun indicarDeNuevoEnLaMismaSesionCaeEnElMismoPlan() {
        val p1 = plan(id = "p1", sesionId = "s1", estado = "finalizado")
        val p2 = plan(id = "p2", sesionId = "s2")
        val alta = plan(id = "pa", momento = "alta", estado = "pausado")
        val planes = listOf(p2, p1, alta)
        assertEquals("p1", planDeDestino(planes, DestinoEjercicios("sesion", "s1", "t1"))?.id)
        assertNull(planDeDestino(planes, DestinoEjercicios("sesion", "s3", "t1")))
        assertEquals("pa", planDeDestino(planes, DestinoEjercicios("alta", null, "t1"))?.id)
        assertNull(planDeDestino(planes, DestinoEjercicios("alta", null, "otro")))
        // Lo vigente del tratamiento es lo que se repite en la sesión siguiente.
        assertEquals("p2", vigenteDeTratamiento(planes, "t1")?.id)
        assertNull(vigenteDeTratamiento(planes, "otro"))
    }

    // ── Compartir ──

    @Test fun elMensajeDeWhatsAppSaludaPorElPrimerNombreYLlevaElEnlace() {
        val m = mensajeCompartirPlan("María Fernanda Quispe", "Clínica Test", "https://x.pe/ejercicios/abc", 3)
        assertTrue("Hola María," in m)
        assertTrue("tus 3 ejercicios de apoyo" in m)
        assertTrue("(Clínica Test)" in m)
        assertTrue("https://x.pe/ejercicios/abc" in m)
        assertTrue("Hola, aquí tienes tu ejercicio de apoyo" in mensajeCompartirPlan(null, null, "u", 1))
    }
}
