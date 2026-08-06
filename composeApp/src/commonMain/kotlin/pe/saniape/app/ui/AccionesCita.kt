package pe.saniape.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import pe.saniape.app.data.CitaPortal
import pe.saniape.app.ui.theme.Sania
import pe.saniape.app.data.SaludRepo

/**
 * Lo que el paciente puede HACER con su cita: confirmar, cancelar, reprogramar
 * y llegar (mapa / WhatsApp).
 *
 * Todo esto ya existía en el backend y en el portal web; la app solo mostraba la
 * cita sin poder tocarla. Confirmar y cancelar desde el celular es lo que reduce
 * las ausencias: una cita que nadie confirma es la que no se presenta.
 */
@Composable
fun AccionesCita(
    cita: CitaPortal,
    onCambio: () -> Unit,
    abrirUrl: (String) -> Unit,
) {
    val c = Sania.colors
    val alcance = rememberCoroutineScope()
    var trabajando by remember { mutableStateOf(false) }
    var confirmarCancelar by remember { mutableStateOf(false) }
    var reprogramar by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    // Sin token no se puede gestionar (cita vieja creada antes de esta función).
    val token = cita.token
    val yaConfirmada = cita.estado.equals("Confirmada", ignoreCase = true)
    val cerrada = cita.estado.equals("Cancelada", ignoreCase = true) ||
        cita.estado.equals("Completada", ignoreCase = true)

    fun ejecutar(accion: suspend () -> String?) {
        if (trabajando) return
        trabajando = true
        alcance.launch {
            val err = accion()
            trabajando = false
            if (err == null) { Toaster.exito("Listo"); onCambio() } else error = err
        }
    }

    Column(Modifier.fillMaxWidth()) {
        // Dónde es. En una clínica con varios locales, sin esto el paciente no
        // sabe a cuál ir — el dato ya venía del servidor y se descartaba.
        if (!cita.sede.isNullOrBlank() || !cita.direccion.isNullOrBlank()) {
            Column(Modifier.fillMaxWidth().padding(top = 10.dp)) {
                cita.sede?.takeIf { it.isNotBlank() }?.let {
                    Text(it, color = c.texto, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                }
                cita.direccion?.takeIf { it.isNotBlank() }?.let {
                    Text(it, color = c.textoSuave, fontSize = 12.sp)
                }
            }
        }

        Row(
            Modifier.fillMaxWidth().padding(top = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (!cerrada && token != null) {
                if (!yaConfirmada) {
                    BotonCita("✓ Confirmar", principal = true, activo = !trabajando) {
                        ejecutar { SaludRepo.confirmarCita(token) }
                    }
                }
                BotonCita("Reprogramar", activo = !trabajando) { reprogramar = true }
                BotonCita("Cancelar", peligro = true, activo = !trabajando) { confirmarCancelar = true }
            }
        }

        Row(
            Modifier.fillMaxWidth().padding(top = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            // Mapa: se abre con las coordenadas si las hay; si no, con la dirección
            // escrita, que en una ciudad chica funciona igual de bien.
            if (cita.lat != null && cita.lng != null) {
                BotonCita("📍 Cómo llegar") {
                    abrirUrl("https://www.google.com/maps/search/?api=1&query=${cita.lat},${cita.lng}")
                }
            } else if (!cita.direccion.isNullOrBlank()) {
                BotonCita("📍 Cómo llegar") {
                    abrirUrl("https://www.google.com/maps/search/?api=1&query=" + cita.direccion.replace(" ", "+"))
                }
            }
            cita.whatsapp?.takeIf { it.isNotBlank() }?.let { wa ->
                BotonCita("💬 Escribir") {
                    val num = wa.filter { it.isDigit() }.let { if (it.length == 9) "51$it" else it }
                    abrirUrl("https://wa.me/$num")
                }
            }
        }
    }

    if (confirmarCancelar) {
        AlertDialog(
            onDismissRequest = { confirmarCancelar = false },
            title = { Text("¿Cancelar tu cita?") },
            text = { Text("Se avisará a la clínica. Si solo quieres cambiarla de día, usa Reprogramar.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmarCancelar = false
                    token?.let { t -> ejecutar { SaludRepo.cancelarCita(t) } }
                }) { Text("Sí, cancelar") }
            },
            dismissButton = { TextButton(onClick = { confirmarCancelar = false }) { Text("No") } },
        )
    }

    if (reprogramar && token != null) {
        DialogoReprogramar(
            onCerrar = { reprogramar = false },
            onElegir = { fecha, hora ->
                reprogramar = false
                ejecutar { SaludRepo.reprogramarCita(token, fecha, hora) }
            },
        )
    }

    // El servidor explica en español por qué no se pudo (menos de 3 horas para la
    // cita, cita ya cerrada…). Se muestra tal cual: reescribirlo perdería el motivo.
    error?.let { msg ->
        AlertDialog(
            onDismissRequest = { error = null },
            title = { Text("No se pudo") },
            text = { Text(msg) },
            confirmButton = { TextButton(onClick = { error = null }) { Text("Entendido") } },
        )
    }
}

@Composable
private fun BotonCita(
    texto: String,
    principal: Boolean = false,
    peligro: Boolean = false,
    activo: Boolean = true,
    onClick: () -> Unit,
) {
    val c = Sania.colors
    val fondo = if (principal) Navy else c.superficie
    val borde = if (peligro) RedDanger else if (principal) Navy else c.borde
    val color = if (principal) Blanco else if (peligro) RedDanger else c.texto
    Box(
        Modifier
            .clip(RoundedCornerShape(10.dp))
            .background(fondo)
            .border(1.dp, borde, RoundedCornerShape(10.dp))
            .clickable(enabled = activo) { onClick() }
            .padding(horizontal = 14.dp, vertical = 9.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(texto, color = color, fontSize = 13.sp, fontWeight = FontWeight.Bold)
    }
}
