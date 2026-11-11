package pe.saniape.app.data.staff

import io.github.jan.supabase.postgrest.query.filter.FilterOperator
import io.github.jan.supabase.realtime.PostgresAction
import io.github.jan.supabase.realtime.RealtimeChannel
import io.github.jan.supabase.realtime.channel
import io.github.jan.supabase.realtime.postgresChangeFlow
import io.github.jan.supabase.realtime.realtime
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.withContext
import pe.saniape.app.data.Supabase

/**
 * El odontograma ABIERTO se actualiza solo (11/11/2026, pendiente de la auditoría
 * dental: «dientes_hallazgos sin Realtime»). Si la asistente marca una caries desde
 * la web o el doctor desde otro teléfono, la boca que se está mirando la muestra sin
 * salir y volver a entrar.
 *
 * Mismo diseño que [RealtimeFicha]: complementario (si no conecta, el odontograma
 * funciona igual, solo no se refresca solo), filtrado EN EL SERVIDOR por el paciente
 * y con las ráfagas juntadas en una sola recarga (marcar con pincel dispara varios
 * eventos seguidos). Requiere la tabla en la publicación `supabase_realtime`
 * (migración 20261211110000 de la web).
 */
object RealtimeOdontograma {
    private var contador = 0

    /**
     * Escucha hasta que se cancele el coroutine que la llama (un LaunchedEffect de la
     * pantalla). [onCambio] corre una vez por ráfaga. Nunca lanza.
     */
    suspend fun escuchar(pacienteId: String, onCambio: suspend () -> Unit) {
        var canal: RealtimeChannel? = null
        val rafaga = RafagaEventos()
        val senal = Channel<Unit>(Channel.CONFLATED)
        try {
            coroutineScope {
                runCatching {
                    val c = Supabase.client.channel("odontograma-$pacienteId-${contador++}")
                    canal = c
                    c.postgresChangeFlow<PostgresAction>(schema = "public") {
                        table = "dientes_hallazgos"
                        filter("paciente_id", FilterOperator.EQ, pacienteId)
                    }
                        .onEach { rafaga.evento(relojMonotonoMs()); senal.trySend(Unit) }
                        .launchIn(this)
                    c.subscribe()
                }
                while (true) {
                    senal.receive()
                    while (true) {
                        val falta = rafaga.faltan(relojMonotonoMs()) ?: break
                        if (falta <= 0L) break
                        delay(falta)
                    }
                    if (rafaga.disparar() != null) runCatching { onCambio() }
                }
            }
        } finally {
            canal?.let { c -> withContext(NonCancellable) { runCatching { Supabase.client.realtime.removeChannel(c) } } }
        }
    }
}
