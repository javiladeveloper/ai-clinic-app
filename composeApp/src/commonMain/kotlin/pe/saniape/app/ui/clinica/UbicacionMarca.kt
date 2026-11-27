package pe.saniape.app.ui.clinica

import androidx.compose.runtime.Composable

/** Dónde está el celular al marcar asistencia. [precision] en metros (null si el equipo no la da). */
data class PuntoGps(val lat: Double, val lng: Double, val precision: Double?)

/**
 * 📍 La ubicación PRECISA del celular, para marcar asistencia (27/11/2026): la marca
 * solo vale dentro de la clínica y lo decide el servidor con este punto. A diferencia
 * de la del directorio de clínicas (ui/reservar), pide la mejor precisión y la devuelve.
 *
 * [onUbicacion] recibe el punto, o null si no hay permiso, el GPS está apagado o falló.
 * Devuelve la función que dispara la solicitud.
 */
@Composable
expect fun recordarUbicacionPrecisa(onUbicacion: (PuntoGps?) -> Unit): () -> Unit
