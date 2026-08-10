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

    /**
     * Upsert del token del dispositivo para el usuario logueado.
     *
     * Devuelve si se registró. Antes era `Unit` con el error tragado: si fallaba
     * la RLS o la red, el celular se quedaba sin push y no había rastro en
     * ninguna parte (2026-08-10).
     */
    suspend fun registrarToken(token: String): Boolean {
        val userId = Supabase.client.auth.currentUserOrNull()?.id ?: return false
        return runCatching {
            // Delete + insert (más simple que upsert con onConflict en supabase-kt, y el
            // token es UNIQUE): si el token ya era de otro perfil (cambio de cuenta en el
            // mismo celular), pasa a ser del usuario actual.
            Supabase.client.postgrest["dispositivos_push"].delete { filter { eq("token", token) } }
            Supabase.client.postgrest["dispositivos_push"].insert(buildJsonObject {
                put("perfil_id", userId)
                put("token", token)
                put("plataforma", "android")
            })
            true
        }.getOrElse {
            println("SaniaPush: no se pudo registrar el dispositivo — ${it.message}")
            false
        }
    }

    /**
     * Igual que registrarToken pero para el PACIENTE (portal): los pacientes no
     * tienen perfil de clinica — su identidad es la cuenta (auth_user_id) y su
     * tabla es dispositivos_push_paciente. El dashboard les envia campañas y
     * recordatorios de retorno con enviarFcmACuenta.
     */
    suspend fun registrarTokenPaciente(token: String): Boolean {
        val userId = Supabase.client.auth.currentUserOrNull()?.id ?: return false
        return runCatching {
            Supabase.client.postgrest["dispositivos_push_paciente"].delete { filter { eq("token", token) } }
            Supabase.client.postgrest["dispositivos_push_paciente"].insert(buildJsonObject {
                put("auth_user_id", userId)
                put("token", token)
                put("plataforma", "android")
            })
            true
        }.getOrElse {
            println("SaniaPush: no se pudo registrar el dispositivo del paciente — ${it.message}")
            false
        }
    }
}
