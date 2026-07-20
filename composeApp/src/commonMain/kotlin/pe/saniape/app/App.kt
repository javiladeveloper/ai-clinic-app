package pe.saniape.app

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
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

        // Cola offline: vaciar lo que quedó pendiente al abrir la app, y quedar
        // atentos a que vuelva la señal para sincronizar sin que el usuario haga nada.
        LaunchedEffect(Unit) {
            pe.saniape.app.data.offline.Sincronizador.disparar(scope)
            pe.saniape.app.data.offline.RedMonitor.iniciar {
                pe.saniape.app.data.offline.Sincronizador.disparar(scope)
                // Además de reenviar lo pendiente, avisar a las PANTALLAS: las lecturas
                // que fallaron sin señal dejaron listas vacías ("no cargan los pacientes")
                // aunque los datos estén intactos. Con esto se repueblan solas.
                pe.saniape.app.data.offline.EstadoRed.volvioLaRed()
            }
        }

        // Aviso de nueva versión (sugerido): se consulta una vez por arranque, tras la intro.
        // "Más tarde" lo descarta hasta el próximo arranque (no molesta en la misma sesión).
        var urlActualizacion by remember { mutableStateOf<String?>(null) }
        var avisoDescartado by remember { mutableStateOf(false) }
        val acciones = pe.saniape.app.ui.recordarAcciones()
        LaunchedEffect(introLista) {
            if (introLista && urlActualizacion == null) {
                val r = runCatching { pe.saniape.app.data.VersionRepo.chequear() }.getOrNull()
                if (r?.hayActualizacion == true) urlActualizacion = r.urlTienda
            }
        }

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

                        // La PUERTA por la que entró manda: si el login fue por "clínicas"
                        // (usuario+contraseña), respetamos ese modo aunque app_metadata esté
                        // vacío. Muchas cuentas de staff (creadas sin pasar por el callback
                        // de Google) no tienen los flags — sin esto caían al portal de
                        // paciente. La validación real la hace el servidor (RLS + /contexto);
                        // si no es staff, ClinicaConTabs muestra "no es una clínica".
                        val guardado = Preferencias.modoActivo()
                        modo = when {
                            // La PUERTA elegida manda, simétrico para ambos lados: si entró por
                            // clínica → clínica; si entró por Google (paciente) → paciente, aunque
                            // app_metadata no tenga aún el flag tienePortal (p.ej. alta nueva por
                            // Google, o cuenta que también es staff). El servidor valida de verdad.
                            guardado == "clinica" -> "clinica"
                            guardado == "paciente" -> "paciente"
                            tieneClinica -> "clinica"
                            else -> "paciente"
                        }
                        // Coherencia: si la puerta fue de clínica, tratamos que tiene clínica
                        // (el servidor decide de verdad; esto solo evita el rebote al portal).
                        if (guardado == "clinica") tieneClinica = true
                        logueado = true
                    }
                    is SessionStatus.NotAuthenticated -> {
                        // Limpieza CENTRAL al cerrar sesión (cualquier puerta): borra el contexto
                        // cacheado de la clínica anterior + marca/modo, para que la próxima cuenta
                        // no vea datos de la anterior ni caiga al panel equivocado.
                        pe.saniape.app.data.staff.StaffContextoRepo.limpiar()
                        pe.saniape.app.data.staff.DashboardRepo.limpiarCache()
                        Preferencias.setModoActivo(null)
                        Preferencias.setLogoClinica(null); Preferencias.setNombreClinica(null)
                        logueado = false; modo = null
                    }
                    else -> { /* Loading: conserva estado */ }
                }
            }
        }

        fun irA(nuevoModo: String) {
            Preferencias.setModoActivo(nuevoModo)
            modo = nuevoModo
        }

        Surface(color = Sand) {
          androidx.compose.foundation.layout.Box(Modifier.fillMaxSize()) {
            when {
                !introLista -> IntroMarca(onFin = { introLista = true })
                logueado == false -> PantallaLogin(onLogueado = { logueado = true })
                logueado == true && modo == "clinica" -> ClinicaConTabs(
                    puedeIrAPortal = tienePortal,
                    onIrAPortal = { irA("paciente") },
                    // La limpieza de contexto/marca/modo la hace NotAuthenticated (central).
                    onCerrarSesion = { scope.launch { Supabase.client.auth.signOut() } },
                )
                logueado == true && modo == "paciente" -> PortalConTabs(
                    nombre = nombre,
                    puedeIrAClinica = tieneClinica,
                    onIrAClinica = { irA("clinica") },
                    // La limpieza de contexto/marca/modo la hace NotAuthenticated (central).
                    onCerrarSesion = { scope.launch { Supabase.client.auth.signOut() } },
                )
                else -> { /* esperando estado de sesión tras la intro */ }
            }
            // Toast global (creado/guardado/error) sobre cualquier pantalla, salvo la intro.
            if (introLista) pe.saniape.app.ui.ToastHost()

            // Aviso sugerido de nueva versión (solo si hay update y no se descartó aún).
            urlActualizacion?.takeIf { !avisoDescartado }?.let { url ->
                pe.saniape.app.ui.DialogoActualizacion(
                    onActualizar = { acciones.abrirUrl(url); avisoDescartado = true },
                    onMasTarde = { avisoDescartado = true },
                )
            }
          }
        }
    }
}

private fun kotlinx.serialization.json.JsonPrimitive.contentOrNull(): String? =
    if (content == "null") null else content