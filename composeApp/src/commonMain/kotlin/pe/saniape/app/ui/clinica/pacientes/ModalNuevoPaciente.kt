package pe.saniape.app.ui.clinica.pacientes

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import pe.saniape.app.data.staff.PacienteStaff
import pe.saniape.app.data.staff.PacientesRepo
import pe.saniape.app.ui.theme.Sania

/**
 * Alta de paciente desde la app (esencial de recepción en mostrador): DNI PRIMERO con
 * búsqueda en el padrón (autocompleta el nombre, como la web), teléfono, edad y motivo.
 * Anti-duplicados: antes de crear se busca el DNI en la clínica; si ya existe, se ofrece
 * abrir su ficha en vez de duplicar.
 */
@Composable
fun ModalNuevoPaciente(
    onCancelar: () -> Unit,
    onCreado: (PacienteStaff) -> Unit,       // creado (o el existente elegido) → abrir ficha
) {
    val c = Sania.colors
    val scope = rememberCoroutineScope()
    var dni by remember { mutableStateOf("") }
    var nombre by remember { mutableStateOf("") }
    var telefono by remember { mutableStateOf("") }
    var edad by remember { mutableStateOf("") }
    var motivo by remember { mutableStateOf("") }
    var buscandoDni by remember { mutableStateOf(false) }
    var avisoDni by remember { mutableStateOf<String?>(null) }
    var existente by remember { mutableStateOf<PacienteStaff?>(null) }   // duplicado detectado
    var guardando by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    fun buscarDni() {
        val d = dni.trim()
        if (d.length != 8 || buscandoDni) return
        buscandoDni = true; avisoDni = null; existente = null
        scope.launch {
            // 1) ¿Ya existe en la clínica? (anti-duplicados)
            val ya = PacientesRepo.porDni(d)
            if (ya != null) {
                existente = ya
                avisoDni = "Ya registrado: ${ya.nombre}"
            } else {
                // 2) Padrón nacional (autocompleta el nombre).
                val n = PacientesRepo.nombrePorDni(d)
                if (n != null) { nombre = n; avisoDni = "✓ Encontrado en el padrón" }
                else avisoDni = "No encontrado — escribe el nombre manualmente"
            }
            buscandoDni = false
        }
    }

    DialogoForm(
        titulo = "Nuevo paciente",
        subtitulo = "Datos esenciales — el resto se completa en la ficha",
        textoAccion = if (guardando) "Creando…" else "Crear paciente",
        accionHabilitada = nombre.isNotBlank() && !guardando && existente == null,
        onCancelar = { if (!guardando) onCancelar() },
        onAccion = {
            if (nombre.isBlank() || guardando) return@DialogoForm
            guardando = true; error = null
            scope.launch {
                // Dedup final por si no usó el botón buscar.
                val d = dni.trim()
                val ya = if (d.length == 8) PacientesRepo.porDni(d) else null
                if (ya != null) { existente = ya; avisoDni = "Ya registrado: ${ya.nombre}"; guardando = false; return@launch }
                val creado = PacientesRepo.crearPaciente(
                    nombre = nombre, dni = d.ifBlank { null },
                    telefono = telefono.trim().ifBlank { null },
                    edad = edad.toIntOrNull(), diagnostico = motivo.trim().ifBlank { null },
                )
                guardando = false
                if (creado != null) onCreado(creado) else error = "No se pudo crear. Revisa tu conexión."
            }
        },
    ) {
        TarjetaForm(titulo = "Identidad", icono = "🪪") {
            EtqForm("DNI (busca el nombre en el padrón)")
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = dni,
                    onValueChange = { dni = it.filter { ch -> ch.isDigit() }.take(8); avisoDni = null; existente = null },
                    placeholder = { Text("8 dígitos", color = c.textoSuave) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.weight(1f),
                )
                Box(
                    Modifier.clip(RoundedCornerShape(Sania.shape.sm.dp))
                        .background(if (dni.length == 8 && !buscandoDni) c.navy else c.borde)
                        .clickable(enabled = dni.length == 8 && !buscandoDni) { buscarDni() }
                        .padding(horizontal = 14.dp, vertical = 13.dp),
                ) {
                    Text(if (buscandoDni) "…" else "🔍 Buscar", color = c.sobreNavy,
                        fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
            }
            avisoDni?.let {
                Text(it, color = if (existente != null) c.error else c.textoSuave,
                    fontSize = 11.sp, modifier = Modifier.padding(top = 4.dp))
            }
            // Duplicado: ofrecer abrir la ficha existente (nunca crear dos veces).
            existente?.let { p ->
                Spacer(Modifier.height(8.dp))
                Box(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(Sania.shape.sm.dp))
                        .background(c.navy).clickable { onCreado(p) }.padding(vertical = 10.dp),
                    contentAlignment = Alignment.Center,
                ) { Text("→ Abrir la ficha de ${p.nombre}", color = c.sobreNavy, fontSize = 12.sp, fontWeight = FontWeight.Bold) }
            }

            Spacer(Modifier.height(10.dp))
            EtqForm("Nombre completo *")
            OutlinedTextField(value = nombre, onValueChange = { nombre = it },
                placeholder = { Text("Nombres y apellidos", color = c.textoSuave) },
                singleLine = true, modifier = Modifier.fillMaxWidth())

            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Column(Modifier.weight(2f)) {
                    EtqForm("Teléfono")
                    OutlinedTextField(value = telefono, onValueChange = { telefono = it.filter { ch -> ch.isDigit() || ch == '+' } },
                        singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                        modifier = Modifier.fillMaxWidth())
                }
                Column(Modifier.weight(1f)) {
                    EtqForm("Edad")
                    OutlinedTextField(value = edad, onValueChange = { edad = it.filter { ch -> ch.isDigit() }.take(3) },
                        singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth())
                }
            }

            Spacer(Modifier.height(10.dp))
            EtqForm("Motivo (por qué viene) — opcional")
            OutlinedTextField(value = motivo, onValueChange = { motivo = it },
                placeholder = { Text("Ej. dolor lumbar, evaluación general…", color = c.textoSuave) },
                minLines = 2, modifier = Modifier.fillMaxWidth())

            error?.let {
                Text("⚠ $it", color = c.error, fontSize = 11.sp, modifier = Modifier.padding(top = 6.dp))
            }
        }
    }
}
