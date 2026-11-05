package pe.saniape.app.data.staff

// La próxima cita de cada tratamiento abierto, para el aviso «N tratamientos
// abiertos» de la agenda (gemelo del «📅 … agendada» de PlanPaciente.tsx web,
// 2026-11-05): con ella se ve que el plan ya tiene su cita antes de crear otro
// o de ir a la ficha a agendar de nuevo.

/** La próxima cita de un tratamiento: sin atender (Pendiente/Confirmada) y de hoy en adelante. */
data class ProximaCitaPlan(
    val tratamientoId: String,
    val fecha: String,
    val hora: String?,
    val terapeutaNombre: String?,
)

/** La PRIMERA de cada tratamiento (por fecha y hora, sin importar cómo lleguen). */
fun primeraCitaPorTratamiento(filas: List<ProximaCitaPlan>): Map<String, ProximaCitaPlan> {
    val m = linkedMapOf<String, ProximaCitaPlan>()
    filas.sortedWith(compareBy({ it.fecha }, { it.hora ?: "" }))
        .forEach { if (it.tratamientoId !in m) m[it.tratamientoId] = it }
    return m
}

/**
 * «📅 Intervención agendada · vie 06/11 · 09:00 · Dr. Mamani». Intervención si
 * va por unidades, Sesión si tiene plan de sesiones, si no Cita.
 */
fun textoCitaAgendada(modalidad: String?, totalSesiones: Int, esConsulta: Boolean, c: ProximaCitaPlan): String {
    val que = when {
        modalidad == "Unidades" -> "Intervención"
        totalSesiones > 0 && !esConsulta -> "Sesión"
        else -> "Cita"
    }
    return buildString {
        append("📅 $que agendada · ${fechaLegibleCorta(c.fecha)}")
        c.hora?.take(5)?.takeIf { it.isNotBlank() }?.let { append(" · $it") }
        c.terapeutaNombre?.takeIf { it.isNotBlank() }?.let { append(" · $it") }
    }
}
