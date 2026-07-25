package pe.saniape.app.data.staff

/**
 * "Aviso RX": marca que un paciente quedó con una RX (receta / orden de rayos)
 * PENDIENTE al terminar una sesión, para recordárselo al profesional cuando abra
 * la SIGUIENTE sesión de ese paciente.
 *
 * El backend web no expone un campo dedicado para esto y las escrituras de sesión
 * pasan siempre por el endpoint `/api/staff/sesion/estado` (no se pueden agregar
 * columnas desde la app). Por eso el flag se persiste como un MARCADOR discreto
 * dentro del texto de evolución (`mejorias`) de la sesión: ese campo ya viaja al
 * endpoint y sincroniza entre dispositivos (recepción ↔ profesional). Al abrir la
 * sesión siguiente se lee el marcador de la sesión anterior y se muestra el aviso.
 *
 * Toda la lógica de "poner / quitar / detectar" el marcador vive aquí (una sola
 * fuente de verdad), para que escribir y leer no se desincronicen.
 */
object AvisoRx {
    /** Marcador que se guarda dentro del texto de evolución. Único y estable. */
    const val MARCADOR = "⚕️ RX pendiente"

    /** ¿Esta sesión dejó una RX pendiente? (marcador en evolución o procedimientos). */
    fun dejoRx(sesion: SesionFicha?): Boolean =
        contiene(sesion?.mejorias) || contiene(sesion?.notas)

    private fun contiene(texto: String?): Boolean =
        texto?.contains(MARCADOR) == true

    /**
     * Devuelve el texto de evolución con el marcador presente o ausente según [activo].
     * Idempotente: nunca duplica el marcador ni deja restos si se desmarca.
     */
    fun aplicar(mejorias: String?, activo: Boolean): String {
        val limpio = limpiar(mejorias)
        if (!activo) return limpio
        return if (limpio.isBlank()) MARCADOR else "$limpio\n$MARCADOR"
    }

    /** El texto de evolución SIN el marcador (para mostrarlo limpio al usuario). */
    fun limpiar(texto: String?): String =
        texto.orEmpty()
            .lineSequence()
            .filter { it.trim() != MARCADOR }
            .joinToString("\n")
            .trim()
}
