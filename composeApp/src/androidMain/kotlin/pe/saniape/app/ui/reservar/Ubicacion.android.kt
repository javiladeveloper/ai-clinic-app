package pe.saniape.app.ui.reservar

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority

/**
 * Android: pide permiso de ubicación y obtiene la posición actual con
 * FusedLocationProvider (Play Services). Más preciso que el geolocation web.
 */
@Composable
actual fun recordarSolicitarUbicacion(
    onUbicacion: (Pair<Double, Double>?) -> Unit,
): () -> Unit {
    val context = LocalContext.current
    val callback by rememberUpdatedState(onUbicacion)

    val permLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { concedido ->
        if (concedido) obtenerUbicacion(context, callback) else callback(null)
    }

    return remember {
        {
            val yaConcedido = ContextCompat.checkSelfPermission(
                context, Manifest.permission.ACCESS_FINE_LOCATION,
            ) == PackageManager.PERMISSION_GRANTED
            if (yaConcedido) obtenerUbicacion(context, callback)
            else permLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
        }
    }
}

private fun obtenerUbicacion(context: Context, onUbicacion: (Pair<Double, Double>?) -> Unit) {
    try {
        val cliente = LocationServices.getFusedLocationProviderClient(context)
        cliente.getCurrentLocation(Priority.PRIORITY_BALANCED_POWER_ACCURACY, null)
            .addOnSuccessListener { loc ->
                onUbicacion(if (loc != null) loc.latitude to loc.longitude else null)
            }
            .addOnFailureListener { onUbicacion(null) }
    } catch (e: SecurityException) {
        onUbicacion(null)
    }
}