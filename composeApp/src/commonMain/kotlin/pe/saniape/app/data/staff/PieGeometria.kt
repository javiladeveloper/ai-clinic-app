package pe.saniape.app.data.staff

/**
 * 🦶 La GEOMETRÍA del mapa del pie: la misma del SVG de la web
 * (`components/podologia/MapaPie.tsx`), viewBox 100 × 210, dedos arriba y el
 * hallux del pie DERECHO a la derecha. El pie izquierdo es el derecho en
 * espejo: así, frente al paciente, el hallux de cada pie queda hacia el centro
 * (vale igual para la vista plantar —podoscopio— y la dorsal).
 *
 * La usan el Canvas de la app (dibujo + toque) y el informe imprimible (SVG).
 */
object PieGeometria {
    const val ANCHO = 100f
    const val ALTO = 210f

    private val DEDOS: List<FormaMapa> = listOf(
        FormaMapa.Elipse(52f, 22f, 7f, 9.5f),
        FormaMapa.Elipse(39f, 26.5f, 6.5f, 8.5f),
        FormaMapa.Elipse(28f, 33.5f, 6f, 7.5f),
        FormaMapa.Elipse(19f, 43f, 5.5f, 7f),
    )
    private val HALLUX: FormaMapa = FormaMapa.Elipse(72f, 28f, 11f, 15f)

    /** Contorno del pie (no es zona: solo contexto). */
    const val SILUETA = "M44 204 C27 204 20 190 19 170 C18 140 13 112 12 88 C11 70 13 60 19 55 C30 49 60 45 82 46 C90 48 92 60 90 78 C88 100 80 122 76 150 C73 180 63 204 44 204 Z"
    val trazoSilueta: List<ComandoTrazo> = trazosSvg(SILUETA)

    /** Los dedos dibujados como silueta en la vista plantar (contexto, no zona). */
    val dedosDeco: List<FormaMapa> = listOf(HALLUX) + DEDOS

    /** Zona → sus formas (una zona puede ser varias) y su área de toque invisible (uñas, espacios). */
    private val GEOMETRIA: Map<String, ZonaDibujo> = listOf(
        // Plantar
        ZonaDibujo("hallux", listOf(HALLUX)),
        ZonaDibujo("dedos", DEDOS),
        ZonaDibujo("met1", listOf(FormaMapa.Elipse(70f, 66f, 13f, 11f))),
        ZonaDibujo("met_centrales", listOf(FormaMapa.Elipse(46f, 64f, 11f, 10f))),
        ZonaDibujo("met5", listOf(FormaMapa.Elipse(25f, 72f, 9f, 10f))),
        ZonaDibujo("arco", listOf(FormaMapa.Rect(56f, 86f, 23f, 62f, 11f))),
        ZonaDibujo("borde_lateral", listOf(FormaMapa.Rect(18f, 88f, 30f, 62f, 11f))),
        ZonaDibujo("talon", listOf(FormaMapa.Elipse(45f, 178f, 23f, 22f))),
        // Dorsal
        ZonaDibujo("dorso_dedos", listOf(HALLUX) + DEDOS),
        ZonaDibujo("una1", listOf(FormaMapa.Rect(65f, 12f, 14f, 13f, 5f))),
        ZonaDibujo("una2", listOf(FormaMapa.Rect(47.5f, 13f, 9f, 8f, 3f)), listOf(FormaMapa.Elipse(52f, 17f, 7f, 7f))),
        ZonaDibujo("una3", listOf(FormaMapa.Rect(35f, 18.5f, 8f, 7.5f, 3f)), listOf(FormaMapa.Elipse(39f, 22f, 6.5f, 6.5f))),
        ZonaDibujo("una4", listOf(FormaMapa.Rect(24.5f, 26.5f, 7f, 7f, 2.5f)), listOf(FormaMapa.Elipse(28f, 30f, 6f, 6f))),
        ZonaDibujo("una5", listOf(FormaMapa.Rect(15.75f, 37f, 6.5f, 6f, 2.5f)), listOf(FormaMapa.Elipse(19f, 40f, 5.5f, 5.5f))),
        ZonaDibujo(
            "interdigital",
            listOf(FormaMapa.Elipse(61f, 40f, 3.5f, 3.5f), FormaMapa.Elipse(45.5f, 37f, 3.2f, 3.2f), FormaMapa.Elipse(33.5f, 42f, 3f, 3f), FormaMapa.Elipse(23.5f, 50f, 3f, 3f)),
            listOf(FormaMapa.Elipse(61f, 40f, 6f, 6f), FormaMapa.Elipse(45.5f, 37f, 5.5f, 5.5f), FormaMapa.Elipse(33.5f, 42f, 5f, 5f), FormaMapa.Elipse(23.5f, 50f, 5f, 5f)),
        ),
        ZonaDibujo("dorso", listOf(FormaMapa.Rect(20f, 58f, 55f, 110f, 16f))),
        ZonaDibujo("borde_medial", listOf(FormaMapa.Elipse(84f, 68f, 6.5f, 13f))),
    ).associateBy { it.id }

    /** ¿Toda zona del catálogo tiene su dibujo? (para los tests). */
    fun formasDeZona(id: String): ZonaDibujo? = GEOMETRIA[id]

    /**
     * Las zonas de una vista EN ORDEN DE DIBUJO (la última queda arriba): los
     * dedos y el dorso primero, así las uñas y los espacios quedan encima.
     * El pie izquierdo, en espejo.
     */
    fun zonasDe(vista: String, pie: String): List<ZonaDibujo> {
        val zonas = ZONAS_PODO.filter { it.vista == vista }
        val orden = zonas.sortedBy { if (it.id == "dorso_dedos" || it.id == "dorso") 0 else 1 }
        return orden.mapNotNull { z ->
            val g = GEOMETRIA[z.id] ?: return@mapNotNull null
            if (pie == PIE_IZQ) ZonaDibujo(g.id, g.formas.map { it.espejo(ANCHO) }, g.toque.map { it.espejo(ANCHO) }) else g
        }
    }

    /** La zona tocada en (x, y) del viewBox de ese pie (la de arriba gana, con su área de toque). */
    fun zonaEn(vista: String, pie: String, x: Float, y: Float): String? = zonaTocadaEnOrden(zonasDe(vista, pie), x, y)

    // ─── SVG para el informe impreso ─────────────────────────────────────────

    private const val VACIO = "#ffffff"
    private const val BORDE = "#bcc4e8"
    private const val SILUETA_FILL = "#f3f4fa"
    private const val SILUETA_BORDE = "#c9cee6"

    /** Un pie como SVG (colores fijos de papel). [ancho] en px. */
    fun svgPie(zonas: List<ZonaMarcadaPodo>, vista: String, pie: String, ancho: Int): String {
        val porZona = normalizarZonasPodo(zonas).associateBy { it.clave }
        val alto = (ancho * ALTO / ANCHO).toInt()
        val espejo = if (pie == PIE_IZQ) " transform=\"translate(100,0) scale(-1,1)\"" else ""
        // Las formas van SIN espejo dentro del grupo reflejado (como la web).
        val zonasBase = zonasDe(vista, PIE_DER)
        val cuerpo = buildString {
            append("<path d=\"$SILUETA\" fill=\"$SILUETA_FILL\" stroke=\"$SILUETA_BORDE\" stroke-width=\"1\"/>")
            if (vista == "plantar") dedosDeco.forEach { append(svgDeForma(it, "fill=\"$SILUETA_FILL\" stroke=\"$SILUETA_BORDE\" stroke-width=\"0.8\"")) }
            zonasBase.forEach { z ->
                val marcada = porZona["$pie:$vista:${z.id}"]
                val p = pinturaZona(marcada?.hallazgos)
                val fill = p?.color ?: VACIO
                val op = p?.opacidad ?: 1f
                val stroke = p?.color ?: BORDE
                z.formas.forEach { f ->
                    append(svgDeForma(f, "fill=\"$fill\" fill-opacity=\"${numSvg(op)}\" stroke=\"$stroke\" stroke-width=\"1\""))
                }
            }
        }
        return "<figure class=\"pie\"><svg viewBox=\"0 0 100 210\" width=\"$ancho\" height=\"$alto\" role=\"img\" " +
            "aria-label=\"${escSvg(nombrePie(pie))} · vista ${nombreVistaPie(vista).lowercase()}\"><g$espejo>$cuerpo</g></svg>" +
            "<figcaption>${escSvg(nombrePie(pie))}</figcaption></figure>"
    }
}

internal fun escSvg(s: String?): String = s.orEmpty()
    .replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;").replace("'", "&#39;")

/** El color de la clínica para las hojas: solo `#rrggbb`; si no, el navy de Sania. */
internal fun colorHojaClinica(color: String?): String =
    color?.trim()?.takeIf { Regex("^#[0-9a-fA-F]{6}$").matches(it) } ?: "#2c3e7a"

/**
 * El INFORME DE EVALUACIÓN PODOLÓGICA (A4), gemelo de
 * HojaEvaluacionPodologica.tsx, armado en la app (el visor nativo lo imprime o
 * guarda en PDF). Todo lo que viene de la base se escapa; el logo solo si es
 * https y el color solo si es #rrggbb.
 */
fun htmlEvaluacionPodo(
    ev: EvaluacionPodologica,
    anterior: EvaluacionPodologica?,
    clinicaNombre: String?,
    clinicaDireccion: String?,
    logoUrl: String?,
    colorPrincipal: String?,
    pacienteNombre: String?,
    pacienteDocumento: String?,
    profesional: String?,
): String {
    val color = colorHojaClinica(colorPrincipal)
    val zonas = normalizarZonasPodo(ev.zonas)
    val est = estadoRiesgo(ev)
    val cat = categoriaIwgdf(est.categoria)
    val colorRiesgo = cat?.color ?: "#6b7280"
    val tamizaje = lineasTamizaje(ev.tamizaje)
    val datos = paresDatosGenerales(ev.tipoPie, ev.pisada, ev.higiene, ev.calzado)
    val recomendaciones = recomendacionesRiesgo(ev.riesgoIwgdf)
    val comp = compararEvaluacionesPodo(ev, anterior)
    val logo = logoUrl?.trim()?.takeIf { it.startsWith("https://") }
        ?.let { "<img src=\"${escSvg(it)}\" alt=\"\" class=\"logo\">" }.orEmpty()
    fun lista(items: List<String>) = "<ul>" + items.joinToString("") { "<li>${escSvg(it)}</li>" } + "</ul>"
    val cuerpo = buildString {
        if (cat != null) {
            append("<section class=\"riesgo\" style=\"border-color:$colorRiesgo\"><div class=\"rt\" style=\"color:$colorRiesgo\">")
            append(escSvg("Riesgo de pie diabético (IWGDF 2023): categoría ${cat.categoria} — ${cat.nombre}${if (est.incompleto) " (tamizaje incompleto)" else ""}"))
            append("</div><div>${escSvg(cat.criterio)}. <b>Control recomendado: ${escSvg(cat.control.lowercase())}.</b></div>")
            if (anterior != null && comp.riesgo.cambio != null && comp.riesgo.cambio != "igual") {
                append("<div class=\"s\">${if (comp.riesgo.cambio == "subio") "Subió" else "Bajó"} desde la categoría ${comp.riesgo.antes} (${escSvg(fechaEvalPodo(anterior.fecha))}).</div>")
            }
            append("</section>")
        }
        if (est.sinDeterminar) {
            append("<section class=\"aviso ambar\"><div class=\"rt\">Riesgo de pie diabético sin determinar</div>")
            append("<div>Completa el tamizaje (monofilamento 10 g en los 6 sitios y pulsos pedio y tibial posterior) para estratificar el riesgo.</div></section>")
        }
        if (hayUlceraActiva(zonas)) {
            append("<section class=\"aviso rojo\"><b>Úlcera activa:</b> requiere tratamiento y seguimiento cercano; consulte de inmediato ante fiebre, mal olor, enrojecimiento que se extiende o dolor que aumenta.</section>")
        }
        if (zonas.isNotEmpty()) {
            append("<section><h2>Mapa del pie</h2><div class=\"mapas\">")
            for (vista in listOf("plantar", "dorsal")) {
                append("<div><div class=\"vt\">Vista ${vista}</div><div class=\"pies\">")
                PIES_PODO.forEach { append(PieGeometria.svgPie(zonas, vista, it, 95)) }
                append("</div></div>")
            }
            append("</div><div class=\"leyenda\">")
            GRUPOS_HALLAZGO_PODO.forEach { g -> append("<span><i style=\"background:${g.color}\"></i>${escSvg(g.nombre)}</span>") }
            append("<span>· más intenso = más severo</span></div>")
            append(lista(zonas.map { textoZonaMarcada(it) }))
            if (anterior != null && comp.cambios.isNotEmpty()) {
                append("<div class=\"s\"><b>Cambios desde el ${escSvg(fechaEvalPodo(anterior.fecha))}:</b> ${escSvg(comp.cambios.joinToString(" · ") { textoCambioPodo(it) })}</div>")
            }
            append("</section>")
        }
        if (tamizaje.isNotEmpty()) append("<section><h2>Tamizaje de pie diabético</h2>${lista(tamizaje)}</section>")
        if (datos.isNotEmpty() || !ev.observaciones.isNullOrBlank()) {
            append("<section><h2>Datos generales</h2><div class=\"pares\">")
            datos.forEach { (e, v) -> append("<span><b>${escSvg(e)}:</b> ${escSvg(v)}</span>") }
            append("</div>")
            ev.observaciones?.takeIf { it.isNotBlank() }?.let { append("<p class=\"obs\"><b>Observaciones:</b> ${escSvg(it)}</p>") }
            append("</section>")
        }
        if (recomendaciones.isNotEmpty()) append("<section><h2>Recomendaciones</h2>${lista(recomendaciones)}</section>")
    }
    return """<!doctype html><html lang="es"><head><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1">
<title>Evaluación podológica — ${escSvg(pacienteNombre)}</title>
<style>
@page { size: A4 portrait; margin: 1.5cm; }
* { box-sizing: border-box; }
body { font-family: Lato, 'Segoe UI', Arial, sans-serif; color: #111827; background: #fff; margin: 0 auto; max-width: 800px; padding: 16px; font-size: 10.5pt; line-height: 1.35; -webkit-print-color-adjust: exact; print-color-adjust: exact; }
.cab { display: flex; align-items: center; gap: 12px; padding-bottom: 10px; border-bottom: 2px solid $color; }
.logo { width: 64px; height: 64px; object-fit: contain; }
.cli { flex: 1; min-width: 0; } .cli .n { font-weight: bold; font-size: 13pt; text-transform: uppercase; word-break: break-word; }
.s { color: #4b5563; font-size: 9pt; margin-top: 2px; }
.doc { text-align: right; } .doc .n { font-weight: bold; font-size: 11pt; color: $color; }
.pac { padding: 8px 0; border-bottom: 1px solid #e5e7eb; font-size: 9.5pt; } .pac span { margin-right: 24px; }
h1 { margin: 16px 0 0; font-size: 14pt; color: $color; }
section { margin-top: 14px; break-inside: avoid; }
h2 { margin: 0 0 4px; font-size: 9.5pt; text-transform: uppercase; letter-spacing: .03em; color: $color; }
ul { margin: 0; padding-left: 20px; } li { margin: 2px 0; word-break: break-word; font-size: 9.5pt; }
.riesgo { border: 2px solid; border-radius: 8px; padding: 8px 12px; font-size: 9.5pt; } .rt { font-weight: bold; font-size: 11pt; }
.aviso { border: 2px solid; border-radius: 8px; padding: 8px 12px; font-size: 9.5pt; }
.ambar { border-color: #d97706; background: #fffbeb; } .ambar .rt { color: #b45309; }
.rojo { border-color: #dc2626; background: #fef2f2; } .rojo b { color: #b91c1c; }
.mapas { display: flex; flex-wrap: wrap; justify-content: center; gap: 24px; }
.vt { text-align: center; font-size: 8.5pt; font-weight: bold; color: #4b5563; text-transform: capitalize; }
.pies { display: flex; gap: 12px; } .pie { margin: 0; text-align: center; } .pie figcaption { font-size: 7.5pt; font-weight: bold; text-transform: uppercase; color: #6b7280; }
.leyenda { display: flex; flex-wrap: wrap; justify-content: center; gap: 4px 12px; font-size: 8pt; color: #4b5563; margin: 4px 0 6px; }
.leyenda i { display: inline-block; width: 9px; height: 9px; border-radius: 50%; margin-right: 4px; vertical-align: middle; }
.pares span { margin-right: 24px; font-size: 9.5pt; } .obs { margin: 4px 0 0; font-size: 9.5pt; white-space: pre-wrap; word-break: break-word; }
.firma { margin-top: 40px; display: flex; justify-content: flex-end; } .firma div { width: 60mm; text-align: center; font-size: 8.5pt; }
.linea { border-top: 1px solid #1f2937; margin-top: 14mm; padding-top: 2px; }
.pie-hoja { margin-top: 24px; padding-top: 6px; border-top: 1px solid #e5e7eb; font-size: 7.5pt; color: #9ca3af; display: flex; justify-content: space-between; gap: 8px; }
</style></head><body>
<div class="cab">$logo<div class="cli"><div class="n">${escSvg(clinicaNombre)}</div>${clinicaDireccion?.takeIf { it.isNotBlank() }?.let { "<div class=\"s\">${escSvg(it)}</div>" }.orEmpty()}</div>
<div class="doc"><div class="n">EVALUACIÓN PODOLÓGICA</div><div class="s">${escSvg(fechaEvalPodo(ev.fecha))}</div></div></div>
<div class="pac"><span><b>Paciente:</b> ${escSvg(pacienteNombre?.takeIf { it.isNotBlank() } ?: "—")}</span>${pacienteDocumento?.takeIf { it.isNotBlank() }?.let { "<span><b>Documento:</b> ${escSvg(it)}</span>" }.orEmpty()}</div>
<h1>Informe de evaluación podológica</h1>
$cuerpo
<div class="firma"><div><div class="linea">Firma y sello del profesional</div>${profesional?.takeIf { it.isNotBlank() }?.let { "<b>${escSvg(it)}</b>" }.orEmpty()}</div></div>
<div class="pie-hoja"><span>Estratificación de riesgo: IWGDF 2023 (International Working Group on the Diabetic Foot).</span><span>Emitido con Sania</span></div>
</body></html>"""
}
