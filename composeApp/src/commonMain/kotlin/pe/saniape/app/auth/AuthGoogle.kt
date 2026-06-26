package pe.saniape.app.auth

import androidx.compose.runtime.Composable

/**
 * Lanzador del login con Google NATIVO (Credential Manager en Android).
 *
 * Es `expect`: cada plataforma provee su `actual`. En Android abre el selector
 * de cuenta nativo (sin navegador) y entrega el idToken a Supabase.
 */
expect class GoogleAuthLauncher {
    /** Lanza el selector de Google. Llama [onResultado] con éxito/fallo. */
    fun lanzar(onResultado: (exito: Boolean, error: String?) -> Unit)
}

/** Crea el lanzador desde un Composable (necesita contexto de plataforma). */
@Composable
expect fun recordarGoogleAuthLauncher(): GoogleAuthLauncher