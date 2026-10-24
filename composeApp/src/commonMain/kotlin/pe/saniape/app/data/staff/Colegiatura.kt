package pe.saniape.app.data.staff

// ─────────────────────────────────────────────────────────────────────────────
// EL COLEGIO PROFESIONAL de quien atiende, según el rubro de sus especialidades.
// Gemelo de lib/colegiatura.ts (web). `terapeutas.cmp` guarda la colegiatura de
// CUALQUIER profesional (el nombre de la columna es histórico): para una
// psicóloga es su C.Ps.P., para un fisio su CTMP, nunca "CMP" fijo.
//
// UNA sola función para toda la app (antes había tres: la receta, el informe y
// la consulta guiada, cada una con su regla). Solo para MOSTRAR: lo que se
// imprime en receta, informe y certificado lo congela la base con sus triggers.
// ─────────────────────────────────────────────────────────────────────────────

data class Colegio(
    /** Como va en el sello: "C.Ps.P. 12345". */
    val sigla: String,
    val nombre: String,
)

private val CMP = Colegio("CMP", "Colegio Médico del Perú")
private val COP = Colegio("COP", "Colegio Odontológico del Perú")
/** La obstetra (Ley 27853). Misma sigla que el odontológico: así firman las dos. */
private val COLEGIO_OBSTETRAS = Colegio("COP", "Colegio de Obstetras del Perú")

/**
 * Perú. Estética, capilar y "otro" no tienen uno: lo hace un médico, una
 * cosmiatra o un tricólogo, y no se adivina. (`COLEGIO_POR_RUBRO` de la web.)
 */
val COLEGIO_POR_RUBRO: Map<String, Colegio> = mapOf(
    "medicina_general" to CMP,
    // El ginecólogo es médico. La OBSTETRA comparte el rubro: la distingue el
    // nombre de su especialidad ([esObstetra]), no el rubro.
    "ginecologia" to CMP,
    "odontologia" to COP,
    "psicologia" to Colegio("C.Ps.P.", "Colegio de Psicólogos del Perú"),
    "fisioterapia" to Colegio("CTMP", "Colegio Tecnólogo Médico del Perú"),
    "nutricion" to Colegio("CNP", "Colegio de Nutricionistas del Perú"),
    "veterinaria" to Colegio("CMVP", "Colegio Médico Veterinario del Perú"),
)

/** Una especialidad del profesional: su rubro y, si no lo tiene, su nombre lo sugiere. */
data class EspecialidadColegio(val rubro: String?, val nombre: String? = null)

/**
 * ¿Es obstetra (no médico)? Todo lo que la hace prescriptora (sus especialidades
 * de rubros que recetan) es obstetricia a secas: con "Ginecología", "Medicina
 * general" u "Odontología" al lado manda ese colegio. LA regla para mostrar
 * ([colegioDe]); gemela de `esObstetra` (lib/recetas.ts) y del trigger
 * documento_sello_obstetra. Sin el NOMBRE no se distingue del ginecólogo
 * (comparten rubro): con solo rubros, false.
 */
fun esObstetra(especialidades: List<EspecialidadColegio>?): Boolean {
    val prescriben = especialidades.orEmpty().filter { e ->
        rubroDeEspecialidad(e.rubro, e.nombre)?.let { it in RUBROS_QUE_RECETAN } == true
    }
    return prescriben.isNotEmpty() && prescriben.all { esEspecialidadObstetra(it.rubro, it.nombre) }
}

/**
 * El colegio de un profesional (`colegioDe` de la web). Con alguna especialidad
 * médica manda el médico (el psiquiatra es medicina_general → CMP), COP
 * (odontológico) solo si todo lo médico es dental y COP (obstetras) si todo es
 * obstetricia a secas ([esObstetra]). Si no, el del primer rubro que tenga uno.
 * Fuera de Perú ([pais] ≠ "PE") o sin rubro conocido: null.
 */
fun colegioDe(especialidades: List<EspecialidadColegio>?, pais: String? = null): Colegio? {
    if (!pais.isNullOrBlank() && pais != "PE") return null
    val rubros = especialidades.orEmpty().mapNotNull { e -> rubroDeEspecialidad(e.rubro, e.nombre) }
    val medicos = rubros.filter { it in RUBROS_QUE_RECETAN }
    if (medicos.isNotEmpty()) {
        if (medicos.all { it == "odontologia" }) return COP
        return if (esObstetra(especialidades)) COLEGIO_OBSTETRAS else CMP
    }
    return rubros.firstNotNullOfOrNull { COLEGIO_POR_RUBRO[it] }
}

/** Lo mismo con solo los rubros (lo que traen los selectores de receta e informe). */
fun colegioDeRubros(rubros: List<String?>?, pais: String? = null): Colegio? =
    colegioDe(rubros.orEmpty().map { EspecialidadColegio(it) }, pais)

/**
 * La colegiatura como se muestra (`colegiaturaParaMostrar` de la web): si solo
 * guardaron el número se antepone el colegio ("12345" → "C.Ps.P. 12345"); si ya
 * trae letras ("COP 1234", la obstetra que escribió su sigla), tal cual. Vacío → "".
 */
fun colegiaturaParaMostrar(cmp: String?, colegio: Colegio?): String {
    val v = cmp?.trim().orEmpty()
    if (v.isEmpty()) return ""
    return if (colegio != null && Regex("^[0-9][0-9 .-]*$").matches(v)) "${colegio.sigla} $v" else v
}

/**
 * Atajo: la colegiatura de un profesional con los rubros de sus especialidades.
 * Con solo rubros la obstetra no se distingue del ginecólogo: quien tenga los
 * nombres usa [colegiaturaDeEspecialidades].
 */
fun colegiaturaDe(cmp: String?, rubros: List<String?>?, pais: String? = null): String =
    colegiaturaParaMostrar(cmp, colegioDeRubros(rubros, pais))

/** La colegiatura con sus especialidades (rubro + nombre): distingue a la obstetra (COP). */
fun colegiaturaDeEspecialidades(cmp: String?, especialidades: List<EspecialidadColegio>?, pais: String? = null): String =
    colegiaturaParaMostrar(cmp, colegioDe(especialidades, pais))
