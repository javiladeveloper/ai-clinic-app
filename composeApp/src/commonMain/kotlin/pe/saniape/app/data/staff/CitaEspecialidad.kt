package pe.saniape.app.data.staff

/**
 * 🏷️ CAMBIAR LA ESPECIALIDAD DE UNA CITA (Centro OMER, 02/11/2026 en la web;
 * 23/11/2026 en la app: «en la web se puede, en el app no permite»).
 *
 * La consulta se agendó en la especialidad equivocada y al atenderla salía la
 * historia clínica de esa otra. Todo lo de la atención (historia, escalas,
 * plantillas, receta) sale de la especialidad de la cita: con corregirla basta.
 * No cambia la fecha, la hora, el profesional ni el precio.
 *
 * Gemelo de `puedeCambiarEspecialidadCita` (lib/cita-especialidad.ts) y de la
 * condición del menú de /citas web. El servidor (/api/staff/cita/especialidad)
 * vuelve a validar todo.
 */

/** null = se puede; si no, por qué no. */
fun motivoNoCambiarEspecialidad(tipo: String?, estado: String?): String? = when {
    // La sesión es de su tratamiento: su especialidad sale del servicio del tratamiento.
    tipo == "Sesión" -> "La especialidad de una sesión es la de su tratamiento: cámbiala en el tratamiento."
    // Una atención ya completada tiene su historia escrita con la especialidad de entonces.
    estado != "Pendiente" && estado != "Confirmada" -> "Solo se cambia la especialidad de una cita por atender."
    else -> null
}

/**
 * ¿Se ofrece «🏷️ Cambiar especialidad» en esta cita? Solo si la clínica tiene
 * más de una especialidad activa y quien mira agenda (citas) o atiende (sesiones).
 */
fun ofreceCambiarEspecialidad(
    tipo: String?, estado: String?, especialidadesActivas: Int,
    puedeCitas: Boolean, puedeSesiones: Boolean,
): Boolean = especialidadesActivas > 1 && (puedeCitas || puedeSesiones) && motivoNoCambiarEspecialidad(tipo, estado) == null
