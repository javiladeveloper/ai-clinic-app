package pe.saniape.app.ui.clinica.agenda.componentes

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
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
import pe.saniape.app.ui.hora12
import pe.saniape.app.ui.recordarAcciones
import pe.saniape.app.ui.theme.EstadosColor
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
    // Color del tipo desde la config global (reutilizable en toda la app).
    val tipoColor = EstadosColor.tipo(cita.tipo)

    // Atenuar las cerradas (completada/cancelada) para que las activas resalten.
    val cerrada = cita.estado == "Completada" || cita.estado == "Cancelada"

    Row(
        Modifier.fillMaxWidth().height(IntrinsicSize.Min)
            .clip(RoundedCornerShape(Sania.shape.md.dp)).background(c.superficie)
            .border(1.dp, c.borde, RoundedCornerShape(Sania.shape.md.dp)),
    ) {
        // Barra de color por tipo (acento lateral más grueso, marca el tipo de un vistazo).
        Box(Modifier.width(6.dp).fillMaxHeight().background(if (cerrada) c.borde else tipoColor.fg))

        Column(Modifier.fillMaxWidth().padding(Sania.dim.tarjeta)) {
            // Línea 1: CHIP de tipo prominente ····· badge de estado
            Row(verticalAlignment = Alignment.CenterVertically) {
                ChipTipo(cita.tipo, cita.numeroSesion, atenuado = cerrada)
                Spacer(Modifier.weight(1f))
                BadgeEstadoCita(cita.estado, cita.confirmadaPorPaciente)
            }

            Spacer(Modifier.height(8.dp))

            // Hora grande (referencia rápida del día)
            Text(hora12(cita.hora), color = if (cerrada) c.textoSuave else c.navy,
                fontSize = Sania.txt.seccion, fontWeight = FontWeight.Bold)

            Spacer(Modifier.height(6.dp))

            // Línea: nombre del paciente + iconos de contacto a la derecha
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(cita.pacienteNombre ?: "Paciente", color = c.texto,
                    fontSize = Sania.txt.cuerpo, fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f))
                cita.pacienteTelefono?.takeIf { it.isNotBlank() }?.let { tel ->
                    IconoContacto("📞", c.navy) { acciones.abrirUrl("tel:${tel.filter { ch -> ch.isDigit() }}") }
                    Spacer(Modifier.width(6.dp))
                    // WhatsApp con el RECORDATORIO de la cita prellenado (como el 📱 de la web):
                    // un toque y el mensaje sale listo para enviar.
                    IconoContacto("💬", Color(0xFF25D366)) {
                        val n = tel.filter { ch -> ch.isDigit() }.let { if (it.length <= 9) "51$it" else it }
                        val nombre = cita.pacienteNombre?.trim()?.split(" ")?.firstOrNull() ?: ""
                        val msg = "Hola $nombre 👋 Te recordamos tu cita" +
                            (cita.tipo?.let { " de ${it.lowercase()}" } ?: "") +
                            " el ${cita.fecha} a las ${hora12(cita.hora)}. ¿Nos confirmas tu asistencia? 🙌"
                        acciones.abrirUrl("https://wa.me/$n?text=${pe.saniape.app.ui.urlEncode(msg)}")
                    }
                }
            }

            // Línea 3: procedimiento · profesional (sutil)
            val sub = listOfNotNull(cita.procedimiento, cita.terapeutaNombre?.let { "con $it" })
                .joinToString(" · ")
            if (sub.isNotBlank()) {
                Text(sub, color = c.textoSuave, fontSize = 12.sp, modifier = Modifier.padding(top = 2.dp))
            }

            // Chips: costo, Web, Asignar (discretos, en una línea)
            val chips = buildList {
                if (puedeVerCosto) {
                    when {
                        (cita.costo ?: 0.0) > 0 -> add(Triple("S/ ${formato2(cita.costo!!)}", c.teal, c.tealBg))
                        cita.tipo == "Consulta" -> add(Triple("Gratis", c.teal, c.tealBg))
                    }
                }
                if (cita.origen == "online") add(Triple("🌐 Web", c.purple, c.purpleBg))
                if (cita.terapeutaId == null && cita.origen == "online") add(Triple("⚠ Asignar", c.pend, c.pendBg))
            }
            if (chips.isNotEmpty()) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(top = 8.dp)) {
                    chips.forEach { (t, fg, bg) -> Chip(t, fg, bg) }
                }
            }

            // Nota de recepción (recordatorio del tratamiento) — 📌
            cita.notaRecepcion?.let { nota ->
                Spacer(Modifier.height(8.dp))
                Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(Sania.shape.sm.dp))
                    .background(c.pendBg).padding(horizontal = 8.dp, vertical = 6.dp)) {
                    Text("📌 $nota", color = c.pend, fontSize = 11.sp, fontWeight = FontWeight.Medium)
                }
            }

            // Acciones según estado (separadas por un divisor sutil)
            val acc = accionesPara(cita.estado, cita.tipo)
            if (acc.isNotEmpty()) {
                Spacer(Modifier.height(Sania.dim.md))
                Box(Modifier.fillMaxWidth().height(1.dp).background(c.borde))
                Spacer(Modifier.height(Sania.dim.md))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    acc.forEach { (label, accion, color) ->
                        BotonAccion(label, color, !accionando) { onAccion(accion) }
                    }
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

/** Icono de contacto compacto (📞/💬) — botón redondo discreto junto al nombre. */
@Composable
private fun IconoContacto(emoji: String, color: Color, onClick: () -> Unit) {
    Box(
        Modifier.size(34.dp).clip(CircleShape).background(color.copy(alpha = 0.12f))
            .clickable { onClick() },
        contentAlignment = Alignment.Center,
    ) { Text(emoji, fontSize = 15.sp) }
}

@Composable
private fun Chip(texto: String, fg: Color, bg: Color) {
    Box(Modifier.clip(RoundedCornerShape(Sania.shape.pill.dp)).background(bg)
        .padding(horizontal = 8.dp, vertical = 3.dp)) {
        Text(texto, color = fg, fontSize = 10.sp, fontWeight = FontWeight.Bold)
    }
}

/** Chip prominente del TIPO de cita (icono + nombre en mayúsculas + color global). */
@Composable
private fun ChipTipo(tipo: String?, numeroSesion: Int?, atenuado: Boolean) {
    val color = EstadosColor.tipo(tipo)
    val fg = if (atenuado) Sania.colors.textoSuave else color.fg
    val bg = if (atenuado) Sania.colors.chipBg else color.bg
    val etiqueta = buildString {
        append((tipo ?: "Cita").uppercase())
        if (tipo == "Sesión" && numeroSesion != null) append(" #$numeroSesion")
    }
    Box(Modifier.clip(RoundedCornerShape(Sania.shape.pill.dp)).background(bg)
        .padding(horizontal = 10.dp, vertical = 5.dp)) {
        Text("${EstadosColor.iconoTipo(tipo)} $etiqueta", color = fg,
            fontSize = 11.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
fun BadgeEstadoCita(estado: String, confirmadaPaciente: Boolean = false) {
    val c = Sania.colors
    // Colores desde la config global (Confirmada=verde, Completada=azul, etc.).
    val color = EstadosColor.cita(estado)
    val etiqueta = EstadosColor.etiquetaCita(estado)
    Column(horizontalAlignment = Alignment.End) {
        Box(Modifier.clip(RoundedCornerShape(Sania.shape.pill.dp)).background(color.bg)
            .padding(horizontal = 10.dp, vertical = 4.dp)) {
            Text(etiqueta, color = color.fg, fontSize = 11.sp, fontWeight = FontWeight.Bold)
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