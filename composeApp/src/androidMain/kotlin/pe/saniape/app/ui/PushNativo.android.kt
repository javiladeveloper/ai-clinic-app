package pe.saniape.app.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import com.google.firebase.messaging.FirebaseMessaging
import pe.saniape.app.data.staff.PushRepo
import pe.saniape.app.push.FirebaseCfg
import kotlin.coroutines.resume
import kotlinx.coroutines.suspendCancellableCoroutine

/**
 * Android: pide POST_NOTIFICATIONS (13+) si falta y registra el token FCM del dispositivo.
 * Todo best-effort y silencioso: si Firebase no está configurado o el usuario niega el
 * permiso, la app sigue normal (solo no habrá notificaciones en la barra).
 */
@Composable
actual fun EfectoPushNativo() {
    if (!FirebaseCfg.activo) return
    val context = LocalContext.current

    val pedirPermiso = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { /* concedido o no, el registro del token va aparte */ }

    LaunchedEffect(Unit) {
        // 1) Permiso de notificaciones (solo Android 13+ lo exige en runtime).
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED
        ) {
            pedirPermiso.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
        // 2) Token FCM del dispositivo → registrar para el usuario logueado.
        val token = obtenerTokenFcm() ?: return@LaunchedEffect
        PushRepo.registrarToken(token)
    }
}

private suspend fun obtenerTokenFcm(): String? = suspendCancellableCoroutine { cont ->
    try {
        FirebaseMessaging.getInstance().token
            .addOnSuccessListener { cont.resume(it) }
            .addOnFailureListener { cont.resume(null) }
    } catch (_: Exception) {
        cont.resume(null)
    }
}
