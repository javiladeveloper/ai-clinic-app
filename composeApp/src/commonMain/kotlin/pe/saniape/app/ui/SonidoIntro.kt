package pe.saniape.app.ui

import androidx.compose.runtime.Composable

/**
 * Sonido "shimmer" de marca al abrir la intro (igual que la web con Web Audio,
 * sin archivo mp3). expect/actual: en Android se sintetiza con AudioTrack.
 */
@Composable
expect fun recordarSonidoIntro(): () -> Unit