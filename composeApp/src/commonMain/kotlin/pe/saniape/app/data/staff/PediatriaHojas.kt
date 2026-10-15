package pe.saniape.app.data.staff

import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.log10
import kotlin.math.pow

/**
 * 👶 Pediatría: las piezas de dibujo de una curva de crecimiento (marcas de los
 * ejes, escala — las usan el gráfico nativo y el SVG) y las dos hojas
 * imprimibles armadas en la app: "CURVA DE CRECIMIENTO" (gemela de
 * HojaCurvaCrecimiento.tsx + CurvaCrecimiento.tsx) y "CARNÉ DE VACUNAS"
 * (gemela de HojaCarneVacunas.tsx). El visor nativo las imprime o guarda en PDF.
 * Todo lo que viene de la base se escapa; el logo solo si es https y el color
 * solo si es #rrggbb.
 */

/** Una marca del eje X: posición y texto ("6m", "2a", "75"). */
data class MarcaEje(val x: Double, val texto: String)

/** Marcas "lindas" del eje Y (1, 2, 5 × 10^n). Gemelo de `marcasY` de CurvaCrecimiento.tsx. */
fun marcasYCurva(lo: Double, hi: Double, cuantas: Int = 6): List<Double> {
    val rango = hi - lo
    if (!(rango > 0)) return listOf(lo)
    val crudo = rango / cuantas
    val pot = 10.0.pow(floor(log10(crudo)))
    val paso = listOf(1.0, 2.0, 5.0, 10.0).map { it * pot }.firstOrNull { it >= crudo } ?: (pot * 10)
    val out = mutableListOf<Double>()
    var v = ceil(lo / paso) * paso
    while (v <= hi + 1e-9) {
        out.add(redondearJs(v * 1000) / 1000)
        v += paso
    }
    return out
}

/** Marcas del eje X: meses (0–24 cada 3; 0–60 cada 6; 5–19 años cada año) o cm (cada 5). */
fun marcasXCurva(v: VentanaCurva): List<MarcaEje> {
    val out = mutableListOf<MarcaEje>()
    if (v.ejeCm) {
        var x = ceil(v.desde / 5) * 5
        while (x <= v.hasta) { out.add(MarcaEje(x, numJs(x))); x += 5 }
        return out
    }
    val paso = if (v.hasta - v.desde <= 24) 3.0 else if (v.desde >= 60) 12.0 else 6.0
    var x = v.desde
    while (x <= v.hasta + 1e-9) {
        val texto = if (v.desde >= 60 || (x % 12 == 0.0 && x > 0 && paso >= 6)) "${numJs(x / 12)}a"
        else if (x == 0.0) "0" else "${numJs(x)}m"
        out.add(MarcaEje(x, texto))
        x += paso
    }
    return out
}

/** "12,5" (1 decimal, coma), como `fmt` de la web. */
fun numComa(n: Double): String = numJs(redondearJs(n * 10) / 10).replace('.', ',')

/**
 * El rango vertical de una curva: de −3 a +3 DE y los puntos del niño, con un
 * margen del 4 % y redondeado a las marcas. (yMin, yMax, marcas).
 */
data class EscalaCurva(val yMin: Double, val yMax: Double, val marcas: List<Double>)

fun escalaCurva(curva: List<PuntoCurva>, puntos: List<PuntoPaciente>): EscalaCurva {
    val valores = curva.flatMap { listOf(it.zm3, it.z3) } + puntos.map { it.valor }
    val lo0 = valores.minOrNull() ?: 0.0
    val hi0 = valores.maxOrNull() ?: 1.0
    val margen = (hi0 - lo0) * 0.04
    val ys = marcasYCurva(lo0 - margen, hi0 + margen)
    return EscalaCurva(minOf(lo0 - margen, ys.first()), maxOf(hi0 + margen, ys.last()), ys)
}

private fun n1Hoja(v: Double?): String = v?.let { numJs(redondearJs(it * 10) / 10) } ?: "—"
private fun fechaPed(iso: String?): String {
    val p = iso.orEmpty().take(10).split("-")
    return if (p.size == 3 && p[0].length == 4) "${p[2]}/${p[1]}/${p[0]}" else iso.orEmpty()
}
private fun f1(n: Double): String {
    val r = redondearJs(n * 10) / 10
    return if (r == floor(r)) "${r.toLong()}.0" else r.toString()
}

/**
 * La curva en SVG para papel (colores fijos), gemela de CurvaCrecimiento.tsx
 * con `impresion`: bandas −3…+3 DE, las 5 líneas OMS y los puntos del niño.
 * Vacío si la curva tiene menos de 2 puntos.
 */
fun svgCurvaCrecimiento(indicador: IndicadorCrecimiento, curva: List<PuntoCurva>, puntos: List<PuntoPaciente>, v: VentanaCurva): String {
    if (curva.size < 2) return ""
    val w = 640.0; val h = 340.0
    val izq = 40.0; val der = 30.0; val arr = 12.0; val aba = 34.0
    val esc = escalaCurva(curva, puntos)
    fun px(x: Double) = izq + ((x - v.desde) / (v.hasta - v.desde)) * (w - izq - der)
    fun py(y: Double) = arr + (1 - (y - esc.yMin) / (esc.yMax - esc.yMin)) * (h - arr - aba)
    fun linea(z: Int) = curva.mapIndexed { i, p -> "${if (i == 0) "M" else "L"}${f1(px(p.x))},${f1(py(p.en(z)))}" }.joinToString("")
    fun banda(a: Int, b: Int) = curva.mapIndexed { i, p -> "${if (i == 0) "M" else "L"}${f1(px(p.x))},${f1(py(p.en(a)))}" }.joinToString("") +
        curva.asReversed().joinToString("") { p -> "L${f1(px(p.x))},${f1(py(p.en(b)))}" } + "Z"
    val verde = "#16a34a"; val ambar = "#d97706"; val rojo = "#dc2626"; val texto = "#4b5563"; val grilla = "#e5e7eb"; val punto = "#1e2d5e"
    val ultimo = curva.last()
    val desc = "${indicador.nombre} (${indicador.unidad}), patrones OMS con líneas de −3 a +3 desviaciones estándar" +
        (if (puntos.isNotEmpty()) "; ${puntos.size} ${if (puntos.size == 1) "medición" else "mediciones"}, la última ${numComa(puntos.last().valor)} ${indicador.unidad}" else "")
    return buildString {
        append("<svg viewBox=\"0 0 640 340\" class=\"curva\" role=\"img\" aria-label=\"${escSvg(desc)}\">")
        append("<path d=\"${banda(-2, 2)}\" fill=\"$verde\" fill-opacity=\"0.09\"/>")
        append("<path d=\"${banda(2, 3)}\" fill=\"$ambar\" fill-opacity=\"0.1\"/>")
        append("<path d=\"${banda(-3, -2)}\" fill=\"$ambar\" fill-opacity=\"0.1\"/>")
        esc.marcas.forEach { y ->
            append("<line x1=\"$izq\" x2=\"${w - der}\" y1=\"${f1(py(y))}\" y2=\"${f1(py(y))}\" stroke=\"$grilla\" stroke-width=\"0.6\"/>")
            append("<text x=\"${izq - 5}\" y=\"${f1(py(y) + 3.5)}\" text-anchor=\"end\" font-size=\"10\" fill=\"$texto\">${numComa(y)}</text>")
        }
        marcasXCurva(v).forEach { t ->
            append("<line x1=\"${f1(px(t.x))}\" x2=\"${f1(px(t.x))}\" y1=\"$arr\" y2=\"${h - aba}\" stroke=\"$grilla\" stroke-width=\"0.4\"/>")
            append("<text x=\"${f1(px(t.x))}\" y=\"${h - aba + 14}\" text-anchor=\"middle\" font-size=\"10\" fill=\"$texto\">${escSvg(t.texto)}</text>")
        }
        append("<text x=\"${(izq + w - der) / 2}\" y=\"${h - 4}\" text-anchor=\"middle\" font-size=\"10\" fill=\"$texto\">${if (v.ejeCm) "Talla (cm)" else "Edad"}</text>")
        append("<text x=\"10\" y=\"${arr + 10}\" font-size=\"10\" fill=\"$texto\">${escSvg(indicador.unidad)}</text>")
        listOf(Triple(3, "+3", rojo), Triple(2, "+2", ambar), Triple(0, "0", verde), Triple(-2, "−2", ambar), Triple(-3, "−3", rojo)).forEach { (z, txt, color) ->
            val guion = if (z == 3 || z == -3) " stroke-dasharray=\"4 3\"" else ""
            append("<path d=\"${linea(z)}\" fill=\"none\" stroke=\"$color\" stroke-width=\"${if (z == 0) "1.8" else "1.1"}\"$guion/>")
            append("<text x=\"${f1(px(ultimo.x) + 4)}\" y=\"${f1(py(ultimo.en(z)) + 3.5)}\" font-size=\"10\" font-weight=\"700\" fill=\"$color\">$txt</text>")
        }
        if (puntos.size > 1) {
            append("<path d=\"${puntos.mapIndexed { i, p -> "${if (i == 0) "M" else "L"}${f1(px(p.x))},${f1(py(p.valor))}" }.joinToString("")}\" fill=\"none\" stroke=\"$punto\" stroke-width=\"1.6\"/>")
        }
        puntos.forEach { p ->
            append("<circle cx=\"${f1(px(p.x))}\" cy=\"${f1(py(p.valor))}\" r=\"4.2\" fill=\"$punto\" stroke=\"#ffffff\" stroke-width=\"1.5\"/>")
        }
        append("</svg>")
    }
}

/** Cabecera y estilos comunes de las dos hojas (A4 vertical, colores exactos al imprimir). */
private fun hojaPediatria(
    titulo: String, documento: String, hoy: String, color: String, logoUrl: String?, clinicaNombre: String?,
    clinicaDireccion: String?, pacienteLinea: String, cuerpo: String, pie: String,
): String {
    val logo = logoUrl?.trim()?.takeIf { it.startsWith("https://") }
        ?.let { "<img src=\"${escSvg(it)}\" alt=\"\" class=\"logo\">" }.orEmpty()
    return """<!doctype html><html lang="es"><head><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1">
<title>${escSvg(titulo)}</title>
<style>
@page { size: A4 portrait; margin: 1.3cm; }
* { box-sizing: border-box; }
body { font-family: Lato, 'Segoe UI', Arial, sans-serif; color: #111827; background: #fff; margin: 0 auto; max-width: 800px; padding: 16px; font-size: 10.5pt; line-height: 1.35; -webkit-print-color-adjust: exact; print-color-adjust: exact; }
.cab { display: flex; align-items: center; gap: 12px; padding-bottom: 10px; border-bottom: 2px solid $color; }
.logo { width: 64px; height: 64px; object-fit: contain; }
.cli { flex: 1; min-width: 0; } .cli .n { font-weight: bold; font-size: 13pt; text-transform: uppercase; word-break: break-word; }
.s { color: #4b5563; font-size: 8.5pt; margin-top: 2px; }
.doc { text-align: right; } .doc .n { font-weight: bold; font-size: 11pt; color: $color; }
.pac { padding: 8px 0; border-bottom: 1px solid #e5e7eb; font-size: 9.5pt; } .pac span { margin-right: 24px; display: inline-block; }
section { margin-top: 12px; break-inside: avoid; }
h2 { margin: 0 0 4px; font-size: 10pt; text-transform: uppercase; letter-spacing: .03em; color: $color; }
table { width: 100%; border-collapse: collapse; font-size: 9pt; }
th { text-align: left; color: #4b5563; padding: 3px 6px 3px 0; }
td { padding: 2px 6px 2px 0; border-top: 1px solid #e5e7eb; vertical-align: top; word-break: break-word; }
.num { font-variant-numeric: tabular-nums; }
.curvas { display: grid; grid-template-columns: 1fr 1fr; gap: 12px 16px; margin-top: 12px; }
figure { margin: 0; break-inside: avoid; } figcaption { font-weight: bold; font-size: 9pt; color: $color; margin-bottom: 2px; }
.curva { width: 100%; height: auto; display: block; }
.grupo td { border-top: 1px solid #d1d5db; }
.pie-hoja { margin-top: 24px; padding-top: 6px; border-top: 1px solid #e5e7eb; font-size: 7.5pt; color: #6b7280; display: flex; justify-content: space-between; gap: 8px; }
@media (max-width: 560px) { .curvas { grid-template-columns: 1fr; } }
</style></head><body>
<div class="cab">$logo<div class="cli"><div class="n">${escSvg(clinicaNombre)}</div>${clinicaDireccion?.takeIf { it.isNotBlank() }?.let { "<div class=\"s\">${escSvg(it)}</div>" }.orEmpty()}</div>
<div class="doc"><div class="n">$documento</div><div class="s">${escSvg(fechaPed(hoy))}</div></div></div>
<div class="pac">$pacienteLinea</div>
$cuerpo
<div class="pie-hoja"><span>$pie</span><span>Emitido con Sania</span></div>
</body></html>"""
}

/**
 * La hoja "CURVA DE CRECIMIENTO" (A4): el niño, el último estado nutricional
 * (z-score OMS + clasificación NTS 238), las curvas de cada indicador con sus
 * puntos y la tabla de mediciones. [mediciones] cronológicas.
 */
fun htmlCurvaCrecimiento(
    tablas: TablasOms,
    sexo: String,
    fechaNacimiento: String,
    mediciones: List<MedicionCrecimiento>,
    hoy: String,
    pacienteNombre: String?,
    pacienteDocumento: String?,
    clinicaNombre: String?,
    clinicaDireccion: String?,
    logoUrl: String?,
    colorPrincipal: String?,
): String {
    val color = colorHojaClinica(colorPrincipal)
    val evals = mediciones.mapNotNull { m -> evaluarMedicion(tablas, sexo, fechaNacimiento, m)?.let { m to it } }
    val ultima = evals.lastOrNull()?.second
    val indicadores = ultima?.let { u ->
        IndicadorCrecimiento.entries.filter { i -> ventanaCurva(i, u.edadMeses) != null && u.resultados.any { it.indicador == i } }
    }.orEmpty()
    val pac = buildString {
        append("<span><b>Paciente:</b> ${escSvg(pacienteNombre?.takeIf { it.isNotBlank() } ?: "—")}</span>")
        pacienteDocumento?.takeIf { it.isNotBlank() }?.let { append("<span><b>Documento:</b> ${escSvg(it)}</span>") }
        append("<span><b>Sexo:</b> ${if (sexo == "F") "Femenino" else "Masculino"}</span>")
        append("<span><b>Nacimiento:</b> ${escSvg(fechaPed(fechaNacimiento))}</span>")
        append("<span><b>Edad:</b> ${escSvg(textoEdadEntre(fechaNacimiento, hoy))}</span>")
    }
    val cuerpo = buildString {
        if (ultima != null) {
            append("<section><h2>Estado nutricional — ${escSvg(fechaPed(ultima.fecha))} (${escSvg(ultima.edadTexto)})</h2>")
            append("<table><thead><tr><th>Indicador</th><th>Valor</th><th>z / percentil</th><th>Clasificación</th></tr></thead><tbody>")
            ultima.resultados.forEach { r ->
                val cl = r.clasificacion
                append("<tr><td>${escSvg(r.indicador.sigla)} · ${escSvg(r.indicador.nombre)}</td>")
                append("<td class=\"num\">${n1Hoja(r.valor)} ${escSvg(r.indicador.unidad)}</td>")
                append("<td class=\"num\">${escSvg(textoZ(r.z, r.percentil))}</td>")
                append("<td style=\"font-weight:bold;color:${colorNivelHex(cl?.nivel)}\">${escSvg(cl?.texto ?: "Referencial")}</td></tr>")
            }
            append("</tbody></table></section>")
        }
        if (indicadores.isNotEmpty()) {
            append("<div class=\"curvas\">")
            indicadores.forEach { ind ->
                val v = ventanaCurva(ind, ultima!!.edadMeses)!!
                val svg = svgCurvaCrecimiento(ind, curvaReferencia(tablas, ind, sexo, v, 80), puntosPaciente(tablas, ind, sexo, fechaNacimiento, mediciones, v), v)
                append("<figure><figcaption>${escSvg(ind.sigla)} · ${escSvg(ind.nombre)} (${escSvg(ind.unidad)})</figcaption>$svg</figure>")
            }
            append("</div>")
        }
        if (evals.isNotEmpty()) {
            append("<section><h2>Mediciones</h2><table class=\"num\"><thead><tr><th>Fecha</th><th>Edad</th><th>Peso (kg)</th><th>Talla (cm)</th><th>P. cef. (cm)</th><th>IMC</th></tr></thead><tbody>")
            evals.forEach { (m, ev) ->
                append("<tr><td>${escSvg(fechaPed(m.fecha))}</td><td>${escSvg(ev.edadTexto)}</td><td>${n1Hoja(m.peso)}</td>")
                append("<td>${n1Hoja(m.talla)}</td><td>${n1Hoja(m.perimetroCefalico)}</td><td>${ev.imc?.let { numJs(it) } ?: "—"}</td></tr>")
            }
            append("</tbody></table></section>")
        }
    }
    return hojaPediatria(
        titulo = "Curva de crecimiento — ${pacienteNombre.orEmpty()}", documento = "CURVA DE CRECIMIENTO", hoy = hoy, color = color,
        logoUrl = logoUrl, clinicaNombre = clinicaNombre, clinicaDireccion = clinicaDireccion, pacienteLinea = pac, cuerpo = cuerpo,
        pie = "Patrones de crecimiento OMS (2006, 0–5 años; referencia 2007, 5–19 años). Clasificación: NTS N° 238-MINSA/DGIESP-2025. Apoyo a la decisión clínica.",
    )
}

/** Color del estado en la hoja del carné (HEX, como la web). */
fun colorEstadoDosisHex(e: EstadoDosis): String = when (e) {
    EstadoDosis.APLICADA -> "#16a34a"
    EstadoDosis.ATRASADA -> "#dc2626"
    EstadoDosis.PENDIENTE -> "#d97706"
    EstadoDosis.PROXIMA -> "#1d6fa8"
    EstadoDosis.FUERA_DE_EDAD -> "#9ca3af"
    else -> "#6b7280"
}

/** La hoja "CARNÉ DE VACUNAS" (A4): el esquema por edad con fecha, lote y lugar de cada dosis, y las otras vacunas. */
fun htmlCarneVacunas(
    carne: CarneVacunas,
    fechaNacimiento: String?,
    hoy: String,
    pacienteNombre: String?,
    pacienteDocumento: String?,
    clinicaNombre: String?,
    clinicaDireccion: String?,
    logoUrl: String?,
    colorPrincipal: String?,
): String {
    val color = colorHojaClinica(colorPrincipal)
    val pac = buildString {
        append("<span><b>Paciente:</b> ${escSvg(pacienteNombre?.takeIf { it.isNotBlank() } ?: "—")}</span>")
        pacienteDocumento?.takeIf { it.isNotBlank() }?.let { append("<span><b>Documento:</b> ${escSvg(it)}</span>") }
        fechaNacimiento?.let {
            append("<span><b>Nacimiento:</b> ${escSvg(fechaPed(it))}</span>")
            append("<span><b>Edad:</b> ${escSvg(textoEdadEntre(it, hoy))}</span>")
        }
    }
    val cuerpo = buildString {
        append("<p style=\"margin:8px 0 0;font-size:9.5pt\"><b>Estado:</b> ${escSvg(textoResumenCarne(carne, fechaNacimiento != null))} · ")
        append("${carne.resumen.aplicadas} de ${carne.resumen.total} dosis aplicadas.</p>")
        append("<table style=\"margin-top:8px\"><thead><tr style=\"border-bottom:2px solid $color\"><th style=\"width:16%\">Edad</th><th>Vacuna</th><th>Dosis</th><th>Fecha</th><th>Lote</th><th>Lugar / estado</th></tr></thead><tbody>")
        GRUPOS_ESQUEMA.forEach { g ->
            carne.dosis.filter { it.dosis.grupo == g }.forEachIndexed { i, d ->
                val r = d.registro
                val vacuna = if (r != null && r.vacunaCodigo != d.dosis.acepta.first()) "${d.dosis.vacuna} (${cortoVacuna(r.vacunaCodigo)})" else d.dosis.vacuna
                val estado = if (r != null) {
                    if (d.viaHexavalente) "Con la hexavalente" else lugarVacuna(r)
                } else {
                    d.estado.texto + (d.fechaRecomendada?.takeIf {
                        d.estado == EstadoDosis.PROXIMA || d.estado == EstadoDosis.FUTURA || d.estado == EstadoDosis.PENDIENTE || d.estado == EstadoDosis.ATRASADA
                    }?.let { " (${fechaPed(it)})" } ?: "")
                }
                append("<tr${if (i == 0) " class=\"grupo\"" else ""}><td style=\"font-weight:bold\">${if (i == 0) escSvg(g) else ""}</td>")
                append("<td>${escSvg(vacuna)}</td><td>${escSvg(d.dosis.dosis)}</td>")
                append("<td class=\"num\">${r?.let { escSvg(fechaPed(it.fechaAplicacion)) }.orEmpty()}</td>")
                append("<td>${if (r != null && !d.viaHexavalente) escSvg(r.lote.orEmpty()) else ""}</td>")
                append("<td style=\"color:${colorEstadoDosisHex(d.estado)}\">${escSvg(estado)}</td></tr>")
            }
        }
        append("</tbody></table>")
        if (carne.otras.isNotEmpty()) {
            append("<section><h2>Otras vacunas</h2><table><tbody>")
            carne.otras.forEach { o ->
                append("<tr><td>${escSvg(cortoVacuna(o.vacunaCodigo, o.vacunaNombre))}</td><td>${escSvg(o.dosis)}</td>")
                append("<td class=\"num\">${escSvg(fechaPed(o.fechaAplicacion))}</td><td>${escSvg(o.lote.orEmpty())}</td><td>${escSvg(lugarVacuna(o))}</td></tr>")
            }
            append("</tbody></table></section>")
        }
    }
    return hojaPediatria(
        titulo = "Carné de vacunas — ${pacienteNombre.orEmpty()}", documento = "CARNÉ DE VACUNAS", hoy = hoy, color = color,
        logoUrl = logoUrl, clinicaNombre = clinicaNombre, clinicaDireccion = clinicaDireccion, pacienteLinea = pac, cuerpo = cuerpo,
        pie = "Esquema Nacional de Inmunizaciones — NTS N° 246-MINSA/DGIESP-2026 (RM N° 561-2026/MINSA). Documento de apoyo; no reemplaza el carné oficial.",
    )
}
