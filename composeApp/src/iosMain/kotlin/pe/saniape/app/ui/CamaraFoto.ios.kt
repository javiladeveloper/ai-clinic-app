package pe.saniape.app.ui

import androidx.compose.runtime.Composable

/**
 * iOS: tomar foto con la cámara.
 *
 * TODO(iOS Fase 2): implementar con UIImagePickerController(sourceType = .camera)
 * presentado sobre el rootViewController; en didFinishPickingMedia extraer la UIImage,
 * pasarla a JPEG y llamar onFoto(ArchivoSeleccionado(...)). Requiere NSCameraUsageDescription
 * en el Info.plist del iosApp.
 *
 * Por ahora (Fase 1: "que abra en el simulador") es un no-op: el botón de cámara
 * no hará nada en iOS hasta Fase 2. No rompe la compilación ni la app.
 */
@Composable
actual fun recordarCamaraFoto(onFoto: (ArchivoSeleccionado) -> Unit): () -> Unit = {
    // TODO(iOS Fase 2): presentar UIImagePickerController con la cámara.
}
