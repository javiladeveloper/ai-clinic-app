package pe.saniape.app.data.staff

import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.Columns
import io.github.jan.supabase.postgrest.query.Order
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
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put
import pe.saniape.app.data.Supabase
import pe.saniape.app.data.crearHttpClient
import pe.saniape.app.data.offline.RechazoServidor
import pe.saniape.app.data.offline.ResultadoEscritura

/** Una lectura: [items] o, si no se pudo, [error] para mostrar. */
data class LecturaOrto<T>(val items: T? = null, val error: String? = null)

/**
 * 😁 Ortodoncia por /api/staff/ortodoncia (plan) y /api/staff/ortodoncia/controles,
 * con el Bearer del staff (los mismos endpoints de la web). Mismo molde que
 * [AplicacionesEsteticasRepo]: el servidor valida permiso, candado de solo
 * lectura y datos; UN plan por tratamiento y UN control por cita (409
 * YA_EXISTE con el `id` del existente, para abrirlo). DIRECTO, sin cola offline.
 * Un backend sin las rutas (404 sin JSON) → mensaje amable.
 */
object OrtodonciaRepo {

    private const val RUTA_PLANES = "/api/staff/ortodoncia"
    private const val RUTA_CONTROLES = "/api/staff/ortodoncia/controles"
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
            return ResultadoEscritura(registrada = false, rechazo = RechazoServidor(ORTODONCIA_NO_DISPONIBLE, "NO_DISPONIBLE", 404))
        }
        return r
    }

    private suspend fun <T> leer(ruta: String, param: Pair<String, String>, campo: String, convertir: (JsonArray) -> T): LecturaOrto<T> {
        val tk = token() ?: return LecturaOrto(error = "Tu sesión expiró. Vuelve a entrar.")
        return try {
            val resp = http.get("${Supabase.SITE_URL}$ruta") {
                header("Authorization", "Bearer $tk")
                parameter(param.first, param.second)
            }
            val texto = runCatching { resp.bodyAsText() }.getOrNull()
            val o = texto?.let { runCatching { json.parseToJsonElement(it).jsonObject }.getOrNull() }
            when {
                resp.status == HttpStatusCode.OK && o != null -> LecturaOrto(items = convertir((o[campo] as? JsonArray) ?: JsonArray(emptyList())))
                resp.status == HttpStatusCode.NotFound && o?.get("codigo") == null -> LecturaOrto(error = ORTODONCIA_NO_DISPONIBLE)
                else -> LecturaOrto(error = (o?.get("error") as? JsonPrimitive)?.content ?: "No se pudo cargar la ortodoncia")
            }
        } catch (e: CancellationException) { throw e } catch (_: Exception) {
            LecturaOrto(error = "Sin conexión. Revisa tu internet.")
        }
    }

    /** Los planes del paciente, el más reciente primero. */
    suspend fun listarPlanes(pacienteId: String): LecturaOrto<List<PlanOrtodoncia>> =
        leer(RUTA_PLANES, "pacienteId" to pacienteId, "planes") { a ->
            ordenarPlanesOrto(a.mapNotNull { planOrtoDeJson(it as? JsonObject) })
        }

    /** Los controles del plan, el más reciente primero. */
    suspend fun listarControles(planId: String): LecturaOrto<List<ControlOrtodoncia>> =
        leer(RUTA_CONTROLES, "planId" to planId, "controles") { a ->
            ordenarControles(a.mapNotNull { controlOrtoDeJson(it as? JsonObject) })
        }

    private suspend fun enviar(ruta: String, cuerpo: String, nuevo: Boolean): ResultadoEscritura {
        val tk = token() ?: return SIN_SESION
        return try {
            pe.saniape.app.ui.conIndicador {
                val resp = if (nuevo) {
                    http.post("${Supabase.SITE_URL}$ruta") {
                        header("Authorization", "Bearer $tk"); contentType(ContentType.Application.Json); setBody(cuerpo)
                    }
                } else {
                    http.patch("${Supabase.SITE_URL}$ruta") {
                        header("Authorization", "Bearer $tk"); contentType(ContentType.Application.Json); setBody(cuerpo)
                    }
                }
                resultado(resp)
            }
        } catch (e: CancellationException) { throw e } catch (_: Exception) { SIN_RED }
    }

    /** POST (sin [id]) o PATCH. En `cuerpo["plan"]` vuelve la fila; con 409 YA_EXISTE, `cuerpo["id"]` = el plan existente. */
    suspend fun guardarPlan(b: BorradorPlanOrto, id: String? = null): ResultadoEscritura =
        enviar(RUTA_PLANES, cuerpoPlanOrto(b, id).toString(), nuevo = id == null)

    /**
     * POST (sin [id]) o PATCH. En `cuerpo["control"]` vuelve la fila y, si el
     * control movió la fase del plan (solo el último), `cuerpo["plan"]`. Con 409
     * YA_EXISTE, `cuerpo["id"]` = el control que esa cita ya tiene.
     */
    suspend fun guardarControl(b: BorradorControlOrto, id: String? = null): ResultadoEscritura =
        enviar(RUTA_CONTROLES, cuerpoControlOrto(b, id).toString(), nuevo = id == null)

    suspend fun borrarControl(id: String): ResultadoEscritura {
        val tk = token() ?: return SIN_SESION
        return try {
            pe.saniape.app.ui.conIndicador {
                val resp = http.delete("${Supabase.SITE_URL}$RUTA_CONTROLES") {
                    header("Authorization", "Bearer $tk")
                    contentType(ContentType.Application.Json)
                    setBody(buildJsonObject { put("id", id) }.toString())
                }
                resultado(resp)
            }
        } catch (e: CancellationException) { throw e } catch (_: Exception) { SIN_RED }
    }

    /** Caché de la sesión por clínica: lo aprendido cambia poco. */
    private val cacheSugerencias = mutableMapOf<String, List<AprendidoPlan>>()

    /** Lo que la clínica escribe en las observaciones (GET ?sugerencias=1). Si falla, vacío: quedan los chips base. */
    suspend fun sugerencias(clinicaId: String?): List<AprendidoPlan> {
        val clinica = clinicaId?.takeIf { it.isNotBlank() }
        if (clinica != null) cacheSugerencias[clinica]?.let { return it }
        val tk = token() ?: return emptyList()
        return try {
            val resp = withTimeoutOrNull(12_000) {
                http.get("${Supabase.SITE_URL}$RUTA_CONTROLES") {
                    header("Authorization", "Bearer $tk")
                    parameter("sugerencias", "1")
                }
            } ?: return emptyList()
            if (resp.status != HttpStatusCode.OK) return emptyList()
            val o = runCatching { json.parseToJsonElement(resp.bodyAsText()).jsonObject }.getOrNull()
            sugerenciasOrtoDeJson(o).also { if (clinica != null) cacheSugerencias[clinica] = it }
        } catch (e: CancellationException) { throw e } catch (_: Exception) { emptyList() }
    }

    /** Tras guardar: lo aprendido se vuelve a pedir (la observación de hoy ya cuenta). */
    fun olvidarSugerencias() { cacheSugerencias.clear() }

    /**
     * 💰 Las cuotas del tratamiento del plan, SOLO LECTURA (gemelo de
     * ResumenCuotasOrto.tsx): el cronograma (`cuotas_tratamiento`) y lo pagado
     * (`pagos_tratamiento`), las MISMAS tablas y columnas que lee la web, por la
     * RLS del staff. Las dos lecturas en paralelo. null = no se pudo saber (sin
     * la tabla en un backend viejo, sin red): la tarjeta no se muestra.
     */
    suspend fun cuotasTratamiento(tratamientoId: String): CuotasTratamientoOrto? = try {
        withTimeoutOrNull(12_000) {
            coroutineScope {
                val cuotas = async {
                    Supabase.client.postgrest["cuotas_tratamiento"]
                        .select(Columns.list("numero, monto, vence")) {
                            filter { eq("tratamiento_id", tratamientoId) }
                            order("numero", Order.ASCENDING)
                        }.decodeList<JsonObject>()
                }
                val pagos = async {
                    Supabase.client.postgrest["pagos_tratamiento"]
                        .select(Columns.list("monto")) { filter { eq("tratamiento_id", tratamientoId) } }
                        .decodeList<JsonObject>()
                }
                cuotasOrtoDeFilas(cuotas.await(), pagos.await())
            }
        }
    } catch (e: CancellationException) { throw e } catch (_: Exception) { null }

    /**
     * ¿El paciente ya tiene un plan de ortodoncia? (lectura liviana por la RLS).
     * null = no se pudo saber (sin la tabla, sin red): quien llama decide "no".
     */
    suspend fun tienePlan(pacienteId: String): Boolean? = try {
        Supabase.client.postgrest["ortodoncia_planes"]
            .select(Columns.list("id")) { filter { eq("paciente_id", pacienteId) }; limit(1) }
            .decodeList<JsonObject>().isNotEmpty()
    } catch (e: CancellationException) { throw e } catch (_: Exception) { null }

    /**
     * Las citas de ORTODONCIA del paciente (para vincular el control y el próximo
     * control agendado): dentales (la regla de la 🦷) y de un servicio o especialidad
     * de ortodoncia. [nombresServicio] = procedimientoId → nombre (los de sus
     * tratamientos), para las citas sin tratamiento; los que falten se leen del
     * catálogo (una lectura chica). Sin red → vacía (el control se guarda "Sin cita").
     */
    suspend fun citasOrtodoncia(
        pacienteId: String,
        mapaDental: MapaDental,
        mapaOrto: MapaOrtodoncia,
        nombresServicio: Map<String, String>,
    ): List<CitaOrto> {
        fun JsonObject.s(k: String): String? = (this[k] as? JsonPrimitive)?.takeIf { it !is JsonNull }?.content?.takeIf { it.isNotBlank() && it != "null" }
        val filas = try {
            Supabase.client.postgrest["citas"]
                .select(Columns.raw("id, fecha, hora, estado, tipo, especialidad_id, tratamiento_id, terapeuta_id, procedimiento_id, tratamiento:tratamientos(procedimiento:procedimientos(nombre, especialidad_id))")) {
                    filter { eq("paciente_id", pacienteId) }
                    order("fecha", Order.DESCENDING)
                    limit(200)
                }
                .decodeList<JsonObject>()
        } catch (e: CancellationException) { throw e } catch (_: Exception) { return emptyList() }
        // Citas sueltas con un servicio que no está entre sus tratamientos: su nombre, de una.
        val faltan = filas.filter { o ->
            ((o["tratamiento"] as? JsonObject)?.get("procedimiento") as? JsonObject)?.s("nombre") == null &&
                o.s("procedimiento_id")?.let { it !in nombresServicio } == true
        }.mapNotNull { it.s("procedimiento_id") }.distinct()
        val extra: Map<String, String> = if (faltan.isEmpty()) emptyMap() else try {
            Supabase.client.postgrest["procedimientos"]
                .select(Columns.list("id, nombre")) { filter { isIn("id", faltan) } }
                .decodeList<JsonObject>()
                .mapNotNull { o -> o.s("id")?.let { id -> o.s("nombre")?.let { id to it } } }.toMap()
        } catch (e: CancellationException) { throw e } catch (_: Exception) { emptyMap() }
        val nombres = nombresServicio + extra
        return filas.mapNotNull { o ->
            val id = o.s("id") ?: return@mapNotNull null
            val proc = (o["tratamiento"] as? JsonObject)?.get("procedimiento") as? JsonObject
            val espServicio = proc?.s("especialidad_id")
            val servicio = proc?.s("nombre") ?: o.s("procedimiento_id")?.let { nombres[it] }
            if (!citaEsDental(mapaDental, o.s("especialidad_id"), espServicio)) return@mapNotNull null
            if (!esAtencionOrtodoncia(mapaOrto, servicio, o.s("especialidad_id"), espServicio)) return@mapNotNull null
            CitaOrto(
                id = id, fecha = o.s("fecha").orEmpty().take(10), hora = o.s("hora")?.take(5), estado = o.s("estado").orEmpty(),
                tipo = o.s("tipo"), servicio = servicio, tratamientoId = o.s("tratamiento_id"), terapeutaId = o.s("terapeuta_id"),
            )
        }
    }
}
