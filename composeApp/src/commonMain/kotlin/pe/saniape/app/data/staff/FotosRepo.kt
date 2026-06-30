package pe.saniape.app.data.staff

import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.Columns
import io.github.jan.supabase.postgrest.query.Order
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import pe.saniape.app.data.Supabase

/**
 * Una foto evolutiva (antes/durante/después) de un tratamiento. Reusa la tabla
 * documentos_paciente con categoria='Foto evolutiva' (igual que la web). Privada por
 * defecto: el profesional decide si el paciente la ve en su portal (visible_paciente).
 */
data class FotoEvolutiva(
    val id: String,
    val tratamientoId: String?,
    val sesionId: String?,
    val nombre: String,
    val archivoUrl: String,    // path en el bucket privado (se firma para verla)
    val momento: String?,      // "Antes" | "Durante" | "Despues"
    val notas: String?,
    val visiblePaciente: Boolean,
    val createdAt: String?,
)

/**
 * CRUD de fotos evolutivas (Premium + feature fotosEvolutivas). Lecturas/escrituras
 * directas a Supabase con la RLS de staff; la SUBIDA del archivo va por el endpoint
 * /api/staff/documento/subir (multipart Bearer, reusa SolicitudesRepo.subirArchivo) y
 * el firmado para verla por /api/documento (SolicitudesRepo.urlFirmada). No reimplementa.
 */
object FotosRepo {

    private fun JsonObject.str(k: String): String? =
        (this[k] as? JsonPrimitive)?.content?.takeIf { it != "null" }
    private fun JsonObject.bool(k: String): Boolean =
        (this[k] as? JsonPrimitive)?.content?.toBoolean() == true

    /** Orden de momentos: Antes → Durante → Después, luego cronológico (igual que la web). */
    private fun ordenMomento(m: String?): Int = when (m) {
        "Antes" -> 0; "Durante" -> 1; "Despues" -> 2; else -> 1
    }

    /** Fotos evolutivas de un paciente (todas; se filtran por tratamiento en la UI). */
    suspend fun fotosDe(pacienteId: String): List<FotoEvolutiva> {
        val filas = runCatching {
            Supabase.client.postgrest["documentos_paciente"]
                .select(Columns.list(
                    "id, tratamiento_id, sesion_id, nombre, archivo_url, momento, notas, visible_paciente, created_at"
                )) {
                    filter { eq("paciente_id", pacienteId); eq("categoria", "Foto evolutiva") }
                    order("created_at", Order.ASCENDING)
                }
                .decodeList<JsonObject>()
        }.getOrDefault(emptyList())
        return filas.mapNotNull { o ->
            FotoEvolutiva(
                id = o.str("id") ?: return@mapNotNull null,
                tratamientoId = o.str("tratamiento_id"),
                sesionId = o.str("sesion_id"),
                nombre = o.str("nombre") ?: "Foto",
                archivoUrl = o.str("archivo_url") ?: return@mapNotNull null,
                momento = o.str("momento"),
                notas = o.str("notas"),
                visiblePaciente = o.bool("visible_paciente"),
                createdAt = o.str("created_at"),
            )
        }.sortedWith(compareBy({ ordenMomento(it.momento) }, { it.createdAt ?: "" }))
    }

    /** Registra la foto en documentos_paciente (tras subir el archivo al bucket). */
    suspend fun registrarFoto(
        pacienteId: String, tratamientoId: String, sesionId: String?,
        nombre: String, archivoPath: String, tipo: String,
        momento: String, notas: String?, visiblePaciente: Boolean,
    ): Boolean = try {
        Supabase.client.postgrest["documentos_paciente"].insert(buildJsonObject {
            put("paciente_id", pacienteId)
            put("tratamiento_id", tratamientoId)
            if (sesionId != null) put("sesion_id", sesionId)
            put("nombre", nombre)
            put("categoria", "Foto evolutiva")
            put("archivo_url", archivoPath)
            put("tipo_archivo", tipo)
            put("momento", momento)
            if (!notas.isNullOrBlank()) put("notas", notas)
            put("visible_paciente", visiblePaciente)
        })
        true
    } catch (e: Exception) { false }

    /** Alterna si el paciente ve la foto en su portal. */
    suspend fun cambiarVisible(fotoId: String, visible: Boolean): Boolean = try {
        Supabase.client.postgrest["documentos_paciente"]
            .update({ set("visible_paciente", visible) }) { filter { eq("id", fotoId) } }
        true
    } catch (e: Exception) { false }

    /** Borra el registro de la foto (el archivo del bucket queda huérfano, como la web). */
    suspend fun borrarFoto(fotoId: String): Boolean = try {
        Supabase.client.postgrest["documentos_paciente"].delete { filter { eq("id", fotoId) } }
        true
    } catch (e: Exception) { false }
}
