package pe.saniape.app.ui.clinica

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import pe.saniape.app.data.Supabase
import pe.saniape.app.ui.recordarAcciones
import pe.saniape.app.ui.theme.Sania

/**
 * La clínica no terminó el asistente de inicio (`onboardingCompleto=false`).
 * Igual que la web: el Admin lo termina (el asistente vive en la web) y el
 * resto del equipo espera.
 */
@Composable
fun PantallaOnboardingPendiente(
    esAdmin: Boolean,
    clinicaNombre: String,
    onReintentar: () -> Unit,
    onCerrarSesion: () -> Unit,
) {
    val c = Sania.colors
    val acciones = recordarAcciones()
    Surface(color = c.fondo, modifier = Modifier.fillMaxSize()) {
        Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
            Column(
                Modifier.widthIn(max = 440.dp).fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(c.superficie)
                    .border(1.dp, c.borde, RoundedCornerShape(20.dp)).padding(28.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text(if (esAdmin) "🚀" else "🛠️", fontSize = 40.sp)
                if (esAdmin) {
                    Text("Termina de configurar tu clínica", color = c.texto, fontSize = 19.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
                    Text(
                        "Te faltan unos pasos del asistente de inicio de $clinicaNombre (servicios, equipo, tu primera cita). " +
                            "Se completa en la web: inicia sesión con tu misma cuenta y vuelve aquí.",
                        color = c.textoSuave, fontSize = 13.sp, textAlign = TextAlign.Center, lineHeight = 19.sp,
                    )
                    Spacer(Modifier.height(6.dp))
                    Boton("Abrir el asistente ↗", lleno = true) { acciones.abrirUrl("${Supabase.SITE_URL}/onboarding") }
                    Boton("Ya terminé, reintentar", lleno = false, onClick = onReintentar)
                } else {
                    Text("Tu administrador está terminando de configurar la clínica", color = c.texto, fontSize = 18.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
                    Text(
                        "En cuanto termine el asistente de inicio podrás entrar con normalidad. Vuelve a intentarlo en unos minutos.",
                        color = c.textoSuave, fontSize = 13.sp, textAlign = TextAlign.Center, lineHeight = 19.sp,
                    )
                    Spacer(Modifier.height(6.dp))
                    Boton("Reintentar", lleno = true, onClick = onReintentar)
                }
                Text(
                    "Cerrar sesión", color = c.textoSuave, fontSize = 13.sp, fontWeight = FontWeight.Bold,
                    modifier = Modifier.clip(RoundedCornerShape(8.dp)).clickable(onClick = onCerrarSesion).padding(8.dp),
                )
            }
        }
    }
}

@Composable
private fun Boton(texto: String, lleno: Boolean, onClick: () -> Unit) {
    val c = Sania.colors
    Box(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp))
            .background(if (lleno) c.navy else c.superficie)
            .border(1.dp, if (lleno) c.navy else c.borde, RoundedCornerShape(12.dp))
            .clickable(onClick = onClick).padding(vertical = 13.dp),
        contentAlignment = Alignment.Center,
    ) { Text(texto, color = if (lleno) c.sobreNavy else c.navy, fontSize = 14.sp, fontWeight = FontWeight.Bold) }
}
