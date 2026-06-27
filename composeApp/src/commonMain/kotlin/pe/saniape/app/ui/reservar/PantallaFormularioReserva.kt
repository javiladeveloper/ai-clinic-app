package pe.saniape.app.ui.reservar

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
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
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import pe.saniape.app.data.ClinicaDir
import pe.saniape.app.data.InfoReserva
import pe.saniape.app.data.ProfReserva
import pe.saniape.app.data.ReservaRepo
import pe.saniape.app.data.ResultadoReserva
import pe.saniape.app.ui.theme.Sania

/**
 * Formulario de reserva — con MEJORAS nativas: DatePicker y TimePicker nativos
 * de Android (en vez de escribir la fecha/hora a mano como en la web).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PantallaFormularioReserva(clinica: ClinicaDir, onAtras: () -> Unit) {
    val c = Sania.colors
    val scope = rememberCoroutineScope()

    var cargando by remember { mutableStateOf(true) }
    var info by remember { mutableStateOf<InfoReserva?>(null) }

    var dni by remember { mutableStateOf("") }
    var telefono by remember { mutableStateOf("") }
    var fecha by remember { mutableStateOf("") }      // yyyy-MM-dd
    var hora by remember { mutableStateOf("") }        // HH:mm
    var motivo by remember { mutableStateOf("") }
    var profesional by remember { mutableStateOf<ProfReserva?>(null) }
    // Si el paciente ya tiene DNI registrado, viene prellenado y bloqueado.
    var dniFijo by remember { mutableStateOf(false) }

    var mostrarFecha by remember { mutableStateOf(false) }
    var mostrarHora by remember { mutableStateOf(false) }

    var enviando by remember { mutableStateOf(false) }
    var mensaje by remember { mutableStateOf<String?>(null) }
    var exito by remember { mutableStateOf(false) }

    androidx.compose.runtime.LaunchedEffect(clinica.slug) {
        try { info = ReservaRepo.infoClinica(clinica.slug) } catch (_: Exception) {}
        // Prepoblar DNI y teléfono con lo que ya tenemos del perfil del paciente.
        try {
            val p = pe.saniape.app.data.PerfilRepo.cargar()
            if (!p?.dni.isNullOrBlank()) { dni = p!!.dni!!; dniFijo = true }
            if (!p?.telefono.isNullOrBlank()) telefono = p!!.telefono!!
        } catch (_: Exception) {}
        cargando = false
    }

    // ── Diálogo de FECHA nativo ──────────────────────────────────────────
    if (mostrarFecha) {
        val estado = rememberDatePickerState()
        DatePickerDialog(
            onDismissRequest = { mostrarFecha = false },
            confirmButton = {
                TextButton(onClick = {
                    estado.selectedDateMillis?.let { fecha = millisAFechaIso(it) }
                    mostrarFecha = false
                }) { Text("Aceptar", color = c.navy) }
            },
            dismissButton = { TextButton(onClick = { mostrarFecha = false }) { Text("Cancelar", color = c.textoSuave) } },
        ) { DatePicker(state = estado) }
    }

    // ── Diálogo de HORA nativo ───────────────────────────────────────────
    if (mostrarHora) {
        val estado = rememberTimePickerState(initialHour = 10, initialMinute = 0, is24Hour = true)
        DatePickerDialog(  // reutilizamos el contenedor de diálogo para el TimePicker
            onDismissRequest = { mostrarHora = false },
            confirmButton = {
                TextButton(onClick = {
                    hora = "${estado.hour.pad()}:${estado.minute.pad()}"
                    mostrarHora = false
                }) { Text("Aceptar", color = c.navy) }
            },
            dismissButton = { TextButton(onClick = { mostrarHora = false }) { Text("Cancelar", color = c.textoSuave) } },
        ) {
            Box(Modifier.fillMaxWidth().padding(Sania.dim.lg), Alignment.Center) {
                TimePicker(state = estado)
            }
        }
    }

    Surface(color = c.fondo, modifier = Modifier.fillMaxSize()) {
        if (exito) {
            Box(Modifier.fillMaxSize().padding(Sania.dim.xxl), Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("✅", fontSize = 48.sp)
                    Spacer(Modifier.height(Sania.dim.md))
                    Text("¡Reserva enviada!", color = c.ok, fontSize = Sania.txt.subtitulo, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(Sania.dim.sm))
                    Text("La clínica confirmará tu cita. La verás en tu pestaña Inicio.",
                        color = c.textoSuave, fontSize = Sania.txt.cuerpo,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                    Spacer(Modifier.height(Sania.dim.xl))
                    Button(
                        onClick = onAtras,
                        colors = ButtonDefaults.buttonColors(containerColor = c.navy, contentColor = c.sobreNavy),
                        shape = RoundedCornerShape(Sania.shape.md.dp),
                    ) { Text("Volver") }
                }
            }
            return@Surface
        }

        Column(Modifier.fillMaxSize()) {
            // Header
            Column(
                Modifier.fillMaxWidth().background(c.navyDark)
                    .padding(horizontal = Sania.dim.xl, vertical = Sania.dim.lg),
            ) {
                Text("← Volver", color = c.sobreNavy, fontSize = Sania.txt.pequeno,
                    modifier = Modifier.clickable { onAtras() })
                Spacer(Modifier.height(Sania.dim.sm))
                Text("Reservar en ${clinica.nombre}", color = c.sobreNavy,
                    fontSize = Sania.txt.subtitulo, fontWeight = FontWeight.Bold)
            }

            Column(
                Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(Sania.dim.xl),
            ) {
                mensaje?.let {
                    Text("⚠ $it", color = c.error, fontSize = Sania.txt.pequeno,
                        modifier = Modifier.fillMaxWidth().padding(bottom = Sania.dim.md))
                }

                // Profesional (opcional)
                info?.profesionales?.takeIf { it.isNotEmpty() }?.let { profs ->
                    Text("Profesional (opcional)", color = c.textoSuave,
                        fontSize = Sania.txt.pequeno, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(6.dp))
                    ChipProf("Cualquiera", profesional == null) { profesional = null }
                    profs.forEach { p ->
                        ChipProf(p.nombre + (p.especialidad?.let { " · $it" } ?: ""), profesional?.id == p.id) { profesional = p }
                    }
                    Spacer(Modifier.height(Sania.dim.md))
                }

                // FECHA (picker nativo)
                CampoSelector("Fecha", if (fecha.isBlank()) "Elegir fecha" else fecha) { mostrarFecha = true }
                // HORA (picker nativo)
                CampoSelector("Hora", if (hora.isBlank()) "Elegir hora" else hora) { mostrarHora = true }

                if (dniFijo) {
                    // DNI ya registrado: fijo (no se cambia desde la reserva).
                    Column(Modifier.fillMaxWidth().padding(bottom = Sania.dim.sm)) {
                        Text("DNI", color = c.textoSuave, fontSize = Sania.txt.pequeno, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.height(4.dp))
                        Row(
                            Modifier.fillMaxWidth().clip(RoundedCornerShape(Sania.shape.sm.dp))
                                .background(c.superficie).border(1.dp, c.borde, RoundedCornerShape(Sania.shape.sm.dp))
                                .padding(horizontal = 14.dp, vertical = 14.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(dni, color = c.texto, fontSize = Sania.txt.cuerpo, fontWeight = FontWeight.Bold)
                            Text("🔒", color = c.textoSuave, fontSize = Sania.txt.cuerpo)
                        }
                    }
                } else {
                    Campo("DNI", dni, { dni = it.filter { ch -> ch.isDigit() }.take(8) }, KeyboardType.Number, "12345678")
                }
                Campo("Teléfono", telefono, { telefono = it }, KeyboardType.Phone, "999 999 999")
                Campo("Motivo (opcional)", motivo, { motivo = it }, KeyboardType.Text, "¿Qué te trae?")

                Spacer(Modifier.height(Sania.dim.lg))

                Button(
                    onClick = {
                        if (enviando) return@Button
                        mensaje = null
                        if (fecha.isBlank()) { mensaje = "Elige una fecha"; return@Button }
                        if (hora.isBlank()) { mensaje = "Elige una hora"; return@Button }
                        if (dni.length != 8) { mensaje = "Ingresa un DNI válido (8 dígitos)"; return@Button }
                        if (telefono.isBlank()) { mensaje = "Indica tu teléfono"; return@Button }
                        enviando = true
                        scope.launch {
                            val r = ReservaRepo.reservar(
                                slug = clinica.slug, dni = dni, telefono = telefono,
                                fecha = fecha, hora = hora,
                                terapeutaId = profesional?.id, motivo = motivo,
                            )
                            enviando = false
                            when (r) {
                                is ResultadoReserva.Ok -> exito = true
                                is ResultadoReserva.Error -> mensaje = r.mensaje
                            }
                        }
                    },
                    enabled = !enviando,
                    shape = RoundedCornerShape(Sania.shape.md.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = c.navy, contentColor = c.sobreNavy),
                    modifier = Modifier.fillMaxWidth().height(Sania.dim.boton),
                ) {
                    if (enviando) {
                        CircularProgressIndicator(color = c.sobreNavy, strokeWidth = 2.dp,
                            modifier = Modifier.height(20.dp).padding(end = 8.dp))
                    }
                    Text("Reservar cita", fontWeight = FontWeight.Bold)
                }
                Spacer(Modifier.height(Sania.dim.xxl))
            }
        }
    }
}

@Composable
private fun CampoSelector(label: String, valor: String, onClick: () -> Unit) {
    val c = Sania.colors
    Column(Modifier.fillMaxWidth().padding(bottom = Sania.dim.sm)) {
        Text(label, color = c.textoSuave, fontSize = Sania.txt.pequeno, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(4.dp))
        Row(
            Modifier.fillMaxWidth()
                .clip(RoundedCornerShape(Sania.shape.sm.dp))
                .background(c.superficie)
                .border(1.dp, c.borde, RoundedCornerShape(Sania.shape.sm.dp))
                .clickable { onClick() }
                .padding(horizontal = 14.dp, vertical = 14.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(valor, color = c.texto, fontSize = Sania.txt.cuerpo)
            Text("▾", color = c.navy, fontSize = Sania.txt.cuerpo)
        }
    }
}

@Composable
private fun Campo(
    label: String, valor: String, onCambio: (String) -> Unit,
    tipo: KeyboardType, placeholder: String,
) {
    OutlinedTextField(
        value = valor, onValueChange = onCambio,
        label = { Text(label) },
        placeholder = { Text(placeholder, color = Sania.colors.textoSuave) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = tipo, imeAction = ImeAction.Next),
        modifier = Modifier.fillMaxWidth().padding(bottom = Sania.dim.sm),
    )
}

@Composable
private fun ChipProf(texto: String, activo: Boolean, onClick: () -> Unit) {
    val c = Sania.colors
    Row(
        Modifier.fillMaxWidth().padding(vertical = 3.dp)
            .clip(RoundedCornerShape(Sania.shape.sm.dp))
            .background(if (activo) c.chipBg else c.superficie)
            .border(1.dp, if (activo) c.navy else c.borde, RoundedCornerShape(Sania.shape.sm.dp))
            .clickable { onClick() }
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(texto, color = if (activo) c.navy else c.texto, fontSize = Sania.txt.cuerpo,
            fontWeight = if (activo) FontWeight.Bold else FontWeight.Normal)
        if (activo) Text("✓", color = c.navy, fontSize = Sania.txt.cuerpo, fontWeight = FontWeight.Bold)
    }
}

private fun Int.pad(): String = toString().padStart(2, '0')

/** Convierte millis UTC (del DatePicker) a yyyy-MM-dd en zona local. */
private fun millisAFechaIso(millis: Long): String {
    val instant = Instant.fromEpochMilliseconds(millis)
    val d = instant.toLocalDateTime(TimeZone.UTC).date  // DatePicker entrega medianoche UTC
    return "${d.year}-${d.monthNumber.pad()}-${d.dayOfMonth.pad()}"
}