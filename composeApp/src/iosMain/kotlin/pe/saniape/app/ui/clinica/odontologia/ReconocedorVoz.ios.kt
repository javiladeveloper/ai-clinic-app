package pe.saniape.app.ui.clinica.odontologia

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember

/**
 * iOS: todavía sin dictado nativo (SFSpeechRecognizer queda para cuando la app
 * de iOS esté publicada). La pantalla ofrece escribir, o usar el micrófono del
 * teclado de iOS, que el parser entiende igual.
 */
@Composable
actual fun recordarReconocedorVoz(
    onTexto: (texto: String, final: Boolean) -> Unit,
    onEscuchando: (Boolean) -> Unit,
    onError: (String) -> Unit,
): ControlDictado = remember {
    object : ControlDictado {
        override val disponible = false
        override fun iniciar() { onError("En iPhone usa el micrófono del teclado para dictar.") }
        override fun detener() {}
    }
}
