package pe.saniape.app.data.staff

// ─────────────────────────────────────────────────────────────────────────────
// ¿LAS ALERGIAS ANOTADAS SON UNA ALERGIA DE VERDAD? (2026-11-03) — GEMELO de
// lib/alergias.ts (mismas expresiones). Con «Niega alergias» el triaje, la
// consulta y la historia mostraban la franja roja «⚠ Alergias: Niega
// alergias». Lo negativo («Niega», «Ninguna», «No refiere», «NKDA», «-»…) se
// sigue mostrando como dato, pero no como alerta.
// ─────────────────────────────────────────────────────────────────────────────

private val NEGATIVAS_ALERGIA = listOf(
    Regex("""^(no|ninguna?s?|nada|niega|negativo|negativa|ning)$"""),
    Regex("""^niega\b"""),
    Regex("""^no (refiere|conocidas?|presenta|tiene|reporta|registra|alergic)"""),
    Regex("""^sin (alergias?|antecedentes?)"""),
    Regex("""^(nkda|nka|nam|n/a|na|s/a|sin dato|desconoce)$"""),
    Regex("""^ninguna? (conocida|conocidas|referida)s?$"""),
)

private val SOLO_SIGNOS = Regex("""^[-–—_*/.]+$""")

/** Sin tildes, minúsculas, sin puntos ni espacios al final y espacios colapsados (normalizar de la web). */
private fun normalizarAlergia(s: String): String =
    plegarTildes(s).lowercase().replace(Regex("""[.\s]+$"""), "").replace(Regex("""\s+"""), " ").trim()

/** Texto vacío o negativo → false. Cualquier otra cosa → true (ante la duda, alerta). */
fun hayAlergia(texto: String?): Boolean {
    val n = normalizarAlergia(texto.orEmpty())
    if (n.isEmpty() || SOLO_SIGNOS.matches(n)) return false
    return NEGATIVAS_ALERGIA.none { it.containsMatchIn(n) }
}
