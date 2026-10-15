package pe.saniape.app.data.staff

import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.Columns
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.contentType
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import pe.saniape.app.data.Supabase
import pe.saniape.app.data.crearHttpClient
import pe.saniape.app.data.offline.RechazoServidor
import pe.saniape.app.data.offline.ResultadoEscritura

/** Resultado de agregar el DNI a una ficha creada sin él (gemelo de `CompletarDni` web). */
sealed interface ResultadoAgregarDni {
    data object Ok : ResultadoAgregarDni
    /** Ese documento ya está en OTRA ficha que este usuario ve: no se guardó. */
    data class Duplicado(val id: String, val nombre: String) : ResultadoAgregarDni
    data class Error(val mensaje: String) : ResultadoAgregarDni
}

/**
 * Buscar al paciente por nombre o DNI y registrarlo SIN DNI desde la nueva cita
 * (2026-10-16), con el Bearer del staff — los mismos endpoints que la web:
 *  - GET  /api/staff/paciente/buscar   (permiso agendar o pacientes)
 *  - POST /api/staff/paciente/crear    { nombre, idempotency_key } sin `dni`
 *  - POST /api/staff/paciente/actualizar (completar datos; 403/404 reales)
 * El DNI agregado después va directo con la RLS del staff, como `CompletarDni`
 * de la web (el endpoint de actualizar no escribe el documento): se verifica la
 * fila escrita y el 23505 del índice único se traduce sin dar datos de la otra ficha.
 */
object BuscarPacienteRepo {
    private val http = crearHttpClient()
    private suspend fun token(): String? = Supabase.client.auth.currentSessionOrNull()?.accessToken

    /**
     * GET /api/staff/paciente/buscar. [parecidos] = "¿Es alguno de estos?" antes
     * de crear sin DNI (incluye las fichas de baja). Sin red → Error; un servidor
     * sin el endpoint → NoDisponible (quien llama busca a la antigua).
     */
    suspend fun buscar(q: String, limite: Int = 12, parecidos: Boolean = false): RespuestaBusqueda = try {
        val tk = token() ?: return RespuestaBusqueda.Error("Sesión vencida. Vuelve a entrar.", "NO_AUTENTICADO")
        val resp = http.get("${Supabase.SITE_URL}/api/staff/paciente/buscar") {
            header("Authorization", "Bearer $tk")
            parameter("q", q.trim().take(120))
            parameter("limite", limite.coerceIn(1, 20))
            if (parecidos) parameter("modo", "parecidos")
        }
        interpretarBusqueda(resp.status.value, runCatching { resp.bodyAsText() }.getOrNull())
    } catch (e: CancellationException) { throw e } catch (_: Exception) {
        RespuestaBusqueda.Error("Sin conexión", "SIN_RED")
    }

    /** Parecidos con tope de tiempo (4 s, como la web): si no llega, null y se sigue con lo local. */
    suspend fun parecidos(nombre: String): RespuestaBusqueda? =
        withTimeoutOrNull(4_000) { buscar(nombre, limite = 5, parecidos = true) }

    /**
     * Alta SOLO con el nombre. [clave] = la del intento ([claveDeIntento]): la
     * misma solo al reintentar esa alta no confirmada. `status` 0 = sin respuesta.
     */
    suspend fun registrarSinDni(nombre: String, clave: String): Pair<ResultadoAltaRapida, Int> = try {
        val tk = token() ?: return ResultadoAltaRapida(null, error = "Sesión vencida. Vuelve a entrar.") to 401
        // Pacientes por sede: nace en la sede activa (igual que el alta por DNI).
        val sede = SedeActiva.estado.value.let { sedeParaPacienteNuevo(it.pacientesPorSede, it.filtro?.sedeId) }
        val cuerpo = buildJsonObject {
            put("nombre", nombre.trim())
            put("fecha_ingreso", hoyClinicaIso())
            if (sede != null) put("sede_id", sede)
            put("idempotency_key", clave)
        }
        val resp = pe.saniape.app.ui.conIndicador {
            http.post("${Supabase.SITE_URL}/api/staff/paciente/crear") {
                header("Authorization", "Bearer $tk")
                contentType(ContentType.Application.Json)
                setBody(cuerpo.toString())
            }
        }
        val r = interpretarAltaRapida(resp.status.value, resp.bodyAsText()).copy(sedeId = sede)
        if (r.ok) pe.saniape.app.tutoriales.MotorTutoriales.tarea("paciente_creado")
        r to resp.status.value
    } catch (e: CancellationException) { throw e } catch (_: Exception) {
        ResultadoAltaRapida(null, error = "No se pudo registrar. Revisa tu conexión.") to 0
    }

    /**
     * POST /api/staff/paciente/actualizar con SOLO los campos escritos (una clave
     * ausente no toca su columna). 403/404 del servidor llegan como rechazo.
     */
    suspend fun completarDatos(pacienteId: String, campos: Map<String, String>): ResultadoEscritura {
        val tk = token() ?: return ResultadoEscritura(false, rechazo = RechazoServidor("Sesión vencida. Vuelve a entrar.", "NO_AUTENTICADO", 401))
        val cuerpo = buildJsonObject {
            put("pacienteId", pacienteId)
            campos.forEach { (k, v) -> v.trim().takeIf { it.isNotEmpty() }?.let { put(k, it) } }
        }
        return try {
            pe.saniape.app.ui.conIndicador {
                val resp = http.post("${Supabase.SITE_URL}/api/staff/paciente/actualizar") {
                    header("Authorization", "Bearer $tk")
                    contentType(ContentType.Application.Json)
                    setBody(cuerpo.toString())
                }
                AtencionRepo.resultadoDeRespuesta(resp.status.value, runCatching { resp.bodyAsText() }.getOrNull())
            }
        } catch (e: CancellationException) { throw e } catch (_: Exception) {
            ResultadoEscritura(false, rechazo = RechazoServidor("Sin conexión. Revisa tu internet.", "SIN_RED"))
        }
    }

    /**
     * Agrega el DNI (y, si se eligió, el nombre del padrón) a una ficha creada
     * sin él. Antes mira si ese documento ya está en otra ficha visible: no lo
     * duplica. Con la fila escrita verificada (0 filas = sin permiso).
     */
    suspend fun agregarDni(pacienteId: String, dni: String, nombrePadron: String?): ResultadoAgregarDni {
        val doc = dni.trim()
        return try {
            val otro = Supabase.client.postgrest["pacientes"]
                .select(Columns.list("id", "nombre")) {
                    filter { eq("dni", doc); neq("id", pacienteId) }
                    limit(1)
                }.decodeList<JsonObject>().firstOrNull()
            if (otro != null) {
                val id = (otro["id"] as? JsonPrimitive)?.content.orEmpty()
                return ResultadoAgregarDni.Duplicado(id, (otro["nombre"] as? JsonPrimitive)?.content ?: "otro paciente")
            }
            val escritas = Supabase.client.postgrest["pacientes"].update({
                set("dni", doc)
                if (!nombrePadron.isNullOrBlank()) set("nombre", nombrePadron.trim())
            }) {
                select(Columns.list("id"))
                filter { eq("id", pacienteId) }
            }.decodeList<JsonObject>()
            if (escritas.isEmpty()) ResultadoAgregarDni.Error(MSG_SIN_PERMISO_DNI) else ResultadoAgregarDni.Ok
        } catch (e: CancellationException) { throw e } catch (e: Exception) {
            // supabase-kt no expone el código de Postgres: va en el mensaje
            // ("duplicate key value…" = 23505; "row-level security" = 42501).
            ResultadoAgregarDni.Error(mensajeErrorDni(null, e.message))
        }
    }
}

const val MSG_SIN_PERMISO_DNI = "No tienes permiso para editar los datos de este paciente. Pídeselo a recepción o a un Admin."

/** Error de Postgres al escribir el DNI → texto (como `CompletarDni` web). Puro. */
fun mensajeErrorDni(codigo: String?, mensaje: String?): String {
    val m = mensaje.orEmpty()
    return when {
        codigo == "23505" || m.contains("23505") || m.contains("duplicate key", ignoreCase = true) ->
            "Ese documento ya está en otra ficha; avisa a un Admin."
        codigo == "42501" || m.contains("42501") || m.contains("row-level security", ignoreCase = true) ->
            "Modo solo lectura: no puedes modificar datos."
        else -> "No se pudo guardar. Intenta de nuevo."
    }
}
