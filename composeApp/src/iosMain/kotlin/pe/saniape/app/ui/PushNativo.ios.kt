package pe.saniape.app.ui

import androidx.compose.runtime.Composable

/**
 * iOS: notificaciones push reales.
 *
 * TODO(iOS Fase 2): iOS usa APNs (no FCM directo). Opciones:
 *   a) Firebase Cloud Messaging con su SDK de iOS (reusa el backend de push actual), o
 *   b) APNs nativo con UNUserNotificationCenter + registro de deviceToken.
 * Pedir permiso con UNUserNotificationCenter.requestAuthorization, obtener el token de
 * APNs/FCM y registrarlo en dispositivos_push (como hace Android). Requiere capability
 * Push Notifications en el target de Xcode y un certificado/clave APNs en Apple Developer.
 *
 * Por ahora (Fase 1) es un no-op: la app funciona sin push en iOS.
 */
@Composable
actual fun EfectoPushNativo() {
    // TODO(iOS Fase 2): registrar APNs/FCM y guardar el token en dispositivos_push.
}
