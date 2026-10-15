package pe.saniape.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import pe.saniape.app.data.EmbarazoPortal
import pe.saniape.app.data.MiEmbarazo
import pe.saniape.app.data.staff.NOMBRE_TRIMESTRE
import pe.saniape.app.data.staff.fechaEmb
import pe.saniape.app.data.staff.numEmb
import pe.saniape.app.ui.theme.Sania

/**
 * 🤰 Mi embarazo del portal de la paciente (gemelo de MiEmbarazo.tsx): la EG de
 * hoy, la FPP y cuánto falta, sus controles (fecha, EG, peso, PA, AU, LCF), el
 * próximo control y los signos de alarma. Las notas internas no llegan.
 *
 * Un ítem por embarazo + uno para los signos (nada de listas perezosas
 * anidadas). Sin embarazo en curso no agrega nada: el portal de una paciente de
 * otra especialidad no cambia. [hoy] recalcula la EG si lo pintado viene de la caché.
 */
internal fun LazyListScope.seccionEmbarazo(datos: MiEmbarazo, espacioArriba: Boolean, hoy: String) {
    if (!datos.tieneDatos) return
    item(key = "embarazo-titulo") {
        Column {
            if (espacioArriba) Spacer(Modifier.height(Sania.dim.sm))
            Text("🤰 MI EMBARAZO", color = Sania.colors.textoSuave, fontSize = Sania.txt.mini, fontWeight = FontWeight.Bold)
        }
    }
    datos.embarazos.forEach { e ->
        item(key = "embarazo-" + e.id) { TarjetaMiEmbarazo(e.vigente(hoy)) }
    }
    if (datos.signosAlarma.isNotEmpty()) {
        item(key = "embarazo-signos") { SignosAlarmaPortal(datos.signosAlarma) }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TarjetaMiEmbarazo(e: EmbarazoPortal) {
    val c = Sania.colors
    val semanas = e.egHoyDias?.let { it / 7 }
    val fraccion = e.egHoyDias?.let { (it / 280f).coerceIn(0f, 1f) } ?: 0f
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(Sania.shape.lg.dp)).background(c.superficie)
            .border(1.dp, c.borde, RoundedCornerShape(Sania.shape.lg.dp)).padding(14.dp),
    ) {
        Row(verticalAlignment = Alignment.Top) {
            Column(Modifier.weight(1f)) {
                Text(e.egHoy ?: "—", color = c.navy, fontSize = 24.sp, fontWeight = FontWeight.Bold)
                val sub = listOfNotNull(e.trimestre?.let { NOMBRE_TRIMESTRE[it] }, e.clinicaNombre?.let { "🏥 $it" }).joinToString(" · ")
                if (sub.isNotEmpty()) Text(sub, color = c.textoSuave, fontSize = 12.sp, modifier = Modifier.padding(top = 2.dp))
            }
            Column(horizontalAlignment = Alignment.End) {
                Text("FECHA PROBABLE DE PARTO", color = c.textoSuave, fontSize = 9.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.End)
                Text(fechaEmb(e.fpp), color = c.texto, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                Text(if (e.diasParaFpp >= 0) "faltan ${e.diasParaFpp} días" else "hace ${-e.diasParaFpp} días", color = c.textoSuave, fontSize = 11.sp)
            }
        }
        if (semanas != null) {
            Spacer(Modifier.height(10.dp))
            Box(
                Modifier.fillMaxWidth().height(10.dp).clip(RoundedCornerShape(Sania.shape.pill.dp)).background(c.chipBg)
                    .border(1.dp, c.borde, RoundedCornerShape(Sania.shape.pill.dp))
                    .semantics { contentDescription = "Semana $semanas de 40" },
            ) {
                Box(Modifier.fillMaxWidth(fraccion).height(10.dp).clip(RoundedCornerShape(Sania.shape.pill.dp)).background(c.navy))
            }
            Row(Modifier.fillMaxWidth().padding(top = 2.dp)) {
                Text("Semana 0", color = c.textoSuave, fontSize = 10.sp, modifier = Modifier.weight(1f))
                Text("Semana 40", color = c.textoSuave, fontSize = 10.sp)
            }
        }
        val chips = listOfNotNull(
            e.proximoControl?.let { Triple("📅 Próximo control: ${fechaEmb(it)}", c.teal, c.tealBg) },
            e.grupoSanguineo?.let { g -> Triple("🩸 $g" + (e.rh?.let { if (it == "+") " Rh+" else " Rh−" } ?: ""), c.texto, c.chipBg) },
            e.profesional?.let { Triple("👩‍⚕️ $it", c.texto, c.chipBg) },
        )
        if (chips.isNotEmpty()) {
            FlowRow(Modifier.padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                chips.forEach { (t, fg, bg) -> ChipEmb(t, fg, bg) }
            }
        }
        if (e.controles.isNotEmpty()) {
            Text("MIS CONTROLES (${e.controles.size})", color = c.textoSuave, fontSize = 10.sp, fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(top = 14.dp, bottom = 4.dp))
            Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(Sania.shape.md.dp)).border(1.dp, c.borde, RoundedCornerShape(Sania.shape.md.dp))) {
                e.controles.reversed().forEachIndexed { i, ctl ->
                    if (i > 0) Box(Modifier.fillMaxWidth().height(1.dp).background(c.borde))
                    Column(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(fechaEmb(ctl.fecha), color = c.texto, fontSize = 13.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                            ctl.eg?.let { Text(it, color = c.textoSuave, fontSize = 12.sp) }
                        }
                        val medidas = listOfNotNull(
                            ctl.peso?.let { "⚖️ ${numEmb(it)} kg" },
                            ctl.paSistolica?.let { "🩺 PA $it/${ctl.paDiastolica ?: "—"}" },
                            ctl.alturaUterina?.let { "📏 AU ${numEmb(it)} cm" },
                            ctl.lcf?.let { "💓 $it lpm" },
                        )
                        if (medidas.isNotEmpty()) Text(medidas.joinToString("   "), color = c.texto, fontSize = 12.sp, modifier = Modifier.padding(top = 2.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun ChipEmb(texto: String, fg: Color, bg: Color) {
    Text(texto, color = fg, fontSize = 12.sp, fontWeight = FontWeight.Bold,
        modifier = Modifier.clip(RoundedCornerShape(Sania.shape.pill.dp)).background(bg).padding(horizontal = 10.dp, vertical = 5.dp))
}

@Composable
private fun SignosAlarmaPortal(signos: List<String>) {
    val c = Sania.colors
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(Sania.shape.lg.dp)).background(c.errorBg)
            .border(2.dp, c.error, RoundedCornerShape(Sania.shape.lg.dp)).padding(14.dp),
    ) {
        Text("🚨 Acude de inmediato a tu centro de salud si tienes:", color = c.error, fontSize = 14.sp, fontWeight = FontWeight.Bold)
        signos.forEach { Text("• $it", color = c.texto, fontSize = 13.sp, modifier = Modifier.padding(top = 3.dp)) }
    }
}
