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
import pe.saniape.app.data.staff.alternarChip
import pe.saniape.app.data.staff.chipPuesto
import pe.saniape.app.data.staff.claveChip
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
 * [opciones] = patologías de la especialidad (chips; las personalizadas mandan).
 * [especialidadId] = la de la cita/tratamiento: trae los diagnósticos APRENDIDOS de
 * esa especialidad y, detrás, los frecuentes del rubro (pool). UNA vez al abrir
 * (caché en [pe.saniape.app.data.staff.ChipsRepo]); al escribir solo se filtra en memoria.
 * [maxExtras] = chips extra (cortos) de esos frecuentes además de [opciones]. 0 = ninguno.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun DiagnosticoInput(
    value: String,
    onChange: (String) -> Unit,
    opciones: List<String>,
    placeholder: String? = null,
    especialidadId: String? = null,
    maxExtras: Int = 6,
    /** false = no pide sugerencias ni muestra extras (p. ej. sin servicio elegido). */
    activo: Boolean = true,
) {
    val c = Sania.colors
    var enfocado by remember { mutableStateOf(false) }
    // Diagnósticos que la clínica ya escribió antes + los del rubro (se enriquece solo).
    var aprendidos by remember { mutableStateOf<List<String>>(emptyList()) }
    LaunchedEffect(especialidadId, activo) {
        aprendidos = if (!activo) emptyList()
        else runCatching { DiagnosticosRepo.sugerencias(especialidadId) }.getOrDefault(emptyList())
    }

    // Sin tildes ni mayúsculas (la clave plegada de los chips).
    fun normalizar(s: String) = claveChip(s)

    // Chips = los de la especialidad + unos pocos frecuentes CORTOS (los largos
    // quedan solo en el typeahead), como la web.
    val chips = remember(opciones, aprendidos, maxExtras) {
        if (maxExtras <= 0) opciones
        else {
            val vistos = opciones.mapTo(HashSet()) { normalizar(it) }
            opciones + aprendidos.filter { it.length <= 40 && normalizar(it) !in vistos }.take(maxExtras)
        }
    }

    // El ejemplo sale de las opciones REALES de la especialidad (antes, cableado a
    // fisioterapia, se leía "Lumbalgia" en una clínica dental).
    val ejemplo = placeholder ?: opciones.filter { it.isNotBlank() }.take(2)
        .takeIf { it.isNotEmpty() }?.let { "Ej. ${it.joinToString(", ")}…" } ?: "Escribe el diagnóstico…"

    // Para el typeahead: chips de la especialidad + los aprendidos, sin duplicar.
    val opcionesTypeahead = remember(opciones, aprendidos) {
        val vistos = opciones.mapTo(HashSet()) { normalizar(it) }
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

    // Chip: si ya está (término COMPLETO) lo quita; si no, lo agrega con coma.
    // Antes era una subcadena: quitar "Postural" dejaba "Alteración" suelto.
    fun toggleChip(chip: String) = onChange(alternarChip(value, chip))

    Column {
        OutlinedTextField(
            value = value,
            onValueChange = { onChange(it); enfocado = true },
            placeholder = { Text(ejemplo, color = c.textoSuave) },
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

        // Chips de atajo (patologías de la especialidad + frecuentes).
        if (chips.isNotEmpty()) {
            Spacer(Modifier.height(6.dp))
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                chips.forEach { chip ->
                    val puesto = chipPuesto(value, chip)
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
