package pe.saniape.app.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import platform.Foundation.NSData
import platform.Foundation.NSURL
import platform.Foundation.dataWithContentsOfURL
import platform.UIKit.UIDocumentPickerViewController
import platform.UIKit.UIDocumentPickerDelegateProtocol
import platform.UniformTypeIdentifiers.UTTypeImage
import platform.UniformTypeIdentifiers.UTTypePDF
import platform.darwin.NSObject

/**
 * iOS: selector de archivos (PDF/imagen) para adjuntos clínicos. Presenta
 * UIDocumentPickerViewController sobre la UI de Compose; al elegir, lee el archivo a
 * NSData → ByteArray y llama onElegido. Equivale a ActivityResultContracts.GetContent
 * de Android. Los archivos clínicos son ≤15 MB, así que se cargan en memoria.
 */
@Composable
actual fun recordarSelectorArchivo(onElegido: (ArchivoSeleccionado) -> Unit): () -> Unit {
    val handler = remember { SelectorHandler(onElegido) }
    return { handler.abrir() }
}

private class SelectorHandler(
    private val onElegido: (ArchivoSeleccionado) -> Unit,
) : NSObject(), UIDocumentPickerDelegateProtocol {

    fun abrir() {
        val picker = UIDocumentPickerViewController(
            forOpeningContentTypes = listOf(UTTypePDF, UTTypeImage),
        )
        picker.delegate = this
        picker.allowsMultipleSelection = false
        rootViewController()?.presentViewController(picker, animated = true, completion = null)
    }

    override fun documentPicker(
        controller: UIDocumentPickerViewController,
        didPickDocumentsAtURLs: List<*>,
    ) {
        val url = didPickDocumentsAtURLs.firstOrNull() as? NSURL ?: return
        // Los archivos del picker vienen "security-scoped": hay que abrir el acceso.
        val abierto = url.startAccessingSecurityScopedResource()
        val data = NSData.dataWithContentsOfURL(url)
        if (abierto) url.stopAccessingSecurityScopedResource()
        data ?: return
        val nombre = url.lastPathComponent ?: "archivo"
        onElegido(ArchivoSeleccionado(nombre = nombre, bytes = data.aByteArray(), mime = mimeDe(nombre)))
    }

    override fun documentPickerWasCancelled(controller: UIDocumentPickerViewController) {}
}

private fun mimeDe(nombre: String): String? = when (nombre.substringAfterLast('.', "").lowercase()) {
    "pdf" -> "application/pdf"
    "jpg", "jpeg" -> "image/jpeg"
    "png" -> "image/png"
    "heic" -> "image/heic"
    "webp" -> "image/webp"
    else -> null
}
