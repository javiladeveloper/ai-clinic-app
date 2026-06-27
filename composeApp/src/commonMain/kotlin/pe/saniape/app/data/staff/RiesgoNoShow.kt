package pe.saniape.app.data.staff

/** Nivel de riesgo de inasistencia. */
enum class NivelRiesgo { Alto, Medio, Bajo }

/** Resultado del cálculo de riesgo (igual que lib/riesgo-noshow.ts de la web). */
data class ResultadoRiesgo(
    val score: Int,
    val nivel: NivelRiesgo,
    val motivos: List<String>,
)

/** Señales para calcular el riesgo. */
data class SenalesNoShow(
    val faltasPrevias: Int,
    val citasPrevias: Int,
    val confirmadaPorPaciente: Boolean,
    val pendiente: Boolean,
    val esOnline: Boolean,
    val sinTelefono: Boolean,
)

/**
 * Calcula el riesgo de que un paciente falte a su cita. REGLA DE NEGOCIO idéntica
 * a la web (lib/riesgo-noshow.ts): mismos pesos y umbrales. No la cambiamos.
 */
fun calcularRiesgoNoShow(s: SenalesNoShow): ResultadoRiesgo {
    var score = 20
    val motivos = mutableListOf<String>()

    // Historial de faltas (señal más fuerte)
    if (s.citasPrevias > 0) {
        val tasa = s.faltasPrevias.toDouble() / s.citasPrevias
        when {
            tasa >= 0.5 -> { score += 45; motivos.add("Faltó a ${s.faltasPrevias} de ${s.citasPrevias} citas") }
            tasa >= 0.25 -> { score += 30; motivos.add("Ha faltado antes (${s.faltasPrevias}/${s.citasPrevias})") }
            s.faltasPrevias > 0 -> { score += 15; motivos.add("Tiene alguna falta previa") }
        }
    } else {
        score += 10; motivos.add("Paciente nuevo (sin historial)")
    }

    // Confirmación
    if (s.confirmadaPorPaciente) {
        score -= 30; motivos.add("Confirmó su cita ✓")
    } else if (s.pendiente) {
        score += 20; motivos.add("Sin confirmar")
    }

    // Reserva online sin confirmar
    if (s.esOnline && !s.confirmadaPorPaciente) {
        score += 15; motivos.add("Reserva web sin confirmar")
    }

    // Sin teléfono
    if (s.sinTelefono) {
        score += 15; motivos.add("Sin teléfono para recordarle")
    }

    score = score.coerceIn(0, 100)
    val nivel = when {
        score >= 60 -> NivelRiesgo.Alto
        score >= 35 -> NivelRiesgo.Medio
        else -> NivelRiesgo.Bajo
    }
    return ResultadoRiesgo(score, nivel, motivos)
}
