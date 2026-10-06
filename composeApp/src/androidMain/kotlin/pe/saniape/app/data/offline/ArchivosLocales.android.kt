package pe.saniape.app.data.offline

import java.io.File

/** Android: `filesDir/fotos_pendientes` (privado de la app; no lo ve la galería). */
actual object ArchivosLocales {
    private fun dir(): File? = pe.saniape.app.ui.ContextoApp.contexto
        ?.let { File(it.filesDir, "fotos_pendientes").apply { mkdirs() } }

    actual fun guardar(nombre: String, bytes: ByteArray): Boolean = runCatching {
        val d = dir() ?: return false
        val tmp = File(d, "$nombre.tmp")
        tmp.writeBytes(bytes)
        // Escribir y renombrar: un corte a medias no deja una foto truncada con el nombre final.
        tmp.renameTo(File(d, nombre))
    }.getOrDefault(false)

    actual fun leer(nombre: String): ByteArray? = runCatching {
        dir()?.let { File(it, nombre) }?.takeIf { it.isFile }?.readBytes()
    }.getOrNull()

    actual fun borrar(nombre: String) {
        runCatching { dir()?.let { File(it, nombre).delete() } }
    }
}
