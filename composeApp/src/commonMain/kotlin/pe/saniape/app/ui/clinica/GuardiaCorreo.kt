package pe.saniape.app.ui.clinica

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import pe.saniape.app.data.staff.ConfirmacionCorreo
import pe.saniape.app.data.staff.CorreoConfirmacionRepo
import pe.saniape.app.data.staff.EstadoCorreo
import pe.saniape.app.data.staff.textoHorasRestantes
import pe.saniape.app.ui.Toaster
import pe.saniape.app.ui.theme.Sania

/**
 * 📧 «Confirma tu correo» (24/11/2026), gemelo de GuardiaCorreo.tsx:
 *  · Pendiente → franja arriba con las horas que quedan y «Reenviar».
 *  · Vencido   → el panel se tapa hasta confirmar (reenviar, corregir el correo o salir).
 * Se vuelve a consultar cada tanto: al confirmar en el correo, se destraba solo.
 */
@Composable
fun GuardiaCorreo(onCerrarSesion: () -> Unit) {
    val c = Sania.colors
    var dato by remember { mutableStateOf<ConfirmacionCorreo?>(null) }
    var ocupado by remember { mutableStateOf(false) }
    var cambiando by remember { mutableStateOf(false) }
    var nuevo by remember { mutableStateOf("") }
    var refresco by remember { mutableStateOf(0) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(refresco) {
        while (true) {
            val d = CorreoConfirmacionRepo.leer()
            dato = d
            when (d.estado) {
                EstadoCorreo.VENCIDO -> delay(8_000)
                EstadoCorreo.PENDIENTE -> delay(60_000)
                else -> return@LaunchedEffect
            }
        }
    }

    val d = dato ?: return
    if (d.estado == EstadoCorreo.NO_APLICA || d.estado == EstadoCorreo.CONFIRMADO) return

    fun pedir(accion: String, email: String? = null) {
        if (ocupado) return
        ocupado = true
        scope.launch {
            val (error, enviadoA) = CorreoConfirmacionRepo.pedir(accion, email)
            ocupado = false
            if (error != null) { Toaster.error(error); return@launch }
            Toaster.exito("Te enviamos el enlace a ${enviadoA ?: email ?: d.email ?: "tu correo"}. Revisa también la carpeta de spam.")
            cambiando = false
            nuevo = ""
            refresco++
        }
    }

    if (d.estado == EstadoCorreo.PENDIENTE) {
        Box(Modifier.fillMaxWidth().statusBarsPadding().background(c.pendBg).padding(horizontal = 12.dp, vertical = 6.dp)) {
            Text(
                "📧 Confirma tu correo${d.email?.let { " ($it)" } ?: ""}: ${textoHorasRestantes(d.horasRestantes)}.  " +
                    if (ocupado) "Enviando…" else "Reenviar el correo",
                color = c.pend, fontSize = 12.sp, fontWeight = FontWeight.Bold,
                modifier = Modifier.clickable(enabled = !ocupado) { pedir("reenviar") },
            )
        }
        return
    }

    // VENCIDO: tapa el panel.
    Box(
        Modifier.fillMaxSize().background(Color(0xB80F1437)).clickable(enabled = false) {}.padding(16.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            Modifier.widthIn(max = 420.dp).fillMaxWidth().clip(RoundedCornerShape(Sania.shape.lg.dp))
                .background(c.superficie).padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text("📧", fontSize = 36.sp)
            Text("Confirma tu correo para continuar", color = c.texto, fontSize = 18.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
            Spacer(Modifier.height(8.dp))
            Text(
                "Pasaron las 24 horas. Te enviamos un enlace a ${d.email ?: "tu correo"}: ábrelo y toca «Confirmar mi correo». " +
                    "Tus datos están intactos y esta pantalla se quita sola al confirmar.",
                color = c.textoSuave, fontSize = 13.sp, textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(14.dp))
            if (cambiando) {
                OutlinedTextField(
                    value = nuevo, onValueChange = { nuevo = it.trim() }, singleLine = true,
                    label = { Text("Tu correo correcto") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                    modifier = Modifier.fillMaxWidth(),
                )
                Text("Desde ahora entrarás a Sania con este correo.", color = c.textoSuave, fontSize = 11.sp,
                    modifier = Modifier.fillMaxWidth().padding(top = 4.dp))
                Spacer(Modifier.height(10.dp))
                Boton("Guardar y enviar", principal = true, habilitado = !ocupado && "@" in nuevo) { pedir("cambiar", nuevo) }
                Boton("Volver", principal = false, habilitado = true) { cambiando = false }
            } else {
                Boton(if (ocupado) "Enviando…" else "Reenviar el correo", principal = true, habilitado = !ocupado) { pedir("reenviar") }
                Boton("Ya lo confirmé", principal = false, habilitado = true) { refresco++ }
                Text("Escribí mal mi correo", color = c.navy, fontSize = 13.sp, fontWeight = FontWeight.Bold,
                    modifier = Modifier.clickable { cambiando = true }.padding(10.dp))
                Text("Cerrar sesión", color = c.textoSuave, fontSize = 12.sp,
                    modifier = Modifier.clickable { onCerrarSesion() }.padding(6.dp))
            }
            Spacer(Modifier.height(8.dp))
            Text("¿No te llega? Revisa spam o escríbenos por WhatsApp al +51 916 884 168.",
                color = c.textoSuave, fontSize = 11.sp, textAlign = TextAlign.Center)
        }
    }
}

@Composable
private fun Boton(texto: String, principal: Boolean, habilitado: Boolean, onClick: () -> Unit) {
    val c = Sania.colors
    Box(
        Modifier.fillMaxWidth().padding(vertical = 4.dp).clip(RoundedCornerShape(Sania.shape.md.dp))
            .background(if (principal) (if (habilitado) c.navy else c.navy.copy(alpha = 0.5f)) else c.fondo)
            .clickable(enabled = habilitado, onClick = onClick).padding(vertical = 12.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(texto, color = if (principal) c.sobreNavy else c.texto, fontSize = 14.sp, fontWeight = FontWeight.Bold)
    }
}
