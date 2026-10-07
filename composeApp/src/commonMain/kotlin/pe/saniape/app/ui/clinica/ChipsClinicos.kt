package pe.saniape.app.ui.clinica

import pe.saniape.app.data.staff.claveChip
import pe.saniape.app.data.staff.plegarTildes

/**
 * Chips clínicos por especialidad — GEMELO de `lib/chipsClinicos.ts` de la web
 * (2026-10-07). Es la base offline: lo que sugiere el código cuando la
 * especialidad no tiene chips personalizados. Encima de esto llegan los
 * aprendidos y el pool del rubro (ver data/staff/ChipsRepo.kt).
 *
 * `tipos` = patologías (DIAGNÓSTICO al completar la evaluación, tipo de patología del paciente),
 * `sintomas` = lo que refiere el paciente (MOTIVO al agendar, síntomas del paciente).
 * Match por inclusión del nombre sin tildes ("fisio" → "Fisioterapia"); gana la
 * PRIMERA clave que calce, por eso el orden importa (estética va al final).
 */
data class ChipsEspecialidad(val tipos: List<String>, val sintomas: List<String>)

/** Una especialidad con sus chips personalizados (/especialidades), si los tiene. */
data class EspecialidadChips(
    val nombre: String,
    val chipsTipos: List<String>? = null,
    val chipsSintomas: List<String>? = null,
)

// Genérico multi-rubro (fallback para especialidades sin match)
val CHIPS_DEFAULT = ChipsEspecialidad(
    tipos = listOf("Traumatológica", "Neurológica", "Respiratoria", "Cardiovascular", "Digestiva", "Dermatológica", "Pediátrica", "Geriátrica", "Otros"),
    sintomas = listOf("Dolor", "Hinchazón", "Fiebre", "Mareo", "Náuseas", "Fatiga", "Ardor", "Adormecimiento", "Calambre", "Picazón"),
)

private val FISIO = ChipsEspecialidad(
    tipos = listOf("Lesión Deportiva", "Traumatológica", "Neurológica", "Respiratoria", "Geriátrica", "Pediátrica", "Postquirúrgica", "Postural", "Otros"),
    sintomas = listOf("Hinchazón", "Ardor", "Tirón", "Fibrosis", "Adherencia", "Hematoma", "Adormecimiento", "Calambre", "Contractura", "Tendinitis", "Dolor", "Rigidez", "Inflamación"),
)
private val DENTAL = ChipsEspecialidad(
    tipos = listOf("Caries", "Periodontal", "Ortodoncia", "Endodoncia"),
    sintomas = listOf("Dolor dental", "Sangrado de encías", "Sensibilidad", "Mal aliento"),
)
private val CAPILAR = ChipsEspecialidad(
    tipos = listOf("Alopecia androgenética", "Alopecia areata", "Efluvio telógeno", "Calvicie", "Entradas", "Coronilla", "Alopecia difusa", "Post-injerto"),
    sintomas = listOf("Caída de cabello", "Debilitamiento", "Miniaturización", "Picazón", "Descamación", "Zonas despobladas", "Seborrea", "Enrojecimiento"),
)

/** Claves sin tildes, en minúscula. El ORDEN importa: gana la primera que calce. */
internal val CHIPS_POR_ESPECIALIDAD: List<Pair<String, ChipsEspecialidad>> = listOf(
    "fisio" to FISIO,
    // Mismas palabras que PALABRAS_RUBRO de fisioterapia: "TERAPIA FISICA Y
    // REHABILITACION" o "Kinesiología" caían al set genérico.
    "rehabilit" to FISIO,
    "terapia fisica" to FISIO,
    "kinesi" to FISIO,
    "masoterap" to FISIO,
    "psico" to ChipsEspecialidad(
        tipos = listOf("Ansiedad", "Depresión", "Estrés", "Familiar", "Conductual"),
        sintomas = listOf("Insomnio", "Irritabilidad", "Fatiga", "Aislamiento", "Angustia"),
    ),
    "odonto" to DENTAL,
    "dental" to DENTAL,
    // 'ortodon' y no 'orto': "Ortopedia" / "Traumatología y ortopedia" caían en
    // los chips de ortodoncia.
    "ortodon" to ChipsEspecialidad(
        tipos = listOf("Apiñamiento", "Diastema", "Mordida abierta", "Sobremordida", "Mordida cruzada", "Maloclusión Clase I", "Maloclusión Clase II", "Maloclusión Clase III"),
        sintomas = listOf("Dientes torcidos", "Espacios entre dientes", "Dificultad al morder", "Protrusión"),
    ),
    "dermato" to ChipsEspecialidad(
        tipos = listOf("Acné", "Dermatitis", "Alérgica", "Infecciosa"),
        sintomas = listOf("Picazón", "Enrojecimiento", "Descamación", "Manchas"),
    ),
    // Ginecología y obstetricia (2026-10-07).
    "gineco" to ChipsEspecialidad(
        tipos = listOf("Ginecológica", "Obstétrica", "Planificación familiar", "Infecciosa", "Endocrina", "Mamaria", "Oncológica", "Fertilidad", "Climaterio"),
        sintomas = listOf("Dolor pélvico", "Flujo vaginal anormal", "Sangrado anormal", "Retraso menstrual", "Dolor menstrual", "Picazón vaginal", "Ardor al orinar", "Dolor en la mama", "Bochornos"),
    ),
    "obstet" to ChipsEspecialidad(
        tipos = listOf("Obstétrica", "Control prenatal", "Embarazo de alto riesgo", "Puerperio", "Planificación familiar"),
        sintomas = listOf("Náuseas del embarazo", "Sangrado", "Dolor abdominal", "Pérdida de líquido", "Menos movimientos fetales", "Hinchazón de piernas", "Dolor de cabeza"),
    ),
    "fertilidad" to ChipsEspecialidad(
        tipos = listOf("Infertilidad", "Ovario poliquístico", "Endometriosis", "Factor masculino", "Reserva ovárica", "Fertilidad"),
        sintomas = listOf("Deseo de embarazo", "Ciclos irregulares", "Dolor menstrual", "Pérdidas recurrentes"),
    ),
    "psiquiatr" to ChipsEspecialidad(
        tipos = listOf("Ansiedad", "Depresión", "Trastorno bipolar", "Trastorno del sueño", "Psicótica", "Adicciones"),
        sintomas = listOf("Insomnio", "Irritabilidad", "Tristeza persistente", "Angustia", "Aislamiento", "Falta de concentración"),
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
    // Trasplante capilar / tricología (varias claves para distintos nombres).
    "capilar" to CAPILAR, "trasplante" to CAPILAR, "trico" to CAPILAR, "alopecia" to CAPILAR,
    // Estética caía al set genérico (Traumatológica, Fiebre…): ahora tiene el suyo.
    // Al FINAL: "Estética dental" o "Estética capilar" se quedan con la suya.
    "estetic" to ChipsEspecialidad(
        tipos = listOf("Facial", "Corporal", "Acné", "Pigmentación", "Envejecimiento", "Depilación", "Post-procedimiento"),
        sintomas = listOf("Manchas", "Arrugas", "Flacidez", "Piel grasa", "Piel seca", "Brotes de acné", "Enrojecimiento", "Sensibilidad"),
    ),
)

private fun normalizar(s: String): String = plegarTildes(s).lowercase().trim()

/** Chips sugeridos por el sistema para una especialidad según su nombre (`getChipsSeed`). */
fun chipsDeEspecialidad(nombreEspecialidad: String?): ChipsEspecialidad {
    val n = normalizar(nombreEspecialidad ?: return CHIPS_DEFAULT)
    return CHIPS_POR_ESPECIALIDAD.firstOrNull { (k, _) -> n.contains(k) }?.second ?: CHIPS_DEFAULT
}

/**
 * Los chips de UNA especialidad: los personalizados (/especialidades) mandan
 * sobre los del código, campo por campo (como la web en la ficha y en la cita).
 */
fun chipsDeEspecialidad(esp: EspecialidadChips): ChipsEspecialidad {
    val seed = chipsDeEspecialidad(esp.nombre)
    return ChipsEspecialidad(
        tipos = esp.chipsTipos?.takeIf { it.isNotEmpty() } ?: seed.tipos,
        sintomas = esp.chipsSintomas?.takeIf { it.isNotEmpty() } ?: seed.sintomas,
    )
}

/**
 * La UNIÓN (sin duplicados) de los chips de las especialidades activas de la
 * clínica, respetando los personalizados (`getChipsClinicos`). Sin especialidades → genérico.
 */
fun chipsDeClinica(especialidades: List<EspecialidadChips>): ChipsEspecialidad {
    if (especialidades.isEmpty()) return CHIPS_DEFAULT
    val tipos = ArrayList<String>()
    val sintomas = ArrayList<String>()
    // Sin repetir por clave plegada: "Inflamacion" (personalizado) = "Inflamación".
    val vistosT = HashSet<String>()
    val vistosS = HashSet<String>()
    for (esp in especialidades) {
        val ch = chipsDeEspecialidad(esp)
        ch.tipos.forEach { if (vistosT.add(claveChip(it))) tipos.add(it) }
        ch.sintomas.forEach { if (vistosS.add(claveChip(it))) sintomas.add(it) }
    }
    return ChipsEspecialidad(tipos, sintomas)
}
