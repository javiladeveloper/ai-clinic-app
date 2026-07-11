package pe.saniape.app.data

/**
 * Versión de ESTA build en runtime, para comparar con la última disponible en la tienda
 * (endpoint /api/app/version) y mostrar el aviso de actualización.
 * expect/actual: Android lee BuildConfig.VERSION_CODE; iOS lee CFBundleVersion.
 */
expect object VersionApp {
    /** Código de versión de esta build (Android versionCode / iOS CFBundleVersion). */
    val codigo: Int

    /** "android" | "ios" — para elegir qué comparar y a qué tienda mandar. */
    val plataforma: String
}
