package pe.saniape.app.ui

import androidx.compose.runtime.Composable

/**
 * iOS no tiene botón "Atrás" físico ni del sistema (se navega con el gesto de
 * deslizar desde el borde, que gestiona cada pantalla). No hay nada que interceptar:
 * no-op. La navegación interna de la app la maneja el estado de Compose igual que en Android.
 */
@Composable
actual fun ManejarAtras(activo: Boolean, onAtras: () -> Unit) {
    // Sin equivalente en iOS: la navegación es por gesto, no por un botón global.
}
