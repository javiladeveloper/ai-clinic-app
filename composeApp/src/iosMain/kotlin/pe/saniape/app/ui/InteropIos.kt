package pe.saniape.app.ui

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.usePinned
import platform.Foundation.NSData
import platform.Foundation.create
import platform.UIKit.UIApplication
import platform.UIKit.UIViewController
import platform.posix.memcpy

/** Helpers nativos compartidos por los pickers/mapa de iOS (Fase 2). */

@OptIn(ExperimentalForeignApi::class)
internal fun ByteArray.aNSData(): NSData = usePinned { pin ->
    NSData.create(bytes = if (isNotEmpty()) pin.addressOf(0) else null, length = size.toULong())
}

@OptIn(ExperimentalForeignApi::class)
internal fun NSData.aByteArray(): ByteArray {
    val len = length.toInt()
    val out = ByteArray(len)
    if (len > 0) out.usePinned { pin -> memcpy(pin.addressOf(0), bytes, length) }
    return out
}

/**
 * El view controller más arriba de la jerarquía, para presentar pickers modales
 * (cámara, archivos) sobre la UI de Compose.
 */
internal fun rootViewController(): UIViewController? {
    var vc = UIApplication.sharedApplication.keyWindow?.rootViewController
    while (vc?.presentedViewController != null) vc = vc.presentedViewController
    return vc
}
