package pe.saniape.app.auth

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember

/**
 * iOS: login con Google.
 *
 * TODO(iOS Fase 2): implementar con el SDK GoogleSignIn de iOS (GIDSignIn) para obtener el
 * idToken y pasarlo a Supabase con signInWith(IDToken) — igual que Android pero con el
 * flujo nativo de iOS. Requiere:
 *   - Añadir GoogleSignIn vía Swift Package en el iosApp.
 *   - Un OAuth Client ID de tipo iOS en Google Cloud + el REVERSED_CLIENT_ID como URL Scheme.
 *   - GIDClientID en el Info.plist.
 * El staff (usuario+contraseña) SÍ funciona en iOS desde ya (AuthStaff usa Supabase Auth,
 * que es multiplataforma). Solo el botón de Google del paciente queda pendiente.
 *
 * Por ahora devuelve un error claro si se pulsa "Continuar con Google" en iOS.
 */
actual class GoogleAuthLauncher {
    actual fun lanzar(onResultado: (exito: Boolean, error: String?) -> Unit) {
        onResultado(false, "El acceso con Google en iOS estará disponible pronto. Mientras tanto, ingresa como clínica con tu usuario y contraseña.")
    }
}

@Composable
actual fun recordarGoogleAuthLauncher(): GoogleAuthLauncher = remember { GoogleAuthLauncher() }
