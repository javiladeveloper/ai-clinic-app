package pe.saniape.app.ui.clinica.agenda.componentes

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import pe.saniape.app.data.staff.DiagnosticosRepo
import pe.saniape.app.ui.theme.Sania

/**
 * Campo de Diagnóstico / Motivo con TYPEAHEAD + chips — igual que la web
 * (components/pacientes/DiagnosticoInput.tsx).
 *
 * Texto libre con dos ayudas para escribir rápido, sin encajonar:
 *  - Typeahead: mientras escribes, sugiere las opciones que coinciden con la "palabra en
 *    curso" (lo que va desde la última coma hasta el final). Tocar una la completa,
 *    respetando lo que ya escribiste antes.
 *  - Chips debajo: tocar agrega/quita (atajo en móvil).
 *
 * [opciones] = patologías de la especialidad (chips) + lo que se quiera enriquecer.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun DiagnosticoInput(
    value: String,
    onChange: (String) -> Unit,
    opciones: List<String>,
    placeholder: String = "Ej. Lumbalgia mecánica, contractura…",
) {
    val c = Sania.colors
    var enfocado by remember { mutableStateOf(false) }
    // Diagnósticos que la clínica ya escribió antes (se enriquece solo, como las técnicas).
    var aprendidos by remember { mutableStateOf<List<String>>(emptyList()) }
    LaunchedEffect(Unit) {
        aprendidos = runCatching { DiagnosticosRepo.sugerencias() }.getOrDefault(emptyList())
    }

    fun normalizar(s: String) = s.lowercase().trim()

    // Para el typeahead: chips de la especialidad + los aprendidos, sin duplicar.
    val opcionesTypeahead = remember(opciones, aprendidos) {
        val vistos = opciones.map { normalizar(it) }.toSet()
        opciones + aprendidos.filter { normalizar(it) !in vistos }
    }

    // "Palabra en curso": desde el último separador (, ; / salto) hasta el final.
    val enCurso = remember(value) {
        value.substringAfterLast(',').substringAfterLast(';')
            .substringAfterLast('/').substringAfterLast('\n').trimStart()
    }

    // Sugerencias del typeahead: opciones que contienen lo tecleado, aún no exactas.
    val sugerencias = remember(enCurso, opcionesTypeahead) {
        val q = normalizar(enCurso)
        if (q.isEmpty()) emptyList()
        else opcionesTypeahead.filter { normalizar(it).contains(q) && normalizar(it) != q }.take(6)
    }

    // Completa la palabra en curso con la opción elegida (conserva lo anterior).
    fun completar(op: String) {
        val antes = value.dropLast(enCurso.length)
        onChange(antes + op)
    }

    // Chip: si ya está lo quita; si no, lo agrega separado por coma.
    fun toggleChip(chip: String) {
        val puesto = normalizar(value).contains(normalizar(chip))
        if (puesto) {
            onChange(
                value.split(",").map { it.trim() }
                    .filterNot { it.equals(chip, ignoreCase = true) }
                    .filter { it.isNotBlank() }.joinToString(", ")
            )
        } else {
            onChange(if (value.isBlank()) chip else "${value.trim().trimEnd(',')}, $chip")
        }
    }

    Column {
        OutlinedTextField(
            value = value,
            onValueChange = { onChange(it); enfocado = true },
            placeholder = { Text(placeholder, color = c.textoSuave) },
            modifier = Modifier.fillMaxWidth(),
            minLines = 3,
            shape = RoundedCornerShape(Sania.shape.sm.dp),
        )

        // Dropdown de sugerencias (typeahead): aparece al escribir algo que coincide.
        if (sugerencias.isNotEmpty()) {
            Column(
                Modifier.fillMaxWidth().padding(top = 4.dp)
                    .clip(RoundedCornerShape(Sania.shape.sm.dp))
                    .background(c.fondo)
                    .border(1.dp, c.borde, RoundedCornerShape(Sania.shape.sm.dp)),
            ) {
                sugerencias.forEach { s ->
                    Box(
                        Modifier.fillMaxWidth().clickable { completar(s) }
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                    ) {
                        Text(s, color = c.texto, fontSize = 13.sp)
                    }
                }
            }
        }

        // Chips de atajo (patologías de la especialidad).
        if (opciones.isNotEmpty()) {
            Spacer(Modifier.height(6.dp))
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                opciones.forEach { chip ->
                    val puesto = value.contains(chip, ignoreCase = true)
                    Box(
                        Modifier.clip(RoundedCornerShape(Sania.shape.pill.dp))
                            .background(if (puesto) c.navy else c.chipBg)
                            .border(1.dp, if (puesto) c.navy else c.borde, RoundedCornerShape(Sania.shape.pill.dp))
                            .clickable { toggleChip(chip) }
                            .padding(horizontal = 10.dp, vertical = 5.dp),
                    ) {
                        Text(
                            "${if (puesto) "✓" else "+"} $chip",
                            color = if (puesto) c.sobreNavy else c.textoSuave,
                            fontSize = 11.sp, fontWeight = FontWeight.Bold,
                        )
                    }
                }
            }
        }
    }
}
