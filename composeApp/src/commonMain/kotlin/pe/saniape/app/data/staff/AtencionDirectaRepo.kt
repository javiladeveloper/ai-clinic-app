package pe.saniape.app.data.staff

import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.Columns
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.client.statement.bodyAsText
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.put
import pe.saniape.app.data.Supabase
import pe.saniape.app.data.crearHttpClient

/**
 * ⚡ Atención directa (AtencionDirecta.kt): lo que la app lee y escribe. La regla
 * vive en la web — GET/POST `/api/staff/cita/atender-rapido` (mismo contrato que
 * `components/citas/AtenderRapido.tsx`), con el Bearer del staff como los demás
 * endpoints `/api/staff/...` — y la app solo muestra y manda.
 */
object AtencionDirectaRepo {
    private val http = crearHttpClient()
    private val json = Json { ignoreUnknownKeys = true }
    private suspend fun token(): String? = Supabase.client.auth.currentSessionOrNull()?.accessToken

    sealed class Lectura {
        data class Ok(val datos: DatosAtencionDirecta) : Lectura()
        data class Error(val mensaje: String) : Lectura()
    }

    /** GET ?citaId=: la cita, su evolución (si ya se atendió) y la última del paciente. */
    suspend fun leer(citaId: String): Lectura {
        val tk = token() ?: return Lectura.Error("Tu sesión expiró. Vuelve a entrar.")
        return try {
            val resp = http.get("${Supabase.SITE_URL}/api/staff/cita/atender-rapido") {
                header("Authorization", "Bearer $tk")
                parameter("citaId", citaId)
            }
            val cuerpo = runCatching { json.parseToJsonElement(resp.bodyAsText()) as? JsonObject }.getOrNull()
            if (resp.status.value !in 200..299) {
                Lectura.Error((cuerpo?.get("error") as? JsonPrimitive)?.contentOrNull ?: "No se pudo abrir la atención")
            } else parsearAtencionDirecta(cuerpo)?.let { Lectura.Ok(it) } ?: Lectura.Error("No se pudo abrir la atención")
        } catch (e: kotlin.coroutines.cancellation.CancellationException) {
            throw e
        } catch (_: Exception) {
            Lectura.Error("Sin conexión: no se pudo abrir la atención.")
        }
    }

    /**
     * Resultado del POST. [ok]: la evolución quedó y la cita está Completada.
     * [atencionId] != null con error = la evolución SÍ quedó (se relee).
     */
    data class Guardado(val ok: Boolean, val error: String? = null, val atencionId: String? = null)

    /**
     * POST { citaId, evolucion: {evolucion, estado, tareas, plan}, cobrara }.
     * Directo, sin cola: es la historia clínica y el cierre de la cita, quien
     * atiende tiene que ver el resultado. [cobrara] = la pantalla cobra enseguida
     * con /api/staff/cita/cobrar (el servidor no auto-cobra).
     */
    suspend fun guardar(citaId: String, e: EvolucionSesionPsico, cobrara: Boolean): Guardado {
        val cuerpo = buildJsonObject {
            put("citaId", citaId)
            put("evolucion", buildJsonObject {
                put("evolucion", e.evolucion); put("estado", e.estado); put("tareas", e.tareas); put("plan", e.plan)
            })
            put("cobrara", cobrara)
        }
        val (res, rechazo, resp) = try {
            pe.saniape.app.data.offline.Sincronizador.enviarAhoraCompleto(
                "/api/staff/cita/atender-rapido", cuerpo, pe.saniape.app.data.offline.nuevaIdemKey(),
            )
        } catch (ex: kotlin.coroutines.cancellation.CancellationException) {
            throw ex
        } catch (_: Exception) {
            Triple(pe.saniape.app.data.offline.ResultadoEnvio.SIN_RED, null, null)
        }
        return when (res) {
            pe.saniape.app.data.offline.ResultadoEnvio.OK ->
                Guardado(true, atencionId = (resp?.get("atencionId") as? JsonPrimitive)?.contentOrNull)
            pe.saniape.app.data.offline.ResultadoEnvio.SIN_RED ->
                Guardado(false, "Sin conexión: revisa la agenda antes de repetir.")
            pe.saniape.app.data.offline.ResultadoEnvio.RECHAZADO -> Guardado(
                false,
                rechazo?.error?.takeIf { it.isNotBlank() } ?: "No se pudo guardar",
                (rechazo?.datos?.get("atencionId") as? JsonPrimitive)?.contentOrNull?.takeIf { it != "null" },
            )
        }
    }

    /**
     * ¿Cuáles de estas citas YA atendidas tienen su evolución? (`useEvolucionCitas`
     * de la web). UNA consulta por lista y solo con las que la ofrecen: en DALU la
     * lista va vacía y no se pide nada. null = no se pudo leer (botón discreto).
     */
    suspend fun citasConEvolucion(citaIds: Collection<String>): Set<String>? {
        val ids = citaIds.distinct()
        if (ids.isEmpty()) return emptySet()
        return try {
            Supabase.client.postgrest["atenciones_clinicas"]
                .select(Columns.list("cita_evolucion_id")) { filter { isIn("cita_evolucion_id", ids) } }
                .decodeList<JsonObject>()
                .mapNotNull { (it["cita_evolucion_id"] as? JsonPrimitive)?.contentOrNull?.takeIf { s -> s != "null" } }
                .toSet()
        } catch (e: kotlin.coroutines.cancellation.CancellationException) {
            throw e
        } catch (_: Exception) {
            null
        }
    }

    /**
     * Los servicios activos, lo justo para sugerir el precio de la atención directa
     * ([precioSugeridoAtencionDirecta]). Vacío si la base no tiene alguna columna.
     */
    suspend fun serviciosPrecio(): List<ServicioPrecioAtencion> = try {
        Supabase.client.postgrest["procedimientos"]
            .select(Columns.raw("id, precio, especialidad_id, modo_cobro, tipo_cita, tipo_clinico, especialidad:especialidades(usa_sesiones)")) {
                filter { eq("estado", "Activo") }
            }
            .decodeList<JsonObject>()
            .mapNotNull { o ->
                ServicioPrecioAtencion(
                    id = o.s("id") ?: return@mapNotNull null,
                    precio = o.s("precio")?.toDoubleOrNull() ?: 0.0,
                    especialidadId = o.s("especialidad_id"),
                    modoCobro = o.s("modo_cobro"),
                    usaSesiones = (o["especialidad"] as? JsonObject)?.s("usa_sesiones") != "false",
                    tipoCita = o.s("tipo_cita"),
                    tipoClinico = o.s("tipo_clinico"),
                )
            }
    } catch (e: kotlin.coroutines.cancellation.CancellationException) {
        throw e
    } catch (_: Exception) {
        emptyList()
    }

    /**
     * Los tratamientos TERMINADOS (Completado) del paciente con la fecha de su
     * última sesión completada: los candidatos de «＋ Ampliar tratamiento anterior»
     * ([tratamientosAmpliables] decide cuáles). Dos consultas chicas, solo con el
     * aviso «＋ Crear tratamiento» a la vista. Vacío si falla (queda solo «Crear»).
     */
    suspend fun tratamientosTerminados(pacienteId: String): List<TratAmpliable> = try {
        val filas = Supabase.client.postgrest["tratamientos"]
            .select(Columns.raw(
                "id, estado, modalidad, procedimiento_id, terapeuta_id, total_sesiones, precio_por_sesion, " +
                    "diagnostico, fecha_inicio, created_at, " +
                    "procedimiento:procedimientos(nombre, precio, especialidad_id, tipo_clinico, especialidad:especialidades(usa_sesiones))"
            )) {
                filter { eq("paciente_id", pacienteId); eq("estado", "Completado") }
            }
            .decodeList<JsonObject>()
        val ids = filas.mapNotNull { it.s("id") }
        val ultima = mutableMapOf<String, String>()
        if (ids.isNotEmpty()) {
            Supabase.client.postgrest["sesiones"]
                .select(Columns.list("tratamiento_id, fecha")) {
                    filter { isIn("tratamiento_id", ids); eq("estado", "Completada") }
                    order("fecha", io.github.jan.supabase.postgrest.query.Order.DESCENDING)
                }
                .decodeList<JsonObject>()
                .forEach { s ->
                    val t = s.s("tratamiento_id"); val f = s.s("fecha")
                    if (t != null && f != null && t !in ultima) ultima[t] = f
                }
        }
        filas.mapNotNull { o ->
            val id = o.s("id") ?: return@mapNotNull null
            val p = o["procedimiento"] as? JsonObject
            TratAmpliable(
                id = id, estado = o.s("estado"), modalidad = o.s("modalidad"),
                procedimientoId = o.s("procedimiento_id"), servicio = p?.s("nombre"),
                precioServicio = p?.s("precio")?.toDoubleOrNull(),
                precioPorSesion = o.s("precio_por_sesion")?.toDoubleOrNull(),
                totalSesiones = o.s("total_sesiones")?.toDoubleOrNull()?.toInt(),
                terapeutaId = o.s("terapeuta_id"), especialidadId = p?.s("especialidad_id"),
                diagnostico = o.s("diagnostico"), fechaInicio = o.s("fecha_inicio"), creadoAt = o.s("created_at"),
                usaSesionesServicio = (p?.get("especialidad") as? JsonObject)?.s("usa_sesiones")?.let { it != "false" },
                ultimaSesion = ultima[id], tipoClinico = p?.s("tipo_clinico"),
            )
        }
    } catch (e: kotlin.coroutines.cancellation.CancellationException) {
        throw e
    } catch (_: Exception) {
        emptyList()
    }

    private fun JsonObject.s(k: String): String? = when (val v = this[k]) {
        is JsonPrimitive -> v.contentOrNull?.takeIf { it != "null" }
        is JsonArray, is JsonObject -> null
        else -> null
    }
}
