package pe.saniape.app.ui

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
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import pe.saniape.app.data.InfoReserva
import pe.saniape.app.data.PortalRepo
import pe.saniape.app.data.ProfReserva
import pe.saniape.app.data.ReservaRepo
import pe.saniape.app.data.ResultadoReserva

/**
 * Tab Reservar — formulario de reserva contra el API web (lógica segura: verifica
 * DNI con MaxFind, rate-limit, valida horario). Usa la clínica habitual del paciente.
 */
@Composable
fun PantallaReservar() {
    var cargando by remember { mutableStateOf(true) }
    var slug by remember { mutableStateOf<String?>(null) }
    var info by remember { mutableStateOf<InfoReserva?>(null) }

    // Campos del formulario
    var dni by remember { mutableStateOf("") }
    var telefono by remember { mutableStateOf("") }
    var fecha by remember { mutableStateOf("") }     // yyyy-MM-dd
    var hora by remember { mutableStateOf("") }       // HH:mm
    var motivo by remember { mutableStateOf("") }
    var profesional by remember { mutableStateOf<ProfReserva?>(null) }

    var enviando by remember { mutableStateOf(false) }
    var mensaje by remember { mutableStateOf<String?>(null) }
    var exito by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        try {
            // Asegura que ya cargamos las citas (de ahí sale la clínica habitual).
            if (PortalRepo.clinicaHabitualSlug == null) PortalRepo.misCitas()
            val s = PortalRepo.clinicaHabitualSlug
            slug = s
            if (s != null) {
                info = ReservaRepo.infoClinica(s)
                if (info == null) mensaje = "No se pudo cargar la información de tu clínica. Revisa tu conexión."
            }
        } catch (e: Exception) {
            mensaje = "Error al cargar: ${e.message}"
        } finally { cargando = false }
    }

    Surface(color = Sand, modifier = Modifier.fillMaxSize()) {
        when {
            cargando -> Box(Modifier.fillMaxSize(), Alignment.Center) {
                CircularProgressIndicator(color = Navy)
            }
            slug == null || info == null -> Box(Modifier.fillMaxSize().padding(28.dp), Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("📅", fontSize = 44.sp)
                    Spacer(Modifier.height(12.dp))
                    Text("Reservar una cita", color = Navy, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(8.dp))
                    Text(
                        // Si hay slug pero no info → fue un error de carga (mensaje real).
                        // Si no hay slug → de verdad no tiene clínica vinculada.
                        mensaje ?: if (slug == null)
                            "Aún no tienes una clínica vinculada. Reserva primero desde la web con tu clínica y luego podrás reservar aquí."
                        else "No se pudo cargar tu clínica. Intenta de nuevo.",
                        color = if (mensaje != null) RedDanger else Muted,
                        fontSize = 14.sp, textAlign = TextAlign.Center,
                    )
                }
            }
            exito -> Box(Modifier.fillMaxSize().padding(28.dp), Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("✅", fontSize = 48.sp)
                    Spacer(Modifier.height(12.dp))
                    Text("¡Reserva enviada!", color = GreenOk, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "La clínica confirmará tu cita. La verás en tu pestaña Inicio.",
                        color = Muted, fontSize = 14.sp, textAlign = TextAlign.Center,
                    )
                }
            }
            else -> {
                val data = info!!
                Column(
                    Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp),
                ) {
                    Text("Reservar una cita", color = Navy, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                    Text(data.clinicaNombre, color = Muted, fontSize = 14.sp)
                    data.direccion?.let { Text("📍 $it", color = Muted, fontSize = 12.sp) }

                    Spacer(Modifier.height(18.dp))

                    mensaje?.let {
                        Text(
                            "⚠ $it", color = RedDanger, fontSize = 13.sp,
                            modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
                        )
                    }

                    // Profesional (opcional)
                    if (data.profesionales.isNotEmpty()) {
                        Etiqueta("Profesional (opcional)")
                        SelectorProfesional(
                            profesionales = data.profesionales,
                            elegido = profesional,
                            onElegir = { profesional = it },
                        )
                        Spacer(Modifier.height(14.dp))
                    }

                    Campo("Fecha (AAAA-MM-DD)", fecha, { fecha = it }, KeyboardType.Number, "2026-07-01")
                    Campo("Hora (HH:MM)", hora, { hora = it }, KeyboardType.Number, "10:30")
                    Campo("DNI", dni, { dni = it.filter { c -> c.isDigit() }.take(8) }, KeyboardType.Number, "12345678")
                    Campo("Teléfono", telefono, { telefono = it }, KeyboardType.Phone, "999 999 999")
                    Campo("Motivo (opcional)", motivo, { motivo = it }, KeyboardType.Text, "¿Qué te trae?")

                    Spacer(Modifier.height(18.dp))

                    Button(
                        onClick = {
                            if (enviando) return@Button
                            mensaje = null
                            if (dni.length != 8) { mensaje = "Ingresa un DNI válido (8 dígitos)"; return@Button }
                            if (telefono.isBlank()) { mensaje = "Indica tu teléfono"; return@Button }
                            if (!Regex("""\d{4}-\d{2}-\d{2}""").matches(fecha)) { mensaje = "Fecha inválida (AAAA-MM-DD)"; return@Button }
                            if (!Regex("""\d{2}:\d{2}""").matches(hora)) { mensaje = "Hora inválida (HH:MM)"; return@Button }
                            enviando = true
                            scope.launch {
                                val r = ReservaRepo.reservar(
                                    slug = slug!!, dni = dni, telefono = telefono,
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
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Navy, contentColor = Blanco),
                        modifier = Modifier.fillMaxWidth().height(52.dp),
                    ) {
                        if (enviando) {
                            CircularProgressIndicator(
                                color = Blanco, strokeWidth = 2.dp,
                                modifier = Modifier.height(20.dp).padding(end = 8.dp),
                            )
                        }
                        Text("Reservar cita", fontWeight = FontWeight.Bold)
                    }
                    Spacer(Modifier.height(40.dp))
                }
            }
        }
    }
}

@Composable
private fun Etiqueta(t: String) {
    Text(t, color = Muted, fontSize = 12.sp, fontWeight = FontWeight.Bold)
}

@Composable
private fun Campo(
    label: String,
    valor: String,
    onCambio: (String) -> Unit,
    tipo: KeyboardType,
    placeholder: String,
) {
    OutlinedTextField(
        value = valor,
        onValueChange = onCambio,
        label = { Text(label) },
        placeholder = { Text(placeholder, color = Muted) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = tipo, imeAction = ImeAction.Next),
        modifier = Modifier.fillMaxWidth().padding(bottom = 10.dp),
    )
}

@Composable
private fun SelectorProfesional(
    profesionales: List<ProfReserva>,
    elegido: ProfReserva?,
    onElegir: (ProfReserva?) -> Unit,
) {
    Column(Modifier.fillMaxWidth()) {
        Spacer(Modifier.height(6.dp))
        // "Cualquiera" + lista
        ChipProf("Cualquiera", elegido == null) { onElegir(null) }
        profesionales.forEach { p ->
            ChipProf(p.nombre + (p.especialidad?.let { " · $it" } ?: ""), elegido?.id == p.id) { onElegir(p) }
        }
    }
}

@Composable
private fun ChipProf(texto: String, activo: Boolean, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 3.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(if (activo) Navy50 else Blanco)
            .border(1.dp, if (activo) Navy else BorderColor, RoundedCornerShape(10.dp))
            .clickable { onClick() }
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(texto, color = if (activo) Navy else TextoPrincipal, fontSize = 14.sp,
            fontWeight = if (activo) FontWeight.Bold else FontWeight.Normal)
        if (activo) Text("✓", color = Navy, fontSize = 14.sp, fontWeight = FontWeight.Bold)
    }
}