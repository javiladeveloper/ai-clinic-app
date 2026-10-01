package pe.saniape.app.data.staff

import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.Columns
import io.github.jan.supabase.postgrest.query.Order
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.contentType
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.encodeToJsonElement
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.put
import pe.saniape.app.data.Supabase
import pe.saniape.app.data.crearHttpClient
import pe.saniape.app.data.offline.RechazoServidor
import pe.saniape.app.data.offline.ResultadoEscritura

/** Una especialidad de la clínica (fila de `especialidades`). */
data class EspecialidadApp(
    val id: String,
    val nombre: String,
    val rubro: String?,
    val estado: String,
    val color: String?,
    val icono: String?,
)

/** Servicio que el servidor sugiere cargar (gemelo de `ServicioInicial` en lib/catalogo-inicial.ts). */
@Serializable
data class ServicioSugerido(
    val nombre: String,
    val categoria: String = "General",
    val descripcion: String = "",
    val precio: Double = 0.0,
    val modo_cobro: String? = null,
    val unidad_label: String? = null,
    val precio_unitario_sugerido: Double? = null,
    val especialidad: String? = null,
)

@Serializable
data class TipoImagenSugerido(val nombre: String, val contexto: String, val orden: Int = 0)

/** `GET /api/staff/especialidad/sugerencias`: lo que el asistente ofrece cargar. */
@Serializable
data class SugerenciasEspecialidad(
    val servicios: List<ServicioSugerido> = emptyList(),
    val tiposImagen: List<TipoImagenSugerido> = emptyList(),
    val tipicos: Int = 0,
)

/**
 * Especialidades de la clínica y su asistente de carga inicial.
 * Lista y alta van por REST directo (RLS por clínica, sin `clinica_id`); las sugerencias
 * y la siembra viven en el servidor (`/api/staff/especialidad/...`): la app no decide reglas.
 */
object EspecialidadesRepo {

    private val json = Json { ignoreUnknownKeys = true; coerceInputValues = true; encodeDefaults = true }
    private val http = crearHttpClient()

    // Defaults del alta, gemelos de `defaultForm` en app/(app)/especialidades/page.tsx.
    private const val COLOR_DEFECTO = "#2c3e7a"
    private const val ICONO_DEFECTO = "🏥"

    private const val COLUMNAS = "id, nombre, rubro, estado, color, icono"

    private suspend fun token(): String? = Supabase.client.auth.currentSessionOrNull()?.accessToken

    private val sinSesion = ResultadoEscritura(
        registrada = false,
        rechazo = RechazoServidor("Tu sesión expiró. Vuelve a entrar.", "NO_AUTENTICADO", 401),
    )
    private val sinRed = ResultadoEscritura(
        registrada = false,
        rechazo = RechazoServidor("Sin conexión. Revisa tu internet.", "SIN_RED"),
    )

    private fun JsonObject.str(k: String): String? = (this[k] as? JsonPrimitive)?.contentOrNull

    private fun aEspecialidad(o: JsonObject): EspecialidadApp? {
        val id = o.str("id") ?: return null
        return EspecialidadApp(
            id = id,
            nombre = o.str("nombre") ?: "Especialidad",
            rubro = o.str("rubro"),
            estado = o.str("estado") ?: "Activa",
            color = o.str("color"),
            icono = o.str("icono"),
        )
    }

    suspend fun listar(): List<EspecialidadApp> =
        Supabase.client.postgrest["especialidades"]
            .select(Columns.list(COLUMNAS)) {
                order("nombre", Order.ASCENDING)
            }
            .decodeList<JsonObject>()
            .mapNotNull(::aEspecialidad)

    /** Alta como el hook web. `rubro` null = que lo deduzca el trigger por el nombre. */
    suspend fun crear(nombre: String, rubro: String?, chips: List<String>?): Result<EspecialidadApp> =
        try {
            val fila = buildJsonObject {
                put("nombre", nombre.trim())
                put("color", COLOR_DEFECTO)
                put("icono", ICONO_DEFECTO)
                put("estado", "Activa")
                put("usa_sesiones", true)
                if (!rubro.isNullOrBlank()) put("rubro", rubro)
                // Sin lista = sigue las sugerencias del sistema (como chipsTocados=false en la web).
                if (!chips.isNullOrEmpty()) put("chips_tipos", buildJsonArray { chips.forEach { add(JsonPrimitive(it)) } })
            }
            val creada = Supabase.client.postgrest["especialidades"]
                .insert(fila) { select(Columns.list(COLUMNAS)) }
                .decodeSingle<JsonObject>()
            aEspecialidad(creada)?.let { Result.success(it) } ?: Result.failure(IllegalStateException("sin id"))
        } catch (e: kotlin.coroutines.cancellation.CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }

    /** Sugerencias o el motivo del fallo (mensaje del servidor si lo dio: 403/404...). */
    suspend fun sugerencias(id: String): Pair<SugerenciasEspecialidad?, String?> {
        val tk = token() ?: return null to "Tu sesión expiró. Vuelve a entrar."
        return try {
            val resp = http.get("${Supabase.SITE_URL}/api/staff/especialidad/sugerencias") {
                header("Authorization", "Bearer $tk")
                parameter("id", id)
            }
            val texto = resp.bodyAsText()
            if (resp.status.value in 200..299) {
                val s = parsearSugerencias(texto)
                if (s != null) s to null else null to "Respuesta inesperada del servidor."
            } else {
                null to (mensajeDeError(texto) ?: "No se pudieron traer las sugerencias.")
            }
        } catch (e: kotlin.coroutines.cancellation.CancellationException) {
            throw e
        } catch (e: Exception) {
            null to "Sin conexión. Revisa tu internet."
        }
    }

    internal fun mensajeDeError(cuerpo: String): String? =
        runCatching { (json.parseToJsonElement(cuerpo) as? JsonObject)?.str("error") }.getOrNull()?.takeIf { it.isNotBlank() }

    /** Carga lo elegido. `cuerpo` = respuesta del servidor (conteos, ver [resumenSembrado]). */
    suspend fun sembrar(
        id: String,
        servicios: List<ServicioSugerido>,
        tiposImagen: Boolean,
        tipicos: Boolean,
    ): ResultadoEscritura {
        val tk = token() ?: return sinSesion
        return try {
            val cuerpo = buildJsonObject {
                put("especialidadId", id)
                put("servicios", json.encodeToJsonElement(servicios))
                put("tiposImagen", tiposImagen)
                put("tipicos", tipicos)
            }
            val resp = http.post("${Supabase.SITE_URL}/api/staff/especialidad/sembrar") {
                header("Authorization", "Bearer $tk")
                contentType(ContentType.Application.Json)
                setBody(cuerpo.toString())
            }
            AtencionRepo.resultadoDeRespuesta(resp.status.value, runCatching { resp.bodyAsText() }.getOrNull())
        } catch (e: kotlin.coroutines.cancellation.CancellationException) {
            throw e
        } catch (e: Exception) {
            sinRed
        }
    }

    internal fun parsearSugerencias(cuerpo: String): SugerenciasEspecialidad? =
        runCatching { json.decodeFromString(SugerenciasEspecialidad.serializer(), cuerpo) }.getOrNull()

    /**
     * "Listo: 8 servicios, 4 tipos de imagen y 3 procedimientos con consentimiento".
     * Omite los ceros; todo en cero = "ya tenías todo". `tipicos` puede venir null
     * (no se pidió) o un objeto con `creados`.
     */
    internal fun resumenSembrado(cuerpo: JsonObject?): String {
        val servicios = (cuerpo?.get("servicios") as? JsonPrimitive)?.intOrNull ?: 0
        val imagenes = (cuerpo?.get("tiposImagen") as? JsonPrimitive)?.intOrNull ?: 0
        val tipicos = ((cuerpo?.get("tipicos") as? JsonObject)?.get("creados") as? JsonPrimitive)?.intOrNull ?: 0
        val partes = buildList {
            if (servicios > 0) add(if (servicios == 1) "1 servicio" else "$servicios servicios")
            if (imagenes > 0) add(if (imagenes == 1) "1 tipo de imagen" else "$imagenes tipos de imagen")
            if (tipicos > 0) add(if (tipicos == 1) "1 procedimiento con consentimiento" else "$tipicos procedimientos con consentimiento")
        }
        return when (partes.size) {
            0 -> "Listo: ya tenías todo lo sugerido, no se agregó nada nuevo"
            1 -> "Listo: ${partes[0]}"
            else -> "Listo: ${partes.dropLast(1).joinToString(", ")} y ${partes.last()}"
        }
    }
}
