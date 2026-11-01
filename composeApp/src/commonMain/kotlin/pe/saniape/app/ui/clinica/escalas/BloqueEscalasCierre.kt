package pe.saniape.app.ui.clinica.escalas

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import pe.saniape.app.ui.Toaster
import pe.saniape.app.data.staff.EscalaCierre
import pe.saniape.app.data.staff.EscalasCierreRepo
import pe.saniape.app.data.staff.MedicionPrevia
import pe.saniape.app.data.staff.OpcionEscala
import pe.saniape.app.data.staff.resultadoEscala
import pe.saniape.app.data.staff.textoCortoOpcion
import pe.saniape.app.ui.theme.Sania

/**
 * 📋 Escalas dentro del cierre (gemelo de EscalasEnCierre.tsx, 2026-11-01): las de
 * la especialidad de la cita, sin salir de la ventana.
 *  · Una pregunta (EVA, Fitzpatrick, biotipo, Glogau): sus opciones a un toque;
 *    tocar otra vez la quita.
 *  · Varias preguntas: «＋ Corto» despliega sus ítems.
 *  · «Antes: …» = la última vez que se le midió.
 * Solo PIDE: quien llama guarda [valores] tras completar (EscalasCierreRepo.guardar).
 * Sin escalas en la especialidad no se pinta nada.
 */
@Composable
fun BloqueEscalasCierre(especialidadId: String?, pacienteId: String?, valores: MutableState<Map<String, List<Int?>>>) {
    var escalas by remember { mutableStateOf<List<EscalaCierre>>(emptyList()) }
    var previas by remember { mutableStateOf<Map<String, MedicionPrevia>>(emptyMap()) }
    LaunchedEffect(especialidadId, pacienteId) {
        escalas = EscalasCierreRepo.deEspecialidad(especialidadId)
        if (escalas.isNotEmpty()) previas = EscalasCierreRepo.ultimasDelPaciente(pacienteId)
    }
    if (escalas.isEmpty()) return
    val c = Sania.colors
    Spacer(Modifier.height(Sania.dim.md))
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(Sania.shape.md.dp))
            .background(c.superficie).border(1.dp, c.borde, RoundedCornerShape(Sania.shape.md.dp)).padding(14.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(bottom = 4.dp)) {
            Text("📋", fontSize = 15.sp)
            Spacer(Modifier.width(7.dp))
            Text("Escalas", color = c.texto, fontSize = 14.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.width(6.dp))
            Text("opcional", color = c.textoSuave, fontSize = 11.sp)
        }
        Text("Quedan en la ficha con su evolución.", color = c.textoSuave, fontSize = 11.sp)
        escalas.forEach { e ->
            Spacer(Modifier.height(10.dp))
            val actual = valores.value[e.instrumento] ?: List(e.items.size) { null }
            val poner = { nuevos: List<Int?> ->
                valores.value = if (nuevos.all { it == null }) valores.value - e.instrumento
                else valores.value + (e.instrumento to nuevos)
            }
            if (e.items.size == 1) {
                CabeceraEscala(e, actual, previas[e.corto])
                OpcionesEscala(e.items[0].opciones, actual[0]) { poner(listOf(it)) }
            } else {
                var abierta by remember(e.instrumento) { mutableStateOf(valores.value.containsKey(e.instrumento)) }
                Row(Modifier.clickable { abierta = !abierta }) {
                    Text(if (abierta) "－ " else "＋ ", color = c.navy, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    CabeceraEscala(e, actual, previas[e.corto])
                }
                if (abierta) {
                    e.items.forEachIndexed { i, item ->
                        Text("${i + 1}. ${item.texto}", color = c.texto, fontSize = 12.sp, modifier = Modifier.padding(top = 8.dp))
                        OpcionesEscala(item.opciones, actual[i]) { v -> poner(actual.toMutableList().also { it[i] = v }) }
                    }
                }
            }
        }
    }
}

/** El guardado sigue aunque se cierre el diálogo o la pantalla. */
private val alcanceEscalas = CoroutineScope(SupervisorJob() + Dispatchers.Default)

/**
 * Qué hacer con las escalas marcadas según cómo terminó el completar (como las
 * fotos): no se completó o quedó en la cola sin señal → se avisa que NO se
 * guardaron (nunca en silencio); completada → se guardan ligadas a la cita.
 */
fun escalasTrasCompletar(
    ok: Boolean, encolada: Boolean,
    pacienteId: String, citaId: String?, especialidadId: String?,
    marcadas: Map<String, List<Int?>>,
) {
    if (marcadas.isEmpty()) return
    val n = if (marcadas.size == 1) "la escala" else "las ${marcadas.size} escalas"
    when {
        !ok -> Toaster.error("No se completó: $n no se guardaron.")
        encolada -> Toaster.error("Sin señal: $n no se guardaron. Márcalas de nuevo desde la web (ficha → 📊 Escalas).")
        else -> alcanceEscalas.launch {
            val fallidas = EscalasCierreRepo.guardar(pacienteId, citaId, especialidadId, marcadas)
            if (fallidas > 0) Toaster.error(if (fallidas == 1) "Una escala no se pudo guardar." else "$fallidas escalas no se pudieron guardar.")
        }
    }
}

@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
private fun CabeceraEscala(e: EscalaCierre, actual: List<Int?>, antes: MedicionPrevia?) {
    val c = Sania.colors
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(e.corto, color = c.texto, fontSize = 13.sp, fontWeight = FontWeight.Bold)
        antes?.let { Text("Antes: ${it.texto}${if (it.fecha.isNotBlank()) " (${it.fecha})" else ""}", color = c.textoSuave, fontSize = 11.sp) }
        resultadoEscala(e, actual)?.let { Text("✓ $it", color = c.ok, fontSize = 11.sp, fontWeight = FontWeight.Bold) }
    }
}

@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
private fun OpcionesEscala(opciones: List<OpcionEscala>, marcado: Int?, onElegir: (Int?) -> Unit) {
    val c = Sania.colors
    FlowRow(
        Modifier.padding(top = 5.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        opciones.forEach { op ->
            val activa = marcado == op.valor
            Text(
                textoCortoOpcion(op.texto),
                color = if (activa) c.sobreNavy else c.texto, fontSize = 12.sp,
                fontWeight = if (activa) FontWeight.Bold else FontWeight.Normal,
                modifier = Modifier.clip(RoundedCornerShape(Sania.shape.sm.dp))
                    .background(if (activa) c.navy else c.superficie)
                    .border(1.dp, if (activa) c.navy else c.borde, RoundedCornerShape(Sania.shape.sm.dp))
                    .clickable { onElegir(if (activa) null else op.valor) }
                    .padding(horizontal = 10.dp, vertical = 6.dp),
            )
        }
    }
}
