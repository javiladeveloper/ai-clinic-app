package pe.saniape.app.ui.clinica.agenda.componentes

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import pe.saniape.app.data.staff.CitaStaff
import pe.saniape.app.data.staff.dineroDeFila
import pe.saniape.app.data.staff.formatearTotalesPorMoneda
import pe.saniape.app.data.staff.monedaDeFila
import pe.saniape.app.data.staff.totalesPorMoneda
import pe.saniape.app.ui.hora12
import pe.saniape.app.ui.theme.Sania

/**
 * «💰 N atendidas sin cobrar · S/ X» de la agenda de HOY (T2 2026-10-24,
 * gemelo del banner de /citas): lo ya atendido que no pasó por caja, cada una
 * con el 💰 de siempre ([onCobrar] abre ModalCobrarCita). Al pie, «🧮 Cerrar mi
 * caja» si quien mira puede ([onCerrarCaja] null = no). Plegado si son más de 3.
 */
@Composable
fun BannerSinCobrar(
    filas: List<CitaStaff>,
    nombreTipo: (CitaStaff) -> String,
    onCobrar: (CitaStaff) -> Unit,
    onCerrarCaja: (() -> Unit)? = null,
) {
    if (filas.isEmpty()) return
    val c = Sania.colors
    var abierto by remember { mutableStateOf<Boolean?>(null) }
    val verLista = abierto ?: (filas.size <= 3)
    val forma = RoundedCornerShape(Sania.shape.md.dp)
    // Multipaís: un total por moneda (nunca sumados); con una sola, el de siempre.
    val total = formatearTotalesPorMoneda(totalesPorMoneda(filas.map { it.costo to monedaDeFila(it.sedeId) }))
    Column(Modifier.fillMaxWidth().clip(forma).border(1.dp, c.pend, forma)) {
        Row(
            Modifier.fillMaxWidth().background(c.pendBg).clickable { abierto = !verLista }.padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("💰", fontSize = 18.sp)
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text("${filas.size} atendida${if (filas.size != 1) "s" else ""} sin cobrar · $total",
                    color = c.pend, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                Text("Ya se atendieron hoy y no pasaron por caja.", color = c.textoSuave, fontSize = 11.sp)
            }
            Text(if (verLista) "▲" else "▼", color = c.pend, fontSize = 12.sp, fontWeight = FontWeight.Bold)
        }
        if (verLista) {
            filas.forEach { cita ->
                Row(
                    Modifier.fillMaxWidth().background(c.superficie).clickable { onCobrar(cita) }.padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(cita.pacienteNombre ?: "Paciente", color = c.texto, fontSize = 13.sp, fontWeight = FontWeight.Bold,
                            maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(listOfNotNull(hora12(cita.hora), nombreTipo(cita), cita.terapeutaNombre).joinToString(" · "),
                            color = c.textoSuave, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    Text(dineroDeFila(cita.costo, cita.sedeId), color = c.navy, fontSize = 13.sp, fontWeight = FontWeight.ExtraBold)
                    Spacer(Modifier.width(8.dp))
                    Text("💰 Cobrar", color = c.sobreNavy, fontSize = 12.sp, fontWeight = FontWeight.Bold,
                        modifier = Modifier.clip(RoundedCornerShape(Sania.shape.sm.dp)).background(c.ok).padding(horizontal = 10.dp, vertical = 6.dp))
                }
            }
        }
        onCerrarCaja?.let { cerrar ->
            Row(
                Modifier.fillMaxWidth().background(c.superficie).clickable { cerrar() }.padding(horizontal = 14.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("🧮 Cerrar mi caja", color = c.navy, fontSize = 13.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                Text("›", color = c.textoSuave, fontSize = 14.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}
