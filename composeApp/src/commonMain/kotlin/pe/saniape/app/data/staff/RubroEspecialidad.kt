package pe.saniape.app.data.staff

/** Gemelo de `PALABRAS_RUBRO` en lib/flujo.ts (el orden importa: gana el primero). */
internal val PALABRAS_RUBRO: List<Pair<String, List<String>>> = listOf(
    "odontologia" to listOf("odonto", "dental", "endodon", "ortodon", "periodon", "estomatolog"),
    "fisioterapia" to listOf("fisio", "rehabilit", "terapia f", "kinesi", "masoterap", "quiropra", "osteopat"),
    "ginecologia" to listOf("gineco", "obstet", "fertilidad"),
    "capilar" to listOf("capilar", "tricolog", "injerto"),
    "estetica" to listOf("estetic", "cosmet", "cosmiat"),
    // Dermatología es MÉDICA (2026-10-15): rubro medicina_general, como
    // psiquiatría. Va DESPUÉS de estética: "Dermatología estética",
    // "Dermocosmética" o "Cosmiatría" siguen siendo estética.
    "medicina_general" to listOf("dermat"),
    "nutricion" to listOf("nutri"),
    // Psiquiatría es MÉDICA (2026-10-09): antes de psicología para que
    // "Psiquiatría y Psicología" o "Neuropsiquiatría" no caigan en psicología.
    "medicina_general" to listOf("psiquiatr"),
    "psicologia" to listOf("psico", "terapia p"),
    "medicina_general" to listOf("medicina general", "medicina fam", "medicina interna", "medico general", "pediatr"),
    "veterinaria" to listOf("veterin"),
)

private fun sinTildesRubro(s: String): String = buildString {
    for (ch in s.lowercase()) append(
        when (ch) { 'á' -> 'a'; 'é' -> 'e'; 'í' -> 'i'; 'ó' -> 'o'; 'ú', 'ü' -> 'u'; else -> ch }
    )
}

/** Gemelo de `rubroPorNombre` en lib/flujo.ts: el rubro que sugiere el nombre, o null. */
internal fun rubroPorNombre(nombre: String?): String? {
    val n = sinTildesRubro(nombre.orEmpty())
    if (n.isBlank()) return null
    return PALABRAS_RUBRO.firstOrNull { (_, ps) -> ps.any { n.contains(it) } }?.first
}

/** Gemelo de `rubroDeEspecialidad` (lib/flujo.ts): el rubro guardado o, si no, el que sugiere el nombre. */
internal fun rubroDeEspecialidad(rubro: String?, nombre: String?): String? =
    rubro?.trim()?.takeIf { it.isNotEmpty() } ?: rubroPorNombre(nombre)

/**
 * ¿Esta especialidad es de OBSTETRICIA a secas (la obstetra, del Colegio de
 * Obstetras del Perú)? "Obstetricia", "Obstetra", "Obstetricia y psicoprofilaxis".
 * "Ginecología y obstetricia" NO: esa la ejerce un médico gineco-obstetra (CMP).
 * Gemelo de `esEspecialidadObstetra` (lib/flujo.ts).
 */
fun esEspecialidadObstetra(rubro: String?, nombre: String?): Boolean {
    val n = sinTildesRubro(nombre.orEmpty())
    if (!n.contains("obstetr") || n.contains("ginec")) return false
    val r = rubroDeEspecialidad(rubro, nombre)
    return r == null || r == "ginecologia"
}
