package pe.saniape.app.ui.clinica.audiologia

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import pe.saniape.app.data.staff.AudiometriaOido
import pe.saniape.app.data.staff.Curva
import pe.saniape.app.data.staff.DB_MAX
import pe.saniape.app.data.staff.DB_MIN
import pe.saniape.app.data.staff.DB_PASO
import pe.saniape.app.data.staff.FRECUENCIAS_AEREA
import pe.saniape.app.data.staff.FRECUENCIAS_OSEA
import pe.saniape.app.data.staff.Oido
import pe.saniape.app.data.staff.Umbral
import pe.saniape.app.data.staff.brechaAereoOsea
import pe.saniape.app.ui.theme.Sania
import kotlin.math.abs
import kotlin.math.roundToInt

enum class ViaAudiometria { AEREA, OSEA }

// El lienzo en las MISMAS unidades que el SVG de la web (340 × 372): mismo dibujo y mismos toques.
private const val W = 340f
private const val H = 372f
private const val IZQ = 38f
private const val DER = 12f
private const val ARR = 26f
private const val ABA = 22f
private const val ANCHO = W - IZQ - DER
private const val ALTO = H - ARR - ABA
private fun xDe(f: Int) = IZQ + (FRECUENCIAS_AEREA.indexOf(f) + 0.5f) * (ANCHO / FRECUENCIAS_AEREA.size)
private fun yDe(db: Int) = ARR + ((db - DB_MIN).toFloat() / (DB_MAX - DB_MIN)) * ALTO

val COLOR_OD = Color(0xFFDC2626)
val COLOR_OI = Color(0xFF2563EB)
fun colorOido(o: Oido) = if (o == Oido.OD) COLOR_OD else COLOR_OI

private val FRANJAS = listOf(20 to "Normal", 35 to "Leve", 50 to "Moderada", 65 to "Mod. severa", 80 to "Severa", 95 to "Profunda", DB_MAX to "Total")
private fun etiquetaHz(f: Int) = if (f >= 1000) "${f / 1000}k" else "$f"

/** El toque (en unidades del lienzo) → la curva nueva, o null si cae fuera de la cuadrícula. Pura. */
fun tocarAudiograma(valor: AudiometriaOido, via: ViaAudiometria, sinRespuesta: Boolean, px: Float, py: Float): AudiometriaOido? {
    if (px < IZQ || px > W - DER || py < ARR - 6 || py > H - ABA + 6) return null
    val fs = if (via == ViaAudiometria.OSEA) FRECUENCIAS_OSEA else FRECUENCIAS_AEREA
    val f = fs.minBy { abs(xDe(it) - px) }
    val crudo = DB_MIN + ((py - ARR) / ALTO) * (DB_MAX - DB_MIN)
    val db = ((crudo / DB_PASO).roundToInt() * DB_PASO).coerceIn(DB_MIN, DB_MAX)
    val actual: Curva = if (via == ViaAudiometria.OSEA) valor.osea else valor.aerea
    val previo = actual[f]
    val nueva = if (previo != null && previo.db == db && previo.nr == sinRespuesta) actual - f else actual + (f to Umbral(db, sinRespuesta))
    return if (via == ViaAudiometria.OSEA) valor.copy(osea = nueva) else valor.copy(aerea = nueva)
}

/**
 * 📈 Audiograma de un oído (gemelo de components/audiologia/Audiograma.tsx): aérea O (derecho,
 * rojo) / X (izquierdo, azul); ósea < / >; la aérea unida con línea continua, la ósea punteada
 * solo si hay brecha; «sin respuesta» con flecha y sin unir. Con [onChange] es editable.
 */
@Composable
fun Audiograma(
    oido: Oido,
    valor: AudiometriaOido,
    via: ViaAudiometria = ViaAudiometria.AEREA,
    sinRespuesta: Boolean = false,
    onChange: ((AudiometriaOido) -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val c = Sania.colors
    val color = colorOido(oido)
    val medidor = rememberTextMeasurer()
    val valorActual by rememberUpdatedState(valor)
    val viaActual by rememberUpdatedState(via)
    val nrActual by rememberUpdatedState(sinRespuesta)
    val alCambiar by rememberUpdatedState(onChange)
    val suave = c.textoSuave
    val linea = c.borde
    Column(modifier.widthIn(max = 380.dp)) {
        Row(Modifier.padding(bottom = 4.dp)) {
            Text(oido.nombre, color = color, fontSize = 13.sp, fontWeight = FontWeight.Bold)
            Text(if (oido == Oido.OD) "  O aérea · < ósea" else "  X aérea · > ósea", color = c.textoSuave, fontSize = 11.sp)
        }
        Canvas(
            Modifier.fillMaxWidth().aspectRatio(W / H).clip(RoundedCornerShape(12.dp)).border(1.dp, c.borde, RoundedCornerShape(12.dp))
                .then(
                    if (onChange != null) Modifier.pointerInput(Unit) {
                        detectTapGestures { p ->
                            val e = size.width / W
                            tocarAudiograma(valorActual, viaActual, nrActual, p.x / e, p.y / e)?.let { alCambiar?.invoke(it) }
                        }
                    } else Modifier,
                ),
        ) {
            val e = size.width / W
            fun P(x: Float, y: Float) = Offset(x * e, y * e)
            // Franjas de la OMS.
            FRANJAS.forEachIndexed { i, (hasta, nombre) ->
                val desde = if (i == 0) DB_MIN else FRANJAS[i - 1].first
                drawRect(color.copy(alpha = 0.02f + i * 0.022f), topLeft = P(IZQ, yDe(desde)), size = Size(ANCHO * e, (yDe(hasta) - yDe(desde)) * e))
                texto(medidor, nombre, P(W - DER - 3, yDe(desde) + 2), 8f * e, suave, derecha = true)
            }
            // Cuadrícula.
            for (db in DB_MIN..DB_MAX step 10) {
                drawLine(linea, P(IZQ, yDe(db)), P(W - DER, yDe(db)), strokeWidth = (if (db == 0) 1.4f else 0.7f) * e)
                texto(medidor, "$db", P(IZQ - 5, yDe(db) - 6), 9f * e, suave, derecha = true)
            }
            FRECUENCIAS_AEREA.forEach { f ->
                drawLine(linea, P(xDe(f), ARR), P(xDe(f), H - ABA), strokeWidth = 0.7f * e)
                texto(medidor, etiquetaHz(f), P(xDe(f), ARR - 19), 9.5f * e, suave, centro = true)
            }
            texto(medidor, "dB HL · frecuencia en Hz", P(IZQ, H - 16), 8.5f * e, suave)
            // Trazos.
            fun tramos(c: Curva, fs: List<Int>): List<List<Offset>> {
                val out = mutableListOf<List<Offset>>(); var actual = mutableListOf<Offset>()
                fs.filter { c[it] != null }.forEach { f ->
                    val u = c.getValue(f)
                    if (u.nr) { if (actual.size > 1) out += actual; actual = mutableListOf(); return@forEach }
                    actual += P(xDe(f), yDe(u.db))
                }
                if (actual.size > 1) out += actual
                return out
            }
            tramos(valor.aerea, FRECUENCIAS_AEREA).forEach { t -> polilinea(t, color, 1.8f * e, null) }
            val brecha = brechaAereoOsea(valor)
            if (brecha != null && brecha >= 10) tramos(valor.osea, FRECUENCIAS_OSEA).forEach { t ->
                polilinea(t, color, 1.4f * e, PathEffect.dashPathEffect(floatArrayOf(4f * e, 3f * e)))
            }
            val s = if (oido == Oido.OD) -1f else 1f
            fun flecha(cx: Float, cy: Float) {
                drawLine(color, P(cx, cy + 6), P(cx + s * 7, cy + 16), strokeWidth = 1.6f * e)
                drawLine(color, P(cx + s * 7, cy + 16), P(cx + s * 7 - s * 5, cy + 16), strokeWidth = 1.6f * e)
                drawLine(color, P(cx + s * 7, cy + 16), P(cx + s * 7, cy + 11), strokeWidth = 1.6f * e)
            }
            valor.osea.forEach { (f, u) ->
                if (f !in FRECUENCIAS_OSEA) return@forEach
                val cx = xDe(f); val cy = yDe(u.db)
                val p = Path().apply {
                    if (oido == Oido.OD) { moveTo((cx - 4) * e, (cy - 6) * e); lineTo((cx - 10) * e, cy * e); lineTo((cx - 4) * e, (cy + 6) * e) }
                    else { moveTo((cx + 4) * e, (cy - 6) * e); lineTo((cx + 10) * e, cy * e); lineTo((cx + 4) * e, (cy + 6) * e) }
                }
                drawPath(p, color, style = Stroke(2f * e))
                if (u.nr) flecha(cx, cy)
            }
            valor.aerea.forEach { (f, u) ->
                if (f !in FRECUENCIAS_AEREA) return@forEach
                val cx = xDe(f); val cy = yDe(u.db)
                if (oido == Oido.OD) {
                    drawCircle(Color.White, 5.5f * e, P(cx, cy))
                    drawCircle(color, 5.5f * e, P(cx, cy), style = Stroke(2f * e))
                } else {
                    drawLine(color, P(cx - 5, cy - 5), P(cx + 5, cy + 5), strokeWidth = 2.2f * e)
                    drawLine(color, P(cx + 5, cy - 5), P(cx - 5, cy + 5), strokeWidth = 2.2f * e)
                }
                if (u.nr) flecha(cx, cy)
            }
        }
    }
}

private fun DrawScope.polilinea(pts: List<Offset>, color: Color, ancho: Float, efecto: PathEffect?) {
    val p = Path().apply { moveTo(pts[0].x, pts[0].y); pts.drop(1).forEach { lineTo(it.x, it.y) } }
    drawPath(p, color, style = Stroke(ancho, pathEffect = efecto))
}

private fun DrawScope.texto(
    medidor: androidx.compose.ui.text.TextMeasurer, t: String, en: Offset, tamPx: Float, color: Color,
    derecha: Boolean = false, centro: Boolean = false,
) {
    val estilo = TextStyle(fontSize = (tamPx / density).sp, color = color)
    val r = medidor.measure(t, estilo)
    val x = when { derecha -> en.x - r.size.width; centro -> en.x - r.size.width / 2f; else -> en.x }
    drawText(r, topLeft = Offset(x, en.y))
}
