package pe.saniape.app.ui.clinica.capilar

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import pe.saniape.app.data.staff.CabezaSilueta
import pe.saniape.app.data.staff.FormaSilueta
import pe.saniape.app.data.staff.formasDeGrado

private val PELO = Color(0xFF3F3A52)
private val PIEL = Color(0xFFF1D3BF)
private val CONTORNO = Color(0xFF8892C8)

/**
 * El cuero cabelludo visto desde arriba (frente arriba) con las zonas sin pelo
 * o ralas del grado — gemelo de SiluetaCapilar.tsx, con las MISMAS formas
 * (`formasDeGrado`). Colores FIJOS (es una ilustración: se ve igual en el tema
 * oscuro). Sin grado o escala "otro": la cabeza con pelo y un "?".
 */
@Composable
fun SiluetaCapilar(escala: String?, grado: String?, tam: Dp = 56.dp, titulo: String? = null) {
    val formas = remember(escala, grado) { formasDeGrado(escala, grado) }
    val mod = if (titulo != null) Modifier.semantics { contentDescription = titulo } else Modifier
    Box(mod.size(width = tam, height = tam * 1.2f), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val k = size.width / 100f
            fun p(v: Double) = (v * k).toFloat()
            val c = CabezaSilueta
            val trazo = 1.2f * k
            // Nariz (marca la frente) y orejas.
            val nariz = Path().apply { moveTo(p(45.0), p(13.0)); lineTo(p(50.0), p(3.0)); lineTo(p(55.0), p(13.0)); close() }
            drawPath(nariz, PIEL)
            drawPath(nariz, CONTORNO, style = Stroke(trazo))
            for (cx in listOf(c.CX - c.RX, c.CX + c.RX)) {
                val tl = Offset(p(cx - 4), p(66.0 - 9))
                val sz = Size(p(8.0), p(18.0))
                drawOval(PIEL, tl, sz)
                drawOval(CONTORNO, tl, sz, style = Stroke(trazo))
            }
            val cabeza = Rect(p(c.CX - c.RX), p(c.CY - c.RY), p(c.CX + c.RX), p(c.CY + c.RY))
            drawOval(PELO, cabeza.topLeft, cabeza.size)
            clipPath(Path().apply { addOval(cabeza) }) {
                formas.orEmpty().forEach { f ->
                    val alfa = f.o.toFloat().coerceIn(0f, 1f)
                    when (f) {
                        is FormaSilueta.Elipse -> drawOval(
                            PIEL, Offset(p(f.cx - f.rx), p(f.cy - f.ry)), Size(p(f.rx * 2), p(f.ry * 2)), alpha = alfa,
                        )
                        is FormaSilueta.Rect -> drawRoundRect(
                            PIEL, Offset(p(f.x), p(f.y)), Size(p(f.w), p(f.h)),
                            cornerRadius = CornerRadius(p(minOf(f.w / 2, 3.0))), alpha = alfa,
                        )
                    }
                }
            }
            drawOval(CONTORNO, cabeza.topLeft, cabeza.size, style = Stroke(1.5f * k))
        }
        if (formas == null) {
            Text("?", color = Color.White.copy(alpha = 0.85f), fontWeight = FontWeight.Bold,
                fontSize = (tam.value * 0.28f).sp)
        }
    }
}
