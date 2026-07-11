package pe.saniape.app.ui.clinica.pacientes

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import pe.saniape.app.data.staff.CampoPaciente
import pe.saniape.app.data.staff.PacientesRepo
import pe.saniape.app.ui.theme.Sania

/**
 * Campos personalizados de la ficha del paciente (espejo de CamposCustomPaciente.tsx web):
 * la clínica define sus campos por rubro (capilar: densidad folicular; estética: fototipo…)
 * y aquí se ven/editan sus valores (pacientes.campos_custom). Si la clínica no definió
 * campos, no se muestra nada.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CamposCustomPaciente(
    pacienteId: String,
    valoresIniciales: Map<String, String>,
    editable: Boolean = true,
) {
    val c = Sania.colors
    val scope = rememberCoroutineScope()
    var campos by remember { mutableStateOf<List<CampoPaciente>>(emptyList()) }
    var valores by remember(pacienteId) { mutableStateOf(valoresIniciales) }
    var editando by remember { mutableStateOf(false) }
    var guardando by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        campos = runCatching { PacientesRepo.camposPaciente() }.getOrDefault(emptyList())
    }
    if (campos.isEmpty()) return

    fun mostrar(campo: CampoPaciente, v: String?): String = when {
        v.isNullOrBlank() -> "—"
        campo.tipo == "booleano" -> if (v == "true") "Sí" else "No"
        else -> v
    }

    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(Sania.shape.md.dp))
            .background(c.superficie).border(1.dp, c.borde, RoundedCornerShape(Sania.shape.md.dp)).padding(14.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(bottom = 8.dp)) {
            Text("DATOS ADICIONALES", color = c.textoSuave, fontSize = 11.sp,
                fontWeight = FontWeight.Bold, letterSpacing = 0.5.sp, modifier = Modifier.weight(1f))
            if (editable) {
                Text("✏ Editar", color = c.navy, fontSize = 12.sp, fontWeight = FontWeight.Bold,
                    modifier = Modifier.clickable { editando = true })
            }
        }
        campos.forEach { campo ->
            Row(Modifier.padding(vertical = 1.dp)) {
                Text("${campo.nombre}:", color = c.textoSuave, fontSize = 12.sp, modifier = Modifier.width(140.dp))
                Text(mostrar(campo, valores[campo.id]), color = c.texto, fontSize = 12.sp, fontWeight = FontWeight.Medium)
            }
        }
    }

    if (editando) {
        var draft by remember { mutableStateOf(valores) }
        AlertDialog(
            onDismissRequest = { if (!guardando) editando = false },
            title = { Text("Datos adicionales", fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    campos.forEach { campo ->
                        Column {
                            Text(campo.nombre.uppercase(), color = c.textoSuave, fontSize = 10.sp,
                                fontWeight = FontWeight.Bold, letterSpacing = 0.5.sp,
                                modifier = Modifier.padding(bottom = 4.dp))
                            when (campo.tipo) {
                                "booleano" -> Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    listOf("true" to "Sí", "false" to "No", "" to "—").forEach { (v, etq) ->
                                        val sel = (draft[campo.id] ?: "") == v
                                        Box(
                                            Modifier.clip(RoundedCornerShape(Sania.shape.pill.dp))
                                                .background(if (sel) c.navy else c.chipBg)
                                                .border(1.dp, if (sel) c.navy else c.borde, RoundedCornerShape(Sania.shape.pill.dp))
                                                .clickable { draft = draft + (campo.id to v) }
                                                .padding(horizontal = 14.dp, vertical = 6.dp),
                                        ) { Text(etq, color = if (sel) c.sobreNavy else c.textoSuave, fontSize = 12.sp, fontWeight = FontWeight.Bold) }
                                    }
                                }
                                "opciones" -> FlowRow(
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                    verticalArrangement = Arrangement.spacedBy(6.dp),
                                ) {
                                    campo.opciones.forEach { op ->
                                        val sel = draft[campo.id] == op
                                        Box(
                                            Modifier.clip(RoundedCornerShape(Sania.shape.pill.dp))
                                                .background(if (sel) c.navy else c.chipBg)
                                                .border(1.dp, if (sel) c.navy else c.borde, RoundedCornerShape(Sania.shape.pill.dp))
                                                .clickable { draft = draft + (campo.id to if (sel) "" else op) }
                                                .padding(horizontal = 12.dp, vertical = 6.dp),
                                        ) { Text(op, color = if (sel) c.sobreNavy else c.textoSuave, fontSize = 12.sp, fontWeight = FontWeight.Bold) }
                                    }
                                }
                                else -> OutlinedTextField(
                                    value = draft[campo.id] ?: "",
                                    onValueChange = { nuevo ->
                                        draft = draft + (campo.id to
                                            if (campo.tipo == "numero") nuevo.filter { it.isDigit() || it == '.' } else nuevo)
                                    },
                                    placeholder = {
                                        Text(if (campo.tipo == "fecha") "AAAA-MM-DD" else campo.ayuda ?: "", color = c.textoSuave)
                                    },
                                    singleLine = true,
                                    keyboardOptions = if (campo.tipo == "numero")
                                        KeyboardOptions(keyboardType = KeyboardType.Number) else KeyboardOptions.Default,
                                    modifier = Modifier.fillMaxWidth(),
                                )
                            }
                            campo.ayuda?.takeIf { it.isNotBlank() && campo.tipo != "texto" }?.let {
                                Text(it, color = c.textoSuave, fontSize = 10.sp, modifier = Modifier.padding(top = 2.dp))
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(enabled = !guardando, onClick = {
                    guardando = true
                    scope.launch {
                        val ok = PacientesRepo.guardarCamposCustom(pacienteId, campos, draft)
                        guardando = false
                        if (ok) { valores = draft.filterValues { it.isNotBlank() }; editando = false; pe.saniape.app.ui.Toaster.exito("Datos guardados") }
                        else pe.saniape.app.ui.Toaster.error("No se pudo guardar")
                    }
                }) { Text(if (guardando) "Guardando…" else "Guardar", color = c.navy, fontWeight = FontWeight.Bold) }
            },
            dismissButton = { TextButton(onClick = { editando = false }, enabled = !guardando) {
                Text("Cancelar", color = c.textoSuave) } },
            containerColor = c.superficie,
        )
    }
}
