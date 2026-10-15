package pe.saniape.app.data.staff

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * HOJA "EJERCICIOS PARA CASA" — lo que se imprime (o se guarda en PDF), PURO.
 * Gemelo de `lib/ejercicios-hoja.ts` (datos) y de `components/fisio/HojaEjercicios.tsx`
 * (disposición) de la web.
 *
 * Dos orígenes, una sola forma:
 *  - STAFF: [hojaDesdePlan] desde el plan que ya lee la pestaña 🏠 de la ficha.
 *  - PACIENTE: [parsearHojaEjercicios] desde GET /api/paciente/mis-ejercicios/hoja,
 *    que la manda ya armada (contrato: docs/app-contrato-portal-paciente.md §7).
 *
 * [htmlHojaEjercicios] la pinta como HTML autocontenido para el visor con
 * impresión de la app (`acciones.abrirHtml`): logo solo https, color solo
 * #rrggbb, TODO texto escapado y ningún ejercicio partido entre páginas.
 */

data class EjercicioHoja(
    val numero: Int,
    val nombre: String,
    /** Postura inicial (estática: imprime nítida). Si no hay, el GIF. */
    val imagenInicio: String?,
    val imagenFin: String?,
    /** "3 series × 10 repeticiones". */
    val dosis: String,
    /** "Lunes a viernes · 2 veces al día". */
    val frecuencia: String,
    val dias: List<Int>,
    val vecesAlDia: Int,
    /** Minutos aproximados de UNA vez (todas sus series). */
    val minutos: Int,
    val detalles: List<String>,
    val indicaciones: String?,
    val pasos: List<String>,
    val materiales: List<String>,
    /** "Detente si el dolor pasa de 4/10". */
    val precaucion: String?,
)

data class HojaEjercicios(
    val titulo: String,
    /** "Sesión #8 · 06/06/26" · "Al terminar el tratamiento". */
    val etiqueta: String,
    /** "Hasta la próxima sesión" · "Para seguir en casa". */
    val vigencia: String,
    /** AAAA-MM-DD: desde cuándo. */
    val fecha: String,
    val motivo: String?,
    val indicaciones: String?,
    val precauciones: String?,
    val profesional: String?,
    val ejercicios: List<EjercicioHoja>,
    val minutosTotales: Int,
)

/** La cabecera de la hoja: nombre, logo y color de la clínica. */
data class ClinicaHoja(val nombre: String?, val logoUrl: String?, val colorPrincipal: String?, val direccion: String? = null)

/** Segundos que toma una repetición sin sostener (subir, bajar, respirar). */
private const val SEG_POR_REPETICION = 4

/**
 * Cuánto toma hacer UNA vez el ejercicio (todas sus series), en segundos:
 * repeticiones × (sostener o 4 s) por serie, los descansos entre series, y el
 * doble si es de ambos lados. Misma regla que `segundosEstimados` de la web.
 */
fun segundosEstimados(series: Int?, repeticiones: Int?, sostenerSeg: Int?, descansoSeg: Int?, lado: String?): Int {
    val s = max(1, series?.takeIf { it != 0 } ?: 1)
    val reps = repeticiones?.takeIf { it != 0 }
    val sostener = sostenerSeg?.takeIf { it != 0 }
    val porSerie = when {
        reps != null && sostener != null -> reps * (sostener + 2)
        sostener != null -> sostener
        else -> (repeticiones ?: 10) * SEG_POR_REPETICION
    }
    val lados = if (lado == "ambos") 2 else 1
    return lados * (s * porSerie + (s - 1) * max(0, descansoSeg ?: 0))
}

/** Minutos redondeados hacia arriba, al menos 1. */
fun minutosDeSegundos(seg: Int): Int = max(1, ceil(seg / 60.0).toInt())

/** "Lunes a viernes" · "Lun · Mié · Vie · 2 veces al día". */
fun textoFrecuencia(dias: List<Int>?, vecesAlDia: Int): String =
    textoDias(dias) + if (vecesAlDia > 1) " · $vecesAlDia veces al día" else ""

private fun detallesDe(lado: String?, carga: String?, descansoSeg: Int?): List<String> = listOfNotNull(
    lado?.takeIf { it.isNotEmpty() }?.let { if (it == "ambos") "Ambos lados" else "Lado $it" },
    carga?.takeIf { it.isNotEmpty() }?.let { "Con $it" },
    descansoSeg?.takeIf { it != 0 }?.let { "Descansa $it s entre series" },
)

private fun precaucionDe(dolorMaximo: Int?): String? = dolorMaximo?.let { "Detente si el dolor pasa de $it/10" }

/** Un ejercicio de la hoja desde un ítem del plan (staff). */
internal fun ejercicioHojaDeItem(numero: Int, i: ItemPlanEjercicios): EjercicioHoja {
    val e = i.ejercicio
    val inicio = e?.posturaInicialUrl?.takeIf { it.isNotBlank() }
    return EjercicioHoja(
        numero = numero,
        nombre = i.ejercicioNombre,
        imagenInicio = inicio ?: e?.gifUrl?.takeIf { it.isNotBlank() },
        // La final solo si hay inicial estática: con un GIF solo, una imagen basta.
        imagenFin = if (inicio != null) e?.posturaFinalUrl?.takeIf { it.isNotBlank() } else null,
        dosis = textoDosis(i),
        frecuencia = textoFrecuencia(i.dias, i.vecesAlDia),
        dias = i.dias.sorted(),
        vecesAlDia = max(1, i.vecesAlDia),
        minutos = minutosDeSegundos(segundosEstimados(i.series, i.repeticiones, i.sostenerSeg, i.descansoSeg, i.lado)),
        detalles = detallesDe(i.lado, i.carga, i.descansoSeg),
        indicaciones = i.indicaciones?.trim()?.takeIf { it.isNotEmpty() },
        pasos = e?.pasos.orEmpty(),
        materiales = e?.materiales.orEmpty().map { nombreMaterial(it) },
        precaucion = precaucionDe(i.dolorMaximo),
    )
}

/** La hoja desde el plan que lee la ficha del staff (`hojaDesdePlan` de la web). */
fun hojaDesdePlan(plan: PlanEjercicios, profesional: String? = null): HojaEjercicios {
    val ejercicios = plan.activos.sortedBy { it.orden }.mapIndexed { k, i -> ejercicioHojaDeItem(k + 1, i) }
    return HojaEjercicios(
        titulo = plan.titulo,
        etiqueta = etiquetaPlan(plan),
        vigencia = vigenciaPlan(plan.momento),
        fecha = plan.fechaInicio,
        motivo = plan.motivo,
        indicaciones = plan.indicacionesGenerales,
        precauciones = plan.precauciones,
        profesional = profesional ?: plan.creadoPorNombre,
        ejercicios = ejercicios,
        minutosTotales = ejercicios.sumOf { it.minutos },
    )
}

/** Una fila de la tabla semanal: por día (lunes a domingo) cuántas casillas (0 = no toca). */
data class FilaSeguimiento(val numero: Int, val nombre: String, val casillas: List<Int>)

/**
 * La tabla para marcar en papel: una fila por ejercicio, una columna por día
 * (lunes a domingo); en cada día que toca, tantas casillas como veces al día (tope 6).
 */
fun tablaSeguimiento(ejs: List<EjercicioHoja>): List<FilaSeguimiento> = ejs.map { e ->
    FilaSeguimiento(e.numero, e.nombre, DIAS_SEMANA.map { d -> if (d.n in e.dias) min(6, max(1, e.vecesAlDia)) else 0 })
}

private val MESES = listOf(
    "enero", "febrero", "marzo", "abril", "mayo", "junio", "julio", "agosto", "septiembre", "octubre", "noviembre", "diciembre",
)

/** "15 de octubre de 2026" sin zonas horarias. Vacío si no es fecha. */
fun fechaHoja(f: String?): String {
    val p = f.orEmpty().take(10).split("-").map { it.toIntOrNull() }
    if (p.size != 3) return ""
    val (y, m, d) = p
    if (y == null || m == null || d == null || y == 0 || m !in 1..12 || d == 0) return ""
    return "$d de ${MESES[m - 1]} de $y"
}

// ─── Respuesta del paciente: GET /api/paciente/mis-ejercicios/hoja ──────────

/** Lo que manda el portal: la hoja ya armada, la clínica y el nombre del paciente. */
data class HojaEjerciciosPortal(val hoja: HojaEjercicios, val clinica: ClinicaHoja?, val pacienteNombre: String?)

private fun JsonObject.txtH(k: String): String? =
    (this[k] as? JsonPrimitive)?.takeIf { it !is JsonNull }?.content?.takeIf { it.isNotBlank() && it != "null" }
private fun JsonObject.entH(k: String): Int? =
    (this[k] as? JsonPrimitive)?.takeIf { it !is JsonNull }?.content?.toDoubleOrNull()?.roundToInt()
private fun JsonObject.textosH(k: String): List<String> =
    (this[k] as? JsonArray).orEmpty().mapNotNull { e -> (e as? JsonPrimitive)?.takeIf { it !is JsonNull }?.content?.takeIf { it.isNotBlank() } }

/**
 * JSON de la hoja del portal → [HojaEjerciciosPortal]. Tolerante: un ejercicio sin
 * nombre se descarta; null si no es JSON, si no trae `hoja` o si no queda ningún ejercicio.
 */
fun parsearHojaEjercicios(texto: String): HojaEjerciciosPortal? {
    val raiz = runCatching { Json.parseToJsonElement(texto).jsonObject }.getOrNull() ?: return null
    val h = raiz["hoja"] as? JsonObject ?: return null
    val ejercicios = (h["ejercicios"] as? JsonArray).orEmpty().mapIndexedNotNull { k, el ->
        val e = el as? JsonObject ?: return@mapIndexedNotNull null
        val dias = (e["dias"] as? JsonArray).orEmpty()
            .mapNotNull { (it as? JsonPrimitive)?.content?.toDoubleOrNull()?.roundToInt()?.takeIf { d -> d in 1..7 } }
            .distinct().sorted()
        EjercicioHoja(
            numero = e.entH("numero") ?: (k + 1),
            nombre = e.txtH("nombre") ?: return@mapIndexedNotNull null,
            imagenInicio = e.txtH("imagenInicio"),
            imagenFin = e.txtH("imagenFin"),
            dosis = e.txtH("dosis").orEmpty(),
            frecuencia = e.txtH("frecuencia").orEmpty(),
            dias = dias,
            vecesAlDia = max(1, e.entH("vecesAlDia") ?: 1),
            minutos = max(1, e.entH("minutos") ?: 1),
            detalles = e.textosH("detalles"),
            indicaciones = e.txtH("indicaciones"),
            pasos = e.textosH("pasos"),
            materiales = e.textosH("materiales"),
            precaucion = e.txtH("precaucion"),
        )
    }
    if (ejercicios.isEmpty()) return null
    val hoja = HojaEjercicios(
        titulo = h.txtH("titulo") ?: "Ejercicios de apoyo",
        etiqueta = h.txtH("etiqueta").orEmpty(),
        vigencia = h.txtH("vigencia").orEmpty(),
        fecha = h.txtH("fecha").orEmpty(),
        motivo = h.txtH("motivo"),
        indicaciones = h.txtH("indicaciones"),
        precauciones = h.txtH("precauciones"),
        profesional = h.txtH("profesional"),
        ejercicios = ejercicios,
        minutosTotales = h.entH("minutosTotales") ?: ejercicios.sumOf { it.minutos },
    )
    val cl = raiz["clinica"] as? JsonObject
    val clinica = cl?.txtH("nombre")?.let { ClinicaHoja(it, cl.txtH("logo_url"), cl.txtH("color_principal")) }
    val pac = (raiz["paciente"] as? JsonObject)?.txtH("nombre")
    return HojaEjerciciosPortal(hoja, clinica, pac)
}

// ─── HTML ───────────────────────────────────────────────────────────────────

/** Escapa TODO texto que viene de la base antes de meterlo en el HTML. */
fun escHoja(s: String?): String = s.orEmpty()
    .replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;").replace("'", "&#39;")

private val HEX = Regex("^#[0-9a-fA-F]{6}$")

/** El color de la clínica solo si es #rrggbb (va dentro del CSS); si no, el navy. */
fun colorDeHoja(c: String?): String = c?.trim()?.takeIf { HEX.matches(it) } ?: "#2c3e7a"

/** Una imagen (logo o ejercicio) solo si es https: nada de `javascript:` ni `data:`. */
fun urlSeguraHoja(u: String?): String? = u?.trim()?.takeIf { it.startsWith("https://") && it.none { ch -> ch.isWhitespace() } }

/** Párrafo con los saltos de línea del texto original. */
private fun conSaltos(s: String): String = escHoja(s).replace("\n", "<br>")

private fun htmlImagen(src: String?, alt: String, etiqueta: String?): String {
    val u = urlSeguraHoja(src) ?: return ""
    return "<figure><img src=\"${escHoja(u)}\" alt=\"${escHoja(alt)}\">" +
        (etiqueta?.let { "<figcaption>${escHoja(it)}</figcaption>" } ?: "") + "</figure>"
}

private fun htmlEjercicio(e: EjercicioHoja, color: String): String = buildString {
    append("<li class=\"ej\">")
    val inicio = htmlImagen(e.imagenInicio, "${e.nombre}: posición inicial", if (urlSeguraHoja(e.imagenFin) != null) "Inicio" else null)
    val fin = if (inicio.isNotEmpty()) htmlImagen(e.imagenFin, "${e.nombre}: posición final", "Final") else ""
    if (inicio.isNotEmpty()) append("<div class=\"imgs\">$inicio$fin</div>")
    append("<div class=\"cuerpo\">")
    append("<div class=\"nom\"><span style=\"color:$color\">${e.numero}.</span> ${escHoja(e.nombre)}</div>")
    append("<div class=\"dosis\"><b>${escHoja(e.dosis)}</b><span>${escHoja(e.frecuencia)}</span><span class=\"g\">≈ ${e.minutos} min</span></div>")
    if (e.detalles.isNotEmpty()) append("<div class=\"det\">${escHoja(e.detalles.joinToString(" · "))}</div>")
    e.indicaciones?.let { append("<div class=\"ind\">“${conSaltos(it)}”</div>") }
    if (e.pasos.isNotEmpty()) append("<ol class=\"pasos\">${e.pasos.joinToString("") { "<li>${escHoja(it)}</li>" }}</ol>")
    if (e.materiales.isNotEmpty()) append("<div class=\"mat\">Necesitas: ${escHoja(e.materiales.joinToString(", "))}</div>")
    e.precaucion?.let { append("<div class=\"prec\">⚠ ${escHoja(it)}</div>") }
    append("</div></li>")
}

private fun htmlTabla(filas: List<FilaSeguimiento>, color: String): String = buildString {
    if (filas.isEmpty()) return@buildString
    append("<section class=\"sem\"><div class=\"semcab\"><h2 style=\"color:$color\">Mi semana: marca cada vez que lo hagas</h2>")
    append("<span>Semana del ____ / ____ al ____ / ____</span></div>")
    append("<table><thead><tr><th class=\"izq\">Ejercicio</th>")
    DIAS_SEMANA.forEach { append("<th>${escHoja(it.corto)}</th>") }
    append("</tr></thead><tbody>")
    filas.forEach { f ->
        append("<tr><td class=\"izq\">${f.numero}. ${escHoja(f.nombre)}</td>")
        f.casillas.forEach { n ->
            if (n == 0) append("<td class=\"no\">—</td>")
            else append("<td>${"<span class=\"box\"></span>".repeat(n)}</td>")
        }
        append("</tr>")
    }
    append("<tr><td class=\"izq\"><b>Dolor del día (0 a 10)</b></td>")
    repeat(DIAS_SEMANA.size) { append("<td></td>") }
    append("</tr></tbody></table>")
    append("<p class=\"nota\">Los días en gris no toca ese ejercicio. Trae esta hoja a tu próxima sesión.</p></section>")
}

/**
 * La hoja "Ejercicios para casa" como HTML autocontenido (A4 vertical), gemela de
 * `HojaEjercicios.tsx`: cabecera con logo y color de la clínica, paciente y para
 * cuándo, motivo/indicaciones/precauciones, cada ejercicio (sin partirse entre
 * páginas) y la tabla semanal Lun–Dom para marcar con lapicero.
 */
fun htmlHojaEjercicios(h: HojaEjercicios, clinica: ClinicaHoja?, pacienteNombre: String?, pacienteDocumento: String? = null): String {
    val color = colorDeHoja(clinica?.colorPrincipal)
    val logo = urlSeguraHoja(clinica?.logoUrl)?.let { "<img src=\"${escHoja(it)}\" alt=\"\" class=\"logo\">" }.orEmpty()
    val datos = listOfNotNull(
        "<span><b>Paciente:</b> ${escHoja(pacienteNombre?.takeIf { it.isNotBlank() } ?: "—")}</span>",
        pacienteDocumento?.takeIf { it.isNotBlank() }?.let { "<span><b>Documento:</b> ${escHoja(it)}</span>" },
        h.etiqueta.takeIf { it.isNotBlank() }?.let { "<span><b>Indicados:</b> ${escHoja(it)}</span>" },
        h.vigencia.takeIf { it.isNotBlank() }?.let { "<span><b>Vigencia:</b> ${escHoja(it)}</span>" },
        if (h.ejercicios.isNotEmpty()) "<span><b>Tiempo aprox.:</b> ${h.minutosTotales} min</span>" else null,
    ).joinToString("")
    return buildString {
        append("<!doctype html><html lang=\"es\"><head><meta charset=\"utf-8\">")
        append("<meta name=\"viewport\" content=\"width=device-width, initial-scale=1\">")
        append("<title>${escHoja(h.titulo.ifBlank { "Ejercicios para casa" })}</title><style>")
        append(
            """
@page { size: A4 portrait; margin: 1.3cm; }
* { box-sizing: border-box; }
body { font-family: Lato, 'Segoe UI', Arial, sans-serif; color: #111827; margin: 0 auto; max-width: 800px; padding: 16px; font-size: 10pt; line-height: 1.35; background: #fff; -webkit-print-color-adjust: exact; print-color-adjust: exact; }
.cab { display: flex; flex-wrap: wrap; align-items: center; gap: 6px 12px; padding-bottom: 10px; border-bottom: 2px solid $color; }
.logo { width: 60px; height: 60px; object-fit: contain; flex-shrink: 0; }
.cli { flex: 1; min-width: 150px; }
.cli .n { font-weight: bold; font-size: 12.5pt; text-transform: uppercase; overflow-wrap: anywhere; }
.cli .s { font-size: 8.5pt; color: #4b5563; }
.doc { text-align: right; }
.doc .t { font-weight: bold; font-size: 11pt; color: $color; }
.doc .f { font-size: 9pt; color: #4b5563; }
.datos { padding: 8px 0; border-bottom: 1px solid #e5e7eb; display: flex; flex-wrap: wrap; gap: 2px 22px; font-size: 9.5pt; }
h1 { margin: 12px 0 0; font-size: 14pt; color: $color; overflow-wrap: anywhere; }
.mot { margin: 4px 0 0; }
.gen { margin: 6px 0 0; font-size: 9.5pt; overflow-wrap: anywhere; }
.precs { margin-top: 10px; border: 2px solid #dc2626; background: #fef2f2; border-radius: 8px; padding: 8px 12px; break-inside: avoid; page-break-inside: avoid; }
.precs .t { font-weight: bold; font-size: 9.5pt; text-transform: uppercase; letter-spacing: .03em; color: #b91c1c; }
.precs .x { font-size: 9.5pt; color: #7f1d1d; font-weight: 600; overflow-wrap: anywhere; }
ol.lista { list-style: none; padding: 0; margin: 4px 0 0; }
.ej { border: 1px solid #d1d5db; border-radius: 8px; padding: 10px; margin-top: 10px; display: flex; gap: 12px; break-inside: avoid; page-break-inside: avoid; }
.imgs { display: flex; gap: 6px; flex-shrink: 0; }
figure { margin: 0; width: 38mm; max-width: 30vw; }
figure img { width: 100%; height: 28mm; object-fit: contain; background: #fff; border: 1px solid #e5e7eb; border-radius: 6px; }
figcaption { text-align: center; font-size: 7pt; color: #6b7280; margin-top: 2px; }
.cuerpo { flex: 1; min-width: 0; }
.nom { font-weight: bold; font-size: 11pt; line-height: 1.2; overflow-wrap: anywhere; }
.dosis { margin-top: 4px; display: flex; flex-wrap: wrap; gap: 2px 16px; font-size: 9.5pt; }
.g { color: #4b5563; }
.det { font-size: 8.5pt; color: #4b5563; margin-top: 2px; }
.ind { font-size: 9pt; margin-top: 4px; font-style: italic; overflow-wrap: anywhere; }
.pasos { padding-left: 16px; margin: 4px 0 0; font-size: 8.5pt; color: #1f2937; }
.mat { font-size: 8pt; color: #6b7280; margin-top: 2px; }
.prec { font-size: 8.5pt; font-weight: bold; color: #b91c1c; margin-top: 2px; }
.sem { margin-top: 20px; break-inside: avoid; page-break-inside: avoid; }
.semcab { display: flex; flex-wrap: wrap; align-items: flex-end; justify-content: space-between; gap: 8px; }
.semcab h2 { margin: 0; font-size: 10pt; text-transform: uppercase; letter-spacing: .03em; }
.semcab span { font-size: 8.5pt; color: #4b5563; }
table { width: 100%; border-collapse: collapse; font-size: 8.5pt; margin-top: 6px; }
th, td { border: 1px solid #9ca3af; padding: 4px 2px; text-align: center; vertical-align: middle; }
th { background: #f3f4f6; font-weight: bold; }
.izq { text-align: left; padding: 4px 6px; overflow-wrap: anywhere; }
td.no { background: #e5e7eb; color: #9ca3af; }
tr { break-inside: avoid; page-break-inside: avoid; }
thead { display: table-header-group; }
.box { display: inline-block; width: 3.2mm; height: 3.2mm; border: 1px solid #374151; border-radius: 1px; margin: 1px; vertical-align: middle; }
.nota { font-size: 8pt; color: #6b7280; margin: 4px 0 0; }
.firma { margin-top: 16px; display: flex; justify-content: flex-end; break-inside: avoid; page-break-inside: avoid; }
.firma div.c { width: 60mm; max-width: 100%; text-align: center; font-size: 8.5pt; }
.firma .l { height: 10mm; }
.firma .r { border-top: 1px solid #1f2937; padding-top: 2px; }
.pie { margin-top: 12px; padding-top: 6px; border-top: 1px solid #e5e7eb; font-size: 7.5pt; color: #9ca3af; display: flex; justify-content: space-between; gap: 8px; }
@media (max-width: 520px) { .ej { flex-direction: column; } .doc { text-align: left; width: 100%; } figure { max-width: 46%; } }
            """.trimIndent(),
        )
        append("</style></head><body>")
        append("<div class=\"cab\">$logo<div class=\"cli\"><div class=\"n\">${escHoja(clinica?.nombre)}</div>")
        clinica?.direccion?.takeIf { it.isNotBlank() }?.let { append("<div class=\"s\">${escHoja(it)}</div>") }
        append("</div><div class=\"doc\"><div class=\"t\">EJERCICIOS PARA CASA</div><div class=\"f\">${escHoja(fechaHoja(h.fecha))}</div></div></div>")
        append("<div class=\"datos\">$datos</div>")
        append("<h1>${escHoja(h.titulo)}</h1>")
        h.motivo?.let { append("<p class=\"mot\"><b>Motivo:</b> ${escHoja(it)}</p>") }
        h.indicaciones?.let { append("<p class=\"gen\">${conSaltos(it)}</p>") }
        h.precauciones?.let { append("<div class=\"precs\"><div class=\"t\">⚠ Precauciones</div><div class=\"x\">${conSaltos(it)}</div></div>") }
        append("<ol class=\"lista\">")
        h.ejercicios.forEach { append(htmlEjercicio(it, color)) }
        append("</ol>")
        append(htmlTabla(tablaSeguimiento(h.ejercicios), color))
        append("<div class=\"firma\"><div class=\"c\"><div class=\"l\"></div><div class=\"r\">Firma y sello del profesional</div>")
        h.profesional?.takeIf { it.isNotBlank() }?.let { append("<div><b>${escHoja(it)}</b></div>") }
        append("</div></div>")
        append("<div class=\"pie\"><span>Ejercicios personales: no los compartas ni los cambies sin consultar a tu fisioterapeuta.</span><span>Emitido con Sania</span></div>")
        append("</body></html>")
    }
}
