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
}

/**
 * [onTexto] recibe lo reconocido; `final` = true cuando la frase terminó (el
 * texto parcial va cambiando mientras se habla). [onEscuchando] prende y apaga
 * el indicador. [onError] trae un mensaje para mostrar tal cual.
 */
@Composable
expect fun recordarReconocedorVoz(
    onTexto: (texto: String, final: Boolean) -> Unit,
    onEscuchando: (Boolean) -> Unit,
    onError: (String) -> Unit,
): ControlDictado
