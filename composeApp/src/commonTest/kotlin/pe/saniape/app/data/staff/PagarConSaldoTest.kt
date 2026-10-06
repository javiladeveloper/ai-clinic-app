package pe.saniape.app.data.staff

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import pe.saniape.app.data.offline.RechazoServidor
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Pagar con saldo a favor (docs/app-contrato-pagar-con-saldo.md): parseo del
 * disponible, el reparto que cuadra al céntimo (misma regla que el servidor),
 * el historial agrupado por grupo_pago_id y los mensajes de cada rechazo.
 * Caso del QA (Emerson): S/ 205.97 a favor, deuda S/ 490 → Saldo 205.97 + Yape 284.03.
 */
class PagarConSaldoTest {

    // ── Formato ──

    @Test
    fun soles_con_miles_y_signo_delante() {
        assertEquals("S/ 205.97", soles(205.97))
        assertEquals("S/ 1,205.97", soles(1205.97))
        assertEquals("-S/ 205.97", soles(-205.97))
        assertEquals("S/ 0.29", soles(0.29))
        assertEquals("284.03", textoMonto(284.03))
    }

    // ── Disponible ──

    @Test
    fun parsea_el_disponible_y_sus_origenes() {
        val s = parsearSaldoDisponible(
            """{ "disponible": 205.97, "origenes": [
                 { "tratamientoId": "a", "nombre": "Consulta", "estado": "Activo", "disponible": 100 },
                 { "tratamientoId": "b", "nombre": "Ortodoncia", "estado": "Cancelado", "disponible": 105.97 },
                 { "nombre": "sin id" } ] }"""
        )!!
        assertEquals(205.97, s.disponible)
        assertEquals(listOf("a", "b"), s.origenes.map { it.tratamientoId })
        assertEquals("Cancelado", s.origenes[1].estado)
        assertEquals(0.0, parsearSaldoDisponible("""{ "disponible": 0, "origenes": [] }""")!!.disponible)
        assertNull(parsearSaldoDisponible("no es json"))
    }

    @Test
    fun tope_y_reparto_sugerido() {
        assertEquals(205.97, saldoUsable(205.97, 490.0))
        assertEquals(490.0, saldoUsable(800.0, 490.0))
        assertEquals(0.0, saldoUsable(100.0, 0.0))
        assertEquals(205.97 to 284.03, repartoSugerido(490.0, 205.97))
        assertEquals(490.0 to 0.0, repartoSugerido(490.0, 800.0))
        assertEquals(0.0 to 0.0, repartoSugerido(0.0, 100.0))
    }

    // ── Validación (gemela de validarPartesConSaldo) ──

    @Test
    fun caso_emerson_cuadra_al_centimo() {
        val v = validarPartesConSaldo(listOf(PartePago("Saldo a favor", 205.97), PartePago("Yape", 284.03)), 490.0)
        assertIs<ValidacionPartes.Ok>(v)
        assertEquals(205.97, v.saldo)
        assertEquals(2, v.partes.size)
    }

    @Test
    fun todo_con_saldo_es_una_sola_parte_valida() {
        val v = validarPartesConSaldo(listOf(PartePago("saldo a favor", 120.0)), 120.0)
        assertIs<ValidacionPartes.Ok>(v)
        assertEquals(120.0, v.saldo)
    }

    @Test
    fun rechaza_lo_que_el_servidor_rechaza() {
        val noSuma = validarPartesConSaldo(listOf(PartePago("Saldo a favor", 205.97), PartePago("Yape", 284.0)), 490.0)
        assertEquals("Los pagos no suman el total de S/ 490.00: faltan S/ 0.03.", (noSuma as ValidacionPartes.Error).mensaje)
        val dosSaldos = validarPartesConSaldo(listOf(PartePago("Saldo a favor", 100.0), PartePago("Saldo a favor", 100.0)), 200.0)
        assertEquals("El saldo a favor va en una sola parte.", (dosSaldos as ValidacionPartes.Error).mensaje)
        val cinco = validarPartesConSaldo(List(5) { PartePago("Efectivo", 1.0) }, 5.0)
        assertEquals("Un pago lleva de 1 a 4 medios de pago.", (cinco as ValidacionPartes.Error).mensaje)
        assertIs<ValidacionPartes.Error>(validarPartesConSaldo(listOf(PartePago("Saldo a favor", 10.005)), 10.005))
        assertIs<ValidacionPartes.Error>(validarPartesConSaldo(emptyList(), 0.0))
    }

    @Test
    fun el_cobro_dividido_de_citas_sigue_exigiendo_dos_medios() {
        val v = validarPagosDivididos(listOf(PartePago("Efectivo", 40.0)), 40.0)
        assertEquals("Un pago dividido lleva de 2 a 4 medios de pago.", (v as ValidacionPagos.Error).mensaje)
    }

    // ── Del formulario a las partes ──

    @Test
    fun formulario_con_un_medio_para_el_resto() {
        val v = partesDelFormulario(490.0, "205.97", usable = 205.97, metodoResto = "Yape")
        assertIs<ValidacionPartes.Ok>(v)
        assertEquals(listOf(PartePago("Saldo a favor", 205.97), PartePago("Yape", 284.03)), v.partes)
        assertEquals("Saldo a favor S/ 205.97 + Yape S/ 284.03", resumenPartes(v.partes))
    }

    @Test
    fun formulario_todo_con_saldo() {
        val v = partesDelFormulario(150.0, "150", usable = 200.0, metodoResto = "Yape")
        assertIs<ValidacionPartes.Ok>(v)
        assertEquals(listOf(PartePago("Saldo a favor", 150.0)), v.partes)
    }

    @Test
    fun formulario_con_el_resto_dividido() {
        val filas = listOf(FilaPago("Efectivo", "84.03"), FilaPago("Yape", "200"))
        val v = partesDelFormulario(490.0, "205,97", usable = 205.97, metodoResto = "Yape", filasResto = filas)
        assertIs<ValidacionPartes.Ok>(v)
        assertEquals(3, v.partes.size)
        assertEquals("Saldo a favor S/ 205.97 + Efectivo S/ 84.03 + Yape S/ 200.00", resumenPartes(v.partes))
        // El resto no cuadra: el mensaje del cobro dividido.
        val mal = partesDelFormulario(490.0, "205.97", 205.97, "Yape", listOf(FilaPago("Efectivo", "84"), FilaPago("Yape", "200")))
        assertEquals("Los pagos no suman el total de S/ 284.03: faltan S/ 0.03.", (mal as ValidacionPartes.Error).mensaje)
        // Con saldo, el resto va en hasta 3 medios (4 en total).
        val cuatro = partesDelFormulario(490.0, "190", 205.97, "Yape", List(4) { FilaPago("Efectivo", "75") })
        assertEquals("Con saldo a favor, el resto va en hasta 3 medios.", (cuatro as ValidacionPartes.Error).mensaje)
    }

    @Test
    fun formulario_mensajes_de_pantalla() {
        assertEquals("Indica el monto del pago.", (partesDelFormulario(null, "10", 10.0, "Yape") as ValidacionPartes.Error).mensaje)
        assertEquals("Indica cuánto se paga con saldo a favor.", (partesDelFormulario(100.0, "", 10.0, "Yape") as ValidacionPartes.Error).mensaje)
        assertEquals("Con saldo a favor puedes usar hasta S/ 205.97.",
            (partesDelFormulario(490.0, "300", 205.97, "Yape") as ValidacionPartes.Error).mensaje)
        assertEquals("El saldo a favor no puede ser mayor que el monto del pago.",
            (partesDelFormulario(100.0, "150", 205.97, "Yape") as ValidacionPartes.Error).mensaje)
    }

    @Test
    fun cuerpo_del_registro_con_partes() {
        val c = cuerpoPagoConSaldo("t1", 490.0, listOf(PartePago("Saldo a favor", 205.97), PartePago(" Yape ", 284.03)), "  nota ", false)
        assertEquals("t1", c["tratamientoId"]!!.jsonPrimitive.content)
        assertEquals("490.0", c["monto"]!!.jsonPrimitive.content)
        val pagos = c["pagos"] as JsonArray
        assertEquals("Saldo a favor", (pagos[0] as JsonObject)["metodo"]!!.jsonPrimitive.content)
        assertEquals("Yape", (pagos[1] as JsonObject)["metodo"]!!.jsonPrimitive.content)
        assertEquals("284.03", (pagos[1] as JsonObject)["monto"]!!.jsonPrimitive.content)
        assertEquals("nota", c["notas"]!!.jsonPrimitive.content)
        assertNull(c["metodo"])
        assertNull(c["recordar"])
        assertNull(c["sesionId"])
        assertNull(c["idempotency_key"])   // la pone quien envía (y la conserva ante un 5xx)
    }

    @Test
    fun uuid_v4_que_el_servidor_acepta_como_grupo() {
        val r = Random(42)
        repeat(50) {
            val u = nuevoUuid(r)
            assertTrue(esUuid(u), u)
            assertEquals('4', u[14])
            assertTrue(u[19] in "89ab", u)
        }
        assertFalse(esUuid("0123456789abcdef0123456789abcdef"))   // la clave de la cola NO sirve de grupo
    }

    // ── Historial ──

    private fun pago(
        id: String, monto: Double, metodo: String, fecha: String = "2026-10-06",
        tipo: String? = null, grupo: String? = null, uso: String? = null, destino: String? = null,
    ) = PagoFicha(id, monto, metodo, null, fecha, null, saldoTipo = tipo, saldoUsoId = uso, grupoPagoId = grupo, destinoNombre = destino)

    @Test
    fun agrupa_el_pago_mixto_con_el_saldo_primero() {
        val filas = listOf(
            pago("p2", 284.03, "Yape", grupo = "g1"),
            pago("p1", 205.97, "Saldo a favor", tipo = "uso", grupo = "g1"),
            pago("p3", 100.0, "Efectivo", fecha = "2026-10-01"),
        )
        val g = agruparPagos(filas)
        assertEquals(2, g.size)
        val mixto = g[0]
        assertEquals(TipoPagoMostrado.MIXTO, mixto.tipo)
        assertEquals("p2", mixto.clave)
        assertEquals(490.0, mixto.monto)
        assertEquals("Saldo a favor + Yape", mixto.metodo)
        assertEquals("Saldo a favor S/ 205.97 + Yape S/ 284.03", mixto.detalle)
        assertEquals("p1", mixto.uso?.id)
        assertTrue(mixto.agrupado)
        val simple = g[1]
        assertEquals(TipoPagoMostrado.NORMAL, simple.tipo)
        assertNull(simple.detalle)
        assertFalse(simple.agrupado)
    }

    @Test
    fun el_consumo_es_saldo_aplicado_a_otro_tratamiento() {
        val g = agruparPagos(listOf(
            pago("c1", -100.0, "Saldo a favor", tipo = "consumo", uso = "u1", destino = "Ortodoncia"),
            pago("c2", -5.97, "Saldo a favor", tipo = "consumo", uso = "u2"),
            pago("p1", 300.0, "Efectivo"),
        ))
        assertEquals(3, g.size)
        assertEquals(TipoPagoMostrado.CONSUMO, g[0].tipo)
        assertEquals("Saldo aplicado a Ortodoncia", g[0].detalle)
        assertEquals(-100.0, g[0].monto)
        assertEquals("Saldo aplicado a otro tratamiento", g[1].detalle)
        assertTrue(g[0].agrupado)
    }

    @Test
    fun varias_partes_sin_saldo_tambien_van_en_una_linea() {
        val g = agruparPagos(listOf(pago("a", 20.0, "Efectivo", grupo = "g"), pago("b", 20.0, "Yape", grupo = "g")))
        assertEquals(1, g.size)
        assertEquals(TipoPagoMostrado.NORMAL, g[0].tipo)
        assertEquals("Efectivo S/ 20.00 + Yape S/ 20.00", g[0].detalle)
        assertTrue(g[0].agrupado)
    }

    @Test
    fun pagado_neto_suma_tambien_las_filas_negativas() {
        // Origen: pagó 300, dio 100 como saldo → pagado neto 200 (igual que la web).
        val origen = listOf(pago("p", 300.0, "Efectivo"), pago("c", -100.0, "Saldo a favor", tipo = "consumo", uso = "u"))
        assertEquals(200.0, pagadoNeto(origen))
        assertTrue(yaDioSaldo(origen))
        // Destino: 205.97 con saldo + 284.03 Yape = 490.
        assertEquals(490.0, pagadoNeto(listOf(pago("u", 205.97, "Saldo a favor", tipo = "uso", grupo = "g"), pago("y", 284.03, "Yape", grupo = "g"))))
        assertEquals(0.3, pagadoNeto(listOf(pago("a", 0.1, "X"), pago("b", 0.2, "X"))))
    }

    // ── Mensajes ──

    private fun rechazo(codigo: String?, status: Int, error: String = "", datos: JsonObject? = null) =
        RechazoServidor(error, codigo, status, datos)

    @Test
    fun saldo_insuficiente_dice_cuanto_hay() {
        val m = mensajeRechazoPagoSaldo(rechazo("SALDO_INSUFICIENTE", 409, "El saldo a favor disponible es S/ 50.00…",
            buildJsonObject { put("disponible", 50.0) }))
        assertTrue(m.contains("S/ 50.00"), m)
        assertTrue(m.contains("Ajusta"), m)
        // Sin el campo, el texto del servidor.
        assertEquals("texto del servidor", mensajeRechazoPagoSaldo(rechazo("SALDO_INSUFICIENTE", 409, "texto del servidor")))
    }

    @Test
    fun mensajes_por_codigo() {
        assertEquals("Con saldo a favor se puede pagar hasta lo que se debe de este tratamiento (S/ 80.00).",
            mensajeRechazoPagoSaldo(rechazo("SALDO_EXCEDE_DEUDA", 409, datos = buildJsonObject { put("deuda", 80) })))
        assertEquals("Este tratamiento no tiene deuda: no hace falta usar saldo a favor.",
            mensajeRechazoPagoSaldo(rechazo("SALDO_EXCEDE_DEUDA", 409, datos = buildJsonObject { put("deuda", 0) })))
        assertEquals("No se puede usar saldo a favor en un tratamiento cancelado o eliminado.",
            mensajeRechazoPagoSaldo(rechazo("DESTINO_NO_VALIDO", 409)))
        assertEquals("El saldo a favor paga el tratamiento, no el cobro de una sesión.",
            mensajeRechazoPagoSaldo(rechazo("SALDO_EN_SESION", 400)))
        assertEquals("Primero deshaz el saldo aplicado a Ortodoncia: este dinero ya se usó para pagarlo.",
            mensajeRechazoPagoSaldo(rechazo("SALDO_YA_APLICADO", 409, "Primero deshaz el saldo aplicado a Ortodoncia: este dinero ya se usó para pagarlo.")))
        assertEquals("Los pagos no suman el total de S/ 490.00: faltan S/ 0.03.",
            mensajeRechazoPagoSaldo(rechazo("PAGO_DIVIDIDO_INVALIDO", 400, "Los pagos no suman el total de S/ 490.00: faltan S/ 0.03.")))
        assertEquals("Las partes del pago no son válidas.", mensajeRechazoPagoSaldo(rechazo("PAGO_DIVIDIDO_INVALIDO", 400, "HTTP 400")))
        assertEquals(MENSAJE_PAGO_SALDO_INCIERTO, mensajeRechazoPagoSaldo(rechazo(null, 504, "HTTP 504")))
        assertEquals("No tienes permiso para registrar cobros.", mensajeRechazoPagoSaldo(rechazo(null, 403)))
        assertEquals("Tu sesión expiró. Vuelve a entrar.", mensajeRechazoPagoSaldo(rechazo(null, 401)))
    }

    @Test
    fun mensajes_de_editar_borrar_y_liberar() {
        assertTrue(mensajeOperacionSaldo(0, null, "el borrado").startsWith("Sin conexión"))
        assertEquals("Primero deshaz el saldo aplicado a Consulta: este dinero ya se usó para pagarlo.",
            mensajeOperacionSaldo(409, rechazo("SALDO_YA_APLICADO", 409, "Primero deshaz el saldo aplicado a Consulta: este dinero ya se usó para pagarlo."), "x"))
        assertEquals("Operación parcial: se eliminaron 1 de 2 partes…",
            mensajeOperacionSaldo(409, rechazo(null, 409, "Operación parcial: se eliminaron 1 de 2 partes…"), "x"))
        assertEquals("Solo se puede pasar a saldo a favor lo pagado de un tratamiento cancelado.",
            mensajeOperacionSaldo(409, rechazo("NO_CANCELADO", 409), "x"))
        assertTrue(mensajeOperacionSaldo(409, rechazo("SIN_SALDO_QUE_LIBERAR", 409), "x").startsWith("No hay nada que pasar"))
        assertEquals("Solo el administrador puede hacerlo.", mensajeOperacionSaldo(403, rechazo("SOLO_ADMIN", 403), "x"))
    }

    // ── Cancelados ──

    @Test
    fun parsea_la_liberacion() {
        val l = parsearLiberacion(buildJsonObject {
            put("ok", true); put("monto", 400); put("liberadoAt", "2026-10-06T15:00:00Z"); put("liberadoPor", "uid"); put("repetido", false)
        })!!
        assertEquals(400.0, l.monto)
        assertEquals("uid", l.liberadoPor)
        assertFalse(l.repetido)
        assertTrue(parsearLiberacion(buildJsonObject { put("monto", 50); put("repetido", true) })!!.repetido)
        assertNull(parsearLiberacion(null))
    }

    @Test
    fun fecha_de_liberacion_en_hora_de_lima() {
        assertEquals("06/10/2026", fechaLiberacion("2026-10-06T15:00:00Z"))
        // 03:00 UTC del 7 = 22:00 del 6 en Lima.
        assertEquals("06/10/2026", fechaLiberacion("2026-10-07T03:00:00.123456+00:00"))
        assertEquals("06/10/2026", fechaLiberacion("2026-10-06"))
    }

    // ── Eliminar ──

    @Test
    fun resumen_al_eliminar_separa_caja_recibido_y_dado() {
        val r = resumenAlEliminar(listOf(
            pago("p1", 300.0, "Efectivo"),
            pago("p2", 50.0, "Yape", grupo = "g"),
            pago("u1", 40.0, "Saldo a favor", tipo = "uso", grupo = "g"),
            pago("c1", -60.0, "Saldo a favor", tipo = "consumo", uso = "x", destino = "Ortodoncia"),
            pago("c2", -10.0, "Saldo a favor", tipo = "consumo", uso = "y", destino = "Ortodoncia"),
        ))
        assertEquals(2, r.reales.size)
        assertEquals(350.0, r.totalReales)
        assertEquals(40.0, r.recibido)
        assertEquals(listOf("Ortodoncia" to 70.0), r.dado)
        assertTrue(r.tieneDinero)
        assertFalse(resumenAlEliminar(emptyList()).tieneDinero)
    }

    @Test
    fun eliminar_muestra_el_texto_del_servidor_tambien_si_es_parcial() {
        val parcial = "Operación parcial: se revirtieron 1 movimiento(s) de pago de este tratamiento y otro no (…). El tratamiento NO se eliminó: revisa sus pagos antes de reintentar."
        assertEquals(parcial, mensajeEliminarTratamiento(409, rechazo(null, 409, parcial)))
        assertTrue(mensajeEliminarTratamiento(0, null).startsWith("Sin conexión"))
        assertTrue(mensajeEliminarTratamiento(500, rechazo(null, 500, "HTTP 500")).contains("Recarga la ficha"))
    }

    @Test
    fun el_rechazo_conserva_los_datos_extra() {
        val r = RechazoServidor("x", "SALDO_INSUFICIENTE", 409, buildJsonObject { put("disponible", JsonPrimitive(12.5)) })
        assertEquals(12.5, numeroDelRechazo(r, "disponible"))
        assertNull(numeroDelRechazo(RechazoServidor("x"), "disponible"))
    }
}
