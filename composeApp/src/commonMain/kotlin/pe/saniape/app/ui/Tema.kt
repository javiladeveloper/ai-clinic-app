package pe.saniape.app.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import pe.saniape.app.ui.theme.Paleta
import pe.saniape.app.ui.theme.TemaSania as TemaSaniaImpl

/**
 * Puente de compatibilidad: las pantallas existentes usan estos nombres sueltos
 * (Navy, Sand, …). Apuntan a la ÚNICA paleta central [Paleta]. Para colores nuevos,
 * usar `Sania.colors.*` (sistema de diseño) en vez de agregar aquí.
 *
 * El tema real vive en `ui/theme/Tema.kt`.
 */

val Navy: Color get() = Paleta.Navy600
val NavyDark: Color get() = Paleta.Navy700
val Lav: Color get() = Paleta.Lav300
val Sand: Color get() = Paleta.Sand
val BorderColor: Color get() = Paleta.Borde
val Muted: Color get() = Paleta.Muted
val GreenOk: Color get() = Paleta.Green
val Amber: Color get() = Paleta.Amber
val RedDanger: Color get() = Paleta.Red

// Adicionales nombrados (reemplazan literales sueltos en las pantallas)
val TextoPrincipal: Color get() = Paleta.Texto
val Blanco: Color get() = Paleta.Blanco
val Navy50: Color get() = Paleta.Navy50
val GreenBg: Color get() = Paleta.GreenBg
val AmberBg: Color get() = Paleta.AmberBg
val RedBg: Color get() = Paleta.RedBg
val Blue: Color get() = Paleta.Blue

@Composable
fun TemaSania(content: @Composable () -> Unit) = TemaSaniaImpl(content)