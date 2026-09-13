package pe.saniape.app.ui

import androidx.compose.runtime.mutableStateOf

/**
 * La cita que se quiere ver porque alguien tocó una notificación.
 *
 * Un recordatorio que solo abre la app deja a la persona buscando qué hacer. Con
 * esto cae directo en la cita: el PACIENTE en la suya (con el botón Confirmar
 * delante) y el PROFESIONAL en su agenda, posicionada en el día correcto.
 *
 * Es un estado global mínimo a propósito: el aviso puede llegar con la app
 * cerrada (arranca MainActivity) o abierta (llega por onNewIntent), y ambos
 * caminos tienen que terminar en la misma pantalla.
 *
 * La FECHA viaja aparte del id porque el staff la necesita ANTES de tener la
 * cita cargada: sin ella la agenda abriría en hoy, y una cita de la semana que
 * viene no se vería (reportado 2026-09-13).
 *
 * Se limpia al consumirlo: si no, se volvería a la misma cita cada vez que se
 * abre la app.
 */
object CitaPendienteDeAbrir {
    private val estado = mutableStateOf<String?>(null)
    private val fecha = mutableStateOf<String?>(null)

    /** Lo llama la capa nativa cuando la notificación trae una cita. */
    fun pedir(citaId: String?, citaFecha: String? = null) {
        if (!citaId.isNullOrBlank()) {
            estado.value = citaId
            if (!citaFecha.isNullOrBlank()) fecha.value = citaFecha
        }
    }

    /** Id de la cita a abrir, o null. Leerlo desde Compose reacciona al cambio. */
    val actual: String? get() = estado.value

    /** Fecha (ISO `YYYY-MM-DD`) de esa cita, o null si el aviso no la trajo. */
    val fechaActual: String? get() = fecha.value

    /** Se llamó y ya se atendió: se olvida para no repetir en el próximo arranque. */
    fun consumir() { estado.value = null; fecha.value = null }
}
