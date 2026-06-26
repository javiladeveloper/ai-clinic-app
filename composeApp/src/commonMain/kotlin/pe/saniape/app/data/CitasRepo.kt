package pe.saniape.app.data

import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.Order

/**
 * Lee las citas del paciente autenticado desde la MISMA tabla `citas` de la web.
 *
 * Por ahora filtra por el `paciente_id` resuelto desde la cuenta. La RLS de
 * Supabase ya restringe lo que el usuario puede ver; aquí solo pedimos lo suyo.
 */
object CitasRepo {

    /** Citas del paciente, de la más próxima a la más lejana. */
    suspend fun misCitas(pacienteId: String): List<Cita> {
        return Supabase.client.postgrest["citas"]
            .select {
                filter { eq("paciente_id", pacienteId) }
                order("fecha", Order.ASCENDING)
                order("hora", Order.ASCENDING)
            }
            .decodeList<Cita>()
    }

    /** Resuelve la fila de paciente asociada al email del usuario logueado. */
    suspend fun pacienteIdDelUsuario(): String? {
        val email = Supabase.client.auth.currentUserOrNull()?.email ?: return null
        val filas = Supabase.client.postgrest["pacientes"]
            .select {
                filter { eq("email", email) }
                limit(1)
            }
            .decodeList<PacienteMin>()
        return filas.firstOrNull()?.id
    }
}

@kotlinx.serialization.Serializable
private data class PacienteMin(val id: String)