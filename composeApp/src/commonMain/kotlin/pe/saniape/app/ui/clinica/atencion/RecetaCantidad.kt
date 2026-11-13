package pe.saniape.app.ui.clinica.atencion

import kotlin.math.ceil
import kotlin.math.roundToInt

// Cantidad total sugerida de la receta: dosis por toma × tomas al día × días.
//
// Gemelo de lib/recetas.ts (cantidadSugerida) de la web (12/11/2026, Jonathan:
// «a veces no aparece la cantidad total… quizás porque hay letras y números en
// la dosis, frecuencia»; y la app no la calculaba: se escribía a mano). Entiende
// lo que de verdad se escribe: «c/8h», «cada ocho horas», «3 x día», «1 tab VO»,
// «500 mg» cuando cuadra con la concentración, «por siete días». Lo que no se
// puede contar con certeza (jarabes en ml, gotas, «si hay dolor», «uso
// continuo») devuelve null y queda a mano.

private val NUM_PALABRA = mapOf(
    "un" to 1.0, "una" to 1.0, "uno" to 1.0, "dos" to 2.0, "tres" to 3.0, "cuatro" to 4.0, "cinco" to 5.0,
    "seis" to 6.0, "siete" to 7.0, "ocho" to 8.0, "nueve" to 9.0, "diez" to 10.0, "once" to 11.0, "doce" to 12.0,
    "catorce" to 14.0, "quince" to 15.0, "veinte" to 20.0, "veintiuno" to 21.0, "veinticuatro" to 24.0,
    "treinta" to 30.0, "cuarenta" to 40.0, "cuarentaiocho" to 48.0, "sesenta" to 60.0, "setenta" to 70.0, "noventa" to 90.0,
)
private val PAL = NUM_PALABRA.keys.sortedByDescending { it.length }.joinToString("|")

/** Un número en cifras o en palabras. */
private val N = """(\d+(?:[.,]\d+)?|$PAL)"""
private fun numDe(s: String): Double = NUM_PALABRA[s] ?: s.replace(',', '.').toDoubleOrNull() ?: Double.NaN

private val SEGUN_NECESIDAD = Regex("""condicional|si (hay )?dolor|si fiebre|sos\b|prn\b|segun|necesidad|a demanda""")
private val DOSIS_UNICA = Regex("""dosis unica|unica vez|una sola vez|stat\b""")
private val CADA_HORAS = Regex("""cada $N ?(?:h\b|hs\b|hrs?\b|horas?)""")
private val CADA_DIAS = Regex("""cada $N ?dias?""")
private val CADA_A_SECAS = Regex("""cada (\d+)$""")
private val VECES_DIA = Regex("""$N ?(?:veces|vez|v)? ?(?:al|por) dia""")
private val VECES_SEMANA = Regex("""$N ?(?:veces|vez|v)? ?(?:a la|por) semana""")
private val MOMENTOS = Regex("""\b(desayuno|almuerzo|cena|manana|mediodia|tarde|noche)\b""")
private val UNA_AL_DIA = Regex("""diari|cada dia|al dia|antes de dormir|al acostarse|en la noche|por la noche|cada noche|en ayunas|cada manana|por la manana|en la manana""")

/** Tomas al día según la frecuencia escrita ("cada 8 horas" → 3). null = no se puede saber. */
internal fun tomasPorDia(frecuencia: String?): Double? {
    // «c/8h», «q8h» → «cada 8 h»; «x día», «/día» → «por dia».
    val f = sinTildes(frecuencia.orEmpty()).trim()
        .replace(Regex("""\bc\s*/\s*|\bq(?=\s*\d)"""), "cada ")
        .replace(Regex("""\s*(?:/|\bx)\s*(dia|semana)\b""")) { " por ${it.groupValues[1]}" }
        .replace(Regex("""\s+"""), " ")
    if (f.isEmpty()) return null
    if (SEGUN_NECESIDAD.containsMatchIn(f)) return null
    if (DOSIS_UNICA.containsMatchIn(f)) return 1.0
    CADA_HORAS.find(f)?.let { val h = numDe(it.groupValues[1]); return if (h > 0) 24 / h else null }
    CADA_DIAS.find(f)?.let { val d = numDe(it.groupValues[1]); return if (d > 0) 1 / d else null }
    // «cada 8» a secas: son horas.
    CADA_A_SECAS.find(f)?.let { val h = it.groupValues[1].toDouble(); return if (h > 0 && h <= 48) 24 / h else null }
    VECES_DIA.find(f)?.let { return numDe(it.groupValues[1]) }
    VECES_SEMANA.find(f)?.let { return numDe(it.groupValues[1]) / 7 }
    if (Regex("""\bbid\b""").containsMatchIn(f)) return 2.0
    if (Regex("""\btid\b""").containsMatchIn(f)) return 3.0
    if (Regex("""\bqid\b""").containsMatchIn(f)) return 4.0
    if (Regex("""interdiari|dia por medio|dejando un dia""").containsMatchIn(f)) return 0.5
    if (Regex("""semanal|cada semana""").containsMatchIn(f)) return 1.0 / 7
    // «mañana y noche», «desayuno, almuerzo y cena»: una toma por momento nombrado.
    val momentos = MOMENTOS.findAll(f).map { it.value }.toSet()
    if (momentos.size >= 2) return momentos.size.toDouble()
    if (UNA_AL_DIA.containsMatchIn(f)) return 1.0
    return null
}

private val DURACION = Regex("""$N ?(dias?|d\b|semanas?|sem\b|mes(?:es)?)""")

/** Días de tratamiento ("7 días" → 7, "2 semanas" → 14, "1 mes" → 30). null = continuo o no se entiende. */
internal fun diasDeDuracion(duracion: String?): Double? {
    val d = sinTildes(duracion.orEmpty()).trim()
    if (d.isEmpty() || Regex("""continu|indefinid|permanente|cronic""").containsMatchIn(d)) return null
    Regex("""^(\d+(?:[.,]\d+)?)$""").find(d)?.let { return numDe(it.groupValues[1]) }
    val m = DURACION.find(d) ?: return null
    val n = numDe(m.groupValues[1])
    return when {
        m.groupValues[2].startsWith("sem") -> n * 7
        m.groupValues[2].startsWith("mes") -> n * 30
        else -> n
    }
}

/** Unidades que se CUENTAN (una por toma): tabletas, cápsulas, sobres… */
private val UNIDADES_CONTABLES = Regex("""^(tabletas?|tabs?|tbl?s?|comprimidos?|comps?|cp|capsulas?|caps?|grageas?|pastillas?|pildoras?|sobres?|supositorios?|ovulos?|ampollas?|amps?|ampolletas?|parches?|unidad(es)?|und?s?)$""")

/** Formas que se venden por unidad contable (su `unidadSugerida`). */
private val FORMA_CONTABLE = setOf("tabletas", "cápsulas", "ampollas", "supositorios", "óvulos", "parches", "sobres")
private val UNIDAD_DE_MASA = Regex("""^(mg|mcg|ug|g|gr|ui)\b""")
private val MASA = Regex("""^(\d+(?:[.,]\d+)?)\s*(mg|mcg|ug|g|gr|ui)\b""")

/** «500 mg» → (500, "mg"), para comparar la dosis con la concentración. */
private fun masaDe(txt: String?): Pair<Double, String>? {
    val m = MASA.find(sinTildes(txt.orEmpty()).trim()) ?: return null
    val n = numDe(m.groupValues[1])
    val u = when (m.groupValues[2]) { "gr" -> "g"; "ug" -> "mcg"; else -> m.groupValues[2] }
    return if (n > 0) n to u else null
}

private val FRACCION = Regex("""^(?:(\d+)\s+)?(\d+)\s*/\s*(\d+)\s*(.*)$""")
private val DECIMAL = Regex("""^(\d+(?:[.,]\d+)?)\s*(.*)$""")
private val EN_PALABRA = Regex("""^(media|medio|$PAL)\s+(.*)$""")

/**
 * Unidades por toma ("1 tableta" → 1, "1/2 tableta" → 0.5, "2" → 2). null si la
 * dosis no se cuenta en unidades de la forma (5 ml de jarabe, 20 gotas). Lo que
 * sigue a la unidad no estorba ("1 tableta vía oral"). Una dosis en masa
 * ("500 mg") vale solo si la forma se cuenta por unidad y cuadra con la
 * concentración: 500 mg de tabletas de 500 mg es 1.
 */
internal fun unidadesPorToma(dosis: String?, forma: String?, concentracion: String? = null): Double? {
    val d = sinTildes(dosis.orEmpty().replace("½", " 1/2")).trim()
    if (d.isEmpty()) return null
    val n: Double
    var unidad: String
    val frac = FRACCION.find(d)
    val dec = DECIMAL.find(d)
    val pal = EN_PALABRA.find(d)
    if (frac != null && (frac.groupValues[3].toDoubleOrNull() ?: 0.0) > 0) {
        n = (frac.groupValues[1].toDoubleOrNull() ?: 0.0) + frac.groupValues[2].toDouble() / frac.groupValues[3].toDouble()
        unidad = frac.groupValues[4]
    } else if (dec != null) {
        n = numDe(dec.groupValues[1]); unidad = dec.groupValues[2]
    } else if (pal != null) {
        n = if (pal.groupValues[1] == "media" || pal.groupValues[1] == "medio") 0.5 else numDe(pal.groupValues[1])
        unidad = pal.groupValues[2]
    } else return null
    if (!(n > 0)) return null
    unidad = unidad.trim()
    val contable = unidadSugerida(forma) in FORMA_CONTABLE
    // Solo el número ("1"): vale si la forma se cuenta por unidad.
    if (unidad.isEmpty()) return if (contable) n else null
    // La unidad es la primera palabra; lo demás ("vía oral", "de 500 mg") no cuenta.
    val primera = unidad.split(Regex("""[\s(,;]+""")).first().trimEnd('.')
    if (UNIDADES_CONTABLES.matches(primera)) return n
    if (contable && UNIDAD_DE_MASA.containsMatchIn(unidad)) {
        val dosisMasa = masaDe(d)
        val conc = masaDe(concentracion)
        if (dosisMasa != null && conc != null && dosisMasa.second == conc.second) {
            val partes = dosisMasa.first / conc.first
            // Enteros o mitades: 500 mg de 500 → 1; 250 de 500 → 0,5. Otra cosa, a mano.
            if (partes > 0 && (partes * 2) % 1.0 == 0.0) return partes
        }
    }
    return null
}

/** La cantidad total que sale de dosis × tomas al día × días (redondeada hacia arriba), o null. */
internal fun cantidadSugerida(dosis: String?, frecuencia: String?, duracion: String?, forma: String?, concentracion: String? = null): Int? {
    val porToma = unidadesPorToma(dosis, forma, concentracion) ?: return null
    val tomas = tomasPorDia(frecuencia) ?: return null
    val unica = DOSIS_UNICA.containsMatchIn(sinTildes(frecuencia.orEmpty()))
    val dias = if (unica) 1.0 else diasDeDuracion(duracion) ?: return null
    val total = ceil(porToma * tomas * dias - 1e-9)
    if (total.isNaN() || !(total > 0) || total > 9999) return null
    return total.roundToInt()
}

/**
 * La fila tras un cambio: si se tocó la cantidad, manda lo escrito (a mano hasta
 * que se borre); si cambió un dato del que sale la cuenta y la cantidad no es a
 * mano, se pone la sugerida. Sin sugerencia, queda como estaba.
 */
internal fun conCantidadSugerida(antes: ItemRecetaForm, despues: ItemRecetaForm): ItemRecetaForm {
    if (despues.cantidad != antes.cantidad) return despues.copy(cantidadAMano = despues.cantidad.isNotBlank())
    if (despues.cantidadAMano) return despues
    val cambioLaCuenta = despues.dosis != antes.dosis || despues.frecuencia != antes.frecuencia ||
        despues.duracion != antes.duracion || despues.forma != antes.forma || despues.concentracion != antes.concentracion
    if (!cambioLaCuenta) return despues
    val sugerida = cantidadSugerida(despues.dosis, despues.frecuencia, despues.duracion, despues.forma, despues.concentracion)
        ?: return despues
    return despues.copy(cantidad = sugerida.toString())
}
