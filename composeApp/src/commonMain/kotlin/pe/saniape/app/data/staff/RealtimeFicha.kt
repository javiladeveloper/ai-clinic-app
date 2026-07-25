package pe.saniape.app.data.staff

import io.github.jan.supabase.realtime.PostgresAction
import io.github.jan.supabase.realtime.channel
import io.github.jan.supabase.realtime.postgresChangeFlow
import io.github.jan.supabase.realtime.realtime
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import pe.saniape.app.data.Supabase

/**
 * Realtime de la FICHA del paciente: escucha cambios en `citas`, `tratamientos` y
 * `sesiones` y avisa para refrescar la ficha SIN que el usuario recargue. Efecto: si el
 * fisio crea/edita/completa citas, evaluaciones (una evaluación es una cita de
 * `tipo = "Evaluación"`), tratamientos o sesiones desde la web u otro dispositivo, la
 * ficha ABIERTA se actualiza sola — antes el cambio solo se veía saliendo y volviendo a
 * entrar.
 *
 * Mismo diseño defensivo que [RealtimeAgenda]: es COMPLEMENTARIO. Si Realtime no conecta
 * (sin señal, servidor caído, etc.) la ficha sigue funcionando igual — solo no se
 * auto-refresca. Nunca la rompe.
 *
 * RLS: el canal recibe solo las filas que el usuario puede ver (mismo get_clinica_id()
 * que las lecturas). El fisio con scope solo se entera de lo de SUS pacientes. No se
 * filtra por paciente en el servidor (las filas se vinculan por `tratamiento_id`/
 * `paciente_id`, no siempre por el paciente abierto): un cambio dispara una recarga suave
 * de la ficha abierta, que es barata (mantiene el contenido y solo muestra "Actualizando…").
 */
object RealtimeFicha {

    // `citas` cubre también las EVALUACIONES (son citas de tipo "Evaluación") y los hitos
    // (próxima cita / última atención) que muestra la ficha.
    private val TABLAS = listOf("citas", "tratamientos", "sesiones")

    /**
     * Suscribe a cambios de `citas`/`tratamientos`/`sesiones` y llama a [onCambio] en cada
     * INSERT/UPDATE/DELETE. Devuelve un Job cancelable (para cortar la suscripción al
     * cerrar la ficha). Best-effort: cualquier fallo se traga y no rompe nada.
     */
    fun suscribir(scope: CoroutineScope, onCambio: () -> Unit): Job {
        return scope.launch {
            runCatching {
                val canal = Supabase.client.channel("ficha-paciente")
                // launchIn(this) → los flujos son hijos de ESTE Job, así job.cancel() los corta.
                TABLAS.forEach { t ->
                    canal.postgresChangeFlow<PostgresAction>(schema = "public") { table = t }
                        .onEach { onCambio() }
                        .launchIn(this)
                }
                canal.subscribe()
            }
            // Si algo falla, no relanzar: la ficha sigue viva sin auto-refresh.
        }
    }
}
