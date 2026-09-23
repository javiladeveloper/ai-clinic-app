package pe.saniape.app.data.staff

/**
 * Convierte lo que dicta el odontólogo en hallazgos del odontograma.
 *
 * "pieza dieciséis caries oclusal y pieza veinticuatro ausente" →
 *   [16 · Caries · O] [24 · Ausente]
 *
 * Gemelo de `lib/odontograma-voz.ts` en la web: mismas palabras, mismo orden
 * de reemplazos y mismos casos de prueba. Lógica pura, sin Android: el
 * reconocimiento de voz vive aparte y solo le pasa texto a esto.
 *
 * El orden de NUMEROS_HABLADOS importa y se conserva tal cual (LinkedHashMap):
 * "veintiuno" tiene que reemplazarse antes de que "uno" suelto lo parta.
 */

data class HallazgoInterpretado(
    val idTemporal: String,
    /** "diente" o "boca". */
    val tipo: String,
    /** FDI: '11'-'48', '51'-'85'. Null en los de boca. */
    val diente: String? = null,
    val hallazgoId: String? = null,
    val hallazgoNombre: String,
    val color: String,
    val superficies: List<String>? = null,
    val textoOriginal: String? = null,
)

/** Números hablados → código FDI. Mismo orden que la web. */
private val NUMEROS_HABLADOS: LinkedHashMap<String, String> = linkedMapOf(
    // Cuadrante 1 (18-11)
    "dieciocho" to "18", "uno ocho" to "18", "1 8" to "18",
    "diecisiete" to "17", "uno siete" to "17", "1 7" to "17",
    "dieciseis" to "16", "dieciséis" to "16", "uno seis" to "16", "1 6" to "16",
    "quince" to "15", "uno cinco" to "15", "1 5" to "15",
    "catorce" to "14", "uno cuatro" to "14", "1 4" to "14",
    "trece" to "13", "uno tres" to "13", "1 3" to "13",
    "doce" to "12", "uno dos" to "12", "1 2" to "12",
    "once" to "11", "uno uno" to "11", "1 1" to "11",
    // Cuadrante 2 (21-28)
    "veintiuno" to "21", "veintiun" to "21", "veintiún" to "21", "dos uno" to "21", "2 1" to "21",
    "veintidos" to "22", "veintidós" to "22", "dos dos" to "22", "2 2" to "22",
    "veintitres" to "23", "veintitrés" to "23", "dos tres" to "23", "2 3" to "23",
    "veinticuatro" to "24", "dos cuatro" to "24", "2 4" to "24",
    "veinticinco" to "25", "dos cinco" to "25", "2 5" to "25",
    "veintiseis" to "26", "veintiséis" to "26", "dos seis" to "26", "2 6" to "26",
    "veintisiete" to "27", "dos siete" to "27", "2 7" to "27",
    "veintiocho" to "28", "dos ocho" to "28", "2 8" to "28",
    // Cuadrante 3 (31-38)
    "treinta y uno" to "31", "tres uno" to "31", "3 1" to "31",
    "treinta y dos" to "32", "tres dos" to "32", "3 2" to "32",
    "treinta y tres" to "33", "tres tres" to "33", "3 3" to "33",
    "treinta y cuatro" to "34", "tres cuatro" to "34", "3 4" to "34",
    "treinta y cinco" to "35", "tres cinco" to "35", "3 5" to "35",
    "treinta y seis" to "36", "tres seis" to "36", "3 6" to "36",
    "treinta y siete" to "37", "tres siete" to "37", "3 7" to "37",
    "treinta y ocho" to "38", "tres ocho" to "38", "3 8" to "38",
    // Cuadrante 4 (41-48)
    "cuarenta y uno" to "41", "cuatro uno" to "41", "4 1" to "41",
    "cuarenta y dos" to "42", "cuatro dos" to "42", "4 2" to "42",
    "cuarenta y tres" to "43", "cuatro tres" to "43", "4 3" to "43",
    "cuarenta y cuatro" to "44", "cuatro cuatro" to "44", "4 4" to "44",
    "cuarenta y cinco" to "45", "cuatro cinco" to "45", "4 5" to "45",
    "cuarenta y seis" to "46", "cuatro seis" to "46", "4 6" to "46",
    "cuarenta y siete" to "47", "cuatro siete" to "47", "4 7" to "47",
    "cuarenta y ocho" to "48", "cuatro ocho" to "48", "4 8" to "48",
    // Deciduos (51-55, 61-65, 71-75, 81-85)
    "cincuenta y uno" to "51", "cinco uno" to "51", "cincuenta y dos" to "52", "cinco dos" to "52",
    "cincuenta y tres" to "53", "cinco tres" to "53", "cincuenta y cuatro" to "54", "cinco cuatro" to "54",
    "cincuenta y cinco" to "55", "cinco cinco" to "55",
    "sesenta y uno" to "61", "seis uno" to "61", "sesenta y dos" to "62", "seis dos" to "62",
    "sesenta y tres" to "63", "seis tres" to "63", "sesenta y cuatro" to "64", "seis cuatro" to "64",
    "sesenta y cinco" to "65", "seis cinco" to "65",
    "setenta y uno" to "71", "siete uno" to "71", "setenta y dos" to "72", "siete dos" to "72",
    "setenta y tres" to "73", "siete tres" to "73", "setenta y cuatro" to "74", "siete cuatro" to "74",
    "setenta y cinco" to "75", "siete cinco" to "75",
    "ochenta y uno" to "81", "ocho uno" to "81", "ochenta y dos" to "82", "ocho dos" to "82",
    "ochenta y tres" to "83", "ocho tres" to "83", "ochenta y cuatro" to "84", "ocho cuatro" to "84",
    "ochenta y cinco" to "85", "ocho cinco" to "85",
)

private fun re(p: String) = Regex(p, RegexOption.IGNORE_CASE)

/** Las caras que nombra el texto. Las combinadas (MO, MOD) se reconocen primero. */
private fun extraerSuperficies(texto: String): List<String>? {
    val t = texto.lowercase()
    if (re("""mesio\s*ocluso\s*distal|\bmod\b""").containsMatchIn(t)) return listOf("M", "O", "D")
    if (re("""mesio\s*oclusal|\bmo\b|\bom\b""").containsMatchIn(t)) return listOf("M", "O")
    if (re("""disto\s*oclusal|ocluso\s*distal|\bdo\b|\bod\b""").containsMatchIn(t)) return listOf("O", "D")
    if (re("""ocluso\s*vestibular|\bov\b|\bvo\b""").containsMatchIn(t)) return listOf("O", "V")
    if (re("""ocluso\s*palatino|ocluso\s*lingual|\bop\b|\bol\b""").containsMatchIn(t)) return listOf("O", "L")

    val res = linkedSetOf<String>()
    if (re("""\boclusal\b|\boclusion\b|\boclusión\b""").containsMatchIn(t)) res.add("O")
    if (re("""\bmesial\b""").containsMatchIn(t)) res.add("M")
    if (re("""\bdistal\b""").containsMatchIn(t)) res.add("D")
    if (re("""\bvestibular\b""").containsMatchIn(t)) res.add("V")
    if (re("""\bpalatino\b|\bpalatina\b|\blingual\b""").containsMatchIn(t)) res.add("L")
    return res.toList().takeIf { it.isNotEmpty() }
}

/** Condiciones de la boca entera, que no son de una pieza. */
private fun detectarHallazgosBoca(texto: String): List<String> {
    val t = texto.lowercase()
    val r = mutableListOf<String>()
    if (re("sarro|tartaro|tártaro|calculo dental|cálculo dental").containsMatchIn(t)) r.add("Sarro")
    if (re("gingivitis|encias sangrantes|encías sangrantes|encias inflamadas").containsMatchIn(t)) r.add("Gingivitis")
    if (re("bruxismo|apretamiento|desgaste general").containsMatchIn(t)) r.add("Bruxismo")
    if (re("profilaxis|limpieza general|limpieza profunda").containsMatchIn(t)) r.add("Profilaxis")
    return r
}

/** Qué se dijo de una pieza. Si no nombra nada reconocible, es Caries (lo más dictado). */
private fun detectarHallazgoDiente(texto: String): String {
    val t = texto.lowercase()
    return when {
        re("ausente|falta|perdido|perdida|extraido|extraído|vacio|vacío|sin pieza").containsMatchIn(t) -> "Ausente"
        re("corona|funda|protesis fija|prótesis fija").containsMatchIn(t) -> "Corona"
        re("endodoncia|conducto|tratamiento de conducto").containsMatchIn(t) -> "Endodoncia"
        re("fractura|fracturado|roto|partido|fisura").containsMatchIn(t) -> "Fractura"
        re("extraccion indicada|extracción indicada|por extraer|resto radicular").containsMatchIn(t) -> "Extracción indicada"
        re("implante").containsMatchIn(t) -> "Implante"
        re("perno").containsMatchIn(t) -> "Perno"
        re("restauracion existente|restauración existente|obturado|resina existente|amalgama existente").containsMatchIn(t) -> "Restauración existente"
        else -> "Caries"
    }
}

/** Minúsculas, sin puntuación, y los números hablados convertidos a FDI. */
fun normalizarTextoDictado(texto: String): String {
    var res = texto.lowercase()
        .replace(Regex("[.,;:]"), " ")
        .replace(Regex("""\s+"""), " ")
        .trim()
    for ((hablado, num) in NUMEROS_HABLADOS) {
        res = res.replace(re("""\b${Regex.escape(hablado)}\b"""), num)
    }
    return res
}

/**
 * Cada pieza nombrada hasta la siguiente pieza o el final del texto.
 * (Mismo patrón que la web: el bloque de una pieza termina donde empieza otra.)
 */
private val REGEX_DIENTE = re(
    """(?:pieza|diente|numero|número)?\s*([1-4][1-8]|[5-8][1-5])\s+((?:(?!\b(?:pieza|diente|numero|número)?\s*(?:[1-4][1-8]|[5-8][1-5])\b).)+)""",
)

private val ES_PIEZA = Regex("^[1-4][1-8]$|^[5-8][1-5]$")

fun interpretarDictadoDental(
    textoDictado: String,
    catalogo: List<HallazgoDental> = emptyList(),
): List<HallazgoInterpretado> {
    if (textoDictado.isBlank()) return emptyList()
    val norm = normalizarTextoDictado(textoDictado)
    val resultados = mutableListOf<HallazgoInterpretado>()
    var contador = 0
    fun buscar(nombre: String) = catalogo.firstOrNull { it.nombre.equals(nombre, ignoreCase = true) }

    // 1. Condiciones de boca completa.
    for (boca in detectarHallazgosBoca(norm)) {
        if (resultados.any { it.tipo == "boca" && it.hallazgoNombre.equals(boca, ignoreCase = true) }) continue
        val cat = catalogo.firstOrNull { it.nombre.lowercase().contains(boca.lowercase()) }
        resultados.add(
            HallazgoInterpretado(
                idTemporal = "dictado-${++contador}", tipo = "boca",
                hallazgoId = cat?.id, hallazgoNombre = cat?.nombre ?: boca,
                color = cat?.color ?: "#0284c7", textoOriginal = boca,
            ),
        )
    }

    val cubiertos = mutableSetOf<String>()
    fun agregarDiente(diente: String, resto: String, clave: String) {
        if (clave in cubiertos) return
        cubiertos.add(clave)
        val nombre = detectarHallazgoDiente(resto)
        val cat = buscar(nombre)
        resultados.add(
            HallazgoInterpretado(
                idTemporal = "dictado-${++contador}", tipo = "diente", diente = diente,
                hallazgoId = cat?.id, hallazgoNombre = cat?.nombre ?: nombre,
                color = cat?.color ?: if (nombre == "Ausente") "#64748b" else "#dc2626",
                // Una ausencia no tiene caras.
                superficies = if (nombre == "Ausente") null else extraerSuperficies(resto),
                textoOriginal = "$diente $resto".trim(),
            ),
        )
    }

    // 2. Estrategia A: cada pieza con lo que se dijo hasta la siguiente.
    for (m in REGEX_DIENTE.findAll(norm)) {
        val diente = m.groupValues[1]
        val resto = m.groupValues[2]
        val nombre = detectarHallazgoDiente(resto)
        val caras = extraerSuperficies(resto)?.joinToString("") ?: ""
        agregarDiente(diente, resto, "$diente-$nombre-$caras")
    }

    // 3. Estrategia B: dictado corto que la A no tomó ("26 ausente").
    if (resultados.none { it.tipo == "diente" }) {
        val palabras = norm.split(Regex("""\s+"""))
        for (i in palabras.indices) {
            val p = palabras[i]
            if (!ES_PIEZA.matches(p)) continue
            val segmento = palabras.drop(i + 1).take(5).joinToString(" ")
            agregarDiente(p, segmento, "$p-${detectarHallazgoDiente(segmento)}")
        }
    }
    return resultados
}
