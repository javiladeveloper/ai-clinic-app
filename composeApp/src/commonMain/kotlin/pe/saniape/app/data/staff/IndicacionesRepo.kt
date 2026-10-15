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

/** Lectura de hojas: [items] o, si no se pudo, [error] para mostrar. */
data class LecturaIndicaciones(val items: List<IndicacionPaciente>? = null, val error: String? = null)

/**
 * 📝 Indicaciones para casa por /api/staff/indicaciones (+ /plantillas,
 * /sugerencias, /catalogo), con el Bearer del staff. Mismo molde que
 * [PlanAlimentacionRepo]: el servidor valida permiso ('pacientes' o
 * 'sesiones'), el candado de solo lectura y los datos (`validarIndicacion`, el
 * mismo que aquí). DIRECTO, sin cola offline: la pestaña necesita la fila
 * guardada para mostrarla, imprimirla y mandarla.
 *
 * Las plantillas y chips BASE vienen SOLO de /catalogo (la única copia de esos
 * textos es la web): caché por clínica para la sesión.
 */
object IndicacionesRepo {

    private const val RUTA = "/api/staff/indicaciones"
    private const val RUTA_PLANTILLAS = "/api/staff/indicaciones/plantillas"
    private const val RUTA_SUGERENCIAS = "/api/staff/indicaciones/sugerencias"
    private const val RUTA_CATALOGO = "/api/staff/indicaciones/catalogo"
    private val http = crearHttpClient()
    private val json = Json { ignoreUnknownKeys = true }

    private fun token(): String? = Supabase.client.auth.currentSessionOrNull()?.accessToken

    private val SIN_SESION = ResultadoEscritura(
        registrada = false, rechazo = RechazoServidor("Tu sesión expiró. Vuelve a entrar.", "NO_AUTENTICADO", 401),
    )
    private val SIN_RED = ResultadoEscritura(
        registrada = false, rechazo = RechazoServidor("Sin conexión. Revisa tu internet.", "SIN_RED"),
    )

    /** Respuesta → resultado; un 404 SIN el JSON del API es un servidor que aún no tiene la ruta. */
    private suspend fun resultado(resp: HttpResponse): ResultadoEscritura {
        val cuerpo = runCatching { resp.bodyAsText() }.getOrNull()
        val r = AtencionRepo.resultadoDeRespuesta(resp.status.value, cuerpo)
        if (resp.status == HttpStatusCode.NotFound && r.rechazo?.codigo == null) {
            return ResultadoEscritura(registrada = false, rechazo = RechazoServidor(INDICACIONES_NO_DISPONIBLE, "NO_DISPONIBLE", 404))
        }
        return r
    }

    /** Las del paciente, la más reciente primero. */
    suspend fun listar(pacienteId: String): LecturaIndicaciones {
        val tk = token() ?: return LecturaIndicaciones(error = "Tu sesión expiró. Vuelve a entrar.")
        return try {
            val resp = http.get("${Supabase.SITE_URL}$RUTA") {
                header("Authorization", "Bearer $tk")
                parameter("pacienteId", pacienteId)
            }
            val texto = runCatching { resp.bodyAsText() }.getOrNull()
            val o = texto?.let { runCatching { json.parseToJsonElement(it).jsonObject }.getOrNull() }
            when {
                resp.status == HttpStatusCode.OK && o != null ->
                    LecturaIndicaciones(items = ordenarIndicaciones((o["items"] as? JsonArray).orEmpty()
                        .mapNotNull { (it as? JsonObject)?.let(::indicacionDeJson) }))
                resp.status == HttpStatusCode.NotFound && o?.get("codigo") == null -> LecturaIndicaciones(error = INDICACIONES_NO_DISPONIBLE)
                else -> LecturaIndicaciones(error = (o?.get("error") as? JsonPrimitive)?.content
                    ?: "No se pudieron cargar las indicaciones")
            }
        } catch (e: CancellationException) { throw e } catch (_: Exception) {
            LecturaIndicaciones(error = "Sin conexión. Revisa tu internet.")
        }
    }

    /** POST (nueva, sin [id]) o PATCH (editar, con [id]). Borrador YA validado. En `cuerpo["indicacion"]` vuelve la fila. */
    suspend fun guardar(b: BorradorIndicacion, id: String? = null): ResultadoEscritura {
        val tk = token() ?: return SIN_SESION
        val cuerpo = cuerpoIndicacion(b, id).toString()
        return try {
            pe.saniape.app.ui.conIndicador {
                val resp = if (id == null) {
                    http.post("${Supabase.SITE_URL}$RUTA") {
                        header("Authorization", "Bearer $tk"); contentType(ContentType.Application.Json); setBody(cuerpo)
                    }
                } else {
                    http.patch("${Supabase.SITE_URL}$RUTA") {
                        header("Authorization", "Bearer $tk"); contentType(ContentType.Application.Json); setBody(cuerpo)
                    }
                }
                resultado(resp)
            }
        } catch (e: CancellationException) { throw e } catch (_: Exception) { SIN_RED }
    }

    suspend fun borrar(id: String): ResultadoEscritura = borrarEn(RUTA, id)

    // ── Plantillas de la clínica ──

    /** Las de la clínica, por nombre. Si no se pudo leer (o el servidor no las tiene), lista vacía. */
    suspend fun plantillas(): List<PlantillaIndicaciones> {
        val tk = token() ?: return emptyList()
        return try {
            val resp = http.get("${Supabase.SITE_URL}$RUTA_PLANTILLAS") { header("Authorization", "Bearer $tk") }
            if (resp.status != HttpStatusCode.OK) return emptyList()
            val o = json.parseToJsonElement(resp.bodyAsText()).jsonObject
            (o["items"] as? JsonArray).orEmpty().mapNotNull { (it as? JsonObject)?.let(::plantillaIndicacionesDeJson) }
        } catch (e: CancellationException) { throw e } catch (_: Exception) { emptyList() }
    }

    /** Guarda (o reemplaza, si ya hay una con ese nombre). `cuerpo["actualizada"]` dice cuál. */
    suspend fun guardarPlantilla(nombre: String, i: ContenidoIndicacionesFuente, especialidadId: String?): ResultadoEscritura {
        val tk = token() ?: return SIN_SESION
        return try {
            pe.saniape.app.ui.conIndicador {
                val resp = http.post("${Supabase.SITE_URL}$RUTA_PLANTILLAS") {
                    header("Authorization", "Bearer $tk")
                    contentType(ContentType.Application.Json)
                    setBody(cuerpoPlantillaIndicaciones(nombre, i, especialidadId).toString())
                }
                resultado(resp)
            }
        } catch (e: CancellationException) { throw e } catch (_: Exception) { SIN_RED }
    }

    suspend fun borrarPlantilla(id: String): ResultadoEscritura = borrarEn(RUTA_PLANTILLAS, id)

    private suspend fun borrarEn(ruta: String, id: String): ResultadoEscritura {
        val tk = token() ?: return SIN_SESION
        return try {
            pe.saniape.app.ui.conIndicador {
                val resp = http.delete("${Supabase.SITE_URL}$ruta") {
                    header("Authorization", "Bearer $tk")
                    contentType(ContentType.Application.Json)
                    setBody(buildJsonObject { put("id", id) }.toString())
                }
                resultado(resp)
            }
        } catch (e: CancellationException) { throw e } catch (_: Exception) { SIN_RED }
    }

    // ── Autocompletado que aprende ──

    /** Caché de la sesión por (clínica, especialidad): UN viaje al abrir el editor; al escribir se filtra en memoria. */
    private val cacheSugerencias = mutableMapOf<String, SugerenciasIndicaciones>()

    /**
     * Lo que la clínica ya escribió en sus hojas (más usado primero). Si falla o
     * el servidor aún no tiene la ruta, vacío: el editor sigue con los chips base.
     */
    suspend fun sugerencias(clinicaId: String?, especialidadId: String?): SugerenciasIndicaciones {
        val clinica = clinicaId?.takeIf { it.isNotBlank() }
        val k = "$clinica|${especialidadId.orEmpty()}"
        if (clinica != null) cacheSugerencias[k]?.let { return it }
        val tk = token() ?: return SugerenciasIndicaciones()
        return try {
            val resp = http.get("${Supabase.SITE_URL}$RUTA_SUGERENCIAS") {
                header("Authorization", "Bearer $tk")
                especialidadId?.takeIf { it.isNotBlank() }?.let { parameter("especialidadId", it) }
            }
            if (resp.status != HttpStatusCode.OK) return SugerenciasIndicaciones()
            val o = runCatching { json.parseToJsonElement(resp.bodyAsText()).jsonObject }.getOrNull() ?: return SugerenciasIndicaciones()
            sugerenciasIndicacionesDeJson(o).also { if (clinica != null) cacheSugerencias[k] = it }
        } catch (e: CancellationException) { throw e } catch (_: Exception) { SugerenciasIndicaciones() }
    }

    /** Tras guardar una hoja: la próxima vez se vuelve a pedir (ya aprendió lo nuevo). */
    fun olvidarSugerencias() { cacheSugerencias.clear() }

    // ── Catálogo base (plantillas y chips por especialidad) ──

    /** Caché de la sesión por (clínica, especialidad, servicio): cambia solo con un deploy de la web. */
    private val cacheCatalogo = mutableMapOf<String, CatalogoIndicaciones>()

    /**
     * GET /catalogo. Con [especialidadId]: su rubro; sin él, la unión de las
     * especialidades activas de la clínica. [servicio]: el nombre del servicio
     * de la cita (para `sugerida`). null = sin red, sin la ruta o error: quien
     * llama sigue sin sugerencias (el aviso de la agenda simplemente no sale).
     */
    suspend fun catalogo(clinicaId: String?, especialidadId: String? = null, servicio: String? = null): CatalogoIndicaciones? {
        val clinica = clinicaId?.takeIf { it.isNotBlank() }
        val esp = especialidadId?.takeIf { it.isNotBlank() }
        val serv = servicio?.trim()?.takeIf { it.isNotEmpty() }?.take(200)
        val k = "$clinica|${esp.orEmpty()}|${serv.orEmpty()}"
        if (clinica != null) cacheCatalogo[k]?.let { return it }
        val tk = token() ?: return null
        return try {
            val resp = withTimeoutOrNull(12_000) {
                http.get("${Supabase.SITE_URL}$RUTA_CATALOGO") {
                    header("Authorization", "Bearer $tk")
                    esp?.let { parameter("especialidadId", it) }
                    serv?.let { parameter("servicio", it) }
                }
            } ?: return null
            if (resp.status != HttpStatusCode.OK) return null
            val o = runCatching { json.parseToJsonElement(resp.bodyAsText()).jsonObject }.getOrNull()
            catalogoIndicacionesDeJson(o)?.also { if (clinica != null) cacheCatalogo[k] = it }
        } catch (e: CancellationException) { throw e } catch (_: Exception) { null }
    }

    /**
     * El catálogo para una atención, como la web: el rubro de su especialidad
     * y, si esa especialidad no tiene rubro reconocido (o no hay), la unión de
     * la clínica.
     */
    suspend fun catalogoParaAtencion(clinicaId: String?, especialidadId: String?, servicio: String?): CatalogoIndicaciones? {
        if (especialidadId.isNullOrBlank()) return catalogo(clinicaId, null, servicio)
        val c = catalogo(clinicaId, especialidadId, servicio) ?: return null
        return if (c.rubro == null) catalogo(clinicaId, null, servicio) ?: c else c
    }

    /**
     * El servicio de una cita sin tratamiento visible (gemelo de /citas web: el
     * `procedimiento_id` de la propia cita): nombre y especialidad. null = no se pudo.
     */
    suspend fun servicioDeProcedimiento(procedimientoId: String): Pair<String, String?>? = try {
        Supabase.client.postgrest["procedimientos"]
            .select(Columns.list("nombre, especialidad_id")) { filter { eq("id", procedimientoId) } }
            .decodeList<JsonObject>()
            .firstOrNull()
            ?.let { o ->
                fun s(k: String) = (o[k] as? JsonPrimitive)?.takeIf { it !is JsonNull }?.content?.takeIf { it.isNotBlank() && it != "null" }
                s("nombre")?.let { it to s("especialidad_id") }
            }
    } catch (e: CancellationException) { throw e } catch (_: Exception) { null }
}
