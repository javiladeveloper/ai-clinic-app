package pe.saniape.app.ui

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.util.Log
import androidx.core.app.NotificationManagerCompat
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import com.google.firebase.messaging.FirebaseMessaging
import io.github.jan.supabase.auth.auth
import pe.saniape.app.data.Supabase
import pe.saniape.app.data.staff.PushRepo
import pe.saniape.app.push.FirebaseCfg
import kotlin.coroutines.resume
import kotlinx.coroutines.suspendCancellableCoroutine

/**
 * Android: pide POST_NOTIFICATIONS (13+) si falta y registra el token FCM del dispositivo.
 *
 * Tres cosas que fallaban y por las que un fisio entraba a la app y NUNCA le
 * llegaba un push (2026-08-10):
 *
 *  1. Se pedía el permiso y se seguía de largo SIN ESPERAR la respuesta:
 *     `launch()` no bloquea, así que el token se pedía con el diálogo todavía
 *     abierto. Ahora el registro espera a que el usuario conteste.
 *  2. `LaunchedEffect(Unit)` corre UNA vez por montaje: al cambiar de cuenta en
 *     el mismo celular no volvía a ejecutarse, y el token quedaba registrado
 *     para el usuario anterior. Ahora la clave es el id del usuario.
 *  3. Los errores se tragaban en silencio. Si fallaba la RLS, la red o Firebase,
 *     no quedaba rastro en ningún lado y la única forma de saberlo era consultar
 *     la base a mano. Ahora queda en el log (Logcat, etiqueta "SaniaPush").
 */
private const val TAG = "SaniaPush"

@Composable
actual fun EfectoPushNativo(paciente: Boolean) {
    if (!FirebaseCfg.activo) {
        Log.w(TAG, "Firebase sin configurar: no hay notificaciones del celular")
        return
    }
    val context = LocalContext.current

    // Se guarda la respuesta del diálogo para poder continuar DESPUÉS de que el
    // usuario conteste, no mientras decide.
    var permisoResuelto by remember { mutableStateOf(false) }
    val pedirPermiso = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { concedido ->
        Log.i(TAG, if (concedido) "Permiso de notificaciones concedido" else "Permiso de notificaciones DENEGADO")
        permisoResuelto = true
    }

    // La clave incluye al usuario: al cambiar de cuenta en el mismo celular, el
    // token debe pasar a la cuenta nueva. Con `Unit` se quedaba en la anterior.
    val userId = Supabase.client.auth.currentUserOrNull()?.id

    LaunchedEffect(userId, permisoResuelto) {
        if (userId == null) return@LaunchedEffect

        // 1) Permiso (solo Android 13+ lo exige en runtime).
        val hacefalta = Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        if (hacefalta && !permisoResuelto) {
            Log.i(TAG, "Pidiendo permiso de notificaciones…")
            pedirPermiso.launch(Manifest.permission.POST_NOTIFICATIONS)
            // Se sale: al contestar, `permisoResuelto` cambia y este efecto
            // vuelve a correr — ahora sí con la respuesta del usuario.
            return@LaunchedEffect
        }

        // 2) Token FCM del dispositivo → registrar para el usuario logueado.
        //
        // Se registra AUNQUE el permiso esté denegado: el token es válido igual
        // y si el usuario lo activa después en Ajustes de Android, los push
        // empiezan a llegar sin tener que volver a entrar a la app.
        val token = obtenerTokenFcm()
        if (token == null) {
            Log.w(TAG, "No se pudo obtener el token FCM: este celular no recibirá notificaciones")
            return@LaunchedEffect
        }
        val ok = if (paciente) PushRepo.registrarTokenPaciente(token) else PushRepo.registrarToken(token)
        Log.i(TAG, if (ok) "Dispositivo registrado para $userId" else "FALLÓ el registro del dispositivo para $userId")

        // 3) ¿Los avisos están apagados a nivel de sistema?
        //
        // Android deja de mostrar el diálogo si ya se negó antes, así que el
        // usuario podía quedarse sin avisos sin haber visto NUNCA una pregunta
        // —y sin nada en la app que se lo dijera—. Esto lo deja visible en
        // Inicio, con el botón para activarlos (2026-08-11).
        EstadoAvisos.apagados = !NotificationManagerCompat.from(context).areNotificationsEnabled()
        if (EstadoAvisos.apagados) Log.w(TAG, "Los avisos del celular están APAGADOS en Ajustes de Android")
    }
}

/**
 * Context de aplicación para abrir los Ajustes desde fuera de Compose.
 * Lo llena SaniaApplication.onCreate, igual que Preferencias/RedMonitor.
 */
object ContextoApp {
    @Volatile var contexto: Context? = null
        private set

    fun init(context: Context) { contexto = context.applicationContext }
}

/** Abre la pantalla de notificaciones de la app en los Ajustes de Android. */
actual fun abrirAjustesDeNotificaciones() {
    val ctx = ContextoApp.contexto ?: return
    val intent = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
        putExtra(Settings.EXTRA_APP_PACKAGE, ctx.packageName)
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    runCatching { ctx.startActivity(intent) }.onFailure {
        // Fabricantes que no tienen esa pantalla: cae a la ficha de la app.
        runCatching {
            ctx.startActivity(
                Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                    .setData(Uri.fromParts("package", ctx.packageName, null))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        }
    }
}

private suspend fun obtenerTokenFcm(): String? = suspendCancellableCoroutine { cont ->
    try {
        FirebaseMessaging.getInstance().token
            .addOnSuccessListener { cont.resume(it) }
            .addOnFailureListener { e ->
                Log.w(TAG, "Firebase no dio el token: ${e.message}")
                cont.resume(null)
            }
    } catch (e: Exception) {
        Log.w(TAG, "Error pidiendo el token FCM: ${e.message}")
        cont.resume(null)
    }
}
