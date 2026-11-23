package pe.saniape.app.data.staff

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.put
import kotlin.math.ln
import kotlin.math.roundToInt
import kotlin.math.sqrt

/**
 * 🦴 EVALUACIÓN REUMATOLÓGICA. Gemelo de `lib/evaluacion-reumatologica.ts` (web):
 * los MISMOS ids de articulaciones, puntos y grupos de fuerza, las mismas
 * coordenadas del dibujo y las mismas fórmulas. El servidor recalcula conteos e
 * índices al guardar; aquí se calculan para mostrarlos EN VIVO mientras se llena.
 *
 * La especialidad se reconoce por su NOMBRE en la web; a la app le llega resuelta
 * en /api/staff/contexto (`especialidadesReumatologia`, `soloReumatologia`).
 */

data class MapaReumatologia(val ids: List<String> = emptyList(), val solo: Boolean = false)

fun citaEsReumatologia(
    mapa: MapaReumatologia,
    especialidadId: String? = null,
    especialidadServicioId: String? = null,
    especialidadesProfesional: List<String>? = null,
): Boolean = atencionEnMapaEspecialidad(mapa.ids, mapa.solo, especialidadId, especialidadServicioId, especialidadesProfesional)

/** ¿La ficha muestra la pestaña 🦴 Evaluación articular? Misma regla que `pacienteEsPodologia`. */
fun pacienteEsReumatologia(
    mapa: MapaReumatologia,
    atenciones: List<AtencionFisio> = emptyList(),
    tieneEvaluaciones: Boolean = false,
    especialidadesDeQuienMira: List<String>? = null,
): Boolean {
    if (mapa.solo) return true
    if (mapa.ids.isEmpty()) return false
    if (tieneEvaluaciones) return true
    if (atenciones.any { citaEsReumatologia(mapa, it.especialidadId, it.especialidadServicioId, it.especialidadesProfesional) }) return true
    return especialidadesDeQuienMira.orEmpty().any { it.isNotBlank() && it in mapa.ids }
}

const val EVALUACION_REUMA_NO_DISPONIBLE = "La evaluación articular aún no está disponible en este servidor."

// ── El mapa ──────────────────────────────────────────────────────────────────
/**
 * Dibujo: la ilustración del cuerpo (la misma de la web, `/reumatologia/cuerpo.webp`)
 * en un lienzo de 240 × 539. El lado derecho del PACIENTE va a la izquierda de
 * quien mira. Gemelo de `MAPA_ARTICULAR` en lib/evaluacion-reumatologica.ts.
 */
object MapaArticularGeo {
    const val ANCHO = 240f
    const val ALTO = 539f
    /** La bolita de la mano y del pie DERECHOS del paciente (el izquierdo: x' = ANCHO − x). */
    val BOLITA: Map<String, Pair<Float, Float>> = mapOf("mano" to (21f to 292f), "pie" to (74f to 526f))
}

data class ArticulacionDef(val id: String, val nombre: String, val lado: String, val en28: Boolean, val x: Float, val y: Float) {
    /** "mano" | "pie" si se marca en el cuadro ampliado; null si va sobre el cuerpo. */
    val zona: String? get() = zonaAmpliadaDe(id)
}

/** Las articulaciones chicas (dedos) se marcan en la mano / el pie ampliado, no sobre el cuerpo. */
fun zonaAmpliadaDe(id: String): String? = when {
    id.startsWith("mcf") || id.startsWith("ifp") || id.startsWith("ifd") -> "mano"
    id.startsWith("mtf") -> "pie"
    else -> null
}

/**
 * Dónde va cada articulación DENTRO de la imagen ampliada, en % (x, y). La mano es
 * la derecha con la palma al frente y el pulgar a la izquierda; el pie, el derecho
 * visto desde arriba con el dedo gordo a la izquierda. Gemelo de PUNTOS_MANO / PUNTOS_PIE (web).
 */
val PUNTOS_MANO_REUMA: Map<String, Pair<Float, Float>> = mapOf(
    "mcf1" to (24f to 60f), "ifp1" to (13f to 49f),
    "mcf2" to (41f to 42.5f), "ifp2" to (38f to 26f), "ifd2" to (36f to 14f),
    "mcf3" to (57f to 40f), "ifp3" to (57f to 22.5f), "ifd3" to (57f to 10f),
    "mcf4" to (72f to 41f), "ifp4" to (75f to 25.6f), "ifd4" to (77f to 14f),
    "mcf5" to (84f to 47.5f), "ifp5" to (88f to 35f), "ifd5" to (92f to 25.6f),
)
val PUNTOS_PIE_REUMA: Map<String, Pair<Float, Float>> = mapOf(
    "mtf1" to (18f to 40f), "mtf2" to (42f to 38f), "mtf3" to (59f to 42f), "mtf4" to (74f to 47f), "mtf5" to (88f to 53f),
)
/** Proporción ancho / alto de cada imagen ampliada. */
const val PROPORCION_MANO_REUMA = 150f / 207f
const val PROPORCION_PIE_REUMA = 150f / 216f
/** La mano derecha se usa tal cual y la izquierda en espejo; el pie, al revés (dedo gordo hacia el centro). */
fun zonaReumaEnEspejo(zona: String, lado: String): Boolean = if (zona == "mano") lado == "izq" else lado == "der"

/** d = dolorosa · i = inflamada. Solo se guardan las marcadas. */
data class EstadoArticulacion(val d: Boolean = false, val i: Boolean = false) {
    val texto: String get() = when {
        d && i -> "dolorosa e inflamada"; d -> "dolorosa"; i -> "inflamada"; else -> "sin hallazgos"
    }
}

private data class BaseArt(val clave: String, val nombre: String, val fem: Boolean, val en28: Boolean, val x: Float, val y: Float)

private val BASE_ARTICULACIONES: List<BaseArt> = buildList {
    val (mx, my) = MapaArticularGeo.BOLITA.getValue("mano")
    val (px, py) = MapaArticularGeo.BOLITA.getValue("pie")
    add(BaseArt("hombro", "Hombro", false, true, 58f, 113f))
    add(BaseArt("codo", "Codo", false, true, 41f, 196f))
    add(BaseArt("muneca", "Muñeca", true, true, 25f, 262f))
    (1..5).forEach { n -> add(BaseArt("mcf$n", "MCF $n (nudillo)", true, true, mx, my)) }
    add(BaseArt("ifp1", "IF del pulgar", true, true, mx, my))
    (2..5).forEach { n -> add(BaseArt("ifp$n", "IFP $n", true, true, mx, my)) }
    add(BaseArt("rodilla", "Rodilla", true, true, 84f, 383f))
    // Mapa completo
    (2..5).forEach { n -> add(BaseArt("ifd$n", "IFD $n", true, false, mx, my)) }
    add(BaseArt("cadera", "Cadera", true, false, 86f, 275f))
    add(BaseArt("tobillo", "Tobillo", false, false, 82f, 496f))
    (1..5).forEach { n -> add(BaseArt("mtf$n", "MTF $n (pie)", true, false, px, py)) }
}

val ARTICULACIONES_REUMA: List<ArticulacionDef> = listOf("der", "izq").flatMap { lado ->
    BASE_ARTICULACIONES.map { b ->
        val ladoTxt = if (lado == "der") (if (b.fem) "derecha" else "derecho") else (if (b.fem) "izquierda" else "izquierdo")
        ArticulacionDef("${b.clave}_$lado", "${b.nombre} $ladoTxt", lado, b.en28, if (lado == "der") b.x else MapaArticularGeo.ANCHO - b.x, b.y)
    }
}
private val ARTICULACION_POR_ID = ARTICULACIONES_REUMA.associateBy { it.id }

/** Tocar una articulación: nada → dolor → inflamación → ambas → nada (null). */
fun rotarEstadoArticulacion(e: EstadoArticulacion?): EstadoArticulacion? = when {
    e == null || (!e.d && !e.i) -> EstadoArticulacion(d = true)
    e.d && !e.i -> EstadoArticulacion(i = true)
    !e.d && e.i -> EstadoArticulacion(d = true, i = true)
    else -> null
}

/** Lo que se tocó sobre el cuerpo: una articulación grande, o la bolita de una mano / un pie. */
sealed interface ToqueMapaReuma {
    data class Articulacion(val id: String) : ToqueMapaReuma
    data class Zona(val zona: String, val lado: String) : ToqueMapaReuma
}

/** [x], [y] en unidades del dibujo; [visibles] = las articulaciones que se muestran (28 o completo). */
fun toqueEnMapaReuma(x: Float, y: Float, visibles: List<ArticulacionDef>): ToqueMapaReuma? {
    fun d2(ax: Float, ay: Float) = (ax - x) * (ax - x) + (ay - y) * (ay - y)
    val candidatos = mutableListOf<Pair<ToqueMapaReuma, Float>>()
    visibles.filter { it.zona == null }.forEach { a -> if (d2(a.x, a.y) <= 17f * 17f) candidatos += ToqueMapaReuma.Articulacion(a.id) to d2(a.x, a.y) }
    visibles.mapNotNull { a -> a.zona?.let { Triple(it, a.lado, a) } }.distinctBy { it.first + it.second }.forEach { (zona, lado, a) ->
        if (d2(a.x, a.y) <= 21f * 21f) candidatos += ToqueMapaReuma.Zona(zona, lado) to d2(a.x, a.y)
    }
    return candidatos.minByOrNull { it.second }?.first
}

/** La articulación de la mano / el pie ampliado más cercana al toque ([px], [py] en % de la imagen YA en espejo si corresponde). */
fun toqueEnZonaReuma(zona: String, lado: String, px: Float, py: Float, visibles: List<ArticulacionDef>, proporcion: Float): String? {
    val puntos = if (zona == "mano") PUNTOS_MANO_REUMA else PUNTOS_PIE_REUMA
    val espejo = zonaReumaEnEspejo(zona, lado)
    return visibles.filter { it.zona == zona && it.lado == lado }.mapNotNull { a ->
        val p = puntos[a.id.substringBeforeLast('_')] ?: return@mapNotNull null
        val x = if (espejo) 100f - p.first else p.first
        // La distancia en y se lleva a la misma escala que x (la imagen es más alta que ancha).
        val dx = x - px; val dy = (p.second - py) / proporcion
        a.id to (dx * dx + dy * dy)
    }.filter { it.second <= 9f * 9f }.minByOrNull { it.second }?.first
}

data class ConteosReuma(val nad28: Int, val nat28: Int, val dolorosas: Int, val inflamadas: Int)

fun conteosReuma(a: Map<String, EstadoArticulacion>): ConteosReuma {
    var nad = 0; var nat = 0; var d = 0; var i = 0
    a.forEach { (id, e) ->
        val def = ARTICULACION_POR_ID[id] ?: return@forEach
        if (e.d) { d++; if (def.en28) nad++ }
        if (e.i) { i++; if (def.en28) nat++ }
    }
    return ConteosReuma(nad, nat, d, i)
}

// ── Puntos dolorosos y fuerza ────────────────────────────────────────────────
data class OpcionReuma(val id: String, val nombre: String)

val PUNTOS_DOLOROSOS_REUMA: List<OpcionReuma> = listOf(
    "occipucio" to "Occipucio", "cervical" to "Cervical bajo", "trapecio" to "Trapecio",
    "supraespinoso" to "Supraespinoso", "costilla2" to "Segunda costilla", "epicondilo" to "Epicóndilo lateral",
    "gluteo" to "Glúteo", "trocanter" to "Trocánter mayor", "rodilla_medial" to "Rodilla (cara interna)",
).flatMap { (clave, nombre) -> listOf("der" to "der.", "izq" to "izq.").map { (l, t) -> OpcionReuma("${clave}_$l", "$nombre $t") } }

val GRUPOS_FUERZA_REUMA: List<OpcionReuma> = listOf(
    OpcionReuma("prension_der", "Prensión mano der."), OpcionReuma("prension_izq", "Prensión mano izq."),
    OpcionReuma("proximal_sup_der", "Hombro / brazo der."), OpcionReuma("proximal_sup_izq", "Hombro / brazo izq."),
    OpcionReuma("proximal_inf_der", "Cadera / muslo der."), OpcionReuma("proximal_inf_izq", "Cadera / muslo izq."),
)

// ── Índices ──────────────────────────────────────────────────────────────────
enum class NivelActividadReuma(val texto: String) {
    REMISION("Remisión"), BAJA("Actividad baja"), MODERADA("Actividad moderada"), ALTA("Actividad alta")
}

private fun r1(n: Double) = (n * 10).roundToInt() / 10.0
private fun r2(n: Double) = (n * 100).roundToInt() / 100.0

fun cdaiReuma(c: ConteosReuma, evgPaciente: Double?, evgMedico: Double?): Double? =
    if (evgPaciente == null || evgMedico == null) null else r1(c.nad28 + c.nat28 + evgPaciente + evgMedico)

fun nivelCdai(v: Double): NivelActividadReuma =
    if (v <= 2.8) NivelActividadReuma.REMISION else if (v <= 10) NivelActividadReuma.BAJA else if (v <= 22) NivelActividadReuma.MODERADA else NivelActividadReuma.ALTA

/** DAS28 con VSG (mm/h) o con PCR (mg/L): valor y tipo ("VSG" | "PCR"). */
fun das28Reuma(c: ConteosReuma, evgPaciente: Double?, vsg: Double?, pcr: Double?): Pair<Double, String>? {
    if (evgPaciente == null) return null
    val base = 0.56 * sqrt(c.nad28.toDouble()) + 0.28 * sqrt(c.nat28.toDouble()) + 0.014 * evgPaciente * 10
    if (vsg != null && vsg > 0) return r2(base + 0.70 * ln(vsg)) to "VSG"
    if (pcr != null && pcr >= 0) return r2(base + 0.36 * ln(pcr + 1) + 0.96) to "PCR"
    return null
}

fun nivelDas28(v: Double): NivelActividadReuma =
    if (v < 2.6) NivelActividadReuma.REMISION else if (v <= 3.2) NivelActividadReuma.BAJA else if (v <= 5.1) NivelActividadReuma.MODERADA else NivelActividadReuma.ALTA

data class ActividadReuma(val nombre: String, val valor: Double, val nivel: NivelActividadReuma)

/** El índice que manda para mostrar: DAS28 si lo hay; si no, CDAI. */
fun actividadReuma(cdai: Double?, das28: Double?, das28Tipo: String?): ActividadReuma? = when {
    das28 != null -> ActividadReuma("DAS28-${das28Tipo ?: "VSG"}", das28, nivelDas28(das28))
    cdai != null -> ActividadReuma("CDAI", cdai, nivelCdai(cdai))
    else -> null
}

// ── Fila y borrador ──────────────────────────────────────────────────────────
data class EvaluacionReumatologica(
    val id: String,
    val pacienteId: String,
    val terapeutaId: String?,
    val fecha: String,
    val creadoEn: String,
    val articulaciones: Map<String, EstadoArticulacion>,
    val puntosDolorosos: List<String>,
    val fuerza: Map<String, Int>,
    val eva: Double?,
    val evgPaciente: Double?,
    val evgMedico: Double?,
    val rigidezMin: Int?,
    val vsg: Double?,
    val pcr: Double?,
    val nad28: Int,
    val nat28: Int,
    val cdai: Double?,
    val das28: Double?,
    val das28Tipo: String?,
    val observaciones: String?,
) {
    val actividad: ActividadReuma? get() = actividadReuma(cdai, das28, das28Tipo)
}

data class BorradorEvaluacionReuma(
    val pacienteId: String,
    val fecha: String,
    val citaId: String? = null,
    val articulaciones: Map<String, EstadoArticulacion> = emptyMap(),
    val puntosDolorosos: List<String> = emptyList(),
    val fuerza: Map<String, Int> = emptyMap(),
    val eva: Double? = null,
    val evgPaciente: Double? = null,
    val evgMedico: Double? = null,
    val rigidezMin: Int? = null,
    val vsg: Double? = null,
    val pcr: Double? = null,
    val observaciones: String? = null,
)

fun validarEvaluacionReuma(b: BorradorEvaluacionReuma): List<String> {
    val e = mutableListOf<String>()
    if (b.rigidezMin != null && b.rigidezMin !in 0..1440) e += "La rigidez matutina va en minutos (0 a 1440)."
    if (b.vsg != null && (b.vsg < 0 || b.vsg > 200)) e += "La VSG va de 0 a 200 mm/h."
    if (b.pcr != null && (b.pcr < 0 || b.pcr > 500)) e += "La PCR va de 0 a 500 mg/L."
    if ((b.observaciones ?: "").length > 4000) e += "Las observaciones son demasiado largas."
    val vacia = b.articulaciones.isEmpty() && b.puntosDolorosos.isEmpty() && b.fuerza.isEmpty() && b.eva == null &&
        b.evgPaciente == null && b.evgMedico == null && b.rigidezMin == null && b.vsg == null && b.pcr == null &&
        b.observaciones.isNullOrBlank()
    if (vacia) e += "Marca al menos una articulación o completa algún dato (o escribe «sin hallazgos» en observaciones)."
    return e
}

private fun numeroJson(v: Double?) = if (v == null) JsonNull else JsonPrimitive(v)

/** El cuerpo del POST (sin [id]) o del PATCH (con [id]): el contrato de /api/staff/evaluacion-reumatologica. */
fun cuerpoEvaluacionReuma(b: BorradorEvaluacionReuma, id: String? = null): JsonObject = buildJsonObject {
    if (id != null) put("id", id) else { put("pacienteId", b.pacienteId); b.citaId?.let { put("citaId", it) } }
    put("fecha", b.fecha)
    put("articulaciones", buildJsonObject {
        b.articulaciones.forEach { (k, e) -> put(k, buildJsonObject { if (e.d) put("d", true); if (e.i) put("i", true) }) }
    })
    put("puntosDolorosos", buildJsonArray { b.puntosDolorosos.forEach { add(JsonPrimitive(it)) } })
    put("fuerza", buildJsonObject { b.fuerza.forEach { (k, n) -> put(k, n) } })
    put("eva", numeroJson(b.eva)); put("evgPaciente", numeroJson(b.evgPaciente)); put("evgMedico", numeroJson(b.evgMedico))
    put("rigidezMin", if (b.rigidezMin == null) JsonNull else JsonPrimitive(b.rigidezMin))
    put("vsg", numeroJson(b.vsg)); put("pcr", numeroJson(b.pcr))
    put("observaciones", b.observaciones?.let { JsonPrimitive(it) } ?: JsonNull)
}

private fun JsonObject.txt(k: String): String? = (this[k] as? JsonPrimitive)?.contentOrNull?.takeIf { it != "null" && it.isNotBlank() }
private fun JsonObject.num(k: String): Double? = (this[k] as? JsonPrimitive)?.let { it.doubleOrNull ?: it.contentOrNull?.toDoubleOrNull() }

/** Fila de la API (snake_case) → modelo. null si le falta lo mínimo. */
fun evaluacionReumaDeJson(o: JsonObject): EvaluacionReumatologica? {
    val id = o.txt("id") ?: return null
    val articulaciones = (o["articulaciones"] as? JsonObject).orEmpty().mapNotNull { (k, v) ->
        val e = v as? JsonObject ?: return@mapNotNull null
        if (k !in ARTICULACION_POR_ID) return@mapNotNull null
        val d = (e["d"] as? JsonPrimitive)?.booleanOrNull == true
        val i = (e["i"] as? JsonPrimitive)?.booleanOrNull == true
        if (d || i) k to EstadoArticulacion(d, i) else null
    }.toMap()
    return EvaluacionReumatologica(
        id = id,
        pacienteId = o.txt("paciente_id").orEmpty(),
        terapeutaId = o.txt("terapeuta_id"),
        fecha = o.txt("fecha").orEmpty(),
        creadoEn = o.txt("created_at").orEmpty(),
        articulaciones = articulaciones,
        puntosDolorosos = (o["puntos_dolorosos"] as? JsonArray).orEmpty().mapNotNull { (it as? JsonPrimitive)?.contentOrNull },
        fuerza = (o["fuerza"] as? JsonObject).orEmpty().mapNotNull { (k, v) -> (v as? JsonPrimitive)?.doubleOrNull?.let { k to it.roundToInt() } }.toMap(),
        eva = o.num("eva"), evgPaciente = o.num("evg_paciente"), evgMedico = o.num("evg_medico"),
        rigidezMin = o.num("rigidez_min")?.roundToInt(),
        vsg = o.num("vsg"), pcr = o.num("pcr"),
        nad28 = o.num("nad28")?.roundToInt() ?: 0, nat28 = o.num("nat28")?.roundToInt() ?: 0,
        cdai = o.num("cdai"), das28 = o.num("das28"), das28Tipo = o.txt("das28_tipo"),
        observaciones = o.txt("observaciones"),
    )
}

fun ordenarEvaluacionesReuma(l: List<EvaluacionReumatologica>): List<EvaluacionReumatologica> =
    l.sortedByDescending { it.fecha + it.creadoEn }

data class CambioReuma(val etiqueta: String, val antes: String, val ahora: String, val mejora: Boolean)

private fun limpio(n: Double) = if (n == n.toLong().toDouble()) n.toLong().toString() else n.toString()

/** Qué cambió respecto de la evaluación anterior (en todo esto, menos es mejor). */
fun compararEvaluacionesReuma(ahora: EvaluacionReumatologica, antes: EvaluacionReumatologica?): List<CambioReuma> {
    if (antes == null) return emptyList()
    val pares: List<Triple<String, Double?, Double?>> = listOf(
        Triple("Articulaciones dolorosas", antes.nad28.toDouble(), ahora.nad28.toDouble()),
        Triple("Articulaciones inflamadas", antes.nat28.toDouble(), ahora.nat28.toDouble()),
        Triple("CDAI", antes.cdai, ahora.cdai),
        Triple("DAS28", if (antes.das28Tipo == ahora.das28Tipo) antes.das28 else null, ahora.das28),
        Triple("Dolor (EVA)", antes.eva, ahora.eva),
        Triple("Rigidez matutina (min)", antes.rigidezMin?.toDouble(), ahora.rigidezMin?.toDouble()),
    )
    return pares.mapNotNull { (t, a, b) -> if (a != null && b != null && a != b) CambioReuma(t, limpio(a), limpio(b), b < a) else null }
}

fun nombresArticulaciones(a: Map<String, EstadoArticulacion>, inflamadas: Boolean): List<String> =
    ARTICULACIONES_REUMA.filter { x -> a[x.id]?.let { if (inflamadas) it.i else it.d } == true }.map { it.nombre }
