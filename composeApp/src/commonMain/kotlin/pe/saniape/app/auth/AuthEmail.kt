package pe.saniape.app.auth

import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.providers.builtin.Email
import pe.saniape.app.data.Supabase

/**
 * Login TEMPORAL con email + contraseña (mismo Supabase Auth que la web).
 *
 * ⚠️ TEMPORAL: sirve para poder ENTRAR y probar la app mientras se configura el
 * login con Google (Web Client ID + SHA-1 en Google Cloud). Una vez que Google
 * funcione, se puede quitar esta pantalla/botón.
 *
 * Funciona con cualquier cuenta que ya exista en tu base (las de prueba también).
 */
object AuthEmail {
    /** Inicia sesión. Lanza excepción si las credenciales son inválidas. */
    suspend fun entrar(email: String, password: String) {
        Supabase.client.auth.signInWith(Email) {
            this.email = email.trim()
            this.password = password
        }
    }
}