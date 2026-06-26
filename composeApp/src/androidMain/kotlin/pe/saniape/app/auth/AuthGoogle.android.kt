package pe.saniape.app.auth

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.credentials.CredentialManager
import androidx.credentials.GetCredentialRequest
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.providers.Google
import io.github.jan.supabase.auth.providers.builtin.IDToken
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import pe.saniape.app.data.Supabase

/**
 * Login Google NATIVO en Android (Credential Manager + idToken → Supabase).
 *
 * REQUIERE: un OAuth Web Client ID de Google Cloud (el "Web client", no el de
 * Android) en [WEB_CLIENT_ID]. Es el mismo que ya usa Supabase para validar el
 * token. Sin él, el login falla con "audience mismatch".
 */
actual class GoogleAuthLauncher(
    private val context: Context,
    private val scope: CoroutineScope,
) {
    actual fun lanzar(onResultado: (exito: Boolean, error: String?) -> Unit) {
        scope.launch {
            try {
                val opcion = GetGoogleIdOption.Builder()
                    .setFilterByAuthorizedAccounts(false)
                    .setServerClientId(WEB_CLIENT_ID)
                    .setAutoSelectEnabled(false)
                    .build()

                val request = GetCredentialRequest.Builder()
                    .addCredentialOption(opcion)
                    .build()

                val cm = CredentialManager.create(context)
                val respuesta = cm.getCredential(context, request)
                val cred = respuesta.credential
                val googleCred = GoogleIdTokenCredential.createFrom(cred.data)
                val idToken = googleCred.idToken

                withContext(Dispatchers.Default) {
                    Supabase.client.auth.signInWith(IDToken) {
                        this.idToken = idToken
                        provider = Google
                    }
                }
                onResultado(true, null)
            } catch (e: Exception) {
                onResultado(false, e.message ?: "No se pudo iniciar sesión con Google")
            }
        }
    }

    companion object {
        // "Web client" OAuth Client ID de Google Cloud (el mismo que usa Supabase
        // en Auth > Google). NO es el de tipo Android.
        const val WEB_CLIENT_ID = "942581341329-7qfpdj6fsol2bkkhj2movknju4tets1q.apps.googleusercontent.com"
    }
}

@Composable
actual fun recordarGoogleAuthLauncher(): GoogleAuthLauncher {
    val context = LocalContext.current
    val scope = rememberAppScope()
    return GoogleAuthLauncher(context, scope)
}