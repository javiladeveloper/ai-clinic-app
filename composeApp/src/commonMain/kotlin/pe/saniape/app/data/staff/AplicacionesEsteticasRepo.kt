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

/** Lectura de mapas: [items] o, si no se pudo, [error] para mostrar. */
data class LecturaAplicaciones(val items: List<AplicacionEstetica>? = null, val error: String? = null)

/**
 * 💉 Mapa de aplicación por /api/staff/aplicacion-estetica, con el Bearer del
 * staff. Mismo molde que [IndicacionesRepo]: el servidor valida permiso, el
 * candado de solo lectura y los datos, calcula los totales y la vista, y
 * admite UN mapa por cita (POST con una cita que ya tiene el suyo → 409
 * YA_EXISTE con el `id` del existente, para abrirlo). DIRECTO, sin cola offline.
 */
object AplicacionesEsteticasRepo {

    private const val RUTA = "/api/staff/aplicacion-estetica"
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
            return ResultadoEscritura(registrada = false, rechazo = RechazoServidor(APLICACIONES_NO_DISPONIBLE, "NO_DISPONIBLE", 404))
        }
        return r
    }

    /** Los del paciente, el más reciente primero. */
    suspend fun listar(pacienteId: String): LecturaAplicaciones {
        val tk = token() ?: return LecturaAplicaciones(error = "Tu sesión expiró. Vuelve a entrar.")
        return try {
            val resp = http.get("${Supabase.SITE_URL}$RUTA") {
                header("Authorization", "Bearer $tk")
                parameter("pacienteId", pacienteId)
            }
            val texto = runCatching { resp.bodyAsText() }.getOrNull()
            val o = texto?.let { runCatching { json.parseToJsonElement(it).jsonObject }.getOrNull() }
            when {
                resp.status == HttpStatusCode.OK && o != null ->
                    LecturaAplicaciones(items = ordenarAplicaciones((o["items"] as? JsonArray).orEmpty()
                        .mapNotNull { (it as? JsonObject)?.let(::aplicacionEsteticaDeJson) }))
                resp.status == HttpStatusCode.NotFound && o?.get("codigo") == null -> LecturaAplicaciones(error = APLICACIONES_NO_DISPONIBLE)
                else -> LecturaAplicaciones(error = (o?.get("error") as? JsonPrimitive)?.content
                    ?: "No se pudieron cargar las aplicaciones")
            }
        } catch (e: CancellationException) { throw e } catch (_: Exception) {
            LecturaAplicaciones(error = "Sin conexión. Revisa tu internet.")
        }
    }

    /**
     * POST (nuevo, sin [id]) o PATCH (editar, con [id]). Borrador YA validado.
     * En `cuerpo["aplicacion"]` vuelve la fila; con 409 YA_EXISTE, `cuerpo["id"]`
     * es el mapa que esa cita ya tiene.
     */
    suspend fun guardar(b: BorradorAplicacion, id: String? = null): ResultadoEscritura {
        val tk = token() ?: return SIN_SESION
        val cuerpo = cuerpoAplicacion(b, id).toString()
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

    suspend fun borrar(id: String): ResultadoEscritura {
        val tk = token() ?: return SIN_SESION
        return try {
            pe.saniape.app.ui.conIndicador {
                val resp = http.delete("${Supabase.SITE_URL}$RUTA") {
                    header("Authorization", "Bearer $tk")
                    contentType(ContentType.Application.Json)
                    setBody(buildJsonObject { put("id", id) }.toString())
                }
                resultado(resp)
            }
        } catch (e: CancellationException) { throw e } catch (_: Exception) { SIN_RED }
    }

    /** Caché de la sesión por clínica: los productos cambian poco. */
    private val cacheRecientes = mutableMapOf<String, List<ProductoReciente>>()

    /** Productos de la clínica (GET ?recientes=1). Si falla, vacío: el editor sigue con los comunes. */
    suspend fun recientes(clinicaId: String?): List<ProductoReciente> {
        val clinica = clinicaId?.takeIf { it.isNotBlank() }
        if (clinica != null) cacheRecientes[clinica]?.let { return it }
        val tk = token() ?: return emptyList()
        return try {
            val resp = http.get("${Supabase.SITE_URL}$RUTA") {
                header("Authorization", "Bearer $tk")
                parameter("recientes", "1")
            }
            if (resp.status != HttpStatusCode.OK) return emptyList()
            val o = runCatching { json.parseToJsonElement(resp.bodyAsText()).jsonObject }.getOrNull()
            productosRecientesDeJson(o).also { if (clinica != null) cacheRecientes[clinica] = it }
        } catch (e: CancellationException) { throw e } catch (_: Exception) { emptyList() }
    }

    /** Tras guardar: los productos de la clínica se vuelven a pedir (el lote de hoy ya cuenta). */
    fun olvidarRecientes() { cacheRecientes.clear() }

    /** Clínica MIXTA con estética: ¿el paciente ya tiene mapas? (sin la tabla o sin red → false). */
    suspend fun tieneAplicaciones(pacienteId: String): Boolean = try {
        Supabase.client.postgrest["aplicaciones_esteticas"]
            .select(Columns.list("id")) { filter { eq("paciente_id", pacienteId) }; limit(1) }
            .decodeList<JsonObject>().isNotEmpty()
    } catch (e: CancellationException) { throw e } catch (_: Exception) { false }

    /**
     * Las citas de ESTÉTICA del paciente (para vincular el mapa): especialidad de
     * la cita → la del servicio de su tratamiento. [nombresServicio] =
     * procedimientoId → nombre (los de sus tratamientos), para las citas sin
     * tratamiento. Sin red → vacía (el mapa se guarda "Sin cita").
     */
    suspend fun citasEstetica(pacienteId: String, mapa: MapaEstetica, nombresServicio: Map<String, String>): List<CitaParaMapa> {
        fun JsonObject.s(k: String): String? = (this[k] as? JsonPrimitive)?.takeIf { it !is JsonNull }?.content?.takeIf { it.isNotBlank() && it != "null" }
        val filas = try {
            Supabase.client.postgrest["citas"]
                .select(Columns.raw("id, fecha, hora, estado, especialidad_id, tratamiento_id, terapeuta_id, procedimiento_id, tratamiento:tratamientos(procedimiento:procedimientos(nombre, especialidad_id))")) {
                    filter { eq("paciente_id", pacienteId) }
                    order("fecha", Order.DESCENDING)
                    limit(200)
                }
                .decodeList<JsonObject>()
        } catch (e: CancellationException) { throw e } catch (_: Exception) { return emptyList() }
        return filas.mapNotNull { o ->
            val id = o.s("id") ?: return@mapNotNull null
            val proc = (o["tratamiento"] as? JsonObject)?.get("procedimiento") as? JsonObject
            if (!citaEsEstetica(mapa, o.s("especialidad_id"), proc?.s("especialidad_id"))) return@mapNotNull null
            CitaParaMapa(
                id = id, fecha = o.s("fecha").orEmpty().take(10), hora = o.s("hora")?.take(5), estado = o.s("estado").orEmpty(),
                servicio = proc?.s("nombre") ?: o.s("procedimiento_id")?.let { nombresServicio[it] },
                tratamientoId = o.s("tratamiento_id"), terapeutaId = o.s("terapeuta_id"),
            )
        }
    }
}
