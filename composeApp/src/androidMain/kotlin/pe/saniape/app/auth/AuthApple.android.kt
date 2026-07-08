package pe.saniape.app.auth

import androidx.compose.runtime.Composable

/**
 * Android: no hay Sign in with Apple. El launcher es null → PantallaLogin no muestra el
 * botón de Apple (los pacientes en Android usan Google, que es lo que aplica en esa tienda).
 */
actual class AppleAuthLauncher {
    actual fun lanzar(onResultado: (exito: Boolean, error: String?) -> Unit) {
        onResultado(false, "Sign in with Apple no está disponible en Android.")
    }
}

@Composable
actual fun recordarAppleAuthLauncher(): AppleAuthLauncher? = null
