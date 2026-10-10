package pe.saniape.app.data.staff

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

// ─────────────────────────────────────────────────────────────────────────────
// ESCALAS EN LA CONSULTA MÉDICA (psiquiatría): PHQ-9, GAD-7, ASRS, AUDIT…
// aplicadas DENTRO de la atención (`atenciones_clinicas.escalas`). Gemelo de
// lib/escalas-atencion.ts. Las definiciones (ítems, opciones, cortes) llegan por
// GET /api/staff/evaluacion-psico/instrumentos (las mismas de la evaluación
// psicológica).
//
// El puntaje que se ve en la app es ORIENTATIVO: al guardar se manda solo
// `{instrumento, valores, sexo}` y el SERVIDOR recalcula puntaje, categoría y
// alertas (normalizarEscalas); lo que vuelve es lo que vale. Un tamizaje
// positivo no es un diagnóstico.
// ─────────────────────────────────────────────────────────────────────────────

const val MAX_ESCALAS = 20

/** Una escala aplicada en la atención (EscalaAplicada de la web). */
data class EscalaAplicadaApp(
    val instrumento: String,
    val corto: String,
    val nombre: String,
    /** Una respuesta por ítem, en el orden del instrumento (null = sin responder). */
    val valores: List<Int?>,
    val puntaje: Double?,
    val min: Double,
    val max: Double,
    val categoria: String?,
    val alertas: List<String>,
    val completo: Boolean,
    /** 'F' | 'M' | null (cortes del AUDIT-C). */
    val sexo: String?,
)

private fun JsonObject.txtE(k: String): String? =
    (this[k] as? JsonPrimitive)?.takeIf { it !is JsonNull }?.content?.takeIf { it.isNotBlank() }

private fun JsonObject.numE(k: String): Double? = txtE(k)?.replace(',', '.')?.toDoubleOrNull()

/** Lo guardado en la atención (ya recalculado por el servidor). Sin instrumento se descarta. */
fun leerEscalasAtencion(lista: List<JsonElement>?): List<EscalaAplicadaApp> =
    lista.orEmpty().mapNotNull { e ->
        val o = e as? JsonObject ?: return@mapNotNull null
        val ins = o.txtE("instrumento") ?: return@mapNotNull null
        EscalaAplicadaApp(
            instrumento = ins,
            corto = o.txtE("corto") ?: ins.uppercase(),
            nombre = o.txtE("nombre").orEmpty(),
            valores = (o["valores"] as? JsonArray).orEmpty().map { enteroDeJson(it) },
            puntaje = o.numE("puntaje"),
            min = o.numE("min") ?: 0.0,
            max = o.numE("max") ?: 0.0,
            categoria = o.txtE("categoria"),
            alertas = (o["alertas"] as? JsonArray).orEmpty()
                .mapNotNull { (it as? JsonPrimitive)?.takeIf { p -> p !is JsonNull }?.content?.ifBlank { null } },
            completo = o.txtE("completo") == "true",
            sexo = o.txtE("sexo")?.takeIf { it == "F" || it == "M" },
        )
    }.take(MAX_ESCALAS)

/** Lo que viaja a `guardar`: solo instrumento, valores y sexo (el servidor puntúa). */
fun jsonEscalas(lista: List<EscalaAplicadaApp>): JsonArray = JsonArray(lista.take(MAX_ESCALAS).map { e ->
    buildJsonObject {
        put("instrumento", e.instrumento)
        put("valores", JsonArray(e.valores.map { v -> if (v == null) JsonNull else JsonPrimitive(v) }))
        put("sexo", e.sexo?.let { JsonPrimitive(it) } ?: JsonNull)
    }
})

// ── Cálculo orientativo (calcularInstrumento de lib/instrumentos-psico.ts) ───

/** Lo que aporta un ítem: el valor (con inverso) o, en modo 'positivos', 0/1. */
private fun aporte(ins: InstrumentoPsico, it: ItemInstrumentoPsico, valor: Int): Int {
    if (ins.modo == "positivos") return if (valor >= (it.umbralPositivo ?: Int.MAX_VALUE)) 1 else 0
    if (!it.inverso) return valor
    val vals = ins.opcionesDe(it).map { o -> o.valor }
    if (vals.isEmpty()) return valor
    return vals.min() + vals.max() - valor
}

private data class ResEscala(val puntaje: Double?, val categoria: String?, val positivo: Boolean, val nombre: String)

private fun calcular(ins: InstrumentoPsico, e: EscalaInstrumentoPsico, r: List<Int?>, sexo: String?): ResEscala {
    var suma = 0
    var falta = false
    for (n in e.items) {
        val idx = ins.items.indexOfFirst { it.n == n }
        val it = ins.items.getOrNull(idx)
        val v = if (idx >= 0) r.getOrNull(idx) else null
        if (it == null || v == null) { falta = true; continue }
        suma += aporte(ins, it, v)
    }
    val puntaje = if (falta) null else suma.toDouble()
    val bandas = (sexo?.let { e.bandasSexo?.get(it) }) ?: e.bandas
    val b = puntaje?.let { p -> bandas.firstOrNull { p >= it.min && p <= it.max } }
    return ResEscala(puntaje, b?.categoria?.ifBlank { null }, b?.positivo == true, e.nombre)
}

/** Deja un valor por ítem y solo valores que existen en sus opciones (sanearRespuestas). */
fun sanearValores(ins: InstrumentoPsico, crudos: List<Int?>): List<Int?> =
    ins.items.mapIndexed { i, it -> crudos.getOrNull(i)?.takeIf { v -> ins.opcionesDe(it).any { o -> o.valor == v } } }

/**
 * Puntúa en el teléfono, SOLO para mostrar (aplicarEscala de la web). Incompleto
 * → sin total ni categoría; las alertas de riesgo salen igual.
 */
fun aplicarEscalaLocal(ins: InstrumentoPsico, crudos: List<Int?>, sexo: String? = null): EscalaAplicadaApp {
    val sx = sexo?.takeIf { it == "F" || it == "M" }
    val r = sanearValores(ins, crudos)
    val faltantes = ins.items.filterIndexed { i, it -> !it.opcional && it.puntua && r[i] == null }
    val completo = faltantes.isEmpty()
    val total = ins.total?.let { calcular(ins, it, r, sx) }
    val subs = ins.subescalas.map { it to calcular(ins, it, r, sx) }
    val alertas = ins.alertas.filter { a ->
        val idx = ins.items.indexOfFirst { it.n == a.item }
        val v = if (idx >= 0) r[idx] else null
        v != null && v >= a.desde
    }.map { it.mensaje }.filter { it.isNotBlank() }
    val categoria = if (!completo) null else {
        val extras = subs.filter { (def, res) -> def.enGlobal && res.positivo }.map { (_, res) -> "${res.nombre}: ${res.categoria}" }
        (listOfNotNull(total?.categoria) + extras).joinToString(" · ").ifBlank { null }
    }
    return EscalaAplicadaApp(
        instrumento = ins.id,
        corto = ins.corto,
        nombre = ins.nombre,
        valores = r,
        puntaje = total?.puntaje,
        min = ins.total?.min ?: 0.0,
        max = ins.total?.max ?: 0.0,
        categoria = categoria,
        alertas = alertas,
        completo = completo,
        sexo = sx,
    )
}

/** "PHQ-9: 14 / 27 — Depresión moderada · ⚠ …" (textoEscala de la web). */
fun textoEscala(e: EscalaAplicadaApp): String {
    val p = e.puntaje?.let { pt ->
        if (e.min == 0.0) "${numeroPsico(pt)} / ${numeroPsico(e.max)}"
        else "${numeroPsico(pt)} (${numeroPsico(e.min)}–${numeroPsico(e.max)})"
    } ?: "incompleta"
    val cat = if (e.completo && !e.categoria.isNullOrBlank()) " — ${e.categoria}" else ""
    val al = if (e.alertas.isNotEmpty()) " · ⚠ ${e.alertas.joinToString(" · ")}" else ""
    return "${e.corto}: $p$cat$al"
}

/** Una línea por escala (para el resumen y el informe). */
fun textoEscalas(lista: List<EscalaAplicadaApp>): String = lista.joinToString("\n") { textoEscala(it) }

/**
 * Las escalas tal como las dejó el servidor, si son las MISMAS respuestas que el
 * borrador (mismo instrumento y valores, en orden). Así lo que se ve tras guardar
 * es el cálculo que vale, sin marcar cambios. Si no coinciden, null.
 */
fun escalasDelServidorSiCoinciden(borrador: List<EscalaAplicadaApp>, servidor: List<EscalaAplicadaApp>): List<EscalaAplicadaApp>? {
    if (borrador.size != servidor.size) return null
    val iguales = borrador.zip(servidor).all { (a, b) -> a.instrumento == b.instrumento && a.valores == b.valores }
    return if (iguales) servidor else null
}
