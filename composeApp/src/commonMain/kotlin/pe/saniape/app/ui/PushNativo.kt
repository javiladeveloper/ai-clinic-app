package pe.saniape.app.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * Efecto que activa las notificaciones REALES del celular (FCM) para el usuario logueado:
 * pide el permiso de notificaciones (Android 13+), obtiene el token FCM del dispositivo y
 * lo registra en dispositivos_push. No-op si Firebase no está configurado (FirebaseCfg vacío).
 * Montarlo cuando hay sesión de staff (panel de clínica).
 */
@Composable
expect fun EfectoPushNativo(paciente: Boolean = false)

/**
 * Si los avisos del celular están APAGADOS a nivel de sistema.
 *
 * Android deja de mostrar el diálogo de permiso cuando ya se negó (o cuando el
 * usuario lo apagó luego en Ajustes), y la app se quedaba muda sin decir nada:
 * el token se registraba igual, el servidor enviaba, y el aviso moría en el
 * sistema. Desde fuera parecía "las notificaciones no funcionan" sin ninguna
 * pista de por qué (reporte del dueño 2026-08-11).
 *
 * `EfectoPushNativo` lo pone al montar; la pantalla de Inicio lo lee para
 * mostrar el aviso con el botón que abre los ajustes del sistema.
 */
object EstadoAvisos {
    var apagados by mutableStateOf(false)
        internal set
}

/** Abre los ajustes de notificaciones de la app (Android). No-op en iOS. */
expect fun abrirAjustesDeNotificaciones()
