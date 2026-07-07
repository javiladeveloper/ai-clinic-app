package pe.saniape.app.ui.reservar

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.useContents
import platform.CoreLocation.CLLocation
import platform.CoreLocation.CLLocationManager
import platform.CoreLocation.CLLocationManagerDelegateProtocol
import platform.CoreLocation.kCLAuthorizationStatusAuthorizedAlways
import platform.CoreLocation.kCLAuthorizationStatusAuthorizedWhenInUse
import platform.CoreLocation.kCLAuthorizationStatusDenied
import platform.CoreLocation.kCLAuthorizationStatusRestricted
import platform.Foundation.NSError
import platform.darwin.NSObject

/**
 * iOS: ubicación del paciente para el directorio de clínicas cercanas.
 * Pide permiso "cuando se usa" y una sola posición (requestLocation), equivalente al
 * FusedLocationProvider de Android. Requiere NSLocationWhenInUseUsageDescription (ya está
 * en Info.plist). Cualquier fallo/denegación → onUbicacion(null), que la UI ya maneja.
 */
@Composable
actual fun recordarSolicitarUbicacion(
    onUbicacion: (Pair<Double, Double>?) -> Unit,
): () -> Unit {
    val delegate = remember { UbicacionDelegate(onUbicacion) }
    val manager = remember { CLLocationManager().also { it.delegate = delegate } }
    return {
        when (manager.authorizationStatus) {
            kCLAuthorizationStatusAuthorizedWhenInUse, kCLAuthorizationStatusAuthorizedAlways ->
                manager.requestLocation()
            kCLAuthorizationStatusDenied, kCLAuthorizationStatusRestricted ->
                onUbicacion(null)
            else ->
                // Aún sin decidir: dispara el prompt; el resultado llega por el delegate.
                manager.requestWhenInUseAuthorization()
        }
    }
}

@OptIn(ExperimentalForeignApi::class)
private class UbicacionDelegate(
    private val onUbicacion: (Pair<Double, Double>?) -> Unit,
) : NSObject(), CLLocationManagerDelegateProtocol {

    override fun locationManagerDidChangeAuthorization(manager: CLLocationManager) {
        when (manager.authorizationStatus) {
            kCLAuthorizationStatusAuthorizedWhenInUse, kCLAuthorizationStatusAuthorizedAlways ->
                manager.requestLocation()
            kCLAuthorizationStatusDenied, kCLAuthorizationStatusRestricted ->
                onUbicacion(null)
            else -> {}
        }
    }

    override fun locationManager(manager: CLLocationManager, didUpdateLocations: List<*>) {
        val loc = didUpdateLocations.lastOrNull() as? CLLocation
        if (loc == null) {
            onUbicacion(null)
            return
        }
        loc.coordinate.useContents { onUbicacion(latitude to longitude) }
    }

    override fun locationManager(manager: CLLocationManager, didFailWithError: NSError) {
        onUbicacion(null)
    }
}
