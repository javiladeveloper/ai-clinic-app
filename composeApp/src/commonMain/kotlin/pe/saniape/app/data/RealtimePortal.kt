package pe.saniape.app.data

import io.github.jan.supabase.realtime.PostgresAction
import io.github.jan.supabase.realtime.channel
import io.github.jan.supabase.realtime.postgresChangeFlow
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
                val canal = Supabase.client.channel("portal-vinculos")
                val cambios = canal.postgresChangeFlow<PostgresAction>(schema = "public") {
                    table = "portal_vinculos"
                }
                cambios
                    .onEach { onCambio() }
                    .launchIn(scope)
                canal.subscribe()
            }
            // Si falla, no se relanza: el portal sigue vivo sin auto-refresco.
        }
    }
}
