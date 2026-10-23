package pe.saniape.app.data.staff

import io.github.jan.supabase.postgrest.query.filter.FilterOperator
import io.github.jan.supabase.realtime.HasOldRecord
import io.github.jan.supabase.realtime.HasRecord
import io.github.jan.supabase.realtime.PostgresAction
import io.github.jan.supabase.realtime.RealtimeChannel
import io.github.jan.supabase.realtime.channel
import io.github.jan.supabase.realtime.postgresChangeFlow
import io.github.jan.supabase.realtime.realtime
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
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
 * Solo lo del PACIENTE ABIERTO y en ráfaga (DALU, 23/10/2026): antes escuchaba las tres
 * tablas de TODA la clínica sin filtro, y cualquier cambio de otro usuario recargaba la
 * ficha entera (una docena de consultas); completar una sesión, que dispara 3–5 eventos
 * seguidos, la recargaba otras tantas veces. Ahora:
 *  - `citas` y `tratamientos` se filtran EN EL SERVIDOR por `paciente_id`.
 *  - `sesiones` no tiene `paciente_id`: se descartan en el teléfono las de tratamientos
 *    que no son del paciente ([eventoEsDelPaciente]).
 *  - Los eventos se juntan ([RafagaEventos], ~800 ms sin eventos) en UNA recarga.
 *
 * RLS: el canal recibe solo las filas que el usuario puede ver (mismo get_clinica_id()
 * que las lecturas).
 */
object RealtimeFicha {

    // `citas` cubre también las EVALUACIONES (son citas de tipo "Evaluación") y los hitos
    // (próxima cita / última atención) que muestra la ficha.
    private val TABLAS = listOf("citas", "tratamientos", "sesiones")

    /** Cada suscripción con su propio tema: al cerrar una ficha no se corta la de la siguiente. */
    private var contador = 0

    /**
     * Suscribe a los cambios del paciente [pacienteId] y llama a [onCambio] UNA vez por
     * ráfaga, con el momento ([relojMonotonoMs]) en que llegó su último evento — la ficha
     * lo usa para no recargar si ya hay una carga que lo incluye ([accionRealtime]).
     * [tratamientosDelPaciente] se lee en cada evento (los tratamientos cambian mientras
     * la ficha está abierta). Devuelve un Job cancelable (al cerrar la ficha se corta y
     * se deja el canal). Best-effort: cualquier fallo se traga y no rompe nada.
     */
    fun suscribir(
        scope: CoroutineScope,
        pacienteId: String,
        tratamientosDelPaciente: () -> Set<String>,
        onCambio: (ultimoEventoMs: Long) -> Unit,
    ): Job {
        return scope.launch {
            var canal: RealtimeChannel? = null
            val rafaga = RafagaEventos()
            // Conflado: basta con saber que hay "algo pendiente"; la ráfaga lleva la hora.
            val senal = Channel<Unit>(Channel.CONFLATED)
            try {
                runCatching {
                    val c = Supabase.client.channel("ficha-paciente-$pacienteId-${contador++}")
                    canal = c
                    // launchIn(this) → los flujos son hijos de ESTE Job, así job.cancel() los corta.
                    TABLAS.forEach { t ->
                        c.postgresChangeFlow<PostgresAction>(schema = "public") {
                            table = t
                            // En el servidor: solo las filas de este paciente. Las sesiones
                            // no tienen la columna (se filtran abajo, por su tratamiento).
                            if (t != "sesiones") filter("paciente_id", FilterOperator.EQ, pacienteId)
                        }
                            .onEach { a ->
                                val col = columnaDuenoEvento(t)
                                val nuevo = (a as? HasRecord)?.record?.texto(col)
                                val viejo = (a as? HasOldRecord)?.oldRecord?.texto(col)
                                if (eventoEsDelPaciente(t, nuevo, viejo, pacienteId, tratamientosDelPaciente())) {
                                    rafaga.evento(relojMonotonoMs())
                                    senal.trySend(Unit)
                                }
                            }
                            .launchIn(this)
                    }
                    c.subscribe()
                }
                // Si algo falla al suscribir, no relanzar: la ficha sigue viva sin auto-refresh.
                while (true) {
                    senal.receive()
                    // Esperar a que la ráfaga se calme (cada evento nuevo corre la espera).
                    while (true) {
                        val falta = rafaga.faltan(relojMonotonoMs()) ?: break
                        if (falta <= 0L) break
                        delay(falta)
                    }
                    rafaga.disparar()?.let { onCambio(it) }
                }
            } finally {
                // Dejar el canal en el servidor (antes quedaba abierto hasta reconectar).
                canal?.let { c -> withContext(NonCancellable) { runCatching { Supabase.client.realtime.removeChannel(c) } } }
            }
        }
    }

    private fun JsonObject.texto(k: String): String? =
        (this[k] as? JsonPrimitive)?.content?.takeIf { it != "null" }
}
