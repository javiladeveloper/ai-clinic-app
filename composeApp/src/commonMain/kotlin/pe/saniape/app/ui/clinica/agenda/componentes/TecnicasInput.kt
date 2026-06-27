package pe.saniape.app.ui.clinica.agenda.componentes

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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import pe.saniape.app.data.staff.TecnicasRepo
import pe.saniape.app.ui.theme.Sania

private const val SEPARADOR = " + "

/**
 * Entrada de técnicas/procedimientos con chips + autocomplete (igual que la web).
 * Las técnicas ya usadas en la clínica se sugieren solas (tabla tecnicas_sesion).
 * El valor es un string "Tec1 + Tec2 + Tec3".
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TecnicasInput(value: String, onChange: (String) -> Unit) {
    val c = Sania.colors
    var texto by remember { mutableStateOf("") }
    var sugerencias by remember { mutableStateOf<List<String>>(emptyList()) }

    val chips = remember(value) { value.split(SEPARADOR).map { it.trim() }.filter { it.isNotEmpty() } }

    LaunchedEffect(Unit) {
        sugerencias = runCatching { TecnicasRepo.sugerencias() }.getOrDefault(emptyList())
    }

    fun normalizar(s: String) = s.lowercase().trim()
    val filtradas = sugerencias
        .filter { s -> chips.none { normalizar(it) == normalizar(s) } }
        .filter { s -> texto.isBlank() || normalizar(s).contains(normalizar(texto)) }
        .take(6)

    fun agregar(nombre: String) {
        val n = nombre.trim()
        if (n.isEmpty() || chips.any { normalizar(it) == normalizar(n) }) return
        onChange((chips + n).joinToString(SEPARADOR))
        texto = ""
    }
    fun quitar(nombre: String) = onChange(chips.filter { it != nombre }.joinToString(SEPARADOR))

    Column {
        // Chips actuales
        if (chips.isNotEmpty()) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.padding(bottom = 6.dp)) {
                chips.forEach { chip ->
                    Row(
                        Modifier.clip(RoundedCornerShape(Sania.shape.pill.dp)).background(c.chipBg)
                            .padding(start = 10.dp, end = 6.dp, top = 4.dp, bottom = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(chip, color = c.navy, fontSize = 12.sp)
                        Spacer(Modifier.width(4.dp))
                        Text("✕", color = c.navy, fontSize = 11.sp, modifier = Modifier.clickable { quitar(chip) })
                    }
                }
            }
        }
        OutlinedTextField(
            value = texto, onValueChange = { texto = it },
            placeholder = {
                Text(if (chips.isEmpty()) "Ej: TENS, ultrasonido… (Enter para agregar)" else "Agregar otra…",
                    color = c.textoSuave)
            },
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            modifier = Modifier.fillMaxWidth(),
        )
        // Botón "agregar" del texto actual (Enter no siempre dispara en móvil)
        if (texto.isNotBlank()) {
            Box(Modifier.padding(top = 4.dp).clip(RoundedCornerShape(Sania.shape.sm.dp))
                .background(c.navy).clickable { agregar(texto) }.padding(horizontal = 12.dp, vertical = 6.dp)) {
                Text("+ Agregar \"$texto\"", color = c.sobreNavy, fontSize = 11.sp)
            }
        }
        // Sugerencias
        if (filtradas.isNotEmpty()) {
            Spacer(Modifier.padding(top = 6.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                filtradas.forEach { sug ->
                    Box(
                        Modifier.clip(RoundedCornerShape(Sania.shape.pill.dp))
                            .border(1.dp, c.borde, RoundedCornerShape(Sania.shape.pill.dp))
                            .clickable { agregar(sug) }.padding(horizontal = 10.dp, vertical = 4.dp),
                    ) { Text(sug, color = c.textoSuave, fontSize = 11.sp) }
                }
            }
        }
    }
}