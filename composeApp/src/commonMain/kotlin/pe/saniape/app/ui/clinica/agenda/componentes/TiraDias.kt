package pe.saniape.app.ui.clinica.agenda.componentes

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.plus
import pe.saniape.app.ui.clinica.agenda.iso
import pe.saniape.app.ui.theme.Sania

/** Un día de la tira. */
data class DiaTira(val iso: String, val diaSemana: String, val diaMes: String)

private val DIAS_ES = listOf("Lun", "Mar", "Mié", "Jue", "Vie", "Sáb", "Dom")

/** Tira horizontal de días (desde -2 hasta +12, como la web). */
@Composable
fun TiraDias(hoy: String, seleccionado: String, onSeleccionar: (String) -> Unit) {
    val c = Sania.colors
    val dias = remember(hoy) { construirDias(hoy, offset = -2, n = 15) }
    LazyRow(
        Modifier.fillMaxWidth().padding(vertical = Sania.dim.sm),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        contentPadding = PaddingValues(horizontal = Sania.dim.lg),
    ) {
        items(dias) { dia ->
            val activo = dia.iso == seleccionado
            Column(
                Modifier.size(width = 52.dp, height = 64.dp)
                    .clip(RoundedCornerShape(Sania.shape.md.dp))
                    .background(if (activo) c.navy else c.superficie)
                    .border(1.dp, if (activo) c.navy else c.borde, RoundedCornerShape(Sania.shape.md.dp))
                    .clickable { onSeleccionar(dia.iso) }
                    .padding(vertical = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Text(dia.diaSemana, color = if (activo) c.sobreNavy else c.textoSuave, fontSize = 11.sp)
                Text(dia.diaMes, color = if (activo) c.sobreNavy else c.texto,
                    fontSize = 18.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

private fun construirDias(isoInicio: String, offset: Int, n: Int): List<DiaTira> {
    val p = isoInicio.split("-")
    var fecha = LocalDate(p[0].toInt(), p[1].toInt(), p[2].toInt()).plus(DatePeriod(days = offset))
    val lista = mutableListOf<DiaTira>()
    repeat(n) {
        lista.add(DiaTira(fecha.iso(), DIAS_ES[fecha.dayOfWeek.ordinal], fecha.dayOfMonth.toString()))
        fecha = fecha.plus(DatePeriod(days = 1))
    }
    return lista
}