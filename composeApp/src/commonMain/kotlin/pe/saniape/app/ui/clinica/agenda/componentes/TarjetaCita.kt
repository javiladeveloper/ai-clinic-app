package pe.saniape.app.ui.clinica.agenda.componentes

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import pe.saniape.app.data.staff.CitaStaff
import pe.saniape.app.ui.recordarAcciones
import pe.saniape.app.ui.theme.Sania

/**
 * Tarjeta de una cita en la agenda. Muestra (como la web): color por tipo, hora,
 * paciente, "Sesión #N", costo, profesional, badge "🌐 Web", botones de contacto
 * (📞/💬) y las acciones según estado.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TarjetaCita(
    cita: CitaStaff,
    puedeVerCosto: Boolean,
    accionando: Boolean,
    onAccion: (AccionTarjeta) -> Unit,
) {
    val c = Sania.colors
    val acciones = recordarAcciones()
    val colorTipo = when (cita.tipo) {
        "Evaluación" -> c.info
        "Sesión" -> c.ok
        else -> c.navy
    }

    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(Sania.shape.md.dp)).background(c.superficie)
            .border(1.dp, c.borde, RoundedCornerShape(Sania.shape.md.dp)).padding(Sania.dim.tarjeta),
    ) {
        // Cabecera: punto tipo + hora + badge estado
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(8.dp).clip(CircleShape).background(colorTipo))
            Spacer(Modifier.width(8.dp))
            Text(cita.hora.take(5), color = c.navy, fontSize = Sania.txt.seccion, fontWeight = FontWeight.Bold)
            Spacer(Modifier.weight(1f))
            BadgeEstadoCita(cita.estado, cita.confirmadaPorPaciente)
        }
        Spacer(Modifier.height(6.dp))

        // Paciente + teléfono/contacto
        Text(cita.pacienteNombre ?: "Paciente", color = c.texto,
            fontSize = Sania.txt.cuerpo, fontWeight = FontWeight.SemiBold)

        // Línea de info: tipo (+ Sesión #N) · procedimiento · profesional
        val infoTipo = buildString {
            append(cita.tipo ?: "Cita")
            if (cita.tipo == "Sesión" && cita.numeroSesion != null) append(" #${cita.numeroSesion}")
        }
        Text(
            listOfNotNull(infoTipo, cita.procedimiento, cita.terapeutaNombre?.let { "con $it" })
                .joinToString(" · "),
            color = c.textoSuave, fontSize = 12.sp,
        )

        // Nota de recepción (recordatorio del tratamiento) — 📌
        cita.notaRecepcion?.let { nota ->
            Spacer(Modifier.height(4.dp))
            Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(Sania.shape.sm.dp))
                .background(c.pendBg).padding(horizontal = 8.dp, vertical = 5.dp)) {
                Text("📌 $nota", color = c.pend, fontSize = 11.sp, fontWeight = FontWeight.Medium)
            }
        }

        // Chips: costo, Web, asignar
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(top = 6.dp)) {
            if (puedeVerCosto) {
                val costoTxt = when {
                    (cita.costo ?: 0.0) > 0 -> "S/ ${formato2(cita.costo!!)}"
                    cita.tipo == "Consulta" -> "Gratis"
                    else -> null
                }
                costoTxt?.let { Chip(it, c.teal, c.tealBg) }
            }
            if (cita.origen == "online") Chip("🌐 Web", c.purple, c.purpleBg)
            if (cita.terapeutaId == null && cita.origen == "online") Chip("⚠ Asignar", c.pend, c.pendBg)
        }

        // Botones de contacto (si hay teléfono)
        cita.pacienteTelefono?.takeIf { it.isNotBlank() }?.let { tel ->
            Spacer(Modifier.height(6.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                MiniAccion("📞 Llamar", c.navy) { acciones.abrirUrl("tel:${tel.filter { ch -> ch.isDigit() }}") }
                MiniAccion("💬 WhatsApp", Color(0xFF25D366)) {
                    val n = tel.filter { ch -> ch.isDigit() }.let { if (it.length <= 9) "51$it" else it }
                    acciones.abrirUrl("https://wa.me/$n")
                }
            }
        }

        // Acciones según estado
        val acc = accionesPara(cita.estado, cita.tipo)
        if (acc.isNotEmpty()) {
            Spacer(Modifier.height(Sania.dim.md))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                acc.forEach { (label, accion, color) ->
                    BotonAccion(label, color, !accionando) { onAccion(accion) }
                }
            }
        }
    }
}

enum class AccionTarjeta { Confirmar, Completar, Cancelar, Revertir, Editar, PasarEvaluacion }

/** Acciones disponibles según estado/tipo (espeja accionesCita de la web). */
@Composable
private fun accionesPara(estado: String, tipo: String?): List<Triple<String, AccionTarjeta, Color>> {
    val c = Sania.colors
    val lista = mutableListOf<Triple<String, AccionTarjeta, Color>>()
    val activa = estado == "Pendiente" || estado == "Confirmada"
    if (estado == "Pendiente") lista.add(Triple("✓ Confirmar", AccionTarjeta.Confirmar, c.ok))
    if (tipo == "Consulta" && activa) lista.add(Triple("→ Evaluación", AccionTarjeta.PasarEvaluacion, c.info))
    if (activa) lista.add(Triple("✓ Completar", AccionTarjeta.Completar, c.navy))
    if (estado == "Completada" || estado == "Cancelada") lista.add(Triple("↩ Revertir", AccionTarjeta.Revertir, c.pend))
    if (estado != "Cancelada" && estado != "Completada") {
        lista.add(Triple("✏ Editar", AccionTarjeta.Editar, c.textoSuave))
        lista.add(Triple("✕ Cancelar", AccionTarjeta.Cancelar, c.error))
    }
    return lista
}

@Composable
private fun BotonAccion(label: String, color: Color, habilitado: Boolean, onClick: () -> Unit) {
    Box(
        Modifier.clip(RoundedCornerShape(Sania.shape.sm.dp))
            .background(color.copy(alpha = 0.12f))
            .border(1.dp, color, RoundedCornerShape(Sania.shape.sm.dp))
            .clickable(enabled = habilitado) { onClick() }
            .padding(horizontal = 12.dp, vertical = 8.dp),
    ) { Text(label, color = color, fontSize = 12.sp, fontWeight = FontWeight.Bold) }
}

@Composable
private fun MiniAccion(label: String, color: Color, onClick: () -> Unit) {
    Box(
        Modifier.clip(RoundedCornerShape(Sania.shape.sm.dp))
            .border(1.dp, color.copy(alpha = 0.5f), RoundedCornerShape(Sania.shape.sm.dp))
            .clickable { onClick() }.padding(horizontal = 10.dp, vertical = 5.dp),
    ) { Text(label, color = color, fontSize = 11.sp, fontWeight = FontWeight.Bold) }
}

@Composable
private fun Chip(texto: String, fg: Color, bg: Color) {
    Box(Modifier.clip(RoundedCornerShape(Sania.shape.pill.dp)).background(bg)
        .padding(horizontal = 8.dp, vertical = 3.dp)) {
        Text(texto, color = fg, fontSize = 10.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
fun BadgeEstadoCita(estado: String, confirmadaPaciente: Boolean = false) {
    val c = Sania.colors
    val etiqueta = if (estado == "Pendiente") "Sin confirmar" else estado
    val (fg, bg) = when (estado) {
        "Confirmada", "Completada" -> c.ok to c.okBg
        "Pendiente" -> c.pend to c.pendBg
        "Cancelada" -> c.error to c.errorBg
        else -> c.navy to c.chipBg
    }
    Column(horizontalAlignment = Alignment.End) {
        Box(Modifier.clip(RoundedCornerShape(Sania.shape.pill.dp)).background(bg)
            .padding(horizontal = 10.dp, vertical = 4.dp)) {
            Text(etiqueta, color = fg, fontSize = 11.sp, fontWeight = FontWeight.Bold)
        }
        if (confirmadaPaciente) {
            Text("✓ Confirmó el paciente", color = c.ok, fontSize = 9.sp, fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(top = 2.dp))
        }
    }
}

private fun formato2(n: Double): String {
    val cent = (n * 100).toLong()
    return "${cent / 100}.${(cent % 100).toString().padStart(2, '0')}"
}