package pe.saniape.app.ui.clinica

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

/** Android: permiso de ubicación precisa + una posición de alta precisión (FusedLocationProvider). */
@Composable
actual fun recordarUbicacionPrecisa(onUbicacion: (PuntoGps?) -> Unit): () -> Unit {
    val context = LocalContext.current
    val callback by rememberUpdatedState(onUbicacion)
    val permiso = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { concedido ->
        if (concedido) puntoActual(context) { callback(it) } else callback(null)
    }
    return remember {
        {
            val ya = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
            if (ya) puntoActual(context) { callback(it) } else permiso.launch(Manifest.permission.ACCESS_FINE_LOCATION)
        }
    }
}

private fun puntoActual(context: Context, listo: (PuntoGps?) -> Unit) {
    try {
        LocationServices.getFusedLocationProviderClient(context)
            .getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, null)
            .addOnSuccessListener { loc ->
                listo(if (loc != null) PuntoGps(loc.latitude, loc.longitude, if (loc.hasAccuracy()) loc.accuracy.toDouble() else null) else null)
            }
            .addOnFailureListener { listo(null) }
    } catch (e: SecurityException) {
        listo(null)
    }
}
