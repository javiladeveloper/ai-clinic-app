package pe.saniape.app.data.staff

import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.log10
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow

/**
 * 🖨 CARNÉ PERINATAL (A4 vertical), gemelo de HojaCarnePerinatal.tsx de la web,
 * armado en la app: el visor nativo lo imprime o lo guarda en PDF. Inspirado en
 * el carné CLAP/OPS (simplificado): clínica, gestante, embarazo actual,
 * antecedentes y riesgos, cuadro de consultas antenatales, curvas (AU, ganancia
 * de peso, PA), signos de alarma y firma.
 *
 * Todo lo que viene de la base se ESCAPA; el logo solo si es https y el color
 * solo si es #rrggbb. Las curvas son SVG propio (gemelo de CurvaEmbarazo.tsx en
 * modo impresión: colores hex, sin variables CSS).
 */

private data class SerieSvg(val puntos: List<PuntoSerieEmb>, val nombre: String, val secundaria: Boolean = false)

private const val C_PRINCIPAL = "#2c3e7a"
private const val C_SECUNDARIA = "#0f766e"
private const val C_BANDA = "#16a34a"
private const val C_REF = "#dc2626"
private const val C_EJE = "#6b7280"
private const val C_GRILLA = "#e5e7eb"

private fun f1(v: Double): String = numEmb(floor(v * 10 + 0.5) / 10)

/** Gemelo de `ticks` de CurvaEmbarazo.tsx. */
private fun ticksEmb(lo: Double, hi: Double, cuantos: Int): List<Double> {
    val paso0 = (hi - lo) / cuantos
    val base = if (paso0 == 0.0) 1.0 else paso0
    val mag = 10.0.pow(floor(log10(base)))
    val paso = listOf(1.0, 2.0, 5.0, 10.0).map { it * mag }.firstOrNull { it >= paso0 } ?: paso0
    if (paso <= 0.0) return emptyList()
    val out = mutableListOf<Double>()
    var v = ceil(lo / paso) * paso
    var guarda = 0
    while (v <= hi + 1e-9 && guarda++ < 200) {
        out += floor(v * 100 + 0.5) / 100
        v += paso
    }
    return out
}

/** Una curva contra la EG (semanas): banda de referencia, líneas (140/90) y series. */
private fun svgCurva(
    series: List<SerieSvg>, banda: List<PuntoBandaEmb>, lineas: List<Pair<Double, String>>, unidad: String,
    xMin: Double, xMax: Double = 41.0, etiquetaBanda: String? = null,
): String {
    val w = 340.0; val h = 190.0; val mt = 10.0; val mr = 10.0; val mb = 26.0; val ml = 34.0
    val todos = series.flatMap { it.puntos }
    val xs = todos.map { it.x } + banda.map { it.x }
    val x0 = min(xMin, xs.minOrNull() ?: xMin)
    val x1 = max(xMax, xs.maxOrNull() ?: xMax)
    val ys = todos.map { it.y } + banda.flatMap { listOf(it.min, it.max) } + lineas.map { it.first }
    var y0 = ys.minOrNull() ?: 0.0
    var y1 = ys.maxOrNull() ?: 1.0
    val margen = max((y1 - y0) * 0.1, 1.0)
    y0 = floor(y0 - margen); y1 = ceil(y1 + margen)
    fun X(v: Double) = ml + ((v - x0) / (if (x1 - x0 == 0.0) 1.0 else x1 - x0)) * (w - ml - mr)
    fun Y(v: Double) = h - mb - ((v - y0) / (if (y1 - y0 == 0.0) 1.0 else y1 - y0)) * (h - mt - mb)
    val sb = StringBuilder()
    sb.append("<figure class=\"curva\"><svg viewBox=\"0 0 340 190\" role=\"img\" xmlns=\"http://www.w3.org/2000/svg\">")
    ticksEmb(y0, y1, 4).forEach { v ->
        sb.append("<line x1=\"$ml\" x2=\"${w - mr}\" y1=\"${f1(Y(v))}\" y2=\"${f1(Y(v))}\" stroke-dasharray=\"3 3\" stroke=\"$C_GRILLA\"/>")
        sb.append("<text x=\"${ml - 4}\" y=\"${f1(Y(v) + 3)}\" font-size=\"9\" text-anchor=\"end\" fill=\"$C_EJE\">${numEmb(v)}</text>")
    }
    ticksEmb(x0, x1, 6).filter { it == floor(it) }.forEach { v ->
        sb.append("<text x=\"${f1(X(v))}\" y=\"${h - mb + 12}\" font-size=\"9\" text-anchor=\"middle\" fill=\"$C_EJE\">${numEmb(v)}</text>")
    }
    sb.append("<text x=\"${w - mr}\" y=\"${h - 2}\" font-size=\"8.5\" text-anchor=\"end\" fill=\"$C_EJE\">semanas</text>")
    sb.append("<text x=\"2\" y=\"${mt + 2}\" font-size=\"8.5\" fill=\"$C_EJE\">${escSvg(unidad)}</text>")
    if (banda.size > 1) {
        val arriba = banda.joinToString("L") { "${f1(X(it.x))},${f1(Y(it.max))}" }
        val abajo = banda.reversed().joinToString("L") { "${f1(X(it.x))},${f1(Y(it.min))}" }
        sb.append("<path d=\"M${arriba}L${abajo}Z\" fill=\"$C_BANDA\" fill-opacity=\"0.14\" stroke=\"$C_BANDA\" stroke-opacity=\"0.5\" stroke-width=\"0.8\"/>")
    }
    lineas.forEach { (y, et) ->
        sb.append("<line x1=\"$ml\" x2=\"${w - mr}\" y1=\"${f1(Y(y))}\" y2=\"${f1(Y(y))}\" stroke-dasharray=\"5 3\" stroke-width=\"1\" stroke=\"$C_REF\"/>")
        sb.append("<text x=\"${w - mr - 2}\" y=\"${f1(Y(y) - 3)}\" font-size=\"8.5\" text-anchor=\"end\" fill=\"$C_REF\">${escSvg(et)}</text>")
    }
    series.forEach { s ->
        val color = if (s.secundaria) C_SECUNDARIA else C_PRINCIPAL
        if (s.puntos.size > 1) {
            sb.append("<polyline fill=\"none\" stroke-width=\"2\" stroke=\"$color\" points=\"${s.puntos.joinToString(" ") { "${f1(X(it.x))},${f1(Y(it.y))}" }}\"/>")
        }
        s.puntos.forEach { p ->
            sb.append("<circle cx=\"${f1(X(p.x))}\" cy=\"${f1(Y(p.y))}\" r=\"3.5\" stroke-width=\"1.5\" fill=\"$color\" stroke=\"#fff\"/>")
        }
    }
    sb.append("</svg>")
    if (etiquetaBanda != null || series.size > 1) {
        sb.append("<figcaption>")
        if (series.size > 1) series.forEach { s ->
            sb.append("<span><i style=\"background:${if (s.secundaria) C_SECUNDARIA else C_PRINCIPAL}\"></i>${escSvg(s.nombre)}</span>")
        }
        etiquetaBanda?.let { sb.append("<span><i class=\"b\" style=\"background:$C_BANDA\"></i>${escSvg(it)}</span>") }
        sb.append("</figcaption>")
    }
    sb.append("</figure>")
    return sb.toString()
}

/** Las tres curvas del carné (gemelo de GraficosEmbarazo.tsx con `impresion`); vacío sin datos. */
fun curvasCarne(emb: Embarazo, controles: List<ControlPrenatal>): String {
    val d = datacion(emb.entrada)
    val au = serieAu(controles, d)
    val pesoPre = emb.pesoPregestacional
    val cat = categoriaImc(imcEmb(pesoPre, emb.tallaCm))
    val multiple = esMultiple(emb.riesgos)
    val gan = if (cat != null) serieGanancia(controles, d, pesoPre) else emptyList()
    val (sist, diast) = seriePa(controles, d)
    if (au.isEmpty() && gan.isEmpty() && sist.isEmpty()) return ""
    val sb = StringBuilder("<div class=\"curvas\">")
    if (au.isNotEmpty()) {
        sb.append("<div class=\"caja\"><div class=\"ct\">Altura uterina vs EG</div>")
        sb.append(svgCurva(listOf(SerieSvg(au, "AU")), bandaAu(), emptyList(), "cm", 16.0, etiquetaBanda = "esperada (EG ± 2 cm)"))
        sb.append("</div>")
    }
    if (gan.isNotEmpty() && cat != null) {
        sb.append("<div class=\"caja\"><div class=\"ct\">Ganancia de peso vs EG</div>")
        sb.append(svgCurva(listOf(SerieSvg(gan, "Ganancia")), bandaGanancia(cat, multiple), emptyList(), "kg", 0.0,
            etiquetaBanda = "IOM${if (multiple) " gemelar" else ""}: ${NOMBRE_CATEGORIA_IMC[cat].orEmpty().lowercase()}"))
        sb.append("</div>")
    }
    if (sist.isNotEmpty()) {
        sb.append("<div class=\"caja\"><div class=\"ct\">Presión arterial</div>")
        sb.append(svgCurva(listOf(SerieSvg(sist, "Sistólica"), SerieSvg(diast, "Diastólica", secundaria = true)), emptyList(),
            listOf(140.0 to "140", 90.0 to "90"), "mmHg", 8.0))
        sb.append("</div>")
    }
    return sb.append("</div>").toString()
}

/** La hoja completa del carné perinatal. [profesionales]: terapeuta_id → nombre ("Atendió"). */
fun htmlCarnePerinatal(
    emb: Embarazo,
    controles: List<ControlPrenatal>,
    hoy: String,
    clinicaNombre: String?,
    clinicaDireccion: String?,
    logoUrl: String?,
    colorPrincipal: String?,
    pacienteNombre: String?,
    pacienteDocumento: String?,
    pacienteEdad: Int?,
    pacienteTelefono: String?,
    profesionales: Map<String, String> = emptyMap(),
): String {
    val color = colorHojaClinica(colorPrincipal)
    val r = resumenEmbarazo(emb, controles, hoy)
    val d = r.datacion
    val filas = ordenarControles(controles)
    val logo = logoUrl?.trim()?.takeIf { it.startsWith("https://") }
        ?.let { "<img src=\"${escSvg(it)}\" alt=\"\" class=\"logo\">" }.orEmpty()
    fun par(k: String, v: String?) = if (v.isNullOrBlank()) "" else "<span><b>${escSvg(k)}:</b> ${escSvg(v)}</span>"
    val gestante = buildString {
        append("<span><b>Gestante:</b> ${escSvg(pacienteNombre?.takeIf { it.isNotBlank() } ?: "—")}</span>")
        append(par("Documento", pacienteDocumento))
        append(par("Edad", pacienteEdad?.let { "$it años" }))
        append(par("Teléfono", pacienteTelefono))
    }
    val actual = buildString {
        append("<div class=\"recuadro\">")
        append(par("FUR", emb.fur?.let { fechaEmb(it) + if (emb.furIncierta) " (incierta)" else "" } ?: "—"))
        append("<span><b>FPP:</b> <b style=\"color:$color\">${escSvg(d?.let { fechaEmb(it.fpp) } ?: "—")}</b></span>")
        r.egHoyDias?.let { append(par("EG hoy", textoEg(it))) }
        if (emb.ecoFecha != null) append(par("Ecografía precoz", "${fechaEmb(emb.ecoFecha)} · ${emb.ecoEgSemanas ?: 0} sem ${emb.ecoEgDias ?: 0} d"))
        d?.let { append("<span class=\"motivo\">${escSvg(it.motivo)}</span>") }
        if (emb.estado == "finalizado") {
            append("<span class=\"motivo\"><b>Terminó:</b> ${escSvg(nombreDesenlace(emb.desenlace).orEmpty())} · ${escSvg(emb.fechaFin?.let { fechaEmb(it) }.orEmpty())}</span>")
        }
        append("</div><div class=\"pares\">")
        append(par("Fórmula obstétrica", formulaObstetrica(emb)))
        append(par("Grupo y Rh", grupoRh(emb.grupoSanguineo, emb.rh)))
        append(par("Peso pregestacional", emb.pesoPregestacional?.let { "${numEmb(it)} kg" }))
        append(par("Talla", emb.tallaCm?.let { "${numEmb(it)} cm" }))
        if (r.imcPregestacional != null && r.categoriaImc != null) {
            append("<span class=\"ancho\"><b>IMC pregestacional:</b> ${escSvg(numEmb(r.imcPregestacional))} (${escSvg(NOMBRE_CATEGORIA_IMC[r.categoriaImc])}) · ganancia recomendada ${escSvg(textoGananciaRecomendada(r.categoriaImc, esMultiple(emb.riesgos)))} (IOM 2009)</span>")
        }
        if (emb.riesgos.isNotEmpty()) append("<span class=\"ancho\"><b>Factores de riesgo:</b> ${escSvg(emb.riesgos.joinToString(" · ") { nombreRiesgo(it) })}</span>")
        val prox = if (r.proximoControl != null && emb.activo) " · próximo control: ${fechaEmb(r.proximoControl)}" else ""
        append("<span class=\"ancho\"><b>Controles:</b> ${r.minsa.realizados} de ${r.minsa.minimo} (NTS 105-MINSA)${escSvg(prox)}</span>")
        append("</div>")
    }
    val cuadro = if (filas.isEmpty()) "<p class=\"s\">Sin controles registrados.</p>" else buildString {
        append("<div class=\"tabla\"><table><thead><tr>")
        listOf("N°", "Fecha", "EG", "Peso", "PA", "AU", "LCF", "Mov. fetales", "Present.", "Edema", "Prot.", "Suplementos", "Próx. cita", "Atendió")
            .forEach { append("<th>${escSvg(it)}</th>") }
        append("</tr></thead><tbody>")
        filas.forEachIndexed { i, c ->
            val celdas = listOf(
                "${i + 1}", fechaEmb(c.fecha), textoEg(egDelControl(c, d)) ?: "—", c.peso?.let { numEmb(it) } ?: "—",
                if (c.paSistolica != null) "${c.paSistolica}/${c.paDiastolica}" else "—", c.alturaUterina?.let { numEmb(it) } ?: "—",
                c.lcf?.toString() ?: "—", nombreMovimientos(c.movimientosFetales) ?: "—", nombrePresentacion(c.presentacion) ?: "—",
                c.edema?.let { if (it == "no") "No" else it } ?: "—", c.proteinuria ?: "—",
                c.suplementos.joinToString(", ") { nombreSuplemento(it) }.ifEmpty { "—" },
                c.proximoControl?.let { fechaEmb(it) } ?: "—", c.terapeutaId?.let { profesionales[it] }.orEmpty(),
            )
            append("<tr>")
            celdas.forEach { append("<td>${escSvg(it)}</td>") }
            append("</tr>")
        }
        append("</tbody></table></div>")
    }
    val signos = SIGNOS_ALARMA_EMB.joinToString("") { "<li>${escSvg(it.educativo)}</li>" }
    return """<!doctype html><html lang="es"><head><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1">
<title>Carné perinatal — ${escSvg(pacienteNombre)}</title>
<style>
@page { size: A4 portrait; margin: 1.2cm; }
* { box-sizing: border-box; }
body { font-family: Lato, 'Segoe UI', Arial, sans-serif; color: #111827; background: #fff; margin: 0 auto; max-width: 820px; padding: 16px; font-size: 9.5pt; line-height: 1.35; -webkit-print-color-adjust: exact; print-color-adjust: exact; }
.cab { display: flex; align-items: center; gap: 12px; padding-bottom: 10px; border-bottom: 2px solid $color; }
.logo { width: 64px; height: 64px; object-fit: contain; }
.cli { flex: 1; min-width: 0; } .cli .n { font-weight: bold; font-size: 13pt; text-transform: uppercase; word-break: break-word; }
.s { color: #4b5563; font-size: 8.5pt; margin: 2px 0 0; }
.doc { text-align: right; } .doc .n { font-weight: bold; font-size: 12pt; color: $color; }
.gest { padding: 8px 0; border-bottom: 1px solid #e5e7eb; display: flex; flex-wrap: wrap; gap: 2px 24px; }
.recuadro { margin-top: 10px; border: 2px solid $color; border-radius: 8px; padding: 8px 12px; display: flex; flex-wrap: wrap; gap: 4px 24px; }
.motivo { width: 100%; font-size: 8.5pt; color: #4b5563; }
.pares { margin-top: 8px; display: flex; flex-wrap: wrap; gap: 4px 24px; } .ancho { width: 100%; }
section { margin-top: 14px; break-inside: avoid; }
h2 { margin: 0 0 4px; font-size: 9.5pt; text-transform: uppercase; letter-spacing: .03em; color: $color; }
.tabla { overflow-x: auto; } table { width: 100%; border-collapse: collapse; min-width: 640px; }
th { border: 1px solid #9ca3af; padding: 2px 4px; font-size: 7.5pt; text-align: left; background: #f3f4f6; }
td { border: 1px solid #d1d5db; padding: 2px 4px; font-size: 8pt; vertical-align: top; white-space: nowrap; }
.nota { font-size: 7.5pt; color: #6b7280; margin-top: 4px; }
.curvas { display: grid; grid-template-columns: repeat(auto-fit, minmax(220px, 1fr)); gap: 8px; }
.caja { border: 1px solid #d1d5db; border-radius: 8px; padding: 6px; break-inside: avoid; }
.ct { font-size: 8.5pt; font-weight: bold; text-transform: uppercase; color: #374151; margin-bottom: 2px; }
.curva { margin: 0; } .curva svg { width: 100%; height: auto; display: block; }
figcaption { display: flex; flex-wrap: wrap; gap: 2px 10px; font-size: 7.5pt; color: $C_EJE; }
figcaption i { display: inline-block; width: 9px; height: 9px; border-radius: 50%; margin-right: 4px; vertical-align: middle; } figcaption i.b { border-radius: 2px; opacity: .3; width: 12px; }
.alarma { border: 2px solid #dc2626; border-radius: 8px; padding: 8px 12px; } .alarma .t { font-weight: bold; color: #b91c1c; }
.alarma ul { margin: 2px 0 0; padding-left: 20px; columns: 2; } .alarma li { font-size: 9pt; }
.firma { margin-top: 40px; display: flex; justify-content: flex-end; } .firma div { width: 60mm; text-align: center; font-size: 8.5pt; }
.linea { border-top: 1px solid #1f2937; margin-top: 14mm; padding-top: 2px; }
.pie-hoja { margin-top: 24px; padding-top: 6px; border-top: 1px solid #e5e7eb; font-size: 7pt; color: #9ca3af; display: flex; justify-content: space-between; gap: 8px; }
@media print { .tabla { overflow: visible; } table { min-width: 0; } }
</style></head><body>
<div class="cab">$logo<div class="cli"><div class="n">${escSvg(clinicaNombre)}</div>${clinicaDireccion?.takeIf { it.isNotBlank() }?.let { "<div class=\"s\">${escSvg(it)}</div>" }.orEmpty()}</div>
<div class="doc"><div class="n">CARNÉ PERINATAL</div><div class="s">Control prenatal · emitido ${escSvg(fechaEmb(hoy))}</div></div></div>
<div class="gest">$gestante</div>
<section>$actual</section>
<section><h2>Consultas antenatales</h2>$cuadro
<div class="nota">EG: edad gestacional · PA: presión arterial (mmHg) · AU: altura uterina (cm) · LCF: latidos cardiacos fetales (lpm) · Prot.: proteinuria en tira</div></section>
<section>${curvasCarne(emb, controles)}</section>
<section class="alarma"><div class="t">Acuda de inmediato al establecimiento de salud si presenta:</div><ul>$signos</ul></section>
<div class="firma"><div><div class="linea">Firma y sello del profesional</div></div></div>
<div class="pie-hoja"><span>FPP por Naegele / ecografía (ACOG CO 700) · AU esperada EG ± 2 cm · ganancia IOM 2009 · controles NTS 105-MINSA. Apoyo: no reemplaza el criterio clínico.</span><span>Emitido con Sania</span></div>
</body></html>"""
}
