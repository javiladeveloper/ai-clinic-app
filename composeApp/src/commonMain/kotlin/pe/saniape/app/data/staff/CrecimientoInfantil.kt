package pe.saniape.app.data.staff

import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.plus
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.ln
import kotlin.math.pow
import kotlin.math.sqrt

/**
 * CRECIMIENTO INFANTIL (Pediatría, pestaña 👶 Crecimiento): z-score, percentil y
 * clasificación con los patrones de la OMS. Gemelo PURO de
 * `lib/crecimiento-infantil.ts` de la web: mismas tablas (CrecimientoOmsDatos,
 * generado desde la web), mismas fórmulas y redondeos, mismos textos.
 *
 *  · 0–5 años: WHO Child Growth Standards 2006 (tablas LMS por día).
 *  · 5–19 años: WHO Reference 2007 (tablas LMS por mes, interpoladas).
 *  · Igual que `anthro`/`anthroplus` de la OMS: edad en días redondeada; meses =
 *    días / 30,4375; z = ((y/M)^L − 1) / (L·S) y, en peso e IMC, el AJUSTE de la
 *    OMS para |z| > 3.
 *  · Clasificación: NTS N° 238-MINSA/DGIESP-2025 (Tabla N° 3). APOYO a la
 *    decisión clínica: se muestra, nunca bloquea nada.
 *
 * Las tablas se convierten a números SOLO al abrir la pestaña / el portal
 * ([TablasOms.cargar], perezoso): la ficha no paga nada si no se mira.
 */

/** Las tablas LMS (gemelas de `TablaOms` de la web). */
enum class TablaOms { WFA, LHFA, BFA, HCFA, WFL, WFH, WFA07, HFA07, BFA07 }

/** Una curva LMS por sexo: nodos ordenados por x (interpolación lineal). */
class CurvaLms(val x: DoubleArray, val l: DoubleArray, val m: DoubleArray, val s: DoubleArray) {
    val size: Int get() = x.size
}

/** Las 9 tablas × 2 sexos ya en números. */
class TablasOms internal constructor(private val curvas: Map<TablaOms, Pair<CurvaLms, CurvaLms>>) {
    /** [sexo]: "F" o "M" (cualquier otra cosa = masculino, como la web). */
    fun curva(tabla: TablaOms, sexo: String): CurvaLms = curvas.getValue(tabla).let { if (sexo == "F") it.second else it.first }

    companion object {
        private val perezosas by lazy {
            TablasOms(TablaOms.entries.associateWith { t ->
                parsearCurvaOms(CrecimientoOmsDatos.texto(t, false)) to parsearCurvaOms(CrecimientoOmsDatos.texto(t, true))
            })
        }

        /** Las tablas (se arman la primera vez que se piden; después, la misma instancia). */
        fun cargar(): TablasOms = perezosas
    }
}

/** "x,l,m,s;x,l,m,s;…" → curva. */
internal fun parsearCurvaOms(texto: String): CurvaLms {
    val nodos = texto.split(';').filter { it.isNotBlank() }
    val x = DoubleArray(nodos.size); val l = DoubleArray(nodos.size); val m = DoubleArray(nodos.size); val s = DoubleArray(nodos.size)
    nodos.forEachIndexed { i, n ->
        val p = n.split(',')
        x[i] = p[0].toDouble(); l[i] = p[1].toDouble(); m[i] = p[2].toDouble(); s[i] = p[3].toDouble()
    }
    return CurvaLms(x, l, m, s)
}

enum class IndicadorCrecimiento(val clave: String, val sigla: String, val nombre: String, val unidad: String) {
    PESO_EDAD("peso_edad", "P/E", "Peso para la edad", "kg"),
    TALLA_EDAD("talla_edad", "T/E", "Talla para la edad", "cm"),
    PESO_TALLA("peso_talla", "P/T", "Peso para la talla", "kg"),
    IMC_EDAD("imc_edad", "IMC/E", "IMC para la edad", "kg/m²"),
    PC_EDAD("pc_edad", "PC/E", "Perímetro cefálico para la edad", "cm");

    companion object {
        fun de(clave: String?): IndicadorCrecimiento? = entries.firstOrNull { it.clave == clave }
    }
}

/** Días por mes de la OMS (anthro: age_in_months = age_in_days / 30.4375). */
const val DIAS_POR_MES = 30.4375
/** Desde acá (60 meses) rige la referencia OMS 2007. */
const val DIAS_5_ANIOS = 60 * DIAS_POR_MES
/** Hasta acá llegan las curvas (19 años: la referencia OMS 2007 termina en 228 meses). */
const val MESES_MAX = 228

private fun r2(n: Double) = redondearJs(n * 100) / 100
private fun r1(n: Double) = redondearJs(n * 10) / 10

// ─── Edad ────────────────────────────────────────────────────────────────────

private val ISO_FECHA = Regex("""^\d{4}-\d{2}-\d{2}$""")
private fun diaEpoca(iso: String): Long? = runCatching { LocalDate.parse(iso.take(10)).toEpochDays().toLong() }.getOrNull()

/** Edad en días a una fecha ('YYYY-MM-DD'). null si falta algo o la fecha es antes de nacer. */
fun edadEnDias(fechaNacimiento: String?, fecha: String?): Int? {
    if (fechaNacimiento == null || fecha == null) return null
    if (!ISO_FECHA.matches(fechaNacimiento.take(10)) || !ISO_FECHA.matches(fecha.take(10))) return null
    val a = diaEpoca(fechaNacimiento) ?: return null
    val b = diaEpoca(fecha) ?: return null
    val d = b - a
    return if (d >= 0) d.toInt() else null
}

fun mesesDeDias(dias: Double): Double = dias / DIAS_POR_MES

private fun textoMeses(meses: Int): String {
    if (meses < 24) return "$meses ${if (meses == 1) "mes" else "meses"}"
    val anios = meses / 12
    val resto = meses % 12
    if (anios >= 6 || resto == 0) return "$anios años"
    return "$anios a $resto m"
}

/** "15 días", "3 meses", "1 a 4 m", "7 años" desde la edad en días (meses OMS de 30,4375 días). */
fun textoEdad(dias: Int?): String {
    if (dias == null || dias < 0) return "—"
    if (dias < 31) return "$dias ${if (dias == 1) "día" else "días"}"
    return textoMeses(floor(mesesDeDias(dias.toDouble())).toInt())
}

/** La edad que dice la gente, por CALENDARIO: el día que cumple un año dice "12 meses". */
fun textoEdadEntre(fechaNacimiento: String?, fecha: String?): String {
    val dias = edadEnDias(fechaNacimiento, fecha) ?: return "—"
    if (dias < 31) return "$dias ${if (dias == 1) "día" else "días"}"
    return textoMeses(mesesCumplidos(fechaNacimiento, fecha)!!)
}

/**
 * Meses CUMPLIDOS por calendario (el día que cumple 2 años = 24). null si falta
 * una fecha o es antes de nacer. Un 29 de febrero cumple el 1 de marzo.
 */
fun mesesCumplidos(fechaNacimiento: String?, fecha: String?): Int? {
    if (edadEnDias(fechaNacimiento, fecha) == null) return null
    val (y1, m1, d1) = fechaNacimiento!!.take(10).split("-").map { it.toInt() }
    val (y2, m2, d2) = fecha!!.take(10).split("-").map { it.toInt() }
    return maxOf(0, (y2 - y1) * 12 + (m2 - m1) - (if (d2 < d1) 1 else 0))
}

// ─── Prematuridad: edad corregida ────────────────────────────────────────────
//
// Gemelo de la web (docs/superpowers/specs/2026-10-16-prematuridad-curvas-design.md):
//  · Edad corregida = edad cronológica − (40 semanas − EG al nacer); edad
//    postmenstrual (EPM) = EG al nacer + edad cronológica (AAP, Pediatrics 2004;114:1362).
//  · Solo si nació PREMATURO (EG < 37 semanas, OMS) y solo hasta que CUMPLE 2
//    años por calendario; desde ahí, la cronológica.
//  · EPM < 40 semanas = antes del término: los patrones OMS no aplican (van
//    INTERGROWTH-21st / Fenton, que no están acá). Ningún z-score: nunca uno falso.

/** Edad gestacional al nacer: semanas completas + días (0–6). */
data class EdadGestacional(val semanas: Int, val dias: Int? = null)

const val EG_MIN_SEMANAS = 22
const val EG_MAX_SEMANAS = 42
/** 40 semanas: el término. */
const val DIAS_TERMINO = 280
/** 37 semanas: por debajo, pretérmino (OMS). */
const val DIAS_PRETERMINO = 37 * 7
/** Se corrige hasta los 24 meses de edad cronológica. */
const val MESES_FIN_CORRECCION = 24

/** ¿Semanas 22–42 y días 0–6 (o sin días)? Gemelo de `egValida` / pacientes_edad_gestacional_chk. */
fun egValida(semanas: Int?, dias: Int? = null): Boolean =
    semanas != null && semanas in EG_MIN_SEMANAS..EG_MAX_SEMANAS && (dias == null || dias in 0..6)

/** La EG de las columnas de `pacientes` (null si no hay o está fuera de rango), como `egDePaciente`. */
fun egDe(semanas: Int?, dias: Int?): EdadGestacional? = if (egValida(semanas, dias)) EdadGestacional(semanas!!, dias) else null

/** EG en días totales (32+4 → 228). */
fun egEnDias(eg: EdadGestacional): Int = eg.semanas * 7 + (eg.dias ?: 0)

/** ¿Nació antes de las 37 semanas? */
fun esPrematuro(eg: EdadGestacional?): Boolean = eg != null && egEnDias(eg) < DIAS_PRETERMINO

/** Días como "34+2 sem" (o "34 sem" justo en la semana). */
fun textoSemanasDias(diasTotales: Int): String {
    val s = diasTotales / 7
    val d = diasTotales % 7
    return if (d != 0) "$s+$d sem" else "$s sem"
}

/** "32+4 sem", "38 sem"; "" sin dato. */
fun textoEg(eg: EdadGestacional?): String = eg?.let { textoSemanasDias(egEnDias(it)) } ?: ""

/**
 * La fecha en que cumplió (o cumplirá) 40 semanas: nacimiento + (280 − EG)
 * días. Desde ella se cuenta la edad corregida. null si no nació prematuro.
 */
fun fechaTermino(fechaNacimiento: String?, eg: EdadGestacional?): String? {
    if (fechaNacimiento == null || !ISO_FECHA.matches(fechaNacimiento.take(10)) || !esPrematuro(eg)) return null
    return runCatching {
        LocalDate.parse(fechaNacimiento.take(10)).plus(DIAS_TERMINO - egEnDias(eg!!), DateTimeUnit.DAY).toString()
    }.getOrNull()
}

data class EdadEvaluada(
    /** La edad con la que se evalúa (días): corregida si aplica. null = antes del término (sin z-score). */
    val dias: Int?,
    val usaEdadCorregida: Boolean,
    /** Edad postmenstrual < 40 semanas: los patrones OMS no aplican. */
    val antesDelTermino: Boolean,
    /** EG al nacer + edad cronológica (días); null sin EG. */
    val postmenstrualDias: Int?,
)

/**
 * La edad con la que se evalúa una medición, a partir de la cronológica y la EG
 * al nacer. [mesesCal]: meses cumplidos por CALENDARIO ([mesesCumplidos]): el
 * corte es "cumplió 2 años", no 730 días. Sin él se aproxima con meses OMS —
 * usar [edadParaEvaluarEntre] cuando se tienen las fechas.
 */
fun edadParaEvaluar(diasCronologicos: Int, eg: EdadGestacional?, mesesCal: Int? = null): EdadEvaluada {
    val postmenstrualDias = eg?.let { egEnDias(it) + diasCronologicos }
    val cumplio2 = if (mesesCal != null) mesesCal >= MESES_FIN_CORRECCION
    else mesesDeDias(diasCronologicos.toDouble()) >= MESES_FIN_CORRECCION
    if (eg == null || !esPrematuro(eg) || cumplio2) {
        return EdadEvaluada(diasCronologicos, usaEdadCorregida = false, antesDelTermino = false, postmenstrualDias = postmenstrualDias)
    }
    val corregida = postmenstrualDias!! - DIAS_TERMINO
    if (corregida < 0) return EdadEvaluada(null, usaEdadCorregida = false, antesDelTermino = true, postmenstrualDias = postmenstrualDias)
    return EdadEvaluada(corregida, usaEdadCorregida = true, antesDelTermino = false, postmenstrualDias = postmenstrualDias)
}

/** [edadParaEvaluar] desde las fechas (corte a los 2 años por calendario). null si falta una fecha o es antes de nacer. */
fun edadParaEvaluarEntre(fechaNacimiento: String?, fecha: String?, eg: EdadGestacional?): EdadEvaluada? {
    val dias = edadEnDias(fechaNacimiento, fecha) ?: return null
    return edadParaEvaluar(dias, eg, mesesCumplidos(fechaNacimiento, fecha))
}

/**
 * La edad corregida como TEXTO, por calendario desde la fecha de término
 * ("5 meses"). null si a esa fecha no se corrige.
 */
fun textoEdadCorregida(fechaNacimiento: String?, fecha: String?, eg: EdadGestacional?): String? {
    val e = edadParaEvaluarEntre(fechaNacimiento, fecha, eg)
    return if (e?.usaEdadCorregida == true) textoEdadEntre(fechaTermino(fechaNacimiento, eg), fecha) else null
}

// ─── LMS ─────────────────────────────────────────────────────────────────────

data class Lms(val l: Double, val m: Double, val s: Double)

/** L, M y S en `x` (interpolación lineal entre nodos). null fuera de la tabla. */
fun lmsEn(c: CurvaLms, x: Double): Lms? {
    if (c.size == 0 || !x.isFinite()) return null
    if (x < c.x[0] || x > c.x[c.size - 1]) return null
    var lo = 0
    var hi = c.size - 1
    while (hi - lo > 1) {
        val mid = (lo + hi) ushr 1
        if (c.x[mid] <= x) lo = mid else hi = mid
    }
    if (c.x[lo] == x || hi == lo) return Lms(c.l[lo], c.m[lo], c.s[lo])
    if (c.x[hi] == x) return Lms(c.l[hi], c.m[hi], c.s[hi])
    val f = (x - c.x[lo]) / (c.x[hi] - c.x[lo])
    return Lms(c.l[lo] + f * (c.l[hi] - c.l[lo]), c.m[lo] + f * (c.m[hi] - c.m[lo]), c.s[lo] + f * (c.s[hi] - c.s[lo]))
}

/** z-score LMS (sin ajuste). */
fun zLms(y: Double, p: Lms): Double =
    if (p.l == 0.0) ln(y / p.m) / p.s else ((y / p.m).pow(p.l) - 1) / (p.s * p.l)

/** La medida que corresponde a un z (la curva). */
fun valorEnZ(z: Double, p: Lms): Double =
    if (p.l == 0.0) p.m * exp(p.s * z) else p.m * (1 + p.l * p.s * z).pow(1 / p.l)

/** z-score con el AJUSTE de la OMS para |z| > 3 (P/E, P/T, IMC/E). Igual que `compute_zscore_adjusted`. */
fun zAjustado(y: Double, p: Lms): Double {
    val z = zLms(y, p)
    if (z > 3) {
        val sd3 = valorEnZ(3.0, p); val sd2 = valorEnZ(2.0, p)
        return 3 + (y - sd3) / (sd3 - sd2)
    }
    if (z < -3) {
        val sd3 = valorEnZ(-3.0, p); val sd2 = valorEnZ(-2.0, p)
        return -3 + (y - sd3) / (sd2 - sd3)
    }
    return z
}

/** Percentil (0–100) de un z: la normal estándar acumulada (Abramowitz–Stegun 7.1.26). */
fun percentilDeZ(z: Double): Double {
    val x = abs(z) / sqrt(2.0)
    val t = 1 / (1 + 0.3275911 * x)
    val erf = 1 - (((((1.061405429 * t - 1.453152027) * t) + 1.421413741) * t - 0.284496736) * t + 0.254829592) * t * exp(-x * x)
    val cdf = 0.5 * (1 + (if (z < 0) -erf else erf))
    return cdf * 100
}

// ─── Indicadores ─────────────────────────────────────────────────────────────

/** Qué tabla y en qué eje: edad en días (2006), edad en meses (2007) o talla en cm. */
data class ReferenciaOms(val tabla: TablaOms, val x: Double, val ajustado: Boolean, val fuente: String)

const val FUENTE_OMS_2006 = "OMS 2006"
const val FUENTE_OMS_2007 = "OMS 2007"

/**
 * La referencia de un indicador a una edad (días, sin redondear) y, para P/T,
 * una talla. null = el indicador no aplica a esa edad / talla.
 */
fun referenciaDe(indicador: IndicadorCrecimiento, dias: Double, tallaCm: Double? = null): ReferenciaOms? {
    if (!(dias >= 0)) return null
    val diaRed = floor(dias + 0.5) // anthro: round_up
    val meses = dias / DIAS_POR_MES
    val menor5 = meses < 60
    return when (indicador) {
        IndicadorCrecimiento.PESO_EDAD ->
            if (menor5) ReferenciaOms(TablaOms.WFA, diaRed, true, FUENTE_OMS_2006)
            else if (meses <= 120) ReferenciaOms(TablaOms.WFA07, meses, true, FUENTE_OMS_2007) else null
        IndicadorCrecimiento.TALLA_EDAD ->
            if (menor5) ReferenciaOms(TablaOms.LHFA, diaRed, false, FUENTE_OMS_2006)
            else if (meses <= MESES_MAX) ReferenciaOms(TablaOms.HFA07, meses, false, FUENTE_OMS_2007) else null
        IndicadorCrecimiento.IMC_EDAD ->
            if (menor5) ReferenciaOms(TablaOms.BFA, diaRed, true, FUENTE_OMS_2006)
            else if (meses <= MESES_MAX) ReferenciaOms(TablaOms.BFA07, meses, true, FUENTE_OMS_2007) else null
        IndicadorCrecimiento.PC_EDAD ->
            if (menor5) ReferenciaOms(TablaOms.HCFA, diaRed, false, FUENTE_OMS_2006) else null
        IndicadorCrecimiento.PESO_TALLA -> {
            if (!menor5 || tallaCm == null || !tallaCm.isFinite()) null
            else ReferenciaOms(if (diaRed < 731) TablaOms.WFL else TablaOms.WFH, tallaCm, true, FUENTE_OMS_2006)
        }
    }
}

/** z (2 decimales, como la OMS), percentil (0–100, 1 decimal) y de qué referencia salió. */
data class ZIndicador(val z: Double, val percentil: Double, val fuente: String)

/** z-score y percentil de una medida. null = no aplica a esa edad/talla o falta un dato. */
fun zIndicador(
    tablas: TablasOms, indicador: IndicadorCrecimiento, sexo: String, dias: Double,
    medida: Double?, tallaCm: Double? = null,
): ZIndicador? {
    if (medida == null || !medida.isFinite() || medida <= 0) return null
    val ref = referenciaDe(indicador, dias, tallaCm) ?: return null
    val lms = lmsEn(tablas.curva(ref.tabla, sexo), ref.x) ?: return null
    val z = if (ref.ajustado) zAjustado(medida, lms) else zLms(medida, lms)
    if (!z.isFinite()) return null
    val zr = r2(z)
    return ZIndicador(zr, r1(percentilDeZ(zr)), ref.fuente)
}

// ─── Clasificación (NTS N° 238-MINSA/DGIESP-2025, Tabla N° 3) ────────────────

/** ok = verde · alerta = ámbar · riesgo = rojo · info = azul (talla alta). */
enum class NivelCrecimiento(val clave: String) {
    OK("ok"), ALERTA("alerta"), RIESGO("riesgo"), INFO("info");

    companion object {
        fun de(clave: String?): NivelCrecimiento? = entries.firstOrNull { it.clave == clave }
    }
}

const val FUENTE_NTS_238 = "NTS 238-MINSA"
const val FUENTE_OMS_REFERENCIAL = "OMS (referencial)"

data class ClasificacionCrecimiento(val texto: String, val nivel: NivelCrecimiento, val fuente: String = FUENTE_NTS_238)

/**
 * Clasificación de un z (ya redondeado a 2 decimales) a una edad en meses.
 * "> +2" excluye el +2; "+2 a −2" lo incluye. null = la norma no clasifica ese
 * indicador a esa edad (se muestra el z solo).
 */
fun clasificarZ(indicador: IndicadorCrecimiento, z: Double, meses: Double): ClasificacionCrecimiento? {
    if (!z.isFinite()) return null
    val menor5 = meses < 60
    fun c(t: String, n: NivelCrecimiento, f: String = FUENTE_NTS_238) = ClasificacionCrecimiento(t, n, f)
    return when (indicador) {
        IndicadorCrecimiento.PESO_EDAD -> when {
            !menor5 -> null // 5–10 años: la NTS no lo usa (se evalúa con IMC/E).
            z > 2 -> c("Sobrepeso", NivelCrecimiento.ALERTA)
            z >= -2 -> c("Normal", NivelCrecimiento.OK)
            z >= -3 -> c("Bajo peso", NivelCrecimiento.ALERTA)
            else -> c("Bajo peso severo", NivelCrecimiento.RIESGO)
        }
        IndicadorCrecimiento.PESO_TALLA -> when {
            !menor5 -> null
            z > 3 -> c("Obesidad", NivelCrecimiento.RIESGO)
            z > 2 -> c("Sobrepeso", NivelCrecimiento.ALERTA)
            z >= -2 -> c("Normal", NivelCrecimiento.OK)
            z >= -3 -> c("Desnutrición aguda (emaciado)", NivelCrecimiento.ALERTA)
            else -> c("Desnutrición aguda severa", NivelCrecimiento.RIESGO)
        }
        IndicadorCrecimiento.TALLA_EDAD -> when {
            z > 2 -> c(if (menor5) "Alto" else "Talla alta", NivelCrecimiento.INFO)
            z >= -2 -> c("Normal", NivelCrecimiento.OK)
            z >= -3 -> c(if (menor5) "Talla baja (desnutrición crónica)" else "Talla baja", NivelCrecimiento.ALERTA)
            else -> c("Talla baja severa", NivelCrecimiento.RIESGO)
        }
        IndicadorCrecimiento.PC_EDAD -> when {
            !menor5 -> null
            z < -2 -> c("Riesgo de microcefalia", NivelCrecimiento.RIESGO)
            z > 2 -> c("Riesgo de macrocefalia", NivelCrecimiento.RIESGO)
            else -> c("Normal", NivelCrecimiento.OK)
        }
        IndicadorCrecimiento.IMC_EDAD -> if (menor5) when {
            // La NTS 238 no clasifica IMC/E antes de los 5 años: puntos de la OMS, referenciales.
            z > 3 -> c("Obesidad", NivelCrecimiento.RIESGO, FUENTE_OMS_REFERENCIAL)
            z > 2 -> c("Sobrepeso", NivelCrecimiento.ALERTA, FUENTE_OMS_REFERENCIAL)
            z > 1 -> c("Riesgo de sobrepeso", NivelCrecimiento.ALERTA, FUENTE_OMS_REFERENCIAL)
            z >= -2 -> c("Normal", NivelCrecimiento.OK, FUENTE_OMS_REFERENCIAL)
            z >= -3 -> c("Emaciado", NivelCrecimiento.ALERTA, FUENTE_OMS_REFERENCIAL)
            else -> c("Severamente emaciado", NivelCrecimiento.RIESGO, FUENTE_OMS_REFERENCIAL)
        } else when {
            // NTS 238 (Tabla N° 3, nota): "IMC ≥ 3 DE = obesidad severa" — aquí sí es ≥.
            z >= 3 -> c("Obesidad severa", NivelCrecimiento.RIESGO)
            z > 2 -> c("Obesidad", NivelCrecimiento.RIESGO)
            z > 1 -> c("Sobrepeso", NivelCrecimiento.ALERTA)
            z >= -2 -> c("Normal", NivelCrecimiento.OK)
            z >= -3 -> c("Delgadez", NivelCrecimiento.ALERTA)
            else -> c("Delgadez severa", NivelCrecimiento.RIESGO)
        }
    }
}

// ─── Evaluación de una medición ──────────────────────────────────────────────

/** Lo mínimo de una medición para evaluarla (también la del portal). */
data class MedicionCrecimiento(
    val fecha: String,
    val peso: Double? = null,
    val talla: Double? = null,
    val perimetroCefalico: Double? = null,
)

data class ResultadoIndicador(
    val indicador: IndicadorCrecimiento,
    /** La medida evaluada (kg, cm o kg/m²). */
    val valor: Double,
    val z: Double,
    val percentil: Double,
    val fuente: String,
    val clasificacion: ClasificacionCrecimiento?,
)

data class EvaluacionCrecimiento(
    val fecha: String,
    val edadDias: Int,
    val edadMeses: Double,
    /** Por calendario ("12 meses" el día que cumple un año). */
    val edadTexto: String,
    val imc: Double?,
    val resultados: List<ResultadoIndicador>,
    /**
     * Prematuridad: la edad corregida con la que se evaluó (y dibuja). null si no
     * se corrige (sin EG, a término, ≥ 2 años o antes del término).
     */
    val edadCorregidaDias: Int? = null,
    val edadCorregidaMeses: Double? = null,
    /** Por calendario desde la fecha de término ("5 meses"). */
    val edadCorregidaTexto: String? = null,
    val usaEdadCorregida: Boolean = false,
    /** Edad postmenstrual < 40 semanas: sin z-score (`resultados` vacío). */
    val antesDelTermino: Boolean = false,
    /** "34+2 sem" — solo antes del término. */
    val edadPostmenstrualTexto: String? = null,
)

private fun positivo(v: Double?): Double? = v?.takeIf { it.isFinite() && it > 0 }

/** IMC con 1 decimal (talla en cm). */
fun imcDe(peso: Double?, tallaCm: Double?): Double? {
    if (peso == null || tallaCm == null || tallaCm <= 0) return null
    return r1(peso / ((tallaCm / 100).pow(2)))
}

/**
 * Todos los indicadores que aplican a una medición. null = falta la fecha de
 * nacimiento o la medición es de antes de nacer o después de los 19 años.
 * [eg] (edad gestacional al nacer, opcional): si nació prematuro, evalúa con la
 * edad corregida (ver [edadParaEvaluar]). Sin [eg], exactamente lo de siempre.
 */
fun evaluarMedicion(
    tablas: TablasOms, sexo: String, fechaNacimiento: String?, m: MedicionCrecimiento, eg: EdadGestacional? = null,
): EvaluacionCrecimiento? {
    val diasCrono = edadEnDias(fechaNacimiento, m.fecha) ?: return null
    if (mesesDeDias(diasCrono.toDouble()) > MESES_MAX + 1) return null
    val edad = edadParaEvaluar(diasCrono, eg, mesesCumplidos(fechaNacimiento, m.fecha))
    val peso = positivo(m.peso)
    val talla = positivo(m.talla)
    val pc = positivo(m.perimetroCefalico)
    val imcExacto = if (peso != null && talla != null) peso / ((talla / 100).pow(2)) else null
    val medidas = mapOf(
        IndicadorCrecimiento.PESO_EDAD to peso, IndicadorCrecimiento.TALLA_EDAD to talla,
        IndicadorCrecimiento.PESO_TALLA to peso, IndicadorCrecimiento.IMC_EDAD to imcExacto, IndicadorCrecimiento.PC_EDAD to pc,
    )
    // Antes del término no hay patrón OMS: ningún z (nunca uno falso).
    val diasEval = edad.dias
    val resultados = if (diasEval == null) emptyList() else IndicadorCrecimiento.entries.mapNotNull { ind ->
        val meses = mesesDeDias(diasEval.toDouble())
        val valor = medidas[ind] ?: return@mapNotNull null
        val zi = zIndicador(tablas, ind, sexo, diasEval.toDouble(), valor, if (ind == IndicadorCrecimiento.PESO_TALLA) talla else null)
            ?: return@mapNotNull null
        ResultadoIndicador(
            ind, if (ind == IndicadorCrecimiento.IMC_EDAD) r1(valor) else valor, zi.z, zi.percentil, zi.fuente,
            clasificarZ(ind, zi.z, meses),
        )
    }
    val corr = if (edad.usaEdadCorregida) edad.dias else null
    return EvaluacionCrecimiento(
        fecha = m.fecha,
        edadDias = diasCrono,
        edadMeses = r1(mesesDeDias(diasCrono.toDouble())),
        edadTexto = textoEdadEntre(fechaNacimiento, m.fecha),
        imc = imcDe(peso, talla),
        resultados = resultados,
        edadCorregidaDias = corr,
        edadCorregidaMeses = corr?.let { r1(mesesDeDias(it.toDouble())) },
        edadCorregidaTexto = if (corr != null) textoEdadCorregida(fechaNacimiento, m.fecha, eg) else null,
        usaEdadCorregida = edad.usaEdadCorregida,
        antesDelTermino = edad.antesDelTermino,
        edadPostmenstrualTexto = if (edad.antesDelTermino) textoSemanasDias(edad.postmenstrualDias!!) else null,
    )
}

/** La edad en meses con la que se dibuja la curva: la corregida si aplica. null antes del término. */
fun edadCurvaMeses(ev: EvaluacionCrecimiento): Double? =
    if (ev.antesDelTermino) null else ev.edadCorregidaMeses ?: ev.edadMeses

/**
 * La edad para mostrar: "7 meses (corregida 5 meses)"; antes del término
 * "20 días (34+2 sem posmenstruales)"; sin corrección, "7 meses".
 * [corto]: "(corr. 5 meses)" / "(34+2 sem EPM)" para tablas.
 */
fun textoEdadEvaluada(ev: EvaluacionCrecimiento, corto: Boolean = false): String = when {
    ev.antesDelTermino && ev.edadPostmenstrualTexto != null ->
        "${ev.edadTexto} (${ev.edadPostmenstrualTexto} ${if (corto) "EPM" else "posmenstruales"})"
    ev.edadCorregidaTexto != null -> "${ev.edadTexto} (${if (corto) "corr." else "corregida"} ${ev.edadCorregidaTexto})"
    else -> ev.edadTexto
}

/** Leyenda de la corrección (pestaña, hoja, portal). null si no hay EG prematura. */
fun leyendaPrematuridad(eg: EdadGestacional?): String? {
    if (!esPrematuro(eg)) return null
    return "Nació prematuro (${textoEg(eg)}): hasta los $MESES_FIN_CORRECCION meses se evalúa con la edad corregida " +
        "(edad cronológica − (40 semanas − edad gestacional al nacer)). Antes de las 40 semanas de edad posmenstrual " +
        "los patrones OMS no aplican: usar curvas INTERGROWTH-21st o Fenton."
}

/**
 * La fecha de la PRIMERA evaluación de un prematuro que ya no se corrige
 * (cumplió 2 años): ahí se avisa que desde los 2 años se evalúa con la edad
 * real, porque el punto "salta" en la curva. null si no aplica.
 */
fun primeraSinCorreccion(evaluaciones: List<EvaluacionCrecimiento>, eg: EdadGestacional?): String? {
    if (!esPrematuro(eg)) return null
    return evaluaciones.sortedBy { it.fecha }.firstOrNull { !it.usaEdadCorregida && !it.antesDelTermino }?.fecha
}

const val AVISO_FIN_CORRECCION = "Cumplió 2 años: desde los 2 años se evalúa con la edad real (ya no se corrige por prematuridad)."

/** Aviso cuando la última medición es de antes del término. */
const val AVISO_ANTES_DEL_TERMINO =
    "Aún antes del término: los patrones de la OMS empiezan a las 40 semanas de edad posmenstrual. Usar curvas INTERGROWTH-21st o Fenton."

/** El peor nivel de una evaluación (para el chip del resumen). */
fun peorNivel(clasificaciones: List<ClasificacionCrecimiento?>): NivelCrecimiento? =
    listOf(NivelCrecimiento.RIESGO, NivelCrecimiento.ALERTA, NivelCrecimiento.INFO, NivelCrecimiento.OK)
        .firstOrNull { n -> clasificaciones.any { it?.nivel == n } }

/** Un número con 2 decimales exactos ("−1.25" → "1.25"), sin signo. */
private fun dosDecimales(v: Double): String {
    val cent = redondearJs(abs(v) * 100).toLong()
    return "${cent / 100}.${(cent % 100).toString().padStart(2, '0')}"
}

/** Como `String(n)` de JS para un número de 1 decimal: 50 → "50", 10.6 → "10.6". */
private fun numCorto(v: Double): String = if (v == floor(v)) v.toLong().toString() else v.toString()

/** "z −1,25 · P11" — texto corto para tarjetas, HC y portal (igual que la web). */
fun textoZ(z: Double, percentil: Double): String {
    val zTxt = (if (z < 0) "−" else if (z > 0) "+" else "") + dosDecimales(z).replace('.', ',')
    val p = when {
        percentil < 0.1 -> "<0,1"
        percentil > 99.9 -> ">99,9"
        else -> numCorto(redondearJs(percentil * 10) / 10).replace('.', ',')
    }
    return "z $zTxt · P$p"
}

// ─── Curvas para el gráfico ──────────────────────────────────────────────────

/** Las líneas de referencia que se dibujan (z). */
val LINEAS_Z: List<Int> = listOf(-3, -2, 0, 2, 3)

/** Eje x: edad en meses o talla en cm (P/T). */
data class VentanaCurva(val ejeCm: Boolean, val desde: Double, val hasta: Double)

/**
 * La ventana del gráfico según la edad de la ÚLTIMA medición: 0–24 meses, 0–5
 * años o 5–19 años (P/E hasta 10). P/T: de la talla mínima a la máxima de su
 * tabla. null = el indicador no aplica a esa edad.
 */
fun ventanaCurva(indicador: IndicadorCrecimiento, mesesUltima: Double): VentanaCurva? {
    val menor5 = mesesUltima < 60
    if (indicador == IndicadorCrecimiento.PESO_TALLA) {
        if (!menor5) return null
        return if (mesesUltima < 24) VentanaCurva(true, 45.0, 110.0) else VentanaCurva(true, 65.0, 120.0)
    }
    if (indicador == IndicadorCrecimiento.PC_EDAD) return if (menor5) VentanaCurva(false, 0.0, if (mesesUltima < 24) 24.0 else 60.0) else null
    if (menor5) return VentanaCurva(false, 0.0, if (mesesUltima < 24) 24.0 else 60.0)
    if (indicador == IndicadorCrecimiento.PESO_EDAD) return if (mesesUltima <= 120) VentanaCurva(false, 60.0, 120.0) else null
    return VentanaCurva(false, 60.0, MESES_MAX.toDouble())
}

data class PuntoCurva(val x: Double, val zm3: Double, val zm2: Double, val z0: Double, val z2: Double, val z3: Double) {
    /** El valor de una línea (−3, −2, 0, 2 o 3). */
    fun en(z: Int): Double = when (z) { -3 -> zm3; -2 -> zm2; 0 -> z0; 2 -> z2; else -> z3 }
}

/** Las curvas de referencia (−3, −2, 0, +2, +3 DE) de un indicador en una ventana, ~[pasos] puntos. */
fun curvaReferencia(tablas: TablasOms, indicador: IndicadorCrecimiento, sexo: String, v: VentanaCurva, pasos: Int = 60): List<PuntoCurva> {
    val out = ArrayList<PuntoCurva>(pasos + 1)
    val paso = (v.hasta - v.desde) / pasos
    for (i in 0..pasos) {
        val x = r2(v.desde + i * paso)
        val lms = if (v.ejeCm) {
            lmsEn(tablas.curva(if (v.desde < 65) TablaOms.WFL else TablaOms.WFH, sexo), x)
        } else {
            // El borde de 60 meses de una ventana de 0–5 años se dibuja con la tabla 2006.
            val dias = x * DIAS_POR_MES
            val ref = if (x >= 60 && v.desde >= 60) referenciaDe(indicador, dias) else referenciaDe(indicador, minOf(dias, DIAS_5_ANIOS - 1))
            ref?.let { lmsEn(tablas.curva(it.tabla, sexo), it.x) }
        } ?: continue
        fun en(z: Double) = r2(valorEnZ(z, lms))
        out.add(PuntoCurva(x, en(-3.0), en(-2.0), en(0.0), en(2.0), en(3.0)))
    }
    return out
}

data class PuntoPaciente(val x: Double, val valor: Double, val fecha: String, val z: Double?)

/**
 * Los puntos del niño para un indicador (x = edad en meses o talla en cm), en la
 * ventana. Con [eg] prematura: x = edad corregida; antes del término no se dibuja.
 */
fun puntosPaciente(
    tablas: TablasOms, indicador: IndicadorCrecimiento, sexo: String, fechaNacimiento: String?,
    mediciones: List<MedicionCrecimiento>, v: VentanaCurva, eg: EdadGestacional? = null,
): List<PuntoPaciente> {
    val out = mutableListOf<PuntoPaciente>()
    for (m in mediciones) {
        val ev = evaluarMedicion(tablas, sexo, fechaNacimiento, m, eg) ?: continue
        val r = ev.resultados.firstOrNull { it.indicador == indicador } ?: continue
        val x = if (v.ejeCm) positivo(m.talla) else (ev.edadCorregidaDias ?: ev.edadDias) / DIAS_POR_MES
        if (x == null || x < v.desde || x > v.hasta) continue
        out.add(PuntoPaciente(r2(x), r.valor, m.fecha, r.z))
    }
    return out.sortedBy { it.x }
}

/** Indicadores con curva para la última edad y alguna medición que los tenga (selector P/E, T/E…). */
fun indicadoresConCurva(mediciones: List<MedicionCrecimiento>, mesesUltima: Double): List<IndicadorCrecimiento> =
    IndicadorCrecimiento.entries.filter { i ->
        ventanaCurva(i, mesesUltima) != null && mediciones.any { m ->
            when (i) {
                IndicadorCrecimiento.PC_EDAD -> m.perimetroCefalico != null
                IndicadorCrecimiento.TALLA_EDAD -> m.talla != null
                IndicadorCrecimiento.PESO_EDAD -> m.peso != null
                else -> m.peso != null && m.talla != null
            }
        }
    }

// ─── Lo de siempre ───────────────────────────────────────────────────────────

/** Sexo de la base → "F" | "M" | null (cualquier otra cosa = sin dato). */
fun sexoValido(v: String?): String? = v?.trim()?.uppercase()?.takeIf { it == "F" || it == "M" }

/** Colores de los niveles para las hojas impresas (HEX, como la web). */
fun colorNivelHex(n: NivelCrecimiento?): String = when (n) {
    NivelCrecimiento.OK -> "#16a34a"
    NivelCrecimiento.ALERTA -> "#d97706"
    NivelCrecimiento.RIESGO -> "#dc2626"
    NivelCrecimiento.INFO -> "#1d6fa8"
    null -> "#6b7280"
}

// ─── ¿Quién es pediátrico? (gemelos de lib/flujo.ts) ─────────────────────────

// `esPediatria(nombre)` (por el NOMBRE, nunca odontopediatría) vive en AtencionVariantes.kt.

/**
 * Qué especialidades de la clínica son de pediatría (de /api/staff/contexto:
 * `especialidadesPediatria`, `soloPediatria`). Un backend sin esos campos deja
 * el mapa vacío y la pestaña 👶 no aparece.
 */
data class MapaPediatria(val ids: List<String> = emptyList(), val solo: Boolean = false) {
    val activo: Boolean get() = solo || ids.isNotEmpty()
}

/** Gemelo de `mapaPediatria`: por el nombre de cada especialidad. */
fun mapaPediatriaDe(especialidades: List<Pair<String, String?>>): MapaPediatria {
    val ids = especialidades.filter { esPediatria(it.second) }.map { it.first }
    return MapaPediatria(ids, especialidades.isNotEmpty() && ids.size == especialidades.size)
}

/** ¿Esta cita / tratamiento es de pediatría? Misma cadena que `citaEsFisio`. */
fun citaEsPediatria(
    mapa: MapaPediatria,
    especialidadId: String? = null,
    especialidadServicioId: String? = null,
    especialidadesProfesional: List<String>? = null,
): Boolean = atencionEnMapaEspecialidad(mapa.ids, mapa.solo, especialidadId, especialidadServicioId, especialidadesProfesional)

/**
 * ¿La ficha muestra la pestaña 👶 Crecimiento? (gemelo de `pacienteEsPediatria`):
 * clínica solo de pediatría → siempre; sin pediatría → nunca; mixta → si tiene
 * una atención de pediatría, vacunas ya registradas, si quien mira es pediatra
 * o (señal secundaria, SOLO porque la clínica tiene pediatría) es menor de 18.
 */
fun pacienteEsPediatria(
    mapa: MapaPediatria,
    atenciones: List<AtencionFisio> = emptyList(),
    tieneVacunas: Boolean = false,
    especialidadesDeQuienMira: List<String>? = null,
    edadAnios: Int? = null,
): Boolean {
    if (mapa.solo) return true
    if (mapa.ids.isEmpty()) return false
    if (tieneVacunas) return true
    if (atenciones.any { citaEsPediatria(mapa, it.especialidadId, it.especialidadServicioId, it.especialidadesProfesional) }) return true
    if (especialidadesDeQuienMira.orEmpty().any { it.isNotBlank() && it in mapa.ids }) return true
    return edadAnios != null && edadAnios in 0..17
}
