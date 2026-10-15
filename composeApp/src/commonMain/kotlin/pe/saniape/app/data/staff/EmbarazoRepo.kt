package pe.saniape.app.data.staff

import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.Columns
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.client.request.patch
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put
import pe.saniape.app.data.Supabase
import pe.saniape.app.data.crearHttpClient
import pe.saniape.app.data.offline.RechazoServidor
import pe.saniape.app.data.offline.ResultadoEscritura
import pe.saniape.app.data.offline.enviarOEncolarDetalle

/** Lo que muestra la pestaña 🤰: embarazos, controles y la sede de la paciente (su "hoy"). */
data class LecturaEmbarazo(
    val embarazos: List<Embarazo>? = null,
    val controles: List<ControlPrenatal> = emptyList(),
    val sedeId: String? = null,
    val error: String? = null,
)

/** Una cita de la paciente que el control nuevo puede tomar (la del día, de gineco). */
data class CitaVinculable(
    val id: String,
    val fecha: String,
    val estado: String?,
    val especialidadId: String?,
    val especialidadServicioId: String?,
    val especialidadesProfesional: List<String>?,
)

/**
 * La cita a vincular (gemelo de `citaVinculo` de FormControl.tsx): la ÚNICA cita
 * activa de gineco-obstetricia de esa fecha que aún no tenga control. En una
 * clínica mixta la cita de medicina general del mismo día NO es el control.
 */
fun citaParaVincular(citas: List<CitaVinculable>, fecha: String, usadas: Set<String>, mapa: MapaGineco): String? {
    val delDia = citas.filter { c ->
        c.fecha == fecha && c.id !in usadas &&
            citaEsGineco(mapa, c.especialidadId, c.especialidadServicioId, c.especialidadesProfesional) &&
            !Regex("cancel|no asisti", RegexOption.IGNORE_CASE).containsMatchIn(c.estado.orEmpty())
    }
    return delDia.singleOrNull()?.id
}

/** Texto cuando el servidor todavía no tiene las rutas (backend viejo). */
const val EMBARAZO_NO_DISPONIBLE = "El carné perinatal todavía no está disponible en el servidor."

/**
 * 🤰 Embarazo y controles prenatales por /api/staff/embarazo y
 * /api/staff/control-prenatal (+ /sugerencias), con el Bearer del staff. El
 * servidor valida permiso, el candado de solo lectura y los datos, y CALCULA la
 * FPP / el método de datación y la EG de cada control.
 *
 * El control NUEVO va por la cola offline ([enviarOEncolarDetalle]) con su
 * `clave` de idempotencia (la misma en cada reintento: la API devuelve el
 * existente con `repetido: true`). Lo demás (embarazo, editar y borrar) es
 * DIRECTO: necesita la fila guardada para mostrarla.
 */
object EmbarazoRepo {

    private const val RUTA_EMB = "/api/staff/embarazo"
    const val RUTA_CONTROL = "/api/staff/control-prenatal"
    private const val RUTA_SUGERENCIAS = "/api/staff/control-prenatal/sugerencias"
    private val http = crearHttpClient()
    private val json = Json { ignoreUnknownKeys = true }

    private fun token(): String? = Supabase.client.auth.currentSessionOrNull()?.accessToken

    private val SIN_SESION = ResultadoEscritura(
        registrada = false, rechazo = RechazoServidor("Tu sesión expiró. Vuelve a entrar.", "NO_AUTENTICADO", 401),
    )
    private val SIN_RED = ResultadoEscritura(
        registrada = false, rechazo = RechazoServidor("Sin conexión. Revisa tu internet.", "SIN_RED"),
    )

    private suspend fun resultado(resp: HttpResponse): ResultadoEscritura {
        val cuerpo = runCatching { resp.bodyAsText() }.getOrNull()
        val r = AtencionRepo.resultadoDeRespuesta(resp.status.value, cuerpo)
        if (resp.status == HttpStatusCode.NotFound && r.rechazo?.codigo == null) {
            return ResultadoEscritura(registrada = false, rechazo = RechazoServidor(EMBARAZO_NO_DISPONIBLE, "NO_DISPONIBLE", 404))
        }
        return r
    }

    private sealed interface Lista { data class Ok(val items: JsonArray) : Lista; data class Error(val msg: String) : Lista }

    private suspend fun listar(ruta: String, pacienteId: String, tk: String): Lista = try {
        val resp = http.get("${Supabase.SITE_URL}$ruta") {
            header("Authorization", "Bearer $tk")
            parameter("pacienteId", pacienteId)
        }
        val o = runCatching { json.parseToJsonElement(resp.bodyAsText()).jsonObject }.getOrNull()
        when {
            resp.status == HttpStatusCode.OK && o != null -> Lista.Ok((o["items"] as? JsonArray) ?: JsonArray(emptyList()))
            resp.status == HttpStatusCode.NotFound && o?.get("codigo") == null -> Lista.Error(EMBARAZO_NO_DISPONIBLE)
            else -> Lista.Error((o?.get("error") as? JsonPrimitive)?.content ?: "No se pudo cargar el embarazo")
        }
    } catch (e: CancellationException) { throw e } catch (_: Exception) { Lista.Error("Sin conexión. Revisa tu internet.") }

    /** Embarazos (activo primero), controles y la sede de la paciente, EN PARALELO. */
    suspend fun cargar(pacienteId: String): LecturaEmbarazo {
        val tk = token() ?: return LecturaEmbarazo(error = "Tu sesión expiró. Vuelve a entrar.")
        return coroutineScope {
            val emb = async { listar(RUTA_EMB, pacienteId, tk) }
            val ctl = async { listar(RUTA_CONTROL, pacienteId, tk) }
            val sede = async {
                try {
                    Supabase.client.postgrest["pacientes"].select(Columns.raw("sede_id")) { filter { eq("id", pacienteId) } }
                        .decodeList<JsonObject>().firstOrNull()?.get("sede_id")
                        ?.let { (it as? JsonPrimitive)?.content?.takeIf { s -> s.isNotBlank() && s != "null" } }
                } catch (e: CancellationException) { throw e } catch (_: Exception) { null }
            }
            val e = emb.await()
            val c = ctl.await()
            when {
                e is Lista.Error -> LecturaEmbarazo(error = e.msg)
                c is Lista.Error -> LecturaEmbarazo(error = c.msg)
                else -> LecturaEmbarazo(
                    embarazos = ordenarEmbarazos((e as Lista.Ok).items.mapNotNull { (it as? JsonObject)?.let(::embarazoDeJson) }),
                    controles = (c as Lista.Ok).items.mapNotNull { (it as? JsonObject)?.let(::controlDeJson) },
                    sedeId = sede.await(),
                )
            }
        }
    }

    private suspend fun enviar(metodo: String, ruta: String, cuerpo: JsonObject): ResultadoEscritura {
        val tk = token() ?: return SIN_SESION
        val texto = cuerpo.toString()
        return try {
            pe.saniape.app.ui.conIndicador {
                val resp = when (metodo) {
                    "POST" -> http.post("${Supabase.SITE_URL}$ruta") {
                        header("Authorization", "Bearer $tk"); contentType(ContentType.Application.Json); setBody(texto)
                    }
                    "PATCH" -> http.patch("${Supabase.SITE_URL}$ruta") {
                        header("Authorization", "Bearer $tk"); contentType(ContentType.Application.Json); setBody(texto)
                    }
                    else -> http.delete("${Supabase.SITE_URL}$ruta") {
                        header("Authorization", "Bearer $tk"); contentType(ContentType.Application.Json); setBody(texto)
                    }
                }
                resultado(resp)
            }
        } catch (e: CancellationException) { throw e } catch (_: Exception) { SIN_RED }
    }

    /** POST (nuevo, sin [id]) o PATCH (editar). Borrador YA validado. En `cuerpo["embarazo"]` vuelve la fila. */
    suspend fun guardarEmbarazo(b: BorradorEmbarazo, id: String? = null): ResultadoEscritura =
        enviar(if (id == null) "POST" else "PATCH", RUTA_EMB, cuerpoEmbarazo(b, id))

    /** Finalizar (desenlace + fecha) o reabrir. */
    suspend fun cambiarEstado(id: String, finalizar: Boolean, desenlace: String? = null, fechaFin: String? = null): ResultadoEscritura =
        enviar("PATCH", RUTA_EMB, cuerpoEstadoEmbarazo(id, finalizar, desenlace, fechaFin))

    /** Borra el embarazo y sus controles. */
    suspend fun borrarEmbarazo(id: String): ResultadoEscritura =
        enviar("DELETE", RUTA_EMB, buildJsonObject { put("id", id) })

    /**
     * Control NUEVO: con señal, directo (vuelve la fila en `cuerpo["control"]`);
     * sin señal, a la cola offline con la MISMA clave (`b.clave`) como
     * idempotencia: el reintento nunca duplica el control.
     */
    suspend fun crearControl(b: BorradorControl): ResultadoEscritura =
        enviarOEncolarDetalle("control-prenatal:crear", RUTA_CONTROL, cuerpoControl(b), idemKey = b.clave)

    suspend fun editarControl(b: BorradorControl, id: String): ResultadoEscritura =
        enviar("PATCH", RUTA_CONTROL, cuerpoControl(b, id))

    suspend fun borrarControl(id: String): ResultadoEscritura =
        enviar("DELETE", RUTA_CONTROL, buildJsonObject { put("id", id) })

    /** Caché de la sesión por clínica: UN viaje al abrir el formulario. */
    private val cacheSugerencias = mutableMapOf<String, List<AprendidoPlan>>()

    /** Observaciones que la clínica ya escribió. Si falla o no existe la ruta: vacío (chips base). */
    suspend fun sugerencias(clinicaId: String?): List<AprendidoPlan> {
        val clinica = clinicaId?.takeIf { it.isNotBlank() }
        if (clinica != null) cacheSugerencias[clinica]?.let { return it }
        val tk = token() ?: return emptyList()
        return try {
            val resp = http.get("${Supabase.SITE_URL}$RUTA_SUGERENCIAS") { header("Authorization", "Bearer $tk") }
            if (resp.status != HttpStatusCode.OK) return emptyList()
            val o = runCatching { json.parseToJsonElement(resp.bodyAsText()).jsonObject }.getOrNull()
            aprendidosControlDeJson(o).also { if (clinica != null) cacheSugerencias[clinica] = it }
        } catch (e: CancellationException) { throw e } catch (_: Exception) { emptyList() }
    }

    /** Tras guardar: la próxima vez se vuelve a pedir (ya aprendió lo nuevo). */
    fun olvidarSugerencias() { cacheSugerencias.clear() }

    /**
     * Clínica MIXTA: ¿la paciente ya tiene embarazos? (así no pierde la pestaña si
     * cambió la especialidad de sus citas). Sin la tabla (backend viejo) o sin red → false.
     */
    suspend fun tieneEmbarazos(pacienteId: String): Boolean = try {
        Supabase.client.postgrest["embarazos"]
            .select(Columns.list("id")) { filter { eq("paciente_id", pacienteId) }; limit(1) }
            .decodeList<JsonObject>().isNotEmpty()
    } catch (e: CancellationException) { throw e } catch (_: Exception) { false }

    /**
     * Las citas de la paciente en esa fecha con la cadena de especialidad (la
     * de la cita → la del servicio → las del profesional), para vincular el
     * control a la de gineco-obstetricia. Sin red → vacío (no se vincula).
     */
    suspend fun citasDelDia(pacienteId: String, fecha: String): List<CitaVinculable> = try {
        fun JsonObject.s(k: String) = (this[k] as? JsonPrimitive)?.content?.takeIf { it != "null" && it.isNotBlank() }
        val filas = Supabase.client.postgrest["citas"]
            .select(Columns.raw("id, fecha, estado, especialidad_id, terapeuta_id, " +
                "tratamiento:tratamientos!citas_tratamiento_id_fkey(procedimiento:procedimientos(especialidad_id))")) {
                filter { eq("paciente_id", pacienteId); eq("fecha", fecha) }
                limit(20)
            }.decodeList<JsonObject>()
        val sinEsp = filas.filter { it.s("especialidad_id") == null }.mapNotNull { it.s("terapeuta_id") }.distinct()
        val espProf: Map<String, List<String>> = if (sinEsp.isEmpty()) emptyMap() else runCatching {
            Supabase.client.postgrest["terapeuta_especialidades"]
                .select(Columns.list("terapeuta_id", "especialidad_id")) { filter { isIn("terapeuta_id", sinEsp) } }
                .decodeList<JsonObject>().groupBy({ it.s("terapeuta_id").orEmpty() }, { it.s("especialidad_id").orEmpty() })
        }.getOrDefault(emptyMap())
        filas.mapNotNull { o ->
            val id = o.s("id") ?: return@mapNotNull null
            val proc = (o["tratamiento"] as? JsonObject)?.get("procedimiento") as? JsonObject
            CitaVinculable(
                id = id, fecha = o.s("fecha")?.take(10).orEmpty(), estado = o.s("estado"),
                especialidadId = o.s("especialidad_id"), especialidadServicioId = proc?.s("especialidad_id"),
                especialidadesProfesional = o.s("terapeuta_id")?.let { espProf[it] },
            )
        }
    } catch (e: CancellationException) { throw e } catch (_: Exception) { emptyList() }
}
