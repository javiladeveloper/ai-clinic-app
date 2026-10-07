package pe.saniape.app.data.staff

/**
 * Diagnósticos/motivos frecuentes para el typeahead de "Diagnóstico / Motivo".
 * Espeja lib/diagnosticos.ts de la web: desde 2026-10-07 va por [ChipsRepo]
 * (lo de la clínica en esa especialidad + generales y, detrás, el pool del rubro).
 */
object DiagnosticosRepo {

    /** Más usados primero. Una vez por apertura del formulario (caché en memoria). */
    suspend fun sugerencias(especialidadId: String?): List<String> =
        ChipsRepo.textos(CampoChip.DIAGNOSTICO, especialidadId)

    /**
     * Aprende los términos de un diagnóstico (RPC registrar_chips con la
     * especialidad). En segundo plano: nunca bloquea el completar.
     */
    fun registrar(texto: String, especialidadId: String?, nombrePaciente: String? = null) =
        ChipsRepo.registrarDiagnostico(texto, especialidadId, nombrePaciente)
}
