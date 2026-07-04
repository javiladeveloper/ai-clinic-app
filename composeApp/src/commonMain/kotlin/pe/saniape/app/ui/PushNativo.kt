package pe.saniape.app.ui

import androidx.compose.runtime.Composable

/**
 * Efecto que activa las notificaciones REALES del celular (FCM) para el usuario logueado:
 * pide el permiso de notificaciones (Android 13+), obtiene el token FCM del dispositivo y
 * lo registra en dispositivos_push. No-op si Firebase no está configurado (FirebaseCfg vacío).
 * Montarlo cuando hay sesión de staff (panel de clínica).
 */
@Composable
expect fun EfectoPushNativo()
