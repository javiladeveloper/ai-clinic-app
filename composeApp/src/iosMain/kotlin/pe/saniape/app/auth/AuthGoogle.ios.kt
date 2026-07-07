package pe.saniape.app.auth

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.providers.Google
import io.github.jan.supabase.auth.providers.builtin.IDToken
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import pe.saniape.app.data.Supabase

/**
 * Login con Google en iOS mediante un PUENTE hacia Swift.
 *
 * El SDK GoogleSignIn (GIDSignIn) es dependencia del proyecto Xcode; el flujo nativo lo
 * ejecuta Swift y aquí recibimos el `idToken` + el `nonce` CRUDO. El idToken se intercambia
 * con Supabase (signInWith(IDToken)) igual que en Android.
 *
 * NONCE: GoTrue valida `sha256(nonce_enviado) == nonce_del_token`. Por eso Swift genera un
 * nonce crudo, manda su SHA-256 a Google (que lo devuelve en el token) y nos pasa el crudo
 * para Supabase. Así ambos lados cuadran.
 */
object GoogleSignInPuente {
    /**
     * Swift inyecta aquí el flujo GIDSignIn. Debe llamar al callback con
     * (idToken, nonceCrudo, error): idToken+nonce no nulos si el login fue OK; error si falló.
     */
    var proveedorIdToken: ((callback: (idToken: String?, nonce: String?, error: String?) -> Unit) -> Unit)? = null
}

actual class GoogleAuthLauncher(
    private val scope: CoroutineScope,
) {
    actual fun lanzar(onResultado: (exito: Boolean, error: String?) -> Unit) {
        val proveedor = GoogleSignInPuente.proveedorIdToken
        if (proveedor == null) {
            onResultado(
                false,
                "El acceso con Google en iOS estará disponible pronto. Mientras tanto, ingresa como clínica con tu usuario y contraseña.",
            )
            return
        }
        proveedor { idToken, nonce, error ->
            if (idToken.isNullOrBlank()) {
                onResultado(false, error ?: "No se pudo iniciar sesión con Google.")
                return@proveedor
            }
            scope.launch {
                try {
                    withContext(Dispatchers.Default) {
                        Supabase.client.auth.signInWith(IDToken) {
                            this.idToken = idToken
                            provider = Google
                            this.nonce = nonce   // nonce CRUDO; GoTrue lo hashea y compara
                        }
                    }
                    onResultado(true, null)
                } catch (e: Exception) {
                    onResultado(false, e.message ?: "No se pudo iniciar sesión con Google")
                }
            }
        }
    }
}

@Composable
actual fun recordarGoogleAuthLauncher(): GoogleAuthLauncher {
    val scope = rememberAppScope()
    return remember { GoogleAuthLauncher(scope) }
}
