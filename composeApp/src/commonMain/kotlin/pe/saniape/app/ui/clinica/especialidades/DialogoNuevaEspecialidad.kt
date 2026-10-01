package pe.saniape.app.ui.clinica.especialidades

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import pe.saniape.app.data.staff.EspecialidadApp
import pe.saniape.app.data.staff.EspecialidadesRepo
import pe.saniape.app.ui.Toaster
import pe.saniape.app.ui.clinica.chipsDeEspecialidad
import pe.saniape.app.ui.clinica.pacientes.DialogoForm
import pe.saniape.app.ui.clinica.pacientes.EtqForm
import pe.saniape.app.ui.clinica.pacientes.coloresCampoForm
import pe.saniape.app.ui.theme.Sania

/** Los rubros que la base conoce (enum `rubro_clinico`). Gemelo de `RUBROS` en lib/flujo.ts. */
internal val RUBROS: List<String> = listOf(
    "fisioterapia", "odontologia", "estetica", "nutricion", "psicologia",
    "capilar", "medicina_general", "ginecologia", "veterinaria", "otro",
)

/** Nombre visible de cada rubro. Gemelo de `NOMBRE_RUBRO` en lib/flujo.ts. */
internal val NOMBRE_RUBRO: Map<String, String> = mapOf(
    "fisioterapia" to "🏃 Fisioterapia / Rehabilitación",
    "odontologia" to "🦷 Odontología",
    "estetica" to "💆 Estética / Dermatología",
    "nutricion" to "🥗 Nutrición",
    "psicologia" to "🧠 Psicología",
    "capilar" to "💇 Capilar",
    "medicina_general" to "🩺 Medicina general",
    "ginecologia" to "🌸 Ginecología",
    "veterinaria" to "🐾 Veterinaria",
    "otro" to "Otro",
)

/**
 * Alta de una especialidad (gemelo del modal "Nueva especialidad" de
 * app/(app)/especialidades/page.tsx): nombre, tipo (rubro) y etiquetas de patología.
 * Tipo vacío = "Automático": lo deduce el trigger de la base por el nombre.
 * Las etiquetas siguen las sugeridas por el nombre hasta que la persona las toca;
 * sin tocar no se guardan (la especialidad sigue las sugerencias del sistema).
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun DialogoNuevaEspecialidad(
    onCancelar: () -> Unit,
    onCreada: (EspecialidadApp) -> Unit,
) {
    val c = Sania.colors
    val scope = rememberCoroutineScope()

    var nombre by remember { mutableStateOf("") }
    var rubro by remember { mutableStateOf<String?>(null) }
    var chips by remember { mutableStateOf(chipsDeEspecialidad("").tipos) }
    var chipsTocados by remember { mutableStateOf(false) }
    var nuevoChip by remember { mutableStateOf("") }
    var guardando by remember { mutableStateOf(false) }

    fun agregarChip() {
        val t = nuevoChip.trim()
        if (t.isEmpty()) return
        if (chips.none { it.equals(t, ignoreCase = true) }) {
            chips = chips + t
            chipsTocados = true
        }
        nuevoChip = ""
    }

    fun crear() {
        if (guardando || nombre.isBlank()) return
        guardando = true
        val chipsAGuardar = if (chipsTocados) chips else null
        scope.launch {
            val r = try {
                EspecialidadesRepo.crear(nombre, rubro, chipsAGuardar)
            } finally {
                guardando = false
            }
            r.onSuccess {
                Toaster.exito("Especialidad creada")
                onCreada(it)
            }.onFailure { e ->
                val msg = e.message.orEmpty()
                Toaster.error(
                    when {
                        msg.contains("duplicate", ignoreCase = true) || msg.contains("23505") ->
                            "Ya existe una especialidad con ese nombre."
                        msg.contains("row-level security", ignoreCase = true) || msg.contains("42501") ->
                            "No tienes permiso para crear especialidades."
                        else -> "No se pudo crear la especialidad. Revisa tu conexión e intenta de nuevo."
                    }
                )
            }
        }
    }

    DialogoForm(
        titulo = "Nueva especialidad",
        subtitulo = "Luego te sugerimos sus servicios para cargarlos de una vez",
        textoAccion = if (guardando) "Creando…" else "Crear",
        accionHabilitada = !guardando && nombre.isNotBlank(),
        onCancelar = onCancelar,
        onAccion = { crear() },
    ) {
        EtqForm("Nombre de la especialidad *")
        OutlinedTextField(
            value = nombre,
            onValueChange = {
                nombre = it
                // Mientras no se hayan editado, las etiquetas siguen al nombre (como la web).
                if (!chipsTocados) chips = chipsDeEspecialidad(it).tipos
            },
            placeholder = { Text("Ej. Cardiología, Odontología, Fisioterapia…") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
            colors = coloresCampoForm(),
            modifier = Modifier.fillMaxWidth(),
        )

        Spacer(Modifier.height(Sania.dim.lg))
        EtqForm("Tipo")
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            ChipRubro("Automático (según el nombre)", sel = rubro == null) { rubro = null }
            RUBROS.forEach { r ->
                ChipRubro(NOMBRE_RUBRO[r] ?: r, sel = rubro == r) { rubro = r }
            }
        }
        Text(
            "Define qué herramientas trae: odontología agrega el odontograma y el presupuesto dental.",
            color = c.textoSuave, fontSize = Sania.txt.mini,
            modifier = Modifier.padding(top = 6.dp),
        )

        Spacer(Modifier.height(Sania.dim.lg))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.weight(1f)) { EtqForm("Tipos de patología / motivo") }
            if (chipsTocados) {
                Text(
                    "↻ Restablecer sugeridas", color = c.navy, fontSize = Sania.txt.mini,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.clip(RoundedCornerShape(Sania.shape.sm.dp))
                        .clickable {
                            chips = chipsDeEspecialidad(nombre).tipos
                            chipsTocados = false
                        }
                        .padding(horizontal = 6.dp, vertical = 4.dp),
                )
            }
        }
        Text(
            "Aparecen como botones rápidos en la ficha del paciente. " +
                if (chipsTocados) "Personalizadas por tu clínica." else "Sugeridas por el sistema.",
            color = c.textoSuave, fontSize = Sania.txt.mini,
            modifier = Modifier.padding(bottom = 6.dp),
        )
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            chips.forEach { chip ->
                Row(
                    Modifier.clip(RoundedCornerShape(Sania.shape.pill.dp)).background(c.chipBg)
                        .border(1.dp, c.borde, RoundedCornerShape(Sania.shape.pill.dp))
                        .clickable {
                            chips = chips - chip
                            chipsTocados = true
                        }
                        .padding(horizontal = 10.dp, vertical = 5.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(chip, color = c.navy, fontSize = Sania.txt.mini, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.width(4.dp))
                    Text("✕", color = c.textoSuave, fontSize = Sania.txt.mini)
                }
            }
        }
        if (chips.isEmpty()) {
            Text(
                "Sin etiquetas — los pacientes solo verán el campo de texto libre",
                color = c.textoSuave, fontSize = Sania.txt.mini, fontStyle = FontStyle.Italic,
            )
        }
        Spacer(Modifier.height(Sania.dim.sm))
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = nuevoChip,
                onValueChange = { nuevoChip = it },
                placeholder = { Text("Ej. Lesión deportiva, Ansiedad…") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(
                    capitalization = KeyboardCapitalization.Sentences,
                    imeAction = ImeAction.Done,
                ),
                keyboardActions = KeyboardActions(onDone = { agregarChip() }),
                colors = coloresCampoForm(),
                modifier = Modifier.weight(1f),
            )
            Spacer(Modifier.width(Sania.dim.sm))
            val puede = nuevoChip.isNotBlank()
            Box(
                Modifier.clip(RoundedCornerShape(Sania.shape.md.dp))
                    .background(if (puede) c.navy else c.borde)
                    .clickable(enabled = puede) { agregarChip() }
                    .padding(horizontal = Sania.dim.lg, vertical = 14.dp),
            ) {
                Text(
                    "+ Agregar", color = if (puede) c.sobreNavy else c.textoSuave,
                    fontWeight = FontWeight.Bold, fontSize = Sania.txt.cuerpo,
                )
            }
        }
    }
}

@Composable
private fun ChipRubro(texto: String, sel: Boolean, onClick: () -> Unit) {
    val c = Sania.colors
    Box(
        Modifier.clip(RoundedCornerShape(Sania.shape.pill.dp))
            .background(if (sel) c.navy else c.superficie)
            .border(1.dp, if (sel) c.navy else c.borde, RoundedCornerShape(Sania.shape.pill.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 7.dp),
    ) {
        Text(
            texto, color = if (sel) c.sobreNavy else c.texto,
            fontSize = Sania.txt.mini, fontWeight = FontWeight.Bold,
        )
    }
}
