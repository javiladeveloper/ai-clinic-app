package pe.saniape.app.data.staff

import io.github.jan.supabase.auth.auth
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.contentType
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import pe.saniape.app.data.Supabase
import pe.saniape.app.data.crearHttpClient

/**
 * Fichas SIN documento con el mismo nombre (17/11/2026) — gemelo de
 * `lib/fichas-sin-documento.ts` + `app/api/staff/paciente/sin-documento` (web).
 *
 * El Dr. Rodriguez Moya importó 10 mil pacientes de su sistema anterior, solo
 * con el nombre. Cuando uno vuelve y recepción lo registra POR DNI nacía otra
 * ficha y la historia quedaba partida en dos. Antes de crear por documento se
 * pregunta «¿Es esta persona?»: si lo es, se le pone el documento a esa ficha.
 * El servidor decide qué es «el mismo nombre» (mismas palabras, cualquier orden,
 * sin tildes); aquí solo se leen las respuestas.
 */
data class FichaSinDocumento(
    val id: String,
    val nombre: String,
    val fechaNacimiento: String?,
    val edad: Int?,
    /** Atenciones (citas) que ya tiene: lo que quedaría partido con una ficha nueva. */
    val citas: Int,
)

/** La línea de datos de una ficha: «nació el 1988-01-27 · 7 atenciones». */
fun datosFichaSinDocumento(f: FichaSinDocumento): String = listOfNotNull(
    f.fechaNacimiento?.let { "nació el ${fechaCortaIso(it)}" } ?: f.edad?.let { "$it años" },
    if (f.citas > 0) "${f.citas} ${if (f.citas == 1) "atención" else "atenciones"}" else "sin atenciones",
).joinToString(" · ")

/** «1988-01-27» → «27/01/1988» (si no tiene esa forma, tal cual). */
private fun fechaCortaIso(iso: String): String {
    val m = Regex("^(\\d{4})-(\\d{2})-(\\d{2})").find(iso) ?: return iso
    val (a, mes, d) = m.destructured
    return "$d/$mes/$a"
}

private fun JsonObject.txt(k: String): String? =
    (this[k] as? JsonPrimitive)?.content?.takeIf { it != "null" && it.isNotBlank() }

/** GET /api/staff/paciente/sin-documento → fichas. Cualquier error = ninguna (no frena el alta). */
fun interpretarFichasSinDocumento(status: Int, cuerpo: String): List<FichaSinDocumento> {
    if (status !in 200..299) return emptyList()
    val o = runCatching { Json.parseToJsonElement(cuerpo) as? JsonObject }.getOrNull() ?: return emptyList()
    val lista = o["resultados"] as? JsonArray ?: return emptyList()
    return lista.mapNotNull { e ->
        val f = e as? JsonObject ?: return@mapNotNull null
        val id = f.txt("id") ?: return@mapNotNull null
        FichaSinDocumento(
            id = id,
            nombre = f.txt("nombre") ?: return@mapNotNull null,
            fechaNacimiento = f.txt("fecha_nacimiento"),
            edad = f.txt("edad")?.toIntOrNull(),
            citas = f.txt("citas")?.toIntOrNull() ?: 0,
        )
    }
}

/** Resultado de ponerle el documento a una ficha: el id elegido, o el error. */
data class ResultadoAsignarDocumento(val id: String?, val error: String? = null) {
    val ok: Boolean get() = id != null
}

/**
 * POST /api/staff/paciente/sin-documento → id. Si otra ficha ya tiene ese
 * documento (409 DOCUMENTO_EN_USO), es ESA la que se elige.
 */
fun interpretarAsignarDocumento(status: Int, cuerpo: String): ResultadoAsignarDocumento {
    val o = runCatching { Json.parseToJsonElement(cuerpo) as? JsonObject }.getOrNull()
    val id = o?.txt("id")
    if (status in 200..299 && id != null) return ResultadoAsignarDocumento(id)
    if (o?.txt("codigo") == "DOCUMENTO_EN_USO" && id != null) return ResultadoAsignarDocumento(id)
    return ResultadoAsignarDocumento(null, o?.txt("error") ?: "No se pudo guardar el documento.")
}

object FichasSinDocumentoRepo {
    private val http = crearHttpClient()
    private suspend fun token(): String? = Supabase.client.auth.currentSessionOrNull()?.accessToken

    /** Fichas SIN documento con el mismo nombre. Sin señal o error: ninguna. */
    suspend fun buscar(nombre: String): List<FichaSinDocumento> = try {
        val tk = token()
        if (tk == null || nombre.isBlank()) emptyList()
        else {
            val resp = http.get("${Supabase.SITE_URL}/api/staff/paciente/sin-documento") {
                header("Authorization", "Bearer $tk")
                parameter("nombre", nombre.trim())
            }
            interpretarFichasSinDocumento(resp.status.value, resp.bodyAsText())
        }
    } catch (e: kotlinx.coroutines.CancellationException) {
        throw e
    } catch (_: Exception) {
        emptyList()
    }

    /** «Es esta»: le pone el documento (y el teléfono, si no tenía) a esa ficha. */
    suspend fun asignar(pacienteId: String, documento: String, tipoDocumento: String? = null, telefono: String? = null): ResultadoAsignarDocumento = try {
        val tk = token() ?: return ResultadoAsignarDocumento(null, "Sesión vencida. Vuelve a entrar.")
        val cuerpo = buildJsonObject {
            put("pacienteId", pacienteId)
            put("dni", documento.trim())
            normalizarTipoDocumento(tipoDocumento)?.let { put("tipo_documento", it) }
            telefonoDeAlta(telefono)?.let { put("telefono", it) }
        }
        val resp = http.post("${Supabase.SITE_URL}/api/staff/paciente/sin-documento") {
            header("Authorization", "Bearer $tk")
            contentType(ContentType.Application.Json)
            setBody(cuerpo.toString())
        }
        interpretarAsignarDocumento(resp.status.value, resp.bodyAsText())
    } catch (e: kotlinx.coroutines.CancellationException) {
        throw e
    } catch (_: Exception) {
        ResultadoAsignarDocumento(null, "No se pudo guardar el documento. Revisa tu conexión.")
    }
}
