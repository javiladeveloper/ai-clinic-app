package pe.saniape.app.ui.clinica

import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Paridad con `lib/chipsClinicos.ts` de la web (2026-10-15): las listas se
 * copian TAL CUAL (mismo texto, mismo orden). Si la web cambia una lista, este
 * test se actualiza junto con ChipsClinicos.kt.
 */
class ChipsClinicosParidadTest {

    @Test
    fun medicinaGeneralIgualQueLaWeb() {
        val ch = chipsDeEspecialidad("Medicina General")
        assertEquals(
            listOf("Respiratoria", "Digestiva", "Cardiovascular", "Metabólica", "Infecciosa", "Urinaria", "Dermatológica", "Osteomuscular", "Neurológica", "Alérgica", "Salud mental", "Chequeo preventivo"),
            ch.tipos,
        )
        assertEquals(
            listOf("Fiebre", "Dolor", "Mareo", "Náuseas", "Tos", "Dolor de garganta", "Dolor de cabeza", "Dolor abdominal", "Diarrea", "Cansancio"),
            ch.sintomas,
        )
    }

    @Test
    fun pediatriaIgualQueLaWeb() {
        val ch = chipsDeEspecialidad("Pediatría")
        assertEquals(
            listOf("Respiratoria", "Digestiva", "Del desarrollo", "Infecciosa", "Control CRED", "Nutricional", "Anemia", "Alérgica", "Dermatológica", "Recién nacido", "Vacunas"),
            ch.tipos,
        )
        assertEquals(
            listOf("Fiebre", "Tos", "Vómitos", "Diarrea", "Llanto persistente", "Secreción nasal", "Falta de apetito", "Dificultad para respirar", "Dolor de oído", "Erupción en la piel"),
            ch.sintomas,
        )
    }

    @Test
    fun dermatologiaIgualQueLaWeb() {
        val ch = chipsDeEspecialidad("Dermatología")
        assertEquals(
            listOf("Acné", "Dermatitis", "Alérgica", "Infecciosa", "Psoriasis", "Rosácea", "Hongos", "Verrugas / virales", "Pigmentaria", "Lunares / tumoral", "Capilar", "Uñas"),
            ch.tipos,
        )
        assertEquals(
            listOf("Picazón", "Enrojecimiento", "Descamación", "Manchas", "Granos", "Ronchas", "Ardor", "Ampollas", "Lunar que cambia", "Caída de cabello"),
            ch.sintomas,
        )
        // "Dermatología estética": 'dermato' va antes que 'estetic' (como en la web).
        assertEquals(ch, chipsDeEspecialidad("Dermatología estética"))
    }

    @Test
    fun podologiaIgualQueLaWeb() {
        val ch = chipsDeEspecialidad("Podología")
        assertEquals(
            listOf("Uña encarnada", "Hongos", "Pie diabético", "Callosidades", "Verruga plantar", "Pie de atleta", "Juanete", "Pie plano", "Talón doloroso", "Grietas en talones", "Biomecánica"),
            ch.tipos,
        )
        assertEquals(
            listOf("Dolor al caminar", "Picazón", "Mal olor", "Engrosamiento de uña", "Uña enterrada", "Inflamación del dedo", "Ardor en los pies", "Durezas", "Sudoración excesiva", "Adormecimiento"),
            ch.sintomas,
        )
    }

    @Test
    fun psicologiaIgualQueLaWeb() {
        val ch = chipsDeEspecialidad("Psicología")
        assertEquals(
            listOf("Ansiedad", "Depresión", "Estrés", "Familiar", "Conductual", "Pareja", "Duelo", "Infantil", "Adolescente", "Aprendizaje", "Autoestima"),
            ch.tipos,
        )
        assertEquals(
            listOf("Insomnio", "Irritabilidad", "Fatiga", "Aislamiento", "Angustia", "Preocupación excesiva", "Tristeza persistente", "Llanto fácil", "Falta de concentración", "Pensamientos intrusivos"),
            ch.sintomas,
        )
    }

    @Test
    fun obstetriciaYFertilidadIgualQueLaWeb() {
        assertEquals(
            listOf("Obstétrica", "Control prenatal", "Embarazo de alto riesgo", "Puerperio", "Planificación familiar", "Psicoprofilaxis", "Lactancia materna", "Infección urinaria", "Anemia del embarazo", "Amenaza de aborto"),
            chipsDeEspecialidad("Obstetricia").tipos,
        )
        assertEquals(
            listOf("Deseo de embarazo", "Ciclos irregulares", "Dolor menstrual", "Pérdidas recurrentes", "Ausencia de regla", "Dolor en las relaciones", "Acné o vello excesivo", "Aumento de peso"),
            chipsDeEspecialidad("Fertilidad").sintomas,
        )
    }

    @Test
    fun ordenDeClavesIgualQueLaWeb() {
        assertEquals(
            listOf(
                "fisio", "rehabilit", "terapia fisica", "kinesi", "masoterap", "psico", "odonto", "dental", "ortodon",
                "dermato", "gineco", "obstet", "fertilidad", "psiquiatr", "nutri", "medicina general", "pediatr", "podo",
                "capilar", "trasplante", "trico", "alopecia", "estetic",
            ),
            CHIPS_POR_ESPECIALIDAD.map { it.first },
        )
    }
}
