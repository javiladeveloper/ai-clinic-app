package pe.saniape.app.data

import io.github.jan.supabase.auth.AuthConfig
import pe.saniape.app.BuildConfig

/**
 * En Android la base del sitio sale de BuildConfig:
 *  - debug   → http://10.0.2.2:3000 (Next.js local, npm run dev)
 *  - release → https://www.saniape.com
 * (definido en build.gradle.kts via buildConfigField).
 */
actual fun siteUrlPlataforma(): String = BuildConfig.SITE_URL

/** Android: sin ajustes (el login con Google usa ID token, no deep link). */
actual fun AuthConfig.configurarAuthPlataforma() {}