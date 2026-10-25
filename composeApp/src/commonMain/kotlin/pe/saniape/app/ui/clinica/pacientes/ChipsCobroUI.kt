package pe.saniape.app.ui.clinica.pacientes

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import pe.saniape.app.data.staff.chipActivo
import pe.saniape.app.data.staff.chipsCobro
import pe.saniape.app.data.staff.formatearDinero
import pe.saniape.app.data.staff.textoMontoChip
import pe.saniape.app.ui.theme.Sania

/**
 * Chips «Todo / 50 % / Saldo» encima del monto de un cobro (data/staff/ChipsCobro):
 * un toque llena el campo, que sigue editable. Gemelo de components/ui/ChipsCobro
 * de la web. FlowRow (nunca LazyRow): baja de línea en pantallas angostas.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ChipsCobro(
    total: Double?,
    saldo: Double?,
    valor: String,
    onElegir: (String) -> Unit,
    moneda: String = pe.saniape.app.ui.monedaUI(),
    habilitado: Boolean = true,
    modifier: Modifier = Modifier,
) {
    val opciones = chipsCobro(total, saldo, moneda)
    if (opciones.isEmpty()) return
    val c = Sania.colors
    val activo = chipActivo(opciones, valor)
    FlowRow(modifier, horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        opciones.forEach { o ->
            val on = activo == o.chip
            Box(
                Modifier.clip(RoundedCornerShape(Sania.shape.pill.dp))
                    .background(if (on) c.teal else c.superficie)
                    .border(1.dp, c.teal, RoundedCornerShape(Sania.shape.pill.dp))
                    .clickable(enabled = habilitado) { onElegir(textoMontoChip(o.monto)) }
                    .padding(horizontal = 10.dp, vertical = 6.dp),
            ) {
                Text("${o.etiqueta} · ${formatearDinero(o.monto, moneda)}",
                    color = if (on) c.sobreNavy else c.teal, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}
