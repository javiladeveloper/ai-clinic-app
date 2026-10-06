package pe.saniape.app.data.offline

/**
 * Archivos binarios en el almacenamiento PRIVADO de la app (no en memoria ni en
 * la galería): hoy, las fotos de una sesión completada sin señal, que esperan
 * ahí hasta subirse ([FotosPendientes]). Sobreviven al cierre de la app.
 *
 * Android: `filesDir/fotos_pendientes` (necesita el Context de ContextoApp,
 * que se inicia en Application.onCreate). iOS: Application Support.
 * Todo devuelve false/null ante cualquier fallo: nunca lanza.
 */
expect object ArchivosLocales {
    fun guardar(nombre: String, bytes: ByteArray): Boolean
    fun leer(nombre: String): ByteArray?
    fun borrar(nombre: String)
}
