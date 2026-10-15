package pe.saniape.app.data.staff

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import pe.saniape.app.ui.clinica.atencion.normalizarFrase

/**
 * CHIPS Y AUTOCOMPLETADO QUE APRENDE del editor del plan alimentario
 * (Nutrición, ficha → 🥗 Plan alimentario). Gemelo PURO de
 * `lib/plan-alimentacion-chips.ts` de la web: las MISMAS listas base, la clave
 * del tiempo de comida y la mezcla base + aprendido (`frasesCampoPlan`).
 *
 * Lo aprendido lo arma el servidor (GET /api/staff/plan-alimentacion/sugerencias,
 * ver [PlanAlimentacionRepo.sugerencias]) con sus usos; la app solo lo lee y lo
 * mezcla: a chip visible solo con 2+ usos, al autocompletado todo. El
 * agregar/quitar un chip y el autocompletado son los de la consulta guiada
 * (`insertarFrase` / `quitarFrase` / `completarFragmento` de PasosBasicos.kt):
 * las comidas van uno por línea; el resto, con coma.
 */

/** Los campos de texto del plan que llevan chips (además del detalle de cada comida). */
val CAMPOS_TEXTO_PLAN: List<String> = listOf("objetivo", "evitar", "recomendaciones", "hidratacion")

/** true = uno por línea (el detalle de cada comida, como sale en la hoja); false = con coma. */
fun porLineaCampoPlan(campo: String): Boolean = campo == "comida"

val CHIPS_PLAN: Map<String, List<String>> = mapOf(
    "objetivo" to listOf(
        "Bajar de peso", "Mantener el peso", "Ganar masa muscular", "Controlar la glucosa",
        "Bajar colesterol y triglicéridos", "Controlar la presión arterial", "Alimentación en el embarazo",
        "Mejorar la digestión", "Subir de peso", "Comer más saludable", "Mejorar el rendimiento deportivo",
    ),
    "evitar" to listOf(
        "Gaseosas y jugos azucarados", "Frituras", "Embutidos", "Azúcar y dulces", "Harinas refinadas",
        "Comida rápida", "Alcohol", "Exceso de sal", "Galletas y snacks de paquete", "Pan blanco",
        "Mayonesa y cremas", "Bebidas energizantes",
    ),
    "recomendaciones" to listOf(
        "Comer cada 3 a 4 horas", "No saltarse el desayuno", "Masticar despacio", "Cenar 2 horas antes de dormir",
        "Medio plato de verduras en almuerzo y cena", "Preferir a la plancha o al horno", "Preferir sancochado en vez de frito",
        "Leer las etiquetas", "Servir en plato pequeño", "Caminar 30 minutos al día", "Comer sin pantallas",
        "Llevar su lonchera saludable",
    ),
    "hidratacion" to listOf(
        "2 litros de agua al día", "8 vasos de agua al día", "Agua antes de cada comida", "Infusiones sin azúcar",
        "Llevar siempre una botella de agua", "Más agua si hace ejercicio", "Agua en vez de gaseosa",
    ),
)

/** Opciones realistas (Perú) por tiempo de comida. Una por línea en el detalle. */
val CHIPS_COMIDAS: Map<String, List<String>> = mapOf(
    "Desayuno" to listOf(
        "Avena con fruta", "Pan integral con palta", "Pan integral con queso fresco", "Huevo sancochado",
        "Quinua con manzana", "Yogur natural sin azúcar", "1 taza de leche descremada", "Infusión sin azúcar",
        "Fruta picada", "Tortilla de verduras", "Café sin azúcar",
    ),
    "Media mañana" to listOf(
        "Fruta picada", "1 fruta entera", "Yogur natural", "Puñado de frutos secos", "Galletas integrales",
        "Palitos de zanahoria y pepino", "Queso fresco", "Gelatina sin azúcar",
    ),
    "Almuerzo" to listOf(
        "½ plato de verduras", "¼ de proteína (pollo, pescado o menestra)", "¼ de carbohidrato (arroz, papa o camote)",
        "Ensalada fresca", "Pescado a la plancha", "Pollo al horno sin piel", "Menestra con arroz", "Sopa de verduras",
        "Camote sancochado", "Fruta de postre", "Refresco sin azúcar",
    ),
    "Media tarde" to listOf(
        "Fruta picada", "Yogur natural", "Puñado de frutos secos", "Pan integral con palta", "Huevo sancochado",
        "Infusión sin azúcar", "Mazamorra de quinua sin azúcar",
    ),
    "Cena" to listOf(
        "Sopa de verduras", "Ensalada fresca", "Pescado a la plancha", "Pollo a la plancha", "Tortilla de verduras",
        "Verduras salteadas", "Infusión sin azúcar", "Cena ligera",
    ),
)

/** Para un tiempo de comida que no es uno de los cinco ("Pre entreno", "Merienda"). */
val CHIPS_COMIDA_GENERAL: List<String> = listOf(
    "Fruta picada", "Yogur natural", "Puñado de frutos secos", "Huevo sancochado", "Pan integral con palta",
    "Infusión sin azúcar", "Ensalada fresca", "Sopa de verduras",
)

/**
 * Un ítem aprendido: el texto y en cuántos planes se usó (null = no se sabe).
 */
data class AprendidoPlan(val texto: String, val usos: Int? = null)

/** Lo aprendido de la clínica (más usado primero). Un backend sin la ruta → todo vacío. */
data class SugerenciasPlan(
    val objetivo: List<AprendidoPlan> = emptyList(),
    val evitar: List<AprendidoPlan> = emptyList(),
    val recomendaciones: List<AprendidoPlan> = emptyList(),
    val hidratacion: List<AprendidoPlan> = emptyList(),
    /** Por tiempo de comida ("Desayuno", "Media mañana"… o el nombre que la clínica usa). */
    val comidas: Map<String, List<AprendidoPlan>> = emptyMap(),
) {
    fun deCampo(campo: String): List<AprendidoPlan> = when (campo) {
        "objetivo" -> objetivo
        "evitar" -> evitar
        "recomendaciones" -> recomendaciones
        "hidratacion" -> hidratacion
        else -> emptyList()
    }
}

/**
 * Clave para comparar: la de la consulta guiada y, como el NFD de la web,
 * la ñ como n ("media manana" = "Media mañana").
 */
private fun clavePlan(s: String): String = normalizarFrase(s).replace('ñ', 'n')

/**
 * El tiempo de comida tal como se guarda en las sugerencias: uno de los cinco
 * de siempre (sin importar tildes ni mayúsculas) o, si no, el escrito, limpio.
 */
fun claveTiempoComida(tiempo: String?): String {
    val t = tiempo.orEmpty().replace(Regex("""\s+"""), " ").trim()
    val n = clavePlan(t)
    return TIEMPOS_COMIDA.firstOrNull { clavePlan(it) == n } ?: t
}

/** Chips base de un tiempo de comida (los de su tiempo o, si no es de los cinco, los generales). */
fun chipsBaseComida(tiempo: String?): List<String> = CHIPS_COMIDAS[claveTiempoComida(tiempo)] ?: CHIPS_COMIDA_GENERAL

/** Lo aprendido para un tiempo de comida (busca sin tildes ni mayúsculas). */
fun aprendidosDeComida(s: SugerenciasPlan?, tiempo: String?): List<AprendidoPlan> {
    if (s == null) return emptyList()
    val k = clavePlan(claveTiempoComida(tiempo))
    return s.comidas.entries.firstOrNull { clavePlan(it.key) == k }?.value.orEmpty()
}

/** Cuántos aprendidos que NO están en la base suben a chip (el resto, solo al autocompletado). */
const val APRENDIDOS_EN_CHIPS = 4
/**
 * Un aprendido sube a CHIP solo si se usó en al menos estos planes (como
 * USOS_PARA_CHIP de la consulta guiada); uno suelto queda en el
 * autocompletado. Sin el dato de usos, tampoco es chip.
 */
const val USOS_PARA_CHIP_PLAN = 2
/** Largo máximo de un aprendido para ser chip (los largos solo van al autocompletado). */
const val MAX_LARGO_CHIP = 40

data class ChipPlan(val texto: String, val aprendida: Boolean = false)

/** Chips y autocompletado de un campo (lo que pinta el campo del editor). */
data class FrasesCampoPlan(val chips: List<ChipPlan>, val opciones: List<String>, val porLinea: Boolean)

/**
 * Gemelo de `frasesCampoPlan`:
 *  · autocompletado: lo aprendido primero (más usado primero) y luego la base, sin repetir;
 *  · chips: primero los de la base que la clínica más usa (en el orden de lo
 *    aprendido), luego hasta [APRENDIDOS_EN_CHIPS] aprendidos cortos, usados en
 *    [USOS_PARA_CHIP_PLAN]+ planes, que no están en la base, y el resto de la
 *    base en su orden.
 */
fun frasesCampoPlan(base: List<String>, aprendidos: List<AprendidoPlan>?, porLinea: Boolean): FrasesCampoPlan {
    val aprItems = aprendidos.orEmpty()
    val apr = aprItems.map { it.texto }
    val enBase = base.associateBy { clavePlan(it) }
    val vistos = HashSet<String>()
    val opciones = mutableListOf<String>()
    for (o in apr + base) {
        val k = clavePlan(o)
        if (k.isEmpty() || !vistos.add(k)) continue
        opciones += o
    }
    val chips = mutableListOf<ChipPlan>()
    val enChips = HashSet<String>()
    fun poner(texto: String, aprendida: Boolean) {
        val k = clavePlan(texto)
        if (k.isEmpty() || !enChips.add(k)) return
        chips += ChipPlan(texto, aprendida)
    }
    for (a in apr) enBase[clavePlan(a)]?.let { poner(it, false) }
    var extra = 0
    for (item in aprItems) {
        if (extra >= APRENDIDOS_EN_CHIPS) break
        val a = item.texto
        val k = clavePlan(a)
        if ((item.usos ?: 0) < USOS_PARA_CHIP_PLAN) continue
        if (k in enBase || a.length > MAX_LARGO_CHIP || k in enChips) continue
        poner(a, true); extra++
    }
    for (b in base) poner(b, false)
    return FrasesCampoPlan(chips, opciones, porLinea)
}

/** Las frases de un campo de texto del plan ("objetivo", "evitar", "recomendaciones", "hidratacion"). */
fun frasesCampoTextoPlan(campo: String, s: SugerenciasPlan?): FrasesCampoPlan =
    frasesCampoPlan(CHIPS_PLAN[campo].orEmpty(), s?.deCampo(campo), porLineaCampoPlan(campo))

/** Las frases del detalle de una comida (chips de su tiempo + lo que la clínica ya usa). */
fun frasesComidaPlan(tiempo: String?, s: SugerenciasPlan?): FrasesCampoPlan =
    frasesCampoPlan(chipsBaseComida(tiempo), aprendidosDeComida(s, tiempo), porLineaCampoPlan("comida"))

// ─── JSON del API → modelo (tolerante: lo que no es lista de textos, vacío) ──

/**
 * Contrato: cada ítem es un objeto con texto y usos (más usado primero). Un
 * servidor anterior mandaba solo el texto: cuenta como 1 uso (autocompletado,
 * nunca chip). Lo que no es ninguno de los dos se descarta.
 */
private fun textos(v: kotlinx.serialization.json.JsonElement?): List<AprendidoPlan> = (v as? JsonArray).orEmpty().mapNotNull { x ->
    when (x) {
        is JsonPrimitive -> x.takeIf { it !is JsonNull && it.isString }?.content?.trim()?.takeIf { it.isNotEmpty() }
            ?.let { AprendidoPlan(it, 1) }
        is JsonObject -> {
            val t = (x["texto"] as? JsonPrimitive)?.takeIf { it !is JsonNull && it.isString }?.content?.trim()
            val usos = (x["usos"] as? JsonPrimitive)?.takeIf { it !is JsonNull }?.content?.toDoubleOrNull()?.toInt()
            t?.takeIf { it.isNotEmpty() }?.let { AprendidoPlan(it, usos) }
        }
        else -> null
    }
}

fun sugerenciasPlanDeJson(o: JsonObject?): SugerenciasPlan {
    if (o == null) return SugerenciasPlan()
    val comidas = (o["comidas"] as? JsonObject).orEmpty()
        .mapValues { (_, v) -> textos(v) }
        .filter { (k, v) -> k.isNotBlank() && v.isNotEmpty() }
    return SugerenciasPlan(
        objetivo = textos(o["objetivo"]),
        evitar = textos(o["evitar"]),
        recomendaciones = textos(o["recomendaciones"]),
        hidratacion = textos(o["hidratacion"]),
        comidas = comidas,
    )
}
