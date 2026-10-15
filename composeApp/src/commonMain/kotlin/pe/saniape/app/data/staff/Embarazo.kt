package pe.saniape.app.data.staff

import kotlinx.datetime.LocalDate
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlin.math.abs
import kotlin.math.floor

/**
 * 🤰 CARNÉ PERINATAL (obstetricia): el embarazo y sus controles prenatales
 * estructurados. Tablas `embarazos` y `controles_prenatales`, vía
 * /api/staff/embarazo y /api/staff/control-prenatal (ver [EmbarazoRepo]).
 *
 * Gemelo PURO de `lib/embarazo.ts` de la web (mismos ids de catálogo, mismos
 * textos, mismas reglas; los tests copian los valores esperados de
 * `lib/__tests__/embarazo.test.ts`):
 *   · datación: FPP por Naegele (FUR + 280 d) y corrección por ecografía
 *     precoz con los umbrales de la ACOG (Committee Opinion 700);
 *   · edad gestacional en una fecha, trimestre;
 *   · apoyo clínico (NUNCA bloquea): PA (ACOG PB 222), AU para la EG (McDonald
 *     orientativa 20–24, ACOG PB 227 de la 24 a la 38 6/7), LCF 110–160 (FIGO
 *     2015), ganancia de peso por IMC pregestacional (IOM 2009, único y
 *     gemelar), controles mínimos y próximo control (NTS 105-MINSA);
 *   · validación (los mismos mensajes que la API) y series de los gráficos.
 *
 * Las fechas son 'AAAA-MM-DD' de la zona de la clínica (la sede de la
 * paciente): se cuentan como días enteros (LocalDate), sin horas ni zona, así
 * que nunca se corren un día.
 *
 * Solo GINECO-OBSTETRICIA y por PACIENTE: el mapa ([MapaGineco]) lo arma la web
 * (/api/staff/contexto: `especialidadesGineco`, `soloGineco`, por el rubro
 * guardado 'ginecologia'); un backend sin esos campos deja el mapa vacío y la
 * pestaña 🤰 no aparece.
 */

// ─── ¿Es gineco-obstetricia? ─────────────────────────────────────────────────

/** Qué especialidades de la clínica son de gineco-obstetricia. `solo` = todas lo son. */
data class MapaGineco(val ids: List<String> = emptyList(), val solo: Boolean = false) {
    val activo: Boolean get() = solo || ids.isNotEmpty()
}

/** Gemelo de `mapaGineco`: SOLO el rubro guardado 'ginecologia' (sin rubro no cuenta). */
fun mapaGineco(especialidades: List<Pair<String, String?>>?): MapaGineco {
    val esps = especialidades.orEmpty()
    val ids = esps.filter { it.second == "ginecologia" }.map { it.first }
    return MapaGineco(ids, esps.isNotEmpty() && ids.size == esps.size)
}

/** ¿Esta cita / tratamiento es de gineco-obstetricia? Misma cadena que `citaEsFisio`. */
fun citaEsGineco(
    mapa: MapaGineco,
    especialidadId: String? = null,
    especialidadServicioId: String? = null,
    especialidadesProfesional: List<String>? = null,
): Boolean = atencionEnMapaEspecialidad(mapa.ids, mapa.solo, especialidadId, especialidadServicioId, especialidadesProfesional)

/**
 * ¿La ficha muestra la pestaña 🤰 Embarazo? (gemelo de `pacienteEsGineco`):
 * clínica solo de gineco-obstetricia → sí; sin gineco → no; mixta → si ya tiene
 * embarazos registrados, si alguna atención es de gineco o si quien mira lo es.
 */
fun pacienteEsGineco(
    mapa: MapaGineco,
    atenciones: List<AtencionFisio> = emptyList(),
    tieneEmbarazos: Boolean = false,
    especialidadesDeQuienMira: List<String>? = null,
): Boolean {
    if (mapa.solo) return true
    if (mapa.ids.isEmpty()) return false
    if (tieneEmbarazos) return true
    if (atenciones.any { citaEsGineco(mapa, it.especialidadId, it.especialidadServicioId, it.especialidadesProfesional) }) return true
    return especialidadesDeQuienMira.orEmpty().any { it.isNotBlank() && it in mapa.ids }
}

// ─── Fechas (días enteros) ───────────────────────────────────────────────────

/** Duración del embarazo desde la FUR (regla de Naegele: FUR + 280 días). */
const val DIAS_EMBARAZO = 280
/** 44 semanas: más allá, la FUR es de otro embarazo (misma regla que la HC). */
const val MAX_DIAS_EG = 308

private val RE_FECHA_EMB = Regex("^\\d{4}-\\d{2}-\\d{2}$")

/** ¿Es 'AAAA-MM-DD' y una fecha que existe (no 31/02)? */
fun esFechaIsoEmb(v: String?): Boolean =
    v != null && RE_FECHA_EMB.matches(v) && runCatching { LocalDate.parse(v) }.isSuccess

private fun diaNum(iso: String): Int = LocalDate.parse(iso.take(10)).toEpochDays()

/** iso + n días. */
fun sumarDiasEmb(iso: String, n: Int): String = LocalDate.fromEpochDays(diaNum(iso) + n).toString()

/** Días de `desde` a `hasta` (negativo si `hasta` es anterior). */
fun diasEntreEmb(desde: String, hasta: String): Int = diaNum(hasta) - diaNum(desde)

/** "2026-03-01" → "01/03/2026". */
fun fechaEmb(iso: String?): String {
    if (iso.isNullOrBlank()) return "—"
    val p = iso.take(10).split("-")
    return if (p.size == 3 && p[0].length == 4) "${p[2]}/${p[1]}/${p[0]}" else iso
}

/** Math.round de JS (la mitad hacia +∞), igual en todas las plataformas. */
private fun redondear(x: Double): Double = floor(x + 0.5)
private fun r1(x: Double): Double = redondear(x * 10) / 10
private fun r2(x: Double): Double = redondear(x * 100) / 100

/** Un número como lo imprime JS: 16 → "16", 11.5 → "11.5", -4 → "-4". */
fun numEmb(v: Double): String = if (v == floor(v) && abs(v) < 1e15) v.toLong().toString() else v.toString()

// ─── Catálogos (ids ESTABLES: los de la web y la base) ───────────────────────

data class OpcionEmb(val id: String, val nombre: String)
data class SignoAlarma(val id: String, val nombre: String, val educativo: String)

/** Antecedentes / factores de riesgo (chips). */
val RIESGOS_EMBARAZO: List<OpcionEmb> = listOf(
    OpcionEmb("preeclampsia_previa", "Preeclampsia previa"),
    OpcionEmb("hta_cronica", "HTA crónica"),
    OpcionEmb("diabetes", "Diabetes pregestacional"),
    OpcionEmb("diabetes_gestacional_previa", "Diabetes gestacional previa"),
    OpcionEmb("cesarea_previa", "Cesárea previa"),
    OpcionEmb("edad_35", "Edad ≥ 35 años"),
    OpcionEmb("adolescente", "Adolescente (≤ 19 años)"),
    OpcionEmb("embarazo_multiple", "Embarazo múltiple"),
    OpcionEmb("parto_pretermino_previo", "Parto pretérmino previo"),
    OpcionEmb("aborto_recurrente", "Abortos a repetición"),
    OpcionEmb("rh_negativo", "Rh negativo"),
    OpcionEmb("anemia", "Anemia"),
    OpcionEmb("obesidad", "Obesidad"),
    OpcionEmb("periodo_intergenesico_corto", "Periodo intergenésico corto"),
    OpcionEmb("its_vih_sifilis", "ITS / VIH / sífilis"),
    OpcionEmb("enfermedad_renal", "Enfermedad renal"),
    OpcionEmb("tiroides", "Enfermedad tiroidea"),
)

val SUPLEMENTOS_EMB: List<OpcionEmb> = listOf(
    OpcionEmb("acido_folico", "Ácido fólico"),
    OpcionEmb("hierro", "Hierro"),
    OpcionEmb("calcio", "Calcio"),
)

/** Signos de alarma (carné MINSA / CLAP): en el control, los que REFIERE; en el portal y el carné, la lista educativa. */
val SIGNOS_ALARMA_EMB: List<SignoAlarma> = listOf(
    SignoAlarma("sangrado", "Sangrado vaginal", "Sangrado por la vagina"),
    SignoAlarma("perdida_liquido", "Pérdida de líquido", "Pérdida de líquido por la vagina"),
    SignoAlarma("cefalea_vision", "Dolor de cabeza / visión borrosa", "Dolor de cabeza intenso, visión borrosa, lucecitas o zumbido de oídos"),
    SignoAlarma("edema_brusco", "Hinchazón de cara o manos", "Hinchazón de la cara, las manos o los pies"),
    SignoAlarma("fiebre", "Fiebre", "Fiebre"),
    SignoAlarma("vomitos", "Vómitos persistentes", "Vómitos que no paran"),
    SignoAlarma("movimientos_disminuidos", "Bebé se mueve menos", "El bebé se mueve menos o deja de moverse"),
    SignoAlarma("contracciones", "Contracciones / dolor (< 37 sem)", "Contracciones o dolor de barriga antes de las 37 semanas"),
    SignoAlarma("disuria", "Ardor al orinar", "Ardor o dolor al orinar"),
    SignoAlarma("convulsiones", "Convulsiones / desmayo", "Convulsiones o desmayos"),
)

val MOVIMIENTOS_FETALES: List<OpcionEmb> = listOf(
    OpcionEmb("presentes", "Presentes"), OpcionEmb("disminuidos", "Disminuidos"), OpcionEmb("ausentes", "Ausentes"),
)
val PRESENTACIONES_FETALES: List<OpcionEmb> = listOf(
    OpcionEmb("cefalica", "Cefálica"), OpcionEmb("podalica", "Podálica"),
    OpcionEmb("transversa", "Transversa"), OpcionEmb("no_evaluable", "No evaluable"),
)
val EDEMAS_EMB: List<OpcionEmb> = listOf(
    OpcionEmb("no", "Sin edema"), OpcionEmb("+", "+"), OpcionEmb("++", "++"), OpcionEmb("+++", "+++"),
)
val PROTEINURIAS_EMB: List<OpcionEmb> = listOf(
    OpcionEmb("negativo", "Negativa"), OpcionEmb("trazas", "Trazas"), OpcionEmb("+", "+"), OpcionEmb("++", "++"), OpcionEmb("+++", "+++"),
)
val DESENLACES_EMB: List<OpcionEmb> = listOf(
    OpcionEmb("parto", "Parto vaginal"), OpcionEmb("cesarea", "Cesárea"), OpcionEmb("aborto", "Aborto"), OpcionEmb("otro", "Otro"),
)
val GRUPOS_SANGUINEOS: List<String> = listOf("A", "B", "AB", "O")

private fun nombreDe(lista: List<OpcionEmb>, id: String?): String? = id?.let { i -> lista.firstOrNull { it.id == i }?.nombre }
fun nombreRiesgo(id: String): String = nombreDe(RIESGOS_EMBARAZO, id) ?: id
fun nombreSuplemento(id: String): String = nombreDe(SUPLEMENTOS_EMB, id) ?: id
fun nombreSignoAlarma(id: String): String = SIGNOS_ALARMA_EMB.firstOrNull { it.id == id }?.nombre ?: id
fun nombreMovimientos(id: String?): String? = nombreDe(MOVIMIENTOS_FETALES, id)
fun nombrePresentacion(id: String?): String? = nombreDe(PRESENTACIONES_FETALES, id)
fun nombreDesenlace(id: String?): String? = nombreDe(DESENLACES_EMB, id)

// ─── Filas de la base ────────────────────────────────────────────────────────

/** Lo que hace falta para datar (la fila del embarazo o el borrador del formulario). */
data class EntradaDatacion(
    val fur: String? = null,
    val furIncierta: Boolean = false,
    val ecoFecha: String? = null,
    val ecoEgSemanas: Int? = null,
    val ecoEgDias: Int? = null,
)

data class Embarazo(
    val id: String = "",
    val pacienteId: String = "",
    val terapeutaId: String? = null,
    val especialidadId: String? = null,
    val fur: String? = null,
    val furIncierta: Boolean = false,
    val ecoFecha: String? = null,
    val ecoEgSemanas: Int? = null,
    val ecoEgDias: Int? = null,
    val fpp: String = "",
    val metodoDatacion: String = "fur",
    val gestas: Int? = null,
    val partos: Int? = null,
    val abortos: Int? = null,
    val cesareas: Int? = null,
    val grupoSanguineo: String? = null,
    val rh: String? = null,
    val pesoPregestacional: Double? = null,
    val tallaCm: Double? = null,
    val riesgos: List<String> = emptyList(),
    val antecedentes: String? = null,
    val estado: String = "activo",
    val desenlace: String? = null,
    val fechaFin: String? = null,
    val createdAt: String? = null,
) {
    val activo: Boolean get() = estado == "activo"
    val entrada: EntradaDatacion get() = EntradaDatacion(fur, furIncierta, ecoFecha, ecoEgSemanas, ecoEgDias)
}

data class ControlPrenatal(
    val id: String = "",
    val embarazoId: String = "",
    val pacienteId: String = "",
    val citaId: String? = null,
    val terapeutaId: String? = null,
    val fecha: String = "",
    val egDias: Int? = null,
    val peso: Double? = null,
    val paSistolica: Int? = null,
    val paDiastolica: Int? = null,
    val alturaUterina: Double? = null,
    val lcf: Int? = null,
    val movimientosFetales: String? = null,
    val presentacion: String? = null,
    val edema: String? = null,
    val proteinuria: String? = null,
    val suplementos: List<String> = emptyList(),
    val signosAlarma: List<String> = emptyList(),
    val signosAlarmaExplicados: Boolean = false,
    val observaciones: String? = null,
    val proximoControl: String? = null,
    val createdAt: String? = null,
    /** Guardado en la cola offline: aún sin id real (no se edita ni borra hasta que suba). */
    val pendiente: Boolean = false,
)

/** numeric / texto con coma → número; null si vacío o no es número. */
fun aNumeroEmb(v: String?): Double? {
    val t = v?.trim()?.replace(',', '.') ?: return null
    if (t.isEmpty()) return null
    return t.toDoubleOrNull()?.takeIf { it.isFinite() }
}

private fun enteroEmb(v: String?): Int? = aNumeroEmb(v)?.takeIf { it == floor(it) && abs(it) < 1e9 }?.toInt()

// ─── Datación (FUR / ecografía precoz) ───────────────────────────────────────

/**
 * Diferencia máxima (días) entre la EG por FUR y la de la ecografía para
 * QUEDARSE con la FUR, según la EG de la ecografía (ACOG CO 700, tabla 1):
 * ≤ 8 6/7 → 5 · 9 0/7–15 6/7 → 7 · 16 0/7–21 6/7 → 10 · 22 0/7–27 6/7 → 14 · ≥ 28 → 21.
 */
fun umbralRedatacionDias(egEcoDias: Int): Int = when {
    egEcoDias <= 8 * 7 + 6 -> 5
    egEcoDias <= 15 * 7 + 6 -> 7
    egEcoDias <= 21 * 7 + 6 -> 10
    egEcoDias <= 27 * 7 + 6 -> 14
    else -> 21
}

data class Datacion(
    /** Día 0 del embarazo (FUR, o la FUR "corregida" por la ecografía). */
    val inicio: String,
    val fpp: String,
    val metodo: String,
    /** |EG por FUR − EG por eco| el día de la eco (solo si hay las dos). */
    val discrepanciaDias: Int? = null,
    val umbralDias: Int? = null,
    /** true = había FUR y la ecografía la corrigió. */
    val corregidaPorEco: Boolean = false,
    val motivo: String,
)

private fun egEcoEnDias(e: EntradaDatacion): Int? =
    if (e.ecoFecha != null && esFechaIsoEmb(e.ecoFecha) && e.ecoEgSemanas != null) e.ecoEgSemanas * 7 + (e.ecoEgDias ?: 0) else null

/** FPP por la regla de Naegele (FUR + 280 días). */
fun fppNaegele(fur: String): String = sumarDiasEmb(fur, DIAS_EMBARAZO)

/**
 * La datación: FUR, o ecografía precoz cuando no hay FUR, la FUR es incierta,
 * o la diferencia supera el umbral de la ACOG (CO 700). null si no hay con qué datar.
 */
fun datacion(e: EntradaDatacion): Datacion? {
    val fur = e.fur?.takeIf { esFechaIsoEmb(it) }
    val egEco = egEcoEnDias(e)
    val inicioEco = egEco?.let { sumarDiasEmb(e.ecoFecha!!, -it) }
    fun conFpp(inicio: String, metodo: String, disc: Int?, umbral: Int?, corregida: Boolean, motivo: String) =
        Datacion(inicio, sumarDiasEmb(inicio, DIAS_EMBARAZO), metodo, disc, umbral, corregida, motivo)

    if (inicioEco != null && egEco != null && (fur == null || e.furIncierta)) {
        return conFpp(inicioEco, "eco", null, null, false,
            if (fur != null) "FUR incierta: datado por ecografía" else "Sin FUR: datado por ecografía")
    }
    if (fur != null && inicioEco != null && egEco != null) {
        val egFur = diasEntreEmb(fur, e.ecoFecha!!)
        val disc = abs(egFur - egEco)
        val umbral = umbralRedatacionDias(egEco)
        if (disc > umbral) {
            return conFpp(inicioEco, "eco", disc, umbral, true,
                "La ecografía corrige la FUR: $disc días de diferencia (más de $umbral a esa edad gestacional)")
        }
        return conFpp(fur, "fur", disc, umbral, false,
            "FUR confirmada por ecografía ($disc días de diferencia, hasta $umbral se mantiene la FUR)")
    }
    if (fur != null) {
        return conFpp(fur, "fur", null, null, false,
            if (e.furIncierta) "Por FUR (incierta: confirmar con ecografía)" else "Por FUR (regla de Naegele)")
    }
    return null
}

/** EG en días en una fecha desde el día 0; null fuera de 0–44 semanas. */
fun egDiasEn(inicio: String?, fecha: String?): Int? {
    if (!esFechaIsoEmb(inicio) || !esFechaIsoEmb(fecha)) return null
    val d = diasEntreEmb(inicio!!, fecha!!)
    return if (d < 0 || d > MAX_DIAS_EG) null else d
}

/** "24 sem 3 d" (el mismo formato que el cuadro de la HC). */
fun textoEg(dias: Int?): String? {
    if (dias == null || dias < 0) return null
    val sem = dias / 7
    val d = dias % 7
    return "$sem sem" + if (d != 0) " $d d" else ""
}

/** Semanas con decimales (eje de los gráficos). */
fun semanasDecimal(dias: Int): Double = r2(dias / 7.0)

/** Trimestre: 1 (< 14 sem), 2 (14–27 6/7), 3 (≥ 28). */
fun trimestre(dias: Int?): Int? = when {
    dias == null -> null
    dias < 14 * 7 -> 1
    dias < 28 * 7 -> 2
    else -> 3
}

val NOMBRE_TRIMESTRE: Map<Int, String> = mapOf(1 to "1.er trimestre", 2 to "2.º trimestre", 3 to "3.er trimestre")

// ─── Apoyo clínico (nunca bloquea) ───────────────────────────────────────────

/** 'alerta' = actuar (rojo) · 'atencion' = revisar (ámbar) · 'info' = dato. */
data class AlertaEmbarazo(val id: String, val nivel: String, val texto: String, val fuente: String? = null)

private const val F_PA = "ACOG PB 222 (2020)"
private const val F_AU = "ACOG PB 227 (2021)"
private const val F_LCF = "FIGO (2015)"
private const val F_IOM = "IOM (2009)"
private const val F_IOM_GEMELAR = "IOM (2009), guía provisional para gemelares"
private const val F_MCDONALD = "regla de McDonald (orientativa)"

/** Ventana de la AU (semanas, hasta la 38 6/7); la ACOG PB 227 rige desde la 24. */
const val AU_DESDE_SEM = 20
const val AU_HASTA_SEM = 38
const val DESDE_AU_ACOG = 24
const val TOLERANCIA_AU_BANDA = 2.0
const val TOLERANCIA_AU_ALERTA = 3.0

data class AuEsperada(val esperada: Double, val min: Double, val max: Double)

/** AU esperada para la EG (regla de McDonald): null fuera de las 20–38 6/7 semanas. */
fun auEsperada(egDias: Int?): AuEsperada? {
    if (egDias == null) return null
    if (egDias < AU_DESDE_SEM * 7 || egDias > AU_HASTA_SEM * 7 + 6) return null
    val esperada = r1(egDias / 7.0)
    return AuEsperada(esperada, r1(esperada - TOLERANCIA_AU_BANDA), r1(esperada + TOLERANCIA_AU_BANDA))
}

data class EvaluacionAu(val estado: String, val diferencia: Double, val nivel: String, val acog: Boolean)

/**
 * ¿La AU va con la EG? 'alerta' SOLO de la 24 a la 38 con más de 3 cm (ACOG
 * PB 227); fuera de EG ± 2 cm (o antes de la 24) es 'atencion' orientativa.
 */
fun evaluarAu(au: Double?, egDias: Int?): EvaluacionAu? {
    val ref = auEsperada(egDias)
    if (au == null || ref == null) return null
    val diferencia = r1(au - ref.esperada)
    val a = abs(diferencia)
    val estado = if (a <= TOLERANCIA_AU_BANDA) "acorde" else if (diferencia < 0) "baja" else "alta"
    val acog = egDias!! >= DESDE_AU_ACOG * 7
    val nivel = if (acog && a > TOLERANCIA_AU_ALERTA) "alerta" else if (a > TOLERANCIA_AU_BANDA) "atencion" else "ok"
    return EvaluacionAu(estado, diferencia, nivel, acog)
}

private val PROTEINURIA_POSITIVA = listOf("+", "++", "+++")

/** Presión arterial (ACOG PB 222): ≥ 140/90 descartar THE; ≥ 160/110 rango severo. */
fun alertasPa(sist: Int?, diast: Int?, egDias: Int?, proteinuria: String?): List<AlertaEmbarazo> {
    if (sist == null || diast == null) return emptyList()
    val out = mutableListOf<AlertaEmbarazo>()
    val pa = "$sist/$diast mmHg"
    if (sist >= 160 || diast >= 110) {
        out += AlertaEmbarazo("pa_severa", "alerta", "PA $pa en rango severo: evaluación de inmediato (descartar preeclampsia con signos de severidad).", F_PA)
    } else if (sist >= 140 || diast >= 90) {
        val antes20 = egDias != null && egDias < 20 * 7
        out += AlertaEmbarazo("pa_elevada", "alerta",
            "PA $pa (≥ 140/90): descartar trastorno hipertensivo del embarazo${if (antes20) " — antes de las 20 semanas sugiere HTA crónica" else ""}. Repetir la toma en 4 horas.",
            F_PA)
    }
    if ((sist >= 140 || diast >= 90) && proteinuria != null && proteinuria in PROTEINURIA_POSITIVA && (egDias == null || egDias >= 20 * 7)) {
        out += AlertaEmbarazo("sospecha_preeclampsia", "alerta", "PA elevada con proteinuria $proteinuria en tira: sospecha de preeclampsia.", F_PA)
    }
    return out
}

/** Rango normal de la FCF (FIGO 2015), evaluado desde las 20 semanas. */
const val LCF_MIN = 110
const val LCF_MAX = 160

fun alertaLcf(lcf: Int?, egDias: Int?): AlertaEmbarazo? {
    if (lcf == null || (egDias != null && egDias < 20 * 7)) return null
    if (lcf < LCF_MIN) return AlertaEmbarazo("lcf_baja", "alerta", "LCF $lcf lpm (< 110): bradicardia fetal, evaluar bienestar fetal.", F_LCF)
    if (lcf > LCF_MAX) return AlertaEmbarazo("lcf_alta", "alerta", "LCF $lcf lpm (> 160): taquicardia fetal, descartar fiebre materna u otra causa.", F_LCF)
    return null
}

// ── Ganancia de peso (IOM 2009) ──

/** IMC con peso (kg) y talla (cm); null si falta o no es posible. */
fun imcEmb(peso: Double?, tallaCm: Double?): Double? {
    if (peso == null || tallaCm == null || peso <= 0 || tallaCm < 100) return null
    val m = tallaCm / 100
    return r1(peso / (m * m))
}

/** 'bajo' | 'normal' | 'sobrepeso' | 'obesidad'. */
fun categoriaImc(v: Double?): String? = when {
    v == null -> null
    v < 18.5 -> "bajo"
    v < 25 -> "normal"
    v < 30 -> "sobrepeso"
    else -> "obesidad"
}

val NOMBRE_CATEGORIA_IMC: Map<String, String> = mapOf(
    "bajo" to "Bajo peso", "normal" to "Normal", "sobrepeso" to "Sobrepeso", "obesidad" to "Obesidad",
)

data class RangoKg(val min: Double, val max: Double)

/** Ganancia total recomendada (kg) — IOM 2009, tabla S-1 (embarazo único). */
val GANANCIA_IOM: Map<String, RangoKg> = mapOf(
    "bajo" to RangoKg(12.5, 18.0), "normal" to RangoKg(11.5, 16.0),
    "sobrepeso" to RangoKg(7.0, 11.5), "obesidad" to RangoKg(5.0, 9.0),
)
/** Gemelar (guías provisionales del IOM 2009). Bajo peso: sin recomendación. */
val GANANCIA_IOM_GEMELAR: Map<String, RangoKg> = mapOf(
    "normal" to RangoKg(17.0, 25.0), "sobrepeso" to RangoKg(14.0, 23.0), "obesidad" to RangoKg(11.0, 19.0),
)

fun rangoGanancia(cat: String, multiple: Boolean = false): RangoKg? = if (multiple) GANANCIA_IOM_GEMELAR[cat] else GANANCIA_IOM[cat]

/** ¿El embarazo está marcado como múltiple? (riesgo 'embarazo_multiple'). */
fun esMultiple(riesgos: List<String>?): Boolean = riesgos.orEmpty().contains("embarazo_multiple")

/** "11.5–16 kg", "17–25 kg (gemelar)" o la aclaración de que el IOM no da rango. */
fun textoGananciaRecomendada(cat: String, multiple: Boolean = false): String {
    val r = rangoGanancia(cat, multiple) ?: return "sin rango del IOM para gemelares con bajo peso"
    return "${numEmb(r.min)}–${numEmb(r.max)} kg${if (multiple) " (gemelar)" else ""}"
}

/** Banda de ganancia esperada (kg) a una EG: 0 → 0,5–2 kg en la semana 13, luego lineal hasta el total en la 40. */
fun bandaIom(cat: String, egDias: Int, multiple: Boolean = false): RangoKg? = bandaIomEn(cat, egDias.toDouble(), multiple)

/** Lo mismo con la EG en días fraccionarios (los tests de la web usan 26,5 semanas). */
fun bandaIomEn(cat: String, egDias: Double, multiple: Boolean = false): RangoKg? {
    val sem = (egDias / 7.0).coerceIn(0.0, 40.0)
    val total = rangoGanancia(cat, multiple) ?: return null
    if (sem <= 13) {
        val f = sem / 13
        return RangoKg(r2(0.5 * f), r2(2.0 * f))
    }
    val f = (sem - 13) / (40 - 13)
    return RangoKg(r2(0.5 + (total.min - 0.5) * f), r2(2.0 + (total.max - 2.0) * f))
}

data class EvaluacionGanancia(val ganancia: Double, val banda: RangoKg, val categoria: String, val estado: String, val multiple: Boolean)

fun evaluarGanancia(peso: Double?, pesoPre: Double?, tallaCm: Double?, egDias: Int?, multiple: Boolean = false): EvaluacionGanancia? {
    val cat = categoriaImc(imcEmb(pesoPre, tallaCm))
    if (peso == null || pesoPre == null || cat == null || egDias == null) return null
    val ganancia = r1(peso - pesoPre)
    val banda = bandaIom(cat, egDias, multiple) ?: return null
    // Medio kilo de tolerancia: la balanza y la ropa no son de laboratorio.
    val estado = if (ganancia < banda.min - 0.5) "debajo" else if (ganancia > banda.max + 0.5) "encima" else "dentro"
    return EvaluacionGanancia(ganancia, banda, cat, estado, multiple)
}

// ── Controles mínimos y próximo control (NTS 105-MINSA) ──

const val MINIMO_CONTROLES_MINSA = 6
/** Hasta qué EG (días) debería haberse hecho cada una de las 6 atenciones. */
val VENTANAS_MINSA_DIAS = listOf(22 * 7 - 1, 22 * 7 - 1, 24 * 7 + 6, 29 * 7 + 6, 35 * 7 + 6, 40 * 7 + 6)

data class CumplimientoMinsa(val realizados: Int, val minimo: Int, val esperadosALaFecha: Int, val controlada: Boolean, val atrasada: Boolean)

fun cumplimientoMinsa(realizados: Int, egHoyDias: Int?): CumplimientoMinsa {
    val esperados = if (egHoyDias == null) 0 else VENTANAS_MINSA_DIAS.count { egHoyDias > it }
    return CumplimientoMinsa(realizados, MINIMO_CONTROLES_MINSA, esperados, realizados >= MINIMO_CONTROLES_MINSA, realizados < esperados)
}

/**
 * Próximo control: mensual hasta las 32 sem, quincenal 32–36, semanal desde la
 * 36; al cruzar de periodo se recorta a la semana 32 (o 36) sin bajar del
 * intervalo nuevo. Sin EG, en 4 semanas.
 */
fun proximoControlSugerido(fecha: String, egDias: Int?): String {
    if (egDias == null) return sumarDiasEmb(fecha, 28)
    val dias = when {
        egDias < 32 * 7 -> minOf(28, maxOf(14, 32 * 7 - egDias))
        egDias < 36 * 7 -> minOf(14, maxOf(7, 36 * 7 - egDias))
        else -> 7
    }
    return sumarDiasEmb(fecha, dias)
}

/** Suplementos por EG (sugerencia): ácido fólico; desde la 14 hierro; desde la 20 calcio. */
fun suplementosSugeridos(egDias: Int?): List<String> {
    if (egDias == null) return emptyList()
    val sem = egDias / 7.0
    if (sem < 14) return listOf("acido_folico")
    return if (sem < 20) listOf("acido_folico", "hierro") else listOf("acido_folico", "hierro", "calcio")
}

/** Riesgos deducidos de la edad, el Rh y las cesáreas (se sugieren, el profesional decide). */
fun riesgosSugeridos(edad: Int? = null, rh: String? = null, cesareas: Int? = null): List<String> {
    val out = mutableListOf<String>()
    if (edad != null && edad >= 35) out += "edad_35"
    if (edad != null && edad in 1..19) out += "adolescente"
    if (rh == "-") out += "rh_negativo"
    if ((cesareas ?: 0) > 0) out += "cesarea_previa"
    return out
}

/** Todas las alertas de apoyo de un control (lo más grave primero). [c].egDias ya resuelta. */
fun alertasControl(c: ControlPrenatal, emb: Embarazo? = null): List<AlertaEmbarazo> {
    val out = mutableListOf<AlertaEmbarazo>()
    val eg = c.egDias
    out += alertasPa(c.paSistolica, c.paDiastolica, eg, c.proteinuria)
    alertaLcf(c.lcf, eg)?.let { out += it }
    if (c.movimientosFetales == "ausentes") out += AlertaEmbarazo("mov_ausentes", "alerta", "Movimientos fetales ausentes: evaluar bienestar fetal de inmediato.")
    else if (c.movimientosFetales == "disminuidos") out += AlertaEmbarazo("mov_disminuidos", "atencion", "Movimientos fetales disminuidos: evaluar bienestar fetal.")
    val signos = c.signosAlarma.filter { s -> SIGNOS_ALARMA_EMB.any { it.id == s } }
    if (signos.isNotEmpty()) out += AlertaEmbarazo("signos_alarma", "alerta", "Refiere signos de alarma: ${signos.joinToString(", ") { nombreSignoAlarma(it) }}.")
    val au = evaluarAu(c.alturaUterina, eg)
    if (au != null && au.nivel != "ok") {
        val dif = (if (au.diferencia > 0) "+" else "") + numEmb(au.diferencia)
        out += AlertaEmbarazo(
            "au", au.nivel,
            "AU ${numEmb(c.alturaUterina!!)} cm a las ${textoEg(eg)}: $dif cm de lo esperado" +
                if (au.nivel == "alerta") " — ecografía para descartar ${if (au.diferencia < 0) "restricción del crecimiento u oligohidramnios" else "macrosomía, polihidramnios o embarazo múltiple"}."
                else " (fuera de EG ± 2 cm).",
            if (au.nivel == "alerta") F_AU else F_MCDONALD,
        )
    }
    val hta = c.paSistolica != null && (c.paSistolica >= 140 || (c.paDiastolica ?: 0) >= 90)
    if (!hta && c.proteinuria != null && c.proteinuria in PROTEINURIA_POSITIVA) {
        out += AlertaEmbarazo("proteinuria", "atencion", "Proteinuria ${c.proteinuria} en tira: controlar PA y descartar infección urinaria.")
    }
    if (c.edema == "+++") out += AlertaEmbarazo("edema", "atencion", "Edema +++: controlar PA y proteinuria.")
    val g = emb?.let { evaluarGanancia(c.peso, it.pesoPregestacional, it.tallaCm, eg, esMultiple(it.riesgos)) }
    if (g != null && g.estado != "dentro") {
        out += AlertaEmbarazo(
            "ganancia", "atencion",
            "Ganancia de peso ${if (g.ganancia > 0) "+" else ""}${numEmb(g.ganancia)} kg: ${if (g.estado == "debajo") "por debajo" else "por encima"} de lo esperado para su IMC pregestacional${if (g.multiple) " en un embarazo gemelar" else ""} (${numEmb(g.banda.min)}–${numEmb(g.banda.max)} kg a esta EG).",
            if (g.multiple) F_IOM_GEMELAR else F_IOM,
        )
    }
    val orden = mapOf("alerta" to 0, "atencion" to 1, "info" to 2)
    return out.sortedBy { orden[it.nivel] ?: 3 }
}

// ─── Resumen del embarazo (ficha, carné) ─────────────────────────────────────

/** "G3 P1 A1 C0" con lo que haya. */
fun formulaObstetrica(gestas: Int?, partos: Int?, abortos: Int?, cesareas: Int?): String? =
    listOf("G" to gestas, "P" to partos, "A" to abortos, "C" to cesareas)
        .filter { it.second != null }.joinToString(" ") { "${it.first}${it.second}" }.ifEmpty { null }

fun formulaObstetrica(e: Embarazo): String? = formulaObstetrica(e.gestas, e.partos, e.abortos, e.cesareas)

fun grupoRh(grupo: String?, rh: String?): String? {
    val rhTxt = rh?.let { if (it == "+") "Rh+" else "Rh−" }
    return if (grupo != null) grupo + (rhTxt?.let { " $it" } ?: "") else rhTxt
}

/** Activo primero, luego por FPP (el más reciente arriba). */
fun ordenarEmbarazos(lista: List<Embarazo>): List<Embarazo> =
    lista.sortedWith(compareBy<Embarazo> { if (it.activo) 0 else 1 }.thenByDescending { it.fpp }.thenByDescending { it.createdAt.orEmpty() })

/** Cronológico (el primero arriba): el cuadro del carné y los gráficos. */
fun ordenarControles(lista: List<ControlPrenatal>): List<ControlPrenatal> =
    lista.sortedWith(compareBy<ControlPrenatal> { it.fecha }.thenBy { it.createdAt.orEmpty() })

/** EG del control: la guardada, o la calculada con la datación. */
fun egDelControl(c: ControlPrenatal, d: Datacion?): Int? = c.egDias ?: d?.let { egDiasEn(it.inicio, c.fecha) }

data class ResumenEmbarazo(
    val datacion: Datacion?,
    val egHoyDias: Int?,
    val trimestre: Int?,
    val imcPregestacional: Double?,
    val categoriaImc: String?,
    val minsa: CumplimientoMinsa,
    val proximoControl: String?,
    val proximoVencido: Boolean,
    /** Alertas del ÚLTIMO control (apoyo). */
    val alertas: List<AlertaEmbarazo>,
    val diasParaFpp: Int?,
)

fun resumenEmbarazo(emb: Embarazo, controles: List<ControlPrenatal>, hoy: String): ResumenEmbarazo {
    val d = datacion(emb.entrada)
    val activo = emb.activo
    val egHoy = if (activo && d != null) egDiasEn(d.inicio, hoy) else null
    val orden = ordenarControles(controles)
    val ultimo = orden.lastOrNull()
    val egUltimo = ultimo?.let { egDelControl(it, d) }
    val proximo = when {
        !activo -> null
        ultimo != null -> ultimo.proximoControl ?: proximoControlSugerido(ultimo.fecha, egUltimo)
        else -> hoy
    }
    val imcPre = imcEmb(emb.pesoPregestacional, emb.tallaCm)
    return ResumenEmbarazo(
        datacion = d,
        egHoyDias = egHoy,
        trimestre = trimestre(egHoy),
        imcPregestacional = imcPre,
        categoriaImc = categoriaImc(imcPre),
        minsa = cumplimientoMinsa(orden.size, egHoy),
        proximoControl = proximo,
        proximoVencido = proximo != null && ultimo != null && proximo < hoy,
        alertas = if (ultimo != null) alertasControl(ultimo.copy(egDias = egUltimo), emb) else emptyList(),
        diasParaFpp = if (activo && d != null) diasEntreEmb(hoy, d.fpp) else null,
    )
}

// ─── Series para los gráficos (x = semanas de EG) ────────────────────────────

data class PuntoSerieEmb(val x: Double, val y: Double, val fecha: String)
data class PuntoBandaEmb(val x: Double, val min: Double, val max: Double)

/** AU de cada control con EG. */
fun serieAu(controles: List<ControlPrenatal>, d: Datacion?): List<PuntoSerieEmb> =
    ordenarControles(controles).mapNotNull { c ->
        val eg = egDelControl(c, d)
        val au = c.alturaUterina
        if (eg != null && au != null) PuntoSerieEmb(semanasDecimal(eg), au, c.fecha) else null
    }

/** Banda esperada de la AU (EG ± 2 cm) de la semana 20 a la 38. */
fun bandaAu(): List<PuntoBandaEmb> =
    (AU_DESDE_SEM..AU_HASTA_SEM).map { s -> PuntoBandaEmb(s.toDouble(), s - TOLERANCIA_AU_BANDA, s + TOLERANCIA_AU_BANDA) }

/** Ganancia de peso (kg) de cada control, si hay peso pregestacional. */
fun serieGanancia(controles: List<ControlPrenatal>, d: Datacion?, pesoPre: Double?): List<PuntoSerieEmb> {
    if (pesoPre == null) return emptyList()
    return ordenarControles(controles).mapNotNull { c ->
        val eg = egDelControl(c, d)
        val p = c.peso
        if (eg != null && p != null) PuntoSerieEmb(semanasDecimal(eg), r1(p - pesoPre), c.fecha) else null
    }
}

/** Banda IOM (única o gemelar), cada 2 semanas de la 0 a la 40; vacía si el IOM no da rango. */
fun bandaGanancia(cat: String, multiple: Boolean = false): List<PuntoBandaEmb> =
    (0..40 step 2).mapNotNull { s -> bandaIom(cat, s * 7, multiple)?.let { PuntoBandaEmb(s.toDouble(), it.min, it.max) } }

/** PA sistólica y diastólica de cada control con EG. */
fun seriePa(controles: List<ControlPrenatal>, d: Datacion?): Pair<List<PuntoSerieEmb>, List<PuntoSerieEmb>> {
    val sist = mutableListOf<PuntoSerieEmb>()
    val diast = mutableListOf<PuntoSerieEmb>()
    for (c in ordenarControles(controles)) {
        val eg = egDelControl(c, d) ?: continue
        if (c.paSistolica == null || c.paDiastolica == null) continue
        sist += PuntoSerieEmb(semanasDecimal(eg), c.paSistolica.toDouble(), c.fecha)
        diast += PuntoSerieEmb(semanasDecimal(eg), c.paDiastolica.toDouble(), c.fecha)
    }
    return sist to diast
}

// ─── Validación (los mismos mensajes que la API) ─────────────────────────────

object LimitesEmbarazo {
    const val ECO_SEM_MIN = 4
    const val ECO_SEM_MAX = 42
    const val FORMULA = 30
    const val PESO_MIN = 25.0
    const val PESO_MAX = 250.0
    const val TALLA_MIN = 120.0
    const val TALLA_MAX = 210.0
    const val ANTECEDENTES = 2000
}

object LimitesControl {
    const val PESO_MIN = 20.0
    const val PESO_MAX = 250.0
    const val PAS_MIN = 50.0
    const val PAS_MAX = 260.0
    const val PAD_MIN = 30.0
    const val PAD_MAX = 160.0
    const val AU_MIN = 5.0
    const val AU_MAX = 50.0
    const val LCF_MIN = 50.0
    const val LCF_MAX = 240.0
    const val OBSERVACIONES = 4000
}

private fun vacioEmb(v: String?): Boolean = v == null || v == ""

private fun revisarNumero(e: MutableList<String>, v: String?, nombre: String, min: Double, max: Double, enteroObligatorio: Boolean = false) {
    if (vacioEmb(v)) return
    val n = if (enteroObligatorio) enteroEmb(v)?.toDouble() else aNumeroEmb(v)
    if (n == null) e += "$nombre: no es un número${if (enteroObligatorio) " entero" else ""}."
    else if (n < min || n > max) e += "$nombre: fuera de rango (${numEmb(min)}–${numEmb(max)})."
}

/** Lo que arma el formulario del embarazo (los números como los escribió). */
data class BorradorEmbarazo(
    val pacienteId: String,
    val especialidadId: String? = null,
    val fur: String? = null,
    val furIncierta: Boolean = false,
    val ecoFecha: String? = null,
    val ecoEgSemanas: String? = null,
    val ecoEgDias: String? = null,
    val gestas: String? = null,
    val partos: String? = null,
    val abortos: String? = null,
    val cesareas: String? = null,
    val grupoSanguineo: String? = null,
    val rh: String? = null,
    val pesoPregestacional: String? = null,
    val tallaCm: String? = null,
    val riesgos: List<String> = emptyList(),
    val antecedentes: String? = null,
    val estado: String = "activo",
    val desenlace: String? = null,
    val fechaFin: String? = null,
) {
    val entrada: EntradaDatacion
        get() = EntradaDatacion(
            fur = fur?.takeIf { esFechaIsoEmb(it) },
            furIncierta = furIncierta,
            ecoFecha = ecoFecha?.takeIf { esFechaIsoEmb(it) },
            ecoEgSemanas = enteroEmb(ecoEgSemanas),
            ecoEgDias = enteroEmb(ecoEgDias),
        )
}

private fun Int?.txt(): String? = this?.toString()
private fun Double?.txt(): String? = this?.let { numEmb(it) }

/** Fila → borrador (para finalizar / reabrir con la misma validación). */
fun borradorDesdeEmbarazo(f: Embarazo): BorradorEmbarazo = BorradorEmbarazo(
    pacienteId = f.pacienteId, especialidadId = f.especialidadId,
    fur = f.fur, furIncierta = f.furIncierta, ecoFecha = f.ecoFecha, ecoEgSemanas = f.ecoEgSemanas.txt(), ecoEgDias = f.ecoEgDias.txt(),
    gestas = f.gestas.txt(), partos = f.partos.txt(), abortos = f.abortos.txt(), cesareas = f.cesareas.txt(),
    grupoSanguineo = f.grupoSanguineo, rh = f.rh, pesoPregestacional = f.pesoPregestacional.txt(), tallaCm = f.tallaCm.txt(),
    riesgos = f.riesgos, antecedentes = f.antecedentes, estado = f.estado, desenlace = f.desenlace, fechaFin = f.fechaFin,
)

/** Errores para mostrar (vacío = válido). [hoy] = hoy de la sede de la paciente. */
fun validarEmbarazo(b: BorradorEmbarazo, hoy: String): List<String> {
    val e = mutableListOf<String>()
    if (b.pacienteId.isBlank()) e += "Falta la paciente."
    if (!vacioEmb(b.fur) && !esFechaIsoEmb(b.fur)) e += "FUR inválida."
    else if (!b.fur.isNullOrEmpty() && b.fur > hoy) e += "La FUR no puede ser una fecha futura."
    val conEco = !vacioEmb(b.ecoFecha) || !vacioEmb(b.ecoEgSemanas)
    if (conEco) {
        if (!esFechaIsoEmb(b.ecoFecha)) e += "Ecografía: falta la fecha."
        else if (b.ecoFecha!! > hoy) e += "La fecha de la ecografía no puede ser futura."
        if (vacioEmb(b.ecoEgSemanas)) e += "Ecografía: falta la edad gestacional que dio (semanas)."
        revisarNumero(e, b.ecoEgSemanas, "Semanas de la ecografía", LimitesEmbarazo.ECO_SEM_MIN.toDouble(), LimitesEmbarazo.ECO_SEM_MAX.toDouble(), true)
        revisarNumero(e, b.ecoEgDias, "Días de la ecografía", 0.0, 6.0, true)
    }
    if (vacioEmb(b.fur) && !conEco) e += "Indica la FUR o una ecografía precoz (fecha y edad gestacional) para datar el embarazo."
    listOf(Triple(b.gestas, "Gestas", 1.0), Triple(b.partos, "Partos", 0.0), Triple(b.abortos, "Abortos", 0.0), Triple(b.cesareas, "Cesáreas", 0.0))
        .forEach { (v, nombre, min) -> revisarNumero(e, v, nombre, min, LimitesEmbarazo.FORMULA.toDouble(), true) }
    if (!vacioEmb(b.grupoSanguineo) && b.grupoSanguineo !in GRUPOS_SANGUINEOS) e += "Grupo sanguíneo inválido."
    if (!vacioEmb(b.rh) && b.rh != "+" && b.rh != "-") e += "Rh inválido."
    revisarNumero(e, b.pesoPregestacional, "Peso pregestacional", LimitesEmbarazo.PESO_MIN, LimitesEmbarazo.PESO_MAX)
    revisarNumero(e, b.tallaCm, "Talla", LimitesEmbarazo.TALLA_MIN, LimitesEmbarazo.TALLA_MAX)
    if ((b.antecedentes?.trim().orEmpty()).length > LimitesEmbarazo.ANTECEDENTES) e += "Antecedentes: texto muy largo (máx. ${LimitesEmbarazo.ANTECEDENTES})."
    val estado = b.estado
    if (estado != "activo" && estado != "finalizado") e += "Estado inválido."
    if (estado == "finalizado") {
        if (DESENLACES_EMB.none { it.id == b.desenlace }) e += "Indica cómo terminó el embarazo (parto, cesárea, aborto u otro)."
        if (!esFechaIsoEmb(b.fechaFin)) e += "Indica la fecha en que terminó el embarazo."
        else if (b.fechaFin!! > hoy) e += "La fecha de término no puede ser futura."
    }
    // Una FUR/eco que ya no da un embarazo en curso (más de 44 sem) solo vale si se cierra.
    if (e.isEmpty() && estado == "activo") {
        val d = datacion(b.entrada)
        if (d != null && diasEntreEmb(d.inicio, hoy) > MAX_DIAS_EG) {
            e += "Con esa FUR / ecografía el embarazo tendría más de 44 semanas: revisa la fecha o regístralo como finalizado."
        }
    }
    // El fin no puede ser antes del inicio (día 0 de la datación).
    if (e.isEmpty() && estado == "finalizado") {
        val d = datacion(b.entrada)
        if (d != null && b.fechaFin != null && b.fechaFin < d.inicio) {
            e += "La fecha de término no puede ser anterior al inicio del embarazo (${fechaEmb(d.inicio)})."
        }
    }
    return e
}

/** Avisos que NO bloquean (fórmula obstétrica incoherente). */
fun avisosEmbarazo(b: BorradorEmbarazo): List<String> {
    val g = enteroEmb(b.gestas) ?: return emptyList()
    val p = enteroEmb(b.partos) ?: 0
    val a = enteroEmb(b.abortos) ?: 0
    val c = enteroEmb(b.cesareas) ?: 0
    return if (g < p + a + c + 1)
        listOf("Gestas ($g) es menor que partos + abortos + cesáreas + el embarazo actual (${p + a + c + 1}): revisa la fórmula (salvo gemelares).")
    else emptyList()
}

/**
 * ¿Ese control cabe en ESE embarazo? (la misma regla que la API: 400
 * FUERA_DEL_EMBARAZO). Se rechaza: antes del inicio o con más de 44 semanas; un
 * control NUEVO en un embarazo finalizado; editar uno a una fecha posterior al fin.
 */
fun validarControlEnEmbarazo(fecha: String, emb: Embarazo, editando: Boolean): List<String> {
    val e = mutableListOf<String>()
    if (emb.estado == "finalizado" && !editando) {
        e += "Este embarazo ya está finalizado: no se le agregan controles. Si se finalizó por error, reábrelo."
        return e
    }
    val d = datacion(emb.entrada) ?: return e
    if (!esFechaIsoEmb(fecha)) return e
    if (fecha < d.inicio) e += "La fecha del control es anterior al inicio del embarazo (${fechaEmb(d.inicio)})."
    else if (diasEntreEmb(d.inicio, fecha) > MAX_DIAS_EG) e += "Con esa fecha el embarazo tendría más de 44 semanas: revisa la fecha del control o la FUR."
    if (emb.estado == "finalizado" && emb.fechaFin != null && fecha > emb.fechaFin) {
        e += "La fecha del control es posterior al fin del embarazo (${fechaEmb(emb.fechaFin)})."
    }
    return e
}

/** Lo que arma el formulario del control (los números como los escribió). */
data class BorradorControl(
    val embarazoId: String,
    val fecha: String? = null,
    /** Solo si se corrigió a mano (si no, la calcula el servidor). */
    val egDias: String? = null,
    val peso: String? = null,
    val paSistolica: String? = null,
    val paDiastolica: String? = null,
    val alturaUterina: String? = null,
    val lcf: String? = null,
    val movimientosFetales: String? = null,
    val presentacion: String? = null,
    val edema: String? = null,
    val proteinuria: String? = null,
    val suplementos: List<String> = emptyList(),
    val signosAlarma: List<String> = emptyList(),
    val signosAlarmaExplicados: Boolean = false,
    val observaciones: String? = null,
    val proximoControl: String? = null,
    val citaId: String? = null,
    /** Idempotencia del POST (una por formulario; la misma en cada reintento). */
    val clave: String? = null,
)

/** Forma válida de la clave de idempotencia (8–100 caracteres seguros). */
val RE_CLAVE_IDEMPOTENCIA = Regex("^[A-Za-z0-9_.:-]{8,100}$")

private fun enListaEmb(lista: List<OpcionEmb>, v: String?): Boolean = vacioEmb(v) || lista.any { it.id == v }

/** Errores para mostrar (vacío = válido). */
fun validarControl(b: BorradorControl, hoy: String): List<String> {
    val e = mutableListOf<String>()
    if (b.embarazoId.isBlank()) e += "Falta el embarazo."
    if (!vacioEmb(b.fecha) && !esFechaIsoEmb(b.fecha)) e += "Fecha inválida."
    else if (!b.fecha.isNullOrEmpty() && b.fecha > hoy) e += "La fecha del control no puede ser futura."
    revisarNumero(e, b.egDias, "Edad gestacional (días)", 0.0, MAX_DIAS_EG.toDouble(), true)
    revisarNumero(e, b.peso, "Peso", LimitesControl.PESO_MIN, LimitesControl.PESO_MAX)
    revisarNumero(e, b.paSistolica, "PA sistólica", LimitesControl.PAS_MIN, LimitesControl.PAS_MAX, true)
    revisarNumero(e, b.paDiastolica, "PA diastólica", LimitesControl.PAD_MIN, LimitesControl.PAD_MAX, true)
    if (vacioEmb(b.paSistolica) != vacioEmb(b.paDiastolica)) e += "La presión arterial necesita sistólica y diastólica."
    else if (!vacioEmb(b.paSistolica) && (aNumeroEmb(b.paSistolica) ?: 0.0) <= (aNumeroEmb(b.paDiastolica) ?: 0.0)) {
        e += "La sistólica tiene que ser mayor que la diastólica."
    }
    revisarNumero(e, b.alturaUterina, "Altura uterina", LimitesControl.AU_MIN, LimitesControl.AU_MAX)
    revisarNumero(e, b.lcf, "LCF", LimitesControl.LCF_MIN, LimitesControl.LCF_MAX, true)
    if (!enListaEmb(MOVIMIENTOS_FETALES, b.movimientosFetales)) e += "Movimientos fetales: valor inválido."
    if (!enListaEmb(PRESENTACIONES_FETALES, b.presentacion)) e += "Presentación: valor inválido."
    if (!enListaEmb(EDEMAS_EMB, b.edema)) e += "Edema: valor inválido."
    if (!enListaEmb(PROTEINURIAS_EMB, b.proteinuria)) e += "Proteinuria: valor inválido."
    if ((b.observaciones?.trim().orEmpty()).length > LimitesControl.OBSERVACIONES) e += "Observaciones: texto muy largo (máx. ${LimitesControl.OBSERVACIONES})."
    if (!vacioEmb(b.proximoControl) && !esFechaIsoEmb(b.proximoControl)) e += "Próximo control: fecha inválida."
    else if (!b.proximoControl.isNullOrEmpty() && esFechaIsoEmb(b.fecha) && b.proximoControl <= b.fecha!!) {
        e += "El próximo control tiene que ser después de la fecha del control."
    }
    if (!vacioEmb(b.clave) && !RE_CLAVE_IDEMPOTENCIA.matches(b.clave!!)) {
        e += "Clave de idempotencia inválida (8–100 caracteres: letras, números, - _ . :)."
    }
    // Un control vacío no sirve: al menos una medida.
    val medidas = listOf(b.peso, b.paSistolica, b.alturaUterina, b.lcf, b.movimientosFetales, b.presentacion, b.edema, b.proteinuria, b.observaciones)
    if (e.isEmpty() && medidas.all { vacioEmb(it) } && b.signosAlarma.isEmpty()) {
        e += "Registra al menos una medida (peso, PA, AU, LCF…) u observación."
    }
    return e
}

/** Gemelo de `filaControl`: el control como lo guardaría el servidor (para mostrarlo mientras espera en la cola). */
fun controlLocal(b: BorradorControl, d: Datacion?, hoy: String, id: String, pacienteId: String): ControlPrenatal {
    val fecha = b.fecha?.takeIf { esFechaIsoEmb(it) } ?: hoy
    val eg = enteroEmb(b.egDias) ?: d?.let { egDiasEn(it.inicio, fecha) }
    fun lista(l: List<OpcionEmb>, v: String?) = v?.takeIf { x -> l.any { it.id == x } }
    return ControlPrenatal(
        id = id, embarazoId = b.embarazoId, pacienteId = pacienteId, citaId = b.citaId, fecha = fecha, egDias = eg,
        peso = aNumeroEmb(b.peso)?.let(::r1), paSistolica = enteroEmb(b.paSistolica), paDiastolica = enteroEmb(b.paDiastolica),
        alturaUterina = aNumeroEmb(b.alturaUterina)?.let(::r1), lcf = enteroEmb(b.lcf),
        movimientosFetales = lista(MOVIMIENTOS_FETALES, b.movimientosFetales), presentacion = lista(PRESENTACIONES_FETALES, b.presentacion),
        edema = lista(EDEMAS_EMB, b.edema), proteinuria = lista(PROTEINURIAS_EMB, b.proteinuria),
        suplementos = b.suplementos.filter { s -> SUPLEMENTOS_EMB.any { it.id == s } }.distinct(),
        signosAlarma = b.signosAlarma.filter { s -> SIGNOS_ALARMA_EMB.any { it.id == s } }.distinct(),
        signosAlarmaExplicados = b.signosAlarmaExplicados,
        observaciones = b.observaciones?.trim()?.ifEmpty { null },
        proximoControl = b.proximoControl?.takeIf { esFechaIsoEmb(it) } ?: proximoControlSugerido(fecha, eg),
        pendiente = true,
    )
}

// ─── Cuerpos de la API (camelCase) ───────────────────────────────────────────

private fun kotlinx.serialization.json.JsonObjectBuilder.num(k: String, v: String?, entero: Boolean = false) {
    val n = if (entero) enteroEmb(v)?.toDouble() else aNumeroEmb(v)
    when {
        n == null -> put(k, JsonNull)
        n == floor(n) -> put(k, n.toLong())
        else -> put(k, n)
    }
}
private fun kotlinx.serialization.json.JsonObjectBuilder.lista(k: String, l: List<String>) = put(k, buildJsonArray { l.forEach { add(JsonPrimitive(it)) } })

/** POST (sin [id], con pacienteId) o PATCH (con [id]) del embarazo. */
fun cuerpoEmbarazo(b: BorradorEmbarazo, id: String? = null): JsonObject = buildJsonObject {
    if (id != null) put("id", id) else put("pacienteId", b.pacienteId)
    put("especialidadId", b.especialidadId)
    put("fur", b.fur?.takeIf { it.isNotBlank() })
    put("furIncierta", b.furIncierta)
    put("ecoFecha", b.ecoFecha?.takeIf { it.isNotBlank() })
    num("ecoEgSemanas", b.ecoEgSemanas, entero = true)
    num("ecoEgDias", b.ecoEgDias, entero = true)
    num("gestas", b.gestas, entero = true)
    num("partos", b.partos, entero = true)
    num("abortos", b.abortos, entero = true)
    num("cesareas", b.cesareas, entero = true)
    put("grupoSanguineo", b.grupoSanguineo)
    put("rh", b.rh)
    num("pesoPregestacional", b.pesoPregestacional)
    num("tallaCm", b.tallaCm)
    lista("riesgos", b.riesgos)
    put("antecedentes", b.antecedentes?.trim()?.ifEmpty { null })
}

/** PATCH de finalizar (desenlace + fecha) o reabrir (estado activo). */
fun cuerpoEstadoEmbarazo(id: String, finalizar: Boolean, desenlace: String? = null, fechaFin: String? = null): JsonObject = buildJsonObject {
    put("id", id)
    put("estado", if (finalizar) "finalizado" else "activo")
    if (finalizar) { put("desenlace", desenlace); put("fechaFin", fechaFin) }
}

/** POST (sin [id]: lleva embarazoId, citaId y la clave) o PATCH (con [id]) del control. */
fun cuerpoControl(b: BorradorControl, id: String? = null): JsonObject = buildJsonObject {
    if (id != null) put("id", id) else {
        put("embarazoId", b.embarazoId)
        b.citaId?.let { put("citaId", it) }
        b.clave?.let { put("clave", it) }
    }
    put("fecha", b.fecha)
    num("egDias", b.egDias, entero = true)
    num("peso", b.peso)
    num("paSistolica", b.paSistolica, entero = true)
    num("paDiastolica", b.paDiastolica, entero = true)
    num("alturaUterina", b.alturaUterina)
    num("lcf", b.lcf, entero = true)
    put("movimientosFetales", b.movimientosFetales)
    put("presentacion", b.presentacion)
    put("edema", b.edema)
    put("proteinuria", b.proteinuria)
    lista("suplementos", b.suplementos)
    lista("signosAlarma", b.signosAlarma)
    put("signosAlarmaExplicados", b.signosAlarmaExplicados)
    put("observaciones", b.observaciones?.trim()?.ifEmpty { null })
    put("proximoControl", b.proximoControl)
}

// ─── Lectura (filas de la base, snake_case) ──────────────────────────────────

private fun JsonElement?.primEmb(): JsonPrimitive? = (this as? JsonPrimitive)?.takeIf { it !is JsonNull }
private fun JsonObject.txtEmb(k: String): String? = this[k].primEmb()?.content?.trim()?.takeIf { it.isNotEmpty() && it != "null" }
private fun JsonObject.numEmbJ(k: String): Double? = aNumeroEmb(txtEmb(k))
private fun JsonObject.intEmb(k: String): Int? = numEmbJ(k)?.takeIf { it == floor(it) }?.toInt()
private fun JsonObject.boolEmb(k: String): Boolean = this[k].primEmb()?.let { it.booleanOrNull ?: (it.content == "true") } ?: false
private fun JsonObject.listaEmb(k: String): List<String> =
    (this[k] as? JsonArray).orEmpty().mapNotNull { it.primEmb()?.content?.takeIf { s -> s.isNotBlank() } }

fun embarazoDeJson(o: JsonObject): Embarazo? {
    val id = o.txtEmb("id") ?: return null
    return Embarazo(
        id = id, pacienteId = o.txtEmb("paciente_id").orEmpty(), terapeutaId = o.txtEmb("terapeuta_id"),
        especialidadId = o.txtEmb("especialidad_id"), fur = o.txtEmb("fur")?.take(10), furIncierta = o.boolEmb("fur_incierta"),
        ecoFecha = o.txtEmb("eco_fecha")?.take(10), ecoEgSemanas = o.intEmb("eco_eg_semanas"), ecoEgDias = o.intEmb("eco_eg_dias"),
        fpp = o.txtEmb("fpp")?.take(10).orEmpty(), metodoDatacion = o.txtEmb("metodo_datacion") ?: "fur",
        gestas = o.intEmb("gestas"), partos = o.intEmb("partos"), abortos = o.intEmb("abortos"), cesareas = o.intEmb("cesareas"),
        grupoSanguineo = o.txtEmb("grupo_sanguineo"), rh = o.txtEmb("rh"),
        pesoPregestacional = o.numEmbJ("peso_pregestacional"), tallaCm = o.numEmbJ("talla_cm"),
        riesgos = o.listaEmb("riesgos"), antecedentes = o.txtEmb("antecedentes"),
        estado = o.txtEmb("estado") ?: "activo", desenlace = o.txtEmb("desenlace"), fechaFin = o.txtEmb("fecha_fin")?.take(10),
        createdAt = o.txtEmb("created_at"),
    )
}

fun controlDeJson(o: JsonObject): ControlPrenatal? {
    val id = o.txtEmb("id") ?: return null
    return ControlPrenatal(
        id = id, embarazoId = o.txtEmb("embarazo_id").orEmpty(), pacienteId = o.txtEmb("paciente_id").orEmpty(),
        citaId = o.txtEmb("cita_id"), terapeutaId = o.txtEmb("terapeuta_id"), fecha = o.txtEmb("fecha")?.take(10).orEmpty(),
        egDias = o.intEmb("eg_dias"), peso = o.numEmbJ("peso"), paSistolica = o.intEmb("pa_sistolica"), paDiastolica = o.intEmb("pa_diastolica"),
        alturaUterina = o.numEmbJ("altura_uterina"), lcf = o.intEmb("lcf"),
        movimientosFetales = o.txtEmb("movimientos_fetales"), presentacion = o.txtEmb("presentacion"),
        edema = o.txtEmb("edema"), proteinuria = o.txtEmb("proteinuria"),
        suplementos = o.listaEmb("suplementos"), signosAlarma = o.listaEmb("signos_alarma"),
        signosAlarmaExplicados = o.boolEmb("signos_alarma_explicados"), observaciones = o.txtEmb("observaciones"),
        proximoControl = o.txtEmb("proximo_control")?.take(10), createdAt = o.txtEmb("created_at"),
    )
}

// ─── Observaciones: chips base + autocompletado que aprende ──────────────────

val CHIPS_OBS_CONTROL: List<String> = listOf(
    "Gestante asintomática", "Niega signos de alarma", "Útero grávido acorde a la EG", "Se solicita ecografía obstétrica",
    "Se solicitan exámenes de laboratorio", "Se solicita ecografía morfológica", "Se indica test de tolerancia a la glucosa",
    "Vacunación al día", "Se refuerza plan de parto", "Psicoprofilaxis obstétrica", "Control de PA en 1 semana",
    "Se deriva a emergencia",
)

/** GET /api/staff/control-prenatal/sugerencias → { observaciones: [{ texto, usos }] }. */
fun aprendidosControlDeJson(o: JsonObject?): List<AprendidoPlan> = (o?.get("observaciones") as? JsonArray).orEmpty().mapNotNull { x ->
    when (x) {
        is JsonPrimitive -> x.takeIf { it !is JsonNull && it.isString }?.content?.trim()?.takeIf { it.isNotEmpty() }?.let { AprendidoPlan(it, 1) }
        is JsonObject -> x.txtEmb("texto")?.let { AprendidoPlan(it, x.numEmbJ("usos")?.toInt()) }
        else -> null
    }
}

/** Chips (base ∪ aprendidos) y autocompletado de las observaciones (por línea). */
fun frasesObservacionControl(aprendidos: List<AprendidoPlan>?): FrasesCampoPlan =
    frasesCampoPlan(CHIPS_OBS_CONTROL, aprendidos, porLinea = true)
