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

/** Tab Reservar — placeholder hasta conectar con la API web. */
@Composable
fun PantallaReservar() {
    PantallaSimple(
        emoji = "📅",
        titulo = "Reservar una cita",
        texto = "Pronto podrás reservar tu cita aquí, eligiendo clínica, profesional y horario.",
    )
}

/** Tab Salud — muestra el tratamiento del paciente (reusa la lógica del portal). */
@Composable
fun PantallaSalud() {
    PantallaSimple(
        emoji = "💙",
        titulo = "Mi salud",
        texto = "Aquí verás tu tratamiento, progreso y documentos.",
    )
}

/** Tab Más — perfil, ajustes y cerrar sesión. */
@Composable
fun PantallaMas(nombre: String?, onCerrarSesion: () -> Unit) {
    Surface(color = Sand, modifier = Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().padding(20.dp)) {
            Text("Más", color = Navy, fontSize = 22.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(16.dp))

            nombre?.let {
                Column(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(Color.White)
                        .padding(16.dp),
                ) {
                    Text(it, color = Color(0xFF1F2937), fontSize = 16.sp, fontWeight = FontWeight.Bold)
                    Text("Paciente", color = Muted, fontSize = 13.sp)
                }
                Spacer(Modifier.height(16.dp))
            }

            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(Color.White)
                    .clickable { onCerrarSesion() }.padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text("Cerrar sesión", color = RedDanger, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                Text("→", color = RedDanger, fontSize = 15.sp)
            }
        }
    }
}

@Composable
private fun PantallaSimple(emoji: String, titulo: String, texto: String) {
    Surface(color = Sand, modifier = Modifier.fillMaxSize()) {
        Column(
            Modifier.fillMaxSize().padding(28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Text(emoji, fontSize = 44.sp)
            Spacer(Modifier.height(12.dp))
            Text(titulo, color = Navy, fontSize = 20.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))
            Text(texto, color = Muted, fontSize = 14.sp, textAlign = TextAlign.Center)
        }
    }
}