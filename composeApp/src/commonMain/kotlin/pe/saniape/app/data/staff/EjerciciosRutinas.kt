package pe.saniape.app.data.staff

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put
import kotlin.math.roundToInt

/**
 * RUTINAS DE EJERCICIOS — "📋 Usar rutina" y "💾 Guardar como rutina", PURO.
 * Gemelo de `lib/ejercicios-rutinas.ts` (lo que la pantalla necesita: leer,
 * buscar, resumir y armar los cuerpos). Contrato: docs/app-contrato-ejercicios.md §5.
 *
 * Dos fuentes:
 *  - las de la CLÍNICA: GET /api/staff/ejercicios/plantillas (filas snake_case);
 *  - las BASE de Sania: GET /api/staff/ejercicios/rutinas-base (camelCase). La app
 *    NUNCA las copia: viven en la web y llegan resueltas contra la biblioteca.
 * Usarlas: POST /api/staff/ejercicios/plan { accion: 'rutina' }.
 */

/** Un ejercicio de una rutina, con su dosis. */
data class EjercicioRutina(
    val ejercicioId: String?,
    val slug: String?,
    val nombre: String,
    val series: Int,
    val repeticiones: Int?,
    val sostenerSeg: Int?,
    val descansoSeg: Int?,
    val vecesAlDia: Int,
    val dias: List<Int>,
    val lado: String?,
    val carga: String?,
    val indicaciones: String?,
    val dolorMaximo: Int?,
    /** false = ya no está en la biblioteca (al usarla se omite). Solo lo informan las base. */
    val disponible: Boolean = true,
)

/** Una rutina para el buscador: de la clínica o base. */
data class RutinaEjercicios(
    /** "clinica" | "base". */
    val origen: String,
    /** id de la plantilla (clínica) o de la rutina base. */
    val id: String,
    val nombre: String,
    val descripcion: String?,
    val ejercicios: List<EjercicioRutina>,
    val palabras: List<String> = emptyList(),
) {
    val esDeClinica: Boolean get() = origen == ORIGEN_CLINICA
    val clave: String get() = "$origen:$id"
}

const val ORIGEN_CLINICA = "clinica"
const val ORIGEN_BASE = "base"

/** Gemelos de los CHECK de la tabla `plantillas_ejercicios`. */
object LimitesRutina {
    const val NOMBRE = 80
    const val DESCRIPCION = 300
}

private fun JsonObject.txtR(k: String): String? =
    (this[k] as? JsonPrimitive)?.takeIf { it !is JsonNull }?.content?.takeIf { it.isNotBlank() && it != "null" }
private fun JsonObject.entR(k: String): Int? =
    (this[k] as? JsonPrimitive)?.takeIf { it !is JsonNull }?.content?.toDoubleOrNull()?.roundToInt()
private fun JsonObject.diasR(k: String): List<Int> = (this[k] as? JsonArray).orEmpty()
    .mapNotNull { (it as? JsonPrimitive)?.content?.toDoubleOrNull()?.roundToInt()?.takeIf { d -> d in 1..7 } }
    .distinct().sorted()
private fun JsonObject.textosR(k: String): List<String> =
    (this[k] as? JsonArray).orEmpty().mapNotNull { e -> (e as? JsonPrimitive)?.takeIf { it !is JsonNull }?.content?.takeIf { it.isNotBlank() } }

private fun raizItems(texto: String): List<JsonObject>? {
    val raiz = runCatching { Json.parseToJsonElement(texto).jsonObject }.getOrNull() ?: return null
    return (raiz["items"] as? JsonArray ?: return null).mapNotNull { it as? JsonObject }
}

/** Un ejercicio de rutina de la clínica (forma de `plan_ejercicios_items`, snake_case). */
private fun ejercicioDeFila(o: JsonObject): EjercicioRutina? {
    val nombre = o.txtR("ejercicio_nombre") ?: return null
    return EjercicioRutina(
        ejercicioId = o.txtR("ejercicio_id"), slug = o.txtR("slug"), nombre = nombre,
        series = o.entR("series") ?: 1, repeticiones = o.entR("repeticiones"), sostenerSeg = o.entR("sostener_seg"),
        descansoSeg = o.entR("descanso_seg"), vecesAlDia = o.entR("veces_al_dia") ?: 1, dias = o.diasR("dias"),
        lado = o.txtR("lado"), carga = o.txtR("carga"), indicaciones = o.txtR("indicaciones"), dolorMaximo = o.entR("dolor_maximo"),
    )
}

/**
 * `{ items: PlantillaEjercicios[] }` → rutinas de la clínica. Tolerante: una fila
 * sin id o sin nombre, o un ejercicio malformado, se descartan. null = no es la
 * respuesta esperada (no es JSON o no trae `items`).
 */
fun parsearRutinasClinica(texto: String): List<RutinaEjercicios>? = raizItems(texto)?.mapNotNull { o ->
    RutinaEjercicios(
        origen = ORIGEN_CLINICA,
        id = o.txtR("id") ?: return@mapNotNull null,
        nombre = o.txtR("nombre") ?: return@mapNotNull null,
        descripcion = o.txtR("descripcion"),
        ejercicios = (o["ejercicios"] as? JsonArray).orEmpty().mapNotNull { (it as? JsonObject)?.let(::ejercicioDeFila) },
    )
}

/** `{ items: [...] }` de /rutinas-base (camelCase) → rutinas base. null = respuesta inesperada. */
fun parsearRutinasBase(texto: String): List<RutinaEjercicios>? = raizItems(texto)?.mapNotNull { o ->
    RutinaEjercicios(
        origen = ORIGEN_BASE,
        id = o.txtR("id") ?: return@mapNotNull null,
        nombre = o.txtR("nombre") ?: return@mapNotNull null,
        descripcion = o.txtR("descripcion"),
        palabras = o.textosR("palabras"),
        ejercicios = (o["ejercicios"] as? JsonArray).orEmpty().mapNotNull { el ->
            val e = el as? JsonObject ?: return@mapNotNull null
            EjercicioRutina(
                ejercicioId = e.txtR("ejercicioId"), slug = e.txtR("slug"), nombre = e.txtR("nombre") ?: return@mapNotNull null,
                series = e.entR("series") ?: 1, repeticiones = e.entR("repeticiones"), sostenerSeg = e.entR("sostenerSeg"),
                descansoSeg = e.entR("descansoSeg"), vecesAlDia = e.entR("vecesAlDia") ?: 1, dias = e.diasR("dias"),
                lado = e.txtR("lado"), carga = e.txtR("carga"), indicaciones = e.txtR("indicaciones"), dolorMaximo = e.entR("dolorMaximo"),
                disponible = (e["disponible"] as? JsonPrimitive)?.content != "false",
            )
        },
    )
}

/**
 * Filtra por texto (sin tildes, TODAS las palabras) en el nombre, la descripción,
 * las palabras clave y los ejercicios. Las de la clínica primero (`buscarRutinas`).
 */
fun buscarRutinas(lista: List<RutinaEjercicios>, texto: String): List<RutinaEjercicios> {
    val palabras = normalizarTexto(texto).split(Regex("\\s+")).filter { it.isNotEmpty() }
    val filtradas = if (palabras.isEmpty()) lista else lista.filter { r ->
        val pajar = normalizarTexto((listOf(r.nombre, r.descripcion.orEmpty()) + r.palabras + r.ejercicios.map { it.nombre }).joinToString(" "))
        palabras.all { it in pajar }
    }
    return filtradas.sortedByDescending { it.esDeClinica }
}

/** "6 ejercicios · Báscula pélvica, Puente glúteo, …" para la vista previa. */
fun resumenRutina(r: RutinaEjercicios, max: Int = 3): String {
    val n = r.ejercicios.size
    val nombres = r.ejercicios.take(max).joinToString(", ") { it.nombre }
    return "$n ejercicio${if (n == 1) "" else "s"}" + if (nombres.isNotEmpty()) " · $nombres${if (n > max) "…" else ""}" else ""
}

/** "3 series × 10 repeticiones · Lunes a viernes" de un ejercicio de rutina. */
fun textoItemRutina(e: EjercicioRutina): String =
    "${textoDosis(e.series, e.repeticiones, e.sostenerSeg)} · ${textoDias(e.dias)}" +
        if (e.vecesAlDia > 1) " · ${e.vecesAlDia} veces al día" else ""

/** Los que ya no están en la biblioteca (solo las base lo informan): se omitirán al usarla. */
fun noDisponibles(r: RutinaEjercicios): List<String> = r.ejercicios.filter { !it.disponible }.map { it.nombre }

// ─── Guardar como rutina: el mismo nombre la REEMPLAZA ───────────────────────

/** Mismo nombre sin importar mayúsculas ni espacios (el índice único es lower(btrim(nombre))). */
fun claveNombreRutina(s: String): String = s.trim().replace(Regex("\\s+"), " ").lowercase()

/** La rutina de la clínica que ya tiene ese nombre (guardar la reemplaza). */
fun rutinaConNombre(lista: List<RutinaEjercicios>, nombre: String): RutinaEjercicios? {
    val k = claveNombreRutina(nombre)
    if (k.isEmpty()) return null
    return lista.firstOrNull { it.esDeClinica && claveNombreRutina(it.nombre) == k }
}

/** Nombre sugerido al guardar un plan como rutina: su motivo o su título (si no es el genérico). */
fun nombreSugeridoRutina(motivo: String?, titulo: String?): String {
    val t = titulo.orEmpty().trim()
    val base = motivo.orEmpty().trim().ifEmpty { if (t != "Ejercicios de apoyo") t else "" }
    return base.take(LimitesRutina.NOMBRE)
}

/** Qué hace el botón de guardar en este momento. */
enum class PasoGuardarRutina { GUARDAR, PEDIR_CONFIRMACION, REEMPLAZAR }

/**
 * Guardar con un nombre que ya existe REEMPLAZA esa rutina: el primer toque solo
 * pide confirmarlo; el segundo (ya confirmado) la reemplaza. Un nombre nuevo se
 * guarda de una.
 */
fun pasoGuardarRutina(existe: Boolean, confirmado: Boolean): PasoGuardarRutina = when {
    !existe -> PasoGuardarRutina.GUARDAR
    confirmado -> PasoGuardarRutina.REEMPLAZAR
    else -> PasoGuardarRutina.PEDIR_CONFIRMACION
}

/** El texto del botón según el paso. */
fun textoBotonGuardarRutina(paso: PasoGuardarRutina, guardando: Boolean): String = when {
    guardando -> "Guardando…"
    paso == PasoGuardarRutina.GUARDAR -> "Guardar rutina"
    paso == PasoGuardarRutina.PEDIR_CONFIRMACION -> "Reemplazar rutina"
    else -> "Sí, reemplazar"
}

// ─── Cuerpos y respuestas ────────────────────────────────────────────────────

/** POST /api/staff/ejercicios/plan { accion: 'rutina' } para el destino elegido. Sin clinica_id. */
internal fun cuerpoUsarRutina(pacienteId: String, r: RutinaEjercicios, d: DestinoEjercicios): JsonObject = buildJsonObject {
    put("accion", "rutina")
    put("pacienteId", pacienteId)
    if (r.esDeClinica) put("plantillaId", r.id) else put("rutinaBase", r.id)
    put("momento", d.momento)
    d.sesionId?.takeIf { it.isNotEmpty() }?.let { put("sesionId", it) }
    d.tratamientoId?.takeIf { it.isNotEmpty() }?.let { put("tratamientoId", it) }
}

/** POST /api/staff/ejercicios/plantillas: los ejercicios ACTIVOS del plan, con su dosis. */
internal fun cuerpoGuardarRutina(
    nombre: String, descripcion: String, desdePlanId: String, indicacionesGenerales: String?, precauciones: String?,
): JsonObject = buildJsonObject {
    put("nombre", nombre.trim())
    put("descripcion", descripcion.trim())
    put("desdePlanId", desdePlanId)
    put("indicacionesGenerales", indicacionesGenerales?.takeIf { it.isNotBlank() }?.let { JsonPrimitive(it) } ?: JsonNull)
    put("precauciones", precauciones?.takeIf { it.isNotBlank() }?.let { JsonPrimitive(it) } ?: JsonNull)
}

/** `{ ok, planId, agregados, omitidos[] }` de usar una rutina. */
data class RutinaAplicada(val agregados: Int, val omitidos: List<String>)

internal fun rutinaAplicadaDe(cuerpo: JsonObject?): RutinaAplicada =
    RutinaAplicada(cuerpo?.entR("agregados") ?: 0, cuerpo?.textosR("omitidos").orEmpty())

/** El aviso tras usarla (título + detalle), como el toast de la web. */
fun avisoRutinaAplicada(nombre: String, a: RutinaAplicada): Pair<String, String> {
    val n = a.agregados
    val titulo = if (n > 0) "«$nombre»: $n ejercicio${if (n == 1) "" else "s"} agregado${if (n == 1) "" else "s"}"
    else "Esos ejercicios ya estaban en el plan"
    val detalle = if (a.omitidos.isNotEmpty()) "Ya no están en la biblioteca: ${a.omitidos.joinToString(", ")}."
    else "Con la dosis de la rutina. Ajusta cada uno a este paciente."
    return titulo to detalle
}

/**
 * ¿La función existe en el servidor? Un 404 SIN el JSON de error del endpoint es
 * una web vieja que aún no tiene la ruta; 403 MODULO_APAGADO es una clínica sin
 * fisioterapia. En ambos casos el botón se oculta.
 */
fun funcionNoDisponible(status: Int, codigo: String?, cuerpoEsJsonConError: Boolean): Boolean =
    (status == 404 && !cuerpoEsJsonConError) || (status == 403 && codigo == "MODULO_APAGADO")
