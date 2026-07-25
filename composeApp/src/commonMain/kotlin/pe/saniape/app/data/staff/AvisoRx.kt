package pe.saniape.app.data.staff

/**
 * "Aviso RX": marca que un paciente quedó con una RX (receta / orden de rayos)
 * PENDIENTE al terminar una sesión, para recordárselo al profesional cuando abra
 * la SIGUIENTE sesión de ese paciente.
 *
 * El dato vive en su propia columna (`sesiones.rx_pendiente`) y viaja al endpoint
 * `/api/staff/sesion/estado` como `rxPendiente`. Antes se guardaba como un marcador
 * de texto escondido dentro de la evolución (`mejorias`): funcionaba, pero ensuciaba
 * la historia clínica, se perdía si alguien editaba ese texto desde la web, y la web
 * no podía mostrar el aviso. Con la columna es explícito y funciona en web y app.
 */
object AvisoRx {
    /** ¿Esta sesión dejó una RX pendiente? */
    fun dejoRx(sesion: SesionFicha?): Boolean = sesion?.rxPendiente == true
}
