package pe.saniape.app.data.staff

import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.Columns
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.contentType
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import pe.saniape.app.data.Supabase
import pe.saniape.app.data.crearHttpClient

/**
 * ESCALAS EN EL CIERRE (gemelo de la web, 2026-11-01, Centro OMER: «tiene que
 * sentirse fluido»). Al completar una evaluación o una sesión, las escalas
 * propias de la especialidad de la cita (EVA del dolor, Fitzpatrick, biotipo,
 * Glogau…) se marcan a un toque y se guardan con el cierre, ligadas a la cita,
 * por /api/staff/escalas-paciente (el servidor recalcula). «Antes: …» = la
 * última medición del paciente.
 *
 * Solo las PROPIAS de la clínica (escalas_clinica): las de fábrica (PHQ-9…)
 * viven en la consulta médica y los tests psicológicos.
 */
data class OpcionEscala(val valor: Int, val texto: String)
data class ItemEscala(val texto: String, val opciones: List<OpcionEscala>)
data class RangoEscala(val min: Int, val max: Int, val etiqueta: String)
data class EscalaCierre(
    /** «propia:<uuid>»: el id con el que la guarda el servidor. */
    val instrumento: String,
    val corto: String,
    val nombre: String,
    val items: List<ItemEscala>,
    val rangos: List<RangoEscala>,
)

/** La última medición de una escala del paciente («Glogau III», 12 oct.). */
data class MedicionPrevia(val texto: String, val fecha: String)

/** «I · Piel muy clara: siempre se quema…» → «I · Piel muy clara» (como la web). */
fun textoCortoOpcion(t: String): String = t.substringBefore(':').trim()

/**
 * Lo que se ve al marcar: en una escala de puntaje (EVA 0–10) «7 · Dolor severo»;
 * en una clasificación corta (Fitzpatrick, biotipo, Glogau) solo el tipo. null
 * si falta responder algo.
 */
fun resultadoEscala(e: EscalaCierre, valores: List<Int?>): String? {
    if (valores.size != e.items.size || valores.any { it == null }) return null
    val total = valores.sumOf { it ?: 0 }
    val etiqueta = e.rangos.firstOrNull { total in it.min..it.max }?.etiqueta
    val min = e.items.sumOf { it.opciones.minOfOrNull { o -> o.valor } ?: 0 }
    val max = e.items.sumOf { it.opciones.maxOfOrNull { o -> o.valor } ?: 0 }
    return when {
        etiqueta == null -> "$total"
        max - min > 6 -> "$total · $etiqueta"
        else -> etiqueta
    }
}

private fun JsonObject.texto(k: String) = (this[k] as? JsonPrimitive)?.content
private fun opcionesDe(arr: JsonArray?) = arr?.mapNotNull { o ->
    val obj = o as? JsonObject ?: return@mapNotNull null
    val v = (obj["valor"] as? JsonPrimitive)?.intOrNull ?: return@mapNotNull null
    OpcionEscala(v, obj.texto("texto") ?: return@mapNotNull null)
}.orEmpty()

/** Una fila de escalas_clinica → escala para el cierre (null si no tiene la forma). */
fun escalaDeFila(f: JsonObject): EscalaCierre? {
    val id = f.texto("id") ?: return null
    val def = f["definicion"] as? JsonObject ?: return null
    val compartidas = opcionesDe(def["opciones"] as? JsonArray)
    val items = (def["items"] as? JsonArray)?.mapNotNull { i ->
        val obj = i as? JsonObject ?: return@mapNotNull null
        val propias = opcionesDe(obj["opciones"] as? JsonArray)
        ItemEscala(obj.texto("texto") ?: return@mapNotNull null, propias.ifEmpty { compartidas })
    }.orEmpty()
    if (items.isEmpty() || items.any { it.opciones.size < 2 }) return null
    val rangos = (def["rangos"] as? JsonArray)?.mapNotNull { r ->
        val obj = r as? JsonObject ?: return@mapNotNull null
        val min = (obj["min"] as? JsonPrimitive)?.intOrNull ?: return@mapNotNull null
        val max = (obj["max"] as? JsonPrimitive)?.intOrNull ?: return@mapNotNull null
        RangoEscala(min, max, obj.texto("etiqueta") ?: "")
    }.orEmpty()
    return EscalaCierre("propia:$id", def.texto("corto") ?: return null, def.texto("nombre") ?: "", items, rangos)
}

object EscalasCierreRepo {
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }
    private val http by lazy { crearHttpClient() }
    private fun url() = "${Supabase.SITE_URL}/api/staff/escalas-paciente"

    /** Las escalas propias activas asignadas a esta especialidad (vacío si no hay o falla). */
    suspend fun deEspecialidad(especialidadId: String?): List<EscalaCierre> {
        if (especialidadId.isNullOrBlank()) return emptyList()
        return runCatching {
            Supabase.client.postgrest["escalas_clinica"]
                .select(Columns.raw("id, definicion, especialidad_ids")) { filter { eq("estado", "Activa") } }
                .decodeList<JsonObject>()
                .filter { f -> (f["especialidad_ids"] as? JsonArray)?.any { (it as? JsonPrimitive)?.content.equals(especialidadId, ignoreCase = true) } == true }
                .mapNotNull { escalaDeFila(it) }
                .sortedBy { it.corto.lowercase() }
        }.getOrDefault(emptyList())
    }

    /** La última medición de cada escala del paciente, por nombre corto. */
    suspend fun ultimasDelPaciente(pacienteId: String?): Map<String, MedicionPrevia> {
        if (pacienteId.isNullOrBlank()) return emptyMap()
        val tk = Supabase.client.auth.currentSessionOrNull()?.accessToken ?: return emptyMap()
        return runCatching {
            val r = http.get(url()) { header("Authorization", "Bearer $tk"); parameter("paciente", pacienteId) }
            if (r.status.value !in 200..299) return emptyMap()
            val lista = json.parseToJsonElement(r.bodyAsText()).jsonObject["escalas"]?.jsonArray ?: return emptyMap()
            val out = linkedMapOf<String, MedicionPrevia>()
            for (x in lista) { // del más nuevo al más viejo: queda la primera de cada una
                val o = x.jsonObject
                val e = o["escala"]?.jsonObject ?: continue
                val corto = e["corto"]?.jsonPrimitive?.content ?: continue
                if (corto in out) continue
                val categoria = (e["categoria"] as? JsonPrimitive)?.takeIf { it !is JsonNull }?.content
                val puntaje = (e["puntaje"] as? JsonPrimitive)?.intOrNull
                val min = (e["min"] as? JsonPrimitive)?.intOrNull ?: 0
                val max = (e["max"] as? JsonPrimitive)?.intOrNull ?: 0
                val texto = when {
                    puntaje == null -> categoria ?: "—"
                    categoria == null -> "$puntaje"
                    max - min > 6 -> "$puntaje · $categoria"
                    else -> categoria
                }
                out[corto] = MedicionPrevia(texto, o["aplicadaEn"]?.jsonPrimitive?.content?.take(10) ?: "")
            }
            out
        }.getOrDefault(emptyMap())
    }

    /** Guarda las marcadas, ligadas a la cita. Devuelve cuántas fallaron (nunca lanza). */
    suspend fun guardar(pacienteId: String?, citaId: String?, especialidadId: String?, marcadas: Map<String, List<Int?>>): Int {
        val completas = marcadas.filterValues { v -> v.any { it != null } }
        if (pacienteId.isNullOrBlank() || completas.isEmpty()) return 0
        val tk = Supabase.client.auth.currentSessionOrNull()?.accessToken ?: return completas.size
        var fallidas = 0
        for ((instrumento, valores) in completas) {
            val ok = runCatching {
                http.post(url()) {
                    header("Authorization", "Bearer $tk")
                    contentType(ContentType.Application.Json)
                    setBody(buildJsonObject {
                        put("pacienteId", pacienteId)
                        put("citaId", citaId)
                        put("especialidadId", especialidadId)
                        put("instrumento", instrumento)
                        put("valores", buildJsonArray { valores.forEach { add(if (it == null) JsonNull else JsonPrimitive(it)) } })
                    }.toString())
                }.status.value in 200..299
            }.getOrDefault(false)
            if (!ok) fallidas++
        }
        return fallidas
    }
}
