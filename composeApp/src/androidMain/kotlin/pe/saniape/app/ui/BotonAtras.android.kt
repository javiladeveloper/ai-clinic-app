package pe.saniape.app.ui

import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable

/** Android: usa BackHandler para interceptar el botón Atrás del sistema. */
@Composable
actual fun ManejarAtras(activo: Boolean, onAtras: () -> Unit) {
    BackHandler(enabled = activo, onBack = onAtras)
}