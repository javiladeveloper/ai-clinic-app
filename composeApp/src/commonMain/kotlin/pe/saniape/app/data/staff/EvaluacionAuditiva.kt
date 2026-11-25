package pe.saniape.app.data.staff

import io.github.jan.supabase.auth.auth
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.client.request.patch
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import pe.saniape.app.data.Supabase
import pe.saniape.app.data.crearHttpClient
import kotlin.math.roundToInt

/*
 * 👂 EVALUACIÓN AUDITIVA (audiología, 24/11/2026). Gemelo de lib/evaluacion-auditiva.ts
 * (web): anamnesis → videotoscopia (¿apto?) → audiometría aérea y ósea + logoaudiometría →
 * conclusión → demostración de audífonos. GRADO = OMS 2021 (promedio de 500/1000/2000/4000 Hz);
 * TIPO = por la brecha aéreo-ósea (≥ 15 dB, criterio por validar con la clínica: Sania solo
 * SUGIERE). Lógica PURA + lectura/escritura por /api/staff/evaluacion-auditiva.
 */

enum class Oido(val clave: String, val nombre: String) { OD("od", "Oído derecho"), OI("oi", "Oído izquierdo") }

val FRECUENCIAS_AEREA = listOf(250, 500, 1000, 2000, 3000, 4000, 6000, 8000)
val FRECUENCIAS_OSEA = listOf(500, 1000, 2000, 4000)
val FRECUENCIAS_PTA = listOf(500, 1000, 2000, 4000)
const val DB_MIN = -10
const val DB_MAX = 120
const val DB_PASO = 5
const val UMBRAL_BRECHA_DB = 15

data class Umbral(val db: Int, val nr: Boolean = false)
typealias Curva = Map<Int, Umbral>
data class AudiometriaOido(val aerea: Curva = emptyMap(), val osea: Curva = emptyMap())

enum class GradoPerdida(val clave: String, val etiqueta: String) {
    NORMAL("normal", "Audición normal"), LEVE("leve", "Pérdida leve"), MODERADA("moderada", "Pérdida moderada"),
    MOD_SEVERA("moderadamente_severa", "Pérdida moderadamente severa"), SEVERA("severa", "Pérdida severa"),
    PROFUNDA("profunda", "Pérdida profunda"), TOTAL("total", "Pérdida total"),
}
enum class TipoPerdida(val clave: String, val etiqueta: String) {
    NORMAL("normal", "Normal"), CONDUCTIVA("conductiva", "Conductiva"), NEUROSENSORIAL("neurosensorial", "Neurosensorial"), MIXTA("mixta", "Mixta"),
}

val MOTIVOS_AUDITIVOS = listOf("Oye menos", "Zumbido (tinnitus)", "Vértigo o mareo", "Dolor de oído", "Supuración", "Oído tapado",
    "No entiende las palabras", "Control", "Examen ocupacional")
val HALLAZGOS_OTOSCOPIA = listOf("Conducto permeable", "Membrana timpánica íntegra", "Tapón de cerumen", "Cerumen parcial", "Conducto inflamado",
    "Secreción", "Perforación timpánica", "Membrana opaca o retraída", "Cuerpo extraño")
val HALLAZGOS_QUE_IMPIDEN = listOf("Tapón de cerumen", "Conducto inflamado", "Secreción", "Cuerpo extraño")

data class Anamnesis(
    val motivos: List<String> = emptyList(), val lado: String? = null, val tiempo: String = "",
    val ruido: Boolean? = null, val familiares: Boolean? = null, val audifonosPrevios: Boolean? = null, val notas: String = "",
)
data class OtoscopiaOido(val hallazgos: List<String> = emptyList(), val nota: String = "")
data class Otoscopia(val od: OtoscopiaOido = OtoscopiaOido(), val oi: OtoscopiaOido = OtoscopiaOido(), val apto: Boolean? = null, val indicacion: String = "") {
    fun de(o: Oido) = if (o == Oido.OD) od else oi
    fun con(o: Oido, v: OtoscopiaOido) = if (o == Oido.OD) copy(od = v) else copy(oi = v)
}
data class Logo(val srt: Int? = null, val discriminacion: Int? = null, val intensidad: Int? = null)
data class ConclusionOido(val grado: GradoPerdida? = null, val tipo: TipoPerdida? = null)
data class Conclusion(val od: ConclusionOido = ConclusionOido(), val oi: ConclusionOido = ConclusionOido(), val texto: String = "", val recomendacion: String = "") {
    fun de(o: Oido) = if (o == Oido.OD) od else oi
    fun con(o: Oido, v: ConclusionOido) = if (o == Oido.OD) copy(od = v) else copy(oi = v)
}
data class Demostracion(val hecha: Boolean = false, val modelo: String = "", val oido: String? = null, val resultado: String = "")

data class DatosAuditivos(
    val anamnesis: Anamnesis = Anamnesis(),
    val otoscopia: Otoscopia = Otoscopia(),
    val audiometria: Map<Oido, AudiometriaOido> = mapOf(Oido.OD to AudiometriaOido(), Oido.OI to AudiometriaOido()),
    val logo: Map<Oido, Logo> = mapOf(Oido.OD to Logo(), Oido.OI to Logo()),
    val conclusion: Conclusion = Conclusion(),
    val demostracion: Demostracion = Demostracion(),
) {
    fun aud(o: Oido) = audiometria[o] ?: AudiometriaOido()
    fun log(o: Oido) = logo[o] ?: Logo()
}

// ── JSON ↔ datos (tolerante, como normalizarDatos) ──────────────────────────────

private fun JsonObject?.o(k: String) = this?.get(k) as? JsonObject
private fun JsonObject?.s(k: String, max: Int) = (this?.get(k) as? JsonPrimitive)?.takeIf { it.isString }?.content?.take(max) ?: ""
private fun JsonObject?.b3(k: String): Boolean? = (this?.get(k) as? JsonPrimitive)?.content?.let { if (it == "true") true else if (it == "false") false else null }
private fun JsonObject?.lista(k: String) = (this?.get(k) as? JsonArray)?.mapNotNull { (it as? JsonPrimitive)?.content?.trim()?.take(80)?.takeIf(String::isNotEmpty) }?.take(20).orEmpty()
private fun lado(v: String?) = v?.takeIf { it == "derecho" || it == "izquierdo" || it == "ambos" }
private fun JsonObject?.num(k: String, min: Int, max: Int) = (this?.get(k) as? JsonPrimitive)?.content?.toDoubleOrNull()?.takeIf { it in min.toDouble()..max.toDouble() }?.roundToInt()

fun dbValido(v: Int) = v in DB_MIN..DB_MAX && v % DB_PASO == 0

private fun curva(j: JsonObject?, fs: List<Int>): Curva = fs.mapNotNull { f ->
    val u = j.o(f.toString()) ?: return@mapNotNull null
    val db = (u["db"] as? JsonPrimitive)?.content?.toDoubleOrNull()?.takeIf { it % 1.0 == 0.0 }?.toInt() ?: return@mapNotNull null
    if (!dbValido(db)) return@mapNotNull null
    f to Umbral(db, (u["nr"] as? JsonPrimitive)?.content == "true")
}.toMap()

fun normalizarDatos(crudo: JsonElement?): DatosAuditivos {
    val d = crudo as? JsonObject
    val an = d.o("anamnesis"); val ot = d.o("otoscopia"); val au = d.o("audiometria"); val lo = d.o("logo"); val co = d.o("conclusion"); val de = d.o("demostracion")
    fun oto(o: Oido) = OtoscopiaOido(ot.o(o.clave).lista("hallazgos"), ot.o(o.clave).s("nota", 500))
    fun aud(o: Oido) = AudiometriaOido(curva(au.o(o.clave).o("aerea"), FRECUENCIAS_AEREA), curva(au.o(o.clave).o("osea"), FRECUENCIAS_OSEA))
    fun log(o: Oido) = Logo(lo.o(o.clave).num("srt", DB_MIN, DB_MAX), lo.o(o.clave).num("discriminacion", 0, 100), lo.o(o.clave).num("intensidad", DB_MIN, DB_MAX))
    fun con(o: Oido): ConclusionOido {
        val c = co.o(o.clave)
        val g = (c?.get("grado") as? JsonPrimitive)?.content; val t = (c?.get("tipo") as? JsonPrimitive)?.content
        return ConclusionOido(GradoPerdida.entries.find { it.clave == g }, TipoPerdida.entries.find { it.clave == t })
    }
    return DatosAuditivos(
        anamnesis = Anamnesis(an.lista("motivos"), lado((an?.get("lado") as? JsonPrimitive)?.content), an.s("tiempo", 120),
            an.b3("ruido"), an.b3("familiares"), an.b3("audifonosPrevios"), an.s("notas", 2000)),
        otoscopia = Otoscopia(oto(Oido.OD), oto(Oido.OI), ot.b3("apto"), ot.s("indicacion", 500)),
        audiometria = mapOf(Oido.OD to aud(Oido.OD), Oido.OI to aud(Oido.OI)),
        logo = mapOf(Oido.OD to log(Oido.OD), Oido.OI to log(Oido.OI)),
        conclusion = Conclusion(con(Oido.OD), con(Oido.OI), co.s("texto", 3000), co.s("recomendacion", 2000)),
        demostracion = Demostracion((de?.get("hecha") as? JsonPrimitive)?.content == "true", de.s("modelo", 200),
            lado((de?.get("oido") as? JsonPrimitive)?.content), de.s("resultado", 1000)),
    )
}

private fun JsonObjectBuilderPut(b: kotlinx.serialization.json.JsonObjectBuilder, k: String, v: Boolean?) {
    if (v == null) b.put(k, JsonNull) else b.put(k, v)
}

fun datosAJson(d: DatosAuditivos): JsonObject = buildJsonObject {
    put("anamnesis", buildJsonObject {
        put("motivos", buildJsonArray { d.anamnesis.motivos.forEach { add(JsonPrimitive(it)) } })
        put("lado", d.anamnesis.lado?.let(::JsonPrimitive) ?: JsonNull)
        put("tiempo", d.anamnesis.tiempo)
        JsonObjectBuilderPut(this, "ruido", d.anamnesis.ruido)
        JsonObjectBuilderPut(this, "familiares", d.anamnesis.familiares)
        JsonObjectBuilderPut(this, "audifonosPrevios", d.anamnesis.audifonosPrevios)
        put("notas", d.anamnesis.notas)
    })
    put("otoscopia", buildJsonObject {
        Oido.entries.forEach { o ->
            val x = d.otoscopia.de(o)
            put(o.clave, buildJsonObject { put("hallazgos", buildJsonArray { x.hallazgos.forEach { add(JsonPrimitive(it)) } }); put("nota", x.nota) })
        }
        JsonObjectBuilderPut(this, "apto", d.otoscopia.apto)
        put("indicacion", d.otoscopia.indicacion)
    })
    put("audiometria", buildJsonObject {
        Oido.entries.forEach { o ->
            val a = d.aud(o)
            fun cj(c: Curva) = buildJsonObject { c.forEach { (f, u) -> put(f.toString(), buildJsonObject { put("db", u.db); if (u.nr) put("nr", true) }) } }
            put(o.clave, buildJsonObject { put("aerea", cj(a.aerea)); put("osea", cj(a.osea)) })
        }
    })
    put("logo", buildJsonObject {
        Oido.entries.forEach { o ->
            val l = d.log(o)
            put(o.clave, buildJsonObject {
                put("srt", l.srt?.let(::JsonPrimitive) ?: JsonNull)
                put("discriminacion", l.discriminacion?.let(::JsonPrimitive) ?: JsonNull)
                put("intensidad", l.intensidad?.let(::JsonPrimitive) ?: JsonNull)
            })
        }
    })
    put("conclusion", buildJsonObject {
        Oido.entries.forEach { o ->
            val c = d.conclusion.de(o)
            put(o.clave, buildJsonObject {
                put("grado", c.grado?.clave?.let(::JsonPrimitive) ?: JsonNull)
                put("tipo", c.tipo?.clave?.let(::JsonPrimitive) ?: JsonNull)
            })
        }
        put("texto", d.conclusion.texto); put("recomendacion", d.conclusion.recomendacion)
    })
    put("demostracion", buildJsonObject {
        put("hecha", d.demostracion.hecha); put("modelo", d.demostracion.modelo)
        put("oido", d.demostracion.oido?.let(::JsonPrimitive) ?: JsonNull); put("resultado", d.demostracion.resultado)
    })
}

// ── Cálculos ────────────────────────────────────────────────────────────────────

private fun r1(n: Double) = (n * 10).roundToInt() / 10.0

/** Promedio tonal (500, 1000, 2000 y 4000 Hz). null si falta alguna. */
fun promedioTonal(c: Curva): Double? {
    val vals = FRECUENCIAS_PTA.map { c[it]?.db ?: return null }
    return r1(vals.sum().toDouble() / vals.size)
}

fun gradoOMS(pta: Double?): GradoPerdida? = when {
    pta == null -> null
    pta < 20 -> GradoPerdida.NORMAL
    pta < 35 -> GradoPerdida.LEVE
    pta < 50 -> GradoPerdida.MODERADA
    pta < 65 -> GradoPerdida.MOD_SEVERA
    pta < 80 -> GradoPerdida.SEVERA
    pta < 95 -> GradoPerdida.PROFUNDA
    else -> GradoPerdida.TOTAL
}

fun brechaAereoOsea(o: AudiometriaOido): Double? {
    val pares = FRECUENCIAS_OSEA.mapNotNull { f -> val a = o.aerea[f]?.db; val b = o.osea[f]?.db; if (a != null && b != null) a - b else null }
    if (pares.isEmpty()) return null
    return r1(pares.sum().toDouble() / pares.size)
}

fun tipoSugerido(o: AudiometriaOido): TipoPerdida? {
    val aerea = promedioTonal(o.aerea) ?: return null
    if (aerea < 20) return TipoPerdida.NORMAL
    val osea = promedioTonal(o.osea) ?: return null
    val brecha = brechaAereoOsea(o) ?: return null
    return if (brecha >= UMBRAL_BRECHA_DB) (if (osea < 20) TipoPerdida.CONDUCTIVA else TipoPerdida.MIXTA) else TipoPerdida.NEUROSENSORIAL
}

data class ResumenOido(val pta: Double?, val grado: GradoPerdida?, val tipo: TipoPerdida?, val brecha: Double?)

fun resumenOido(o: AudiometriaOido): ResumenOido {
    val pta = promedioTonal(o.aerea)
    return ResumenOido(pta, gradoOMS(pta), tipoSugerido(o), brechaAereoOsea(o))
}

/** Lo que vale: lo confirmado por el profesional o, si no tocó nada, la sugerencia. */
data class ConclusionVigente(val grado: GradoPerdida?, val tipo: TipoPerdida?, val sugerido: ResumenOido)
fun conclusionOido(d: DatosAuditivos, o: Oido): ConclusionVigente {
    val s = resumenOido(d.aud(o))
    return ConclusionVigente(d.conclusion.de(o).grado ?: s.grado, d.conclusion.de(o).tipo ?: s.tipo, s)
}

/** Grado de la PERSONA según la OMS: el del mejor oído. */
fun gradoPersona(d: DatosAuditivos): GradoPerdida? {
    val a = promedioTonal(d.aud(Oido.OD).aerea) ?: return null
    val b = promedioTonal(d.aud(Oido.OI).aerea) ?: return null
    return gradoOMS(minOf(a, b))
}

fun sugiereAudifonos(d: DatosAuditivos): Boolean = Oido.entries.any { o ->
    val c = conclusionOido(d, o)
    (c.tipo == TipoPerdida.NEUROSENSORIAL || c.tipo == TipoPerdida.MIXTA) && c.grado != null && c.grado != GradoPerdida.NORMAL
}

fun hallazgosQueImpiden(d: DatosAuditivos): List<String> =
    Oido.entries.flatMap { d.otoscopia.de(it).hallazgos.filter { h -> h in HALLAZGOS_QUE_IMPIDEN } }.distinct()

fun hayAudiometria(d: DatosAuditivos): Boolean = Oido.entries.any { d.aud(it).aerea.isNotEmpty() || d.aud(it).osea.isNotEmpty() }

enum class PasoAuditivo(val titulo: String) { ANAMNESIS("Anamnesis"), OTOSCOPIA("Videotoscopia"), AUDIOMETRIA("Audiometría"), CONCLUSION("Conclusión"), AUDIFONOS("Audífonos") }

fun pasosDisponibles(d: DatosAuditivos): List<PasoAuditivo> = when {
    d.otoscopia.apto == false -> listOf(PasoAuditivo.ANAMNESIS, PasoAuditivo.OTOSCOPIA)
    sugiereAudifonos(d) || d.demostracion.hecha -> PasoAuditivo.entries.toList()
    else -> PasoAuditivo.entries.filter { it != PasoAuditivo.AUDIFONOS }
}

private fun numTxt(n: Double) = if (n % 1.0 == 0.0) n.toInt().toString() else n.toString()

/** «Pérdida moderada neurosensorial (promedio 45 dB HL)». */
fun fraseOido(d: DatosAuditivos, o: Oido): String {
    val c = conclusionOido(d, o)
    val g = c.grado ?: return "Sin audiometría"
    val pta = c.sugerido.pta?.let { " (promedio ${numTxt(it)} dB HL)" } ?: ""
    if (g == GradoPerdida.NORMAL) return "${GradoPerdida.NORMAL.etiqueta}$pta"
    val tipo = c.tipo?.takeIf { it != TipoPerdida.NORMAL }?.let { " ${it.etiqueta.lowercase()}" } ?: ""
    return "${g.etiqueta}$tipo$pta"
}

/** El resumen de una evaluación en la lista (como resumenFila de la web). */
fun resumenEvaluacion(d: DatosAuditivos): String = when {
    d.otoscopia.apto == false -> "Oído no apto para audiometría" + (d.otoscopia.indicacion.takeIf { it.isNotBlank() }?.let { " · $it" } ?: "")
    !hayAudiometria(d) -> "En curso: falta la audiometría"
    else -> "OD: ${fraseOido(d, Oido.OD).lowercase()} · OI: ${fraseOido(d, Oido.OI).lowercase()}"
}

// ── Lectura y escritura ─────────────────────────────────────────────────────────

data class EvaluacionAuditivaFila(val id: String, val citaId: String?, val fecha: String, val datos: DatosAuditivos)

object EvaluacionAuditivaRepo {
    private val json = Json { ignoreUnknownKeys = true }
    private val http = crearHttpClient()
    private val URL get() = "${Supabase.SITE_URL}/api/staff/evaluacion-auditiva"
    private suspend fun token() = Supabase.client.auth.currentSessionOrNull()?.accessToken

    private fun fila(o: JsonObject): EvaluacionAuditivaFila? {
        fun s(k: String) = (o[k] as? JsonPrimitive)?.content?.takeIf { it != "null" }
        return EvaluacionAuditivaFila(s("id") ?: return null, s("cita_id"), s("fecha").orEmpty(), normalizarDatos(o["datos"]))
    }

    /** Las evaluaciones del paciente. null = error. */
    suspend fun listar(pacienteId: String): List<EvaluacionAuditivaFila>? = try {
        val tk = token() ?: throw IllegalStateException()
        val r = http.get(URL) { header("Authorization", "Bearer $tk"); parameter("pacienteId", pacienteId) }
        if (!r.status.isSuccess()) null
        else ((json.parseToJsonElement(r.bodyAsText()) as? JsonObject)?.get("items") as? JsonArray)
            ?.mapNotNull { (it as? JsonObject)?.let(::fila) }.orEmpty()
    } catch (e: CancellationException) { throw e } catch (_: Exception) { null }

    private suspend fun respuesta(r: HttpResponse): Pair<EvaluacionAuditivaFila?, String?> {
        val o = runCatching { json.parseToJsonElement(r.bodyAsText()) as? JsonObject }.getOrNull()
        if (!r.status.isSuccess()) return null to ((o?.get("error") as? JsonPrimitive)?.content ?: "No se pudo guardar la evaluación")
        val e = (o?.get("evaluacion") as? JsonObject)?.let(::fila) ?: return null to "No se pudo guardar la evaluación"
        return e to null
    }

    /** Crea (sin [id]) o actualiza. Devuelve la fila guardada o el error. */
    suspend fun guardar(id: String?, pacienteId: String, citaId: String?, fecha: String, d: DatosAuditivos): Pair<EvaluacionAuditivaFila?, String?> = try {
        val tk = token() ?: return null to "Tu sesión venció. Vuelve a entrar."
        val cuerpo = if (id != null) buildJsonObject { put("id", id); put("datos", datosAJson(d)) }
        else buildJsonObject {
            put("pacienteId", pacienteId); put("citaId", citaId?.let(::JsonPrimitive) ?: JsonNull); put("fecha", fecha); put("datos", datosAJson(d))
        }
        val r = if (id != null) http.patch(URL) { header("Authorization", "Bearer $tk"); contentType(ContentType.Application.Json); setBody(cuerpo.toString()) }
        else http.post(URL) { header("Authorization", "Bearer $tk"); contentType(ContentType.Application.Json); setBody(cuerpo.toString()) }
        respuesta(r)
    } catch (e: CancellationException) { throw e } catch (_: Exception) { null to "Sin conexión. Intenta de nuevo." }
}

/** ¿La especialidad es de audiología? Por el NOMBRE, como la web (`esAudiologia`). */
fun esAudiologia(nombre: String?): Boolean =
    Regex("audiolog|audiometr|audifon|auditiv").containsMatchIn(
        nombre.orEmpty().lowercase().replace('í', 'i').replace('ó', 'o').replace('á', 'a').replace('é', 'e').replace('ú', 'u'),
    )
