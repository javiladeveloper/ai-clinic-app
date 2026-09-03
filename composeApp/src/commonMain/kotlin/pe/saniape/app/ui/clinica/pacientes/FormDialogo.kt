package pe.saniape.app.ui.clinica.pacientes

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextFieldColors
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlinx.datetime.toLocalDateTime
import pe.saniape.app.ui.theme.Sania

/**
 * Esquema ESTÁNDAR de los popups de la ficha (igual que Nuevo/Editar tratamiento):
 * Dialog full-width, header navy con título+subtítulo, cuerpo scrolleable, footer fijo
 * con Cancelar + botón de acción a ancho completo. Uniformiza TODOS los modales.
 */
@Composable
fun DialogoForm(
    titulo: String,
    subtitulo: String?,
    textoAccion: String,
    accionHabilitada: Boolean = true,
    onCancelar: () -> Unit,
    onAccion: () -> Unit,
    contenido: @Composable ColumnScope.() -> Unit,
) {
    val c = Sania.colors
    Dialog(onDismissRequest = onCancelar, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Column(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp).heightIn(max = 720.dp)
                .clip(RoundedCornerShape(Sania.shape.lg.dp)).background(c.fondo),
        ) {
            // Header navy
            Column(Modifier.fillMaxWidth().background(c.navyDark).padding(horizontal = 18.dp, vertical = 16.dp)) {
                Text(titulo, color = c.sobreNavy, fontSize = 19.sp, fontWeight = FontWeight.Bold)
                subtitulo?.let {
                    Text(it, color = c.sobreNavy.copy(alpha = 0.7f), fontSize = 12.sp, modifier = Modifier.padding(top = 2.dp))
                }
            }
            // Cuerpo
            Column(
                Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                content = contenido,
            )
            // Footer
            Box(Modifier.fillMaxWidth().height(1.dp).background(c.borde))
            Row(
                Modifier.fillMaxWidth().background(c.superficie).padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                TextButton(onClick = onCancelar) { Text("Cancelar", color = c.textoSuave, fontWeight = FontWeight.Bold) }
                Box(
                    Modifier.weight(1f).clip(RoundedCornerShape(Sania.shape.md.dp))
                        .background(if (accionHabilitada) c.navy else c.borde)
                        .clickable(enabled = accionHabilitada) { onAccion() }.padding(vertical = 13.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(textoAccion, color = if (accionHabilitada) c.sobreNavy else c.textoSuave,
                        fontWeight = FontWeight.Bold, fontSize = 15.sp)
                }
            }
        }
    }
}

/** Tarjeta de sección con título e ícono — agrupa campos relacionados (esquema estándar). */
@Composable
fun TarjetaForm(titulo: String, icono: String, contenido: @Composable ColumnScope.() -> Unit) {
    val c = Sania.colors
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(Sania.shape.md.dp))
            .background(c.superficie).border(1.dp, c.borde, RoundedCornerShape(Sania.shape.md.dp)).padding(14.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(bottom = 12.dp)) {
            Text(icono, fontSize = 15.sp)
            Spacer(Modifier.width(7.dp))
            Text(titulo, color = c.texto, fontSize = 14.sp, fontWeight = FontWeight.Bold)
        }
        contenido()
    }
}

/**
 * Colores estándar de los campos de texto del flujo de pacientes. Sin esto,
 * OutlinedTextField usa el colorScheme por defecto de Material3 (no el tema
 * Sania): el texto tecleado quedaba lavado/casi invisible — se veía "borroso",
 * sobre todo en tema oscuro (reporte DALU 2026-07-23, campo DNI del alta).
 */
@Composable
fun coloresCampoForm(): TextFieldColors {
    val c = Sania.colors
    return OutlinedTextFieldDefaults.colors(
        focusedTextColor = c.texto, unfocusedTextColor = c.texto,
        cursorColor = c.navy,
        focusedBorderColor = c.navy, unfocusedBorderColor = c.borde,
        focusedContainerColor = c.superficie, unfocusedContainerColor = c.superficie,
    )
}

/** Etiqueta de campo (MAYÚSCULAS, espaciado) — esquema estándar. */
@Composable
fun EtqForm(t: String) {
    Text(t.uppercase(), color = Sania.colors.textoSuave, fontSize = 10.sp, fontWeight = FontWeight.Bold,
        letterSpacing = 0.5.sp, modifier = Modifier.padding(bottom = 5.dp))
}

/** Caja tocable que muestra el valor elegido y abre un selector — esquema estándar. */
@Composable
fun CajaSelectorForm(valor: String, onClick: () -> Unit) {
    val c = Sania.colors
    Box(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(Sania.shape.sm.dp))
            .background(c.superficie).border(1.dp, c.borde, RoundedCornerShape(Sania.shape.sm.dp))
            .clickable { onClick() }.padding(horizontal = 12.dp, vertical = 13.dp),
    ) { Text(valor, color = c.texto, fontSize = 14.sp) }
}

/**
 * Selector de FECHA nativo (DatePickerDialog M3) que devuelve "AAAA-MM-DD".
 * Compartido por todos los formularios: la fecha NUNCA se teclea como texto
 * (un typo en "2026-9-3" guardaba cualquier cosa — reporte de paridad 2026-09-02).
 */
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun DialogoFecha(onElegir: (String) -> Unit, onCerrar: () -> Unit) {
    val c = Sania.colors
    val estadoP = androidx.compose.material3.rememberDatePickerState()
    androidx.compose.material3.DatePickerDialog(
        onDismissRequest = onCerrar,
        confirmButton = {
            TextButton(onClick = {
                estadoP.selectedDateMillis?.let { ms ->
                    val d = kotlinx.datetime.Instant.fromEpochMilliseconds(ms)
                        .toLocalDateTime(kotlinx.datetime.TimeZone.UTC).date
                    onElegir("${d.year}-${d.monthNumber.toString().padStart(2, '0')}-${d.dayOfMonth.toString().padStart(2, '0')}")
                }
                onCerrar()
            }) { Text("Aceptar", color = c.navy) }
        },
        dismissButton = { TextButton(onClick = onCerrar) { Text("Cancelar", color = c.textoSuave) } },
    ) { androidx.compose.material3.DatePicker(state = estadoP) }
}

/** Selector de HORA nativo (12h en pantalla, devuelve "HH:MM" en 24h). */
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun DialogoHora(horaInicial: String, onElegir: (String) -> Unit, onCerrar: () -> Unit) {
    val c = Sania.colors
    val partes = horaInicial.split(":")
    val estadoP = androidx.compose.material3.rememberTimePickerState(
        initialHour = partes.getOrNull(0)?.toIntOrNull() ?: 9,
        initialMinute = partes.getOrNull(1)?.toIntOrNull() ?: 0,
        is24Hour = false,
    )
    androidx.compose.material3.DatePickerDialog(
        onDismissRequest = onCerrar,
        confirmButton = {
            TextButton(onClick = {
                onElegir("${estadoP.hour.toString().padStart(2, '0')}:${estadoP.minute.toString().padStart(2, '0')}")
                onCerrar()
            }) { Text("Aceptar", color = c.navy) }
        },
        dismissButton = { TextButton(onClick = onCerrar) { Text("Cancelar", color = c.textoSuave) } },
    ) {
        Box(Modifier.fillMaxWidth().padding(18.dp), contentAlignment = Alignment.Center) {
            androidx.compose.material3.TimePicker(state = estadoP)
        }
    }
}

/**
 * Chips de DURACIÓN (grilla fluida). En la web es un select de 15 min a 12 h;
 * en el celular chips con las duraciones reales de una clínica — cubren el 99%
 * de las citas sin un dropdown de 20 opciones.
 */
@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
fun ChipsDuracion(
    valor: Int,
    onChange: (Int) -> Unit,
    opciones: List<Int> = listOf(15, 30, 45, 60, 90, 120),
) {
    val c = Sania.colors
    androidx.compose.foundation.layout.FlowRow(
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        opciones.forEach { d ->
            val activo = valor == d
            Box(
                Modifier.clip(RoundedCornerShape(Sania.shape.sm.dp))
                    .background(if (activo) c.navy else c.superficie)
                    .border(1.dp, if (activo) c.navy else c.borde, RoundedCornerShape(Sania.shape.sm.dp))
                    .clickable { onChange(d) }.padding(horizontal = 14.dp, vertical = 8.dp),
            ) {
                Text(
                    if (d < 60) "$d min" else if (d % 60 == 0) "${d / 60} h" else "${d / 60} h ${d % 60}",
                    color = if (activo) c.sobreNavy else c.texto, fontSize = 12.sp,
                    fontWeight = if (activo) FontWeight.Bold else FontWeight.Normal,
                )
            }
        }
    }
}

/**
 * Métodos de pago de la CLÍNICA (tabla metodos_pago) con fallback a los de
 * siempre en el primer render o sin red — paridad con useMetodosPago de la web.
 * Los chips hardcodeados ignoraban los métodos que la clínica configuró.
 */
@Composable
fun rememberMetodosPago(): List<String> {
    val estado = androidx.compose.runtime.remember {
        androidx.compose.runtime.mutableStateOf(listOf("Efectivo", "Yape", "Plin", "BCP", "Transferencia", "Otro"))
    }
    androidx.compose.runtime.LaunchedEffect(Unit) {
        runCatching { pe.saniape.app.data.staff.CatalogosCobroRepo.nombresMetodos() }
            .getOrNull()?.takeIf { it.isNotEmpty() }?.let { estado.value = it }
    }
    return estado.value
}

/** Chips de MÉTODO DE PAGO desde la configuración de la clínica (con fallback). */
@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
fun ChipsMetodoPago(seleccionado: String, onElegir: (String) -> Unit) {
    val c = Sania.colors
    val metodos = rememberMetodosPago()
    androidx.compose.foundation.layout.FlowRow(
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        metodos.forEach { m ->
            val activo = seleccionado == m
            Box(
                Modifier.clip(RoundedCornerShape(Sania.shape.pill.dp))
                    .background(if (activo) c.navy else c.superficie)
                    .border(1.dp, if (activo) c.navy else c.borde, RoundedCornerShape(Sania.shape.pill.dp))
                    .clickable { onElegir(m) }.padding(horizontal = 12.dp, vertical = 7.dp),
            ) {
                Text(m, color = if (activo) c.sobreNavy else c.texto, fontSize = 12.sp,
                    fontWeight = if (activo) FontWeight.Bold else FontWeight.Normal)
            }
        }
    }
}
