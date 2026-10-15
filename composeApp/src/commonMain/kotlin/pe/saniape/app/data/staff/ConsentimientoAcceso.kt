package pe.saniape.app.data.staff

// ─────────────────────────────────────────────────────────────────────────────
// ¿QUIÉN USA EL CONSENTIMIENTO INFORMADO POR PROCEDIMIENTO? (2026-10-15)
//
// GEMELO de lib/atencion-medica.ts (si cambia una, cambia la otra):
//   · rubroParaTipicos → rubroBibliotecaConsentimiento
//   · mapaConsentimiento, clinicaConConsentimiento, accesoConsentimiento
//   · lib/consentimiento-informado.ts → qué rubros traen modelos en la biblioteca
//
// Las MÉDICAS (rubros que recetan) siguen el flujo médico como siempre
// (`mapaMedico`). Las NO médicas lo tienen si la biblioteca trae modelos para
// su rubro: estética, capilar y podología (GLAM, RENOVA, PodoBlack). Fisio,
// psicología o nutrición: no (DALU no cambia). No depende del plan.
// ─────────────────────────────────────────────────────────────────────────────

/** Podología por el NOMBRE (no tiene rubro en la base). Gemelo de `esPodologia` de lib/flujo.ts. */
fun esPodologia(nombre: String?): Boolean {
    val n = plegarTildes(nombre.orEmpty()).lowercase()
    return n.contains("podolog") || n.contains("podiatr")
}

/**
 * El RUBRO DE BIBLIOTECA de una especialidad (rubroParaTipicos de la web): el de
 * la base afinado por el nombre. Psiquiatría → null (médica, sin típicos);
 * podología → "podologia"; dermatología clínica → "dermatologia".
 */
fun rubroBibliotecaConsentimiento(nombre: String?, rubro: String?): String? {
    if (esPsiquiatria(nombre)) return null
    if (esPodologia(nombre)) return "podologia"
    val r = rubro ?: rubroPorNombre(nombre)
    if (r == "medicina_general" && esDermatologia(nombre)) return "dermatologia"
    return r
}

/**
 * Rubros con modelos en la biblioteca (BIBLIOTECA_CONSENTIMIENTOS de la web);
 * ginecología usa los de medicina general (BIBLIOTECA_TAMBIEN).
 */
internal val RUBROS_CON_BIBLIOTECA_CI = setOf(
    "medicina_general", "odontologia", "estetica", "capilar", "podologia", "dermatologia", "ginecologia",
)

/** ¿La biblioteca trae modelos para esta especialidad? (`bibliotecaParaEspecialidades([e]).length > 0`). */
fun tieneBibliotecaConsentimiento(nombre: String?, rubro: String?): Boolean =
    rubroBibliotecaConsentimiento(nombre, rubro) in RUBROS_CON_BIBLIOTECA_CI

/**
 * Las especialidades que usan el consentimiento informado: las de [mapaMedico]
 * (exactamente lo de siempre) + las NO médicas con modelos en la biblioteca.
 * `solo` = todas las especialidades quedan dentro.
 */
fun mapaConsentimiento(mapaMedico: MapaClinico, especialidades: List<EspecialidadNombre>): MapaClinico {
    val medica = { e: EspecialidadNombre -> (e.rubro ?: rubroPorNombre(e.nombre)) in RUBROS_QUE_RECETAN }
    val conBiblioteca = especialidades.filter { !medica(it) && tieneBibliotecaConsentimiento(it.nombre, it.rubro) }.map { it.id }
    val ids = (mapaMedico.ids + conBiblioteca).distinct()
    val solo = mapaMedico.solo || (especialidades.isNotEmpty() && especialidades.all { it.id in ids })
    return MapaClinico(ids, solo)
}

/** ¿La clínica usa el consentimiento informado (Ajustes y la sección de la ficha)? */
fun clinicaConConsentimiento(mapa: MapaClinico?): Boolean = mapa?.activo == true

/** `historiaMedica` = la HC de norma entera (IPRESS, típicos); `consentimiento` = al menos las plantillas. */
data class AccesoConsentimiento(val historiaMedica: Boolean, val consentimiento: Boolean)

fun accesoConsentimiento(mapaMedico: MapaClinico, especialidades: List<EspecialidadNombre>): AccesoConsentimiento {
    val historiaMedica = mapaMedico.activo
    return AccesoConsentimiento(historiaMedica, historiaMedica || clinicaConConsentimiento(mapaConsentimiento(mapaMedico, especialidades)))
}
