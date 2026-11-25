package pe.saniape.app.ui.clinica.pacientes

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import pe.saniape.app.data.staff.AlertaEstetica
import pe.saniape.app.data.staff.TonoAlerta
import pe.saniape.app.ui.theme.Sania

/**
 * La franja de alertas de piel (fototipo, embarazo, retinoides…), como la de la
 * ficha. Gemelo de components/estetica/ChipsAlertas.tsx. Sin alertas no dibuja nada.
 */
@Composable
fun FranjaAlertasEstetica(alertas: List<AlertaEstetica>, modifier: Modifier = Modifier) {
    if (alertas.isEmpty()) return
    val c = Sania.colors
    Row(modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        alertas.forEach { a ->
            val (fg, bg) = when (a.tono) {
                TonoAlerta.RIESGO -> c.error to c.errorBg
                TonoAlerta.AVISO -> c.pend to c.pendBg
                TonoAlerta.INFO -> c.info to c.infoBg
            }
            Text("${a.icono} ${a.texto}", color = fg, fontSize = 12.sp, fontWeight = FontWeight.Bold,
                modifier = Modifier.clip(RoundedCornerShape(Sania.shape.pill.dp)).background(bg)
                    .padding(horizontal = 10.dp, vertical = 4.dp))
        }
    }
}
