package pe.saniape.app.ui.clinica.odontologia

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import pe.saniape.app.data.staff.COLOR_REALIZADO
import pe.saniape.app.data.staff.DienteHallazgo
import pe.saniape.app.data.staff.HallazgoDental
import pe.saniape.app.ui.theme.Sania

/**
 * "Mostrar al paciente": el odontograma a pantalla completa, sin botones ni
 * siglas clínicas, con lo que hay que tratar en palabras simples. Para girar
 * el celular hacia el paciente y explicarle — es lo que convierte la evaluación
 * en tratamiento aceptado. Gemelo de components/odontologia/MostrarAlPaciente.tsx.
 */
@Composable
internal fun MostrarAlPaciente(
    cuadrantes: List<List<String>>,
    porDiente: Map<String, List<DienteHallazgo>>,
    hallazgos: List<DienteHallazgo>,
    catalogo: List<HallazgoDental>,
    onCerrar: () -> Unit,
) {
    val c = Sania.colors
    // Lo pendiente agrupado por hallazgo: "Caries — piezas 16, 26, 36".
    data class Grupo(val nombre: String, val color: String, val piezas: MutableList<String>)
    val porTratar = remember(hallazgos, catalogo) {
        val porId = catalogo.associateBy { it.id }
        val grupos = linkedMapOf<String, Grupo>()
        for (r in hallazgos) {
            if (r.estado != "Pendiente") continue
            val h = porId[r.hallazgoId] ?: continue
            // La pieza que falta no es algo por hacer: ya se ve con su aspa azul.
            if (h.marcaAusente || h.nombre.lowercase().startsWith("ausente")) continue
            val g = grupos.getOrPut(h.id) { Grupo(h.nombre, h.color, mutableListOf()) }
            val pieza = when {
                r.diente == "BOCA" -> "toda la boca"
                r.dienteHasta != null -> "${r.diente}–${r.dienteHasta}"
                else -> r.diente
            }
            if (pieza !in g.piezas) g.piezas.add(pieza)
        }
        grupos.values.sortedByDescending { it.piezas.size }
    }
    val tratados = remember(hallazgos) { hallazgos.count { it.estado == "Realizado" } }

    // Entra y sale con un fundido + leve zoom (el celular se gira hacia el
    // paciente: un corte seco se siente brusco). Al cerrar se espera a que
    // termine la salida y recién entonces se desmonta. Con las animaciones del
    // sistema desactivadas Compose las salta y cierra al instante.
    val visible = remember { MutableTransitionState(false) }
    var cerrando by remember { mutableStateOf(false) }
    visible.targetState = !cerrando
    LaunchedEffect(visible.isIdle, visible.currentState, cerrando) {
        if (cerrando && visible.isIdle && !visible.currentState) onCerrar()
    }
    val cerrar = { cerrando = true }

    Dialog(onDismissRequest = cerrar, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        AnimatedVisibility(
            visibleState = visible,
            enter = fadeIn() + scaleIn(initialScale = 0.96f),
            exit = fadeOut() + scaleOut(targetScale = 0.96f),
        ) {
        Column(
            Modifier.fillMaxSize().background(c.superficie).verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 20.dp),
        ) {
            Row(verticalAlignment = Alignment.Top) {
                Column(Modifier.weight(1f)) {
                    Text("Así está tu boca hoy", color = c.navy, fontSize = 26.sp,
                        fontStyle = FontStyle.Italic, fontWeight = FontWeight.SemiBold)
                    val piezas = porTratar.sumOf { it.piezas.size }
                    Text(
                        if (porTratar.isEmpty()) "No hay nada pendiente por tratar."
                        else "$piezas por tratar" + (if (tratados > 0) " · $tratados ya tratados" else ""),
                        color = c.textoSuave, fontSize = 14.sp, modifier = Modifier.padding(top = 4.dp),
                    )
                }
                Box(
                    Modifier.clip(RoundedCornerShape(Sania.shape.md.dp)).border(1.5.dp, c.borde, RoundedCornerShape(Sania.shape.md.dp))
                        .clickable { cerrar() }.padding(horizontal = 14.dp, vertical = 8.dp),
                ) { Text("✕ Cerrar", color = c.texto, fontWeight = FontWeight.Bold, fontSize = 13.sp) }
            }
            Spacer(Modifier.height(16.dp))
            // Solo para mirar: tocar no hace nada.
            Boca(cuadrantes = cuadrantes, porDiente = porDiente, catalogo = catalogo, onTocar = { _, _ -> })
            Spacer(Modifier.height(12.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(14.dp).clip(RoundedCornerShape(3.dp)).background(colorDe("#dc2626")))
                Text(" Por tratar", color = c.texto, fontSize = 13.sp)
                Spacer(Modifier.width(14.dp))
                Box(Modifier.size(14.dp).clip(RoundedCornerShape(3.dp)).background(colorDe(COLOR_REALIZADO)))
                Text(" Ya tratado", color = c.texto, fontSize = 13.sp)
                Spacer(Modifier.width(14.dp))
                Text("✕", color = colorDe(COLOR_REALIZADO), fontWeight = FontWeight.Black, fontSize = 13.sp)
                Text(" Pieza que falta", color = c.texto, fontSize = 13.sp)
            }
            Spacer(Modifier.height(14.dp))
            porTratar.forEach { g ->
                Row(
                    Modifier.fillMaxWidth().padding(bottom = 8.dp).clip(RoundedCornerShape(Sania.shape.md.dp))
                        .border(1.dp, c.borde, RoundedCornerShape(Sania.shape.md.dp)).padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(Modifier.size(14.dp).clip(CircleShape).background(colorDe(g.color)))
                    Spacer(Modifier.width(12.dp))
                    Column {
                        Text(g.nombre, color = c.texto, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                        Text(
                            if (g.piezas == listOf("toda la boca")) "Toda la boca"
                            else (if (g.piezas.size == 1) "Pieza " else "Piezas ") + g.piezas.joinToString(", "),
                            color = c.textoSuave, fontSize = 13.sp,
                        )
                    }
                }
            }
        }
        }
    }
}
