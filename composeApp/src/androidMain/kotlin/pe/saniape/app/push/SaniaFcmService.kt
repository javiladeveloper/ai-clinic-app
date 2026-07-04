package pe.saniape.app.push

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import pe.saniape.app.MainActivity
import pe.saniape.app.R

/**
 * Recibe los push FCM (enviados por el dashboard vía lib/fcm.ts) y los muestra en la BARRA
 * del celular — con la app cerrada o abierta. Tocar la notificación abre la app.
 */
class SaniaFcmService : FirebaseMessagingService() {

    override fun onMessageReceived(msg: RemoteMessage) {
        val titulo = msg.notification?.title ?: msg.data["titulo"] ?: "Sania"
        val cuerpo = msg.notification?.body ?: msg.data["cuerpo"] ?: ""
        mostrar(titulo, cuerpo)
    }

    override fun onNewToken(token: String) {
        // El token rotó: se re-registra en el próximo arranque con sesión
        // (EfectoPushNativo registra el token vigente al entrar al panel).
    }

    private fun mostrar(titulo: String, cuerpo: String) {
        val nm = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            nm.createNotificationChannel(
                NotificationChannel(CANAL, "Notificaciones de la clínica", NotificationManager.IMPORTANCE_HIGH)
            )
        }
        val abrir = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java).apply { flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP },
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
            .build()
        nm.notify(System.currentTimeMillis().toInt(), notif)
    }

    private companion object { const val CANAL = "sania_general" }
}
