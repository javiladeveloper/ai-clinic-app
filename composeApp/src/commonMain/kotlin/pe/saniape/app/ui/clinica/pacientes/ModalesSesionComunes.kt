package pe.saniape.app.ui.clinica.pacientes

import pe.saniape.app.data.staff.LocalTerminologiaPaciente
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import pe.saniape.app.data.staff.motivoObligatorio
import pe.saniape.app.data.staff.textoConfirmarAlta
import pe.saniape.app.data.staff.tituloCambioEstadoSesion
import pe.saniape.app.data.staff.validarCambioEstadoSesion
import pe.saniape.app.ui.theme.Sania

/**
 * Cambiar el estado de una sesión pendiente (menú ⋯): Reprogramada pide nueva
 * fecha y hora (pickers nativos) y motivo; No asistió / Cancelada / Otro piden
 * motivo y se confirman aquí — el modal ES la confirmación. Mismos textos y
 * reglas que la ficha web ("Otro" exige motivo). Lo usan la ficha y el módulo
 * Sesiones: un solo modal, un solo comportamiento.
 *
 * [onConfirmar] recibe (motivo, fecha, hora); fecha/hora solo en Reprogramada.
 */
@Composable
fun ModalEstadoSesion(
    numero: Int,
    estado: String,
    fechaInicial: String,
    horaInicial: String?,
    subtitulo: String? = null,
    guardando: Boolean = false,
    onCancelar: () -> Unit,
    onConfirmar: (motivo: String?, fecha: String?, hora: String?) -> Unit,
) {
    val c = Sania.colors
    val esReprog = estado == "Reprogramada"
    var motivo by remember { mutableStateOf("") }
    var fecha by remember { mutableStateOf(fechaInicial.ifBlank { pe.saniape.app.data.staff.hoyClinicaIso() }) }
    var hora by remember { mutableStateOf(horaInicial?.take(5)?.ifBlank { null } ?: "09:00") }
    var mostrarFecha by remember { mutableStateOf(false) }
    var mostrarHora by remember { mutableStateOf(false) }
    val error = validarCambioEstadoSesion(estado, motivo, if (esReprog) fecha else null, if (esReprog) hora else null)

    if (mostrarFecha) DialogoFecha(onElegir = { fecha = it }, onCerrar = { mostrarFecha = false })
    if (mostrarHora) DialogoHora(hora, onElegir = { hora = it }, onCerrar = { mostrarHora = false })

    DialogoForm(
        titulo = tituloCambioEstadoSesion(estado),
        subtitulo = listOfNotNull("Sesión #$numero", subtitulo?.takeIf { it.isNotBlank() }).joinToString(" · ") +
            " · No afecta el contador de sesiones completadas",
        textoAccion = if (guardando) "Guardando…" else "Confirmar",
        accionHabilitada = error == null && !guardando,
        onCancelar = onCancelar,
        onAccion = {
            onConfirmar(motivo.trim().ifBlank { null }, if (esReprog) fecha else null, if (esReprog) hora else null)
        },
    ) {
        if (esReprog) {
            TarjetaForm(titulo = "Nueva fecha y hora", icono = "📅") {
                Row(horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp)) {
                    Column(Modifier.weight(1f)) { EtqForm("Fecha"); CajaSelectorForm(fecha) { mostrarFecha = true } }
                    Column(Modifier.weight(1f)) {
                        EtqForm("Hora"); CajaSelectorForm(pe.saniape.app.ui.hora12(hora)) { mostrarHora = true }
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
        }
        TarjetaForm(
            titulo = "Motivo / Nota" + if (motivoObligatorio(estado)) " (requerido)" else " (opcional)",
            icono = "📝",
        ) {
            OutlinedTextField(
                colors = coloresCampoForm(),
                value = motivo, onValueChange = { motivo = it },
                placeholder = { Text("Ej: El ${LocalTerminologiaPaciente.current.paciente} avisó que no podía venir…", color = c.textoSuave) },
                modifier = Modifier.fillMaxWidth(), minLines = 2,
            )
        }
    }
}

/**
 * Confirmación antes de dar de alta (antes se ejecutaba con un toque, fácil de
 * tocar por error). Avisa cuántas sesiones pendientes se cerrarán, como la web.
 */
@Composable
fun DialogoConfirmarAlta(
    sesionesPendientes: Int?,
    onCancelar: () -> Unit,
    onConfirmar: () -> Unit,
    /**
     * Saldo pendiente del tratamiento (null = no se sabe). El alta NO exige pago
     * (cobrar ≠ atender), pero la deuda se avisa: antes se cerraba sin mencionarla
     * (reporte 29/09/2026). Gemelo de la confirmación de la web.
     */
    saldo: Double? = null,
    /** Con permiso de pagos: en vez de "Cancelar", ir a registrar el pago. */
    onRegistrarPago: (() -> Unit)? = null,
) {
    val c = Sania.colors
    val conDeuda = saldo != null && saldo > 0.005
    AlertDialog(
        onDismissRequest = onCancelar,
        title = { Text("¿Dar de alta este tratamiento?", fontWeight = FontWeight.Bold) },
        text = {
            Column {
                if (conDeuda) {
                    Text("⚠ Tiene ${formatoSoles(saldo!!)} sin pagar. El alta no borra la deuda: queda por cobrar.",
                        color = c.error, fontSize = Sania.txt.cuerpo, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(8.dp))
                }
                Text(textoConfirmarAlta(sesionesPendientes), color = c.texto, fontSize = Sania.txt.cuerpo)
            }
        },
        confirmButton = {
            TextButton(onClick = onConfirmar) {
                Text(if (conDeuda) "Dar de alta igual" else "Dar de alta", color = c.ok, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            if (conDeuda && onRegistrarPago != null) {
                TextButton(onClick = onRegistrarPago) { Text("💵 Registrar pago", color = c.navy, fontWeight = FontWeight.Bold) }
            } else {
                TextButton(onClick = onCancelar) { Text("Cancelar", color = c.textoSuave) }
            }
        },
        containerColor = c.superficie,
    )
}

/**
 * Tras el alta: ofrecer pedirle al paciente su calificación (encuesta de
 * satisfacción). Igual que la web: abre el WhatsApp DEL CELULAR con el mensaje
 * listo — gratis, sin plantilla de Meta, y le llega aunque no tenga la app.
 */
@Composable
fun DialogoEncuestaAlta(onPedir: () -> Unit, onCerrar: () -> Unit) {
    val c = Sania.colors
    AlertDialog(
        onDismissRequest = onCerrar,
        title = { Text("¿Le pides su opinión?", fontWeight = FontWeight.Bold) },
        text = {
            Text("Se abre WhatsApp con el mensaje listo. No cuesta nada: sale de tu celular.",
                color = c.textoSuave, fontSize = 13.sp)
        },
        confirmButton = {
            TextButton(onClick = onPedir) { Text("⭐ Pedir calificación", color = c.navy, fontWeight = FontWeight.Bold) }
        },
        dismissButton = { TextButton(onClick = onCerrar) { Text("Ahora no", color = c.textoSuave) } },
        containerColor = c.superficie,
    )
}

/**
 * La sesión se completó pero el cobro no entró. No hay un endpoint que haga las
 * dos cosas en una sola llamada, así que se dice claro qué quedó hecho y se deja
 * reintentar SOLO el cobro (volver a completar no hace falta ni conviene).
 */
@Composable
fun DialogoCobroFallido(
    numeroSesion: Int,
    monto: Double,
    metodo: String,
    motivo: String?,
    reintentando: Boolean,
    onReintentar: () -> Unit,
    onCerrar: () -> Unit,
) {
    val c = Sania.colors
    AlertDialog(
        onDismissRequest = { if (!reintentando) onCerrar() },
        title = { Text("✓ Sesión #$numeroSesion completada", fontWeight = FontWeight.Bold) },
        text = {
            Column {
                Text("Pero el cobro de S/ ${formatoSoles(monto)} ($metodo) NO se registró.",
                    color = c.error, fontSize = Sania.txt.cuerpo, fontWeight = FontWeight.SemiBold)
                motivo?.takeIf { it.isNotBlank() }?.let {
                    Spacer(Modifier.height(6.dp))
                    Text("Motivo: $it", color = c.texto, fontSize = 12.sp)
                }
                Spacer(Modifier.height(8.dp))
                Text("Reintenta solo el cobro (la sesión ya quedó completada), o regístralo luego desde 💳 Cobrar.",
                    color = c.textoSuave, fontSize = 12.sp)
            }
        },
        confirmButton = {
            TextButton(onClick = onReintentar, enabled = !reintentando) {
                Text(if (reintentando) "Cobrando…" else "↻ Reintentar cobro", color = c.navy, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onCerrar, enabled = !reintentando) { Text("Luego", color = c.textoSuave) }
        },
        containerColor = c.superficie,
    )
}

/** Aviso rojo arriba de la ficha de un paciente dado de baja (como la web). */
@Composable
fun AvisoFichaInactiva(puedeReactivar: Boolean) {
    val c = Sania.colors
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(Sania.shape.md.dp))
            .background(c.errorBg).border(1.dp, c.error, RoundedCornerShape(Sania.shape.md.dp))
            .padding(horizontal = 14.dp, vertical = 11.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Text("🚫", fontSize = 16.sp)
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text("${LocalTerminologiaPaciente.current.Paciente} dado de baja", color = c.error, fontSize = 13.sp, fontWeight = FontWeight.Bold)
            Text(
                "Su historial se puede consultar, pero la ficha no acepta sesiones, citas ni pagos nuevos." +
                    if (puedeReactivar) " Para volver a atenderlo, reactívalo desde ⋯." else "",
                color = c.texto, fontSize = 12.sp, modifier = Modifier.padding(top = 2.dp),
            )
        }
    }
}

private fun formatoSoles(n: Double): String {
    val centavos = kotlin.math.round(n * 100).toLong()
    return "${centavos / 100}.${(centavos % 100).toString().padStart(2, '0')}"
}

/**
 * "¿El paciente pagó esta sesión?" + monto + método: el bloque de cobro del
 * cierre de una sesión. Uno solo para la ficha y la agenda (antes solo la ficha
 * lo tenía y desde la agenda el cobro era un segundo viaje).
 */
@Composable
fun BloqueCobroSesion(
    cobrar: Boolean, onCobrar: (Boolean) -> Unit,
    monto: String, onMonto: (String) -> Unit,
    metodo: String, onMetodo: (String) -> Unit,
) {
    val c = Sania.colors
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(Sania.shape.sm.dp))
            .background(if (cobrar) c.chipBg else c.fondo)
            .border(1.dp, if (cobrar) c.navy else c.borde, RoundedCornerShape(Sania.shape.sm.dp))
            .clickable { onCobrar(!cobrar) }
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.size(22.dp).clip(RoundedCornerShape(Sania.shape.sm.dp))
                .background(if (cobrar) c.navy else c.superficie)
                .border(1.dp, if (cobrar) c.navy else c.borde, RoundedCornerShape(Sania.shape.sm.dp)),
            contentAlignment = Alignment.Center,
        ) { if (cobrar) Text("✓", color = c.sobreNavy, fontSize = 13.sp, fontWeight = FontWeight.Bold) }
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text("¿El ${LocalTerminologiaPaciente.current.paciente} pagó esta sesión?", color = c.texto, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
            Text("El cobro se registra junto con el completar", color = c.textoSuave, fontSize = 11.sp,
                modifier = Modifier.padding(top = 1.dp))
        }
        Text("💳", fontSize = 16.sp)
    }
    if (cobrar) {
        Spacer(Modifier.height(8.dp))
        TarjetaForm(titulo = "Cobro", icono = "💳") {
            EtqForm("Monto (S/)")
            androidx.compose.material3.OutlinedTextField(colors = coloresCampoForm(),
                value = monto,
                onValueChange = { onMonto(it.filter { ch -> ch.isDigit() || ch == '.' }) },
                singleLine = true,
                keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                    keyboardType = androidx.compose.ui.text.input.KeyboardType.Decimal),
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(10.dp))
            EtqForm("Método")
            ChipsMetodoPago(metodo) { onMetodo(it) }
        }
    }
}
