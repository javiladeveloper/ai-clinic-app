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
            // Se llama al ENDPOINT, no a la tabla.
            //
            // El upsert directo con `onConflict = token` parecía la vía corta, pero
            // la RLS lo bloquea justo en el caso que importa: la política es
            // `perfil_id = auth.uid()`, así que si esa fila ya existe a nombre de
            // OTRO perfil (el mismo celular usado antes con otra cuenta), el
            // UPDATE del upsert no puede tocarla. Falla y devuelve false, en
            // silencio.
            //
            // Y aunque la fila fuera propia, seguía sin cubrir el caso real de un
            // usuario con DOS aparatos: cada uno tiene su token, pero la tabla
            // solo mostraba uno por perfil — nadie llegó nunca a tener dos
            // (comprobado en producción 2026-08-11).
            //
            // El endpoint usa service_role y resuelve las dos cosas del lado del
            // servidor: reasigna el token si estaba a nombre de otro, y conserva
            // los demás aparatos del mismo usuario.
            val res = Supabase.client.postgrest.rpc(
                "registrar_dispositivo_push",
                buildJsonObject {
                    put("p_token", token)
                    put("p_plataforma", "android")
                },
            )
            println("SaniaPush: dispositivo registrado para $userId")
            res
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
            // Por el endpoint, igual que el del staff (ver la nota de registrarToken):
            // la RLS bloquea el upsert directo cuando el token ya está a nombre de otro.
            Supabase.client.postgrest.rpc(
                "registrar_dispositivo_push_paciente",
                buildJsonObject {
                    put("p_token", token)
                    put("p_plataforma", "android")
                },
            )
            println("SaniaPush: dispositivo del paciente registrado para $userId")
            true
        }.getOrElse {
            println("SaniaPush: no se pudo registrar el dispositivo del paciente — ${it.message}")
            false
        }
    }
}
