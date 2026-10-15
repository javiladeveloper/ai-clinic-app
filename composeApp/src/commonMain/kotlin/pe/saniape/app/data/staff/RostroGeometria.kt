package pe.saniape.app.data.staff

/**
 * 💉 La GEOMETRÍA del rostro del mapa de aplicación: la misma del SVG de la web
 * (`components/estetica/MapaFacial.tsx`), viewBox 240 × 320. Lado del
 * PACIENTE: en la vista frontal su derecha queda a la IZQUIERDA de quien mira;
 * el perfil derecho mira a la derecha y el izquierdo es el mismo dibujo en espejo.
 *
 * La usan el Canvas de la app (dibujo + toque) y el registro imprimible (SVG).
 */
object RostroGeometria {
    const val ANCHO = 240f
    const val ALTO = 320f

    /**
     * Radio mínimo del área de toque, en unidades del viewBox (el de la web: a
     * 320 px de ancho son ≈ 35 px de diámetro). La app además lo sube para que
     * nunca baje de 32 dp aunque el rostro se dibuje más chico.
     */
    const val RADIO_TOQUE = 13f

    private fun e(cx: Float, cy: Float, rx: Float, ry: Float, rot: Float = 0f) = FormaMapa.Elipse(cx, cy, rx, ry, rot)

    /** Vista frontal. Pares: la derecha del paciente con x < 120. */
    val FRONTAL: Map<String, FormaMapa.Elipse> = mapOf(
        "frente" to e(120f, 56f, 52f, 20f),
        "glabela" to e(120f, 100f, 11f, 11f),
        "cola_ceja_der" to e(66f, 97f, 9f, 6f, 15f),
        "cola_ceja_izq" to e(174f, 97f, 9f, 6f, -15f),
        "patas_gallo_der" to e(55f, 122f, 7f, 12f),
        "patas_gallo_izq" to e(185f, 122f, 7f, 12f),
        "sien_der" to e(45f, 92f, 8f, 15f),
        "sien_izq" to e(195f, 92f, 8f, 15f),
        "ojera_der" to e(88f, 134f, 14f, 5f),
        "ojera_izq" to e(152f, 134f, 14f, 5f),
        "nariz_lineas" to e(120f, 124f, 11f, 5f),
        "nariz" to e(120f, 150f, 8f, 14f),
        "malar_der" to e(72f, 152f, 15f, 11f),
        "malar_izq" to e(168f, 152f, 15f, 11f),
        "surco_nasogeniano_der" to e(99f, 181f, 5f, 14f, 18f),
        "surco_nasogeniano_izq" to e(141f, 181f, 5f, 14f, -18f),
        "codigo_barras" to e(120f, 186f, 13f, 4f),
        "labio_superior" to e(120f, 197f, 17f, 5f),
        "labio_inferior" to e(120f, 209f, 15f, 5f),
        "comisura_der" to e(96f, 211f, 6f, 7f),
        "comisura_izq" to e(144f, 211f, 6f, 7f),
        "menton" to e(120f, 243f, 17f, 11f),
        "mandibula_der" to e(74f, 229f, 15f, 7f, 38f),
        "mandibula_izq" to e(166f, 229f, 15f, 7f, -38f),
        "masetero_der" to e(52f, 188f, 9f, 17f),
        "masetero_izq" to e(188f, 188f, 9f, 17f),
        "cuello" to e(120f, 294f, 30f, 13f),
    )

    /** Perfil DERECHO (mira a la derecha). El izquierdo es su espejo. */
    val PERFIL: Map<String, FormaMapa.Elipse> = mapOf(
        "dorso_nasal" to e(188f, 141f, 5f, 15f, -28f),
        "punta_nasal" to e(198f, 165f, 6f, 6f),
        "menton_proyeccion" to e(183f, 244f, 8f, 12f),
        "angulo_mandibular_der" to e(112f, 236f, 12f, 9f),
        "angulo_mandibular_izq" to e(112f, 236f, 12f, 9f),
    )

    /** La forma de una zona en su vista (sin espejo). */
    fun formaDeZona(id: String): FormaMapa.Elipse? {
        val z = zonaFacial(id) ?: return null
        return if (z.vista == "frontal") FRONTAL[id] else PERFIL[id]
    }

    /** Las zonas de la vista en orden de dibujo (sin espejo: el perfil izquierdo se refleja al dibujar). */
    fun zonasDibujo(vista: String): List<ZonaDibujo> =
        zonasDeVista(vista).mapNotNull { z -> formaDeZona(z.id)?.let { ZonaDibujo(z.id, listOf(it)) } }

    /**
     * La zona tocada en (x, y) del viewBox VISIBLE (en el perfil izquierdo, ya
     * reflejado). Primero gana la zona dibujada; fuera de todas, el área de
     * toque (radio ≥ [radioMin]) más cercana.
     */
    fun zonaEn(vista: String, x: Float, y: Float, radioMin: Float = RADIO_TOQUE): String? {
        val px = if (vista == "lateral_izq") ANCHO - x else x
        return zonaTocadaConRadio(zonasDibujo(vista), px, y, maxOf(radioMin, RADIO_TOQUE))
    }

    // ─── El dibujo del rostro (trazos de la web) ─────────────────────────────

    /** Frontal: cuello, contorno, cejas, ojos, nariz y boca (se dibujan con trazo; el contorno con relleno). */
    val FRONTAL_CONTORNO = "M120 18 C176 18 206 60 206 122 C206 166 196 200 179 229 C163 254 143 271 120 273 C97 271 77 254 61 229 C44 200 34 166 34 122 C34 60 64 18 120 18 Z"
    val FRONTAL_TRAZOS: List<String> = listOf(
        "M86 258 L82 318 M154 258 L158 318",
        "M74 103 C86 95 100 95 110 100 M130 100 C140 95 154 95 166 103",
        "M74 119 C82 112 96 112 104 119 C96 124 82 124 74 119 Z M136 119 C144 112 158 112 166 119 C158 124 144 124 136 119 Z",
        "M114 118 C113 136 108 152 104 162 C106 170 114 170 120 167 C126 170 134 170 136 162 C132 152 127 136 126 118",
        "M102 203 C110 194 116 196 120 198 C124 196 130 194 138 203 C130 213 110 213 102 203 Z M102 203 L138 203",
    )
    /** Orejas (con relleno del fondo) y pupilas (rellenas con la tinta). */
    val FRONTAL_OREJAS: List<FormaMapa.Elipse> = listOf(e(33f, 135f, 7f, 17f), e(207f, 135f, 7f, 17f))
    val FRONTAL_PUPILAS: List<FormaMapa.Elipse> = listOf(e(89f, 119f, 2.6f, 2.6f), e(151f, 119f, 2.6f, 2.6f))

    val PERFIL_TRAZOS: List<String> = listOf(
        "M150 20 C90 12 40 52 40 122 C40 160 52 190 70 210",
        "M150 20 C176 26 182 60 180 95 L183 106 C178 113 176 119 178 126 L200 164 C203 170 198 175 188 176 L184 182 C190 188 190 194 186 197 C182 200 182 203 186 206 C189 210 188 216 182 219 C178 223 180 229 186 237 C191 251 183 262 166 266",
        "M166 266 C150 268 128 258 113 240 C105 228 101 206 101 186",
        "M142 263 C144 284 147 300 150 318 M70 210 C74 250 76 290 78 318",
        "M96 124 C88 118 86 140 90 152 C93 160 102 160 104 150 C106 138 103 128 96 124 Z",
        "M160 118 C166 114 172 116 175 120 C170 122 165 122 160 118 Z M156 104 C163 100 172 100 178 104",
        "M184 168 C178 170 176 175 182 177",
    )

    /** Color por tipo de producto (hex: igual en pantalla, modo oscuro y papel). */
    val COLOR_TIPO: Map<String, String> = mapOf(
        "toxina" to "#7c3aed",
        "acido_hialuronico" to "#1d6fa8",
        "bioestimulador" to "#d97706",
        "regenerativo" to "#16a34a",
        "otro" to "#2c3e7a",
    )
    fun colorTipo(tipo: String?): String = COLOR_TIPO[tipo] ?: COLOR_TIPO.getValue("otro")

    // ─── SVG para el registro impreso ────────────────────────────────────────

    /** El rostro (solo lectura) con lo aplicado en cada zona, en colores de papel. [anchoMm] en mm. */
    fun svgRostro(vista: String, porZona: Map<String, ResumenZonaAplicada>, anchoMm: Int): String {
        val espejo = vista == "lateral_izq"
        val tinta = "#6b7280"
        val fondo = "#ffffff"
        val dibujo = buildString {
            append("<g fill=\"none\" stroke=\"$tinta\" stroke-width=\"1.3\" stroke-linecap=\"round\" stroke-linejoin=\"round\">")
            if (vista == "frontal") {
                append("<path d=\"${FRONTAL_TRAZOS[0]}\"/>")
                FRONTAL_OREJAS.forEach { append(svgDeForma(it, "fill=\"$fondo\"")) }
                append("<path d=\"$FRONTAL_CONTORNO\" fill=\"$fondo\"/>")
                FRONTAL_TRAZOS.drop(1).forEach { append("<path d=\"$it\"/>") }
                FRONTAL_PUPILAS.forEach { append(svgDeForma(it, "fill=\"$tinta\"")) }
            } else PERFIL_TRAZOS.forEach { append("<path d=\"$it\"/>") }
            append("</g>")
            zonasDibujo(vista).forEach { z ->
                val r = porZona[z.id] ?: return@forEach
                val color = colorTipo(r.tipos.firstOrNull())
                append(svgDeForma(z.formas.first(), "fill=\"$color\" fill-opacity=\"0.5\" stroke=\"$color\" stroke-width=\"1.2\""))
            }
        }
        val textos = buildString {
            zonasDibujo(vista).forEach { z ->
                val r = porZona[z.id] ?: return@forEach
                val f = z.formas.first() as FormaMapa.Elipse
                val ancho = maxOf(22f, r.texto.length * 4.6f + 6f)
                val x = if (espejo) ANCHO - f.cx else f.cx
                val color = colorTipo(r.tipos.firstOrNull())
                append("<rect x=\"${numSvg(x - ancho / 2)}\" y=\"${numSvg(f.cy - 6)}\" width=\"${numSvg(ancho)}\" height=\"12\" rx=\"6\" fill=\"#ffffff\" fill-opacity=\"0.92\" stroke=\"$color\" stroke-width=\"0.8\"/>")
                append("<text x=\"${numSvg(x)}\" y=\"${numSvg(f.cy + 3)}\" text-anchor=\"middle\" font-size=\"7.5\" font-weight=\"700\" fill=\"#1e2d5e\">${escSvg(r.texto)}</text>")
            }
            if (vista == "frontal") {
                append("<g font-size=\"8\" fill=\"$tinta\" font-weight=\"700\"><text x=\"8\" y=\"314\">Der.</text><text x=\"232\" y=\"314\" text-anchor=\"end\">Izq.</text></g>")
            } else {
                append("<text x=\"${if (espejo) 232 else 8}\" y=\"314\" text-anchor=\"${if (espejo) "end" else "start"}\" font-size=\"8\" fill=\"$tinta\" font-weight=\"700\">Perfil ${if (vista == "lateral_der") "derecho" else "izquierdo"}</text>")
            }
        }
        val transform = if (espejo) " transform=\"translate(240,0) scale(-1,1)\"" else ""
        val titulo = if (vista == "frontal") "Rostro, vista frontal" else "Rostro, perfil ${if (vista == "lateral_der") "derecho" else "izquierdo"}"
        return "<svg viewBox=\"0 0 240 320\" style=\"width:${anchoMm}mm;height:auto\" role=\"img\" aria-label=\"$titulo\"><g$transform>$dibujo</g>$textos</svg>"
    }
}

/**
 * El REGISTRO DE APLICACIÓN (A4), gemelo de HojaRegistroAplicacion.tsx, armado
 * en la app (el visor nativo lo imprime o guarda en PDF): el rostro con lo
 * aplicado, la tabla de trazabilidad (zona · producto · dosis · puntos ·
 * técnica · lote · vencimiento) y quién aplicó. Todo lo de la base se escapa.
 */
fun htmlRegistroAplicacion(
    a: AplicacionEstetica,
    clinicaNombre: String?,
    clinicaDireccion: String?,
    logoUrl: String?,
    colorPrincipal: String?,
    pacienteNombre: String?,
    pacienteDocumento: String?,
    profesional: String?,
    servicio: String?,
): String {
    val color = colorHojaClinica(colorPrincipal)
    val porZona = totalesPorZona(a.aplicaciones)
    val t = a.totales
    val perfiles = if (a.vista == "frontal") emptyList() else listOf("lateral_der", "lateral_izq").filter { v ->
        a.aplicaciones.any { x ->
            val z = zonaFacial(x.zona)
            z?.vista == "lateral" && (z.lado == null || z.lado == (if (v == "lateral_der") "der" else "izq"))
        }
    }
    val logo = logoUrl?.trim()?.takeIf { it.startsWith("https://") }
        ?.let { "<img src=\"${escSvg(it)}\" alt=\"\" class=\"logo\">" }.orEmpty()
    val filas = a.aplicaciones.joinToString("") { x ->
        val tec = listOfNotNull(TECNICAS_APLICACION.firstOrNull { it.id == x.tecnica }?.nombre, PROFUNDIDADES.firstOrNull { it.id == x.profundidad }?.nombre)
            .joinToString(" · ").ifEmpty { "—" }
        val vencido = vencidoEn(x.vencimiento, a.fecha)
        "<tr><td>${escSvg(zonaFacial(x.zona)?.nombre ?: x.zona)}</td>" +
            "<td>${escSvg(x.producto)}${x.notas?.let { "<span class=\"nota\">${escSvg(it)}</span>" }.orEmpty()}</td>" +
            "<td class=\"num b\">${escSvg(textoDosis(x.dosis, x.unidad))}</td><td class=\"num\">${x.puntos ?: "—"}</td>" +
            "<td>${escSvg(tec)}</td><td class=\"nw\">${escSvg(x.lote ?: "—")}</td>" +
            "<td class=\"nw${if (vencido) " vencido" else ""}\">${escSvg(x.vencimiento ?: "—")}</td></tr>"
    }
    val productos = t.porProducto.joinToString("") { p ->
        "<li><b>${escSvg(p.producto)}</b>: ${escSvg(textoDosis(p.total, p.unidad))}${if (p.lotes.isNotEmpty()) " · lote ${escSvg(p.lotes.joinToString(", "))}" else ""}</li>"
    }
    return """<!doctype html><html lang="es"><head><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1">
<title>Registro de aplicación — ${escSvg(pacienteNombre)}</title>
<style>
@page { size: A4 portrait; margin: 1.5cm; }
* { box-sizing: border-box; }
body { font-family: Lato, 'Segoe UI', Arial, sans-serif; color: #111827; background: #fff; margin: 0 auto; max-width: 800px; padding: 16px; font-size: 10pt; line-height: 1.35; -webkit-print-color-adjust: exact; print-color-adjust: exact; }
.cab { display: flex; align-items: center; gap: 12px; padding-bottom: 10px; border-bottom: 2px solid $color; }
.logo { width: 64px; height: 64px; object-fit: contain; }
.cli { flex: 1; min-width: 0; } .cli .n { font-weight: bold; font-size: 13pt; text-transform: uppercase; word-break: break-word; }
.s { color: #4b5563; font-size: 8.5pt; }
.doc { text-align: right; } .doc .n { font-weight: bold; font-size: 11pt; color: $color; }
.pac { padding: 8px 0; border-bottom: 1px solid #e5e7eb; font-size: 9.5pt; } .pac span { margin-right: 24px; }
.top { margin-top: 12px; display: flex; flex-wrap: wrap; gap: 16px; break-inside: avoid; }
.rostros { display: flex; gap: 8px; justify-content: center; }
.tot { flex: 1; min-width: 200px; }
h2 { margin: 0 0 4px; font-size: 9.5pt; text-transform: uppercase; letter-spacing: .03em; color: $color; }
.grande { font-size: 13pt; font-weight: bold; }
ul { margin: 6px 0 0; padding-left: 18px; font-size: 9pt; }
.obs { margin-top: 6px; font-size: 9pt; white-space: pre-line; word-break: break-word; }
table { margin-top: 14px; width: 100%; border-collapse: collapse; font-size: 8.8pt; }
th { text-align: left; color: $color; border-bottom: 2px solid #d1d5db; padding: 4px 6px 4px 0; }
td { border-bottom: 1px solid #e5e7eb; padding: 4px 6px 4px 0; vertical-align: top; }
tr { break-inside: avoid; } .num { text-align: right; } .b { font-weight: bold; } .nw { white-space: nowrap; }
.nota { display: block; font-size: 7.8pt; color: #4b5563; } .vencido { color: #b91c1c; font-weight: bold; }
.firma { margin-top: 48px; display: flex; justify-content: flex-end; break-inside: avoid; } .firma div { width: 64mm; text-align: center; font-size: 8.5pt; }
.linea { border-top: 1px solid #1f2937; margin-top: 14mm; padding-top: 2px; }
.pie-hoja { margin-top: 24px; padding-top: 6px; border-top: 1px solid #e5e7eb; font-size: 7.5pt; color: #9ca3af; display: flex; justify-content: space-between; gap: 8px; }
</style></head><body>
<div class="cab">$logo<div class="cli"><div class="n">${escSvg(clinicaNombre)}</div>${clinicaDireccion?.takeIf { it.isNotBlank() }?.let { "<div class=\"s\">${escSvg(it)}</div>" }.orEmpty()}</div>
<div class="doc"><div class="n">REGISTRO DE APLICACIÓN</div><div class="s">${escSvg(fechaIndicacion(a.fecha))}</div></div></div>
<div class="pac"><span><b>Paciente:</b> ${escSvg(pacienteNombre?.takeIf { it.isNotBlank() } ?: "—")}</span>${pacienteDocumento?.takeIf { it.isNotBlank() }?.let { "<span><b>Documento:</b> ${escSvg(it)}</span>" }.orEmpty()}${servicio?.takeIf { it.isNotBlank() }?.let { "<span><b>Servicio:</b> ${escSvg(it)}</span>" }.orEmpty()}</div>
<div class="top"><div class="rostros">${RostroGeometria.svgRostro("frontal", porZona, 62)}${perfiles.joinToString("") { RostroGeometria.svgRostro(it, porZona, 44) }}</div>
<div class="tot"><h2>Total de la sesión</h2><div class="grande">${escSvg(textoTotales(t))}</div><div class="s">${t.zonas} zonas · ${t.puntos} puntos</div><ul>$productos</ul>
${a.observaciones?.takeIf { it.isNotBlank() }?.let { "<p class=\"obs\"><b>Observaciones:</b> ${escSvg(it)}</p>" }.orEmpty()}</div></div>
<table><thead><tr><th>Zona</th><th>Producto</th><th class="num">Dosis</th><th class="num">Ptos.</th><th>Técnica / plano</th><th>Lote</th><th>Vence</th></tr></thead><tbody>$filas</tbody></table>
<div class="firma"><div><div class="linea">Firma y sello del profesional</div>${profesional?.takeIf { it.isNotBlank() }?.let { "<b>${escSvg(it)}</b>" }.orEmpty()}</div></div>
<div class="pie-hoja"><span>Registro de trazabilidad del producto aplicado (lote y vencimiento). Derecha/izquierda: del paciente.</span><span>Emitido con Sania</span></div>
</body></html>"""
}
