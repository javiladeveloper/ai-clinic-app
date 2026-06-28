package pe.saniape.app.auth

import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.providers.builtin.Email
import pe.saniape.app.data.Supabase

/**
 * Login del STAFF (clínicas) con usuario + contraseña — mismo Supabase Auth que la web.
 * Los pacientes usan Google (AuthGoogle); el staff usa email/contraseña por regla de
 * negocio (no todos tienen Google, y así coincide con el login web).
 */
object AuthStaff {

    /** Inicia sesión con email/contraseña. Devuelve null si OK, o el mensaje de error. */
    suspend fun ingresar(email: String, password: String): String? {
        return try {
            Supabase.client.auth.signInWith(Email) {
                this.email = email.trim()
                this.password = password
            }
            null
        } catch (e: Exception) {
            val msg = e.message ?: ""
            when {
                msg.contains("Invalid login", ignoreCase = true) ||
                    msg.contains("invalid_credentials", ignoreCase = true) ->
                    "Correo o contraseña incorrectos."
                msg.contains("Email not confirmed", ignoreCase = true) ->
                    "Tu cuenta no está confirmada. Revisa tu correo."
                else -> "No se pudo iniciar sesión. Intenta de nuevo."
            }
        }
    }
}