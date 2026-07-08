package pe.saniape.app.auth

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.providers.Apple
import io.github.jan.supabase.auth.providers.builtin.IDToken
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import pe.saniape.app.data.Supabase

/**
 * iOS: Sign in with Apple mediante un PUENTE hacia Swift (framework AuthenticationServices,
 * nativo del sistema — sin SDK externo). Swift ejecuta ASAuthorizationController y aquí
 * recibimos el `idToken` + el `nonce` CRUDO, que intercambiamos con Supabase (provider Apple).
 *
 * NONCE: igual que Google, GoTrue valida sha256(nonce_enviado) == nonce_del_token. Swift genera
 * el nonce crudo, manda su SHA-256 en la request de Apple y nos pasa el crudo para Supabase.
 *
 * CONFIG externa (una vez):
 *   - Xcode: capability "Sign in with Apple" (entitlement) — ya en el proyecto.
 *   - Developer portal: App ID pe.saniape.app con "Sign in with Apple" habilitado + regenerar perfil.
 *   - Supabase: Authentication > Providers > Apple habilitado, con pe.saniape.app como client id.
 */
object AppleSignInPuente {
    /**
     * Swift inyecta aquí el flujo ASAuthorization. Debe llamar al callback con
     * (idToken, nonceCrudo, error): idToken+nonce no nulos si el login fue OK; error si falló/canceló.
     */
    var proveedor: ((callback: (idToken: String?, nonce: String?, error: String?) -> Unit) -> Unit)? = null
}

actual class AppleAuthLauncher(
    private val scope: CoroutineScope,
) {
    actual fun lanzar(onResultado: (exito: Boolean, error: String?) -> Unit) {
        val proveedor = AppleSignInPuente.proveedor
        if (proveedor == null) {
            onResultado(false, "El acceso con Apple no está disponible.")
            return
        }
        proveedor { idToken, nonce, error ->
            if (idToken.isNullOrBlank()) {
                onResultado(false, error ?: "No se pudo iniciar sesión con Apple.")
                return@proveedor
            }
            scope.launch {
                try {
                    withContext(Dispatchers.Default) {
                        Supabase.client.auth.signInWith(IDToken) {
                            this.idToken = idToken
                            provider = Apple
                            this.nonce = nonce
                        }
                    }
                    onResultado(true, null)
                } catch (e: Exception) {
                    onResultado(false, e.message ?: "No se pudo iniciar sesión con Apple")
                }
            }
        }
    }
}

@Composable
actual fun recordarAppleAuthLauncher(): AppleAuthLauncher? {
    val scope = rememberAppScope()
    return remember { AppleAuthLauncher(scope) }
}
