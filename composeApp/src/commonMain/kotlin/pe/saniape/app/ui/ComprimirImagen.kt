package pe.saniape.app.ui

/**
 * Comprime una imagen ANTES de subirla (fotos clínicas): redimensiona al lado máximo y
 * re-codifica a JPEG. Una foto de cámara de ~5-8 MB baja a ~200-500 KB sin perder valor
 * clínico — no satura el storage (mismo criterio que la web: 1600px / calidad 0.7).
 *
 * Si la entrada no es una imagen rasterizable o comprimir no reduce, devuelve el original.
 * expect/actual: en Android usa Bitmap + corrección de rotación EXIF (las fotos de cámara
 * suelen venir rotadas por metadata).
 */
expect fun comprimirImagen(archivo: ArchivoSeleccionado, maxLado: Int = 1600, calidad: Int = 70): ArchivoSeleccionado
