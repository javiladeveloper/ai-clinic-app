package pe.saniape.app.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.FileProvider
import java.io.File

/**
 * Android: TakePicture escribe la foto a un archivo temporal en cache (vía FileProvider) y,
 * al confirmar, se leen los bytes y se borra el temporal. Resolución completa de la cámara
 * (TakePicturePreview solo da un thumbnail — no sirve para fotos clínicas).
 */
@Composable
actual fun recordarCamaraFoto(onFoto: (ArchivoSeleccionado) -> Unit): () -> Unit {
    val context = LocalContext.current
    // Archivo temporal reutilizable por sesión de captura.
    val estado = remember { object { var archivo: File? = null; var uri: android.net.Uri? = null } }

    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok ->
        val f = estado.archivo
        if (ok && f != null && f.exists()) {
            try {
                val bytes = f.readBytes()
                if (bytes.isNotEmpty()) {
                    onFoto(ArchivoSeleccionado(nombre = "foto-${System.currentTimeMillis()}.jpg", bytes = bytes, mime = "image/jpeg"))
                }
            } catch (_: Exception) { /* foto ilegible: se ignora */ }
        }
        f?.delete()
        estado.archivo = null
    }

    return lanzar@{
        try {
            val dir = File(context.cacheDir, "fotos_camara").apply { mkdirs() }
            val f = File(dir, "captura-${System.currentTimeMillis()}.jpg")
            val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", f)
            estado.archivo = f
            estado.uri = uri
            launcher.launch(uri)
        } catch (_: Exception) { /* sin cámara/provider: no hace nada */ }
    }
}
