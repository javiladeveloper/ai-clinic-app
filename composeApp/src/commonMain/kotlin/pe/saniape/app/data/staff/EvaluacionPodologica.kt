package pe.saniape.app.data.staff

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.put
import kotlin.math.roundToInt

/**
 * 🦶 EVALUACIÓN DEL PIE (podología): mapa de zonas y hallazgos, tamizaje de pie
 * diabético con la categoría de riesgo IWGDF 2023, tipo de pie, pisada,
 * calzado, higiene y observaciones. Tabla `evaluaciones_podologicas`, vía
 * /api/staff/evaluacion-podologica (ver [EvaluacionPodologicaRepo]).
 *
 * Gemelo PURO de `lib/evaluacion-podologica.ts` de la web: los catálogos (ids
 * ESTABLES), la normalización, el cálculo del riesgo (el MISMO que el servidor
 * usa al guardar: la app lo muestra en vivo, el servidor lo vuelve a calcular),
 * la validación, el comparativo y los textos del informe. Los textos son
 * idénticos a los de la web.
 *
 * Fuente clínica del riesgo — IWGDF 2023 (Bus SA et al., DMRR 2024;40:e3651 y
 * Schaper NC et al., DMRR 2024;40:e3657). Con el tamizaje INCOMPLETO y nada
 * positivo, el riesgo queda SIN DETERMINAR: nunca se ofrece el control anual
 * sin haber descartado la pérdida de sensibilidad y la EAP.
 *
 * Solo PODOLOGÍA y por PACIENTE: el mapa ([MapaPodologia]) lo arma la web
 * (/api/staff/contexto: `especialidadesPodologia`, `soloPodologia`, por el
 * NOMBRE de la especialidad); un backend sin esos campos deja el mapa vacío y
 * la pestaña no aparece.
 */

// ─── ¿Es podología? ──────────────────────────────────────────────────────────

/** Qué especialidades de la clínica son de podología. `solo` = todas lo son. */
data class MapaPodologia(val ids: List<String> = emptyList(), val solo: Boolean = false) {
    val activo: Boolean get() = solo || ids.isNotEmpty()
}

/** ¿Esta cita / tratamiento es de podología? Misma cadena que `citaEsFisio`. */
fun citaEsPodologia(
    mapa: MapaPodologia,
    especialidadId: String? = null,
    especialidadServicioId: String? = null,
    especialidadesProfesional: List<String>? = null,
): Boolean = atencionEnMapaEspecialidad(mapa.ids, mapa.solo, especialidadId, especialidadServicioId, especialidadesProfesional)

/**
 * ¿La ficha muestra la pestaña 🦶 Evaluación del pie? (gemelo de
 * `pacienteEsPodologia`): clínica solo de podología → sí; sin podología → no;
 * mixta → si ya tiene evaluaciones, si alguna atención es de podología o si
 * quien mira es podólogo.
 */
fun pacienteEsPodologia(
    mapa: MapaPodologia,
    atenciones: List<AtencionFisio> = emptyList(),
    tieneEvaluaciones: Boolean = false,
    especialidadesDeQuienMira: List<String>? = null,
): Boolean {
    if (mapa.solo) return true
    if (mapa.ids.isEmpty()) return false
    if (tieneEvaluaciones) return true
    if (atenciones.any { citaEsPodologia(mapa, it.especialidadId, it.especialidadServicioId, it.especialidadesProfesional) }) return true
    return especialidadesDeQuienMira.orEmpty().any { it.isNotBlank() && it in mapa.ids }
}

/**
 * La cadena de `citaEsFisio` / `citaEsNutricion` (atencionEnMapa de la web):
 * especialidad de la cita → la del servicio → el profesional (si TODAS sus
 * especialidades están en el mapa). Lo que no se sabe es NO.
 */
internal fun atencionEnMapaEspecialidad(
    ids: List<String>, solo: Boolean,
    especialidadId: String?, especialidadServicioId: String?, especialidadesProfesional: List<String>?,
): Boolean {
    if (solo) return true
    if (ids.isEmpty()) return false
    listOf(especialidadId, especialidadServicioId).firstOrNull { !it.isNullOrBlank() }?.let { return it in ids }
    val prof = especialidadesProfesional.orEmpty().filter { it.isNotBlank() }
    return prof.isNotEmpty() && prof.all { it in ids }
}

// ─── Catálogos (ids ESTABLES: los de la web) ─────────────────────────────────

const val PIE_DER = "der"
const val PIE_IZQ = "izq"
val PIES_PODO = listOf(PIE_DER, PIE_IZQ)
fun nombrePie(pie: String): String = if (pie == PIE_IZQ) "Pie izquierdo" else "Pie derecho"
private fun cortoPie(pie: String): String = if (pie == PIE_IZQ) "Izq." else "Der."
fun nombreVistaPie(vista: String): String = if (vista == "dorsal") "Dorsal" else "Plantar"

/** Una zona del pie. tipo 'una' = uña (se sugieren los hallazgos de uña); 'piel' = el resto. */
data class ZonaPodo(val id: String, val vista: String, val nombre: String, val tipo: String)

/** 17 zonas por pie: 8 plantares y 9 dorsales. */
val ZONAS_PODO: List<ZonaPodo> = listOf(
    ZonaPodo("hallux", "plantar", "Hallux (pulpejo)", "piel"),
    ZonaPodo("dedos", "plantar", "Dedos 2.º a 5.º", "piel"),
    ZonaPodo("met1", "plantar", "Cabeza del 1.er metatarsiano", "piel"),
    ZonaPodo("met_centrales", "plantar", "Cabezas metatarsianas centrales (2.ª–4.ª)", "piel"),
    ZonaPodo("met5", "plantar", "Cabeza del 5.º metatarsiano", "piel"),
    ZonaPodo("arco", "plantar", "Arco (bóveda plantar)", "piel"),
    ZonaPodo("borde_lateral", "plantar", "Borde lateral", "piel"),
    ZonaPodo("talon", "plantar", "Talón", "piel"),
    ZonaPodo("una1", "dorsal", "Uña del 1.er dedo (hallux)", "una"),
    ZonaPodo("una2", "dorsal", "Uña del 2.º dedo", "una"),
    ZonaPodo("una3", "dorsal", "Uña del 3.er dedo", "una"),
    ZonaPodo("una4", "dorsal", "Uña del 4.º dedo", "una"),
    ZonaPodo("una5", "dorsal", "Uña del 5.º dedo", "una"),
    ZonaPodo("dorso_dedos", "dorsal", "Dorso de los dedos", "piel"),
    ZonaPodo("interdigital", "dorsal", "Espacios interdigitales", "piel"),
    ZonaPodo("dorso", "dorsal", "Dorso del pie", "piel"),
    ZonaPodo("borde_medial", "dorsal", "Borde medial (1.ª articulación, juanete)", "piel"),
)
private val ZONA_PODO_POR_ID = ZONAS_PODO.associateBy { it.id }
fun zonaPodo(id: String?): ZonaPodo? = id?.let { ZONA_PODO_POR_ID[it] }

/** Grupos de hallazgos: el color de la zona en el mapa (y la leyenda). Hex: igual en modo oscuro e impresos. */
data class GrupoHallazgoPodo(val id: String, val nombre: String, val color: String)

val GRUPOS_HALLAZGO_PODO: List<GrupoHallazgoPodo> = listOf(
    GrupoHallazgoPodo("ulcera", "Úlcera", "#dc2626"),
    GrupoHallazgoPodo("herida", "Fisura, ampolla, maceración", "#ea580c"),
    GrupoHallazgoPodo("inflamacion", "Eritema, edema", "#db2777"),
    GrupoHallazgoPodo("presion", "Callos y helomas", "#d97706"),
    GrupoHallazgoPodo("una", "Uñas", "#7c3aed"),
    GrupoHallazgoPodo("infeccion", "Verruga, hongos de piel", "#0f766e"),
)
fun colorGrupoPodo(grupo: String): String = GRUPOS_HALLAZGO_PODO.firstOrNull { it.id == grupo }?.color ?: "#6b7280"

/** Un hallazgo del catálogo. `gravedad` decide el "peor hallazgo" de la zona. */
data class HallazgoPodoDef(val id: String, val nombre: String, val grupo: String, val aplica: String, val gravedad: Int)

val HALLAZGOS_PODO: List<HallazgoPodoDef> = listOf(
    HallazgoPodoDef("ulcera", "Úlcera", "ulcera", "ambos", 100),
    HallazgoPodoDef("fisura", "Fisura / grieta", "herida", "piel", 70),
    HallazgoPodoDef("ampolla", "Ampolla", "herida", "piel", 65),
    HallazgoPodoDef("eritema", "Eritema", "inflamacion", "ambos", 60),
    HallazgoPodoDef("edema", "Edema", "inflamacion", "ambos", 58),
    HallazgoPodoDef("maceracion", "Maceración", "herida", "piel", 55),
    HallazgoPodoDef("onicocriptosis", "Onicocriptosis (uña encarnada)", "una", "una", 50),
    HallazgoPodoDef("hematoma_subungueal", "Hematoma subungueal", "una", "una", 45),
    HallazgoPodoDef("heloma_duro", "Heloma duro", "presion", "piel", 40),
    HallazgoPodoDef("heloma_blando", "Heloma blando", "presion", "piel", 40),
    HallazgoPodoDef("hiperqueratosis", "Hiperqueratosis / callo", "presion", "piel", 30),
    HallazgoPodoDef("verruga", "Verruga plantar", "infeccion", "piel", 28),
    HallazgoPodoDef("tina", "Pie de atleta (tiña)", "infeccion", "piel", 26),
    HallazgoPodoDef("onicogrifosis", "Onicogrifosis", "una", "una", 22),
    HallazgoPodoDef("onicomicosis", "Onicomicosis (hongos)", "una", "una", 20),
)
private val HALLAZGO_POR_ID = HALLAZGOS_PODO.associateBy { it.id }
fun hallazgoPodo(id: String?): HallazgoPodoDef? = id?.let { HALLAZGO_POR_ID[it] }

data class HallazgosDeZona(val sugeridos: List<HallazgoPodoDef>, val otros: List<HallazgoPodoDef>)

/** Los hallazgos para una zona: primero los que le corresponden (uña o piel), luego el resto. */
fun hallazgosParaZona(zonaId: String): HallazgosDeZona {
    val tipo = zonaPodo(zonaId)?.tipo ?: "piel"
    val sugeridos = HALLAZGOS_PODO.filter { it.aplica == "ambos" || it.aplica == tipo }
    return HallazgosDeZona(sugeridos, HALLAZGOS_PODO.filter { it !in sugeridos })
}

val SEVERIDADES_PODO: List<Pair<Int, String>> = listOf(1 to "Leve", 2 to "Moderado", 3 to "Severo")
fun nombreSeveridad(s: Int): String = SEVERIDADES_PODO.firstOrNull { it.first == s }?.second ?: "Leve"
/** Opacidad del relleno de la zona según la severidad de su peor hallazgo. */
fun opacidadSeveridad(s: Int): Float = when (s) { 3 -> 0.95f; 2 -> 0.7f; else -> 0.45f }

/** Monofilamento 10 g: los 3 sitios por pie de la guía práctica IWGDF. */
data class SitioMonofilamento(val id: String, val pie: String, val zona: String, val nombre: String)

val SITIOS_MONOFILAMENTO: List<SitioMonofilamento> = PIES_PODO.flatMap { pie ->
    listOf(
        SitioMonofilamento("${pie}_hallux", pie, "hallux", "Pulpejo del hallux"),
        SitioMonofilamento("${pie}_met1", pie, "met1", "1.ª cabeza metatarsiana"),
        SitioMonofilamento("${pie}_met5", pie, "met5", "5.ª cabeza metatarsiana"),
    )
}

/** Estados del pulso: presente / disminuido / ausente. */
val ESTADOS_PULSO: List<Pair<String, String>> = listOf("presente" to "Presente", "disminuido" to "Disminuido", "ausente" to "Ausente")

data class PulsoPodo(val id: String, val pie: String, val nombre: String)

val PULSOS_PODO: List<PulsoPodo> = PIES_PODO.flatMap { pie ->
    listOf(PulsoPodo("${pie}_pedio", pie, "Pedio"), PulsoPodo("${pie}_tibial", pie, "Tibial posterior"))
}

/** Opción de una lista (id estable → nombre). */
data class OpcionPodo(val id: String, val nombre: String)

val DEFORMIDADES_PODO: List<OpcionPodo> = listOf(
    OpcionPodo("hallux_valgus", "Hallux valgus (juanete)"),
    OpcionPodo("dedos_garra", "Dedos en garra / martillo"),
    OpcionPodo("prominencia_mt", "Prominencia de cabezas metatarsianas"),
    OpcionPodo("charcot", "Pie de Charcot"),
    OpcionPodo("pie_cavo", "Pie cavo"),
    OpcionPodo("pie_plano_rigido", "Pie plano rígido"),
    OpcionPodo("quintus_varo", "Juanete de sastre (5.º dedo)"),
    OpcionPodo("limitacion_articular", "Limitación de la movilidad articular"),
)

val TIPOS_PIE: List<OpcionPodo> = listOf(OpcionPodo("normal", "Normal"), OpcionPodo("plano", "Plano"), OpcionPodo("cavo", "Cavo"))
val PISADAS: List<OpcionPodo> = listOf(OpcionPodo("neutra", "Neutra"), OpcionPodo("pronadora", "Pronadora"), OpcionPodo("supinadora", "Supinadora"))
val HIGIENES: List<OpcionPodo> = listOf(OpcionPodo("buena", "Buena"), OpcionPodo("regular", "Regular"), OpcionPodo("deficiente", "Deficiente"))
private fun nombreDeLista(lista: List<OpcionPodo>, id: String?): String? = id?.let { i -> lista.firstOrNull { it.id == i }?.nombre }

/** Límites: gemelos de los CHECK de la base. */
object LimitesEvalPodo {
    const val CALZADO = 500
    const val OBSERVACIONES = 4000
}

// ─── Modelo ──────────────────────────────────────────────────────────────────

data class HallazgoMarcado(val tipo: String, val severidad: Int)

data class ZonaMarcadaPodo(val pie: String, val vista: String, val zona: String, val hallazgos: List<HallazgoMarcado>) {
    val clave: String get() = "$pie:$vista:$zona"
}

/**
 * Tamizaje de pie diabético. Mapas con TODAS las claves (null = sin evaluar).
 * [completo] lo pone el SERVIDOR al guardar (lo que mande el cliente se ignora).
 */
data class TamizajePodo(
    val diabetes: Boolean,
    /** der_hallux… → true siente · false no siente · null sin evaluar. */
    val monofilamento: Map<String, Boolean?>,
    /** der / izq → "presente" | "ausente" | null. */
    val vibracion: Map<String, String?>,
    /** der_pedio, der_tibial, izq_pedio, izq_tibial → "presente" | "disminuido" | "ausente" | null. */
    val pulsos: Map<String, String?>,
    val eapDiagnosticada: Boolean = false,
    val deformidades: List<String> = emptyList(),
    val antecedenteUlcera: Boolean = false,
    val amputacion: Boolean = false,
    val erc: Boolean = false,
    val completo: Boolean? = null,
)

fun tamizajeVacio(diabetes: Boolean = true): TamizajePodo = TamizajePodo(
    diabetes = diabetes,
    monofilamento = SITIOS_MONOFILAMENTO.associate { it.id to null },
    vibracion = mapOf(PIE_DER to null, PIE_IZQ to null),
    pulsos = PULSOS_PODO.associate { it.id to null },
)

/** Forma completa y limpia (claves de la lista, valores válidos, deformidades del catálogo en su orden). */
fun normalizarTamizaje(t: TamizajePodo): TamizajePodo = t.copy(
    monofilamento = SITIOS_MONOFILAMENTO.associate { it.id to t.monofilamento[it.id] },
    vibracion = PIES_PODO.associateWith { p -> t.vibracion[p]?.takeIf { it == "presente" || it == "ausente" } },
    pulsos = PULSOS_PODO.associate { p -> p.id to t.pulsos[p.id]?.takeIf { v -> ESTADOS_PULSO.any { it.first == v } } },
    deformidades = DEFORMIDADES_PODO.map { it.id }.filter { it in t.deformidades },
)

data class EvaluacionPodologica(
    val id: String,
    val pacienteId: String,
    val citaId: String? = null,
    val tratamientoId: String? = null,
    val terapeutaId: String? = null,
    val fecha: String,
    val zonas: List<ZonaMarcadaPodo> = emptyList(),
    val tamizaje: TamizajePodo? = null,
    val riesgoIwgdf: Int? = null,
    val tipoPie: String? = null,
    val pisada: String? = null,
    val higiene: String? = null,
    val calzado: String? = null,
    val observaciones: String? = null,
    val createdAt: String? = null,
)

/** Lo que arma el formulario (lo que se manda a la API). */
data class BorradorEvaluacionPodo(
    val pacienteId: String,
    val fecha: String? = null,
    val citaId: String? = null,
    val tratamientoId: String? = null,
    val zonas: List<ZonaMarcadaPodo> = emptyList(),
    val tamizaje: TamizajePodo? = null,
    val tipoPie: String? = null,
    val pisada: String? = null,
    val higiene: String? = null,
    val calzado: String? = null,
    val observaciones: String? = null,
)

// ─── Normalización de las zonas ──────────────────────────────────────────────

private fun severidadValida(v: Int): Int = if (v in 1..3) v else 1

/**
 * Zonas → lista limpia: pie y zona conocidos (la vista es la de la zona),
 * hallazgos conocidos sin repetir (queda la severidad mayor), severidad 1–3,
 * sin zonas vacías; una zona repetida se fusiona. Orden: pie derecho y luego
 * el izquierdo, cada uno en el orden del catálogo.
 */
fun normalizarZonasPodo(zonas: List<ZonaMarcadaPodo>): List<ZonaMarcadaPodo> {
    val porClave = LinkedHashMap<String, Pair<ZonaMarcadaPodo, MutableList<HallazgoMarcado>>>()
    for (x in zonas) {
        if (x.pie != PIE_DER && x.pie != PIE_IZQ) continue
        val def = zonaPodo(x.zona) ?: continue
        val clave = "${x.pie}:${def.vista}:${def.id}"
        val (_, hs) = porClave.getOrPut(clave) { ZonaMarcadaPodo(x.pie, def.vista, def.id, emptyList()) to mutableListOf() }
        for (h in x.hallazgos) {
            if (hallazgoPodo(h.tipo) == null) continue
            val sev = severidadValida(h.severidad)
            val i = hs.indexOfFirst { it.tipo == h.tipo }
            if (i >= 0) hs[i] = hs[i].copy(severidad = maxOf(hs[i].severidad, sev)) else hs += HallazgoMarcado(h.tipo, sev)
        }
    }
    fun orden(z: ZonaMarcadaPodo) = (if (z.pie == PIE_DER) 0 else 100) + ZONAS_PODO.indexOfFirst { it.id == z.zona }
    return porClave.values.map { (z, hs) -> z.copy(hallazgos = hs.toList()) }
        .filter { it.hallazgos.isNotEmpty() }
        .sortedBy { orden(it) }
}

// ─── JSON (snake_case dentro de zonas/tamizaje, como se guarda) ──────────────

private fun JsonElement?.prim(): JsonPrimitive? = (this as? JsonPrimitive)?.takeIf { it !is JsonNull }
private fun JsonElement?.textoPodo(): String? = prim()?.takeIf { it.isString }?.content
private fun JsonObject.txtPodo(k: String): String? =
    this[k].prim()?.content?.takeIf { it.isNotBlank() && it != "null" }
private fun JsonElement?.esTrue(): Boolean = prim()?.let { !it.isString && it.booleanOrNull == true } == true

/** Zonas tal como llegan (JSON) → lista limpia. No es lista → vacía. */
fun zonasPodoDeJson(v: JsonElement?): List<ZonaMarcadaPodo> {
    val arr = v as? JsonArray ?: return emptyList()
    val crudas = arr.mapNotNull { x ->
        val o = x as? JsonObject ?: return@mapNotNull null
        val pie = o["pie"].textoPodo() ?: return@mapNotNull null
        val zona = o["zona"].textoPodo() ?: return@mapNotNull null
        val hs = (o["hallazgos"] as? JsonArray).orEmpty().mapNotNull { h ->
            val ho = h as? JsonObject ?: return@mapNotNull null
            val tipo = ho["tipo"].textoPodo() ?: return@mapNotNull null
            val sev = ho["severidad"].prim()?.let { p -> p.doubleOrNull ?: p.content.toDoubleOrNull() }
            HallazgoMarcado(tipo, sev?.takeIf { it.isFinite() }?.roundToInt() ?: 1)
        }
        ZonaMarcadaPodo(pie, zonaPodo(zona)?.vista ?: "plantar", zona, hs)
    }
    return normalizarZonasPodo(crudas)
}

/** Tamizaje tal como llega → forma completa y limpia. No es objeto → null (sin tamizaje). */
fun tamizajePodoDeJson(v: JsonElement?): TamizajePodo? {
    val o = v as? JsonObject ?: return null
    val mono = o["monofilamento"] as? JsonObject
    val vib = o["vibracion"] as? JsonObject
    val pul = o["pulsos"] as? JsonObject
    val defs = (o["deformidades"] as? JsonArray)?.mapNotNull { it.textoPodo() }.orEmpty()
    return normalizarTamizaje(
        TamizajePodo(
            diabetes = o["diabetes"].esTrue(),
            monofilamento = SITIOS_MONOFILAMENTO.associate { s ->
                s.id to mono?.get(s.id).prim()?.takeIf { !it.isString }?.booleanOrNull
            },
            vibracion = PIES_PODO.associateWith { p -> vib?.get(p).textoPodo() },
            pulsos = PULSOS_PODO.associate { p -> p.id to pul?.get(p.id).textoPodo() },
            eapDiagnosticada = o["eap_diagnosticada"].esTrue(),
            deformidades = defs,
            antecedenteUlcera = o["antecedente_ulcera"].esTrue(),
            amputacion = o["amputacion"].esTrue(),
            erc = o["erc"].esTrue(),
            completo = o["completo"].prim()?.takeIf { !it.isString }?.booleanOrNull,
        ),
    )
}

fun zonasPodoAJson(zonas: List<ZonaMarcadaPodo>): JsonArray = buildJsonArray {
    normalizarZonasPodo(zonas).forEach { z ->
        add(buildJsonObject {
            put("pie", z.pie); put("vista", z.vista); put("zona", z.zona)
            put("hallazgos", buildJsonArray {
                z.hallazgos.forEach { h -> add(buildJsonObject { put("tipo", h.tipo); put("severidad", h.severidad) }) }
            })
        })
    }
}

/** El tamizaje tal como lo espera la API (snake_case). `completo` no se manda: lo pone el servidor. */
fun tamizajePodoAJson(t: TamizajePodo): JsonObject {
    val n = normalizarTamizaje(t)
    return buildJsonObject {
        put("diabetes", n.diabetes)
        put("monofilamento", buildJsonObject { n.monofilamento.forEach { (k, v) -> if (v == null) put(k, JsonNull) else put(k, v) } })
        put("vibracion", buildJsonObject { n.vibracion.forEach { (k, v) -> if (v == null) put(k, JsonNull) else put(k, v) } })
        put("pulsos", buildJsonObject { n.pulsos.forEach { (k, v) -> if (v == null) put(k, JsonNull) else put(k, v) } })
        put("eap_diagnosticada", n.eapDiagnosticada)
        put("deformidades", buildJsonArray { n.deformidades.forEach { add(JsonPrimitive(it)) } })
        put("antecedente_ulcera", n.antecedenteUlcera)
        put("amputacion", n.amputacion)
        put("erc", n.erc)
    }
}

/** Fila del API → modelo. Sin id se descarta. */
fun evaluacionPodoDeJson(o: JsonObject): EvaluacionPodologica? {
    val id = o.txtPodo("id") ?: return null
    return EvaluacionPodologica(
        id = id,
        pacienteId = o.txtPodo("paciente_id").orEmpty(),
        citaId = o.txtPodo("cita_id"),
        tratamientoId = o.txtPodo("tratamiento_id"),
        terapeutaId = o.txtPodo("terapeuta_id"),
        fecha = o.txtPodo("fecha").orEmpty().take(10),
        zonas = zonasPodoDeJson(o["zonas"]),
        tamizaje = tamizajePodoDeJson(o["tamizaje"]),
        riesgoIwgdf = o["riesgo_iwgdf"].prim()?.content?.toDoubleOrNull()?.roundToInt()?.takeIf { it in 0..3 },
        tipoPie = o.txtPodo("tipo_pie"),
        pisada = o.txtPodo("pisada"),
        higiene = o.txtPodo("higiene"),
        calzado = o.txtPodo("calzado"),
        observaciones = o.txtPodo("observaciones"),
        createdAt = o.txtPodo("created_at"),
    )
}

/**
 * Cuerpo de POST (sin [id]) o PATCH (con [id]): camelCase arriba, snake_case
 * dentro de zonas/tamizaje. No diabético y nada registrado = sin tamizaje. El
 * riesgo NO se manda (lo calcula el servidor).
 */
fun cuerpoEvaluacionPodo(b: BorradorEvaluacionPodo, id: String? = null): JsonObject = buildJsonObject {
    if (id == null) {
        put("pacienteId", b.pacienteId)
        b.citaId?.takeIf { it.isNotBlank() }?.let { put("citaId", it) }
        b.tratamientoId?.takeIf { it.isNotBlank() }?.let { put("tratamientoId", it) }
    } else put("id", id)
    b.fecha?.takeIf { it.isNotBlank() }?.let { put("fecha", it) }
    put("zonas", zonasPodoAJson(b.zonas))
    val t = b.tamizaje?.takeIf { tamizajeTieneDatos(it) }
    if (t == null) put("tamizaje", JsonNull) else put("tamizaje", tamizajePodoAJson(t))
    fun opc(k: String, v: String?) { val s = v?.trim()?.takeIf { it.isNotEmpty() }; if (s == null) put(k, JsonNull) else put(k, s) }
    opc("tipoPie", b.tipoPie)
    opc("pisada", b.pisada)
    opc("higiene", b.higiene)
    opc("calzado", b.calzado)
    opc("observaciones", b.observaciones)
}

// ─── Riesgo IWGDF 2023 ───────────────────────────────────────────────────────

/** Una categoría del sistema de estratificación IWGDF 2023 (Bus et al., DMRR 2024;40:e3651). */
data class CategoriaIwgdf(
    val categoria: Int,
    val nombre: String,
    val criterio: String,
    val control: String,
    /** Para agendar: el intervalo MENOR del rango (lo prudente). */
    val meses: Int,
    /** Hex: el mismo en pantalla y en papel. */
    val color: String,
)

val CATEGORIAS_IWGDF: List<CategoriaIwgdf> = listOf(
    CategoriaIwgdf(0, "Muy bajo", "Sin pérdida de sensibilidad protectora ni enfermedad arterial periférica", "Una vez al año", 12, "#16a34a"),
    CategoriaIwgdf(1, "Bajo", "Pérdida de sensibilidad protectora o enfermedad arterial periférica", "Cada 6 a 12 meses", 6, "#d97706"),
    CategoriaIwgdf(2, "Moderado", "Pérdida de sensibilidad + EAP, o cualquiera de ellas + deformidad del pie", "Cada 3 a 6 meses", 3, "#ea580c"),
    CategoriaIwgdf(3, "Alto", "Pérdida de sensibilidad o EAP + úlcera previa, amputación o enfermedad renal terminal", "Cada 1 a 3 meses", 1, "#dc2626"),
)
fun categoriaIwgdf(c: Int?): CategoriaIwgdf? = c?.let { x -> CATEGORIAS_IWGDF.firstOrNull { it.categoria == x } }

data class ResultadoRiesgoPodo(
    /** null = no aplica, o SIN DETERMINAR (diabético con el tamizaje incompleto y nada positivo). */
    val categoria: Int?,
    val aplica: Boolean,
    val sinDeterminar: Boolean,
    val lops: Boolean,
    val eap: Boolean,
    val deformidad: Boolean,
    val factorAlto: Boolean,
    val completo: Boolean,
    val motivos: List<String>,
    val avisos: List<String>,
)

private val RIESGO_VACIO = ResultadoRiesgoPodo(null, false, false, false, false, false, false, false, emptyList(), emptyList())
private fun ladoTexto(pie: String) = if (pie == PIE_DER) "derecho" else "izquierdo"

/**
 * Categoría IWGDF 2023 a partir del tamizaje (gemelo EXACTO de `riesgoIwgdf`):
 *  · LOPS = algún sitio del monofilamento "no siente" o la vibración ausente.
 *  · EAP = algún pulso disminuido o ausente, o EAP ya diagnosticada.
 *  · 3 = (LOPS o EAP) y (úlcera previa, amputación o ERC terminal)
 *  · 2 = LOPS + EAP, LOPS + deformidad o EAP + deformidad · 1 = LOPS o EAP · 0 = ninguna.
 * Con el tamizaje INCOMPLETO: si nada da ≥ 1 → sin determinar (null); si ya da
 * ≥ 1 → esa categoría, marcada incompleta (puede subir).
 */
fun riesgoIwgdf(entrada: TamizajePodo?): ResultadoRiesgoPodo {
    val t = entrada?.let { normalizarTamizaje(it) } ?: return RIESGO_VACIO
    if (!t.diabetes) return RIESGO_VACIO
    val motivos = mutableListOf<String>()
    val avisos = mutableListOf<String>()
    val insensibles = SITIOS_MONOFILAMENTO.filter { t.monofilamento[it.id] == false }
    val vibAusente = PIES_PODO.filter { t.vibracion[it] == "ausente" }
    val lops = insensibles.isNotEmpty() || vibAusente.isNotEmpty()
    if (insensibles.isNotEmpty()) motivos += "Pérdida de sensibilidad protectora: no siente el monofilamento en " +
        insensibles.joinToString(", ") { "${it.nombre.lowercase()} ${ladoTexto(it.pie)}" }
    if (vibAusente.isNotEmpty()) motivos += "Vibración ausente (diapasón 128 Hz) en " +
        vibAusente.joinToString(" y ") { nombrePie(it).lowercase() }

    val pulsosMal = PULSOS_PODO.filter { t.pulsos[it.id] == "disminuido" || t.pulsos[it.id] == "ausente" }
    val eap = pulsosMal.isNotEmpty() || t.eapDiagnosticada
    if (pulsosMal.isNotEmpty()) motivos += "Enfermedad arterial periférica probable: pulso " +
        pulsosMal.joinToString(", ") { "${it.nombre.lowercase()} ${ladoTexto(it.pie)} ${t.pulsos[it.id]}" }
    if (t.eapDiagnosticada) motivos += "Enfermedad arterial periférica ya diagnosticada"

    val deformidad = t.deformidades.isNotEmpty()
    if (deformidad) motivos += "Deformidad: " + t.deformidades.joinToString(", ") { d ->
        DEFORMIDADES_PODO.firstOrNull { it.id == d }?.nombre?.lowercase() ?: d
    }

    val altos = listOfNotNull(
        "antecedente de úlcera".takeIf { t.antecedenteUlcera },
        "amputación".takeIf { t.amputacion },
        "enfermedad renal terminal".takeIf { t.erc },
    )
    val factorAlto = altos.isNotEmpty()
    if (factorAlto) motivos += "Factor de alto riesgo: ${altos.joinToString(", ")}"

    val calculada = when {
        (lops || eap) && factorAlto -> 3
        (lops && eap) || (lops && deformidad) || (eap && deformidad) -> 2
        lops || eap -> 1
        else -> 0
    }
    val completo = tamizajeCompleto(t)
    val sinDeterminar = !completo && calculada == 0
    val categoria = if (sinDeterminar) null else calculada
    if (!completo) {
        val falta = faltaDelTamizaje(t)
        avisos += when {
            sinDeterminar -> "Riesgo sin determinar: completa el tamizaje (falta $falta). Sin descartar la pérdida de sensibilidad y la EAP no hay categoría 0."
            calculada < 3 -> "Tamizaje incompleto: falta $falta. La categoría puede subir al completarlo."
            else -> "Tamizaje incompleto: falta $falta."
        }
    }
    if (factorAlto && !lops && !eap && completo) {
        avisos += "Tiene úlcera previa, amputación o enfermedad renal terminal sin pérdida de sensibilidad ni EAP registradas: revise el tamizaje. El juicio clínico manda."
    }
    return ResultadoRiesgoPodo(categoria, true, sinDeterminar, lops, eap, deformidad, factorAlto, completo, motivos, avisos)
}

/**
 * ¿El tamizaje permite DESCARTAR la pérdida de sensibilidad y la EAP? Los 6
 * sitios del monofilamento registrados y los 4 pulsos (o la EAP ya
 * diagnosticada). La vibración es complementaria: no hace falta.
 */
fun tamizajeCompleto(entrada: TamizajePodo?): Boolean {
    val t = entrada?.let { normalizarTamizaje(it) } ?: return false
    val mono = SITIOS_MONOFILAMENTO.all { t.monofilamento[it.id] != null }
    val pulsos = t.eapDiagnosticada || PULSOS_PODO.all { t.pulsos[it.id] != null }
    return mono && pulsos
}

/** "monofilamento (2/6 sitios) y pulsos (0/4)". */
private fun faltaDelTamizaje(t: TamizajePodo): String {
    val monoHechos = SITIOS_MONOFILAMENTO.count { t.monofilamento[it.id] != null }
    val pulsosHechos = PULSOS_PODO.count { t.pulsos[it.id] != null }
    return listOfNotNull(
        "monofilamento ($monoHechos/${SITIOS_MONOFILAMENTO.size} sitios)".takeIf { monoHechos < SITIOS_MONOFILAMENTO.size },
        "pulsos ($pulsosHechos/${PULSOS_PODO.size})".takeIf { pulsosHechos < PULSOS_PODO.size && !t.eapDiagnosticada },
    ).joinToString(" y ")
}

/** ¿El tamizaje trae ALGO? (no diabético y nada registrado = vacío: no se guarda). */
fun tamizajeTieneDatos(entrada: TamizajePodo?): Boolean {
    val t = entrada?.let { normalizarTamizaje(it) } ?: return false
    return t.diabetes || t.eapDiagnosticada || t.antecedenteUlcera || t.amputacion || t.erc || t.deformidades.isNotEmpty() ||
        t.monofilamento.values.any { it != null } || t.pulsos.values.any { it != null } || t.vibracion.values.any { it != null }
}

/** Cómo mostrar el riesgo GUARDADO de una evaluación (badge, informe). */
data class EstadoRiesgoPodo(val categoria: Int?, val sinDeterminar: Boolean, val incompleto: Boolean)

fun estadoRiesgo(tamizaje: TamizajePodo?, riesgoIwgdf: Int?): EstadoRiesgoPodo {
    val categoria = categoriaIwgdf(riesgoIwgdf)?.categoria
    val t = tamizaje?.let { normalizarTamizaje(it) }
    if (t == null || !t.diabetes) return EstadoRiesgoPodo(null, sinDeterminar = false, incompleto = false)
    val completo = tamizajeCompleto(t)
    // Una fila guardada ANTES de la regla puede traer 0 con el tamizaje incompleto:
    // la categoría 0 exige descartar LOPS y EAP, así que también es "sin determinar"
    // (nunca "Muy bajo" ni el control anual).
    if (categoria == null || (categoria == 0 && !completo)) return EstadoRiesgoPodo(null, sinDeterminar = true, incompleto = false)
    return EstadoRiesgoPodo(categoria, sinDeterminar = false, incompleto = !completo)
}

fun estadoRiesgo(ev: EvaluacionPodologica): EstadoRiesgoPodo = estadoRiesgo(ev.tamizaje, ev.riesgoIwgdf)

/** Texto del riesgo para leer ("Riesgo IWGDF 2 · Moderado (tamizaje incompleto)"). null = no aplica. */
fun textoRiesgo(e: EstadoRiesgoPodo): String? {
    if (e.sinDeterminar) return "Riesgo sin determinar: completa el tamizaje (monofilamento y pulsos)"
    val c = categoriaIwgdf(e.categoria) ?: return null
    return "Riesgo IWGDF ${c.categoria} · ${c.nombre}${if (e.incompleto) " (tamizaje incompleto)" else ""}"
}

/** Fecha sugerida del próximo control: hoy + el intervalo menor de la categoría. Sin categoría (o sin determinar) → null. */
fun fechaProximoControl(hoy: String, categoria: Int?): String? {
    val c = categoriaIwgdf(categoria) ?: return null
    return sumarMesesIso(hoy, c.meses)
}

/**
 * "📅 Agendar próximo control": se cuenta desde el día de la evaluación; si esa
 * fecha ya pasó, desde hoy. null = no hay control que proponer (sin riesgo o
 * sin determinar: primero se completa el tamizaje).
 */
fun fechaControlParaAgendar(ev: EvaluacionPodologica, hoy: String): String? {
    val est = estadoRiesgo(ev)
    if (est.sinDeterminar) return null
    val desde = fechaProximoControl(ev.fecha, est.categoria)
    return if (desde != null && desde >= hoy) desde else fechaProximoControl(hoy, est.categoria)
}

/** Gemelo de `sumarMeses` (lib/cuotas): mismo día, recortado al último del mes. */
fun sumarMesesIso(fecha: String, meses: Int): String? {
    val m = Regex("""^(\d{4})-(\d{2})-(\d{2})""").find(fecha) ?: return null
    val y = m.groupValues[1].toInt()
    val mes = m.groupValues[2].toInt()
    val d = m.groupValues[3].toInt()
    val total = (mes - 1) + meses
    val ny = y + total.floorDiv(12)
    val nm = total.mod(12) + 1
    val dia = minOf(d, diasDelMesPodo(ny, nm))
    return "${ny.toString().padStart(4, '0')}-${nm.toString().padStart(2, '0')}-${dia.toString().padStart(2, '0')}"
}

private fun diasDelMesPodo(y: Int, m: Int): Int = when (m) {
    2 -> if ((y % 4 == 0 && y % 100 != 0) || y % 400 == 0) 29 else 28
    4, 6, 9, 11 -> 30
    else -> 31
}

/** ¿Hay una úlcera ACTIVA en el mapa? No es una categoría: se trata / deriva. */
fun hayUlceraActiva(zonas: List<ZonaMarcadaPodo>?): Boolean = zonas.orEmpty().any { z -> z.hallazgos.any { it.tipo == "ulcera" } }

private val RE_DIABETES = Regex("""diab[eé]t|\bdm ?(1|2|i|ii)\b|\bdm\b""", RegexOption.IGNORE_CASE)
private val RE_NO_DIABETES = Regex("""no (es )?diab[eé]t|niega diab""", RegexOption.IGNORE_CASE)

/** ¿Los antecedentes del paciente dicen diabetes? (valor por defecto del tamizaje). */
fun diabeticoPorAntecedentes(vararg textos: String?): Boolean =
    textos.any { t -> RE_DIABETES.containsMatchIn(t.orEmpty()) && !RE_NO_DIABETES.containsMatchIn(t.orEmpty()) }

/**
 * ¿El diagnóstico pide la evaluación del pie con su tamizaje IWGDF? (pie
 * diabético / diabetes / pie de riesgo). Gemelo de `diagnosticoPideEvaluacionPie`
 * (web 2026-10-16): tras completar con ese diagnóstico se ofrece "🦶 Evaluar el pie".
 */
fun diagnosticoPideEvaluacionPie(diagnostico: String?): Boolean {
    val t = diagnostico.orEmpty()
    return diabeticoPorAntecedentes(t) || Regex("pie (de|en) riesgo", RegexOption.IGNORE_CASE).containsMatchIn(t)
}

/** Recomendaciones para el informe según la categoría (educación IWGDF 2023). */
fun recomendacionesRiesgo(categoria: Int?): List<String> {
    if (categoria == null) return emptyList()
    val base = listOf(
        "Revise sus pies todos los días (planta y entre los dedos); use un espejo o pida ayuda.",
        "Lave los pies a diario con agua tibia y séquelos bien, sobre todo entre los dedos.",
        "Use calzado cómodo que respete la forma del pie y revise su interior antes de ponérselo.",
    )
    if (categoria == 0) return base + "Control del pie una vez al año."
    val riesgo = listOf(
        "No camine descalzo, ni en casa ni en la playa, ni solo con medias.",
        "Hidrate la piel seca (no entre los dedos) y use medias sin costuras, cambiándolas a diario.",
        "Corte las uñas rectas; no se corte callos ni use callicidas: acuda al podólogo.",
        "Consulte de inmediato ante una ampolla, corte, enrojecimiento, hinchazón o dolor.",
        "Mantenga su glucosa en control con su médico.",
    )
    val alto = if (categoria >= 2) listOf(
        "Calzado terapéutico o plantillas a medida si hay deformidad o zonas de presión.",
        "Atención podológica profesional regular (callos, uñas, piel).",
    ) else emptyList()
    val control = CATEGORIAS_IWGDF.getOrNull(categoria)?.control?.lowercase()
    return base + riesgo + alto + listOfNotNull(control?.let { "Próximo control del pie: $it." })
}

// ─── Mapa: el peor hallazgo de una zona ──────────────────────────────────────

/** El hallazgo más grave (por tipo; a igual tipo, por severidad). */
fun peorHallazgo(hallazgos: List<HallazgoMarcado>?): HallazgoMarcado? {
    var peor: HallazgoMarcado? = null
    for (h in hallazgos.orEmpty()) {
        val g = hallazgoPodo(h.tipo)?.gravedad ?: 0
        val gp = peor?.let { hallazgoPodo(it.tipo)?.gravedad ?: 0 } ?: -1
        if (g > gp || (g == gp && peor != null && h.severidad > peor.severidad)) peor = h
    }
    return peor
}

data class PinturaZona(val color: String, val opacidad: Float)

/** Color y opacidad de una zona marcada (null = sin hallazgos). La úlcera es roja y plena siempre. */
fun pinturaZona(hallazgos: List<HallazgoMarcado>?): PinturaZona? {
    val p = peorHallazgo(hallazgos) ?: return null
    val def = hallazgoPodo(p.tipo) ?: return null
    return PinturaZona(colorGrupoPodo(def.grupo), if (def.grupo == "ulcera") 0.95f else opacidadSeveridad(p.severidad))
}

/** "Heloma duro (moderado), Onicomicosis (leve)". */
fun textoHallazgos(hallazgos: List<HallazgoMarcado>): String =
    hallazgos.joinToString(", ") { "${hallazgoPodo(it.tipo)?.nombre ?: it.tipo} (${nombreSeveridad(it.severidad).lowercase()})" }

/** "Pie derecho · Talón: Fisura / grieta (moderado)". */
fun textoZonaMarcada(z: ZonaMarcadaPodo): String =
    "${nombrePie(z.pie)} · ${zonaPodo(z.zona)?.nombre ?: z.zona}: ${textoHallazgos(z.hallazgos)}"

/** Nombre de la zona elegida ("Pie derecho · Talón"). */
fun nombreSeleccionPie(pie: String, zona: String): String = "${nombrePie(pie)} · ${zonaPodo(zona)?.nombre ?: zona}"

// ─── Validación ──────────────────────────────────────────────────────────────

private val RE_FECHA_PODO = Regex("""^\d{4}-\d{2}-\d{2}$""")
private fun enListaPodo(lista: List<OpcionPodo>, v: String?) = v.isNullOrEmpty() || lista.any { it.id == v }

/** Errores para mostrar (vacío = válido). Mismos textos que la web. */
fun validarEvaluacionPodo(b: BorradorEvaluacionPodo): List<String> {
    val e = mutableListOf<String>()
    if (b.pacienteId.isBlank()) e += "Falta el paciente."
    if (!b.fecha.isNullOrEmpty() && !RE_FECHA_PODO.matches(b.fecha)) e += "Fecha inválida."
    if (!enListaPodo(TIPOS_PIE, b.tipoPie)) e += "Tipo de pie no válido."
    if (!enListaPodo(PISADAS, b.pisada)) e += "Pisada no válida."
    if (!enListaPodo(HIGIENES, b.higiene)) e += "Higiene no válida."
    if (b.calzado.orEmpty().trim().length > LimitesEvalPodo.CALZADO) e += "El calzado es muy largo (máx. ${LimitesEvalPodo.CALZADO} caracteres)."
    if (b.observaciones.orEmpty().trim().length > LimitesEvalPodo.OBSERVACIONES) e += "Las observaciones son muy largas (máx. ${LimitesEvalPodo.OBSERVACIONES} caracteres)."
    if (e.isEmpty() && !evaluacionTieneDatos(b)) e += "La evaluación está vacía: marca una zona, haz el tamizaje o completa los datos generales."
    return e
}

/** ¿El borrador trae algo que guardar? */
fun evaluacionTieneDatos(b: BorradorEvaluacionPodo): Boolean =
    normalizarZonasPodo(b.zonas).isNotEmpty() || tamizajeTieneDatos(b.tamizaje) ||
        !b.tipoPie.isNullOrEmpty() || !b.pisada.isNullOrEmpty() || !b.higiene.isNullOrEmpty() ||
        b.calzado.orEmpty().isNotBlank() || b.observaciones.orEmpty().isNotBlank()

/** Más reciente primero (fecha y, a igual fecha, la última creada). */
fun ordenarEvaluacionesPodo(items: List<EvaluacionPodologica>): List<EvaluacionPodologica> =
    items.sortedWith(compareByDescending<EvaluacionPodologica> { it.fecha }.thenByDescending { it.createdAt.orEmpty() })

/**
 * Lo que casi no cambia de una visita a otra se arrastra de la anterior
 * (diabetes, deformidades, antecedentes, EAP diagnosticada); las pruebas, en blanco.
 */
fun tamizajeInicialPodo(anterior: EvaluacionPodologica?, diabeticoSugerido: Boolean): TamizajePodo {
    val prev = anterior?.tamizaje?.let { normalizarTamizaje(it) }
    val t = tamizajeVacio(prev?.diabetes ?: diabeticoSugerido)
    return if (prev == null) t else t.copy(
        deformidades = prev.deformidades, antecedenteUlcera = prev.antecedenteUlcera, amputacion = prev.amputacion,
        erc = prev.erc, eapDiagnosticada = prev.eapDiagnosticada,
    )
}

/** Mensaje cuando el servidor todavía no tiene el endpoint (backend viejo → 404 sin JSON). */
const val EVALUACION_PIE_NO_DISPONIBLE =
    "La evaluación del pie aún no está disponible en el servidor. Intenta más tarde."

// ─── Comparativo contra la evaluación anterior ───────────────────────────────

data class CambioHallazgoPodo(val pie: String, val zona: String, val hallazgo: String, val cambio: String, val antes: Int?, val ahora: Int?)
data class CambioRiesgoPodo(val antes: Int?, val ahora: Int?, val cambio: String?)
data class ComparativoPodo(val riesgo: CambioRiesgoPodo, val cambios: List<CambioHallazgoPodo>)

/** Qué cambió de la anterior a la actual: el riesgo y cada hallazgo por zona. */
fun compararEvaluacionesPodo(
    actualZonas: List<ZonaMarcadaPodo>, actualRiesgo: Int?,
    anteriorZonas: List<ZonaMarcadaPodo>?, anteriorRiesgo: Int?,
    hayAnterior: Boolean = anteriorZonas != null,
): ComparativoPodo {
    val antes = if (hayAnterior) anteriorRiesgo else null
    val ahora = actualRiesgo
    val riesgo = CambioRiesgoPodo(
        antes, ahora,
        if (antes == null || ahora == null) null else if (ahora > antes) "subio" else if (ahora < antes) "bajo" else "igual",
    )
    if (!hayAnterior) return ComparativoPodo(riesgo, emptyList())
    data class Item(val pie: String, val zona: String, val tipo: String, val sev: Int)
    fun mapa(zs: List<ZonaMarcadaPodo>): LinkedHashMap<String, Item> {
        val m = LinkedHashMap<String, Item>()
        for (z in normalizarZonasPodo(zs)) for (h in z.hallazgos) m["${z.clave}:${h.tipo}"] = Item(z.pie, z.zona, h.tipo, h.severidad)
        return m
    }
    val a = mapa(anteriorZonas.orEmpty())
    val b = mapa(actualZonas)
    val cambios = mutableListOf<CambioHallazgoPodo>()
    for ((k, x) in b) {
        val y = a[k]
        if (y == null) cambios += CambioHallazgoPodo(x.pie, x.zona, x.tipo, "nuevo", null, x.sev)
        else if (x.sev != y.sev) cambios += CambioHallazgoPodo(x.pie, x.zona, x.tipo, if (x.sev > y.sev) "empeoro" else "mejoro", y.sev, x.sev)
    }
    for ((k, y) in a) if (k !in b) cambios += CambioHallazgoPodo(y.pie, y.zona, y.tipo, "resuelto", y.sev, null)
    val peso = mapOf("nuevo" to 0, "empeoro" to 1, "mejoro" to 2, "resuelto" to 3)
    return ComparativoPodo(riesgo, cambios.sortedBy { peso[it.cambio] ?: 9 })
}

fun compararEvaluacionesPodo(actual: EvaluacionPodologica, anterior: EvaluacionPodologica?): ComparativoPodo =
    compararEvaluacionesPodo(actual.zonas, actual.riesgoIwgdf, anterior?.zonas, anterior?.riesgoIwgdf, anterior != null)

fun textoCambioPodo(c: CambioHallazgoPodo): String {
    val h = hallazgoPodo(c.hallazgo)?.nombre ?: c.hallazgo
    val donde = "${cortoPie(c.pie)} ${zonaPodo(c.zona)?.nombre ?: c.zona}"
    fun sev(s: Int?) = nombreSeveridad(s ?: 1).lowercase()
    return when (c.cambio) {
        "nuevo" -> "Nuevo: $h — $donde"
        "resuelto" -> "Resuelto: $h — $donde"
        "empeoro" -> "Empeoró: $h — $donde (${sev(c.antes)} → ${sev(c.ahora)})"
        else -> "Mejoró: $h — $donde (${sev(c.antes)} → ${sev(c.ahora)})"
    }
}

// ─── Textos del tamizaje y de los datos generales ────────────────────────────

/** Líneas del tamizaje, listas para leer ("Monofilamento 10 g — Pie derecho: …"). */
fun lineasTamizaje(entrada: TamizajePodo?): List<String> {
    val x = entrada?.let { normalizarTamizaje(it) } ?: return emptyList()
    if (!x.diabetes) return listOf("Paciente no diabético: no aplica la estratificación IWGDF.")
    val out = mutableListOf<String>()
    for (pie in PIES_PODO) {
        val sitios = SITIOS_MONOFILAMENTO.filter { it.pie == pie && x.monofilamento[it.id] != null }
        if (sitios.isNotEmpty()) out += "Monofilamento 10 g — ${nombrePie(pie)}: " +
            sitios.joinToString(", ") { "${it.nombre.lowercase()} ${if (x.monofilamento[it.id] == true) "siente" else "NO siente"}" }
    }
    val vib = PIES_PODO.filter { x.vibracion[it] != null }
    if (vib.isNotEmpty()) out += "Vibración 128 Hz: " + vib.joinToString(", ") { "${nombrePie(it).lowercase()} ${x.vibracion[it]}" }
    val pul = PULSOS_PODO.filter { x.pulsos[it.id] != null }
    if (pul.isNotEmpty()) out += "Pulsos: " + pul.joinToString(", ") {
        "${it.nombre.lowercase()} ${if (it.pie == PIE_DER) "der." else "izq."} ${x.pulsos[it.id]}"
    }
    if (x.eapDiagnosticada) out += "Enfermedad arterial periférica ya diagnosticada"
    if (x.deformidades.isNotEmpty()) out += "Deformidades: " + x.deformidades.joinToString(", ") { d ->
        DEFORMIDADES_PODO.firstOrNull { it.id == d }?.nombre ?: d
    }
    val ant = listOfNotNull("úlcera previa".takeIf { x.antecedenteUlcera }, "amputación".takeIf { x.amputacion },
        "enfermedad renal terminal".takeIf { x.erc })
    out += if (ant.isNotEmpty()) "Antecedentes: ${ant.joinToString(", ")}" else "Sin úlcera previa, amputación ni enfermedad renal terminal"
    return out
}

/** Tipo de pie, pisada, higiene y calzado como pares etiqueta/valor (solo los que hay). */
fun paresDatosGenerales(tipoPie: String?, pisada: String?, higiene: String?, calzado: String?): List<Pair<String, String>> =
    listOf(
        "Tipo de pie" to nombreDeLista(TIPOS_PIE, tipoPie),
        "Pisada" to nombreDeLista(PISADAS, pisada),
        "Higiene" to nombreDeLista(HIGIENES, higiene),
        "Calzado" to calzado?.trim()?.takeIf { it.isNotEmpty() },
    ).mapNotNull { (e, v) -> v?.let { e to it } }

/** "15/10/2026" (determinista). */
fun fechaEvalPodo(iso: String): String {
    val m = Regex("""^(\d{4})-(\d{2})-(\d{2})""").find(iso) ?: return iso
    return "${m.groupValues[3]}/${m.groupValues[2]}/${m.groupValues[1]}"
}

// ─── Autocompletado que aprende (GET /sugerencias) ───────────────────────────

/** Lo aprendido de la clínica (más usado primero). Un backend sin la ruta → todo vacío. */
data class SugerenciasPodo(val calzado: List<AprendidoPlan> = emptyList(), val observaciones: List<AprendidoPlan> = emptyList())

private fun aprendidosPodo(v: JsonElement?): List<AprendidoPlan> = (v as? JsonArray).orEmpty().mapNotNull { x ->
    when (x) {
        is JsonPrimitive -> x.takeIf { it !is JsonNull && it.isString }?.content?.trim()?.takeIf { it.isNotEmpty() }?.let { AprendidoPlan(it, 1) }
        is JsonObject -> {
            val t = x["texto"].textoPodo()?.trim()
            val usos = x["usos"].prim()?.content?.toDoubleOrNull()?.toInt()
            t?.takeIf { it.isNotEmpty() }?.let { AprendidoPlan(it, usos) }
        }
        else -> null
    }
}

fun sugerenciasPodoDeJson(o: JsonObject?): SugerenciasPodo =
    if (o == null) SugerenciasPodo() else SugerenciasPodo(aprendidosPodo(o["calzado"]), aprendidosPodo(o["observaciones"]))

val CHIPS_BASE_PODO: Map<String, List<String>> = mapOf(
    "calzado" to listOf(
        "Calzado adecuado", "Punta estrecha", "Tacón alto", "Calzado pequeño", "Calzado deportivo",
        "Sandalias", "Calzado de seguridad", "Usa plantillas", "Medias de algodón", "Sin medias",
    ),
    "observaciones" to listOf(
        "Piel seca (xerosis)", "Hiperhidrosis", "Bromhidrosis", "Uñas cortadas en curva",
        "Se corta los callos en casa", "Camina descalzo en casa", "Buena adherencia a las indicaciones",
        "Se sugiere estudio de la pisada", "Se deriva al médico para control de glucosa", "Se deriva a cirugía vascular",
    ),
)

/** Chips (base ∪ aprendidos) y autocompletado de un campo. Calzado por coma; observaciones por línea. */
fun frasesCampoPodo(campo: String, sugerencias: SugerenciasPodo?): FrasesCampoPlan = frasesCampoPlan(
    CHIPS_BASE_PODO[campo].orEmpty(),
    if (campo == "calzado") sugerencias?.calzado else sugerencias?.observaciones,
    porLinea = campo != "calzado",
)
