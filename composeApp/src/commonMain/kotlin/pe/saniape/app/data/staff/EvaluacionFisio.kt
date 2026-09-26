package pe.saniape.app.data.staff

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject

/**
 * Evaluación fisioterapéutica estructurada (M5), mapa corporal (M6) y objetivos
 * (M8). Gemelo PURO de `lib/evaluacion-fisio.ts` de la web: catálogos, limpieza,
 * comparativo inicial → última y resumen. Los datos se guardan en
 * `evaluaciones_fisio.datos` (jsonb) con la MISMA forma que la web, así una
 * evaluación hecha en la app se ve igual en la web y al revés.
 *
 * Solo FISIOTERAPIA: quien lo muestra decide con `citaEsFisio` / `pacienteEsFisio`,
 * nunca por clínica (un paciente dental no ve nada de esto).
 */

// ─── Tipos de datos guardados ────────────────────────────────────────────────

/** lado: "D" | "I" | null. */
data class RangoMedido(val articulacion: String, val movimiento: String, val lado: String?, val grados: Int?)
data class FuerzaMedida(val grupo: String, val lado: String?, val valor: Int?)
/** resultado: "+" | "-". */
data class PruebaEspecial(val nombre: String, val lado: String?, val resultado: String)
data class TestAplicado(val id: String, val respuestas: List<Int?>, val puntaje: Double?)

data class EvaluacionFisioDatos(
    val eva: Int? = null,
    /** Zona del mapa → intensidad (1 leve · 2 moderado · 3 severo). Vacío = sin mapa. */
    val zonas: Map<String, Int> = emptyMap(),
    val localizacion: String? = null,
    val rangos: List<RangoMedido> = emptyList(),
    val fuerza: List<FuerzaMedida> = emptyList(),
    val pruebas: List<PruebaEspecial> = emptyList(),
    val tests: List<TestAplicado> = emptyList(),
    val notas: String? = null,
)

/** tipo: "inicial" | "reevaluacion" | "alta". */
data class EvaluacionFisio(
    val id: String,
    val pacienteId: String,
    val tratamientoId: String?,
    val citaId: String?,
    val terapeutaId: String?,
    val tipo: String,
    val fecha: String,
    val datos: EvaluacionFisioDatos,
    val createdAt: String? = null,
    val terapeutaNombre: String? = null,
)

/** estado: "en_curso" | "parcial" | "logrado". */
data class ObjetivoTratamiento(
    val id: String,
    val pacienteId: String,
    val tratamientoId: String?,
    val evaluacionId: String?,
    val texto: String,
    val unidad: String?,
    val valorInicial: Double?,
    val meta: Double?,
    val logrado: Double?,
    val estado: String,
    val fechaEstado: String?,
    val orden: Int,
    val createdAt: String? = null,
)

/** Objetivo aún sin guardar (desde el formulario). */
data class ObjetivoBorrador(val texto: String, val unidad: String?, val valorInicial: Double?, val meta: Double?)

data class EstadoObjetivo(val id: String, val label: String)

val ESTADOS_OBJETIVO: List<EstadoObjetivo> = listOf(
    EstadoObjetivo("en_curso", "En curso"),
    EstadoObjetivo("parcial", "Parcial"),
    EstadoObjetivo("logrado", "Logrado"),
)

val TIPOS_EVALUACION: List<Pair<String, String>> = listOf(
    "inicial" to "Inicial", "reevaluacion" to "Reevaluación", "alta" to "De alta",
)
fun nombreTipoEvaluacion(tipo: String): String = TIPOS_EVALUACION.firstOrNull { it.first == tipo }?.second ?: tipo

// ─── Catálogos ───────────────────────────────────────────────────────────────

data class Movimiento(val id: String, val nombre: String, val normal: Int, val bilateral: Boolean = true)
data class Articulacion(val id: String, val nombre: String, val region: String, val movimientos: List<Movimiento>)
data class ItemRegion(val nombre: String, val region: String)

/** Regiones: cervical, lumbar, hombro, codo, muneca, cadera, rodilla, tobillo. */
val ARTICULACIONES: List<Articulacion> = listOf(
    Articulacion("hombro", "Hombro", "hombro", listOf(
        Movimiento("flex", "Flexión", 180), Movimiento("ext", "Extensión", 60),
        Movimiento("abd", "Abducción", 180), Movimiento("re", "Rot. externa", 90),
        Movimiento("ri", "Rot. interna", 70),
    )),
    Articulacion("codo", "Codo", "codo", listOf(
        Movimiento("flex", "Flexión", 150), Movimiento("ext", "Extensión", 0),
        Movimiento("pron", "Pronación", 80), Movimiento("sup", "Supinación", 80),
    )),
    Articulacion("muneca", "Muñeca", "muneca", listOf(
        Movimiento("flex", "Flexión", 80), Movimiento("ext", "Extensión", 70),
        Movimiento("rad", "Desv. radial", 20), Movimiento("cub", "Desv. cubital", 30),
    )),
    Articulacion("cadera", "Cadera", "cadera", listOf(
        Movimiento("flex", "Flexión", 120), Movimiento("ext", "Extensión", 30),
        Movimiento("abd", "Abducción", 45), Movimiento("add", "Aducción", 30),
        Movimiento("ri", "Rot. interna", 45), Movimiento("re", "Rot. externa", 45),
    )),
    Articulacion("rodilla", "Rodilla", "rodilla", listOf(
        Movimiento("flex", "Flexión", 135), Movimiento("ext", "Extensión", 0),
    )),
    Articulacion("tobillo", "Tobillo", "tobillo", listOf(
        Movimiento("dorsi", "Dorsiflexión", 20), Movimiento("planti", "Plantiflexión", 50),
        Movimiento("inv", "Inversión", 35), Movimiento("ev", "Eversión", 15),
    )),
    Articulacion("cervical", "Columna cervical", "cervical", listOf(
        Movimiento("flex", "Flexión", 45, bilateral = false), Movimiento("ext", "Extensión", 45, bilateral = false),
        Movimiento("incl", "Incl. lateral", 45), Movimiento("rot", "Rotación", 60),
    )),
    Articulacion("lumbar", "Columna lumbar", "lumbar", listOf(
        Movimiento("flex", "Flexión", 80, bilateral = false), Movimiento("ext", "Extensión", 25, bilateral = false),
        Movimiento("incl", "Incl. lateral", 35), Movimiento("rot", "Rotación", 45),
    )),
)

fun buscarMovimiento(articulacion: String, movimiento: String): Pair<Articulacion?, Movimiento?> {
    val art = ARTICULACIONES.firstOrNull { it.id == articulacion }
    return art to art?.movimientos?.firstOrNull { it.id == movimiento }
}

/** Fuerza muscular (escala MRC / Daniels 0–5). */
val ESCALA_FUERZA: List<Pair<Int, String>> = listOf(
    0 to "Sin contracción", 1 to "Contracción sin movimiento", 2 to "Movimiento sin gravedad",
    3 to "Vence la gravedad", 4 to "Vence resistencia moderada", 5 to "Fuerza normal",
)

val GRUPOS_MUSCULARES: List<ItemRegion> = listOf(
    ItemRegion("Flexores de cuello", "cervical"),
    ItemRegion("Flexores de hombro", "hombro"),
    ItemRegion("Abductores de hombro", "hombro"),
    ItemRegion("Rotadores externos de hombro", "hombro"),
    ItemRegion("Flexores de codo (bíceps)", "codo"),
    ItemRegion("Extensores de codo (tríceps)", "codo"),
    ItemRegion("Extensores de muñeca", "muneca"),
    ItemRegion("Prensión (mano)", "muneca"),
    ItemRegion("Abdominales / core", "lumbar"),
    ItemRegion("Extensores de tronco", "lumbar"),
    ItemRegion("Flexores de cadera (psoas)", "cadera"),
    ItemRegion("Abductores de cadera (glúteo medio)", "cadera"),
    ItemRegion("Extensores de cadera (glúteo mayor)", "cadera"),
    ItemRegion("Cuádriceps", "rodilla"),
    ItemRegion("Isquiotibiales", "rodilla"),
    ItemRegion("Dorsiflexores (tibial anterior)", "tobillo"),
    ItemRegion("Plantiflexores (tríceps sural)", "tobillo"),
)

val PRUEBAS_ESPECIALES: List<ItemRegion> = listOf(
    ItemRegion("Spurling", "cervical"), ItemRegion("Distracción cervical", "cervical"),
    ItemRegion("ULTT (tensión neural MS)", "cervical"),
    ItemRegion("Lasègue", "lumbar"), ItemRegion("Slump", "lumbar"), ItemRegion("Bragard", "lumbar"),
    ItemRegion("Neer", "hombro"), ItemRegion("Hawkins-Kennedy", "hombro"), ItemRegion("Jobe", "hombro"),
    ItemRegion("Speed", "hombro"), ItemRegion("Aprensión", "hombro"), ItemRegion("Drop arm", "hombro"),
    ItemRegion("Cozen", "codo"), ItemRegion("Mill", "codo"),
    ItemRegion("Phalen", "muneca"), ItemRegion("Tinel", "muneca"), ItemRegion("Finkelstein", "muneca"),
    ItemRegion("FABER (Patrick)", "cadera"), ItemRegion("FADIR", "cadera"), ItemRegion("Thomas", "cadera"),
    ItemRegion("Trendelenburg", "cadera"), ItemRegion("Ober", "cadera"),
    ItemRegion("Cajón anterior", "rodilla"), ItemRegion("Lachman", "rodilla"), ItemRegion("Cajón posterior", "rodilla"),
    ItemRegion("McMurray", "rodilla"), ItemRegion("Apley", "rodilla"), ItemRegion("Bostezo valgo/varo", "rodilla"),
    ItemRegion("Clarke", "rodilla"),
    ItemRegion("Cajón anterior de tobillo", "tobillo"), ItemRegion("Inclinación talar", "tobillo"),
    ItemRegion("Thompson", "tobillo"),
)

/**
 * Mapa corporal: zonas tocables de frente y de espalda. `_d`/`_i` = lado del
 * PACIENTE. La geometría vive en `ui/clinica/fisio/MapaCorporal.kt` (misma que el SVG web).
 */
data class ZonaCorporal(val id: String, val nombre: String, val vista: String, val region: String? = null)

private fun par(base: String, nombre: String, vista: String, region: String?): List<ZonaCorporal> = listOf(
    ZonaCorporal("${base}_d", "$nombre D", vista, region),
    ZonaCorporal("${base}_i", "$nombre I", vista, region),
)

val ZONAS_CORPORALES: List<ZonaCorporal> = buildList {
    add(ZonaCorporal("cabeza", "Cabeza", "frente"))
    add(ZonaCorporal("cuello", "Cuello", "frente", "cervical"))
    addAll(par("hombro", "Hombro", "frente", "hombro"))
    add(ZonaCorporal("pecho", "Pecho", "frente"))
    add(ZonaCorporal("abdomen", "Abdomen", "frente"))
    addAll(par("brazo", "Brazo", "frente", "hombro"))
    addAll(par("codo", "Codo", "frente", "codo"))
    addAll(par("antebrazo", "Antebrazo", "frente", "codo"))
    addAll(par("mano", "Muñeca/mano", "frente", "muneca"))
    addAll(par("cadera", "Cadera/ingle", "frente", "cadera"))
    addAll(par("muslo", "Muslo", "frente", "rodilla"))
    addAll(par("rodilla", "Rodilla", "frente", "rodilla"))
    addAll(par("pierna", "Pierna", "frente", "tobillo"))
    addAll(par("pie", "Tobillo/pie", "frente", "tobillo"))
    add(ZonaCorporal("cervical", "Cervical (nuca)", "espalda", "cervical"))
    addAll(par("escapula", "Escápula", "espalda", "hombro"))
    add(ZonaCorporal("dorsal", "Dorsal", "espalda"))
    add(ZonaCorporal("lumbar", "Lumbar", "espalda", "lumbar"))
    addAll(par("gluteo", "Glúteo", "espalda", "cadera"))
    addAll(par("isquio", "Muslo posterior", "espalda", "rodilla"))
    addAll(par("poplitea", "Hueco poplíteo", "espalda", "rodilla"))
    addAll(par("pantorrilla", "Pantorrilla", "espalda", "tobillo"))
    addAll(par("talon", "Talón", "espalda", "tobillo"))
}
val NOMBRE_ZONA: Map<String, String> = ZONAS_CORPORALES.associate { it.id to it.nombre }

/** Intensidad 1..3 → (etiqueta, color ARGB). Mismos colores que la web. */
val INTENSIDADES: List<Pair<String, Long>> = listOf(
    "Leve" to 0xFFFACC15, "Moderado" to 0xFFF97316, "Severo" to 0xFFDC2626,
)

/** "Lumbar (severo), Rodilla D (moderado)", de más a menos intenso. */
fun textoZonas(zonas: Map<String, Int>?): String {
    if (zonas.isNullOrEmpty()) return ""
    return zonas.entries
        .filter { NOMBRE_ZONA[it.key] != null && it.value in 1..3 }
        .sortedByDescending { it.value }
        .joinToString(", ") { "${NOMBRE_ZONA[it.key]} (${INTENSIDADES[it.value - 1].first.lowercase()})" }
}

/** Un toque en una zona: nada → leve → moderado → severo → nada. */
fun alternarZona(zonas: Map<String, Int>, id: String): Map<String, Int> {
    val sig = ((zonas[id] ?: 0) + 1) % 4
    return LinkedHashMap(zonas).apply { if (sig == 0) remove(id) else put(id, sig) }
}

// ─── Región probable ─────────────────────────────────────────────────────────

private val PALABRAS_REGION: List<Pair<String, Regex>> = listOf(
    "rodilla" to Regex("rodill|lca\\b|ligamento cruzado|menisc|r[oó]tul|patel|gonartr"),
    "lumbar" to Regex("lumb|ci[aá]t|hernia|espalda baja|sacro|discopat|espondil|columna"),
    "cervical" to Regex("cerv|cuello|tort[ií]col|whiplash|latigazo"),
    "hombro" to Regex("hombr|manguito|supraesp|bursitis subacr|capsulitis|escápul|escapul"),
    "codo" to Regex("codo|epicond|epitroc"),
    "muneca" to Regex("mu[ñn]eca|mano|carpian|carpo|quervain|dedo"),
    "cadera" to Regex("cadera|coxa|coxart|ingle|tr[oó]cánter|trocanter|gl[uú]te"),
    "tobillo" to Regex("tobill|esguince|aquil|fascitis|plantar|pie\\b|tal[oó]n"),
)

/** Regiones que aparecen en el diagnóstico/motivo y en lo medido (en ese orden, sin repetir). */
fun regionesProbables(texto: String?, datos: EvaluacionFisioDatos? = null): List<String> {
    val out = mutableListOf<String>()
    fun add(r: String?) { if (r != null && r !in out) out.add(r) }
    // En minúsculas antes de buscar: /i de JS también ignora mayúsculas con tilde.
    val t = texto?.lowercase()
    if (!t.isNullOrBlank()) for ((r, re) in PALABRAS_REGION) if (re.containsMatchIn(t)) add(r)
    datos?.zonas?.entries?.sortedByDescending { it.value }?.forEach { (id, _) ->
        add(ZONAS_CORPORALES.firstOrNull { it.id == id }?.region)
    }
    datos?.rangos?.forEach { r -> add(ARTICULACIONES.firstOrNull { it.id == r.articulacion }?.region) }
    return out
}

/** Test funcional que corresponde a cada región. */
val TEST_POR_REGION: Map<String, String> = mapOf(
    "lumbar" to "oswestry", "cervical" to "ndi", "hombro" to "quickdash", "codo" to "quickdash",
    "muneca" to "quickdash", "rodilla" to "lysholm",
)

/** Ordena un catálogo poniendo primero lo de las regiones probables (estable). */
fun <T> ordenarPorRegion(lista: List<T>, regiones: List<String>, region: (T) -> String): List<T> {
    if (regiones.isEmpty()) return lista
    return lista.sortedBy { regiones.indexOf(region(it)).let { i -> if (i == -1) 99 else i } }
}

// ─── Objetivos sugeridos ─────────────────────────────────────────────────────

private val OBJETIVOS_REGION: Map<String, List<String>> = mapOf(
    "rodilla" to listOf("Subir y bajar escaleras sin dolor", "Ponerse en cuclillas sin dolor", "Caminar 30 min sin dolor"),
    "lumbar" to listOf("Estar sentado 1 hora sin dolor", "Levantar peso del suelo sin dolor", "Dormir sin despertar por el dolor"),
    "cervical" to listOf("Girar el cuello para manejar sin dolor", "Trabajar 2 h en computadora sin dolor", "Dormir sin despertar por el dolor"),
    "hombro" to listOf("Elevar el brazo sobre la cabeza sin dolor", "Dormir sobre el lado afectado", "Vestirse sin ayuda"),
    "codo" to listOf("Cargar bolsas sin dolor", "Agarrar y girar objetos sin dolor"),
    "muneca" to listOf("Abrir frascos sin dolor", "Escribir o teclear 1 h sin dolor"),
    "cadera" to listOf("Caminar 30 min sin dolor", "Ponerse medias y zapatos sin ayuda", "Subir escaleras sin dolor"),
    "tobillo" to listOf("Caminar 30 min sin dolor", "Apoyo en un pie 30 segundos", "Volver a correr 5 km"),
)
private val OBJETIVOS_GENERALES = listOf("Dolor EVA ≤ 2", "Volver a su deporte", "Volver al trabajo sin limitaciones")

/**
 * Sugerencias: primero lo que sale de lo MEDIDO (rango bajo lo normal, EVA alto),
 * luego lo típico de la región. Nada se agrega solo: son chips que el fisio toca.
 */
fun sugerirObjetivos(regiones: List<String>, datos: EvaluacionFisioDatos?, yaPuestos: List<String> = emptyList()): List<ObjetivoBorrador> {
    val out = mutableListOf<ObjetivoBorrador>()
    val puesto = yaPuestos.map { it.lowercase() }.toSet()
    fun add(o: ObjetivoBorrador) {
        if (o.texto.lowercase() in puesto || out.any { it.texto == o.texto }) return
        out.add(o)
    }
    datos?.rangos?.forEach { r ->
        val g = r.grados ?: return@forEach
        val (art, mov) = buscarMovimiento(r.articulacion, r.movimiento)
        if (art == null || mov == null || mov.normal == 0) return@forEach
        if (g >= mov.normal * 0.9) return@forEach
        add(ObjetivoBorrador(
            "${mov.nombre} ${art.nombre.lowercase()}${r.lado?.let { " $it" } ?: ""}",
            "°", g.toDouble(), redondearJs(mov.normal * 0.9),
        ))
    }
    val eva = datos?.eva
    if (eva != null && eva >= 4) add(ObjetivoBorrador("Dolor (EVA)", "/10", eva.toDouble(), 2.0))
    for (r in regiones) for (t in OBJETIVOS_REGION[r].orEmpty()) add(ObjetivoBorrador(t, null, null, null))
    for (t in OBJETIVOS_GENERALES) add(ObjetivoBorrador(t, null, null, null))
    return out.take(8)
}

/** "90° → meta 122°" (vacío si no tiene medida). */
fun medidaTexto(valorInicial: Double?, meta: Double?, unidad: String?): String {
    if (valorInicial == null && meta == null) return ""
    val u = unidad ?: ""
    return "${valorInicial?.let { numJs(it) } ?: "—"}$u → meta ${meta?.let { numJs(it) } ?: "—"}$u"
}

// ─── Limpieza ────────────────────────────────────────────────────────────────

/** Quita filas a medio llenar (sin grados, sin valor) para no guardar ruido. */
fun limpiarDatos(d: EvaluacionFisioDatos): EvaluacionFisioDatos = EvaluacionFisioDatos(
    eva = d.eva?.coerceIn(0, 10),
    zonas = d.zonas.filter { NOMBRE_ZONA[it.key] != null && it.value in 1..3 },
    localizacion = d.localizacion?.trim()?.ifEmpty { null },
    rangos = d.rangos.filter { it.grados != null },
    fuerza = d.fuerza.filter { it.valor != null },
    pruebas = d.pruebas.filter { it.nombre.isNotBlank() },
    tests = d.tests.filter { t -> t.respuestas.any { it != null } },
    notas = d.notas?.trim()?.ifEmpty { null },
)

fun tieneDatos(d: EvaluacionFisioDatos?): Boolean {
    if (d == null) return false
    val l = limpiarDatos(d)
    return l.eva != null || l.zonas.isNotEmpty() || l.localizacion != null || l.rangos.isNotEmpty() ||
        l.fuerza.isNotEmpty() || l.pruebas.isNotEmpty() || l.tests.isNotEmpty() || l.notas != null
}

// ─── JSON (misma forma que la web) ───────────────────────────────────────────

private fun JsonObject.texto(k: String): String? =
    (this[k] as? JsonPrimitive)?.takeIf { it !is JsonNull }?.content?.takeIf { it != "null" }
private fun JsonObject.numero(k: String): Double? =
    (this[k] as? JsonPrimitive)?.takeIf { it !is JsonNull }?.content?.toDoubleOrNull()
private fun JsonElement?.numeroONull(): Double? =
    (this as? JsonPrimitive)?.takeIf { it !is JsonNull }?.content?.toDoubleOrNull()
private fun ladoDe(o: JsonObject): String? = o.texto("lado")?.takeIf { it == "D" || it == "I" }

/** Un número entero se escribe sin ".0" (como lo guarda la web). */
private fun primitivoNum(v: Double): JsonPrimitive =
    if (v == kotlin.math.floor(v) && kotlin.math.abs(v) < 1e15) JsonPrimitive(v.toLong()) else JsonPrimitive(v)

fun datosDeJson(e: JsonElement?): EvaluacionFisioDatos {
    val o = e as? JsonObject ?: return EvaluacionFisioDatos()
    fun arr(k: String): List<JsonObject> = (o[k] as? JsonArray)?.mapNotNull { it as? JsonObject }.orEmpty()
    return EvaluacionFisioDatos(
        eva = o.numero("eva")?.toInt(),
        zonas = (o["zonas"] as? JsonObject)?.entries?.mapNotNull { (k, v) ->
            v.numeroONull()?.toInt()?.takeIf { it in 1..3 }?.let { k to it }
        }?.toMap(LinkedHashMap()) ?: emptyMap(),
        localizacion = o.texto("localizacion"),
        rangos = arr("rangos").mapNotNull { r ->
            RangoMedido(r.texto("articulacion") ?: return@mapNotNull null, r.texto("movimiento") ?: return@mapNotNull null,
                ladoDe(r), r.numero("grados")?.toInt())
        },
        fuerza = arr("fuerza").mapNotNull { f ->
            FuerzaMedida(f.texto("grupo") ?: return@mapNotNull null, ladoDe(f), f.numero("valor")?.toInt())
        },
        pruebas = arr("pruebas").mapNotNull { p ->
            PruebaEspecial(p.texto("nombre") ?: return@mapNotNull null, ladoDe(p), if (p.texto("resultado") == "+") "+" else "-")
        },
        tests = arr("tests").mapNotNull { t ->
            val id = t.texto("id")?.takeIf { it in TESTS_FUNCIONALES } ?: return@mapNotNull null
            TestAplicado(id, (t["respuestas"] as? JsonArray)?.map { it.numeroONull()?.toInt() }.orEmpty(), t.numero("puntaje"))
        },
        notas = o.texto("notas"),
    )
}

/** Datos LIMPIOS → jsonb (solo los bloques con algo, como `limpiarDatos` de la web). */
fun datosAJson(d: EvaluacionFisioDatos): JsonObject {
    val l = limpiarDatos(d)
    fun lado(v: String?) = if (v == null) JsonNull else JsonPrimitive(v)
    return buildJsonObject {
        l.eva?.let { put("eva", it) }
        if (l.zonas.isNotEmpty()) putJsonObject("zonas") { l.zonas.forEach { (k, v) -> put(k, v) } }
        l.localizacion?.let { put("localizacion", it) }
        if (l.rangos.isNotEmpty()) putJsonArray("rangos") {
            l.rangos.forEach { r -> add(buildJsonObject {
                put("articulacion", r.articulacion); put("movimiento", r.movimiento)
                put("lado", lado(r.lado)); put("grados", r.grados)
            }) }
        }
        if (l.fuerza.isNotEmpty()) putJsonArray("fuerza") {
            l.fuerza.forEach { f -> add(buildJsonObject { put("grupo", f.grupo); put("lado", lado(f.lado)); put("valor", f.valor) }) }
        }
        if (l.pruebas.isNotEmpty()) putJsonArray("pruebas") {
            l.pruebas.forEach { p -> add(buildJsonObject { put("nombre", p.nombre.trim()); put("lado", lado(p.lado)); put("resultado", p.resultado) }) }
        }
        if (l.tests.isNotEmpty()) putJsonArray("tests") {
            l.tests.forEach { t -> add(buildJsonObject {
                put("id", t.id)
                put("respuestas", buildJsonArray { t.respuestas.forEach { add(if (it == null) JsonNull else JsonPrimitive(it)) } })
                put("puntaje", t.puntaje?.let { primitivoNum(it) } ?: JsonNull)
            }) }
        }
        l.notas?.let { put("notas", it) }
    }
}

internal fun numeroAJson(v: Double?): JsonElement = v?.let { primitivoNum(it) } ?: JsonNull

// ─── Comparativo inicial → última ────────────────────────────────────────────

/** categoria: "dolor" | "rango" | "fuerza" | "test" | "prueba". */
data class FilaComparativo(
    val clave: String,
    val categoria: String,
    val etiqueta: String,
    val unidad: String,
    val inicial: Double?,
    val ultimo: Double?,
    val inicialTexto: String? = null,
    val ultimoTexto: String? = null,
    val fechaInicial: String,
    val fechaUltima: String?,
    val delta: Double?,
    /** true mejoró · false empeoró · null igual o sin comparación. */
    val mejora: Boolean?,
    val referencia: Int? = null,
    val interpretacion: String? = null,
)

private val ordenCron = compareBy<EvaluacionFisio>({ it.fecha }, { it.createdAt ?: "" })

private data class Medicion(val valor: Double?, val texto: String?, val fecha: String)
private class Serie(
    val categoria: String, val etiqueta: String, val unidad: String, val referencia: Int?,
    /** "mayor" | "menor" | "normal". */
    val mejor: String, val testId: String?,
) { val meds = mutableListOf<Medicion>() }

/**
 * Por cada cosa medida (EVA, cada rango con su lado, cada grupo, cada test, cada
 * prueba): la PRIMERA medición y la ÚLTIMA. Por métrica y no por evaluación.
 */
fun compararEvaluaciones(evaluaciones: List<EvaluacionFisio>): List<FilaComparativo> {
    val evs = evaluaciones.sortedWith(ordenCron)
    val series = LinkedHashMap<String, Serie>()
    fun push(clave: String, base: () -> Serie, m: Medicion) { series.getOrPut(clave, base).meds.add(m) }
    for (ev in evs) {
        val d = ev.datos
        d.eva?.let { push("eva", { Serie("dolor", "Dolor (EVA)", "/10", null, "menor", null) }, Medicion(it.toDouble(), null, ev.fecha)) }
        for (r in d.rangos) {
            val g = r.grados ?: continue
            val (art, mov) = buscarMovimiento(r.articulacion, r.movimiento)
            if (art == null || mov == null) continue
            push("rango|${r.articulacion}|${r.movimiento}|${r.lado ?: ""}", {
                Serie("rango", "${mov.nombre} ${art.nombre.lowercase()}${r.lado?.let { " $it" } ?: ""}", "°", mov.normal, "normal", null)
            }, Medicion(g.toDouble(), null, ev.fecha))
        }
        for (f in d.fuerza) {
            val v = f.valor ?: continue
            push("fuerza|${f.grupo}|${f.lado ?: ""}", {
                Serie("fuerza", "Fuerza ${f.grupo.lowercase()}${f.lado?.let { " $it" } ?: ""}", "/5", null, "mayor", null)
            }, Medicion(v.toDouble(), null, ev.fecha))
        }
        for (t in d.tests) {
            val def = TESTS_FUNCIONALES[t.id] ?: continue
            val p = t.puntaje ?: continue
            push("test|${t.id}", {
                Serie("test", def.corto, if (def.unidad == "%") "%" else "pts", null, if (def.mejorEsMayor) "mayor" else "menor", t.id)
            }, Medicion(p, null, ev.fecha))
        }
        for (p in d.pruebas) {
            push("prueba|${p.nombre.lowercase()}|${p.lado ?: ""}", {
                Serie("prueba", "${p.nombre}${p.lado?.let { " $it" } ?: ""}", "", null, "menor", null)
            }, Medicion(if (p.resultado == "+") 1.0 else 0.0, if (p.resultado == "+") "+" else "−", ev.fecha))
        }
    }
    val orden = listOf("dolor", "test", "rango", "fuerza", "prueba")
    val filas = series.map { (clave, s) ->
        val primera = s.meds.first()
        val ultima = if (s.meds.size > 1) s.meds.last() else null
        val ini = primera.valor
        val fin = ultima?.valor
        val delta = if (ini != null && fin != null) redondearJs((fin - ini) * 10) / 10 else null
        var mejora: Boolean? = null
        if (delta != null && delta != 0.0) {
            mejora = if (s.mejor == "normal" && s.referencia != null) {
                val dIni = kotlin.math.abs(s.referencia - ini!!)
                val dFin = kotlin.math.abs(s.referencia - fin!!)
                if (dFin == dIni) null else dFin < dIni
            } else if (s.mejor == "mayor") delta > 0 else delta < 0
        }
        val interp = s.testId?.let { id -> (fin ?: ini)?.let { interpretar(id, it) } }
        FilaComparativo(
            clave = clave, categoria = s.categoria, etiqueta = s.etiqueta, unidad = s.unidad,
            inicial = ini, ultimo = fin, inicialTexto = primera.texto, ultimoTexto = ultima?.texto,
            fechaInicial = primera.fecha, fechaUltima = ultima?.fecha,
            delta = delta, mejora = mejora, referencia = s.referencia, interpretacion = interp,
        )
    }
    return filas.sortedBy { orden.indexOf(it.categoria) }
}

/** Valor con su unidad, como la web ("125°", "34 %", "88 pts", "7/10"). */
fun formatoValor(f: FilaComparativo, v: Double?, texto: String? = null): String {
    if (texto != null) return texto
    if (v == null) return "—"
    val n = numJs(v)
    return when (f.unidad) { "°" -> "$n°"; "%" -> "$n %"; "pts" -> "$n pts"; else -> "$n${f.unidad}" }
}

/** "Flexión rodilla D: 90° → 125° (+35°)". */
fun textoFila(f: FilaComparativo): String {
    if (f.fechaUltima == null) return "${f.etiqueta}: ${formatoValor(f, f.inicial, f.inicialTexto)}"
    val d = if (f.delta != null && f.categoria != "prueba")
        " (${if (f.delta > 0) "+" else ""}${numJs(f.delta)}${if (f.unidad == "°") "°" else ""})" else ""
    return "${f.etiqueta}: ${formatoValor(f, f.inicial, f.inicialTexto)} → ${formatoValor(f, f.ultimo, f.ultimoTexto)}$d"
}

/** Chip del delta: "+35°", "−5"… o mejor/peor/igual en pruebas. */
fun textoDelta(f: FilaComparativo): String =
    if (f.categoria == "prueba") when (f.mejora) { true -> "mejor"; false -> "peor"; null -> "igual" }
    else if (f.delta == null || f.delta == 0.0) "="
    else "${if (f.delta > 0) "+" else ""}${numJs(f.delta)}${if (f.unidad == "°") "°" else ""}"

// ─── Resumen para la pestaña ─────────────────────────────────────────────────

data class TratamientoOrigen(val id: String, val citaOrigenId: String?)

data class ResumenEvaluacionFisio(
    val hayDatos: Boolean,
    val evaluaciones: List<EvaluacionFisio>,
    val inicial: EvaluacionFisio?,
    val ultima: EvaluacionFisio?,
    val comparativo: List<FilaComparativo>,
    val zonasInicial: Map<String, Int>?,
    val zonasUltima: Map<String, Int>?,
    val objetivos: List<ObjetivoTratamiento>,
    val objetivosLogrados: Int,
    val lineas: List<String>,
)

/** El vínculo evaluación ↔ tratamiento se resuelve al LEER (el tratamiento nace después, con cita_origen_id). */
fun tratamientoDeEvaluacion(tratamientoId: String?, citaId: String?, tratamientos: List<TratamientoOrigen>): String? {
    if (tratamientoId != null) return tratamientoId
    if (citaId == null) return null
    return tratamientos.firstOrNull { it.citaOrigenId == citaId }?.id
}

fun tratamientoDeEvaluacion(ev: EvaluacionFisio, tratamientos: List<TratamientoOrigen>): String? =
    tratamientoDeEvaluacion(ev.tratamientoId, ev.citaId, tratamientos)

fun tratamientoDeObjetivo(o: ObjetivoTratamiento, evaluaciones: List<EvaluacionFisio>, tratamientos: List<TratamientoOrigen>): String? {
    if (o.tratamientoId != null) return o.tratamientoId
    val ev = evaluaciones.firstOrNull { it.id == o.evaluacionId } ?: return null
    return tratamientoDeEvaluacion(ev, tratamientos)
}

fun resumenEvaluacionFisio(
    evaluaciones: List<EvaluacionFisio>,
    objetivos: List<ObjetivoTratamiento> = emptyList(),
    tratamientos: List<TratamientoOrigen> = emptyList(),
    tratamientoId: String? = null,
): ResumenEvaluacionFisio {
    var evs = evaluaciones.sortedWith(ordenCron)
    var objs = objetivos
    if (tratamientoId != null) {
        evs = evs.filter { tratamientoDeEvaluacion(it, tratamientos) == tratamientoId }
        objs = objs.filter { tratamientoDeObjetivo(it, evaluaciones, tratamientos) == tratamientoId }
    }
    objs = objs.sortedWith(compareBy<ObjetivoTratamiento>({ it.orden }, { it.createdAt ?: "" }))
    val comparativo = compararEvaluaciones(evs)
    val conZonas = evs.filter { it.datos.zonas.isNotEmpty() }
    val inicial = evs.firstOrNull { it.tipo == "inicial" } ?: evs.firstOrNull()
    val ultima = if (evs.size > 1) evs.last() else null
    val lineas = comparativo.map { textoFila(it) }.toMutableList()
    for (o in objs) {
        val u = o.unidad ?: ""
        val medida = if (o.valorInicial != null || o.meta != null)
            " (${o.valorInicial?.let { numJs(it) } ?: "—"}$u → meta ${o.meta?.let { numJs(it) } ?: "—"}$u" +
                "${o.logrado?.let { ", logrado ${numJs(it)}$u" } ?: ""})"
        else ""
        lineas.add("Objetivo: ${o.texto}$medida — ${ESTADOS_OBJETIVO.firstOrNull { it.id == o.estado }?.label ?: o.estado}")
    }
    return ResumenEvaluacionFisio(
        hayDatos = evs.isNotEmpty() || objs.isNotEmpty(),
        evaluaciones = evs,
        inicial = inicial,
        ultima = ultima,
        comparativo = comparativo,
        zonasInicial = conZonas.firstOrNull()?.datos?.zonas,
        zonasUltima = if (conZonas.size > 1) conZonas.last().datos.zonas else null,
        objetivos = objs,
        objetivosLogrados = objs.count { it.estado == "logrado" },
        lineas = lineas,
    )
}

// ─── ¿La ficha muestra la pestaña 📏? (gemelo de `pacienteEsFisio`) ───────────

data class AtencionFisio(
    val especialidadId: String? = null,
    val especialidadServicioId: String? = null,
    val especialidadesProfesional: List<String>? = null,
)

fun pacienteEsFisio(
    mapa: MapaFisio,
    atenciones: List<AtencionFisio> = emptyList(),
    tieneEvaluaciones: Boolean = false,
    especialidadesDeQuienMira: List<String>? = null,
): Boolean {
    if (mapa.solo) return true
    if (mapa.ids.isEmpty()) return false
    if (tieneEvaluaciones) return true
    if (atenciones.any { citaEsFisio(mapa, it.especialidadId, it.especialidadServicioId, it.especialidadesProfesional) }) return true
    return especialidadesDeQuienMira.orEmpty().any { it.isNotBlank() && it in mapa.ids }
}
