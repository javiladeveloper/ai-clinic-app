package pe.saniape.app.ui.clinica.historia

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import pe.saniape.app.data.staff.PuntoSerieHc
import pe.saniape.app.data.staff.numeroHc
import pe.saniape.app.ui.theme.Sania

/**
 * Línea simple de una serie numérica en el tiempo (peso, IMC, perímetro…), sin
 * librerías: Canvas con el mínimo y el máximo como rejilla, un punto por toma y,
 * debajo, la lista en texto (el gráfico nunca es lo único que dice algo).
 * Gemelo en espíritu de `psico/GraficoPerfilPsico.kt` y `fisio/CurvaDolor`.
 *
 * Opcional (PA del embarazo, como `CurvaEmbarazo` de la web): una segunda serie
 * en el mismo eje, líneas de umbral discontinuas en rojo con su etiqueta y los
 * puntos de alerta rellenos en rojo con halo, más una leyenda.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun GraficoLineaHc(
    titulo: String,
    unidad: String,
    puntos: List<PuntoSerieHc>,
    color: Color = Sania.colors.teal,
    /**
     * Eje de TIEMPO real: la distancia entre puntos es proporcional a los días
     * entre tomas (tricoscopía). Por defecto, equiespaciados como siempre.
     */
    ejeTiempo: Boolean = false,
    /**
     * Eje X NUMÉRICO (p. ej. semanas de edad gestacional): un valor por punto y
     * el rango del eje. null = como siempre (orden o tiempo).
     */
    ejeX: EjeXGrafico? = null,
    /** Banda de referencia (x, mínimo, máximo) en el eje de [ejeX] (AU esperada, IOM…). */
    banda: List<BandaGrafico> = emptyList(),
    /** Texto de cada punto en la lista de abajo; null = "fecha: valor unidad". */
    textoPunto: ((PuntoSerieHc, Int) -> String)? = null,
    /** Nombre de la serie principal en la leyenda (solo se muestra con [serie2]). */
    nombre: String? = null,
    /** Segunda serie en el MISMO eje X: su punto i va en la x del punto i de [puntos] (diastólica junto a la sistólica). */
    serie2: SerieGrafico? = null,
    /** Líneas horizontales de umbral (PA 140/90…): discontinuas en rojo, con su etiqueta a la derecha. */
    lineas: List<LineaRefGrafico> = emptyList(),
    /** ¿Se resalta el punto i de la serie (0 = principal, 1 = [serie2])? Relleno rojo con halo. */
    alerta: ((serie: Int, i: Int) -> Boolean)? = null,
    /** Texto de la leyenda para los puntos de [alerta] (p. ej. "≥ 140/90"). */
    leyendaAlerta: String? = null,
    /** Línea de aviso bajo el título (en rojo), p. ej. "⚠ 2 tomas ≥ 140/90". */
    aviso: String? = null,
    /** Descripción para TalkBack; null = la lista de puntos. */
    descripcion: String? = null,
    alto: Dp = 96.dp,
) {
    if (puntos.isEmpty()) return
    val c = Sania.colors
    val rejilla = c.borde
    val fondo = c.superficie
    val colorBanda = c.ok
    val rojo = c.error
    val bandaX = if (ejeX != null) banda else emptyList()
    val valores2 = serie2?.puntos.orEmpty().map { it.valor }
    val min = remember(puntos, bandaX, serie2, lineas) {
        (puntos.map { it.valor } + valores2 + bandaX.map { it.min } + lineas.map { it.y }).min()
    }
    val max = remember(puntos, bandaX, serie2, lineas) {
        (puntos.map { it.valor } + valores2 + bandaX.map { it.max } + lineas.map { it.y }).max()
    }
    val rango = (max - min).takeIf { it > 0.0001 } ?: 1.0
    val fracciones = remember(puntos, ejeTiempo) {
        if (ejeTiempo) pe.saniape.app.data.staff.fraccionesTiempo(puntos.map { it.fecha }) else null
    }
    val textoTalkBack = descripcion ?: ("$titulo: " + puntos.joinToString("; ") { "${it.fecha} ${numeroHc(it.valor)} $unidad" })
    val medidor = rememberTextMeasurer()
    val estiloLinea = TextStyle(fontSize = 8.sp, color = rojo, fontWeight = FontWeight.Bold)

    Column(Modifier.fillMaxWidth().semantics { contentDescription = textoTalkBack }) {
        Text(titulo, color = c.texto, fontSize = 12.sp, modifier = Modifier.padding(bottom = if (aviso != null) 0.dp else 4.dp))
        aviso?.let { Text(it, color = rojo, fontSize = 11.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(bottom = 4.dp)) }
        Row(Modifier.fillMaxWidth()) {
            Column(Modifier.height(alto).width(40.dp), verticalArrangement = Arrangement.SpaceBetween) {
                Text(numeroHc(max), color = c.textoSuave, fontSize = 8.sp)
                Text(numeroHc(min), color = c.textoSuave, fontSize = 8.sp)
            }
            Canvas(Modifier.weight(1f).height(alto)) {
                val padY = 6.dp.toPx()
                val padX = 8.dp.toPx()
                val iw = size.width - padX * 2
                val ih = size.height - padY * 2
                val anchoX = ejeX?.let { (it.hasta - it.desde).takeIf { a -> a > 0 } ?: 1.0 }
                fun xNum(v: Double) = padX + (((v - ejeX!!.desde) / anchoX!!).toFloat()).coerceIn(0f, 1f) * iw
                fun x(i: Int) = if (ejeX != null) xNum(ejeX.valores.getOrElse(i) { ejeX.desde }) else padX + when {
                    puntos.size == 1 -> iw / 2
                    fracciones != null -> fracciones[i].toFloat() * iw
                    else -> i * iw / (puntos.size - 1)
                }
                fun y(v: Double) = padY + ih - (((v - min) / rango).toFloat()) * ih
                if (ejeX != null && bandaX.size > 1) {
                    val b = Path()
                    bandaX.forEachIndexed { i, p -> if (i == 0) b.moveTo(xNum(p.x), y(p.max)) else b.lineTo(xNum(p.x), y(p.max)) }
                    bandaX.reversed().forEach { p -> b.lineTo(xNum(p.x), y(p.min)) }
                    b.close()
                    drawPath(b, colorBanda.copy(alpha = 0.14f))
                    drawPath(b, colorBanda.copy(alpha = 0.5f), style = Stroke(width = 0.8.dp.toPx()))
                }
                drawLine(rejilla, Offset(0f, y(max)), Offset(size.width, y(max)), strokeWidth = 1.dp.toPx())
                drawLine(rejilla, Offset(0f, y(min)), Offset(size.width, y(min)), strokeWidth = 1.dp.toPx())
                lineas.forEach { l ->
                    val yy = y(l.y)
                    drawLine(rojo, Offset(0f, yy), Offset(size.width, yy), strokeWidth = 1.dp.toPx(),
                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(5.dp.toPx(), 3.dp.toPx())))
                    val t = medidor.measure(l.etiqueta, estiloLinea)
                    // Encima de la línea; si no entra arriba (es el máximo), debajo.
                    val arriba = yy - t.size.height - 1.dp.toPx()
                    drawText(t, topLeft = Offset(size.width - t.size.width - 2.dp.toPx(), if (arriba >= 0f) arriba else yy + 1.dp.toPx()))
                }
                fun trazar(serie: Int, valores: List<Double>, col: Color) {
                    if (valores.size > 1) {
                        val p = Path()
                        valores.forEachIndexed { i, v -> if (i == 0) p.moveTo(x(i), y(v)) else p.lineTo(x(i), y(v)) }
                        drawPath(p, col, style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
                    }
                    valores.forEachIndexed { i, v ->
                        val o = Offset(x(i), y(v))
                        if (alerta?.invoke(serie, i) == true) {
                            drawCircle(rojo.copy(alpha = 0.22f), 8.dp.toPx(), o)
                            drawCircle(rojo, 5.dp.toPx(), o)
                            drawCircle(fondo, 5.dp.toPx(), o, style = Stroke(1.5.dp.toPx()))
                        } else {
                            drawCircle(fondo, 4.dp.toPx(), o)
                            drawCircle(col, 4.dp.toPx(), o, style = Stroke(2.dp.toPx()))
                        }
                    }
                }
                serie2?.let { s2 -> trazar(1, s2.puntos.take(puntos.size).map { it.valor }, s2.color) }
                trazar(0, puntos.map { it.valor }, color)
            }
        }
        ejeX?.let { e ->
            Row(Modifier.fillMaxWidth().padding(start = 40.dp)) {
                Text(numeroHc(e.desde), color = c.textoSuave, fontSize = 8.sp, modifier = Modifier.weight(1f))
                Text("${numeroHc(e.hasta)} ${e.etiqueta.orEmpty()}".trim(), color = c.textoSuave, fontSize = 8.sp)
            }
        }
        if (serie2 != null || (alerta != null && leyendaAlerta != null)) {
            FlowRow(
                Modifier.fillMaxWidth().padding(start = 40.dp, top = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                if (serie2 != null) {
                    ItemLeyendaHc(nombre ?: titulo, color)
                    ItemLeyendaHc(serie2.nombre, serie2.color)
                }
                if (lineas.isNotEmpty()) ItemLeyendaHc("Umbral ${lineas.joinToString("/") { it.etiqueta }}", rojo, discontinua = true)
                if (alerta != null && leyendaAlerta != null) ItemLeyendaHc(leyendaAlerta, rojo, alerta = true)
            }
        }
        Text(
            puntos.withIndex().joinToString(" · ") { (i, p) ->
                textoPunto?.invoke(p, i) ?: "${fechaHc(p.fecha)}: ${numeroHc(p.valor)} $unidad".trim()
            },
            color = c.textoSuave, fontSize = 11.sp, modifier = Modifier.padding(top = 4.dp),
        )
    }
}

/** Una entrada de la leyenda: punto (serie), punto con halo (alerta) o trazo discontinuo (umbral). */
@Composable
private fun ItemLeyendaHc(texto: String, color: Color, discontinua: Boolean = false, alerta: Boolean = false) {
    val c = Sania.colors
    Row(verticalAlignment = Alignment.CenterVertically) {
        when {
            discontinua -> Box(Modifier.size(width = 14.dp, height = 8.dp).drawBehind {
                drawLine(color, Offset(0f, size.height / 2), Offset(size.width, size.height / 2), strokeWidth = 1.5.dp.toPx(),
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(3.dp.toPx(), 2.dp.toPx())))
            })
            alerta -> Box(Modifier.size(10.dp).drawBehind {
                drawCircle(color.copy(alpha = 0.22f), size.minDimension / 2)
                drawCircle(color, size.minDimension / 3.2f)
            })
            else -> Box(Modifier.size(8.dp).clip(CircleShape).drawBehind { drawCircle(color) })
        }
        Text(texto, color = c.textoSuave, fontSize = 10.sp, modifier = Modifier.padding(start = 4.dp))
    }
}

/** Segunda serie de [GraficoLineaHc] (mismo eje X que la principal, punto a punto). */
internal data class SerieGrafico(val nombre: String, val puntos: List<PuntoSerieHc>, val color: Color)

/** Línea horizontal de umbral de [GraficoLineaHc] (en las unidades del eje Y). */
internal data class LineaRefGrafico(val y: Double, val etiqueta: String)

/** Eje X numérico de [GraficoLineaHc]: un valor por punto y el rango del eje. */
internal data class EjeXGrafico(val valores: List<Double>, val desde: Double, val hasta: Double, val etiqueta: String? = null)

/** Un tramo de la banda de referencia de [GraficoLineaHc]. */
internal data class BandaGrafico(val x: Double, val min: Double, val max: Double)

/** "2026-10-08" → "08/10/2026"; otra cosa se deja como vino. */
internal fun fechaHc(iso: String?): String {
    if (iso.isNullOrBlank()) return "—"
    val f = iso.take(10)
    val p = f.split("-")
    return if (p.size == 3 && p[0].length == 4) "${p[2]}/${p[1]}/${p[0]}" else iso
}
