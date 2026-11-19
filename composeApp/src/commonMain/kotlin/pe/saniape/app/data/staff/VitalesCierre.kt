package pe.saniape.app.data.staff

import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.Columns
import io.github.jan.supabase.postgrest.query.Order
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive
import pe.saniape.app.data.Supabase

/**
 * 🩺 Signos vitales al cerrar una SESIÓN o una EVALUACIÓN (gemelo de
 * lib/vitales-cierre.ts de la web, 18/11/2026: la fisio de DALU «quiere saber
 * cómo estaba en cada sesión»). No es una tabla aparte: es el MISMO triaje de la
 * cita (`atenciones_clinicas` por cita, POST /api/staff/atencion/triaje), así
 * que sale solo en la ficha y la historia.
 *
 * Aparece solo si la clínica toma triaje en la especialidad de la atención
 * ([triajeEnCitaDe]). Siempre opcional. Con HTA, diabetes u otra enfermedad en
 * los antecedentes del paciente se abre solo y avisa.
 */

/** Lo que tiene sentido medir en CADA sesión: los signos vitales, no las medidas corporales. */
private val MEDICIONES_DE_SESION = listOf("presion", "frecuencia_cardiaca", "frecuencia_respiratoria", "temperatura", "saturacion_o2")
private val POR_DEFECTO = listOf("presion", "frecuencia_cardiaca", "saturacion_o2")

/** Las mediciones del cierre: las que eligió la clínica, sin peso/talla/perímetro. */
fun medicionesDeCierre(camposClinica: List<String>?): List<String> {
    val elegidas = camposClinica?.takeIf { it.isNotEmpty() } ?: MEDICIONES_DE_SESION
    return MEDICIONES_DE_SESION.filter { it in elegidas }.ifEmpty { POR_DEFECTO }
}

/** Antecedentes que piden tomar los signos en cada sesión (mismas reglas que la web). */
private val ANTECEDENTES_DE_RIESGO: List<Pair<Regex, String>> = listOf(
    Regex("hipertens|presion alta|\\bhta\\b") to "hipertensión",
    Regex("diabet|\\bdm\\s?[12]?\\b|glucosa alta|azucar alta") to "diabetes",
    Regex("cardi|corazon|arritmi|infarto|marcapaso|angina|coronari") to "cardiaco",
    Regex("\\basma|\\bepoc\\b|respirat|pulmon|bronqui|fibrosis") to "respiratorio",
    Regex("renal|ri[nñ]on|dialisis") to "renal",
    Regex("tiroid") to "tiroides",
    Regex("\\bacv\\b|derrame cerebral|ictus|hemipleji") to "ACV",
    Regex("epileps|convulsi") to "epilepsia",
    Regex("obesi") to "obesidad",
    Regex("anemia") to "anemia",
    Regex("cancer|oncolog|tumor|quimioterap") to "oncológico",
)
private val NEGACION = Regex("\\b(no|niega|sin|descarta)\\b[^,;.\\n]*$")

private fun sinTildesVitales(s: String): String = s.lowercase()
    .replace('á', 'a').replace('é', 'e').replace('í', 'i').replace('ó', 'o').replace('ú', 'u').replace('ü', 'u')

/**
 * Los antecedentes de riesgo del paciente (texto libre de `pacientes.antecedentes`):
 * las cirugías («vesícula», «cesárea») no cuentan, lo negado («niega HTA») tampoco.
 */
fun antecedentesDeRiesgo(texto: String?): List<String> {
    val t = sinTildesVitales(texto.orEmpty())
    if (t.isBlank()) return emptyList()
    return ANTECEDENTES_DE_RIESGO.mapNotNull { (re, etiqueta) ->
        etiqueta.takeIf {
            re.findAll(t).any { m -> !NEGACION.containsMatchIn(t.substring(maxOf(0, m.range.first - 12), m.range.first)) }
        }
    }
}

/** "PA 120/80 · FC 72 · SpO₂ 98%" de los valores del formulario. "" si no hay nada. */
fun resumenVitales(v: Map<String, String>): String {
    fun hay(k: String) = !v[k].isNullOrBlank()
    return buildList {
        if (hay("presion_sistolica") || hay("presion_diastolica")) {
            add("PA ${v["presion_sistolica"]?.ifBlank { null } ?: "?"}/${v["presion_diastolica"]?.ifBlank { null } ?: "?"}")
        }
        if (hay("frecuencia_cardiaca")) add("FC ${v["frecuencia_cardiaca"]}")
        if (hay("frecuencia_respiratoria")) add("FR ${v["frecuencia_respiratoria"]}")
        if (hay("temperatura")) add("T° ${v["temperatura"]}")
        if (hay("saturacion_o2")) add("SpO₂ ${v["saturacion_o2"]}%")
    }.joinToString(" · ")
}

/** ¿Hay algo escrito en las columnas de estas mediciones? */
fun hayVitalesCierre(v: Map<String, String>, mediciones: List<String>): Boolean =
    columnasDeMediciones(mediciones).any { !v[it].isNullOrBlank() }

object VitalesCierreRepo {
    /**
     * La cita de la atención: la que se pasa o, para una sesión, la cita ligada
     * (la vigente antes que una cancelada). null = sin cita (no se ofrece el bloque).
     */
    suspend fun citaDeAtencion(citaId: String?, sesionId: String?): String? {
        if (!citaId.isNullOrBlank()) return citaId
        if (sesionId.isNullOrBlank()) return null
        return runCatching {
            Supabase.client.postgrest["citas"].select(Columns.raw("id, estado")) {
                filter { eq("sesion_id", sesionId) }
                order("created_at", Order.DESCENDING)
                limit(5)
            }.decodeList<JsonObject>()
        }.getOrNull()?.let { filas ->
            val vigente = filas.firstOrNull { it["estado"]?.jsonPrimitive?.content != "Cancelada" } ?: filas.firstOrNull()
            vigente?.get("id")?.jsonPrimitive?.content
        }
    }

    /** Los antecedentes del paciente (texto libre). */
    suspend fun antecedentes(pacienteId: String?): String? {
        if (pacienteId.isNullOrBlank()) return null
        return runCatching {
            Supabase.client.postgrest["pacientes"].select(Columns.raw("antecedentes")) {
                filter { eq("id", pacienteId) }
                limit(1)
            }.decodeList<JsonObject>().firstOrNull()?.get("antecedentes")?.jsonPrimitive?.content
        }.getOrNull()?.takeUnless { it == "null" }
    }

    /**
     * Guarda lo escrito como el triaje de la cita. [valores] null = no se tocó el
     * bloque (lo de recepción queda tal cual). Sin señal va a la cola.
     * Devuelve true si quedó registrado (o no había nada que guardar).
     */
    suspend fun guardar(citaId: String?, valores: Map<String, String>?, mediciones: List<String>): Boolean {
        if (valores == null || !hayVitalesCierre(valores, mediciones) || citaId.isNullOrBlank()) return true
        return runCatching {
            AtencionMedicaRepo.guardarTriaje(citaId, cuerpoVitalesTriaje(valores, mediciones), null).registrada
        }.getOrDefault(false)
    }
}
