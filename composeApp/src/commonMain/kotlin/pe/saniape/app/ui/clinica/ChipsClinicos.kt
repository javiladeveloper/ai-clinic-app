package pe.saniape.app.ui.clinica

/**
 * Chips clínicos por especialidad (espejo de lib/chipsClinicos.ts de la web):
 * `tipos` = patologías (para el DIAGNÓSTICO al completar la evaluación),
 * `sintomas` = lo que refiere el paciente (para el MOTIVO al agendar).
 * Match por inclusión del nombre normalizado (ej. "fisio" → "Fisioterapia").
 *
 * Nota: la web además permite chips personalizados por clínica (especialidades.chips_tipos);
 * aquí se usa el catálogo semilla por nombre — el mismo fallback de la web.
 */
data class ChipsEspecialidad(val tipos: List<String>, val sintomas: List<String>)

private val CHIPS_DEFAULT = ChipsEspecialidad(
    tipos = listOf("Traumatológica", "Neurológica", "Respiratoria", "Cardiovascular", "Digestiva", "Dermatológica", "Pediátrica", "Geriátrica", "Otros"),
    sintomas = listOf("Dolor", "Hinchazón", "Fiebre", "Mareo", "Náuseas", "Fatiga", "Ardor", "Adormecimiento", "Calambre", "Picazón"),
)

private val CAPILAR = ChipsEspecialidad(
    tipos = listOf("Alopecia androgenética", "Alopecia areata", "Efluvio telógeno", "Calvicie", "Entradas", "Coronilla", "Alopecia difusa", "Post-injerto"),
    sintomas = listOf("Caída de cabello", "Debilitamiento", "Miniaturización", "Picazón", "Descamación", "Zonas despobladas", "Seborrea", "Enrojecimiento"),
)
private val DENTAL = ChipsEspecialidad(
    tipos = listOf("Caries", "Periodontal", "Ortodoncia", "Endodoncia"),
    sintomas = listOf("Dolor dental", "Sangrado de encías", "Sensibilidad", "Mal aliento"),
)

private val CHIPS_POR_ESPECIALIDAD: Map<String, ChipsEspecialidad> = mapOf(
    "fisio" to ChipsEspecialidad(
        tipos = listOf("Lesión Deportiva", "Traumatológica", "Neurológica", "Respiratoria", "Geriátrica", "Pediátrica", "Postquirúrgica", "Postural", "Otros"),
        sintomas = listOf("Hinchazón", "Ardor", "Tirón", "Fibrosis", "Adherencia", "Hematoma", "Adormecimiento", "Calambre", "Contractura", "Tendinitis", "Dolor", "Rigidez", "Inflamación"),
    ),
    "psico" to ChipsEspecialidad(
        tipos = listOf("Ansiedad", "Depresión", "Estrés", "Familiar", "Conductual"),
        sintomas = listOf("Insomnio", "Irritabilidad", "Fatiga", "Aislamiento", "Angustia"),
    ),
    "odonto" to DENTAL,
    "dental" to DENTAL,
    "orto" to ChipsEspecialidad(
        tipos = listOf("Apiñamiento", "Diastema", "Mordida abierta", "Sobremordida", "Mordida cruzada", "Maloclusión Clase I", "Maloclusión Clase II", "Maloclusión Clase III"),
        sintomas = listOf("Dientes torcidos", "Espacios entre dientes", "Dificultad al morder", "Protrusión"),
    ),
    "dermato" to ChipsEspecialidad(
        tipos = listOf("Acné", "Dermatitis", "Alérgica", "Infecciosa"),
        sintomas = listOf("Picazón", "Enrojecimiento", "Descamación", "Manchas"),
    ),
    "nutri" to ChipsEspecialidad(
        tipos = listOf("Sobrepeso", "Diabetes", "Hipertensión", "Desnutrición"),
        sintomas = listOf("Fatiga", "Ansiedad por comida", "Digestión pesada"),
    ),
    "medicina general" to ChipsEspecialidad(
        tipos = listOf("Respiratoria", "Digestiva", "Cardiovascular", "Metabólica"),
        sintomas = listOf("Fiebre", "Dolor", "Mareo", "Náuseas", "Tos"),
    ),
    "pediatr" to ChipsEspecialidad(
        tipos = listOf("Respiratoria", "Digestiva", "Del desarrollo", "Infecciosa"),
        sintomas = listOf("Fiebre", "Tos", "Vómitos", "Diarrea", "Llanto persistente"),
    ),
    "podo" to ChipsEspecialidad(
        tipos = listOf("Uña encarnada", "Hongos", "Pie diabético", "Callosidades"),
        sintomas = listOf("Dolor al caminar", "Picazón", "Mal olor", "Engrosamiento de uña"),
    ),
    "capilar" to CAPILAR, "trasplante" to CAPILAR, "trico" to CAPILAR, "alopecia" to CAPILAR,
)

private fun normalizar(s: String): String = s.lowercase()
    .replace('á', 'a').replace('é', 'e').replace('í', 'i').replace('ó', 'o').replace('ú', 'u')
    .trim()

/** Chips sugeridos para una especialidad según su nombre (fallback genérico si no matchea). */
fun chipsDeEspecialidad(nombreEspecialidad: String?): ChipsEspecialidad {
    val n = normalizar(nombreEspecialidad ?: return CHIPS_DEFAULT)
    // "fisioterapia" contiene "terapia": revisar claves largas/específicas ANTES que genéricas
    // no hace falta aquí porque las claves no colisionan (fisio/psico/odonto…).
    val clave = CHIPS_POR_ESPECIALIDAD.keys.firstOrNull { n.contains(it) }
    return clave?.let { CHIPS_POR_ESPECIALIDAD[it] } ?: CHIPS_DEFAULT
}
