package pe.saniape.app.ui

import androidx.compose.runtime.Composable

/**
 * Tomar una foto con la CÁMARA del dispositivo (fotos clínicas: antes/después, sesión).
 * Devuelve una lambda que abre la cámara; al confirmar la captura llama [onFoto] con los
 * bytes de la imagen. Si el usuario cancela, no llama nada.
 *
 * expect/actual: en Android usa ActivityResultContracts.TakePicture con un archivo temporal
 * vía FileProvider (no requiere permiso CAMERA: delega en la app de cámara del sistema).
 */
@Composable
expect fun recordarCamaraFoto(onFoto: (ArchivoSeleccionado) -> Unit): () -> Unit
