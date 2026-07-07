package pe.saniape.app.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.get
import kotlinx.cinterop.usePinned
import platform.AVFAudio.AVAudioEngine
import platform.AVFAudio.AVAudioFormat
import platform.AVFAudio.AVAudioPCMBuffer
import platform.AVFAudio.AVAudioPlayerNode
import platform.AVFAudio.AVAudioSession
import platform.AVFAudio.AVAudioSessionCategoryAmbient
import platform.AVFAudio.setActive
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.sin

/**
 * iOS: mismo "shimmer" de marca que Android, sintetizado a PCM y reproducido con
 * AVAudioEngine. Sin archivo de audio. Cualquier fallo de audio es silencioso
 * (la intro sigue funcionando visualmente).
 */
@Composable
actual fun recordarSonidoIntro(): () -> Unit = remember {
    {
        try { reproducirShimmerIos() } catch (_: Throwable) { /* sin audio: no pasa nada */ }
    }
}

private data class Nota(val freq: Double, val inicio: Double, val vol: Double)

@OptIn(ExperimentalForeignApi::class)
private fun reproducirShimmerIos() {
    val sampleRate = 44100.0
    val duracion = 1.1
    val n = (sampleRate * duracion).toInt()

    val notas = listOf(
        Nota(freq = 587.33, inicio = 0.00, vol = 0.5),
        Nota(freq = 783.99, inicio = 0.12, vol = 0.45),
        Nota(freq = 1046.50, inicio = 0.24, vol = 0.4),
        Nota(freq = 1318.51, inicio = 0.40, vol = 0.32),
    )

    val muestras = FloatArray(n)
    for (i in 0 until n) {
        val t = i / sampleRate
        var s = 0.0
        for (nota in notas) {
            if (t >= nota.inicio) {
                val tl = t - nota.inicio
                val env = exp(-3.2 * tl) * (1 - exp(-40 * tl))
                s += sin(2 * PI * nota.freq * tl) * env * nota.vol
                s += sin(2 * PI * nota.freq * 2 * tl) * env * nota.vol * 0.12
            }
        }
        val fadeGlobal = if (t > duracion - 0.2) ((duracion - t) / 0.2).coerceIn(0.0, 1.0) else 1.0
        muestras[i] = (s * fadeGlobal * 0.6).coerceIn(-1.0, 1.0).toFloat()
    }

    // Categoría Ambient: respeta el interruptor de silencio del iPhone y no corta otra música.
    val session = AVAudioSession.sharedInstance()
    session.setCategory(AVAudioSessionCategoryAmbient, null)
    session.setActive(true, null)

    val engine = AVAudioEngine()
    val player = AVAudioPlayerNode()
    val formato = AVAudioFormat(standardFormatWithSampleRate = sampleRate, channels = 1u)
    engine.attachNode(player)
    engine.connect(player, engine.mainMixerNode, formato)

    val buffer = AVAudioPCMBuffer(pCMFormat = formato, frameCapacity = n.toUInt())!!
    buffer.frameLength = n.toUInt()
    val canal = buffer.floatChannelData!!.get(0)!!
    muestras.usePinned { pin ->
        platform.posix.memcpy(canal, pin.addressOf(0), (n * 4).toULong())
    }

    engine.startAndReturnError(null)
    player.scheduleBuffer(buffer, null)
    player.play()
    // El engine se detiene solo al liberarse; la reproducción es corta (~1.1s).
}
