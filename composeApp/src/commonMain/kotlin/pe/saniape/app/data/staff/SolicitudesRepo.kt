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

/** "LIMITE_PLAN: Se lleno el espacio…" (error de la base) → solo el texto para la persona. */
fun mensajeLimitePlan(error: String): String =
    error.substringAfter("LIMITE_PLAN:").lineSequence().first().trim()
        .ifBlank { "Se llenó el espacio de documentos de tu plan." }

/** Resultado de subir un archivo: el path guardado, o el motivo (LIMITE_PLAN = sin espacio en el plan). */
sealed class SubidaArchivo {
    data class Ok(val path: String, val tipo: String) : SubidaArchivo()
    data class Error(val mensaje: String, val limitePlan: Boolean = false) : SubidaArchivo()
}

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
    /** "Documento", "Receta", "Consentimiento"… (las fotos evolutivas NO llegan aquí). */
    val categoria: String? = null,
    /** Tratamiento al que se ancló al subirlo; null = General (sin tratamiento). */
    val tratamientoId: String? = null,
    /** `visible_paciente` (null = no se pudo leer). En un informe psicológico, false = versión reemplazada. */
    val visiblePaciente: Boolean? = null,
) {
    /** 🔒 Protegido: el PDF del informe psicológico (solo lo ven el Admin y el tratante). */
    val protegidoPsico: Boolean get() = categoria == CATEGORIA_INFORME_PSICOLOGICO || categoria == CATEGORIA_TEST_PSICOLOGICO

    /** "· reemplazado": PDF de un informe psicológico del que se emitió una versión nueva (el paciente ya no lo ve). */
    val informeReemplazado: Boolean get() = categoria == CATEGORIA_INFORME_PSICOLOGICO && visiblePaciente == false
}

/**
 * Tratamiento por defecto al subir un documento: el ACTIVO más reciente (por
 * fecha de inicio o de creación), como tratamientoPorDefecto de la web. null =
 * ninguno activo → "General".
 */
fun tratamientoPorDefectoDoc(tratamientos: List<TratamientoPaciente>): String? =
    tratamientos.filter { (it.estado ?: "Activo") == "Activo" }
        .maxByOrNull { it.fechaInicio ?: it.createdAt ?: "" }?.id

/** Categoría de las fotos de la galería: no se listan como documentos. */
const val CATEGORIA_FOTO_EVOLUTIVA = "Foto evolutiva"

/** Un grupo de documentos de la ficha: un tratamiento (o "General") y sus categorías. */
data class GrupoDocumentos(
    val tratamientoId: String?,
    val titulo: String,
    val categorias: List<Pair<String, List<DocumentoFicha>>>,
)

/**
 * Documentos de la ficha agrupados por TRATAMIENTO → CATEGORÍA. Los tratamientos
 * salen en el orden de [nombresTratamiento] (el de la ficha); un tratamiento que ya
 * no está en la lista sale igual, como "Tratamiento anterior". Lo que no tiene
 * tratamiento va al final, en "General". Las fotos evolutivas se excluyen (viven
 * en la galería del tratamiento).
 */
fun agruparDocumentosFicha(
    docs: List<DocumentoFicha>,
    nombresTratamiento: List<Pair<String, String>>,
): List<GrupoDocumentos> {
    // Ni las fotos evolutivas (galería) ni las fotos de tests psicológicos (material
    // protegido: solo en el espacio de la evaluación, contrato §0).
    val lista = docs.filter { it.categoria != CATEGORIA_FOTO_EVOLUTIVA && it.categoria != CATEGORIA_TEST_PSICOLOGICO }
    if (lista.isEmpty()) return emptyList()
    fun porCategoria(l: List<DocumentoFicha>) =
        l.groupBy { it.categoria?.takeIf { c -> c.isNotBlank() } ?: "Documento" }.toList()
    val nombres = nombresTratamiento.toMap()
    val conTrat = lista.filter { it.tratamientoId != null }.groupBy { it.tratamientoId!! }
    val orden = nombresTratamiento.map { it.first }.filter { it in conTrat } +
        conTrat.keys.filter { it !in nombres }
    val grupos = orden.map { id ->
        GrupoDocumentos(id, nombres[id] ?: "Tratamiento anterior", porCategoria(conTrat.getValue(id)))
    }
    val generales = lista.filter { it.tratamientoId == null }
    return grupos + (if (generales.isNotEmpty()) listOf(GrupoDocumentos(null, "General", porCategoria(generales))) else emptyList())
}

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
        suspend fun leer(columnas: String) = Supabase.client.postgrest["documentos_paciente"]
            .select(Columns.list(columnas)) {
                filter { eq("paciente_id", pacienteId) }
                order("created_at", Order.DESCENDING)
            }
            .decodeList<JsonObject>()
        // `visible_paciente` marca los informes reemplazados; si la consulta con
        // esa columna fallara, la lista sale igual sin la marca.
        val filas = runCatching { leer("id, nombre, archivo_url, tipo_archivo, categoria, tratamiento_id, visible_paciente") }
            .recoverCatching { leer("id, nombre, archivo_url, tipo_archivo, categoria, tratamiento_id") }
            .getOrDefault(emptyList())
        return filas.mapNotNull { o ->
            // Las fotos evolutivas son de la galería del tratamiento, no de esta lista.
            if (o.str("categoria") == CATEGORIA_FOTO_EVOLUTIVA || o.str("categoria") == CATEGORIA_TEST_PSICOLOGICO) return@mapNotNull null
            DocumentoFicha(
                id = o.str("id") ?: return@mapNotNull null,
                nombre = o.str("nombre") ?: "Documento",
                archivoUrl = o.str("archivo_url") ?: return@mapNotNull null,
                tipoArchivo = o.str("tipo_archivo"),
                categoria = o.str("categoria"),
                tratamientoId = o.str("tratamiento_id"),
                visiblePaciente = when (o.str("visible_paciente")) { "true" -> true; "false" -> false; else -> null },
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

    /**
     * Elimina un documento (solo el registro; el archivo del bucket queda huérfano,
     * como la web). null = listo; si no, el motivo para mostrar (p. ej. el informe
     * psicológico emitido no se borra: trigger INFORME_EMITIDO_NO_SE_BORRA).
     */
    suspend fun eliminarDocumento(documentoId: String): String? = try {
        Supabase.client.postgrest["documentos_paciente"].delete { filter { eq("id", documentoId) } }
        null
    } catch (e: Exception) { fraseDeErrorBasePsico(e.message) ?: "No se pudo eliminar" }

    /**
     * Sube un archivo al bucket privado vía /api/staff/documento/subir (multipart, Bearer).
     * Devuelve el path guardado (o null si falló). [prefijo]: "doc" o "res".
     */
    suspend fun subirArchivo(pacienteId: String, nombre: String, bytes: ByteArray, mime: String?, prefijo: String): Pair<String, String>? =
        (subirArchivoDetalle(pacienteId, nombre, bytes, mime, prefijo) as? SubidaArchivo.Ok)?.let { it.path to it.tipo }

    /**
     * Como [subirArchivo], pero con el MOTIVO si no entra: adjuntar documentos es
     * de todos los planes y el Básico tiene tope de espacio — el servidor responde
     * 403 `codigo: LIMITE_PLAN` con un texto claro ("Se llenó el espacio…"), que
     * hay que mostrar tal cual en vez de un "No se pudo" mudo.
     */
    suspend fun subirArchivoDetalle(
        pacienteId: String, nombre: String, bytes: ByteArray, mime: String?, prefijo: String,
        /** Tratamiento al que se ancla (campo opcional nuevo del endpoint; un servidor viejo lo ignora). */
        tratamientoId: String? = null,
    ): SubidaArchivo {
        return try {
            val tk = token() ?: return SubidaArchivo.Error("Tu sesión expiró. Vuelve a entrar.")
            val resp = http.post("${Supabase.SITE_URL}/api/staff/documento/subir") {
                header("Authorization", "Bearer $tk")
                setBody(MultiPartFormDataContent(formData {
                    append("pacienteId", pacienteId)
                    append("prefijo", prefijo)
                    if (tratamientoId != null) append("tratamientoId", tratamientoId)
                    append("archivo", bytes, Headers.build {
                        append(HttpHeaders.ContentType, mime ?: "application/octet-stream")
                        append(HttpHeaders.ContentDisposition, "filename=\"$nombre\"")
                    })
                }))
            }
            val obj = runCatching { Json.parseToJsonElement(resp.bodyAsText()).jsonObject }.getOrNull()
            if (resp.status != HttpStatusCode.OK) {
                val error = (obj?.get("error") as? JsonPrimitive)?.content?.takeIf { it != "null" && it.isNotBlank() }
                val codigo = (obj?.get("codigo") as? JsonPrimitive)?.content
                return SubidaArchivo.Error(error ?: "No se pudo subir el archivo", limitePlan = codigo == "LIMITE_PLAN")
            }
            val path = obj?.get("path")?.jsonPrimitive?.content ?: return SubidaArchivo.Error("No se pudo subir el archivo")
            val tipo = obj["tipo"]?.jsonPrimitive?.content ?: ""
            SubidaArchivo.Ok(path, tipo)
        } catch (e: Exception) { SubidaArchivo.Error("No se pudo subir el archivo. Revisa tu conexión.") }
    }

    /**
     * Espacio de documentos que usa la clínica (bytes), por la misma RPC que la web
     * (`documentos_espacio_usado`). Solo tiene sentido con tope (Básico). null = no
     * se pudo medir (base sin la RPC o sin señal): no se muestra la barra.
     */
    suspend fun espacioUsadoBytes(): Long? = runCatching {
        Supabase.client.postgrest.rpc("documentos_espacio_usado").data.trim().trim('"').toDoubleOrNull()?.toLong()
    }.getOrNull()

    /** Crea el registro de un documento clínico (tras subir el archivo). */
    suspend fun registrarDocumento(pacienteId: String, nombre: String, archivoPath: String, tipo: String): Boolean =
        registrarDocumentoDetalle(pacienteId, nombre, archivoPath, tipo) == null

    /**
     * Como [registrarDocumento], pero devuelve el MOTIVO si la base lo rechazó
     * (null = quedó registrado). El trigger de espacio frena con "LIMITE_PLAN: …"
     * aunque la subida haya pasado (dos teléfonos subiendo a la vez).
     */
    suspend fun registrarDocumentoDetalle(
        pacienteId: String, nombre: String, archivoPath: String, tipo: String,
        tratamientoId: String? = null,
    ): String? = try {
        registrarDocumentoInsert(pacienteId, nombre, archivoPath, tipo, tratamientoId)
        null
    } catch (e: Exception) {
        val m = e.message.orEmpty()
        when {
            m.contains("LIMITE_PLAN") -> mensajeLimitePlan(m)
            // Trigger de la base: el tratamiento no es de este paciente / clínica.
            m.contains("TRATAMIENTO_INVALIDO") ->
                "Ese tratamiento no pertenece a este paciente. Elige otro o súbelo como General."
            else -> "No se pudo registrar el documento"
        }
    }


    private suspend fun registrarDocumentoInsert(
        pacienteId: String, nombre: String, archivoPath: String, tipo: String, tratamientoId: String?,
    ) {
        Supabase.client.postgrest["documentos_paciente"].insert(buildJsonObject {
            put("paciente_id", pacienteId)
            // Anclado a un tratamiento (null = General). No se manda clinica_id: DEFAULT.
            if (tratamientoId != null) put("tratamiento_id", tratamientoId)
            put("nombre", nombre)
            put("categoria", "Documento")
            put("archivo_url", archivoPath)
            put("tipo_archivo", tipo)
        })
    }

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
