package pe.saniape.app.data.staff

import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.Columns
import io.github.jan.supabase.postgrest.query.Order
import io.ktor.client.request.header
import io.ktor.client.request.get
import io.ktor.client.request.forms.MultiPartFormDataContent
import io.ktor.client.request.forms.formData
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.Headers
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.put
import pe.saniape.app.data.Supabase
import pe.saniape.app.data.crearHttpClient

/** Una solicitud clínica: Examen externo o Derivación a otra especialidad. */
data class SolicitudFicha(
    val id: String,
    val tipo: String,            // "Examen" | "Derivacion"
    val descripcion: String,
    val estado: String,          // Pendiente | Completada | Cancelada
    val fecha: String,
    val terapeutaNombre: String?,
    val especialidadDestinoId: String?,
    val especialidadDestinoNombre: String?,
    val resultadoNota: String?,
    val resultadoArchivoUrl: String?,
    val lugar: String = "Externo",   // "Interno" (en la clínica, deriva al área) | "Externo"
) {
    val esInterno: Boolean get() = lugar == "Interno"
}

/** Un documento clínico del paciente (radiografía, análisis…) en el bucket privado. */
data class DocumentoFicha(
    val id: String,
    val nombre: String,
    val archivoUrl: String,      // path en el bucket (privado): se firma para verlo
    val tipoArchivo: String?,
)

/**
 * Exámenes / Derivaciones / Documentos del paciente. Espeja ExamenesDerivaciones (web):
 * lecturas y CRUD directos a Supabase con la RLS de staff; el trigger de plan (LIMITE_PLAN)
 * gatea exámenes/derivaciones igual que en la web. Las URLs de documentos privados se
 * firman vía /api/documento (acepta Bearer). La subida de archivos va en un paso aparte.
 */
object SolicitudesRepo {

    private val http = crearHttpClient()
    private suspend fun token(): String? = Supabase.client.auth.currentSessionOrNull()?.accessToken
    private fun JsonObject.str(k: String): String? =
        (this[k] as? JsonPrimitive)?.content?.takeIf { it != "null" }

    /** Solicitudes (exámenes + derivaciones) del paciente. */
    suspend fun solicitudesDe(pacienteId: String): List<SolicitudFicha> {
        val filas = runCatching {
            Supabase.client.postgrest["solicitudes"]
                .select(Columns.raw(
                    "id, tipo, descripcion, estado, fecha, especialidad_destino_id, lugar, " +
                        "resultado_nota, resultado_archivo_url, terapeuta:terapeutas(nombre), " +
                        "especialidad_destino:especialidades!solicitudes_especialidad_destino_id_fkey(nombre)"
                )) {
                    filter { eq("paciente_id", pacienteId) }
                    order("fecha", Order.DESCENDING)
                }
                .decodeList<JsonObject>()
        }.getOrDefault(emptyList())
        return filas.mapNotNull { o ->
            SolicitudFicha(
                id = o.str("id") ?: return@mapNotNull null,
                tipo = o.str("tipo") ?: "Examen",
                descripcion = o.str("descripcion") ?: "",
                estado = o.str("estado") ?: "Pendiente",
                fecha = o.str("fecha") ?: "",
                terapeutaNombre = (o["terapeuta"] as? JsonObject)?.str("nombre"),
                especialidadDestinoId = o.str("especialidad_destino_id"),
                especialidadDestinoNombre = (o["especialidad_destino"] as? JsonObject)?.str("nombre"),
                resultadoNota = o.str("resultado_nota"),
                resultadoArchivoUrl = o.str("resultado_archivo_url"),
                lugar = o.str("lugar") ?: "Externo",
            )
        }
    }

    /** Documentos clínicos del paciente. */
    suspend fun documentosDe(pacienteId: String): List<DocumentoFicha> {
        val filas = runCatching {
            Supabase.client.postgrest["documentos_paciente"]
                .select(Columns.list("id, nombre, archivo_url, tipo_archivo")) {
                    filter { eq("paciente_id", pacienteId) }
                    order("created_at", Order.DESCENDING)
                }
                .decodeList<JsonObject>()
        }.getOrDefault(emptyList())
        return filas.mapNotNull { o ->
            DocumentoFicha(
                id = o.str("id") ?: return@mapNotNull null,
                nombre = o.str("nombre") ?: "Documento",
                archivoUrl = o.str("archivo_url") ?: return@mapNotNull null,
                tipoArchivo = o.str("tipo_archivo"),
            )
        }
    }

    /**
     * Crea una solicitud (examen o derivación). [tratamientoId]: del que nace la derivación.
     * [lugar]: "Externo" (el paciente se lo hace afuera) o "Interno" (en la clínica, deriva al
     * área [especialidadDestinoId] que la clínica designó para exámenes; recepción lo agenda/cobra).
     */
    suspend fun crearSolicitud(
        pacienteId: String, tipo: String, descripcion: String,
        terapeutaId: String?, especialidadDestinoId: String?, tratamientoId: String? = null,
        lugar: String = "Externo",
    ): Boolean = try {
        Supabase.client.postgrest["solicitudes"].insert(buildJsonObject {
            put("paciente_id", pacienteId)
            put("tipo", tipo)
            put("descripcion", descripcion)
            put("lugar", lugar)
            if (terapeutaId != null) put("terapeuta_id", terapeutaId)
            if (especialidadDestinoId != null) put("especialidad_destino_id", especialidadDestinoId)
            if (tratamientoId != null) put("tratamiento_id", tratamientoId)
        })
        true
    } catch (e: Exception) { false }

    /** Registra el resultado de un examen (nota + archivo opcional). */
    suspend fun registrarResultado(solicitudId: String, nota: String?, archivoPath: String?): Boolean = try {
        Supabase.client.postgrest["solicitudes"].update({
            set("estado", "Completada")
            set("resultado_nota", nota)
            set("resultado_archivo_url", archivoPath)
            set("fecha_resultado", pe.saniape.app.ui.clinica.agenda.hoyIso())
        }) { filter { eq("id", solicitudId) } }
        true
    } catch (e: Exception) { false }

    /** Cambia el estado de una solicitud (p.ej. Cancelada). */
    suspend fun cambiarEstado(solicitudId: String, estado: String): Boolean = try {
        Supabase.client.postgrest["solicitudes"].update({ set("estado", estado) }) {
            filter { eq("id", solicitudId) }
        }
        true
    } catch (e: Exception) { false }

    /** Elimina una solicitud. */
    suspend fun eliminarSolicitud(solicitudId: String): Boolean = try {
        Supabase.client.postgrest["solicitudes"].delete { filter { eq("id", solicitudId) } }
        true
    } catch (e: Exception) { false }

    /** Elimina un documento (solo el registro; el archivo del bucket queda huérfano, como la web). */
    suspend fun eliminarDocumento(documentoId: String): Boolean = try {
        Supabase.client.postgrest["documentos_paciente"].delete { filter { eq("id", documentoId) } }
        true
    } catch (e: Exception) { false }

    /**
     * Sube un archivo al bucket privado vía /api/staff/documento/subir (multipart, Bearer).
     * Devuelve el path guardado (o null si falló). [prefijo]: "doc" o "res".
     */
    suspend fun subirArchivo(pacienteId: String, nombre: String, bytes: ByteArray, mime: String?, prefijo: String): Pair<String, String>? {
        return try {
            val tk = token() ?: return null
            val resp = http.post("${Supabase.SITE_URL}/api/staff/documento/subir") {
                header("Authorization", "Bearer $tk")
                setBody(MultiPartFormDataContent(formData {
                    append("pacienteId", pacienteId)
                    append("prefijo", prefijo)
                    append("archivo", bytes, Headers.build {
                        append(HttpHeaders.ContentType, mime ?: "application/octet-stream")
                        append(HttpHeaders.ContentDisposition, "filename=\"$nombre\"")
                    })
                }))
            }
            if (resp.status != HttpStatusCode.OK) return null
            val obj = Json.parseToJsonElement(resp.bodyAsText()).jsonObject
            val path = obj["path"]?.jsonPrimitive?.content ?: return null
            val tipo = obj["tipo"]?.jsonPrimitive?.content ?: ""
            path to tipo
        } catch (e: Exception) { null }
    }

    /** Crea el registro de un documento clínico (tras subir el archivo). */
    suspend fun registrarDocumento(pacienteId: String, nombre: String, archivoPath: String, tipo: String): Boolean = try {
        Supabase.client.postgrest["documentos_paciente"].insert(buildJsonObject {
            put("paciente_id", pacienteId)
            put("nombre", nombre)
            put("categoria", "Documento")
            put("archivo_url", archivoPath)
            put("tipo_archivo", tipo)
        })
        true
    } catch (e: Exception) { false }

    /**
     * Pide al backend una URL firmada temporal para abrir un documento privado.
     * /api/documento valida que el path pertenezca a la clínica del staff (acepta Bearer).
     */
    suspend fun urlFirmada(path: String): String? {
        return try {
            val tk = token() ?: return null
            val resp = http.get("${Supabase.SITE_URL}/api/documento?path=$path") {
                header("Authorization", "Bearer $tk")
            }
            if (resp.status != HttpStatusCode.OK) null
            else Json.parseToJsonElement(resp.bodyAsText()).jsonObject["url"]?.jsonPrimitive?.content
        } catch (e: Exception) { null }
    }
}
