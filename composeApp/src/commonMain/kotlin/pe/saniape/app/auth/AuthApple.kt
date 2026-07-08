package pe.saniape.app.auth

import androidx.compose.runtime.Composable

/**
 * Lanzador de "Iniciar sesión con Apple" (Sign in with Apple).
 *
 * Solo aplica en iOS: la App Store exige ofrecer Sign in with Apple cuando la app
 * también ofrece login social (Google). En Android no existe → el launcher es null y
 * la pantalla de login no muestra el botón.
 */
expect class AppleAuthLauncher {
    /** Lanza la hoja nativa de Apple. Llama [onResultado] con éxito/fallo. */
    fun lanzar(onResultado: (exito: Boolean, error: String?) -> Unit)
}

/** Crea el lanzador, o null si la plataforma no soporta Sign in with Apple (Android). */
@Composable
expect fun recordarAppleAuthLauncher(): AppleAuthLauncher?
