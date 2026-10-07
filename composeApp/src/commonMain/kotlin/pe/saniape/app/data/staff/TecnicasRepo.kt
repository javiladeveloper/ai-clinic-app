package pe.saniape.app.data.staff

/**
 * Técnicas/procedimientos de la clínica para el autocompletado al completar una
 * sesión. Desde 2026-10-07 van por [ChipsRepo] (contrato de chips por
 * especialidad): lo propio de la clínica en ESA especialidad + las generales y,
 * detrás, lo que usan las clínicas del mismo rubro (pool compartido).
 */
object TecnicasRepo {
    /** Más usadas primero (propias) y luego las del rubro. Una vez por apertura (caché). */
    suspend fun sugerencias(especialidadId: String?): List<String> =
        ChipsRepo.textos(CampoChip.TECNICA, especialidadId)

    /**
     * Aprende las técnicas usadas (RPC registrar_chips, con la especialidad del
     * tratamiento/cita). En segundo plano: vuelve al instante y nunca falla.
     * [texto] viene unido con " + " (mismo formato que sesiones.notas); se parte
     * con el normalizador, así "TENS+COMPRESA" no entra como UNA técnica
     * (así se llenó DALU de frases enteras, Jonathan 2026-09-11).
     */
    fun registrar(texto: String, especialidadId: String?, nombrePaciente: String? = null) =
        ChipsRepo.registrarTecnicas(texto, especialidadId, nombrePaciente)
}
