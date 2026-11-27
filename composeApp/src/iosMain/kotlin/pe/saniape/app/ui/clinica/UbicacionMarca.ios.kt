package pe.saniape.app.ui.clinica

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.useContents
import platform.CoreLocation.CLLocation
import platform.CoreLocation.CLLocationManager
import platform.CoreLocation.CLLocationManagerDelegateProtocol
import platform.CoreLocation.kCLAuthorizationStatusAuthorizedAlways
import platform.CoreLocation.kCLAuthorizationStatusAuthorizedWhenInUse
import platform.CoreLocation.kCLAuthorizationStatusDenied
import platform.CoreLocation.kCLAuthorizationStatusRestricted
import platform.CoreLocation.kCLLocationAccuracyBest
import platform.Foundation.NSError
import platform.darwin.NSObject

/** iOS: permiso «cuando se usa» + una posición con la mejor precisión (requestLocation). */
@Composable
actual fun recordarUbicacionPrecisa(onUbicacion: (PuntoGps?) -> Unit): () -> Unit {
    val callback by rememberUpdatedState(onUbicacion)
    val delegate = remember { MarcaDelegate { callback(it) } }
    val manager = remember { CLLocationManager().also { it.delegate = delegate; it.desiredAccuracy = kCLLocationAccuracyBest } }
    return {
        when (manager.authorizationStatus) {
            kCLAuthorizationStatusAuthorizedWhenInUse, kCLAuthorizationStatusAuthorizedAlways -> { delegate.esperando = true; manager.requestLocation() }
            kCLAuthorizationStatusDenied, kCLAuthorizationStatusRestricted -> callback(null)
            else -> { delegate.esperando = true; manager.requestWhenInUseAuthorization() }
        }
    }
}

@OptIn(ExperimentalForeignApi::class)
private class MarcaDelegate(private val listo: (PuntoGps?) -> Unit) : NSObject(), CLLocationManagerDelegateProtocol {
    /** Solo responde a una solicitud en curso: iOS avisa del permiso también al crear el manager. */
    var esperando = false

    override fun locationManagerDidChangeAuthorization(manager: CLLocationManager) {
        if (!esperando) return
        when (manager.authorizationStatus) {
            kCLAuthorizationStatusAuthorizedWhenInUse, kCLAuthorizationStatusAuthorizedAlways -> manager.requestLocation()
            kCLAuthorizationStatusDenied, kCLAuthorizationStatusRestricted -> { esperando = false; listo(null) }
            else -> {}
        }
    }

    override fun locationManager(manager: CLLocationManager, didUpdateLocations: List<*>) {
        if (!esperando) return
        esperando = false
        val loc = didUpdateLocations.lastOrNull() as? CLLocation
        if (loc == null) { listo(null); return }
        val precision = loc.horizontalAccuracy.takeIf { it >= 0 }
        loc.coordinate.useContents { listo(PuntoGps(latitude, longitude, precision)) }
    }

    override fun locationManager(manager: CLLocationManager, didFailWithError: NSError) {
        if (!esperando) return
        esperando = false
        listo(null)
    }
}
