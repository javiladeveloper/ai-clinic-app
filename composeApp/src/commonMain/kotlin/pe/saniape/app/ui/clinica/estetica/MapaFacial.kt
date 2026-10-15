package pe.saniape.app.ui.clinica.estetica

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import pe.saniape.app.data.staff.FormaMapa
import pe.saniape.app.data.staff.ResumenZonaAplicada
import pe.saniape.app.data.staff.RostroGeometria
import pe.saniape.app.data.staff.trazosSvg
import pe.saniape.app.data.staff.zonaFacial
import pe.saniape.app.ui.clinica.mapas.NodoZonaAccesible
import pe.saniape.app.ui.clinica.mapas.colorHex
import pe.saniape.app.ui.clinica.mapas.dibujarForma
import pe.saniape.app.ui.clinica.mapas.dibujarTrazo
import pe.saniape.app.ui.theme.Sania

/** Los trazos del rostro, leídos una vez. */
private object TrazosRostro {
    val contorno = trazosSvg(RostroGeometria.FRONTAL_CONTORNO)
    val frontal = RostroGeometria.FRONTAL_TRAZOS.map { trazosSvg(it) }
    val perfil = RostroGeometria.PERFIL_TRAZOS.map { trazosSvg(it) }
}

/**
 * 💉 El ROSTRO del mapa de aplicación. Gemelo de `components/estetica/MapaFacial.tsx`:
 * frente o perfil (lado del PACIENTE: en el frente su derecha queda a la
 * izquierda de quien mira; el perfil izquierdo es el derecho en espejo), la
 * MISMA geometría (viewBox 240 × 320, ver [RostroGeometria]) en un Canvas, con
 * lo aplicado escrito sobre cada zona.
 *
 * Toque: gana la zona dibujada; fuera de ellas, el área de toque INVISIBLE más
 * cercana, con radio mínimo de 13 unidades y nunca menos de 32 dp de diámetro
 * (código de barras, surcos, comisuras, punta nasal…). El editor ofrece además
 * la lista de zonas. Accesible: un nodo por zona con su nombre y su total.
 * Sin [onZona] = solo lectura; [compacto] = miniatura (solo las zonas con algo).
 */
@Composable
fun MapaFacial(
    vista: String,
    porZona: Map<String, ResumenZonaAplicada>,
    modifier: Modifier = Modifier,
    seleccionada: String? = null,
    onZona: ((String) -> Unit)? = null,
    compacto: Boolean = false,
) {
    val c = Sania.colors
    val espejo = vista == "lateral_izq"
    val zonas = remember(vista) { RostroGeometria.zonasDibujo(vista) }
    val medidor = rememberTextMeasurer()
    val alTocar by rememberUpdatedState(onZona)
    val tinta = c.textoSuave
    val fondo = c.superficie
    val lavanda = Color(0xFF8892C8)
    val titulo = if (vista == "frontal") "Rostro, vista frontal" else "Rostro, perfil ${if (vista == "lateral_der") "derecho" else "izquierdo"}"
    BoxWithConstraints(
        modifier.aspectRatio(RostroGeometria.ANCHO / RostroGeometria.ALTO)
            .semantics { contentDescription = titulo }
            .then(if (onZona != null) Modifier.pointerInput(vista) {
                detectTapGestures { p ->
                    val s = size.width / RostroGeometria.ANCHO
                    // Radio mínimo: 13 del viewBox o 16 dp (32 dp de diámetro), lo que sea mayor.
                    val radioMin = 16.dp.toPx() / s
                    RostroGeometria.zonaEn(vista, p.x / s, p.y / s, radioMin)?.let { z -> alTocar?.invoke(z) }
                }
            } else Modifier),
    ) {
        val escalaDp = maxWidth.value / RostroGeometria.ANCHO
        Canvas(Modifier.fillMaxSize()) {
            val s = size.width / RostroGeometria.ANCHO
            scale(scaleX = if (espejo) -1f else 1f, scaleY = 1f, pivot = Offset(size.width / 2f, 0f)) {
                if (vista == "frontal") {
                    dibujarTrazo(TrazosRostro.frontal[0], s, tinta)
                    RostroGeometria.FRONTAL_OREJAS.forEach { dibujarForma(it, s, fondo, tinta, 1.3f) }
                    dibujarTrazo(TrazosRostro.contorno, s, tinta, relleno = fondo)
                    TrazosRostro.frontal.drop(1).forEach { dibujarTrazo(it, s, tinta) }
                    RostroGeometria.FRONTAL_PUPILAS.forEach { dibujarForma(it, s, tinta, null) }
                } else TrazosRostro.perfil.forEach { dibujarTrazo(it, s, tinta) }
                zonas.forEach { z ->
                    val r = porZona[z.id]
                    if (compacto && r == null) return@forEach
                    val sel = seleccionada == z.id
                    val color = r?.let { colorHex(RostroGeometria.colorTipo(it.tipos.firstOrNull())) }
                    val relleno = when {
                        color != null -> color.copy(alpha = if (sel) 0.75f else 0.5f)
                        onZona != null -> lavanda.copy(alpha = if (sel) 0.45f else 0.14f)
                        else -> null
                    }
                    dibujarForma(z.formas.first(), s, relleno, if (sel) c.navy else color ?: lavanda,
                        if (sel) 2.4f else 1.2f, punteado = color == null && !sel)
                }
            }
            if (!compacto) {
                // Totales escritos sobre cada zona con algo (fuera del espejo: si no, salen al revés).
                zonas.forEach { z ->
                    val r = porZona[z.id] ?: return@forEach
                    val f = z.formas.first() as FormaMapa.Elipse
                    val ancho = maxOf(22f, r.texto.length * 4.6f + 6f) * s
                    val x = (if (espejo) RostroGeometria.ANCHO - f.cx else f.cx) * s
                    val col = colorHex(RostroGeometria.colorTipo(r.tipos.firstOrNull()))
                    val tl = Offset(x - ancho / 2f, (f.cy - 6f) * s)
                    drawRoundRect(Color.White.copy(alpha = 0.92f), tl, Size(ancho, 12f * s), CornerRadius(6f * s))
                    drawRoundRect(col, tl, Size(ancho, 12f * s), CornerRadius(6f * s), style = Stroke(0.8f * s))
                    val medida = medidor.measure(r.texto, TextStyle(color = Color(0xFF1E2D5E), fontSize = (7.5f * s).toSp(), fontWeight = FontWeight.Bold))
                    drawText(medida, topLeft = Offset(x - medida.size.width / 2f, f.cy * s - medida.size.height / 2f))
                }
                // Rótulo del lado: evita confundir derecha/izquierda del paciente.
                val estilo = TextStyle(color = tinta, fontSize = (8f * s).toSp(), fontWeight = FontWeight.Bold)
                if (vista == "frontal") {
                    drawText(medidor.measure("Der.", estilo), topLeft = Offset(8f * s, 304f * s))
                    val izq = medidor.measure("Izq.", estilo)
                    drawText(izq, topLeft = Offset(232f * s - izq.size.width, 304f * s))
                } else {
                    val t = medidor.measure("Perfil ${if (vista == "lateral_der") "derecho" else "izquierdo"}", estilo)
                    drawText(t, topLeft = Offset(if (espejo) 232f * s - t.size.width else 8f * s, 304f * s))
                }
            }
        }
        // Accesibilidad: un nodo por zona (sin interceptar el dedo).
        if (!compacto) zonas.forEach { z ->
            val r = porZona[z.id]
            val nombre = zonaFacial(z.id)?.nombre ?: z.id
            val forma = if (espejo) z.formas.first().espejo(RostroGeometria.ANCHO) else z.formas.first()
            NodoZonaAccesible(
                formas = listOf(forma), escalaDp = escalaDp,
                descripcion = nombre + (r?.let { ": ${it.texto}" } ?: ""),
                seleccionada = seleccionada == z.id,
                onActivar = onZona?.let { cb -> { cb(z.id) } },
            )
        }
    }
}
