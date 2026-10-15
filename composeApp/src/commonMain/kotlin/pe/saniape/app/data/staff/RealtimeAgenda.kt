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
 * Realtime de la AGENDA: escucha cambios en la tabla `citas` y avisa para refrescar
 * la vista SIN que el usuario recargue. Efecto: si desde la web (u otro dispositivo) se
 * agenda/cambia/cancela una cita, la agenda del fisio se actualiza sola al instante.
 *
 * Diseño defensivo: es COMPLEMENTARIO. Si Realtime no conecta (sin señal, servidor caído,
 * etc.) la app sigue funcionando igual — solo no se auto-refresca. Nunca rompe la agenda.
 *
 * RLS: el canal recibe solo las filas que el usuario puede ver (mismo get_clinica_id()
 * que las lecturas). El fisio con scope solo se entera de SUS citas.
 */
object RealtimeAgenda {

    /**
     * Suscribe a cambios de `citas` y llama a [onCambio] en cada INSERT/UPDATE/DELETE.
     * Devuelve un Job cancelable (para cortar la suscripción al salir de la pantalla).
     * Best-effort: cualquier fallo se traga y devuelve un Job ya completado.
     */
    fun suscribir(scope: CoroutineScope, onCambio: () -> Unit): Job = suscribirConMeses(scope) { onCambio() }

    /**
     * Como [suscribir], pero dice QUÉ meses tocó el cambio (fecha nueva y vieja,
     * "AAAA-MM"): el calendario del mes vence solo esos. null = no se sabe (un
     * DELETE sin la fila completa): hay que vencer todos.
     */
    fun suscribirConMeses(scope: CoroutineScope, onCambio: (meses: Set<String>?) -> Unit): Job {
        return scope.launch {
            runCatching {
                val canal = Supabase.client.channel("agenda-citas")
                val cambios = canal.postgresChangeFlow<PostgresAction>(schema = "public") {
                    table = "citas"
                }
                cambios
                    .onEach { onCambio(mesesDeAccion(it)) }
                    .launchIn(scope)
                canal.subscribe()
            }
            // Si algo falla, no relanzar: la agenda sigue viva sin auto-refresh.
        }
    }
}

/** Meses de un cambio de Realtime (fila nueva y vieja). Tolerante: sin fecha → null. */
private fun mesesDeAccion(a: PostgresAction): Set<String>? {
    fun fecha(o: kotlinx.serialization.json.JsonObject?): String? =
        (o?.get("fecha") as? kotlinx.serialization.json.JsonPrimitive)?.content?.takeIf { it != "null" }
    return when (a) {
        is PostgresAction.Insert -> mesesDeCambio(fecha(a.record), null)
        // Sin la fila vieja completa (sin REPLICA IDENTITY FULL) no se sabe de qué
        // mes salió: una cita reprogramada a otro mes dejaría el viejo mal contado.
        is PostgresAction.Update -> fecha(a.oldRecord)?.let { mesesDeCambio(fecha(a.record), it) }
        is PostgresAction.Delete -> mesesDeCambio(null, fecha(a.oldRecord))
        else -> null
    }
}
