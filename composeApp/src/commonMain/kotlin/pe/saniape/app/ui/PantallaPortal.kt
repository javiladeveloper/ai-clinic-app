package pe.saniape.app.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
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
import pe.saniape.app.data.CitaPortal
import pe.saniape.app.data.PortalRepo
import pe.saniape.app.data.Tratamiento

/**
 * Portal del paciente — saludo, próxima cita destacada, mi tratamiento
 * (progreso + timeline), más citas y historial. Lee de la misma base que la web.
 */
@Composable
fun PantallaPortal(nombre: String?, onCerrarSesion: () -> Unit) {
    var cargando by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var proximas by remember { mutableStateOf<List<CitaPortal>>(emptyList()) }
    var pasadas by remember { mutableStateOf<List<CitaPortal>>(emptyList()) }
    var tratamientos by remember { mutableStateOf<List<Tratamiento>>(emptyList()) }
    var verHistorial by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        try {
            val (prox, pas) = PortalRepo.misCitas()
            proximas = prox; pasadas = pas
            tratamientos = PortalRepo.misTratamientos()
        } catch (e: Exception) {
            error = e.message ?: "No se pudieron cargar tus datos"
        } finally {
            cargando = false
        }
    }

    val primerNombre = nombre?.trim()?.split(" ")?.firstOrNull()

    Surface(color = Sand, modifier = Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            // Barra superior de marca
            Row(
                modifier = Modifier.fillMaxWidth().background(NavyDark)
                    .padding(horizontal = 20.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text("Sania", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                TextButton(onClick = onCerrarSesion) {
                    Text("Cerrar sesión", color = Color.White, fontSize = 13.sp)
                }
            }

            when {
                cargando -> Box(Modifier.fillMaxSize(), Alignment.Center) {
                    CircularProgressIndicator(color = Navy)
                }
                error != null -> Box(Modifier.fillMaxSize().padding(24.dp), Alignment.Center) {
                    Text("⚠ $error", color = RedDanger, fontSize = 14.sp)
                }
                else -> LazyColumn(
                    modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(vertical = 20.dp),
                ) {
                    // Saludo
                    item {
                        Column {
                            Text(
                                if (primerNombre != null) "Hola, $primerNombre 👋" else "Hola 👋",
                                color = Color(0xFF1F2937), fontSize = 24.sp, fontWeight = FontWeight.Bold,
                            )
                            Text("Tu salud, en un solo lugar.", color = Muted, fontSize = 13.sp)
                        }
                    }

                    // Próxima cita destacada o estado vacío
                    val proxima = proximas.firstOrNull()
                    if (proxima != null) {
                        item { Etiqueta("TU PRÓXIMA CITA") }
                        item { CitaHero(proxima) }
                    } else {
                        item { TarjetaVacia() }
                    }

                    // Más próximas
                    val otras = proximas.drop(1)
                    if (otras.isNotEmpty()) {
                        item { Etiqueta("MÁS CITAS PRÓXIMAS") }
                        items(otras) { CitaItem(it, atenuar = false) }
                    }

                    // Mi tratamiento
                    val ordenados = tratamientos.sortedByDescending { it.estado == "Activo" }
                    if (ordenados.isNotEmpty()) {
                        item { Etiqueta("MI TRATAMIENTO") }
                        items(ordenados) { TarjetaTratamiento(it) }
                    }

                    // Historial (colapsable)
                    if (pasadas.isNotEmpty()) {
                        item {
                            Row(
                                modifier = Modifier.fillMaxWidth()
                                    .clickable { verHistorial = !verHistorial },
                                horizontalArrangement = Arrangement.SpaceBetween,
                            ) {
                                Text(
                                    "HISTORIAL DE CITAS (${pasadas.size})",
                                    color = Muted, fontSize = 11.sp, fontWeight = FontWeight.Bold,
                                )
                                Text(if (verHistorial) "▲" else "▼", color = Muted, fontSize = 11.sp)
                            }
                        }
                        if (verHistorial) {
                            items(pasadas) { CitaItem(it, atenuar = true) }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun Etiqueta(texto: String) {
    Text(texto, color = Muted, fontSize = 11.sp, fontWeight = FontWeight.Bold)
}

@Composable
private fun CitaHero(c: CitaPortal) {
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp))
            .border(1.dp, Navy, RoundedCornerShape(16.dp)),
    ) {
        Column(Modifier.fillMaxWidth().background(Navy).padding(horizontal = 18.dp, vertical = 14.dp)) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(c.fecha, color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.Bold)
                Box(
                    Modifier.clip(RoundedCornerShape(20.dp)).background(Color.White.copy(alpha = 0.15f))
                        .padding(horizontal = 10.dp, vertical = 4.dp),
                ) { Text(c.estado, color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold) }
            }
            Text(c.hora.take(5), color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.ExtraBold)
        }
        Column(Modifier.fillMaxWidth().background(Color.White).padding(18.dp)) {
            c.clinica?.let { Text(it, color = Navy, fontSize = 15.sp, fontWeight = FontWeight.Bold) }
            Text(
                (c.tipo ?: "Cita") + (c.profesional?.let { " · con $it" } ?: ""),
                color = Muted, fontSize = 13.sp,
            )
        }
    }
}

@Composable
private fun CitaItem(c: CitaPortal, atenuar: Boolean) {
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(Color.White)
            .border(1.dp, BorderColor, RoundedCornerShape(14.dp)).padding(14.dp),
    ) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                "${c.fecha} · ${c.hora.take(5)}",
                color = if (atenuar) Muted else Color(0xFF1F2937),
                fontSize = 15.sp, fontWeight = FontWeight.Bold,
            )
            BadgeEstado(c.estado)
        }
        c.clinica?.let { Text(it, color = Navy, fontSize = 13.sp, fontWeight = FontWeight.SemiBold) }
        Text(
            (c.tipo ?: "") + (c.profesional?.let { " · $it" } ?: ""),
            color = Muted, fontSize = 12.sp,
        )
    }
}

@Composable
private fun TarjetaTratamiento(t: Tratamiento) {
    var verSesiones by remember { mutableStateOf(false) }
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(Color.White)
            .border(1.dp, BorderColor, RoundedCornerShape(16.dp)).padding(16.dp),
    ) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Top,
        ) {
            Column(Modifier.weight(1f)) {
                Text(t.procedimiento, color = Color(0xFF1F2937), fontSize = 15.sp, fontWeight = FontWeight.Bold)
                t.clinica?.let { Text("🏥 $it", color = Muted, fontSize = 12.sp) }
            }
            val activo = t.estado == "Activo"
            Box(
                Modifier.clip(RoundedCornerShape(20.dp))
                    .background(if (activo) Color(0xFFDCFCE7) else Color(0xFFEEF0F9))
                    .padding(horizontal = 10.dp, vertical = 4.dp),
            ) {
                Text(
                    if (activo) "En curso" else if (t.estado == "Completado") "Terminado" else t.estado,
                    color = if (activo) GreenOk else Muted, fontSize = 11.sp, fontWeight = FontWeight.Bold,
                )
            }
        }

        // Progreso de sesiones
        if (t.usaSesiones && t.totalSesiones != null) {
            val pct = (t.sesionesCompletadas.toFloat() / t.totalSesiones).coerceIn(0f, 1f)
            Spacer(Modifier.height(12.dp))
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text("Progreso", color = Muted, fontSize = 12.sp)
                Text(
                    "${t.sesionesCompletadas} de ${t.totalSesiones} sesiones",
                    color = Color(0xFF1F2937), fontSize = 12.sp, fontWeight = FontWeight.Bold,
                )
            }
            Spacer(Modifier.height(4.dp))
            Box(
                Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(20.dp))
                    .background(Color(0xFFEEF0F9)),
            ) {
                Box(
                    Modifier.fillMaxWidth(pct).height(8.dp).clip(RoundedCornerShape(20.dp))
                        .background(GreenOk),
                )
            }
        }

        // Timeline de sesiones (colapsable)
        if (t.sesiones.isNotEmpty()) {
            Spacer(Modifier.height(10.dp))
            Text(
                if (verSesiones) "Ocultar sesiones" else "Ver mis sesiones",
                color = Navy, fontSize = 13.sp, fontWeight = FontWeight.Bold,
                modifier = Modifier.clickable { verSesiones = !verSesiones },
            )
            AnimatedVisibility(verSesiones) {
                Column(Modifier.padding(top = 8.dp)) {
                    t.sesiones.forEach { s ->
                        Row(
                            Modifier.fillMaxWidth().padding(vertical = 3.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Box(
                                Modifier.size(8.dp).clip(RoundedCornerShape(8.dp))
                                    .background(colorEstadoSesion(s.estado)),
                            )
                            Spacer(Modifier.width(8.dp))
                            Text("Sesión #${s.numero}", color = Color(0xFF1F2937), fontSize = 13.sp)
                            Spacer(Modifier.width(6.dp))
                            Text("· ${s.fecha}", color = Muted, fontSize = 12.sp)
                            Spacer(Modifier.weight(1f))
                            Text(s.estado, color = Muted, fontSize = 11.sp)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TarjetaVacia() {
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(Color.White)
            .border(1.dp, BorderColor, RoundedCornerShape(16.dp)).padding(vertical = 32.dp, horizontal = 20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("📅", fontSize = 34.sp)
        Spacer(Modifier.height(8.dp))
        Text("No tienes citas próximas", color = Color(0xFF1F2937), fontSize = 16.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(4.dp))
        Text(
            "Cuando reserves una cita, aparecerá aquí.",
            color = Muted, fontSize = 13.sp,
        )
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
        Modifier.clip(RoundedCornerShape(20.dp)).background(bg)
            .padding(horizontal = 12.dp, vertical = 5.dp),
    ) { Text(estado, color = fg, fontSize = 11.sp, fontWeight = FontWeight.Bold) }
}

private fun colorEstadoSesion(estado: String): Color = when (estado) {
    "Completada" -> GreenOk
    "En progreso" -> Color(0xFF1D6FA8)
    "Reprogramada" -> Amber
    else -> Muted
}