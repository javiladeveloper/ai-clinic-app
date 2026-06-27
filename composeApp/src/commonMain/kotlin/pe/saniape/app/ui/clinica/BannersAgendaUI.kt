package pe.saniape.app.ui.clinica

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import pe.saniape.app.data.staff.BannersAgenda
import pe.saniape.app.data.staff.CitaStaff
import pe.saniape.app.data.staff.Derivacion
import pe.saniape.app.ui.hora12
import pe.saniape.app.ui.recordarAcciones
import pe.saniape.app.data.staff.NivelRiesgo
import pe.saniape.app.ui.theme.Sania

/**
 * Banners de la agenda (igual que la web): derivaciones pendientes, citas vencidas
 * sin cerrar, y recordatorio de citas de mañana. Colapsables.
 */
@Composable
fun BannersAgendaUI(
    banners: BannersAgenda,
    onVerCitaManana: (CitaStaff) -> Unit,
    onCerrarVencida: (CitaStaff, vino: Boolean) -> Unit,
    onReagendarVencida: (CitaStaff) -> Unit,
    onAgendarDerivacion: (Derivacion) -> Unit,
    onMarcarDerivacion: (Derivacion) -> Unit,
) {
    val c = Sania.colors
    val acciones = recordarAcciones()
    Column(Modifier.fillMaxWidth().padding(horizontal = Sania.dim.lg)) {
        // ── Derivaciones pendientes (morado) ──
        if (banners.derivaciones.isNotEmpty()) {
            BannerColapsable(
                titulo = "↗ ${banners.derivaciones.size} derivación(es) pendiente(s)",
                subtitulo = "Agenda su evaluación con el especialista.",
                colorFg = c.navy, colorBg = c.chipBg, colorBorde = c.navy,
                abiertoInicial = true,
            ) {
                banners.derivaciones.forEach { d ->
                    Row(
                        Modifier.fillMaxWidth().padding(vertical = 4.dp),
                        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(d.pacienteNombre ?: "Paciente", color = c.texto,
                                fontSize = Sania.txt.pequeno, fontWeight = FontWeight.SemiBold)
                            d.especialidadDestino?.let { Text("→ $it", color = c.textoSuave, fontSize = 12.sp) }
                        }
                        MiniBoton("📅 Agendar", c.navy) { onAgendarDerivacion(d) }
                        Spacer(Modifier.width(6.dp))
                        MiniBoton("✓", c.ok) { onMarcarDerivacion(d) }
                    }
                }
            }
            Spacer(Modifier.height(Sania.dim.sm))
        }

        // ── Citas vencidas sin cerrar (ámbar) ──
        if (banners.vencidas.isNotEmpty()) {
            BannerColapsable(
                titulo = "⏰ ${banners.vencidas.size} cita(s) sin cerrar",
                subtitulo = "Pasaron de fecha y siguen pendientes. ¿El paciente asistió?",
                colorFg = c.pend, colorBg = c.pendBg, colorBorde = c.pend,
                abiertoInicial = false,
            ) {
                banners.vencidas.forEach { cita ->
                    Column(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
                        Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text("${cita.fecha} · ${hora12(cita.hora)} · ${cita.tipo ?: ""}",
                                    color = c.texto, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                                Text(cita.pacienteNombre ?: "Paciente", color = c.textoSuave, fontSize = 12.sp)
                            }
                            cita.pacienteTelefono?.takeIf { it.isNotBlank() }?.let { tel ->
                                IconoMini("📞", c.navy) { acciones.abrirUrl("tel:${tel.filter { ch -> ch.isDigit() }}") }
                                Spacer(Modifier.width(6.dp))
                                IconoMini("💬", androidx.compose.ui.graphics.Color(0xFF25D366)) {
                                    val n = tel.filter { ch -> ch.isDigit() }.let { if (it.length <= 9) "51$it" else it }
                                    acciones.abrirUrl("https://wa.me/$n")
                                }
                            }
                        }
                        Row(Modifier.padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            MiniBoton("✓ Sí vino", c.ok) { onCerrarVencida(cita, true) }
                            MiniBoton("✗ No vino", c.error) { onCerrarVencida(cita, false) }
                            MiniBoton("📅 Reagendar", c.navy) { onReagendarVencida(cita) }
                        }
                    }
                }
            }
            Spacer(Modifier.height(Sania.dim.sm))
        }

        // ── Próximas citas — mañana (azul) ──
        if (banners.manana.isNotEmpty()) {
            BannerColapsable(
                titulo = "🔔 Próximas citas — mañana",
                subtitulo = "${banners.manana.size} cita(s) programada(s) para mañana.",
                colorFg = c.info, colorBg = c.infoBg, colorBorde = c.info,
                abiertoInicial = false,
            ) {
                banners.manana.forEach { item ->
                    val cita = item.cita
                    val riesgoAlto = item.riesgo.nivel == NivelRiesgo.Alto && !cita.confirmadaPorPaciente
                    Row(
                        Modifier.fillMaxWidth().padding(vertical = 4.dp).clickable { onVerCitaManana(cita) },
                        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
                    ) {
                        Text(hora12(cita.hora), color = c.navy, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.width(8.dp))
                        Column(Modifier.weight(1f)) {
                            Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                                Text(cita.pacienteNombre ?: "Paciente", color = c.texto, fontSize = 12.sp)
                                if (riesgoAlto) {
                                    Spacer(Modifier.width(6.dp))
                                    Box(
                                        Modifier.clip(RoundedCornerShape(Sania.shape.pill.dp))
                                            .background(c.errorBg).padding(horizontal = 6.dp, vertical = 2.dp),
                                    ) { Text("⚠ riesgo de falta", color = c.error, fontSize = 9.sp, fontWeight = FontWeight.Bold) }
                                }
                            }
                            // Motivos del riesgo (si es alto), o profesional.
                            if (riesgoAlto && item.riesgo.motivos.isNotEmpty()) {
                                Text(item.riesgo.motivos.joinToString(" · "), color = c.textoSuave, fontSize = 10.sp)
                            } else {
                                cita.terapeutaNombre?.let { Text(it, color = c.textoSuave, fontSize = 11.sp) }
                            }
                        }
                        // Estado de confirmación + contacto para empezar recordatorios.
                        if (cita.confirmadaPorPaciente) {
                            Text("✓ Confirmó", color = c.ok, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                        cita.pacienteTelefono?.takeIf { it.isNotBlank() }?.let { tel ->
                            Spacer(Modifier.width(6.dp))
                            IconoMini("📞", c.navy) { acciones.abrirUrl("tel:${tel.filter { ch -> ch.isDigit() }}") }
                            Spacer(Modifier.width(6.dp))
                            IconoMini("💬", androidx.compose.ui.graphics.Color(0xFF25D366)) {
                                val n = tel.filter { ch -> ch.isDigit() }.let { if (it.length <= 9) "51$it" else it }
                                acciones.abrirUrl("https://wa.me/$n")
                            }
                        }
                    }
                }
            }
            Spacer(Modifier.height(Sania.dim.sm))
        }
    }
}

@Composable
private fun BannerColapsable(
    titulo: String,
    subtitulo: String,
    colorFg: androidx.compose.ui.graphics.Color,
    colorBg: androidx.compose.ui.graphics.Color,
    colorBorde: androidx.compose.ui.graphics.Color,
    abiertoInicial: Boolean,
    contenido: @Composable () -> Unit,
) {
    val c = Sania.colors
    var abierto by remember { mutableStateOf(abiertoInicial) }
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(Sania.shape.md.dp))
            .background(colorBg).border(1.dp, colorBorde.copy(alpha = 0.4f), RoundedCornerShape(Sania.shape.md.dp))
            .padding(Sania.dim.md),
    ) {
        Row(
            Modifier.fillMaxWidth().clickable { abierto = !abierto },
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(titulo, color = colorFg, fontSize = Sania.txt.pequeno, fontWeight = FontWeight.Bold)
                Text(subtitulo, color = c.textoSuave, fontSize = 11.sp)
            }
            Text(if (abierto) "▲" else "▼", color = colorFg, fontSize = 12.sp)
        }
        AnimatedVisibility(abierto) {
            Column(Modifier.padding(top = Sania.dim.sm)) { contenido() }
        }
    }
}

@Composable
private fun MiniBoton(texto: String, color: androidx.compose.ui.graphics.Color, onClick: () -> Unit) {
    androidx.compose.foundation.layout.Box(
        Modifier.clip(RoundedCornerShape(Sania.shape.sm.dp))
            .background(color.copy(alpha = 0.12f))
            .border(1.dp, color, RoundedCornerShape(Sania.shape.sm.dp))
            .clickable { onClick() }.padding(horizontal = 10.dp, vertical = 6.dp),
    ) { Text(texto, color = color, fontSize = 11.sp, fontWeight = FontWeight.Bold) }
}

/** Icono de contacto compacto (📞/💬) para los banners. */
@Composable
private fun IconoMini(emoji: String, color: androidx.compose.ui.graphics.Color, onClick: () -> Unit) {
    androidx.compose.foundation.layout.Box(
        Modifier.size(30.dp).clip(androidx.compose.foundation.shape.CircleShape)
            .background(color.copy(alpha = 0.12f)).clickable { onClick() },
        contentAlignment = androidx.compose.ui.Alignment.Center,
    ) { Text(emoji, fontSize = 13.sp) }
}