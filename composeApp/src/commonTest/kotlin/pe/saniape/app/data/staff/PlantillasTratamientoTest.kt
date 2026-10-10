package pe.saniape.app.data.staff

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Reglas del formulario "Nuevo tratamiento": plantillas, modalidad y primera sesión. */
class PlantillasTratamientoTest {

    private fun proc(
        id: String = "p1", precio: Double = 80.0, modo: String? = null, usaSesiones: Boolean = true,
        tarifarios: List<TarifarioRef> = emptyList(), precioPaquete: Double? = null, sugerido: Double? = null,
    ) = ProcedimientoRef(
        id = id, nombre = "Servicio $id", precio = precio, precioPaquete = precioPaquete, especialidadId = "e1",
        usaSesiones = usaSesiones, tarifarios = tarifarios, modoCobro = modo, precioUnitarioSugerido = sugerido,
    )

    private fun plantilla(
        procId: String? = "p1", modalidad: String? = null, total: Int? = null, paquete: Double? = null,
        porSesion: Double? = null, unidades: Int? = null, unitario: Double? = null,
        indicaciones: String? = null, controlDias: Int? = null,
    ) = PlantillaRef(
        id = "pl-$procId-$total", nombre = "Plantilla", procedimientoId = procId, terapeutaId = null,
        modalidad = modalidad, totalSesiones = total, precioPaquete = paquete, precioPorSesion = porSesion,
        cantidadUnidades = unidades, precioUnitario = unitario, diagnostico = null, tecnicasSesion = null,
        indicaciones = indicaciones, controlDias = controlDias,
    )

    private val tar10 = TarifarioRef("t10", 10, 700.0)
    private val tar6 = TarifarioRef("t6", 6, 450.0)

    // ── Tipo y modalidad ──

    @Test
    fun tipoSaleDelModoEfectivoDelServicio() {
        assertEquals(TipoTratamientoNuevo.SESIONES, tipoTratamientoDe(proc()))
        assertEquals(TipoTratamientoNuevo.CONSULTA, tipoTratamientoDe(proc(usaSesiones = false, precio = 0.0)))
        assertEquals(TipoTratamientoNuevo.SERVICIO_UNICO, tipoTratamientoDe(proc(usaSesiones = false, precio = 120.0)))
        assertEquals(TipoTratamientoNuevo.UNIDADES, tipoTratamientoDe(proc(modo = "unidades")))
        // El servicio manda sobre la especialidad (antes: Paquete para un 'simple' gratis
        // en una especialidad con sesiones; Consulta para uno 'sesiones' en una sin sesiones).
        assertEquals(TipoTratamientoNuevo.CONSULTA, tipoTratamientoDe(proc(modo = "simple", precio = 0.0, usaSesiones = true)))
        assertEquals(TipoTratamientoNuevo.SESIONES, tipoTratamientoDe(proc(modo = "sesiones", usaSesiones = false)))
        // Precio propio de la sede: decide servicio único vs consulta.
        assertEquals(TipoTratamientoNuevo.SERVICIO_UNICO, tipoTratamientoDe(proc(modo = "simple", precio = 0.0), precioSede = 50.0))
    }

    @Test
    fun nuncaNaceUnaModalidadIncompatibleConElModoDeCobro() {
        assertEquals("Sesión suelta", modalidadAGuardar(TipoTratamientoNuevo.SESIONES, "Sesión suelta"))
        assertEquals("Paquete", modalidadAGuardar(TipoTratamientoNuevo.SESIONES, "Unidades"))
        assertEquals("Paquete", modalidadAGuardar(TipoTratamientoNuevo.SESIONES, "Sesiones"))
        assertEquals("Paquete", modalidadAGuardar(TipoTratamientoNuevo.SESIONES, null))
        assertEquals("Unidades", modalidadAGuardar(TipoTratamientoNuevo.UNIDADES, "Paquete"))
        assertEquals("Consulta", modalidadAGuardar(TipoTratamientoNuevo.SERVICIO_UNICO, "Paquete"))
        assertEquals("Consulta", modalidadAGuardar(TipoTratamientoNuevo.CONSULTA, "Sesión suelta"))
    }

    // ── Prefill del servicio ──

    @Test
    fun servicioPrellenaTarifarioDe10YPrecioDeSede() {
        val f = camposDeServicio(proc(tarifarios = listOf(tar6, tar10)))
        assertEquals("10", f.totalSesiones)
        assertEquals("700.0", f.precioPaquete)
        assertEquals("80.0", f.precioPorSesion)
        assertEquals("", f.precioAcordado)
        val sede = camposDeServicio(proc(sugerido = 3.0), precioSede = 95.0)
        assertEquals("95.0", sede.precioPorSesion)
        assertEquals("95.0", sede.precioUnitario)
        assertEquals("3.0", camposDeServicio(proc(sugerido = 3.0)).precioUnitario)
        // La modalidad elegida se conserva si el tipo la admite.
        assertEquals("Sesión suelta", camposDeServicio(proc(), modalidad = "Sesión suelta").modalidad)
        assertEquals("Unidades", camposDeServicio(proc(modo = "unidades"), modalidad = "Sesión suelta").modalidad)
    }

    // ── Aplicar plantilla ──

    @Test
    fun aplicarPlantillaPoneSusValoresSobreLosDelServicio() {
        val p = proc(tarifarios = listOf(tar10))
        val r = aplicarPlantilla(camposDeServicio(p), plantilla(modalidad = "Paquete", total = 8, paquete = 520.0),
            TipoTratamientoNuevo.SESIONES, p.tarifarios, mismaMoneda = true)
        assertEquals("8", r.campos.totalSesiones)
        assertEquals("520.0", r.campos.precioPaquete)
        assertEquals("80.0", r.campos.precioPorSesion)   // no lo trae: el del servicio
        assertFalse(r.preciosOmitidos)
    }

    @Test
    fun cambiarDePlantillaNoArrastraValoresDeLaAnterior() {
        val p = proc(tarifarios = listOf(tar10))
        val base = camposDeServicio(p)
        val a = aplicarPlantilla(base, plantilla(modalidad = "Sesión suelta", porSesion = 60.0), TipoTratamientoNuevo.SESIONES, p.tarifarios, mismaMoneda = true)
        assertEquals("Sesión suelta", a.campos.modalidad)
        // B se aplica sobre los valores FRESCOS del servicio, no sobre A.
        val b = aplicarPlantilla(base, plantilla(total = 10), TipoTratamientoNuevo.SESIONES, p.tarifarios, mismaMoneda = true)
        assertEquals("Paquete", b.campos.modalidad)
        assertEquals("80.0", b.campos.precioPorSesion)
        assertEquals("700.0", b.campos.precioPaquete)
        // "Sin plantilla" = los campos del servicio tal cual.
        assertEquals(base, camposDeServicio(p))
    }

    @Test
    fun plantillaConModalidadIncompatibleNoLaImpone() {
        val p = proc(modo = "unidades")
        val r = aplicarPlantilla(camposDeServicio(p), plantilla(modalidad = "Paquete", unidades = 2000, unitario = 4.5),
            TipoTratamientoNuevo.UNIDADES, emptyList(), mismaMoneda = true)
        assertEquals("Unidades", r.campos.modalidad)
        assertEquals("2000", r.campos.cantidadUnidades)
        assertEquals("4.5", r.campos.precioUnitario)
        val s = aplicarPlantilla(camposDeServicio(proc()), plantilla(modalidad = "Unidades"), TipoTratamientoNuevo.SESIONES, emptyList(), mismaMoneda = true)
        assertEquals("Paquete", s.campos.modalidad)
    }

    @Test
    fun conOtroNumeroDeSesionesElPaqueteSaleDelTarifarioDeEseTamanoONadie() {
        val p = proc(tarifarios = listOf(tar10, tar6))
        val conTar = aplicarPlantilla(camposDeServicio(p), plantilla(total = 6), TipoTratamientoNuevo.SESIONES, p.tarifarios, mismaMoneda = true)
        assertEquals("6", conTar.campos.totalSesiones)
        assertEquals("450.0", conTar.campos.precioPaquete)
        val sinTar = aplicarPlantilla(camposDeServicio(p), plantilla(total = 4), TipoTratamientoNuevo.SESIONES, p.tarifarios, mismaMoneda = true)
        assertEquals("", sinTar.campos.precioPaquete)        // nunca el precio de 10 para 4
        assertFalse(sinTar.preciosOmitidos)
    }

    @Test
    fun servicioUnicoLlevaElPrecioDeLaPlantillaAlAcordado() {
        val p = proc(modo = "simple", precio = 300.0)
        val r = aplicarPlantilla(camposDeServicio(p), plantilla(porSesion = 250.0), TipoTratamientoNuevo.SERVICIO_UNICO, emptyList(), mismaMoneda = true)
        assertEquals("Consulta", r.campos.modalidad)
        assertEquals("250.0", r.campos.precioAcordado)
        assertEquals("300.0", r.campos.precioPorSesion)   // el base del servicio no se toca
    }

    // ── Multimoneda ──

    @Test
    fun enOtraMonedaNoSeCopianLosPreciosDeLaPlantilla() {
        val p = proc(tarifarios = listOf(tar10))
        val pl = plantilla(modalidad = "Paquete", total = 8, paquete = 520.0, porSesion = 70.0)
        // Sede en BOB con precio propio: ese, nada de la plantilla ni del servicio (están en PEN).
        val conSede = aplicarPlantilla(camposDeServicio(p, 60.0), pl, TipoTratamientoNuevo.SESIONES, p.tarifarios, mismaMoneda = false, precioSede = 60.0)
        assertEquals("8", conSede.campos.totalSesiones)
        assertEquals("", conSede.campos.precioPaquete)
        assertEquals("60.0", conSede.campos.precioPorSesion)
        assertTrue(conSede.preciosOmitidos)
        // Sin precio de sede: vacío para escribirlo.
        val sinSede = aplicarPlantilla(camposDeServicio(p), pl, TipoTratamientoNuevo.SESIONES, p.tarifarios, mismaMoneda = false)
        assertEquals("", sinSede.campos.precioPorSesion)
        assertEquals("", sinSede.campos.precioPaquete)
        // Misma moneda con precio de sede: la plantilla (combo negociado) manda, como la web.
        val misma = aplicarPlantilla(camposDeServicio(p, 60.0), pl, TipoTratamientoNuevo.SESIONES, p.tarifarios, mismaMoneda = true, precioSede = 60.0)
        assertEquals("520.0", misma.campos.precioPaquete)
        assertEquals("70.0", misma.campos.precioPorSesion)
        assertFalse(misma.preciosOmitidos)

        assertTrue(mismaMonedaQueLaClinica("PEN", listOf("PEN", "pen")))
        assertFalse(mismaMonedaQueLaClinica("PEN", listOf("PEN", "BOB")))
        assertNull(avisoPreciosPlantilla(false, "PEN", "BOB", null))
        assertTrue(avisoPreciosPlantilla(true, "PEN", "BOB", 60.0)!!.contains("precio del servicio en esta sede"))
        assertTrue(avisoPreciosPlantilla(true, "PEN", "BOB", null)!!.contains("escribe el precio en BOB"))
    }

    @Test
    fun profesionalQueNoAtiendeElServicio() {
        assertTrue(profesionalAtiende("e1", listOf("e1", "e2")))
        assertFalse(profesionalAtiende("e1", listOf("e2")))
        assertTrue(profesionalAtiende(null, listOf("e2")))
        assertTrue(profesionalAtiende("e1", emptyList()))
    }

    // ── Qué plantillas se ofrecen ──

    @Test
    fun noSeOfrecenPlantillasSinServicioOConServicioInactivo() {
        val activos = listOf(proc("p1"), proc("p2"))
        val lista = listOf(plantilla("p1"), plantilla(null), plantilla("p9"), plantilla(""), plantilla("p2"))
        assertEquals(listOf("p1", "p2"), plantillasOfrecibles(lista, activos).map { it.procedimientoId })
        assertEquals(listOf("p1"), plantillasOfrecibles(lista, activos, apagadosEnSede = setOf("p2")).map { it.procedimientoId })
    }

    // ── Valores clínicos ──

    @Test
    fun indicacionesYControlDiasSeAplicanDesdeHoyDeLaSede() {
        assertEquals("2026-10-16", proximoControlDePlantilla(7, "2026-10-09"))
        assertEquals("2026-11-08", proximoControlDePlantilla(30, "2026-10-09"))
        assertEquals("2026-10-09", proximoControlDePlantilla(0, "2026-10-09"))
        assertNull(proximoControlDePlantilla(null, "2026-10-09"))
        assertNull(proximoControlDePlantilla(-1, "2026-10-09"))
        // Con protocolo de controles, lo programa el protocolo.
        assertNull(proximoControlDePlantilla(7, "2026-10-09", servicioConProtocolo = true))
        assertEquals("Ibuprofeno 400", medicacionDePlantilla("  Ibuprofeno 400 "))
        assertNull(medicacionDePlantilla("   "))
    }

    // ── Primera sesión ──

    @Test
    fun seOfreceAgendarSiNoSeAgendoYHayId() {
        assertTrue(ofrecerAgendarTrasCrear("t1", primeraAgendada = false))
        assertFalse(ofrecerAgendarTrasCrear("t1", primeraAgendada = true))
        assertFalse(ofrecerAgendarTrasCrear(null, primeraAgendada = false))   // cola offline: sin id
        assertFalse(ofrecerAgendarTrasCrear("", primeraAgendada = false))
    }

    @Test
    fun laCitaQueSeOfreceDependeDelTipo() {
        assertEquals("Sesión", tipoCitaPrimera(TipoTratamientoNuevo.SESIONES))
        assertEquals("Sesión", tipoCitaPrimera(TipoTratamientoNuevo.UNIDADES))
        assertEquals("Sesión", tipoCitaPrimera(TipoTratamientoNuevo.SERVICIO_UNICO))
        assertEquals("Sesión", tipoCitaPrimera(TipoTratamientoNuevo.CONSULTA))
        assertTrue(textoAgendarPrimera(TipoTratamientoNuevo.SESIONES).contains("primera sesión"))
        assertTrue(textoAgendarPrimera(TipoTratamientoNuevo.UNIDADES).contains("intervención"))
        assertTrue(textoAgendarPrimera(TipoTratamientoNuevo.SERVICIO_UNICO).contains("la cita"))
        assertNotNull(modalidadesDe(TipoTratamientoNuevo.CONSULTA).singleOrNull())
    }
}
