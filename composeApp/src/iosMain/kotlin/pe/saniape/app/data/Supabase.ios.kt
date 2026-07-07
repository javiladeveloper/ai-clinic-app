package pe.saniape.app.data

import kotlin.experimental.ExperimentalNativeApi
import kotlin.native.Platform

/**
 * iOS: la base del sitio depende de si el binario es debug o release
 * (equivalente a lo que en Android hace BuildConfig.SITE_URL):
 *  - debug   → http://localhost:3000 (Next.js local; el simulador comparte el
 *              localhost del Mac, así que aquí NO se usa 10.0.2.2 como en el emulador Android).
 *  - release → https://www.saniape.com
 */
@OptIn(ExperimentalNativeApi::class)
actual fun siteUrlPlataforma(): String =
    if (Platform.isDebugBinary) "http://localhost:3000" else "https://www.saniape.com"
