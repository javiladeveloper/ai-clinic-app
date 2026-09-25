package pe.saniape.app.ui.clinica.odontologia

import androidx.compose.runtime.Composable

/**
 * El micrófono para dictar el odontograma.
 *
 * Es `expect`: cada plataforma pone el suyo. En Android es el SpeechRecognizer
 * del sistema (reconocimiento de Google, gratis, en español de Perú), que no
 * necesita subir audio a ningún servidor nuestro.
 *
 * Si el teléfono no tiene servicio de voz (algunos sin Google, o iOS por ahora)
 * [disponible] es false y la pantalla ofrece escribir o usar el micrófono del
 * teclado: el parser trabaja igual con texto tipeado.
 */
interface ControlDictado {
    val disponible: Boolean
    fun iniciar()
    fun detener()

    /**
     * Manos libres: el médico deja el celular a un costado y trabaja con las dos
     * manos. Queda escuchando hasta [detener]: en cada pausa el reconocedor se
     * reinicia solo sin perder lo dicho (el del sistema corta a los ~3 s de
     * silencio) y la pantalla no se apaga. Por defecto, igual que [iniciar]
     * (plataformas sin dictado nativo).
     */
    fun iniciarContinuo() { iniciar() }
}

/**
 * [onTexto] recibe lo reconocido; `final` = true cuando la frase terminó (el
 * texto parcial va cambiando mientras se habla). [onEscuchando] prende y apaga
 * el indicador (en modo continuo queda prendido hasta detener). [onError] trae
 * un mensaje para mostrar tal cual.
 */
@Composable
expect fun recordarReconocedorVoz(
    onTexto: (texto: String, final: Boolean) -> Unit,
    onEscuchando: (Boolean) -> Unit,
    onError: (String) -> Unit,
): ControlDictado
