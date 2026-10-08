package pe.saniape.app.data.staff

import kotlin.jvm.JvmName

/**
 * Cómo llama la clínica a sus "pacientes" (Cliente en estética/spa…). Gemelo de
 * `resolverTerminologia` de la web: las cuatro formas. Solo texto de UI.
 */
data class TerminologiaPaciente(
    val paciente: String = "paciente",
    val pacientes: String = "pacientes",
    @get:JvmName("getPacienteCap") val Paciente: String = "Paciente",
    @get:JvmName("getPacientesCap") val Pacientes: String = "Pacientes",
) {
    companion object {
        /** Desde el singular/plural que manda el servidor; vacío o faltante → "Paciente"/"Pacientes". */
        fun de(singular: String?, plural: String?): TerminologiaPaciente {
            val s = singular?.trim().orEmpty().ifBlank { "Paciente" }
            val p = plural?.trim().orEmpty().ifBlank { "${s}s" }
            return TerminologiaPaciente(s.lowercase(), p.lowercase(), cap(s), cap(p))
        }
        private fun cap(t: String) = t.replaceFirstChar { it.uppercase() }
    }
}
