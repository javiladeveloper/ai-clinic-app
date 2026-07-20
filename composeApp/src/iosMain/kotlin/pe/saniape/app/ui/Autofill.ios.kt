package pe.saniape.app.ui

import androidx.compose.ui.Modifier

/**
 * iOS: sin efecto por ahora.
 *
 * El llavero de iOS rellena a través de `UITextContentType` en la vista nativa, y
 * Compose Multiplatform 1.7.3 no lo expone. Cuando se suba a una versión que traiga
 * la API `ContentType` (pública desde Compose 1.8), este actual y el de Android se
 * pueden reemplazar por una sola implementación común.
 *
 * No rompe nada: en iOS el login sigue funcionando escribiendo las credenciales.
 */
actual fun Modifier.autofill(tipo: TipoCampo, onRelleno: (String) -> Unit): Modifier = this
