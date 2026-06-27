package pe.saniape.app.ui

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import kotlin.concurrent.thread
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.sin

/**
 * Sintetiza un "shimmer" de marca: tres tonos suaves ascendentes (acorde) con
 * envolvente de campana, mezclados, a 44.1kHz. Sin archivo de audio.
 */
@Composable
actual fun recordarSonidoIntro(): () -> Unit = remember {
    {
        thread(isDaemon = true) {
            try { reproducirShimmer() } catch (_: Exception) { /* sin audio: no pasa nada */ }
        }
    }
}

private fun reproducirShimmer() {
    val sampleRate = 44100
    val duracion = 1.1                       // segundos
    val n = (sampleRate * duracion).toInt()
    val buffer = ShortArray(n)

    // Acorde ascendente tipo "campanita" (notas en Hz). Cada una entra escalonada.
    val notas = listOf(
        Nota(freq = 587.33, inicio = 0.00, vol = 0.5),  // Re5
        Nota(freq = 783.99, inicio = 0.12, vol = 0.45), // Sol5
        Nota(freq = 1046.50, inicio = 0.24, vol = 0.4), // Do6
        Nota(freq = 1318.51, inicio = 0.40, vol = 0.32),// Mi6
    )

    for (i in 0 until n) {
        val t = i.toDouble() / sampleRate
        var s = 0.0
        for (nota in notas) {
            if (t >= nota.inicio) {
                val tl = t - nota.inicio
                // Envolvente: ataque rápido + caída exponencial (campana).
                val env = exp(-3.2 * tl) * (1 - exp(-40 * tl))
                s += sin(2 * PI * nota.freq * tl) * env * nota.vol
                // Armónico suave para brillo
                s += sin(2 * PI * nota.freq * 2 * tl) * env * nota.vol * 0.12
            }
        }
        // Fade-out global al final
        val fadeGlobal = if (t > duracion - 0.2) ((duracion - t) / 0.2).coerceIn(0.0, 1.0) else 1.0
        val v = (s * fadeGlobal * 0.6).coerceIn(-1.0, 1.0)
        buffer[i] = (v * Short.MAX_VALUE).toInt().toShort()
    }

    val track = AudioTrack.Builder()
        .setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
                .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                .build()
        )
        .setAudioFormat(
            AudioFormat.Builder()
                .setSampleRate(sampleRate)
                .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                .build()
        )
        .setBufferSizeInBytes(buffer.size * 2)
        .setTransferMode(AudioTrack.MODE_STATIC)
        .build()

    track.write(buffer, 0, buffer.size)
    track.play()
    // Liberar tras reproducir
    Thread.sleep((duracion * 1000).toLong() + 200)
    try { track.stop(); track.release() } catch (_: Exception) {}
}

private data class Nota(val freq: Double, val inicio: Double, val vol: Double)