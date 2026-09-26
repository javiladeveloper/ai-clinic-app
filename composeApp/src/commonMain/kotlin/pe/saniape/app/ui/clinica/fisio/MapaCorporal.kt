package pe.saniape.app.ui.clinica.fisio

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.background
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import pe.saniape.app.data.staff.INTENSIDADES
import pe.saniape.app.data.staff.alternarZona
import pe.saniape.app.data.staff.textoZonas
import pe.saniape.app.ui.theme.Sania

/**
 * Mapa corporal tocable (M6). Gemelo de `components/fisio/MapaCorporal.tsx`: MISMA
 * geometría (viewBox 120 × 234, 39 zonas, frente y espalda) dibujada en un Canvas.
 * Cada toque sube la intensidad: leve → moderado → severo → nada. Sin [onChange]
 * es de solo lectura (pestaña 📏).
 *
 * Lados: en la vista de FRENTE la derecha del paciente queda a la izquierda de quien
 * mira; en la de ESPALDA coinciden (por eso los `_d` cambian de lado entre vistas).
 */

/** Forma en coordenadas del viewBox. */
internal sealed class FormaZona {
    data class Elipse(val cx: Float, val cy: Float, val rx: Float, val ry: Float) : FormaZona()
    data class Rect(val x: Float, val y: Float, val w: Float, val h: Float, val r: Float = 3f) : FormaZona()

    fun contiene(px: Float, py: Float): Boolean = when (this) {
        is Elipse -> { val dx = (px - cx) / rx; val dy = (py - cy) / ry; dx * dx + dy * dy <= 1f }
        is Rect -> px in x..(x + w) && py in y..(y + h)
    }
}

private const val VB_W = 120f
private const val VB_H = 234f

private fun espejo(f: FormaZona): FormaZona = when (f) {
    is FormaZona.Elipse -> f.copy(cx = VB_W - f.cx)
    is FormaZona.Rect -> f.copy(x = VB_W - f.x - f.w)
}

/** Pares: la forma dada es la del lado IZQUIERDO de quien mira. */
private fun par(base: String, f: FormaZona, izqEs: Char): List<Pair<String, FormaZona>> {
    val otro = if (izqEs == 'd') 'i' else 'd'
    return listOf("${base}_$izqEs" to f, "${base}_$otro" to espejo(f))
}

internal val MAPA_FRENTE: List<Pair<String, FormaZona>> = buildList {
    add("cabeza" to FormaZona.Elipse(60f, 17f, 12f, 15f))
    add("cuello" to FormaZona.Rect(53f, 32f, 14f, 10f, 3f))
    addAll(par("hombro", FormaZona.Elipse(36f, 50f, 10f, 8f), 'd'))
    add("pecho" to FormaZona.Rect(44f, 43f, 32f, 29f, 6f))
    add("abdomen" to FormaZona.Rect(45f, 73f, 30f, 30f, 5f))
    addAll(par("brazo", FormaZona.Rect(24f, 58f, 12f, 29f, 5f), 'd'))
    addAll(par("codo", FormaZona.Elipse(28f, 93f, 7f, 6f), 'd'))
    addAll(par("antebrazo", FormaZona.Rect(20f, 99f, 12f, 27f, 5f), 'd'))
    addAll(par("mano", FormaZona.Elipse(24f, 136f, 7f, 9f), 'd'))
    addAll(par("cadera", FormaZona.Rect(45f, 104f, 14f, 16f, 4f), 'd'))
    addAll(par("muslo", FormaZona.Rect(44f, 121f, 15f, 37f, 6f), 'd'))
    addAll(par("rodilla", FormaZona.Elipse(51f, 165f, 8f, 7f), 'd'))
    addAll(par("pierna", FormaZona.Rect(45f, 173f, 12f, 40f, 5f), 'd'))
    addAll(par("pie", FormaZona.Elipse(50f, 222f, 9f, 7f), 'd'))
}

internal val MAPA_ESPALDA: List<Pair<String, FormaZona>> = buildList {
    add("cervical" to FormaZona.Rect(53f, 32f, 14f, 11f, 3f))
    addAll(par("escapula", FormaZona.Rect(39f, 44f, 19f, 25f, 6f), 'i'))
    add("dorsal" to FormaZona.Rect(45f, 70f, 30f, 15f, 4f))
    add("lumbar" to FormaZona.Rect(45f, 86f, 30f, 18f, 4f))
    addAll(par("gluteo", FormaZona.Rect(45f, 105f, 14f, 18f, 6f), 'i'))
    addAll(par("isquio", FormaZona.Rect(44f, 124f, 15f, 34f, 6f), 'i'))
    addAll(par("poplitea", FormaZona.Elipse(51f, 165f, 8f, 7f), 'i'))
    addAll(par("pantorrilla", FormaZona.Rect(45f, 173f, 12f, 40f, 5f), 'i'))
    addAll(par("talon", FormaZona.Elipse(50f, 222f, 9f, 7f), 'i'))
}

/** Silueta de la espalda que no es zona (cabeza y brazos): solo contexto. */
private val ESPALDA_DECO: List<FormaZona> = buildList {
    add(FormaZona.Elipse(60f, 17f, 12f, 15f))
    listOf(FormaZona.Rect(24f, 50f, 13f, 38f, 6f), FormaZona.Rect(20f, 90f, 12f, 38f, 5f), FormaZona.Elipse(24f, 136f, 7f, 9f))
        .forEach { add(it); add(espejo(it)) }
}

/** La zona tocada en (x, y) del viewBox; la de arriba gana (orden inverso al dibujo). */
internal fun zonaEn(zonas: List<Pair<String, FormaZona>>, x: Float, y: Float): String? =
    zonas.lastOrNull { it.second.contiene(x, y) }?.first

private fun DrawScope.dibujar(f: FormaZona, escala: Float, relleno: Color, borde: Color) {
    val trazo = Stroke(width = 1f * escala)
    when (f) {
        is FormaZona.Elipse -> {
            val tl = Offset((f.cx - f.rx) * escala, (f.cy - f.ry) * escala)
            val sz = Size(f.rx * 2 * escala, f.ry * 2 * escala)
            drawOval(relleno, tl, sz)
            drawOval(borde, tl, sz, style = trazo)
        }
        is FormaZona.Rect -> {
            val tl = Offset(f.x * escala, f.y * escala)
            val sz = Size(f.w * escala, f.h * escala)
            val cr = CornerRadius(f.r * escala, f.r * escala)
            drawRoundRect(relleno, tl, sz, cr)
            drawRoundRect(borde, tl, sz, cr, style = trazo)
        }
    }
}

@Composable
private fun VistaMapa(
    titulo: String,
    zonas: List<Pair<String, FormaZona>>,
    deco: List<FormaZona>,
    valor: Map<String, Int>,
    onToggle: ((String) -> Unit)?,
    ancho: Dp,
    modifier: Modifier = Modifier,
) {
    val c = Sania.colors
    val colorSin = c.superficie
    val bordeSin = c.lav.copy(alpha = 0.7f)
    val decoFill = c.chipBg
    // El detector de toques vive mientras la vista existe: lee SIEMPRE el último callback
    // (si no, cada toque partiría del mapa del primer dibujo).
    val toggleActual by rememberUpdatedState(onToggle)
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        val base = Modifier.widthIn(max = ancho).fillMaxWidth().aspectRatio(VB_W / VB_H)
            .semantics { contentDescription = "$titulo: ${textoZonas(valor.filterKeys { k -> zonas.any { it.first == k } }).ifEmpty { "sin marcas" }}" }
        val conToque = if (onToggle != null) base.pointerInput(zonas) {
            detectTapGestures { p ->
                val escala = size.width / VB_W
                zonaEn(zonas, p.x / escala, p.y / escala)?.let { toggleActual?.invoke(it) }
            }
        } else base
        Canvas(conToque) {
            val escala = size.width / VB_W
            deco.forEach { dibujar(it, escala, decoFill, c.borde) }
            zonas.forEach { (id, f) ->
                val v = valor[id]
                if (v != null && v in 1..3) {
                    val col = Color(INTENSIDADES[v - 1].second)
                    dibujar(f, escala, col.copy(alpha = 0.9f), col)
                } else dibujar(f, escala, colorSin, bordeSin)
            }
        }
        Text(titulo.uppercase(), color = c.textoSuave, fontSize = 10.sp, fontWeight = FontWeight.Bold,
            letterSpacing = 0.6.sp, modifier = Modifier.padding(top = 3.dp))
    }
}

/**
 * Frente y espalda lado a lado. [ancho] = máximo de cada vista (la figura escala).
 * [leyenda]: colores + "toca para marcar" (si es editable). Debajo, el texto de lo marcado.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun MapaCorporal(
    valor: Map<String, Int>?,
    onChange: ((Map<String, Int>) -> Unit)? = null,
    ancho: Dp = 140.dp,
    leyenda: Boolean = true,
    mostrarTexto: Boolean = true,
) {
    val c = Sania.colors
    val v = valor.orEmpty()
    val toggle: ((String) -> Unit)? = onChange?.let { cb -> { id: String -> cb(alternarZona(v, id)) } }
    Column(Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterHorizontally)) {
            VistaMapa("Frente", MAPA_FRENTE, emptyList(), v, toggle, ancho, Modifier.weight(1f, fill = false).widthIn(max = ancho))
            VistaMapa("Espalda", MAPA_ESPALDA, ESPALDA_DECO, v, toggle, ancho, Modifier.weight(1f, fill = false).widthIn(max = ancho))
        }
        if (leyenda) {
            FlowRow(
                Modifier.fillMaxWidth().padding(top = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterHorizontally),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                if (onChange != null) Text("Toca para marcar · otra vez sube la intensidad", color = c.textoSuave, fontSize = 10.sp)
                INTENSIDADES.forEach { (label, argb) ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(9.dp).clip(CircleShape).background(Color(argb)))
                        Spacer(Modifier.width(4.dp))
                        Text(label, color = c.textoSuave, fontSize = 10.sp)
                    }
                }
            }
        }
        val texto = textoZonas(v)
        if (mostrarTexto && texto.isNotEmpty()) {
            Text(texto, color = c.texto, fontSize = 12.sp, textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(top = 5.dp))
        }
    }
}
