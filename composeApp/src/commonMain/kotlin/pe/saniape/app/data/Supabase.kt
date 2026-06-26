package pe.saniape.app.data

import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.auth.Auth
import io.github.jan.supabase.postgrest.Postgrest

/**
 * Cliente Supabase compartido — apunta a la MISMA base que la web (saniape.com).
 *
 * URL y anon key son públicas (las mismas que NEXT_PUBLIC_* de la web): la
 * seguridad real la da RLS en Postgres, no el secreto de esta llave.
 */
object Supabase {
    const val URL = "https://rigohpumndmlbufngced.supabase.co"

    // Base del sitio web (para llamar a los endpoints /api que reutilizan la lógica
    // segura: reservar con verificación de DNI, rate-limit, etc.).
    const val SITE_URL = "https://www.saniape.com"

    // anon key (pública) — idéntica a la de la web. RLS protege los datos.
    const val ANON_KEY =
        "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6InJpZ29ocHVtbmRtbGJ1Zm5nY2VkIiwicm9sZSI6ImFub24iLCJpYXQiOjE3ODExOTEwODksImV4cCI6MjA5Njc2NzA4OX0.EYWO4z_j9CSqcuC9oOn8LoBtM-zJ4A-t3iWohSLAexI"

    val client: SupabaseClient by lazy {
        createSupabaseClient(
            supabaseUrl = URL,
            supabaseKey = ANON_KEY,
        ) {
            install(Auth)
            install(Postgrest)
        }
    }
}