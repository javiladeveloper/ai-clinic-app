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

/** Un servidor sin /api/staff/vacunas todavía (404 sin el JSON del API). */
const val VACUNAS_NO_DISPONIBLE = "El carné de vacunas todavía no está disponible. Inténtalo más tarde."

/** Lectura del carné: [items] o, si no se pudo, [error] para mostrar. */
data class LecturaVacunas(val items: List<VacunaPaciente>? = null, val error: String? = null)

/**
 * 👶 Pediatría (staff) con el Bearer: el carné por /api/staff/vacunas
 * (GET/POST/PATCH/DELETE, contrato arriba de la ruta en la web) y los datos de
 * la ficha que las curvas necesitan (sexo y fecha de nacimiento) por
 * /api/staff/paciente/actualizar — los mismos endpoints que la web. El
 * servidor valida permiso, candado de solo lectura, sede y datos. DIRECTO, sin
 * cola offline: la pestaña necesita la fila guardada para mostrarla.
 */
object PediatriaRepo {

    private const val RUTA_VACUNAS = "/api/staff/vacunas"
    private const val RUTA_ACTUALIZAR = "/api/staff/paciente/actualizar"
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
            return ResultadoEscritura(registrada = false, rechazo = RechazoServidor(VACUNAS_NO_DISPONIBLE, "NO_DISPONIBLE", 404))
        }
        return r
    }

    /** Las dosis del paciente (el carné las ordena por su cuenta). */
    suspend fun vacunas(pacienteId: String): LecturaVacunas {
        val tk = token() ?: return LecturaVacunas(error = "Tu sesión expiró. Vuelve a entrar.")
        return try {
            val resp = http.get("${Supabase.SITE_URL}$RUTA_VACUNAS") {
                header("Authorization", "Bearer $tk")
                parameter("pacienteId", pacienteId)
            }
            val texto = runCatching { resp.bodyAsText() }.getOrNull()
            val o = texto?.let { runCatching { json.parseToJsonElement(it).jsonObject }.getOrNull() }
            when {
                resp.status == HttpStatusCode.OK && o != null ->
                    LecturaVacunas(items = (o["items"] as? JsonArray).orEmpty().mapNotNull { (it as? JsonObject)?.let(::vacunaDeJson) })
                resp.status == HttpStatusCode.NotFound && o?.get("codigo") == null -> LecturaVacunas(error = VACUNAS_NO_DISPONIBLE)
                else -> LecturaVacunas(error = (o?.get("error") as? JsonPrimitive)?.content ?: "No se pudo cargar el carné de vacunas")
            }
        } catch (e: CancellationException) { throw e } catch (_: Exception) {
            LecturaVacunas(error = "Sin conexión. Revisa tu internet.")
        }
    }

    /** POST (nueva, sin [id]) o PATCH (editar, con [id]). Borrador YA validado. En `cuerpo["vacuna"]` vuelve la fila. */
    suspend fun guardarVacuna(b: BorradorVacuna, id: String? = null): ResultadoEscritura {
        val tk = token() ?: return SIN_SESION
        val cuerpo = cuerpoVacuna(b, id).toString()
        return try {
            pe.saniape.app.ui.conIndicador {
                val resp = if (id == null) {
                    http.post("${Supabase.SITE_URL}$RUTA_VACUNAS") {
                        header("Authorization", "Bearer $tk"); contentType(ContentType.Application.Json); setBody(cuerpo)
                    }
                } else {
                    http.patch("${Supabase.SITE_URL}$RUTA_VACUNAS") {
                        header("Authorization", "Bearer $tk"); contentType(ContentType.Application.Json); setBody(cuerpo)
                    }
                }
                resultado(resp)
            }
        } catch (e: CancellationException) { throw e } catch (_: Exception) { SIN_RED }
    }

    suspend fun borrarVacuna(id: String): ResultadoEscritura {
        val tk = token() ?: return SIN_SESION
        return try {
            pe.saniape.app.ui.conIndicador {
                val resp = http.delete("${Supabase.SITE_URL}$RUTA_VACUNAS") {
                    header("Authorization", "Bearer $tk")
                    contentType(ContentType.Application.Json)
                    setBody(buildJsonObject { put("id", id) }.toString())
                }
                resultado(resp)
            }
        } catch (e: CancellationException) { throw e } catch (_: Exception) { SIN_RED }
    }

    /**
     * Guarda el sexo ("F" / "M" / null = sin dato) o la fecha de nacimiento de la
     * ficha por /api/staff/paciente/actualizar (como el aviso de la web). Un
     * servidor que aún no conoce la clave responde `sinCambios`: se avisa que no
     * quedó guardado (en vez de fingir que sí).
     */
    suspend fun actualizarFicha(pacienteId: String, sexo: String? = null, tocarSexo: Boolean = false, fechaNacimiento: String? = null): ResultadoEscritura {
        val tk = token() ?: return SIN_SESION
        val cuerpo = buildJsonObject {
            put("pacienteId", pacienteId)
            if (tocarSexo) put("sexo", sexoValido(sexo)?.let { JsonPrimitive(it) } ?: kotlinx.serialization.json.JsonNull)
            fechaNacimiento?.let { put("fechaNacimiento", it) }
        }.toString()
        return try {
            pe.saniape.app.ui.conIndicador {
                val resp = http.post("${Supabase.SITE_URL}$RUTA_ACTUALIZAR") {
                    header("Authorization", "Bearer $tk"); contentType(ContentType.Application.Json); setBody(cuerpo)
                }
                val r = AtencionRepo.resultadoDeRespuesta(resp.status.value, runCatching { resp.bodyAsText() }.getOrNull())
                if (r.registrada && (r.cuerpo?.get("sinCambios") as? JsonPrimitive)?.content == "true") {
                    ResultadoEscritura(registrada = false, rechazo = RechazoServidor(
                        "El servidor todavía no guarda este dato. Inténtalo más tarde.", "NO_DISPONIBLE", 200,
                    ))
                } else r
            }
        } catch (e: CancellationException) { throw e } catch (_: Exception) { SIN_RED }
    }

    /**
     * El sexo de la ficha ("F" / "M" / null). Lectura aparte y a prueba de fallos
     * (una base sin la columna devuelve null): la ficha no depende de ella.
     */
    suspend fun sexoDe(pacienteId: String): String? = try {
        Supabase.client.postgrest["pacientes"]
            .select(Columns.raw("sexo")) { filter { eq("id", pacienteId) } }
            .decodeList<JsonObject>().firstOrNull()
            ?.let { sexoValido((it["sexo"] as? JsonPrimitive)?.content) }
    } catch (e: CancellationException) { throw e } catch (_: Exception) { null }

    /**
     * Clínica MIXTA con pediatría: ¿el paciente ya tiene vacunas registradas?
     * Una lectura chica; sin la tabla (backend viejo) o sin red → false.
     */
    suspend fun tieneVacunas(pacienteId: String): Boolean = try {
        Supabase.client.postgrest["vacunas_paciente"]
            .select(Columns.list("id")) { filter { eq("paciente_id", pacienteId) }; limit(1) }
            .decodeList<JsonObject>().isNotEmpty()
    } catch (e: CancellationException) { throw e } catch (_: Exception) { false }
}
