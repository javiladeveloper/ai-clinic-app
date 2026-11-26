package pe.saniape.app.data.staff

/**
 * 🏷️ ETIQUETAS DE PACIENTES (25/11/2026, FISIOSALUD: «agregar como tipo etiquetas a los
 * pacientes para armar como grupitos en una lista»). Gemelo de `lib/etiquetas-paciente.ts`
 * (web): mismas reglas. Columna `pacientes.etiquetas` (text[]).
 */
const val MAX_ETIQUETAS = 20
const val MAX_LARGO_ETIQUETA = 40

/** «  artritis   reumatoide » → «Artritis reumatoide». Vacía → null. */
fun normalizarEtiqueta(t: String): String? {
    val limpia = t.replace(Regex("\\s+"), " ").trim().take(MAX_LARGO_ETIQUETA).trim()
    if (limpia.isEmpty()) return null
    return limpia.replaceFirstChar { it.uppercaseChar() }
}

/** Minúsculas y sin tildes, para comparar. */
private fun claveEtiqueta(s: String): String = buildString {
    for (ch in s.lowercase().trim()) append(when (ch) {
        'á', 'à', 'ä', 'â' -> 'a'; 'é', 'è', 'ë', 'ê' -> 'e'; 'í', 'ì', 'ï', 'î' -> 'i'
        'ó', 'ò', 'ö', 'ô' -> 'o'; 'ú', 'ù', 'ü', 'û' -> 'u'; else -> ch
    })
}

/** Igual sin importar mayúsculas ni tildes («Lupus» = «lupus», «Artrosis» = «artrósis»). */
private fun mismaEtiqueta(a: String, b: String) = claveEtiqueta(a) == claveEtiqueta(b)

/** Agrega sin duplicar; si la clínica ya la usa ([conocidas]), con esa escritura (el grupo no se parte). */
fun agregarEtiqueta(actuales: List<String>, nueva: String, conocidas: List<String> = emptyList()): List<String> {
    val n = normalizarEtiqueta(nueva) ?: return actuales
    if (actuales.any { mismaEtiqueta(it, n) } || actuales.size >= MAX_ETIQUETAS) return actuales
    return actuales + (conocidas.firstOrNull { mismaEtiqueta(it, n) } ?: n)
}

fun quitarEtiqueta(actuales: List<String>, etiqueta: String): List<String> = actuales.filterNot { mismaEtiqueta(it, etiqueta) }

fun tieneEtiqueta(etiquetas: List<String>, etiqueta: String): Boolean = etiquetas.any { mismaEtiqueta(it, etiqueta) }

/** Las etiquetas de la lista cargada, sin repetir (la primera escritura gana), en orden alfabético. */
fun etiquetasDeLista(listas: List<List<String>>): List<String> {
    val vistas = LinkedHashMap<String, String>()
    listas.forEach { l -> l.forEach { e -> vistas.getOrPut(claveEtiqueta(e)) { e } } }
    return vistas.values.sortedBy { claveEtiqueta(it) }
}

/** Color estable por etiqueta (el mismo en la lista y en la ficha), en ARGB. Misma paleta que la web. */
private val PALETA_ETIQUETAS = listOf(0xFF2563EB, 0xFF7C3AED, 0xFF0F766E, 0xFFD97706, 0xFFDB2777, 0xFF16A34A, 0xFF0891B2, 0xFF9333EA)
fun colorEtiqueta(e: String): Long {
    var h = 0L
    for (ch in e.lowercase()) h = (h * 31 + ch.code) and 0xFFFFFFFFL
    return PALETA_ETIQUETAS[(h % PALETA_ETIQUETAS.size).toInt()]
}

/** Una opción del filtro «Diagnóstico» (RPC diagnosticos_de_pacientes). */
data class OpcionDiagnostico(val clave: String, val diagnostico: String, val codigo: String?, val pacientes: Int)
