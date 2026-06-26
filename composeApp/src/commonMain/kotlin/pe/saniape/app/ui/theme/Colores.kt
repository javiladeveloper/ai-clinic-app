package pe.saniape.app.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * Paleta de marca Sania — ÚNICA fuente de verdad de los colores.
 *
 * Espeja las CSS vars de la web (globals.css). NADIE debe escribir Color(0xFF...)
 * fuera de este archivo: si falta un color, se agrega aquí con nombre semántico.
 *
 * Se exponen vía [SaniaColors] (en Tema.kt) para poder soportar tema claro/oscuro
 * a futuro sin tocar las pantallas.
 */
internal object Paleta {
    // ── Marca (navy / lavanda / arena) ───────────────────────────────
    val Navy700 = Color(0xFF1E2D5E)   // sidebar, textos muy oscuros, statusbar
    val Navy600 = Color(0xFF2C3E7A)   // botón primario, brand
    val Navy500 = Color(0xFF4A5FA8)   // hover, focus
    val Navy100 = Color(0xFFBCC4E8)   // bordes suaves
    val Navy50 = Color(0xFFEEF0F9)    // fondos hover, chips activos

    val Lav300 = Color(0xFF8892C8)    // acentos, sidebar active
    val Lav50 = Color(0xFFEEF0F9)     // fondos suaves

    val Sand = Color(0xFFF7F8FC)      // fondo principal
    val Borde = Color(0xFFDDE1F0)     // bordes de cards/tablas
    val Muted = Color(0xFF6B7280)     // textos secundarios

    val Blanco = Color(0xFFFFFFFF)
    val Texto = Color(0xFF1F2937)     // texto principal sobre fondo claro

    // ── Semánticos (estado) ──────────────────────────────────────────
    val Green = Color(0xFF16A34A)
    val GreenBg = Color(0xFFDCFCE7)
    val Amber = Color(0xFFD97706)
    val AmberBg = Color(0xFFFEF3C7)
    val Red = Color(0xFFDC2626)
    val RedBg = Color(0xFFFEE2E2)
    val Blue = Color(0xFF1D6FA8)
    val BlueBg = Color(0xFFDBEAFE)
    val Purple = Color(0xFF7C3AED)
    val PurpleBg = Color(0xFFEDE9FE)
    val Teal = Color(0xFF0F766E)
    val TealBg = Color(0xFFCCFBF1)
}