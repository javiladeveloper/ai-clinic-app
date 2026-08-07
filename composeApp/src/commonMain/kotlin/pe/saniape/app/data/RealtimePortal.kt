package pe.saniape.app.data

import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.realtime.PostgresAction
import io.github.jan.supabase.realtime.channel
import io.github.jan.supabase.realtime.postgresChangeFlow
import io.github.jan.supabase.realtime.realtime
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch

/**
 * El portal del paciente se entera solo de que su clínica lo confirmó.
 *
 * Antes, cuando recepción tocaba "Es mi paciente", la app seguía diciendo
 * "Pendiente de confirmación" hasta cerrar sesión y volver a entrar. Es el peor
 * momento posible para no reaccionar: la clínica le dice "listo, ya está" y el
 * paciente no ve ningún cambio.
 *
 * Escucha `portal_vinculos` en vez de `citas`: la RLS de citas es de staff, así
 * que al paciente no le llegaría nada por ese canal. La política de vínculos
 * filtra por auth.uid(), de modo que solo recibe el suyo.
 *
 * Complementario, nunca crítico: si Realtime no conecta (sin señal, servidor
 * caído), la app funciona igual — solo no se refresca sola.
 */
object RealtimePortal {

    /**
     * Suscribe a los cambios de vínculo del paciente y llama a [onCambio] en cada
     * uno. Devuelve un Job cancelable para cortar al salir de la pantalla.
     */
    fun suscribir(scope: CoroutineScope, onCambio: () -> Unit): Job {
        return scope.launch {
            runCatching {
                // El canal respeta la RLS, así que necesita el token del paciente.
                // Suscribirse antes de que la sesión esté lista deja el canal
                // conectado pero sin recibir NADA — silencioso y difícil de ver.
                val sesion = Supabase.client.auth.currentSessionOrNull()
                    ?: run {
                        // Espera corta a que Auth termine de restaurar la sesión.
                        var intentos = 0
                        var s: io.github.jan.supabase.auth.user.UserSession? = null
                        while (s == null && intentos < 10) {
                            kotlinx.coroutines.delay(500)
                            s = Supabase.client.auth.currentSessionOrNull()
                            intentos++
                        }
                        s
                    } ?: return@runCatching
                val canal = Supabase.client.channel("portal-paciente")
                // Vínculos: la clínica confirmó su cuenta.
                canal.postgresChangeFlow<PostgresAction>(schema = "public") {
                    table = "portal_vinculos"
                }.onEach { onCambio() }.launchIn(scope)
                // Pagos: si le cobraron en recepción, su deuda baja SOLA. Sin
                // esto la app seguía mostrando el saldo viejo y el paciente
                // podía intentar pagar algo que ya pagó.
                canal.postgresChangeFlow<PostgresAction>(schema = "public") {
                    table = "pagos_tratamiento"
                }.onEach { onCambio() }.launchIn(scope)
                canal.subscribe()
            }
            // Si falla, no se relanza: el portal sigue vivo sin auto-refresco.
        }
    }
}
