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
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.pow
import kotlin.math.sign

/**
 * 💇 TRICOSCOPÍA (medicina capilar): la evaluación con el tricoscopio en cada
 * visita. Tabla `evaluaciones_tricoscopicas`, vía /api/staff/tricoscopia (ver
 * [TricoscopiaRepo]).
 *
 * Gemelo PURO de `lib/tricoscopia.ts` de la web: escalas y grados, zonas,
 * signos, validación (mismos textos), la evolución por zona y la ESTIMACIÓN
 * para trasplante (`estimarTrasplante`, con los mismos valores por defecto).
 * Los tests (commonTest/TricoscopiaTest) copian los valores esperados de
 * `lib/__tests__/tricoscopia.test.ts`.
 *
 * Solo CAPILAR, y por PACIENTE (como nutrición): el MAPA lo arma la web
 * (/api/staff/contexto: `especialidadesCapilar`, `soloCapilar`); un backend sin
 * esos campos deja el mapa vacío y la pestaña no aparece.
 */

// ─── Visibilidad (gemelos de mapaCapilar / citaEsCapilar / pacienteEsCapilar) ─

data class MapaCapilar(val ids: List<String> = emptyList(), val solo: Boolean = false) {
    /** ¿La clínica hace algo capilar? */
    val activo: Boolean get() = solo || ids.isNotEmpty()
}

/** ¿Esta cita / tratamiento es capilar? Misma cadena que `citaEsNutricion`. */
fun citaEsCapilar(
    mapa: MapaCapilar,
    especialidadId: String? = null,
    especialidadServicioId: String? = null,
    especialidadesProfesional: List<String>? = null,
): Boolean {
    if (mapa.solo) return true
    if (mapa.ids.isEmpty()) return false
    listOf(especialidadId, especialidadServicioId).firstOrNull { !it.isNullOrBlank() }
        ?.let { return it in mapa.ids }
    val prof = especialidadesProfesional.orEmpty().filter { it.isNotBlank() }
    return prof.isNotEmpty() && prof.all { it in mapa.ids }
}

/**
 * ¿La ficha muestra la pestaña 💇 Tricoscopía? Clínica solo capilar (RENOVA) →
 * sí; sin capilar → no; mixta (GLAM) → si ya tiene evaluaciones, si alguna
 * atención es capilar o si quien mira es de una especialidad capilar.
 */
fun pacienteEsCapilar(
    mapa: MapaCapilar,
    atenciones: List<AtencionFisio> = emptyList(),
    tieneEvaluaciones: Boolean = false,
    especialidadesDeQuienMira: List<String>? = null,
): Boolean {
    if (mapa.solo) return true
    if (mapa.ids.isEmpty()) return false
    if (tieneEvaluaciones) return true
    if (atenciones.any { citaEsCapilar(mapa, it.especialidadId, it.especialidadServicioId, it.especialidadesProfesional) }) return true
    return especialidadesDeQuienMira.orEmpty().any { it.isNotBlank() && it in mapa.ids }
}

// ─── Catálogos ───────────────────────────────────────────────────────────────

data class DefEscala(val id: String, val nombre: String, val boton: String, val grados: List<String>, val ayuda: String)

val ESCALAS: List<DefEscala> = listOf(
    DefEscala("hamilton_norwood", "Hamilton-Norwood", "♂ Hamilton-Norwood",
        listOf("I", "II", "IIa", "III", "IIIa", "III vértex", "IV", "IVa", "V", "Va", "VI", "VII"),
        "Patrón masculino: entradas y coronilla. Las variantes \"a\" retroceden de frente sin coronilla."),
    DefEscala("ludwig", "Ludwig", "♀ Ludwig", listOf("I", "II", "III"),
        "Patrón femenino: rarefacción de la coronilla con la línea frontal conservada."),
    DefEscala("sinclair", "Sinclair", "♀ Sinclair", listOf("1", "2", "3", "4", "5"),
        "Patrón femenino por el ancho de la raya central (1 = normal, 5 = avanzado)."),
    DefEscala("difuso", "Patrón difuso", "Patrón difuso", listOf("Leve", "Moderado", "Severo"),
        "Caída difusa (efluvio, difusa no androgenética): sin un patrón de zonas."),
    DefEscala("otro", "Otro", "Otro", emptyList(), "Escríbelo (ej. alopecia areata en placa, cicatricial…)."),
)

fun escalaDe(id: String?): DefEscala? = ESCALAS.firstOrNull { it.id == id }

/** "Hamilton-Norwood III vértex" · "Ludwig II" · "Patrón difuso (moderado)" · "Otro: …". */
fun textoClasificacion(escala: String?, grado: String?): String? {
    val e = escalaDe(escala)
    val g = grado.orEmpty().trim()
    if (e == null) return g.ifEmpty { null }
    if (e.id == "otro") return g.ifEmpty { "Otro patrón" }
    if (e.id == "difuso") return if (g.isNotEmpty()) "${e.nombre} (${g.lowercase()})" else e.nombre
    return if (g.isNotEmpty()) "${e.nombre} $g" else e.nombre
}

enum class ZonaCapilar(val id: String, val nombre: String, val corto: String) {
    FRONTAL("frontal", "Frontal", "Frontal"),
    MEDIA("media", "Media", "Media"),
    VERTEX("vertex", "Coronilla / vértex", "Coronilla"),
    OCCIPITAL("occipital", "Occipital (donante)", "Occipital"),
    TEMPORAL("temporal", "Temporales", "Temporal");

    companion object {
        fun de(id: String?): ZonaCapilar? = entries.firstOrNull { it.id == id }
    }
}

fun nombreZona(id: String): String = ZonaCapilar.de(id)?.nombre ?: id

/** Signos tricoscópicos (Rudnicka, Atlas of Trichoscopy 2012). Claves estables: se guardan en la base. */
val SIGNOS_TRICOSCOPICOS: List<Pair<String, String>> = listOf(
    "peripilar" to "Signo peripilar",
    "vellos" to "Vellos / miniaturización",
    "anisotricosis" to "Anisotricosis",
    "unidades_un_pelo" to "Unidades de un solo pelo",
    "puntos_amarillos" to "Puntos amarillos",
    "puntos_negros" to "Puntos negros",
    "pelos_exclamacion" to "Pelos en signo de exclamación",
    "pelos_rotos" to "Pelos rotos",
    "pelos_recrecimiento" to "Pelos cortos de recrecimiento",
    "puntos_blancos" to "Puntos blancos (fibrosis)",
    "descamacion" to "Descamación",
    "descamacion_perifolicular" to "Descamación perifolicular",
    "eritema" to "Eritema perifolicular",
    "vasos" to "Vasos arborizantes / en lazo",
    "panal" to "Patrón en panal",
)
private val IDS_SIGNO = SIGNOS_TRICOSCOPICOS.map { it.first }.toSet()
fun nombreSigno(id: String): String = SIGNOS_TRICOSCOPICOS.firstOrNull { it.first == id }?.second ?: id

/** Las mediciones de una zona, en el orden de la grilla. `mejora`: 1 = subir es mejor, −1 = bajar es mejor. */
enum class MetricaZona(
    val id: String, val nombre: String, val corto: String, val unidad: String, val decimales: Int,
    val mejora: Int, val placeholder: String, val min: Double, val max: Double,
) {
    DENSIDAD("densidad", "Densidad", "Densidad", "pelos/cm²", 1, 1, "180", 0.0, 600.0),
    UF_CM2("uf_cm2", "Unidades foliculares", "UF/cm²", "UF/cm²", 1, 1, "80", 0.0, 300.0),
    MINIATURIZACION("miniaturizacion_pct", "Miniaturización", "% miniat.", "%", 1, -1, "15", 0.0, 100.0),
    PELOS_POR_UF("pelos_por_uf", "Pelos por UF", "Pelos/UF", "", 2, 1, "2.2", 0.0, 6.0),
    DIAMETRO("diametro_um", "Diámetro medio", "Diám. µm", "µm", 1, 1, "70", 0.0, 200.0);

    /** Clave camelCase del pedido (la API acepta las dos). */
    val camel: String get() = when (this) {
        DENSIDAD -> "densidad"; UF_CM2 -> "ufCm2"; MINIATURIZACION -> "miniaturizacionPct"
        PELOS_POR_UF -> "pelosPorUf"; DIAMETRO -> "diametroUm"
    }
}

/** Límites de lo que no es por zona (gemelos de LIMITES_TRICOSCOPIA). */
object LimitesTricoscopia {
    val DONANTE_AREA = 10.0 to 500.0
    val EXTRAIBLE = 1.0 to 60.0
    val RECEPTOR_AREA = 1.0 to 400.0
    val DENSIDAD_OBJETIVO = 5.0 to 120.0
    const val GRADO = 40
    const val OBSERVACIONES = 4000
    const val SIGNOS = 20
}

/** Valores por defecto CONSERVADORES de la estimación (ver el diseño de la web: fuentes). */
object TrasplanteDefecto {
    const val AREA_DONANTE_CM2 = 150.0
    const val EXTRAIBLE_PCT = 25.0
    const val DENSIDAD_OBJETIVO = 35.0
}
/** Sobre este % extraído de la zona donante se avisa (riesgo de dejarla rala). */
const val EXTRAIBLE_SEGURO_MAX = 30.0
/** Áreas receptoras de un toque (cm²). */
val AREAS_RECEPTORAS_RAPIDAS: List<Int> = listOf(25, 50, 75, 100, 150)

// ─── Modelo ──────────────────────────────────────────────────────────────────

data class ZonaMedida(
    val zona: ZonaCapilar,
    val densidad: Double? = null,
    val ufCm2: Double? = null,
    val miniaturizacionPct: Double? = null,
    val diametroUm: Double? = null,
    val pelosPorUf: Double? = null,
    val signos: List<String> = emptyList(),
) {
    fun valor(m: MetricaZona): Double? = when (m) {
        MetricaZona.DENSIDAD -> densidad
        MetricaZona.UF_CM2 -> ufCm2
        MetricaZona.MINIATURIZACION -> miniaturizacionPct
        MetricaZona.PELOS_POR_UF -> pelosPorUf
        MetricaZona.DIAMETRO -> diametroUm
    }

    fun con(m: MetricaZona, v: Double?): ZonaMedida = when (m) {
        MetricaZona.DENSIDAD -> copy(densidad = v)
        MetricaZona.UF_CM2 -> copy(ufCm2 = v)
        MetricaZona.MINIATURIZACION -> copy(miniaturizacionPct = v)
        MetricaZona.PELOS_POR_UF -> copy(pelosPorUf = v)
        MetricaZona.DIAMETRO -> copy(diametroUm = v)
    }
}

data class DonanteTrico(val areaCm2: Double?, val extraiblePct: Double?)
data class ReceptorTrico(val areaCm2: Double?, val densidadObjetivo: Double?)

data class EvaluacionTricoscopica(
    val id: String,
    val pacienteId: String = "",
    val citaId: String? = null,
    val tratamientoId: String? = null,
    val terapeutaId: String? = null,
    val fecha: String,
    val escala: String? = null,
    val grado: String? = null,
    val zonas: List<ZonaMedida> = emptyList(),
    val donante: DonanteTrico? = null,
    val receptor: ReceptorTrico? = null,
    val observaciones: String? = null,
    val visiblePaciente: Boolean = true,
    val createdAt: String? = null,
)

/** Una zona tal como llega del formulario o del JSON: textos sin limpiar (null/"" = vacío). */
data class ZonaCruda(
    val zona: String,
    val valores: Map<MetricaZona, String?> = emptyMap(),
    val signos: List<String> = emptyList(),
)

/** Dos números crudos: donante (área, % extraíble) o receptor (área, densidad objetivo). */
data class ParCrudo(val area: String?, val segundo: String?)

/** Lo que se manda a la API (y lo que valida el formulario). Gemelo de `BorradorTricoscopia`. */
data class BorradorTricoscopia(
    val pacienteId: String,
    val fecha: String? = null,
    val citaId: String? = null,
    val tratamientoId: String? = null,
    val escala: String? = null,
    val grado: String? = null,
    val zonas: List<ZonaCruda> = emptyList(),
    val donante: ParCrudo? = null,
    val receptor: ParCrudo? = null,
    val observaciones: String? = null,
    val visiblePaciente: Boolean = true,
)

// ─── Números ─────────────────────────────────────────────────────────────────

/** `Math.round(n * 10^d) / 10^d` de JS (la mitad sube). */
fun redondearTrico(n: Double, d: Int): Double {
    val p = 10.0.pow(d)
    return floor(n * p + 0.5) / p
}

/** Como `String(n)` de JS: 120.0 → "120", 120.5 → "120.5". */
fun numTrico(v: Double): String =
    if (v == floor(v) && abs(v) < 1e15) v.toLong().toString() else v.toString()

/** `toLocaleString('es-PE')` de un entero: 12000 → "12,000". */
fun milesTrico(n: Number): String {
    val r = floor(n.toDouble() + 0.5).toLong()
    val s = abs(r).toString().reversed().chunked(3).joinToString(",").reversed()
    return if (r < 0) "-$s" else s
}

private val RE_NUM = Regex("""^-?\d+(\.\d+)?$""")
private val RE_FECHA = Regex("""^\d{4}-\d{2}-\d{2}$""")

/**
 * Número de un input o de la base: acepta "12,5" (teclado decimal del celular
 * en español). null = vacío; NaN = escribió algo que no es número.
 */
fun aNumeroTrico(v: String?): Double? {
    if (v == null) return null
    val s = v.trim().replaceFirst(',', '.')
    if (s.isEmpty()) return null
    return if (RE_NUM.matches(s)) s.toDouble() else Double.NaN
}

/** Pelos por UF a partir de densidad ÷ UF/cm² (null si no se puede o queda fuera de rango). */
fun pelosPorUfCalculado(densidad: Double?, ufCm2: Double?): Double? {
    if (densidad == null || ufCm2 == null || !(ufCm2 > 0) || !(densidad >= 0)) return null
    val r = redondearTrico(densidad / ufCm2, 2)
    return if (r <= MetricaZona.PELOS_POR_UF.max) r else null
}

// ─── Lectura y validación ────────────────────────────────────────────────────

data class LecturaZonas(val zonas: List<ZonaMedida>, val errores: List<String>)

/**
 * Zonas crudas → zonas limpias + errores (gemelo de `leerZonas`). Se descartan
 * las zonas sin ningún dato; los signos desconocidos o repetidos se quitan.
 * Pelos/UF se guarda SOLO si se midió: el calculado no se congela en la base.
 */
fun leerZonas(raw: List<ZonaCruda>?): LecturaZonas {
    val errores = mutableListOf<String>()
    if (raw == null) return LecturaZonas(emptyList(), errores)
    val vistas = mutableSetOf<String>()
    val zonas = mutableListOf<ZonaMedida>()
    for (item in raw) {
        val id = item.zona.trim()
        val zc = ZonaCapilar.de(id)
        if (zc == null) { errores += "Zona desconocida: «${id.ifEmpty { "—" }}»."; continue }
        if (!vistas.add(id)) { errores += "${zc.nombre}: está repetida."; continue }
        var z = ZonaMedida(zc)
        for (m in MetricaZona.entries) {
            val n = aNumeroTrico(item.valores[m]) ?: continue
            if (n.isNaN()) { errores += "${zc.nombre} · ${m.nombre}: no es un número."; continue }
            if (n < m.min || n > m.max) {
                errores += "${zc.nombre} · ${m.nombre}: fuera de rango (${numTrico(m.min)}–${numTrico(m.max)}${if (m.unidad.isNotEmpty()) " ${m.unidad}" else ""})."
                continue
            }
            z = z.con(m, redondearTrico(n, m.decimales))
        }
        // Cada UF tiene al menos un pelo: menos pelos/cm² que UF/cm² es un error de tipeo (columnas cruzadas).
        if (z.densidad != null && z.ufCm2 != null && z.densidad < z.ufCm2) {
            errores += "${zc.nombre}: la densidad (${numTrico(z.densidad)} pelos/cm²) no puede ser menor que las UF/cm² (${numTrico(z.ufCm2)})."
        }
        z = z.copy(signos = item.signos.filter { it in IDS_SIGNO }.distinct().take(LimitesTricoscopia.SIGNOS))
        if (tieneDatos(z)) zonas += z
    }
    // Siempre en el orden anatómico (frontal → temporal).
    return LecturaZonas(zonas.sortedBy { it.zona.ordinal }, errores)
}

/** ¿La zona tiene algo anotado (una medición o un signo)? */
fun tieneDatos(z: ZonaMedida): Boolean = MetricaZona.entries.any { z.valor(it) != null } || z.signos.isNotEmpty()
private fun tieneMedicion(z: ZonaMedida) = MetricaZona.entries.any { z.valor(it) != null }

/** Pelos/UF de una zona: el medido o, si no se midió, el calculado (`calculado` = mostrar con ≈). */
data class PelosPorUf(val valor: Double?, val calculado: Boolean)

fun pelosPorUfDe(densidad: Double?, ufCm2: Double?, pelosPorUf: Double?): PelosPorUf {
    if (pelosPorUf != null) return PelosPorUf(pelosPorUf, false)
    val c = pelosPorUfCalculado(densidad, ufCm2)
    return PelosPorUf(c, c != null)
}
fun pelosPorUfDe(z: ZonaMedida): PelosPorUf = pelosPorUfDe(z.densidad, z.ufCm2, z.pelosPorUf)

/** El valor de una medición para mostrar ("2.5", "≈2.5" si es pelos/UF calculado, o "—"). */
fun textoMedicion(z: ZonaMedida, m: MetricaZona): String {
    if (m == MetricaZona.PELOS_POR_UF) {
        val p = pelosPorUfDe(z)
        return if (p.valor == null) "—" else (if (p.calculado) "≈" else "") + numTrico(p.valor)
    }
    return z.valor(m)?.let(::numTrico) ?: "—"
}

private data class LecturaPar(val a: Double?, val b: Double?, val errores: List<String>) {
    val vacio get() = a == null && b == null
}

private fun leerPar(
    raw: ParCrudo?, etiqueta: String,
    rangoA: Pair<Double, Double>, nombreA: String, rangoB: Pair<Double, Double>, nombreB: String,
): LecturaPar {
    if (raw == null) return LecturaPar(null, null, emptyList())
    val errores = mutableListOf<String>()
    fun uno(s: String?, r: Pair<Double, Double>, nombre: String): Double? {
        val n = aNumeroTrico(s) ?: return null
        if (n.isNaN()) { errores += "$etiqueta · $nombre: no es un número."; return null }
        if (n < r.first || n > r.second) { errores += "$etiqueta · $nombre: fuera de rango (${numTrico(r.first)}–${numTrico(r.second)})."; return null }
        return redondearTrico(n, 1)
    }
    val a = uno(raw.area, rangoA, nombreA)
    val b = uno(raw.segundo, rangoB, nombreB)
    return LecturaPar(a, b, errores)
}

data class LecturaDonante(val donante: DonanteTrico?, val errores: List<String>)
data class LecturaReceptor(val receptor: ReceptorTrico?, val errores: List<String>)

fun leerDonante(raw: ParCrudo?): LecturaDonante {
    val r = leerPar(raw, "Zona donante", LimitesTricoscopia.DONANTE_AREA, "área (cm²)", LimitesTricoscopia.EXTRAIBLE, "% extraíble")
    return LecturaDonante(if (r.vacio) null else DonanteTrico(r.a, r.b), r.errores)
}

fun leerReceptor(raw: ParCrudo?): LecturaReceptor {
    val r = leerPar(raw, "Zona receptora", LimitesTricoscopia.RECEPTOR_AREA, "área (cm²)",
        LimitesTricoscopia.DENSIDAD_OBJETIVO, "densidad objetivo (UF/cm²)")
    return LecturaReceptor(if (r.vacio) null else ReceptorTrico(r.a, r.b), r.errores)
}

/** ¿Este grado es válido para esta escala? (otro: cualquier texto corto). */
fun gradoValido(escala: String?, grado: String?): Boolean {
    val g = grado.orEmpty().trim()
    if (g.isEmpty()) return true
    if (g.length > LimitesTricoscopia.GRADO) return false
    val e = escalaDe(escala) ?: return false // grado sin escala
    return e.id == "otro" || g in e.grados
}

/** Errores para mostrar (vacío = válido). Con [hoy] (zona de la clínica), la fecha no puede ser futura. */
fun validarTricoscopia(b: BorradorTricoscopia, hoy: String? = null): List<String> {
    val e = mutableListOf<String>()
    if (b.pacienteId.isBlank()) e += "Falta el paciente."
    val fecha = b.fecha
    if (!fecha.isNullOrEmpty() && !RE_FECHA.matches(fecha)) e += "Fecha inválida."
    else if (!fecha.isNullOrEmpty() && hoy != null && fecha > hoy) e += "La fecha no puede ser futura."
    val escala = b.escala.orEmpty().trim()
    val grado = b.grado.orEmpty().trim()
    when {
        escala.isNotEmpty() && escalaDe(escala) == null -> e += "Escala de clasificación desconocida."
        escala == "otro" && grado.isEmpty() -> e += "Escribe qué patrón es (escala \"Otro\")."
        !gradoValido(escala.ifEmpty { null }, b.grado) -> e +=
            if (escala.isNotEmpty()) "El grado «$grado» no corresponde a la escala ${escalaDe(escala)!!.nombre}." else "Elige la escala del grado."
    }
    if (b.observaciones.orEmpty().trim().length > LimitesTricoscopia.OBSERVACIONES) {
        e += "Las observaciones son muy largas (máx. ${LimitesTricoscopia.OBSERVACIONES} caracteres)."
    }
    val z = leerZonas(b.zonas)
    e += z.errores
    e += leerDonante(b.donante).errores
    e += leerReceptor(b.receptor).errores
    if (escala.isEmpty() && z.zonas.none(::tieneMedicion) && z.errores.isEmpty()) {
        e += "Anota la clasificación o al menos una medición de alguna zona."
    }
    return e
}

/** ¿La fecha es posterior a hoy (de la clínica)? */
fun fechaTricoscopiaFutura(fecha: String, hoy: String): Boolean = fecha.take(10) > hoy.take(10)

/**
 * El cuerpo JSON del POST (nueva: con pacienteId) o del PATCH (con [id]): las
 * zonas YA limpias (`leerZonas`), en camelCase. Pelos/UF solo si se midió.
 */
fun cuerpoTricoscopia(b: BorradorTricoscopia, id: String? = null): JsonObject = buildJsonObject {
    if (id != null) put("id", id) else put("pacienteId", b.pacienteId)
    b.fecha?.takeIf { it.isNotBlank() }?.let { put("fecha", it) }
    if (id == null) b.citaId?.takeIf { it.isNotBlank() }?.let { put("citaId", it) }
    put("tratamientoId", b.tratamientoId?.takeIf { it.isNotBlank() })
    val escala = b.escala?.trim()?.takeIf { it.isNotEmpty() }
    put("escala", escala)
    put("grado", if (escala != null) b.grado?.trim()?.replace(Regex("\\s+"), " ")?.takeIf { it.isNotEmpty() } else null)
    put("zonas", buildJsonArray {
        leerZonas(b.zonas).zonas.forEach { z ->
            add(buildJsonObject {
                put("zona", z.zona.id)
                MetricaZona.entries.forEach { m -> put(m.camel, z.valor(m)) }
                put("signos", buildJsonArray { z.signos.forEach { add(JsonPrimitive(it)) } })
            })
        }
    })
    val d = leerDonante(b.donante).donante
    put("donante", d?.let { buildJsonObject { put("areaCm2", it.areaCm2); put("extraiblePct", it.extraiblePct) } } ?: JsonNull)
    val r = leerReceptor(b.receptor).receptor
    put("receptor", r?.let { buildJsonObject { put("areaCm2", it.areaCm2); put("densidadObjetivo", it.densidadObjetivo) } } ?: JsonNull)
    put("observaciones", b.observaciones?.trim()?.takeIf { it.isNotEmpty() })
    put("visiblePaciente", b.visiblePaciente)
}

// ─── Lectura del API ─────────────────────────────────────────────────────────

private fun JsonObject.txtTri(k: String): String? =
    (this[k] as? JsonPrimitive)?.takeIf { it !is JsonNull }?.content?.trim()?.takeIf { it.isNotEmpty() && it != "null" }

private fun JsonElement?.crudo(): String? =
    (this as? JsonPrimitive)?.takeIf { it !is JsonNull }?.content

/** Zonas del JSON (snake_case de la base o camelCase) → crudas. Lo que no es lista, nada. */
fun zonasCrudasDeJson(el: JsonElement?): List<ZonaCruda> = (el as? JsonArray).orEmpty().mapNotNull { item ->
    val o = item as? JsonObject ?: return@mapNotNull null
    ZonaCruda(
        zona = o.txtTri("zona").orEmpty(),
        valores = MetricaZona.entries.associateWith { m -> (if (m.id in o) o[m.id] else o[m.camel]).crudo() },
        signos = (o["signos"] as? JsonArray).orEmpty().mapNotNull { (it as? JsonPrimitive)?.takeIf { p -> p.isString }?.content },
    )
}

private fun parDeJson(el: JsonElement?, a: String, aCamel: String, b: String, bCamel: String): ParCrudo? {
    val o = el as? JsonObject ?: return null
    return ParCrudo((if (a in o) o[a] else o[aCamel]).crudo(), (if (b in o) o[b] else o[bCamel]).crudo())
}

/**
 * Fila del API → modelo, con los jsonb ya limpios (gemelo de `normalizarEvaluacion`):
 * lo que venga mal, fuera. Sin id o sin fecha se descarta.
 */
fun evaluacionDeJson(o: JsonObject): EvaluacionTricoscopica? {
    val id = o.txtTri("id") ?: return null
    val fecha = o.txtTri("fecha")?.take(10) ?: return null
    return EvaluacionTricoscopica(
        id = id,
        pacienteId = o.txtTri("paciente_id").orEmpty(),
        citaId = o.txtTri("cita_id"),
        tratamientoId = o.txtTri("tratamiento_id"),
        terapeutaId = o.txtTri("terapeuta_id"),
        fecha = fecha,
        escala = escalaDe(o.txtTri("escala"))?.id,
        grado = o.txtTri("grado"),
        zonas = leerZonas(zonasCrudasDeJson(o["zonas"])).zonas,
        donante = leerDonante(parDeJson(o["donante"], "area_cm2", "areaCm2", "extraible_pct", "extraiblePct")).donante,
        receptor = leerReceptor(parDeJson(o["receptor"], "area_cm2", "areaCm2", "densidad_objetivo", "densidadObjetivo")).receptor,
        observaciones = o.txtTri("observaciones"),
        visiblePaciente = (o["visible_paciente"] as? JsonPrimitive)?.content != "false",
        createdAt = o.txtTri("created_at"),
    )
}

// ─── Evolución ───────────────────────────────────────────────────────────────

/** Cronológico: fecha y, el mismo día, el orden en que se registraron. */
fun ordenarEvaluaciones(items: List<EvaluacionTricoscopica>?): List<EvaluacionTricoscopica> =
    items.orEmpty().sortedWith(compareBy<EvaluacionTricoscopica>({ it.fecha }, { it.createdAt.orEmpty() }))

fun zonaDe(ev: EvaluacionTricoscopica, zona: ZonaCapilar): ZonaMedida? = ev.zonas.firstOrNull { it.zona == zona }
fun valorZona(ev: EvaluacionTricoscopica, zona: ZonaCapilar, m: MetricaZona): Double? = zonaDe(ev, zona)?.valor(m)

data class Cambio(
    /** actual − referencia (en la unidad de la métrica; puntos porcentuales para %). */
    val delta: Double,
    /** Cambio relativo en % (null si la referencia es 0 o la métrica ya es un %). */
    val pct: Double?,
    /** "mejora" · "empeora" · "igual". */
    val tendencia: String,
    val desde: Double,
    val fechaDesde: String,
)

/** Cambio de una métrica respecto de una referencia, con su sentido clínico. */
fun cambioMetrica(actual: Double?, referencia: Double?, m: MetricaZona, fechaDesde: String): Cambio? {
    if (actual == null || referencia == null) return null
    val delta = redondearTrico(actual - referencia, m.decimales)
    val pct = if (m == MetricaZona.MINIATURIZACION || referencia == 0.0) null
    else redondearTrico(((actual - referencia) / referencia) * 100, 0)
    val tendencia = when {
        delta == 0.0 -> "igual"
        sign(delta).toInt() == m.mejora -> "mejora"
        else -> "empeora"
    }
    return Cambio(delta, pct, tendencia, referencia, fechaDesde)
}

data class EvolucionZona(
    val zona: ZonaCapilar,
    val actual: Map<MetricaZona, Double?>,
    /** Contra la evaluación ANTERIOR que midió eso en esta zona. */
    val vsAnterior: Map<MetricaZona, Cambio>,
    /** Contra la PRIMERA que lo midió. */
    val vsPrimera: Map<MetricaZona, Cambio>,
)

/**
 * Por zona (las que tiene la evaluación [idActual], por defecto la última): sus
 * valores y el cambio contra la anterior y la primera que midieron lo mismo.
 */
fun evolucionZonas(items: List<EvaluacionTricoscopica>?, idActual: String? = null): List<EvolucionZona> {
    val orden = ordenarEvaluaciones(items)
    val i = if (idActual != null) orden.indexOfFirst { it.id == idActual } else orden.size - 1
    if (i < 0) return emptyList()
    val actual = orden[i]
    val previas = orden.subList(0, i)
    return actual.zonas.filter(::tieneMedicion).map { z ->
        val vals = MetricaZona.entries.associateWith { z.valor(it) }
        val vsAnterior = mutableMapOf<MetricaZona, Cambio>()
        val vsPrimera = mutableMapOf<MetricaZona, Cambio>()
        for (m in MetricaZona.entries) {
            val con = previas.filter { valorZona(it, z.zona, m) != null }
            if (con.isEmpty() || vals[m] == null) continue
            val ant = con.last()
            val pri = con.first()
            val ca = cambioMetrica(vals[m], valorZona(ant, z.zona, m), m, ant.fecha)
            if (ca != null) vsAnterior[m] = ca
            if (pri !== ant) {
                cambioMetrica(vals[m], valorZona(pri, z.zona, m), m, pri.fecha)?.let { vsPrimera[m] = it }
            } else if (ca != null) vsPrimera[m] = ca
        }
        EvolucionZona(z.zona, vals, vsAnterior, vsPrimera)
    }
}

/** Puntos de una métrica en una zona, cronológicos. */
fun serieZona(items: List<EvaluacionTricoscopica>?, zona: ZonaCapilar, m: MetricaZona): List<PuntoSerieHc> =
    ordenarEvaluaciones(items).mapNotNull { ev -> valorZona(ev, zona, m)?.let { PuntoSerieHc(ev.fecha, it) } }

/** Zonas con al menos dos puntos de la métrica (las que tienen evolución que dibujar). */
fun zonasConEvolucion(items: List<EvaluacionTricoscopica>?, m: MetricaZona): List<ZonaCapilar> =
    ZonaCapilar.entries.filter { serieZona(items, it, m).size >= 2 }

/** Métricas que tienen evolución en alguna zona (en el orden de la grilla). */
fun metricasConEvolucion(items: List<EvaluacionTricoscopica>?): List<MetricaZona> =
    MetricaZona.entries.filter { zonasConEvolucion(items, it).isNotEmpty() }

/** "+12.5 pelos/cm²" · "−3 pp" · "sin cambio". */
fun textoCambio(c: Cambio?, m: MetricaZona): String {
    if (c == null) return "—"
    if (c.delta == 0.0) return "sin cambio"
    val unidad = if (m == MetricaZona.MINIATURIZACION) " pp" else if (m.unidad.isNotEmpty()) " ${m.unidad}" else ""
    val pct = c.pct?.let { " (${if (it > 0) "+" else ""}${numTrico(it)} %)" }.orEmpty()
    return "${if (c.delta > 0) "+" else "−"}${numTrico(abs(c.delta))}$unidad$pct"
}

/** Flecha del cambio: ↑ / ↓ / =. */
fun flechaCambio(c: Cambio?): String = when {
    c == null -> ""
    c.delta > 0 -> "↑"
    c.delta < 0 -> "↓"
    else -> "="
}

/** Valor con su unidad, o "—". */
fun textoValor(v: Double?, m: MetricaZona, conUnidad: Boolean = true): String {
    if (v == null) return "—"
    val u = if (conUnidad && m.unidad.isNotEmpty()) (if (m.unidad == "%") " %" else " ${m.unidad}") else ""
    return numTrico(v) + u
}

// ─── Lectura clínica (orientativa) ───────────────────────────────────────────

data class ComparacionDonante(val zona: ZonaCapilar, val densidadPct: Double?, val miniaturizacionPp: Double?)

/** Cada zona contra la donante (occipital): densidad en % de la occipital y miniaturización en puntos más. */
fun comparacionConDonante(ev: EvaluacionTricoscopica): List<ComparacionDonante> {
    val occ = zonaDe(ev, ZonaCapilar.OCCIPITAL) ?: return emptyList()
    return ev.zonas.filter { it.zona != ZonaCapilar.OCCIPITAL }.map { z ->
        ComparacionDonante(
            zona = z.zona,
            densidadPct = if (z.densidad != null && occ.densidad != null && occ.densidad != 0.0)
                redondearTrico((z.densidad / occ.densidad) * 100, 0) else null,
            miniaturizacionPp = if (z.miniaturizacionPct != null && occ.miniaturizacionPct != null)
                redondearTrico(z.miniaturizacionPct - occ.miniaturizacionPct, 1) else null,
        )
    }.filter { it.densidadPct != null || it.miniaturizacionPp != null }
}

/** "Frontal 60 % de la densidad +20 pp de miniaturización · …" (como la web). */
fun textoComparacionDonante(lista: List<ComparacionDonante>): String = lista.joinToString(" · ") { v ->
    listOfNotNull(
        v.zona.nombre,
        v.densidadPct?.let { "${numTrico(it)} % de la densidad" },
        v.miniaturizacionPp?.let { "${if (it > 0) "+" else ""}${numTrico(it)} pp de miniaturización" },
    ).joinToString(" ")
}

data class IndiciosAndrogeneticos(val mayores: List<String>, val menores: List<String>)

/** Hallazgos COMPATIBLES con patrón androgenético (Rakowska 2009). Orientativo: no es un diagnóstico. */
fun indiciosAndrogeneticos(ev: EvaluacionTricoscopica): IndiciosAndrogeneticos {
    val fr = zonaDe(ev, ZonaCapilar.FRONTAL)
    val occ = zonaDe(ev, ZonaCapilar.OCCIPITAL)
    val mayores = mutableListOf<String>()
    val menores = mutableListOf<String>()
    fr?.miniaturizacionPct?.let { if (it > 10) mayores += "Miniaturización frontal ${numTrico(it)} % (> 10 %)" }
    val dFr = fr?.diametroUm
    val dOcc = occ?.diametroUm
    if (dFr != null && dOcc != null && dFr < dOcc) {
        mayores += "Diámetro frontal menor que el occipital (${numTrico(dFr)} vs ${numTrico(dOcc)} µm)"
    }
    if (fr?.signos?.contains("peripilar") == true) menores += "Signo peripilar en la zona frontal"
    if (fr?.signos?.contains("vellos") == true) menores += "Vellos aumentados en la zona frontal"
    return IndiciosAndrogeneticos(mayores, menores)
}

/** La regla de Rakowska: dos criterios mayores, o uno mayor y dos menores (orientativo). */
fun cumpleCriteriosAndrogeneticos(r: IndiciosAndrogeneticos): Boolean =
    r.mayores.size >= 2 || (r.mayores.size >= 1 && r.menores.size >= 2)

// ─── Estimación para trasplante (ESTIMACIÓN: no reemplaza al cirujano) ───────

data class EntradaTrasplante(
    val ufCm2Donante: Double? = null,
    val densidadDonante: Double? = null,
    val pelosPorUfDonante: Double? = null,
    val areaDonanteCm2: Double? = null,
    val extraiblePct: Double? = null,
    val areaReceptoraCm2: Double? = null,
    val densidadObjetivo: Double? = null,
)

/** "alcanza" · "justo" · "no_alcanza" · "incompleto". */
data class ResultadoTrasplante(
    val nivel: String,
    val ufCm2Donante: Double?,
    /** "uf_cm2" · "densidad" · null. */
    val origenDonante: String?,
    val areaDonanteCm2: Double,
    val extraiblePct: Double,
    val densidadObjetivo: Double,
    val areaReceptoraCm2: Double?,
    val ufDonanteTotal: Long?,
    val ufDisponibles: Long?,
    val ufNecesarias: Long?,
    val balance: Long?,
    val cobertura: Double?,
    val densidadLograble: Double?,
    val pelosDisponibles: Long?,
    val faltan: List<String>,
    val avisos: List<String>,
    val mensaje: String,
)

/**
 * UF disponibles = ⌊UF/cm² donante × área donante × % extraíble⌋;
 * UF necesarias = ⌈área receptora × densidad objetivo⌉. Alcanza con margen si
 * las necesarias son ≤ 80 % de las disponibles. Por defecto: 150 cm², 25 %, 35 UF/cm².
 */
fun estimarTrasplante(e: EntradaTrasplante): ResultadoTrasplante {
    fun pos(v: Double?) = v?.takeIf { it.isFinite() && it > 0 }
    val areaDonanteCm2 = pos(e.areaDonanteCm2) ?: TrasplanteDefecto.AREA_DONANTE_CM2
    val extraiblePct = pos(e.extraiblePct) ?: TrasplanteDefecto.EXTRAIBLE_PCT
    val densidadObjetivo = pos(e.densidadObjetivo) ?: TrasplanteDefecto.DENSIDAD_OBJETIVO
    val areaReceptoraCm2 = pos(e.areaReceptoraCm2)
    val pelosUf = pos(e.pelosPorUfDonante)

    var ufCm2Donante = pos(e.ufCm2Donante)
    var origenDonante: String? = if (ufCm2Donante != null) "uf_cm2" else null
    val densDon = pos(e.densidadDonante)
    if (ufCm2Donante == null && densDon != null && pelosUf != null) {
        ufCm2Donante = redondearTrico(densDon / pelosUf, 1)
        origenDonante = "densidad"
    }

    val faltan = mutableListOf<String>()
    if (ufCm2Donante == null) faltan += "UF/cm² de la zona occipital (o su densidad y pelos por UF)"
    if (areaReceptoraCm2 == null) faltan += "el área receptora (cm²)"

    val avisos = mutableListOf<String>()
    if (extraiblePct > EXTRAIBLE_SEGURO_MAX) {
        avisos += "Extraer más del ${numTrico(EXTRAIBLE_SEGURO_MAX)} % de la zona donante aumenta el riesgo de que quede rala."
    }

    val ufDonanteTotal = ufCm2Donante?.let { floor(it * areaDonanteCm2 + 0.5).toLong() }
    val ufDisponibles = ufCm2Donante?.let { floor(it * areaDonanteCm2 * extraiblePct / 100).toLong() }
    val ufNecesarias = areaReceptoraCm2?.let { ceil(it * densidadObjetivo).toLong() }
    val ambos = ufDisponibles != null && ufNecesarias != null
    val balance = if (ambos) ufDisponibles!! - ufNecesarias!! else null
    val cobertura = if (ambos && ufNecesarias!! > 0) redondearTrico(ufDisponibles!!.toDouble() / ufNecesarias, 2) else null
    val densidadLograble = if (ufDisponibles != null && areaReceptoraCm2 != null)
        redondearTrico(ufDisponibles / areaReceptoraCm2, 1) else null
    val pelosDisponibles = if (ufDisponibles != null && pelosUf != null) floor(ufDisponibles * pelosUf + 0.5).toLong() else null

    var nivel = "incompleto"
    var mensaje = "Para estimar falta: ${faltan.joinToString(" y ")}."
    if (ambos) {
        val nec = ufNecesarias!!
        val disp = ufDisponibles!!
        if (nec <= disp * 0.8) {
            nivel = "alcanza"
            mensaje = "Alcanza con margen: se necesitarían ~${milesTrico(nec)} UF de ~${milesTrico(disp)} disponibles; queda reserva para una segunda sesión o para la progresión de la caída."
        } else if (nec <= disp) {
            nivel = "justo"
            mensaje = "Alcanza justo: ~${milesTrico(nec)} UF de ~${milesTrico(disp)} disponibles. Sin reserva para una segunda sesión ni para la progresión de la caída."
        } else {
            nivel = "no_alcanza"
            mensaje = "No alcanza: faltan ~${milesTrico(nec - disp)} UF. Con lo disponible se lograrían ~${densidadLograble?.let(::numTrico)} UF/cm² en ${numTrico(areaReceptoraCm2!!)} cm². Considerar una densidad objetivo menor, priorizar la zona frontal, fuentes adicionales (barba/cuerpo) o tratamiento médico."
        }
    }
    return ResultadoTrasplante(
        nivel, ufCm2Donante, origenDonante, areaDonanteCm2, extraiblePct, densidadObjetivo, areaReceptoraCm2,
        ufDonanteTotal, ufDisponibles, ufNecesarias, balance, cobertura, densidadLograble, pelosDisponibles,
        faltan, avisos, mensaje,
    )
}

/** La estimación de una evaluación guardada (zona occipital + donante/receptor). null si no se cargó nada de trasplante. */
fun trasplanteDeEvaluacion(ev: EvaluacionTricoscopica): ResultadoTrasplante? {
    if (ev.receptor == null && ev.donante == null) return null
    val occ = zonaDe(ev, ZonaCapilar.OCCIPITAL)
    return estimarTrasplante(EntradaTrasplante(
        ufCm2Donante = occ?.ufCm2, densidadDonante = occ?.densidad,
        pelosPorUfDonante = occ?.let { pelosPorUfDe(it).valor },
        areaDonanteCm2 = ev.donante?.areaCm2, extraiblePct = ev.donante?.extraiblePct,
        areaReceptoraCm2 = ev.receptor?.areaCm2, densidadObjetivo = ev.receptor?.densidadObjetivo,
    ))
}

/** "alcanza con margen" · "alcanza justo" · "no alcanza". */
fun textoNivelTrasplante(nivel: String): String = when (nivel) {
    "alcanza" -> "alcanza con margen"
    "justo" -> "alcanza justo"
    "no_alcanza" -> "no alcanza"
    else -> "faltan datos"
}

// ─── Formulario ──────────────────────────────────────────────────────────────

/**
 * Lo que se copia COMO VALOR de la visita anterior: la clasificación y la zona
 * donante / receptora. Las mediciones NO (fingirían una evolución "sin cambio"):
 * salen en gris con [referenciaCasilla]. Los signos tampoco: se miran de nuevo.
 */
data class CopiaAnterior(val escala: String?, val grado: String?, val donante: DonanteTrico?, val receptor: ReceptorTrico?)

fun copiaDeAnterior(ev: EvaluacionTricoscopica): CopiaAnterior = CopiaAnterior(ev.escala, ev.grado, ev.donante, ev.receptor)

data class ReferenciaCasilla(val texto: String, /** "calculado" · "anterior" · "ejemplo" */ val tipo: String)

/**
 * La referencia gris de cada casilla: pelos/UF calculado (≈2.5) si hay densidad
 * y UF/cm²; si no, el valor de la visita anterior ("ant. 120"); si no, el ejemplo.
 */
fun referenciaCasilla(m: MetricaZona, densidad: Double?, ufCm2: Double?, anterior: ZonaMedida?): ReferenciaCasilla {
    if (m == MetricaZona.PELOS_POR_UF) {
        pelosPorUfCalculado(densidad, ufCm2)?.let { return ReferenciaCasilla("≈${numTrico(it)}", "calculado") }
    }
    anterior?.valor(m)?.let { return ReferenciaCasilla("ant. ${numTrico(it)}", "anterior") }
    return ReferenciaCasilla(m.placeholder, "ejemplo")
}

/** La escala sugerida para una visita nueva: la de la última evaluación que la tenga. */
fun escalaSugerida(items: List<EvaluacionTricoscopica>?): String? =
    ordenarEvaluaciones(items).lastOrNull { it.escala != null }?.escala

/** Texto de un número para la casilla del formulario ("" si no hay). */
fun textoCampoTrico(v: Double?): String = v?.let(::numTrico).orEmpty()

/**
 * Pelos/UF guardado igual al calculado (densidad ÷ UF/cm²) se deja vacío al
 * editar: es la sugerencia gris, y así corregir la densidad no arrastra un valor viejo.
 */
fun pelosPorUfParaEditar(z: ZonaMedida): Double? =
    if (z.pelosPorUf != null && z.pelosPorUf == pelosPorUfCalculado(z.densidad, z.ufCm2)) null else z.pelosPorUf

/**
 * Donante y receptor del formulario como los manda la web: con área receptora
 * se guardan con sus parámetros (los por defecto incluidos), así si mañana
 * cambian, este registro no se mueve.
 */
fun paresDelFormulario(
    donanteArea: String, extraible: String, receptorArea: String, densidadObjetivo: String,
): Pair<ParCrudo?, ParCrudo?> {
    val hayReceptor = receptorArea.isNotBlank()
    val hayDonante = hayReceptor || donanteArea.isNotBlank() || extraible.isNotBlank()
    val donante = if (hayDonante) ParCrudo(
        donanteArea.trim().ifEmpty { numTrico(TrasplanteDefecto.AREA_DONANTE_CM2) },
        extraible.trim().ifEmpty { numTrico(TrasplanteDefecto.EXTRAIBLE_PCT) },
    ) else null
    val receptor = if (hayReceptor) ParCrudo(
        receptorArea, densidadObjetivo.trim().ifEmpty { numTrico(TrasplanteDefecto.DENSIDAD_OBJETIVO) },
    ) else null
    return donante to receptor
}

/** Errores de la base / del servidor → texto para el usuario (gemelo de `mensajeErrorTricoscopia`). */
fun mensajeErrorTricoscopia(msg: String?): String {
    val m = msg.orEmpty()
    return when {
        "TRICOSCOPIA_PACIENTE_INVALIDO" in m -> "El paciente no es de esta clínica."
        "TRICOSCOPIA_CITA_INVALIDA" in m -> "La cita no es de este paciente."
        "TRICOSCOPIA_TRATAMIENTO_INVALIDO" in m -> "El tratamiento no es de este paciente."
        "TRICOSCOPIA_PROFESIONAL_INVALIDO" in m -> "El profesional no es de esta clínica."
        "TRICOSCOPIA_INMUTABLE" in m -> "No se puede cambiar el paciente de una evaluación."
        "evaluaciones_tricoscopicas_" in m && "check" in m -> "Hay un valor fuera de rango. Revisa las mediciones."
        else -> "No se pudo guardar. Intenta de nuevo."
    }
}
