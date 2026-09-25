package pe.saniape.app.ui.clinica.odontologia

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.view.WindowManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner

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
 *
 * MANOS LIBRES (`iniciarContinuo`): el reconocedor del sistema termina solo en
 * cada silencio largo. En modo continuo, al terminar (con resultado o con "no
 * te escuché") se vuelve a arrancar solo, así que lo dicho se va SUMANDO y el
 * médico no tiene que tocar nada. Mientras dura, la pantalla queda encendida
 * (FLAG_KEEP_SCREEN_ON): si se apaga, Android corta el micrófono.
 *
 * Batería y bucles: el reenganche espera [REENGANCHE_MS] (el servicio de
 * Google necesita soltar el micrófono; reengancharlo antes da ERROR_CLIENT /
 * RECOGNIZER_BUSY y entraba en un bucle rápido). Si falla varias veces SEGUIDAS
 * se espera cada vez más ([esperaTrasFallo]) y a las [MAX_FALLOS_SEGUIDOS] se
 * corta con un aviso. Si pasa [MAX_SILENCIOS_SEGUIDOS] ciclos sin oír a nadie
 * (unos minutos), también se pausa: nadie está dictando y el micrófono + la
 * pantalla encendida gastan batería. Al salir de la app (ON_STOP) se detiene.
 *
 * Sin servicio de primer plano a propósito: el reconocimiento corre en el
 * proceso de Google, no en el nuestro, así que un servicio nuestro no lo
 * mantendría vivo con la pantalla bloqueada; y exigiría declarar
 * FOREGROUND_SERVICE_MICROPHONE en Play Console (Android 14). Pantalla
 * encendida resuelve el caso real (celular apoyado a un costado).
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
    // Estado del modo manos libres, compartido entre el listener y los botones.
    val estado = remember { EstadoDictado() }
    val handler = remember { Handler(Looper.getMainLooper()) }
    val actividad = remember(context) { context.buscarActividad() }

    fun pantallaEncendida(si: Boolean) {
        val w = actividad?.window ?: return
        if (si) w.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        else w.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    }

    fun intentEscuchar() = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
        putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
        putExtra(RecognizerIntent.EXTRA_LANGUAGE, "es-PE")
        putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
        // Un dictado de odontograma lleva pausas entre pieza y pieza: sin
        // esto el reconocedor corta a los ~2 s de silencio.
        putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 3500L)
        putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, 3500L)
    }

    fun arrancar() {
        runCatching { reconocedor?.startListening(intentEscuchar()) }
            .onFailure { error("No se pudo usar el dictado. Puedes escribirlo.") }
    }

    /**
     * Modo continuo: vuelve a escuchar tras [demora] ms, sin perder lo dicho.
     * Nunca hay más de UN reenganche pendiente (onResults y onError pueden
     * llegar seguidos): se quita el anterior antes de programar otro.
     */
    fun reengancharSiContinuo(demora: Long = REENGANCHE_MS, cancelarAntes: Boolean = false): Boolean {
        if (!estado.continuo) return false
        handler.removeCallbacksAndMessages(null)
        handler.postDelayed({
            if (estado.continuo) {
                if (cancelarAntes) runCatching { reconocedor?.cancel() }
                arrancar()
            }
        }, demora)
        return true
    }

    fun terminar() {
        val eraContinuo = estado.continuo
        estado.continuo = false
        estado.fallosSeguidos = 0
        estado.silenciosSeguidos = 0
        handler.removeCallbacksAndMessages(null)
        runCatching { reconocedor?.stopListening() }
        if (eraContinuo) pantallaEncendida(false)
        escuchando(false)
    }

    // Al salir de la app (otra app, bloqueo, botón de inicio) se deja de
    // escuchar: sin pantalla Android corta el micrófono igual, y los reintentos
    // en segundo plano solo gastarían batería.
    val ciclo = LocalLifecycleOwner.current
    DisposableEffect(ciclo) {
        val observador = LifecycleEventObserver { _, evento ->
            if (evento == Lifecycle.Event.ON_STOP) terminar()
        }
        ciclo.lifecycle.addObserver(observador)
        onDispose { ciclo.lifecycle.removeObserver(observador) }
    }

    DisposableEffect(reconocedor) {
        reconocedor?.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) { escuchando(true) }
            // Alguien habla: el ciclo funciona, se reinician los contadores.
            override fun onBeginningOfSpeech() { estado.fallosSeguidos = 0; estado.silenciosSeguidos = 0 }
            override fun onRmsChanged(rmsdB: Float) {}
            override fun onBufferReceived(buffer: ByteArray?) {}
            // En continuo el indicador sigue prendido: se va a reenganchar.
            override fun onEndOfSpeech() { if (!estado.continuo) escuchando(false) }
            override fun onEvent(eventType: Int, params: Bundle?) {}

            override fun onPartialResults(partialResults: Bundle?) {
                partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    ?.firstOrNull()?.let { texto(it, false) }
            }

            override fun onResults(results: Bundle?) {
                val frase = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()
                frase?.let { texto(it, true) }
                estado.fallosSeguidos = 0
                if (!frase.isNullOrBlank()) estado.silenciosSeguidos = 0
                if (!reengancharSiContinuo()) escuchando(false)
            }

            override fun onError(codigo: Int) {
                // Silencio o "no entendí" en manos libres: no es un error, se
                // sigue escuchando. Ocupado: se reintenta un poco después.
                if (estado.continuo) {
                    when (codigo) {
                        // Silencio: normal en manos libres (entre pieza y pieza).
                        // Pero muchos seguidos = nadie dicta: se pausa.
                        SpeechRecognizer.ERROR_NO_MATCH,
                        SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> {
                            estado.silenciosSeguidos++
                            if (estado.silenciosSeguidos < MAX_SILENCIOS_SEGUIDOS) { reengancharSiContinuo(); return }
                            terminar()
                            error("Dictado en pausa: no se escuchó nada en un rato. Toca el micrófono para seguir.")
                            return
                        }
                        // Fallos del reconocedor: reintento con espera creciente
                        // (0,5 s → 1 s → 2 s → 4 s) y tope, nunca un bucle rápido.
                        SpeechRecognizer.ERROR_CLIENT,
                        SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> {
                            estado.fallosSeguidos++
                            if (estado.fallosSeguidos < MAX_FALLOS_SEGUIDOS) {
                                reengancharSiContinuo(
                                    esperaTrasFallo(estado.fallosSeguidos),
                                    cancelarAntes = codigo == SpeechRecognizer.ERROR_RECOGNIZER_BUSY,
                                )
                                return
                            }
                            terminar()
                            error("El dictado se detuvo: el micrófono no respondía. Toca para intentar de nuevo.")
                            return
                        }
                    }
                    // Lo demás (sin red, sin permiso) corta el manos libres.
                    terminar()
                } else {
                    escuchando(false)
                    // ERROR_CLIENT llega tras detener a mano: no es un fallo.
                    if (codigo == SpeechRecognizer.ERROR_CLIENT) return
                }
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
        onDispose {
            estado.continuo = false
            handler.removeCallbacksAndMessages(null)
            pantallaEncendida(false)
            reconocedor?.destroy()
        }
    }

    fun empezar(continuo: Boolean) {
        handler.removeCallbacksAndMessages(null)
        estado.fallosSeguidos = 0
        estado.silenciosSeguidos = 0
        estado.continuo = continuo
        if (continuo) { pantallaEncendida(true); escuchando(true) }
        arrancar()
    }

    val permiso = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
        if (ok) empezar(estado.pedidoContinuo)
        else error("Sin permiso del micrófono no se puede dictar. Puedes escribirlo.")
    }

    return remember(reconocedor) {
        object : ControlDictado {
            override val disponible = disponible

            private fun pedir(continuo: Boolean) {
                if (reconocedor == null) { error("Este teléfono no tiene dictado por voz. Puedes escribirlo."); return }
                val concedido = ContextCompat.checkSelfPermission(
                    context, Manifest.permission.RECORD_AUDIO,
                ) == PackageManager.PERMISSION_GRANTED
                if (concedido) empezar(continuo)
                else { estado.pedidoContinuo = continuo; permiso.launch(Manifest.permission.RECORD_AUDIO) }
            }

            override fun iniciar() = pedir(continuo = false)
            override fun iniciarContinuo() = pedir(continuo = true)
            override fun detener() = terminar()
        }
    }
}

/** Estado mutable del dictado (fuera de Compose: lo tocan callbacks del sistema). */
private class EstadoDictado {
    @Volatile var continuo = false
    @Volatile var pedidoContinuo = false
    /** ERROR_CLIENT / RECOGNIZER_BUSY seguidos, sin una frase buena en medio. */
    @Volatile var fallosSeguidos = 0
    /** Ciclos seguidos sin oír a nadie (NO_MATCH / SPEECH_TIMEOUT). */
    @Volatile var silenciosSeguidos = 0
}

/** Pausa antes de volver a escuchar tras una frase o un silencio. */
private const val REENGANCHE_MS = 400L

/** Fallos seguidos del reconocedor antes de rendirse. */
private const val MAX_FALLOS_SEGUIDOS = 5

/**
 * Ciclos seguidos sin voz antes de pausar el manos libres. Cada ciclo dura lo
 * que el reconocedor espera (~5-8 s): unos 3 minutos sin que nadie hable.
 */
private const val MAX_SILENCIOS_SEGUIDOS = 25

/** 500 ms, 1 s, 2 s, 4 s… (tope 4 s). */
private fun esperaTrasFallo(n: Int): Long = (500L shl (n - 1).coerceIn(0, 3))

/** La Activity detrás del contexto de Compose (para mantener la pantalla encendida). */
private fun Context.buscarActividad(): Activity? {
    var c: Context? = this
    while (c is ContextWrapper) {
        if (c is Activity) return c
        c = c.baseContext
    }
    return null
}
