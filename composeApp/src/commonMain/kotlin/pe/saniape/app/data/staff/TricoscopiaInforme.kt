package pe.saniape.app.data.staff

import kotlinx.datetime.LocalDate
import kotlin.math.floor

/**
 * El INFORME TRICOSCÓPICO (A4) armado en la app — gemelo de HojaTricoscopia.tsx
 * (la página web pide la sesión del navegador; el visor nativo imprime / guarda
 * en PDF este HTML). Todo lo que viene de la base se escapa; el logo solo si es
 * https y el color solo si es #rrggbb ([colorHojaIndicaciones]).
 */

/** Colores fijos por zona (los del gráfico impreso de la web). Temporales, punteado. */
data class ColorZona(val color: String, val punteado: Boolean = false)

val COLOR_ZONA_TRICO: Map<ZonaCapilar, ColorZona> = mapOf(
    ZonaCapilar.FRONTAL to ColorZona("#3b5bbf"),
    ZonaCapilar.VERTEX to ColorZona("#eb6834"),
    ZonaCapilar.OCCIPITAL to ColorZona("#1baf7a"),
    ZonaCapilar.MEDIA to ColorZona("#8b5cf6"),
    ZonaCapilar.TEMPORAL to ColorZona("#6b7280", punteado = true),
)

data class SerieZonaSvg(val zona: ZonaCapilar, val puntos: List<PuntoSerieHc>)

/** Días desde 1970 de un "YYYY-MM-DD" (null si no es fecha). */
fun diasDeFechaTrico(iso: String): Long? =
    runCatching { LocalDate.parse(iso.take(10)).toEpochDays().toLong() }.getOrNull()

/**
 * Posición de cada fecha en el eje de TIEMPO real (0 = la primera, 1 = la
 * última): la distancia es proporcional a los días entre visitas. Una sola
 * fecha (o fechas iguales / ilegibles) → 0.
 */
fun fraccionesTiempo(fechas: List<String>): List<Double> {
    val dias = fechas.map { diasDeFechaTrico(it) }
    val validos = dias.filterNotNull()
    if (validos.isEmpty()) return fechas.map { 0.0 }
    val t0 = validos.min()
    val t1 = validos.max()
    return dias.map { d -> if (d == null || t1 == t0) 0.0 else (d - t0).toDouble() / (t1 - t0) }
}

private fun f1(v: Double): String {
    val n = floor(v * 10 + 0.5).toLong()
    return "${n / 10}.${kotlin.math.abs(n % 10)}"
}

private fun ddmmyy(iso: String): String {
    val p = iso.take(10).split("-")
    return if (p.size == 3 && p[0].length == 4) "${p[2]}/${p[1]}/${p[0].takeLast(2)}" else iso
}

/**
 * Gemelo de `svgEvolucionZonas`: una línea por zona sobre un eje de fechas
 * común (tiempo real). "" si ninguna zona tiene dos puntos. Solo números,
 * fechas y nombres de zona fijos: no hay texto del usuario que escapar.
 */
fun svgEvolucionZonas(series: List<SerieZonaSvg>, unidad: String = "pelos/cm²"): String {
    val conDos = series.filter { it.puntos.size >= 2 }
    if (conDos.isEmpty()) return ""
    val fechas = conDos.flatMap { s -> s.puntos.map { it.fecha } }.distinct().sorted()
    val valores = conDos.flatMap { s -> s.puntos.map { it.valor } }
    val min = valores.min()
    val max = valores.max()
    val rango = (max - min).takeIf { it != 0.0 } ?: 1.0
    val w = 360.0; val h = 150.0; val l = 34.0; val r = 16.0; val t = 10.0; val b = 34.0
    val frac = fechas.zip(fraccionesTiempo(fechas)).toMap()
    fun x(f: String) = l + (frac[f] ?: 0.0) * (w - l - r)
    fun y(v: Double) = t + (h - t - b) - ((v - min) / rango) * (h - t - b)
    val lineas = conDos.joinToString("") { s ->
        val c = COLOR_ZONA_TRICO.getValue(s.zona)
        val dash = if (c.punteado) " stroke-dasharray=\"4 3\"" else ""
        val pts = s.puntos.joinToString(" ") { "${f1(x(it.fecha))},${f1(y(it.valor))}" }
        "<polyline points=\"$pts\" fill=\"none\" stroke=\"${c.color}\" stroke-width=\"2\"$dash/>" +
            s.puntos.joinToString("") { "<circle cx=\"${f1(x(it.fecha))}\" cy=\"${f1(y(it.valor))}\" r=\"2.6\" fill=\"${c.color}\"/>" }
    }
    fun ancla(i: Int) = when {
        fechas.size > 1 && i == 0 -> "start"
        fechas.size > 1 && i == fechas.size - 1 -> "end"
        else -> "middle"
    }
    // Con el eje de tiempo, dos visitas cercanas se pisarían: una cada ≥ 30 px (la última siempre).
    val rotuladas = mutableListOf<Int>()
    fechas.forEachIndexed { i, f ->
        val previa = rotuladas.lastOrNull()
        if (previa == null || x(f) - x(fechas[previa]) >= 30) rotuladas += i
        else if (i == fechas.size - 1 && rotuladas.size > 1) rotuladas[rotuladas.size - 1] = i
    }
    val etiquetas = rotuladas.joinToString("") { i ->
        val f = fechas[i]
        "<text x=\"${f1(x(f))}\" y=\"${numTrico(h - b + 11)}\" font-size=\"7\" text-anchor=\"${ancla(i)}\" fill=\"#6b7280\">${ddmmyy(f)}</text>"
    }
    var lx = l
    val leyenda = conDos.joinToString("") { s ->
        val c = COLOR_ZONA_TRICO.getValue(s.zona)
        val nombre = s.zona.corto
        val dash = if (c.punteado) " stroke-dasharray=\"4 3\"" else ""
        val el = "<line x1=\"${numTrico(lx)}\" y1=\"${numTrico(h - 6)}\" x2=\"${numTrico(lx + 12)}\" y2=\"${numTrico(h - 6)}\" stroke=\"${c.color}\" stroke-width=\"2\"$dash/>" +
            "<text x=\"${numTrico(lx + 15)}\" y=\"${numTrico(h - 3)}\" font-size=\"8\" fill=\"#374151\">$nombre</text>"
        lx += 22 + nombre.length * 4.6
        el
    }
    return "<svg viewBox=\"0 0 360 150\" width=\"100%\" style=\"max-width:460px\" role=\"img\" aria-label=\"Evolución por zona (${escTrico(unidad)})\" font-family=\"Helvetica, Arial, sans-serif\">" +
        "<text x=\"${numTrico(l - 4)}\" y=\"${f1(y(max) + 3)}\" font-size=\"8\" text-anchor=\"end\" fill=\"#6b7280\">${numTrico(max)}</text>" +
        "<text x=\"${numTrico(l - 4)}\" y=\"${f1(y(min) + 3)}\" font-size=\"8\" text-anchor=\"end\" fill=\"#6b7280\">${numTrico(min)}</text>" +
        "<line x1=\"${numTrico(l)}\" y1=\"${numTrico(h - b)}\" x2=\"${numTrico(w - r)}\" y2=\"${numTrico(h - b)}\" stroke=\"#e5e7eb\"/>" +
        lineas + etiquetas + leyenda + "</svg>"
}

fun escTrico(s: String?): String = s.orEmpty()
    .replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;").replace("'", "&#39;")

/**
 * El informe de la evaluación [ev] con la evolución hasta ESA evaluación (un
 * informe viejo no muestra lo que vino después). [items]: todas las del paciente.
 */
fun htmlInformeTricoscopia(
    ev: EvaluacionTricoscopica,
    items: List<EvaluacionTricoscopica>,
    clinicaNombre: String?,
    clinicaDireccion: String?,
    logoUrl: String?,
    colorPrincipal: String?,
    pacienteNombre: String?,
    pacienteDocumento: String?,
    profesional: String?,
): String {
    val color = colorHojaIndicaciones(colorPrincipal)
    val clasif = textoClasificacion(ev.escala, ev.grado)
    val evol = evolucionZonas(items, ev.id)
    val orden = ordenarEvaluaciones(items)
    val hasta = orden.subList(0, (orden.indexOfFirst { it.id == ev.id } + 1).coerceAtLeast(0))
    val svg = svgEvolucionZonas(ZonaCapilar.entries.map { z -> SerieZonaSvg(z, serieZona(hasta, z, MetricaZona.DENSIDAD)) })
    val cols = MetricaZona.entries.filter { m -> ev.zonas.any { textoMedicion(it, m) != "—" } }
    val vsDonante = comparacionConDonante(ev)
    val indicios = indiciosAndrogeneticos(ev)
    val trasplante = trasplanteDeEvaluacion(ev)
    val logo = logoUrl?.trim()?.takeIf { it.startsWith("https://") }
        ?.let { "<img src=\"${escTrico(it)}\" alt=\"\" class=\"logo\">" }.orEmpty()

    val cuerpo = buildString {
        append("<section class=\"clasif\">${svgSilueta(ev.escala, ev.grado, 64, "sil-informe")}<div><h2>Clasificación</h2>")
        append("<div class=\"grande\">${escTrico(clasif ?: "Sin clasificar")}</div></div></section>")

        if (ev.zonas.isNotEmpty()) {
            append("<section><h2>Mediciones por zona</h2><table><thead><tr><th>Zona</th>")
            cols.forEach { m -> append("<th>${escTrico(m.corto)}${if (m == MetricaZona.DENSIDAD) " (pelos/cm²)" else ""}</th>") }
            append("</tr></thead><tbody>")
            ev.zonas.forEach { z ->
                val e = evol.firstOrNull { it.zona == z.zona }
                append("<tr><td><b>${escTrico(z.zona.nombre)}</b>")
                if (z.signos.isNotEmpty()) append("<div class=\"s\">${escTrico(z.signos.joinToString(" · ") { nombreSigno(it) })}</div>")
                append("</td>")
                cols.forEach { m ->
                    append("<td>${escTrico(textoMedicion(z, m))}")
                    val c = e?.vsAnterior?.get(m)
                    if (c != null && (m == MetricaZona.DENSIDAD || m == MetricaZona.MINIATURIZACION)) {
                        append("<div class=\"s\">${escTrico(textoCambio(c, m))} vs anterior</div>")
                    }
                    append("</td>")
                }
                append("</tr>")
            }
            append("</tbody></table></section>")
        }

        if (vsDonante.isNotEmpty() || indicios.mayores.isNotEmpty()) {
            append("<section class=\"lectura\">")
            if (vsDonante.isNotEmpty()) append("<p><b>Contra la zona donante (occipital):</b> ${escTrico(textoComparacionDonante(vsDonante))}.</p>")
            if (indicios.mayores.isNotEmpty()) {
                val tit = if (cumpleCriteriosAndrogeneticos(indicios)) "Hallazgos compatibles con patrón androgenético" else "Hallazgos de patrón androgenético"
                append("<p><b>$tit</b> (criterios tricoscópicos de Rakowska, orientativo): ${escTrico((indicios.mayores + indicios.menores).joinToString(" · "))}.</p>")
            }
            append("</section>")
        }

        val conPrimera = evol.filter { it.vsPrimera[MetricaZona.DENSIDAD] != null }
        if (conPrimera.isNotEmpty()) {
            append("<section><h2>Evolución de la densidad</h2><ul>")
            conPrimera.forEach { z ->
                val c = z.vsPrimera.getValue(MetricaZona.DENSIDAD)
                append("<li><b>${escTrico(z.zona.nombre)}:</b> ${numTrico(c.desde)} → ${z.actual[MetricaZona.DENSIDAD]?.let(::numTrico) ?: "—"} pelos/cm² (${escTrico(textoCambio(c, MetricaZona.DENSIDAD))} desde el ${escTrico(fechaIndicacion(c.fechaDesde))})</li>")
            }
            append("</ul>")
            if (svg.isNotEmpty()) append("<div class=\"graf\">$svg</div>")
            append("</section>")
        }

        if (trasplante != null) {
            append("<section class=\"caja\"><h2>Estimación para trasplante (orientativa)</h2>")
            if (trasplante.nivel != "incompleto") {
                append("<p>Zona donante: ${trasplante.ufCm2Donante?.let(::numTrico)} UF/cm² × ${numTrico(trasplante.areaDonanteCm2)} cm² × ${numTrico(trasplante.extraiblePct)} % extraíble ")
                append("= <b>${trasplante.ufDisponibles?.let(::milesTrico)} UF disponibles</b>. ")
                append("Zona receptora: ${trasplante.areaReceptoraCm2?.let(::numTrico)} cm² × ${numTrico(trasplante.densidadObjetivo)} UF/cm² ")
                append("= <b>${trasplante.ufNecesarias?.let(::milesTrico)} UF necesarias</b>.</p>")
            }
            append("<p><b>${escTrico(trasplante.mensaje)}</b></p>")
            trasplante.avisos.forEach { append("<p class=\"aviso\">⚠ ${escTrico(it)}</p>") }
            append("<p class=\"s\">Estimación: no reemplaza la evaluación del cirujano (densidad real, calibre, laxitud del cuero cabelludo y progresión de la caída).</p></section>")
        }

        ev.observaciones?.takeIf { it.isNotBlank() }?.let {
            append("<section><h2>Observaciones</h2><p class=\"pre\">${escTrico(it)}</p></section>")
        }
    }

    return """<!doctype html><html lang="es"><head><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1">
<title>Informe tricoscópico — ${escTrico(pacienteNombre)}</title>
<style>
@page { size: A4 portrait; margin: 1.5cm; }
* { box-sizing: border-box; }
body { font-family: Lato, 'Segoe UI', Arial, sans-serif; color: #111827; margin: 0 auto; max-width: 800px; padding: 16px; font-size: 10pt; line-height: 1.35; -webkit-print-color-adjust: exact; print-color-adjust: exact; }
.cab { display: flex; align-items: center; gap: 12px; padding-bottom: 10px; border-bottom: 2px solid $color; }
.logo { width: 64px; height: 64px; object-fit: contain; }
.cli { flex: 1; min-width: 0; } .cli .n { font-weight: bold; font-size: 13pt; text-transform: uppercase; word-break: break-word; }
.s { color: #4b5563; font-size: 8pt; font-weight: normal; }
.doc { text-align: right; } .doc .n { font-weight: bold; font-size: 11pt; color: $color; }
.pac { padding: 8px 0; border-bottom: 1px solid #e5e7eb; font-size: 9.5pt; }
.pac span { margin-right: 24px; }
section { margin-top: 14px; break-inside: avoid; }
h2 { margin: 0 0 4px; font-size: 9.5pt; text-transform: uppercase; letter-spacing: .03em; color: $color; }
.clasif { display: flex; align-items: center; gap: 16px; }
.grande { font-size: 13pt; font-weight: bold; }
table { width: 100%; border-collapse: collapse; font-size: 9pt; font-variant-numeric: tabular-nums; }
th { text-align: left; font-size: 8pt; text-transform: uppercase; color: #4b5563; border-bottom: 1px solid #d1d5db; padding: 4px 8px 4px 0; }
td { border-bottom: 1px solid #e5e7eb; padding: 4px 8px 4px 0; vertical-align: top; }
.lectura p { margin: 4px 0; font-size: 9pt; }
ul { margin: 0; padding-left: 20px; font-size: 9pt; } li { margin: 2px 0; }
.graf { margin-top: 8px; }
.caja { border: 1px solid #d1d5db; border-radius: 8px; padding: 10px; } .caja p { margin: 4px 0; font-size: 9pt; }
.aviso { color: #b91c1c; }
.pre { white-space: pre-wrap; word-break: break-word; font-size: 9.5pt; margin: 0; }
.firma { margin-top: 40px; display: flex; justify-content: flex-end; }
.firma div { width: 60mm; text-align: center; font-size: 8.5pt; }
.linea { border-top: 1px solid #1f2937; margin-top: 14mm; padding-top: 2px; }
.pie { margin-top: 24px; padding-top: 6px; border-top: 1px solid #e5e7eb; font-size: 7.5pt; color: #9ca3af; display: flex; justify-content: space-between; gap: 8px; }
</style></head><body>
<div class="cab">$logo<div class="cli"><div class="n">${escTrico(clinicaNombre)}</div>${clinicaDireccion?.takeIf { it.isNotBlank() }?.let { "<div class=\"s\">${escTrico(it)}</div>" }.orEmpty()}</div>
<div class="doc"><div class="n">INFORME TRICOSCÓPICO</div><div class="s">${escTrico(fechaIndicacion(ev.fecha))}</div></div></div>
<div class="pac"><span><b>Paciente:</b> ${escTrico(pacienteNombre?.takeIf { it.isNotBlank() } ?: "—")}</span>${pacienteDocumento?.takeIf { it.isNotBlank() }?.let { "<span><b>Documento:</b> ${escTrico(it)}</span>" }.orEmpty()}</div>
$cuerpo
<div class="firma"><div><div class="linea">Firma y sello del profesional</div>${profesional?.takeIf { it.isNotBlank() }?.let { "<b>${escTrico(it)}</b>" }.orEmpty()}</div></div>
<div class="pie"><span>Densidad en pelos/cm²; UF = unidades foliculares; ≈ = calculado (densidad ÷ UF/cm²); pp = puntos porcentuales.</span><span>Emitido con Sania</span></div>
</body></html>"""
}
