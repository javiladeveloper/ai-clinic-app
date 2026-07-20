package pe.saniape.app.data.offline

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Señal de "volvió la conexión", para que las PANTALLAS se recarguen solas.
 *
 * La cola ya reenvía sus escrituras al volver la red, pero las lecturas que
 * fallaron mientras no había señal quedaban en pantalla como una lista vacía o
 * un "no se pudo cargar": el usuario veía sus pacientes desaparecidos y creía
 * que se había perdido algo, cuando los datos estaban intactos en el servidor.
 *
 * Las pantallas observan [token] (un contador que sube en cada reconexión) y
 * vuelven a pedir sus datos. Es un contador y no un booleano para que dos cortes
 * seguidos disparen dos recargas.
 */
object EstadoRed {
    private val _token = MutableStateFlow(0)

    /** Sube en cada reconexión. Las pantallas lo usan como clave de recarga. */
    val token: StateFlow<Int> = _token

    fun volvioLaRed() { _token.value = _token.value + 1 }
}
