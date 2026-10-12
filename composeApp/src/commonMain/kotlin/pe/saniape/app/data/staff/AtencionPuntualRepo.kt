package pe.saniape.app.data.staff

import io.github.jan.supabase.auth.auth
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.contentType
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.addJsonObject
import pe.saniape.app.data.Supabase
import pe.saniape.app.data.crearHttpClient

/**
 * ATENCIÓN PUNTUAL dental — el paciente vino por algo concreto: piezas →
 * qué se hizo → cobro. La lógica vive en la web (lib/atencion-puntual.ts):
 *  · POST /api/staff/atencion-puntual          registra (odontograma + tratamientos + cita)
 *  · POST /api/staff/atencion-puntual/cobrar   cobra todo con un medio (permiso de caja)
 *  · GET  /api/staff/comprobante/atencion/{id} recibo de la visita (ReciboRepo)
 * Alimenta el MISMO odontograma que la revisión completa.
 */

/** Lo que se le hizo a una pieza ("BOCA" = toda la boca). */
data class ItemPuntual(val diente: String, val procedimientoId: String, val precio: Double)

data class ResultadoPuntual(val resumen: String, val total: Double, val consulta: Double) {
    val porCobrar: Double get() = kotlin.math.round((total + consulta) * 100) / 100
}

/** Éxito con datos, o el mensaje del servidor. */
sealed interface RespuestaPuntual<out T> {
    data class Ok<T>(val valor: T) : RespuestaPuntual<T>
    data class Error(val mensaje: String, val codigo: String? = null) : RespuestaPuntual<Nothing>
}

const val BOCA = "BOCA"

object AtencionPuntualRepo {
    private val http = crearHttpClient()

    // ── Puras (testeables sin red) ───────────────────────────────────────────

    internal fun cuerpoRegistrar(
        citaId: String, items: List<ItemPuntual>, terapeutaId: String?, nota: String,
        cobrarConsulta: Boolean, idempotencia: String,
    ): JsonObject = buildJsonObject {
        put("citaId", citaId)
        if (!terapeutaId.isNullOrBlank()) put("terapeutaId", terapeutaId)
        put("nota", nota.trim())
        put("cobrarConsulta", cobrarConsulta)
        put("idempotency_key", idempotencia)
        putJsonArray("items") {
            items.forEach { it2 ->
                addJsonObject {
                    put("diente", it2.diente)
                    put("procedimientoId", it2.procedimientoId)
                    put("precio", it2.precio)
                }
            }
        }
    }

    internal fun parsearResultado(o: JsonObject): ResultadoPuntual {
        fun s(k: String) = (o[k] as? JsonPrimitive)?.contentOrNull
        return ResultadoPuntual(
            resumen = s("resumen") ?: "",
            total = s("total")?.toDoubleOrNull() ?: 0.0,
            consulta = s("consulta")?.toDoubleOrNull() ?: 0.0,
        )
    }

    internal fun mensajeError(cuerpo: String?, status: Int): Pair<String, String?> {
        val o = cuerpo?.let { runCatching { kotlinx.serialization.json.Json.parseToJsonElement(it).jsonObject }.getOrNull() }
        fun s(k: String) = (o?.get(k) as? JsonPrimitive)?.contentOrNull?.takeIf { it.isNotBlank() }
        return (s("causa") ?: s("error") ?: "No se pudo completar (HTTP $status).") to s("codigo")
    }

    // ── Red ──────────────────────────────────────────────────────────────────

    private suspend fun post(ruta: String, cuerpo: JsonObject): RespuestaPuntual<JsonObject> {
        val tk = runCatching { Supabase.client.auth.currentSessionOrNull()?.accessToken }.getOrNull()
            ?: return RespuestaPuntual.Error("Tu sesión expiró. Vuelve a entrar.")
        return try {
            val resp = http.post("${Supabase.SITE_URL}$ruta") {
                header("Authorization", "Bearer $tk")
                contentType(ContentType.Application.Json)
                setBody(cuerpo.toString())
            }
            val texto = runCatching { resp.bodyAsText() }.getOrNull()
            if (resp.status.value in 200..299) {
                RespuestaPuntual.Ok(texto?.let { runCatching { kotlinx.serialization.json.Json.parseToJsonElement(it).jsonObject }.getOrNull() } ?: JsonObject(emptyMap()))
            } else {
                val (msg, cod) = mensajeError(texto, resp.status.value)
                RespuestaPuntual.Error(msg, cod)
            }
        } catch (e: kotlin.coroutines.cancellation.CancellationException) {
            throw e
        } catch (e: Exception) {
            RespuestaPuntual.Error("Sin conexión: no se registró. Vuelve a intentarlo.")
        }
    }

    /** Registra la atención. Con la misma [idempotencia] un reintento no duplica. */
    suspend fun registrar(
        citaId: String, items: List<ItemPuntual>, terapeutaId: String?, nota: String,
        cobrarConsulta: Boolean, idempotencia: String,
    ): RespuestaPuntual<ResultadoPuntual> =
        when (val r = post("/api/staff/atencion-puntual", cuerpoRegistrar(citaId, items, terapeutaId, nota, cobrarConsulta, idempotencia))) {
            is RespuestaPuntual.Ok -> RespuestaPuntual.Ok(parsearResultado(r.valor))
            is RespuestaPuntual.Error -> r
        }

    /** Cobra todo lo pendiente de la atención. Devuelve lo cobrado. */
    suspend fun cobrar(citaId: String, metodo: String, idempotencia: String): RespuestaPuntual<Double> =
        when (val r = post("/api/staff/atencion-puntual/cobrar", buildJsonObject {
            put("citaId", citaId); put("metodo", metodo); put("idempotency_key", idempotencia)
        })) {
            is RespuestaPuntual.Ok -> RespuestaPuntual.Ok((r.valor["cobrado"] as? JsonPrimitive)?.contentOrNull?.toDoubleOrNull() ?: 0.0)
            is RespuestaPuntual.Error -> r
        }
}
