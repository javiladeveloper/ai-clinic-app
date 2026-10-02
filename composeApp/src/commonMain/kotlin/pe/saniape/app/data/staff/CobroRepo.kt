package pe.saniape.app.data.staff

import io.github.jan.supabase.auth.auth
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.bodyAsText
import io.ktor.http.isSuccess
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import pe.saniape.app.data.Supabase
import pe.saniape.app.data.crearHttpClient

/**
 * Estado de pago de UNA cita, tal como lo arma la web
 * (`GET /api/staff/cita/estado-pago`). Para el profesional que atiende: solo el
 * estado y la deuda, NUNCA lo ya pagado. Es informativo: jamás bloquea atender.
 */
@Serializable
data class EstadoPagoCita(
    /** "pagado" | "debe" | "parcial" | "gratis" | "sin_dato". */
    val estado: String = "sin_dato",
    /** Lo que falta pagar (S/); null si no aplica. */
    val deuda: Double? = null,
    /** Texto listo para mostrar ("✓ Pagado", "Debe S/ 80"…): lo decide el servidor. */
    val etiqueta: String = "",
    /** La cita es parte de un paquete (la deuda es del paquete, no de la sesión). */
    val esPaquete: Boolean = false,
) {
    /** ¿Hay algo que mostrar? sin_dato o sin etiqueta = nada. */
    val mostrable: Boolean get() = estado != "sin_dato" && etiqueta.isNotBlank()
}

@Serializable
internal data class RespuestaEstadosPago(
    val estados: Map<String, EstadoPagoCita> = emptyMap(),
)

private val jsonEstadoPago = Json { ignoreUnknownKeys = true; coerceInputValues = true }

/** Parseo del cuerpo 200 de estado-pago (puro: se prueba sin red). */
internal fun parsearEstadosPago(cuerpo: String): Map<String, EstadoPagoCita> =
    jsonEstadoPago.decodeFromString(RespuestaEstadosPago.serializer(), cuerpo).estados

/** Ids únicos, sin vacíos, en lotes del tope del servidor ([CobroRepo.LOTE]). */
internal fun lotesEstadoPago(ids: List<String>): List<List<String>> =
    ids.filter { it.isNotBlank() }.distinct().chunked(CobroRepo.LOTE)

/**
 * Lectura del estado de pago de las citas (agenda, consulta guiada, completar
 * sesión). UNA petición por lista cargada, en lotes de [LOTE] ids (el tope del
 * servidor). Cualquier fallo —sin red, 401, 404 mientras el endpoint no esté
 * desplegado— devuelve lo que se pudo leer (o vacío): la pantalla simplemente no
 * muestra el badge. Solo se registra en el log.
 */
object CobroRepo {

    const val LOTE = 100

    private val http = crearHttpClient()

    /** Estado de pago por id de cita. Las citas que el usuario no puede ver no vienen. */
    suspend fun estadosPago(ids: List<String>): Map<String, EstadoPagoCita> {
        val grupos = lotesEstadoPago(ids)
        if (grupos.isEmpty()) return emptyMap()
        val tk = runCatching { Supabase.client.auth.currentSessionOrNull()?.accessToken }.getOrNull()
            ?: return emptyMap()
        val res = mutableMapOf<String, EstadoPagoCita>()
        for (g in grupos) {
            try {
                val resp = http.get("${Supabase.SITE_URL}/api/staff/cita/estado-pago?ids=${g.joinToString(",")}") {
                    header("Authorization", "Bearer $tk")
                }
                if (resp.status.isSuccess()) {
                    res.putAll(parsearEstadosPago(resp.bodyAsText()))
                } else {
                    println("SaniaCobro: estado-pago respondió ${resp.status.value}")
                }
            } catch (e: kotlin.coroutines.cancellation.CancellationException) {
                throw e
            } catch (e: Exception) {
                println("SaniaCobro: no se pudo leer el estado de pago — ${e.message}")
            }
        }
        return res
    }

    /** Atajo para una sola cita (consulta guiada, completar sesión). */
    suspend fun estadoPago(citaId: String?): EstadoPagoCita? =
        citaId?.takeIf { it.isNotBlank() }?.let { estadosPago(listOf(it))[it] }
}
