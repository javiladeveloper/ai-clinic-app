package pe.saniape.app.data.staff

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject

// ─────────────────────────────────────────────────────────────────────────────
// OFTALMOLOGÍA (2026-10-30, Dr. Santiago) — GEMELO PURO de lib/oftalmologia.ts.
//
// El examen oftalmológico estructurado de la consulta y la receta de lentes que
// sale de él. Se guarda en `atenciones_clinicas.examen_oftalmologico` (jsonb)
// por /api/staff/atencion/guardar (lib/atencion-app-borrador.ts lo acepta y el
// servidor lo vuelve a normalizar con sus rangos). Mismas claves que la web.
//
// Reglas del doctor: todo por ojo (OD/OI); esfera ±25 D de 0.25; cilindro −10..0
// de 0.25; eje 0°..180° de 5°; ADD +0.25..+4; PIO 0..70; DIP "lejos/cerca"
// (62/60); AV en Snellen 20/x + CD, MM, PL, NPL; se imprime SOLO la receta de
// lentes (de la refracción subjetiva), por /api/staff/imprimir/lentes/{citaId}.
// ─────────────────────────────────────────────────────────────────────────────

@Serializable data class OjosTxt(val od: String? = null, val oi: String? = null)
@Serializable data class OjosNum(val od: Double? = null, val oi: Double? = null)
@Serializable data class Refraccion(val esf: Double? = null, val cil: Double? = null, val eje: Double? = null)
@Serializable data class RefraccionAv(val esf: Double? = null, val cil: Double? = null, val eje: Double? = null, val av: String? = null) {
    val ref: Refraccion get() = Refraccion(esf, cil, eje)
}

@Serializable data class AvOftalmo(
    val lejosSc: OjosTxt = OjosTxt(), val lejosCc: OjosTxt = OjosTxt(),
    val cercaSc: OjosTxt = OjosTxt(), val cercaCc: OjosTxt = OjosTxt(),
    /** Agujero estenopeico (de lejos). */
    val ae: OjosTxt = OjosTxt(),
)
@Serializable data class PioOftalmo(val od: Double? = null, val oi: Double? = null, val metodo: String? = null, val hora: String? = null)
/** Lensometría: los lentes que trae puestos. */
@Serializable data class LensoOftalmo(val od: Refraccion = Refraccion(), val oi: Refraccion = Refraccion(), val addCerca: Double? = null)
@Serializable data class AutorrefOftalmo(val od: Refraccion = Refraccion(), val oi: Refraccion = Refraccion())
/** Refracción subjetiva: la que va a la receta de lentes. */
@Serializable data class SubjetivaOftalmo(
    val od: RefraccionAv = RefraccionAv(), val oi: RefraccionAv = RefraccionAv(),
    val addIntermedia: Double? = null, val addCerca: Double? = null,
    val dipLejos: Double? = null, val dipCerca: Double? = null,
)
@Serializable data class AnexosOftalmo(
    val orbita: OjosTxt = OjosTxt(), val lagrimal: OjosTxt = OjosTxt(),
    val but: OjosNum = OjosNum(), val schirmer: OjosNum = OjosNum(),
)
/** Grado de apertura angular (Shaffer) 0 a 4 por cuadrante. */
@Serializable data class GradoGonio(val superior: Int? = null, val temporal: Int? = null, val nasal: Int? = null, val inferior: Int? = null) {
    operator fun get(c: String): Int? = when (c) { "superior" -> superior; "temporal" -> temporal; "nasal" -> nasal; else -> inferior }
    fun con(c: String, v: Int?): GradoGonio = when (c) {
        "superior" -> copy(superior = v); "temporal" -> copy(temporal = v); "nasal" -> copy(nasal = v); else -> copy(inferior = v)
    }
}
@Serializable data class OjosGrado(val od: GradoGonio = GradoGonio(), val oi: GradoGonio = GradoGonio())
@Serializable data class GonioOftalmo(
    val grado: OjosGrado = OjosGrado(),
    val hallazgos: Map<String, OjosTxt> = CUADRANTES.associateWith { OjosTxt() },
    val conclusion: String? = null,
    val adicional: String? = null,
)

@Serializable data class ExamenOftalmo(
    val v: Int = 1,
    val av: AvOftalmo = AvOftalmo(),
    val pio: PioOftalmo = PioOftalmo(),
    val lenso: LensoOftalmo = LensoOftalmo(),
    val autorref: AutorrefOftalmo = AutorrefOftalmo(),
    val subjetiva: SubjetivaOftalmo = SubjetivaOftalmo(),
    val indicaciones: String? = null,
    val anexos: AnexosOftalmo = AnexosOftalmo(),
    val bio: Map<String, OjosTxt> = BIOMICROSCOPIA.associate { it.first to OjosTxt() },
    val fondo: Map<String, OjosTxt> = FONDO_OJO.associate { it.first to OjosTxt() },
    val gonio: GonioOftalmo = GonioOftalmo(),
)

val CUADRANTES = listOf("superior", "temporal", "nasal", "inferior")
val BIOMICROSCOPIA = listOf(
    "conjuntiva" to "Conjuntiva", "esclera" to "Esclera", "cornea" to "Córnea", "camara" to "Cámara anterior",
    "iris" to "Iris", "pupila" to "Pupila", "cristalino" to "Cristalino",
)
val FONDO_OJO = listOf(
    "vitreo" to "Vítreo", "papila" to "Papila óptica", "macula" to "Mácula", "vasos" to "Vasos retinianos", "periferia" to "Retina periférica",
)
val ANEXOS = listOf("orbita" to "Órbita y párpados", "lagrimal" to "Sistema lagrimal")

fun examenVacio(): ExamenOftalmo = ExamenOftalmo()

// ── Rangos (los del doctor) ──────────────────────────────────────────────────

data class Rango(val min: Double, val max: Double, val paso: Double, val decimales: Int, val unidad: String)

object RANGOS {
    val esf = Rango(-25.0, 25.0, 0.25, 2, "D")
    val cil = Rango(-10.0, 0.0, 0.25, 2, "D")
    val eje = Rango(0.0, 180.0, 5.0, 0, "°")
    val add = Rango(0.25, 4.0, 0.25, 2, "D")
    val pio = Rango(0.0, 70.0, 1.0, 0, "mmHg")
    val dip = Rango(40.0, 80.0, 1.0, 0, "mm")
    val but = Rango(0.0, 60.0, 1.0, 0, "s")
    val schirmer = Rango(0.0, 35.0, 1.0, 0, "mm")
    val gonio = Rango(0.0, 4.0, 1.0, 0, "")
}

// redondeoJs (Presupuestos.kt): Math.round de JS (la mitad hacia +∞), no el redondeo bancario de Kotlin.

private fun aDecimales(n: Double, d: Int): Double {
    val f = 10.0.pow(d)
    return redondeoJs(n * f) / f
}

/**
 * Un valor escrito → número dentro del rango y en su paso, o null. Acepta coma
 * decimal y el "+" ("+2,00"). Fuera de rango → null (no se inventa un tope).
 */
fun valorEnRango(v: Any?, r: Rango): Double? {
    val n: Double = when (v) {
        null -> return null
        is Number -> v.toDouble()
        is String -> v.trim().replace(',', '.').removePrefix("+").takeIf { it.isNotEmpty() }?.toDoubleOrNull() ?: return null
        else -> return null
    }
    if (!n.isFinite()) return null
    val limpio = aDecimales(redondeoJs(n / r.paso) * r.paso, r.decimales)
    if (limpio < r.min - 1e-9 || limpio > r.max + 1e-9) return null
    return if (limpio == 0.0) 0.0 else limpio
}

/** Un paso arriba/abajo (las flechas ▲▼), sin salirse del rango. Vacío arranca en 0 (o el mínimo). */
fun pasoSiguiente(v: Double?, r: Rango, dir: Int): Double {
    val base = v ?: (if (r.min <= 0 && r.max >= 0) 0.0 else r.min)
    val n = if (v == null) base else base + dir * r.paso
    return aDecimales(min(r.max, max(r.min, n)), r.decimales)
}

/** n con [d] decimales fijos ("2.50", "-0.25", "62"). */
private fun fijoOft(n: Double, d: Int): String {
    val f = 10.0.pow(d)
    val entero = redondeoJs(abs(n) * f).toLong()
    val signo = if (n < 0 && entero != 0L) "-" else ""
    if (d == 0) return "$signo$entero"
    val div = f.toLong()
    return "$signo${entero / div}.${(entero % div).toString().padStart(d, '0')}"
}

/** Dioptrías como en la receta: "+0.00", "-0.50", "+2.00". */
fun dioptria(n: Double?): String {
    if (n == null || !n.isFinite()) return ""
    val t = fijoOft(n, 2)
    return if (t.startsWith("-")) t else "+$t"
}

/** Un número del examen para mostrar ("62", "+2.25"). */
fun mostrarNum(v: Double?, r: Rango, conSigno: Boolean): String =
    if (v == null) "" else if (conSigno) dioptria(v) else fijoOft(v, r.decimales)

// ── Agudeza visual ───────────────────────────────────────────────────────────

/** Snellen 20/x (de mejor a peor) y las que no son Snellen. */
val SNELLEN = listOf("20/10", "20/15", "20/20", "20/25", "20/30", "20/40", "20/50", "20/60", "20/70", "20/80", "20/100", "20/150", "20/200", "20/400")
val AV_BAJA = listOf(
    "CD" to "CD — cuenta dedos", "MM" to "MM — movimiento de manos", "PL" to "PL — percibe luz", "NPL" to "NPL — no percibe luz",
)
/** Letras de más o de menos en la línea ("20/20-2"). */
val AJUSTES_AV = listOf("-1", "-2", "-3", "+1", "+2", "+3")

private val RE_SNELLEN = Regex("^20/(\\d{2,3})([+-][1-4])?$")

/** Lo escrito → "20/20-2" | "CD" | … o null. */
fun normalizarAV(v: String?): String? {
    val t = v?.trim()?.uppercase()?.replace(Regex("\\s+"), "") ?: return null
    if (t.isEmpty()) return null
    if (AV_BAJA.any { it.first == t }) return t
    val m = RE_SNELLEN.matchEntire(t) ?: return null
    val base = "20/${m.groupValues[1]}"
    return if (base in SNELLEN) base + m.groupValues[2] else null
}

/** "20/20-2" → ("20/20", "-2"); "CD" → ("CD", ""). */
fun partesAV(v: String?): Pair<String, String> {
    val n = normalizarAV(v) ?: return "" to ""
    val m = RE_SNELLEN.matchEntire(n)
    return if (m != null) "20/${m.groupValues[1]}" to m.groupValues[2] else n to ""
}

// ── Textos "Normal" (atajo: se escribe solo lo anormal) ──────────────────────

val NORMAL_BIO = mapOf(
    "conjuntiva" to "Tranquila", "esclera" to "Blanca", "cornea" to "Transparente", "camara" to "Formada, sin células",
    "iris" to "Sin alteraciones", "pupila" to "Redonda, reactiva", "cristalino" to "Transparente",
)
val NORMAL_FONDO = mapOf(
    "vitreo" to "Transparente", "papila" to "Bordes nítidos, E/P 0.3", "macula" to "Brillo foveolar presente",
    "vasos" to "Relación A/V 2/3", "periferia" to "Retina aplicada",
)
val NORMAL_ANEXOS = mapOf("orbita" to "Sin alteraciones", "lagrimal" to "Permeable")

fun OjosTxt.de(ojo: String): String? = if (ojo == "od") od else oi
fun OjosTxt.con(ojo: String, v: String?): OjosTxt = if (ojo == "od") copy(od = v) else copy(oi = v)
fun OjosNum.de(ojo: String): Double? = if (ojo == "od") od else oi
fun OjosNum.con(ojo: String, v: Double?): OjosNum = if (ojo == "od") copy(od = v) else copy(oi = v)

/** "✓ Normal" de un ojo en un grupo de textos: llena solo lo vacío (lo anormal ya escrito manda). */
fun normalEnMapa(grupo: Map<String, OjosTxt>, textos: Map<String, String>, ojo: String): Map<String, OjosTxt> =
    grupo + textos.mapValues { (k, t) -> val o = grupo[k] ?: OjosTxt(); if (o.de(ojo).isNullOrBlank()) o.con(ojo, t) else o }

fun normalEnAnexos(a: AnexosOftalmo, ojo: String): AnexosOftalmo = a.copy(
    orbita = if (a.orbita.de(ojo).isNullOrBlank()) a.orbita.con(ojo, NORMAL_ANEXOS.getValue("orbita")) else a.orbita,
    lagrimal = if (a.lagrimal.de(ojo).isNullOrBlank()) a.lagrimal.con(ojo, NORMAL_ANEXOS.getValue("lagrimal")) else a.lagrimal,
)

/** ⇣ Copiar del autorrefractómetro / de la lensometría a la subjetiva (la AV de la subjetiva se respeta). */
fun copiarASubjetiva(e: ExamenOftalmo, desde: String): ExamenOftalmo {
    val (od, oi) = if (desde == "lenso") e.lenso.od to e.lenso.oi else e.autorref.od to e.autorref.oi
    val s = e.subjetiva
    return e.copy(subjetiva = s.copy(
        od = RefraccionAv(od.esf, od.cil, od.eje, s.od.av),
        oi = RefraccionAv(oi.esf, oi.cil, oi.eje, s.oi.av),
        addCerca = if (desde == "lenso" && e.lenso.addCerca != null && s.addCerca == null) e.lenso.addCerca else s.addCerca,
    ))
}

// ── Normalización ────────────────────────────────────────────────────────────

private val jsonOftalmo = Json { ignoreUnknownKeys = true; coerceInputValues = true; isLenient = true }

private fun texto(v: String?, tope: Int = 600): String? =
    v?.filter { it == '\t' || it == '\n' || it == '\r' || it >= ' ' }?.trim()?.take(tope)?.trim()?.takeIf { it.isNotEmpty() }

private fun textoOjos(o: OjosTxt) = OjosTxt(texto(o.od), texto(o.oi))
private fun numOjos(o: OjosNum, r: Rango) = OjosNum(valorEnRango(o.od, r), valorEnRango(o.oi, r))
private fun avOjos(o: OjosTxt) = OjosTxt(normalizarAV(o.od), normalizarAV(o.oi))
private fun refraccion(r: Refraccion) = Refraccion(valorEnRango(r.esf, RANGOS.esf), valorEnRango(r.cil, RANGOS.cil), valorEnRango(r.eje, RANGOS.eje))
private fun refAv(r: RefraccionAv) = refraccion(r.ref).let { RefraccionAv(it.esf, it.cil, it.eje, normalizarAV(r.av)) }
private fun grado(g: GradoGonio) = GradoGonio(
    valorEnRango(g.superior, RANGOS.gonio)?.toInt(), valorEnRango(g.temporal, RANGOS.gonio)?.toInt(),
    valorEnRango(g.nasal, RANGOS.gonio)?.toInt(), valorEnRango(g.inferior, RANGOS.gonio)?.toInt(),
)
private val HORA = Regex("^([01]\\d|2[0-3]):[0-5]\\d$")

/** La forma válida (rangos, pasos, textos recortados), con TODAS las claves. Nunca lanza. */
fun limpiarExamenOftalmo(e: ExamenOftalmo): ExamenOftalmo = ExamenOftalmo(
    v = 1,
    av = AvOftalmo(avOjos(e.av.lejosSc), avOjos(e.av.lejosCc), avOjos(e.av.cercaSc), avOjos(e.av.cercaCc), avOjos(e.av.ae)),
    pio = PioOftalmo(
        valorEnRango(e.pio.od, RANGOS.pio), valorEnRango(e.pio.oi, RANGOS.pio),
        texto(e.pio.metodo, 60), e.pio.hora?.takeIf { HORA.matches(it) },
    ),
    lenso = LensoOftalmo(refraccion(e.lenso.od), refraccion(e.lenso.oi), valorEnRango(e.lenso.addCerca, RANGOS.add)),
    autorref = AutorrefOftalmo(refraccion(e.autorref.od), refraccion(e.autorref.oi)),
    subjetiva = SubjetivaOftalmo(
        refAv(e.subjetiva.od), refAv(e.subjetiva.oi),
        valorEnRango(e.subjetiva.addIntermedia, RANGOS.add), valorEnRango(e.subjetiva.addCerca, RANGOS.add),
        valorEnRango(e.subjetiva.dipLejos, RANGOS.dip), valorEnRango(e.subjetiva.dipCerca, RANGOS.dip),
    ),
    indicaciones = texto(e.indicaciones, 2000),
    anexos = AnexosOftalmo(
        textoOjos(e.anexos.orbita), textoOjos(e.anexos.lagrimal),
        numOjos(e.anexos.but, RANGOS.but), numOjos(e.anexos.schirmer, RANGOS.schirmer),
    ),
    bio = BIOMICROSCOPIA.associate { (k, _) -> k to textoOjos(e.bio[k] ?: OjosTxt()) },
    fondo = FONDO_OJO.associate { (k, _) -> k to textoOjos(e.fondo[k] ?: OjosTxt()) },
    gonio = GonioOftalmo(
        OjosGrado(grado(e.gonio.grado.od), grado(e.gonio.grado.oi)),
        CUADRANTES.associateWith { textoOjos(e.gonio.hallazgos[it] ?: OjosTxt()) },
        texto(e.gonio.conclusion, 2000), texto(e.gonio.adicional, 2000),
    ),
)

/** El jsonb de la base → el examen (null si no hay o no tiene nada). Como normalizarExamenOftalmo de la web. */
fun examenOftalmoDesdeJson(el: JsonElement?): ExamenOftalmo? {
    if (el == null || el is JsonNull || el !is JsonObject) return null
    val e = runCatching { jsonOftalmo.decodeFromJsonElement(ExamenOftalmo.serializer(), el) }.getOrNull() ?: return null
    return limpiarExamenOftalmo(e).takeIf { examenOftalmoTieneContenido(it) }
}

/** El examen → JSON para `guardar` (el servidor lo vuelve a normalizar; uno vacío queda null). */
fun examenOftalmoAJson(e: ExamenOftalmo): JsonElement =
    jsonOftalmo.encodeToJsonElement(ExamenOftalmo.serializer(), limpiarExamenOftalmo(e))

/** ¿Hay algo anotado? (la versión `v` no cuenta). */
fun examenOftalmoTieneContenido(e: ExamenOftalmo?): Boolean {
    if (e == null) return false
    fun t(o: OjosTxt) = !o.od.isNullOrBlank() || !o.oi.isNullOrBlank()
    fun n(o: OjosNum) = o.od != null || o.oi != null
    fun r(x: Refraccion) = x.esf != null || x.cil != null || x.eje != null
    fun g(x: GradoGonio) = CUADRANTES.any { x[it] != null }
    val av = e.av
    return t(av.lejosSc) || t(av.lejosCc) || t(av.cercaSc) || t(av.cercaCc) || t(av.ae) ||
        e.pio.od != null || e.pio.oi != null || !e.pio.metodo.isNullOrBlank() || !e.pio.hora.isNullOrBlank() ||
        r(e.lenso.od) || r(e.lenso.oi) || e.lenso.addCerca != null ||
        r(e.autorref.od) || r(e.autorref.oi) ||
        r(e.subjetiva.od.ref) || r(e.subjetiva.oi.ref) || !e.subjetiva.od.av.isNullOrBlank() || !e.subjetiva.oi.av.isNullOrBlank() ||
        e.subjetiva.addIntermedia != null || e.subjetiva.addCerca != null || e.subjetiva.dipLejos != null || e.subjetiva.dipCerca != null ||
        !e.indicaciones.isNullOrBlank() ||
        t(e.anexos.orbita) || t(e.anexos.lagrimal) || n(e.anexos.but) || n(e.anexos.schirmer) ||
        e.bio.values.any(::t) || e.fondo.values.any(::t) ||
        g(e.gonio.grado.od) || g(e.gonio.grado.oi) || e.gonio.hallazgos.values.any(::t) ||
        !e.gonio.conclusion.isNullOrBlank() || !e.gonio.adicional.isNullOrBlank()
}

// ── Avisos (no bloquean el guardado) ─────────────────────────────────────────

/** Lo que conviene revisar antes de imprimir la receta. */
fun avisosExamenOftalmo(e: ExamenOftalmo): List<String> {
    val out = mutableListOf<String>()
    fun ref(nombre: String, r: Refraccion, ojo: String) {
        if (r.cil != null && r.cil != 0.0 && r.eje == null) out.add("$nombre $ojo: con cilindro falta el eje.")
    }
    for ((ojo, O) in listOf("od" to "OD", "oi" to "OI")) {
        ref("Lensometría", if (ojo == "od") e.lenso.od else e.lenso.oi, O)
        ref("Autorrefractómetro", if (ojo == "od") e.autorref.od else e.autorref.oi, O)
        ref("Refracción subjetiva", (if (ojo == "od") e.subjetiva.od else e.subjetiva.oi).ref, O)
    }
    val s = e.subjetiva
    if ((s.dipLejos == null) != (s.dipCerca == null)) out.add("DIP: se anotan las dos, lejos y cerca (ej. 62/60).")
    if (s.dipLejos != null && s.dipCerca != null && s.dipCerca > s.dipLejos) out.add("DIP: la de cerca suele ser menor que la de lejos.")
    return out
}

// ── Receta de lentes (lo único que se imprime) ───────────────────────────────

data class RecetaLentes(
    val od: RefraccionAv, val oi: RefraccionAv,
    /** "62/60" (lejos/cerca), o la que haya. */
    val dip: String?, val addCerca: Double?, val addIntermedia: Double?, val indicaciones: String?,
)

private fun enteroOft(n: Double): String = fijoOft(n, 0)

/** La receta sale de la refracción SUBJETIVA. null = no hay graduación que imprimir. */
fun recetaDeLentes(e: ExamenOftalmo?): RecetaLentes? {
    if (e == null) return null
    val s = e.subjetiva
    fun tiene(r: RefraccionAv) = r.esf != null || r.cil != null
    if (!tiene(s.od) && !tiene(s.oi) && s.addCerca == null && s.addIntermedia == null) return null
    val dip = when {
        s.dipLejos != null && s.dipCerca != null -> "${enteroOft(s.dipLejos)}/${enteroOft(s.dipCerca)}"
        s.dipLejos != null -> enteroOft(s.dipLejos)
        s.dipCerca != null -> enteroOft(s.dipCerca)
        else -> null
    }
    return RecetaLentes(s.od, s.oi, dip, s.addCerca, s.addIntermedia, e.indicaciones)
}

// ── Resumen en texto (historia clínica, informe) ─────────────────────────────

private fun numTxt(v: Double?): String? = v?.let { if (it % 1.0 == 0.0) enteroOft(it) else it.toString() }

private fun refTxt(r: Refraccion): String {
    if (r.esf == null && r.cil == null) return ""
    val cil = if (r.cil != null && r.cil != 0.0) " ${dioptria(r.cil)} x ${numTxt(r.eje) ?: "—"}°" else ""
    return "${dioptria(r.esf ?: 0.0)}$cil"
}

private fun par(od: String?, oi: String?, suf: String = ""): String =
    if (od == null && oi == null) "" else "OD ${od ?: "—"}$suf · OI ${oi ?: "—"}$suf"
private fun par(o: OjosTxt, suf: String = "") = par(o.od, o.oi, suf)
private fun par(o: OjosNum, suf: String = "") = par(numTxt(o.od), numTxt(o.oi), suf)

/** El examen en líneas legibles para la HC (sin lo vacío). */
fun lineasExamenOftalmo(e: ExamenOftalmo?): List<Pair<String, String>> {
    if (e == null) return emptyList()
    val out = mutableListOf<Pair<String, String>>()
    fun add(t: String, v: String) { if (v.isNotBlank()) out.add(t to v) }
    fun unir(vararg p: String) = p.filter { it.isNotBlank() }.joinToString(" | ")
    fun puntos(vararg p: String?) = p.filterNotNull().filter { it.isNotBlank() }.joinToString(" · ")
    val av = e.av
    add("AV lejos", unir(par(av.lejosSc).let { if (it.isNotEmpty()) "SC $it" else "" }, par(av.lejosCc).let { if (it.isNotEmpty()) "CC $it" else "" }, par(av.ae).let { if (it.isNotEmpty()) "AE $it" else "" }))
    add("AV cerca", unir(par(av.cercaSc).let { if (it.isNotEmpty()) "SC $it" else "" }, par(av.cercaCc).let { if (it.isNotEmpty()) "CC $it" else "" }))
    add("PIO", puntos(par(numTxt(e.pio.od), numTxt(e.pio.oi), " mmHg"), e.pio.metodo, e.pio.hora))
    fun refPar(od: Refraccion, oi: Refraccion) = puntos(refTxt(od).let { if (it.isNotEmpty()) "OD $it" else "" }, refTxt(oi).let { if (it.isNotEmpty()) "OI $it" else "" })
    add("Lensometría", puntos(refPar(e.lenso.od, e.lenso.oi), e.lenso.addCerca?.let { "ADD cerca ${dioptria(it)}" }))
    add("Autorrefractómetro", refPar(e.autorref.od, e.autorref.oi))
    val s = e.subjetiva
    val avRx = par(s.od.av, s.oi.av)
    add("Refracción subjetiva", puntos(
        refPar(s.od.ref, s.oi.ref), if (avRx.isNotEmpty()) "AV c/Rx $avRx" else "",
        s.addIntermedia?.let { "ADD intermedia ${dioptria(it)}" }, s.addCerca?.let { "ADD cerca ${dioptria(it)}" },
        recetaDeLentes(e)?.dip?.let { "DIP $it" },
    ))
    add("Indicaciones", e.indicaciones.orEmpty())
    add("Órbita y párpados", par(e.anexos.orbita))
    add("Sistema lagrimal", par(e.anexos.lagrimal))
    add("BUT", par(e.anexos.but, " s"))
    add("Test de Schirmer", par(e.anexos.schirmer, " mm"))
    for ((k, t) in BIOMICROSCOPIA) add(t, par(e.bio[k] ?: OjosTxt()))
    for ((k, t) in FONDO_OJO) add(t, par(e.fondo[k] ?: OjosTxt()))
    fun gTxt(x: GradoGonio) = if (CUADRANTES.any { x[it] != null })
        CUADRANTES.joinToString(" ") { "${it.first().uppercaseChar()} ${x[it] ?: "—"}" } else ""
    add("Gonioscopía (grado)", puntos(gTxt(e.gonio.grado.od).let { if (it.isNotEmpty()) "OD $it" else "" }, gTxt(e.gonio.grado.oi).let { if (it.isNotEmpty()) "OI $it" else "" }))
    for (c in CUADRANTES) add("Gonioscopía $c", par(e.gonio.hallazgos[c] ?: OjosTxt()))
    add("Conclusión de gonioscopía", e.gonio.conclusion.orEmpty())
    add("Información adicional", e.gonio.adicional.orEmpty())
    return out
}

fun textoExamenOftalmo(e: ExamenOftalmo?): String = lineasExamenOftalmo(e).joinToString("\n") { (t, v) -> "$t: $v" }

// ── Diagnósticos sugeridos por el examen (reglas FIJAS, no IA) ───────────────

data class SugerenciaDx(val codigo: String, val descripcion: String, /** D definitivo · P presuntivo */ val tipo: String, val motivo: String)

private val DESCRIPCION_CIE = mapOf(
    "H52.1" to "Miopía", "H52.0" to "Hipermetropía", "H52.2" to "Astigmatismo", "H52.4" to "Presbicia",
    "H52.3" to "Anisometropía y aniseiconía", "H40.0" to "Sospecha de glaucoma",
    "H04.1" to "Otros trastornos de la glándula lagrimal", "H25.9" to "Catarata senil, no especificada",
    "H10.9" to "Conjuntivitis, no especificada", "H11.0" to "Pterigión", "H00.0" to "Orzuelo y otras inflamaciones profundas del párpado",
    "H00.1" to "Calacio", "H01.0" to "Blefaritis", "H16.9" to "Queratitis, no especificada",
    "H35.3" to "Degeneración de la mácula y del polo posterior del ojo", "H43.3" to "Otras opacidades vítreas",
)

/** Equivalente esférico: esfera + cilindro / 2. */
fun equivalenteEsferico(r: Refraccion): Double? {
    if (r.esf == null && r.cil == null) return null
    return (r.esf ?: 0.0) + (r.cil ?: 0.0) / 2
}

private val RE_NEGACION = Regex("\\b(sin|no|niega|ausencia de|descarta)\\b")

/** ¿El texto MENCIONA el hallazgo (y no lo niega)? "sin opacidades", "no hay secreción" no cuentan. */
fun mencionaHallazgo(texto: String?, re: Regex): Boolean {
    for (frase in texto.orEmpty().lowercase().split(Regex("[.,;\\n]+"))) {
        val m = re.find(frase) ?: continue
        if (RE_NEGACION.containsMatchIn(frase.substring(0, m.range.first))) continue
        return true
    }
    return false
}

private fun signo(n: Double) = (if (n > 0) "+" else "") + fijoOft(n, 2)

fun sugerirDiagnosticos(e: ExamenOftalmo?, edad: Int? = null): List<SugerenciaDx> {
    if (e == null) return emptyList()
    val porCodigo = LinkedHashMap<String, SugerenciaDx>()
    fun sugerir(codigo: String, tipo: String, motivo: String) {
        val ya = porCodigo[codigo]
        if (ya != null) {
            if (!ya.motivo.contains(motivo)) porCodigo[codigo] = ya.copy(motivo = "${ya.motivo} · $motivo")
            return
        }
        porCodigo[codigo] = SugerenciaDx(codigo, DESCRIPCION_CIE[codigo] ?: codigo, tipo, motivo)
    }
    val ojos = listOf("od" to "OD", "oi" to "OI")

    // 1. Refracción: la subjetiva; si no hay, el autorrefractómetro.
    val sub = e.subjetiva.od.ref to e.subjetiva.oi.ref
    val auto = e.autorref.od to e.autorref.oi
    fun tiene(p: Pair<Refraccion, Refraccion>) = listOf(p.first, p.second).any { it.esf != null || it.cil != null }
    val fuente = if (tiene(sub)) sub else if (tiene(auto)) auto else null
    if (fuente != null) {
        val se = mutableMapOf<String, Double>()
        for ((k, O) in ojos) {
            val r = if (k == "od") fuente.first else fuente.second
            val eq = equivalenteEsferico(r) ?: continue
            se[k] = eq
            if (eq <= -0.5) sugerir("H52.1", "D", "$O ${signo(eq)} D")
            if (eq >= 0.5) sugerir("H52.0", "D", "$O ${signo(eq)} D")
            if (r.cil != null && abs(r.cil) >= 0.5) sugerir("H52.2", "D", "$O cil ${signo(r.cil)}${r.eje?.let { " x ${enteroOft(it)}°" } ?: ""}")
        }
        val od = se["od"]; val oi = se["oi"]
        if (od != null && oi != null && abs(od - oi) >= 2) sugerir("H52.3", "D", "diferencia de ${fijoOft(abs(od - oi), 2)} D entre ojos")
    }
    val add = e.subjetiva.addCerca ?: e.lenso.addCerca
    if (add != null && add >= 0.75 && (edad == null || edad >= 35)) sugerir("H52.4", "D", "ADD cerca ${signo(add)}")

    // 2. Glaucoma: PIO alta, ángulo estrecho o excavación grande.
    val reEp = Regex("e\\s*/\\s*p\\s*[:=]?\\s*0?[.,](\\d)", RegexOption.IGNORE_CASE)
    for ((k, O) in ojos) {
        val pio = if (k == "od") e.pio.od else e.pio.oi
        if (pio != null && pio > 21) sugerir("H40.0", "P", "PIO $O ${enteroOft(pio)} mmHg")
        val g = if (k == "od") e.gonio.grado.od else e.gonio.grado.oi
        val estrechos = CUADRANTES.count { c -> g[c]?.let { it <= 1 } == true }
        if (estrechos >= 3) sugerir("H40.0", "P", "ángulo estrecho $O ($estrechos cuadrantes grado 0–1)")
        val ep = reEp.find(e.fondo["papila"]?.de(k).orEmpty())
        if (ep != null && ep.groupValues[1].toInt() >= 6) sugerir("H40.0", "P", "excavación $O E/P 0.${ep.groupValues[1]}")
    }

    // 3. Ojo seco: BUT o Schirmer bajos.
    for ((k, O) in ojos) {
        val but = e.anexos.but.de(k); val sch = e.anexos.schirmer.de(k)
        if (but != null && but < 10) sugerir("H04.1", "P", "BUT $O ${enteroOft(but)} s")
        if (sch != null && sch < 10) sugerir("H04.1", "P", "Schirmer $O ${enteroOft(sch)} mm")
    }

    // 4. Lo escrito en la exploración (sin las frases que lo niegan).
    fun bio(k: String, ojo: String) = e.bio[k]?.de(ojo)
    fun fondo(k: String, ojo: String) = e.fondo[k]?.de(ojo)
    val reglas: List<Triple<String, Regex, (String) -> List<String?>>> = listOf(
        Triple("H25.9", Regex("catarat|opacidad|opaco|esclerosis nuclear")) { o -> listOf(bio("cristalino", o)) },
        Triple("H11.0", Regex("pterigi")) { o -> listOf(bio("conjuntiva", o), bio("cornea", o)) },
        Triple("H10.9", Regex("hiperemi|secreci|folicul|papilas conjuntivales|conjuntivitis")) { o -> listOf(bio("conjuntiva", o)) },
        Triple("H16.9", Regex("queratit|úlcera|ulcera|infiltrad")) { o -> listOf(bio("cornea", o)) },
        Triple("H00.0", Regex("orzuelo")) { o -> listOf(e.anexos.orbita.de(o)) },
        Triple("H00.1", Regex("chalaz|calaci")) { o -> listOf(e.anexos.orbita.de(o)) },
        Triple("H01.0", Regex("blefarit|costras en (las )?pesta")) { o -> listOf(e.anexos.orbita.de(o)) },
        Triple("H35.3", Regex("drusa|degeneraci[oó]n macular|dmae")) { o -> listOf(fondo("macula", o)) },
        Triple("H43.3", Regex("opacidad|miodesops|flotador|condensaci")) { o -> listOf(fondo("vitreo", o)) },
    )
    for ((codigo, re, textos) in reglas) {
        for ((k, O) in ojos) {
            if (textos(k).any { mencionaHallazgo(it, re) }) sugerir(codigo, "P", "$O: según la exploración")
        }
    }
    return porCodigo.values.toList()
}

// ── Listas de la consulta oftalmológica ──────────────────────────────────────

/** FRECUENTES_OFTALMOLOGIA de lib/cie10.ts (el buscador CIE-10 abre con estos). */
val FRECUENTES_OFTALMOLOGIA: List<Pair<String, String>> = listOf(
    "H52.1" to "Miopía",
    "H52.0" to "Hipermetropía",
    "H52.2" to "Astigmatismo",
    "H52.4" to "Presbicia",
    "H52.3" to "Anisometropía y aniseiconía",
    "H10.9" to "Conjuntivitis, no especificada",
    "H10.1" to "Conjuntivitis atópica aguda",
    "H04.1" to "Otros trastornos de la glándula lagrimal",
    "H11.0" to "Pterigión",
    "H00.0" to "Orzuelo y otras inflamaciones profundas del párpado",
    "H00.1" to "Calacio",
    "H01.0" to "Blefaritis",
    "H16.9" to "Queratitis, no especificada",
    "H25.9" to "Catarata senil, no especificada",
    "H40.0" to "Sospecha de glaucoma",
    "H40.1" to "Glaucoma primario de ángulo abierto",
    "H35.3" to "Degeneración de la mácula y del polo posterior del ojo",
    "H43.3" to "Otras opacidades vítreas",
    "H50.9" to "Estrabismo, no especificado",
    "H53.0" to "Ambliopía ex anopsia",
    "H57.1" to "Dolor ocular",
    "T15.9" to "Cuerpo extraño en parte externa del ojo, sitio no especificado",
)

/** EXAMENES_FRECUENTES_OFTALMOLOGIA de lib/atencion-medica.ts. */
val EXAMENES_FRECUENTES_OFTALMOLOGIA = listOf(
    "OCT de mácula", "OCT de nervio óptico", "Campimetría computarizada", "Paquimetría", "Topografía corneal",
    "Biometría ocular", "Retinografía", "Ecografía ocular (modo B)", "Angiografía con fluoresceína",
    "Curva de presión intraocular", "Recuento de células endoteliales", "Glucosa en ayunas", "Hemoglobina glicosilada (HbA1c)",
)

/** Métodos de tonometría (la lista del campo «Método» de la web). */
val METODOS_TONOMETRIA = listOf("Goldmann", "Aire (no contacto)", "Perkins", "iCare (rebote)", "Tonopen", "Digital")
