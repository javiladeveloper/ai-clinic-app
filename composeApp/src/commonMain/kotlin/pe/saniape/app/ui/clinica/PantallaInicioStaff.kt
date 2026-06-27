package pe.saniape.app.ui.clinica

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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
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
import pe.saniape.app.data.staff.CitaAgenda
import pe.saniape.app.data.staff.ContextoStaff
import pe.saniape.app.data.staff.DashboardRepo
import pe.saniape.app.data.staff.StatsDashboard
import pe.saniape.app.ui.hora12
import pe.saniape.app.ui.theme.Sania

/**
 * Inicio del staff: saludo + stats (2×2 del profesional o KPIs del gestor) + agenda
 * de hoy. Los stats vienen ya filtrados por miTerapeutaId desde /api/dashboard/stats.
 */
@Composable
fun PantallaInicioStaff(ctx: ContextoStaff) {
    val c = Sania.colors
    var cargando by remember { mutableStateOf(true) }
    var stats by remember { mutableStateOf<StatsDashboard?>(null) }

    LaunchedEffect(Unit) {
        try { stats = DashboardRepo.stats() } catch (_: Exception) {}
        cargando = false
    }

    val primerNombre = ctx.nombre?.trim()?.split(" ")?.firstOrNull()

    Surface(color = c.fondo, modifier = Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            // Barra de marca
            Row(
                Modifier.fillMaxWidth().background(c.navyDark)
                    .padding(horizontal = Sania.dim.xl, vertical = Sania.dim.lg),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    pe.saniape.app.ui.LogoSania(size = 24.dp)
                    Spacer(Modifier.width(8.dp))
                    Text(ctx.clinicaNombre, color = c.sobreNavy, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                }
                ctx.rol?.let { Text(it, color = c.sobreNavy.copy(alpha = 0.8f), fontSize = 12.sp) }
            }

            if (cargando) {
                Box(Modifier.fillMaxSize(), Alignment.Center) { CircularProgressIndicator(color = c.navy) }
                return@Column
            }
            val s = stats

            LazyColumn(
                Modifier.fillMaxSize().padding(horizontal = Sania.dim.lg),
                verticalArrangement = Arrangement.spacedBy(Sania.dim.md),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(vertical = Sania.dim.lg),
            ) {
                item {
                    Column {
                        Text(
                            if (primerNombre != null) "Hola, $primerNombre 👋" else "Hola 👋",
                            color = c.texto, fontSize = Sania.txt.titulo, fontWeight = FontWeight.Bold,
                        )
                        Text("Este es tu resumen de hoy.", color = c.textoSuave, fontSize = Sania.txt.pequeno)
                    }
                }

                if (s != null) {
                    // Stats: profesional (2×2) vs gestor (KPIs).
                    if (s.esProfesional) {
                        item {
                            Column(verticalArrangement = Arrangement.spacedBy(Sania.dim.md)) {
                                Row(horizontalArrangement = Arrangement.spacedBy(Sania.dim.md)) {
                                    StatCard("Citas hoy", s.citasHoy.toString(), "📅", Modifier.weight(1f))
                                    StatCard("Pendientes", s.misCitasPendientes.toString(), "⏳", Modifier.weight(1f))
                                }
                                Row(horizontalArrangement = Arrangement.spacedBy(Sania.dim.md)) {
                                    StatCard("Mis pacientes", s.totalPacientes.toString(), "👥", Modifier.weight(1f))
                                    StatCard("Sesiones", s.misSesionesCompletadas.toString(), "✅", Modifier.weight(1f))
                                }
                            }
                        }
                    } else {
                        item {
                            Column(verticalArrangement = Arrangement.spacedBy(Sania.dim.md)) {
                                Row(horizontalArrangement = Arrangement.spacedBy(Sania.dim.md)) {
                                    StatCard("Total pacientes", s.totalPacientes.toString(), "👥", Modifier.weight(1f))
                                    StatCard("Citas hoy", s.citasHoy.toString(), "📅", Modifier.weight(1f))
                                }
                                Row(horizontalArrangement = Arrangement.spacedBy(Sania.dim.md)) {
                                    StatCard("Sin confirmar", s.citasSinConfirmar.toString(), "❓", Modifier.weight(1f))
                                    StatCard("Sin profesional", s.citasSinProfesional.toString(), "🧑‍⚕️", Modifier.weight(1f))
                                }
                            }
                        }
                    }

                    // Agenda de hoy
                    item {
                        Spacer(Modifier.height(Sania.dim.sm))
                        Text("AGENDA DE HOY", color = c.textoSuave, fontSize = Sania.txt.mini, fontWeight = FontWeight.Bold)
                    }
                    if (s.agendaHoy.isEmpty()) {
                        item {
                            Text("No tienes citas para hoy.", color = c.textoSuave, fontSize = Sania.txt.cuerpo,
                                modifier = Modifier.padding(vertical = Sania.dim.sm))
                        }
                    } else {
                        items(s.agendaHoy) { cita -> FilaAgenda(cita) }
                    }
                } else {
                    item { Text("No se pudieron cargar tus datos.", color = c.error, fontSize = Sania.txt.cuerpo) }
                }
            }
        }
    }
}

@Composable
private fun StatCard(label: String, valor: String, emoji: String, modifier: Modifier = Modifier) {
    val c = Sania.colors
    Column(
        modifier.clip(RoundedCornerShape(Sania.shape.md.dp)).background(c.superficie)
            .border(1.dp, c.borde, RoundedCornerShape(Sania.shape.md.dp)).padding(Sania.dim.lg),
    ) {
        Text(emoji, fontSize = 22.sp)
        Spacer(Modifier.height(6.dp))
        Text(valor, color = c.navy, fontSize = 26.sp, fontWeight = FontWeight.Bold)
        Text(label, color = c.textoSuave, fontSize = 12.sp)
    }
}

@Composable
private fun FilaAgenda(cita: CitaAgenda) {
    val c = Sania.colors
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(Sania.shape.sm.dp)).background(c.superficie)
            .border(1.dp, c.borde, RoundedCornerShape(Sania.shape.sm.dp)).padding(Sania.dim.md),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(hora12(cita.hora), color = c.navy, fontSize = Sania.txt.cuerpo, fontWeight = FontWeight.Bold)
        Spacer(Modifier.padding(horizontal = 6.dp))
        Column(Modifier.weight(1f)) {
            Text(cita.paciente, color = c.texto, fontSize = Sania.txt.cuerpo, fontWeight = FontWeight.SemiBold)
            Text(cita.procedimiento, color = c.textoSuave, fontSize = 12.sp)
        }
        BadgeEstadoCita(cita.estado)
    }
}

@Composable
fun BadgeEstadoCita(estado: String) {
    val c = Sania.colors
    val (fg, bg) = when (estado) {
        "Confirmada", "Completada" -> c.ok to c.okBg
        "Pendiente" -> c.pend to c.pendBg
        "Cancelada" -> c.error to c.errorBg
        else -> c.navy to c.chipBg
    }
    Box(Modifier.clip(RoundedCornerShape(Sania.shape.pill.dp)).background(bg)
        .padding(horizontal = 10.dp, vertical = 4.dp)) {
        Text(estado, color = fg, fontSize = 11.sp, fontWeight = FontWeight.Bold)
    }
}