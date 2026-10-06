package pe.saniape.app.data.staff

import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.Columns
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.bodyAsText
import io.ktor.http.isSuccess
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
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

    /**
     * Con qué se pagó cada cita COBRADA ("Yape", "Efectivo + Yape"), gemelo de
     * `metodosPagoDeCitas` de la web: UNA lectura de `movimientos` por la parte
     * base (`cita:<id>`) de todas las citas pedidas y, solo si alguna se dividió
     * (casi nunca), una segunda por sus partes 2..n. Nunca una por tarjeta.
     * Es adorno: cualquier fallo devuelve lo que se pudo (o vacío) y la tarjeta
     * dice "Pagado" sin el medio, como antes. Quien llama decide si el rol lo ve.
     */
    suspend fun metodosPagoDeCitas(citaIds: List<String>): Map<String, String> {
        val ids = citaIds.filter { it.isNotBlank() }.distinct()
        if (ids.isEmpty()) return emptyMap()
        suspend fun leer(comprobantes: List<String>, conDescripcion: Boolean): List<MovimientoCobroCita> =
            comprobantes.chunked(LOTE).flatMap { lote ->
                Supabase.client.postgrest["movimientos"]
                    .select(Columns.raw(if (conDescripcion) "comprobante, metodo_pago, descripcion" else "comprobante, metodo_pago")) {
                        filter { isIn("comprobante", lote) }
                    }
                    .decodeList<JsonObject>()
                    .mapNotNull { o ->
                        fun s(k: String) = (o[k] as? JsonPrimitive)?.content?.takeIf { it != "null" }
                        s("comprobante")?.let { MovimientoCobroCita(it, s("metodo_pago"), s("descripcion")) }
                    }
            }
        return try {
            val base = leer(ids.map { comprobanteCita(it) }, conDescripcion = true)
            val divididas = citasConCobroDividido(base)
            val extra = if (divididas.isEmpty()) emptyList()
                else runCatching { leer(divididas.flatMap { comprobantesExtraDeCita(it) }, conDescripcion = false) }
                    .getOrElse { if (it is kotlin.coroutines.cancellation.CancellationException) throw it; emptyList() }
            metodosPorCita(base, extra)
        } catch (e: kotlin.coroutines.cancellation.CancellationException) {
            throw e
        } catch (e: Exception) {
            println("SaniaCobro: no se pudieron leer los medios de pago — ${e.message}")
            emptyMap()
        }
    }

    /**
     * Anula el cobro DIVIDIDO de una cita (solo Admin): POST
     * /api/staff/cita/anular-cobro. Borra todas sus partes de caja y la cita
     * vuelve a "por cobrar". DIRECTO, sin cola offline: borra dinero y el Admin
     * tiene que ver el resultado en el momento. Devuelve null si se anuló; si
     * no, el texto a mostrar ([mensajeAnularCobro]).
     */
    suspend fun anularCobro(citaId: String): String? {
        val cuerpo = buildJsonObject { put("citaId", citaId) }
        val (res, rechazo, _) = runCatching {
            pe.saniape.app.data.offline.Sincronizador.enviarAhoraCompleto(
                "/api/staff/cita/anular-cobro", cuerpo, pe.saniape.app.data.offline.nuevaIdemKey(),
            )
        }.getOrElse {
            if (it is kotlin.coroutines.cancellation.CancellationException) throw it
            Triple(pe.saniape.app.data.offline.ResultadoEnvio.SIN_RED, null, null)
        }
        return when (res) {
            pe.saniape.app.data.offline.ResultadoEnvio.OK -> null
            pe.saniape.app.data.offline.ResultadoEnvio.SIN_RED -> mensajeAnularCobro(0, null)
            pe.saniape.app.data.offline.ResultadoEnvio.RECHAZADO -> mensajeAnularCobro(rechazo?.status ?: 0, rechazo?.error)
        }
    }

    /** Atajo para una sola cita (consulta guiada, completar sesión). */
    suspend fun estadoPago(citaId: String?): EstadoPagoCita? =
        citaId?.takeIf { it.isNotBlank() }?.let { estadosPago(listOf(it))[it] }
}
