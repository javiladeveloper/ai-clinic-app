package pe.saniape.app.ui.clinica.psico

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import pe.saniape.app.data.staff.PerfilTestPsico
import pe.saniape.app.data.staff.colorBarraPsico
import pe.saniape.app.data.staff.geometriaPerfil
import pe.saniape.app.data.staff.leyendaPerfil
import pe.saniape.app.data.staff.numeroPsico
import pe.saniape.app.ui.theme.Sania

private val ANCHO_ETIQUETA = 112.dp
private val ALTO_FILA = 28.dp

/**
 * Perfil de puntajes de un test (GraficoPerfil de la web, sin librerías): una
 * barra horizontal por escala, con las bandas de corte de fondo y la línea
 * punteada de la media cuando la hay. Sin eje común (PSC-17, AUDIT) cada barra
 * va en % de su máximo y su color dice la banda. Debajo, la lista en texto (la
 * vista equivalente: el color nunca es lo único que dice algo) y la leyenda.
 *
 * Cada fila dibuja su tramo de fondo, rejilla y media: apiladas sin espacio, las
 * bandas y las líneas se ven continuas.
 */
@Composable
internal fun GraficoPerfilPsico(perfil: PerfilTestPsico, modifier: Modifier = Modifier) {
    val c = Sania.colors
    val g = remember(perfil) { geometriaPerfil(perfil) }
    val medidor = rememberTextMeasurer()
    val rejilla = c.borde
    val textoEje = c.textoSuave
    val trazoMedia = c.texto
    val descripcion = remember(perfil) {
        "Perfil de ${perfil.titulo}: " + perfil.barras.joinToString("; ") { "${it.escala} ${it.texto}${if (it.categoria.isNotBlank()) ", ${it.categoria}" else ""}" }
    }

    Column(modifier.fillMaxWidth().semantics { contentDescription = descripcion }) {
        perfil.media?.let { m ->
            Row(Modifier.fillMaxWidth()) {
                Column(Modifier.width(ANCHO_ETIQUETA)) {}
                Text("media ${numeroPsico(m)}", color = textoEje, fontSize = 10.sp,
                    modifier = Modifier.padding(start = 2.dp, bottom = 2.dp))
            }
        }
        g.filas.forEach { f ->
            Row(Modifier.fillMaxWidth().height(ALTO_FILA), verticalAlignment = Alignment.CenterVertically) {
                Text(f.etiqueta, color = c.texto, fontSize = 11.sp, maxLines = 2, overflow = TextOverflow.Ellipsis, lineHeight = 12.sp,
                    modifier = Modifier.width(ANCHO_ETIQUETA).padding(end = 6.dp))
                Canvas(Modifier.weight(1f).height(ALTO_FILA)) {
                    val w = size.width
                    val h = size.height
                    g.bandas.forEach { b ->
                        drawRect(Color(colorBarraPsico(b.nivel)).copy(alpha = 0.14f), topLeft = Offset(b.desde * w, 0f),
                            size = Size(((b.hasta - b.desde) * w).coerceAtLeast(0f), h))
                    }
                    g.ticks.forEach { t -> drawLine(rejilla, Offset(t.f * w, 0f), Offset(t.f * w, h), strokeWidth = 1f) }
                    val alto = h * 0.56f
                    val ancho = ((f.hasta - f.desde) * w).coerceAtLeast(2.dp.toPx())
                    drawRoundRect(Color(colorBarraPsico(f.nivel)), topLeft = Offset(f.desde * w, (h - alto) / 2), size = Size(ancho, alto),
                        cornerRadius = CornerRadius(3.dp.toPx()))
                    g.media?.let { m ->
                        drawLine(trazoMedia, Offset(m * w, 0f), Offset(m * w, h), strokeWidth = 1.5.dp.toPx(),
                            pathEffect = PathEffect.dashPathEffect(floatArrayOf(4.dp.toPx(), 3.dp.toPx())))
                    }
                }
            }
        }
        // Eje: las marcas, en el mismo ancho que las barras.
        Row(Modifier.fillMaxWidth()) {
            Column(Modifier.width(ANCHO_ETIQUETA)) {}
            Canvas(Modifier.weight(1f).height(16.dp)) {
                val estilo = TextStyle(color = textoEje, fontSize = 9.sp)
                g.ticks.forEach { t ->
                    val txt = medidor.measure(t.texto, estilo)
                    val x = (t.f * size.width - txt.size.width / 2f).coerceIn(0f, (size.width - txt.size.width).coerceAtLeast(0f))
                    drawText(txt, topLeft = Offset(x, 2f))
                }
            }
        }
        Column(Modifier.padding(top = 6.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            perfil.barras.forEach { b ->
                Text(
                    "${b.escala}: ${b.texto}${if (b.categoria.isNotBlank()) " · ${b.categoria}" else ""}",
                    color = c.texto, fontSize = 12.sp,
                )
            }
            Text(leyendaPerfil(perfil), color = c.textoSuave, fontSize = 11.sp, modifier = Modifier.padding(top = 2.dp))
        }
    }
}

/** Título de un perfil del anexo ("PHQ-9 · 01/10/2026"). */
@Composable
internal fun TituloPerfilPsico(texto: String) {
    Text(texto, color = Sania.colors.texto, fontSize = 13.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(bottom = 4.dp))
}
