package pe.saniape.app.ui

import androidx.compose.runtime.Composable

/**
 * Intercepta el botón "Atrás" del sistema. expect/actual: en Android usa BackHandler.
 * Cuando [activo] es true y el usuario pulsa Atrás, se ejecuta [onAtras] en vez de
 * cerrar la app. Da comportamiento de app nativa al back (retroceder paso a paso).
 */
@Composable
expect fun ManejarAtras(activo: Boolean, onAtras: () -> Unit)