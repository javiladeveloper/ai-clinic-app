package pe.saniape.app.data.staff

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min

/**
 * BASE DE ALIMENTOS del plan alimentario (Nutrición, fase 2). Gemelo PURO de
 * `lib/alimentos.ts` de la web (contrato: docs/app-contrato-portal-paciente.md
 * §13): la forma unificada [Alimento] (catálogo TPCA 2023 + propios de la
 * clínica), la búsqueda en memoria sin tildes (MISMOS puntos y orden), las
 * cantidades ("½", "1 1/2", medidas caseras de la TAFERA 2016), los ÍTEMS de
 * una comida (alimento + cantidad + foto de nutrientes por 100 g) y la
 * validación de un alimento propio. Los cálculos están en AporteNutricional.kt.
 */

/** Cambia con cada semilla del catálogo: va en `?v=` y la caché de la sesión se renueva sola. */
const val VERSION_CATALOGO = "2026-10-25"

/**
 * Marca que va en la RAÍZ de todo pedido del plan y de sus plantillas: esta app
 * conoce los alimentos, así que lo que manda es la verdad completa (`items: []`
 * vacía la comida). Sin ella el servidor protege a la app vieja (conservarItemsPrevios).
 */
const val VERSION_ITEMS = 1

enum class OrigenAlimento(val clave: String) {
    CATALOGO("catalogo"), CLINICA("clinica");

    companion object {
        fun de(s: String?): OrigenAlimento? = entries.firstOrNull { it.clave == s }
    }
}

/** "Taza de loza al ras" · peso NETO de UNA medida (g) · de dónde sale. */
data class MedidaCasera(val nombre: String, val gramos: Double, val fuente: String? = null)

/** Nutrientes por 100 g de parte comestible. null = la fuente no trae el dato (nunca se estima). */
data class Nutrientes100(
    val kcal: Double,
    val proteinas: Double,
    val grasas: Double,
    /** Totales (por diferencia). */
    val carbohidratos: Double?,
    /** DISPONIBLES (los que dan energía). Opcional: los ítems guardados antes no lo traen → [carbohidratosDisponibles]. */
    val carbohidratosDisponibles: Double? = null,
    val fibra: Double? = null,
    /** mg */
    val sodio: Double? = null,
    /** mg */
    val hierro: Double? = null,
    /** mg */
    val calcio: Double? = null,
)

/** Un alimento del catálogo o de la clínica, en una sola forma (lo que usa el buscador). */
data class Alimento(
    val origen: OrigenAlimento,
    val id: String,
    val nombre: String,
    val grupo: String?,
    val por100: Nutrientes100,
    val medidas: List<MedidaCasera>,
    val frecuente: Boolean,
    val principal: Boolean,
    /** Nombre + sinónimos normalizados. */
    val busqueda: String,
    val fuente: String?,
    val nota: String?,
)

/** La medida casera elegida para un ítem: el peso de UNA y cuántas. */
data class MedidaItem(val nombre: String, val gramos: Double, val cantidad: Double)

/**
 * Un alimento con su cantidad dentro de una comida del plan. `por100` es una
 * FOTO (el plan no cambia si el catálogo se corrige); null solo en lo que recibe
 * el PACIENTE (el portal no manda nutrientes). `gramos` es la base de los
 * cálculos; con medida casera = cantidad × medida.gramos.
 */
data class ItemComida(
    val origen: OrigenAlimento?,
    val alimentoId: String,
    val nombre: String,
    val gramos: Double,
    val medida: MedidaItem?,
    val por100: Nutrientes100?,
)

/** Un alimento que la clínica suele poner, con su cantidad habitual (de /sugerencias). */
data class AlimentoFrecuente(
    val origen: OrigenAlimento,
    val alimentoId: String,
    val nombre: String,
    val usos: Int,
    val gramos: Double,
    val medida: MedidaItem?,
)

/** Una fila de `alimentos_clinica` (los propios). */
data class FilaAlimentoClinica(
    val id: String,
    val nombre: String,
    val grupo: String?,
    val kcal: Double?,
    val proteinas: Double?,
    val grasas: Double?,
    val carbohidratos: Double?,
    val fibra: Double?,
    val sodio: Double?,
    val hierro: Double?,
    val calcio: Double?,
    val medidas: List<MedidaCasera>,
    val fuente: String?,
)

/** Rango cerrado (gemelo de LIMITES_ALIMENTO). */
data class RangoAl(val min: Double, val max: Double) {
    fun contiene(v: Double?): Boolean = v != null && v >= min && v <= max
}

/** Gemelos de los CHECK de alimentos_clinica y de lo que acepta un ítem. */
object LimitesAlimento {
    const val NOMBRE = 120
    const val FUENTE = 200
    const val GRUPO = 60
    const val MEDIDAS = 10
    const val NOMBRE_MEDIDA = 60
    const val ITEMS_POR_COMIDA = 30
    val GRAMOS = RangoAl(0.1, 5000.0)
    val CANTIDAD = RangoAl(0.05, 100.0)
    val GRAMOS_MEDIDA = RangoAl(0.1, 5000.0)
    val KCAL = RangoAl(0.0, 950.0)
    val MACRO = RangoAl(0.0, 100.0)
    val SODIO = RangoAl(0.0, 50000.0)
    val HIERRO = RangoAl(0.0, 500.0)
    val CALCIO = RangoAl(0.0, 5000.0)
}

/** Los grupos que se ofrecen al crear un alimento propio (gemelo de GRUPOS del modal web). */
val GRUPOS_ALIMENTO: List<String> = listOf(
    "Cereales y derivados", "Verduras y hortalizas", "Frutas", "Grasas, aceites y semillas", "Pescados y mariscos",
    "Carnes y derivados", "Leche y derivados", "Bebidas", "Huevos", "Azúcares y dulces", "Menestras y leguminosas",
    "Tubérculos y raíces", "Preparaciones", "Suplementos", "Misceláneos",
)

// ─── Números y textos (como `n()` / `texto()` de la web) ─────────────────────

private val RE_NUMERO = Regex("""^[+-]?(\d+\.?\d*|\.\d+)([eE][+-]?\d+)?$""")

/** Un texto como lo convierte `Number()` (con la coma decimal): "" → null; basura → null. */
internal fun numTextoAl(s: String?): Double? {
    if (s == null || s.isEmpty()) return null
    val t = s.trim().replaceFirst(',', '.')
    if (t.isEmpty()) return 0.0 // Number("  ") = 0, como en la web
    if (!RE_NUMERO.matches(t)) return null
    return t.toDoubleOrNull()?.takeIf { it.isFinite() }
}

/** `n()` de la web: número o string NUMERIC → número; null / vacío / basura → null. */
internal fun numAl(v: JsonElement?): Double? {
    val p = v as? JsonPrimitive ?: return null
    if (p is JsonNull) return null
    if (p.isString) return numTextoAl(p.content)
    if (p.content == "true" || p.content == "false") return null
    return p.content.toDoubleOrNull()?.takeIf { it.isFinite() }
}

/** `texto()` de la web: solo strings, espacios colapsados. */
internal fun textoAl(v: JsonElement?): String {
    val p = v as? JsonPrimitive ?: return ""
    if (p is JsonNull || !p.isString) return ""
    return p.content.replace(Regex("""\s+"""), " ").trim()
}

private fun JsonObject.boolAl(k: String): Boolean = (this[k] as? JsonPrimitive)?.content == "true"

/** Un número para el JSON: entero sin ".0" (como lo manda la web). */
internal fun numeroJsonAl(v: Double): JsonPrimitive =
    if (v == floor(v) && abs(v) < 1e15) JsonPrimitive(v.toLong()) else JsonPrimitive(v)

private fun kotlinx.serialization.json.JsonObjectBuilder.ponerNum(k: String, v: Double?) {
    put(k, v?.let(::numeroJsonAl) ?: JsonNull)
}

private const val CON_TILDE = "ÀÁÂÃÄÅàáâãäåÇçÈÉÊËèéêëÌÍÎÏìíîïÑñÒÓÔÕÖòóôõöÙÚÛÜùúûüÝýÿ"
private const val SIN_TILDE = "AAAAAAaaaaaaCcEEEEeeeeIIIIiiiiNnOOOOOoooooUUUUuuuuYyy"

/**
 * Para buscar: minúsculas, sin tildes (ñ → n), solo letras y números, un
 * espacio. Gemelo de `normalizarBusqueda` (NFD sin diacríticos): la columna
 * `busqueda` del catálogo ya viene así.
 */
fun normalizarBusqueda(s: String?): String {
    val out = StringBuilder()
    var separar = false
    for (ch0 in s.orEmpty()) {
        if (ch0 in '̀'..'ͯ') continue // marcas combinantes (texto ya descompuesto)
        val i = CON_TILDE.indexOf(ch0)
        val ch = (if (i >= 0) SIN_TILDE[i] else ch0).let { if (it in 'A'..'Z') it + 32 else it }
        if (ch in 'a'..'z' || ch in '0'..'9') {
            if (separar && out.isNotEmpty()) out.append(' ')
            separar = false
            out.append(ch)
        } else separar = true
    }
    return out.toString()
}

// ─── Filas → Alimento ────────────────────────────────────────────────────────

/** Medidas caseras limpias (las inválidas se descartan; máx. 20). */
fun normalizarMedidas(v: JsonElement?): List<MedidaCasera> {
    val lista = v as? JsonArray ?: return emptyList()
    val out = mutableListOf<MedidaCasera>()
    for (x in lista) {
        val o = x as? JsonObject ?: continue
        val nombre = textoAl(o["nombre"]).take(LimitesAlimento.NOMBRE_MEDIDA)
        val gramos = numAl(o["gramos"])
        if (nombre.isEmpty() || !LimitesAlimento.GRAMOS_MEDIDA.contiene(gramos)) continue
        out += MedidaCasera(nombre, gramos!!, textoAl(o["fuente"]).ifEmpty { null })
    }
    return out.take(20)
}

/**
 * Carbohidratos DISPONIBLES por 100 g: los de la foto si vienen; si no (ítems
 * guardados antes, alimentos propios), totales − fibra (o los totales si no hay
 * fibra). null si no hay carbohidratos.
 */
fun carbohidratosDisponibles(p: Nutrientes100): Double? {
    p.carbohidratosDisponibles?.let { return it }
    val c = p.carbohidratos ?: return null
    return max(0.0, redondearJs((c - (p.fibra ?: 0.0)) * 100) / 100)
}

private fun nutrientesDeFila(o: JsonObject): Nutrientes100 {
    val carb = numAl(o["carbohidratos_g"])
    val fibra = numAl(o["fibra_g"])
    return Nutrientes100(
        kcal = numAl(o["energia_kcal"]) ?: 0.0,
        proteinas = numAl(o["proteinas_g"]) ?: 0.0,
        grasas = numAl(o["grasas_g"]) ?: 0.0,
        carbohidratos = carb,
        carbohidratosDisponibles = numAl(o["carbohidratos_disp_g"])
            ?: carbohidratosDisponibles(Nutrientes100(0.0, 0.0, 0.0, carb, null, fibra)),
        fibra = fibra,
        sodio = numAl(o["sodio_mg"]),
        hierro = numAl(o["hierro_mg"]),
        calcio = numAl(o["calcio_mg"]),
    )
}

/** Fila de GET /api/staff/alimentos → Alimento (sin id o nombre: null). */
fun alimentoDeCatalogo(o: JsonObject): Alimento? {
    val id = textoAl(o["id"]).ifEmpty { return null }
    val nombre = (o["nombre"] as? JsonPrimitive)?.takeIf { it.isString }?.content?.ifBlank { null } ?: return null
    val sinonimos = (o["sinonimos"] as? JsonArray).orEmpty().mapNotNull { (it as? JsonPrimitive)?.takeIf { p -> p.isString }?.content }
    val busqueda = (o["busqueda"] as? JsonPrimitive)?.takeIf { it.isString }?.content?.ifEmpty { null }
        ?: normalizarBusqueda((listOf(nombre) + sinonimos).joinToString(" "))
    val fuente = listOf(textoAl(o["fuente"]), textoAl(o["codigo_fuente"])).filter { it.isNotEmpty() }.joinToString(" · ").ifEmpty { null }
    return Alimento(
        origen = OrigenAlimento.CATALOGO,
        id = id,
        nombre = nombre,
        grupo = textoAl(o["grupo"]).ifEmpty { null },
        por100 = nutrientesDeFila(o),
        medidas = normalizarMedidas(o["medidas"]),
        frecuente = o.boolAl("frecuente"),
        principal = o.boolAl("principal"),
        busqueda = busqueda,
        fuente = fuente,
        nota = textoAl(o["nota"]).ifEmpty { null },
    )
}

/** Fila de GET /api/staff/alimentos/clinica (o `alimento` de POST/PATCH) → modelo. */
fun filaClinicaDeJson(o: JsonObject): FilaAlimentoClinica? {
    val id = textoAl(o["id"]).ifEmpty { return null }
    val nombre = textoAl(o["nombre"]).ifEmpty { return null }
    return FilaAlimentoClinica(
        id = id, nombre = nombre, grupo = textoAl(o["grupo"]).ifEmpty { null },
        kcal = numAl(o["energia_kcal"]), proteinas = numAl(o["proteinas_g"]), grasas = numAl(o["grasas_g"]),
        carbohidratos = numAl(o["carbohidratos_g"]), fibra = numAl(o["fibra_g"]), sodio = numAl(o["sodio_mg"]),
        hierro = numAl(o["hierro_mg"]), calcio = numAl(o["calcio_mg"]),
        medidas = normalizarMedidas(o["medidas"]), fuente = textoAl(o["fuente"]).ifEmpty { null },
    )
}

/** Propio → Alimento (origen clínica, sin frecuente ni principal). */
fun alimentoDeClinica(f: FilaAlimentoClinica): Alimento = Alimento(
    origen = OrigenAlimento.CLINICA,
    id = f.id,
    nombre = f.nombre,
    grupo = f.grupo,
    por100 = Nutrientes100(
        kcal = f.kcal ?: 0.0, proteinas = f.proteinas ?: 0.0, grasas = f.grasas ?: 0.0,
        carbohidratos = f.carbohidratos,
        carbohidratosDisponibles = carbohidratosDisponibles(Nutrientes100(0.0, 0.0, 0.0, f.carbohidratos, null, f.fibra)),
        fibra = f.fibra, sodio = f.sodio, hierro = f.hierro, calcio = f.calcio,
    ),
    medidas = f.medidas,
    frecuente = false,
    principal = false,
    busqueda = normalizarBusqueda(f.nombre),
    fuente = f.fuente,
    nota = null,
)

/** "catalogo:TPCA-A54" — la clave de un alimento (frecuentes, usos, medidas de un ítem ya puesto). */
fun refAlimento(origen: OrigenAlimento?, id: String): String = "${origen?.clave ?: "?"}:$id"
fun refAlimento(a: Alimento): String = refAlimento(a.origen, a.id)
fun refAlimento(it: ItemComida): String = refAlimento(it.origen, it.alimentoId)
fun refAlimento(f: AlimentoFrecuente): String = refAlimento(f.origen, f.alimentoId)

// ─── Búsqueda (en memoria: el catálogo se baja una vez) ─────────────────────

/** Gemelo de PUNTOS_BUSQUEDA (mismos puntos: el mismo orden que la web). */
object PuntosBusqueda {
    const val EMPIEZA_IGUAL = 100.0
    const val PRIMERA_PALABRA = 40.0
    const val PALABRA_AL_COMIENZO = 10.0
    const val PALABRA_DENTRO = 2.0
    const val PALABRA_COMPLETA = 30.0
    const val PRINCIPAL = 40.0
    const val POR_USO = 5.0
    const val TOPE_USOS = 20
    const val PROPIO = 15.0
    const val FRECUENTE = 25.0
    const val POR_CARACTER = 0.3
}

/** Un alimento pre-normalizado (se arma UNA vez: buscar en cada tecla no vuelve a normalizar 1000 nombres). */
class EntradaAlimento(val a: Alimento, val nombre: String, val palabras: List<String>)

fun indexarAlimentos(lista: List<Alimento>): List<EntradaAlimento> = lista.map { a ->
    val nombre = normalizarBusqueda(a.nombre)
    EntradaAlimento(a, nombre, "$nombre ${a.busqueda}".split(' ').filter { it.isNotEmpty() })
}

/**
 * Clave de orden de `localeCompare(…, 'es')` para los empates: sin mayúsculas
 * ni tildes, con la ñ después de la n.
 */
private fun claveOrden(s: String): String = buildString {
    for (ch in s.lowercase()) {
        if (ch == 'ñ') { append("n~"); continue }
        val i = CON_TILDE.indexOf(ch)
        append(if (i >= 0) SIN_TILDE[i].lowercaseChar() else ch)
    }
}

private val ORDEN_NOMBRE: Comparator<Alimento> = compareBy<Alimento> { claveOrden(it.nombre) }.thenBy { it.nombre }

/**
 * Alimentos que calzan con lo escrito, mejores primero (gemelo exacto de
 * `buscarAlimentos`). Cada palabra escrita tiene que ser el comienzo de una
 * palabra del alimento (o, con 3+ letras, estar dentro). Orden: empieza igual
 * que lo escrito · palabra COMPLETA ("papa" → Papa blanca antes que Papaya) ·
 * el genérico (`principal`) · lo que más usa la clínica · los propios · los
 * frecuentes · el nombre más corto.
 */
fun buscarAlimentos(
    indice: List<EntradaAlimento>,
    q: String,
    limite: Int = 12,
    usos: Map<String, Int> = emptyMap(),
): List<Alimento> {
    val qn = normalizarBusqueda(q)
    if (qn.isEmpty()) return emptyList()
    val tokens = qn.split(' ')
    val P = PuntosBusqueda
    val puntuados = ArrayList<Pair<Alimento, Double>>()
    for (e in indice) {
        var p = 0.0
        var ok = true
        for (t in tokens) {
            if (t in e.palabras) p += P.PALABRA_AL_COMIENZO + P.PALABRA_COMPLETA
            else if (e.palabras.any { it.startsWith(t) }) p += P.PALABRA_AL_COMIENZO
            else if (t.length >= 3 && e.palabras.any { it.contains(t) }) p += P.PALABRA_DENTRO
            else { ok = false; break }
        }
        if (!ok) continue
        if (e.nombre.startsWith(qn)) p += P.EMPIEZA_IGUAL
        else if (e.nombre.split(' ')[0].startsWith(tokens[0])) p += P.PRIMERA_PALABRA
        val u = usos[refAlimento(e.a)] ?: 0
        p += min(u, P.TOPE_USOS) * P.POR_USO
        if (e.a.principal) p += P.PRINCIPAL
        if (e.a.origen == OrigenAlimento.CLINICA) p += P.PROPIO
        if (e.a.frecuente) p += P.FRECUENTE
        p -= e.nombre.length * P.POR_CARACTER
        puntuados += e.a to p
    }
    val orden = Comparator<Pair<Alimento, Double>> { x, y ->
        val d = y.second - x.second
        if (d != 0.0) (if (d > 0) 1 else -1) else ORDEN_NOMBRE.compare(x.first, y.first)
    }
    return puntuados.sortedWith(orden).take(limite).map { it.first }
}

// ─── Cantidades ──────────────────────────────────────────────────────────────

private val FRACCIONES: List<Pair<String, Double>> = listOf(
    "¼" to 0.25, "½" to 0.5, "¾" to 0.75, "⅓" to 1.0 / 3, "⅔" to 2.0 / 3,
)
private val RE_FRACCION_UNICODE = Regex("""^(\d+(?:\.\d+)?)?\s*([¼½¾⅓⅔])$""")
private val RE_FRACCION = Regex("""^(?:(\d+)\s+)?(\d+)\s*/\s*(\d+)$""")
private val RE_DECIMAL = Regex("""^\d+(\.\d+)?$""")

/**
 * Lo que se escribe en la cantidad → número. Acepta "1", "1.5", "1,5", "½",
 * "1½", "1 1/2", "1/2". null = vacío; NaN = no se entiende.
 */
fun parsearCantidad(v: String?): Double? {
    if (v == null) return null
    val t = v.trim().replaceFirst(',', '.')
    if (t.isEmpty()) return null
    RE_FRACCION_UNICODE.find(t)?.let { m ->
        val ent = m.groupValues[1].takeIf { it.isNotEmpty() }?.toDouble() ?: 0.0
        return ent + FRACCIONES.first { it.first == m.groupValues[2] }.second
    }
    RE_FRACCION.find(t)?.let { m ->
        val den = m.groupValues[3].toDouble()
        if (den == 0.0) return Double.NaN
        val ent = m.groupValues[1].takeIf { it.isNotEmpty() }?.toDouble() ?: 0.0
        return ent + m.groupValues[2].toDouble() / den
    }
    return if (RE_DECIMAL.matches(t)) t.toDouble() else Double.NaN
}

/** 0.5 → "½", 1.5 → "1½", 2 → "2", 0.4 → "0.4" (máx. 2 decimales). */
fun fraccion(x: Double): String {
    val entero = floor(x + 1e-9)
    val resto = x - entero
    for ((s, v) in FRACCIONES) {
        if (abs(resto - v) < 0.01) return "${if (entero == 0.0) "" else numJs(entero)}$s"
    }
    return numJs(redondearJs(x * 100) / 100)
}

/** Gramos para mostrar: con un decimal si es menos de 10 g ("2.4 g"), si no enteros ("177 g"). */
fun textoGramos(g: Double): String = "${numJs(if (g < 10) redondear1(g) else redondearJs(g))} g"

/** "½ taza de loza al ras (177 g)", "2 × unidad mediana (120 g)" o "80 g". */
fun textoCantidad(gramos: Double, medida: MedidaItem?): String {
    if (medida == null) return textoGramos(gramos)
    val nombre = medida.nombre.take(1).lowercase() + medida.nombre.drop(1)
    val c = medida.cantidad
    return "${fraccion(c)}${if (c > 1 + 1e-9) " ×" else ""} $nombre (${textoGramos(gramos)})"
}

fun textoCantidad(it: ItemComida): String = textoCantidad(it.gramos, it.medida)

/** "Quinua cocida — ½ taza de loza al ras (177 g)" (WhatsApp, hoja, historia clínica). */
fun lineaItem(it: ItemComida): String = "${it.nombre} — ${textoCantidad(it)}"

// ─── Ítems de una comida ─────────────────────────────────────────────────────

/** Nutrientes por 100 g válidos o null (kcal, proteínas y grasas obligatorios; fuera de rango → null). */
fun normalizarNutrientes(v: JsonElement?): Nutrientes100? {
    val o = v as? JsonObject ?: return null
    val kcal = numAl(o["kcal"])
    val prot = numAl(o["proteinas"])
    val gras = numAl(o["grasas"])
    if (!LimitesAlimento.KCAL.contiene(kcal) || !LimitesAlimento.MACRO.contiene(prot) || !LimitesAlimento.MACRO.contiene(gras)) return null
    var fuera = false
    fun opc(k: String, r: RangoAl): Double? {
        val y = numAl(o[k]) ?: return null
        if (!r.contiene(y)) fuera = true
        return y
    }
    val carb = opc("carbohidratos", LimitesAlimento.MACRO)
    val fibra = opc("fibra", LimitesAlimento.MACRO)
    val sodio = opc("sodio", LimitesAlimento.SODIO)
    val hierro = opc("hierro", LimitesAlimento.HIERRO)
    val calcio = opc("calcio", LimitesAlimento.CALCIO)
    val disp = opc("carbohidratosDisponibles", LimitesAlimento.MACRO)
    if (fuera) return null
    return Nutrientes100(kcal!!, prot!!, gras!!, carb, disp, fibra, sodio, hierro, calcio)
}

private fun medidaDeJson(v: JsonElement?): Pair<Boolean, MedidaItem?> {
    val m = v as? JsonObject ?: return true to null // sin medida (null u otra cosa): en gramos
    val nm = textoAl(m["nombre"]).take(LimitesAlimento.NOMBRE_MEDIDA)
    val gm = numAl(m["gramos"])
    val cant = numAl(m["cantidad"])
    if (nm.isEmpty() || !LimitesAlimento.GRAMOS_MEDIDA.contiene(gm) || !LimitesAlimento.CANTIDAD.contiene(cant)) return false to null
    return true to MedidaItem(nm, gm!!, cant!!)
}

/** Un ítem tal como llega (JSON) → ítem limpio, o null si no es válido (gemelo de `normalizarItem`). */
fun normalizarItem(v: JsonElement?): ItemComida? {
    val o = v as? JsonObject ?: return null
    val origen = OrigenAlimento.de((o["origen"] as? JsonPrimitive)?.takeIf { it.isString }?.content) ?: return null
    val alimentoId = textoAl(o["alimentoId"]).take(64)
    val nombre = textoAl(o["nombre"]).take(LimitesAlimento.NOMBRE)
    val por100 = normalizarNutrientes(o["por100"])
    if (alimentoId.isEmpty() || nombre.isEmpty() || por100 == null) return null
    val (medidaOk, medida) = medidaDeJson(o["medida"])
    if (!medidaOk) return null
    // Con medida casera, los gramos salen de la medida (la verdad para los cálculos).
    val gramos = if (medida != null) redondear1(medida.cantidad * medida.gramos) else numAl(o["gramos"])?.let(::redondear1)
    if (!LimitesAlimento.GRAMOS.contiene(gramos)) return null
    return ItemComida(origen, alimentoId, nombre, gramos!!, medida, por100)
}

/** Los ítems válidos de una comida (máx. 30). Lo que no es lista → []. */
fun normalizarItems(v: JsonElement?): List<ItemComida> =
    (v as? JsonArray).orEmpty().mapNotNull(::normalizarItem).take(LimitesAlimento.ITEMS_POR_COMIDA)

/** Ítems ya en el modelo → los mismos, validados como en el servidor (los inválidos se descartan). */
fun normalizarItems(items: List<ItemComida>): List<ItemComida> =
    items.mapNotNull { normalizarItem(itemAJson(it)) }.take(LimitesAlimento.ITEMS_POR_COMIDA)

/**
 * Ítems para MOSTRAR (portal, hoja): basta nombre y gramos; la foto de
 * nutrientes puede no venir (el portal no la manda) y, si viene válida, se conserva.
 */
fun normalizarItemsParaMostrar(v: JsonElement?): List<ItemComida> {
    val out = mutableListOf<ItemComida>()
    for (x in (v as? JsonArray).orEmpty()) {
        val o = x as? JsonObject ?: continue
        val nombre = textoAl(o["nombre"]).take(LimitesAlimento.NOMBRE)
        val gramos = numAl(o["gramos"])
        if (nombre.isEmpty() || !LimitesAlimento.GRAMOS.contiene(gramos)) continue
        val medida = medidaDeJson(o["medida"]).second
        out += ItemComida(
            origen = OrigenAlimento.de((o["origen"] as? JsonPrimitive)?.takeIf { it.isString }?.content),
            alimentoId = textoAl(o["alimentoId"]).take(64),
            nombre = nombre, gramos = gramos!!, medida = medida, por100 = normalizarNutrientes(o["por100"]),
        )
    }
    return out.take(LimitesAlimento.ITEMS_POR_COMIDA)
}

/** Errores de los ítems de una comida (vacío = bien). Mismos textos que `erroresItems`. */
fun erroresItems(items: List<ItemComida>, comida: String): List<String> {
    val e = mutableListOf<String>()
    if (items.size > LimitesAlimento.ITEMS_POR_COMIDA) e += "Máximo ${LimitesAlimento.ITEMS_POR_COMIDA} alimentos en «$comida»."
    val malo = items.firstOrNull { normalizarItem(itemAJson(it)) == null }
    if (malo != null) {
        val nom = malo.nombre.trim()
        e += "Revisa la cantidad de ${if (nom.isNotEmpty()) "«$nom»" else "un alimento"} en «$comida» " +
            "(entre ${numJs(LimitesAlimento.GRAMOS.min)} y ${numJs(LimitesAlimento.GRAMOS.max)} g)."
    }
    return e
}

/** El ítem para el pedido (POST/PATCH del plan o la plantilla). */
fun itemAJson(it: ItemComida): JsonObject = buildJsonObject {
    put("origen", it.origen?.clave ?: "")
    put("alimentoId", it.alimentoId)
    put("nombre", it.nombre)
    put("gramos", numeroJsonAl(it.gramos))
    put("medida", it.medida?.let { m ->
        buildJsonObject {
            put("nombre", m.nombre); put("gramos", numeroJsonAl(m.gramos)); put("cantidad", numeroJsonAl(m.cantidad))
        }
    } ?: JsonNull)
    put("por100", it.por100?.let { p ->
        buildJsonObject {
            ponerNum("kcal", p.kcal); ponerNum("proteinas", p.proteinas); ponerNum("grasas", p.grasas)
            ponerNum("carbohidratos", p.carbohidratos)
            p.carbohidratosDisponibles?.let { d -> put("carbohidratosDisponibles", numeroJsonAl(d)) }
            ponerNum("fibra", p.fibra); ponerNum("sodio", p.sodio); ponerNum("hierro", p.hierro); ponerNum("calcio", p.calcio)
        }
    } ?: JsonNull)
}

fun itemsAJson(items: List<ItemComida>): JsonArray = buildJsonArray { items.forEach { add(itemAJson(it)) } }

/**
 * Un alimento elegido → ítem. Por defecto: la medida indicada ([medida], aunque
 * ya no exista → en gramos), o si se pasan [gramos] sin medida, en gramos; si
 * no, la primera medida casera; si no hay, 100 g.
 */
fun itemDesdeAlimento(
    a: Alimento,
    medida: String? = null,
    cantidad: Double? = null,
    gramos: Double? = null,
    medidaIndicada: Boolean = medida != null,
): ItemComida {
    val m = when {
        medidaIndicada -> a.medidas.firstOrNull { it.nombre == medida }
        gramos != null -> null
        else -> a.medidas.firstOrNull()
    }
    val cant = if (cantidad != null && cantidad > 0) cantidad else 1.0
    return ItemComida(
        origen = a.origen,
        alimentoId = a.id,
        nombre = a.nombre,
        medida = m?.let { MedidaItem(it.nombre, it.gramos, cant) },
        gramos = if (m != null) redondear1(cant * m.gramos) else redondear1(if (gramos != null && gramos > 0) gramos else 100.0),
        por100 = a.por100.copy(),
    )
}

/** Al elegirlo del buscador: con la cantidad habitual de la clínica si la hay. */
fun itemConHabitual(a: Alimento, h: AlimentoFrecuente?): ItemComida = when {
    h == null -> itemDesdeAlimento(a)
    h.medida != null -> itemDesdeAlimento(a, medida = h.medida.nombre, cantidad = h.medida.cantidad, gramos = h.gramos)
    else -> itemDesdeAlimento(a, gramos = h.gramos)
}

/** Cambia la cantidad (y la unidad) de un ítem. [medida] null = gramos. */
fun conCantidad(it: ItemComida, cantidad: Double, medida: MedidaCasera?): ItemComida =
    if (medida != null) it.copy(medida = MedidaItem(medida.nombre, medida.gramos, cantidad), gramos = redondear1(cantidad * medida.gramos))
    else it.copy(medida = null, gramos = redondear1(cantidad))

/**
 * Cambiar de unidad: a gramos conserva el peso; a una medida, la cantidad más
 * parecida al peso que tenía, de a ¼ (mínimo ¼). Gemelo de `cambiarUnidad`.
 */
fun cambiarUnidad(it: ItemComida, medida: MedidaCasera?): ItemComida {
    if (medida == null) return conCantidad(it, it.gramos, null)
    val cant = max(0.25, redondearJs(it.gramos / medida.gramos * 4) / 4)
    return conCantidad(it, cant, medida)
}

/** Las medidas que se pueden elegir: las del alimento o, si ya no existe, la del ítem. */
fun medidasDe(it: ItemComida, a: Alimento?): List<MedidaCasera> {
    val lista = a?.medidas.orEmpty().toMutableList()
    val m = it.medida
    if (m != null && lista.none { x -> x.nombre == m.nombre }) lista.add(0, MedidaCasera(m.nombre, m.gramos))
    return lista
}

/** La cantidad como se escribe en el campo: "½" con medida, "120" en gramos. */
fun textoDeCantidad(it: ItemComida): String = it.medida?.let { fraccion(it.cantidad) } ?: numJs(it.gramos)

/** ¿Lo escrito es una cantidad válida para la unidad del ítem? null = no. */
fun cantidadValida(txt: String, it: ItemComida): Double? {
    val x = parsearCantidad(txt) ?: return null
    if (x.isNaN()) return null
    val r = if (it.medida != null) LimitesAlimento.CANTIDAD else LimitesAlimento.GRAMOS
    return x.takeIf { r.contiene(x) }
}

// ─── Frecuentes de la clínica (/sugerencias → `alimentos`) ───────────────────

/** Tolerante: los mal formados se descartan; un servidor viejo no lo manda (= []). */
fun alimentosFrecuentesDeJson(v: JsonElement?): List<AlimentoFrecuente> =
    (v as? JsonArray).orEmpty().mapNotNull { x ->
        val o = x as? JsonObject ?: return@mapNotNull null
        val origen = OrigenAlimento.de((o["origen"] as? JsonPrimitive)?.takeIf { it.isString }?.content) ?: return@mapNotNull null
        val id = textoAl(o["alimentoId"]).ifEmpty { return@mapNotNull null }
        val (medidaOk, medida) = medidaDeJson(o["medida"])
        val gramos = (if (medida != null) redondear1(medida.cantidad * medida.gramos) else numAl(o["gramos"]))
            ?.takeIf { LimitesAlimento.GRAMOS.contiene(it) } ?: return@mapNotNull null
        AlimentoFrecuente(
            origen = origen, alimentoId = id, nombre = textoAl(o["nombre"]),
            usos = numAl(o["usos"])?.toInt() ?: 1, gramos = gramos, medida = if (medidaOk) medida else null,
        )
    }

// ─── Alimentos propios de la clínica ─────────────────────────────────────────

/** Lo que se escribe en el formulario (todo como texto, tal cual). */
data class BorradorAlimentoClinica(
    val nombre: String,
    val grupo: String? = null,
    val kcal: String? = null,
    val proteinas: String? = null,
    val grasas: String? = null,
    val carbohidratos: String? = null,
    val fibra: String? = null,
    val sodio: String? = null,
    val hierro: String? = null,
    val calcio: String? = null,
    /** (nombre, gramos) tal cual; las filas vacías ya filtradas. */
    val medidas: List<Pair<String, String>> = emptyList(),
    val fuente: String? = null,
)

private fun colapsar(s: String?): String = s.orEmpty().replace(Regex("""\s+"""), " ").trim()

private fun medidasDeBorrador(b: BorradorAlimentoClinica): List<MedidaCasera> = b.medidas.mapNotNull { (n, g) ->
    val nombre = colapsar(n).take(LimitesAlimento.NOMBRE_MEDIDA)
    val gramos = numTextoAl(g)
    if (nombre.isEmpty() || !LimitesAlimento.GRAMOS_MEDIDA.contiene(gramos)) null else MedidaCasera(nombre, gramos!!)
}

/** Errores para mostrar (vacío = válido). Mismos textos que `validarAlimentoClinica`. */
fun validarAlimentoClinica(b: BorradorAlimentoClinica): List<String> {
    val e = mutableListOf<String>()
    val nombre = colapsar(b.nombre)
    if (nombre.isEmpty()) e += "Ponle un nombre al alimento."
    else if (nombre.length > LimitesAlimento.NOMBRE) e += "El nombre es muy largo (máx. ${LimitesAlimento.NOMBRE} caracteres)."
    val kcal = numTextoAl(b.kcal)
    val K = LimitesAlimento.KCAL
    if (kcal == null) e += "Escribe las kcal por 100 g."
    else if (!K.contiene(kcal)) e += "Kcal por 100 g: entre ${numJs(K.min)} y ${numJs(K.max)}."
    var suma = 0.0
    for ((nom, v) in listOf("Proteínas" to b.proteinas, "Grasas" to b.grasas, "Carbohidratos" to b.carbohidratos)) {
        val x = numTextoAl(v)
        if (x == null) e += "Escribe ${nom.lowercase()} por 100 g (0 si no tiene)."
        else if (!LimitesAlimento.MACRO.contiene(x)) e += "$nom: entre 0 y 100 g por 100 g."
        else suma += x
    }
    if (suma > 100.5) e += "Proteínas + grasas + carbohidratos suman ${numJs(redondear1(suma))} g: no pueden pasar de 100 g en 100 g."
    for ((nom, v, r) in listOf(
        Triple("Fibra", b.fibra, LimitesAlimento.MACRO), Triple("Sodio", b.sodio, LimitesAlimento.SODIO),
        Triple("Hierro", b.hierro, LimitesAlimento.HIERRO), Triple("Calcio", b.calcio, LimitesAlimento.CALCIO),
    )) {
        if (v.isNullOrEmpty()) continue
        val x = numTextoAl(v)
        if (x == null || !r.contiene(x)) e += "$nom: entre ${numJs(r.min)} y ${numJs(r.max)}."
    }
    if (colapsar(b.fuente).length > LimitesAlimento.FUENTE) e += "La fuente es muy larga (máx. ${LimitesAlimento.FUENTE} caracteres)."
    if (b.medidas.size > LimitesAlimento.MEDIDAS) e += "Máximo ${LimitesAlimento.MEDIDAS} medidas caseras."
    else if (medidasDeBorrador(b).size != b.medidas.size) e += "Cada medida casera necesita nombre y gramos (entre 0.1 y 5000)."
    return e
}

/** Cuerpo de POST (sin [id]) o PATCH (con [id]) — YA validado. camelCase, como la web. */
fun cuerpoAlimentoClinica(b: BorradorAlimentoClinica, id: String? = null): JsonObject = buildJsonObject {
    id?.let { put("id", it) }
    put("nombre", colapsar(b.nombre).take(LimitesAlimento.NOMBRE))
    put("grupo", colapsar(b.grupo).take(LimitesAlimento.GRUPO).ifEmpty { null }?.let(::JsonPrimitive) ?: JsonNull)
    ponerNum("kcal", numTextoAl(b.kcal))
    ponerNum("proteinas", numTextoAl(b.proteinas))
    ponerNum("grasas", numTextoAl(b.grasas))
    ponerNum("carbohidratos", numTextoAl(b.carbohidratos))
    ponerNum("fibra", b.fibra?.ifEmpty { null }?.let(::numTextoAl))
    ponerNum("sodio", b.sodio?.ifEmpty { null }?.let(::numTextoAl))
    ponerNum("hierro", b.hierro?.ifEmpty { null }?.let(::numTextoAl))
    ponerNum("calcio", b.calcio?.ifEmpty { null }?.let(::numTextoAl))
    put("medidas", buildJsonArray {
        medidasDeBorrador(b).forEach { m -> add(buildJsonObject { put("nombre", m.nombre); put("gramos", numeroJsonAl(m.gramos)) }) }
    })
    put("fuente", colapsar(b.fuente).take(LimitesAlimento.FUENTE).ifEmpty { null }?.let(::JsonPrimitive) ?: JsonNull)
}
