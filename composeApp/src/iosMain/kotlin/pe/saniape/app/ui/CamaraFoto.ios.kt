package pe.saniape.app.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import platform.Foundation.NSDate
import platform.Foundation.timeIntervalSince1970
import platform.UIKit.UIImage
import platform.UIKit.UIImageJPEGRepresentation
import platform.UIKit.UIImagePickerController
import platform.UIKit.UIImagePickerControllerDelegateProtocol
import platform.UIKit.UIImagePickerControllerOriginalImage
import platform.UIKit.UIImagePickerControllerSourceType
import platform.UIKit.UINavigationControllerDelegateProtocol
import platform.darwin.NSObject

/**
 * iOS: tomar foto con la cámara. Presenta UIImagePickerController sobre la UI de Compose;
 * al elegir, convierte la UIImage a JPEG y llama onFoto. En el simulador (sin cámara) cae
 * a la galería, para poder probar el flujo. Requiere NSCameraUsageDescription (ya en Info.plist).
 */
@Composable
actual fun recordarCamaraFoto(onFoto: (ArchivoSeleccionado) -> Unit): () -> Unit {
    val handler = remember { CamaraHandler(onFoto) }
    return { handler.abrir() }
}

private class CamaraHandler(
    private val onFoto: (ArchivoSeleccionado) -> Unit,
) : NSObject(), UIImagePickerControllerDelegateProtocol, UINavigationControllerDelegateProtocol {

    fun abrir() {
        val picker = UIImagePickerController()
        picker.delegate = this
        picker.sourceType =
            if (UIImagePickerController.isSourceTypeAvailable(
                    UIImagePickerControllerSourceType.UIImagePickerControllerSourceTypeCamera,
                )
            ) {
                UIImagePickerControllerSourceType.UIImagePickerControllerSourceTypeCamera
            } else {
                UIImagePickerControllerSourceType.UIImagePickerControllerSourceTypePhotoLibrary
            }
        rootViewController()?.presentViewController(picker, animated = true, completion = null)
    }

    override fun imagePickerController(
        picker: UIImagePickerController,
        didFinishPickingMediaWithInfo: Map<Any?, *>,
    ) {
        val img = didFinishPickingMediaWithInfo[UIImagePickerControllerOriginalImage] as? UIImage
        picker.dismissViewControllerAnimated(true, completion = null)
        val jpeg = img?.let { UIImageJPEGRepresentation(it, 0.9) } ?: return
        val ts = NSDate().timeIntervalSince1970.toLong()
        onFoto(ArchivoSeleccionado(nombre = "foto_$ts.jpg", bytes = jpeg.aByteArray(), mime = "image/jpeg"))
    }

    override fun imagePickerControllerDidCancel(picker: UIImagePickerController) {
        picker.dismissViewControllerAnimated(true, completion = null)
    }
}
