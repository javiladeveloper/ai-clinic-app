package pe.saniape.app.auth

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import io.github.jan.supabase.auth.handleDeeplinks
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import platform.Foundation.NSURL
import pe.saniape.app.data.Supabase

/**
 * Login con Google en iOS DENTRO de la app, con ASWebAuthenticationSession (la hoja de
 * autenticación del sistema), NO el SDK GoogleSignIn ni el Safari externo.
 *
 * POR QUÉ ASÍ (y no el SDK GoogleSignIn): el SDK arrastra todo Firebase/AppCheck/AppAuth/
 * GTMSessionFetcher — cientos de archivos que Xcode compila desde cero (~40 min por build).
 * ASWebAuthenticationSession es un framework del SISTEMA (cero descarga, cero compilación),
 * y Apple lo EXIGE (Guideline 4) en vez de abrir el navegador externo. Mismo enfoque que
 * usa FitCore, que por esto compila en ~10 min. El login de Google sigue funcionando igual.
 *
 * Flujo: construimos la URL de autorización de GoTrue (flujo implícito); Swift la abre en
 * ASWebAuthenticationSession con callbackScheme "saniape"; al volver, el callback trae los
 * tokens en el fragment y los completa handleDeeplinks (el mismo handler del deep link).
 */
object GoogleWebAuthPuente {
    /**
     * Swift inyecta el arranque de ASWebAuthenticationSession:
     * (urlAutorizacion, callbackScheme, onResult(callbackUrl, error)).
     */
    var iniciar: ((authUrl: String, callbackScheme: String, onResult: (callbackUrl: String?, error: String?) -> Unit) -> Unit)? = null
}

actual class GoogleAuthLauncher(
    private val scope: CoroutineScope,
) {
    actual fun lanzar(onResultado: (exito: Boolean, error: String?) -> Unit) {
        val iniciar = GoogleWebAuthPuente.iniciar
        if (iniciar == null) {
            onResultado(false, "El acceso con Google no está disponible. Ingresa con tu usuario y contraseña.")
            return
        }
        // URL de autorización OAuth de Supabase (flujo implícito): vuelve a saniape://login
        // con los tokens en el fragment.
        val authUrl = "${Supabase.URL}/auth/v1/authorize?provider=google&redirect_to=saniape://login"
        iniciar(authUrl, "saniape") { callbackUrl, error ->
            when {
                error != null -> onResultado(false, error)
                callbackUrl != null -> {
                    val url = NSURL(string = callbackUrl)
                    val fragmento = url?.fragment.orEmpty()
                    if (url != null && "access_token=" in fragmento) {
                        // Completa la sesión con los tokens del callback (mismo handler del deep link).
                        // handleDeeplinks es extensión de SupabaseClient (NO de .auth) y solo acepta
                        // saniape://login (scheme/host en configurarAuthPlataforma). El éxito se
                        // avisa cuando la sesión YA quedó importada, no antes.
                        Supabase.client.handleDeeplinks(url) {
                            scope.launch { onResultado(true, null) }
                        }
                    } else {
                        // Google/Supabase devuelven el motivo en el fragment (#error_description=…).
                        val motivo = fragmento.split("&")
                            .firstOrNull { it.startsWith("error_description=") }
                            ?.substringAfter("=")
                            ?.replace("+", " ")
                        onResultado(false, motivo ?: "No se pudo completar el inicio de sesión con Google.")
                    }
                }
                // callbackUrl y error null = el usuario canceló la hoja: sin acción de error.
                else -> onResultado(false, null)
            }
        }
    }
}

@Composable
actual fun recordarGoogleAuthLauncher(): GoogleAuthLauncher {
    val scope = rememberAppScope()
    return remember { GoogleAuthLauncher(scope) }
}
