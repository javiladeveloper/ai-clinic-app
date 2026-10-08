package pe.saniape.app.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext

/**
 * Android: abre el selector de documentos del sistema (PDF e imágenes). Lee los bytes
 * del Uri elegido y resuelve nombre + mime. Los archivos clínicos son ≤15 MB → en memoria.
 */
@Composable
actual fun recordarSelectorArchivo(mime: String, onElegido: (ArchivoSeleccionado) -> Unit): () -> Unit {
    val context = LocalContext.current
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        try {
            val resolver = context.contentResolver
            val tipo = resolver.getType(uri)
            // Nombre del archivo (columna DISPLAY_NAME del proveedor).
            var nombre = "documento"
            resolver.query(uri, null, null, null, null)?.use { cursor ->
                val idx = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                if (idx >= 0 && cursor.moveToFirst()) cursor.getString(idx)?.let { nombre = it }
            }
            val bytes = resolver.openInputStream(uri)?.use { it.readBytes() } ?: return@rememberLauncherForActivityResult
            onElegido(ArchivoSeleccionado(nombre = nombre, bytes = bytes, mime = tipo))
        } catch (_: Exception) { /* archivo ilegible: se ignora */ }
    }
    // Por defecto acepta imágenes y PDF; quien pide solo imágenes pasa "image/*".
    return { launcher.launch(mime) }
}
