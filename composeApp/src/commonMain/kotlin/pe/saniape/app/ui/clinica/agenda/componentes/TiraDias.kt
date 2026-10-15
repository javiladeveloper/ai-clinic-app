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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.withFrameNanos
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

/**
 * Tira horizontal de días (desde -2 hasta +12, como la web). Tocar un día de la
 * tira se comporta como siempre: la tira no se rearma ni se desplaza. Solo si
 * se salta a un día FUERA de ella (calendario del mes, "Hoy", aviso de cita) se
 * arma alrededor de ese día, y se desplaza solo si el día no quedó a la vista.
 */
@Composable
fun TiraDias(hoy: String, seleccionado: String, onSeleccionar: (String) -> Unit) {
    val c = Sania.colors
    // La base vive mientras viva la tira; se mueve SOLO si el elegido queda fuera.
    val baseGuardada = remember(hoy) { arrayOf(hoy) }
    val base = pe.saniape.app.data.staff.baseTiraDias(baseGuardada[0], seleccionado).also { baseGuardada[0] = it }
    val dias = remember(base) { construirDias(base, offset = -2, n = 15) }
    val estado = rememberLazyListState()
    LaunchedEffect(base, seleccionado) {
        val i = dias.indexOfFirst { it.iso == seleccionado }
        if (i < 0) return@LaunchedEffect
        withFrameNanos { }   // que la tira (quizá recién rearmada) ya esté medida
        // Tocar un día de la tira (siempre a la vista, aunque sea en el borde) no desplaza.
        val aLaVista = estado.layoutInfo.visibleItemsInfo.any { it.index == i }
        if (!aLaVista) estado.animateScrollToItem((i - 2).coerceAtLeast(0))
    }
    LazyRow(
        Modifier.fillMaxWidth().padding(vertical = Sania.dim.sm),
        state = estado,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        contentPadding = PaddingValues(horizontal = Sania.dim.lg),
    ) {
        itemsIndexed(dias, key = { _, d -> d.iso }) { _, dia ->
            val activo = dia.iso == seleccionado
            Column(
                Modifier.size(width = 52.dp, height = 64.dp)
                    .clip(RoundedCornerShape(Sania.shape.md.dp))
                    .background(if (activo) c.navy else c.superficie)
                    .border(if (dia.iso == hoy && !activo) 2.dp else 1.dp,
                        if (activo) c.navy else if (dia.iso == hoy) c.lav else c.borde, RoundedCornerShape(Sania.shape.md.dp))
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