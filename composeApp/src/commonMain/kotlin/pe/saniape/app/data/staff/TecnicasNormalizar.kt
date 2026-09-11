package pe.saniape.app.data.staff

/**
 * PARTIR Y NORMALIZAR TÉCNICAS DE SESIÓN (Jonathan, 2026-09-11).
 *
 * Port de `lib/tecnicas-normalizar.ts` de la web. Tiene que vivir acá también
 * porque el catálogo se llena desde LAS DOS puntas: si la app no normaliza, el
 * fisio que carga desde el celular vuelve a ensuciarlo.
 *
 * El caso real: el catálogo de DALU llegó a 359 entradas, 183 de ellas frases
 * enteras ("TENS+COMPRESA+ELONGACION DE GEMELOS LISQUIOS+PSOAS ILIACO").
 *   · 122 tenían "+" DENTRO del nombre → escribían el separador a mano.
 *   ·  56 traían saltos de línea → texto pegado.
 *   ·  36 usaban abreviaturas propias ("Lib." por liberación) que el
 *     autocompletado no reconocía, así que creaban una entrada nueva cada vez.
 *
 * Mantener en sintonía con la web: si cambia una regla allá, cambia acá.
 */
object TecnicasNormalizar {

    /** Lo que separa dos técnicas dentro de un mismo texto. */
    private val SEPARADORES = Regex("""\s*(?:\+|·|;|\n|\r|/(?!\d)|,(?!\d))\s*""")

    /** Abreviaturas que usa la clínica y el autocompletado no reconocía. */
    private val ABREVIATURAS: List<Pair<Regex, String>> = listOf(
        Regex("""\blib\b\.?""", RegexOption.IGNORE_CASE) to "Liberación",
        Regex("""\bejercicios\b""", RegexOption.IGNORE_CASE) to "Ejercicios",
        Regex("""\bejerc\b\.?""", RegexOption.IGNORE_CASE) to "Ejercicio",
        Regex("""\bejer\b\.?""", RegexOption.IGNORE_CASE) to "Ejercicio",
        Regex("""\bmov\b\.?""", RegexOption.IGNORE_CASE) to "Movilización",
        Regex("""\bfortalec\b\.?""", RegexOption.IGNORE_CASE) to "Fortalecimiento",
        Regex("""\bmiof\b\.?""", RegexOption.IGNORE_CASE) to "Miofascial",
        Regex("""\bmio\b\.?""", RegexOption.IGNORE_CASE) to "Miofascial",
        Regex("""\bmusc\b\.?""", RegexOption.IGNORE_CASE) to "Muscular",
        Regex("""\bmm\.?ss\b""", RegexOption.IGNORE_CASE) to "Miembros Superiores",
        Regex("""\bmm\.?ii\b""", RegexOption.IGNORE_CASE) to "Miembros Inferiores",
        Regex("""\bt\.\s*contraste\b""", RegexOption.IGNORE_CASE) to "Terapia de Contraste",
    )

    /**
     * Faltas que se repiten en los datos. No son criterio clínico: es la misma
     * palabra mal escrita, y cada variante creaba su propia entrada.
     *
     * Las palabras completas van ANTES que los fragmentos: si "punsion" se
     * corrige primero, "eslectropunsion" ya no matchea y queda a medias.
     */
    private val ORTOGRAFIA: List<Pair<Regex, String>> = listOf(
        Regex("miofacial", RegexOption.IGNORE_CASE) to "miofascial",
        Regex("miosfacial", RegexOption.IGNORE_CASE) to "miofascial",
        Regex("fascilitaci(o|ó)n", RegexOption.IGNORE_CASE) to "facilitación",
        Regex("libeaci(o|ó)n", RegexOption.IGNORE_CASE) to "liberación",
        Regex("eslectropunsi(o|ó)n", RegexOption.IGNORE_CASE) to "electropunción",
        Regex("electropunsi(o|ó)n", RegexOption.IGNORE_CASE) to "electropunción",
        Regex("punsi(o|ó)n", RegexOption.IGNORE_CASE) to "punción",
        Regex("conbinada", RegexOption.IGNORE_CASE) to "combinada",
        Regex("magnoterapia", RegexOption.IGNORE_CASE) to "magnetoterapia",
        Regex("magnetorapia", RegexOption.IGNORE_CASE) to "magnetoterapia",
        Regex("comprensas?", RegexOption.IGNORE_CASE) to "compresa",
        Regex("elongacion", RegexOption.IGNORE_CASE) to "elongación",
        Regex("enlogacion", RegexOption.IGNORE_CASE) to "elongación",
        Regex("convesional", RegexOption.IGNORE_CASE) to "convencional",
        Regex("analogico", RegexOption.IGNORE_CASE) to "analógico",
        Regex("alinacion", RegexOption.IGNORE_CASE) to "alineación",
        Regex("flexibilizacion", RegexOption.IGNORE_CASE) to "flexibilización",
    )

    /** Variantes de escritura del MISMO aparato o maniobra. */
    private val CANONICAS: Map<String, String> = mapOf(
        "comprensa" to "Compresa", "comprensas" to "Compresa",
        "compresas" to "Compresa", "compresa" to "Compresa",
        "magneto" to "Magnetoterapia", "magnoterapia" to "Magnetoterapia",
        "magnetorapia" to "Magnetoterapia", "magnetoterapia" to "Magnetoterapia",
        "combinada" to "Terapia Combinada", "terapia combinada" to "Terapia Combinada",
        "tens" to "TENS",
        "ultrasonido" to "Ultrasonido", "us" to "Ultrasonido",
        "crioterapia" to "Crioterapia",
        "ventosas" to "Ventosas", "ventosa" to "Ventosas",
        "percutora" to "Percutora",
        "tape" to "Tape", "vendaje neuromuscular" to "Tape",
        "punsion seca" to "Punción Seca", "punción seca" to "Punción Seca",
        "electropunsion" to "Electropunción", "electropunción" to "Electropunción",
        "terapia manual" to "Terapia Manual",
        "ejercicio terapeutico" to "Ejercicio Terapéutico",
        "ejercicio terapéutico" to "Ejercicio Terapéutico",
    )

    private val SIGLAS = Regex("""^(tens|fnp|us|pold|ecom|hans pro|tecar)$""", RegexOption.IGNORE_CASE)
    private val CORTAS = Regex("""^(de|del|la|el|en|y|o|con|por|para|al|a)$""", RegexOption.IGNORE_CASE)

    /** Para comparar: sin tildes, sin puntuación de más, en minúscula. */
    fun clave(s: String): String = s
        .replace('á', 'a').replace('é', 'e').replace('í', 'i')
        .replace('ó', 'o').replace('ú', 'u').replace('Á', 'a')
        .replace('É', 'e').replace('Í', 'i').replace('Ó', 'o').replace('Ú', 'u')
        .lowercase()
        .replace(Regex("""[.,;:]+$"""), "")
        .replace(Regex("""\s+"""), " ")
        .trim()

    /**
     * La DOSIS no es una técnica ("no recetan, hacen puras técnicas"): "Tens
     * 170-35" y "Tens 120-80" son el MISMO TENS con distinta intensidad. La
     * intensidad sigue escrita en la nota de la sesión, que es su lugar.
     */
    private fun quitarDosis(s: String): String = s
        .replace(Regex("""\s*[\d.,]+\s*[-_/]\s*[\d.,]+\s*%?\s*[\d.,]*\s*$"""), "")
        .replace(Regex("""\s*/\s*[\d.,]+\s*%?\s*$"""), "")
        .replace(Regex("""\s+\d+[.,]?\d*\s*(kg|min|%|°)\s*$""", RegexOption.IGNORE_CASE), "")
        .replace(Regex("""\s+\d+[.,]\d+\s*$"""), "")
        .trim()

    /** Limpia UNA técnica. Devuelve "" si lo que queda no es una técnica. */
    fun limpiar(bruto: String): String {
        var s = bruto.replace(Regex("""\s+"""), " ").trim()
        if (s.isEmpty()) return ""

        s = s.replace(Regex("""\s*\([^)]*\)\s*$"""), "").trim()
        s = s.replace(Regex("""\s*por\s+\d+\s*min\.?$""", RegexOption.IGNORE_CASE), "").trim()
        s = s.replace(Regex("""^[-–—•*]+\s*"""), "").replace(Regex("""[-–—•*]+$"""), "").trim()

        for ((re, bien) in ORTOGRAFIA) s = re.replace(s, bien)
        s = quitarDosis(s)

        for ((re, full) in ABREVIATURAS) s = re.replace(s, full)
        // "Lib.miofascial" (sin espacio) quedaba pegado. Se exigen 2 letras
        // detrás para no partir "Ejercicios" en "Ejercicio s".
        for ((_, full) in ABREVIATURAS) {
            s = Regex("($full)(?=[a-záéíóúñA-ZÁÉÍÓÚÑ]{2,})").replace(s, "$1 ")
        }
        s = s.replace(Regex("""\s+"""), " ").trim()

        val k = clave(s)
        CANONICAS[k]?.let { return it }
        if (k.length < 3) return ""
        if (Regex("""^\d+$""").matches(k)) return ""
        return capitalizar(s)
    }

    /** Empareja la forma: un texto gritado no debe ser una técnica distinta. */
    private fun capitalizar(s: String): String {
        val base = s.split(" ").joinToString(" ") { w ->
            when {
                CORTAS.matches(w) -> w.lowercase()
                w.length >= 3 && w == w.uppercase() && Regex("""[A-ZÁÉÍÓÚÑ]{3,}""").containsMatchIn(w) -> w.lowercase()
                else -> w
            }
        }
        return base.split(" ").mapIndexed { i, w ->
            when {
                SIGLAS.matches(w) -> w.uppercase()
                i == 0 && w.isNotEmpty() -> w.replaceFirstChar { it.uppercase() }
                else -> w
            }
        }.joinToString(" ").trim()
    }

    /**
     * Parte un texto en las técnicas que contiene, ya limpias y sin repetidos.
     * "TENS+COMPRESA" → ["TENS", "Compresa"]
     */
    fun partir(texto: String): List<String> {
        if (texto.isBlank()) return emptyList()
        // La dosis se quita ANTES de partir: "COMBINADA 1/CONTINUA/0.8" es una
        // técnica con su dosis, no tres pedazos.
        val sinDosis = Regex("""\s*\d+[\d.,]*\s*/\s*(?=[A-Za-zÁÉÍÓÚÑáéíóúñ])""").replace(texto, " ")
        val vistas = mutableSetOf<String>()
        val salida = mutableListOf<String>()
        for (parte in sinDosis.split(SEPARADORES)) {
            val limpia = limpiar(parte)
            if (limpia.isEmpty()) continue
            val k = clave(limpia)
            if (!vistas.add(k)) continue
            salida.add(limpia)
        }
        return salida
    }
}
