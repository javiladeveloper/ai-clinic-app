package pe.saniape.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import pe.saniape.app.data.Cita
import pe.saniape.app.data.CitasRepo

/**
 * Portal del paciente — lista de sus citas (misma base que la web).
 */
@Composable
fun PantallaPortal(onCerrarSesion: () -> Unit) {
    var cargando by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var citas by remember { mutableStateOf<List<Cita>>(emptyList()) }

    LaunchedEffect(Unit) {
        try {
            val pacienteId = CitasRepo.pacienteIdDelUsuario()
            citas = if (pacienteId != null) CitasRepo.misCitas(pacienteId) else emptyList()
        } catch (e: Exception) {
            error = e.message ?: "No se pudieron cargar tus citas"
        } finally {
            cargando = false
        }
    }

    Surface(color = Sand, modifier = Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            // Barra superior de marca
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(NavyDark)
                    .padding(horizontal = 20.dp, vertical = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text("Sania", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                TextButton(onClick = onCerrarSesion) {
                    Text("Cerrar sesión", color = Color.White, fontSize = 13.sp)
                }
            }

            Text(
                text = "Mis citas",
                color = Navy,
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(start = 20.dp, top = 20.dp, bottom = 4.dp),
            )

            when {
                cargando -> Box(Modifier.fillMaxSize(), Alignment.Center) {
                    CircularProgressIndicator(color = Navy)
                }
                error != null -> Box(Modifier.fillMaxSize().padding(24.dp), Alignment.Center) {
                    Text("⚠ $error", color = RedDanger, fontSize = 14.sp)
                }
                citas.isEmpty() -> Box(Modifier.fillMaxSize().padding(24.dp), Alignment.Center) {
                    Text("No tienes citas registradas todavía.", color = Muted, fontSize = 15.sp)
                }
                else -> LazyColumn(
                    modifier = Modifier.fillMaxSize().padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    items(citas) { cita -> TarjetaCita(cita) }
                }
            }
        }
    }
}

@Composable
private fun TarjetaCita(cita: Cita) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(Color.White)
            .border(1.dp, BorderColor, RoundedCornerShape(16.dp))
            .padding(16.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "${cita.fecha}  ·  ${cita.hora.take(5)}",
                color = Navy,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
            )
            BadgeEstado(cita.estado)
        }
        cita.tipo?.let {
            Spacer(Modifier.height(6.dp))
            Text(it, color = Muted, fontSize = 13.sp)
        }
        cita.notas?.takeIf { it.isNotBlank() }?.let {
            Spacer(Modifier.height(4.dp))
            Text(it, color = Muted, fontSize = 12.sp)
        }
    }
}

@Composable
private fun BadgeEstado(estado: String) {
    val (fg, bg) = when (estado) {
        "Confirmada", "Completada" -> GreenOk to Color(0xFFDCFCE7)
        "Pendiente" -> Amber to Color(0xFFFEF3C7)
        "Cancelada" -> RedDanger to Color(0xFFFEE2E2)
        else -> Navy to Color(0xFFEEF0F9)
    }
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(20.dp))
            .background(bg)
            .padding(horizontal = 12.dp, vertical = 5.dp),
    ) {
        Text(estado, color = fg, fontSize = 11.sp, fontWeight = FontWeight.Bold)
    }
}