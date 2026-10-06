package pe.saniape.app.data.offline

import kotlinx.cinterop.ExperimentalForeignApi
import platform.Foundation.NSApplicationSupportDirectory
import platform.Foundation.NSData
import platform.Foundation.NSFileManager
import platform.Foundation.NSSearchPathForDirectoriesInDomains
import platform.Foundation.NSUserDomainMask
import platform.Foundation.dataWithContentsOfFile
import platform.Foundation.writeToFile
import pe.saniape.app.ui.aByteArray
import pe.saniape.app.ui.aNSData

/** iOS: Application Support/fotos_pendientes (privado de la app). */
@OptIn(ExperimentalForeignApi::class)
actual object ArchivosLocales {
    private fun dir(): String? {
        val base = NSSearchPathForDirectoriesInDomains(NSApplicationSupportDirectory, NSUserDomainMask, true)
            .firstOrNull() as? String ?: return null
        val d = "$base/fotos_pendientes"
        NSFileManager.defaultManager.createDirectoryAtPath(d, withIntermediateDirectories = true, attributes = null, error = null)
        return d
    }

    actual fun guardar(nombre: String, bytes: ByteArray): Boolean = runCatching {
        val d = dir() ?: return false
        bytes.aNSData().writeToFile("$d/$nombre", atomically = true)
    }.getOrDefault(false)

    actual fun leer(nombre: String): ByteArray? = runCatching {
        val d = dir() ?: return null
        NSData.dataWithContentsOfFile("$d/$nombre")?.aByteArray()
    }.getOrNull()

    actual fun borrar(nombre: String) {
        runCatching {
            val d = dir() ?: return
            NSFileManager.defaultManager.removeItemAtPath("$d/$nombre", error = null)
        }
    }
}
