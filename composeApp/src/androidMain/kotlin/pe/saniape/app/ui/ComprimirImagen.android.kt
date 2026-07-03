package pe.saniape.app.ui

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import androidx.exifinterface.media.ExifInterface
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream

/**
 * Android: decodifica con subsampling (no cargar 8 MB enteros en memoria), aplica la rotación
 * EXIF (las fotos de cámara vienen "acostadas" por metadata que se pierde al re-codificar),
 * escala al lado máximo y re-codifica a JPEG. Si algo falla o no reduce, devuelve el original.
 */
actual fun comprimirImagen(archivo: ArchivoSeleccionado, maxLado: Int, calidad: Int): ArchivoSeleccionado {
    val mime = archivo.mime ?: ""
    if (!mime.startsWith("image/")) return archivo
    if (mime == "image/gif") return archivo   // animados: canvas los rompería

    return try {
        // 1) Solo bordes → factor de subsampling (potencia de 2) para decodificar liviano.
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(archivo.bytes, 0, archivo.bytes.size, bounds)
        val (w, h) = bounds.outWidth to bounds.outHeight
        if (w <= 0 || h <= 0) return archivo
        var sample = 1
        while ((maxOf(w, h) / (sample * 2)) >= maxLado) sample *= 2

        val opts = BitmapFactory.Options().apply { inSampleSize = sample }
        var bmp = BitmapFactory.decodeByteArray(archivo.bytes, 0, archivo.bytes.size, opts) ?: return archivo

        // 2) Rotación EXIF (se aplica al bitmap; el JPEG re-codificado ya sale derecho).
        val orientacion = runCatching {
            ExifInterface(ByteArrayInputStream(archivo.bytes))
                .getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
        }.getOrDefault(ExifInterface.ORIENTATION_NORMAL)
        val grados = when (orientacion) {
            ExifInterface.ORIENTATION_ROTATE_90 -> 90f
            ExifInterface.ORIENTATION_ROTATE_180 -> 180f
            ExifInterface.ORIENTATION_ROTATE_270 -> 270f
            else -> 0f
        }
        if (grados != 0f) {
            val m = Matrix().apply { postRotate(grados) }
            bmp = Bitmap.createBitmap(bmp, 0, 0, bmp.width, bmp.height, m, true)
        }

        // 3) Escala final exacta al lado máximo.
        val lado = maxOf(bmp.width, bmp.height)
        if (lado > maxLado) {
            val escala = maxLado.toFloat() / lado
            bmp = Bitmap.createScaledBitmap(
                bmp, (bmp.width * escala).toInt().coerceAtLeast(1),
                (bmp.height * escala).toInt().coerceAtLeast(1), true)
        }

        // 4) JPEG con la calidad pedida. Si no reduce, quedarse con el original.
        val out = ByteArrayOutputStream()
        bmp.compress(Bitmap.CompressFormat.JPEG, calidad.coerceIn(1, 100), out)
        val comprimido = out.toByteArray()
        if (comprimido.isEmpty() || comprimido.size >= archivo.bytes.size) return archivo

        ArchivoSeleccionado(
            nombre = archivo.nombre.substringBeforeLast('.', archivo.nombre) + ".jpg",
            bytes = comprimido,
            mime = "image/jpeg",
        )
    } catch (_: Exception) {
        archivo   // nunca romper la subida por la compresión
    }
}
