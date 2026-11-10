package pe.saniape.app.ui.clinica

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import pe.saniape.app.data.staff.FilaSala
import pe.saniape.app.data.staff.GrupoSala
import pe.saniape.app.data.staff.esperaTexto
import pe.saniape.app.data.staff.horaDelInstante
import pe.saniape.app.ui.componentes.BotonTarjeta
import pe.saniape.app.ui.theme.Sania
import pe.saniape.app.ui.theme.tocable

/**
 * 🪑 «Sigue» en el Inicio (sala de espera de todas las clínicas, 09/11/2026).
 * Gemela de components/dashboard/SigueInicio.tsx: el profesional ve SU sala;
 * recepción / Admin, el resumen por profesional. Cada grupo lleva el nombre del
 * profesional (también el del que mira) y cada fila el del paciente: ninguna
 * indicación sin nombre. Tocar una fila abre la cita en la agenda.
 *
 * [puedePasar] = `pasarEnInicio` + permiso: NUNCA ▶ Pasar en una clínica con
 * sala médica (ahí el paso es 🩺 Triaje / ▶ Atender, en la agenda).
 * [moviendo]: citas con un ▶ Pasar en viaje (el botón dice «…» y no acepta toques).
 * Sin LazyRow ni IntrinsicSize: es un ítem de la LazyColumn del Inicio.
 */
@Composable
fun TarjetaSalaInicio(
    grupos: List<GrupoSala>,
    zona: String?,
    moviendo: Set<String>,
    puedePasar: Boolean,
    onPasar: (FilaSala) -> Unit,
    onAbrir: (FilaSala) -> Unit,
) {
    val c = Sania.colors
    val forma = RoundedCornerShape(Sania.shape.md.dp)
    Column(
        Modifier.fillMaxWidth().clip(forma).border(1.5.dp, c.pend, forma).background(c.superficie).padding(Sania.dim.lg),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text("🪑 Sala de espera", color = c.texto, fontSize = 14.sp, fontWeight = FontWeight.Bold)
        grupos.forEach { g ->
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(g.nombre, color = c.navy, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                g.enConsulta.forEach { f ->
                    val desde = horaDelInstante(f.consultaAt, zona)
                    Text(
                        buildAnnotatedString {
                            withStyle(SpanStyle(color = c.purple, fontWeight = FontWeight.Bold)) { append("En consulta: ") }
                            append(f.paciente)
                            if (desde != null) append(" · desde $desde")
                        },
                        color = c.texto, fontSize = 13.sp,
                        modifier = Modifier.fillMaxWidth().tocable { onAbrir(f) },
                    )
                }
                g.sigue?.let { f ->
                    val enViaje = f.citaId in moviendo
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f).tocable { onAbrir(f) }) {
                            Text(
                                buildAnnotatedString {
                                    withStyle(SpanStyle(color = c.pend, fontWeight = FontWeight.Bold)) { append("Sigue: ") }
                                    append(f.paciente)
                                },
                                color = c.texto, fontSize = 14.sp, fontWeight = FontWeight.SemiBold,
                            )
                            val extra = listOfNotNull(
                                esperaTexto(f.minutos).takeIf { it.isNotEmpty() },
                                (g.esperando.size - 1).takeIf { it > 0 }?.let { "$it más esperando" },
                            ).joinToString(" · ")
                            if (extra.isNotEmpty()) Text(extra, color = c.textoSuave, fontSize = 12.sp)
                        }
                        if (puedePasar) {
                            Spacer(Modifier.width(8.dp))
                            BotonTarjeta(
                                texto = if (enViaje) "…" else "▶ Pasar",
                                color = c.navy,
                                habilitado = !enViaje,
                                lleno = true,
                            ) { if (!enViaje) onPasar(f) }
                        }
                    }
                }
            }
        }
    }
}
