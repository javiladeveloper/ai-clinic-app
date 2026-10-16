package pe.saniape.app.data.staff

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Gemelo de lib/__tests__/presupuestos.test.ts (web): los mismos casos con los
 * mismos números. Si la web cambia una regla de dinero, este test avisa.
 */
class PresupuestosTest {

    // El caso del Dr. Villarroel (estética): toxina por unidades + 2 rellenos + 3 peelings.
    private val toxina = ItemPresupuesto("tox", "11111111-1111-4111-8111-111111111111", "Toxina botulínica", "Tercio superior", "unidades", "unidades", 50, 15.0, 18.0)
    private val relleno = ItemPresupuesto("rel", "22222222-2222-4222-8222-222222222222", "Ácido hialurónico", null, "simple", null, 2, 900.0, 900.0)
    private val peeling = ItemPresupuesto("pee", "33333333-3333-4333-8333-333333333333", "Peeling químico", null, "sesiones", null, 3, 200.0, 250.0, cobro = "sueltas")
    // Paquete del catálogo: 10 sesiones a S/ 500 (por sueltas serían 10 × S/ 60 = S/ 600).
    private val paquete10 = ItemPresupuesto("paq", "55555555-5555-4555-8555-555555555555", "Fisioterapia", null, "sesiones", null, 10, 500.0, 500.0, cobro = "paquete", precioSesion = 60.0)

    private fun presu(
        items: List<ItemPresupuesto> = listOf(toxina, relleno, peeling),
        tipo: String? = "porcentaje", valor: Double = 10.0, estado: String = "Enviado",
        vigencia: String = "2026-10-31", convertido: String? = null, sede: String? = null,
    ) = Presupuesto(
        id = "p-1", pacienteId = "pac-1", sedeId = sede, moneda = "PEN", citaId = "cita-1", terapeutaId = "ter-1", especialidadId = null,
        titulo = "Plan facial", items = items, descuentoTipo = tipo, descuentoValor = valor,
        notasPaciente = null, notasInternas = "Paciente sensible al precio", vigenciaHasta = vigencia, estado = estado,
        enviadoAt = "2026-10-16T15:00:00Z", convertidoAt = convertido, creadoPor = "u-medico", createdAt = "2026-10-16T15:00:00Z",
    )

    // ── totales ──

    @Test fun subtotalPorItemRedondeadoALaMoneda() {
        assertEquals(750.0, subtotalItem(toxina, "PEN"))
        assertEquals(100.01, subtotalItem(relleno.copy(cantidad = 3, precioUnitario = 33.335), "PEN"))
        assertEquals(1001.0, subtotalItem(relleno.copy(cantidad = 3, precioUnitario = 333.6), "CLP"))
    }

    @Test fun descuentoPorcentajeTotalYAhorro() {
        val t = totalesPresupuesto(listOf(toxina, relleno, peeling), "porcentaje", 10.0, "PEN")
        assertEquals(TotalesPresupuesto(3150.0, 315.0, 2835.0, 3450.0, 615.0), t)
    }

    @Test fun descuentoEnMontoNuncaMasQueElSubtotal() {
        assertEquals(30.0, montoDescuento(100.0, "monto", 30.0, "PEN"))
        assertEquals(100.0, montoDescuento(100.0, "monto", 500.0, "PEN"))
        assertEquals(100.0, montoDescuento(100.0, "porcentaje", 150.0, "PEN"))
        assertEquals(0.0, montoDescuento(100.0, null, 10.0, "PEN"))
        assertEquals(0.0, montoDescuento(100.0, "monto", -5.0, "PEN"))
        assertEquals(0.0, montoDescuento(0.0, "porcentaje", 10.0, "PEN"))
        assertEquals(15.0, montoDescuento(99.99, "porcentaje", 15.0, "PEN"))
        assertEquals(150.0, montoDescuento(1001.0, "porcentaje", 15.0, "CLP"))
    }

    @Test fun sinListaPorSeparadoUsaElNegociado() {
        val t = totalesPresupuesto(listOf(itemLibre("Masaje de drenaje", 120.0, 2)), null, 0.0, "PEN")
        assertEquals(TotalesPresupuesto(240.0, 0.0, 240.0, 240.0, 0.0), t)
    }

    @Test fun textoDelTotalConAhorro() {
        assertEquals("S/ 1,800.00 · por separado S/ 2,100.00", textoTotalConAhorro(TotalesPresupuesto(0.0, 0.0, 1800.0, 2100.0, 300.0), "PEN"))
        assertEquals("S/ 1,800.00", textoTotalConAhorro(TotalesPresupuesto(0.0, 0.0, 1800.0, 1800.0, 0.0), "PEN"))
        assertEquals("Bs 500.00 · por separado Bs 600.00", textoTotalConAhorro(TotalesPresupuesto(0.0, 0.0, 500.0, 600.0, 100.0), "BOB"))
    }

    // ── prorrateo ──

    @Test fun prorrateoCuadraExacto() {
        val m = prorratearDescuento(listOf(750.0, 1800.0, 600.0), 315.0, "PEN")
        assertEquals(listOf(675.0, 1620.0, 540.0), m)
        assertEquals(2835.0, m.sum())
    }

    @Test fun prorrateoMayorResto() {
        val m = prorratearDescuento(listOf(100.0, 100.0, 100.0), 100.0, "PEN")
        assertEquals(20000L, redondeoJs(m.sum() * 100).toLong())
        assertTrue(m.all { it in 66.66..66.67 })
    }

    @Test fun prorrateoNuncaNegativo() {
        assertEquals(listOf(0.0, 0.0, 0.0), prorratearDescuento(listOf(10.0, 0.0, 5.0), 15.0, "PEN"))
        assertEquals(listOf(0.0, 0.0), prorratearDescuento(listOf(10.0, 5.0), 100.0, "PEN"))
    }

    @Test fun prorrateoClpEnEnteros() {
        val m = prorratearDescuento(listOf(1000.0, 2000.0, 3001.0), 1000.0, "CLP")
        assertTrue(m.all { it == kotlin.math.floor(it) })
        assertEquals(5001.0, m.sum())
    }

    @Test fun prorrateoAleatorioSiempreCuadra() {
        for (k in 0 until 200) {
            val subs = (0 until 1 + (k % 7)).map { i -> redondeoJs((((k * 37 + i * 101) % 5000) * 100 + (i * 13) % 100).toDouble()) / 100 }
            val s = subs.sum()
            val d = redondeoJs(((k * 53) % 100) / 100.0 * s * 100) / 100
            val m = prorratearDescuento(subs, d, "PEN")
            assertEquals(redondeoJs((s - minOf(d, s)) * 100).toLong(), redondeoJs(m.sum() * 100).toLong())
            assertTrue(m.indices.all { i -> m[i] >= 0 && m[i] <= subs[i] + 1e-9 })
        }
    }

    // ── estado, vigencia y días ──

    @Test fun enviadoVencidoSeVeVencido() {
        assertEquals("Vencido", estadoVisiblePresupuesto("Enviado", "2026-10-15", "2026-10-16"))
        assertEquals("Enviado", estadoVisiblePresupuesto("Enviado", "2026-10-16", "2026-10-16"))
        assertEquals("Borrador", estadoVisiblePresupuesto("Borrador", "2020-01-01", "2026-10-16"))
        assertEquals("Aceptado", estadoVisiblePresupuesto("Aceptado", "2020-01-01", "2026-10-16"))
    }

    @Test fun aceptablePortalYEnPersona() {
        val p = presu()
        assertTrue(aceptable(p, "2026-10-20", "portal"))
        assertFalse(aceptable(p, "2026-11-01", "portal"))
        assertTrue(aceptable(p, "2026-11-01", "en_persona"))
        assertFalse(aceptable(presu(estado = "Borrador"), "2026-10-20", "portal"))
        assertTrue(aceptable(presu(estado = "Borrador"), "2026-10-20", "en_persona"))
        assertFalse(aceptable(presu(estado = "Aceptado"), "2026-10-20", "en_persona"))
    }

    @Test fun faltaConvertirAceptadoSinTratamientos() {
        assertTrue(faltaConvertir(presu(estado = "Aceptado")))
        assertFalse(faltaConvertir(presu(estado = "Aceptado", convertido = "2026-10-16T16:00:00Z")))
        assertFalse(faltaConvertir(presu()))
    }

    @Test fun fechasYDias() {
        assertEquals("2026-10-31", sumarDias("2026-10-16", 15))
        assertEquals("2027-01-04", sumarDias("2026-12-25", 10))
        assertEquals(15, diasEntre("2026-10-16", "2026-10-31"))
        assertEquals(6, diasDesdeEnvio("2026-10-10", "2026-10-16"))
        assertNull(diasDesdeEnvio(null, "2026-10-16"))
        assertEquals(listOf("hoy", "ayer", "hace 5 días"), listOf(textoDiasEnvio(0), textoDiasEnvio(1), textoDiasEnvio(5)))
    }

    @Test fun vigenciaValidaNoPasadaAMasUnAnio() {
        assertNull(validarVigencia("2026-10-31", "2026-10-16"))
        assertTrue(validarVigencia("2026-10-15", "2026-10-16")!!.contains("pasada"))
        assertTrue(validarVigencia("2028-01-01", "2026-10-16")!!.contains("un año"))
        assertTrue(validarVigencia("31/10/2026", "2026-10-16")!!.contains("no es válida"))
    }

    // ── validar lo que se manda ──

    @Test fun rechazaConMensajesClaros() {
        val x = itemLibre("X", 10.0).copy(clave = "l_x")
        assertEquals("Agrega al menos un servicio", errorItems(emptyList(), "PEN"))
        assertTrue(errorItems(listOf(x.copy(cantidad = 0)), "PEN")!!.contains("cantidad"))
        assertTrue(errorItems(listOf(x.copy(precioUnitario = -1.0)), "PEN")!!.contains("negativo"))
        assertTrue(errorItems(listOf(x.copy(precioUnitario = 10.555)), "PEN")!!.contains("2 decimales"))
        assertTrue(errorItems(listOf(x.copy(precioUnitario = 10.5)), "CLP")!!.contains("no usa decimales"))
        assertTrue(errorItems(listOf(x.copy(clave = "a"), x.copy(clave = "a", nombre = "Y")), "PEN")!!.contains("repetida"))
        assertNotNull(errorItems((1..31).map { x.copy(clave = "l_$it") }, "PEN"))
        assertNull(errorItems(listOf(toxina, relleno, peeling, paquete10), "PEN"))
        assertEquals("X: la cantidad debe ser un número entero entre 1 y 100,000", errorItems(listOf(x.copy(cantidad = 100_001)), "PEN"))
    }

    @Test fun leerItemsToleraBasura() {
        val arr = kotlinx.serialization.json.Json.parseToJsonElement(
            """[{"x":1},{"clave":"tox","procedimiento_id":"11111111-1111-4111-8111-111111111111","nombre":"Toxina botulínica","detalle":"Tercio superior","modo":"unidades","unidad_label":"unidades","cantidad":50,"precio_unitario":15,"precio_lista":18},
               {"clave":"pee","nombre":"Peeling","modo":"sesiones","cantidad":3,"precio_unitario":200,"precio_lista":250}]""",
        )
        val l = leerItemsPresupuesto(arr)
        assertEquals(2, l.size)
        assertEquals(toxina, l[0])
        // Los guardados antes del cobro: sesiones = sueltas.
        assertEquals("sueltas", l[1].cobro)
        assertEquals(emptyList(), leerItemsPresupuesto(null))
    }

    @Test fun normalizarDescuento() {
        assertEquals(DescuentoNormalizado(true, "porcentaje", 10.0), normalizarDescuento("porcentaje", "10"))
        assertEquals(DescuentoNormalizado(true, null, 0.0), normalizarDescuento("porcentaje", "0"))
        assertFalse(normalizarDescuento("porcentaje", "101").ok)
        assertFalse(normalizarDescuento("monto", "-1").ok)
        assertFalse(normalizarDescuento("regalo", "5").ok)
        assertEquals(DescuentoNormalizado(true, null, 0.0), normalizarDescuento(null, null))
    }

    // ── catálogo → ítem ──

    private val botox = ServicioPresupuesto(toxina.procedimientoId!!, "Toxina", 600.0, "unidades", "unidades", 18.0)
    private val peel = ServicioPresupuesto(peeling.procedimientoId!!, "Peeling", 250.0, null, usaSesiones = true)
    private val consulta = ServicioPresupuesto(relleno.procedimientoId!!, "Relleno", 900.0, null, usaSesiones = false)

    @Test fun modoEfectivoHeredaDeLaEspecialidad() {
        assertEquals("unidades", modoDeServicio(botox))
        assertEquals("sesiones", modoDeServicio(peel))
        assertEquals("simple", modoDeServicio(consulta))
    }

    @Test fun listaPorUnidadSegunElModo() {
        assertEquals(18.0, precioListaItem(botox))
        assertEquals(250.0, precioListaItem(peel))
        assertNull(precioListaItem(peel, true))
    }

    @Test fun itemDelCatalogoArrancaEnLaLista() {
        val it = itemDeServicio(botox, cantidad = 50)
        assertEquals("unidades", it.modo); assertEquals("unidades", it.unidadLabel)
        assertEquals(50, it.cantidad); assertEquals(15.0 + 3.0, it.precioUnitario); assertEquals(18.0, it.precioLista)
    }

    @Test fun servidorPoneLaListaYServicioPerdidoPasaALibre() {
        val r = conListaDelCatalogo(listOf(toxina.copy(precioLista = 1.0), peeling.copy(procedimientoId = "99999999-9999-4999-8999-999999999999")), listOf(botox))
        assertEquals(18.0, r[0].precioLista)
        assertNull(r[1].procedimientoId); assertNull(r[1].precioLista); assertEquals("simple", r[1].modo)
    }

    // ── permisos ──

    private fun con(vararg ks: String): (String) -> Boolean = { it in ks }

    @Test fun armarAceptarConvertir() {
        assertTrue(puedeArmarPresupuesto(con("sesiones")))
        assertTrue(puedeArmarPresupuesto(con("pagos")))
        assertFalse(puedeArmarPresupuesto(con("pacientes")))
        assertTrue(puedeAceptarEnPersona(con("pagos")))
        assertFalse(puedeConvertirPresupuesto(con("pagos")))
        assertTrue(puedeConvertirPresupuesto(con("sesiones")))
    }

    @Test fun precioPagosOAutorConSesiones() {
        assertTrue(puedeFijarPrecioPresupuesto(con("pagos"), false))
        assertTrue(puedeFijarPrecioPresupuesto(con("sesiones"), true))
        assertFalse(puedeFijarPrecioPresupuesto(con("sesiones"), false))
        assertTrue(esAutorPresupuesto(null, "u1", null))
        assertTrue(esAutorPresupuesto(presu(), "u-medico", null))
        assertTrue(esAutorPresupuesto(presu(), "otro", "ter-1"))
        assertFalse(esAutorPresupuesto(presu(), "otro", "ter-2"))
    }

    @Test fun cambiarProfesionalSinEscalada() {
        assertFalse(puedeCambiarProfesionalPresupuesto(con("sesiones"), presu(), "otro"))
        assertTrue(puedeCambiarProfesionalPresupuesto(con("sesiones"), presu(), "u-medico"))
        assertTrue(puedeCambiarProfesionalPresupuesto(con("pagos"), presu(), "otro"))
        assertTrue(puedeCambiarProfesionalPresupuesto(con("sesiones"), null, "otro"))
    }

    @Test fun contenidoQueElPacienteVe() {
        val p = presu()
        assertFalse(contenidoCambiado(p))
        assertFalse(contenidoCambiado(p, titulo = "Plan facial", items = p.items, descuentoTipo = "porcentaje", descuentoValor = 10.0, notasPaciente = null))
        assertTrue(contenidoCambiado(p, items = listOf(toxina.copy(precioUnitario = 14.0), relleno, peeling)))
        assertTrue(contenidoCambiado(p, descuentoValor = 15.0))
        assertTrue(contenidoCambiado(p, titulo = "Otro"))
        assertTrue(contenidoCambiado(p, notasPaciente = "Incluye controles"))
        val q = presu(items = listOf(paquete10))
        assertTrue(contenidoCambiado(q, items = listOf(paquete10.copy(cobro = "sueltas"))))
        assertFalse(contenidoCambiado(q, items = listOf(paquete10)))
    }

    @Test fun sinPermisoDePrecioListaOLoGuardado() {
        val prev = listOf(toxina)
        assertNull(cambiosDePrecioNoPermitidos(listOf(toxina.copy(precioUnitario = 15.0)), prev, null, 0.0, null, 0.0))
        assertNull(cambiosDePrecioNoPermitidos(listOf(toxina.copy(precioUnitario = 18.0)), prev, null, 0.0, null, 0.0))
        assertTrue(cambiosDePrecioNoPermitidos(listOf(toxina.copy(precioUnitario = 12.0)), prev, null, 0.0, null, 0.0)!!.contains("precio de «Toxina"))
        assertTrue(cambiosDePrecioNoPermitidos(listOf(toxina), prev, null, 0.0, "porcentaje", 5.0)!!.contains("descuento"))
        assertTrue(cambiosDePrecioNoPermitidos(listOf(itemLibre("Extra", 50.0)), null, null, 0.0, null, 0.0)!!.contains("ponerle precio"))
        // Un paquete no se "estira" a más sesiones al mismo precio ni pasa a sueltas.
        val prevPaq = listOf(paquete10)
        assertNull(cambiosDePrecioNoPermitidos(listOf(paquete10), prevPaq, null, 0.0, null, 0.0))
        assertNotNull(cambiosDePrecioNoPermitidos(listOf(paquete10.copy(cantidad = 20, precioLista = 900.0)), prevPaq, null, 0.0, null, 0.0))
        assertNotNull(cambiosDePrecioNoPermitidos(listOf(paquete10.copy(cobro = "sueltas", precioLista = 60.0)), prevPaq, null, 0.0, null, 0.0))
        assertNull(cambiosDePrecioNoPermitidos(listOf(paquete10.copy(cantidad = 20, precioUnitario = 900.0, precioLista = 900.0)), prevPaq, null, 0.0, null, 0.0))
    }

    // ── conversión ──

    @Test fun cadaModoASuModalidad() {
        val l = lineasAConvertir(presu())
        assertEquals(listOf(675.0, 1620.0, 540.0), l.map { it.monto })
        assertEquals(2835.0, l.sumOf { it.monto })
        with(l[0].input) {
            assertEquals("Unidades", modalidad); assertEquals(50, cantidadUnidades); assertEquals(15.0, precioUnitario)
            assertEquals(675.0, precioAcordado); assertNull(totalSesiones); assertEquals("p-1", presupuestoId)
            assertEquals("tox", presupuestoLinea); assertEquals("cita-1", citaOrigenId); assertEquals("ter-1", terapeutaId)
            assertEquals("Presupuesto «Plan facial»: descuento de paquete S/ 75.00", motivoPrecio)
        }
        with(l[1].input) {
            assertEquals("Consulta", modalidad); assertNull(totalSesiones); assertNull(precioPaquete); assertNull(precioPorSesion)
            assertEquals(1620.0, precioAcordado); assertTrue(notas.contains("× 2"))
        }
        with(l[2].input) {
            assertEquals("Sesión suelta", modalidad); assertEquals(3, totalSesiones); assertEquals(180.0, precioPorSesion)
            assertNull(precioPaquete); assertEquals(540.0, precioAcordado)
        }
    }

    @Test fun sueltasPorUnoYPaquetePorUno() {
        val s1 = tratamientoDeLinea(peeling.copy(cantidad = 1), 250.0, "p", "T")
        assertEquals("Sesión suelta", s1.modalidad); assertEquals(1, s1.totalSesiones); assertEquals(250.0, s1.precioPorSesion)
        val p1 = tratamientoDeLinea(paquete10.copy(cantidad = 1, precioUnitario = 250.0, precioLista = null), 250.0, "p", "T")
        assertEquals("Paquete", p1.modalidad); assertEquals(1, p1.totalSesiones); assertEquals(250.0, p1.precioPaquete); assertNull(p1.precioPorSesion)
    }

    @Test fun itemLibreAlDiagnosticoYLibrePorUnidades() {
        val libre = itemLibre("Crema despigmentante", 80.0, 3).copy(clave = "lib")
        val sin = tratamientoDeLinea(libre, 240.0, "p", "T")
        assertNull(sin.procedimientoId); assertEquals("Crema despigmentante", sin.diagnostico); assertEquals("Consulta", sin.modalidad)
        val unid = tratamientoDeLinea(libre.copy(modo = "unidades", unidadLabel = "ml", cantidad = 2, precioUnitario = 300.0), 600.0, "p", "T")
        assertEquals("Unidades", unid.modalidad); assertEquals(2, unid.cantidadUnidades)
        val con = lineasAConvertir(presu(items = listOf(libre), tipo = null, valor = 0.0), servicios = mapOf("lib" to "44444444-4444-4444-8444-444444444444"))
        assertEquals("44444444-4444-4444-8444-444444444444", con[0].input.procedimientoId)
    }

    @Test fun sedeYProfesional() {
        assertEquals("sede-pac", lineasAConvertir(presu(), sedeId = "sede-pac")[0].input.sedeId)
        val l = lineasAConvertir(presu(sede = "sede-bo"), terapeutaId = "ter-9")
        assertEquals("sede-bo", l[0].input.sedeId); assertEquals("ter-9", l[0].input.terapeutaId)
        assertNull(lineasAConvertir(presu())[0].input.sedeId)
    }

    @Test fun precioNegociadoSinDescuentoDePaquete() {
        val l = lineasAConvertir(presu(items = listOf(peeling), tipo = null, valor = 0.0))
        assertEquals(600.0, l[0].monto)
        assertEquals("Presupuesto «Plan facial»: precio acordado (lista S/ 750.00)", l[0].input.motivoPrecio)
    }

    @Test fun adelantoPorDefectoAlQueMasVale() {
        assertEquals("rel", lineaPrincipal(lineasAConvertir(presu())) { it.monto }?.clave)
        assertNull(lineaPrincipal(emptyList<LineaAConvertir>()) { it.monto })
    }

    @Test fun adelantoEnOrdenYNuncaPasaDelTotal() {
        val lineas = listOf("a" to 675.0, "b" to 1620.0, "c" to 540.0)
        assertEquals(listOf("a" to 500.0), repartirAdelanto(500.0, lineas, "PEN"))
        assertEquals(listOf("a" to 675.0, "b" to 325.0), repartirAdelanto(1000.0, lineas, "PEN"))
        assertEquals(lineas, repartirAdelanto(2835.0, lineas, "PEN"))
        assertNull(repartirAdelanto(2835.01, lineas, "PEN"))
        assertEquals(emptyList(), repartirAdelanto(0.0, lineas, "PEN"))
        val c = repartirAdelanto(700.1, listOf("a" to 0.05, "b" to 700.05), "PEN")!!
        assertEquals(70010L, redondeoJs(c.sumOf { it.second } * 100).toLong())
    }

    // ── textos ──

    @Test fun cantidadSegunElModo() {
        assertEquals("× 50 unidades", textoCantidad(toxina))
        assertEquals("× 3 sesiones sueltas", textoCantidad(peeling))
        assertEquals("Paquete de 10 sesiones", textoCantidad(paquete10))
        assertEquals("× 1 sesión", textoCantidad(peeling.copy(cantidad = 1)))
        assertEquals("× 2", textoCantidad(relleno))
        assertEquals("", textoCantidad(relleno.copy(cantidad = 1)))
    }

    @Test fun whatsAppSinNotasInternas() {
        val t = textoPresupuestoWhatsApp(
            presu().copy(notasPaciente = "Incluye controles."), clinica = "Glam Center", paciente = "MARÍA ELENA QUISPE", profesional = "Dr. Juan Villarroel",
        )
        assertTrue(t.contains("🧾 *Presupuesto — Plan facial*"))
        assertTrue(t.contains("Hola María, este es el plan que conversamos en Glam Center:"))
        assertTrue(t.contains("• *Toxina botulínica* (Tercio superior) — 50 unidades × S/ 15.00: S/ 750.00"))
        assertTrue(t.contains("• *Peeling químico* — 3 sesiones sueltas × S/ 200.00: S/ 600.00"))
        assertTrue(t.contains("Descuento de paquete (10 %): −S/ 315.00"))
        assertTrue(t.contains("*Total: S/ 2,835.00*"))
        assertTrue(t.contains("_Por separado: S/ 3,450.00 · ahorras S/ 615.00_"))
        assertTrue(t.contains("Válido hasta el 31 de octubre de 2026."))
        assertTrue(t.contains("— Dr. Juan Villarroel"))
        assertFalse(t.contains("sensible"))
        // urlWhatsApp (api.whatsapp.com/send), nunca wa.me?text=: los emojis viajan intactos.
        assertTrue(pe.saniape.app.ui.urlWhatsApp("51999888777", t).startsWith("https://api.whatsapp.com/send?phone=51999888777&text="))
    }

    @Test fun recordatorioVigenteOVencido() {
        assertTrue(textoRecordatorioWhatsApp(presu(), "2026-10-20", clinica = "Glam", paciente = "Ana").contains("Sigue vigente hasta el 31 de octubre de 2026"))
        assertTrue(textoRecordatorioWhatsApp(presu(), "2026-11-02", paciente = "Ana").contains("vigencia ya pasó"))
    }

    @Test fun auxiliares() {
        assertEquals("José", primerNombre("josé luis"))
        assertEquals("31 de octubre de 2026", fechaLarga("2026-10-31"))
        assertEquals("Un presupuesto aceptado ya no se modifica", mensajeErrorPresupuesto("ERROR: PRESUPUESTO_ACEPTADO_INMUTABLE"))
        assertEquals("No se pudo guardar el presupuesto", mensajeErrorPresupuesto("otra cosa"))
    }

    // ── seguimiento ──

    @Test fun grupoPendienteDeCadaUno() {
        assertEquals("esperan", grupoPendiente(presu(), "2026-10-20"))
        assertEquals("vencidos", grupoPendiente(presu(), "2026-11-02"))
        assertEquals("aceptados", grupoPendiente(presu(estado = "Aceptado"), "2026-10-20"))
        assertNull(grupoPendiente(presu(estado = "Aceptado", convertido = "2026-10-17T10:00:00Z"), "2026-10-20"))
        assertNull(grupoPendiente(presu(estado = "Borrador"), "2026-10-20"))
        assertNull(grupoPendiente(presu(estado = "Rechazado"), "2026-10-20"))
    }

    @Test fun recordarALosDosDias() {
        assertFalse(tocaRecordar(1, null))
        assertTrue(tocaRecordar(3, null))
        assertFalse(tocaRecordar(5, 1))
        assertTrue(tocaRecordar(5, 2))
    }

    // ── sueltas o paquete (pedido del dueño) ──

    private val fisio = ServicioPresupuesto(
        "55555555-5555-4555-8555-555555555555", "Fisioterapia", 60.0, "sesiones", precioPaquete = 999.0,
        tarifarios = listOf(TarifaPresupuesto("t20", 20, 900.0), TarifaPresupuesto("t10", 10, 500.0), TarifaPresupuesto("t5", 5, 280.0, "Inactivo")),
    )

    @Test fun paquetesDelCatalogoComoTratamientoForm() {
        assertEquals(listOf(PaqueteServicio("t10", 10, 500.0), PaqueteServicio("t20", 20, 900.0)), paquetesDelServicio(fisio))
        assertEquals(PaqueteServicio("t10", 10, 500.0), paquetePorDefecto(fisio))
        assertEquals(PaqueteServicio(null, 8, 400.0), paquetePorDefecto(fisio.copy(tarifarios = listOf(TarifaPresupuesto(null, 8, 400.0)))))
        assertEquals(listOf(PaqueteServicio(null, 10, 450.0)), paquetesDelServicio(fisio.copy(precioPaquete = 450.0, tarifarios = emptyList())))
        assertEquals(emptyList(), paquetesDelServicio(fisio.copy(precioPaquete = null, tarifarios = emptyList())))
        assertEquals(900.0, listaPaquete(fisio, 20))
        assertNull(listaPaquete(fisio, 12))
        assertNull(listaPaquete(fisio, 10, true))
    }

    @Test fun elegirPaqueteYVolverASueltas() {
        val suelta = itemDeServicio(fisio, cantidad = 10)
        assertEquals("sueltas", suelta.cobro); assertEquals(60.0, suelta.precioUnitario); assertEquals(60.0, suelta.precioLista); assertEquals(60.0, suelta.precioSesion)
        assertEquals(600.0, subtotalItem(suelta, "PEN"))
        val paq = comoPaquete(suelta, fisio)
        assertEquals("paquete", paq.cobro); assertEquals(10, paq.cantidad); assertEquals(500.0, paq.precioUnitario); assertEquals(500.0, paq.precioLista); assertEquals(60.0, paq.precioSesion)
        assertEquals(500.0, subtotalItem(paq, "PEN"))
        assertEquals("Paquete de 10 · S/ 500.00 · por sueltas S/ 600.00", textoAhorroPaquete(paq, "PEN"))
        val de20 = comoPaquete(suelta, fisio, paquete = PaqueteServicio("t20", 20, 900.0))
        assertEquals(20, de20.cantidad); assertEquals(900.0, de20.precioUnitario); assertEquals(900.0, de20.precioLista)
        val vuelta = comoSueltas(de20, fisio)
        assertEquals("sueltas", vuelta.cobro); assertEquals(20, vuelta.cantidad); assertEquals(60.0, vuelta.precioUnitario); assertEquals(60.0, vuelta.precioLista)
        // Sin paquetes en el catálogo: personalizado (mismas sesiones al precio de sueltas, sin lista).
        val sinPaq = fisio.copy(tarifarios = emptyList(), precioPaquete = null)
        val sin = comoPaquete(itemDeServicio(sinPaq, cantidad = 6), sinPaq)
        assertEquals("paquete", sin.cobro); assertEquals(6, sin.cantidad); assertEquals(360.0, sin.precioUnitario); assertNull(sin.precioLista)
    }

    @Test fun listaDelPaqueteDeEsasSesiones() {
        val r = conListaDelCatalogo(listOf(paquete10.copy(precioLista = 1.0, precioSesion = 1.0), paquete10.copy(clave = "p12", cantidad = 12, precioUnitario = 650.0)), listOf(fisio))
        assertEquals("paquete", r[0].cobro); assertEquals(500.0, r[0].precioLista); assertEquals(60.0, r[0].precioSesion)
        assertEquals("paquete", r[1].cobro); assertNull(r[1].precioLista); assertEquals(60.0, r[1].precioSesion)
    }

    @Test fun descuentoSobreElTotalConPaquete() {
        val items = listOf(paquete10, peeling, toxina)
        // 500 + 600 + 750 = 1850; −10 % = 1665. Por separado: 600 + 750 + 900.
        assertEquals(TotalesPresupuesto(1850.0, 185.0, 1665.0, 2250.0, 585.0), totalesPresupuesto(items, "porcentaje", 10.0, "PEN"))
        val l = lineasAConvertir(presu(items = items))
        assertEquals(1665.0, l.sumOf { it.monto })
        assertEquals(listOf(450.0, 540.0, 675.0), l.map { it.monto })
    }

    @Test fun conversionSegunLoElegido() {
        val l = lineasAConvertir(presu(items = listOf(paquete10, peeling.copy(cantidad = 4)), tipo = "monto", valor = 130.0))
        with(l[0].input) {
            assertEquals("Paquete", modalidad); assertEquals(10, totalSesiones); assertEquals(450.0, precioPaquete)
            assertEquals(450.0, precioAcordado); assertNull(precioPorSesion)
            assertEquals("Presupuesto «Plan facial»: descuento de paquete S/ 50.00", motivoPrecio)
        }
        with(l[1].input) {
            assertEquals("Sesión suelta", modalidad); assertEquals(4, totalSesiones); assertEquals(180.0, precioPorSesion); assertEquals(720.0, precioAcordado)
        }
    }

    @Test fun textosParaElPaciente() {
        assertEquals("Paquete de 10 sesiones", textoCobroItem(paquete10, "PEN"))
        assertEquals("10 sesiones sueltas × S/ 60.00", textoCobroItem(paquete10.copy(cobro = "sueltas", precioUnitario = 60.0), "PEN"))
        assertEquals("1 sesión × S/ 200.00", textoCobroItem(peeling.copy(cantidad = 1), "PEN"))
        assertEquals("50 unidades × S/ 15.00", textoCobroItem(toxina, "PEN"))
        assertEquals("", textoCobroItem(relleno.copy(cantidad = 1), "PEN"))
    }

    // ── caso del dueño: Láser CO2 fraccionado (S/ 380 la sesión, paquete de 3 = S/ 999) ──

    private val laser = ServicioPresupuesto(
        "e61bf3c5-6036-4ecf-a131-d5c7f88079f7", "Láser CO2 fraccionado", 380.0, "sesiones", precioPaquete = null,
        tarifarios = listOf(TarifaPresupuesto("t3", 3, 999.0, "Activo")),
    )

    @Test fun laserPaqueteExacto999() {
        for (n in listOf(1, 2, 3)) {
            val p = comoPaquete(itemDeServicio(laser, cantidad = n), laser)
            assertEquals("paquete", p.cobro); assertEquals(3, p.cantidad); assertEquals(999.0, p.precioUnitario)
            assertEquals(999.0, p.precioLista); assertEquals(380.0, p.precioSesion)
            assertNull(textoPrecioNegociado(p.precioUnitario, p.precioLista))
            assertEquals("Paquete de 3 · S/ 999.00 · por sueltas S/ 1,140.00", textoAhorroPaquete(p, "PEN"))
        }
        // Un precio ESCRITO en sueltas no contamina el paquete del catálogo.
        assertEquals(999.0, comoPaquete(itemDeServicio(laser, cantidad = 3).copy(precioUnitario = 333.33), laser).precioUnitario)
    }

    @Test fun laserServidorGuardaLaMismaListaSinMotivo() {
        val srv = conListaDelCatalogo(listOf(comoPaquete(itemDeServicio(laser), laser)), listOf(laser))[0]
        assertEquals(999.0, srv.precioUnitario); assertEquals(999.0, srv.precioLista)
        val l = lineasAConvertir(presu(items = listOf(srv), tipo = null, valor = 0.0))
        with(l[0].input) {
            assertEquals("Paquete", modalidad); assertEquals(3, totalSesiones); assertEquals(999.0, precioPaquete)
            assertEquals(999.0, precioAcordado); assertNull(motivoPrecio)
        }
    }

    @Test fun precioSobreListaNoEsDescuento() {
        assertEquals("S/ 1,000.00 · sobre lista S/ 999.00", textoPrecioNegociado(1000.0, 999.0))
        assertEquals("S/ 950.00 · lista S/ 999.00 (−5 %)", textoPrecioNegociado(950.0, 999.0))
    }

    // ── descuento en monto fijo (S/) además de % ──

    @Test fun validacionEnVivoDelDescuento() {
        assertNull(errorDescuento("porcentaje", "15", 1000.0, "PEN"))
        assertTrue(errorDescuento("porcentaje", "120", 1000.0, "PEN")!!.contains("100 %"))
        assertNull(errorDescuento("monto", "150", 1000.0, "PEN"))
        assertNull(errorDescuento("monto", "1000", 1000.0, "PEN"))
        assertEquals("No puede pasar del total (S/ 1,000.00)", errorDescuento("monto", "1000.01", 1000.0, "PEN"))
        assertTrue(errorDescuento("monto", "10.555", 1000.0, "PEN")!!.contains("2 decimales"))
        assertTrue(errorDescuento("monto", "10.5", 1000.0, "CLP")!!.contains("no usa decimales"))
        assertEquals("Escribe un número", errorDescuento("monto", "abc", 1000.0, "PEN"))
        assertNull(errorDescuento("monto", "", 1000.0, "PEN"))
    }

    @Test fun descuento149SobreElTotal() {
        val laserPaq = paquete10.copy(clave = "las", cantidad = 3, precioUnitario = 999.0, precioLista = 999.0, precioSesion = 380.0)
        val items = listOf(laserPaq, peeling.copy(cantidad = 2))
        // 999 + 400 = 1399; −149 = 1250. Por separado: 3 × 380 + 2 × 250 = 1640.
        assertEquals(TotalesPresupuesto(1399.0, 149.0, 1250.0, 1640.0, 390.0), totalesPresupuesto(items, "monto", 149.0, "PEN"))
        val l = lineasAConvertir(presu(items = items, tipo = "monto", valor = 149.0))
        assertEquals(125000L, redondeoJs(l.sumOf { it.monto } * 100).toLong())
        assertEquals(listOf(892.6, 357.4), l.map { it.monto })
        assertEquals("Paquete", l[0].input.modalidad); assertEquals(3, l[0].input.totalSesiones); assertEquals(892.6, l[0].input.precioPaquete)
        with(l[1].input) { assertEquals("Sesión suelta", modalidad); assertEquals(2, totalSesiones); assertEquals(178.7, precioPorSesion); assertEquals(357.4, precioAcordado) }
    }

    // ── lo escrito → precio ──

    @Test fun leerPrecioComoLaWeb() {
        assertEquals(LecturaPrecio(true, 150.5), leerPrecio("150,5", "PEN"))
        assertEquals(LecturaPrecio(true, 1200.0), leerPrecio(" 1 200 ", "PEN"))
        assertEquals(LecturaPrecio(true), leerPrecio("", "PEN"))
        assertFalse(leerPrecio("abc", "PEN").ok)
        assertFalse(leerPrecio("10.555", "PEN").ok)
        assertEquals("999.00", textoLista(999.0, "PEN"))
        assertEquals("15.5", textoPrecioCampo(15.5))
        assertEquals("999", textoPrecioCampo(999.0))
    }

    @Test fun hojaSinNotasInternasYEscapada() {
        val p = presu().copy(notasPaciente = "<b>Incluye</b>", estado = "Aceptado", aceptadoAt = "2026-10-16T20:30:00Z", aceptadoCanal = "en_persona")
        val h = htmlPresupuesto(p, "Glam", null, "http://x/logo.png", "#7c3aed", "María", "12345678", "Dr. Juan", "America/Lima")
        assertFalse(h.contains("sensible"))
        assertFalse(h.contains("<b>Incluye</b>"))
        assertFalse(h.contains("http://x/logo.png"))
        assertTrue(h.contains("S/ 2,835.00"))
        assertTrue(h.contains("✓ Aceptado el 16/10 a las 15:30 · en la clínica"))
    }
}
