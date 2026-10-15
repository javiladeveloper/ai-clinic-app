package pe.saniape.app.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import pe.saniape.app.data.IndicacionPortal
import pe.saniape.app.data.htmlIndicacionPortal
import pe.saniape.app.ui.clinica.indicaciones.DetalleIndicacion
import pe.saniape.app.ui.theme.Sania

/**
 * 📝 Mis indicaciones para casa del portal del paciente (gemelo de
 * MisIndicaciones.tsx de la web): la última completa (signos de alarma en un
 * recuadro rojo); las anteriores, plegadas. "Descargar PDF" arma la hoja en la
 * app y la abre en el visor con impresión.
 *
 * Se agrega como ítems del LazyColumn de la pantalla Salud (nada de listas
 * perezosas anidadas). Sin indicaciones no agrega nada.
 */
internal fun LazyListScope.seccionIndicaciones(
    items: List<IndicacionPortal>,
    espacioArriba: Boolean,
    verAnteriores: Boolean,
    onAlternarAnteriores: () -> Unit,
    onDescargar: (IndicacionPortal) -> Unit,
) {
    val ultima = items.firstOrNull() ?: return
    val anteriores = items.drop(1)
    item(key = "ind-etiqueta") {
        Column {
            if (espacioArriba) Spacer(Modifier.height(Sania.dim.sm))
            Text("📝 MIS INDICACIONES PARA CASA", color = Sania.colors.textoSuave, fontSize = Sania.txt.mini, fontWeight = FontWeight.Bold)
        }
    }
    item(key = "ind-" + ultima.id) {
        Column(Modifier.tarjetaIndicacion().padding(Sania.dim.tarjeta)) {
            CabeceraIndicacion(ultima) { onDescargar(ultima) }
            Spacer(Modifier.height(Sania.dim.md))
            DetalleIndicacion(ultima, tituloAlarma = ALARMA_PACIENTE)
        }
    }
    if (anteriores.isNotEmpty()) {
        item(key = "ind-anteriores") {
            val c = Sania.colors
            Row(
                Modifier.fillMaxWidth().heightIn(min = 44.dp).clip(RoundedCornerShape(Sania.shape.sm.dp))
                    .clickable(role = Role.Button, onClick = onAlternarAnteriores).padding(vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("${if (verAnteriores) "▾" else "▸"} Indicaciones anteriores (${anteriores.size})", color = c.navy,
                    fontSize = 13.sp, fontWeight = FontWeight.Bold)
            }
        }
        if (verAnteriores) {
            items(anteriores, key = { "ind-" + it.id }) { i -> TarjetaIndicacionAnterior(i) { onDescargar(i) } }
        }
    }
}

private const val ALARMA_PACIENTE = "🚨 Acuda de inmediato o llame a su clínica si…"

/** Abre la hoja en el visor de documentos (impresión / PDF / compartir). */
internal fun descargarIndicacion(acciones: AccionesNativas, i: IndicacionPortal) {
    acciones.abrirHtml(htmlIndicacionPortal(i), "Indicaciones para casa")
}

@Composable
private fun Modifier.tarjetaIndicacion(): Modifier {
    val c = Sania.colors
    return this.fillMaxWidth().clip(RoundedCornerShape(Sania.shape.md.dp)).background(c.superficie)
        .border(1.dp, c.borde, RoundedCornerShape(Sania.shape.md.dp))
}

/** Una anterior: plegada (título y fecha); tocar la abre. */
@Composable
private fun TarjetaIndicacionAnterior(i: IndicacionPortal, onDescargar: () -> Unit) {
    val c = Sania.colors
    var abierto by rememberSaveable(i.id) { mutableStateOf(false) }
    Column(Modifier.tarjetaIndicacion()) {
        Column(
            Modifier.fillMaxWidth()
                .clickable(role = Role.Button, onClickLabel = if (abierto) "Ocultar indicaciones" else "Ver indicaciones") { abierto = !abierto }
                .padding(Sania.dim.tarjeta),
        ) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(i.titulo, color = c.texto, fontSize = 14.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                Text(if (abierto) "▲" else "▼", color = c.textoSuave, fontSize = 12.sp)
            }
            Text(fechaDMA(i.fecha), color = c.textoSuave, fontSize = 12.sp)
        }
        AnimatedVisibility(abierto) {
            Column(Modifier.padding(start = Sania.dim.tarjeta, end = Sania.dim.tarjeta, bottom = Sania.dim.tarjeta)) {
                HorizontalDivider(color = c.borde)
                Spacer(Modifier.height(Sania.dim.sm))
                CabeceraIndicacion(i, onDescargar)
                Spacer(Modifier.height(Sania.dim.md))
                DetalleIndicacion(i, tituloAlarma = ALARMA_PACIENTE)
            }
        }
    }
}

@Composable
private fun CabeceraIndicacion(i: IndicacionPortal, onDescargar: () -> Unit) {
    val c = Sania.colors
    Column(Modifier.fillMaxWidth()) {
        Text(i.titulo, color = c.texto, fontSize = Sania.txt.seccion, fontWeight = FontWeight.Bold)
        Text(
            listOfNotNull(fechaDMA(i.fecha), i.profesional?.let { "Indicado por $it" }, i.clinica?.nombre?.let { "🏥 $it" })
                .joinToString(" · "),
            color = c.textoSuave, fontSize = 12.sp,
        )
        Spacer(Modifier.height(Sania.dim.sm))
        Box(
            Modifier.heightIn(min = 44.dp).clip(RoundedCornerShape(Sania.shape.sm.dp))
                .border(1.5.dp, c.lav, RoundedCornerShape(Sania.shape.sm.dp))
                .clickable(role = Role.Button, onClick = onDescargar)
                .padding(horizontal = 14.dp, vertical = 10.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text("📄 Descargar PDF", color = c.navy, fontSize = 13.sp, fontWeight = FontWeight.Bold)
        }
    }
}
