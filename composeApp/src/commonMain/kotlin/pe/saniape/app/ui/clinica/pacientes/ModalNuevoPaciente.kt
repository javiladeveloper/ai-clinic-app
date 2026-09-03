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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
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
 * Alta de paciente desde la app — PARIDAD con PacienteForm de la web (2026-09-02: la
 * recepcionista registraba desde el celular y le faltaban campos). DNI PRIMERO con
 * búsqueda en el padrón; documento con país (Tacna es frontera: 1 de cada 7 pacientes
 * de DALU trae RUT chileno, no DNI — solo dígitos los dejaba sin documento y nacían
 * fichas duplicadas). Anti-duplicados por documento antes de crear. Los antecedentes
 * clínicos van colapsados para que el alta rápida siga siendo rápida.
 */
@Composable
fun ModalNuevoPaciente(
    onCancelar: () -> Unit,
    onCreado: (PacienteStaff) -> Unit,       // creado (o el existente elegido) → abrir ficha
) {
    val c = Sania.colors
    val scope = rememberCoroutineScope()
    var paisDoc by remember { mutableStateOf("PE") }         // PE / CL / OTRO
    var sinDocumento by remember { mutableStateOf(false) }
    var dni by remember { mutableStateOf("") }
    var nombre by remember { mutableStateOf("") }
    var telefono by remember { mutableStateOf("") }
    var edad by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var ocupacion by remember { mutableStateOf("") }
    var talla by remember { mutableStateOf("") }
    var peso by remember { mutableStateOf("") }
    var motivo by remember { mutableStateOf("") }
    var observaciones by remember { mutableStateOf("") }
    var flag by remember { mutableStateOf("verde") }
    // Antecedentes clínicos (colapsados por defecto: el alta rápida sigue rápida).
    var verAntecedentes by remember { mutableStateOf(false) }
    var tipoPatologia by remember { mutableStateOf("") }
    var antecedentes by remember { mutableStateOf("") }
    var sintomas by remember { mutableStateOf("") }
    var alergias by remember { mutableStateOf("") }
    var medicacion by remember { mutableStateOf("") }

    var buscandoDni by remember { mutableStateOf(false) }
    var avisoDni by remember { mutableStateOf<String?>(null) }
    var existente by remember { mutableStateOf<PacienteStaff?>(null) }   // duplicado detectado
    var guardando by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    fun buscarDni() {
        val d = dni.trim()
        if (paisDoc != "PE" || d.length != 8 || buscandoDni) return
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
        subtitulo = "Los antecedentes clínicos son opcionales",
        textoAccion = if (guardando) "Creando…" else "Crear paciente",
        accionHabilitada = nombre.isNotBlank() && !guardando && existente == null,
        onCancelar = { if (!guardando) onCancelar() },
        onAccion = {
            if (nombre.isBlank() || guardando) return@DialogoForm
            guardando = true; error = null
            scope.launch {
                // Dedup final por si no usó el botón buscar (cualquier documento).
                val d = if (sinDocumento) "" else dni.trim()
                val ya = if (d.length >= 5) PacientesRepo.porDni(d) else null
                if (ya != null) { existente = ya; avisoDni = "Ya registrado: ${ya.nombre}"; guardando = false; return@launch }
                val creado = PacientesRepo.crearPaciente(
                    nombre = nombre, dni = d.ifBlank { null },
                    telefono = telefono.trim().ifBlank { null },
                    edad = edad.toIntOrNull(), diagnostico = motivo.trim().ifBlank { null },
                    email = email.trim().ifBlank { null },
                    ocupacion = ocupacion.trim().ifBlank { null },
                    talla = talla.toIntOrNull(), peso = peso.toDoubleOrNull(),
                    observaciones = observaciones.trim().ifBlank { null },
                    flag = flag,
                    antecedentes = antecedentes.trim().ifBlank { null },
                    alergias = alergias.trim().ifBlank { null },
                    medicacionActual = medicacion.trim().ifBlank { null },
                    patologias = sintomas.split(",").map { it.trim() }.filter { it.isNotBlank() },
                    tipoPatologia = tipoPatologia.trim().ifBlank { null },
                )
                guardando = false
                if (creado != null) { pe.saniape.app.ui.Toaster.exito("Paciente registrado"); onCreado(creado) }
                else error = "No se pudo crear. Revisa tu conexión."
            }
        },
    ) {
        TarjetaForm(titulo = "Identidad", icono = "🪪") {
            // País del documento: define cómo se valida y si se busca en el padrón
            // (el padrón es peruano — para RUT/pasaporte no hay búsqueda).
            EtqForm("Documento")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("PE" to "🇵🇪 DNI", "CL" to "🇨🇱 RUT", "OTRO" to "🌎 Pasaporte").forEach { (v, etq) ->
                    val activo = paisDoc == v
                    Box(
                        Modifier.clip(RoundedCornerShape(Sania.shape.sm.dp))
                            .background(if (activo) c.navy else c.superficie)
                            .border(1.dp, if (activo) c.navy else c.borde, RoundedCornerShape(Sania.shape.sm.dp))
                            .clickable { paisDoc = v; avisoDni = null; existente = null }
                            .padding(horizontal = 12.dp, vertical = 7.dp),
                    ) {
                        Text(etq, color = if (activo) c.sobreNavy else c.textoSuave,
                            fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
            if (!sinDocumento) {
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(colors = coloresCampoForm(),
                        value = dni,
                        onValueChange = {
                            // Solo el DNI peruano es "8 dígitos": un RUT trae guion
                            // ("12345678-9") y un pasaporte letras. Filtrar todo a
                            // dígitos dejaba a los extranjeros SIN documento.
                            dni = if (paisDoc == "PE") it.filter { ch -> ch.isDigit() }.take(8) else it.take(20)
                            avisoDni = null; existente = null
                        },
                        placeholder = { Text(when (paisDoc) {
                            "PE" -> "8 dígitos"; "CL" -> "12345678-9"; else -> "Nº de pasaporte"
                        }, color = c.textoSuave) },
                        singleLine = true,
                        keyboardOptions = if (paisDoc == "PE")
                            KeyboardOptions(keyboardType = KeyboardType.Number) else KeyboardOptions.Default,
                        modifier = Modifier.weight(1f),
                    )
                    if (paisDoc == "PE") Box(
                        Modifier.clip(RoundedCornerShape(Sania.shape.sm.dp))
                            .background(if (dni.length == 8 && !buscandoDni) c.navy else c.borde)
                            .clickable(enabled = dni.length == 8 && !buscandoDni) { buscarDni() }
                            .padding(horizontal = 14.dp, vertical = 13.dp),
                    ) {
                        Text(if (buscandoDni) "…" else "🔍 Buscar", color = c.sobreNavy,
                            fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
            // Escape para quien llega sin papeles. Sin documento NI teléfono no hay
            // forma de reconocerlo cuando vuelva (así nacieron duplicados) — se
            // advierte abajo, sin bloquear.
            Row(verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.clickable { sinDocumento = !sinDocumento; if (sinDocumento) dni = "" }) {
                Checkbox(checked = sinDocumento, onCheckedChange = { sinDocumento = it; if (it) dni = "" },
                    colors = CheckboxDefaults.colors(checkedColor = c.navy))
                Text("No tiene documento", color = c.textoSuave, fontSize = 12.sp)
            }
            if ((sinDocumento || dni.isBlank()) && telefono.isBlank()) {
                Text("⚠ Sin documento ni teléfono no habrá forma de reconocerlo cuando vuelva (se puede guardar igual).",
                    color = c.pend, fontSize = 11.sp, modifier = Modifier.padding(bottom = 4.dp))
            }
            avisoDni?.let {
                Text(it, color = if (existente != null) c.error else c.textoSuave,
                    fontSize = 11.sp, modifier = Modifier.padding(top = 2.dp))
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
            OutlinedTextField(colors = coloresCampoForm(), value = nombre, onValueChange = { nombre = it },
                placeholder = { Text("Nombres y apellidos", color = c.textoSuave) },
                singleLine = true, modifier = Modifier.fillMaxWidth())

            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Column(Modifier.weight(2f)) {
                    EtqForm("Teléfono")
                    OutlinedTextField(colors = coloresCampoForm(), value = telefono, onValueChange = { telefono = it.filter { ch -> ch.isDigit() || ch == '+' } },
                        singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                        modifier = Modifier.fillMaxWidth())
                }
                Column(Modifier.weight(1f)) {
                    EtqForm("Edad")
                    OutlinedTextField(colors = coloresCampoForm(), value = edad, onValueChange = { edad = it.filter { ch -> ch.isDigit() }.take(3) },
                        singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth())
                }
            }

            Spacer(Modifier.height(10.dp))
            EtqForm("Email — opcional")
            OutlinedTextField(colors = coloresCampoForm(), value = email, onValueChange = { email = it },
                singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                modifier = Modifier.fillMaxWidth())

            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Column(Modifier.weight(2f)) {
                    EtqForm("Ocupación")
                    OutlinedTextField(colors = coloresCampoForm(), value = ocupacion, onValueChange = { ocupacion = it },
                        singleLine = true, modifier = Modifier.fillMaxWidth())
                }
                Column(Modifier.weight(1f)) {
                    EtqForm("Talla (cm)")
                    OutlinedTextField(colors = coloresCampoForm(), value = talla,
                        onValueChange = { talla = it.filter { ch -> ch.isDigit() }.take(3) },
                        singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth())
                }
                Column(Modifier.weight(1f)) {
                    EtqForm("Peso (kg)")
                    OutlinedTextField(colors = coloresCampoForm(), value = peso,
                        onValueChange = { peso = it.filter { ch -> ch.isDigit() || ch == '.' }.take(6) },
                        singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.fillMaxWidth())
                }
            }

            Spacer(Modifier.height(10.dp))
            EtqForm("Motivo (por qué viene) — opcional")
            OutlinedTextField(colors = coloresCampoForm(), value = motivo, onValueChange = { motivo = it },
                placeholder = { Text("Ej. dolor lumbar, evaluación general…", color = c.textoSuave) },
                minLines = 2, modifier = Modifier.fillMaxWidth())

            Spacer(Modifier.height(10.dp))
            EtqForm("Observaciones — opcional")
            OutlinedTextField(colors = coloresCampoForm(), value = observaciones, onValueChange = { observaciones = it },
                placeholder = { Text("Lo que quieras recordar: \"trajo radiografía\", \"la hija paga por ella\"…", color = c.textoSuave) },
                minLines = 2, modifier = Modifier.fillMaxWidth())
        }

        Spacer(Modifier.height(12.dp))
        TarjetaForm(titulo = "Comportamiento", icono = "🚦") {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(
                    Triple("verde", "Confiable", c.ok),
                    Triple("amarillo", "En observación", c.pend),
                    Triple("rojo", "Problemático", c.error),
                ).forEach { (v, etq, col) ->
                    val activo = flag == v
                    Column(
                        Modifier.weight(1f).clip(RoundedCornerShape(Sania.shape.md.dp))
                            .background(if (activo) col.copy(alpha = 0.15f) else c.superficie)
                            .border(1.5.dp, if (activo) col else c.borde, RoundedCornerShape(Sania.shape.md.dp))
                            .clickable { flag = v }.padding(vertical = 10.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Box(Modifier.width(10.dp).height(10.dp).clip(CircleShape).background(col))
                        Spacer(Modifier.height(4.dp))
                        Text(etq, color = if (activo) col else c.textoSuave, fontSize = 10.sp,
                            fontWeight = FontWeight.Bold, maxLines = 1)
                    }
                }
            }
        }

        Spacer(Modifier.height(12.dp))
        // Antecedentes clínicos, colapsables (como el <details> de la web): el alta
        // rápida en mostrador no debe pelearse con 5 campos que casi nunca se llenan ahí.
        Row(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(Sania.shape.md.dp))
                .background(c.superficie).border(1.dp, c.borde, RoundedCornerShape(Sania.shape.md.dp))
                .clickable { verAntecedentes = !verAntecedentes }.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("🩺", fontSize = 15.sp)
            Spacer(Modifier.width(7.dp))
            Text("Antecedentes clínicos — opcional", color = c.texto, fontSize = 14.sp,
                fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            Text(if (verAntecedentes) "▲" else "▼", color = c.textoSuave, fontSize = 12.sp)
        }
        if (verAntecedentes) {
            Spacer(Modifier.height(8.dp))
            TarjetaForm(titulo = "Antecedentes clínicos", icono = "🩺") {
                EtqForm("Tipo de patología")
                OutlinedTextField(colors = coloresCampoForm(), value = tipoPatologia, onValueChange = { tipoPatologia = it },
                    placeholder = { Text("Ej. Traumatológica, Neurológica…", color = c.textoSuave) },
                    singleLine = true, modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(10.dp))
                EtqForm("Antecedentes médicos")
                OutlinedTextField(colors = coloresCampoForm(), value = antecedentes, onValueChange = { antecedentes = it },
                    placeholder = { Text("Cirugías previas, enfermedades crónicas…", color = c.textoSuave) },
                    minLines = 2, modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(10.dp))
                EtqForm("Síntomas (separa con comas)")
                OutlinedTextField(colors = coloresCampoForm(), value = sintomas, onValueChange = { sintomas = it },
                    placeholder = { Text("Dolor lumbar, cervicalgia…", color = c.textoSuave) },
                    singleLine = true, modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Column(Modifier.weight(1f)) {
                        EtqForm("Alergias")
                        OutlinedTextField(colors = coloresCampoForm(), value = alergias, onValueChange = { alergias = it },
                            placeholder = { Text("Látex, AINES…", color = c.textoSuave) },
                            singleLine = true, modifier = Modifier.fillMaxWidth())
                    }
                    Column(Modifier.weight(1f)) {
                        EtqForm("Medicación actual")
                        OutlinedTextField(colors = coloresCampoForm(), value = medicacion, onValueChange = { medicacion = it },
                            placeholder = { Text("Ibuprofeno 400mg…", color = c.textoSuave) },
                            singleLine = true, modifier = Modifier.fillMaxWidth())
                    }
                }
            }
        }

        error?.let {
            Text("⚠ $it", color = c.error, fontSize = 11.sp, modifier = Modifier.padding(top = 6.dp))
        }
    }
}
