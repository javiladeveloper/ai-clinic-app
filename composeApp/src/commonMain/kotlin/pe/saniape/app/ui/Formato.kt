package pe.saniape.app.ui

import kotlinx.datetime.Clock
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

/**
 * Formatea una hora guardada en 24h ("HH:mm" o "HH:mm:ss") a 12h con AM/PM compacto
 * (ej. "10:25" → "10:25 AM", "14:00" → "2:00 PM"). Igual que horaHumana de la web.
 * La BD siempre guarda 24h; esto es solo presentación.
 */
fun hora12(hora24: String?): String {
    if (hora24.isNullOrBlank()) return "—"
    val partes = hora24.split(":")
    val h = partes.getOrNull(0)?.toIntOrNull() ?: return hora24.take(5)
    val m = partes.getOrNull(1)?.toIntOrNull() ?: 0
    val sufijo = if (h < 12) "AM" else "PM"
    val h12 = when {
        h == 0 -> 12
        h > 12 -> h - 12
        else -> h
    }
    return "$h12:${m.toString().padStart(2, '0')} $sufijo"
}

/**
 * Próxima hora EN PUNTO futura (según reloj local), acotada 8am–8pm. Evita que una
 * cita/sesión nueva nazca con una hora ya pasada o fuera del horario típico. Igual que
 * proximaHora() de la web (CitaForm).
 */
fun proximaHoraEnPunto(): String {
    val ahora = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault())
    var h = ahora.hour + (if (ahora.minute > 0) 1 else 0)
    if (h < 8) h = 8
    if (h > 20) h = 20
    return "${h.toString().padStart(2, '0')}:00"
}