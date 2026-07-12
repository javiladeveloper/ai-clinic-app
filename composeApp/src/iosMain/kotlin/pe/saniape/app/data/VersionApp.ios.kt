package pe.saniape.app.data

import platform.Foundation.NSBundle

/** iOS: la versión sale del CFBundleVersion del bundle principal (Info.plist). */
actual object VersionApp {
    actual val codigo: Int =
        (NSBundle.mainBundle.objectForInfoDictionaryKey("CFBundleVersion") as? String)
            ?.toIntOrNull() ?: 0
    actual val nombre: String =
        (NSBundle.mainBundle.objectForInfoDictionaryKey("CFBundleShortVersionString") as? String)
            ?: "—"
    actual val plataforma: String = "ios"
}
