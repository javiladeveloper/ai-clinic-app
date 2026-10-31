package pe.saniape.app.data.staff

// ─────────────────────────────────────────────────────────────────────────────
// ¿DE QUÉ ES LA ATENCIÓN? (consulta guiada, 2026-10-15)
//
// GEMELO de la web (si cambia una, cambia la otra):
//   · lib/flujo.ts → esDermatologia, esPediatria, atencionEsDermatologia,
//     atencionEsPediatria (misma regla que atencionEsPsiquiatria)
//   · components/atencion-medica/ConsultaGuiada.tsx → esGineco y el orden:
//     dental, gineco, psiquiatría y recién dermatología / pediatría
//   · lib/cie10.ts → FRECUENTES_DERMATOLOGIA y frecuentesCie10
//
// Dermatología trae sus CIE-10 frecuentes (capítulo L); pediatría, sus frases
// rápidas para niños (ver ui/clinica/atencion/FrasesPediatria.kt). Las decide
// la app con las especialidades de la clínica (como psiquiatría); si no las
// pudo leer, usa `flags.derma` / `flags.pediatria` del servidor, que un
// servidor viejo no manda (= ninguna).
// ─────────────────────────────────────────────────────────────────────────────

private fun plegado(nombre: String?): String = plegarTildes(nombre.orEmpty()).lowercase()

/**
 * ¿Esta especialidad es de dermatología? Por el NOMBRE ("Dermatología",
 * "Dermatólogo"…). Incluye la estética ("Dermatología estética").
 */
fun esDermatologia(nombre: String?): Boolean = plegado(nombre).contains("dermat")

/**
 * ¿Esta especialidad es de oftalmología (u optometría)? Por el NOMBRE, como
 * pediatría (gemelo de `esOftalmologia`, lib/flujo.ts, 2026-10-30): trae el
 * examen oftalmológico y la receta de lentes.
 */
fun esOftalmologia(nombre: String?): Boolean = plegado(nombre).let { it.contains("oftalm") || it.contains("optometr") }

/** ¿Esta especialidad es de pediatría? Por el NOMBRE. "Odontopediatría" es dental, no cuenta. */
fun esPediatria(nombre: String?): Boolean {
    val n = plegado(nombre)
    return n.contains("pediatr") && !n.contains("odonto")
}

/**
 * ¿La atención es de la especialidad que dice [es] (por su nombre)? La de la
 * cita; sin especialidad, si todas las activas de la clínica lo son.
 */
private fun atencionPorNombre(esps: List<EspecialidadNombre>, especialidadId: String?, es: (String?) -> Boolean): Boolean {
    if (!especialidadId.isNullOrBlank()) return es(esps.firstOrNull { it.id == especialidadId }?.nombre)
    val activas = esps.filter { (it.estado ?: "Activa") == "Activa" }
    return activas.isNotEmpty() && activas.all { es(it.nombre) }
}

/** ¿La atención es de dermatología? (CIE-10 frecuentes propios). */
fun atencionEsDermatologia(esps: List<EspecialidadNombre>, especialidadId: String?): Boolean =
    atencionPorNombre(esps, especialidadId, ::esDermatologia)

/** ¿La atención es de pediatría? (frases rápidas para niños). */
fun atencionEsPediatria(esps: List<EspecialidadNombre>, especialidadId: String?): Boolean =
    atencionPorNombre(esps, especialidadId, ::esPediatria)

/** ¿La atención es de oftalmología? (examen oftalmológico, receta de lentes, sin vitales ni triaje). */
fun atencionEsOftalmologia(esps: List<EspecialidadNombre>, especialidadId: String?): Boolean =
    atencionPorNombre(esps, especialidadId, ::esOftalmologia)

/** ¿La atención es de ginecología? Por el RUBRO de la especialidad (esGineco de ConsultaGuiada.tsx). */
fun atencionEsGineco(esps: List<EspecialidadNombre>, especialidadId: String?): Boolean {
    if (!especialidadId.isNullOrBlank()) return esps.firstOrNull { it.id == especialidadId }?.rubro == "ginecologia"
    val activas = esps.filter { (it.estado ?: "Activa") == "Activa" }
    return activas.isNotEmpty() && activas.all { it.rubro == "ginecologia" }
}

/**
 * Dermatología y pediatría de una atención (gineco y psiquiatría mandan antes).
 * [gineco]: la atención es de ginecología (solo para el ejemplo del CIE-10).
 */
data class VariantesAtencion(
    val derma: Boolean = false, val pediatria: Boolean = false, val gineco: Boolean = false,
    /** OFTALMOLOGÍA (2026-10-30): no excluye a pediatría ("Oftalmología pediátrica"): las frases van por varianteFrases. */
    val oftalmo: Boolean = false,
)

/**
 * Como ConsultaGuiada.tsx: `derma = !dental && !gineco && !psiq && atencionEsDermatologia`,
 * y lo mismo con pediatría. Sin especialidades (no se pudieron leer), los
 * flags del servidor; un servidor viejo no los manda → ninguna.
 */
fun variantesAtencion(esps: List<EspecialidadNombre>, flags: FlagsConsulta, psiq: Boolean): VariantesAtencion {
    if (flags.dental || psiq) return VariantesAtencion()
    if (esps.isEmpty()) return VariantesAtencion(derma = flags.derma == true, pediatria = flags.pediatria == true)
    if (atencionEsGineco(esps, flags.especialidadId)) return VariantesAtencion(gineco = true)
    return VariantesAtencion(
        derma = atencionEsDermatologia(esps, flags.especialidadId),
        pediatria = atencionEsPediatria(esps, flags.especialidadId),
        oftalmo = atencionEsOftalmologia(esps, flags.especialidadId),
    )
}

/** Dermatología (2026-10-15): lo más frecuente en la consulta dermatológica (capítulo L + B35-B36, B07, D22-D23, C43-C44). */
val FRECUENTES_DERMATOLOGIA: List<Pair<String, String>> = listOf(
    "L70.0" to "Acné vulgar",
    "L71.9" to "Rosácea, no especificada",
    "L20.9" to "Dermatitis atópica, no especificada",
    "L21.9" to "Dermatitis seborreica, no especificada",
    "L23.9" to "Dermatitis alérgica de contacto, de causa no especificada",
    "L30.9" to "Dermatitis, no especificada",
    "L40.0" to "Psoriasis vulgar",
    "L50.9" to "Urticaria, no especificada",
    "L29.9" to "Prurito, no especificado",
    "B35.1" to "Tiña de las uñas",
    "B35.3" to "Tiña del pie [Tinea pedis]",
    "B35.4" to "Tiña del cuerpo [Tinea corporis]",
    "B36.0" to "Pitiriasis versicolor",
    "B07.X" to "Verrugas víricas",
    "B08.1" to "Molusco contagioso",
    "B02.9" to "Herpes zoster sin complicaciones",
    "B86.X" to "Escabiosis",
    "L81.1" to "Cloasma",
    "L80.X" to "Vitíligo",
    "L82.X" to "Queratosis seborreica",
    "L57.0" to "Queratosis actínica",
    "D22.9" to "Nevo melanocítico, sitio no especificado",
    "D23.9" to "Tumor benigno de la piel, sitio no especificado",
    "L72.1" to "Quiste tricodérmico",
    "C44.9" to "Tumor maligno de la piel, sitio no especificado",
    "L63.9" to "Alopecia areata, no especificada",
    "L64.9" to "Alopecia andrógena, no especificada",
    "L60.0" to "Uña encarnada",
    "L91.0" to "Cicatriz queloide",
)

/**
 * Los frecuentes del buscador CIE-10 que la app trae CONSIGO (frecuentesCie10 de
 * la web: gana dental, luego psiquiatría, luego dermatología). null = los pide
 * al servidor (medicina, odontología y psiquiatría ya los manda; dermatología,
 * un servidor viejo no la conoce, por eso viaja en la app).
 */
fun frecuentesCie10Locales(dental: Boolean, psiq: Boolean, derma: Boolean, oftalmo: Boolean = false): List<DiagnosticoCie>? = when {
    dental || psiq -> null
    derma -> FRECUENTES_DERMATOLOGIA.map { (c, d) -> DiagnosticoCie(codigo = c, descripcion = d) }
    // Oftalmología (2026-10-30): FRECUENTES_OFTALMOLOGIA viaja en la app (un servidor viejo no la conoce).
    oftalmo -> FRECUENTES_OFTALMOLOGIA.map { (c, d) -> DiagnosticoCie(codigo = c, descripcion = d) }
    else -> null
}

/**
 * Ejemplo del buscador CIE-10 según la atención (gemelo de `ejemploCie10` de
 * lib/cie10.ts, 2026-10-16): en la consulta psicológica salía "J06.9 /
 * faringitis aguda". Gana el primero que aplica, como los frecuentes.
 * Devuelve (código, palabras).
 */
fun ejemploCie10(dental: Boolean = false, gineco: Boolean = false, psiq: Boolean = false, derma: Boolean = false, oftalmo: Boolean = false): Pair<String, String> = when {
    dental -> "K02.1" to "caries de la dentina"
    gineco -> "N76.0" to "vaginitis aguda"
    psiq -> "F41.1" to "ansiedad generalizada"
    derma -> "L70.0" to "acné vulgar"
    oftalmo -> "H52.4" to "presbicia"
    else -> "J06.9" to "faringitis aguda"
}
