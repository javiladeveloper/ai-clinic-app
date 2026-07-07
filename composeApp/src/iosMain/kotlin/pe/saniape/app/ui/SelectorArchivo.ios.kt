package pe.saniape.app.ui

import androidx.compose.runtime.Composable

/**
 * iOS: selector de archivos (PDF/imagen).
 *
 * TODO(iOS Fase 2): implementar con UIDocumentPickerViewController (PDF) y/o PHPickerViewController
 * (fotos de la galería), presentado sobre el rootViewController; leer el archivo elegido a NSData,
 * convertir a ByteArray y llamar onElegido(ArchivoSeleccionado(...)).
 *
 * Por ahora (Fase 1) es un no-op para no bloquear el arranque en el simulador.
 */
@Composable
actual fun recordarSelectorArchivo(onElegido: (ArchivoSeleccionado) -> Unit): () -> Unit = {
    // TODO(iOS Fase 2): presentar UIDocumentPickerViewController / PHPickerViewController.
}
