package pe.saniape.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.clickable
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Tab Más — perfil, ajustes y cerrar sesión. */
@Composable
fun PantallaMas(nombre: String?, onCerrarSesion: () -> Unit) {
    Surface(color = Sand, modifier = Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().padding(20.dp)) {
            Text("Más", color = Navy, fontSize = 22.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(16.dp))

            nombre?.let {
                Column(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(Blanco)
                        .padding(16.dp),
                ) {
                    Text(it, color = TextoPrincipal, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                    Text("Paciente", color = Muted, fontSize = 13.sp)
                }
                Spacer(Modifier.height(16.dp))
            }

            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(Blanco)
                    .clickable { onCerrarSesion() }.padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text("Cerrar sesión", color = RedDanger, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                Text("→", color = RedDanger, fontSize = 15.sp)
            }
        }
    }
}