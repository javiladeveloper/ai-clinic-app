package pe.saniape.app.data

import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.auth.Auth
import io.github.jan.supabase.postgrest.Postgrest
import io.github.jan.supabase.realtime.Realtime
import kotlin.time.Duration.Companion.seconds

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
    // En debug apunta al Next.js local (10.0.2.2:3000); en release a producción.
    // Lo resuelve cada plataforma vía BuildConfig (ver siteUrlPlataforma).
    val SITE_URL: String get() = siteUrlPlataforma()

    // anon key (pública) — idéntica a la de la web. RLS protege los datos.
    const val ANON_KEY =
        "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6InJpZ29ocHVtbmRtbGJ1Zm5nY2VkIiwicm9sZSI6ImFub24iLCJpYXQiOjE3ODExOTEwODksImV4cCI6MjA5Njc2NzA4OX0.EYWO4z_j9CSqcuC9oOn8LoBtM-zJ4A-t3iWohSLAexI"

    val client: SupabaseClient by lazy {
        createSupabaseClient(
            supabaseUrl = URL,
            supabaseKey = ANON_KEY,
        ) {
            // 6s en vez de los 10s por defecto. Sin señal, CADA lectura esperaba 10s antes
            // de rendirse, y una pantalla dispara varias: la app se sentía congelada casi
            // medio minuto. 6s sigue alcanzando de sobra en una red móvil lenta real (las
            // consultas normales responden en <1s), pero el fallo se nota rápido y la
            // pantalla puede ofrecer "Reintentar" en vez de quedarse muda.
            requestTimeout = 6.seconds
            install(Auth)
            install(Postgrest)
            // Realtime: para el auto-refresco en vivo de la agenda (RealtimeAgenda).
            // Best-effort — si no conecta, las lecturas normales siguen funcionando.
            install(Realtime)
        }
    }
}

/**
 * Base del sitio web según la plataforma/variante de build.
 * Android: debug → http://10.0.2.2:3000 (Next.js local), release → producción.
 */
expect fun siteUrlPlataforma(): String