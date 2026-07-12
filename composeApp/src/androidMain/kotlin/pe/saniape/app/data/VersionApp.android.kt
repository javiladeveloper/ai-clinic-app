package pe.saniape.app.data

import pe.saniape.app.BuildConfig

/** Android: la versión sale de BuildConfig (generado por Gradle desde versionCode). */
actual object VersionApp {
    actual val codigo: Int = BuildConfig.VERSION_CODE
    actual val nombre: String = BuildConfig.VERSION_NAME
    actual val plataforma: String = "android"
}
