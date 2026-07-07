package pe.saniape.app.ui

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.useContents
import kotlinx.cinterop.usePinned
import platform.CoreGraphics.CGRectMake
import platform.CoreGraphics.CGSizeMake
import platform.Foundation.NSData
import platform.Foundation.create
import platform.UIKit.UIGraphicsBeginImageContextWithOptions
import platform.UIKit.UIGraphicsEndImageContext
import platform.UIKit.UIGraphicsGetImageFromCurrentImageContext
import platform.UIKit.UIImage
import platform.UIKit.UIImageJPEGRepresentation
import platform.posix.memcpy

/**
 * iOS: comprime la imagen con UIImage. Redimensiona al lado máximo y re-codifica a JPEG.
 * UIImage ya aplica la orientación EXIF al dibujar, así que no hace falta corregir rotación
 * a mano (a diferencia de Android). Si algo falla o no reduce, devuelve el original.
 */
@OptIn(ExperimentalForeignApi::class)
actual fun comprimirImagen(archivo: ArchivoSeleccionado, maxLado: Int, calidad: Int): ArchivoSeleccionado {
    return try {
        val data = archivo.bytes.toNSData()
        val original = UIImage.imageWithData(data) ?: return archivo
        val w = original.size.useContents { width }
        val h = original.size.useContents { height }
        if (w <= 0.0 || h <= 0.0) return archivo

        val escala = minOf(maxLado / w, maxLado / h, 1.0)   // nunca agranda
        val nuevoW = w * escala
        val nuevoH = h * escala

        UIGraphicsBeginImageContextWithOptions(CGSizeMake(nuevoW, nuevoH), false, 1.0)
        original.drawInRect(CGRectMake(0.0, 0.0, nuevoW, nuevoH))
        val redimensionada = UIGraphicsGetImageFromCurrentImageContext()
        UIGraphicsEndImageContext()
        redimensionada ?: return archivo

        val jpeg = UIImageJPEGRepresentation(redimensionada, calidad / 100.0) ?: return archivo
        val bytes = jpeg.toByteArray()
        if (bytes.isEmpty() || bytes.size >= archivo.bytes.size) return archivo

        val nombre = archivo.nombre.substringBeforeLast('.', archivo.nombre) + ".jpg"
        ArchivoSeleccionado(nombre = nombre, bytes = bytes, mime = "image/jpeg")
    } catch (_: Throwable) {
        archivo
    }
}

@OptIn(ExperimentalForeignApi::class)
private fun ByteArray.toNSData(): NSData = usePinned { pin ->
    NSData.create(bytes = if (isNotEmpty()) pin.addressOf(0) else null, length = size.toULong())
}

@OptIn(ExperimentalForeignApi::class)
private fun NSData.toByteArray(): ByteArray {
    val len = length.toInt()
    val out = ByteArray(len)
    if (len > 0) out.usePinned { pin -> memcpy(pin.addressOf(0), bytes, length) }
    return out
}
