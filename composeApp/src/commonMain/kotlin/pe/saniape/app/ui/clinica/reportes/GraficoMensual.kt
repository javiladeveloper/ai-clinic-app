package pe.saniape.app.ui.clinica.reportes

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import pe.saniape.app.data.staff.HitoMes
import pe.saniape.app.data.staff.PuntoMes
import pe.saniape.app.data.staff.entero
import pe.saniape.app.data.staff.indiceTocado
import pe.saniape.app.data.staff.marcasEje
import pe.saniape.app.data.staff.mesConAnio
import pe.saniape.app.data.staff.muestraEtiqueta
import pe.saniape.app.data.staff.pasoEtiquetas
import pe.saniape.app.data.staff.solesGrafico
import pe.saniape.app.ui.theme.Sania

/**
 * Paleta de los gráficos: la MISMA de la web (lib/grafico-tema.ts), validada
 * para daltonismo en los dos temas. Ingresos en aqua y egresos en naranja (no
 * verde/rojo: en deuteranopía se ven iguales).
 */
internal data class PaletaGrafico(
    val serie1: Color, val serie2: Color, val serie3: Color, val serie4: Color,
    val grilla: Color, val eje: Color, val parcial: Color,
) {
    fun serie(i: Int): Color = when (i) { 2 -> serie2; 3 -> serie3; 4 -> serie4; else -> serie1 }
}

private val PALETA_CLARA = PaletaGrafico(
    serie1 = Color(0xFF3B5BBF), serie2 = Color(0xFF1BAF7A), serie3 = Color(0xFFEB6834), serie4 = Color(0xFF8B5CF6),
    grilla = Color(0xFFDDE1F0), eje = Color(0xFF6B7280), parcial = Color(0xFF9CA3AF),
)
private val PALETA_OSCURA = PaletaGrafico(
    serie1 = Color(0xFF4D82E8), serie2 = Color(0xFF17AB88), serie3 = Color(0xFFDD6636), serie4 = Color(0xFF8F6FE0),
    grilla = Color(0x14FFFFFF), eje = Color(0xFF9CA3AF), parcial = Color(0xFF4B5563),
)

/** La paleta del tema activo (el tema lo decide Más → Apariencia o el sistema). */
@Composable
internal fun paletaGrafico(): PaletaGrafico =
    if (Sania.colors.superficie.luminance() < 0.5f) PALETA_OSCURA else PALETA_CLARA

enum class FormaGrafico { BARRAS, LINEA }

/**
 * Gráfico de una serie mensual (gemelo de GraficoMensual.tsx).
 *
 * Lo que lo distingue de un gráfico cualquiera: **el mes en curso no miente**.
 * Va en gris (todavía va a crecer) con la proyección punteada encima, y al
 * tocarlo el detalle dice cuántos días van y a cuánto va camino de cerrar.
 * Los meses con algo anotado (promoción, feriado) llevan 🏷.
 *
 * Tocar una barra (o un punto) muestra sus valores debajo; tocarla otra vez
 * lo cierra.
 */
@Composable
fun GraficoMensual(
    datos: List<PuntoMes>,
    forma: FormaGrafico = FormaGrafico.BARRAS,
    serie: Int = 1,
    dinero: Boolean = false,
    hitos: List<HitoMes> = emptyList(),
    unidad: String = "",
    altura: Dp = 190.dp,
) {
    val c = Sania.colors
    val p = paletaGrafico()
    val color = p.serie(serie)
    val medidor = rememberTextMeasurer()
    val densidad = LocalDensity.current
    var sel by remember(datos) { mutableStateOf<Int?>(null) }

    if (datos.isEmpty()) return
    val hitosPorMes = remember(hitos) { hitos.associateBy { it.mes } }
    val fmtEje: (Double) -> String = { v -> if (dinero) solesGrafico(v, compacto = true) else entero(v) }
    val maximo = datos.maxOf { maxOf(it.valor, it.proyectado ?: 0.0) }
    val marcas = remember(maximo) { marcasEje(maximo) }
    val tope = marcas.last().takeIf { it > 0 } ?: 1.0

    val estiloEje = TextStyle(color = p.eje, fontSize = 10.sp)
    val estiloEjeSel = TextStyle(color = c.texto, fontSize = 10.sp, fontWeight = FontWeight.Bold)
    // Ancho del eje Y = la etiqueta más ancha (así "S/ 12.5k" no se corta a 360 dp).
    val anchoEje = remember(marcas, dinero) {
        marcas.maxOf { medidor.measure(fmtEje(it), estiloEje).size.width }.toFloat() + with(densidad) { 6.dp.toPx() }
    }
    val anchoEtiquetaX = remember { medidor.measure("Dic 25", estiloEje).size.width.toFloat() + with(densidad) { 4.dp.toPx() } }

    val ultimo = datos.last()
    val resumen = "Gráfico mensual de $unidad: ${datos.size} meses. " +
        "${ultimo.etiqueta}: ${if (dinero) solesGrafico(ultimo.valor) else entero(ultimo.valor)}" +
        (if (ultimo.parcial) " (mes en curso)" else "")

    Column(Modifier.fillMaxWidth()) {
        Canvas(
            Modifier.fillMaxWidth().height(altura)
                .semantics { contentDescription = resumen }
                .pointerInput(datos, anchoEje) {
                    detectTapGestures { o ->
                        val i = indiceTocado(o.x, anchoEje, size.width - anchoEje, datos.size)
                        sel = if (i == null || i == sel) null else i
                    }
                },
        ) {
            val arriba = 16.dp.toPx()
            val abajo = size.height - 18.dp.toPx()
            val izq = anchoEje
            val ancho = size.width - izq
            val alto = abajo - arriba
            val n = datos.size
            val slot = ancho / n
            fun y(v: Double): Float = abajo - (v / tope * alto).toFloat().coerceIn(0f, alto)

            // Grilla y eje: recesivos, la serie manda.
            val guion = PathEffect.dashPathEffect(floatArrayOf(6f, 6f))
            marcas.forEach { m ->
                val yy = y(m)
                drawLine(p.grilla, Offset(izq, yy), Offset(size.width, yy), strokeWidth = 1.dp.toPx(), pathEffect = guion)
                val t = medidor.measure(fmtEje(m), estiloEje)
                drawText(t, topLeft = Offset(izq - t.size.width - 4.dp.toPx(), yy - t.size.height / 2f))
            }

            // Columna tocada: un fondo suave detrás.
            sel?.let { i ->
                drawRect(p.grilla.copy(alpha = 0.55f), topLeft = Offset(izq + slot * i, arriba), size = Size(slot, alto))
            }

            val atenuar = { i: Int -> if (sel != null && sel != i) 0.45f else 1f }
            if (forma == FormaGrafico.BARRAS) {
                val anchoBarra = minOf(slot * 0.64f, 28.dp.toPx())
                val r = 4.dp.toPx()
                datos.forEachIndexed { i, d ->
                    val x = izq + slot * i + (slot - anchoBarra) / 2f
                    val col = (if (d.parcial) p.parcial else color).copy(alpha = atenuar(i))
                    val yv = y(d.valor)
                    val h = abajo - yv
                    if (h > 0f) {
                        drawRoundRect(col, Offset(x, yv), Size(anchoBarra, h), CornerRadius(minOf(r, h / 2f)))
                        // Abajo recta: solo se redondea la punta.
                        if (h > r) drawRect(col, Offset(x, abajo - r), Size(anchoBarra, r))
                    }
                    // Mes en curso: a dónde va camino de cerrar, punteado (no es un dato, es una proyección).
                    val proy = d.proyectado
                    if (d.parcial && proy != null && proy > d.valor) {
                        val yp = y(proy)
                        drawRoundRect(
                            p.parcial.copy(alpha = atenuar(i)), Offset(x, yp), Size(anchoBarra, yv - yp),
                            CornerRadius(r), style = Stroke(width = 1.5.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(5f, 5f))),
                        )
                    }
                }
            } else {
                val puntos = datos.mapIndexed { i, d -> Offset(izq + slot * i + slot / 2f, y(d.valor)) }
                val trazo = 2.dp.toPx()
                for (i in 1 until puntos.size) {
                    // El tramo que llega al mes en curso va punteado: ese punto todavía se mueve.
                    val parcial = datos[i].parcial
                    drawLine(
                        if (parcial) p.parcial else color, puntos[i - 1], puntos[i], strokeWidth = trazo,
                        pathEffect = if (parcial) PathEffect.dashPathEffect(floatArrayOf(8f, 6f)) else null,
                    )
                }
                // Relleno muy suave bajo la línea (solo los meses cerrados).
                val cerrados = puntos.filterIndexed { i, _ -> !datos[i].parcial }
                if (cerrados.size >= 2) {
                    val area = Path().apply {
                        moveTo(cerrados.first().x, abajo)
                        cerrados.forEach { lineTo(it.x, it.y) }
                        lineTo(cerrados.last().x, abajo)
                        close()
                    }
                    drawPath(area, color.copy(alpha = 0.10f))
                }
                puntos.forEachIndexed { i, o ->
                    val col = if (datos[i].parcial) p.parcial else color
                    val radio = if (sel == i) 6.dp.toPx() else 3.5.dp.toPx()
                    if (sel == i) drawCircle(c.superficie, radio + 2.dp.toPx(), o)
                    drawCircle(col, radio, o)
                }
            }

            // Hitos: una marca sobre el mes anotado.
            datos.forEachIndexed { i, d ->
                if (hitosPorMes.containsKey(d.mes)) {
                    val t = medidor.measure("🏷", TextStyle(fontSize = 10.sp))
                    drawText(t, topLeft = Offset(izq + slot * i + (slot - t.size.width) / 2f, 0f))
                }
            }

            // Eje X: si con 24 meses no caben todas las etiquetas, una de cada N
            // (siempre la del mes en curso).
            val paso = pasoEtiquetas(n, ancho, anchoEtiquetaX)
            datos.forEachIndexed { i, d ->
                if (muestraEtiqueta(i, n, paso) || sel == i) {
                    val t = medidor.measure(d.etiqueta, if (sel == i) estiloEjeSel else estiloEje)
                    val cx = izq + slot * i + slot / 2f
                    val xx = (cx - t.size.width / 2f).coerceIn(izq - 4.dp.toPx(), size.width - t.size.width)
                    drawText(t, topLeft = Offset(xx, abajo + 4.dp.toPx()))
                }
            }
        }

        val i = sel
        if (i != null && i in datos.indices) {
            DetalleMes(datos[i], dinero, unidad, hitosPorMes[datos[i].mes])
        } else {
            Text(
                if (forma == FormaGrafico.LINEA) "Toca un punto para ver su detalle." else "Toca una barra para ver su detalle.",
                color = c.textoSuave, fontSize = Sania.txt.mini, modifier = Modifier.padding(top = 4.dp),
            )
        }
    }
}

/** Lo que muestra el tooltip de la web, pero fijo debajo del gráfico (legible con el dedo encima). */
@Composable
private fun DetalleMes(d: PuntoMes, dinero: Boolean, unidad: String, hito: HitoMes?) {
    val c = Sania.colors
    val fmt: (Double) -> String = { v -> if (dinero) solesGrafico(v) else entero(v) }
    Column(
        Modifier.fillMaxWidth().padding(top = 6.dp).clip(RoundedCornerShape(Sania.shape.sm.dp))
            .background(c.chipBg).padding(horizontal = 12.dp, vertical = 8.dp),
    ) {
        Text(mesConAnio(d.mes), color = c.texto, fontSize = Sania.txt.pequeno, fontWeight = FontWeight.Bold)
        Text(
            "${fmt(d.valor)}${if (unidad.isNotBlank() && !dinero) " $unidad" else ""}",
            color = c.texto, fontSize = Sania.txt.cuerpo,
        )
        if (d.parcial) {
            Spacer(Modifier.height(2.dp))
            Text(
                "Mes en curso · ${d.diasTranscurridos ?: "?"} de ${d.diasDelMes ?: "?"} días",
                color = c.textoSuave, fontSize = Sania.txt.mini,
            )
            d.proyectado?.let { Text("Va camino de ${fmt(it)}", color = c.texto, fontSize = Sania.txt.mini) }
            d.mismoTramoAnterior?.let {
                Text("A estas alturas del mes pasado: ${fmt(it)}", color = c.textoSuave, fontSize = Sania.txt.mini)
            }
        }
        if (hito != null) {
            Spacer(Modifier.height(2.dp))
            Text("🏷 ${hito.titulo}", color = c.texto, fontSize = Sania.txt.mini, fontWeight = FontWeight.Bold)
            hito.detalle?.let { Text(it, color = c.textoSuave, fontSize = Sania.txt.mini) }
        }
    }
}
