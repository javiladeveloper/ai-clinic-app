package pe.saniape.app.data

import io.github.jan.supabase.auth.AuthConfig
import kotlin.experimental.ExperimentalNativeApi
import kotlin.native.Platform

/**
 * iOS: base del sitio web (equivalente a lo que en Android hace BuildConfig.SITE_URL).
 *  - release → https://www.saniape.com
 *  - debug   → prod por defecto, para poder probar en el simulador SIN levantar el
 *              Next.js local. Si corres el backend local en la Mac (npm run dev), pon
 *              USAR_BACKEND_LOCAL = true → http://localhost:3000 (la excepción ATS para
 *              localhost ya está en Info.plist).
 */
private const val USAR_BACKEND_LOCAL = false

@OptIn(ExperimentalNativeApi::class)
actual fun siteUrlPlataforma(): String =
    if (Platform.isDebugBinary && USAR_BACKEND_LOCAL) "http://localhost:3000"
    else "https://www.saniape.com"

/**
 * iOS: el login con Google vuelve a `saniape://login#access_token=…` (ver AuthGoogle.ios.kt).
 * `handleDeeplinks` solo acepta la URL si coincide con este scheme/host; sin esto la
 * descartaba en silencio y el login "terminaba bien" pero sin sesión.
 */
actual fun AuthConfig.configurarAuthPlataforma() {
    scheme = "saniape"
    host = "login"
}
