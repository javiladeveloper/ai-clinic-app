package pe.saniape.app

import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.status.SessionStatus
import kotlinx.coroutines.launch
import pe.saniape.app.data.Supabase
import pe.saniape.app.ui.PantallaLogin
import pe.saniape.app.ui.PantallaPortal
import pe.saniape.app.ui.Sand
import pe.saniape.app.ui.TemaSania
import androidx.compose.runtime.rememberCoroutineScope

/**
 * Raíz de la app. Decide pantalla según la sesión de Supabase:
 *  - sin sesión → Login (Google)
 *  - con sesión → Portal del paciente
 */
@Composable
fun App() {
    TemaSania {
        var logueado by remember { mutableStateOf<Boolean?>(null) }
        val scope = rememberCoroutineScope()

        // Observa el estado de sesión (Supabase restaura la sesión guardada al iniciar).
        LaunchedEffect(Unit) {
            Supabase.client.auth.sessionStatus.collect { status ->
                logueado = when (status) {
                    is SessionStatus.Authenticated -> true
                    is SessionStatus.NotAuthenticated -> false
                    else -> logueado // Loading/RefreshFailure: conserva estado previo
                }
            }
        }

        Surface(color = Sand) {
            when (logueado) {
                true -> PantallaPortal(
                    onCerrarSesion = {
                        scope.launch { Supabase.client.auth.signOut() }
                    },
                )
                false -> PantallaLogin(onLogueado = { logueado = true })
                null -> { /* arranque: el splash del sistema cubre este instante */ }
            }
        }
    }
}