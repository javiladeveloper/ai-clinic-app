package pe.saniape.app.ui.clinica.odontologia

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat

/**
 * Android: SpeechRecognizer del sistema, en español de Perú.
 *
 * - Pide RECORD_AUDIO la primera vez que se toca el micrófono, no al abrir la
 *   pantalla: pedir un permiso sin que el usuario sepa para qué hace que lo
 *   niegue.
 * - `isRecognitionAvailable` necesita el `<queries>` de RecognitionService en
 *   el manifest (Android 11+). Sin él devuelve false en casi todos los
 *   teléfonos modernos y el dictado parecería roto.
 * - Se destruye al salir de la pantalla: un reconocedor vivo retiene el
 *   micrófono y otras apps no lo pueden usar.
 */
@Composable
actual fun recordarReconocedorVoz(
    onTexto: (texto: String, final: Boolean) -> Unit,
    onEscuchando: (Boolean) -> Unit,
    onError: (String) -> Unit,
): ControlDictado {
    val context = LocalContext.current
    val texto by rememberUpdatedState(onTexto)
    val escuchando by rememberUpdatedState(onEscuchando)
    val error by rememberUpdatedState(onError)

    val disponible = remember { SpeechRecognizer.isRecognitionAvailable(context) }
    val reconocedor = remember {
        if (disponible) SpeechRecognizer.createSpeechRecognizer(context) else null
    }

    DisposableEffect(reconocedor) {
        reconocedor?.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) { escuchando(true) }
            override fun onBeginningOfSpeech() {}
            override fun onRmsChanged(rmsdB: Float) {}
            override fun onBufferReceived(buffer: ByteArray?) {}
            override fun onEndOfSpeech() { escuchando(false) }
            override fun onEvent(eventType: Int, params: Bundle?) {}

            override fun onPartialResults(partialResults: Bundle?) {
                partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    ?.firstOrNull()?.let { texto(it, false) }
            }

            override fun onResults(results: Bundle?) {
                escuchando(false)
                results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    ?.firstOrNull()?.let { texto(it, true) }
            }

            override fun onError(codigo: Int) {
                escuchando(false)
                // "No te entendí" no es un fallo del sistema: se dice distinto
                // para que el odontólogo simplemente repita.
                val msg = when (codigo) {
                    SpeechRecognizer.ERROR_NO_MATCH,
                    SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "No te escuché bien. Toca el micrófono y repite."
                    SpeechRecognizer.ERROR_NETWORK,
                    SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "Sin conexión para el dictado. Puedes escribirlo."
                    SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Falta el permiso del micrófono."
                    SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "El micrófono está ocupado. Intenta de nuevo."
                    else -> "No se pudo usar el dictado (código $codigo)."
                }
                error(msg)
            }
        })
        onDispose { reconocedor?.destroy() }
    }

    fun escuchar() {
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "es-PE")
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            // Un dictado de odontograma lleva pausas entre pieza y pieza: sin
            // esto el reconocedor corta a los ~2 s de silencio.
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 3500L)
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, 3500L)
        }
        reconocedor?.startListening(intent)
    }

    val permiso = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
        if (ok) escuchar() else error("Sin permiso del micrófono no se puede dictar. Puedes escribirlo.")
    }

    return remember(reconocedor) {
        object : ControlDictado {
            override val disponible = disponible
            override fun iniciar() {
                if (reconocedor == null) { error("Este teléfono no tiene dictado por voz. Puedes escribirlo."); return }
                val concedido = ContextCompat.checkSelfPermission(
                    context, Manifest.permission.RECORD_AUDIO,
                ) == PackageManager.PERMISSION_GRANTED
                if (concedido) escuchar() else permiso.launch(Manifest.permission.RECORD_AUDIO)
            }
            override fun detener() { reconocedor?.stopListening() }
        }
    }
}
