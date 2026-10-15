package pe.saniape.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import pe.saniape.app.data.MiOrtodoncia
import pe.saniape.app.data.OrtodonciaPortal
import pe.saniape.app.data.staff.fechaIndicacion
import pe.saniape.app.data.textoProximoControlPortal
import pe.saniape.app.ui.theme.Sania

/**
 * 😁 Mi ortodoncia del portal del paciente (gemelo de MiOrtodoncia.tsx de la
 * web): la fase (barra de las 5), "Mes N de M", el próximo control (agendado o
 * estimado), cómo usar los elásticos que le indicaron y los cuidados de su
 * aparatología. Sin notas, observaciones ni higiene (no vienen del servidor).
 *
 * UN ítem por plan del LazyColumn de Salud (nada de listas perezosas anidadas).
 * Sin planes no agrega nada: el portal de un paciente de otra especialidad no cambia.
 */
internal fun LazyListScope.seccionOrtodoncia(datos: MiOrtodoncia, espacioArriba: Boolean) {
    if (!datos.tieneDatos) return
    item(key = "orto-titulo") {
        Column {
            if (espacioArriba) Spacer(Modifier.height(Sania.dim.sm))
            Text("😁 MI ORTODONCIA", color = Sania.colors.textoSuave, fontSize = Sania.txt.mini, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(Sania.dim.md))
        }
    }
    datos.planes.forEach { p ->
        item(key = "orto-" + p.id) {
            TarjetaOrtodonciaPortal(p)
            Spacer(Modifier.height(Sania.dim.sm))
        }
    }
}

@Composable
private fun TarjetaOrtodonciaPortal(p: OrtodonciaPortal) {
    val c = Sania.colors
    var verConsejos by rememberSaveable(p.id) { mutableStateOf(false) }
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(Sania.shape.md.dp)).background(c.superficie)
            .border(1.dp, c.borde, RoundedCornerShape(Sania.shape.md.dp)).padding(Sania.dim.tarjeta),
    ) {
        Text("😁 ${p.aparatologia}", color = c.texto, fontSize = 15.sp, fontWeight = FontWeight.Bold)
        listOfNotNull(p.arcadas, p.clinicaNombre?.let { "🏥 $it" }, p.profesional).takeIf { it.isNotEmpty() }?.let {
            Text(it.joinToString(" · "), color = c.textoSuave, fontSize = 12.sp)
        }

        // Mes N de M
        if (p.progresoTexto.isNotBlank()) {
            Row(Modifier.fillMaxWidth().padding(top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(p.progresoTexto, color = c.texto, fontSize = 13.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                p.progresoPct?.let { Text("$it%", color = c.textoSuave, fontSize = 12.sp) }
            }
            p.progresoPct?.let { pct ->
                Box(Modifier.fillMaxWidth().padding(top = 4.dp).height(8.dp).clip(RoundedCornerShape(4.dp)).background(c.chipBg)
                    .semantics { contentDescription = "Avance del tratamiento: $pct%" }) {
                    Box(Modifier.fillMaxWidth(pct / 100f).height(8.dp).clip(RoundedCornerShape(4.dp)).background(c.navy))
                }
            }
        }

        // Fases
        if (p.fases.isNotEmpty()) {
            Row(Modifier.fillMaxWidth().padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                p.fases.forEach { f ->
                    Box(
                        Modifier.weight(1f).height(8.dp).clip(RoundedCornerShape(4.dp))
                            .background(when (f.estado) { "hecha" -> c.ok; "actual" -> c.navy; else -> c.chipBg })
                            .semantics { contentDescription = "${f.nombre}: ${f.estado}" },
                    )
                }
            }
            (p.fases.firstOrNull { it.estado == "actual" } ?: p.fase)?.let { f ->
                Text("Fase: ${f.nombre}", color = c.navy, fontSize = 12.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 4.dp))
            }
        }

        // Próximo control (no en retención)
        textoProximoControlPortal(p) { fechaIndicacion(it) }?.let { t ->
            Column(
                Modifier.fillMaxWidth().padding(top = 10.dp).clip(RoundedCornerShape(Sania.shape.sm.dp)).background(c.chipBg)
                    .padding(horizontal = 12.dp, vertical = 8.dp),
            ) {
                Text("PRÓXIMO CONTROL", color = c.textoSuave, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                Text(t, color = c.texto, fontSize = 13.sp, fontWeight = if (p.proximoControl?.agendado == true) FontWeight.Bold else FontWeight.Normal)
            }
        }
        if (p.retirado) {
            Text("✓ Aparatología retirada · fase de retención${p.retencion?.let { ": $it" }.orEmpty()}",
                color = c.ok, fontSize = 12.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 10.dp))
        }

        // Elásticos
        if (p.elasticos.isNotEmpty()) {
            Text("TUS ELÁSTICOS", color = c.textoSuave, fontSize = 9.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 12.dp))
            p.elasticos.forEach { e -> Text("• $e", color = c.texto, fontSize = 13.sp, modifier = Modifier.padding(top = 3.dp)) }
        }

        // Cuidados (plegados)
        if (p.consejos.isNotEmpty()) {
            Text(
                (if (verConsejos) "▲ " else "▼ ") + "Cuidados (${p.consejos.size})",
                color = c.navy, fontSize = 13.sp, fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(top = 8.dp).heightIn(min = 44.dp).clickable { verConsejos = !verConsejos }.padding(vertical = 12.dp),
            )
            if (verConsejos) p.consejos.forEach { k -> Text("• $k", color = c.texto, fontSize = 13.sp, modifier = Modifier.padding(top = 3.dp)) }
        }
        p.ultimoControl?.let {
            Text("Último control: ${fechaIndicacion(it)}", color = c.textoSuave, fontSize = 11.sp, modifier = Modifier.padding(top = 8.dp))
        }
    }
}
