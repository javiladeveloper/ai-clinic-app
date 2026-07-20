package pe.saniape.app.ui

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Indicador GLOBAL de "guardando…".
 *
 * Las gestiones (completar/crear/editar/borrar sesión, pagos, tratamientos…)
 * encadenan varias operaciones en el servidor y tardan un par de segundos. Antes
 * el modal se cerraba al instante y la pantalla quedaba igual, sin señal de que
 * algo estaba pasando: se sentía colgado.
 *
 * Con esto, cualquier acción envuelta en [conIndicador] enciende una barra en la
 * parte superior mientras dura, y la apaga al terminar. Un solo lugar en la UI
 * cubre TODAS las gestiones, sin tocar cada modal.
 */
object EstadoGuardando {
    private val _enCurso = MutableStateFlow(0)

    /** Cuántas operaciones se están guardando ahora mismo (0 = nada en curso). */
    val enCurso: StateFlow<Int> = _enCurso

    fun inicio() { _enCurso.value = _enCurso.value + 1 }
    fun fin() { _enCurso.value = (_enCurso.value - 1).coerceAtLeast(0) }
}

/**
 * Envuelve una gestión para que muestre el indicador mientras dura.
 * Se apaga siempre, incluso si la operación falla.
 */
suspend fun <T> conIndicador(bloque: suspend () -> T): T {
    EstadoGuardando.inicio()
    try {
        return bloque()
    } finally {
        EstadoGuardando.fin()
    }
}
