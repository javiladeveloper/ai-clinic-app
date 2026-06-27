package pe.saniape.app.ui.clinica

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.sp
import pe.saniape.app.data.staff.ContextoStaff
import pe.saniape.app.ui.theme.Sania

// Placeholders temporales. Se reemplazan por las pantallas reales en los
// siguientes pasos (Agenda, Pacientes).

@Composable
fun PantallaAgenda(ctx: ContextoStaff) = PlaceholderClinica("📅", "Agenda", "Tus citas del día aparecerán aquí.")

@Composable
fun PantallaPacientesStaff(ctx: ContextoStaff) =
    PlaceholderClinica("👥", "Pacientes", "Tu lista de pacientes aparecerá aquí.")

@Composable
private fun PlaceholderClinica(emoji: String, titulo: String, texto: String) {
    val c = Sania.colors
    Surface(color = c.fondo, modifier = Modifier.fillMaxSize()) {
        Box(Modifier.fillMaxSize().padding(Sania.dim.xxl), Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(emoji, fontSize = 44.sp)
                Spacer(Modifier.height(Sania.dim.md))
                Text(titulo, color = c.navy, fontSize = Sania.txt.subtitulo, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(Sania.dim.sm))
                Text(texto, color = c.textoSuave, fontSize = Sania.txt.cuerpo, textAlign = TextAlign.Center)
            }
        }
    }
}