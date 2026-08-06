package pe.saniape.app.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import pe.saniape.app.data.CitaPortal
import pe.saniape.app.data.SaludRepo
import pe.saniape.app.ui.theme.Sania

/**
 * "¿Cómo te fue?" — la reseña de una atención ya terminada.
 *
 * Se ofrece solo en citas Completadas: preguntar por una cita cancelada o que
 * aún no ocurrió no tiene sentido.
 *
 * Vale la pena porque las reseñas traen pacientes nuevos, y el momento de
 * pedirlas es cuando el paciente abre la app y ve su atención reciente — no
 * dos semanas después por correo.
 */
@Composable
fun ResenarCita(cita: CitaPortal, onListo: () -> Unit) {
    val c = Sania.colors
    val alcance = rememberCoroutineScope()
    var abierto by remember { mutableStateOf(false) }
    var estrellas by remember { mutableStateOf(0) }
    var comentario by remember { mutableStateOf("") }
    var enviando by remember { mutableStateOf(false) }
    var listo by remember { mutableStateOf(false) }

    val token = cita.token ?: return
    if (!cita.estado.equals("Completada", ignoreCase = true)) return

    if (listo) {
        Text(
            "★ Gracias por tu opinión",
            color = c.textoSuave, fontSize = 12.sp,
            modifier = Modifier.padding(top = 8.dp),
        )
        return
    }

    Text(
        "¿Cómo te fue? Deja tu opinión",
        color = Navy, fontSize = 12.sp, fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(top = 8.dp).clickable { abierto = true },
    )

    if (abierto) {
        AlertDialog(
            onDismissRequest = { if (!enviando) abierto = false },
            title = { Text("¿Cómo te atendieron?") },
            text = {
                Column(Modifier.fillMaxWidth()) {
                    cita.clinica?.let {
                        Text(it, color = c.textoSuave, fontSize = 13.sp, modifier = Modifier.padding(bottom = 10.dp))
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        (1..5).forEach { n ->
                            Text(
                                if (n <= estrellas) "★" else "☆",
                                fontSize = 30.sp,
                                color = if (n <= estrellas) Amber else c.textoSuave,
                                modifier = Modifier.clickable { estrellas = n },
                            )
                        }
                    }
                    OutlinedTextField(
                        value = comentario,
                        onValueChange = { if (it.length <= 500) comentario = it },
                        label = { Text("Cuéntanos (opcional)") },
                        modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                        minLines = 2,
                    )
                }
            },
            confirmButton = {
                TextButton(
                    // Sin estrellas no hay reseña que mandar: el servidor exige 1 a 5.
                    enabled = estrellas in 1..5 && !enviando,
                    onClick = {
                        enviando = true
                        alcance.launch {
                            val err = SaludRepo.resenarCita(token, estrellas, comentario)
                            enviando = false
                            if (err == null) {
                                abierto = false; listo = true
                                Toaster.exito("¡Gracias por tu opinión!")
                                onListo()
                            } else {
                                Toaster.error(err)
                            }
                        }
                    },
                ) { Text(if (enviando) "Enviando…" else "Enviar", color = c.navy) }
            },
            dismissButton = {
                TextButton(onClick = { abierto = false }, enabled = !enviando) {
                    Text("Ahora no", color = c.textoSuave)
                }
            },
        )
    }
}
