package pe.saniape.app

import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.status.SessionStatus
import kotlinx.coroutines.launch
import pe.saniape.app.data.Supabase
import pe.saniape.app.ui.IntroMarca
import pe.saniape.app.ui.PantallaLogin
import pe.saniape.app.ui.PortalConTabs
import pe.saniape.app.ui.Sand
import pe.saniape.app.ui.TemaSania

/**
 * Raíz de la app:
 *  1) Intro animada de marca (una vez por arranque).
 *  2) Según sesión de Supabase: Login (Google) o Portal del paciente.
 */
@Composable
fun App() {
    TemaSania {
        var introLista by remember { mutableStateOf(false) }
        var logueado by remember { mutableStateOf<Boolean?>(null) }
        var nombre by remember { mutableStateOf<String?>(null) }
        val scope = rememberCoroutineScope()

        // Observa el estado de sesión (Supabase restaura la sesión guardada al iniciar).
        LaunchedEffect(Unit) {
            Supabase.client.auth.sessionStatus.collect { status ->
                when (status) {
                    is SessionStatus.Authenticated -> {
                        logueado = true
                        // Nombre del usuario (de Google: user_metadata.full_name/name).
                        val u = status.session.user
                        nombre = (u?.userMetadata?.get("full_name")
                            ?: u?.userMetadata?.get("name"))?.toString()?.trim('"')
                            ?: u?.email
                    }
                    is SessionStatus.NotAuthenticated -> logueado = false
                    else -> { /* Loading/RefreshFailure: conserva estado previo */ }
                }
            }
        }

        Surface(color = Sand) {
            when {
                !introLista -> IntroMarca(onFin = { introLista = true })
                logueado == true -> PortalConTabs(
                    nombre = nombre,
                    onCerrarSesion = { scope.launch { Supabase.client.auth.signOut() } },
                )
                logueado == false -> PantallaLogin(onLogueado = { logueado = true })
                else -> { /* esperando estado de sesión tras la intro */ }
            }
        }
    }
}