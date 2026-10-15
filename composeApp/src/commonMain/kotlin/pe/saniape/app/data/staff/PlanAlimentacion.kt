package pe.saniape.app.data.staff

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlin.math.abs
import kotlin.math.floor

/**
 * PLAN ALIMENTARIO (Nutrición): la dieta que el nutricionista deja en cada
 * control, en la pestaña 🥗 de la ficha. Tablas `planes_alimentacion` y
 * `plantillas_plan_alimentacion`, vía /api/staff/plan-alimentacion (ver
 * [PlanAlimentacionRepo]).
 *
 * Gemelo PURO de `lib/plan-alimentacion.ts` de la web: validación (mismos
 * textos), comidas, macros, el texto del WhatsApp (salida idéntica) y la hoja
 * imprimible. La calculadora vive en RequerimientoEnergetico.kt.
 */

/** Tiempos de comida con los que arranca un plan nuevo (se pueden quitar y agregar). */
val TIEMPOS_COMIDA: List<String> = listOf("Desayuno", "Media mañana", "Almuerzo", "Media tarde", "Cena")

/** Límites de texto (gemelos de LIMITES_PLAN). */
object LimitesPlan {
    const val OBJETIVO = 500
    const val TEXTO = 4000 // recomendaciones, evitar, hidratación
    const val TIEMPO = 60
    const val DETALLE = 2000
    const val COMIDAS = 12
    const val NOMBRE_PLANTILLA = 80
    const val CALCULO = 4000 // JSON serializado
}

/** Gemelos de los CHECK de la base. */
object RangoKcal {
    const val MIN = 500
    const val MAX = 6000
}

data class ComidaPlan(
    val tiempo: String,
    /** "07:30" o null */
    val hora: String? = null,
    val detalle: String = "",
)

/** Lo común del plan y la plantilla (para "partir de" uno u otro). */
interface ContenidoPlanFuente {
    val objetivo: String?
    val kcal: Int?
    val proteinasPct: Double?
    val carbohidratosPct: Double?
    val grasasPct: Double?
    val comidas: List<ComidaPlan>
    val recomendaciones: String?
    val evitar: String?
    val hidratacion: String?
}

/** Una fila de `planes_alimentacion`. */
data class PlanAlimentacion(
    val id: String,
    val pacienteId: String,
    val fecha: String,
    override val objetivo: String? = null,
    override val kcal: Int? = null,
    override val proteinasPct: Double? = null,
    override val carbohidratosPct: Double? = null,
    override val grasasPct: Double? = null,
    override val comidas: List<ComidaPlan> = emptyList(),
    override val recomendaciones: String? = null,
    override val evitar: String? = null,
    override val hidratacion: String? = null,
    val calculo: JsonObject? = null,
    val tratamientoId: String? = null,
    val citaId: String? = null,
    val terapeutaId: String? = null,
    val createdAt: String? = null,
) : ContenidoPlanFuente

/** Una fila de `plantillas_plan_alimentacion`. */
data class PlantillaPlan(
    val id: String,
    val nombre: String,
    override val objetivo: String? = null,
    override val kcal: Int? = null,
    override val proteinasPct: Double? = null,
    override val carbohidratosPct: Double? = null,
    override val grasasPct: Double? = null,
    override val comidas: List<ComidaPlan> = emptyList(),
    override val recomendaciones: String? = null,
    override val evitar: String? = null,
    override val hidratacion: String? = null,
) : ContenidoPlanFuente

fun comidasPorDefecto(): List<ComidaPlan> = TIEMPOS_COMIDA.map { ComidaPlan(it, null, "") }

private val RE_FECHA = Regex("""^\d{4}-\d{2}-\d{2}$""")
private val RE_HORA = Regex("""^([01]\d|2[0-3]):[0-5]\d$""")

/**
 * Comidas → lista limpia. Las que no tienen detalle se descartan: el editor
 * arranca con los 5 tiempos y no todos se usan.
 */
fun normalizarComidas(v: List<ComidaPlan>?): List<ComidaPlan> = v.orEmpty().mapNotNull { c ->
    val detalle = c.detalle.trim()
    if (detalle.isEmpty()) return@mapNotNull null
    val hora = c.hora.orEmpty().trim()
    ComidaPlan(
        tiempo = c.tiempo.trim().ifEmpty { "Comida" }.take(LimitesPlan.TIEMPO),
        hora = if (RE_HORA.matches(hora)) hora else null,
        detalle = detalle.take(LimitesPlan.DETALLE),
    )
}

// ─── Validación (gemela de validarPlan / validarPlantilla) ──────────────────

/** Lo que arma el editor (los números como texto, tal cual se escribieron). */
data class BorradorPlan(
    val pacienteId: String,
    val fecha: String? = null,
    val objetivo: String? = null,
    val kcal: String? = null,
    val proteinasPct: String? = null,
    val carbohidratosPct: String? = null,
    val grasasPct: String? = null,
    val comidas: List<ComidaPlan>? = null,
    val recomendaciones: String? = null,
    val evitar: String? = null,
    val hidratacion: String? = null,
    val calculo: JsonObject? = null,
)

private fun validarEnergia(kcalTxt: String?, p: String?, c: String?, g: String?, e: MutableList<String>) {
    val kcal = aNumero(kcalTxt)
    if (kcal != null) {
        if (kcal.isNaN() || kcal != floor(kcal)) e.add("Kcal: escribe un número entero.")
        else if (kcal < RangoKcal.MIN || kcal > RangoKcal.MAX) e.add("Kcal: entre ${RangoKcal.MIN} y ${RangoKcal.MAX}.")
    }
    val pcts = listOf("Proteínas" to aNumero(p), "Carbohidratos" to aNumero(c), "Grasas" to aNumero(g))
    var malos = false
    for ((nombre, v) in pcts) {
        if (v == null) continue
        if (v.isNaN()) { e.add("$nombre: escribe solo el número."); malos = true }
        else if (v < 0 || v > 100) { e.add("$nombre: entre 0 y 100 %."); malos = true }
    }
    if (malos) return
    val puestos = pcts.count { it.second != null }
    if (puestos in 1..2) e.add("Completa los tres porcentajes (proteínas, carbohidratos y grasas) o deja los tres vacíos.")
    if (puestos == 3) {
        val suma = pcts.sumOf { it.second!! }
        if (abs(suma - 100) > 1) e.add("Los porcentajes suman ${numJs(redondearJs(suma * 10) / 10)} %: tienen que sumar 100 %.")
    }
}

private fun validarTextos(
    objetivo: String?, recomendaciones: String?, evitar: String?, hidratacion: String?, comidas: List<ComidaPlan>?,
    e: MutableList<String>,
) {
    if (objetivo.orEmpty().trim().length > LimitesPlan.OBJETIVO) e.add("El objetivo es muy largo (máx. ${LimitesPlan.OBJETIVO} caracteres).")
    for ((v, nombre) in listOf(recomendaciones to "Las recomendaciones", evitar to "Los alimentos a evitar", hidratacion to "La hidratación")) {
        if (v.orEmpty().trim().length > LimitesPlan.TEXTO) e.add("$nombre: texto muy largo (máx. ${LimitesPlan.TEXTO} caracteres).")
    }
    if (comidas != null) {
        if (normalizarComidas(comidas).size > LimitesPlan.COMIDAS) e.add("Máximo ${LimitesPlan.COMIDAS} comidas por día.")
        if (comidas.any { it.detalle.trim().length > LimitesPlan.DETALLE }) {
            e.add("El detalle de una comida es muy largo (máx. ${LimitesPlan.DETALLE} caracteres).")
        }
    }
}

/** Errores para mostrar (vacío = válido). Mismos textos que la web. */
fun validarPlan(b: BorradorPlan): List<String> {
    val e = mutableListOf<String>()
    if (b.pacienteId.isBlank()) e.add("Falta el paciente.")
    if (!b.fecha.isNullOrEmpty() && !RE_FECHA.matches(b.fecha)) e.add("Fecha inválida.")
    validarTextos(b.objetivo, b.recomendaciones, b.evitar, b.hidratacion, b.comidas, e)
    validarEnergia(b.kcal, b.proteinasPct, b.carbohidratosPct, b.grasasPct, e)
    if (b.calculo != null && b.calculo.toString().length > LimitesPlan.CALCULO) e.add("El cálculo es demasiado grande.")
    if (e.isEmpty() && b.objetivo.orEmpty().trim().isEmpty() && normalizarComidas(b.comidas).isEmpty()) {
        e.add("Escribe el objetivo o al menos una comida.")
    }
    return e
}

/** Errores de "Guardar como plantilla" (la plantilla sale de un plan ya guardado). */
fun validarPlantilla(nombre: String, contenido: ContenidoPlanFuente): List<String> {
    val e = mutableListOf<String>()
    val n = nombre.trim()
    if (n.isEmpty()) e.add("Ponle un nombre a la plantilla.")
    else if (n.length > LimitesPlan.NOMBRE_PLANTILLA) e.add("El nombre es muy largo (máx. ${LimitesPlan.NOMBRE_PLANTILLA} caracteres).")
    validarTextos(contenido.objetivo, contenido.recomendaciones, contenido.evitar, contenido.hidratacion, contenido.comidas, e)
    validarEnergia(
        contenido.kcal?.toString(), contenido.proteinasPct?.let(::numJs), contenido.carbohidratosPct?.let(::numJs),
        contenido.grasasPct?.let(::numJs), e,
    )
    if (e.isEmpty() && contenido.objetivo.orEmpty().trim().isEmpty() && normalizarComidas(contenido.comidas).isEmpty()) {
        e.add("La plantilla está vacía: escribe el objetivo o al menos una comida.")
    }
    return e
}

// ─── Editor: "partir de" y el cuerpo del pedido ─────────────────────────────

/** Una comida en el editor ([k] = clave estable para la lista). */
data class ComidaEditor(val k: Int, val tiempo: String, val hora: String?, val detalle: String) {
    fun aComida() = ComidaPlan(tiempo, hora, detalle)
}

/** El contenido del editor, todo como texto (gemelo de `Contenido` en la web). */
data class ContenidoEditorPlan(
    val objetivo: String = "",
    val kcal: String = "",
    val prot: String = "",
    val carb: String = "",
    val grasa: String = "",
    val comidas: List<ComidaEditor> = emptyList(),
    val recomendaciones: String = "",
    val evitar: String = "",
    val hidratacion: String = "",
)

private var siguienteK = 1
fun conClave(c: List<ComidaPlan>): List<ComidaEditor> = c.map { ComidaEditor(siguienteK++, it.tiempo, it.hora, it.detalle) }

/**
 * Contenido inicial del editor: en blanco (los 5 tiempos), o partiendo de un plan
 * (el último del paciente / el que se edita) o de una plantilla. Gemelo de
 * `contenidoDesde`: sin comidas guardadas, vuelven los 5 tiempos.
 */
fun contenidoDesde(b: ContenidoPlanFuente?): ContenidoEditorPlan {
    if (b == null) return ContenidoEditorPlan(comidas = conClave(comidasPorDefecto()))
    val comidas = normalizarComidas(b.comidas)
    return ContenidoEditorPlan(
        objetivo = b.objetivo.orEmpty(),
        kcal = b.kcal?.toString().orEmpty(),
        prot = b.proteinasPct?.let(::numJs).orEmpty(),
        carb = b.carbohidratosPct?.let(::numJs).orEmpty(),
        grasa = b.grasasPct?.let(::numJs).orEmpty(),
        comidas = conClave(comidas.ifEmpty { comidasPorDefecto() }),
        recomendaciones = b.recomendaciones.orEmpty(),
        evitar = b.evitar.orEmpty(),
        hidratacion = b.hidratacion.orEmpty(),
    )
}

/** Editor → borrador para validar y enviar. */
fun borradorDesdeEditor(pacienteId: String, fecha: String, c: ContenidoEditorPlan, calculo: JsonObject?): BorradorPlan {
    fun vacio(v: String) = v.trim().ifEmpty { null }
    return BorradorPlan(
        pacienteId = pacienteId, fecha = fecha, objetivo = vacio(c.objetivo), kcal = vacio(c.kcal),
        proteinasPct = vacio(c.prot), carbohidratosPct = vacio(c.carb), grasasPct = vacio(c.grasa),
        comidas = c.comidas.map { it.aComida() },
        recomendaciones = vacio(c.recomendaciones), evitar = vacio(c.evitar), hidratacion = vacio(c.hidratacion),
        calculo = calculo,
    )
}

/**
 * Kcal de la calculadora para el campo: dentro de lo que acepta la base
 * (500–6000). La sugerencia ya tiene piso de 1200/1500; el techo protege a un
 * paciente muy pesado y muy activo, que daría más de 6000 y el guardado fallaría.
 */
fun kcalParaUsar(s: SnapshotCalculo): Int = s.kcal.coerceIn(RangoKcal.MIN, RangoKcal.MAX)

/** Lo que hace "Usar estas kcal": pone las kcal y, si los tres % están vacíos, 25/50/25. */
fun usarCalculoEnEditor(c: ContenidoEditorPlan, s: SnapshotCalculo): ContenidoEditorPlan {
    val sinMacros = c.prot.isBlank() && c.carb.isBlank() && c.grasa.isBlank()
    val base = c.copy(kcal = kcalParaUsar(s).toString())
    return if (!sinMacros) base else base.copy(
        prot = MacrosPorDefecto.PROTEINAS.toString(),
        carb = MacrosPorDefecto.CARBOHIDRATOS.toString(),
        grasa = MacrosPorDefecto.GRASAS.toString(),
    )
}

private fun numeroONull(v: String?): Double? = aNumero(v)?.takeIf { !it.isNaN() }

/**
 * Cuerpo del POST (con [pacienteId]) o del PATCH (con [id]) — YA validado.
 * La fecha va SIEMPRE (el "hoy" de la sede): el respaldo del servidor es Lima.
 * Las comidas van como se escribieron; el servidor descarta las vacías.
 */
fun cuerpoPlan(b: BorradorPlan, id: String? = null): JsonObject = buildJsonObject {
    if (id != null) put("id", id) else put("pacienteId", b.pacienteId)
    b.fecha?.takeIf { it.isNotBlank() }?.let { put("fecha", it) }
    fun texto(k: String, v: String?) { if (v.isNullOrBlank()) put(k, JsonNull) else put(k, v.trim()) }
    texto("objetivo", b.objetivo)
    val kcal = numeroONull(b.kcal)
    if (kcal == null) put("kcal", JsonNull) else put("kcal", redondearJs(kcal).toLong())
    for ((k, v) in listOf("proteinasPct" to b.proteinasPct, "carbohidratosPct" to b.carbohidratosPct, "grasasPct" to b.grasasPct)) {
        val n = numeroONull(v)
        if (n == null) put(k, JsonNull) else put(k, n)
    }
    put("comidas", comidasJson(b.comidas.orEmpty()))
    texto("recomendaciones", b.recomendaciones)
    texto("evitar", b.evitar)
    texto("hidratacion", b.hidratacion)
    put("calculo", b.calculo ?: JsonNull)
}

private fun comidasJson(comidas: List<ComidaPlan>): JsonArray = buildJsonArray {
    comidas.forEach { c ->
        add(buildJsonObject {
            put("tiempo", c.tiempo)
            put("hora", c.hora?.let { JsonPrimitive(it) } ?: JsonNull)
            put("detalle", c.detalle)
        })
    }
}

/** Cuerpo de "Guardar como plantilla" (los datos del plan, sin los del paciente). */
fun cuerpoPlantilla(nombre: String, p: ContenidoPlanFuente): JsonObject = buildJsonObject {
    put("nombre", nombre.trim())
    put("objetivo", p.objetivo?.let { JsonPrimitive(it) } ?: JsonNull)
    put("kcal", p.kcal?.let { JsonPrimitive(it) } ?: JsonNull)
    put("proteinasPct", p.proteinasPct?.let { JsonPrimitive(it) } ?: JsonNull)
    put("carbohidratosPct", p.carbohidratosPct?.let { JsonPrimitive(it) } ?: JsonNull)
    put("grasasPct", p.grasasPct?.let { JsonPrimitive(it) } ?: JsonNull)
    put("comidas", comidasJson(p.comidas))
    put("recomendaciones", p.recomendaciones?.let { JsonPrimitive(it) } ?: JsonNull)
    put("evitar", p.evitar?.let { JsonPrimitive(it) } ?: JsonNull)
    put("hidratacion", p.hidratacion?.let { JsonPrimitive(it) } ?: JsonNull)
}

// ─── JSON del API → modelo ───────────────────────────────────────────────────

private fun JsonObject.txtPlan(k: String): String? =
    (this[k] as? JsonPrimitive)?.takeIf { it !is JsonNull }?.content?.takeIf { it.isNotBlank() && it != "null" }
private fun JsonObject.numPlan(k: String): Double? =
    (this[k] as? JsonPrimitive)?.takeIf { it !is JsonNull }?.content?.trim()?.toDoubleOrNull()?.takeIf { it.isFinite() }

private fun comidasDeJson(v: kotlinx.serialization.json.JsonElement?): List<ComidaPlan> = (v as? JsonArray).orEmpty().mapNotNull { x ->
    val o = x as? JsonObject ?: return@mapNotNull null
    ComidaPlan(o.txtPlan("tiempo").orEmpty(), o.txtPlan("hora"), (o["detalle"] as? JsonPrimitive)?.takeIf { it !is JsonNull }?.content.orEmpty())
}

/** Fila del API → modelo. Sin id se descarta. (numeric de Postgres puede llegar como texto.) */
fun planDeJson(o: JsonObject): PlanAlimentacion? {
    val id = o.txtPlan("id") ?: return null
    return PlanAlimentacion(
        id = id,
        pacienteId = o.txtPlan("paciente_id").orEmpty(),
        fecha = o.txtPlan("fecha").orEmpty().take(10),
        objetivo = o.txtPlan("objetivo"),
        kcal = o.numPlan("kcal")?.let { redondearJs(it).toInt() },
        proteinasPct = o.numPlan("proteinas_pct"),
        carbohidratosPct = o.numPlan("carbohidratos_pct"),
        grasasPct = o.numPlan("grasas_pct"),
        comidas = comidasDeJson(o["comidas"]),
        recomendaciones = o.txtPlan("recomendaciones"),
        evitar = o.txtPlan("evitar"),
        hidratacion = o.txtPlan("hidratacion"),
        calculo = o["calculo"] as? JsonObject,
        tratamientoId = o.txtPlan("tratamiento_id"),
        citaId = o.txtPlan("cita_id"),
        terapeutaId = o.txtPlan("terapeuta_id"),
        createdAt = o.txtPlan("created_at"),
    )
}

fun plantillaDeJson(o: JsonObject): PlantillaPlan? {
    val id = o.txtPlan("id") ?: return null
    return PlantillaPlan(
        id = id,
        nombre = o.txtPlan("nombre") ?: "Plantilla",
        objetivo = o.txtPlan("objetivo"),
        kcal = o.numPlan("kcal")?.let { redondearJs(it).toInt() },
        proteinasPct = o.numPlan("proteinas_pct"),
        carbohidratosPct = o.numPlan("carbohidratos_pct"),
        grasasPct = o.numPlan("grasas_pct"),
        comidas = comidasDeJson(o["comidas"]),
        recomendaciones = o.txtPlan("recomendaciones"),
        evitar = o.txtPlan("evitar"),
        hidratacion = o.txtPlan("hidratacion"),
    )
}

/** Más reciente primero (fecha y, a igual fecha, el último creado). */
fun ordenarPlanes(planes: List<PlanAlimentacion>): List<PlanAlimentacion> =
    planes.sortedWith(compareByDescending<PlanAlimentacion> { it.fecha }.thenByDescending { it.createdAt.orEmpty() })

// ─── Presentación ────────────────────────────────────────────────────────────

/** "25 %" sin decimales de más. */
private fun fmtPct(n: Double) = "${numJs(redondearJs(n * 10) / 10)} %"

/** Porcentaje para mostrar (25 → "25"; 33.33 → "33.3"). */
fun textoPct(n: Double): String = numJs(redondearJs(n * 10) / 10)

data class MacroPlan(val nombre: String, val pct: Double, val gramos: Int?)

/** Macros del plan: porcentaje y gramos por día (solo si están los tres). */
fun macrosDePlan(p: ContenidoPlanFuente): List<MacroPlan>? {
    val pr = p.proteinasPct ?: return null
    val ca = p.carbohidratosPct ?: return null
    val gr = p.grasasPct ?: return null
    val g = gramosMacros(p.kcal?.toDouble(), pr, ca, gr)
    return listOf(
        MacroPlan("Proteínas", pr, g.proteinas),
        MacroPlan("Carbohidratos", ca, g.carbohidratos),
        MacroPlan("Grasas", gr, g.grasas),
    )
}

/** "Proteínas 25 % (113 g) · Carbohidratos 50 % (225 g) · Grasas 25 % (50 g)" */
fun textoMacros(p: ContenidoPlanFuente): String? =
    macrosDePlan(p)?.joinToString(" · ") { m -> "${m.nombre} ${fmtPct(m.pct)}${m.gramos?.let { " ($it g)" }.orEmpty()}" }

/** "2026-10-14" → "14/10/2026" (determinista). */
fun fechaPlan(iso: String): String {
    val m = Regex("""^(\d{4})-(\d{2})-(\d{2})""").find(iso) ?: return iso
    return "${m.groupValues[3]}/${m.groupValues[2]}/${m.groupValues[1]}"
}

/** La hora "07:30" → "7:30 a. m." */
fun horaPlan(h: String?): String? {
    val m = Regex("""^(\d{2}):(\d{2})$""").find(h.orEmpty()) ?: return null
    val hh = m.groupValues[1].toInt()
    val sufijo = if (hh < 12) "a. m." else "p. m."
    val h12 = if (hh % 12 == 0) 12 else hh % 12
    return "$h12:${m.groupValues[2]} $sufijo"
}

/**
 * Texto plano del plan para WhatsApp (*negrita*). Salida IDÉNTICA a
 * `textoPlanWhatsApp` de la web. Se abre con `urlWhatsApp` (api.whatsapp.com/send):
 * NUNCA wa.me?text=, que rompe los emojis.
 */
fun textoPlanWhatsApp(
    plan: PlanAlimentacion,
    clinica: String? = null,
    paciente: String? = null,
    profesional: String? = null,
): String {
    val lineas = mutableListOf<String>()
    val nombre = paciente.orEmpty().trim().split(Regex("""\s+""")).first()
    lineas.add("🥗 *Plan alimentario*${if (!clinica.isNullOrEmpty()) " — ${clinica.trim()}" else ""}")
    lineas.add("${if (nombre.isNotEmpty()) "Hola $nombre, este" else "Este"} es tu plan del ${fechaPlan(plan.fecha)}.")
    plan.objetivo?.trim()?.takeIf { it.isNotEmpty() }?.let { lineas += listOf("", "🎯 *Objetivo:* $it") }
    val macros = textoMacros(plan)
    val kcal = plan.kcal?.takeIf { it != 0 }
    if (kcal != null || macros != null) {
        lineas += listOf("", "🔥 *Energía:* ${listOfNotNull(kcal?.let { "$it kcal al día" }, macros).joinToString(" · ")}")
    }
    val comidas = normalizarComidas(plan.comidas)
    if (comidas.isNotEmpty()) {
        lineas += listOf("", "🍽️ *Comidas del día*")
        for (c in comidas) {
            val h = horaPlan(c.hora)
            lineas += listOf("", "*${c.tiempo}*${h?.let { " ($it)" }.orEmpty()}", c.detalle)
        }
    }
    plan.recomendaciones?.trim()?.takeIf { it.isNotEmpty() }?.let { lineas += listOf("", "✅ *Recomendaciones*", it) }
    plan.evitar?.trim()?.takeIf { it.isNotEmpty() }?.let { lineas += listOf("", "🚫 *Evitar*", it) }
    plan.hidratacion?.trim()?.takeIf { it.isNotEmpty() }?.let { lineas += listOf("", "💧 *Hidratación*", it) }
    profesional?.trim()?.takeIf { it.isNotEmpty() }?.let { lineas += listOf("", "— $it") }
    return lineas.joinToString("\n")
}

// ─── Hoja imprimible (gemela de /pacientes/[id]/plan-alimentacion/[planId]) ──

private fun escPlan(s: String?): String = s.orEmpty()
    .replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;").replace("'", "&#39;")

/**
 * La hoja A4 que el nutricionista le entrega al paciente, armada en la app (la
 * página web pide la sesión del navegador; el visor nativo imprime este HTML).
 * Todo lo que viene de la base se escapa; el color solo si es #rrggbb.
 */
fun htmlPlanAlimentario(
    plan: PlanAlimentacion,
    clinicaNombre: String?,
    clinicaDireccion: String?,
    logoUrl: String?,
    colorPrincipal: String?,
    pacienteNombre: String?,
    pacienteDocumento: String?,
    profesional: String?,
): String {
    val color = colorPrincipal?.takeIf { Regex("^#[0-9a-fA-F]{6}$").matches(it) } ?: "#2c3e7a"
    val comidas = normalizarComidas(plan.comidas)
    val macros = macrosDePlan(plan)
    val logo = logoUrl?.takeIf { it.startsWith("https://") }
        ?.let { "<img src=\"${escPlan(it)}\" alt=\"\" class=\"logo\">" }.orEmpty()
    val energia = buildString {
        plan.kcal?.let { append("<span class=\"chip\"><b class=\"g\">$it</b> kcal/día</span>") }
        macros?.forEach { m ->
            append("<span class=\"chip\"><b>${escPlan(m.nombre)}</b> ${textoPct(m.pct)} %${m.gramos?.let { " · $it g" }.orEmpty()}</span>")
        }
    }
    val tabla = if (comidas.isEmpty()) "" else
        "<table><thead><tr><th style=\"width:28%\">Comida</th><th>Qué comer</th></tr></thead><tbody>" +
            comidas.joinToString("") { c ->
                "<tr><td><b>${escPlan(c.tiempo)}</b>${horaPlan(c.hora)?.let { "<div class=\"s\">${escPlan(it)}</div>" }.orEmpty()}</td>" +
                    "<td class=\"pre\">${escPlan(c.detalle)}</td></tr>"
            } + "</tbody></table>"
    val bloques = listOf("Recomendaciones" to plan.recomendaciones, "Alimentos a evitar" to plan.evitar, "Hidratación" to plan.hidratacion)
        .filter { !it.second.isNullOrBlank() }
        .joinToString("") { (t, v) -> "<div class=\"bloque\"><div class=\"t\">${escPlan(t)}</div><div class=\"pre\">${escPlan(v)}</div></div>" }
    return """<!doctype html><html lang="es"><head><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1">
<title>Plan alimentario — ${escPlan(pacienteNombre)}</title>
<style>
@page { size: A4 portrait; margin: 1.5cm; }
* { box-sizing: border-box; }
body { font-family: Lato, 'Segoe UI', Arial, sans-serif; color: #111827; margin: 0 auto; max-width: 800px; padding: 16px; font-size: 10.5pt; line-height: 1.35; -webkit-print-color-adjust: exact; print-color-adjust: exact; }
.cab { display: flex; align-items: center; gap: 12px; padding-bottom: 10px; border-bottom: 2px solid $color; }
.logo { width: 64px; height: 64px; object-fit: contain; }
.cli { flex: 1; min-width: 0; } .cli .n { font-weight: bold; font-size: 13pt; text-transform: uppercase; }
.s { color: #4b5563; font-size: 8.5pt; }
.doc { text-align: right; } .doc .n { font-weight: bold; font-size: 12pt; color: $color; }
.pac { padding: 8px 0; border-bottom: 1px solid #e5e7eb; font-size: 9.5pt; }
.pac span { margin-right: 24px; }
.t { font-weight: bold; font-size: 9pt; text-transform: uppercase; letter-spacing: .03em; color: $color; }
.pre { white-space: pre-wrap; word-break: break-word; }
.chips { margin-top: 12px; } .chip { display: inline-block; border: 1px solid #d1d5db; border-radius: 8px; padding: 5px 10px; margin: 0 6px 6px 0; font-size: 9.5pt; }
.g { font-size: 11pt; }
table { width: 100%; border-collapse: collapse; margin-top: 14px; font-size: 9.5pt; }
th { background: $color; color: #fff; text-align: left; padding: 6px 8px; }
td { border-bottom: 1px solid #e5e7eb; padding: 8px; vertical-align: top; }
tr { break-inside: avoid; }
.bloques { display: grid; grid-template-columns: repeat(auto-fit, minmax(180px, 1fr)); gap: 10px; margin-top: 14px; }
.bloque { border: 1px solid #e5e7eb; border-radius: 8px; padding: 9px; break-inside: avoid; font-size: 9.5pt; }
.firma { margin-top: 40px; display: flex; justify-content: flex-end; }
.firma div { width: 60mm; text-align: center; font-size: 8.5pt; }
.linea { border-top: 1px solid #1f2937; margin-top: 14mm; padding-top: 2px; }
.pie { margin-top: 24px; padding-top: 6px; border-top: 1px solid #e5e7eb; font-size: 7.5pt; color: #9ca3af; display: flex; justify-content: space-between; gap: 8px; }
</style></head><body>
<div class="cab">$logo<div class="cli"><div class="n">${escPlan(clinicaNombre)}</div>${clinicaDireccion?.takeIf { it.isNotBlank() }?.let { "<div class=\"s\">${escPlan(it)}</div>" }.orEmpty()}</div>
<div class="doc"><div class="n">PLAN ALIMENTARIO</div><div class="s">${escPlan(fechaPlan(plan.fecha))}</div></div></div>
<div class="pac"><span><b>Paciente:</b> ${escPlan(pacienteNombre?.takeIf { it.isNotBlank() } ?: "—")}</span>${pacienteDocumento?.takeIf { it.isNotBlank() }?.let { "<span><b>Documento:</b> ${escPlan(it)}</span>" }.orEmpty()}</div>
${plan.objetivo?.takeIf { it.isNotBlank() }?.let { "<div style=\"margin-top:12px\"><div class=\"t\">Objetivo</div><div class=\"pre\">${escPlan(it)}</div></div>" }.orEmpty()}
${if (energia.isNotEmpty()) "<div class=\"chips\">$energia</div>" else ""}
$tabla
${if (bloques.isNotEmpty()) "<div class=\"bloques\">$bloques</div>" else ""}
<div class="firma"><div><div class="linea">Firma y sello del profesional</div>${profesional?.takeIf { it.isNotBlank() }?.let { "<b>${escPlan(it)}</b>" }.orEmpty()}</div></div>
<div class="pie"><span>Plan personal: no lo compartas ni lo cambies sin consultar a tu nutricionista.</span><span>Emitido con Sania</span></div>
</body></html>"""
}

/** Teléfono para avisos: el del apoderado si los recibe (gemelo de `telefonoParaAvisos`). */
fun telefonoParaAvisos(telefono: String?, apoderadoTelefono: String?, apoderadoRecibeAvisos: Boolean): String? =
    if (apoderadoRecibeAvisos && !apoderadoTelefono.isNullOrBlank()) apoderadoTelefono.trim() else telefono
