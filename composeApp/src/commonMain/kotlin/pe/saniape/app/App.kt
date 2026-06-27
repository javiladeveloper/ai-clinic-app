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
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.jsonPrimitive
import pe.saniape.app.data.Preferencias
import pe.saniape.app.data.Supabase
import pe.saniape.app.ui.IntroMarca
import pe.saniape.app.ui.PantallaLogin
import pe.saniape.app.ui.PortalConTabs
import pe.saniape.app.ui.Sand
import pe.saniape.app.ui.TemaSania
import pe.saniape.app.ui.clinica.ClinicaConTabs

/**
 * Raíz de la app:
 *  1) Intro animada de marca (una vez por arranque).
 *  2) Login (Google) si no hay sesión.
 *  3) Según el MODO activo (clínica vs paciente): panel de clínica o portal del paciente.
 *     Una cuenta puede ser staff Y paciente; alterna con botones en "Más".
 */
@Composable
fun App() {
    TemaSania {
        var introLista by remember { mutableStateOf(false) }
        var logueado by remember { mutableStateOf<Boolean?>(null) }
        var nombre by remember { mutableStateOf<String?>(null) }
        // Roles del usuario (de app_metadata).
        var tieneClinica by remember { mutableStateOf(false) }
        var tienePortal by remember { mutableStateOf(false) }
        // Modo activo: "clinica" | "paciente". Se decide al loguear.
        var modo by remember { mutableStateOf<String?>(null) }
        val scope = rememberCoroutineScope()

        LaunchedEffect(Unit) {
            Supabase.client.auth.sessionStatus.collect { status ->
                when (status) {
                    is SessionStatus.Authenticated -> {
                        val u = status.session.user
                        nombre = (u?.userMetadata?.get("full_name")
                            ?: u?.userMetadata?.get("name"))?.toString()?.trim('"')
                            ?: u?.email
                        // Roles desde app_metadata (con fallback al claim legacy 'tipo').
                        val meta = u?.appMetadata
                        val tipo = meta?.get("tipo")?.jsonPrimitive?.contentOrNull()
                        val tc = meta?.get("tieneClinica")?.jsonPrimitive?.booleanOrNull
                        val tp = meta?.get("tienePortal")?.jsonPrimitive?.booleanOrNull
                        tieneClinica = tc ?: (tipo != null && tipo != "paciente")
                        tienePortal = tp ?: (tipo == "paciente")
                        // Modo: el guardado si es válido para sus roles; si no, por defecto.
                        val guardado = Preferencias.modoActivo()
                        modo = when {
                            guardado == "clinica" && tieneClinica -> "clinica"
                            guardado == "paciente" && tienePortal -> "paciente"
                            tieneClinica -> "clinica"
                            else -> "paciente"
                        }
                        logueado = true
                    }
                    is SessionStatus.NotAuthenticated -> { logueado = false; modo = null }
                    else -> { /* Loading: conserva estado */ }
                }
            }
        }

        fun irA(nuevoModo: String) {
            Preferencias.setModoActivo(nuevoModo)
            modo = nuevoModo
        }

        Surface(color = Sand) {
            when {
                !introLista -> IntroMarca(onFin = { introLista = true })
                logueado == false -> PantallaLogin(onLogueado = { logueado = true })
                logueado == true && modo == "clinica" -> ClinicaConTabs(
                    puedeIrAPortal = tienePortal,
                    onIrAPortal = { irA("paciente") },
                    onCerrarSesion = { scope.launch { Supabase.client.auth.signOut() } },
                )
                logueado == true && modo == "paciente" -> PortalConTabs(
                    nombre = nombre,
                    puedeIrAClinica = tieneClinica,
                    onIrAClinica = { irA("clinica") },
                    onCerrarSesion = { scope.launch { Supabase.client.auth.signOut() } },
                )
                else -> { /* esperando estado de sesión tras la intro */ }
            }
        }
    }
}

private fun kotlinx.serialization.json.JsonPrimitive.contentOrNull(): String? =
    if (content == "null") null else content