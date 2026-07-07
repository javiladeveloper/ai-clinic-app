package pe.saniape.app.ui.reservar

import androidx.compose.runtime.Composable

/**
 * iOS: ubicación del paciente para el directorio de clínicas cercanas.
 *
 * TODO(iOS Fase 2): implementar con CLLocationManager (requestWhenInUseAuthorization +
 * requestLocation, con un delegate que entregue lat/lng). Requiere
 * NSLocationWhenInUseUsageDescription en el Info.plist.
 *
 * Por ahora (Fase 1) devuelve null → el directorio muestra "otras ciudades" sin
 * "cerca de ti", que la UI ya maneja sin ubicación.
 */
@Composable
actual fun recordarSolicitarUbicacion(
    onUbicacion: (Pair<Double, Double>?) -> Unit,
): () -> Unit = {
    onUbicacion(null)
}
