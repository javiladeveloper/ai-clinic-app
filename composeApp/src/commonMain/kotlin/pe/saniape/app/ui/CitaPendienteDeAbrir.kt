package pe.saniape.app.ui

import androidx.compose.runtime.mutableStateOf

/**
 * La cita que el paciente quiere ver porque tocó una notificación.
 *
 * Un recordatorio que solo abre la app deja al paciente buscando qué hacer. Con
 * esto cae directo en su cita, con el botón Confirmar delante.
 *
 * Es un estado global mínimo a propósito: el aviso puede llegar con la app
 * cerrada (arranca MainActivity) o abierta (llega por onNewIntent), y ambos
 * caminos tienen que terminar en la misma pantalla.
 *
 * Se limpia al consumirlo: si no, el paciente volvería a la misma cita cada vez
 * que abre la app.
 */
object CitaPendienteDeAbrir {
    private val estado = mutableStateOf<String?>(null)

    /** Lo llama la capa nativa cuando la notificación trae una cita. */
    fun pedir(citaId: String?) {
        if (!citaId.isNullOrBlank()) estado.value = citaId
    }

    /** Id de la cita a abrir, o null. Leerlo desde Compose reacciona al cambio. */
    val actual: String? get() = estado.value

    /** Se llamó y ya se atendió: se olvida para no repetir en el próximo arranque. */
    fun consumir() { estado.value = null }
}
