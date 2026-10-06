package pe.saniape.app.data.staff

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * HISTORIA CLÍNICA IMPRIMIBLE CON LA EVALUACIÓN PSICOLÓGICA (contrato §13.3,
 * gemelo de components/evaluacion-psico/InformePsicoHistoria.tsx de la web).
 *
 * `GET /api/staff/evaluacion-psico/historia?pacienteId=…[&tests=1]` devuelve,
 * por cada evaluación que el usuario PUEDE ver (Admin o tratante; a los demás,
 * lista vacía), el informe VIGENTE y —solo con `tests=1`— los puntajes de los
 * tests. Nunca fotos de dibujos, hojas ni protocolos. Aquí se parsea y se arma
 * el bloque HTML que va dentro de la historia clínica del visor.
 */

data class TestHistoriaPsico(
    val id: String,
    val nombre: String,
    val fecha: String = "",
    val edadTexto: String = "",
    val validez: String? = null,
    val estado: String = "",
    val enInforme: Boolean = true,
    val puntajes: List<PuntajeEscalaPsico> = emptyList(),
    val global: PuntajeGlobalPsico = PuntajeGlobalPsico(),
)

data class EvalHistoriaPsico(
    val evaluacionId: String,
    val tratamientoId: String? = null,
    /** El informe VIGENTE (emitido, no reemplazado); null si aún no hay. */
    val informe: InformePsico? = null,
    /** null = no se pidieron (sin `tests=1`). */
    val tests: List<TestHistoriaPsico>? = null,
)

private fun JsonObject?.cadena(k: String): String? =
    (this?.get(k) as? JsonPrimitive)?.content?.takeIf { it != "null" }

/** Parseo del 200 de `/evaluacion-psico/historia` (puro). Lo que no tenga forma se descarta. */
internal fun parsearHistoriaPsico(o: JsonObject?): List<EvalHistoriaPsico> =
    (o?.get("evaluaciones") as? JsonArray).orEmpty().mapNotNull { x ->
        val e = x as? JsonObject ?: return@mapNotNull null
        val evId = e.cadena("evaluacionId")?.ifBlank { null } ?: return@mapNotNull null
        val inf = e["informe"] as? JsonObject
        val informe = inf?.let { i ->
            // Mismo lector que el espacio de la evaluación (filiación, secciones, plantilla, reemplazaA).
            leerInforme(buildJsonObject {
                i.cadena("id")?.let { put("id", it) }
                put("evaluacion_id", evId)
                i["version"]?.let { put("version", it) }
                put("estado", "emitido")
                i["contenido"]?.let { put("contenido", it) }
                i.cadena("emitido_at")?.let { put("emitido_at", it) }
            })
        }
        val tests = (e["tests"] as? JsonArray)?.mapNotNull { y ->
            val t = y as? JsonObject ?: return@mapNotNull null
            val id = t.cadena("id")?.ifBlank { null } ?: return@mapNotNull null
            val g = t["global"] as? JsonObject
            TestHistoriaPsico(
                id = id,
                nombre = t.cadena("nombre")?.ifBlank { null } ?: "Test",
                fecha = t.cadena("fecha").orEmpty().take(10),
                edadTexto = t.cadena("edadTexto").orEmpty(),
                validez = t.cadena("validez")?.ifBlank { null },
                estado = t.cadena("estado").orEmpty(),
                enInforme = t.cadena("enInforme") != "false",
                puntajes = (t["puntajes"] as? JsonArray).orEmpty().mapNotNull { p ->
                    val q = p as? JsonObject ?: return@mapNotNull null
                    PuntajeEscalaPsico(
                        q.cadena("escala").orEmpty(), q.cadena("directo").orEmpty(), q.cadena("transformado").orEmpty(),
                        q.cadena("percentil").orEmpty(), q.cadena("categoria").orEmpty(),
                    )
                },
                global = PuntajeGlobalPsico(g.cadena("puntaje").orEmpty(), g.cadena("categoria").orEmpty(), g.cadena("descripcion").orEmpty()),
            )
        }
        EvalHistoriaPsico(evId, e.cadena("tratamientoId")?.ifBlank { null }, informe, tests)
    }

/** Las evaluaciones que aportan algo con lo marcado (como `imprimir` de la web). */
fun evaluacionesAImprimir(evals: List<EvalHistoriaPsico>, conInforme: Boolean, conTests: Boolean): List<EvalHistoriaPsico> =
    evals.filter { (conInforme && it.informe != null) || (conTests && !it.tests.isNullOrEmpty()) }

/** Por qué no hay nada que imprimir con lo marcado (null = sí hay). */
fun motivoSinContenidoPsico(evals: List<EvalHistoriaPsico>, conInforme: Boolean, conTests: Boolean): String? = when {
    !conInforme && !conTests -> "Marca qué incluir: el informe vigente, los puntajes de los tests o ambos."
    evals.isEmpty() -> "No hay una evaluación psicológica que puedas incluir (solo la ven el Admin y el profesional tratante)."
    evaluacionesAImprimir(evals, conInforme, conTests).isNotEmpty() -> null
    conInforme && !conTests -> "Todavía no hay un informe psicológico emitido."
    !conInforme -> "La evaluación todavía no tiene tests con puntajes."
    else -> "Todavía no hay un informe emitido ni tests con puntajes."
}

private fun esc(s: String?): String = (s ?: "")
    .replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;")

private fun textoValidez(v: String?): String = when (v) {
    "valido" -> "válido"
    "invalido" -> "inválido"
    null, "" -> ""
    else -> "dudoso"
}

/**
 * El bloque HTML de la evaluación psicológica (solo lo marcado). Estilos en
 * línea: va dentro de la historia clínica del servidor sin chocar con sus clases.
 * [nombreServicio] da el título de cada evaluación por su tratamiento.
 */
fun htmlSeccionPsico(
    evals: List<EvalHistoriaPsico>, conInforme: Boolean, conTests: Boolean,
    nombreServicio: (String?) -> String = { "Evaluación psicológica" },
): String = buildString {
    for (e in evaluacionesAImprimir(evals, conInforme, conTests)) {
        append("<div style=\"margin-bottom:32px;page-break-inside:avoid\">")
        append("<div style=\"font-size:0.72rem;font-weight:700;color:#8892c8;text-transform:uppercase;letter-spacing:.12em;margin-bottom:8px\">")
        append("Evaluación psicológica — ${esc(nombreServicio(e.tratamientoId))}</div>")
        val inf = e.informe
        if (conInforme && inf != null) {
            val c = inf.contenido
            val plantilla = plantillaDelInforme(c)
            append("<div style=\"border:1px solid #e5e7eb;border-radius:8px;padding:16px;margin-bottom:16px\">")
            append("<div style=\"display:flex;flex-wrap:wrap;justify-content:space-between;gap:8px;align-items:baseline;margin-bottom:8px\">")
            append("<div style=\"font-weight:700;font-size:0.95rem;color:#1e2d5e\">Informe psicológico${if (inf.version > 1) " (versión ${inf.version})" else ""}</div>")
            append("<div style=\"font-size:0.74rem;color:#6b7280\">Emitido${fechaDmyPsico(c.fecha).takeIf { it.isNotEmpty() }?.let { " el $it" } ?: ""}</div></div>")
            if (plantilla.encabezado.isNotBlank()) {
                append("<div style=\"white-space:pre-wrap;font-size:0.74rem;color:#6b7280;margin-bottom:4px\">${esc(plantilla.encabezado)}</div>")
            }
            textoReemplazoInforme(c.reemplazaA).takeIf { it.isNotEmpty() }?.let {
                append("<div style=\"font-size:0.74rem;color:#6b7280;margin-bottom:8px\">${esc(it)}</div>")
            }
            val filiacion = CAMPOS_FILIACION.mapNotNull { f -> c.filiacion[f.valor]?.trim()?.takeIf { it.isNotEmpty() }?.let { f.nombre to it } }
            if (filiacion.isNotEmpty()) {
                append("<div style=\"font-size:0.8rem;margin-bottom:12px;line-height:1.5\">")
                filiacion.forEach { (t, v) -> append("<div><span style=\"color:#6b7280\">${esc(t)}:</span> ${esc(v)}</div>") }
                append("</div>")
            }
            seccionesNumeradasInforme(plantilla).forEach { s ->
                val texto = c.secciones[s.clave]?.trim().orEmpty()
                if (texto.isEmpty()) return@forEach
                append("<div style=\"margin-bottom:8px\"><div style=\"font-size:0.76rem;font-weight:700;text-transform:uppercase;letter-spacing:.04em;color:#374151\">${s.numero}. ${esc(s.titulo)}</div>")
                append("<p style=\"white-space:pre-wrap;font-size:0.84rem;margin:2px 0 0\">${esc(texto)}</p></div>")
            }
            val psicologo = c.filiacion["psicologo"]?.trim().orEmpty()
            val colegiatura = c.filiacion["colegiatura"]?.trim().orEmpty()
            append("<div style=\"margin-top:12px;text-align:right;font-size:0.8rem\"><b>${esc(psicologo)}</b>${if (colegiatura.isNotEmpty()) " · ${esc(colegiatura)}" else ""}")
            val lugarFecha = listOf(c.lugar.trim(), fechaDmyPsico(c.fecha)).filter { it.isNotEmpty() }.joinToString(", ")
            if (lugarFecha.isNotEmpty()) append("<div style=\"font-size:0.74rem;color:#6b7280\">${esc(lugarFecha)}</div>")
            append("</div>")
            if (plantilla.pie.isNotBlank()) {
                append("<div style=\"margin-top:12px;padding-top:6px;border-top:1px solid #e5e7eb;white-space:pre-wrap;font-size:0.72rem;color:#6b7280\">${esc(plantilla.pie)}</div>")
            }
            append("</div>")
        }
        val tests = if (conTests) e.tests.orEmpty() else emptyList()
        if (tests.isNotEmpty()) {
            append("<div style=\"font-size:0.7rem;font-weight:700;color:#9ca3af;text-transform:uppercase;letter-spacing:.08em;margin-bottom:6px\">Puntajes de tests (material protegido)</div>")
            tests.forEach { t ->
                append("<div style=\"margin-bottom:12px;page-break-inside:avoid\">")
                val meta = listOf(fechaDmyPsico(t.fecha), t.edadTexto, textoValidez(t.validez).takeIf { it.isNotEmpty() }?.let { "protocolo $it" }.orEmpty())
                    .filter { it.isNotEmpty() }.joinToString(" · ")
                append("<div style=\"font-size:0.82rem\"><b style=\"color:#1e2d5e\">${esc(t.nombre)}</b>${if (meta.isNotEmpty()) "<span style=\"color:#6b7280\"> · ${esc(meta)}</span>" else ""}</div>")
                val global = listOf(t.global.puntaje, t.global.categoria).map { it.trim() }.filter { it.isNotEmpty() }.joinToString(" — ")
                if (global.isNotEmpty()) append("<div style=\"font-size:0.8rem\"><span style=\"color:#6b7280\">Global:</span> ${esc(global)}</div>")
                val filas = t.puntajes.filter { p -> listOf(p.directo, p.transformado, p.percentil, p.categoria).any { it.isNotBlank() } }
                if (filas.isNotEmpty()) {
                    val td = "border:1px solid #e5e7eb;padding:4px 8px"
                    append("<table style=\"width:100%;border-collapse:collapse;font-size:0.76rem;margin-top:4px\"><thead><tr>")
                    listOf("Escala", "Directo", "Transformado", "Percentil", "Categoría").forEach {
                        append("<th style=\"$td;background:#f9fafb;text-align:left;font-size:0.66rem;text-transform:uppercase;color:#6b7280\">$it</th>")
                    }
                    append("</tr></thead><tbody>")
                    filas.forEach { p ->
                        append("<tr>")
                        listOf(p.escala, p.directo, p.transformado, p.percentil, p.categoria).forEachIndexed { i, v ->
                            append("<td style=\"$td\">${esc(v.trim().ifEmpty { if (i == 0) "" else "—" })}</td>")
                        }
                        append("</tr>")
                    }
                    append("</tbody></table>")
                }
                append("</div>")
            }
        }
        append("</div>")
    }
}

/**
 * Mete [seccion] en la historia clínica del servidor (antes de su pie, o al
 * final del cuerpo). Si la historia no llegó o no es la imprimible (p. ej. la
 * página "Función Premium"), arma un documento propio con la sección sola.
 */
fun historiaConSeccionPsico(htmlHistoria: String?, seccion: String, nombrePaciente: String): String {
    val base = htmlHistoria?.takeIf { it.contains("class=\"barra\"") && it.contains("</body>") }
    if (base != null) {
        val pie = base.lastIndexOf("<div style=\"margin-top:36px")
        val donde = if (pie >= 0) pie else base.lastIndexOf("</body>")
        return base.substring(0, donde) + seccion + base.substring(donde)
    }
    return """<!doctype html><html lang="es"><head><meta charset="utf-8">
<meta name="viewport" content="width=device-width, initial-scale=1">
<title>Evaluación psicológica — ${esc(nombrePaciente)}</title>
<style>
 body{font-family:system-ui,-apple-system,sans-serif;margin:0;background:#fff;color:#111827}
 .barra{display:flex;align-items:center;gap:12px;padding:14px 16px;border-bottom:1px solid #e5e7eb;background:#f9fafb;position:sticky;top:0;z-index:20}
 .btn{padding:9px 16px;background:#2c3e7a;color:#fff;border:none;border-radius:8px;font-size:0.85rem;font-weight:700;cursor:pointer}
 @media print{.barra{display:none}@page{margin:1.4cm}}
</style></head><body>
<div class="barra"><div style="flex:1"></div><button class="btn" onclick="window.print()">🖨 Imprimir / Guardar PDF</button></div>
<div style="max-width:800px;margin:0 auto;padding:28px 24px">
<h1 style="font-size:1.3rem;color:#1e2d5e;margin:0 0 20px">${esc(nombrePaciente)}</h1>
$seccion
</div></body></html>"""
}
