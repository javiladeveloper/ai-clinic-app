package pe.saniape.app.data

import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.serialization.json.JsonObject
import pe.saniape.app.data.staff.Alimento
import pe.saniape.app.data.staff.EstadoAdecuacion
import pe.saniape.app.data.staff.EnergiaDesdeAporte
import pe.saniape.app.data.staff.ItemComida
import pe.saniape.app.data.staff.MedidaItem
import pe.saniape.app.data.staff.Nutriente
import pe.saniape.app.data.staff.Reparto
import pe.saniape.app.data.staff.adecuacion
import pe.saniape.app.data.staff.alimentoDeCatalogo
import pe.saniape.app.data.staff.aporteDeComidas
import pe.saniape.app.data.staff.aporteDeItem
import pe.saniape.app.data.staff.compararConPlan
import pe.saniape.app.data.staff.conCantidad
import pe.saniape.app.data.staff.energiaDesdeAporte
import pe.saniape.app.data.staff.incompleto
import pe.saniape.app.data.staff.itemDesdeAlimento
import pe.saniape.app.data.staff.redondearAporte
import pe.saniape.app.data.staff.redondearJs
import pe.saniape.app.data.staff.repartoMacros
import pe.saniape.app.data.staff.sumarAporte
import pe.saniape.app.data.staff.textoAporte
import pe.saniape.app.data.staff.textoSinDato
import pe.saniape.app.data.staff.textoValor

/**
 * Aporte nutricional: paridad NUMÉRICA exacta con
 * `lib/__tests__/aporte-nutricional.test.ts` (valores reales de la TPCA 2023 y
 * medidas de la TAFERA 2016; "•" de la fuente = null).
 */
class AporteNutricionalTest {

    private fun fila(id: String, nombre: String, vararg extra: Pair<String, Any?>): Alimento {
        val m = linkedMapOf<String, Any?>(
            "id" to id, "nombre" to nombre, "grupo" to "x", "energia_kcal" to 0, "proteinas_g" to 0, "grasas_g" to 0,
            "carbohidratos_g" to null, "fibra_g" to null, "sodio_mg" to null, "hierro_mg" to null, "calcio_mg" to null,
            "medidas" to emptyList<Any>(),
        )
        extra.forEach { (k, v) -> m[k] = v }
        return alimentoDeCatalogo(jsonDe(m) as JsonObject)!!
    }

    private val ARROZ_COCIDO = fila("TPCA-A2", "Arroz pilado o pulido cocido", "energia_kcal" to 115, "proteinas_g" to 2.4, "grasas_g" to 0.1,
        "carbohidratos_g" to 25.2, "sodio_mg" to 18, "hierro_mg" to 0.3, "calcio_mg" to 11,
        "medidas" to listOf(mapOf("nombre" to "Taza de loza al ras", "gramos" to 193.2)))
    private val QUINUA_COCIDA = fila("TPCA-A53", "Quinua cocida", "energia_kcal" to 89, "proteinas_g" to 2.8, "grasas_g" to 1.3,
        "carbohidratos_g" to 16.3, "sodio_mg" to 24, "hierro_mg" to 1.6, "calcio_mg" to 27,
        "medidas" to listOf(mapOf("nombre" to "Taza de loza al ras", "gramos" to 354.5)))
    private val PECHUGA = fila("TPCA-F82", "Pollo, pechuga de, sin piel, sancochada sin sal", "energia_kcal" to 150, "proteinas_g" to 30.4,
        "grasas_g" to 2.2, "carbohidratos_g" to 0, "sodio_mg" to 31, "hierro_mg" to 0.5, "calcio_mg" to 12)
    private val PALTA_HASS = fila("TPCA-C161", "Palta Hass", "energia_kcal" to 204, "proteinas_g" to 1, "grasas_g" to 23.5,
        "carbohidratos_g" to 7.8, "fibra_g" to 6.8, "sodio_mg" to 4, "hierro_mg" to 0.57, "calcio_mg" to 9)
    private val OLIVA = fila("TPCA-D7", "Aceite vegetal de olivo", "energia_kcal" to 884, "proteinas_g" to 0, "grasas_g" to 100,
        "carbohidratos_g" to 0, "fibra_g" to 0, "hierro_mg" to 0, "calcio_mg" to 0)
    private val HUEVO = fila("TPCA-J3", "Huevo de gallina entero, sancochado en agua", "energia_kcal" to 148, "proteinas_g" to 12.8,
        "grasas_g" to 9.9, "carbohidratos_g" to 0.8, "hierro_mg" to 3.04, "calcio_mg" to 44,
        "medidas" to listOf(mapOf("nombre" to "Unidad mediana", "gramos" to 59.9)))

    /** Un almuerzo: 1 taza de arroz, 120 g de pechuga, 30 g de palta Hass y 5 g de aceite de oliva. */
    private fun almuerzo(): List<ItemComida> = listOf(
        itemDesdeAlimento(ARROZ_COCIDO),
        itemDesdeAlimento(PECHUGA, gramos = 120.0),
        itemDesdeAlimento(PALTA_HASS, gramos = 30.0),
        itemDesdeAlimento(OLIVA, gramos = 5.0),
    )

    private fun cerca(esperado: Double, real: Double?, tol: Double = 1e-6) =
        assertTrue(real != null && abs(esperado - real) < tol, "esperado $esperado, fue $real")

    // ── Un ítem ──

    @Test fun porGramos() {
        val a = aporteDeItem(itemDesdeAlimento(PECHUGA, gramos = 150.0))
        cerca(225.0, a[Nutriente.KCAL])
        cerca(45.6, a[Nutriente.PROTEINAS])
        cerca(3.3, a[Nutriente.GRASAS])
        assertEquals(0.0, a[Nutriente.CARBOHIDRATOS])
        assertNull(a[Nutriente.FIBRA]) // la tabla no trae el dato: no se inventa
    }

    @Test fun porMedidaCaseraComoLaTafera() {
        val it = itemDesdeAlimento(ARROZ_COCIDO)
        assertEquals(MedidaItem("Taza de loza al ras", 193.2, 1.0), it.medida)
        assertEquals(193.2, it.gramos)
        val r = redondearAporte(sumarAporte(listOf(it)))
        assertEquals(222.0, r.kcal)
        assertEquals(4.6, r.proteinas)
        assertEquals(0.2, r.grasas)
        assertEquals(48.7, r.carbohidratos)
    }

    @Test fun mediaTazaDeQuinua() {
        val it = itemDesdeAlimento(QUINUA_COCIDA, medida = "Taza de loza al ras", cantidad = 0.5)
        assertEquals(177.3, it.gramos)
        assertEquals(158.0, redondearJs(aporteDeItem(it)[Nutriente.KCAL]!!))
    }

    @Test fun dosHuevosMedianos() {
        val it = conCantidad(itemDesdeAlimento(HUEVO), 2.0, HUEVO.medidas[0])
        assertEquals(119.8, it.gramos)
        assertEquals(177.0, redondearJs(aporteDeItem(it)[Nutriente.KCAL]!!))
    }

    // ── Totales ──

    @Test fun sumaUnAlmuerzo() {
        val t = sumarAporte(almuerzo())
        assertEquals(4, t.items)
        cerca(222.18 + 180 + 61.2 + 44.2, t.kcal)
        cerca(4.6368 + 36.48 + 0.3, t.proteinas)
        cerca(0.1932 + 2.64 + 7.05 + 5, t.grasas)
        // Disponibles (arroz: sin fibra en la fuente → = totales; palta Hass: totales − fibra = 1,0).
        cerca(48.6864 + 0.3, t.carbohidratos)
        cerca(48.6864 + 2.34, t[Nutriente.CARBOHIDRATOS_TOTALES])
        cerca(2.04, t[Nutriente.FIBRA])
        assertEquals(mapOf(Nutriente.FIBRA to 2, Nutriente.SODIO to 1), t.sinDato)
        val r = redondearAporte(t)
        assertEquals(508.0, r.kcal)
        assertEquals(41.4, r.proteinas)
        assertEquals(14.9, r.grasas)
        assertEquals(49.0, r.carbohidratos)
        assertEquals(51.0, r[Nutriente.CARBOHIDRATOS_TOTALES])
        assertEquals(2.0, r[Nutriente.FIBRA])
        assertEquals(73.0, r[Nutriente.SODIO])
        assertEquals(1.4, r[Nutriente.HIERRO])
        assertEquals(38.0, r[Nutriente.CALCIO])
        assertEquals("≈ 508 kcal · P 41.4 g · G 14.9 g · C 49 g", textoAporte(t))
        assertEquals("fibra sin dato en 2 alimentos", textoSinDato(t))
    }

    @Test fun porComidaYDelDia() {
        val d = aporteDeComidas(listOf(almuerzo(), emptyList(), listOf(itemDesdeAlimento(HUEVO))))
        assertTrue(d.conItems)
        assertEquals(listOf(4, 0, 1), d.porComida.map { it.items })
        assertEquals(redondearJs(507.58 + 59.9 * 1.48), redondearJs(d.total.kcal))
        assertFalse(aporteDeComidas(listOf(emptyList(), emptyList())).conItems)
        val vacio = sumarAporte(null)
        assertEquals(0.0, vacio.kcal)
        assertEquals(0, vacio.items)
        assertTrue(vacio.sinDato.isEmpty())
    }

    @Test fun losItemsSinFotoNoSuman() {
        // Lo que recibe el paciente (sin `por100`) no rompe ni suma.
        val sinFoto = itemDesdeAlimento(HUEVO).copy(por100 = null)
        assertEquals(0, sumarAporte(listOf(sinFoto)).items)
        assertNull(aporteDeItem(sinFoto)[Nutriente.KCAL])
    }

    // ── Reparto y adecuación ──

    @Test fun repartoDelAlmuerzoSuma100() {
        val r = repartoMacros(sumarAporte(almuerzo()))!!
        assertEquals(Reparto(33.4, 27.0, 39.6), r)
        cerca(100.0, r.proteinas + r.grasas + r.carbohidratos, 1e-9)
        assertNull(repartoMacros(0.0, 0.0, 0.0))
    }

    @Test fun adecuacionBordes() {
        adecuacion(508.0, 600.0)!!.let { assertEquals(85, it.pct); assertEquals(EstadoAdecuacion.BAJO, it.estado) }
        assertEquals(EstadoAdecuacion.ADECUADO, adecuacion(90.0, 100.0)!!.estado)
        assertEquals(EstadoAdecuacion.ADECUADO, adecuacion(110.0, 100.0)!!.estado)
        assertEquals(EstadoAdecuacion.ALTO, adecuacion(111.0, 100.0)!!.estado)
        assertNull(adecuacion(10.0, null))
        assertNull(adecuacion(10.0, 0.0))
    }

    @Test fun contraElPlan() {
        val c = compararConPlan(sumarAporte(almuerzo()), 1800.0, 25.0, 50.0, 25.0)
        c.kcal!!.let { assertEquals(508.0, it.valor); assertEquals(1800.0, it.meta); assertEquals(28, it.pct); assertEquals(EstadoAdecuacion.BAJO, it.estado) }
        c.proteinas!!.let { assertEquals(41.4, it.valor); assertEquals(113.0, it.meta) } // 1800 × 25 % / 4
        assertEquals(50.0, c.grasas!!.meta)                                              // 1800 × 25 % / 9
        c.carbohidratos!!.let { assertEquals(49.0, it.valor); assertEquals(225.0, it.meta) } // 1800 × 50 % / 4
        assertEquals(Reparto(25.0, 25.0, 50.0), c.metaReparto)
        // Sin kcal ni % en el plan: no hay con qué comparar, pero el reparto sale igual.
        val s = compararConPlan(sumarAporte(almuerzo()), null, null, null, null)
        assertNull(s.kcal)
        assertNull(s.proteinas)
        assertNotNull(s.reparto)
        assertEquals(EstadoAdecuacion.ADECUADO, compararConPlan(sumarAporte(almuerzo()), 500.0, 30.0, 40.0, 30.0).kcal!!.estado)
    }

    @Test fun usarEstosTotales() {
        val t = sumarAporte(almuerzo())
        assertEquals(EnergiaDesdeAporte(508, 33.4, 39.6, 27.0), energiaDesdeAporte(t, 500, 6000))
        assertNull(energiaDesdeAporte(t, 600, 6000))
        assertNull(energiaDesdeAporte(sumarAporte(emptyList()), 0, 6000))
    }

    // ── Carbohidratos DISPONIBLES ──

    // T 53 Lentejas chicas cocidas (TPCA 2023): 65 kcal · P 6,4 · G 0,1 · C totales 18,3 · disponibles 10,4 · fibra 7,9.
    private val LENTEJAS = fila("TPCA-T53", "Lentejas chicas cocidas", "energia_kcal" to 65, "proteinas_g" to 6.4, "grasas_g" to 0.1,
        "carbohidratos_g" to 18.3, "carbohidratos_disp_g" to 10.4, "fibra_g" to 7.9, "hierro_mg" to 1.7)

    @Test fun lentejas200gDan61PorCientoDeCarbohidratos() {
        val t = sumarAporte(listOf(itemDesdeAlimento(LENTEJAS, gramos = 200.0)))
        cerca(20.8, t.carbohidratos)
        cerca(36.6, t[Nutriente.CARBOHIDRATOS_TOTALES])
        assertEquals(Reparto(37.6, 1.3, 61.1), repartoMacros(t))
        // Con los totales (el error de antes): 73,4 %.
        assertEquals(73.4, redondearJs(36.6 * 4 / (51.2 + 1.8 + 36.6 * 4) * 1000) / 10)
    }

    @Test fun itemViejoSinDisponiblesLosDeriva() {
        val it = itemDesdeAlimento(LENTEJAS, gramos = 100.0)
        val viejo = it.copy(por100 = it.por100!!.copy(carbohidratosDisponibles = null))
        cerca(10.4, aporteDeItem(viejo)[Nutriente.CARBOHIDRATOS])
        // Sin fibra en la fuente: disponibles = totales.
        cerca(18.3, aporteDeItem(viejo.copy(por100 = viejo.por100!!.copy(fibra = null)))[Nutriente.CARBOHIDRATOS])
    }

    // ── Sin dato: nunca un 0 que engañe ──

    @Test fun sinDatoEnAlgunosOEnTodos() {
        val t = sumarAporte(almuerzo()) // sodio: el aceite de oliva no lo trae
        assertEquals("≥ 73 mg · sin dato en 1 alimento", textoValor(t, Nutriente.SODIO))
        assertEquals("≥ 2 g · sin dato en 2 alimentos", textoValor(t, Nutriente.FIBRA))
        assertEquals("38 mg", textoValor(t, Nutriente.CALCIO))
        assertEquals("508 kcal", textoValor(t, Nutriente.KCAL))
        assertTrue(incompleto(t, Nutriente.SODIO))
        assertFalse(incompleto(t, Nutriente.CALCIO))
        val soloHuevo = sumarAporte(listOf(itemDesdeAlimento(HUEVO))) // huevo: sodio y fibra • en la TPCA
        assertEquals("—", textoValor(soloHuevo, Nutriente.SODIO))
        assertEquals("—", textoValor(soloHuevo, Nutriente.FIBRA))
        assertEquals("1.8 mg", textoValor(soloHuevo, Nutriente.HIERRO))
    }

    @Test fun laLineaDeCadaComidaAvisa() {
        // E 57 Pescado merluza fresco: carbohidratos • en la TPCA.
        val merluza = fila("TPCA-E57", "Pescado merluza, fresco", "energia_kcal" to 72, "proteinas_g" to 15.8, "grasas_g" to 0.5)
        val conArroz = sumarAporte(listOf(itemDesdeAlimento(merluza, gramos = 100.0), itemDesdeAlimento(ARROZ_COCIDO)))
        assertEquals("≈ 294 kcal · P 20.4 g · G 0.7 g · C ≥ 48.7 g (sin dato en 1 alimento)", textoAporte(conArroz))
        assertEquals("≈ 72 kcal · P 15.8 g · G 0.5 g · C — (sin dato en 1 alimento)",
            textoAporte(sumarAporte(listOf(itemDesdeAlimento(merluza, gramos = 100.0)))))
    }
}
