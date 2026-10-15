package pe.saniape.app.data.staff

/**
 * APORTE NUTRICIONAL del plan alimentario: cuánto suman los alimentos de cada
 * comida y cómo se compara con el requerimiento del plan. Gemelo PURO de
 * `lib/aporte-nutricional.ts` (mismas sumas, en el mismo orden: los números
 * salen idénticos a la web).
 *
 *  · Aporte de un ítem = nutriente por 100 g × gramos / 100 (la foto `por100`).
 *  · kcal = las de la fuente (TPCA), no se recalculan con 4/9/4.
 *  · Carbohidratos = DISPONIBLES ([carbohidratosDisponibles]); los totales,
 *    informativos. Con los totales, 200 g de lentejas daban 73 % en vez de 61 %.
 *  · Un nutriente sin dato (null) suma 0 y se cuenta en `sinDato`: nunca se
 *    estima, y al mostrarlo se avisa ("≥ 73 mg · sin dato en 2 alimentos" o "—").
 *  · Adecuación: < 90 % bajo · 90–110 % adecuado · > 110 % alto.
 */

enum class Nutriente(val etiqueta: String, val unidad: String) {
    KCAL("Energía", "kcal"),
    PROTEINAS("Proteínas", "g"),
    GRASAS("Grasas", "g"),
    CARBOHIDRATOS("Carbohidratos", "g"),
    CARBOHIDRATOS_TOTALES("Carbohidratos totales", "g"),
    FIBRA("Fibra", "g"),
    SODIO("Sodio", "mg"),
    HIERRO("Hierro", "mg"),
    CALCIO("Calcio", "mg"),
}

/** Sumas (sin redondear salvo [redondearAporte]), lo que falta en cada nutriente y cuántos alimentos suman. */
data class Aporte(
    val valores: Map<Nutriente, Double>,
    val sinDato: Map<Nutriente, Int>,
    val items: Int,
) {
    operator fun get(k: Nutriente): Double = valores[k] ?: 0.0
    val kcal: Double get() = get(Nutriente.KCAL)
    val proteinas: Double get() = get(Nutriente.PROTEINAS)
    val grasas: Double get() = get(Nutriente.GRASAS)
    val carbohidratos: Double get() = get(Nutriente.CARBOHIDRATOS)
}

/** Límites de la adecuación (% del requerimiento). */
object RangoAdecuacion {
    const val BAJO = 90
    const val ALTO = 110
}

enum class EstadoAdecuacion(val palabra: String) { BAJO("bajo"), ADECUADO("adecuado"), ALTO("alto") }

private fun cero() = Aporte(Nutriente.entries.associateWith { 0.0 }, emptyMap(), 0)

/** Aporte de UN ítem (sin redondear); null donde la fuente no trae el dato. Sin foto (portal): todo null. */
fun aporteDeItem(it: ItemComida): Map<Nutriente, Double?> {
    val p = it.por100 ?: return Nutriente.entries.associateWith { null }
    val f = it.gramos / 100
    fun v(x: Double?): Double? = x?.let { x * f }
    return mapOf(
        Nutriente.KCAL to v(p.kcal), Nutriente.PROTEINAS to v(p.proteinas), Nutriente.GRASAS to v(p.grasas),
        Nutriente.CARBOHIDRATOS to v(carbohidratosDisponibles(p)), Nutriente.CARBOHIDRATOS_TOTALES to v(p.carbohidratos),
        Nutriente.FIBRA to v(p.fibra), Nutriente.SODIO to v(p.sodio), Nutriente.HIERRO to v(p.hierro), Nutriente.CALCIO to v(p.calcio),
    )
}

/** Suma de una lista de ítems (sin redondear). Los ítems sin foto de nutrientes no suman. */
fun sumarAporte(items: List<ItemComida>?): Aporte {
    val v = Nutriente.entries.associateWith { 0.0 }.toMutableMap()
    val sin = mutableMapOf<Nutriente, Int>()
    var n = 0
    for (it in items.orEmpty()) {
        if (it.por100 == null) continue
        val x = aporteDeItem(it)
        for (k in Nutriente.entries) {
            val y = x[k]
            if (y == null) sin[k] = (sin[k] ?: 0) + 1 else v[k] = v.getValue(k) + y
        }
        n++
    }
    return Aporte(v, sin, n)
}

private fun sumarAportes(lista: List<Aporte>): Aporte {
    val v = Nutriente.entries.associateWith { 0.0 }.toMutableMap()
    val sin = mutableMapOf<Nutriente, Int>()
    var n = 0
    for (x in lista) {
        for (k in Nutriente.entries) {
            v[k] = v.getValue(k) + x[k]
            val s = x.sinDato[k] ?: 0
            if (s != 0) sin[k] = (sin[k] ?: 0) + s
        }
        n += x.items
    }
    return Aporte(v, sin, n)
}

data class AporteDia(val porComida: List<Aporte>, val total: Aporte, val conItems: Boolean)

/** Aporte de cada comida (sus ítems) y el total del día. `conItems` = hay al menos un alimento con foto. */
fun aporteDeComidas(itemsPorComida: List<List<ItemComida>>): AporteDia {
    val porComida = itemsPorComida.map { sumarAporte(it) }
    val total = if (porComida.isEmpty()) cero() else sumarAportes(porComida)
    return AporteDia(porComida, total, total.items > 0)
}

/** Para mostrar: kcal enteras, gramos con 1 decimal, sodio y calcio enteros, hierro con 1 decimal. */
fun redondearAporte(a: Aporte): Aporte = a.copy(valores = Nutriente.entries.associateWith { k ->
    val x = a[k]
    when (k) {
        Nutriente.KCAL, Nutriente.SODIO, Nutriente.CALCIO -> redondearJs(x)
        else -> redondear1(x)
    }
})

private fun alimentos(n: Int) = if (n == 1) "alimento" else "alimentos"

/**
 * Un total para MOSTRAR sin esconder lo que falta: "73 mg"; si a algún alimento
 * la fuente no le trae el dato, "≥ 73 mg · sin dato en 2 alimentos"; si falta en todos, "—".
 */
fun textoValor(a: Aporte, k: Nutriente): String {
    val faltan = a.sinDato[k] ?: 0
    if (a.items > 0 && faltan >= a.items) return "—"
    val v = "${numJs(redondearAporte(a)[k])} ${k.unidad}"
    return if (faltan > 0) "≥ $v · sin dato en $faltan ${alimentos(faltan)}" else v
}

/** ¿El total de ese nutriente es un piso (algún alimento sin dato)? */
fun incompleto(a: Aporte, k: Nutriente): Boolean = (a.sinDato[k] ?: 0) > 0

data class Reparto(val proteinas: Double, val grasas: Double, val carbohidratos: Double)

/**
 * % de la energía de proteínas, grasas y carbohidratos DISPONIBLES (4/9/4), con
 * 1 decimal y sumando exactamente 100 (el redondeo va a carbohidratos). null sin energía.
 */
fun repartoMacros(proteinas: Double, grasas: Double, carbohidratos: Double): Reparto? {
    val p = proteinas * KcalPorGramo.PROTEINAS
    val g = grasas * KcalPorGramo.GRASAS
    val c = carbohidratos * KcalPorGramo.CARBOHIDRATOS
    val t = p + g + c
    if (!(t > 0)) return null
    val rp = redondear1(p / t * 100)
    val rg = redondear1(g / t * 100)
    return Reparto(rp, rg, redondear1(100 - rp - rg))
}

fun repartoMacros(a: Aporte): Reparto? = repartoMacros(a.proteinas, a.grasas, a.carbohidratos)

data class Adecuacion(val valor: Double, val meta: Double, val pct: Int, val estado: EstadoAdecuacion)

/** % de adecuación (valor / meta), entero. null sin meta. */
fun adecuacion(valor: Double, meta: Double?): Adecuacion? {
    if (meta == null || !meta.isFinite() || meta <= 0) return null
    val pct = redondearJs(valor / meta * 100).toInt()
    val estado = when {
        pct < RangoAdecuacion.BAJO -> EstadoAdecuacion.BAJO
        pct > RangoAdecuacion.ALTO -> EstadoAdecuacion.ALTO
        else -> EstadoAdecuacion.ADECUADO
    }
    return Adecuacion(valor, meta, pct, estado)
}

data class Comparacion(
    val kcal: Adecuacion?,
    val proteinas: Adecuacion?,
    val grasas: Adecuacion?,
    val carbohidratos: Adecuacion?,
    /** % calculado de los alimentos. */
    val reparto: Reparto?,
    /** % que pide el plan (si están los tres). */
    val metaReparto: Reparto?,
)

/**
 * Totales del día (redondeados) contra el plan: kcal contra las kcal del plan y
 * cada macro (g) contra los gramos de kcal × % (gramosMacros).
 */
fun compararConPlan(total: Aporte, kcal: Double?, proteinasPct: Double?, carbohidratosPct: Double?, grasasPct: Double?): Comparacion {
    val r = redondearAporte(total)
    val tres = proteinasPct != null && carbohidratosPct != null && grasasPct != null
    val metas = if (tres) gramosMacros(kcal, proteinasPct, carbohidratosPct, grasasPct) else null
    return Comparacion(
        kcal = adecuacion(r.kcal, kcal),
        proteinas = adecuacion(r.proteinas, metas?.proteinas?.toDouble()),
        grasas = adecuacion(r.grasas, metas?.grasas?.toDouble()),
        carbohidratos = adecuacion(r.carbohidratos, metas?.carbohidratos?.toDouble()),
        reparto = repartoMacros(total),
        metaReparto = if (tres) Reparto(proteinasPct!!, grasasPct!!, carbohidratosPct!!) else null,
    )
}

data class EnergiaDesdeAporte(val kcal: Int, val proteinasPct: Double, val carbohidratosPct: Double, val grasasPct: Double)

/** "Usar estos totales en el plan": kcal enteras y los tres % (suman 100), o null fuera del rango del plan. */
fun energiaDesdeAporte(total: Aporte, min: Int = RangoKcal.MIN, max: Int = RangoKcal.MAX): EnergiaDesdeAporte? {
    val kcal = redondearJs(total.kcal).toInt()
    val rep = repartoMacros(total) ?: return null
    if (kcal < min || kcal > max) return null
    return EnergiaDesdeAporte(kcal, rep.proteinas, rep.carbohidratos, rep.grasas)
}

/**
 * La línea de una comida: "≈ 420 kcal · P 25 g · G 12 g · C 55 g". Un valor al
 * que le falta el dato de algún alimento va con "≥" y se avisa cuántos; si le falta a todos, "—".
 */
fun textoAporte(a: Aporte): String {
    val r = redondearAporte(a)
    fun v(k: Nutriente, etiqueta: String): String {
        val faltan = a.sinDato[k] ?: 0
        if (a.items > 0 && faltan >= a.items) return "$etiqueta—"
        return "$etiqueta${if (faltan > 0) "≥ " else ""}${numJs(r[k])} ${k.unidad}"
    }
    val faltan = listOf(Nutriente.KCAL, Nutriente.PROTEINAS, Nutriente.GRASAS, Nutriente.CARBOHIDRATOS).maxOf { a.sinDato[it] ?: 0 }
    val aviso = if (faltan > 0) " (sin dato en $faltan ${alimentos(faltan)})" else ""
    return "≈ ${v(Nutriente.KCAL, "")} · ${v(Nutriente.PROTEINAS, "P ")} · ${v(Nutriente.GRASAS, "G ")} · ${v(Nutriente.CARBOHIDRATOS, "C ")}$aviso"
}

/** "fibra sin dato en 2 alimentos" (solo de los nutrientes que se muestran). */
fun textoSinDato(a: Aporte, nutrientes: List<Nutriente> = listOf(Nutriente.CARBOHIDRATOS, Nutriente.FIBRA)): String? =
    nutrientes.filter { (a.sinDato[it] ?: 0) > 0 }
        .joinToString(" · ") { k -> "${k.etiqueta.lowercase()} sin dato en ${a.sinDato[k]} ${alimentos(a.sinDato[k]!!)}" }
        .ifEmpty { null }
