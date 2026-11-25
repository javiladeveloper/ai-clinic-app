package pe.saniape.app.data.staff

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.Columns
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import pe.saniape.app.data.Supabase

/*
 * 🏷️ DE DÓNDE VINO LA CITA (24/11/2026). Gemelo de lib/origen-cita.ts (web).
 * Sani (`origen = leadai`), la web (`online`) y el TELEOPERADOR: la cita la creó
 * (`citas.agendada_por`) alguien con ese rol.
 */

enum class OrigenCita { SANI, WEB, TELEOPERADOR }

data class EtiquetaOrigen(val origen: OrigenCita, val texto: String, val detalle: String)

const val ROL_TELEOPERADOR = "Teleoperador"

fun origenDeCita(origen: String?, agendadaPor: String?, teleoperadores: Map<String, String>): EtiquetaOrigen? = when {
    origen == "leadai" -> EtiquetaOrigen(OrigenCita.SANI, "🤖 Sani", "La agendó Sani, el asistente de WhatsApp")
    origen == "online" -> EtiquetaOrigen(OrigenCita.WEB, "🌐 Web", "La reservó el paciente desde la web")
    agendadaPor != null && teleoperadores.containsKey(agendadaPor) -> {
        val nombre = teleoperadores[agendadaPor].orEmpty().trim()
        EtiquetaOrigen(OrigenCita.TELEOPERADOR, "📞 Teleoperador",
            if (nombre.isNotEmpty()) "La agendó por teléfono $nombre (teleoperador)" else "La agendó un teleoperador por teléfono")
    }
    else -> null
}

/** Los teleoperadores de la clínica (id → nombre), leídos una vez por sesión. */
object TeleoperadoresRepo {
    var mapa by mutableStateOf<Map<String, String>>(emptyMap())
        private set
    private var cargado = false

    suspend fun cargar() {
        if (cargado) return
        cargado = true
        mapa = try {
            Supabase.client.postgrest["perfiles"].select(Columns.list("id, nombre, rol")) {
                filter { eq("rol", ROL_TELEOPERADOR) }
            }.decodeList<JsonObject>().mapNotNull { o ->
                val id = (o["id"] as? JsonPrimitive)?.content ?: return@mapNotNull null
                id to ((o["nombre"] as? JsonPrimitive)?.content?.takeIf { it != "null" }.orEmpty())
            }.toMap()
        } catch (e: CancellationException) { cargado = false; throw e } catch (_: Exception) { cargado = false; emptyMap() }
    }
}
