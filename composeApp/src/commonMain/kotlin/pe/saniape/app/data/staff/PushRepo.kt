package pe.saniape.app.data.staff

import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.postgrest.postgrest
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import pe.saniape.app.data.Supabase

/**
 * Registro del token FCM del dispositivo (notificaciones reales del celular).
 * RLS "Mi dispositivo": cada perfil solo toca los suyos. El envío lo hace el
 * dashboard (lib/fcm.ts) con service_role leyendo esta tabla.
 */
object PushRepo {

    /** Upsert del token del dispositivo para el usuario logueado. Best-effort. */
    suspend fun registrarToken(token: String) {
        val userId = Supabase.client.auth.currentUserOrNull()?.id ?: return
        runCatching {
            // Delete + insert (más simple que upsert con onConflict en supabase-kt, y el
            // token es UNIQUE): si el token ya era de otro perfil (cambio de cuenta en el
            // mismo celular), pasa a ser del usuario actual.
            Supabase.client.postgrest["dispositivos_push"].delete { filter { eq("token", token) } }
            Supabase.client.postgrest["dispositivos_push"].insert(buildJsonObject {
                put("perfil_id", userId)
                put("token", token)
                put("plataforma", "android")
            })
        }
    }
}
