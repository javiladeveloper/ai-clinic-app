package pe.saniape.app.ui

import androidx.compose.runtime.Composable

/** Un archivo elegido por el usuario (bytes en memoria — los archivos clínicos son ≤15 MB). */
data class ArchivoSeleccionado(
    val nombre: String,
    val bytes: ByteArray,
    val mime: String?,
)

/**
 * Selector de archivos nativo (PDF/imagen). Devuelve una lambda que, al invocarse,
 * abre el picker del sistema; cuando el usuario elige, llama [onElegido].
 * expect/actual: en Android usa ActivityResultContracts.GetContent.
 */
@Composable
expect fun recordarSelectorArchivo(onElegido: (ArchivoSeleccionado) -> Unit): () -> Unit
