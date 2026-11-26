package pe.saniape.app.ui.clinica.pacientes

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import pe.saniape.app.data.staff.MAX_ETIQUETAS
import pe.saniape.app.data.staff.PacientesRepo
import pe.saniape.app.data.staff.agregarEtiqueta
import pe.saniape.app.data.staff.quitarEtiqueta
import pe.saniape.app.data.staff.tieneEtiqueta
import pe.saniape.app.ui.Toaster
import pe.saniape.app.ui.theme.Sania

/**
 * 🏷️ Etiquetas del paciente en la cabecera de su ficha (FISIOSALUD, 25/11/2026). Gemelo de
 * `components/pacientes/EtiquetasPaciente.tsx`: chips con ✕ y un campo que sugiere las que
 * ya usa la clínica (una sola escritura por grupo). Se guarda al momento.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun EtiquetasFicha(pacienteId: String, etiquetas: List<String>, editable: Boolean, onCambio: (List<String>) -> Unit) {
    val c = Sania.colors
    val scope = rememberCoroutineScope()
    var abierto by remember { mutableStateOf(false) }
    var texto by remember { mutableStateOf("") }
    var conocidas by remember { mutableStateOf<List<String>>(emptyList()) }
    var guardando by remember { mutableStateOf(false) }
    LaunchedEffect(abierto) { if (abierto && conocidas.isEmpty()) conocidas = PacientesRepo.etiquetasClinica() }
    if (etiquetas.isEmpty() && !editable) return

    fun guardar(nuevas: List<String>) {
        if (guardando || nuevas == etiquetas) return
        val antes = etiquetas
        guardando = true
        onCambio(nuevas)   // al instante; si falla, se vuelve atrás
        scope.launch {
            val ok = PacientesRepo.guardarEtiquetas(pacienteId, nuevas)
            guardando = false
            if (!ok) { onCambio(antes); Toaster.error("No se pudo guardar la etiqueta. Revisa tu conexión.") }
        }
    }

    Column(Modifier.fillMaxWidth()) {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            etiquetas.forEach { e -> ChipEtiqueta(e, onQuitar = if (editable) ({ guardar(quitarEtiqueta(etiquetas, e)) }) else null, icono = true) }
            if (editable && etiquetas.size < MAX_ETIQUETAS && !abierto) {
                Text("＋ Etiqueta", color = c.navy, fontSize = 12.sp, fontWeight = FontWeight.Bold,
                    modifier = Modifier.clip(RoundedCornerShape(Sania.shape.pill.dp))
                        .border(1.dp, c.borde, RoundedCornerShape(Sania.shape.pill.dp))
                        .clickable { abierto = true }.padding(horizontal = 12.dp, vertical = 7.dp))
            }
        }
        if (abierto) {
            Spacer(Modifier.height(6.dp))
            fun agregar(t: String) {
                val nuevas = agregarEtiqueta(etiquetas, t, conocidas)
                texto = ""
                if (nuevas != etiquetas) guardar(nuevas)
            }
            OutlinedTextField(texto, { texto = it.take(40) }, singleLine = true,
                placeholder = { Text("Artritis reumatoide, Control mensual…", color = c.textoSuave) },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { if (texto.isNotBlank()) agregar(texto) else abierto = false }),
                colors = coloresCampoForm(), modifier = Modifier.fillMaxWidth())
            val q = texto.trim().lowercase()
            val sugeridas = conocidas.filter { !tieneEtiqueta(etiquetas, it) && (q.isEmpty() || it.lowercase().contains(q)) }.take(8)
            FlowRow(Modifier.padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                sugeridas.forEach { s ->
                    Text(s, color = c.texto, fontSize = 12.sp,
                        modifier = Modifier.clip(RoundedCornerShape(Sania.shape.pill.dp)).background(c.chipBg)
                            .clickable { agregar(s) }.padding(horizontal = 10.dp, vertical = 7.dp))
                }
            }
            Row(Modifier.fillMaxWidth().padding(top = 6.dp), horizontalArrangement = Arrangement.End) {
                if (texto.isNotBlank()) Text("Agregar «${texto.trim()}»", color = c.navy, fontSize = 13.sp, fontWeight = FontWeight.Bold,
                    modifier = Modifier.clip(RoundedCornerShape(8.dp)).clickable { agregar(texto) }.padding(10.dp))
                Text("Listo", color = c.textoSuave, fontSize = 13.sp, fontWeight = FontWeight.Bold,
                    modifier = Modifier.clip(RoundedCornerShape(8.dp)).clickable { abierto = false; texto = "" }.padding(10.dp))
            }
        }
    }
}
