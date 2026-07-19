package pe.saniape.app.data.offline

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Cuántas escrituras quedan sin sincronizar. La UI lo observa para mostrar el
 * chip "🕐 N pendientes" y hacerle saber al usuario que lo suyo quedó guardado
 * aunque no haya señal.
 */
object EstadoSync {
    private val _pendientes = MutableStateFlow(0L)
    val pendientes: StateFlow<Long> = _pendientes

    fun actualizar(n: Long) { _pendientes.value = n }
}
