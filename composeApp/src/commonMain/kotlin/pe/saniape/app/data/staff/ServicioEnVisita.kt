package pe.saniape.app.data.staff

import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.Columns
import io.github.jan.supabase.postgrest.query.Order
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.contentType
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put
import pe.saniape.app.data.Supabase
import pe.saniape.app.data.crearHttpClient

// «✓ Se realizó en esta visita» — gemelo de `lib/servicio-en-visita.ts` (web).
//
// Lo más frecuente en podología (y en cualquier rubro con un servicio de UNA vez:
// uña encarnada, bótox por unidades, una limpieza) es que el servicio se haga en
// la MISMA visita de la evaluación. Antes había que recorrerlo como un plan por
// sesiones (~12 toques). Ahora, en la cita de evaluación/consulta: el servicio, el
// precio acordado y (si quien mira cobra) el medio. UN guardado en el servidor
// (POST /api/staff/cita/servicio-en-visita): el tratamiento realizado, la cita
// completada y el cobro. La app no reimplementa nada: aquí solo lo puro (qué se
// ofrece) y la llamada.

/** Lo que hace falta del servicio para saber si se hace de una vez y cuánto vale. */
data class ServicioUnaVez(
    val id: String,
    val nombre: String,
    val precio: Double,
    val precioUnitarioSugerido: Double? = null,
    val especialidadId: String? = null,
    /** 'sesiones' / 'simple' / 'unidades' / null (= el de la especialidad). */
    val modoCobro: String? = null,
    /** `especialidad.usa_sesiones` (null = sin dato → por sesiones). */
    val usaSesiones: Boolean? = null,
    /** El servicio ES la Consulta/Evaluación de la clínica (no un procedimiento). */
    val tipoCita: String? = null,
    val tipoClinico: String? = null,
    val estado: String = "Activo",
    val unidadLabel: String? = null,
)

/** Cómo se hace de una vez: pago único o por unidades. */
enum class ModoUnaVez { SIMPLE, UNIDADES }

/**
 * ¿El servicio se hace de una vez? Pago único CON precio (la consulta médica de
 * precio 0 no es un procedimiento) o por unidades. Por sesiones (DALU: paquetes),
 * inactivo, el servicio de la propia Consulta/Evaluación o la evaluación
 * psicológica → null. `modoServicioDeUnaVez` de la web.
 */
fun modoServicioDeUnaVez(s: ServicioUnaVez): ModoUnaVez? {
    if (s.estado != "Activo") return null
    if (!s.tipoCita.isNullOrBlank()) return null
    if (s.tipoClinico == "evaluacion_psicologica") return null
    return when (modoCobroEfectivo(s.modoCobro, s.usaSesiones)) {
        "unidades" -> ModoUnaVez.UNIDADES
        "simple" -> if (s.precio > 0) ModoUnaVez.SIMPLE else null
        else -> null
    }
}

/** Los de una vez que se ofrecen en una cita (de su especialidad, si eligió una; los sin especialidad siempre). */
fun serviciosDeUnaVez(servicios: List<ServicioUnaVez>, especialidadId: String?): List<ServicioUnaVez> =
    servicios.filter { s ->
        (especialidadId.isNullOrBlank() || s.especialidadId == null || s.especialidadId == especialidadId) &&
            modoServicioDeUnaVez(s) != null
    }

/** ¿La clínica (o esa especialidad) vende al menos un servicio de una vez? */
fun hayServicioDeUnaVez(servicios: List<ServicioUnaVez>, especialidadId: String?): Boolean =
    serviciosDeUnaVez(servicios, especialidadId).isNotEmpty()

/**
 * ¿Se ofrece «✓ Se realizó en esta visita» en esta cita? Consulta o Evaluación no
 * cancelada, de hoy o de un día pasado; no dental (tiene su atención puntual);
 * quien mira puede crear el tratamiento; el paciente no está de baja; y la clínica
 * vende algún servicio de una vez. `ofreceServicioEnVisita` de la web.
 */
fun ofreceServicioEnVisita(
    tipo: String?, estado: String, fecha: String, hoy: String,
    dental: Boolean, puedeSesiones: Boolean, soloLectura: Boolean,
    pacienteInactivo: Boolean, hayServicios: Boolean,
): Boolean {
    if (tipo != "Consulta" && tipo != "Evaluación") return false
    if (estado != "Pendiente" && estado != "Confirmada" && estado != "Completada") return false
    if (fecha.isBlank() || fecha.take(10) > hoy) return false
    return !dental && puedeSesiones && !soloLectura && !pacienteInactivo && hayServicios
}

/**
 * Qué ofrece la hoja «🩺 Plan de tratamiento» del cierre de la evaluación
 * (HojaPlanTrasEvaluacion) según el servicio sugerido o elegido, para que
 * recepción tenga UN solo camino al servicio hecho en la visita, toque primero
 * «✓ Completar» o «✓ Se realizó en esta visita». Gemelo de
 * `pasoPlanTrasEvaluacion` (lib/servicio-en-visita.ts, web).
 *  - PLAN: por sesiones (o aún sin elegir) → crear el plan y agendar.
 *  - EN_VISITA: de una vez y la cita lo permite ([ofreceServicioEnVisita]) →
 *    «✓ Se realizó en esta visita» (el mismo POST), con el formulario como
 *    alternativa (programarlo para otro día).
 *  - FORMULARIO: lo demás que no va por sesiones → el formulario de siempre.
 */
enum class PasoPlanTrasEvaluacion { PLAN, EN_VISITA, FORMULARIO }

fun pasoPlanTrasEvaluacion(servicio: ServicioUnaVez?, enVisitaPermitido: Boolean): PasoPlanTrasEvaluacion = when {
    servicio == null -> PasoPlanTrasEvaluacion.PLAN
    modoCobroEfectivo(servicio.modoCobro, servicio.usaSesiones) == "sesiones" -> PasoPlanTrasEvaluacion.PLAN
    enVisitaPermitido && modoServicioDeUnaVez(servicio) != null -> PasoPlanTrasEvaluacion.EN_VISITA
    else -> PasoPlanTrasEvaluacion.FORMULARIO
}

/** ¿La hoja del plan tiene qué ofrecer, o salta al formulario? `hojaPlanTieneQueOfrecer` de la web. */
fun hojaPlanTieneQueOfrecer(candidatos: List<ServicioUnaVez>, enVisitaPermitido: Boolean): Boolean =
    candidatos.any { pasoPlanTrasEvaluacion(it, enVisitaPermitido) != PasoPlanTrasEvaluacion.FORMULARIO }

/** Un servicio del catálogo del plan, con lo que decide si se hace de una vez. */
fun ProcedimientoRef.aServicioUnaVez(): ServicioUnaVez = ServicioUnaVez(
    id = id, nombre = nombre, precio = precio, precioUnitarioSugerido = precioUnitarioSugerido,
    especialidadId = especialidadId, modoCobro = modoCobro, usaSesiones = usaSesiones,
    tipoCita = tipoCita, unidadLabel = unidadLabel,
)

/** El precio de lista: el del servicio; por unidades, cantidad × el unitario sugerido (o el precio). */
fun precioListaServicioEnVisita(s: ServicioUnaVez, cantidad: Int): Double =
    if (modoServicioDeUnaVez(s) == ModoUnaVez.UNIDADES) {
        kotlin.math.round((s.precioUnitarioSugerido ?: s.precio) * maxOf(1, cantidad) * 100) / 100
    } else s.precio

/** Lo que se pide al servidor. */
data class PedidoServicioEnVisita(
    val citaId: String,
    val procedimientoId: String,
    val precio: Double,
    /** Solo por unidades. */
    val cantidad: Int? = null,
    val nota: String? = null,
    val terapeutaId: String? = null,
    /** false = la evaluación/consulta de la cita no se cobra (queda en 0). */
    val cobrarConsulta: Boolean = true,
    /** Con medio de pago se cobra en el mismo guardado (permiso 'pagos'). */
    val metodo: String? = null,
)

data class ResultadoServicioEnVisita(
    val tratamientoId: String?,
    val cobrado: Double,
    /** Quedó registrado, pero algo de después no (controles): se dice. */
    val aviso: String?,
    /** Lo clínico sí, el cobro no: se cobra con 💰. */
    val errorCobro: String?,
)

object ServicioEnVisitaRepo {
    private val http = crearHttpClient()

    // ── Puras (testeables sin red) ───────────────────────────────────────────

    internal fun cuerpo(p: PedidoServicioEnVisita, idempotencia: String): JsonObject = buildJsonObject {
        put("citaId", p.citaId)
        put("procedimientoId", p.procedimientoId)
        put("precio", p.precio)
        p.cantidad?.let { put("cantidad", it) }
        p.nota?.trim()?.takeIf { it.isNotEmpty() }?.let { put("nota", it) }
        p.terapeutaId?.takeIf { it.isNotBlank() }?.let { put("terapeutaId", it) }
        put("cobrarConsulta", p.cobrarConsulta)
        p.metodo?.takeIf { it.isNotBlank() }?.let { put("metodo", it) }
        put("idempotency_key", idempotencia)
    }

    internal fun parsear(o: JsonObject): ResultadoServicioEnVisita {
        fun s(k: String) = (o[k] as? JsonPrimitive)?.contentOrNull?.takeIf { it != "null" && it.isNotBlank() }
        return ResultadoServicioEnVisita(
            tratamientoId = s("tratamientoId"),
            cobrado = s("cobrado")?.toDoubleOrNull() ?: 0.0,
            aviso = s("aviso"),
            errorCobro = s("errorCobro"),
        )
    }

    /** El mensaje del servidor y si se puede reintentar con la MISMA clave (parcial: retoma sin duplicar). */
    internal fun error(cuerpo: String?, status: Int): Pair<String, Boolean> {
        val o = cuerpo?.let { runCatching { Json.parseToJsonElement(it).jsonObject }.getOrNull() }
        fun s(k: String) = (o?.get(k) as? JsonPrimitive)?.contentOrNull?.takeIf { it.isNotBlank() }
        return (s("causa") ?: s("error") ?: "No se pudo registrar (HTTP $status).") to (s("parcial") == "true")
    }

    // ── Red ──────────────────────────────────────────────────────────────────

    /** Los servicios activos con lo que decide si se hacen de una vez. Vacío si falla. */
    suspend fun servicios(): List<ServicioUnaVez> = try {
        Supabase.client.postgrest["procedimientos"]
            .select(Columns.raw(
                "id, nombre, precio, precio_unitario_sugerido, especialidad_id, modo_cobro, unidad_label, " +
                    "tipo_cita, tipo_clinico, especialidad:especialidades(usa_sesiones)",
            )) {
                filter { eq("estado", "Activo") }
                order("nombre", Order.ASCENDING)
            }
            .decodeList<JsonObject>()
            .mapNotNull { o ->
                fun s(k: String) = (o[k] as? JsonPrimitive)?.contentOrNull?.takeIf { it != "null" }
                ServicioUnaVez(
                    id = s("id") ?: return@mapNotNull null,
                    nombre = s("nombre") ?: "Servicio",
                    precio = s("precio")?.toDoubleOrNull() ?: 0.0,
                    precioUnitarioSugerido = s("precio_unitario_sugerido")?.toDoubleOrNull(),
                    especialidadId = s("especialidad_id"),
                    modoCobro = s("modo_cobro"),
                    usaSesiones = ((o["especialidad"] as? JsonObject)?.get("usa_sesiones") as? JsonPrimitive)
                        ?.contentOrNull?.let { it != "false" },
                    tipoCita = s("tipo_cita"),
                    tipoClinico = s("tipo_clinico"),
                    unidadLabel = s("unidad_label"),
                )
            }
    } catch (e: kotlin.coroutines.cancellation.CancellationException) {
        throw e
    } catch (_: Exception) {
        emptyList()
    }

    /**
     * Registra el servicio de la visita. Con la misma [idempotencia] un reintento
     * no duplica. Error: (mensaje, si se reintenta con la MISMA clave).
     */
    suspend fun registrar(p: PedidoServicioEnVisita, idempotencia: String): Result<ResultadoServicioEnVisita> {
        val tk = runCatching { Supabase.client.auth.currentSessionOrNull()?.accessToken }.getOrNull()
            ?: return Result.failure(ErrorServicioEnVisita("Tu sesión expiró. Vuelve a entrar.", reintentarIgual = false))
        return try {
            val resp = pe.saniape.app.ui.conIndicador {
                http.post("${Supabase.SITE_URL}/api/staff/cita/servicio-en-visita") {
                    header("Authorization", "Bearer $tk")
                    contentType(ContentType.Application.Json)
                    setBody(cuerpo(p, idempotencia).toString())
                }
            }
            val texto = runCatching { resp.bodyAsText() }.getOrNull()
            if (resp.status.value in 200..299) {
                Result.success(parsear(texto?.let { runCatching { Json.parseToJsonElement(it).jsonObject }.getOrNull() } ?: JsonObject(emptyMap())))
            } else {
                val (msg, parcial) = error(texto, resp.status.value)
                Result.failure(ErrorServicioEnVisita(msg, reintentarIgual = parcial))
            }
        } catch (e: kotlin.coroutines.cancellation.CancellationException) {
            throw e
        } catch (_: Exception) {
            // Sin respuesta: pudo quedar a medias. La misma clave lo retoma sin duplicar.
            Result.failure(ErrorServicioEnVisita("Sin conexión: no se registró. Vuelve a intentarlo.", reintentarIgual = true))
        }
    }
}

/** Fallo de «Se realizó en esta visita». [reintentarIgual]: reintentar con la MISMA clave de idempotencia. */
class ErrorServicioEnVisita(mensaje: String, val reintentarIgual: Boolean) : Exception(mensaje)
