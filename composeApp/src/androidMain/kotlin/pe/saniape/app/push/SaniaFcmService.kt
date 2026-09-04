package pe.saniape.app.push

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.ContentResolver
import android.content.Intent
import android.media.AudioAttributes
import android.net.Uri
import android.os.Build
import androidx.core.app.NotificationCompat
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import io.github.jan.supabase.auth.auth
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import pe.saniape.app.MainActivity
import pe.saniape.app.data.Preferencias
import pe.saniape.app.data.Supabase
import pe.saniape.app.data.staff.PushRepo
import pe.saniape.app.R

/**
 * Recibe los push FCM (enviados por el dashboard vía lib/fcm.ts) y los muestra en la BARRA
 * del celular — con la app cerrada o abierta. Tocar la notificación abre la app.
 */
class SaniaFcmService : FirebaseMessagingService() {

    override fun onMessageReceived(msg: RemoteMessage) {
        val titulo = msg.notification?.title ?: msg.data["titulo"] ?: "Sania"
        val cuerpo = msg.notification?.body ?: msg.data["cuerpo"] ?: ""
        // Qué abrir al tocar: el recordatorio de cita lleva a ESA cita, con el
        // botón Confirmar a la vista. Un aviso que solo abre la app deja al
        // paciente buscando qué hacer.
        mostrar(titulo, cuerpo, msg.data["citaId"])
    }

    override fun onNewToken(token: String) {
        // El token rotó (reinstalación, restauración del celular, limpieza de
        // datos de Play Services). Antes solo se re-registraba en el PRÓXIMO
        // arranque con sesión: un celular que no abría la app quedaba mudo
        // hasta entonces (2026-09-04). Ahora se registra acá mismo si hay
        // sesión; sin sesión, EfectoPushNativo lo hace al entrar.
        alcance.launch {
            runCatching {
                Supabase.client.auth.awaitInitialization()
                if (Supabase.client.auth.currentSessionOrNull() == null) return@launch
                val esPaciente = Preferencias.modoActivo() == "paciente"
                // El RPC del staff falla para una cuenta sin perfil (paciente):
                // se cae al de paciente, que registra por auth_user_id.
                val ok = if (esPaciente) PushRepo.registrarTokenPaciente(token)
                else PushRepo.registrarToken(token) || PushRepo.registrarTokenPaciente(token)
                android.util.Log.i("SaniaPush", if (ok) "Token FCM rotado y re-registrado" else "Token FCM rotado: no se pudo re-registrar")
            }
        }
    }

    private val alcance = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onDestroy() {
        alcance.cancel()
        super.onDestroy()
    }

    private fun mostrar(titulo: String, cuerpo: String, citaId: String? = null) {
        val nm = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        crearCanal(this)
        val abrir = PendingIntent.getActivity(
            this, citaId?.hashCode() ?: 0,
            Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                if (citaId != null) putExtra(EXTRA_CITA, citaId)
            },
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val notif = NotificationCompat.Builder(this, CANAL)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(titulo)
            .setContentText(cuerpo)
            .setStyle(NotificationCompat.BigTextStyle().bigText(cuerpo))
            .setAutoCancel(true)
            .setContentIntent(abrir)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            // Sonido y vibración explícitos. En Android 8+ manda el canal, pero
            // esto cubre los celulares viejos y sirve de respaldo si el canal
            // quedó creado sin sonido: llegaba muda y no había forma de saberlo.
            .setSound(Uri.parse("${ContentResolver.SCHEME_ANDROID_RESOURCE}://$packageName/${R.raw.sania_notif}"))
            .setVibrate(longArrayOf(0, 400, 200, 400))
            .setDefaults(NotificationCompat.DEFAULT_LIGHTS)
            .build()
        nm.notify(System.currentTimeMillis().toInt(), notif)
    }

    // "_v3": el sonido y la vibración de un canal SOLO se fijan al CREARLO —
    // después son del usuario y la app no puede tocarlos. En los celulares donde
    // ya existía "_v2" se había creado sin sonido (lo creaba mostrar(), que solo
    // corría cuando el aviso ya venía mudo), así que las notificaciones llegaban
    // SIN SONIDO y no había forma de arreglarlo salvo con un id nuevo
    // (reportado por el dueño 2026-08-11: "la notificación sí llega, sin sonido").
    //
    // Subir a _v4… si el sonido vuelve a cambiar. Ojo: cada cambio de id deja el
    // canal anterior visible en los Ajustes del celular; por eso no se hace a la
    // ligera, solo cuando de verdad hay que redefinir sonido o importancia.
    companion object {
        const val CANAL = "sania_general_v3"
        /** Id de la cita que abrió la notificación: MainActivity lo lee para llevar al paciente a ella. */
        const val EXTRA_CITA = "cita_id"

        /**
         * Crea el canal de avisos. Se llama al ARRANCAR la app, no solo al recibir
         * un mensaje.
         *
         * Por qué importa: el servidor manda el push con `notification` +
         * `channel_id`, y en ese caso —con la app cerrada o en segundo plano—
         * Android lo muestra ÉL MISMO, sin pasar por onMessageReceived. Si el
         * canal no existe todavía en el dispositivo, DESCARTA el aviso en
         * silencio.
         *
         * Como el canal solo se creaba dentro de mostrar(), pasaba justo lo peor:
         * con la app abierta llegaba (por onMessageReceived), y con el celular en
         * el bolsillo —el caso que de verdad importa— no llegaba nunca. Encaja
         * con lo reportado: permiso encendido y aun así sin avisos (2026-08-11).
         */
        fun crearCanal(context: android.content.Context) {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
            val nm = context.getSystemService(NotificationManager::class.java) ?: return
            // Se limpian los canales de versiones anteriores para que no queden
            // sueltos en los Ajustes del celular.
            //
            // OJO para quien venga a cambiar el sonido: borrar un canal NO olvida
            // su configuración. Si se recrea con el MISMO id, Android le devuelve
            // los ajustes que tenía —incluido "sin sonido"—, así que borrar y
            // recrear NO sirve para redefinirlo. La única vía es un id que ese
            // celular no haya visto nunca (por eso la serie _v2, _v3…).
            runCatching { nm.deleteNotificationChannel("sania_general") }
            runCatching { nm.deleteNotificationChannel("sania_general_v2") }
            if (nm.getNotificationChannel(CANAL) != null) return // ya existe: no se toca
            // Sonido propio de Sania (mismo acorde que la intro de marca): el fisio
            // reconoce que la notificación es de Sania sin mirar la pantalla.
            // OJO: el sonido de un canal SOLO se aplica al CREARLO.
            val canal = NotificationChannel(CANAL, "Notificaciones de la clínica", NotificationManager.IMPORTANCE_HIGH)
            val sonido = Uri.parse("${ContentResolver.SCHEME_ANDROID_RESOURCE}://${context.packageName}/${R.raw.sania_notif}")
            val attrs = AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_NOTIFICATION)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build()
            canal.setSound(sonido, attrs)
            canal.enableVibration(true)
            nm.createNotificationChannel(canal)
        }
    }
}
