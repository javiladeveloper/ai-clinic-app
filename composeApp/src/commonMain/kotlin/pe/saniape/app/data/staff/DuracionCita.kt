package pe.saniape.app.data.staff

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

// CUÁNTO DURA LA CITA QUE NACE — gemelo de `lib/duracion-cita.ts` (web). Antes la
// app ponía 15 min a la Consulta y 60 a todo lo demás, sin mirar lo que la clínica
// configuró (Configuración → Cómo trabajamos): una Evaluación de 30 nacía de 60 y
// recepción la corregía a mano en cada cita. Ahora es la misma regla que la web:
// la de la especialidad de la cita (si la configuró) → la de la clínica → el
// respaldo del tipo (Consulta 15 / Evaluación 30 / Sesión 60).

/** Respaldo por tipo (DURACION_POR_TIPO de la web). */
val DURACION_POR_TIPO: Map<String, Int> = mapOf("Consulta" to 15, "Evaluación" to 30, "Sesión" to 60)

/** Clave de `configuracion` de cada tipo (CLAVES_DURACION de la web). */
val CLAVES_DURACION: Map<String, String> = mapOf(
    "Consulta" to "duracion_consulta", "Evaluación" to "duracion_evaluacion", "Sesión" to "duracion_sesion",
)

/** Clave con las duraciones propias de cada especialidad (JSON). */
const val CLAVE_DURACIONES_ESPECIALIDAD = "duraciones_especialidad"

private const val DURACION_MIN = 5
private const val DURACION_MAX = 720
private const val DURACION_DEFECTO = 45

/** Minutos enteros entre 5 y 720; si no, null (se rechaza, no se corrige). `validarDuracion`. */
fun duracionValida(v: String?): Int? {
    val n = v?.trim()?.toDoubleOrNull() ?: return null
    if (n % 1.0 != 0.0) return null
    val m = n.toInt()
    return m.takeIf { it in DURACION_MIN..DURACION_MAX }
}

/** Las duraciones de la clínica a partir de sus filas (clave → valor). `leerDuraciones`. */
fun leerDuraciones(filas: Map<String, String?>): Map<String, Int> =
    DURACION_POR_TIPO.mapValues { (tipo, respaldo) -> duracionValida(filas[CLAVES_DURACION[tipo]]) ?: respaldo }

/** `{ "<especialidadId>": { "Sesión": 40 } }` defensivo: lo inválido se descarta. `leerDuracionesEspecialidad`. */
fun leerDuracionesEspecialidad(valor: String?): Map<String, Map<String, Int>> {
    val raiz = runCatching { Json.parseToJsonElement(valor ?: return emptyMap()) as? JsonObject }.getOrNull() ?: return emptyMap()
    return raiz.mapNotNull { (espId, porTipo) ->
        val o = porTipo as? JsonObject ?: return@mapNotNull null
        val propias = DURACION_POR_TIPO.keys.mapNotNull { tipo ->
            duracionValida((o[tipo] as? JsonPrimitive)?.content)?.let { tipo to it }
        }.toMap()
        if (espId.isBlank() || propias.isEmpty()) null else espId to propias
    }.toMap()
}

/** Duraciones de la clínica, configuradas (lo que se lee una vez por formulario). */
data class DuracionesCita(
    val clinica: Map<String, Int> = DURACION_POR_TIPO,
    val porEspecialidad: Map<String, Map<String, Int>> = emptyMap(),
)

/**
 * La duración con que abre la cita de [tipo] de esa [especialidadId]: la propia de
 * la especialidad → la de la clínica → el respaldo del tipo; tipo desconocido, 45.
 * `duracionSugerida(tipo, duracionesParaEspecialidad(...))` de CitaForm (web).
 */
fun duracionSugerida(tipo: String, d: DuracionesCita, especialidadId: String?): Int =
    especialidadId?.let { d.porEspecialidad[it]?.get(tipo) } ?: d.clinica[tipo] ?: DURACION_POR_TIPO[tipo] ?: DURACION_DEFECTO

/** Las opciones del selector de duración, con la sugerida aunque no sea redonda (50 min). */
fun opcionesDuracion(actual: Int, base: List<Int> = listOf(15, 30, 45, 60, 90, 120)): List<Int> =
    (base + actual).filter { it > 0 }.distinct().sorted()
