package pe.saniape.app.ui.clinica.agenda.modales

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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import pe.saniape.app.data.staff.CitaStaff
import pe.saniape.app.data.staff.FilaPago
import pe.saniape.app.data.staff.PartePago
import pe.saniape.app.data.staff.filasIniciales
import pe.saniape.app.data.staff.repartoValido
import pe.saniape.app.ui.clinica.pacientes.ChipsMetodoPago
import pe.saniape.app.ui.clinica.pacientes.DialogoForm
import pe.saniape.app.ui.clinica.pacientes.rememberMetodoPagoInicial
import pe.saniape.app.ui.clinica.pacientes.rememberMetodosPago
import pe.saniape.app.ui.fechaDMA
import pe.saniape.app.ui.theme.Sania
import pe.saniape.app.tutoriales.tourAncla

/**
 * "💵 Registrar cobro" de una Consulta/Evaluación (gemelo del modal de /citas web).
 *
 * Pide el MÉTODO (antes todo se anotaba como efectivo y el arqueo salía mal) y la
 * FECHA DEL PAGO: por defecto el día de la cita, pero recepción la cambia cuando el
 * paciente pagó otro día (caso DALU 30/09/2026: pagó hoy la evaluación de mañana, y
 * el ingreso debe caer en la caja del día en que de verdad entró el dinero).
 *
 * Tres destinos del cobro (Renova 2026-08-26): cobrar normal, abonarlo al
 * tratamiento del paciente, o no cobrar (la cita queda en S/ 0 y saldada).
 * [onConfirmar] recibe (método, modo, fecha yyyy-MM-dd, pagos). Sin botón de
 * tarjeta: la app no tiene aquí el flujo de QR de la web.
 *
 * COBRO DIVIDIDO (DALU 2026-10-03): con "Pagó con más de un medio" se reparte el
 * monto en 2 a 4 medios (S/ 20 Efectivo + S/ 20 Yape) y `pagos` llega con las
 * partes validadas; si no, llega null y vale `metodo`. Cobrar se habilita solo
 * cuando el reparto cuadra al céntimo. Las filas viven fuera del `if` del modo:
 * si un cobro responde "incierto", el reintento sale con el MISMO reparto.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ModalCobrarCita(
    cita: CitaStaff,
    onCancelar: () -> Unit,
    onConfirmar: (metodo: String, modo: String, fecha: String, pagos: List<PartePago>?) -> Unit,
    guardando: Boolean,
    /** Cómo llama el flujo de la clínica al tipo de la cita ("Diagnóstico"…). */
    nombreTipo: String = cita.tipo ?: "Cita",
) {
    val c = Sania.colors
    var modo by remember(cita.id) { mutableStateOf("cobrar") }
    var metodo by rememberMetodoPagoInicial(cita.pacienteId)
    var fecha by remember(cita.id) { mutableStateOf(cita.fecha.take(10).ifBlank { pe.saniape.app.ui.clinica.agenda.hoyIso() }) }
    var mostrarFecha by remember { mutableStateOf(false) }
    val metodos = rememberMetodosPago()
    var dividido by remember(cita.id) { mutableStateOf(false) }
    var filas by remember(cita.id) { mutableStateOf<List<FilaPago>>(emptyList()) }
    val total = cita.costo ?: 0.0
    val conDivision = dividido && modo != "gratis"
    val reparto = if (conDivision) repartoValido(filas, total) else null
    val monto = textoSoles(cita.costo ?: 0.0)
    val tipoMin = nombreTipo.lowercase()

    if (mostrarFecha) {
        val estado = rememberDatePickerState(initialSelectedDateMillis = pe.saniape.app.data.staff.isoAMillisUtc(fecha))
        DatePickerDialog(
            onDismissRequest = { mostrarFecha = false },
            confirmButton = {
                TextButton(onClick = { estado.selectedDateMillis?.let { fecha = millisAIso(it) }; mostrarFecha = false }) {
                    Text("Aceptar", color = c.navy)
                }
            },
            dismissButton = { TextButton(onClick = { mostrarFecha = false }) { Text("Cancelar", color = c.textoSuave) } },
        ) { DatePicker(state = estado) }
    }

    DialogoForm(
        titulo = "💵 Registrar cobro",
        subtitulo = null,
        textoAccion = when {
            guardando -> "Guardando…"
            modo == "gratis" -> "Marcar sin costo"
            modo == "abonar" -> "Cobrar y abonar $monto"
            else -> "Cobrar $monto"
        },
        accionHabilitada = !guardando && (!conDivision || reparto != null),
        ancla = "cobro",
        anclaAccion = "cobro.confirmar",
        onCancelar = { if (!guardando) onCancelar() },
        onAccion = {
            if (conDivision) reparto?.let { onConfirmar(metodo, modo, fecha, it) }
            else onConfirmar(metodo, modo, fecha, null)
        },
    ) {
        // Qué se cobra: tipo · paciente, y el monto grande.
        Column(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(Sania.shape.md.dp)).background(c.okBg)
                .border(1.dp, c.ok, RoundedCornerShape(Sania.shape.md.dp))
                .padding(horizontal = 16.dp, vertical = 12.dp),
        ) {
            Text("$nombreTipo · ${cita.pacienteNombre ?: "Paciente"}", color = c.textoSuave, fontSize = 12.sp)
            Text(monto, color = c.texto, fontSize = 28.sp, fontWeight = FontWeight.Bold)
        }

        Spacer(Modifier.height(12.dp))

        // ¿A dónde va este cobro? Si el paciente decidió entrar en tratamiento,
        // recepción puede abonarlo a su tratamiento o perdonarlo.
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            listOf(
                Triple("cobrar", "Cobrar normal", "Entra a caja como pago de la $tipoMin"),
                Triple("abonar", "Cobrar y abonar al tratamiento", "El monto se descuenta de la deuda del tratamiento del paciente"),
                Triple("gratis", "No cobrar", "Sigue a tratamiento: la $tipoMin queda en S/ 0"),
            ).forEach { (valor, titulo, detalle) ->
                OpcionModo(titulo, detalle, activo = modo == valor) { if (!guardando) modo = valor }
            }
        }

        if (modo != "gratis") {
            Spacer(Modifier.height(14.dp))
            Etiqueta("MÉTODO DE PAGO")
            if (!dividido) ChipsMetodoPago(metodo) { metodo = it }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.tourAncla("cobro.dividir").clickable(enabled = !guardando) {
                    dividido = !dividido
                    if (dividido && filas.isEmpty()) filas = filasIniciales(metodos, metodo)
                },
            ) {
                Checkbox(
                    checked = dividido,
                    onCheckedChange = {
                        dividido = it
                        if (it && filas.isEmpty()) filas = filasIniciales(metodos, metodo)
                    },
                    enabled = !guardando,
                    colors = CheckboxDefaults.colors(checkedColor = c.navy),
                )
                Text("Pagó con más de un medio", color = c.texto, fontSize = 13.sp)
            }
            if (dividido) {
                // Tutoriales: "cobro.medios" se cumple al escribir algún monto.
                Column(Modifier.tourAncla("cobro.medios", valor = filas.joinToString("") { it.monto.trim() })) {
                    PagoDividido(total = total, metodos = metodos, filas = filas, onCambiar = { filas = it }, deshabilitado = guardando)
                }
            }

            Spacer(Modifier.height(14.dp))
            Etiqueta("FECHA DEL PAGO")
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(Sania.shape.sm.dp))
                    .background(c.superficie).border(1.dp, c.borde, RoundedCornerShape(Sania.shape.sm.dp))
                    .clickable(enabled = !guardando) { mostrarFecha = true }
                    .padding(horizontal = 12.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text("📅 ${fechaDMA(fecha)}", color = c.texto, fontSize = Sania.txt.cuerpo)
                Text("▾", color = c.navy)
            }
            Text(
                "Por defecto, el día de la cita. Cámbiala si el paciente pagó otro día (p. ej. pagó hoy una evaluación de mañana).",
                color = c.textoSuave, fontSize = 11.sp, lineHeight = 14.sp, modifier = Modifier.padding(top = 4.dp),
            )
        }

        Spacer(Modifier.height(12.dp))
        Text(
            when (modo) {
                "gratis" -> "No entra dinero a caja: la $tipoMin queda saldada con S/ 0."
                "abonar" -> "Entra a caja como pago del tratamiento: lo verás sumado en la ficha del paciente."
                else -> if (conDivision) "Entra a caja un ingreso por cada medio, en la fecha del pago (el arqueo por método cuadra)."
                    else "Entra a caja en la fecha del pago, con este método. Si el paciente aún no paga, puedes atenderlo igual y cobrarle después."
            },
            color = c.textoSuave, fontSize = 12.sp, lineHeight = 16.sp,
        )
    }
}

/** Opción tipo radio (borde navy cuando está elegida), como las de la web. */
@Composable
private fun OpcionModo(titulo: String, detalle: String, activo: Boolean, onClick: () -> Unit) {
    val c = Sania.colors
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(Sania.shape.sm.dp))
            .background(if (activo) c.navy.copy(alpha = 0.06f) else c.superficie)
            .border(if (activo) 1.5.dp else 1.dp, if (activo) c.navy else c.borde, RoundedCornerShape(Sania.shape.sm.dp))
            .clickable { onClick() }
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Box(
            Modifier.padding(top = 2.dp).size(18.dp).clip(CircleShape)
                .border(2.dp, if (activo) c.navy else c.borde, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            if (activo) Box(Modifier.size(9.dp).clip(CircleShape).background(c.navy))
        }
        Spacer(Modifier.width(10.dp))
        Column {
            Text(titulo, color = c.texto, fontSize = 14.sp, fontWeight = FontWeight.Bold)
            Text(detalle, color = c.textoSuave, fontSize = 11.sp, lineHeight = 14.sp)
        }
    }
}

@Composable
private fun Etiqueta(texto: String) {
    Text(texto, color = Sania.colors.textoSuave, fontSize = 11.sp, fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(bottom = 6.dp))
}

/** 40.0 → "S/ 40.00" (como formatearSoles de la web). */
internal fun textoSoles(n: Double): String {
    val centavos = kotlin.math.round(n * 100).toLong()
    return "S/ ${centavos / 100}.${(centavos % 100).toString().padStart(2, '0')}"
}

private fun millisAIso(millis: Long): String {
    val d = Instant.fromEpochMilliseconds(millis).toLocalDateTime(TimeZone.UTC).date
    return "${d.year}-${d.monthNumber.toString().padStart(2, '0')}-${d.dayOfMonth.toString().padStart(2, '0')}"
}
